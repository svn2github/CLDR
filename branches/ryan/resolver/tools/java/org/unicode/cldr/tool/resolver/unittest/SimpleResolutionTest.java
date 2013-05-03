/*
 * Copyright (C) 2004-2011, Unicode, Inc., Google, Inc., and others.
 * For terms of use, see http://www.unicode.org/terms_of_use.html
 */

package org.unicode.cldr.tool.resolver.unittest;

import org.unicode.cldr.tool.resolver.CldrResolver;
import org.unicode.cldr.tool.resolver.ResolutionType;
import org.unicode.cldr.tool.resolver.ResolverUtils;
import org.unicode.cldr.util.CLDRFile;
import org.unicode.cldr.util.LocaleIDParser;

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
   * This is needed because the testing framework does not detect inherited test
   * methods
   */
  public void TestSimpleResolution() {
      try {
          TestResolution();          
      } catch (Exception e) {
          e.printStackTrace();
      }
  }

  /*
   * (non-Javadoc)
   * 
   * @see
   * org.unicode.cldr.tool.resolver.unittest.ResolverTest#getFullyResolvedToolData
   * (java.lang.String)
   */
  @Override
  protected CLDRFile loadToolDataFromResolver(String locale) {
      String parent = LocaleIDParser.getParent(locale);
      if (parent == null) {
        // locale is root, just grab it straight out of the unresolved data
        return super.loadToolDataFromResolver(locale);
      } else {
        CLDRFile resolvedParent = loadToolDataFromResolver(parent);
        CLDRFile resolvedChild = resolvedParent.cloneAsThawed();
        CLDRFile unresolvedChild = super.loadToolDataFromResolver(locale);
        for (String distinguishedPath : unresolvedChild) {

          String childValue = unresolvedChild.getStringValue(distinguishedPath);
          if (childValue.equals(CldrResolver.UNDEFINED)) {
            assertNotNull("Child locale " + locale
                + " should not contain UNDEFINED values unless the truncation parent (" + parent
                + " has a " + "value at the given path '" + distinguishedPath + "'.",
                resolvedParent.getStringValue(distinguishedPath));
            // Delete undefined values from the child Map
            resolvedChild.remove(distinguishedPath);
          } else {
            // Ignore the //ldml/identity/ elements
            if (!distinguishedPath.startsWith("//ldml/identity/")) {
              String parentValue = resolvedParent.getStringValue(distinguishedPath);
              assertNotEquals(
                  "Child ("
                      + locale
                      + ") should not contain values that are the same in the truncation parent locale ("
                      + parent + ") at path '" + distinguishedPath + "'.",
                  childValue, parentValue);
            }
            // Overwrite the parent value
            resolvedChild.add(distinguishedPath, childValue);
          }
        }
        return resolvedChild;
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
