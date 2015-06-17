/**
 *******************************************************************************
 * Copyright (C) 1996-2001, International Business Machines Corporation and    *
 * others. All Rights Reserved.                                                *
 *******************************************************************************
 *
 * $Date: 2014-03-12 23:45:45 -0700 (Wed, 12 Mar 2014) $
 * $Revision: 9953 $
 *
 *******************************************************************************
 */

package org.unicode.cldr.util;

import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import com.ibm.icu.impl.Row;
import com.ibm.icu.impl.Row.R2;

/**
 * Like Counter, but with a time axis. Latest time wins.
 * @author mark, srl
 *
 * @param <T>
 */
public class TimeCounter<T> implements Iterable<T>, Comparable<TimeCounter<T>> {
    Map<T, KRWLong> map;
    Comparator<T> comparator;

    public TimeCounter() {
        this(null);
    }

    public TimeCounter(boolean naturalOrdering) {
        this(naturalOrdering ? new CldrUtility.ComparableComparator() : null);
    }

    public TimeCounter(Comparator<T> comparator) {
        if (comparator != null) {
            this.comparator = comparator;
            map = new TreeMap<T, KRWLong>(comparator);
        } else {
            map = new LinkedHashMap<T, KRWLong>();
        }
    }

    /**
     * The 'time' axis ensures ordering (if present),
     * if the value is the same.
     *
     * @param <K>
     */
    static private final class KRWLong implements Comparable<KRWLong> {
        static int uniqueCount;
        public long value;
        public long time = 0;
        private final int forceUnique;
        {
            synchronized (KRWLong.class) { // make thread-safe
                forceUnique = uniqueCount++;
            }
        }

        public int compareTo(KRWLong that) {
            if (that.value < value) return -1;
            if (that.value > value) return 1;

            if (this == that) return 0;
            
            // check the 'time' if votes are the same.
            if (that.time < time) return -1;
            if (that.time > time) return 1;

            synchronized (this) { // make thread-safe
                if (that.forceUnique < forceUnique) return -1;
            }
            return 1; // the forceUnique values must be different, so this is the only remaining case
        }

        public String toString() {
            return String.valueOf(value);
        }
        
        /**
         * Update the time value to the max
         * @param timevalue
         */
        public KRWLong updateTime(Long timevalue) {
            if(timevalue != null &&
               timevalue > time ) {
                time = timevalue;
            }
            return this;
        }
    }

    public TimeCounter<T> add(T obj, long countValue, Long timeValue) {
        KRWLong count = map.get(obj);
        if (count == null) map.put(obj, count = new KRWLong());
        count.updateTime(timeValue).value += countValue;
        return this;
    }

    public long getCount(T obj) {
        return get(obj);
    }

    public long get(T obj) {
        KRWLong count = map.get(obj);
        return count == null ? 0 : count.value;
    }

    public TimeCounter<T> clear() {
        map.clear();
        return this;
    }

    public long getTotal() {
        long count = 0;
        for (T item : map.keySet()) {
            count += map.get(item).value;
        }
        return count;
    }

    public int getItemCount() {
        return size();
    }

    private static class Entry<T> {
        KRWLong count;
        T value;
        int uniqueness;

        public Entry(KRWLong count, T value, int uniqueness) {
            this.count = count;
            this.value = value;
            this.uniqueness = uniqueness;
        }
    }

    private static class EntryComparator<T> implements Comparator<Entry<T>> {
        int countOrdering;
        Comparator<T> byValue;

        public EntryComparator(boolean ascending, Comparator<T> byValue) {
            countOrdering = ascending ? 1 : -1;
            this.byValue = byValue;
        }

        public int compare(Entry<T> o1, Entry<T> o2) {
            if (o1.count.value < o2.count.value) return -countOrdering;
            if (o1.count.value > o2.count.value) return countOrdering;

            if (o1.count.time < o2.count.time) return -countOrdering;
            if (o1.count.time > o2.count.time) return countOrdering;
            
            if (byValue != null) {
                return byValue.compare(o1.value, o2.value);
            }
            return o1.uniqueness - o2.uniqueness;
        }
    }

