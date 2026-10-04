package com.caroute.engine;

import com.caroute.domain.*;
import java.util.*;
import java.util.stream.Collectors;

/** Builds feasible options and evaluates shared work once, for both selection and display. */
final class SessionOptimizer
{
    static final class Options
    {
        final List<BossSession> sessions;
        final boolean exhaustive;
        Options(List<BossSession> sessions, boolean exhaustive) { this.sessions = sessions; this.exhaustive = exhaustive; }
    }

    Options options(List<TaskAssessment> group, PlayerProfile profile, RoutingPreferences prefs)
    {
        group.sort(Comparator.comparingInt(a -> a.task().id()));
        List<TaskAssessment> mandatory = group.stream().filter(a -> prefs.pinnedTaskIds().contains(a.task().id())).collect(Collectors.toList());
        List<TaskAssessment> optional = group.stream().filter(a -> !prefs.pinnedTaskIds().contains(a.task().id())).collect(Collectors.toList());
        boolean exhaustive = optional.size() <= RoutingTuning.MAX_EXHAUSTIVE_CHOICES;
        Map<String, BossSession> unique = new TreeMap<>();
        if (exhaustive)
        {
            for (int mask = 0; mask < (1 << optional.size()); mask++)
            {
                List<TaskAssessment> chosen = new ArrayList<>(mandatory);
                for (int i = 0; i < optional.size(); i++) if ((mask & (1 << i)) != 0) chosen.add(optional.get(i));
                add(unique, chosen, group, profile, prefs);
            }
        }
        else
        {
            // Bounded, deterministic candidate generation; never claim general optimality.
            add(unique, mandatory, group, profile, prefs);
            for (TaskAssessment item : optional)
            {
                List<TaskAssessment> chosen = new ArrayList<>(mandatory); chosen.add(item);
                add(unique, chosen, group, profile, prefs);
            }
            optional.sort(Comparator.comparingDouble((TaskAssessment a) -> (prefs.objective()==RouteObjective.MAXIMUM_POINTS_PER_HOUR?a.estimatedMinutes():ObjectivePolicy.taskOrderingCost(a,profile,prefs)) / a.task().points()).thenComparingInt(a -> a.task().id()));
            List<TaskAssessment> prefix = new ArrayList<>(mandatory);
            for (TaskAssessment item : optional) { prefix.add(item); add(unique, prefix, group, profile, prefs); }
        }
        // There are no future task dependencies in the supported independent-activity model.
        Map<String, BossSession> byPoints = new TreeMap<>();
        for (BossSession candidate : unique.values())
        {
            String signature=String.format(Locale.ROOT,"%08d",candidate.points())+"|"+String.join(",",SharedPreparation.acquisitions(candidate).keySet());
            BossSession old = byPoints.get(signature);
            if (old == null || better(candidate,old,prefs.objective()))
                byPoints.put(signature, candidate);
        }
        return new Options(new ArrayList<>(byPoints.values()), exhaustive);
    }

    private void add(Map<String, BossSession> unique, List<TaskAssessment> selected, List<TaskAssessment> group,
        PlayerProfile profile, RoutingPreferences prefs)
    {
        if (selected.isEmpty()) return;
        List<TaskAssessment> closed = closure(selected, group);
        BossSession session = evaluate(closed, profile, prefs);
        unique.putIfAbsent(key(session), session);
    }

    private List<TaskAssessment> closure(List<TaskAssessment> selected, List<TaskAssessment> group)
    {
        Map<Integer, TaskAssessment> tasks = new TreeMap<>();
        selected.forEach(a -> tasks.put(a.task().id(), a));
        int maxKc = selected.stream().mapToInt(a -> a.task().kcThreshold()).max().orElse(0);
        // Any successful encounter can imply a one-kill entry task. Larger KC milestones
        // are implied only by an explicit KC target; execution adds are never kill counters.
        int implied = Math.max(1, maxKc);
        for (TaskAssessment item : group)
            if (item.task().kcThreshold() > 0 && item.task().kcThreshold() <= implied)
                tasks.put(item.task().id(), item);
        return new ArrayList<>(tasks.values());
    }

