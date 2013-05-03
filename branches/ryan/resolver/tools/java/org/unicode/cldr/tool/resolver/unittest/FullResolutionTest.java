/*
 * Copyright (C) 2004-2011, Unicode, Inc., Google, Inc., and others.
 * For terms of use, see http://www.unicode.org/terms_of_use.html
 */

package org.unicode.cldr.tool.resolver.unittest;

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
public class FullResolutionTest extends ResolverTest {
  private static final String LOCALES_TO_TEST = ".*";
  private static final ResolutionType RESOLUTION_TYPE = ResolutionType.FULL;

  /**
   * This is needed because the testing framework does not detect inherited test
   * methods
   */
  public void TestFullResolution() {
    TestResolution();
  }

  @Override
  protected String getLocalesToTest() {
    return LOCALES_TO_TEST;
  }

  @Override
  protected ResolutionType getResolutionType() {
    return RESOLUTION_TYPE;
  }
}
