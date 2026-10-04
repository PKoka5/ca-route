package com.caroute.bridge;

import com.caroute.CaRouteConfig;
import com.caroute.application.AccountState;
import com.caroute.data.AchievementRepository;
import com.caroute.domain.*;
import com.caroute.navigation.GuideController;
import com.caroute.navigation.ShortestPathBridge;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.function.BiFunction;
import net.runelite.api.Client;
import net.runelite.api.EnumComposition;
import net.runelite.api.GameState;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.StructComposition;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.events.PluginMessage;
import net.runelite.client.ui.overlay.worldmap.WorldMapPoint;
import net.runelite.client.ui.overlay.worldmap.WorldMapPointManager;
import org.junit.Test;
import static org.junit.Assert.*;

/** Exercises the actual SDK adapter and profile assembly, not just the validator's fake Source. */
public class ProfileBridgeIntegrationTest
{
    @Test public void verifiedSdkSnapshotEnablesCompletenessAndDriftSuppressesUnsafeRoutes()
    {
        AchievementRepository repository = AchievementRepository.loadBundled();
        RuneLiteProfileReader reader = new RuneLiteProfileReader();
        PlayerProfile verified = reader.read(client(repository, false, false), repository, new AccountState(), "123", 1);
        assertTrue(verified.loaded()); assertTrue(verified.catalogueComplete());
        reader.clearSession();
        PlayerProfile changed = reader.read(client(repository, true, false, Map.of(), true, false), repository, new AccountState(), "456", 2);
        assertFalse(changed.loaded()); assertFalse(changed.catalogueComplete());
        assertTrue("Unsafe task identity must not decode completion bits", changed.completedTaskIds().isEmpty());
        assertTrue(reader.diagnostics().stream().anyMatch(value -> value.startsWith("CA catalogue mismatch:")));
    }

    @Test public void bankObservationsStayOptInAndClearBeforeAnotherAccount()
    {
        AchievementRepository repository = AchievementRepository.loadBundled();
        Client client = client(repository, false, true);
        RuneLiteProfileReader reader = new RuneLiteProfileReader();
        AccountState account = new AccountState();
        assertEquals(GearProfile.CapabilityState.UNKNOWN, reader.read(client, repository, account, "123", 1).gear().capability("bowfa-crystal"));
        account.observeBank = true;
        PlayerProfile observed = reader.read(client, repository, account, "123", 2);
        assertEquals(GearProfile.CapabilityState.PRESENT, observed.gear().capability("bowfa-crystal"));
        assertEquals(GearProfile.CapabilityState.UNKNOWN, observed.gear().capability("twisted-bow"));
        assertFalse(observed.gear().ownershipComplete());
        assertTrue(reader.bankObserved());
        reader.clearSession();
        assertEquals(GearProfile.CapabilityState.UNKNOWN, reader.read(client, repository, new AccountState(), "456", 3).gear().capability("bowfa-crystal"));
        assertFalse(reader.bankObserved());
    }

    @Test public void unsupportedSavedGearDoesNotBreakTheRestOfTheAccount()
    {
        AchievementRepository repository = AchievementRepository.loadBundled();
        AccountState account = new AccountState();
        account.gearOverrides.put("retired-capability", true);
        account.gearOverrides.put("bowfa-crystal", true);
        RuneLiteProfileReader reader = new RuneLiteProfileReader();
        PlayerProfile profile = reader.read(client(repository, false, false), repository, account, "123", 1);
        assertTrue(profile.loaded());
        assertEquals(GearProfile.CapabilityState.PRESENT, profile.gear().capability("bowfa-crystal"));
        assertTrue(reader.diagnostics().stream().anyMatch(value -> value.contains("no longer supported")));
    }

    @Test public void positiveThresholdSynchronizationRevalidatesWithoutRelogging()
    {
        AchievementRepository repository = AchievementRepository.loadBundled();
        Map<Integer, Integer> overrides = new HashMap<>();
        overrides.put(VarbitID.CA_THRESHOLD_EASY, 40);
        Client client = client(repository, false, false, overrides, false, false);
        RuneLiteProfileReader reader = new RuneLiteProfileReader();
        assertFalse(reader.read(client, repository, new AccountState(), "123", 1).catalogueComplete());
        overrides.clear();
        assertTrue(reader.read(client, repository, new AccountState(), "123", 2).catalogueComplete());
    }

