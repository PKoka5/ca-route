package com.caroute.domain;
public enum Confidence
{
    HIGH, MEDIUM, LOW;
    public static Confidence lowest(Confidence a, Confidence b) { return a.ordinal() >= b.ordinal() ? a : b; }
}
