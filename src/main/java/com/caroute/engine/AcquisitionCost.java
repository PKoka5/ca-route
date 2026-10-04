package com.caroute.engine;

import com.caroute.domain.Confidence;
import java.util.Objects;

/** An acquisition estimate, never a claim that unknown preparation is free. */
public final class AcquisitionCost
{
    public enum State { KNOWN, UNKNOWN, UNAVAILABLE }
    private final State state; private final double activeMinutes,waitingMinutes,resourceCost; private final Confidence confidence; private final String reason;
    private final boolean waitingKnown, resourceKnown;
    private AcquisitionCost(State state,double active,double waiting,double resource,Confidence confidence,String reason)
    { this(state, active, waiting, resource, confidence, reason, state == State.KNOWN, state == State.KNOWN); }
    private AcquisitionCost(State state,double active,double waiting,double resource,Confidence confidence,String reason,boolean waitingKnown,boolean resourceKnown)
    {if(!Double.isFinite(active)||!Double.isFinite(waiting)||!Double.isFinite(resource)||active<0||waiting<0||resource<0)throw new IllegalArgumentException("Invalid acquisition estimate");this.state=state;activeMinutes=active;waitingMinutes=waiting;resourceCost=resource;this.confidence=Objects.requireNonNull(confidence);this.reason=Objects.requireNonNull(reason);this.waitingKnown=waitingKnown;this.resourceKnown=resourceKnown;}
    public static AcquisitionCost known(double activeMinutes,double waitingMinutes,double resourceCost,Confidence confidence){return new AcquisitionCost(State.KNOWN,activeMinutes,waitingMinutes,resourceCost,confidence,"Account-specific preparation estimate");}
    public static AcquisitionCost unknown(String reason){return new AcquisitionCost(State.UNKNOWN,0,0,0,Confidence.LOW,reason);}
    public static AcquisitionCost unavailable(String reason){return new AcquisitionCost(State.UNAVAILABLE,0,0,0,Confidence.LOW,reason);}
    public static AcquisitionCost manual(double activeMinutes){return new AcquisitionCost(State.KNOWN,activeMinutes,0,0,Confidence.LOW,"User-reported active preparation estimate",false,false);}
    public boolean hasWaitingEstimate(){return waitingKnown;} public boolean hasResourceEstimate(){return resourceKnown;}
    public State state(){return state;} public double activeMinutes(){return activeMinutes;} public double waitingMinutes(){return waitingMinutes;} public double resourceCost(){return resourceCost;} public Confidence confidence(){return confidence;} public String reason(){return reason;}
}
