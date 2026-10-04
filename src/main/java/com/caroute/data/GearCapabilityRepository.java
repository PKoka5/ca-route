package com.caroute.data;

import com.caroute.domain.GearProfile;
import com.caroute.domain.GearProfile.CapabilityState;
import com.google.gson.*;
import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Versioned ownership rules. No client access, network requests, DPS calculation, or inferred bank absence. */
public final class GearCapabilityRepository
{
    public static final class Definition
    {
        private final String id, label, description, source;
        private final Set<Integer> itemIds;
        private final List<List<String>> alternatives;
        private Definition(JsonObject row)
        {
            id = nonEmpty(row, "id"); label = nonEmpty(row, "label");
            if (!id.matches("[a-z][a-z0-9]*(?:-[a-z0-9]+)*")) throw new IllegalArgumentException("Invalid gear capability identity " + id);
            description = nonEmpty(row, "description"); source = GearCapabilityRepository.source(row, "source");
            Set<Integer> items = new TreeSet<>();
            for (JsonElement item : row.getAsJsonArray("itemIds"))
            {
                int value = integer(item, "item ID for " + id);
                if (value <= 0 || !items.add(value)) throw new IllegalArgumentException("Invalid/duplicate item ID for " + id);
            }
            itemIds = Collections.unmodifiableSet(items);
            List<List<String>> choices = new ArrayList<>();
            Set<List<String>> uniqueChoices = new HashSet<>();
            for (JsonElement choice : row.getAsJsonArray("anyOf"))
            {
                List<String> terms = strings(choice.getAsJsonArray());
                if (terms.isEmpty() || new HashSet<>(terms).size() != terms.size()) throw new IllegalArgumentException("Empty/duplicate gear rule terms for " + id);
                if (!uniqueChoices.add(new ArrayList<>(new TreeSet<>(terms)))) throw new IllegalArgumentException("Duplicate gear alternatives for " + id);
                choices.add(Collections.unmodifiableList(terms));
            }
            alternatives = Collections.unmodifiableList(choices);
            if (itemIds.isEmpty() == alternatives.isEmpty()) throw new IllegalArgumentException("A gear definition needs either item IDs or capability alternatives: " + id);
        }
        public String id() { return id; }
        public String label() { return label; }
        public String description() { return description; }
        public String source() { return source; }
        public Set<Integer> itemIds() { return itemIds; }
        /** Each inner list is an ALL recipe; the outer list supplies ANY alternatives. */
        public List<List<String>> alternatives() { return alternatives; }
    }
    public static final class Recommendation
    {
        private final int id;
        private final String name, activity, source, rationale;
        private final Set<String> recommended;
        private Recommendation(JsonObject row)
        {
            id = integer(row.get("taskId"), "task identity"); name = nonEmpty(row, "taskName"); activity = nonEmpty(row, "activity");
            if (id < 0) throw new IllegalArgumentException("Negative task identity");
            List<String> capabilities = strings(row.getAsJsonArray("recommendedGear"));
            if (new HashSet<>(capabilities).size() != capabilities.size()) throw new IllegalArgumentException("Duplicate recommended gear capability");
            recommended = Collections.unmodifiableSet(new TreeSet<>(capabilities));
            if (recommended.isEmpty()) throw new IllegalArgumentException("Empty gear recommendation");
            source = GearCapabilityRepository.source(row, "source"); rationale = nonEmpty(row, "rationale");
        }
        public int taskId() { return id; }
        public String taskName() { return name; }
        public String activity() { return activity; }
        public Set<String> capabilities() { return recommended; }
        public String source() { return source; }
        public String rationale() { return rationale; }
    }
    private final String version;
    private final List<Definition> definitions;
    private final Map<String, Definition> byId;
    private final Map<Integer, Recommendation> recommendations;
    private GearCapabilityRepository(JsonObject root)
    {
        if (integer(root.get("schemaVersion"), "schema version") != 1) throw new IllegalArgumentException("Unsupported gear schema");
        version = nonEmpty(root, "datasetVersion"); source(root, "itemIdSource");
        Map<String, Definition> rows = new LinkedHashMap<>();
        for (JsonElement row : root.getAsJsonArray("capabilities"))
        {
            Definition definition = new Definition(row.getAsJsonObject());
            if (rows.put(definition.id, definition) != null) throw new IllegalArgumentException("Duplicate gear capability " + definition.id);
        }
        if (rows.isEmpty()) throw new IllegalArgumentException("Empty gear catalogue");
        for (Definition definition : rows.values()) for (List<String> alternative : definition.alternatives) for (String term : alternative)
            if (!rows.containsKey(term)) throw new IllegalArgumentException("Unknown gear capability reference " + term);
        Set<String> visited = new HashSet<>();
        for (String id : rows.keySet()) visit(id, rows, visited, new HashSet<>());
        byId = Collections.unmodifiableMap(rows); definitions = Collections.unmodifiableList(new ArrayList<>(rows.values()));
        Map<Integer, Recommendation> tasks = new TreeMap<>();
        for (JsonElement row : root.getAsJsonArray("recommendations"))
        {
            Recommendation recommendation = new Recommendation(row.getAsJsonObject());
            if (tasks.put(recommendation.id, recommendation) != null) throw new IllegalArgumentException("Duplicate gear task overlay " + recommendation.id);
            for (String capability : recommendation.recommended) if (!rows.containsKey(capability)) throw new IllegalArgumentException("Unknown recommended gear " + capability);
        }
        recommendations = Collections.unmodifiableMap(tasks);
    }
    public static GearCapabilityRepository loadBundled()
    {
        InputStream in = GearCapabilityRepository.class.getResourceAsStream("/com/caroute/gear-capabilities.json");
        if (in == null) throw new IllegalStateException("Bundled gear capability metadata is missing");
        return load(in);
    }
    public static GearCapabilityRepository load(InputStream in)
    {
        try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8))
        { return new GearCapabilityRepository(new JsonParser().parse(reader).getAsJsonObject()); }
        catch (IOException | RuntimeException ex) { throw new IllegalArgumentException("Cannot load gear capability metadata: " + ex.getMessage(), ex); }
    }
    public List<Definition> definitions() { return definitions; }
    public String version() { return version; }
    public Optional<Definition> definition(String id) { return Optional.ofNullable(byId.get(Objects.requireNonNull(id))); }
    /** Canonical task identities and the soft rationale are available for review/UI descriptions. */
    public List<Recommendation> recommendations()
    { return Collections.unmodifiableList(new ArrayList<>(recommendations.values())); }
    /** Only top-level capabilities actually recommended by an overlay, ordered by display label. */
    public List<Definition> recommendedDefinitions()
    {
        Set<String> ids = new TreeSet<>();
        for (Recommendation recommendation : recommendations.values()) ids.addAll(recommendation.recommended);
        return definitionsFor(ids);
    }
    public List<Definition> recommendedDefinitionsFor(String activity)
    { return definitionsFor(recommendedFor(Objects.requireNonNull(activity))); }
    public Set<String> recommendedCapabilities()
    {
        Set<String> ids = new TreeSet<>();
        for (Definition definition : recommendedDefinitions()) ids.add(definition.id);
        return Collections.unmodifiableSet(ids);
    }
    private List<Definition> definitionsFor(Set<String> ids)
    {
        List<Definition> result = new ArrayList<>();
        for (String id : ids) result.add(byId.get(id));
        result.sort(Comparator.comparing(Definition::label).thenComparing(Definition::id));
        return Collections.unmodifiableList(result);
    }
    public Set<String> recommendedFor(String activity)
    {
        Set<String> result = new TreeSet<>();
        for (Recommendation row : recommendations.values()) if (row.activity.equals(activity)) result.addAll(row.recommended);
        return Collections.unmodifiableSet(result);
    }
    /** Overlay identity must match exactly: canonical completion indices alone are insufficient. */
    public Set<String> recommendedFor(int taskId, String name, String activity)
    {
        Recommendation row = recommendations.get(taskId);
        if (row == null) return Collections.emptySet();
        if (!row.name.equals(name) || !row.activity.equals(activity)) throw new IllegalArgumentException("Stale gear overlay identity for task " + taskId);
        return row.recommended;
    }
    public GearProfile classify(Set<Integer> equipped, Set<Integer> owned, boolean ownershipComplete)
    { return classify(equipped, owned, ownershipComplete, Collections.emptyMap()); }
    public GearProfile classify(Set<Integer> equipped, Set<Integer> owned, boolean ownershipComplete, Map<String, Boolean> overrides)
    {
        Set<Integer> observed = new HashSet<>(owned); observed.addAll(equipped);
        Set<String> present = new TreeSet<>(), missing = new TreeSet<>();
        for (Definition row : definitions) if (!Collections.disjoint(row.itemIds, observed)) present.add(row.id);
        // Explicit owned compound kits imply their single, unambiguous ALL recipe's components.
        for (Map.Entry<String, Boolean> override : overrides.entrySet())
        {
            if (!byId.containsKey(override.getKey())) throw new IllegalArgumentException("Unknown gear override " + override.getKey());
            Objects.requireNonNull(override.getValue());
            if (override.getValue()) markPresent(override.getKey(), present);
        }
        for (Map.Entry<String, Boolean> override : overrides.entrySet()) if (!override.getValue() && !present.contains(override.getKey())) missing.add(override.getKey());
        boolean changed;
        do
        {
            changed = false;
            for (Definition row : definitions)
            {
                if (present.contains(row.id) || row.alternatives.isEmpty()) continue;
                CapabilityState state = derived(row, present, missing, ownershipComplete);
                if (state == CapabilityState.PRESENT) { present.add(row.id); missing.remove(row.id); changed = true; }
                else if (state == CapabilityState.MISSING) changed |= missing.add(row.id);
            }
        } while (changed);
        return GearProfile.of(equipped, owned, present, missing, ownershipComplete);
    }
    private void markPresent(String id, Set<String> present)
    {
        if (!present.add(id)) return;
        Definition row = byId.get(id);
        if (row.alternatives.size() == 1) for (String term : row.alternatives.get(0)) markPresent(term, present);
    }
    private static CapabilityState derived(Definition row, Set<String> present, Set<String> missing, boolean complete)
    {
        boolean unknownAlternative = false;
        for (List<String> alternative : row.alternatives)
        {
            boolean unknown = false, failed = false;
            for (String term : alternative)
            {
                if (present.contains(term)) continue;
                if (missing.contains(term) || complete) { failed = true; break; }
                unknown = true;
            }
            if (!failed && !unknown) return CapabilityState.PRESENT;
            if (!failed) unknownAlternative = true;
        }
        return unknownAlternative ? CapabilityState.UNKNOWN : CapabilityState.MISSING;
    }
    private static void visit(String id, Map<String, Definition> rows, Set<String> visited, Set<String> active)
    {
        if (visited.contains(id)) return;
        if (!active.add(id)) throw new IllegalArgumentException("Cyclic gear capability " + id);
        for (List<String> alternative : rows.get(id).alternatives) for (String term : alternative) visit(term, rows, visited, active);
        active.remove(id); visited.add(id);
    }
    private static String nonEmpty(JsonObject row, String key)
    {
        String value = row.get(key).getAsString();
        if (value.trim().isEmpty()) throw new IllegalArgumentException("Empty gear field " + key);
        return value;
    }
    private static int integer(JsonElement element, String label)
    {
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber())
            throw new IllegalArgumentException("Invalid gear number " + label);
        try { return element.getAsBigDecimal().intValueExact(); }
        catch (ArithmeticException | NumberFormatException ex) { throw new IllegalArgumentException("Invalid gear integer " + label, ex); }
    }
    private static String source(JsonObject row, String key)
    {
        String value = nonEmpty(row, key);
        URI uri = URI.create(value);
        if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getHost().trim().isEmpty())
            throw new IllegalArgumentException("Gear provenance must be an HTTPS source");
        return value;
    }
    private static List<String> strings(JsonArray rows)
    {
        List<String> values = new ArrayList<>(); for (JsonElement row : rows) { String value = row.getAsString(); if (value.trim().isEmpty()) throw new IllegalArgumentException("Empty gear reference"); values.add(value); } return values;
    }
}
