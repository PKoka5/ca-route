package com.caroute.application;

import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Rechecks a local export snapshot before computation and before presenting a save dialog. */
public final class RouteExportCoordinator
{
    public static final class Request
    {
        private final long lifecycle, profileVersion;
        private final String account;
        public Request(long lifecycle, String account, long profileVersion)
        {
            this.lifecycle = lifecycle; this.account = Objects.requireNonNull(account); this.profileVersion = profileVersion;
            if (account.isEmpty()) throw new IllegalArgumentException("An export needs an account snapshot");
        }
        public boolean current(boolean active, long lifecycle, String account, long profileVersion)
        { return active && this.lifecycle == lifecycle && this.account.equals(account) && this.profileVersion == profileVersion; }
    }
    private final Executor worker, presentation;
    public RouteExportCoordinator(Executor worker, Executor presentation)
    { this.worker = Objects.requireNonNull(worker); this.presentation = Objects.requireNonNull(presentation); }

    public void submit(BooleanSupplier current, Supplier<TasksTrackerRouteExporter.ExportResult> export,
        Consumer<TasksTrackerRouteExporter.ExportResult> result, Consumer<RuntimeException> failure)
    {
        Objects.requireNonNull(current); Objects.requireNonNull(export); Objects.requireNonNull(result); Objects.requireNonNull(failure);
        if (!current.getAsBoolean()) return;
        try
        {
            worker.execute(() -> {
                if (!current.getAsBoolean()) return;
                try
                {
                    TasksTrackerRouteExporter.ExportResult produced = export.get();
                    presentation.execute(() -> { if (current.getAsBoolean()) result.accept(produced); });
                }
                catch (RuntimeException ex)
                { presentation.execute(() -> { if (current.getAsBoolean()) failure.accept(ex); }); }
            });
        }
        catch (RuntimeException ex)
        { presentation.execute(() -> { if (current.getAsBoolean()) failure.accept(ex); }); }
    }
}
