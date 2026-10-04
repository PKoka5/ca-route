package com.caroute.domain;

import java.util.*;

final class Immutable
{
    private Immutable() {}
    static <T> Set<T> set(Collection<T> v) { return Collections.unmodifiableSet(new LinkedHashSet<>(Objects.requireNonNull(v))); }
    static <T> List<T> list(Collection<T> v) { return Collections.unmodifiableList(new ArrayList<>(Objects.requireNonNull(v))); }
    static <K,V> Map<K,V> map(Map<K,V> v) { return Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(v))); }
    static double nonNegative(double value, String name) { if (!Double.isFinite(value) || value < 0) throw new IllegalArgumentException(name + " must be finite and nonnegative"); return value; }
}
