package com.caroute.application;

import com.caroute.CaRouteConfig;
import com.google.gson.Gson;
import java.nio.file.*;
import java.util.Set;
import java.util.Map;
import com.caroute.domain.Preference;
import com.caroute.domain.TaskFlag;
import com.caroute.domain.RouteObjective;
import net.runelite.client.util.Filepath;
import org.junit.Test;
import static org.junit.Assert.*;

public class AccountStateStoreTest
{
    @Test public void settingsStayLocalAccountScopedAndSkipDoesNotPersist() throws Exception
    {
        Path directory=Files.createTempDirectory("caroute-settings-test");AccountStateStore store=localStore(directory);
        CaRouteConfig config=new CaRouteConfig(){};AccountState state=AccountState.defaults(config);
        state.pins.add(0);state.exclusions.add(1);state.skipped.add(2);state.killCounts.put("Boss",20);store.save("123",state);
        AccountState loaded=store.load("123",config,Set.of(0,1,2));assertEquals(Set.of(0),loaded.pins);assertEquals(Set.of(1),loaded.exclusions);assertTrue(loaded.skipped.isEmpty());assertEquals(Integer.valueOf(20),loaded.killCounts.get("Boss"));
        assertTrue(store.load("456",config,Set.of(0,1,2)).pins.isEmpty());store.reset("123");assertTrue(store.load("123",config,Set.of(0,1,2)).exclusions.isEmpty());
        Files.delete(directory);
    }
    @Test public void accountIdentifiersCannotEscapeStorageDirectory()
    { try{AccountStateStore.key("../other");fail("Traversal must be rejected");}catch(IllegalArgumentException expected){ } }

    @Test public void legacyManualGearMigratesWithoutLosingTaskCustomizations() throws Exception
    {
        Path directory = Files.createTempDirectory("caroute-migration-test");
        Path file = directory.resolve("account.123.json");
        Files.writeString(file, "{\"schema\":1,\"manualGear\":[\"bowfa\",\"bowfa_crystal\"],\"pins\":[7],\"killCounts\":{\"Boss\":42}}");
        AccountStateStore store = localStore(directory);
        AccountState state = store.load("123", new CaRouteConfig(){}, Set.of(7));
        assertEquals(3, state.schema);
        assertEquals(Map.of("bowfa", true, "bowfa-crystal", true), state.gearOverrides);
        assertEquals(Set.of(7), state.pins);
        assertEquals(Integer.valueOf(42), state.killCounts.get("Boss"));
        assertFalse(state.observeBank);
        state.setGearOverride("bowfa", false);
        state.observeBank = true;
        store.save("123", state);
        assertEquals(Map.of("bowfa", false, "bowfa-crystal", true), store.load("123", new CaRouteConfig(){}, Set.of(7)).gearOverrides);
        assertTrue(store.load("123", new CaRouteConfig(){}, Set.of(7)).observeBank);
        assertTrue(store.load("456", new CaRouteConfig(){}, Set.of(7)).gearOverrides.isEmpty());
        state.setGearOverride("bowfa", null);
        state.setGearOverride("bowfa-crystal", null);
        assertTrue(state.gearOverrides.isEmpty());
        store.reset("123"); Files.delete(directory);
    }

    @Test public void changingBankSettingKeepsAccountSidebarPreferences()
    {
        AccountState state = AccountState.defaults(new CaRouteConfig(){});
        state.target = CaRouteConfig.Target.HARD;
        state.preferences.put(TaskFlag.SPEED, Preference.AVOID);
        state.applyConfigChange("observeBank", new CaRouteConfig(){ @Override public boolean observeBank() { return true; } });
        assertTrue(state.observeBank);
        assertEquals(CaRouteConfig.Target.HARD, state.target);
        assertEquals(Preference.AVOID, state.preferences.get(TaskFlag.SPEED));
    }

