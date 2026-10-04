package com.caroute.ui;

import com.caroute.CaRouteConfig;
import com.caroute.application.TaskAction;
import com.caroute.data.GearCapabilityRepository;
import com.caroute.domain.*;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.function.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.PluginPanel;
import static com.caroute.ui.PanelStyles.*;

/** Compact presentation only; eligibility, completion and routing still come from the pure engine. */
public final class CaRoutePanel extends PluginPanel
{
    private final JTabbedPane tabs = new JTabbedPane();
    private final JPanel route = page(), bosses = page(), tasks = page(), profile = page(), prepare = page();
    private final BiConsumer<Integer, TaskAction> taskAction;
    private final Runnable restore, resetGear;
    private final Consumer<Set<Integer>> skip;
    private final Consumer<CaRouteConfig.Target> target;
    private final BiConsumer<TaskFlag, Preference> preference;
    private final BiConsumer<String, Boolean> gearChoice;
    private final Consumer<Boolean> bankChoice;
    private final ProfileActions profileActions;
    private final GuideActions guideActions;
    private final Consumer<String> wikiOpener;
    private final GearCapabilityRepository gearDefinitions = GearCapabilityRepository.loadBundled();
    private RoutePlan current;
    private long renderGeneration;
    private long guideGeneration;
    private final JPanel guideSlot = new GuideSlot();
    private GuideView guideView = GuideView.inactive();
    private DisclosureState disclosures = new DisclosureState();
    private String disclosureAccount = "";
    private TaskBrowserPanel taskBrowser;

