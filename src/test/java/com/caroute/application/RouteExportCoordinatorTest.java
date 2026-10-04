package com.caroute.application;

import com.caroute.domain.*;
import com.google.gson.Gson;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import org.junit.Test;
import static org.junit.Assert.*;

public class RouteExportCoordinatorTest
{
    @Test public void queuedExportNeverComputesAfterAccountVersionOrLifecycleChanges()
    {
        for (String change : List.of("account", "version", "same-account-hop", "shutdown"))
        {
            QueueExecutor worker = new QueueExecutor(), ui = new QueueExecutor(); State state = new State();
            RouteExportCoordinator coordinator = new RouteExportCoordinator(worker, ui);
            AtomicInteger computed = new AtomicInteger(), presented = new AtomicInteger();
            coordinator.submit(state.capture(), () -> { computed.incrementAndGet(); return json(); }, result -> presented.incrementAndGet(), failure -> fail("Unexpected export failure"));
            if (change.equals("account")) state.account = "456";
            else if (change.equals("version")) state.version++;
            else if (change.equals("same-account-hop")) state.lifecycle++;
            else state.active = false;
            worker.runAll(); ui.runAll(); assertEquals(change, 0, computed.get()); assertEquals(change, 0, presented.get());
        }
    }

    @Test public void completedExportIsDiscardedIfAnotherPlanOrLoginArrivesBeforeTheEdt()
    {
        for (boolean hop : new boolean[]{false, true})
        {
            QueueExecutor worker = new QueueExecutor(), ui = new QueueExecutor(); State state = new State();
            RouteExportCoordinator coordinator = new RouteExportCoordinator(worker, ui); List<String> saves = new ArrayList<>();
            coordinator.submit(state.capture(), RouteExportCoordinatorTest::json, result -> saves.add(result.json()), failure -> fail("Unexpected export failure"));
            worker.runAll(); assertEquals(1, ui.pending.size());
            if (hop) state.lifecycle++; else state.version++;
            ui.runAll(); assertTrue(saves.isEmpty());
        }
    }

    @Test public void currentExportPublishesActualJsonOnceAndStaleFailureDoesNotOpenAResultDialog()
    {
        QueueExecutor worker = new QueueExecutor(), ui = new QueueExecutor(); State state = new State();
        RouteExportCoordinator coordinator = new RouteExportCoordinator(worker, ui); List<String> saves = new ArrayList<>(); List<RuntimeException> errors = new ArrayList<>();
        coordinator.submit(state.capture(), RouteExportCoordinatorTest::json, result -> saves.add(result.json()), errors::add);
        worker.runAll(); assertTrue(saves.isEmpty()); ui.runAll(); assertEquals(1, saves.size()); assertTrue(saves.get(0).contains("91000"));
        coordinator.submit(state.capture(), () -> { throw new IllegalStateException("fixture"); }, result -> fail("No failed result"), errors::add);
        worker.runAll(); state.lifecycle++; ui.runAll(); assertTrue(errors.isEmpty());
    }

    private static TasksTrackerRouteExporter.ExportResult json()
    {
        CombatAchievement task = CombatAchievement.builder(0, "Task", "Boss", AchievementTier.EASY).requirementsKnown(true).build();
        TaskAssessment assessment = new TaskAssessment(task, new Eligibility(EligibilityState.AVAILABLE, List.of(), Confidence.HIGH, true, false), 1, 1, Map.of(), List.of(), Confidence.HIGH);
        RoutePlan plan = new RoutePlan(AchievementTier.EASY, 0, 1, 1, 1, 1,
            List.of(new BossSession("Boss", List.of(assessment), 1, 1, 1, List.of(), Confidence.HIGH, false)),
            List.of(assessment), List.of(), Reachability.REACHABLE_NOW, Confidence.HIGH, true, 10);
        return new TasksTrackerRouteExporter(new Gson()).export(plan, Map.of(0, 91000));
    }
    private static final class State
    {
        boolean active = true; long lifecycle = 3, version = 10; String account = "123";
        BooleanSupplier capture()
        { RouteExportCoordinator.Request request = new RouteExportCoordinator.Request(lifecycle, account, version);
            return () -> request.current(active, lifecycle, account, version); }
    }
    private static final class QueueExecutor implements Executor
    {
        final Queue<Runnable> pending = new ArrayDeque<>();
        @Override public void execute(Runnable runnable) { pending.add(runnable); }
        void runAll() { while (!pending.isEmpty()) pending.remove().run(); }
    }
}