    @Test public void schemaTwoSettingsMigrateAndNewPlanningChoicesStayAccountLocal() throws Exception
    {
        Path directory = Files.createTempDirectory("caroute-planning-test");
        Path file = directory.resolve("account.123.json");
        Files.writeString(file, "{\"schema\":2,\"target\":\"HARD\",\"gearOverrides\":{\"bowfa\":true},\"killCounts\":{\"Zulrah\":25}}");
        AccountStateStore store = localStore(directory);
        AccountState state = store.load("123", new CaRouteConfig(){}, Set.of(7));
        assertEquals(3, state.schema);
        assertEquals(CaRouteConfig.Target.HARD, state.target);
        assertEquals(RouteObjective.FASTEST_NEXT_TIER, state.objective);
        state.objective = RouteObjective.MAXIMUM_POINTS_PER_HOUR;
        state.timeBudgetMinutes = 45; state.selectedActivity = "Zulrah"; state.currentActivity = "Zulrah";
        state.preparationEstimates.put("bowfa", 300.5); state.killCountOverrides.put("Zulrah", 120);
        assertEquals(Integer.valueOf(120), state.effectiveKillCounts().get("Zulrah"));
        store.save("123", state);
        AccountState loaded = store.load("123", new CaRouteConfig(){}, Set.of(7));
        assertEquals(RouteObjective.MAXIMUM_POINTS_PER_HOUR, loaded.routingPreferences(0).objective());
        assertEquals(45, loaded.routingPreferences(0).timeBudgetMinutes());
        assertEquals("Zulrah", loaded.selectedActivity);
        assertEquals("", loaded.currentActivity);
        assertEquals(Map.of("bowfa", 300.5), loaded.preparationEstimates);
        assertEquals(Integer.valueOf(120), loaded.effectiveKillCounts().get("Zulrah"));
        loaded.killCountOverrides.remove("Zulrah");
        assertEquals(Integer.valueOf(25), loaded.effectiveKillCounts().get("Zulrah"));
        AccountState other = store.load("456", new CaRouteConfig(){}, Set.of(7));
        assertTrue(other.preparationEstimates.isEmpty()); assertTrue(other.killCountOverrides.isEmpty());
        assertEquals(RouteObjective.EASIEST_PROGRESSION, other.objective);
        store.reset("123"); Files.delete(directory);
    }

    @Test public void invalidPreparationAndCountEntriesAreRemovedWithoutResettingValidChoices()
    {
        AccountState state = AccountState.defaults(new CaRouteConfig(){});
        state.preparationEstimates.put("bowfa", 0.0);
        state.preparationEstimates.put("nan", Double.NaN); state.preparationEstimates.put("negative", -1.0);
        state.preparationEstimates.put("too-large", 100001.0);
        state.killCountOverrides.put("Zulrah", 0); state.killCountOverrides.put("", 5); state.killCountOverrides.put("Kraken", -1);
        state.timeBudgetMinutes = -1; state.sanitize(Set.of(), 0);
        assertEquals(Map.of("bowfa", 0.0), state.preparationEstimates);
        assertEquals(Map.of("Zulrah", 0), state.killCountOverrides);
        assertEquals(60, state.timeBudgetMinutes);
    }

    @Test public void newAccountsStartWithProgressionWhileExistingExplicitModesRemainIntact() throws Exception
    {
        CaRouteConfig config=new CaRouteConfig(){};
        assertEquals(RouteObjective.EASIEST_PROGRESSION,config.objective());
        assertEquals(RouteObjective.EASIEST_PROGRESSION,AccountState.defaults(config).objective);
        Path directory=Files.createTempDirectory("caroute-progression-default");AccountStateStore store=localStore(directory);
        AccountState existing=AccountState.defaults(config);existing.objective=RouteObjective.IRONMAN_EFFICIENT;store.save("123",existing);
        assertEquals(RouteObjective.IRONMAN_EFFICIENT,store.load("123",config,Set.of()).objective);
        assertEquals(RouteObjective.EASIEST_PROGRESSION,store.load("456",config,Set.of()).objective);
        store.reset("123");Files.delete(directory);
    }

    private static AccountStateStore localStore(Path directory)
    { return new AccountStateStore(Filepath.Unchecked.getRooted(directory), new Gson()); }
}
