/*
 * Copyright (C) 2004-2011, Unicode, Inc., Google, Inc., and others.
 * For terms of use, see http://www.unicode.org/terms_of_use.html
 */

package org.unicode.cldr.tool.resolver.unittest;

import java.util.HashSet;
import java.util.Set;

import org.unicode.cldr.tool.resolver.CldrResolver;
import org.unicode.cldr.tool.resolver.ResolutionType;
import org.unicode.cldr.tool.resolver.ResolverUtils;
import org.unicode.cldr.util.CLDRFile;
import org.unicode.cldr.util.CldrUtility;
import org.unicode.cldr.util.Factory;

import com.ibm.icu.dev.test.TestFmwk;

/**
 * A superclass for all Resolver tests to encompass common functionality
 * 
 * @author ryanmentley@google.com (Ryan Mentley)
 */
public abstract class ResolverTest extends TestFmwk {
  /**
   * Gets the fully-resolved data for the locale.
   * 
   * @param locale the locale for which to get the map
   * @return an immutable Map from distinguished path to string value
   */
  protected CLDRFile loadToolDataFromResolver(String locale) {
      // Resolve with the tool
      CLDRFile toolResolved = resolver.resolveLocale(locale);
      return toolResolved;
  }

  /**
   * Determines whether an XPath should be ignored for testing purposes
   * 
   * @param distinguishedPath a distinguished XPath
   * @param file the CLDRFile from which the path was retrieved
   * @return {@code true} if the path should be ignored; {@code false} otherwise
   */
  protected boolean shouldIgnorePath(String distinguishedPath, CLDRFile file) {
      return distinguishedPath.endsWith("/alias") || distinguishedPath.startsWith("//ldml/identity/");
  }

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

  private CldrResolver resolver;

  public ResolverTest() {
      resolver = new CldrResolver(CldrUtility.MAIN_DIRECTORY, getResolutionType());
  }

  /**
   * Template method to test any type of CLDR resolution
   */
  public final void TestResolution() {
    Factory factory = resolver.getFactory();
    Set<String> locales = resolver.getLocaleNames(getLocalesToTest());

    // Resolve with CLDR and check against CldrResolver output.
    for (String locale : locales) {
      CLDRFile cldrResolved = factory.make(locale, true);
      Set<String> cldrPaths = new HashSet<String>();
      CLDRFile toolResolved = loadToolDataFromResolver(locale);
      // Check to make sure no paths from the CLDR-resolved version that aren't
      // explicitly excluded get left out
      for (String distinguishedPath : ResolverUtils.getAllPaths(cldrResolved)) {
        // Check if path should be ignored
        if (!shouldIgnorePath(distinguishedPath, cldrResolved)) {
          String cldrValue = cldrResolved.getStringValue(distinguishedPath);
          String toolValue = toolResolved.getStringValue(distinguishedPath);
          assertNotNull("Path " + distinguishedPath + " is present in CLDR resolved file for locale "
              + locale + " but not in tool resolved file (CLDR value: '" + cldrValue + "').",
              toolValue);
          assertEquals("Tool resolved value for " + distinguishedPath + " in locale " + locale
              + " should match CLDRFile resolved value", cldrValue, toolValue);
          // Add the path to the Set for the next batch of checks
          cldrPaths.add(distinguishedPath);
        }
      }
      // Check to make sure that all paths from the tool-resolved version are
      // also in the CLDR-resolved version
      for (String canonicalPath : toolResolved) {
        // Check if path should be ignored
        if (!shouldIgnorePath(canonicalPath, cldrResolved)) {
          assertTrue("Path " + canonicalPath + " is present in tool resolved file for locale "
              + locale + " but not in CLDR resolved file.", cldrPaths.contains(canonicalPath));
        }
      }
    }
  }
}
