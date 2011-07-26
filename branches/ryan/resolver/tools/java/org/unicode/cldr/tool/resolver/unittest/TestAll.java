// Copyright 2011 Google Inc. All Rights Reserved.

package org.unicode.cldr.tool.resolver.unittest;

import com.ibm.icu.dev.test.TestFmwk.TestGroup;

/**
 * @author ryanmentley@google.com (Ryan Mentley)
 * 
 */
public class TestAll extends TestGroup {

  public TestAll() {
    super(new String[] {
                        "org.unicode.cldr.tool.resolver.unittest.UnitTests",
                        "org.unicode.cldr.tool.resolver.unittest.SimpleResolutionTests",
                        "org.unicode.cldr.tool.resolver.unittest.FullResolutionTests",
                        "org.unicode.cldr.tool.resolver.unittest.NoCodeFallbackTests",
                        },
        "All tests for the CLDR Resolver Tool");
  }

  /**
   * @param args Command-line arguments
   */
  public static void main(String[] args) {
    // TODO(ryanmentley): Auto-generated method stub
    new TestAll().run(args);
  }

}
