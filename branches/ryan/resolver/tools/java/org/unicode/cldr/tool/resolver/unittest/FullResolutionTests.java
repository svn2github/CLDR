// Copyright 2011 Google Inc. All Rights Reserved.

package org.unicode.cldr.tool.resolver.unittest;

import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Set;

import org.unicode.cldr.tool.resolver.CldrResolver;
import org.unicode.cldr.tool.resolver.ResolutionType;
import org.unicode.cldr.unittest.TestAll.TestInfo;
import org.unicode.cldr.util.CLDRFile;
import org.unicode.cldr.util.CLDRFile.Factory;
import org.unicode.cldr.util.CldrUtility;
import org.unicode.cldr.util.XMLFileReader;
import org.unicode.cldr.util.XMLFileReader.SimpleHandler;

import com.ibm.icu.dev.test.TestFmwk;

/**
 * Test the full resolution of CLDR files.
 * 
 * This will take a long time to run (on the order of half an hour).
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
    // If this is ever made multi-threaded, we should just make our own factories. 
    Factory factory = TestInfo.getInstance().getCldrFactory();
    Set<String> locales = resolver.getLocaleNames(LOCALES_TO_TEST);
    for (String locale : locales) {
      CLDRFile cldrResolved = factory.make(locale, true);
      CLDRFile toolResolved = resolver.resolveLocale(locale, RESOLUTION_TYPE);
      StringWriter stringOut = new StringWriter();
      PrintWriter pw = new PrintWriter(stringOut);
      toolResolved.write(pw);
      String xml = stringOut.toString();
      StringReader sr = new StringReader(xml);
      XMLFileReader xmlReader = new XMLFileReader();
      xmlReader.setHandler(new TestHandler(cldrResolved) );
      xmlReader
          .read(locale, sr, XMLFileReader.CONTENT_HANDLER | XMLFileReader.ERROR_HANDLER, false);
    }
  }
  
  private class TestHandler extends SimpleHandler {
    CLDRFile file;
    
    public TestHandler(CLDRFile cldrResolvedFile) {
      this.file = cldrResolvedFile;
    }
    
    @Override
    public void handlePathValue(String path, String value) {
      //System.out.println("Path: \"" + path + "\" Value: \"" + value + "\"");
      assertEquals("CLDRFile resolved value for " + path + "should match tool resolved value",
          file.getStringValue(path), value);
    }
  }
}

