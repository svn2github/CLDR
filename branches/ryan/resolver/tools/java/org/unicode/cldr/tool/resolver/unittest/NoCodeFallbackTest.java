/*
 * Copyright (C) 2004-2011, Unicode, Inc., Google, Inc., and others.
 * For terms of use, see http://www.unicode.org/terms_of_use.html
 */

package org.unicode.cldr.tool.resolver.unittest;

import org.unicode.cldr.tool.resolver.CldrResolver;
import org.unicode.cldr.tool.resolver.ResolutionType;
import org.unicode.cldr.tool.resolver.ResolverUtils;
import org.unicode.cldr.util.CLDRFile;

/**
 * Test the full resolution of CLDR files.
 * 
 * This will take a long time to run.
 * 
 * @author ryanmentley@google.com (Ryan Mentley)
 */
public class NoCodeFallbackTest extends ResolverTest {
  private static final String LOCALES_TO_TEST = ".*";
  private static final ResolutionType RESOLUTION_TYPE = ResolutionType.NO_CODE_FALLBACK;

  /**
   * This is needed because the testing framework does not detect inherited test
   * methods
   */
  public void TestNoCodeFallbackResolution() {
      try {
    TestResolution();
      } catch(Exception e) {
          e.printStackTrace();
      }
  }

  @Override
  protected boolean shouldIgnorePath(String distinguishedPath, CLDRFile file) {
    return super.shouldIgnorePath(distinguishedPath, file)
        || file.getSourceLocaleID(distinguishedPath, null).equals(
          CldrResolver.CODE_FALLBACK);
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
    return ResolverUtils.canonicalXpath(file.getFullXPath(distinguishedPath));
  }
}
