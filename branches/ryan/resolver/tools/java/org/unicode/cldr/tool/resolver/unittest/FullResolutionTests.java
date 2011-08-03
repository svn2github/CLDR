/*
 * Copyright (C) 2004-2011, Unicode, Inc., Google, Inc., and others.
 * For terms of use, see http://www.unicode.org/terms_of_use.html
 */
package org.unicode.cldr.tool.resolver.unittest;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.unicode.cldr.tool.resolver.CldrResolver;
import org.unicode.cldr.tool.resolver.ResolutionType;
import org.unicode.cldr.util.CLDRFile;
import org.unicode.cldr.util.CLDRFile.Factory;
import org.unicode.cldr.util.CldrUtility;
import org.unicode.cldr.util.XMLFileReader.SimpleHandler;

import com.ibm.icu.dev.test.TestFmwk;

/**
 * Test the full resolution of CLDR files.
 * 
 * This will take a long time to run.
 * 
 * @author ryanmentley@google.com (Ryan Mentley)
 */
public class FullResolutionTests extends TestFmwk {
  private static final String LOCALES_TO_TEST = ".*";
  private static final ResolutionType RESOLUTION_TYPE = ResolutionType.FULL;

  public void TestFullResolution() {
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
    for (String locale : locales) {
      // Resolve with CLDR and with the tool
      CLDRFile cldrResolved = factory.make(locale, true);
      CLDRFile toolResolved = resolver.resolveLocale(locale, RESOLUTION_TYPE);

      // Create sets to hold the paths from CLDR and the tool
      Set<String> cldrPaths = new HashSet<String>();
      Set<String> toolPaths = new HashSet<String>();

      SimpleHandler handler = new TestHandler(cldrResolved, toolPaths);
      ResolverTestUtils.processToolResolvedFile(toolResolved, handler);

      // Check to make sure no paths from the CLDR-resolved version that aren't
      // aliases get left out
      for (Iterator<String> fileIter = cldrResolved.iterator("", CLDRFile.ldmlComparator); fileIter
          .hasNext();) {
        String distinguishedPath = fileIter.next();
        String fullPath = cldrResolved.getFullXPath(distinguishedPath);
        // Ignore aliases and the //ldml/identity/ elements
        if (!distinguishedPath.endsWith("/alias")
            && !distinguishedPath.startsWith("//ldml/identity/")) {
          String canonicalPath = ResolverTestUtils.canonicalXpath(fullPath);
          assertTrue("Path " + canonicalPath + " is present in CLDR resolved file for locale "
              + locale + " but not in tool resolved file.", toolPaths.contains(canonicalPath));
          // Add the path to the Set for the next batch of checks
          cldrPaths.add(canonicalPath);
        }
      }
      // Check to make sure that all paths from the tool-resolved version are
      // also in the CLDR-resolved version
      for (String fullPath : toolPaths) {
        // Ignore the //ldml/identity/ elements
        if (!fullPath.startsWith("//ldml/identity/")) {
          assertTrue("Path " + fullPath + " is present in tool resolved file for locale " + locale
              + " but not in CLDR resolved file.", cldrPaths.contains(fullPath));
        }
      }
    }
  }

  private class TestHandler extends SimpleHandler {
    CLDRFile file;
    Set<String> paths;

    /**
     * Creates a test handler
     * 
     * @param cldrResolvedFile a CLDRFile to check the XML against
     * @param pathSet a set into which to insert all discovered XPaths
     */
    public TestHandler(CLDRFile cldrResolvedFile, Set<String> pathSet) {
      this.file = cldrResolvedFile;
      paths = pathSet;
    }

    @Override
    public void handlePathValue(String path, String value) {
      paths.add(ResolverTestUtils.canonicalXpath(path));
      assertEquals("CLDRFile resolved value for " + path + " in locale " + file.getLocaleID()
          + " should match tool resolved value", file.getStringValue(path), value);
    }
  }
}
