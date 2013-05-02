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
  private static final String LOCALES_TO_TEST = "bn.*";
  private static final ResolutionType RESOLUTION_TYPE = ResolutionType.SIMPLE;

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
  protected Map<String, String> loadToolDataFromResolver(String locale) {
      String parent = LocaleIDParser.getParent(locale);
      System.out.println(locale + " child of " + parent);
      if (parent == null) {
        // locale is root, just grab it straight out of the unresolved data
        return super.loadToolDataFromResolver(locale);
      } else {
        Map<String, String> resolvedParentMap = loadToolDataFromResolver(parent);
        Map<String, String> resolvedChildMap = new HashMap<String, String>(resolvedParentMap);
        Map<String, String> unresolvedChildMap = super.loadToolDataFromResolver(locale);
        for (String distinguishedPath : unresolvedChildMap.keySet()) {

          String childValue = unresolvedChildMap.get(distinguishedPath);
          if (distinguishedPath.contains("HNL")) {
              System.out.println(locale + " " + distinguishedPath + " --> " + childValue);
          }
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
        return Collections.unmodifiableMap(resolvedChildMap);
      }
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
  protected String canonicalizeDPath(String distinguishedPath, CLDRFile file) {
    return ResolverUtils.canonicalXpath(distinguishedPath);
  }
}
