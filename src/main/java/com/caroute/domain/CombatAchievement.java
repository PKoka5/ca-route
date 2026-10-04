package com.caroute.domain;

import java.util.*;

public final class CombatAchievement
{
    private final int id, kcThreshold, completionCount, difficulty, activityDifficulty;
    private final String name, activity, description, type, wikiUrl, overlapGroup, provenance;
    private final AchievementTier tier;
    private final Map<String,Integer> requiredSkills, recommendedSkills;
    private final Map<String,QuestStatus> quests;
    private final Set<String> minimumGear, recommendedGear;
    private final Set<TaskFlag> flags;
    private final double secondsPerKill, baseAttempts, setupMinutes;
    private final boolean requirementsKnown, timingKnown;
    private final Requirement requirement;
    private final List<Map<String,Integer>> recommendedAlternatives;
    private final List<String> playerPreparation;
    private final List<String> quickGuide;
    private CombatAchievement(Builder b)
    {
        id=b.id; name=b.name; activity=b.activity; tier=b.tier; description=b.description; type=b.type; wikiUrl=b.wikiUrl;
        overlapGroup=b.overlapGroup; provenance=b.provenance; kcThreshold=b.kcThreshold; completionCount=b.completionCount; difficulty=b.difficulty;
        activityDifficulty=b.activityDifficulty==0?difficulty:b.activityDifficulty;
        requiredSkills=Immutable.map(b.requiredSkills); recommendedSkills=Immutable.map(b.recommendedSkills); quests=Immutable.map(b.quests);
        minimumGear=Immutable.set(b.minimumGear); recommendedGear=Immutable.set(b.recommendedGear); flags=Immutable.set(b.flags);
        secondsPerKill=b.secondsPerKill; baseAttempts=b.baseAttempts; setupMinutes=b.setupMinutes; requirementsKnown=b.requirementsKnown; timingKnown=b.timingKnown;
        List<Requirement> terms = new ArrayList<>(); requiredSkills.forEach((s,l)->terms.add(Requirement.skill(s,l)));
        quests.forEach((q,s)->terms.add(Requirement.quest(q,s))); minimumGear.forEach(g->terms.add(Requirement.gear(g)));
        if (b.requirement != null) terms.add(b.requirement);
        requirement=Requirement.all(terms.toArray(new Requirement[0]));
        List<Map<String,Integer>> alternatives = new ArrayList<>();
        for (Map<String,Integer> group : b.recommendedAlternatives) alternatives.add(Immutable.map(group));
        recommendedAlternatives = Immutable.list(alternatives);
        playerPreparation = Immutable.list(b.playerPreparation);
        quickGuide = Immutable.list(b.quickGuide);
    }
    public static Builder builder(int id,String name,String activity,AchievementTier tier) { return new Builder(id,name,activity,tier); }
    public int id(){return id;} public String name(){return name;} public String activity(){return activity;} public AchievementTier tier(){return tier;} public int points(){return tier.points();}
    public String description(){return description;} public String type(){return type;} public String wikiUrl(){return wikiUrl;} public String overlapGroup(){return overlapGroup;} public String provenance(){return provenance;}
    public Map<String,Integer> requiredSkills(){return requiredSkills;} public Map<String,Integer> recommendedSkills(){return recommendedSkills;} public Map<String,QuestStatus> quests(){return quests;}
    public Set<String> minimumGear(){return minimumGear;} public Set<String> recommendedGear(){return recommendedGear;} public Set<TaskFlag> flags(){return flags;}
    public int kcThreshold(){return kcThreshold;} public int completionCount(){return completionCount;} public int difficulty(){return difficulty;}
    /** Hand-rated activity baseline, separate from the challenge rating and CA reward tier. */
    public int activityDifficulty(){return activityDifficulty;}
    public double secondsPerKill(){return secondsPerKill;} public double baseAttempts(){return baseAttempts;} public double setupMinutes(){return setupMinutes;}
    public boolean requirementsKnown(){return requirementsKnown;} public boolean timingKnown(){return timingKnown;} public Requirement requirement(){return requirement;}
    public List<Map<String,Integer>> recommendedAlternatives(){return recommendedAlternatives;}
    /** Practical, sourced equipment/access reminders; these do not add access locks. */
    public List<String> playerPreparation(){return playerPreparation;}
    /** Static task-specific instructions; opening them does not start combat or travel guidance. */
    public List<String> quickGuide(){return quickGuide;}
    public static final class Builder
    {
        private final int id; private final String name,activity; private final AchievementTier tier;
        private String description="",type="",wikiUrl="",overlapGroup="",provenance="";
        private Map<String,Integer> requiredSkills=Collections.emptyMap(),recommendedSkills=Collections.emptyMap();
        private Map<String,QuestStatus> quests=Collections.emptyMap(); private Set<String> minimumGear=Collections.emptySet(),recommendedGear=Collections.emptySet();
        private Set<TaskFlag> flags=Collections.emptySet(); private int kcThreshold=0,completionCount=1,difficulty=1,activityDifficulty=0;
        private double secondsPerKill=180,baseAttempts=1,setupMinutes=2; private boolean requirementsKnown=false,timingKnown=false; private Requirement requirement;
        private List<Map<String,Integer>> recommendedAlternatives = Collections.emptyList();
        private List<String> playerPreparation = Collections.emptyList();
        private List<String> quickGuide = Collections.emptyList();
        private Builder(int id,String name,String activity,AchievementTier tier) { if(id<0)throw new IllegalArgumentException("Negative task ID"); this.id=id; this.name=Objects.requireNonNull(name); this.activity=Objects.requireNonNull(activity); this.tier=Objects.requireNonNull(tier); if(name.trim().isEmpty()||activity.trim().isEmpty())throw new IllegalArgumentException("Empty task identity"); }
        public Builder description(String v){description=Objects.requireNonNull(v);return this;} public Builder type(String v){type=Objects.requireNonNull(v);return this;} public Builder wikiUrl(String v){wikiUrl=Objects.requireNonNull(v);return this;}
        public Builder requiredSkills(Map<String,Integer> v){requiredSkills=Immutable.map(v);return this;} public Builder recommendedSkills(Map<String,Integer> v){recommendedSkills=Immutable.map(v);return this;} public Builder quests(Map<String,QuestStatus> v){quests=Immutable.map(v);return this;}
        public Builder minimumGear(Set<String> v){minimumGear=Immutable.set(v);return this;} public Builder recommendedGear(Set<String> v){recommendedGear=Immutable.set(v);return this;} public Builder flags(Set<TaskFlag> v){flags=Immutable.set(v);return this;}
        public Builder kcThreshold(int v){if(v<0)throw new IllegalArgumentException("Negative KC");kcThreshold=v;return this;} public Builder completionCount(int v){if(v<1)throw new IllegalArgumentException("Invalid completion count");completionCount=v;return this;}
        public Builder secondsPerKill(double v){secondsPerKill=Immutable.nonNegative(v,"secondsPerKill");if(v==0)throw new IllegalArgumentException("Zero encounter time");return this;} public Builder baseAttempts(double v){baseAttempts=Immutable.nonNegative(v,"baseAttempts");if(v<1)throw new IllegalArgumentException("Attempts below one");return this;}
        public Builder difficulty(int v){if(v<1||v>10)throw new IllegalArgumentException("Difficulty outside 1..10");difficulty=v;return this;} public Builder setupMinutes(double v){setupMinutes=Immutable.nonNegative(v,"setupMinutes");return this;}
        public Builder activityDifficulty(int v){if(v<1||v>10)throw new IllegalArgumentException("Activity difficulty outside 1..10");activityDifficulty=v;return this;}
        public Builder requirementsKnown(boolean v){requirementsKnown=v;return this;} public Builder timingKnown(boolean v){timingKnown=v;return this;} public Builder overlapGroup(String v){overlapGroup=Objects.requireNonNull(v);return this;} public Builder provenance(String v){provenance=Objects.requireNonNull(v);return this;}
        public Builder requirement(Requirement v){requirement=Objects.requireNonNull(v);return this;}
        public Builder recommendedAlternatives(List<Map<String,Integer>> v){recommendedAlternatives=Immutable.list(v);return this;}
        public Builder playerPreparation(List<String> v){playerPreparation=Immutable.list(v);return this;}
        public Builder quickGuide(List<String> v)
        {
            for (String step : v) if (step == null || step.trim().isEmpty()) throw new IllegalArgumentException("Empty quick guide step");
            quickGuide=Immutable.list(v);return this;
        }
        public CombatAchievement build(){recommendedSkills.forEach((s,l)->{Objects.requireNonNull(s);if(l<1||l>99)throw new IllegalArgumentException("Invalid recommended skill");});return new CombatAchievement(this);}
    }
}
