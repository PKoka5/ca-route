package com.caroute.engine;

import com.caroute.domain.*;
import java.util.*;

/** Shared ownership acquisition across activities, with explicit bounded-search exactness. */
final class SharedPreparation
{
    private static final int MAX_LABELS=5000,MAX_TRANSITIONS_PER_ACTIVITY=500000;
    static final class Result
    {
        final List<BossSession> stops;final boolean exact,feasible;final int points;
        Result(List<BossSession> stops,boolean exact,boolean feasible,int points){this.stops=stops;this.exact=exact;this.feasible=feasible;this.points=points;}
    }
    private static final class Label
    {
        final int points;final SortedSet<String> acquired;final double time,cost;final Label prior;final BossSession choice;
        Label(int points,SortedSet<String> acquired,double time,double cost,Label prior,BossSession choice)
        {this.points=points;this.acquired=acquired;this.time=time;this.cost=cost;this.prior=prior;this.choice=choice;}
        String key(){return points+"|"+String.join(",",acquired);}
    }
    static Map<String,Double> acquisitions(BossSession session)
    {
        Map<String,Double> costs=new TreeMap<>();
        for(TaskAssessment assessment:session.tasks())for(Map.Entry<String,Double> component:assessment.components().entrySet())
            if(component.getKey().startsWith("acquire:")&&component.getValue()>0)costs.merge(component.getKey().substring(8),component.getValue(),Math::max);
        return costs;
    }
    static boolean needed(List<List<BossSession>> options)
    {for(List<BossSession> group:options)for(BossSession session:group)if(session!=null&&!acquisitions(session).isEmpty())return true;return false;}
    static double acquisitionWeight(RouteObjective objective)
    {return objective==RouteObjective.EASIEST_PROGRESSION?0.25:objective==RouteObjective.IRONMAN_EFFICIENT?3:1;}

    static Result search(List<List<BossSession>> options,int cover,boolean budget,RoutingPreferences preferences)
    {
        List<Label> labels=Collections.singletonList(new Label(0,new TreeSet<>(),0,0,null,null));boolean exact=true;
        for(List<BossSession> group:options)
        {
            List<BossSession> choices=new ArrayList<>(group);
            choices.sort(Comparator.nullsFirst(Comparator.comparingDouble((BossSession s)->budget?s.estimatedMinutes():-s.points()).thenComparingDouble(BossSession::planningCost).thenComparing(SharedPreparation::sessionKey)));
            Map<String,Label> next=new LinkedHashMap<>();int transitions=0;
            search:for(Label prior:labels)for(BossSession choice:choices)
            {
                if(++transitions>MAX_TRANSITIONS_PER_ACTIVITY){exact=false;break search;}
                Map<String,Double> acquisitions=choice==null?Collections.emptyMap():acquisitions(choice);double paid=0;
                for(Map.Entry<String,Double> acquisition:acquisitions.entrySet())if(prior.acquired.contains(acquisition.getKey()))paid+=acquisition.getValue();
                double time=prior.time+(choice==null?0:choice.estimatedMinutes()-paid),cost=prior.cost+(choice==null?0:choice.planningCost()-paid*acquisitionWeight(preferences.objective()));
                if(budget&&time>preferences.timeBudgetMinutes()+1e-9)continue;
                int points=prior.points+(choice==null?0:choice.points());if(!budget)points=Math.min(cover,points);
                SortedSet<String> acquired=new TreeSet<>(prior.acquired);acquired.addAll(acquisitions.keySet());
                Label candidate=new Label(points,acquired,time,cost,prior,choice),old=next.get(candidate.key());
                if(old==null||better(candidate,old,budget))next.put(candidate.key(),candidate);
            }
            labels=new ArrayList<>(next.values());
            if(labels.size()>MAX_LABELS)
            {
                exact=false;labels.sort(Comparator.comparingInt((Label value)->-value.points)
                    .thenComparingDouble(value->budget?value.time:value.cost).thenComparingDouble(value->budget?value.cost:value.time).thenComparing(Label::key));
                labels=new ArrayList<>(labels.subList(0,MAX_LABELS));
            }
            if(labels.isEmpty())return new Result(Collections.emptyList(),exact,false,0);
        }
        Label best=null;
        for(Label label:labels)if(best==null||label.points>best.points||label.points==best.points&&better(label,best,budget))best=label;
        List<BossSession> selected=new ArrayList<>();
        for(Label at=best;at!=null;at=at.prior)if(at.choice!=null)selected.add(at.choice);
        return new Result(selected,exact,true,best==null?0:best.points);
    }
    private static boolean better(Label candidate,Label old,boolean budget)
    {
        double primary=budget?candidate.time:candidate.cost,previous=budget?old.time:old.cost;
        if(primary<previous-1e-9)return true;if(primary>previous+1e-9)return false;
        // Tier routes keep the original stable tie behavior; budget uses lower policy cost.
        return budget&&candidate.cost<old.cost-1e-9;
    }
    private static String sessionKey(BossSession session)
    {StringBuilder result=new StringBuilder(session.activity());session.tasks().forEach(a->result.append(':').append(a.task().id()));return result.toString();}

    /** Assign one acquisition to the first displayed stop; physical stop totals then match the route. */
    static List<BossSession> assign(List<BossSession> ordered,RouteObjective objective)
    {
        Set<String> acquired=new TreeSet<>();List<BossSession> result=new ArrayList<>();
        for(BossSession stop:ordered)
        {
            Map<String,Double> needs=acquisitions(stop);double shared=0;List<String> repeated=new ArrayList<>();
            for(Map.Entry<String,Double> need:needs.entrySet())if(!acquired.add(need.getKey())){shared+=need.getValue();repeated.add(need.getKey());}
            if(shared==0){result.add(stop);continue;}
            List<String> reasons=new ArrayList<>(stop.reasons());
            reasons.removeIf(reason->reason.startsWith("Estimated "));
            reasons.add(String.format(Locale.ROOT,"Estimated %.1f minutes; shared gear preparation was included at an earlier stop",stop.estimatedMinutes()-shared));
            reasons.add("Gear preparation already covered earlier in this route: "+String.join(", ",repeated));
            result.add(new BossSession(stop.activity(),stop.tasks(),stop.points(),Math.max(0,stop.estimatedMinutes()-shared),Math.max(0,stop.planningCost()-shared*acquisitionWeight(objective)),reasons,stop.confidence(),stop.conditional()));
        }
        return result;
    }
}
