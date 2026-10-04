package com.caroute.application;

import com.caroute.CaRouteConfig;
import com.google.gson.Gson;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import net.runelite.client.util.Filepath;
import org.junit.Test;
import static org.junit.Assert.*;

public class AccountFileCoordinatorTest
{
    @Test public void restartWaitsForAdmittedAccountSnapshotsBeforeLoading() throws Exception
    {
        Path directory = Files.createTempDirectory("caroute-file-order");
        AccountStateStore store = localStore(directory);
        CaRouteConfig config = new CaRouteConfig() {};
        QueuedExecutor oldWorker = new QueuedExecutor(), newWorker = new QueuedExecutor(), client = new QueuedExecutor();
        AccountFileCoordinator oldFiles = new AccountFileCoordinator(null, oldWorker);
        AccountState state = AccountState.defaults(config);
        state.pins.add(1);
        oldFiles.execute(store.saveTask("123", state));
        state.pins.clear(); state.pins.add(2);
        oldFiles.execute(store.saveTask("123", state));
        state.pins.clear(); state.pins.add(3);
        oldFiles.close();

        AccountFileCoordinator newFiles = new AccountFileCoordinator(oldFiles, newWorker);
        AtomicReference<AccountState> loaded = new AtomicReference<>();
        newFiles.load(store, "123", AccountState.defaults(config), Set.of(1, 2, 3), client, () -> true,
            (account, failure) -> { assertNull(failure); loaded.set(account); });
        assertTrue(newWorker.tasks.isEmpty());
        assertFalse(oldWorker.isShutdown());
        oldWorker.next();
        assertEquals(Set.of(1), store.load("123", config, Set.of(1, 2, 3)).pins);
        assertTrue(newWorker.tasks.isEmpty());
        oldWorker.next();
        assertTrue(oldWorker.isShutdown());
        newWorker.next();
        assertNull(loaded.get());
        client.next();
        assertEquals(Set.of(2), loaded.get().pins);
        newFiles.close();
        assertTrue(newWorker.isShutdown());
        try { newFiles.execute(() -> fail("Closed queue accepted a write")); fail("Closed queue must reject writes"); }
        catch (RejectedExecutionException expected) { }
        store.reset("123"); Files.delete(directory);
    }

    @Test public void staleLoadIsRejectedBeforeReadingAndBeforeClientPublication() throws Exception
    {
        Path directory = Files.createTempDirectory("caroute-load-guard");
        AtomicInteger directoryReads = new AtomicInteger();
        AccountStateStore store = new AccountStateStore(() -> {
            directoryReads.incrementAndGet(); return Filepath.Unchecked.getRooted(directory);
        }, new Gson());
        QueuedExecutor worker = new QueuedExecutor(), client = new QueuedExecutor();
        AccountFileCoordinator files = new AccountFileCoordinator(null, worker);
        AtomicLong lifecycle = new AtomicLong(1);
        AtomicReference<AccountState> published = new AtomicReference<>();
        AccountState defaults = AccountState.defaults(new CaRouteConfig() {
            @Override public Target target() { return Target.HARD; }
        });
        files.load(store, "123", defaults, Set.of(), client, () -> lifecycle.get() == 1,
            (state, failure) -> published.set(state));
        lifecycle.set(2); worker.next();
        assertEquals(0, directoryReads.get());
        assertTrue(client.tasks.isEmpty());

        files.load(store, "123", defaults, Set.of(), client, () -> lifecycle.get() == 2,
            (state, failure) -> published.set(state));
        worker.next(); lifecycle.set(3); client.next();
        assertNull(published.get());

        files.load(store, "123", defaults, Set.of(), client, () -> lifecycle.get() == 3,
            (state, failure) -> { assertNull(failure); published.set(state); });
        worker.next(); client.next();
        assertEquals(CaRouteConfig.Target.HARD, published.get().target);
        files.close(); Files.delete(directory);
    }

    @Test public void unreadableStateReturnsTemporaryDefaultsWithoutReplacingTheOriginal() throws Exception
    {
        Path directory = Files.createTempDirectory("caroute-load-failure");
        Path file = directory.resolve("account.123.json");
        String original = "{broken account state";
        Files.writeString(file, original);
        AccountStateStore store = localStore(directory);
        AccountState defaults = AccountState.defaults(new CaRouteConfig() {});
        QueuedExecutor worker = new QueuedExecutor(), client = new QueuedExecutor();
        AccountFileCoordinator files = new AccountFileCoordinator(null, worker);
        AtomicReference<RuntimeException> failure = new AtomicReference<>();
        files.load(store, "123", defaults, Set.of(), client, () -> true, (loaded, ex) -> {
            assertSame(defaults, loaded); failure.set(ex);
        });
        worker.next(); client.next();
        assertNotNull(failure.get());
        assertEquals(original, Files.readString(file));
        files.close(); Files.delete(file); Files.delete(directory);
    }

    private static AccountStateStore localStore(Path directory)
    { return new AccountStateStore(Filepath.Unchecked.getRooted(directory), new Gson()); }

    private static final class QueuedExecutor extends AbstractExecutorService
    {
        private final Queue<Runnable> tasks = new ArrayDeque<>();
        private boolean shutdown;
        void next() { tasks.remove().run(); }
        @Override public void execute(Runnable task)
        { if (shutdown) throw new RejectedExecutionException(); tasks.add(task); }
        @Override public void shutdown() { shutdown = true; }
        @Override public List<Runnable> shutdownNow()
        { shutdown = true; List<Runnable> queued = new ArrayList<>(tasks); tasks.clear(); return queued; }
        @Override public boolean isShutdown() { return shutdown; }
        @Override public boolean isTerminated() { return shutdown && tasks.isEmpty(); }
        @Override public boolean awaitTermination(long timeout, TimeUnit unit) { return isTerminated(); }
    }
}
