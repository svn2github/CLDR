/*
 * Copyright (C) 2004-2011, Unicode, Inc., Google, Inc., and others.
 * For terms of use, see http://www.unicode.org/terms_of_use.html
 */

package org.unicode.cldr.tool.resolver.unittest;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.unicode.cldr.tool.resolver.CldrResolver;
import org.unicode.cldr.tool.resolver.ResolutionType;
import org.unicode.cldr.tool.resolver.ResolverUtils;
import org.unicode.cldr.util.CLDRFile;
import org.unicode.cldr.util.LocaleIDParser;
import org.unicode.cldr.util.XMLFileReader.SimpleHandler;

/**
 * Test the simple resolution of CLDR files.
 * 
 * This will take a long time to run.
 * 
 * @author ryanmentley@google.com (Ryan Mentley)
 */
public class SimpleResolutionTest extends ResolverTest {
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

  /**
   * This is needed because the testing framework does not detect inherited test
   * methods
   */
  public void TestSimpleResolution() {
    TestResolution();
  }

  /*
   * (non-Javadoc)
   * 
   * @see
   * org.unicode.cldr.tool.resolver.unittest.ResolverTest#getFullyResolvedToolData
   * (java.lang.String)
   */
  @Override
  protected Map<String, String> getFullyResolvedToolData(String locale) {
    if (fullyResolvedFromTool.get(locale) == null) { // Cache miss
      // Get parent by truncation
      String parent = LocaleIDParser.getParent(locale);
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

  @Override
  protected String getLocalesToTest() {
    return LOCALES_TO_TEST;
  }

  @Override
  protected ResolutionType getResolutionType() {
    return RESOLUTION_TYPE;
  }

  @Override
  protected SimpleHandler makeHandler(String locale) {
    return new TestHandler(locale);
  }

  @Override
  protected boolean shouldIgnorePath(String distinguishedPath, CLDRFile file) {
    if (distinguishedPath.endsWith("/alias") || distinguishedPath.startsWith("//ldml/identity/")) {
      return true;
    } else {
      // TODO(ryanmentley): THIS IS A HACK. (see ticket #4088)
      // REMOVE THE FOLLOWING IF STATEMENT WHEN TICKET 1297 IS RESOLVED
      // http://unicode.org/cldr/trac/ticket/1297
      if (distinguishedPath.startsWith("//ldml/layout/orientation")) {
        return true;
      }
      return false;
    }
  }
  
  @Override
  protected String canonicalizeDPath(String distinguishedPath, CLDRFile file) {
    return ResolverUtils.canonicalXpath(distinguishedPath);
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
