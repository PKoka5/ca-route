package com.caroute.application;

import com.caroute.domain.*;
import com.caroute.engine.RouteEngine;
import java.util.*;
import java.util.concurrent.Executor;
import org.junit.Test;
import static org.junit.Assert.*;

public class AsyncRouteCoordinatorTest
{
    static class Queue implements Executor { final List<Runnable> queued=new ArrayList<>(); public void execute(Runnable r){queued.add(r);} void drain(){while(!queued.isEmpty())queued.remove(0).run();} }
    @Test public void staleAccountAndRequestResultsNeverReachPresentation()
    {
        Queue workers=new Queue(),ui=new Queue();List<RoutePlan> rendered=new ArrayList<>();
        AsyncRouteCoordinator coordinator=new AsyncRouteCoordinator(workers,ui,(p,r)->new RouteEngine().plan(Collections.emptyList(),p,r),rendered::add,e->{throw e;});
        RoutingPreferences prefs=RoutingPreferences.builder().target(AchievementTier.EASY).build();
        coordinator.request(PlayerProfile.builder().accountKey("A").loaded(true).version(1).thresholds(Map.of(AchievementTier.EASY,1)).build(),prefs);
        workers.drain();
        coordinator.request(PlayerProfile.builder().accountKey("B").loaded(true).version(2).thresholds(Map.of(AchievementTier.EASY,1)).build(),prefs);
        ui.drain();assertTrue(rendered.isEmpty());workers.drain();ui.drain();assertEquals(1,rendered.size());assertEquals(2,rendered.get(0).profileVersion());
        coordinator.close();coordinator.request(PlayerProfile.builder().version(3).build(),prefs);assertTrue(workers.queued.isEmpty());
    }
}
