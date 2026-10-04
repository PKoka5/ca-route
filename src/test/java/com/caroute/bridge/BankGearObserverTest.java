package com.caroute.bridge;

import java.util.Map;
import java.util.Set;
import org.junit.Test;
import static org.junit.Assert.*;

public class BankGearObserverTest
{
    @Test public void bankIsOptInAndPlaceholdersAreNotOwnership()
    {
        BankGearObserver observer = new BankGearObserver();
        observer.observe(false, true, Map.of(10, 1), 1);
        assertFalse(observer.observed());
        observer.observe(true, false, Map.of(10, 1), 2);
        assertFalse(observer.observed());
        observer.observe(true, true, Map.of(10, 1, 11, 0, -1, 3), 3);
        assertEquals(Set.of(10), observer.itemIds());
        assertTrue(observer.observed());
        assertEquals(3, observer.observedAt());
        observer.observe(true, false, Map.of(12, 1), 4);
        assertEquals(Set.of(10), observer.itemIds());
        observer.observe(true, true, Map.of(12, 1), 5);
        assertEquals(Set.of(12), observer.itemIds());
        observer.clear();
        assertTrue(observer.itemIds().isEmpty());
        assertFalse(observer.observed());
    }

    @Test public void disablingClearsObservationsAndRepeatedSnapshotIsStable()
    {
        BankGearObserver observer = new BankGearObserver();
        observer.observe(true, true, Map.of(10, 5), 10);
        observer.observe(true, true, Map.of(10, 6), 20);
        assertEquals(10, observer.observedAt());
        observer.observe(false, false, Map.of(), 30);
        assertFalse(observer.observed());
        assertEquals(0, observer.observedAt());
    }
}
