package com.caroute.engine;

import com.caroute.domain.*;
import java.util.*;
import java.util.stream.Collectors;

/** Pure local calculation with explicit tier, activity and active-time scopes. */
public final class RouteEngine
{
    private final AcquisitionCostProvider acquisition;
    public RouteEngine()
    {
        this((type,capability,gear)->gear.activePreparationEstimates().containsKey(capability)
            ? AcquisitionCost.manual(gear.activePreparationEstimates().get(capability))
            : AcquisitionCost.unknown("Acquisition cost is not yet curated for " + capability));
    }
    public RouteEngine(AcquisitionCostProvider acquisition){this.acquisition=Objects.requireNonNull(acquisition);}

    public RoutePlan plan(List<CombatAchievement> input,PlayerProfile profile,RoutingPreferences prefs)
    {
        Objects.requireNonNull(profile);Objects.requireNonNull(prefs);
        List<CombatAchievement> tasks=new ArrayList<>(input);tasks.sort(Comparator.comparingInt(CombatAchievement::id));
        Set<Integer> ids=new HashSet<>();for(CombatAchievement task:tasks)if(!ids.add(task.id()))throw new IllegalArgumentException("Duplicate task ID " + task.id());
        AchievementTier target=prefs.target();
        if(target==null)
        {
            target=AchievementTier.GRANDMASTER;
            for(AchievementTier tier:AchievementTier.values())if(profile.currentPoints()<profile.thresholds().getOrDefault(tier,Integer.MAX_VALUE)){target=tier;break;}
        }
        int threshold=profile.thresholds().getOrDefault(target,0),gap=Math.max(0,threshold-profile.currentPoints());
        List<TaskAssessment> all=new ArrayList<>();List<String> blockers=new ArrayList<>();
        EligibilityEvaluator eligibility=new EligibilityEvaluator();TaskCostModel costs=new TaskCostModel(acquisition);
        for(CombatAchievement task:tasks)all.add(costs.assess(task,eligibility.evaluate(task,profile,prefs),profile,prefs));
        boolean activityScope=prefs.objective()==RouteObjective.BOSS_COMPLETION,budgetScope=prefs.objective()==RouteObjective.MAXIMUM_POINTS_PER_HOUR;
        if(!profile.loaded()||(!activityScope&&!budgetScope&&threshold<=0))
            return result(target,profile,threshold,gap,all,Collections.emptyList(),Collections.singletonList("Waiting for account state and current tier thresholds"),Reachability.UNKNOWN_PROFILE,false,prefs,false);
        boolean gm=target==AchievementTier.GRANDMASTER;
        if(!gm&&gap==0&&!activityScope&&!budgetScope)
            return result(target,profile,threshold,gap,all,Collections.emptyList(),blockers,Reachability.REACHED,true,prefs,true);
        List<TaskAssessment> remaining=all.stream().filter(a->a.eligibility().state()!=EligibilityState.COMPLETED).collect(Collectors.toList());
        List<TaskAssessment> scoped=activityScope?remaining.stream().filter(a->a.task().activity().equals(prefs.selectedActivity())).collect(Collectors.toList()):remaining;
        scoped.stream().filter(a->!a.eligibility().recommendedNow()).sorted(Comparator.comparingDouble((TaskAssessment a)->preparationCost(a,profile)).thenComparingInt(a->a.task().id()))
            .forEach(a->blockers.add(a.task().name()+": "+String.join("; ",a.eligibility().reasons())));
        for(Integer pin:new TreeSet<>(prefs.pinnedTaskIds()))
        {
            if(!ids.contains(pin))blockers.add("Pinned task "+pin+" is absent from the current catalogue");
            else all.stream().filter(a->a.task().id()==pin&&a.eligibility().state()!=EligibilityState.COMPLETED).findFirst().ifPresent(a->{
                if(activityScope&&!a.task().activity().equals(prefs.selectedActivity()))blockers.add("Pinned task "+pin+" belongs to another activity; remove the pin or change the boss scope");
                else if(!a.eligibility().recommendedNow())blockers.add("Pinned task "+pin+" conflicts with availability/preferences: "+String.join("; ",a.eligibility().reasons()));
            });
        }
        boolean pinConflict=blockers.stream().anyMatch(s->s.startsWith("Pinned task"));
        Map<String,List<TaskAssessment>> groups=new TreeMap<>();
        for(TaskAssessment a:scoped)if(a.eligibility().recommendedNow())groups.computeIfAbsent(a.task().activity(),key->new ArrayList<>()).add(a);
        SessionOptimizer optimizer=new SessionOptimizer();List<BossSession> selected=new ArrayList<>();
        if(activityScope)
        {
            boolean knownActivity=tasks.stream().anyMatch(task->task.activity().equals(prefs.selectedActivity()));
            if(!knownActivity)blockers.add("Select an activity present in the current catalogue for boss completion");
            for(List<TaskAssessment> group:groups.values())selected.add(optimizer.evaluate(group,profile,prefs));
            if(!profile.catalogueComplete())blockers.add("Boss completion requires a verified live catalogue; unseen/new tasks cannot be claimed complete");
            boolean complete=knownActivity&&blockers.isEmpty();
            Reachability reach=complete?(scoped.isEmpty()?Reachability.REACHED:conditional(selected)?Reachability.CONDITIONAL:Reachability.REACHABLE_NOW):Reachability.UNREACHABLE_NOW;
            return result(target,profile,threshold,gap,all,selected,blockers,reach,true,prefs,complete);
        }
        if(gm&&!budgetScope)
        {
            for(List<TaskAssessment> group:groups.values())selected.add(optimizer.evaluate(group,profile,prefs));
            if(!profile.catalogueComplete())blockers.add("Grandmaster requires every task; live catalogue completeness has not been verified");
            Reachability reach=remaining.isEmpty()&&profile.catalogueComplete()&&blockers.isEmpty()?Reachability.REACHED:blockers.isEmpty()?conditional(selected)?Reachability.CONDITIONAL:Reachability.REACHABLE_NOW:Reachability.UNREACHABLE_NOW;
            return result(target,profile,threshold,gap,all,selected,blockers,reach,false,prefs,blockers.isEmpty());
        }
        if(prefs.objective()==RouteObjective.EASIEST_PROGRESSION)
        {
            int ceiling=lowestProgressionCeiling(groups,gap,profile,prefs);
            groups.values().forEach(group->group.removeIf(a->ProgressionDifficulty.stage(a.task(),profile)>ceiling));
            groups.entrySet().removeIf(entry->entry.getValue().isEmpty());
        }
        List<List<BossSession>> options=new ArrayList<>();boolean exact=true;int availablePoints=0;
        for(List<TaskAssessment> group:groups.values())
        {
            SessionOptimizer.Options activity=optimizer.options(group,profile,prefs);exact&=activity.exhaustive;
            boolean hasPin=group.stream().anyMatch(a->prefs.pinnedTaskIds().contains(a.task().id()));
            List<BossSession> candidates=new ArrayList<>(activity.sessions);if(!hasPin)candidates.add(0,null);
            availablePoints+=activity.sessions.stream().mapToInt(BossSession::points).max().orElse(0);options.add(candidates);
        }
        if(budgetScope)return budget(target,profile,threshold,gap,all,options,availablePoints,blockers,pinConflict,exact,prefs);
        int cover=Math.min(gap,availablePoints);if(cover>100000)throw new IllegalArgumentException("Unreasonable route gap");
        if(SharedPreparation.needed(options))
        {
            SharedPreparation.Result shared=SharedPreparation.search(options,cover,false,prefs);selected=shared.stops;exact&=shared.exact;
            boolean covered=shared.feasible&&shared.points>=gap;
            if(!covered)blockers.add(0,"Available recommendations supply "+shared.points+" of the "+gap+" points needed; prepare/unlock more content");
            if(!shared.exact)blockers.add("Shared gear preparation search reached its bounded limit; this is a feasible route without a general optimality guarantee");
            Reachability reach=!covered||pinConflict?Reachability.UNREACHABLE_NOW:conditional(selected)?Reachability.CONDITIONAL:Reachability.REACHABLE_NOW;
            List<String> displayed=reach==Reachability.REACHABLE_NOW||reach==Reachability.CONDITIONAL?blockers.stream().filter(s->s.startsWith("Pinned task")||s.startsWith("Shared gear")).collect(Collectors.toList()):blockers;
            return result(target,profile,threshold,gap,all,selected,displayed,reach,exact,prefs,covered&&!pinConflict);
        }
        double[] dp=new double[cover+1];Arrays.fill(dp,Double.POSITIVE_INFINITY);dp[0]=0;
        int[][] picks=new int[options.size()][cover+1],previous=new int[options.size()][cover+1];
        for(int row=0;row<options.size();row++)
        {
            Arrays.fill(picks[row],-1);double[] next=new double[cover+1];Arrays.fill(next,Double.POSITIVE_INFINITY);
            List<BossSession> choices=options.get(row);
            for(int point=0;point<=cover;point++)if(Double.isFinite(dp[point]))for(int choice=0;choice<choices.size();choice++)
            {
                BossSession session=choices.get(choice);int to=Math.min(cover,point+(session==null?0:session.points()));double value=dp[point]+(session==null?0:session.planningCost());
                if(value<next[to]-1e-9){next[to]=value;picks[row][to]=choice;previous[row][to]=point;}
            }
            dp=next;
        }
        selected=reconstruct(options,picks,previous,cover);
        if(availablePoints<gap)blockers.add(0,"Available recommendations supply "+availablePoints+" of the "+gap+" points needed; prepare/unlock more content");
        Reachability reach=availablePoints<gap||pinConflict?Reachability.UNREACHABLE_NOW:conditional(selected)?Reachability.CONDITIONAL:Reachability.REACHABLE_NOW;
        List<String> displayed=reach==Reachability.REACHABLE_NOW||reach==Reachability.CONDITIONAL?blockers.stream().filter(s->s.startsWith("Pinned task")).collect(Collectors.toList()):blockers;
        return result(target,profile,threshold,gap,all,selected,displayed,reach,exact,prefs,availablePoints>=gap&&!pinConflict);
    }

