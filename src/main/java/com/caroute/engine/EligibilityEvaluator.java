package com.caroute.engine;

import com.caroute.domain.*;
import java.util.*;

final class EligibilityEvaluator
{
    private enum Fact { SATISFIED, UNMET, UNKNOWN }
    private static final class Result
    {
        final Fact fact; final EligibilityState state; final List<String> reasons;
        Result(Fact fact,EligibilityState state,String reason){this(fact,state,reason==null?Collections.emptyList():Collections.singletonList(reason));}
        Result(Fact fact,EligibilityState state,List<String> reasons){this.fact=fact;this.state=state;this.reasons=reasons;}
    }
    Eligibility evaluate(CombatAchievement task,PlayerProfile profile,RoutingPreferences prefs)
    {
        List<String> reasons=new ArrayList<>();
        if(profile.completedTaskIds().contains(task.id())) return new Eligibility(EligibilityState.COMPLETED,Collections.singletonList("Completed in the game"),Confidence.HIGH,false,false);
        Result requirements=evaluate(task.requirement(),profile); reasons.addAll(requirements.reasons);
        boolean unknown=requirements.fact==Fact.UNKNOWN || !task.requirementsKnown();
        if(!task.requirementsKnown())reasons.add("Access requirements are not completely verified; check before starting");
        EligibilityState state=requirements.fact==Fact.UNMET?requirements.state:unknown?EligibilityState.UNKNOWN_REQUIREMENT:EligibilityState.AVAILABLE;
        boolean admitted=requirements.fact!=Fact.UNMET;
        int maxGap=0,totalGap=0; boolean softUnknown=false;
        for(Map.Entry<String,Integer> e:Readiness.levels(task,profile).entrySet())
        {
            Integer actual=profile.skills().get(e.getKey());
            if(actual==null){softUnknown=true;reasons.add("Recommended " + e.getKey() + " level is unreadable");continue;}
            int gap=Math.max(0,e.getValue()-actual);maxGap=Math.max(maxGap,gap);totalGap+=gap;
            if(gap>0)reasons.add("Recommended " + e.getKey() + " " + e.getValue() + "; current " + actual + " (readiness, not an access lock)");
        }
        boolean severe=maxGap>=RoutingTuning.SEVERE_SKILL_SHORTFALL||totalGap>=RoutingTuning.SEVERE_TOTAL_SHORTFALL;
        if (!task.requirementsKnown() && task.recommendedSkills().isEmpty() && task.recommendedAlternatives().isEmpty())
        {
            int highestKnownCombat = Arrays.asList("Attack", "Strength", "Ranged", "Magic").stream()
                .filter(profile.skills()::containsKey).mapToInt(s -> profile.skills().get(s)).max().orElse(99);
            if (highestKnownCombat < 70 && profile.killCounts().getOrDefault(task.activity(), 0) == 0 && !prefs.allowStretch())
            {
                admitted = false; softUnknown = true;
                reasons.add("Prepare/check first: readiness metadata is missing for an inexperienced account; this is not a game access lock");
            }
        }
        boolean knownUndergeared=false;
        for(String capability:task.recommendedGear())
        {
            if(profile.gear().capability(capability)==GearProfile.CapabilityState.PRESENT)continue;
            if(profile.gear().capability(capability)==GearProfile.CapabilityState.MISSING){knownUndergeared=true;reasons.add("Recommended gear capability missing: " + capability + " (readiness, not an access lock)");}
            else{softUnknown=true;reasons.add("Recommended gear capability unconfirmed: " + capability + " (partial ownership coverage)");}
        }
        if(state==EligibilityState.AVAILABLE&&knownUndergeared)state=EligibilityState.AVAILABLE_BUT_UNDERGEARED;
        if(severe){reasons.add("Prepare first: substantial recommended-stat shortfall" + (prefs.allowStretch()?"; stretch override enabled":""));admitted&=prefs.allowStretch();}
        if(prefs.excludedTaskIds().contains(task.id())){state=EligibilityState.CONTENT_EXCLUDED;admitted=false;reasons.add("Manually excluded from recommendations");}
        for(TaskFlag flag:task.flags())if(prefs.preference(flag)==Preference.AVOID){state=EligibilityState.CONTENT_EXCLUDED;admitted=false;reasons.add("Avoid preference: " + flag.name().toLowerCase(Locale.ROOT));}
        if(prefs.deferredTaskIds().contains(task.id())&&state!=EligibilityState.CONTENT_EXCLUDED){state=EligibilityState.DEFERRED;admitted=false;reasons.add("Deferred until restored");}
        if(reasons.isEmpty())reasons.add("Known access requirements satisfied");
        Confidence confidence=unknown||softUnknown?Confidence.LOW:maxGap>0||knownUndergeared?Confidence.MEDIUM:Confidence.HIGH;
        return new Eligibility(state,reasons,confidence,admitted,admitted&&unknown);
    }
    private Result evaluate(Requirement requirement,PlayerProfile profile)
    {
        switch(requirement.kind())
        {
            case SKILL:
                Integer skill=profile.skills().get(requirement.key());
                return skill==null?new Result(Fact.UNKNOWN,EligibilityState.UNKNOWN_REQUIREMENT,"Required " + requirement.key() + " level is unknown"):skill<requirement.level()?new Result(Fact.UNMET,EligibilityState.SKILL_LOCKED,"Requires " + requirement.key() + " " + requirement.level() + "; current " + skill):new Result(Fact.SATISFIED,EligibilityState.AVAILABLE,(String)null);
            case QUEST:
                QuestStatus quest=profile.quests().get(requirement.key());
                return quest==null?new Result(Fact.UNKNOWN,EligibilityState.UNKNOWN_REQUIREMENT,"Quest status unknown: " + requirement.key()):quest.ordinal()<requirement.questStatus().ordinal()?new Result(Fact.UNMET,EligibilityState.QUEST_LOCKED,"Requires " + requirement.key() + " " + requirement.questStatus().name().toLowerCase(Locale.ROOT)):new Result(Fact.SATISFIED,EligibilityState.AVAILABLE,(String)null);
            case GEAR:
                switch(profile.gear().capability(requirement.key()))
                {
                    case PRESENT: return new Result(Fact.SATISFIED,EligibilityState.AVAILABLE,(String)null);
                    case MISSING: return new Result(Fact.UNMET,EligibilityState.GEAR_LOCKED,"Required gear capability missing: " + requirement.key());
                    default: return new Result(Fact.UNKNOWN,EligibilityState.UNKNOWN_REQUIREMENT,"Required gear ownership unknown: " + requirement.key());
                }
            default:
                boolean any=requirement.kind()==Requirement.Kind.ANY; List<String> reasons=new ArrayList<>(); Result unmet=null; boolean unknown=false;
                for(Requirement child:requirement.children())
                {
                    Result result=evaluate(child,profile);
                    if(any&&result.fact==Fact.SATISFIED)return new Result(Fact.SATISFIED,EligibilityState.AVAILABLE,(String)null);
                    reasons.addAll(result.reasons);if(result.fact==Fact.UNMET&&unmet==null)unmet=result;unknown|=result.fact==Fact.UNKNOWN;
                }
                if(any){if(unknown)return new Result(Fact.UNKNOWN,EligibilityState.UNKNOWN_REQUIREMENT,reasons);return new Result(Fact.UNMET,unmet==null?EligibilityState.UNKNOWN_REQUIREMENT:unmet.state,reasons);}
                if(unmet!=null)return new Result(Fact.UNMET,unmet.state,reasons);
                return new Result(unknown?Fact.UNKNOWN:Fact.SATISFIED,unknown?EligibilityState.UNKNOWN_REQUIREMENT:EligibilityState.AVAILABLE,reasons);
        }
    }
}
