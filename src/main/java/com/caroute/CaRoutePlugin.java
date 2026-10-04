package com.caroute;

import com.caroute.application.*;
import com.caroute.bridge.*;
import com.caroute.data.AchievementRepository;
import com.caroute.domain.*;
import com.caroute.engine.RouteEngine;
import com.caroute.engine.PreparationAdvisor;
import com.caroute.ui.CaRoutePanel;
import com.caroute.ui.GearView;
import com.caroute.ui.AccountView;
import com.caroute.ui.ProfileActions;
import com.caroute.ui.RouteExportDialog;
import com.caroute.ui.CombatAchievementIcon;
import com.caroute.ui.GuideActions;
import com.caroute.ui.GuideView;
import com.caroute.navigation.*;
import com.google.gson.Gson;
import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import net.runelite.api.*;
import net.runelite.api.events.*;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.worldmap.WorldMapPointManager;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@PluginDescriptor(name = "CA Route", internalName = "ca-route", legacyDataDirectory = "caroute",
    description = "Local account-aware Combat Achievement progression routes", tags = {"combat", "achievements", "route", "progression"})
public class CaRoutePlugin extends Plugin
{
    private static final Logger LOG = LoggerFactory.getLogger(CaRoutePlugin.class);
    @Inject private Client client;
    @Inject private ClientThread clientThread;
    @Inject private ClientToolbar toolbar;
    @Inject private ConfigManager configManager;
    @Inject private Gson gson;
    @Inject private CaRouteConfig config;
    @Inject private EventBus eventBus;
    @Inject private PluginManager pluginManager;
    @Inject private OverlayManager overlayManager;
    @Inject private WorldMapPointManager worldMapPoints;
    private volatile ShortestPathBridge shortestPath;
    private GuideController guide;
    private GuideEventObserver guideEvents;
    private GuideInfoOverlay guideInfoOverlay;
    private GuideSceneOverlay guideSceneOverlay;
    private GuideMinimapOverlay guideMinimapOverlay;
    private final AtomicLong guidePresentation = new AtomicLong();
    private AchievementRepository repository;
    private RuneLiteProfileReader reader;
    private LocalKillCountReader kcReader;
    private AccountStateStore store;
    private volatile AccountState state;
    private volatile boolean accountWritable;
    private final Set<String> pendingConfigChanges = new LinkedHashSet<>();
    private CaRoutePanel panel;
    private NavigationButton navigation;
    private ExecutorService worker;
    private AccountFileCoordinator accountIo;
    private AsyncRouteCoordinator coordinator;
    private RouteExportCoordinator exportCoordinator;
    private final AtomicLong lifecycle = new AtomicLong();
    private volatile boolean active, dirty;
    private volatile String account = "";
    private long version;
    private List<String> profileDiagnostics = Collections.emptyList();
    private List<Object> lastSnapshot;
    private volatile RoutingPreferences displayedPreferences = RoutingPreferences.builder().build();
    private volatile GearView displayedGear = GearView.unknown();
    private volatile AccountView displayedAccount = AccountView.of(GearView.unknown());
    private volatile String visibleAccount = "";

    @Provides CaRouteConfig provideConfig(ConfigManager manager) { return manager.getConfig(CaRouteConfig.class); }

