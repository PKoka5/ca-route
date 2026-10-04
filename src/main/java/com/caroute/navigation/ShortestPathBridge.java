package com.caroute.navigation;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import net.runelite.api.MenuAction;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.events.PluginChanged;
import net.runelite.client.events.PluginMessage;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.util.Text;

/**
 * Optional Shortest Path integration through its public PluginMessage protocol.
 * The owner must call this adapter on the client thread and forward plugin/menu
 * events to it. STARTED means a request was posted, not that a path was found.
 *
 * Shortest Path has no owner token or current-target query. A clear is therefore
 * allowed only while our last request has not been superseded by another path
 * message, a manual path action, or a Shortest Path lifecycle change. Matching
 * coordinates alone never prove ownership.
 *
 * Protocol: https://github.com/Skretzo/shortest-path/blob/master/src/main/java/shortestpath/ShortestPathPlugin.java
 */
public final class ShortestPathBridge
{
    public enum Result { STARTED, STOPPED, NOT_OWNER, UNAVAILABLE, INVALID_TARGET, FAILED }

    private static final String PLUGIN_NAME = "Shortest Path";
    private static final String NAMESPACE = "shortestpath";
    private final Consumer<PluginMessage> publisher;
    private final BooleanSupplier availability;
    private final Object leaseLock = new Object();
    // Identity matters: another plugin may post the same destination and data.
    // Forwarding our own EventBus echo consumes these entries synchronously.
    private final Set<PluginMessage> ownMessages = Collections.newSetFromMap(new IdentityHashMap<>());
    private volatile WorldPoint ownedTarget;

    public ShortestPathBridge(EventBus eventBus, PluginManager pluginManager)
    {
        this(Objects.requireNonNull(eventBus)::post,
            () -> Objects.requireNonNull(pluginManager).getPlugins().stream()
                .anyMatch(plugin -> PLUGIN_NAME.equals(plugin.getName()) && pluginManager.isPluginActive(plugin)));
    }

    /** Injectable transport and availability for tests, without launching a plugin. */
    public ShortestPathBridge(Consumer<PluginMessage> publisher, BooleanSupplier availability)
    {
        this.publisher = Objects.requireNonNull(publisher);
        this.availability = Objects.requireNonNull(availability);
    }

    public boolean available()
    {
        boolean available = availability.getAsBoolean();
        if (!available) forgetOwnership();
        return available;
    }

    public boolean ownsTarget() { return ownedTarget != null; }

    public Result start(WorldPoint target)
    {
        if (!valid(target)) return Result.INVALID_TARGET;
        synchronized (leaseLock)
        {
            if (!available()) return Result.UNAVAILABLE;
            // Replacing a CA Route destination needs only a new path request.
            ownedTarget = target;
            PluginMessage message = new PluginMessage(NAMESPACE, "path", Map.of("target", target));
            if (!post(message)) { forgetOwnership(); return Result.FAILED; }
            return Result.STARTED;
        }
    }

    public Result stop()
    {
        synchronized (leaseLock)
        {
            if (!ownsTarget()) return Result.NOT_OWNER;
            if (!available()) return Result.UNAVAILABLE;
            forgetOwnership();
            return post(new PluginMessage(NAMESPACE, "clear")) ? Result.STOPPED : Result.FAILED;
        }
    }

    /** Forward all PluginMessage events, including our own synchronous echo. */
    public void observe(PluginMessage event)
    {
        if (event == null) return;
        if (ownMessages.remove(event)) return;
        if (NAMESPACE.equals(event.getNamespace())
            && ("path".equals(event.getName()) || "clear".equals(event.getName())))
            forgetOwnership();
    }

    /** Menu targets are formatted by Shortest Path with RuneLite color tags. */
    public void observe(MenuOptionClicked event)
    {
        if (isManualPathInteraction(event)) manualPathInteraction();
    }

    static boolean isManualPathInteraction(MenuOptionClicked event)
    {
        if (event == null || event.getMenuAction() != MenuAction.RUNELITE) return false;
        String option = plain(event.getMenuOption()), target = plain(event.getMenuTarget());
        return ("Set".equals(option) && ("Target".equals(target) || target.startsWith("Target ") || "Start".equals(target)))
            || ("Clear".equals(option) && "Path".equals(target))
            || "Find closest".equals(option);
    }

    /** A stop/start cycle invalidates leases even if the plugin is active again. */
    public void observe(PluginChanged event)
    {
        if (event != null && PLUGIN_NAME.equals(event.getPlugin().getName())) forgetOwnership();
    }

    public void manualPathInteraction() { forgetOwnership(); }

    /** May also be called by an off-thread event observer before queued cleanup. */
    public void forgetOwnership() { synchronized (leaseLock) { ownedTarget = null; } }

    private boolean post(PluginMessage message)
    {
        ownMessages.add(message);
        try { publisher.accept(message); return true; }
        catch (RuntimeException ex) { ownMessages.remove(message); return false; }
    }

    private static String plain(String value) { return value == null ? "" : Text.removeTags(value).trim(); }

    private static boolean valid(WorldPoint target)
    {
        // Shortest Path packs x/y into 15 bits and the plane into 2 bits.
        // Reject wrapping coordinates and its packed -1 sentinel.
        return target != null && target.getX() >= 0 && target.getX() <= 32767
            && target.getY() >= 0 && target.getY() <= 32767
            && target.getPlane() >= 0 && target.getPlane() <= 3
            && !(target.getX() == 32767 && target.getY() == 32767 && target.getPlane() == 3);
    }
}
