// Copyright 2011 Google Inc. All Rights Reserved.

package org.unicode.cldr.tool.resolver;

import java.util.List;
import java.util.Arrays;
import java.util.Locale;

/**
 * Types of CLDR resolution
 * 
 * @author ryanmentley@google.com (Ryan Mentley)
 */
public enum ResolutionType {
  SIMPLE, FULL, NO_CODE_FALLBACK;

  /* These are to allow multiple names for the same resolution types */

  /* Simple inheritance names */
  private static final String[] SIMPLE_INHERITANCE_ARR = {"p", "partial", "s", "simple"};
  /**
   * A list of resolve types that will result in the simple inheritance model
   */
  private static final List<String> SIMPLE_INHERITANCE = Arrays.asList(SIMPLE_INHERITANCE_ARR);

  /* Fully-resolved names */
  private static final String[] FULLY_RESOLVED_ARR = {"f", "full", "fully", "fully resolved",
      "fully-resolved"};
  /**
   * A list of resolve types that will result in the fully-resolved inheritance
   * model
   */
  private static final List<String> FULLY_RESOLVED = Arrays.asList(FULLY_RESOLVED_ARR);

  /* Fully-resolved inheritance model with code-fallback suppressed names */
  private static final String[] FULLY_RESOLVED_WITHOUT_CODE_FALLBACK_ARR = {"n", "nocodefallback",
      "nocode", "no-code", "no-code-fallback"};
  /**
   * A list of resolve types that will result in the fully-resolved inheritance
   * model with code-fallback suppressed
   */
  private static final List<String> FULLY_RESOLVED_WITHOUT_CODE_FALLBACK = Arrays
      .asList(FULLY_RESOLVED_WITHOUT_CODE_FALLBACK_ARR);

  /**
   * Gets a ResolutionType corresponding to a given string
   * 
   * @param str the string to resolve to a ResolutionType
   * @throws IllegalArgumentException if str does not correspond to any known
   *         resolution type
   * @return a ResolutionType
   */
  public static ResolutionType forString(String str) {
    str = str.toLowerCase(Locale.ENGLISH);
    if (SIMPLE_INHERITANCE.contains(str)) {
      return SIMPLE;
    } else if (FULLY_RESOLVED.contains(str)) {
      return FULL;
    } else if (FULLY_RESOLVED_WITHOUT_CODE_FALLBACK.contains(str)) {
      return NO_CODE_FALLBACK;
    } else {
      throw new IllegalArgumentException("\"" + str + " is not a known type of resolution.");
    }
  }
}
