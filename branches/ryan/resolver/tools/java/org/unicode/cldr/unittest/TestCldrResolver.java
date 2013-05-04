/*
 * Copyright (C) 2004-2011, Unicode, Inc., Google, Inc., and others.
 * For terms of use, see http://www.unicode.org/terms_of_use.html
 */
package org.unicode.cldr.unittest;

import java.util.HashSet;
import java.util.Set;

import org.unicode.cldr.tool.resolver.CldrResolver;
import org.unicode.cldr.tool.resolver.ResolutionType;
import org.unicode.cldr.tool.resolver.ResolverUtils;
import org.unicode.cldr.util.CLDRFile;
import org.unicode.cldr.util.CldrUtility;
import org.unicode.cldr.util.Factory;
import org.unicode.cldr.util.LocaleIDParser;

import com.ibm.icu.dev.test.TestFmwk;

/**
 * Runs all the CLDR Resolver Tool tests
 * 
 * @author jchye@google.com (Jennifer Chye), ryanmentley@google.com (Ryan Mentley)
 */
public class TestCldrResolver extends TestFmwk {  
  private static final String LOCALES_TO_TEST = ".*";

  public void TestSimpleResolution() {
      try {
      new ResolverTest(ResolutionType.SIMPLE) {
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
                  if (!distinguishedPath.startsWith("//ldml/identity/")) {
                    // Ignore the //ldml/identity/ elements
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
                return resolvedChild;
              }
          }
      }.testResolution();
      } catch (Exception e) {
          e.printStackTrace();
      }
  }

  public void TestFullResolution() {
      new ResolverTest(ResolutionType.FULL).testResolution();
  }
  
  public void TestNoCodeFallback() {
      new ResolverTest(ResolutionType.NO_CODE_FALLBACK) {
          @Override
          protected boolean shouldIgnorePath(String distinguishedPath, CLDRFile file) {
            return super.shouldIgnorePath(distinguishedPath, file)
                || file.getSourceLocaleID(distinguishedPath, null).equals(
                  CldrResolver.CODE_FALLBACK);
          }
      }.testResolution();
  }

  /**
   * Main method that runs all CLDR Resolver tests
   * @param args Command-line arguments
   */
  public static void main(String[] args) {
    new TestCldrResolver().run(args);
  }

  /**
   * A superclass for all Resolver tests to encompass common functionality
   * 
   * @author ryanmentley@google.com (Ryan Mentley)
   */
  private class ResolverTest {
    /**
     * Gets the fully-resolved data for the locale.
     * 
     * @param locale the locale for which to get the map
     * @return an immutable Map from distinguished path to string value
     */
    protected CLDRFile loadToolDataFromResolver(String locale) {
        // Resolve with the tool
        return resolver.resolveLocale(locale);
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

    private CldrResolver resolver;

    public ResolverTest(ResolutionType resolutionType) {
        resolver = new CldrResolver(CldrUtility.MAIN_DIRECTORY, resolutionType);
    }

    /**
     * Template method to test any type of CLDR resolution
     */
    public final void testResolution() {
      Factory factory = resolver.getFactory();
      Set<String> locales = resolver.getLocaleNames(LOCALES_TO_TEST);

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
}
