/*
 * Copyright (C) 2004-2011, Unicode, Inc., Google, Inc., and others.
 * For terms of use, see http://www.unicode.org/terms_of_use.html
 */

package org.unicode.cldr.tool.resolver.unittest;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.unicode.cldr.tool.resolver.CldrResolver;
import org.unicode.cldr.tool.resolver.ResolutionType;
import org.unicode.cldr.tool.resolver.ResolverUtils;
import org.unicode.cldr.util.CLDRFile;
import org.unicode.cldr.util.CldrUtility;
import org.unicode.cldr.util.CLDRFile.Factory;
import org.unicode.cldr.util.LanguageTagParser;
import org.unicode.cldr.util.XMLFileReader.SimpleHandler;

import com.ibm.icu.dev.test.TestFmwk;

/**
 * Test the simple resolution of CLDR files.
 * 
 * This will take a long time to run.
 * 
 * @author ryanmentley@google.com (Ryan Mentley)
 */
public class SimpleResolutionTests extends TestFmwk {
  private static final String LOCALES_TO_TEST = ".*";
  private static final ResolutionType RESOLUTION_TYPE = ResolutionType.SIMPLE;
  /**
   * Holds the unresolved data straight out of the resolver tool. Keyed by
   * locale, then full XPath in the canonical form retrieved by
   * {@link ResolverUtils#canonicalXpath(String)}
   */
  private Map<String, Map<String, String>> unresolvedFromTool =
      new HashMap<String, Map<String, String>>();

  /**
   * Caches the fully-resolved data retrieved from the simple tool output. Keyed
   * locale, then full XPath in the canonical form retrieved by
   * {@link ResolverUtils#canonicalXpath(String)}
   */
  private Map<String, Map<String, String>> fullyResolvedFromTool =
      new HashMap<String, Map<String, String>>();


  public void TestSimpleResolution() {
    CldrResolver resolver;
    try {
      resolver = new CldrResolver(CldrUtility.MAIN_DIRECTORY);
    } catch (IllegalArgumentException e) {
      fail("The CLDR_DIR environment variable must be set to the path of the cldr data.  "
          + "Set it using -DCLDR_DIR=/path/to/cldr");
      return;
    }
    Factory factory = resolver.getFactory();
    Set<String> locales = resolver.getLocaleNames(LOCALES_TO_TEST);

    // First pass: Resolve everything with the tool and store paths/values
    for (String locale : locales) {
      // Resolve with the tool
      CLDRFile toolResolved = resolver.resolveLocale(locale, RESOLUTION_TYPE);

      // Process the file and populate unresolvedFromTool
      SimpleHandler handler = new TestHandler(locale);
      ResolverTestUtils.processToolResolvedFile(toolResolved, handler);
    }

    // Second pass: Resolve with CLDR and check against tool output
    for (String locale : locales) {
      CLDRFile cldrResolved = factory.make(locale, true);
      Set<String> cldrPaths = new HashSet<String>();
      Map<String, String> toolResolved = getFullyResolvedToolData(locale);
      // Check to make sure no paths from the CLDR-resolved version that aren't
      // aliases get left out
      for (String distinguishedPath : ResolverUtils.getAllPaths(cldrResolved)) {
        // Ignore aliases and the //ldml/identity/ elements
        if (!distinguishedPath.endsWith("/alias")
            && !distinguishedPath.startsWith("//ldml/identity/")) {
          String canonicalPath = ResolverUtils.canonicalXpath(distinguishedPath);
          // TODO(ryanmentley): THIS IF STATEMENT IS A HACK. REMOVE THE IF
          // STATEMENT (LEAVING THE CONTENTS) WHEN TICKET XXXX IS RESOLVED
          if (!distinguishedPath.startsWith("//ldml/layout/orientation")) {
            assertTrue(
                "Path " + canonicalPath + " is present in CLDR resolved file for locale " + locale
                    + " but not in tool resolved file (value: '"
                    + cldrResolved.getStringValue(canonicalPath) + "'.",
                toolResolved.containsKey(canonicalPath));
            // Add the path to the Set for the next batch of checks
            cldrPaths.add(canonicalPath);
          }
        }
      }
      // Check to make sure that all paths from the tool-resolved version are
      // also in the CLDR-resolved version
      for (String distinguishedPath : toolResolved.keySet()) {
        // Ignore the //ldml/identity/ elements
        if (!distinguishedPath.startsWith("//ldml/identity/")) {
          // TODO(ryanmentley): THIS IF STATEMENT IS A HACK. REMOVE THE IF
          // STATEMENT (LEAVING THE CONTENTS) WHEN TICKET XXXX IS RESOLVED
          if (!distinguishedPath.startsWith("//ldml/layout/orientation")) {
            assertTrue("Path " + distinguishedPath
                + " is present in tool resolved file for locale " + locale
                + " but not in CLDR resolved file.", cldrPaths.contains(distinguishedPath));
          }
        }
      }
    }
  }

