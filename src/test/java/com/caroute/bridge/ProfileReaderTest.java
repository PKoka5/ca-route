package com.caroute.bridge;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class ProfileReaderTest
{
    @Test public void completionBitsCoverSignedAndWordBoundaries()
    {
        int[] words=new int[21];words[0]=1<<31;words[1]=1;words[20]=1;
        assertTrue(RuneLiteProfileReader.completed(31,words));assertTrue(RuneLiteProfileReader.completed(32,words));assertTrue(RuneLiteProfileReader.completed(640,words));assertFalse(RuneLiteProfileReader.completed(639,words));
    }
    @Test public void localKcIsMonotonicAndUnknownBossesDoNotContaminateProfile()
    {
        LocalKillCountReader reader=new LocalKillCountReader(List.of("Zulrah"));Map<String,Integer> counts=new HashMap<>();
        assertTrue(reader.accept("Your Zulrah kill count is: <col=ff0000>1,234</col>.",counts));assertEquals(Integer.valueOf(1234),counts.get("Zulrah"));
        assertFalse(reader.accept("Your Zulrah kill count is: 1.",counts));assertFalse(reader.accept("Your Other kill count is: 4.",counts));
    }
}
