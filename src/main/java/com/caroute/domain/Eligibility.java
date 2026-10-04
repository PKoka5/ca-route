package com.caroute.domain;

import java.util.List;
import java.util.Objects;

public final class Eligibility
{
    private final EligibilityState state; private final List<String> reasons; private final Confidence confidence; private final boolean recommendedNow,conditional;
    public Eligibility(EligibilityState state,List<String> reasons,Confidence confidence,boolean recommendedNow,boolean conditional){this.state=Objects.requireNonNull(state);this.reasons=Immutable.list(reasons);this.confidence=Objects.requireNonNull(confidence);this.recommendedNow=recommendedNow;this.conditional=conditional;}
    public EligibilityState state(){return state;} public List<String> reasons(){return reasons;} public Confidence confidence(){return confidence;} public boolean recommendedNow(){return recommendedNow;} public boolean conditional(){return conditional;}
}
