package com.caroute.application;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;

/** Serializes local file work, including across a rapid plugin disable/re-enable. */
public final class AccountFileCoordinator implements Executor, AutoCloseable
{
    private final ExecutorService worker;
    private final CompletableFuture<Void> drained = new CompletableFuture<>();
    private CompletableFuture<Void> tail;
    private boolean closed;

    public AccountFileCoordinator(AccountFileCoordinator previous, ExecutorService worker)
    {
        this.worker = Objects.requireNonNull(worker);
        tail = previous == null ? CompletableFuture.completedFuture(null) : previous.drained;
    }

    @Override public synchronized void execute(Runnable command)
    {
        if (closed) throw new RejectedExecutionException("Account file queue is closed");
        tail = tail.handle((ignored, failure) -> null).thenRunAsync(command, worker);
    }

    public void load(AccountStateStore store, String account, AccountState defaults, Set<Integer> ids,
        Executor presentation, BooleanSupplier current, BiConsumer<AccountState, RuntimeException> result)
    {
        Set<Integer> capturedIds = Set.copyOf(ids);
        execute(() -> {
            if (!current.getAsBoolean()) return;
            AccountState loaded = defaults;
            RuntimeException failure = null;
            try { loaded = store.load(account, defaults, capturedIds); }
            catch (RuntimeException ex) { failure = ex; }
            AccountState loadedState = loaded;
            RuntimeException loadFailure = failure;
            presentation.execute(() -> {
                if (current.getAsBoolean()) result.accept(loadedState, loadFailure);
            });
        });
    }

    /** Finishes admitted snapshots without waiting on the client or plugin thread. */
    @Override public synchronized void close()
    {
        if (closed) return;
        closed = true;
        tail.whenComplete((ignored, failure) -> {
            worker.shutdown();
            drained.complete(null);
        });
    }
}
