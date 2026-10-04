package com.caroute.application;

import com.caroute.domain.PlayerProfile;
import com.caroute.domain.RoutePlan;
import com.caroute.domain.RoutingPreferences;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.BiFunction;
import java.util.function.Consumer;

/** Owns asynchronous invalidation; it deliberately knows nothing about the game or Swing. */
public final class AsyncRouteCoordinator implements AutoCloseable
{
    private final Executor worker;
    private final Executor presentation;
    private final BiFunction<PlayerProfile, RoutingPreferences, RoutePlan> planner;
    private final Consumer<RoutePlan> result;
    private final Consumer<RuntimeException> failure;
    private long generation;
    private String account = "";
    private long profileVersion = -1;
    private boolean closed;

    public AsyncRouteCoordinator(Executor worker, Executor presentation,
        BiFunction<PlayerProfile, RoutingPreferences, RoutePlan> planner,
        Consumer<RoutePlan> result, Consumer<RuntimeException> failure)
    {
        this.worker = Objects.requireNonNull(worker);
        this.presentation = Objects.requireNonNull(presentation);
        this.planner = Objects.requireNonNull(planner);
        this.result = Objects.requireNonNull(result);
        this.failure = Objects.requireNonNull(failure);
    }

    public synchronized void request(PlayerProfile profile, RoutingPreferences preferences)
    {
        if (closed) { return; }
        final long ticket = ++generation;
        final String expectedAccount = profile.accountKey();
        final long expectedVersion = profile.version();
        account = expectedAccount;
        profileVersion = expectedVersion;
        worker.execute(() -> {
            // A queued stale computation can be discarded before it starts.
            if (!current(ticket, expectedAccount, expectedVersion)) { return; }
            try
            {
                RoutePlan plan = planner.apply(profile, preferences);
                presentation.execute(() -> {
                    synchronized (AsyncRouteCoordinator.this)
                    {
                        if (current(ticket, expectedAccount, expectedVersion)
                            && plan.profileVersion() == expectedVersion)
                        {
                            result.accept(plan);
                        }
                    }
                });
            }
            catch (RuntimeException ex)
            {
                presentation.execute(() -> {
                    synchronized (AsyncRouteCoordinator.this)
                    {
                        if (current(ticket, expectedAccount, expectedVersion)) { failure.accept(ex); }
                    }
                });
            }
        });
    }

    private synchronized boolean current(long ticket, String expectedAccount, long expectedVersion)
    {
        return !closed && generation == ticket && profileVersion == expectedVersion
            && Objects.equals(account, expectedAccount);
    }

    public synchronized void invalidate()
    {
        generation++;
        account = "";
        profileVersion = -1;
    }

    @Override public synchronized void close()
    {
        invalidate();
        closed = true;
    }
}