    public Set<T> getKeysetSortedByCountAndTime(boolean ascending) {
        return getKeysetOrderedByCountAndTime(ascending, null);
    }

    public Set<T> getKeysetOrderedByCountAndTime(boolean ascending, Comparator<T> byValue) {
        Set<Entry<T>> count_key = new TreeSet<Entry<T>>(new EntryComparator<T>(ascending, byValue));
        int counter = 0;
        for (T key : map.keySet()) {
            count_key.add(new Entry<T>(map.get(key), key, counter++));
        }
        Set<T> result = new LinkedHashSet<T>();
        for (Entry<T> entry : count_key) {
            result.add(entry.value);
        }
        return result;
    }

    public Set<Row.R2<Long, T>> getEntrySetSortedByCountAndTime(boolean ascending, Comparator<T> byValue) {
        Set<Entry<T>> count_key = new TreeSet<Entry<T>>(new EntryComparator<T>(ascending, byValue));
        int counter = 0;
        for (T key : map.keySet()) {
            count_key.add(new Entry<T>(map.get(key), key, counter++));
        }
        Set<R2<Long, T>> result = new LinkedHashSet<Row.R2<Long, T>>();
        for (Entry<T> entry : count_key) {
            result.add(Row.of(entry.count.value, entry.value));
        }
        return result;
    }

    public Set<T> getKeysetSortedByKey() {
        Set<T> s = new TreeSet<T>(comparator);
        s.addAll(map.keySet());
        return s;
    }

    // public Map<T,RWInteger> getKeyToKey() {
    // Map<T,RWInteger> result = new HashMap<T,RWInteger>();
    // Iterator<T> it = map.keySet().iterator();
    // while (it.hasNext()) {
    // Object key = it.next();
    // result.put(key, key);
    // }
    // return result;
    // }

    public Set<T> keySet() {
        return map.keySet();
    }

    @Override
    public Iterator<T> iterator() {
        return map.keySet().iterator();
    }
//
//    public Map<T, KRWLong> getMap() {
//        return map; // older code was protecting map, but not the integer values.
//    }

    public int size() {
        return map.size();
    }

    public String toString() {
        return map.toString();
    }

//    public TimeCounter<T> addAll(Collection<T> keys, int delta) {
//        for (T key : keys) {
//            add(key, delta, null);
//        }
//        return this;
//    }
//
//    public TimeCounter<T> addAll(TimeCounter<T> keys) {
//        for (T key : keys) {
//            add(key, keys.getCount(key), null);
//        }
//        return this;
//    }

    @Override
    public int compareTo(TimeCounter<T> o) {
        Iterator<T> i = map.keySet().iterator();
        Iterator<T> j = o.map.keySet().iterator();
        while (true) {
            boolean goti = i.hasNext();
            boolean gotj = j.hasNext();
            if (!goti || !gotj) {
                return goti ? 1 : gotj ? -1 : 0;
            }
            T ii = i.next();
            T jj = i.next();
            int result = ((Comparable<T>) ii).compareTo(jj);
            if (result != 0) {
                return result;
            }
            final long iv = map.get(ii).value;
            final long jv = o.map.get(jj).value;
            if (iv != jv) return iv < jv ? -1 : 0;
        }
    }

    public TimeCounter<T> increment(T key, Long timeValue) {
        return add(key, 1, timeValue);
    }

    public boolean containsKey(T key) {
        return map.containsKey(key);
    }

    public boolean equals(Object o) {
        return map.equals(o);
    }

    public int hashCode() {
        return map.hashCode();
    }

    public boolean isEmpty() {
        return map.isEmpty();
    }

    public TimeCounter<T> remove(T key) {
        map.remove(key);
        return this;
    }

    // public RWLong put(T key, RWLong value) {
    // return map.put(key, value);
    // }
    //
    // public void putAll(Map<? extends T, ? extends RWLong> t) {
    // map.putAll(t);
    // }
    //
    // public Set<java.util.Map.Entry<T, Long>> entrySet() {
    // return map.entrySet();
    // }
    //
    // public Collection<RWLong> values() {
    // return map.values();
    // }

}