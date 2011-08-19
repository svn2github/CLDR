/*
 * Copyright (C) 2004-2011, Unicode, Inc., Google, Inc., and others.
 * For terms of use, see http://www.unicode.org/terms_of_use.html
 */

package org.unicode.cldr.tool.resolver.unittest;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.unicode.cldr.tool.resolver.ResolverUtils;
import org.unicode.cldr.util.CLDRFile;
import org.unicode.cldr.util.XMLFileReader.SimpleHandler;

/**
 * Superclass containing elements common to both full and no-code-fallback
 * resolution
 * 
 * @author ryanmentley@google.com (Ryan Mentley)
 */
public abstract class NonSimpleResolutionTest extends ResolverTest {

  /**
   * Caches the fully-resolved data retrieved from the tool output. Keyed
   * locale, then full XPath in the canonical form retrieved by
   * {@link ResolverUtils#canonicalXpath(String)}
   */
  private Map<String, Map<String, String>> fullyResolvedFromTool =
      new HashMap<String, Map<String, String>>();

  @Override
  protected Map<String, String> getFullyResolvedToolData(String locale) {
    return Collections.unmodifiableMap(fullyResolvedFromTool.get(locale));
  }

  @Override
  protected SimpleHandler makeHandler(String locale) {
    return new TestHandler(locale);
  }

  @Override
  protected String canonicalizeDPath(String distinguishedPath, CLDRFile file) {
    return ResolverUtils.canonicalXpath(file.getFullXPath(distinguishedPath));
  }

  private class TestHandler extends SimpleHandler {
    private String locale;

    /**
     * Creates a test handler
     * 
     * @param locale the locale being handled
     */
    public TestHandler(String locale) {
      this.locale = locale;
    }

    @Override
    public void handlePathValue(String path, String value) {
      String canonicalPath = ResolverUtils.canonicalXpath(path);
      // Populate the unresolved map
      if (!fullyResolvedFromTool.containsKey(locale)) {
        fullyResolvedFromTool.put(locale, new HashMap<String, String>());
      }
      assertFalse("Duplicate paths should never occur", fullyResolvedFromTool.get(locale)
          .containsKey(canonicalPath));
      fullyResolvedFromTool.get(locale).put(canonicalPath, value);
    }
  }
}
