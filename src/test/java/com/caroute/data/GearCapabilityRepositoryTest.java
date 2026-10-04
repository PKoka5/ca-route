package com.caroute.data;

import com.caroute.domain.GearProfile;
import com.caroute.domain.GearProfile.CapabilityState;
import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.lang.reflect.Field;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class GearCapabilityRepositoryTest
{
    private final GearCapabilityRepository repository = GearCapabilityRepository.loadBundled();
    @Test public void observedBowfaKitKeepsEquippedAndOwnedEvidenceSeparate()
    {
        GearProfile gear = repository.classify(Set.of(25867), Set.of(23971,23975,23979), false);
        assertEquals(CapabilityState.PRESENT, gear.capability("bowfa-crystal"));
        assertEquals(CapabilityState.PRESENT, gear.capability("ranged-accuracy-kit"));
        assertEquals(Set.of(25867), gear.equipped());
        assertEquals(Set.of(23971,23975,23979), gear.owned());
        assertFalse(gear.ownershipComplete());
        assertEquals(CapabilityState.UNKNOWN, gear.capability("ratbane-weapon"));
        assertEquals(CapabilityState.PRESENT, repository.classify(Set.of(),Set.of(33021,33031,33023,33027),false).capability("bowfa-crystal"));
    }
    @Test public void inactiveAndGauntletItemsDoNotProveAUsableKit()
    {
        GearProfile gear = repository.classify(Set.of(25862,23888), Set.of(23977,23981), false);
        assertEquals(CapabilityState.UNKNOWN, gear.capability("bowfa-crystal"));
        assertEquals(CapabilityState.UNKNOWN, gear.capability("crystal-armour"));
        assertEquals(CapabilityState.MISSING, repository.classify(Set.of(),Set.of(861),true).capability("bowfa"));
    }
    @Test public void explicitAbsenceIsPerCapabilityAndObservedPositiveWins()
    {
        GearProfile gear = repository.classify(Set.of(25867),Set.of(),false,Map.of("bowfa",false,"ratbane-weapon",false));
        assertEquals(CapabilityState.PRESENT, gear.capability("bowfa"));
        assertEquals(CapabilityState.MISSING, gear.capability("ratbane-weapon"));
        assertEquals(CapabilityState.UNKNOWN, gear.capability("twisted-bow"));
        assertFalse(gear.knownMissing().contains("bowfa"));
        assertEquals(CapabilityState.PRESENT, gear.withOverrides(Map.of("bowfa",false)).capability("bowfa"));
    }
    @Test public void compoundAndComponentControlsPropagateThreeValuedFacts()
    {
        GearProfile full = repository.classify(Set.of(),Set.of(),false,Map.of("bowfa-crystal",true));
        for (String capability : List.of("bowfa","crystal-helm","crystal-body","crystal-legs","crystal-armour","ranged-accuracy-kit"))
            assertEquals(capability,CapabilityState.PRESENT,full.capability(capability));
        GearProfile components = repository.classify(Set.of(),Set.of(),false,Map.of("bowfa",true,"crystal-armour",true));
        assertEquals(CapabilityState.PRESENT,components.capability("ranged-accuracy-kit"));
        GearProfile missing = repository.classify(Set.of(),Set.of(),false,Map.of("crystal-body",false));
        assertEquals(CapabilityState.MISSING,missing.capability("crystal-armour"));
        assertEquals(CapabilityState.MISSING,missing.capability("bowfa-crystal"));
        assertEquals(CapabilityState.UNKNOWN,missing.capability("ranged-accuracy-kit"));
        missing = repository.classify(Set.of(),Set.of(),false,Map.of("crystal-body",false,"twisted-bow",false));
        assertEquals(CapabilityState.MISSING,missing.capability("ranged-accuracy-kit"));
        assertEquals(CapabilityState.PRESENT,repository.classify(Set.of(),Set.of(20997),false,Map.of("bowfa-crystal",false)).capability("ranged-accuracy-kit"));
    }
    @Test public void taskOverlayValidatesFullIdentityAndKeepsRestrictionsSoft()
    {
        assertEquals(Set.of("ratbane-weapon"),repository.recommendedFor(523,"Sit Rat","Scurrius"));
        assertEquals(Set.of("ranged-accuracy-kit"),repository.recommendedFor(230,"Zulrah Speed-Trialist","Zulrah"));
        assertTrue(repository.recommendedFor(224,"Zulrah Adept","Zulrah").isEmpty());
        try { repository.recommendedFor(230,"Reused completion index","Zulrah"); fail("Stale task overlay accepted"); }
        catch (IllegalArgumentException expected) { assertTrue(expected.getMessage().contains("Stale")); }
        assertTrue(AchievementRepository.loadBundled().tasks().get(523).minimumGear().isEmpty());
    }
    @Test public void bundledRulesMatchCuratedSourceExactly() throws IOException
    {
        byte[] curated = Files.readAllBytes(Paths.get("data","gear-capabilities.json"));
        try (InputStream in = getClass().getResourceAsStream("/com/caroute/gear-capabilities.json"))
        { assertArrayEquals(curated,in.readAllBytes()); }
    }
    @Test public void broaderOverlayUsesCanonicalIdentityAndLeavesSpecialRestrictionsUninvented()
    {
        assertEquals(29, repository.definitions().size());
        assertEquals(83, repository.recommendations().size());
        List<com.caroute.domain.CombatAchievement> tasks = AchievementRepository.loadBundled().tasks();
        for (GearCapabilityRepository.Recommendation row : repository.recommendations())
        {
            var task = tasks.get(row.taskId());
            assertEquals(task.name(), row.taskName()); assertEquals(task.activity(), row.activity());
            assertEquals(row.capabilities(), repository.recommendedFor(task.id(), task.name(), task.activity()));
            assertTrue(row.rationale().contains("soft")); assertTrue(row.source().startsWith("https://"));
            assertTrue("Soft overlay invented a minimum gear lock", task.minimumGear().isEmpty());
        }
        for (int id : List.of(11,29,200,215,275,336,339))
        {
            var task = tasks.get(id);
            assertTrue("Special restriction received a contradictory generic kit: " + task.name(),
                repository.recommendedFor(id,task.name(),task.activity()).isEmpty());
        }
        assertEquals(Set.of("melee-weapon"),repository.recommendedFor(153,"Facing Jad Head-on","TzTok-Jad"));
        assertEquals(Set.of("magic-weapon"),repository.recommendedFor(28,"Defence? What Defence?","Barrows"));
    }
    @Test public void affordableAlternativesDoNotRequireOneBestInSlotWeapon()
    {
        GearProfile crossbow = repository.classify(Set.of(9185),Set.of(),false,
            Map.of("twisted-bow",false,"bowfa-crystal",false));
        for (String capability : List.of("ranged-weapon","barrows-weapon","vorkath-weapon","gwd-weapon"))
            assertEquals(capability,CapabilityState.PRESENT,crossbow.capability(capability));
        GearProfile iban = repository.classify(Set.of(),Set.of(12658),false,Map.of("powered-magic",false));
        assertEquals(CapabilityState.PRESENT,iban.capability("magic-weapon"));
        assertEquals(CapabilityState.PRESENT,iban.capability("barrows-weapon"));
        GearProfile mace = repository.classify(Set.of(1434),Set.of(),false);
        assertEquals(CapabilityState.PRESENT,mace.capability("crush-weapon"));
        assertEquals(CapabilityState.PRESENT,mace.capability("sarachnis-weapon"));
        assertEquals(CapabilityState.PRESENT,repository.classify(Set.of(21009),Set.of(),false).capability("vorkath-weapon"));
    }
    @Test public void threeKingCompoundKitRequiresAllStylesAndKeepsUnknownDistinctFromMissing()
    {
        GearProfile two = repository.classify(Set.of(4151,9185),Set.of(),false);
        assertEquals(CapabilityState.UNKNOWN,two.capability("dagannoth-hybrid"));
        GearProfile three = repository.classify(Set.of(4151,9185),Set.of(11905),false);
        assertEquals(CapabilityState.PRESENT,three.capability("dagannoth-hybrid"));
        GearProfile missing = repository.classify(Set.of(4151,9185),Set.of(),false,Map.of("magic-weapon",false));
        assertEquals(CapabilityState.MISSING,missing.capability("dagannoth-hybrid"));
        assertEquals(CapabilityState.UNKNOWN,repository.classify(Set.of(12924,23985,27277,28583),Set.of(),false).capability("powered-magic"));
        assertEquals(CapabilityState.UNKNOWN,repository.classify(Set.of(12924,23985),Set.of(),false).capability("ranged-weapon"));
    }
    @Test public void recommendedLabelsAndRecipesAreValidatedImmutableUiValues()
    {
        assertTrue(repository.definition("nonexistent").isEmpty());
        assertEquals("Supported crossbow",repository.definition("crossbow").get().label());
        assertTrue(repository.recommendedCapabilities().contains("vorkath-weapon"));
        assertFalse(repository.recommendedCapabilities().contains("twisted-bow"));
        assertEquals(Set.of("melee-weapon","vorkath-weapon"),repository.recommendedFor("Vorkath"));
        for (var definition : repository.recommendedDefinitionsFor("Vorkath"))
            assertTrue(repository.recommendedFor("Vorkath").contains(definition.id()));
        assertTrue(repository.recommendedDefinitionsFor("Unknown activity").isEmpty());
        try { repository.recommendedDefinitions().clear(); fail("Mutable definitions"); } catch (UnsupportedOperationException expected) { }
        try { repository.definition("crossbow").get().itemIds().clear(); fail("Mutable item IDs"); } catch (UnsupportedOperationException expected) { }
        try { repository.definition("vorkath-weapon").get().alternatives().get(0).clear(); fail("Mutable recipe"); } catch (UnsupportedOperationException expected) { }
        try { repository.recommendations().clear(); fail("Mutable recommendations"); } catch (UnsupportedOperationException expected) { }
    }
    @Test public void everyRecognizedItemIdExistsInPinnedRuneLiteSdk() throws ReflectiveOperationException
    {
        Set<Integer> officialIds = new HashSet<>();
        for (Field field : net.runelite.api.ItemID.class.getFields())
            if (field.getType() == int.class) officialIds.add(field.getInt(null));
        for (var definition : repository.definitions()) for (int id : definition.itemIds())
            assertTrue("Unverified SDK item ID " + id + " for " + definition.id(),officialIds.contains(id));
    }
    @Test public void invalidRuleCataloguesFailRatherThanSilentlyChangeOwnership() throws IOException
    {
        JsonObject source;
        try (Reader reader = new InputStreamReader(getClass().getResourceAsStream("/com/caroute/gear-capabilities.json"),StandardCharsets.UTF_8))
        { source = new JsonParser().parse(reader).getAsJsonObject(); }
        JsonObject duplicate = source.deepCopy();
        duplicate.getAsJsonArray("capabilities").add(duplicate.getAsJsonArray("capabilities").get(0).deepCopy()); reject(duplicate);
        JsonObject badItem = source.deepCopy(); badItem.getAsJsonArray("capabilities").get(0).getAsJsonObject().getAsJsonArray("itemIds").add(-1); reject(badItem);
        JsonObject fractional = source.deepCopy(); fractional.getAsJsonArray("capabilities").get(0).getAsJsonObject().getAsJsonArray("itemIds").add(9185.5); reject(fractional);
        JsonObject unknown = source.deepCopy(); unknown.getAsJsonArray("capabilities").get(5).getAsJsonObject().getAsJsonArray("anyOf").get(0).getAsJsonArray().add("unmapped-capability"); reject(unknown);
        JsonObject cycle = source.deepCopy(); JsonObject first = cycle.getAsJsonArray("capabilities").get(0).getAsJsonObject();
        first.add("itemIds",new JsonArray()); JsonArray terms = new JsonArray(); terms.add("basic-ranged"); JsonArray any = new JsonArray(); any.add(terms); first.add("anyOf",any); reject(cycle);
        JsonObject badOverlay = source.deepCopy(); badOverlay.getAsJsonArray("recommendations").get(0).getAsJsonObject().getAsJsonArray("recommendedGear").add("unmapped-capability"); reject(badOverlay);
        JsonObject duplicateRecommended = source.deepCopy(); JsonArray recommended = duplicateRecommended.getAsJsonArray("recommendations").get(0).getAsJsonObject().getAsJsonArray("recommendedGear"); recommended.add(recommended.get(0)); reject(duplicateRecommended);
    }
    private void reject(JsonObject source)
    {
        try { GearCapabilityRepository.load(new ByteArrayInputStream(source.toString().getBytes(StandardCharsets.UTF_8))); fail("Invalid gear catalogue accepted"); }
        catch (IllegalArgumentException expected) { assertTrue(expected.getMessage().contains("gear")); }
    }
}
