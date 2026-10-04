package com.caroute.navigation;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import net.runelite.api.Client;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.PluginChanged;
import net.runelite.client.events.PluginMessage;

/**
 * Keeps observing a controller's path lease until its client-thread cleanup ends,
 * even after RuneLite unregisters the owning plugin during shutdown.
 * Register once at startup; after oldGuide.stop(), close and unregister in finally.
 */
public final class GuideEventObserver
{
    private final BooleanSupplier onClientThread;
    private final Consumer<Runnable> enqueue;
    private final ShortestPathBridge paths;
    private final Runnable navigationChanged;
    private volatile boolean disposed;

    public GuideEventObserver(Client client, ClientThread clientThread, ShortestPathBridge paths, GuideController guide)
    {
        this(Objects.requireNonNull(client)::isClientThread, Objects.requireNonNull(clientThread)::invokeLater,
            paths, Objects.requireNonNull(guide)::navigationChanged);
    }

    GuideEventObserver(BooleanSupplier onClientThread, Consumer<Runnable> enqueue,
        ShortestPathBridge paths, Runnable navigationChanged)
    {
        this.onClientThread = Objects.requireNonNull(onClientThread); this.enqueue = Objects.requireNonNull(enqueue);
        this.paths = Objects.requireNonNull(paths); this.navigationChanged = Objects.requireNonNull(navigationChanged);
    }

    // Run before Shortest Path's default-priority handlers, including off-thread
    // requests that can change its config before installing a target on the client.
    @Subscribe(priority = 1.0f)
    public void onPluginMessage(PluginMessage event)
    {
        if (disposed || !"shortestpath".equals(event.getNamespace())) return;
        String action = event.getName();
        if (!"path".equals(action) && !"clear".equals(action) && !"transports".equals(action)) return;
        if (!onClientThread.getAsBoolean() && ("path".equals(action) || "clear".equals(action)))
            paths.forgetOwnership(); // Our requests are exclusively posted on the client thread.
        dispatch(() -> paths.observe(event));
    }

    @Subscribe(priority = 1.0f)
    public void onMenuOptionClicked(MenuOptionClicked event)
    {
        if (disposed || !ShortestPathBridge.isManualPathInteraction(event)) return;
        if (!onClientThread.getAsBoolean()) paths.forgetOwnership();
        dispatch(() -> paths.observe(event));
    }

    @Subscribe(priority = 1.0f)
    public void onPluginChanged(PluginChanged event)
    {
        if (disposed || event.getPlugin() == null || !"Shortest Path".equals(event.getPlugin().getName())) return;
        if (!onClientThread.getAsBoolean()) paths.forgetOwnership();
        dispatch(() -> paths.observe(event));
    }

    private void dispatch(Runnable observation)
    {
        Runnable update = () -> {
            if (disposed) return;
            observation.run(); navigationChanged.run();
        };
        if (onClientThread.getAsBoolean()) update.run(); else enqueue.accept(update);
    }

    /** Call on the client thread, then unregister this observer from EventBus. */
    public void close() { disposed = true; paths.forgetOwnership(); }
}