    public CaRoutePanel(BiConsumer<Integer,TaskAction> taskAction, Runnable restore, Consumer<Set<Integer>> skip,
        Consumer<CaRouteConfig.Target> target, BiConsumer<TaskFlag,Preference> preference)
    { this(taskAction, restore, skip, target, preference, (id, owned) -> {}, enabled -> {}, () -> {}); }
    public CaRoutePanel(BiConsumer<Integer,TaskAction> taskAction, Runnable restore, Consumer<Set<Integer>> skip,
        Consumer<CaRouteConfig.Target> target, BiConsumer<TaskFlag,Preference> preference,
        BiConsumer<String,Boolean> gearChoice, Consumer<Boolean> bankChoice, Runnable resetGear)
    { this(taskAction, restore, skip, target, preference, gearChoice, bankChoice, resetGear, new ProfileActions(){}); }
    public CaRoutePanel(BiConsumer<Integer,TaskAction> taskAction, Runnable restore, Consumer<Set<Integer>> skip,
        Consumer<CaRouteConfig.Target> target, BiConsumer<TaskFlag,Preference> preference,
        BiConsumer<String,Boolean> gearChoice, Consumer<Boolean> bankChoice, Runnable resetGear, ProfileActions profileActions)
    { this(taskAction, restore, skip, target, preference, gearChoice, bankChoice, resetGear, profileActions, new GuideActions(){}); }
    public CaRoutePanel(BiConsumer<Integer,TaskAction> taskAction, Runnable restore, Consumer<Set<Integer>> skip,
        Consumer<CaRouteConfig.Target> target, BiConsumer<TaskFlag,Preference> preference,
        BiConsumer<String,Boolean> gearChoice, Consumer<Boolean> bankChoice, Runnable resetGear, ProfileActions profileActions,
        GuideActions guideActions)
    { this(taskAction, restore, skip, target, preference, gearChoice, bankChoice, resetGear, profileActions, guideActions, net.runelite.client.util.LinkBrowser::browse); }
    CaRoutePanel(BiConsumer<Integer,TaskAction> taskAction, Runnable restore, Consumer<Set<Integer>> skip,
        Consumer<CaRouteConfig.Target> target, BiConsumer<TaskFlag,Preference> preference,
        BiConsumer<String,Boolean> gearChoice, Consumer<Boolean> bankChoice, Runnable resetGear, ProfileActions profileActions,
        Consumer<String> wikiOpener)
    { this(taskAction, restore, skip, target, preference, gearChoice, bankChoice, resetGear, profileActions, new GuideActions(){}, wikiOpener); }
    CaRoutePanel(BiConsumer<Integer,TaskAction> taskAction, Runnable restore, Consumer<Set<Integer>> skip,
        Consumer<CaRouteConfig.Target> target, BiConsumer<TaskFlag,Preference> preference,
        BiConsumer<String,Boolean> gearChoice, Consumer<Boolean> bankChoice, Runnable resetGear, ProfileActions profileActions,
        GuideActions guideActions, Consumer<String> wikiOpener)
    {
        super(false); this.taskAction = taskAction; this.restore = restore; this.skip = skip; this.target = target;
        this.preference = preference; this.gearChoice = gearChoice; this.bankChoice = bankChoice; this.resetGear = resetGear; this.profileActions = profileActions;
        this.wikiOpener = Objects.requireNonNull(wikiOpener);
        this.guideActions = Objects.requireNonNull(guideActions);
        setLayout(new BorderLayout(0, 5)); setBackground(BACKGROUND); setBorder(new EmptyBorder(8, 8, 8, 8));
        JLabel name = new JLabel("CA Route"); name.setName("ca.title"); name.setForeground(TEXT); name.setFont(font(12, false));
        name.setBorder(new EmptyBorder(0, 2, 4, 0)); add(name, BorderLayout.NORTH);
        tabs.addTab("Tasks", scroll(tasks)); tabs.addTab("Bosses", scroll(bosses)); tabs.addTab("Route", scroll(route));
        tabs.addTab("Profile", scroll(profile)); tabs.addTab("Prep", scroll(prepare)); tabs.setToolTipTextAt(4, "Preparation and unlocks");
        tabs.setName("ca.tabs"); tabs.setFont(font(11, false));
        tabs.setBackground(BACKGROUND); tabs.setForeground(Color.WHITE); tabs.setUI(new CompactTabUI());
        tabs.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT); add(tabs, BorderLayout.CENTER); showMessage("Log in to calculate your CA route.");
    }
    private static JScrollPane scroll(JPanel page)
    {
        JScrollPane scroll = new JScrollPane(page); scroll.setBorder(null); scroll.setBackground(BACKGROUND); scroll.getViewport().setBackground(BACKGROUND);
        scroll.getVerticalScrollBar().setUnitIncrement(18); scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER); return scroll;
    }
    public void showMessage(String message)
    {
        renderGeneration++; guideGeneration++; current = null; guideView = GuideView.inactive(); guideSlot.removeAll(); disclosures = new DisclosureState(); disclosureAccount = ""; taskBrowser = null;
        for (JPanel page : pages()) { page.removeAll(); JPanel card = card(); heading(card, "CA Route"); text(card, message, MUTED, 12, false); put(page, card); page.revalidate(); page.repaint(); }
    }
    public void display(RoutePlan plan, List<String> diagnostics) { display(plan, diagnostics, RoutingPreferences.builder().build()); }
    public void display(RoutePlan plan, List<String> diagnostics, RoutingPreferences preferences) { display(plan, diagnostics, preferences, GearView.unknown()); }
    public void display(RoutePlan plan, List<String> diagnostics, RoutingPreferences preferences, GearView gear) { display(plan, diagnostics, preferences, AccountView.of(gear)); }
    public void display(RoutePlan plan, List<String> diagnostics, RoutingPreferences preferences, AccountView account)
    { display(plan, diagnostics, preferences, account, GuideView.inactive()); }
    public void display(RoutePlan plan, List<String> diagnostics, RoutingPreferences preferences, AccountView account, GuideView guide)
    {
        if (plan.reachability() == Reachability.UNKNOWN_PROFILE)
        {
            String mismatch = diagnostics.stream().filter(value -> value.startsWith("CA catalogue mismatch:") || value.startsWith("CA points mismatch:")).findFirst().orElse(null);
            showMessage(mismatch == null ? "Waiting for account data…" : mismatch);
            CollapsibleSection status = new CollapsibleSection("profile.data", "Account data", "Check", disclosures);
            diagnostics.stream().limit(20).forEach(value -> text(status.body(), value, MUTED, 11, false)); put(profile, status); profile.revalidate(); return;
        }
        String key = account.profile() == null ? "" : account.profile().accountKey();
        TaskBrowserPanel.Snapshot browserSnapshot = key.equals(disclosureAccount) && taskBrowser != null ? taskBrowser.snapshot() : null;
        if (!key.equals(disclosureAccount)) { disclosures = new DisclosureState(); disclosureAccount = key; }
        final DisclosureState state = disclosures; final long generation = ++renderGeneration;
        ProfileActions actions = guardedProfileActions(generation); current = plan; pages().forEach(JPanel::removeAll);
        guideView = Objects.requireNonNull(guide); guideGeneration++;
        renderRoute(plan, preferences, account, actions, generation, state);
        bosses.add(sectionHeading("Bosses"));
        for (BossSession session : plan.bossSessions())
        {
            CollapsibleSection section = new CollapsibleSection("boss." + session.activity(), session.activity(), "+" + session.points(), state);
            activityWiki(section, "boss." + session.activity(), session, generation);
            section.body().add(RouteWidgets.metrics(session.tasks().size(), session.points(), session.estimatedMinutes()));
            section.body().add(guideBoss("boss." + session.activity(), session.activity(), generation));
            section.body().add(Box.createVerticalStrut(4));
            for (TaskAssessment assessment : session.tasks()) task(section.body(), assessment, "boss.task." + assessment.task().id(), generation, state, account.profile());
            compactPut(bosses, section);
        }
        taskBrowser = new TaskBrowserPanel(plan.assessments(), (container, assessment) -> task(container, assessment,
            "tasks.task." + assessment.task().id(), generation, state, account.profile()), state, browserSnapshot);
        tasks.add(taskBrowser);
        prepare.add(new PreparationPanel(plan.preparationSteps(), this::readable, state));
        List<String> activities = new ArrayList<>(); plan.assessments().stream().map(a -> a.task().activity()).distinct().sorted().forEach(activities::add);
        profile.add(new ProfilePlanningPanel(preferences, account, activities, actions, state));
        renderPreferences(preferences, generation, state); renderGear(account.gear(), generation, actions, state);
        CollapsibleSection data = new CollapsibleSection("profile.data", "Account data & help", "", state);
        diagnostics.stream().limit(20).forEach(value -> text(data.body(), value, MUTED, 11, false));
        text(data.body(), "Times are estimates. Unknown ownership and history remain unknown.", MUTED, 11, false); compactPut(profile, data);
        pages().forEach(page -> { page.revalidate(); page.repaint(); });
    }
    private List<JPanel> pages() { return Arrays.asList(tasks, bosses, route, profile, prepare); }
    private static void compactPut(JPanel parent, JPanel child) { parent.add(child); parent.add(Box.createVerticalStrut(5)); }

    private void renderRoute(RoutePlan plan, RoutingPreferences preferences, AccountView account, ProfileActions actions, long generation, DisclosureState state)
    {
        JPanel choices = card();
        JComboBox<CaRouteConfig.Target> targetBox = new JComboBox<>(CaRouteConfig.Target.values()); targetBox.setName("route.target");
        targetBox.setSelectedItem(preferences.target() == null ? CaRouteConfig.Target.NEXT_TIER : CaRouteConfig.Target.valueOf(preferences.target().name()));
        targetBox.setToolTipText("Choose the target tier. Next tier advances automatically.");
        targetBox.addActionListener(event -> ifCurrent(generation, () -> target.accept((CaRouteConfig.Target) targetBox.getSelectedItem())));
        choices.add(formRow("Goal", targetBox)); choices.add(Box.createVerticalStrut(3));
        JComboBox<RouteObjective> ordering = new JComboBox<>(RouteObjective.values()); ordering.setName("route.objective");
        ordering.setSelectedItem(plan.objective()); ordering.setToolTipText(plan.objective().description());
        ordering.setRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected, boolean focus)
            { return super.getListCellRendererComponent(list, value instanceof RouteObjective ? shortMode((RouteObjective) value) : value, index, selected, focus); }
        });
        ordering.addActionListener(event -> actions.objective((RouteObjective) ordering.getSelectedItem()));
        choices.add(formRow("Ordering", ordering)); put(route, choices);
        route.add(guideSlot); renderGuide(generation);
        JPanel goal = card();
        String title = plan.objective() == RouteObjective.BOSS_COMPLETION ? (plan.selectedActivity().isEmpty() ? "Choose a boss" : "Complete " + plan.selectedActivity())
            : plan.objective() == RouteObjective.MAXIMUM_POINTS_PER_HOUR ? "Budget: " + time(plan.timeBudgetMinutes()) : "Goal: " + plan.target().displayName();
        goal.add(sectionHeading(title));
        String tier = plan.objective() == RouteObjective.BOSS_COMPLETION || plan.objective() == RouteObjective.MAXIMUM_POINTS_PER_HOUR ? plan.target().displayName() + ": " : "";
        text(goal, tier + plan.currentPoints() + " / " + plan.threshold() + " pts · " + plan.pointsRemaining() + " to go", MUTED, 11, false);
        JProgressBar progress = new JProgressBar(0, Math.max(1, plan.threshold())); progress.setValue(Math.min(plan.currentPoints(), plan.threshold()));
        progress(progress); progress.setAlignmentX(Component.LEFT_ALIGNMENT);
        progress.setMaximumSize(new Dimension(Integer.MAX_VALUE, 7)); progress.setPreferredSize(new Dimension(180, 7)); goal.add(progress);
        if (plan.reachability() == Reachability.UNREACHABLE_NOW)
            goal.add(RouteWidgets.badge("Partial route", GOLD, "This route does not cover the whole goal. Open Requirements & checks or Prep for remaining work."));
        if (plan.objective() != RouteObjective.EASIEST_PROGRESSION)
        {
            JButton easy = new JButton("Easy to hard"); easy.setName("route.easyToHard"); compact(easy);
            easy.setToolTipText("Prefer easier progression for this account. Existing pinned tasks stay included.");
            easy.addActionListener(event -> actions.objective(RouteObjective.EASIEST_PROGRESSION)); goal.add(easy);
        }
        put(route, goal);
        if (plan.stops().isEmpty())
        {
            JPanel empty = card(); heading(empty, plan.reachability() == Reachability.REACHED ? "Goal reached" : "No next stop");
            text(empty, plan.reachability() == Reachability.REACHED ? "Choose a new goal in Profile." : "Check Prep, your time budget or preferences.", MUTED, 12, false); put(route, empty);
        }
        else
        {
            BossSession next = plan.stops().get(0); JPanel stop = card(); heading(stop, "Next boss");
            stop.add(new WikiTitleRow("route.boss", next.activity(), WikiLinks.activity(next.activity(), next.tasks()), url -> ifCurrent(generation, () -> wikiOpener.accept(url))));
            stop.add(RouteWidgets.metrics(next.tasks().size(), next.points(), next.estimatedMinutes()));
            boolean check = next.conditional() || next.confidence() == Confidence.LOW;
            stop.add(RouteWidgets.badge(check ? "Check before starting" : "Ready", check ? GOLD : RouteWidgets.GREEN,
                "Open a task's Requirements & gear for levels, quests and equipment."));
            stop.add(guideBoss("route.boss", next.activity(), generation)); stop.add(Box.createVerticalStrut(4));
            heading(stop, "Tasks");
            next.tasks().forEach(assessment -> task(stop, assessment, "route.task." + assessment.task().id(), generation, state, account.profile()));
            JButton skipButton = new JButton("Skip this stop"); skipButton.setName("route.skip"); compact(skipButton);
            skipButton.setToolTipText("Skip this activity's tasks for this login.");
            Set<Integer> ids = new HashSet<>(); next.tasks().forEach(a -> ids.add(a.task().id()));
            skipButton.addActionListener(event -> ifCurrent(generation, () -> skip.accept(ids))); stop.add(Box.createVerticalStrut(5)); stop.add(skipButton); put(route, stop);
            if (plan.stops().size() > 1)
            {
                CollapsibleSection after = new CollapsibleSection("route.after", "Up next", Integer.toString(plan.stops().size() - 1), state);
                for (int i = 1; i < plan.stops().size(); i++)
                {
                    BossSession session = plan.stops().get(i);
                    CollapsibleSection upcoming = new CollapsibleSection("route.stop." + session.activity(), i + ". " + session.activity(), "+" + session.points(), state);
                    activityWiki(upcoming, "route.stop." + session.activity(), session, generation);
                    upcoming.body().add(RouteWidgets.metrics(session.tasks().size(), session.points(), session.estimatedMinutes()));
                    upcoming.body().add(guideBoss("route.stop." + session.activity(), session.activity(), generation));
                    upcoming.body().add(Box.createVerticalStrut(4));
                    session.tasks().forEach(assessment -> task(upcoming.body(), assessment, "route.task." + assessment.task().id(), generation, state, account.profile())); compactPut(after.body(), upcoming);
                }
                compactPut(route, after);
            }
            CollapsibleSection summary = new CollapsibleSection("route.summary", plan.objective() == RouteObjective.MAXIMUM_POINTS_PER_HOUR ? "Budget plan" : "Route total",
                "+" + plan.projectedPoints() + " · ~" + RouteWidgets.shortTime(plan.estimatedMinutes()), state);
            summary.body().add(RouteWidgets.metrics(plan.stops().stream().mapToInt(stopAt -> stopAt.tasks().size()).sum(), plan.projectedPoints(), plan.estimatedMinutes()));
            if (plan.objective() == RouteObjective.MAXIMUM_POINTS_PER_HOUR) text(summary.body(), "Points within the time budget; this is not an ETA to your tier.", MUTED, 11, false);
            else if (plan.objective() == RouteObjective.BOSS_COMPLETION) text(summary.body(), plan.scopeComplete() ? "Selected boss tasks are covered." : "Some selected boss tasks remain blocked or excluded.", MUTED, 11, false);
            else text(summary.body(), plan.scopeComplete() ? "Covers the points needed for this tier." : "Partial route; more preparation is needed.", MUTED, 11, false);
            text(summary.body(), "Estimated active work. Unknown preparation and waits can add more.", MUTED, 11, false); compactPut(route, summary);
        }
        if (!plan.blockers().isEmpty())
        {
            CollapsibleSection blocked = new CollapsibleSection("route.blockers", "Requirements & checks", Integer.toString(plan.blockers().size()), state);
            blocked.setStatusColor(GOLD); plan.blockers().forEach(reason -> text(blocked.body(), readable(reason), MUTED, 11, false)); compactPut(route, blocked);
        }
        JButton export = new JButton("Export route"); export.setName("route.export"); compact(export);
        export.setToolTipText("Save local Tasks Tracker JSON. Verified task IDs are required."); export.setEnabled(account.exportAvailable() && !plan.stops().isEmpty());
        export.addActionListener(event -> actions.exportRoute()); route.add(export);
    }
    private void task(JPanel container, TaskAssessment assessment, String key, long generation, DisclosureState state, PlayerProfile account)
    { compactPut(container, new TaskCard(key, assessment, state, this::readable, (id, action) -> ifCurrent(generation, () -> taskAction.accept(id, action)),
        url -> ifCurrent(generation, () -> wikiOpener.accept(url)), id -> ifCurrent(generation, () -> guideActions.startTask(id)), account)); }

    private JButton guideBoss(String key, String activity, long generation)
    {
        JButton guide = new JButton("Guide boss"); guide.setName("guide." + key); compact(guide);
        guide.setToolTipText("Show manual travel guidance for " + activity + ". Access requirements still apply.");
        guide.addActionListener(event -> ifCurrent(generation, () -> guideActions.startActivity(activity)));
        guide.setMaximumSize(new Dimension(Integer.MAX_VALUE, guide.getPreferredSize().height)); return guide;
    }
    private static String shortMode(RouteObjective mode)
    {
        switch (mode)
        {
            case FASTEST_NEXT_TIER: return "Fastest to tier";
            case MAXIMUM_POINTS_PER_HOUR: return "Points in budget";
            case BOSS_COMPLETION: return "Complete boss";
            case IRONMAN_EFFICIENT: return "Ironman prep";
            default: return mode.displayName();
        }
    }

    /** Update guide feedback without rebuilding the route or replacing its controls. Call on the EDT. */
    public void displayGuide(GuideView guide)
    {
        if (current == null) return;
        guideView = Objects.requireNonNull(guide); guideGeneration++; renderGuide(renderGeneration);
        route.revalidate(); route.repaint();
    }

    private void renderGuide(long routeGeneration)
    {
        guideSlot.removeAll();
        if (!guideView.active()) { guideSlot.revalidate(); guideSlot.repaint(); return; }
        long generation = guideGeneration;
        JPanel active = page(); active.setBackground(CARD); active.setName("guide.active");
        active.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(BORDER), new EmptyBorder(4, 4, 4, 4)));
        JTextArea title = new WrappedText(guideView.title()); title.setName("guide.title"); title.setEditable(false); title.setFocusable(false);
        title.setOpaque(true); title.setBackground(GOLD); title.setForeground(Color.BLACK); title.setFont(font(12, true)); title.setAlignmentX(Component.LEFT_ALIGNMENT);
        title.setBorder(new EmptyBorder(5, 5, 5, 5)); title.setToolTipText(guideView.description()); active.add(title);
        text(active, guideView.description(), TEXT, 12, false);
        if (!guideView.destination().isEmpty())
        {
            JTextArea destination = new WrappedText(guideView.destination()); destination.setName("guide.destination"); destination.setEditable(false); destination.setFocusable(false);
            destination.setOpaque(false); destination.setForeground(MUTED); destination.setFont(font(11, false)); destination.setAlignmentX(Component.LEFT_ALIGNMENT); active.add(destination);
        }
        if (!guideView.status().isEmpty())
        {
            JTextArea status = new WrappedText(guideView.status()); status.setName("guide.status"); status.setEditable(false); status.setFocusable(false);
            status.setOpaque(false); status.setForeground(TEXT); status.setFont(font(11, false)); status.setAlignmentX(Component.LEFT_ALIGNMENT); active.add(status);
        }
        if (!guideView.instructions().isEmpty())
        {
            CollapsibleSection travel = new CollapsibleSection("guide.travel." + guideView.activity(), "Travel details", "", disclosures);
            JTextArea instructions = new WrappedText(guideView.instructions()); instructions.setName("guide.instructions"); instructions.setEditable(false); instructions.setFocusable(false);
            instructions.setOpaque(false); instructions.setForeground(MUTED); instructions.setFont(font(11, false)); instructions.setAlignmentX(Component.LEFT_ALIGNMENT);
            travel.body().add(instructions); active.add(travel); active.add(Box.createVerticalStrut(4));
        }
        JPanel controls = new JPanel(new GridLayout(1, 2, 4, 0)); controls.setOpaque(false); controls.setAlignmentX(Component.LEFT_ALIGNMENT);
        JButton map = new JButton("Map"), stop = new JButton("Stop"); map.setName("guide.map"); stop.setName("guide.stop"); compact(map); compact(stop);
        map.setEnabled(guideView.canFocus()); map.setToolTipText(guideView.canFocus() ? "Open the world map, then center it on this destination" : "No mapped destination is available for this guide.");
        stop.setToolTipText("Stop the active guide.");
        map.addActionListener(event -> ifGuideCurrent(routeGeneration, generation, guideActions::focusMap));
        stop.addActionListener(event -> ifGuideCurrent(routeGeneration, generation, guideActions::stop));
        controls.add(map); controls.add(stop); controls.setMaximumSize(new Dimension(Integer.MAX_VALUE, controls.getPreferredSize().height)); active.add(controls);
        put(guideSlot, active); guideSlot.revalidate(); guideSlot.repaint();
    }

    private void ifGuideCurrent(long routeGeneration, long generation, Runnable command)
    { if (generation == guideGeneration && guideView.active()) ifCurrent(routeGeneration, command); }

    private static final class GuideSlot extends JPanel
    {
        GuideSlot() { setLayout(new BoxLayout(this, BoxLayout.Y_AXIS)); setOpaque(false); setAlignmentX(Component.LEFT_ALIGNMENT); }
        @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, getPreferredSize().height); }
    }

    private void activityWiki(CollapsibleSection section, String key, BossSession session, long generation)
    {
        String url = WikiLinks.activity(session.activity(), session.tasks());
        if (!url.isEmpty()) section.setHeaderLink(new WikiLinkButton(key, session.activity(), url, link -> ifCurrent(generation, () -> wikiOpener.accept(link))));
    }

    private void renderPreferences(RoutingPreferences preferences, long generation, DisclosureState state)
    {
        JPanel goal = card(); heading(goal, "Target tier");
        JComboBox<CaRouteConfig.Target> targetBox = new JComboBox<>(CaRouteConfig.Target.values()); compact(targetBox); targetBox.setName("profile.target");
        targetBox.setSelectedItem(preferences.target() == null ? CaRouteConfig.Target.NEXT_TIER : CaRouteConfig.Target.valueOf(preferences.target().name()));
        targetBox.setToolTipText("Next tier advances automatically."); targetBox.addActionListener(event -> ifCurrent(generation, () -> target.accept((CaRouteConfig.Target) targetBox.getSelectedItem()))); goal.add(targetBox); put(profile, goal);
        CollapsibleSection choices = new CollapsibleSection("profile.preferences", "Task preferences", "", state);
        for (TaskFlag flag : TaskFlag.values())
        {
            text(choices.body(), flag.name().charAt(0) + flag.name().substring(1).toLowerCase(Locale.ROOT).replace('_', ' '), MUTED, 11, false);
            JComboBox<Preference> box = new JComboBox<>(Preference.values()); compact(box); box.setName("profile.preference." + flag.name().toLowerCase(Locale.ROOT));
            box.setRenderer(new DefaultListCellRenderer() {
                @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected, boolean focus) {
                    return super.getListCellRendererComponent(list, value instanceof Preference ? value == Preference.NORMAL ? "Normal" : value == Preference.PREFER_NOT ? "Prefer not" : "Avoid" : value, index, selected, focus);
                }
            });
            box.setSelectedItem(preferences.preference(flag)); box.setToolTipText("Prefer not lowers priority. Avoid removes these tasks.");
            box.addActionListener(event -> ifCurrent(generation, () -> preference.accept(flag, (Preference) box.getSelectedItem()))); choices.body().add(box);
        }
        JButton reset = new JButton("Restore task choices"); reset.setName("profile.restore"); compact(reset); reset.addActionListener(event -> ifCurrent(generation, restore));
        choices.body().add(Box.createVerticalStrut(6)); choices.body().add(reset); compactPut(profile, choices);
    }
    private void renderGear(GearView view, long generation, ProfileActions actions, DisclosureState state)
    {
        CollapsibleSection gear = new CollapsibleSection("profile.gear", "Gear & preparation", "", state); JPanel content = gear.body();
        JCheckBox bank = new JCheckBox("Observe open bank", view.bankEnabled()); bank.setName("gear.bank"); compact(bank); bank.setOpaque(false); bank.setForeground(Color.WHITE);
        bank.setToolTipText("Optional positive observations for this login. Unseen bank items stay unknown.");
        bank.addActionListener(event -> ifCurrent(generation, () -> bankChoice.accept(bank.isSelected()))); content.add(bank);
        text(content, !view.bankEnabled() ? "Bank observation off" : view.bankObserved() ? view.bankItems() + " observed item types" : "Open your bank to refresh", MUTED, 10, false);
        for (GearCapabilityRepository.Definition definition : gearDefinitions.definitions())
        {
            GearProfile.CapabilityState ownership = view.gear().capability(definition.id());
            CollapsibleSection item = new CollapsibleSection("gear.item." + definition.id(), definition.label(), ownership == GearProfile.CapabilityState.PRESENT ? "Owned" : ownership == GearProfile.CapabilityState.MISSING ? "Missing" : "Unknown", state);
            item.setStatusColor(ownership == GearProfile.CapabilityState.PRESENT ? RouteWidgets.GREEN : ownership == GearProfile.CapabilityState.MISSING ? GOLD : MUTED);
            text(item.body(), definition.description(), MUTED, 11, false);
            JComboBox<ManualGearChoice> choice = new JComboBox<>(ManualGearChoice.values()); compact(choice); choice.setName("gear." + definition.id());
            Boolean override = view.overrides().get(definition.id()); choice.setSelectedItem(override == null ? ManualGearChoice.AUTO : override ? ManualGearChoice.OWNED : ManualGearChoice.MISSING);
            choice.setToolTipText("Auto uses observations. Detected items take priority over a Missing choice.");
            choice.addActionListener(event -> ifCurrent(generation, () -> gearChoice.accept(definition.id(), ((ManualGearChoice) choice.getSelectedItem()).owned))); item.body().add(choice);
            if (gearDefinitions.recommendedCapabilities().contains(definition.id())) preparationEstimate(item.body(), definition.id(), view.gear().activePreparationEstimates().get(definition.id()), actions);
            compactPut(content, item);
        }
        JButton reset = new JButton("Reset gear & prep"); reset.setName("gear.reset"); compact(reset); reset.addActionListener(event -> ifCurrent(generation, resetGear)); content.add(reset); compactPut(profile, gear);
    }
    private void preparationEstimate(JPanel parent, String capability, Double value, ProfileActions actions)
    {
        text(parent, "Preparation (active minutes)", MUTED, 10, false);
        JTextField minutes = new JTextField(value == null ? "" : String.valueOf(value), 6); minutes.setName("gear.estimate." + capability); compact(minutes);
        minutes.setToolTipText("Your estimate; counted once per capability in the route. Waiting time stays unknown.");
        JPanel row = new JPanel(new BorderLayout(3, 0)); row.setOpaque(false); row.setAlignmentX(Component.LEFT_ALIGNMENT);
        JPanel controls = new JPanel(new GridLayout(1, 2, 3, 0)); controls.setOpaque(false);
        JButton apply = new JButton("Set"), clear = new JButton("Auto"); apply.setName("gear.estimate.set." + capability); clear.setName("gear.estimate.auto." + capability); compact(apply); compact(clear);
        apply.putClientProperty("JComponent.minimumWidth", 0); clear.putClientProperty("JComponent.minimumWidth", 0);
        JLabel error = new JLabel(); error.setForeground(GOLD); compact(error);
        apply.addActionListener(event -> { try { String input = minutes.getText().trim().replace(',', '.'); Double estimate = input.isEmpty() ? null : Double.valueOf(input);
                if (estimate != null && (!Double.isFinite(estimate) || estimate < 0 || estimate > 100000)) throw new IllegalArgumentException();
                actions.preparationEstimate(capability, estimate); error.setText(""); }
            catch (IllegalArgumentException invalid) { error.setText("Use 0–100000 minutes"); } });
        clear.addActionListener(event -> actions.preparationEstimate(capability, null)); controls.add(apply); controls.add(clear);
        row.add(minutes, BorderLayout.CENTER); row.add(controls, BorderLayout.EAST); parent.add(row); parent.add(error);
    }
    private enum ManualGearChoice
    {
        AUTO("Auto", null), OWNED("Owned", true), MISSING("Missing", false);
        private final String label; private final Boolean owned;
        ManualGearChoice(String label, Boolean owned) { this.label = label; this.owned = owned; }
        @Override public String toString() { return label; }
    }
    public static String time(double minutes) { int rounded = (int) Math.ceil(minutes); return rounded < 60 ? rounded + " min" : rounded / 60 + "h " + rounded % 60 + "m"; }
    public RoutePlan currentPlan() { return current; }
    private void ifCurrent(long generation, Runnable command) { if (generation == renderGeneration && current != null) command.run(); }
    private ProfileActions guardedProfileActions(long generation)
    {
        return new ProfileActions() {
            @Override public void objective(RouteObjective value) { ifCurrent(generation, () -> profileActions.objective(value)); }
            @Override public void selectedActivity(String value) { ifCurrent(generation, () -> profileActions.selectedActivity(value)); }
            @Override public void timeBudget(int value) { ifCurrent(generation, () -> profileActions.timeBudget(value)); }
            @Override public void currentActivity(String value) { ifCurrent(generation, () -> profileActions.currentActivity(value)); }
            @Override public void killCount(String activity, Integer value) { ifCurrent(generation, () -> profileActions.killCount(activity, value)); }
            @Override public void preparationEstimate(String capability, Double value) { ifCurrent(generation, () -> profileActions.preparationEstimate(capability, value)); }
            @Override public void exportRoute() { ifCurrent(generation, profileActions::exportRoute); }
        };
    }
    private String readable(String reason)
    {
        List<GearCapabilityRepository.Definition> definitions = new ArrayList<>(gearDefinitions.definitions());
        definitions.sort(Comparator.comparingInt((GearCapabilityRepository.Definition definition) -> definition.id().length()).reversed());
        for (GearCapabilityRepository.Definition definition : definitions) reason = reason.replace(definition.id(), definition.label()); return reason;
    }
}