    @Test public void addedTasksCannotExplainFewerLivePointsThanVerifiedCompletedTasks()
    {
        AchievementRepository repository = AchievementRepository.loadBundled();
        Map<Integer, Integer> overrides = new HashMap<>();
        Client client = client(repository, false, false, overrides, true, true);
        RuneLiteProfileReader reader = new RuneLiteProfileReader();
        PlayerProfile inconsistent = reader.read(client, repository, new AccountState(), "123", 1);
        assertFalse(inconsistent.loaded());
        assertTrue(reader.diagnostics().stream().anyMatch(value -> value.startsWith("CA points mismatch:")));
        overrides.put(VarbitID.CA_POINTS, repository.tasks().get(0).points());
        PlayerProfile consistent = reader.read(client, repository, new AccountState(), "123", 2);
        assertTrue(consistent.loaded());
        assertFalse("Added task is not in our route metadata", consistent.catalogueComplete());
    }

    @Test public void verifiedKnownSubsetStartsAndRetainsGuidanceButIdentityDriftStopsIt()
    {
        AchievementRepository repository = AchievementRepository.loadBundled();
        Client client = client(repository, false, false, Collections.emptyMap(), false, true);
        RuneLiteProfileReader reader = new RuneLiteProfileReader();
        AccountState state = new AccountState();
        PlayerProfile partial = reader.read(client, repository, state, "123", 1);
        assertTrue("All bundled completion identities are safe despite an added live task", partial.loaded());
        assertFalse("The new live task is outside our metadata coverage", partial.catalogueComplete());
        CombatAchievement task = repository.tasks().stream()
            .filter(candidate -> candidate.id() == 398).findFirst().orElseThrow();
        assertEquals("Into the Den of Giants", task.name());
        List<PluginMessage> messages = new ArrayList<>();
        ShortestPathBridge[] reference = new ShortestPathBridge[1];
        ShortestPathBridge bridge = new ShortestPathBridge(message -> {
            messages.add(message); reference[0].observe(message);
        }, () -> true);
        reference[0] = bridge;
        List<WorldMapPoint> markers = new ArrayList<>();
        WorldMapPointManager points = new WorldMapPointManager() {
            @Override public void add(WorldMapPoint point) { markers.add(point); }
            @Override public void remove(WorldMapPoint point) { markers.remove(point); }
        };
        GuideController guide = new GuideController(client, points, bridge, new CaRouteConfig() {}, ignored -> {});
        assertTrue(guide.startTask(task, partial));
        assertTrue(guide.view().active());
        assertEquals(Integer.valueOf(task.id()), guide.view().taskId());
        assertEquals(task.description(), guide.view().description());
        assertEquals(1, markers.size()); assertEquals("path", messages.get(0).getName());
        assertEquals(new WorldPoint(1419, 3588, 0), messages.get(0).getData().get("target"));
        guide.refresh(reader.read(client, repository, state, "123", 2));
        assertTrue("A routine profile refresh must keep the safe selected task active", guide.view().active());
        assertEquals(1, messages.size());

        reader.clearSession();
        PlayerProfile unsafe = reader.read(client(repository, true, false), repository, state, "123", 3);
        assertFalse(unsafe.loaded());
        guide.refresh(unsafe);
        assertFalse(guide.view().active()); assertTrue(markers.isEmpty());
        assertEquals("clear", messages.get(1).getName());
        assertFalse("An unsafe live identity must still refuse a fresh selection", guide.startTask(task, unsafe));
        assertEquals(2, messages.size());
    }

    private static Client client(AchievementRepository repository, boolean renamed, boolean bankOpen)
    { return client(repository, renamed, bankOpen, Collections.emptyMap(), false, false); }

