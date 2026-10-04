package com.caroute.bridge;

import com.caroute.application.AccountState;
import com.caroute.data.AchievementRepository;
import com.caroute.data.GearCapabilityRepository;
import com.caroute.domain.*;
import java.util.*;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.InventoryID;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;

/** All calls must occur on RuneLite's client thread. Missing facts stay absent/unknown. */
public final class RuneLiteProfileReader
{
    private static final int[] COMPLETION_VARPS = {
        VarPlayerID.CA_TASK_COMPLETED_0, VarPlayerID.CA_TASK_COMPLETED_1,
        VarPlayerID.CA_TASK_COMPLETED_2, VarPlayerID.CA_TASK_COMPLETED_3,
        VarPlayerID.CA_TASK_COMPLETED_4, VarPlayerID.CA_TASK_COMPLETED_5,
        VarPlayerID.CA_TASK_COMPLETED_6, VarPlayerID.CA_TASK_COMPLETED_7,
        VarPlayerID.CA_TASK_COMPLETED_8, VarPlayerID.CA_TASK_COMPLETED_9,
        VarPlayerID.CA_TASK_COMPLETED_10, VarPlayerID.CA_TASK_COMPLETED_11,
        VarPlayerID.CA_TASK_COMPLETED_12, VarPlayerID.CA_TASK_COMPLETED_13,
        VarPlayerID.CA_TASK_COMPLETED_14, VarPlayerID.CA_TASK_COMPLETED_15,
        VarPlayerID.CA_TASK_COMPLETED_16, VarPlayerID.CA_TASK_COMPLETED_17,
        VarPlayerID.CA_TASK_COMPLETED_18, VarPlayerID.CA_TASK_COMPLETED_19,
        VarPlayerID.CA_TASK_COMPLETED_20
    };
    private static final int[] THRESHOLDS = {
        VarbitID.CA_THRESHOLD_EASY, VarbitID.CA_THRESHOLD_MEDIUM, VarbitID.CA_THRESHOLD_HARD,
        VarbitID.CA_THRESHOLD_ELITE, VarbitID.CA_THRESHOLD_MASTER, VarbitID.CA_THRESHOLD_GRANDMASTER
    };
    private final List<String> diagnostics = new ArrayList<>();
    private final GearCapabilityRepository gearRepository = GearCapabilityRepository.loadBundled();
    private final BankGearObserver bank = new BankGearObserver();
    private final LiveCatalogueValidator catalogueValidator = new LiveCatalogueValidator();
    private LiveCatalogueValidator.Result liveCatalogue;
    private long nextCatalogueAttempt;
    public List<String> diagnostics() { return Collections.unmodifiableList(new ArrayList<>(diagnostics)); }
    public void clearSession() { bank.clear(); liveCatalogue = null; nextCatalogueAttempt = 0; }
    public boolean bankObserved() { return bank.observed(); }
    public int bankItemCount() { return bank.itemIds().size(); }
    public long bankObservedAt() { return bank.observedAt(); }
    public boolean catalogueRetryDue(long now)
    { return liveCatalogue != null && liveCatalogue.retryable() && now >= nextCatalogueAttempt; }
    public Map<Integer, Integer> verifiedStructIds()
    { return liveCatalogue == null ? Collections.emptyMap() : liveCatalogue.verifiedStructIds(); }
    public static boolean completed(int taskId, int[] words)
    {
        if (taskId < 0 || taskId / 32 >= words.length) throw new IllegalArgumentException("Task completion index exceeds supported words");
        return (words[taskId / 32] & (1 << (taskId % 32))) != 0;
    }
    public PlayerProfile read(Client client, AchievementRepository repository, AccountState state, String account, long version)
    {
        diagnostics.clear(); boolean valid = true;
        Map<String, Integer> skills = new TreeMap<>(); Map<String, QuestStatus> quests = new TreeMap<>();
        for (Skill skill : Skill.values())
            try { int level = client.getRealSkillLevel(skill); if (level >= 1 && level <= 99) skills.put(skill.getName(), level); }
            catch (RuntimeException ex) { diagnostics.add("Unreadable skill: " + skill.getName()); }
        for (Quest quest : Quest.values())
            try {
                QuestState status = quest.getState(client);
                if (status != null) quests.put(quest.getName(), status == QuestState.FINISHED ? QuestStatus.COMPLETED
                    : status == QuestState.IN_PROGRESS ? QuestStatus.IN_PROGRESS : QuestStatus.NOT_STARTED);
            } catch (RuntimeException ex) { diagnostics.add("Unreadable quest: " + quest.getName()); }
        for (String skill : Arrays.asList("Attack", "Strength", "Defence", "Ranged", "Magic", "Hitpoints"))
            if (!skills.containsKey(skill)) { valid = false; diagnostics.add("Waiting for core combat skills to synchronize"); break; }
        long now = System.currentTimeMillis();
        if (liveCatalogue == null || (liveCatalogue.retryable() && now >= nextCatalogueAttempt))
        {
            liveCatalogue = catalogueValidator.validate(client, repository.tasks(), COMPLETION_VARPS.length * 32);
            nextCatalogueAttempt = now + 5000;
        }
        int points = 0;
        try { points = Math.max(0, client.getVarbitValue(VarbitID.CA_POINTS)); }
        catch (RuntimeException ex) { valid = false; diagnostics.add("CA points are not synchronized yet"); }
        Map<AchievementTier, Integer> thresholds = new EnumMap<>(AchievementTier.class); int fallback = 0; boolean thresholdsLive = true;
        for (AchievementTier tier : AchievementTier.values())
        {
            for (CombatAchievement task : repository.tasks()) if (task.tier() == tier) fallback += task.points();
            int threshold = 0;
            try { threshold = client.getVarbitValue(THRESHOLDS[tier.ordinal()]); } catch (RuntimeException ignored) { }
            if (threshold <= 0) { thresholdsLive = false; threshold = fallback; diagnostics.add(tier.displayName() + " threshold uses static dataset fallback"); }
            thresholds.put(tier, threshold);
        }
        // Positive threshold values can settle after the immutable cache identities are loaded.
        // Revalidate changed values instead of retaining a transient mismatch for the whole login.
        if (thresholdsLive && !thresholds.equals(liveCatalogue.thresholds()))
        {
            liveCatalogue = catalogueValidator.validate(client, repository.tasks(), COMPLETION_VARPS.length * 32);
            nextCatalogueAttempt = now + 5000;
        }
        diagnostics.addAll(liveCatalogue.diagnostics());
        Set<Integer> done = new TreeSet<>(); int[] words = new int[COMPLETION_VARPS.length];
        if (!liveCatalogue.snapshotSafe())
        {
            valid = false;
            diagnostics.add(liveCatalogue.retryable() ? "Waiting for live CA identities to synchronize"
                : "CA catalogue mismatch: routing is paused until the bundled data or cache mapping is updated");
        }
        else try {
            for (int i = 0; i < words.length; i++) words[i] = client.getVarpValue(COMPLETION_VARPS[i]);
            for (CombatAchievement task : repository.tasks()) if (completed(task.id(), words)) done.add(task.id());
            for (int id = repository.tasks().size(); id < words.length * 32; id++)
                if (completed(id, words)) { diagnostics.add("Unmapped completed CA " + id + "; update the catalogue"); break; }
        } catch (RuntimeException ex) { valid = false; diagnostics.add("Completion state is not synchronized yet"); }
        int computed = repository.tasks().stream().filter(t -> done.contains(t.id())).mapToInt(CombatAchievement::points).sum();
        if (computed != points)
        {
            diagnostics.add("Game/data points mismatch: " + points + " live vs " + computed + " mapped; catalogue may have changed");
            if (liveCatalogue.identityComplete() || computed > points)
            {
                valid = false;
                diagnostics.add("CA points mismatch: completion bits and live points are inconsistent; waiting for synchronization or a data update");
            }
        }
        diagnostics.add("Catalogue snapshot " + repository.version() + "; " + liveCatalogue.liveTaskCount() + " live tasks observed");
        AccountType type = AccountType.IRONMAN;
        try {
            String name = client.getAccountType().name();
            type = name.contains("HARDCORE") ? AccountType.HARDCORE_IRONMAN : name.contains("ULTIMATE") ? AccountType.ULTIMATE_IRONMAN
                : name.contains("IRONMAN") ? AccountType.IRONMAN : name.equals("NORMAL") ? AccountType.MAIN : AccountType.IRONMAN;
        } catch (RuntimeException ex) { diagnostics.add("Account type unreadable; conservative acquisition profile used"); }
        Set<Integer> equipped = new TreeSet<>(), held = new TreeSet<>();
        try {
            ItemContainer equipment = client.getItemContainer(InventoryID.EQUIPMENT);
            if (equipment != null) for (Item item : equipment.getItems()) if (item != null && item.getId() >= 0 && item.getQuantity() > 0) equipped.add(item.getId());
            ItemContainer inventory = client.getItemContainer(InventoryID.INVENTORY);
            if (inventory != null) for (Item item : inventory.getItems()) if (item != null && item.getId() >= 0 && item.getQuantity() > 0) held.add(item.getId());
        } catch (RuntimeException ex) { diagnostics.add("Equipment scan incomplete; ownership is unknown"); }
        try { bank.capture(client, state.observeBank, System.currentTimeMillis()); }
        catch (RuntimeException ex) { diagnostics.add("Open bank observation unavailable; unseen gear remains unknown"); }
        held.addAll(equipped); held.addAll(bank.itemIds());
        Map<String, Boolean> supportedOverrides = new TreeMap<>();
        Set<String> capabilities = new HashSet<>(); gearRepository.definitions().forEach(definition -> capabilities.add(definition.id()));
        state.gearOverrides.forEach((id, owned) -> { if (capabilities.contains(id)) supportedOverrides.put(id, owned); });
        if (supportedOverrides.size() != state.gearOverrides.size()) diagnostics.add("Some saved gear choices are no longer supported; they are ignored without resetting your account preferences");
        Map<String, Double> estimates = new TreeMap<>();
        state.preparationEstimates.forEach((id, minutes) -> { if (capabilities.contains(id)) estimates.put(id, minutes); });
        GearProfile gear = gearRepository.classify(equipped, held, false, supportedOverrides).withActivePreparationEstimates(estimates);
        diagnostics.add("Gear rules " + gearRepository.version() + "; only curated capabilities affect recommendations");
        return PlayerProfile.builder().accountKey(account).version(version).loaded(valid).accountType(type)
            .completedTaskIds(done).currentPoints(points).thresholds(thresholds).skills(skills).quests(quests)
            .killCounts(state.effectiveKillCounts()).gear(gear)
            .catalogueComplete(liveCatalogue.catalogueComplete() && thresholdsLive && thresholds.equals(liveCatalogue.thresholds()) && computed == points).build();
    }
}
