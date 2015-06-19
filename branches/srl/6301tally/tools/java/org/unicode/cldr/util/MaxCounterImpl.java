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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
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
public class MaxCounterImpl<T extends Comparable<T>> implements MaxCounter<T> {

    private Set<CounterEntry> entries = new TreeSet<CounterEntry>();
    private Set<T> allEntries = new TreeSet<T>();
    
    /**
     * Sort:
     * 1. by count
     * 2. by timestamp (could be 0)
     * 3. by UCA
     * @author srl
     *
     */
    private final class CounterEntry implements Comparable<CounterEntry> {
        private final long count;
        private final long timeValue;
        private final T value;

        public CounterEntry(T value, long count, long timeValue) {
            this.count = count;
            this.timeValue = timeValue;
            this.value = value;
        }
        
        @Override
        public int compareTo(CounterEntry o) {
            if(this == o) return 0;
            
            if(this.count > count) return -1;
            if(this.count < count) return 1;
            
            if(this.timeValue > timeValue) return -1;
            if(this.timeValue < timeValue) return 1;
            
            return this.value.compareTo(o.value);
        }
    }
    
    
    @Override
    public MaxCounter<T> clear() {
        entries.clear();
        return this;
    }

    @Override
    public MaxCounter<T> add(final T obj, final long countValue, Long timeValue) {
        if(timeValue==null) timeValue = 0L;
        entries.add(new CounterEntry(obj, countValue, timeValue));
        allEntries.add(obj);
        return this;
    }

    @Override
    public int size() {
        return allEntries.size();
    }

    @Override
    public long getCount(T obj) {
        long lastCount = -1;
        for(CounterEntry e : entries) {
            if(e.count <= lastCount) continue; // skip these

            if(e.value.equals(obj)) {
                return e.count;
            }

            lastCount = e.count;
        }
        return 0;
    }

    @Override
    public Set<T> keySet() {
        return allEntries;
    }

    @Override
    public Iterator<T> iterator() {
        return new Iterator<T>(){
            final Iterator<CounterEntry> i = getElidedList().iterator();

            @Override
            public boolean hasNext() {
                return i.hasNext();
            }

            @Override
            public T next() {
                return i.next().value;
            }

            @Override
            public void remove() {
                throw new InternalError("read only");
            }};
    }

    @Override
    public long getTotal() {
        long l = 0;
        for(CounterEntry e : getEntryIterator()) {
            l += e.count;
        }
        return l;
    }
    
    private Iterable<CounterEntry> getEntryIterator() {
        final List<CounterEntry> elided = getElidedList();
        
        return new Iterable<CounterEntry>(){

            @Override
            public Iterator<MaxCounterImpl<T>.CounterEntry> iterator() {
                return elided.iterator();
            }};
    }

    private List<CounterEntry> getElidedList() {
        final List<CounterEntry> elided;
        elided = new ArrayList<CounterEntry>(entries.size());
        long lastCount = -1;
        for(CounterEntry e : entries) {
            if(e.count <= lastCount) continue; // skip these
            elided.add(e);
            lastCount = e.count;
        }
        return elided;
    }
    
    
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("}");
        return sb.toString();
    }
}