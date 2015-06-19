package org.unicode.cldr.util;

import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * Abstract interface to Counter for use by VoteResolver
 * @author srl
 *
 * @param <T>
 */
public interface MaxCounter<T extends Comparable<T>> extends Iterable<T>{

    MaxCounter<T> clear();

    MaxCounter<T> add(T obj, long countValue, Long timeValue);

    int size();

    long getCount(T obj);

    /**
     * Set of all T values.
     * @return
     */
    Set<T> keySet();
    
    /**
     * Iterator. First item will be highest count / latest time / lowest uca
     */
    public Iterator<T> iterator();

    long getTotal();

}