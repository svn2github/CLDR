/*
 * Copyright (C) 2004-2011, Unicode, Inc., Google, Inc., and others.
 * For terms of use, see http://www.unicode.org/terms_of_use.html
 */
package org.unicode.cldr.tool.resolver.unittest;

import com.ibm.icu.dev.test.TestFmwk.TestGroup;

/**
 * Runs all the CLDR Resolver Tool tests
 * 
 * @author ryanmentley@google.com (Ryan Mentley)
 */
public class TestAll extends TestGroup {

  public TestAll() {
    super(new String[] {
                        "org.unicode.cldr.tool.resolver.unittest.FullResolutionTest",
                        "org.unicode.cldr.tool.resolver.unittest.NoCodeFallbackTest",
                        "org.unicode.cldr.tool.resolver.unittest.SimpleResolutionTest",
                        },
        "All tests for the CLDR Resolver Tool");
  }

  /**
   * Main method that runs all CLDR Resolver tests
   * @param args Command-line arguments
   */
  public static void main(String[] args) {
    new TestAll().run(args);
  }
}
