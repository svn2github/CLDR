/**
*******************************************************************************
* Copyright (C) 1996-2001, International Business Machines Corporation and    *
* others. All Rights Reserved.                                                *
*******************************************************************************
*
* $Date$
* $Revision$
*
*******************************************************************************
*/

package org.unicode.cldr.util;


import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public final class Counter {
    Map map = new HashMap();

    static public final class RWInteger implements Comparable {
        static int uniqueCount;
        public int value;
        private int forceUnique = uniqueCount++;

        // public RWInteger() {
          //  forceUnique

        public int compareTo(Object other) {
            RWInteger that = (RWInteger) other;
            if (that.value < value) return -1;
            else if (that.value > value) return 1;
            else if (that.forceUnique < forceUnique) return -1;
            else if (that.forceUnique > forceUnique) return 1;
            return 0;
        }
        public String toString() {
            return String.valueOf(value);
        }
    }

    public void add(Object obj, int countValue) {
        RWInteger count = (RWInteger)map.get(obj);
        if (count == null) map.put(obj, count = new RWInteger());
        count.value += countValue;
    }
    
    public int getCount(Object obj) {
        RWInteger count = (RWInteger) map.get(obj);
        return count == null ? 0 : count.value;
    }
    
    public void clear() {
        map.clear();
    }
    
    public int getTotal() {
        int count = 0;
        for (Iterator it = map.keySet().iterator(); it.hasNext();) {
            count += ((RWInteger) map.get(it.next())).value;
        }
        return count;
    }
    
    public int getItemCount() {
        return map.size();
    }

    public Map getSortedByCount() {
        Map result = new TreeMap();
        Iterator it = map.keySet().iterator();
        while (it.hasNext()) {
            Object key = it.next();
            Object count = map.get(key);
            result.put(count, key);
        }
        return result;
    }

    public Map getKeyToKey() {
        Map result = new HashMap();
        Iterator it = map.keySet().iterator();
        while (it.hasNext()) {
            Object key = it.next();
            result.put(key, key);
        }
        return result;
    }

    public Set keySet() {
        return map.keySet();
    }
    
    public Map getMap() {
        return Collections.unmodifiableMap(map);
    }

    public int size() {
        return map.size();
    }
}