  /**
   * Gets the fully-resolved data from the simple data.<br />
   * <b>Precondition:</b> {@link #unresolvedFromTool} is already populated.
   * 
   * @param locale the locale for which to get the map
   * @return an immutable Map from distinguished path to string value
   */
  private Map<String, String> getFullyResolvedToolData(String locale) {
    if (fullyResolvedFromTool.get(locale) == null) { // Cache miss
      // Get parent by truncation
      String parent = LanguageTagParser.getParent(locale);
      if (parent == null) {
        // locale is root, just grab it straight out of the unresolved data
        fullyResolvedFromTool.put(locale,
            Collections.unmodifiableMap(unresolvedFromTool.get(locale)));
      } else {
        Map<String, String> resolvedParentMap = getFullyResolvedToolData(parent);
        Map<String, String> resolvedChildMap = new HashMap<String, String>(resolvedParentMap);
        Map<String, String> unresolvedChildMap = unresolvedFromTool.get(locale);
        for (String distinguishedPath : unresolvedChildMap.keySet()) {

          String childValue = unresolvedChildMap.get(distinguishedPath);
          if (childValue.equals(CldrResolver.UNDEFINED)) {
            assertTrue("Child locale " + locale
                + " should not contain UNDEFINED values unless the truncation parent (" + parent
                + " has a " + "value at the given path '" + distinguishedPath + "'.",
                resolvedParentMap.containsKey(distinguishedPath));
            // Delete undefined values from the child Map
            resolvedChildMap.remove(distinguishedPath);
          } else {
            // Ignore the //ldml/identity/ elements
            if (!distinguishedPath.startsWith("//ldml/identity/")) {
              assertFalse(
                  "Child ("
                      + locale
                      + ") should not contain values that are the same in the truncation parent locale ("
                      + parent + ") at path '" + distinguishedPath + "'.",
                  childValue.equals(resolvedParentMap.get(distinguishedPath)));
            }
            // Overwrite the parent value
            resolvedChildMap.put(distinguishedPath, childValue);
          }
        }
        fullyResolvedFromTool.put(locale, Collections.unmodifiableMap(resolvedChildMap));
      }
    }

    // Cache is populated now if it wasn't already; return the result from the
    // cache
    return fullyResolvedFromTool.get(locale);
  }

  private class TestHandler extends SimpleHandler {
    private String locale;

    /**
     * Creates a test handler
     * 
     * @param locale the locale being handled
     */
    public TestHandler(String locale) {
      this.locale = locale;
    }

    @Override
    public void handlePathValue(String path, String value) {
      String canonicalPath = ResolverUtils.canonicalXpath(path);
      // Populate the unresolved map
      if (!unresolvedFromTool.containsKey(locale)) {
        unresolvedFromTool.put(locale, new HashMap<String, String>());
      }
      assertFalse("Duplicate paths should never occur",
          unresolvedFromTool.get(locale).containsKey(canonicalPath));
      unresolvedFromTool.get(locale).put(canonicalPath, value);
    }
  }
}
