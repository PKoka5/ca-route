package com.caroute.navigation;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.client.events.PluginChanged;
import net.runelite.client.events.PluginMessage;
import net.runelite.client.plugins.Plugin;
import org.junit.Test;
import static org.junit.Assert.*;
import static com.caroute.navigation.ShortestPathBridge.Result.*;

public class ShortestPathBridgeTest
{
    private static final WorldPoint BOSS_ENTRANCE = new WorldPoint(3016, 3848, 0);
    private static final WorldPoint OTHER_TARGET = new WorldPoint(3200, 3200, 0);

    @Test public void startUsesThePublicPathContractAndLeavesTransportSettingsAlone()
    {
        Fixture f = new Fixture();
        assertEquals(STARTED, f.bridge.start(BOSS_ENTRANCE));
        assertTrue(f.bridge.available()); assertTrue(f.bridge.ownsTarget());
        assertEquals(1, f.sent.size());
        PluginMessage message = f.sent.get(0);
        assertEquals("shortestpath", message.getNamespace()); assertEquals("path", message.getName());
        assertEquals(Map.of("target", BOSS_ENTRANCE), message.getData());
        assertFalse(message.getData().containsKey("start")); assertFalse(message.getData().containsKey("config"));
    }

    @Test public void stoppingOnlyClearsOurOwnLiveRequestAndIsIdempotent()
    {
        Fixture f = new Fixture();
        assertEquals(NOT_OWNER, f.bridge.stop()); assertTrue(f.sent.isEmpty());
        f.bridge.start(BOSS_ENTRANCE);
        assertEquals(STOPPED, f.bridge.stop()); assertFalse(f.bridge.ownsTarget());
        PluginMessage clear = f.sent.get(1);
        assertEquals("shortestpath", clear.getNamespace()); assertEquals("clear", clear.getName()); assertTrue(clear.getData().isEmpty());
        assertEquals(NOT_OWNER, f.bridge.stop()); assertEquals(2, f.sent.size());
    }

    @Test public void anotherPluginTakesOwnershipEvenWhenItsCoordinatesMatchOurs()
    {
        Fixture f = new Fixture(); f.bridge.start(BOSS_ENTRANCE);
        f.bridge.observe(new PluginMessage("shortestpath", "path", Map.of("target", BOSS_ENTRANCE)));
        assertFalse(f.bridge.ownsTarget()); assertEquals(NOT_OWNER, f.bridge.stop()); assertEquals(1, f.sent.size());
        f.bridge.start(BOSS_ENTRANCE);
        f.bridge.observe(new PluginMessage("shortestpath", "path", Map.of("target", OTHER_TARGET)));
        assertEquals(NOT_OWNER, f.bridge.stop()); assertEquals(2, f.sent.size());
        f.bridge.start(BOSS_ENTRANCE); f.bridge.observe(new PluginMessage("shortestpath", "clear"));
        assertEquals(NOT_OWNER, f.bridge.stop()); assertEquals(3, f.sent.size());
    }

    @Test public void transportRepliesAndOtherNamespacesDoNotStealTheClaim()
    {
        Fixture f = new Fixture(); f.bridge.start(BOSS_ENTRANCE);
        f.bridge.observe(new PluginMessage("shortestpath", "transports", Map.of("destination", List.of(OTHER_TARGET))));
        f.bridge.observe(new PluginMessage("another-plugin", "path", Map.of("target", OTHER_TARGET)));
        assertTrue(f.bridge.ownsTarget()); assertEquals(STOPPED, f.bridge.stop());
    }

    @Test public void delayedSelfEchoesCannotBeMistakenForAnExternalRoute()
    {
        List<PluginMessage> sent = new ArrayList<>(); ShortestPathBridge bridge = new ShortestPathBridge(sent::add, () -> true);
        bridge.start(BOSS_ENTRANCE); bridge.start(OTHER_TARGET);
        bridge.observe(sent.get(0)); bridge.observe(sent.get(1)); assertTrue(bridge.ownsTarget());
        assertEquals(STOPPED, bridge.stop()); bridge.start(BOSS_ENTRANCE);
        bridge.observe(sent.get(2)); bridge.observe(sent.get(3)); assertTrue(bridge.ownsTarget());
        bridge.observe(new PluginMessage("shortestpath", "path", Map.of("target", BOSS_ENTRANCE)));
        assertFalse(bridge.ownsTarget());
    }

    @Test public void aReentrantExternalRequestDuringOurPublishStillRevokesOwnership()
    {
        ShortestPathBridge[] holder = new ShortestPathBridge[1]; List<PluginMessage> sent = new ArrayList<>();
        holder[0] = new ShortestPathBridge(message -> {
            sent.add(message); holder[0].observe(message);
            holder[0].observe(new PluginMessage("shortestpath", "path", Map.of("target", OTHER_TARGET)));
        }, () -> true);
        assertEquals(STARTED, holder[0].start(BOSS_ENTRANCE));
        assertFalse(holder[0].ownsTarget()); assertEquals(NOT_OWNER, holder[0].stop()); assertEquals(1, sent.size());
    }

