/*
 * Copyright (C) 2004-2011, Unicode, Inc., Google, Inc., and others.
 * For terms of use, see http://www.unicode.org/terms_of_use.html
 */

package org.unicode.cldr.tool.resolver.unittest;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.unicode.cldr.tool.resolver.CldrResolver;
import org.unicode.cldr.tool.resolver.ResolutionType;
import org.unicode.cldr.tool.resolver.ResolverUtils;
import org.unicode.cldr.util.CLDRFile;
import org.unicode.cldr.util.CldrUtility;
import org.unicode.cldr.util.Factory;
import org.unicode.cldr.util.XMLFileReader.SimpleHandler;

import com.ibm.icu.dev.test.TestFmwk;

/**
 * A superclass for all Resolver tests to encompass common functionality
 * 
 * @author ryanmentley@google.com (Ryan Mentley)
 */
public abstract class ResolverTest extends TestFmwk {
  /**
   * Gets the fully-resolved data for the locale<br />
   * <b>Precondition:</b> caches have already been populated.
   * 
   * @param locale the locale for which to get the map
   * @return an immutable Map from distinguished path to string value
   */
  protected abstract Map<String, String> getFullyResolvedToolData(String locale);

  /**
   * Determines whether an XPath should be ignored for testing purposes
   * 
   * @param distinguishedPath a distinguished XPath
   * @param file the CLDRFile from which the path was retrieved
   * @return {@code true} if the path should be ignored; {@code false} otherwise
   */
  protected abstract boolean shouldIgnorePath(String distinguishedPath, CLDRFile file);

  /**
   * Gets the set of locales to be tested
   * 
   * @return a regular expression matching the locales to be tested
   */
  protected abstract String getLocalesToTest();

  /**
   * Gets the resolution type for the test
   * 
   * @return a non-null {@link ResolutionType}
   */
  protected abstract ResolutionType getResolutionType();

  /**
   * Make a handler to handle incoming XML data
   * 
   * @param locale the name of the locale to handle
   * @return a {@link SimpleHandler} to handle the XML data for the given locale
   */
  protected abstract SimpleHandler makeHandler(String locale);
  
  /**
   * Transform a distinguished XPath to the canonical form that the XML file
   * will return
   * 
   * @param distinguishedPath a distinguished XPath
   * @return a String in the same form as paths canonicalized from XML files
   */
  protected abstract String canonicalizeDPath(String distinguishedPath, CLDRFile file);

  /**
   * Template method to test any type of CLDR resolution
   */
  public final void TestResolution() {
    CldrResolver resolver;
    try {
      resolver = new CldrResolver(CldrUtility.MAIN_DIRECTORY);
    } catch (IllegalArgumentException e) {
      fail("The CLDR_DIR environment variable must be set to the path of the cldr data.  "
          + "Set it using -DCLDR_DIR=/path/to/cldr");
      return;
    }
    Factory factory = resolver.getFactory();
    Set<String> locales = resolver.getLocaleNames(getLocalesToTest());

    // First pass: Resolve everything with the tool and store paths/values
    for (String locale : locales) {
      // Resolve with the tool
      CLDRFile toolResolved = resolver.resolveLocale(locale, getResolutionType());

      // Process the file and populate caches
      SimpleHandler handler = makeHandler(locale);
      ResolverTestUtils.processToolResolvedFile(toolResolved, handler);
    }

    // Second pass: Resolve with CLDR and check against tool output
    for (String locale : locales) {
      CLDRFile cldrResolved = factory.make(locale, true);
      Set<String> cldrPaths = new HashSet<String>();
      Map<String, String> toolResolved = getFullyResolvedToolData(locale);
      // Check to make sure no paths from the CLDR-resolved version that aren't
      // explicitly excluded get left out
      for (String distinguishedPath : ResolverUtils.getAllPaths(cldrResolved)) {
        // Check if path should be ignored
        if (!shouldIgnorePath(distinguishedPath, cldrResolved)) {
          String canonicalPath = canonicalizeDPath(distinguishedPath, cldrResolved);
          String cldrValue = cldrResolved.getStringValue(distinguishedPath);
          assertTrue("Path " + canonicalPath + " is present in CLDR resolved file for locale "
              + locale + " but not in tool resolved file (CLDR value: '" + cldrValue + "').",
              toolResolved.containsKey(canonicalPath));
          assertEquals("Tool resolved value for " + canonicalPath + " in locale " + locale
              + " should match CLDRFile resolved value", cldrValue, toolResolved.get(canonicalPath));
          // Add the path to the Set for the next batch of checks
          cldrPaths.add(canonicalPath);
        }
      }
      // Check to make sure that all paths from the tool-resolved version are
      // also in the CLDR-resolved version
      for (String canonicalPath : toolResolved.keySet()) {
        // Check if path should be ignored
        if (!shouldIgnorePath(canonicalPath, cldrResolved)) {
          assertTrue("Path " + canonicalPath + " is present in tool resolved file for locale "
              + locale + " but not in CLDR resolved file.", cldrPaths.contains(canonicalPath));
        }
      }
    }
  }
}
