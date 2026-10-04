package com.caroute.application;

import com.caroute.CaRouteConfig;
import com.google.gson.Gson;
import java.util.Set;
import java.util.Objects;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.StandardCopyOption;
import net.runelite.client.util.Filepath;

/** Local RuneLite config only. No player name or account state is sent to another service. */
public final class AccountStateStore
{
    @FunctionalInterface
    public interface DirectoryProvider
    {
        Filepath get() throws IOException;
    }

    private final DirectoryProvider directoryProvider;
    private Filepath directory;
    private final Gson gson;

    public AccountStateStore(Filepath directory, Gson gson)
    { this(() -> directory, gson); }
    /** Resolves and migrates the plugin directory only when a worker first reads or writes. */
    public AccountStateStore(DirectoryProvider directoryProvider, Gson gson)
    {
        this.directoryProvider = Objects.requireNonNull(directoryProvider);
        this.gson = Objects.requireNonNull(gson);
    }

    public AccountState load(String accountKey, CaRouteConfig config, Set<Integer> ids)
    { return load(accountKey, AccountState.defaults(config), ids); }

    public AccountState load(String accountKey, AccountState defaults, Set<Integer> ids)
    {
        AccountState state;
        try {
            Filepath file = path(accountKey);
            if (file.exists()) {
                try (Reader reader = file.openBufferedReader()) { state = gson.fromJson(reader, AccountState.class); }
            } else { state = defaults; }
        }
        catch (IOException ex) { throw new IllegalStateException("Cannot read local CA Route settings", ex); }
        if (state == null) { throw new IllegalArgumentException("Account settings are empty"); }
        state.sanitize(ids, System.currentTimeMillis());
        return state;
    }

    public void save(String accountKey, AccountState state)
    { saveTask(accountKey, state).run(); }

    /** Captures mutable client-thread state before its file write is queued on a worker. */
    public Runnable saveTask(String accountKey, AccountState state)
    {
        String fileName = key(accountKey) + ".json";
        String serialized = gson.toJson(state);
        return () -> write(fileName, serialized);
    }

    private void write(String fileName, String serialized)
    {
        try {
            Filepath localDirectory = directory();
            localDirectory.createDirectories();
            Filepath file = localDirectory.joinSegment(fileName), temporary = localDirectory.createTempFile("account-", ".tmp");
            try {
                temporary.write(serialized);
                try { temporary.moveTo(file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
                catch (AtomicMoveNotSupportedException ex) { temporary.moveTo(file, StandardCopyOption.REPLACE_EXISTING); }
            } finally { temporary.deleteIfExists(); }
        } catch (IOException ex) { throw new IllegalStateException("Cannot persist local CA Route settings", ex); }
    }

    public void reset(String accountKey)
    {
        try { path(accountKey).deleteIfExists(); } catch (IOException ex) { throw new IllegalStateException("Cannot reset local CA Route settings", ex); }
    }

    private Filepath path(String accountKey) throws IOException
    { return directory().joinSegment(key(accountKey) + ".json"); }

    private synchronized Filepath directory() throws IOException
    {
        if (directory == null) directory = Objects.requireNonNull(directoryProvider.get());
        return directory;
    }

    static String key(String accountKey)
    {
        if (accountKey == null || !accountKey.matches("[0-9]+")) { throw new IllegalArgumentException("Account identity is unavailable"); }
        return "account." + accountKey;
    }
}
