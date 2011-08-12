// Copyright 2011 Google Inc. All Rights Reserved.

package org.unicode.cldr.tool.resolver;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.unicode.cldr.util.CLDRFile;
import org.unicode.cldr.util.XPathParts;

/**
 * @author ryanmentley@google.com (Ryan Mentley)
 * 
 */
public class ResolverUtils {
  /**
   * This is a static class and should never be instantiated
   */
  private ResolverUtils() {
  }

  /**
   * Get the set of paths with non-null values from a CLDR file (including all extra paths).
   * 
   * @param file the CLDRFile from which to extract paths
   * @return a Set containing all the paths returned by
   *         {@link CLDRFile#iterator()}, plus those from
   *         {@link CLDRFile#getExtraPaths(java.util.Collection)}
   */
  public static Set<String> getAllPaths(CLDRFile file) {
    String locale = file.getLocaleID();
    Set<String> paths = new HashSet<String>();
    for (Iterator<String> fileIter = file.iterator(); fileIter.hasNext();) {
      paths.add(fileIter.next());
    }
    for (String path : file.getExtraPaths()) {
      if (file.getStringValue(path) != null) {
        paths.add(path);
      } else {
        debugPrintln(path + " is null in " + locale + ".", 3);
      }
    }
    return paths;
  }

  /**
   * Debugging method used to make null and empty strings more obvious in
   * printouts
   * 
   * @param str the string
   * @return "[null]" if str==null, "[empty]" if str is the empty string, str
   *         otherwise
   */
  public static String strRep(String str) {
    if (str == null) {
      return "[null]";
    } else if (str.isEmpty()) {
      return "[empty]";
    } else {
      return str;
    }
  }

  /**
   * Returns a canonical representation of an XPath for use in comparing XPaths
   * 
   * @param xPath the original XPath
   * @return the canonical representation of xPath, or {@code null} if {@code xPath == null}
   */
  public static String canonicalXpath(String xPath) {
    if (xPath != null) {
      return new XPathParts().initialize(xPath).toString();      
    } else {
      return null;
    }
    
  }

  /**
   * Debugging method to print things based on verbosity.
   * 
   * @param str The string to print
   * @param verbosity The minimum VERBOSITY level at which to print this message
   */
  static void debugPrint(String str, int verbosity) {
    if (CldrResolver.VERBOSITY >= verbosity) {
      System.out.print(str);
    }
  }

  /**
   * Debugging method to print things based on verbosity.
   * 
   * @param str
   * @param verbosity
   */
  static void debugPrintln(String str, int verbosity) {
    debugPrint(str + "\n", verbosity);
  }
}
