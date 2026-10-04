package com.caroute.domain;

import java.util.*;

public final class PlayerProfile
{
    private final String accountKey; private final long version; private final boolean loaded,catalogueComplete;
    private final AccountType accountType; private final Set<Integer> completedTaskIds; private final int currentPoints;
    private final Map<AchievementTier,Integer> thresholds; private final Map<String,Integer> skills,killCounts; private final Map<String,QuestStatus> quests; private final GearProfile gear;
    private PlayerProfile(Builder b) {accountKey=b.accountKey;version=b.version;loaded=b.loaded;catalogueComplete=b.catalogueComplete;accountType=b.accountType;completedTaskIds=Immutable.set(b.completedTaskIds);currentPoints=b.currentPoints;thresholds=Immutable.map(b.thresholds);skills=Immutable.map(b.skills);killCounts=Immutable.map(b.killCounts);quests=Immutable.map(b.quests);gear=b.gear;}
    public static Builder builder(){return new Builder();}
    public String accountKey(){return accountKey;} public long version(){return version;} public boolean loaded(){return loaded;} public boolean catalogueComplete(){return catalogueComplete;} public AccountType accountType(){return accountType;}
    public Set<Integer> completedTaskIds(){return completedTaskIds;} public int currentPoints(){return currentPoints;} public Map<AchievementTier,Integer> thresholds(){return thresholds;} public Map<String,Integer> skills(){return skills;} public Map<String,Integer> killCounts(){return killCounts;} public Map<String,QuestStatus> quests(){return quests;} public GearProfile gear(){return gear;}
    public static final class Builder
    {
        private String accountKey=""; private long version; private boolean loaded,catalogueComplete;
        private AccountType accountType=AccountType.MAIN; private Set<Integer> completedTaskIds=Collections.emptySet(); private int currentPoints;
        private Map<AchievementTier,Integer> thresholds=Collections.emptyMap(); private Map<String,Integer> skills=Collections.emptyMap(),killCounts=Collections.emptyMap(); private Map<String,QuestStatus> quests=Collections.emptyMap(); private GearProfile gear=GearProfile.unknown();
        public Builder accountKey(String v){accountKey=Objects.requireNonNull(v);return this;} public Builder version(long v){version=v;return this;} public Builder loaded(boolean v){loaded=v;return this;} public Builder catalogueComplete(boolean v){catalogueComplete=v;return this;} public Builder accountType(AccountType v){accountType=Objects.requireNonNull(v);return this;}
        public Builder completedTaskIds(Set<Integer> v){completedTaskIds=Immutable.set(v);return this;} public Builder currentPoints(int v){if(v<0)throw new IllegalArgumentException("Negative points");currentPoints=v;return this;}
        public Builder thresholds(Map<AchievementTier,Integer> v){v.values().forEach(n->{if(n<0)throw new IllegalArgumentException("Negative threshold");});thresholds=Immutable.map(v);return this;} public Builder skills(Map<String,Integer> v){v.values().forEach(n->{if(n<1||n>99)throw new IllegalArgumentException("Invalid skill");});skills=Immutable.map(v);return this;} public Builder killCounts(Map<String,Integer> v){v.values().forEach(n->{if(n<0)throw new IllegalArgumentException("Negative KC");});killCounts=Immutable.map(v);return this;} public Builder quests(Map<String,QuestStatus> v){quests=Immutable.map(v);return this;} public Builder gear(GearProfile v){gear=Objects.requireNonNull(v);return this;}
        public PlayerProfile build(){return new PlayerProfile(this);}
    }
}