    @Override protected void startUp()
    {
        lifecycle.incrementAndGet();
        repository = AchievementRepository.loadBundled(); reader = new RuneLiteProfileReader();
        shortestPath = new ShortestPathBridge(eventBus, pluginManager);
        ShortestPathBridge guidePaths = shortestPath;
        guide = new GuideController(client, worldMapPoints, shortestPath, config,
            view -> { if (shortestPath == guidePaths) presentGuide(view); });
        guideEvents = new GuideEventObserver(client, clientThread, shortestPath, guide); eventBus.register(guideEvents);
        guideInfoOverlay = new GuideInfoOverlay(this, client, guide::current, config);
        guideSceneOverlay = new GuideSceneOverlay(client, guide::current, config);
        guideMinimapOverlay = new GuideMinimapOverlay(client, guide::current, config);
        overlayManager.add(guideInfoOverlay); overlayManager.add(guideSceneOverlay); overlayManager.add(guideMinimapOverlay);
        store = new AccountStateStore(this::getPluginDirectory, gson);
        accountIo = new AccountFileCoordinator(accountIo,
            Executors.newSingleThreadExecutor(r -> { Thread t = new Thread(r, "ca-route-files"); t.setDaemon(true); return t; }));
        Set<String> activities = new TreeSet<>(); repository.tasks().forEach(t -> activities.add(t.activity())); kcReader = new LocalKillCountReader(activities);
        panel = new CaRoutePanel(this::action, this::restoreAll, this::skipStop, this::setTarget, this::setPreference,
            this::setGearChoice, this::setBankChoice, this::resetGearChoices, new ProfileActions() {
                @Override public void objective(RouteObjective objective) { mutateAccount(state -> state.objective = objective); }
                @Override public void selectedActivity(String activity) { mutateAccount(state -> state.selectedActivity = activity); }
                @Override public void timeBudget(int minutes) { mutateAccount(state -> state.timeBudgetMinutes = Math.max(1, Math.min(1440, minutes))); }
                @Override public void currentActivity(String activity) { mutateAccount(state -> state.currentActivity = activity); }
                @Override public void killCount(String activity, Integer count) { mutateAccount(state -> { if (count == null) state.killCountOverrides.remove(activity); else if (count >= 0) state.killCountOverrides.put(activity, count); }); }
                @Override public void preparationEstimate(String capability, Double minutes) { mutateAccount(state -> { if (minutes == null) state.preparationEstimates.remove(capability); else if (Double.isFinite(minutes) && minutes >= 0 && minutes <= 100000) state.preparationEstimates.put(capability, minutes); }); }
                @Override public void exportRoute() { CaRoutePlugin.this.exportRoute(); }
            }, new GuideActions() {
                @Override public void startTask(int taskId) { selectTask(taskId); }
                @Override public void startActivity(String activity) { selectActivity(activity); }
                @Override public void stop() { guideCommand(GuideController::stop); }
                @Override public void focusMap() { guideCommand(GuideController::focusMap); }
            });
        worker = Executors.newSingleThreadExecutor(r -> { Thread t = new Thread(r, "ca-route-planner"); t.setDaemon(true); return t; });
        exportCoordinator = new RouteExportCoordinator(worker, SwingUtilities::invokeLater);
        RouteEngine engine = new RouteEngine();
        PreparationAdvisor adviser = new PreparationAdvisor();
        coordinator = new AsyncRouteCoordinator(worker, SwingUtilities::invokeLater,
            (profile, preferences) -> { RoutePlan plan = engine.plan(repository.tasks(), profile, preferences); return plan.withPreparationSteps(adviser.advise(plan.assessments(), profile)); },
            plan -> { AccountView view = displayedAccount; if (view.profile() == null || view.profile().version() != plan.profileVersion()) return;
                visibleAccount = view.profile().accountKey(); panel.display(plan, profileDiagnostics, displayedPreferences, view, guide.view()); },
            ex -> { LOG.warn("CA Route calculation failed", ex); panel.showMessage("Route calculation failed. Check client logs."); });
        navigation = NavigationButton.builder().tooltip("CA Route").icon(icon()).priority(6).panel(panel).build();
        toolbar.addNavigation(navigation); active = true;
        clientThread.invokeLater(() -> { if (active) { dirty = true; refresh(); } });
    }
    @Override protected void shutDown()
    {
        lifecycle.incrementAndGet();
        active = false; guidePresentation.incrementAndGet();
        GuideController oldGuide = guide;
        GuideEventObserver oldEvents = guideEvents;
        if (oldGuide != null) clientThread.invokeLater(() -> {
            try { oldGuide.stop(); }
            finally { if (oldEvents != null) { oldEvents.close(); eventBus.unregister(oldEvents); } }
        });
        if (guideInfoOverlay != null) overlayManager.remove(guideInfoOverlay);
        if (guideSceneOverlay != null) overlayManager.remove(guideSceneOverlay);
        if (guideMinimapOverlay != null) overlayManager.remove(guideMinimapOverlay);
        if (coordinator != null) coordinator.close(); if (worker != null) worker.shutdownNow();
        // Complete admitted account snapshots without blocking client/plugin shutdown.
        if (accountIo != null) accountIo.close();
        if (navigation != null) toolbar.removeNavigation(navigation); account = ""; visibleAccount = ""; state = null; accountWritable = false; if (reader != null) reader.clearSession();
    }
    @Subscribe public void onGameTick(GameTick event)
    {
        if (active && guide != null) guide.tick();
        long now = System.currentTimeMillis();
        if (state != null && state.snoozedUntil.entrySet().removeIf(entry -> entry.getValue() <= now)) { saveAccount(); dirty = true; }
        if (dirty || (reader != null && reader.catalogueRetryDue(now))) { dirty = false; refresh(); }
    }
    @Subscribe public void onVarbitChanged(VarbitChanged event) { dirty = true; }
    @Subscribe public void onStatChanged(StatChanged event) { dirty = true; }
    @Subscribe public void onItemContainerChanged(ItemContainerChanged event) { dirty = true; }
    @Subscribe public void onWidgetLoaded(WidgetLoaded event) { dirty = true; }
    @Subscribe public void onGameStateChanged(GameStateChanged event)
    {
        if (event.getGameState() == GameState.LOGGED_IN) { dirty = true; refresh(); }
        else if (event.getGameState() == GameState.LOGIN_SCREEN || event.getGameState() == GameState.HOPPING)
        { lifecycle.incrementAndGet(); guidePresentation.incrementAndGet(); if (guide != null) guide.stop(); if (coordinator != null) coordinator.invalidate(); if (reader != null) reader.clearSession(); account = ""; visibleAccount = ""; state = null; accountWritable = false; pendingConfigChanges.clear(); lastSnapshot = null; displayedGear = GearView.unknown(); displayedAccount = AccountView.of(displayedGear); SwingUtilities.invokeLater(() -> { if (panel != null) panel.showMessage("Log in to calculate your CA route."); }); }
    }
    @Subscribe public void onChatMessage(ChatMessage event)
    {
        if (event.getType() != ChatMessageType.GAMEMESSAGE || state == null || !active) return;
        Map<String, Integer> previous = new HashMap<>(state.killCounts);
        if (kcReader.accept(event.getMessage(), state.killCounts)) { state.killCountOverrides.keySet().removeIf(activity -> !Objects.equals(previous.get(activity), state.killCounts.get(activity))); saveAccount(); dirty = true; }
    }
    @Subscribe public void onConfigChanged(ConfigChanged event)
    {
        if (!CaRouteConfig.GROUP.equals(event.getGroup()) || event.getKey().startsWith("account.")) return;
        clientThread.invokeLater(() -> {
            if (!active) return;
            if (active && guide != null && (event.getKey().startsWith("guide") || event.getKey().equals("useShortestPath")))
            { guide.configure(); return; }
            if (state != null) { state.applyConfigChange(event.getKey(), config); saveAccount(); dirty = true; }
            else if (!account.isEmpty()) pendingConfigChanges.add(event.getKey());
        });
    }
    private void refresh()
    {
        if (!active || client.getGameState() != GameState.LOGGED_IN || client.getLocalPlayer() == null) return;
        String current = Long.toUnsignedString(client.getAccountHash());
        if (current.equals("0")) return;
        if (!current.equals(account))
        {
            lifecycle.incrementAndGet();
            guidePresentation.incrementAndGet(); if (guide != null) guide.stop();
            coordinator.invalidate(); reader.clearSession(); visibleAccount = ""; account = current; state = null; accountWritable = false; pendingConfigChanges.clear(); lastSnapshot = null;
            Set<Integer> ids = new HashSet<>(); repository.tasks().forEach(t -> ids.add(t.id()));
            long expectedLifecycle = lifecycle.get();
            AccountStateStore accountStore = store;
            AccountState defaults = AccountState.defaults(config);
            CaRoutePanel currentPanel = panel;
            SwingUtilities.invokeLater(() -> {
                if (active && expectedLifecycle == lifecycle.get() && current.equals(account)) currentPanel.showMessage("Reading account state…");
            });
            try {
                accountIo.load(accountStore, current, defaults, ids, clientThread::invokeLater,
                    () -> active && expectedLifecycle == lifecycle.get() && current.equals(account), (loaded, failure) -> {
                        if (failure != null) LOG.warn("Invalid CA Route account settings; preserving the file and using temporary defaults", failure);
                        boolean configChanged = !pendingConfigChanges.isEmpty();
                        pendingConfigChanges.forEach(key -> loaded.applyConfigChange(key, config));
                        pendingConfigChanges.clear();
                        state = loaded; accountWritable = failure == null;
                        if (configChanged) saveAccount();
                        dirty = true; refresh();
                    });
            } catch (RejectedExecutionException ex) { if (active) LOG.warn("CA Route account loading was rejected", ex); }
        }
        if (state == null) return;
        PlayerProfile profile = reader.read(client, repository, state, account, ++version);
        guide.refresh(profile);
        RoutingPreferences preferences = state.routingPreferences(System.currentTimeMillis());
        List<Object> snapshot = Arrays.asList(account, profile.completedTaskIds(), profile.currentPoints(), profile.skills(), profile.quests(),
            profile.killCounts(), profile.thresholds(), profile.accountType(), profile.loaded(), profile.gear().capabilities(), profile.gear().equipped(),
            profile.gear().owned(), profile.gear().knownMissing(), profile.gear().ownershipComplete(), profile.catalogueComplete(),
            profile.gear().activePreparationEstimates(), new TreeMap<>(state.killCountOverrides),
            new TreeMap<>(state.gearOverrides), state.observeBank, reader.bankObserved(), reader.bankItemCount(),
            preferences.target(), preferences.objective(), preferences.selectedActivity(), preferences.timeBudgetMinutes(), preferences.currentActivity(), preferences.preferences(), preferences.excludedTaskIds(), preferences.pinnedTaskIds(),
            preferences.deferredTaskIds(), preferences.allowStretch(), reader.diagnostics());
        if (snapshot.equals(lastSnapshot)) return;
        lastSnapshot = snapshot; profileDiagnostics = new ArrayList<>(reader.diagnostics()); displayedPreferences = preferences;
        if (!accountWritable) profileDiagnostics.add("Local account settings could not be read. Changes will not be saved until the settings can be loaded again.");
        displayedGear = new GearView(profile.gear(), state.gearOverrides, state.observeBank,
            reader.bankObserved(), reader.bankItemCount(), reader.bankObservedAt());
        displayedAccount = new AccountView(displayedGear, profile, state.killCountOverrides, !reader.verifiedStructIds().isEmpty());
        coordinator.request(profile, preferences);
    }
    private void action(int id, TaskAction action)
    { mutateAccount(state -> state.act(id, action, System.currentTimeMillis())); }
    private void selectTask(int id)
    {
        guideCommand(controller -> repository.tasks().stream().filter(task -> task.id() == id).findFirst()
            .ifPresent(task -> controller.startTask(task, displayedAccount.profile())));
    }
    private void selectActivity(String activity)
    {
        guideCommand(controller -> {
            PlayerProfile profile = displayedAccount.profile();
            if (profile != null && repository.tasks().stream().anyMatch(task -> task.activity().equals(activity)
                && !profile.completedTaskIds().contains(task.id()))) controller.startActivity(activity, profile);
        });
    }
    private void guideCommand(java.util.function.Consumer<GuideController> command)
    {
        String expected = visibleAccount; long expectedLifecycle = lifecycle.get();
        clientThread.invokeLater(() -> {
            PlayerProfile profile = displayedAccount.profile();
            if (!active || guide == null || expected.isEmpty() || expectedLifecycle != lifecycle.get()
                || !expected.equals(account) || profile == null || !expected.equals(profile.accountKey())
                || client.getGameState() != GameState.LOGGED_IN) return;
            command.accept(guide);
        });
    }
    private void presentGuide(GuideView view)
    {
        long expectedLifecycle = lifecycle.get(), ticket = guidePresentation.incrementAndGet();
        String expectedAccount = account;
        SwingUtilities.invokeLater(() -> {
            if (active && panel != null && expectedLifecycle == lifecycle.get() && ticket == guidePresentation.get()
                && !expectedAccount.isEmpty() && expectedAccount.equals(account) && expectedAccount.equals(visibleAccount))
                panel.displayGuide(view);
        });
    }
    private void restoreAll()
    {
        mutateAccount(state -> { state.pins.clear(); state.exclusions.clear(); state.snoozedUntil.clear(); state.skipped.clear(); });
    }
    private void skipStop(Set<Integer> ids) { mutateAccount(state -> state.skipped.addAll(ids)); }
    private void setTarget(CaRouteConfig.Target target) { mutateAccount(state -> state.target = target); }
    private void setPreference(TaskFlag flag, Preference preference) { mutateAccount(state -> state.preferences.put(flag, preference)); }
    private void setGearChoice(String capability, Boolean owned)
    {
        mutateAccount(state -> state.setGearOverride(capability, owned));
    }
    private void setBankChoice(boolean enabled)
    {
        mutateAccount(state -> state.observeBank = enabled);
    }
    private void resetGearChoices()
    {
        mutateAccount(state -> { state.gearOverrides.clear(); state.manualGear.clear(); state.preparationEstimates.clear(); });
    }
    private void mutateAccount(java.util.function.Consumer<AccountState> change)
    {
        String expected = visibleAccount; long expectedLifecycle = lifecycle.get();
        clientThread.invokeLater(() -> { if (!active || state == null || expected.isEmpty() || expectedLifecycle != lifecycle.get() || !expected.equals(account)) return;
            change.accept(state); saveAccount(); dirty = true; });
    }
    private void saveAccount()
    {
        AccountState savingState = state;
        String savingAccount = account;
        if (!active || !accountWritable || savingState == null || savingAccount.isEmpty()) return;
        Runnable save = store.saveTask(savingAccount, savingState);
        try {
            accountIo.execute(() -> {
                try { save.run(); }
                catch (RuntimeException ex) { LOG.warn("CA Route account settings could not be saved", ex); }
            });
        } catch (RejectedExecutionException ex) { if (active) LOG.warn("CA Route account saving was rejected", ex); }
    }
    private void exportRoute()
    {
        RoutePlan plan = panel.currentPlan(); AccountView view = displayedAccount; String expected = visibleAccount;
        if (!active || expected.isEmpty()) return;
        if (plan == null || view.profile() == null || plan.profileVersion() != view.profile().version()) { RouteExportDialog.error(panel, "Wait for the current route to finish updating."); return; }
        RouteExportCoordinator.Request request = new RouteExportCoordinator.Request(lifecycle.get(), expected, plan.profileVersion());
        clientThread.invokeLater(() -> {
            if (!exportCurrent(request)) return;
            Map<Integer, Integer> mappings = new TreeMap<>(reader.verifiedStructIds());
            exportCoordinator.submit(() -> exportCurrent(request), () -> {
                List<TaskAssessment> selected = new ArrayList<>(); plan.stops().forEach(stop -> selected.addAll(stop.tasks()));
                return new TasksTrackerRouteExporter(gson).export(plan, mappings, new PreparationAdvisor().advise(selected, view.profile()));
            }, result -> {
                // The coordinator invokes this on the EDT; never inspect Swing state on the client/worker thread.
                if (panel.currentPlan() != plan || !exportCurrent(request)) return;
                if (result.success()) RouteExportDialog.save(panel, result.json(), accountIo, () -> exportCurrent(request)); else RouteExportDialog.error(panel, result.error());
            }, failure -> {
                if (panel.currentPlan() != plan || !exportCurrent(request)) return;
                LOG.warn("CA Route export failed", failure); RouteExportDialog.error(panel, "Could not export the current route. Check client logs.");
            });
        });
    }
    private boolean exportCurrent(RouteExportCoordinator.Request request)
    {
        AccountView view = displayedAccount;
        return view.profile() != null && request.current(active, lifecycle.get(), account, view.profile().version())
            && account.equals(view.profile().accountKey()) && account.equals(visibleAccount);
    }
    private static BufferedImage icon()
    {
        return CombatAchievementIcon.create();
    }
}