    BossSession evaluate(List<TaskAssessment> tasks, PlayerProfile profile, RoutingPreferences prefs)
    {
        tasks = new ArrayList<>(tasks); tasks.sort(Comparator.comparingInt(a -> a.task().id()));
        if (prefs.objective() == RouteObjective.EASIEST_PROGRESSION)
            tasks.sort(Comparator.comparingInt((TaskAssessment a) -> ProgressionDifficulty.stage(a.task(), profile)).thenComparingInt(a -> a.task().id()));
        String activity = tasks.get(0).task().activity();
        double setup = 0, execution = 0, progression = 0, penalties = 0, coordination = 0;
        int points = 0, maxThreshold = 0;
        Confidence confidence = Confidence.HIGH;
        boolean conditional = false;
        Map<String, Double> overlapping = new TreeMap<>();
        Map<String, Double> acquisition = new TreeMap<>();
        Set<String> missingGear = new TreeSet<>(), presentGear = new TreeSet<>();
        List<String> reasons = new ArrayList<>();
        for (TaskAssessment assessment : tasks)
        {
            CombatAchievement task = assessment.task(); points += task.points();
            confidence = Confidence.lowest(confidence, assessment.confidence());
            conditional |= assessment.eligibility().conditional();
            Map<String, Double> c = assessment.components();
            for (String capability : task.recommendedGear())
            {
                if (profile.gear().capability(capability) == GearProfile.CapabilityState.PRESENT) presentGear.add(capability);
                else missingGear.add(capability);
            }
            setup = Math.max(setup, c.getOrDefault("setup", 0.0));
            coordination = Math.max(coordination, c.getOrDefault("groupCoordination", 0.0));
            double work = c.getOrDefault("encounterWork", 0.0) + c.getOrDefault("retryWork", 0.0);
            if (task.kcThreshold() > 0)
            {
                maxThreshold = Math.max(maxThreshold, task.kcThreshold());
                // KC milestones all share a counter. Use the largest remaining estimate,
                // not a sum of per-rung rates that changes its assumptions halfway through.
                progression = Math.max(progression, work);
            }
            else if (!task.overlapGroup().isEmpty()) overlapping.merge(task.overlapGroup(), work, Math::max);
            else execution += work;
            for (Map.Entry<String,Double> component : c.entrySet())
                if (component.getKey().startsWith("acquire:")) acquisition.merge(component.getKey(), component.getValue(), Math::max);
            for (Map.Entry<String, Double> component : c.entrySet())
                if (!component.getKey().startsWith("acquire:") && !Arrays.asList("encounterWork", "retryWork", "setup", "acquisitionActive", "groupCoordination", "gearReadiness").contains(component.getKey()))
                    penalties += component.getValue();
        }
        // Preparing one recommended capability serves every task in the same session.
        penalties += missingGear.size() * RoutingTuning.MISSING_RECOMMENDED_GEAR_PENALTY;
        execution += overlapping.values().stream().mapToDouble(Double::doubleValue).sum();
        double prepare = acquisition.values().stream().mapToDouble(Double::doubleValue).sum();
        double minutes = setup + progression + execution + prepare + coordination;
        reasons.add(tasks.size() + " incomplete tasks; " + points + " projected points");
        reasons.add(String.format(Locale.ROOT, "Estimated %.1f minutes; setup charged once (%.1f min)", minutes, setup));
        if (tasks.size() > 1) reasons.add("Tasks share this activity, reducing unnecessary boss switching");
        if (maxThreshold > 0) reasons.add("One KC progression toward " + maxThreshold + "; nested thresholds share the grind");
        if (!presentGear.isEmpty()) reasons.add("Supported gear confirmed: " + String.join(", ", presentGear));
        if (!missingGear.isEmpty()) reasons.add("Check recommended gear before starting: " + String.join(", ", missingGear) + "; preparation is shared across this task bundle");
        if (conditional) reasons.add("Check uncertain access requirements before travelling");
        if (confidence == Confidence.LOW) reasons.add("Some access, gear, KC or timing evidence is incomplete; ETA is approximate");
        if (prefs.currentActivity().equals(activity)) reasons.add("Already at this activity: no additional setup cost");
        double policyCost=ObjectivePolicy.cost(prefs.objective(),minutes,minutes+penalties,setup,coordination,prepare,tasks,profile);
        String policy=ObjectivePolicy.explanation(prefs.objective());if(!policy.isEmpty())reasons.add(policy);
        if(prefs.objective()==RouteObjective.EASIEST_PROGRESSION)
            reasons.add("Progression stage "+tasks.stream().mapToInt(a->ProgressionDifficulty.stage(a.task(),profile)).max().orElse(1)
                +": estimated activity/task challenge, adjusted for recorded readiness and known KC");
        return new BossSession(activity, tasks, points, minutes, policyCost, reasons, confidence, conditional);
    }

    private static boolean better(BossSession candidate,BossSession old,RouteObjective objective)
    {
        if(objective==RouteObjective.MAXIMUM_POINTS_PER_HOUR)
        {
            if(candidate.estimatedMinutes()<old.estimatedMinutes()-1e-9)return true;
            if(candidate.estimatedMinutes()>old.estimatedMinutes()+1e-9)return false;
        }
        return candidate.planningCost()<old.planningCost()-1e-9
            || Math.abs(candidate.planningCost()-old.planningCost())<1e-9&&key(candidate).compareTo(key(old))<0;
    }

    private static String key(BossSession session)
    { return session.tasks().stream().map(a -> String.format(Locale.ROOT, "%08d", a.task().id())).collect(Collectors.joining(",")); }
}