    @Test public void manualShortestPathMenusRevokeOwnershipWithoutClearingTheNewRoute()
    {
        for (String[] menu : new String[][] { {"Set", "<col=ff0000>Target</col>"}, {"Set", "<col=ff0000>Target</col><col=00ff00> 2</col>"},
            {"Set", "<col=ff0000>Start</col>"}, {"Clear", "<col=ff0000>Path</col>"}, {"Find closest", "Bank"} })
        {
            Fixture f = new Fixture(); f.bridge.start(BOSS_ENTRANCE);
            f.bridge.observe(menu(menu[0], menu[1], MenuAction.RUNELITE));
            assertFalse(menu[0] + " " + menu[1], f.bridge.ownsTarget());
            assertEquals(NOT_OWNER, f.bridge.stop()); assertEquals(1, f.sent.size());
        }
        Fixture f = new Fixture(); f.bridge.start(BOSS_ENTRANCE);
        f.bridge.observe(menu("Walk here", "", MenuAction.WALK));
        f.bridge.observe(menu("Set", "Bank tag", MenuAction.RUNELITE));
        f.bridge.observe(menu("Clear", "Ground marker", MenuAction.RUNELITE));
        f.bridge.observe(menu("Set", "Target", MenuAction.CC_OP));
        assertTrue(f.bridge.ownsTarget());
    }

    @Test public void missingOrRestartedPluginDoesNotLeaveAClaimOnItsFutureRoute()
    {
        Fixture f = new Fixture(); f.active.set(false);
        assertFalse(f.bridge.available()); assertEquals(UNAVAILABLE, f.bridge.start(BOSS_ENTRANCE)); assertTrue(f.sent.isEmpty());
        f.active.set(true); f.bridge.start(BOSS_ENTRANCE);
        f.bridge.observe(new PluginChanged(plugin("Other Plugin"), false)); assertTrue(f.bridge.ownsTarget());
        f.bridge.observe(new PluginChanged(plugin("Shortest Path"), false));
        assertFalse(f.bridge.ownsTarget()); assertEquals(NOT_OWNER, f.bridge.stop());
        f.bridge.start(BOSS_ENTRANCE); f.bridge.observe(new PluginChanged(plugin("Shortest Path"), true));
        assertEquals(NOT_OWNER, f.bridge.stop());
        f.bridge.start(BOSS_ENTRANCE); f.active.set(false);
        assertEquals(UNAVAILABLE, f.bridge.stop()); assertFalse(f.bridge.ownsTarget());
        f.active.set(true); assertEquals(NOT_OWNER, f.bridge.stop()); assertEquals(3, f.sent.size());
    }

    @Test public void invalidPointsAndPublicationFailureCannotCreateAnUnsafeClaim()
    {
        Fixture f = new Fixture();
        for (WorldPoint target : new WorldPoint[] {null, new WorldPoint(-1, 3200, 0), new WorldPoint(32768, 3200, 0),
            new WorldPoint(3200, 32768, 0), new WorldPoint(3200, 3200, 4), new WorldPoint(32767, 32767, 3)})
            assertEquals(INVALID_TARGET, f.bridge.start(target));
        assertTrue(f.sent.isEmpty()); assertFalse(f.bridge.ownsTarget());
        ShortestPathBridge failing = new ShortestPathBridge(message -> { throw new IllegalStateException("unavailable transport"); }, () -> true);
        assertEquals(FAILED, failing.start(BOSS_ENTRANCE)); assertFalse(failing.ownsTarget()); assertEquals(NOT_OWNER, failing.stop());
        f.bridge.start(BOSS_ENTRANCE); f.bridge.forgetOwnership(); assertEquals(NOT_OWNER, f.bridge.stop()); assertEquals(1, f.sent.size());
    }

    private static final class Fixture
    {
        final AtomicBoolean active = new AtomicBoolean(true);
        final List<PluginMessage> sent = new ArrayList<>();
        final ShortestPathBridge bridge;
        Fixture()
        {
            ShortestPathBridge[] holder = new ShortestPathBridge[1];
            bridge = holder[0] = new ShortestPathBridge(message -> { sent.add(message); holder[0].observe(message); }, active::get);
        }
    }

    private static Plugin plugin(String name) { return new Plugin() { @Override public String getName() { return name; } }; }

    private static MenuOptionClicked menu(String option, String target, MenuAction type)
    {
        // Lightweight implementation of the RuneLite interface; no real client or browser.
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
