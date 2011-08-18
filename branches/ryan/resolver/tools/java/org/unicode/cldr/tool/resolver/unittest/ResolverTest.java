/*
 * Copyright (C) 2004-2011, Unicode, Inc., Google, Inc., and others.
 * For terms of use, see http://www.unicode.org/terms_of_use.html
 */

package org.unicode.cldr.tool.resolver.unittest;

import java.util.Map;

import org.unicode.cldr.util.CLDRFile;

import com.ibm.icu.dev.test.TestFmwk;

/**
 * A superclass for all Resolver tests to encompass common functionality
 * 
 * @author ryanmentley@google.com (Ryan Mentley)
 */
public abstract class ResolverTest extends TestFmwk {
  /**
   * Gets the fully-resolved data for the locale
   * 
   * @param locale the locale for which to get the map
   * @return an immutable Map from distinguished path to string value
   */
  protected abstract Map<String, String> getFullyResolvedToolData(String locale);

  /**
   * Determines whether an XPath should be ignored for testing purposes
   * 
   * @param distinguishedPath a distinguished XPath
   * @param file the CLDRFile from which the path was retrieved
   * @return {@code true} if the path should be ignored; {@code false} otherwise
   */
  protected abstract boolean shouldIgnorePath(String distinguishedPath, CLDRFile file);
}