    private static Client client(AchievementRepository repository, boolean renamed, boolean bankOpen,
        Map<Integer, Integer> varbitOverrides, boolean completeFirst, boolean addedTask)
    {
        Map<Integer, EnumComposition> enums = new HashMap<>();
        Map<Integer, StructComposition> structs = new HashMap<>();
        Map<Integer, Integer> thresholds = new HashMap<>();
        int[] thresholdIds = {VarbitID.CA_THRESHOLD_EASY, VarbitID.CA_THRESHOLD_MEDIUM, VarbitID.CA_THRESHOLD_HARD,
            VarbitID.CA_THRESHOLD_ELITE, VarbitID.CA_THRESHOLD_MASTER, VarbitID.CA_THRESHOLD_GRANDMASTER};
        int cumulative = 0;
        for (AchievementTier tier : AchievementTier.values())
        {
            List<Integer> tierStructs = new ArrayList<>();
            for (CombatAchievement task : repository.tasks()) if (task.tier() == tier)
            {
                int structId = 100000 + task.id(); tierStructs.add(structId); cumulative += task.points();
                structs.put(structId, proxy(StructComposition.class, (method, args) -> {
                    if (method.equals("getId")) return structId;
                    if (method.equals("getIntValue")) return ((Integer) args[0]) == LiveCatalogueValidator.COMPLETION_INDEX_PARAM ? task.id() : tier.points();
                    if (method.equals("getStringValue")) return renamed && task.id() == 0 ? "Changed live identity" : task.name();
                    return null;
                }));
            }
            enums.put(LiveCatalogueValidator.TASK_ENUMS[tier.ordinal()], enumeration(tierStructs.stream().mapToInt(Integer::intValue).toArray()));
            thresholds.put(thresholdIds[tier.ordinal()], cumulative);
        }
        enums.put(LiveCatalogueValidator.TIER_POINTS_ENUM, enumeration(new int[]{1, 2, 3, 4, 5, 6}, new int[]{1, 2, 3, 4, 5, 6}));
        if (addedTask)
        {
            int id = repository.tasks().size(), structId = 100000 + id;
            int[] easy = enums.get(LiveCatalogueValidator.TASK_ENUMS[0]).getIntVals();
            int[] expanded = Arrays.copyOf(easy, easy.length + 1); expanded[easy.length] = structId;
            enums.put(LiveCatalogueValidator.TASK_ENUMS[0], enumeration(expanded));
            structs.put(structId, proxy(StructComposition.class, (method, args) -> method.equals("getId") ? structId
                : method.equals("getIntValue") ? ((Integer) args[0]) == LiveCatalogueValidator.COMPLETION_INDEX_PARAM ? id : 1
                : method.equals("getStringValue") ? "A new live task" : null));
            thresholds.replaceAll((key, value) -> value + 1);
        }
        ItemContainer bank = proxy(ItemContainer.class, (method, args) -> method.equals("getItems")
            ? new Item[]{new Item(25867, 1), new Item(23971, 1), new Item(23975, 1), new Item(23979, 1), new Item(20997, 0)} : null);
        Widget widget = proxy(Widget.class, (method, args) -> method.equals("isHidden") ? false : null);
        return proxy(Client.class, (method, args) -> {
            switch (method)
            {
                case "isClientThread": return true;
                case "getGameState": return GameState.LOGGED_IN;
                case "getRealSkillLevel": return 99;
                case "getAccountType": return net.runelite.api.vars.AccountType.NORMAL;
                case "getEnum": return enums.get((Integer) args[0]);
                case "getStructComposition": return structs.get((Integer) args[0]);
                case "getVarbitValue": return varbitOverrides.getOrDefault((Integer) args[0], thresholds.getOrDefault((Integer) args[0], 0));
                case "getVarpValue": return completeFirst && ((Integer) args[0]) == VarPlayerID.CA_TASK_COMPLETED_0 ? 1 : 0;
                case "getWidget": return bankOpen ? widget : null;
                case "getItemContainer": return bankOpen && args[0] == InventoryID.BANK ? bank : null;
                default: return null;
            }
        });
    }
    private static EnumComposition enumeration(int[] values)
    {
        int[] keys = new int[values.length]; for (int i = 0; i < keys.length; i++) keys[i] = i;
        return enumeration(keys, values);
    }
    private static EnumComposition enumeration(int[] keys, int[] values)
    {
        return proxy(EnumComposition.class, (method, args) -> method.equals("getKeys") ? keys
            : method.equals("getIntVals") ? values : method.equals("size") ? keys.length : null);
    }
    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, BiFunction<String, Object[], Object> implementation)
    {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (instance, method, args) -> {
            Object result = implementation.apply(method.getName(), args == null ? new Object[0] : args);
            if (result != null || !method.getReturnType().isPrimitive()) return result;
            if (method.getReturnType() == boolean.class) return false;
            if (method.getReturnType() == long.class) return 0L;
            if (method.getReturnType() == void.class) return null;
            return 0;
        });
    }
}