    private static RoutePlan budget(AchievementTier target,PlayerProfile profile,int threshold,int gap,List<TaskAssessment> all,List<List<BossSession>> options,int available,List<String> blockers,boolean pinConflict,boolean exact,RoutingPreferences prefs)
    {
        if(available>100000)throw new IllegalArgumentException("Unreasonable available point total");
        if(SharedPreparation.needed(options))
        {
            SharedPreparation.Result shared=SharedPreparation.search(options,available,true,prefs);
            List<String> displayed=blockers.stream().filter(s->s.startsWith("Pinned task")).collect(Collectors.toList());
            if(!shared.feasible)displayed.add("Pinned task bundles cannot fit the "+prefs.timeBudgetMinutes()+" active-minute budget; increase the budget or remove pins");
            displayed.add("Budget horizon: "+prefs.timeBudgetMinutes()+" active minutes; projected points are for this horizon, not an ETA to the selected tier");
            boolean reached=budgetReached(profile,all,pinConflict);
            if(shared.points==0&&shared.feasible&&!reached)addEmptyBudgetReason(displayed,available,all,profile);
            if(!shared.exact)displayed.add("Shared gear preparation search reached its bounded limit; more points may be possible");
            boolean complete=reached||shared.feasible&&shared.points>0&&!pinConflict;Reachability reach=reached?Reachability.REACHED:!complete?Reachability.UNREACHABLE_NOW:conditional(shared.stops)?Reachability.CONDITIONAL:Reachability.REACHABLE_NOW;
            return result(target,profile,threshold,gap,all,shared.stops,displayed,reach,exact&&shared.exact,prefs,complete);
        }
        double[] minutes=new double[available+1],cost=new double[available+1];Arrays.fill(minutes,Double.POSITIVE_INFINITY);Arrays.fill(cost,Double.POSITIVE_INFINITY);minutes[0]=cost[0]=0;
        int[][] picks=new int[options.size()][available+1],previous=new int[options.size()][available+1];
        for(int row=0;row<options.size();row++)
        {
            Arrays.fill(picks[row],-1);double[] nextTime=new double[available+1],nextCost=new double[available+1];Arrays.fill(nextTime,Double.POSITIVE_INFINITY);Arrays.fill(nextCost,Double.POSITIVE_INFINITY);
            List<BossSession> choices=options.get(row);
            for(int point=0;point<=available;point++)if(Double.isFinite(minutes[point]))for(int choice=0;choice<choices.size();choice++)
            {
                BossSession session=choices.get(choice);int to=point+(session==null?0:session.points());if(to>available)continue;
                double time=minutes[point]+(session==null?0:session.estimatedMinutes()),value=cost[point]+(session==null?0:session.planningCost());
                if(time>prefs.timeBudgetMinutes()+1e-9)continue;
                if(time<nextTime[to]-1e-9||Math.abs(time-nextTime[to])<1e-9&&value<nextCost[to]-1e-9)
                {nextTime[to]=time;nextCost[to]=value;picks[row][to]=choice;previous[row][to]=point;}
            }
            minutes=nextTime;cost=nextCost;
        }
        int best=-1;for(int point=available;point>=0;point--)if(Double.isFinite(minutes[point])){best=point;break;}
        List<BossSession> selected=best<0?Collections.emptyList():reconstruct(options,picks,previous,best);
        List<String> displayed=blockers.stream().filter(s->s.startsWith("Pinned task")).collect(Collectors.toList());
        if(best<0)displayed.add("Pinned task bundles cannot fit the "+prefs.timeBudgetMinutes()+" active-minute budget; increase the budget or remove pins");
        displayed.add("Budget horizon: "+prefs.timeBudgetMinutes()+" active minutes; projected points are for this horizon, not an ETA to the selected tier");
        boolean reached=budgetReached(profile,all,pinConflict);
        if(best==0&&!reached)addEmptyBudgetReason(displayed,available,all,profile);
        boolean complete=reached||best>0&&!pinConflict;
        Reachability reach=reached?Reachability.REACHED:!complete?Reachability.UNREACHABLE_NOW:conditional(selected)?Reachability.CONDITIONAL:Reachability.REACHABLE_NOW;
        return result(target,profile,threshold,gap,all,selected,displayed,reach,exact,prefs,complete);
    }
    private static boolean budgetReached(PlayerProfile profile,List<TaskAssessment> all,boolean pinConflict)
    {return !pinConflict&&profile.catalogueComplete()&&all.stream().allMatch(a->a.eligibility().state()==EligibilityState.COMPLETED);}
    private static void addEmptyBudgetReason(List<String> blockers,int available,List<TaskAssessment> all,PlayerProfile profile)
    {
        if(all.stream().allMatch(a->a.eligibility().state()==EligibilityState.COMPLETED)&&!profile.catalogueComplete())blockers.add("No remaining mapped tasks, but live catalogue completeness must be verified before completion can be claimed");
        else if(available==0)blockers.add("No incomplete tasks are eligible under current requirements or preferences; check Prepare and restore exclusions if appropriate");
        else blockers.add("No incomplete eligible task bundle fits this active-time budget; increase the budget or prepare faster alternatives");
    }
    private static List<BossSession> reconstruct(List<List<BossSession>> options,int[][] picks,int[][] previous,int point)
    {
        List<BossSession> selected=new ArrayList<>();
        for(int row=options.size()-1;row>=0;row--)
        {
            int chosen=picks[row][point];if(chosen>=0){BossSession session=options.get(row).get(chosen);if(session!=null)selected.add(session);point=previous[row][point];}
        }
        return selected;
    }
    private static boolean conditional(List<BossSession> sessions){return sessions.stream().anyMatch(BossSession::conditional);}
    /** First minimize the hardest needed stage; efficiency cannot trade it for a point-rich harder boss. */
    private static int lowestProgressionCeiling(Map<String,List<TaskAssessment>> groups,int gap,PlayerProfile profile,RoutingPreferences prefs)
    {
        SortedMap<Integer,Integer> pointsByStage=new TreeMap<>();int pinnedStage=1;
        for(List<TaskAssessment> group:groups.values())for(TaskAssessment assessment:group)
        {
            int stage=ProgressionDifficulty.stage(assessment.task(),profile);
            pointsByStage.merge(stage,assessment.task().points(),Integer::sum);
            if(prefs.pinnedTaskIds().contains(assessment.task().id()))pinnedStage=Math.max(pinnedStage,stage);
        }
        int needed=Math.min(gap,pointsByStage.values().stream().mapToInt(Integer::intValue).sum()),points=0,ceiling=1;
        for(Map.Entry<Integer,Integer> entry:pointsByStage.entrySet())
        {
            points+=entry.getValue();ceiling=entry.getKey();
            if(points>=needed)break;
        }
        return Math.max(ceiling,pinnedStage);
    }
    private static double preparationCost(TaskAssessment assessment,PlayerProfile profile)
    {
        double cost=assessment.planningCost();for(var requirement:assessment.task().requiredSkills().entrySet())cost+=Math.max(0,requirement.getValue()-profile.skills().getOrDefault(requirement.getKey(),0))*5;
        for(var requirement:assessment.task().quests().entrySet())if(profile.quests().getOrDefault(requirement.getKey(),QuestStatus.NOT_STARTED).ordinal()<requirement.getValue().ordinal())cost+=100;return cost;
    }
    private static RoutePlan result(AchievementTier target,PlayerProfile profile,int threshold,int gap,List<TaskAssessment> assessments,List<BossSession> stops,List<String> blockers,Reachability reach,boolean optimal,RoutingPreferences prefs,boolean scopeComplete)
    {
        Comparator<BossSession> ranking=Comparator.comparingDouble((BossSession s)->(prefs.objective()==RouteObjective.MAXIMUM_POINTS_PER_HOUR?s.estimatedMinutes():s.planningCost())/Math.max(1,s.points())).thenComparing(BossSession::activity);
        if(prefs.objective()==RouteObjective.EASIEST_PROGRESSION)
            ranking=Comparator.comparingInt((BossSession session)->ProgressionDifficulty.stage(session,profile)).thenComparing(ranking);
        stops=new ArrayList<>(stops);stops.sort(ranking);stops=SharedPreparation.assign(stops,prefs.objective());int points=stops.stream().mapToInt(BossSession::points).sum();double minutes=stops.stream().mapToDouble(BossSession::estimatedMinutes).sum();
        Confidence confidence=Confidence.HIGH;for(BossSession stop:stops)confidence=Confidence.lowest(confidence,stop.confidence());
        if(reach==Reachability.UNKNOWN_PROFILE||reach==Reachability.UNREACHABLE_NOW||!profile.catalogueComplete())confidence=Confidence.LOW;
        Map<String,List<TaskAssessment>> byActivity=new TreeMap<>();for(TaskAssessment a:assessments)if(a.eligibility().recommendedNow())byActivity.computeIfAbsent(a.task().activity(),key->new ArrayList<>()).add(a);
        SessionOptimizer evaluator=new SessionOptimizer();List<BossSession> ranked=new ArrayList<>();for(List<TaskAssessment> group:byActivity.values())ranked.add(evaluator.evaluate(group,profile,prefs));ranked.sort(ranking);
        return new RoutePlan(target,profile.currentPoints(),threshold,gap,points,minutes,stops,assessments,blockers,reach,confidence,optimal,profile.version()).withBossSessions(ranked).withScope(prefs.objective(),prefs.selectedActivity(),prefs.timeBudgetMinutes(),scopeComplete);
    }
}
