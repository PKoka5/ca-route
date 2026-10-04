package com.caroute.navigation;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.PluginChanged;
import net.runelite.client.events.PluginMessage;
import net.runelite.client.plugins.Plugin;
import org.junit.Test;
import static org.junit.Assert.*;
import static com.caroute.navigation.ShortestPathBridge.Result.*;

public class GuideEventObserverTest
{
    private static final WorldPoint OUR_TARGET = new WorldPoint(3016, 3849, 0);
    private static final WorldPoint FOREIGN_TARGET = new WorldPoint(3200, 3200, 0);

    @Test public void observerSurvivesRootUnregistrationAndRevokesBeforeForeignPathProcessing() throws Exception
    {
        Fixture f = new Fixture(); RootListener root = new RootListener(); f.bus.register(root);
        assertEquals(STARTED, f.bridge.start(OUR_TARGET)); assertTrue(f.bridge.ownsTarget());
        assertEquals(1, f.updates.get()); assertEquals(1, root.events);
        f.bus.unregister(root); // RuneLite unregisters the owner before shutdown cleanup runs.
        ForeignPathSink sink = new ForeignPathSink(f.bridge); f.bus.register(sink);
        offThread(() -> f.bus.post(new PluginMessage("shortestpath", "path", Map.of("target", FOREIGN_TARGET))));
        assertTrue("Lease must be revoked before the default-priority path handler", sink.sawRevokedClaim);
        assertFalse(f.bridge.ownsTarget()); assertEquals(1, f.updates.get()); assertEquals(1, f.queue.size());
        assertEquals(NOT_OWNER, f.bridge.stop()); assertEquals(1, f.sent.size());
        f.drain(); assertEquals(2, f.updates.get());
        f.observer.close(); f.bus.unregister(f.observer);
    }

    @Test public void queuedOldEventsAreDiscardedAfterCleanupAndCannotTouchANewGuide() throws Exception
    {
        Fixture f = new Fixture(); f.bridge.start(OUR_TARGET);
        offThread(() -> {
            f.bus.post(new PluginMessage("shortestpath", "transports", Map.of("destination", List.of(FOREIGN_TARGET))));
            f.bus.post(new PluginMessage("shortestpath", "clear"));
        });
        assertFalse(f.bridge.ownsTarget()); assertEquals(2, f.queue.size());
        // Cleanup may run before the queued presentation updates. It must not clear the foreign route.
        assertEquals(NOT_OWNER, f.bridge.stop()); f.observer.close(); f.bus.unregister(f.observer);
        AtomicInteger newUpdates = new AtomicInteger(); List<PluginMessage> newSent = new ArrayList<>();
        ShortestPathBridge replacement = new ShortestPathBridge(message -> { newSent.add(message); f.bus.post(message); }, () -> true);
        GuideEventObserver newObserver = new GuideEventObserver(() -> true, f.queue::add, replacement, newUpdates::incrementAndGet);
        f.bus.register(newObserver); replacement.start(FOREIGN_TARGET);
        f.drain();
        assertTrue(replacement.ownsTarget()); assertEquals(1, newUpdates.get());
        assertEquals("Disposed observer must suppress its queued callbacks", 1, f.updates.get());
        assertEquals(1, f.sent.size()); assertEquals(1, newSent.size());
        replacement.stop(); newObserver.close(); f.bus.unregister(newObserver);
    }

    @Test public void onlyShortestPathCommandsMenusAndLifecycleChangesAffectTheLease()
    {
        Fixture f = new Fixture(); f.bridge.start(OUR_TARGET);
        f.bus.post(new PluginMessage("another-plugin", "path", Map.of("target", FOREIGN_TARGET)));
        f.bus.post(new PluginMessage("shortestpath", "unrelated-message"));
        f.bus.post(menu("Walk here", "", MenuAction.WALK));
        f.bus.post(menu("Clear", "Ground marker", MenuAction.RUNELITE));
        f.bus.post(new PluginChanged(plugin("Other Plugin"), false));
        assertTrue(f.bridge.ownsTarget()); assertEquals(1, f.updates.get()); assertTrue(f.queue.isEmpty());
        f.bus.post(new PluginMessage("shortestpath", "transports"));
        assertTrue(f.bridge.ownsTarget()); assertEquals(2, f.updates.get());
        f.bus.post(menu("Set", "<col=ff0000>Target</col><col=00ff00> 2</col>", MenuAction.RUNELITE));
        assertFalse(f.bridge.ownsTarget()); assertEquals(NOT_OWNER, f.bridge.stop()); assertEquals(1, f.sent.size());
        f.bridge.start(OUR_TARGET); f.bus.post(new PluginChanged(plugin("Shortest Path"), false));
        assertFalse(f.bridge.ownsTarget()); assertEquals(NOT_OWNER, f.bridge.stop());
        int beforeClose = f.updates.get(); f.observer.close();
        f.bus.post(new PluginMessage("shortestpath", "path", Map.of("target", FOREIGN_TARGET)));
        assertEquals(beforeClose, f.updates.get()); f.bus.unregister(f.observer);
    }

    private static final class Fixture
    {
        final EventBus bus = new EventBus();
        final Thread clientThread = Thread.currentThread();
        final Queue<Runnable> queue = new ConcurrentLinkedQueue<>();
        final List<PluginMessage> sent = new ArrayList<>();
        final AtomicInteger updates = new AtomicInteger();
        final ShortestPathBridge bridge = new ShortestPathBridge(message -> { sent.add(message); bus.post(message); }, () -> true);
        final GuideEventObserver observer = new GuideEventObserver(() -> Thread.currentThread() == clientThread,
            queue::add, bridge, updates::incrementAndGet);
        Fixture() { bus.register(observer); }
        void drain() { Runnable action; while ((action = queue.poll()) != null) action.run(); }
    }

    public static final class RootListener
    {
        int events;
        @Subscribe public void onPluginMessage(PluginMessage event) { events++; }
    }

    public static final class ForeignPathSink
    {
        final ShortestPathBridge bridge;
        volatile boolean sawRevokedClaim;
        ForeignPathSink(ShortestPathBridge bridge) { this.bridge = bridge; }
        @Subscribe public void onPluginMessage(PluginMessage event)
        {
            if (FOREIGN_TARGET.equals(event.getData().get("target"))) sawRevokedClaim = !bridge.ownsTarget();
        }
    }

    private static void offThread(Runnable action) throws Exception
    {
        FutureTask<Void> work = new FutureTask<>(action, null);
        Thread worker = new Thread(work, "guide-observer-test"); worker.setDaemon(true); worker.start();
        work.get(5, TimeUnit.SECONDS);
    }

    private static Plugin plugin(String name) { return new Plugin() { @Override public String getName() { return name; } }; }

    private static MenuOptionClicked menu(String option, String target, MenuAction type)
    {
        MenuEntry entry = (MenuEntry) Proxy.newProxyInstance(MenuEntry.class.getClassLoader(), new Class<?>[] {MenuEntry.class},
            (proxy, method, arguments) -> {
                switch (method.getName())
                {
                    case "getOption": return option;
                    case "getTarget": return target;
                    case "getType": return type;
                    default: return null;
                }
            });
        return new MenuOptionClicked(entry);
    }
}
