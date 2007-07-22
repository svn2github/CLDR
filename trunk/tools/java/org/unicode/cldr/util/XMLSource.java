/*
 ******************************************************************************
 * Copyright (C) 2005, International Business Machines Corporation and        *
 * others. All Rights Reserved.                                               *
 ******************************************************************************
 */

package org.unicode.cldr.util;

import org.unicode.cldr.util.CLDRFile.SimpleXMLSource;
import org.unicode.cldr.util.CLDRFile.Status;
import org.unicode.cldr.util.XPathParts.Comments;

import com.ibm.icu.util.Freezable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class XMLSource implements Freezable {
  public static final String CODE_FALLBACK_ID = "code-fallback";
  public static final boolean USE_PARTS_IN_ALIAS = false;
  private transient XPathParts parts = new XPathParts(null, null);
  private static Map<String,String> allowDuplicates = new HashMap<String,String>();
  
  private String localeID;
  private boolean nonInheriting;
  protected boolean locked;
  transient String[] fixedPath = new String[1];
  
  public String getLocaleID() {
    return localeID;
  }
  
  public void setLocaleID(String localeID) {
    if (locked) throw new UnsupportedOperationException("Attempt to modify locked object");
    this.localeID = localeID;
  }
  /**
   * Adds all the path,value pairs in tempMap.
   * The paths must be Full Paths.
   * @param tempMap
   * @param conflict_resolution
   */
  public void putAll(Map<String,String> tempMap, int conflict_resolution) {
    for (Iterator<String> it = tempMap.keySet().iterator(); it.hasNext();) {
      String path = it.next();
      if (conflict_resolution == CLDRFile.MERGE_KEEP_MINE && getValueAtPath(path) != null) continue;
      putValueAtPath(path, tempMap.get(path));
    }
  }
  /**
   * Adds all the path, value pairs in otherSource.
   * @param otherSource
   * @param conflict_resolution
   */
  public void putAll(XMLSource otherSource, int conflict_resolution) {
    for (Iterator<String> it = otherSource.iterator(); it.hasNext();) {
      String path = it.next();
      if (conflict_resolution == CLDRFile.MERGE_KEEP_MINE && getValueAtDPath(path) != null) continue;
      putValueAtPath(otherSource.getFullPathAtDPath(path), otherSource.getValueAtDPath(path));
    }
  }
  
  /**
   * Removes all the paths in the collection.
   * WARNING: must be distinguishedPaths
   * @param xpaths
   */
  public void removeAll(Collection<String> xpaths) {
    for (Iterator<String> it = xpaths.iterator(); it.hasNext();) {
      removeValueAtDPath(it.next());
    }
  }
  
  /**
   * Tests whether the full path for this dpath is draft or now.
   * @param path
   * @return
   */
  public boolean isDraft(String path) {
    String fullpath = getFullPath(path);
    if (path == null) return false;
    if (fullpath.indexOf("[@draft=") < 0) return false;
    return parts.set(fullpath).containsAttribute("draft");
  }
  
  public boolean isFrozen() {
    return locked;
  }
  
  /**
   * Adds the path,value pair. The path must be full path.
   * @param xpath
   * @param value
   */
  public String putValueAtPath(String xpath, String value) {
    if (locked) {
      throw new UnsupportedOperationException("Attempt to modify locked object");
    }
    String distinguishingXPath = CLDRFile.getDistinguishingXPath(xpath, fixedPath, nonInheriting);	
    putValueAtDPath(distinguishingXPath, value);
    if (!fixedPath[0].equals(distinguishingXPath)) {
      clearCache();
      putFullPathAtDPath(distinguishingXPath, fixedPath[0]);
    }
    return distinguishingXPath;
  }
  
  /**
   * Gets those paths that allow duplicates
   */
  
  public static Map getPathsAllowingDuplicates() {
    return allowDuplicates;
  }
  
  /**
   * Internal class. Immutable!
   */
  public static final class Alias {
    //public String oldLocaleID;
    private String newLocaleID;
    private String oldPath;
    private String newPath;
    private boolean pathsEqual;
    static final Pattern aliasPattern = Pattern.compile("(?:\\[@source=\"([^\"]*)\"])?(?:\\[@path=\"([^\"]*)\"])?(?:\\[@draft=\"([^\"]*)\"])?"); // constant, so no need to sync
    static final Map<String,Alias> cache = new HashMap<String,Alias>();
    
    public static Alias make(String aliasPath) {
      int pos = aliasPath.indexOf("/alias");
      if (pos < 0) return null; // quickcheck
      
      // check cache
      synchronized (Alias.class) {
        Alias result = cache.get(aliasPath);
        if (result == null) {
          result = new Alias();
          result.oldPath = aliasPath.substring(0,pos); // this is safe
          String relativePath;
          if (USE_PARTS_IN_ALIAS) {
            XPathParts tempAliasParts = new XPathParts(null, null);      
            if (!tempAliasParts.set(aliasPath).containsElement("alias")) {
              return null;
            }
            Map attributes = tempAliasParts.getAttributes(tempAliasParts.size()-1);
            result.newLocaleID = (String) attributes.get("source");
            relativePath = (String) attributes.get("path");
            if (result.newLocaleID != null && result.newLocaleID.equals("locale")) {
              result.newLocaleID = null;
            }
            if (relativePath == null) {
              result.newPath = result.oldPath;
            }
            else {
              result.newPath = tempAliasParts.trimLast().addRelative(relativePath).toString();
            }
          } else {
            // do the same as the above with a regex
            Matcher matcher = aliasPattern.matcher(aliasPath.substring(pos+6));
            if (matcher.matches()) {
              result.newLocaleID = matcher.group(1);
              if (result.newLocaleID != null && result.newLocaleID.equals("locale")) {
                result.newLocaleID = null;
              }
              String relativePath2 = matcher.group(2);
              if (relativePath2 == null) {
                result.newPath = result.oldPath;
              }
              else {
                result.newPath = addRelative(result.oldPath, relativePath2);
              }
              
//            if (false) { // test
//            if (newLocaleID != null) {
//            if (!newLocaleID.equals(result.newLocaleID)) {
//            throw new IllegalArgumentException();
//            }
//            } else if (result.newLocaleID != null) {
//            throw new IllegalArgumentException();
//            }
//            if (!relativePath2.equals(relativePath)) {
//            throw new IllegalArgumentException();
//            }
//            if (!newPath.equals(result.newPath)) {
//            throw new IllegalArgumentException();
//            }
//            }
            } else {
              throw new IllegalArgumentException("bad alias pattern for " + aliasPath);
            }
          }
          
          if (result.newPath.equals(result.oldPath) && result.newLocaleID == null) {
            throw new IllegalArgumentException("Alias must have different path or different source. AliasPath: " + aliasPath + ", Alias: " + result.toString());
          }
          result.pathsEqual = result.oldPath.equals(result.newPath);
          cache.put(aliasPath,result);
        }
        return result;
      }
    }
    
    /**
     * Create a new path from an old path + relative portion.
     * Basically, each ../ at the front of the relative portion removes a trailing
     * element+attributes from the old path.
     * WARNINGS: 
     * 1. It could fail if an attribute value contains '/'. This should not be the
     * case except in alias elements, but need to verify.
     * 2. Also assumes that there are no extra /'s in the relative or old path.
     * 3. If we verified that the relative paths always used " in place of ',
     * we could also save a step.
     * 
     * Maybe we could clean up #2 and #3 when reading in a CLDRFile the first time?
     * @param oldPath
     * @param relativePath
     * @return
     */
    static String addRelative(String oldPath, String relativePath) {
      if (relativePath.startsWith("//")) {
        return relativePath;
      }
      while (relativePath.startsWith("../")) {
        relativePath = relativePath.substring(3);
        // strip extra "/". Shouldn't occur, but just to be safe.
        while (relativePath.startsWith("/")) {
          relativePath = relativePath.substring(1);
        }
        // strip last element
        oldPath = stripLastElement(oldPath);
      }
      return oldPath + "/" + relativePath.replace('\'', '"');
    }

    //static final String ATTRIBUTE_PATTERN = "\\[@([^=]+)=\"([^\"]*)\"]";
    static final Pattern MIDDLE_OF_ATTRIBUTE_VALUE = Pattern.compile("[^\"]*\"\\]");

    public static String stripLastElement(String oldPath) {
      int oldPos = oldPath.lastIndexOf('/');
      // verify that we are not in the middle of an attribute value
      Matcher verifyElement = MIDDLE_OF_ATTRIBUTE_VALUE.matcher(oldPath.substring(oldPos));
      while (verifyElement.lookingAt()) {
        oldPos = oldPath.lastIndexOf('/',oldPos-1);
        // will throw exception if we didn't find anything
        verifyElement.reset(oldPath.substring(oldPos));
      }
      oldPath = oldPath.substring(0, oldPos);
      return oldPath;
    }
    
    public String toString() {
      return 
      //"oldLocaleID: " + oldLocaleID + ", " +
      "oldPath: " + oldPath + ", "
      + "newLocaleID: " + newLocaleID + ", "
      + "newPath: " + newPath;
    }
    /**
     * This function is called on the full path, when we know the distinguishing path matches the oldPath.
     * So we just want to modify the base of the path
     * @param oldPath 
     * @param newPath 
     * @param result
     * @return
     */
    public String changeNewToOld(String fullPath, String newPath, String oldPath) {
      // do common case quickly
      if (fullPath.startsWith(newPath)) {
        return oldPath + fullPath.substring(newPath.length());
      }
      
      // fullPath will be the same as newPath, except for some attributes at the end.
      // add those attributes to oldPath, starting from the end.
      XPathParts partsOld = new XPathParts();
      XPathParts partsNew = new XPathParts();
      XPathParts partsFull = new XPathParts();
      partsOld.set(oldPath);
      partsNew.set(newPath);
      partsFull.set(fullPath);
      Map<String,String> attributesFull = partsFull.getAttributes(-1);
      Map<String,String> attributesNew = partsNew.getAttributes(-1);
      Map<String,String> attributesOld = partsOld.getAttributes(-1);
      for (Iterator<String> it = attributesFull.keySet().iterator(); it.hasNext();) {
        String attribute = it.next();
        if (attributesNew.containsKey(attribute)) continue;
        attributesOld.put(attribute, attributesFull.get(attribute));
      }
      String result = partsOld.toString();
      
      // for now, just assume check that there are no goofy bits
      //if (!fullPath.startsWith(newPath)) {
//    if (false) {
//    throw new IllegalArgumentException("Failure to fix path. "
//    + "\r\n\tfullPath: " + fullPath
//    + "\r\n\toldPath: " + oldPath
//    + "\r\n\tnewPath: " + newPath
//    );
//    }
//    String tempResult = oldPath + fullPath.substring(newPath.length());
//    if (!result.equals(tempResult)) {
//    System.err.println("fullPath: " + fullPath + "\r\n\toldPath: "
//    + oldPath + "\r\n\tnewPath: " + newPath
//    + "\r\n\tnewPath: " + result);
//    }
      return result;
    }
    public String getOldPath() {
      return oldPath;
    }
    public String getNewLocaleID() {
      return newLocaleID;
    }
    public String getNewPath() {
      return newPath;
    }
    
    public String composeNewAndOldPath(String path) {
      return newPath + path.substring(oldPath.length());
    }
    
    public String composeOldAndNewPath(String path) {
      return oldPath + path.substring(newPath.length());
    }

    public boolean pathsEqual() {
      return pathsEqual;
    }

    public static boolean isAliasPath(String path) {
      return path.contains("/alias");
    }
  }
  // should be overriden 
  /**
   * returns a map from the aliases' parents in the keyset to the alias path
   */
  public List<Alias> addAliases(List<Alias> output) {
    for (Iterator<String> it = getAliasSet().iterator(); it.hasNext();) {
      String path = it.next();
      //if (!Alias.isAliasPath(path)) continue;
      String fullPath = getFullPathAtDPath(path);
      Alias temp = Alias.make(fullPath);
      if (temp == null) continue;
      output.add(temp);
    }
    return output;
  }
  
  /**
   * Clear any internal caches.
   */
  private void clearCache() {
    synchronized (ALIAS_CACHE) {
      UNMOD_ALIAS_CACHE = null;
    }
    synchronized (VALUE_TO_PATH_MUTEX) {
      VALUE_TO_PATH = null;
    }
  }

  private Set<String> ALIAS_CACHE = new HashSet<String>();
  private Set<String> UNMOD_ALIAS_CACHE = null;
  private Object VALUE_TO_PATH_MUTEX = new Object();
  private Relation<String,String> VALUE_TO_PATH = null;
  
  public Set<String> getAliasSet() {
    synchronized (ALIAS_CACHE) {
      if (UNMOD_ALIAS_CACHE == null) {
        ALIAS_CACHE.clear();
        for (Iterator<String> it = iterator(); it.hasNext();) {
          String path = it.next();
          if (!Alias.isAliasPath(path)) continue;
          ALIAS_CACHE.add(path);
        }
        UNMOD_ALIAS_CACHE = Collections.unmodifiableSet(ALIAS_CACHE);
      }
      return UNMOD_ALIAS_CACHE;
    }
  }
  
  /**
   * Return the localeID of the XMLSource where the path was found
   * SUBCLASSING: must be overridden in a resolving locale
   * @param path
   * @param status TODO
   * @return
   */
  public String getSourceLocaleID(String path, CLDRFile.Status status) {
    if (status != null) {
      status.pathWhereFound = CLDRFile.getDistinguishingXPath(path, null, false);
    }
    return getLocaleID();
  }
  
  /**
   * Remove the value.
   * SUBCLASSING: must be overridden in a resolving locale
   * @param xpath
   */
  public void removeValueAtPath(String xpath) {
    if (locked) throw new UnsupportedOperationException("Attempt to modify locked object");
    clearCache();
    removeValueAtDPath(CLDRFile.getDistinguishingXPath(xpath, null, nonInheriting));
  }
  /**
   * Get the value.
   * SUBCLASSING: must be overridden in a resolving locale
   * @param xpath
   * @return
   */
  public String getValueAtPath(String xpath) {
    return getValueAtDPath(CLDRFile.getDistinguishingXPath(xpath, null, nonInheriting));
  }
  /**
   * Get the full path for a distinguishing path
   * SUBCLASSING: must be overridden in a resolving locale
   * @param xpath
   * @return
   */
  public String getFullPath(String xpath) {
    return getFullPathAtDPath(CLDRFile.getDistinguishingXPath(xpath, null, nonInheriting));
  }
  
  /**
   * Put the full path for this distinguishing path
   * The caller will have processed the path, and only call this with the distinguishing path
   * SUBCLASSING: must be overridden
   */
  abstract public void putFullPathAtDPath(String distinguishingXPath, String fullxpath);
  /**
   * Put the distinguishing path, value.
   * The caller will have processed the path, and only call this with the distinguishing path
   * SUBCLASSING: must be overridden
   */
  abstract public void putValueAtDPath(String distinguishingXPath, String value);
  /**
   * Remove the path, and the full path, and value corresponding to the path.
   * The caller will have processed the path, and only call this with the distinguishing path
   * SUBCLASSING: must be overridden
   */
  abstract public void removeValueAtDPath(String distinguishingXPath);
  
  /**
   * Get the value at the given distinguishing path
   * The caller will have processed the path, and only call this with the distinguishing path
   * SUBCLASSING: must be overridden
   */
  abstract public String getValueAtDPath(String path);
  
  public boolean hasValueAtDPath(String path) {
    return (getValueAtDPath(path)!=null);
  }
  
  /**
   * Get the full path at the given distinguishing path
   * The caller will have processed the path, and only call this with the distinguishing path
   * SUBCLASSING: must be overridden
   */
  abstract public String getFullPathAtDPath(String path);
  
  /**
   * Get the comments for the source.
   * TODO: integrate the Comments class directly into this class
   * SUBCLASSING: must be overridden
   */
  abstract public Comments getXpathComments();
  /**
   * Set the comments for the source.
   * TODO: integrate the Comments class directly into this class
   * SUBCLASSING: must be overridden
   */
  abstract public void setXpathComments(Comments comments);
  /**
   * @return an iterator over the distinguished paths
   */
  abstract public Iterator<String> iterator();
  /**
   * @return an XMLSource for the given localeID; null if unavailable
   */
  abstract public XMLSource make(String localeID);
  /**
   * @return all localeIDs for which make(...) returns a non-null value
   */
  abstract public Set getAvailableLocales();
  
  /**
   * @return an iterator over the distinguished paths that start with the prefix.
   * SUBCLASSING: Normally overridden for efficiency
   */
  public Iterator<String> iterator(String prefix) {
    if (prefix == null || prefix.length() ==0) return iterator();
    return new com.ibm.icu.impl.CollectionUtilities.PrefixIterator().set(iterator(), prefix);
  }
  
  public Iterator<String> iterator(Matcher pathFilter) {
    if (pathFilter == null) return iterator();
    return new com.ibm.icu.impl.CollectionUtilities.RegexIterator().set(iterator(), pathFilter);
  }
  
  /**
   * @return returns whether resolving or not
   * SUBCLASSING: Only changed for resolving subclasses
   */
  public boolean isResolving() {
    return false;
  }
  
  /**
   * @return returns a resolving class (same one if we are resolving already)
   * SUBCLASSING: Don't override; there should be only one ResolvingSource
   */
  public XMLSource getResolving() {
    if (isResolving()) return this;
    return new ResolvingSource(this);
  }
  
  /**
   * SUBCLASSING: must be overridden
   */
  public Object cloneAsThawed() { 
    try {
      XMLSource result = (XMLSource) super.clone();
      result.locked = false;
      return result;
    } catch (CloneNotSupportedException e) {
      throw new InternalError("should never happen");
    }
  }
  /**
   * for debugging only
   */
  public String toString() {
    StringBuffer result = new StringBuffer();
    for (Iterator<String> it = iterator(); it.hasNext();) {
      String path = it.next();
      String value = getValueAtDPath(path);
      String fullpath = getFullPathAtDPath(path);
      result.append(fullpath).append(" =\t ").append(value).append("\r\n");
    }
    return result.toString();
  }
  
  /**
   * for debugging only
   */
  public String toString(String regex) {
    Matcher matcher = Pattern.compile(regex).matcher("");
    StringBuffer result = new StringBuffer();
    for (Iterator<String> it = iterator(matcher); it.hasNext();) {
      String path = it.next();
      //if (!matcher.reset(path).matches()) continue;
      String value = getValueAtDPath(path);
      String fullpath = getFullPathAtDPath(path);
      result.append(fullpath).append(" =\t ").append(value).append("\r\n");
    }
    return result.toString();
  }
  
  /**
   * @return returns whether supplemental or not
   */
  public boolean isNonInheriting() {
    return nonInheriting;
  }
  
  /**
   * @return sets whether supplemental. Normally only called internall.
   */
  public void setNonInheriting(boolean nonInheriting) {
    if (locked) throw new UnsupportedOperationException("Attempt to modify locked object");
    this.nonInheriting = nonInheriting;
  }
  
  /**
   * Internal class for doing resolution
   * @author davis
   *
   */
  private static class ResolvingSource extends XMLSource {
    private XMLSource mySource;
//    private Alias tempAlias = new Alias();
    
    private static class ParentAndPath {
      String parentID;
      String path;
      List<Alias> aliases = new ArrayList<Alias>();
      //String aliasPart;
      //String newPart;
      XMLSource source;
      String desiredLocaleID;
      
      public String toString() {
        return "[parentID: " + parentID + "; path: " + path + "; locale: " + desiredLocaleID + "; "/*+aliases: " + aliases */ + "]";
      }
      
      public ParentAndPath set(String xpath, XMLSource source, String desiredLocaleID) {
        parentID = source.getLocaleID();
        path = xpath;
        this.source = source;
        this.desiredLocaleID = desiredLocaleID;
        return this;
      }
      public ParentAndPath next() {
        aliases.clear();
        source.addAliases(aliases);
        if (aliases.size() != 0) for (Iterator<Alias> it = aliases.iterator(); it.hasNext();) {
          Alias alias = it.next();
          if (!path.startsWith(alias.getOldPath())) continue;
          // TODO fix parent, path, and return
          parentID = alias.getNewLocaleID();
          if (parentID == null) parentID = desiredLocaleID;
          source = source.make(parentID);
          path = alias.composeNewAndOldPath(path); //  alias.getNewPath() + path.substring(alias.getOldPath().length());
          return this;
        }
        parentID = LocaleIDParser.getParent(parentID);
        source = parentID == null ? null : source.make(parentID);
        return this;
      }
    }
    
    public boolean isResolving() {
      return true;
    }
    
    /*
     * If there is an alias, then inheritance gets tricky.
     * If there is a path //ldml/xyz/.../uvw/alias[@path=...][@source=...]
     * then the parent for //ldml/xyz/.../uvw/abc/.../def/
     * is source, and the path to search for is really: //ldml/xyz/.../uvw/path/abc/.../def/
     */
    public static final boolean TRACE_VALUE = false;
    
//    Map<String,String> getValueAtDPathCache = new HashMap();
    
    public String getValueAtDPath(String xpath) {
      if (TRACE_VALUE) System.out.println("xpath: " + xpath
          + "\r\n\tsource: " + mySource.getClass().getName()
          + "\r\n\tlocale: " + mySource.getLocaleID()
      );
      String result = mySource.getValueAtDPath(xpath);
      
      if (result == null) {
        AliasLocation fullStatus = getCachedFullStatus(xpath);
        if (fullStatus != null) {
          result = getSource(fullStatus).getValueAtDPath(fullStatus.pathWhereFound);
        }
      }
      if (TRACE_VALUE) System.out.println("result: " + result);
      return result;
    }
    
    public XMLSource getSource(AliasLocation fullStatus) {
      return fullStatus.localeWhereFound.equals(mySource.localeID) ? mySource 
          : fullStatus.localeWhereFound.equals(CODE_FALLBACK_ID) ? constructedItems
            : make(fullStatus.localeWhereFound);
    }

    
//    public String _getValueAtDPath(String xpath) {
//      XMLSource currentSource = mySource;
//      String result;
//      ParentAndPath parentAndPath = new ParentAndPath();
//      
//      parentAndPath.set(xpath, currentSource, getLocaleID()).next();
//      while (true) {
//        if (parentAndPath.parentID == null) {
//          return constructedItems.getValueAtDPath(xpath);
//        }
//        currentSource = make(parentAndPath.parentID); // factory.make(parentAndPath.parentID, false).dataSource;
//        if (TRACE_VALUE) System.out.println("xpath: " + parentAndPath.path
//            + "\r\n\tsource: " + currentSource.getClass().getName()
//            + "\r\n\tlocale: " + currentSource.getLocaleID()
//        );
//        result = currentSource.getValueAtDPath(parentAndPath.path);
//        if (result != null) {
//          if (TRACE_VALUE) System.out.println("result: " + result);
//          return result;
//        }
//        parentAndPath.next();
//      }
//    }
    
    Map<String,String> getFullPathAtDPathCache = new HashMap();
    
    public String getFullPathAtDPath(String xpath) {
      String result = mySource.getFullPathAtDPath(xpath);
      if (result != null) {
        return result;
      }
      // This is tricky. We need to find the alias location's path and full path.
      // then we need to the the non-distinguishing elements from them,
      // and add them into the requested path.
      AliasLocation fullStatus = getCachedFullStatus(xpath);
      if (fullStatus != null) {
        String fullPathWhereFound = getSource(fullStatus).getFullPathAtDPath(fullStatus.pathWhereFound);
        if (fullPathWhereFound == null) {
          result = null;
        } else if (fullPathWhereFound.equals(fullStatus.pathWhereFound)) {
          result = xpath; // no difference
        } else {
          result = getFullPathAtDPathCache.get(xpath);
          if (result == null) {
            // find the differences, and add them into xpath
            // we do this by walking through each element, adding the corresponding attribute values.
            // we add attributes FROM THE END, in case the lengths are different!
            XPathParts xpathParts = new XPathParts().set(xpath);
            XPathParts fullPathWhereFoundParts = new XPathParts().set(fullPathWhereFound);
            XPathParts pathWhereFoundParts = new XPathParts().set(fullStatus.pathWhereFound);
            int offset = xpathParts.size() - pathWhereFoundParts.size();
            for (int i = 0; i < pathWhereFoundParts.size(); ++i) {
              Map<String,String> fullAttributes = fullPathWhereFoundParts.getAttributes(i);
              Map<String,String> attributes = pathWhereFoundParts.getAttributes(i);
              if (!attributes.equals(fullAttributes)) { // add differences
                Map<String,String> targetAttributes = xpathParts.getAttributes(i + offset);
                for (String key : fullAttributes.keySet()) {
                  if (!attributes.containsKey(key)) {
                    String value = fullAttributes.get(key);
                    targetAttributes.put(key, value);
                  }
                }
              }
            }
            result = xpathParts.toString();
            getFullPathAtDPathCache.put(xpath, result);
          }
        }
      }
//
//      result = getFullPathAtDPathCache.get(xpath);
//      if (result == null) {
//        if (getCachedKeySet().contains(xpath)) {
//          result = _getFullPathAtDPath(xpath);
//          getFullPathAtDPathCache.put(xpath, result);
//        }
//      }
      return result;
    }

    private AliasLocation getCachedFullStatus(String xpath) {
      AliasLocation fullStatus = getSourceLocaleIDCache.get(xpath);
      if (fullStatus == null) {
        fullStatus = new AliasLocation();
        Status status = new Status();
        fullStatus.localeWhereFound = _getSourceLocaleID(xpath, status);
        fullStatus.pathWhereFound = status.pathWhereFound;
        getSourceLocaleIDCache.put(xpath, fullStatus); // cache copy
      }
      return fullStatus;
    }
    
//    private String _getFullPathAtDPath(String xpath) {
//      String result = null;
//      XMLSource currentSource = mySource;
//      ParentAndPath parentAndPath = new ParentAndPath();
//      parentAndPath.set(xpath, currentSource, getLocaleID()).next();
//      while (true) {
//        if (parentAndPath.parentID == null) {
//          return constructedItems.getFullPathAtDPath(xpath);
//        }
//        currentSource = make(parentAndPath.parentID); // factory.make(parentAndPath.parentID, false).dataSource;
//        result = currentSource.getValueAtDPath(parentAndPath.path);
//        if (result != null) {
//          result = currentSource.getFullPathAtDPath(parentAndPath.path);
//          return tempAlias.changeNewToOld(result, parentAndPath.path, xpath);
//        }
//        parentAndPath.next();
//      }
//    }
    
    public String getWinningPath(String xpath) {
      String result = mySource.getWinningPath(xpath);
      if (result != null) return result;
      AliasLocation fullStatus = getCachedFullStatus(xpath);
      if (fullStatus != null) {
        result = getSource(fullStatus).getWinningPath(fullStatus.pathWhereFound);
      } else {
        result = xpath;
      }
//
//      result = getWinningPathCache.get(xpath);
//      if (result == null) {
//        if (!getCachedKeySet().contains(xpath)) {
//          return xpath;
//        }
//        result = _getWinningPath(xpath);
//        getWinningPathCache.put(xpath, result);
//      }
      return result;
    }
    
//    Map<String,String> getWinningPathCache = new HashMap();
//    
//    public String _getWinningPath(String xpath) {
//      XMLSource currentSource = mySource;
//      ParentAndPath parentAndPath = new ParentAndPath();
//      parentAndPath.set(xpath, currentSource, getLocaleID()).next();
//      while (true) {
//        if (parentAndPath.parentID == null) {
//          return xpath; // ran out of parents
//          //return constructedItems.getWinningPath(xpath);
//        }
//        currentSource = make(parentAndPath.parentID); // factory.make(parentAndPath.parentID, false).dataSource;
//        String result = currentSource.getWinningPath(parentAndPath.path);
//        if (result != null) {
//          return result;
//        }
//        parentAndPath.next();
//      }
//    }
    
    class AliasLocation {
      public String pathWhereFound;
      public String localeWhereFound;
      
      public AliasLocation set(AliasLocation status) {
        pathWhereFound = status.pathWhereFound;
        localeWhereFound = status.localeWhereFound;
        return this;
      }
    }
    
    private transient Map<String,AliasLocation> getSourceLocaleIDCache = new HashMap();
    
    public String getSourceLocaleID(String distinguishedXPath, CLDRFile.Status status) {
      //xpath = CLDRFile.getDistinguishingXPath(xpath, null, false);
      boolean hasValue = mySource.hasValueAtDPath(distinguishedXPath);
      //String result = currentSource.getValueAtDPath(xpath);
      if (hasValue) {
        if (status != null) {
          status.pathWhereFound = distinguishedXPath;
        }
        return mySource.getLocaleID();
      }
      // otherwise, it might be an alias; figure that out and return
      AliasLocation fullStatus = getCachedFullStatus(distinguishedXPath);
      if (fullStatus == null) {
        fullStatus = new AliasLocation();
        if (status == null) {
          status = new Status();
        }
        fullStatus.localeWhereFound = _getSourceLocaleID(distinguishedXPath, status);
        fullStatus.pathWhereFound = status.pathWhereFound;
        getSourceLocaleIDCache.put(distinguishedXPath, fullStatus); // cache copy
        
      } else if(status != null) {
        // copy pathWhereFound from cache
        status.pathWhereFound = fullStatus.pathWhereFound;
      }
      return fullStatus.localeWhereFound;
    }
    
    public String _getSourceLocaleID(String xpath, CLDRFile.Status status) {
      XMLSource currentSource = mySource;
      ParentAndPath parentAndPath = new ParentAndPath();
      parentAndPath.set(xpath, currentSource, getLocaleID()).next();
      while (true) {
        if (parentAndPath.parentID == null) {
          if (status != null) {
            status.pathWhereFound = parentAndPath.path;
          }
          return CODE_FALLBACK_ID;
        }
        currentSource = make(parentAndPath.parentID);
        boolean result = currentSource.hasValueAtDPath(parentAndPath.path);
        //result = currentSource.getValueAtDPath(parentAndPath.path);
        if(result != false) { // was: null
          /*result = */ currentSource.getFullPathAtDPath(parentAndPath.path); // what does this do?
          if (status != null) {
            status.pathWhereFound = parentAndPath.path;
          }
          return currentSource.getLocaleID();
        }
        parentAndPath.next();
      }
    }
    /**
     * We have to go through the source, add all the paths, then recurse to parents
     * However, aliases are tricky, so watch it.
     */
    static final boolean TRACE_FILL = false;
    private void fillKeys(int level, XMLSource currentSource, Alias alias, List<Alias> excludedAliases, Set<String> resultKeySet) {
      if (TRACE_FILL) {
        if (level > 10) throw new IllegalArgumentException("Stack overflow");
        System.out.println(Utility.repeat("\t",level) + "mySource.getLocaleID(): " + currentSource.getLocaleID());
        System.out.println(Utility.repeat("\t",level) + "currentSource.getClass().getName(): " + currentSource.getClass().getName());
        System.out.println(Utility.repeat("\t",level) + "alias: " + alias);
        System.out.println(Utility.repeat("\t",level) + "cachedKeySet.size(): " + resultKeySet.size());
        System.out.println(Utility.repeat("\t",level) + "excludedAliases: " + excludedAliases);
      }
      List<Alias> collectedAliases = null;
      // make a pass through, filling all the direct paths, excluding aliases, and collecting others
      for (Iterator<String> it = currentSource.iterator(); it.hasNext();) {
        String path = it.next();
        String originalPath = path;
        if (alias != null) {
          if (!path.startsWith(alias.getNewPath())) continue; // skip unless matches alias
          if (!alias.pathsEqual()) { // substitute OLD path // getOldPath().equals(alias.getNewPath())
            path = alias.composeOldAndNewPath(path); // alias.getOldPath() + path.substring(alias.getNewPath().length());
          }
        }
        if (excludedAliases != null && startsWith(path, excludedAliases)) {
          //System.out.println("Skipping: " + path);
          continue;
        }
        if (Alias.isAliasPath(path)) { // quick check
          String fullPath = currentSource.getFullPathAtDPath(originalPath);
          // it's ok that the fullpath is not mapped to the old path, since 
          // the only thing the Alias.make cares about is the last bit
          Alias possibleAlias = Alias.make(fullPath);
          if (possibleAlias != null) {
            if (collectedAliases == null) collectedAliases = new ArrayList<Alias>();
            collectedAliases.add(possibleAlias);
          }
        }
        resultKeySet.add(path); // Note: we add the aliases
      }
      
      // recurse on the parent, unless at the end of the line (constructedItems
      if (currentSource != constructedItems) { // end of the line?
        if (TRACE_FILL) {
          System.out.println(Utility.repeat("\t",level) + "Recursing on Parent: ");
        }
        XMLSource parentSource = constructedItems; // default
        String parentID = LocaleIDParser.getParent(currentSource.getLocaleID());
        if (parentID != null) parentSource = make(parentID); // factory.make(parentID, false).dataSource;
        if (collectedAliases != null) {
          if (excludedAliases == null) excludedAliases = new ArrayList<Alias>();
          else excludedAliases.addAll(collectedAliases);
        }
        fillKeys(level+1, parentSource, alias, excludedAliases, resultKeySet);
      }
      
      // now recurse on the aliases we found
      if (collectedAliases != null) for (Iterator<Alias> it = collectedAliases.iterator(); it.hasNext();) {
        if (TRACE_FILL) {
          System.out.println(Utility.repeat("\t",level) + "Recursing on Alias: ");
        }
        Alias foundAlias = it.next();
        // this is important. If the new source is null, use *this* (the desired locale)
        XMLSource aliasSource = mySource;
        if (foundAlias.getNewLocaleID() != null) {
          aliasSource = make(foundAlias.getNewLocaleID()); // factory.make(foundAlias.newLocaleID, false).dataSource;
        }
        fillKeys(level+1, aliasSource, foundAlias, null, resultKeySet);
      }
      if (TRACE_FILL) System.out.println(Utility.repeat("\t",level) + "=> cachedKeySet.size(): " + resultKeySet.size());
    }
    
    private transient Set<String> cachedKeySet = null;
    /**
     * This function is kinda tricky. What it does it come up with the set of all the paths that
     * would return a value, fully resolved. This wouldn't be a problem but for aliases.
     * Whenever there is an alias oldpath = p relativePath = x source=y
     * Then you have to *not* add any of the oldpath... from the normal inheritance heirarchy
     * Instead from source, you see everything that matches oldpath+relativePath + z, and for each one
     * add oldpath+z
     */
    public Iterator<String> iterator() {
      return getCachedKeySet().iterator();
    }
    private Set<String> getCachedKeySet() {
      if (cachedKeySet == null) {
        cachedKeySet = new HashSet<String>();
        fillKeys(0, mySource, null, null, cachedKeySet);
        //System.out.println("CachedKeySet: " + cachedKeySet);
        //cachedKeySet.addAll(constructedItems.keySet());
        cachedKeySet = Collections.unmodifiableSet(cachedKeySet);
      }
      return cachedKeySet;
    }
    private static boolean startsWith(String path, List<Alias> aliasPaths) {
      for (Iterator<Alias> it = aliasPaths.iterator(); it.hasNext();) {
        if (path.startsWith(it.next().getOldPath())) return true;
      }
      return false;
    }
    public void putFullPathAtDPath(String distinguishingXPath, String fullxpath) {
      throw new UnsupportedOperationException("Resolved CLDRFiles are read-only");
    }
    public void putValueAtDPath(String distinguishingXPath, String value) {
      throw new UnsupportedOperationException("Resolved CLDRFiles are read-only");
    }
    public Comments getXpathComments() {
      return mySource.getXpathComments();
    }
    public void setXpathComments(Comments path) {
      throw new UnsupportedOperationException("Resolved CLDRFiles are read-only");		
    }
    public void removeValueAtDPath(String xpath) {
      throw new UnsupportedOperationException("Resolved CLDRFiles are read-only");
    }
    public Object freeze() {
        return this; // No-op. ResolvingSource is already read-only. 
    }
    public ResolvingSource(/*Factory factory, */XMLSource source) {
      super();
      //this.factory = factory;
      mySource = source;
    }
    public String getLocaleID() {
      return mySource.getLocaleID();
    }
    
    private static final String [] keyDisplayNames = {
      "calendar",
      "collation",
      "currency"
    };
    private static final String[][] typeDisplayNames = {
      { "big5han", "collation" },
      { "buddhist", "calendar" },
      { "chinese", "calendar" },
      { "direct", "collation" },
      { "gb2312han", "collation" },
      { "gregorian", "calendar" },
      { "hebrew", "calendar" },
      { "islamic", "calendar" },
      { "islamic-civil", "calendar" },
      { "japanese", "calendar" },
      { "phonebook", "collation" },
      { "pinyin", "collation" },
      { "stroke", "collation" },
      { "traditional", "collation" } };
    private static final boolean SKIP_SINGLEZONES = false;
    private static XMLSource constructedItems = new SimpleXMLSource(null, null);
    
    static {
      StandardCodes sc = StandardCodes.make();
      Map countries_zoneSet = sc.getCountryToZoneSet();
      Map zone_countries = sc.getZoneToCounty();
      
      //Set types = sc.getAvailableTypes();
      for (int typeNo = 0; typeNo <= CLDRFile.TZ_START; ++typeNo ) {
        String type = CLDRFile.getNameName(typeNo);
        //int typeNo = typeNameToCode(type);
        //if (typeNo < 0) continue;
        String type2 = (typeNo == CLDRFile.CURRENCY_SYMBOL) ? CLDRFile.getNameName(CLDRFile.CURRENCY_NAME)
            : (typeNo >= CLDRFile.TZ_START) ? "tzid"
                : type;				
        Set<String> codes = sc.getGoodAvailableCodes(type2);
        //String prefix = CLDRFile.NameTable[typeNo][0];
        //String postfix = CLDRFile.NameTable[typeNo][1];
        //String prefix2 = "//ldml" + prefix.substring(6); // [@version=\"" + GEN_VERSION + "\"]
        for (Iterator<String> codeIt = codes.iterator(); codeIt.hasNext(); ) {
          String code = codeIt.next();
          String value = code;
          if (typeNo == CLDRFile.TZ_EXEMPLAR) { // skip single-zone countries
            if (SKIP_SINGLEZONES) {
              String country = (String) zone_countries.get(code);
              Set s = (Set) countries_zoneSet.get(country);
              if (s != null && s.size() == 1) continue;
            }
            value = TimezoneFormatter.getFallbackName(value);
          }
          addFallbackCode(typeNo, code, value);
        }
      }
      String[] extraCodes = {"de_AT", "de_CH", "en_AU", "en_CA", "en_GB", "en_US", "es_419", "es_ES", "fr_CA", "fr_CH", "nl_BE", "pt_BR", "pt_PT", "zh_Hans", "zh_Hant"};
      for (String extraCode : extraCodes) {
        addFallbackCode(CLDRFile.LANGUAGE_NAME, extraCode, extraCode);
      }
      addFallbackCode(CLDRFile.TERRITORY_NAME, "HK", "HK", "short");
      addFallbackCode(CLDRFile.TERRITORY_NAME, "MO", "MO", "short");
      
      for (int i = 0; i < keyDisplayNames.length; ++i) {
        constructedItems.putValueAtPath(
            "//ldml/localeDisplayNames/keys/key" +
            "[@type=\"" + keyDisplayNames[i] + "\"]",
            keyDisplayNames[i]);
      }
      for (int i = 0; i < typeDisplayNames.length; ++i) {
        constructedItems.putValueAtPath(
            "//ldml/localeDisplayNames/types/type"
            + "[@type=\"" + typeDisplayNames[i][0] + "\"]"
            + "[@key=\"" + typeDisplayNames[i][1] + "\"]",
            typeDisplayNames[i][0]);
      }
      String[][] relativeValues = {
          {"Three days ago", "-3"},
          {"The day before yesterday", "-2"},
          {"Yesterday", "-1"},
          {"Today", "0"},
          {"Tomorrow", "1"},
          {"The day after tomorrow", "2"},
          {"Three days from now", "3"},
      };
      for (int i = 0; i < relativeValues.length; ++i) {
        constructedItems.putValueAtPath(
            "//ldml/dates/calendars/calendar[@type=\"gregorian\"]/fields/field[@type=\"day\"]/relative[@type=\"" + relativeValues[i][1] + "\"]",
            relativeValues[i][0]);
      }

      constructedItems.freeze();
      allowDuplicates = Collections.unmodifiableMap(allowDuplicates);
      //System.out.println("constructedItems: " + constructedItems);
    }
    
    private static void addFallbackCode(int typeNo, String code, String value) {
      addFallbackCode(typeNo, code, value, null);
    }
    private static void addFallbackCode(int typeNo, String code, String value, String alt) {
      //String path = prefix + code + postfix;
      String fullpath = CLDRFile.getKey(typeNo, code);
      if (alt != null) {
        fullpath = fullpath.replace("]", "][@alt=\"" + alt + "\"]");
      }
      //System.out.println(fullpath + "\t=> " + code);
      String distinguishingPath = constructedItems.putValueAtPath(fullpath, value);
      if (typeNo == CLDRFile.LANGUAGE_NAME || typeNo == CLDRFile.SCRIPT_NAME || typeNo == CLDRFile.TERRITORY_NAME) {
        allowDuplicates.put(distinguishingPath, code);
      }
    }
    public XMLSource make(String localeID) {
      return mySource.make(localeID);
    }
    public Set getAvailableLocales() {
      return mySource.getAvailableLocales();
    }
    @Override
    public boolean isHere(String path) {
      return mySource.isHere(path); // only test one level
    }
  }
  /**
   * See CLDRFile isWinningPath for documentation
   * @param path
   * @return
   */
  public boolean isWinningPath(String path) {
    return getWinningPath(path).equals(path);
  }

  /**
   * See CLDRFile getWinningPath for documentation.
   * Default implementation is that it removes draft and  [@alt="...proposed..." if possible
   * @param path
   * @return
   */
  public String getWinningPath(String path) {
    String newPath = CLDRFile.getNondraftNonaltXPath(path);
    if (!newPath.equals(path)) {
      String value = getValueAtPath(newPath); // ensure that it still works
      if (value != null) {
        return newPath;
      }
    }
    return path;
  }

  /**
   * return true if the path in this file (without resolution). Default implementation is to just see if the path has a value.
   * The resolved source must just test the top level.
   * @param path
   * @return
   */
  public boolean isHere(String path) {
    return getValueAtPath(path) != null;
  }

  /**
   * Find all the distinguished paths having values matching valueToMatch, and add them to result.
   * @param valueToMatch
   * @param pathPrefix
   * @param result
   */
  public void getPathsWithValue(String valueToMatch, String pathPrefix, Set<String> result) {
    // build a Relation mapping value to paths, if needed
    synchronized (VALUE_TO_PATH_MUTEX) {
      if (VALUE_TO_PATH == null) {
        VALUE_TO_PATH = new Relation(new HashMap(), HashSet.class);
        for (Iterator<String> it = iterator(); it.hasNext();) {
          String path = it.next();
          String value = getValueAtDPath(path);
          VALUE_TO_PATH.put(value, path);
        }
      }
      Set<String> paths = VALUE_TO_PATH.getAll(valueToMatch);
      if (paths == null) {
        return;
      }
      if (pathPrefix == null || pathPrefix.length() == 0) {
        result.addAll(paths);
        return;
      }
      for (String path : paths) {
        if (path.startsWith(pathPrefix)) {
          result.add(path);
        }
      }
    }
  }
}
