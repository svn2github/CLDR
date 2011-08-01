/*
 * Copyright (C) 2004-2011, Unicode, Inc., Google, Inc., and others.
 * For terms of use, see http://www.unicode.org/terms_of_use.html
 */

package org.unicode.cldr.tool.resolver.unittest;

import java.io.PrintWriter;
import java.io.StringWriter;

import org.unicode.cldr.util.CLDRFile;
import org.unicode.cldr.util.XPathParts;

/**
 * Package-private class containing helper methods for testing the CLDR resolver tool
 *  
 * @author ryanmentley@google.com (Ryan Mentley)
 */
class ResolverTestUtils {
  
  /**
   * Static class, no public constructor
   */
  private ResolverTestUtils() {}

  /**
   * Prints a CLDRFile to a String
   * 
   * @param cldrFile the CLDRFile to print
   * @return a String containing the data from cldrFile in XML
   */
  static String writeToString(CLDRFile cldrFile) {
    StringWriter stringOut = new StringWriter();
    PrintWriter pw = new PrintWriter(stringOut);
    cldrFile.write(pw);
    String xml = stringOut.toString();
    return xml;
  }
  
  /**
   * Returns a canonical representation of an XPath for use in comparing XPaths
   * 
   * @param xPath the original XPath
   * @return the canonical representation of xPath
   */
  static String canonicalXpath(String xPath) {
    return new XPathParts().initialize(xPath).toString();
  }
}
