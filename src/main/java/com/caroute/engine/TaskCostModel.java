package com.caroute.engine;

import com.caroute.domain.*;
import java.util.*;

final class TaskCostModel
{
    private final AcquisitionCostProvider acquisition;
    TaskCostModel(AcquisitionCostProvider acquisition){this.acquisition=acquisition;}
    TaskAssessment assess(CombatAchievement task,Eligibility eligibility,PlayerProfile profile,RoutingPreferences prefs)
    {
        Map<String,Double> components=new LinkedHashMap<>();List<String> reasons=new ArrayList<>(eligibility.reasons());Confidence confidence=eligibility.confidence();
        double count=task.completionCount();
        if(task.kcThreshold()>0)
        {
            Integer kc=profile.killCounts().get(task.activity());count=Math.max(1,task.kcThreshold()-(kc==null?0:kc));
            if(kc==null){confidence=Confidence.LOW;reasons.add("KC is unknown; ETA assumes no observed progress, not verified zero");}
            else if(kc>=task.kcThreshold()){confidence=Confidence.LOW;reasons.add("Observed KC exceeds this incomplete task; authoritative game completion needs checking");}
        }
        double encounter=count*task.secondsPerKill()/60.0;
        Integer observedKc=profile.killCounts().get(task.activity());
        double competence=observedKc==null?1.0:observedKc>=150?0.35:observedKc>=25?0.6:1.0;
        double effectiveAttempts=1.0+(task.baseAttempts()-1.0)*competence;
        components.put("encounterWork",encounter);components.put("retryWork",encounter*(effectiveAttempts-1));components.put("setup",task.activity().equals(prefs.currentActivity())?0.0:task.setupMinutes());
        double acquisitionMinutes=0,gearPenalty=0;
        for(String capability:new TreeSet<>(task.recommendedGear()))
        {
            if(profile.gear().capability(capability)==GearProfile.CapabilityState.PRESENT)continue;
            gearPenalty+=RoutingTuning.MISSING_RECOMMENDED_GEAR_PENALTY;
            reasons.add("Gear readiness adds " + RoutingTuning.MISSING_RECOMMENDED_GEAR_PENALTY + " planning-cost units for " + capability + "; this is not elapsed time or an access requirement");
            if(profile.gear().capability(capability)==GearProfile.CapabilityState.UNKNOWN && !profile.gear().activePreparationEstimates().containsKey(capability)){confidence=Confidence.LOW;reasons.add("Gear preparation time is unestimated because this capability's ownership is unknown");continue;}
            AcquisitionCost cost=Objects.requireNonNull(acquisition.assess(profile.accountType(),capability,profile.gear()));confidence=Confidence.lowest(confidence,cost.confidence());
            if(cost.state()==AcquisitionCost.State.KNOWN){acquisitionMinutes+=cost.activeMinutes();components.put("acquire:"+capability,cost.activeMinutes());reasons.add(cost.reason() + " for " + capability + ": " + cost.activeMinutes() + " active minutes; "
                + (cost.hasWaitingEstimate()?cost.waitingMinutes()+" waiting minutes":"waiting time unknown") + "; "
                + (cost.hasResourceEstimate()?"resource cost "+cost.resourceCost():"resource cost unknown"));}
            else reasons.add(cost.reason() + "; acquisition time is excluded from the encounter ETA");
        }
        components.put("acquisitionActive",acquisitionMinutes);
        double statPenalty=0;for(Map.Entry<String,Integer> stat:Readiness.levels(task,profile).entrySet()){Integer level=profile.skills().get(stat.getKey());if(level!=null){int gap=Math.max(0,stat.getValue()-level);statPenalty+=gap*gap*RoutingTuning.STAT_COST_PER_SQUARED_LEVEL;}}
        components.put("statReadiness",statPenalty);components.put("gearReadiness",gearPenalty);
        components.put("learningMechanics",Math.max(0,task.difficulty()-1)*0.5);
        components.put("groupCoordination",task.flags().contains(TaskFlag.GROUP)?3.0:0.0);
        double preference=0;for(TaskFlag flag:task.flags())if(prefs.preference(flag)==Preference.PREFER_NOT){preference+=RoutingTuning.PREFER_NOT_PENALTY;reasons.add("Prefer not: " + flag.name().toLowerCase(Locale.ROOT) + " adds planning cost");}
        components.put("userPreference",preference);
        if(!task.timingKnown()){confidence=Confidence.LOW;reasons.add("Encounter duration/attempts are heuristic estimates");}
        components.put("uncertainty",confidence==Confidence.LOW?RoutingTuning.UNKNOWN_FACT_PENALTY:0.0);
        double physical=encounter*effectiveAttempts+components.get("setup")+acquisitionMinutes+components.get("groupCoordination");
        double planning=components.entrySet().stream().filter(e->!e.getKey().startsWith("acquire:")).mapToDouble(e->e.getValue()).sum();
        return new TaskAssessment(task,eligibility,physical,planning,components,reasons,confidence);
    }
}
