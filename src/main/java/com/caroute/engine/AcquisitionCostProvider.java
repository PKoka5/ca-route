package com.caroute.engine;

import com.caroute.domain.AccountType;
import com.caroute.domain.GearProfile;

@FunctionalInterface
public interface AcquisitionCostProvider
{
    AcquisitionCost assess(AccountType accountType,String capability,GearProfile ownership);
    static AcquisitionCostProvider unknown(){return (account,capability,gear)->AcquisitionCost.unknown("Acquisition time for " + capability + " is unknown; no purchase or unlock is assumed");}
}
