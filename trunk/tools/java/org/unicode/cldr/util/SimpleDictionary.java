package org.unicode.cldr.util;

import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * This is a simple dictionary class used for testing.
 * @author markdavis
 */
public class SimpleDictionary extends Dictionary implements Dictionary.Builder {
  private TreeMap<CharSequence, Integer> data = new TreeMap<CharSequence, Integer>();
  private Set<CharSequence> possibleMatchesBefore;
  private Set<CharSequence> possibleMatchesAfter;
  private Status finalStatus;
  boolean done;
  private int matchCount;
  private CharSequence lastEntry = "";

  public Builder addMapping(CharSequence text, int result) {
    if (compare(text,lastEntry) <= 0) {
      throw new IllegalArgumentException("Each string must be greater than the previous one.");
    }
    lastEntry = text;
    data.put(text, result);
    return this;
  }

  public Map<CharSequence, Integer> getMapping() {
    return Collections.unmodifiableMap(data);
  }

  @Override
  public Dictionary setOffset(int offset) {
    possibleMatchesBefore = data.keySet();
    done = false;
    return super.setOffset(offset);
  }

  /**
   * Dumb implementation.
   * 
   */
  @Override
  public Status next() {
    // There are two degenerate cases: our dictionary is empty, or we are called on an empty string.

    // As long as we get matches, we return them.
    // When we fail, we return one of two statuses
    // DONE if there were no more matches in the dictionary past the last match
    // SINGLE if there was a longer match, plus the longest offset that we successfully got to.
    
    // if we have already narrowed down to the end, just return the status
    // everything should already be set to make this work.
    if (done) {
      if (finalStatus == Status.NONE) {
        matchValue = matchValue = Integer.MIN_VALUE;
      }
      return finalStatus;
    }

    CharSequence firstMatch = null;
    
    while (matchEnd < text.length()) {
      // get the next probe value
      ++matchEnd; 
      CharSequence probe = text.subSequence(offset, matchEnd);
      
      // narrow to the items that start with the probe
      // this filters Before into After
      
      firstMatch = filterToStartsWith(probe);
      
      // if we have a full match, return it
      
      if (firstMatch != null && firstMatch.length() == probe.length()) {
        possibleMatchesAfter.remove(firstMatch);
        possibleMatchesBefore = possibleMatchesAfter;
        matchValue = data.get(firstMatch);
        finalStatus = Status.NONE;
        return Status.MATCH;
      }

      // See if we've run out
      // example: probe = "man"
      // three cases, based on what was in the set
      // {man}: return DONE (we did a match before)
      // {man, many}: return SINGLE
      // {man, many, manner}: return PLURAL
      // {many}: return SINGLE
      if (possibleMatchesAfter.size() == 0) {
        --matchEnd; // backup
        break;
      }
      possibleMatchesBefore = possibleMatchesAfter;
    }
    // no more work to be done.
    done = true;
    
    if (matchEnd == offset || possibleMatchesBefore.size() == 0) {
      matchValue = Integer.MIN_VALUE;
      return finalStatus = Status.NONE;
    }
    if (firstMatch == null) { // just in case we skipped the above loop
      firstMatch = possibleMatchesBefore.iterator().next();
    }
    matchValue = data.get(firstMatch);
    matchCount = possibleMatchesBefore.size();
    return finalStatus = Status.PARTIAL;
  }
  
  public boolean nextUniquePartial() {
    // we have already set the matchValue, so we don't need to reset here.
    return matchCount == 1;
  } 
  
  /**
   * Returns the first matching item, if there is one, and
   * filters the rest of the list to those that match the probe.
   * @param probe
   * @return
   */
  private CharSequence filterToStartsWith(CharSequence probe) {
    CharSequence result = null;
    possibleMatchesAfter = new TreeSet();
    for (Iterator<CharSequence> it = possibleMatchesBefore.iterator(); it.hasNext();) {
      CharSequence item = it.next();
      if (startsWith(item, probe)) {
        if (result == null) {
          result = item;
        }
        possibleMatchesAfter.add(item);
      }
    }
    return result;
  }
  
  public static boolean startsWith(CharSequence first, CharSequence possiblePrefix) {
    if (first.length() < possiblePrefix.length()) {
      return false;
    }
    for (int i = 0; i < possiblePrefix.length(); ++i) {
      if (first.charAt(i) != possiblePrefix.charAt(i)) {
        return false;
      }
    }
    return true;
  }

  public boolean contains(CharSequence text) {
    return data.containsKey(text);
  }
  
  public int get(CharSequence text) {
    return data.get(text);
  }
//  public static class GeqGetter<K> {
//    private Set<K> set;
//    private Iterator<K> iterator;
//    private Comparator<? super K> comparator;
//    boolean done;
//    K lastItem = null;
//    private int count;
//    
//    public GeqGetter(Set<K> set, Comparator<K> comparator) {
//      this.comparator = comparator;
//      this.set = set;
//      reset();
//    }
//
//    public GeqGetter reset() {
//      iterator = set.iterator();
//      done = false;
//      return this;
//    }
//
//    /**
//     * Returns least element greater than or equal to probe.
//     * @param probe
//     * @return
//     */
//    public K getGeq(K probe) {
//      if (lastItem != null && comparator.compare(lastItem, probe) >= 0) {
//        return lastItem;
//      }
//      count = 0;
//      while (iterator.hasNext()) {
//        lastItem = iterator.next();
//        ++count;
//        if (comparator.compare(lastItem, probe) >= 0) {
//          return lastItem;
//        }
//      }
//      lastItem = null;
//      return lastItem;
//    }
//  }

//  public static class CharSequenceComparator implements Comparator<CharSequence> {
//    
//    public int compare(CharSequence first, CharSequence second) {
//      int minLen = first.length();
//      if (minLen > second.length())
//        minLen = second.length();
//      int result;
//      for (int i = 0; i < minLen; ++i) {
//        if (0 != (result = first.charAt(i) - second.charAt(i)))
//          return result;
//      }
//      return first.length() - second.length();
//    }
//    
//  }
}