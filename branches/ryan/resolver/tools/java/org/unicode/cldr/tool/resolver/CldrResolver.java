/*
 * Copyright (C) 2004-2011, Unicode, Inc., Google, Inc., and others.
 * For terms of use, see http://www.unicode.org/terms_of_use.html
 */
package org.unicode.cldr.tool.resolver;

import com.ibm.icu.dev.tool.UOption;

import org.unicode.cldr.util.CLDRFile;
import org.unicode.cldr.util.CldrUtility;
import org.unicode.cldr.util.CLDRFile.DraftStatus;
import org.unicode.cldr.util.Factory;
import org.unicode.cldr.util.LocaleIDParser;
import org.unicode.cldr.util.SimpleXMLSource;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

/**
 * Class designed for the resolution of CLDR XML Files (e.g., removing aliases
 * but leaving the inheritance structure intact).
 * 
 * Instances of this class are not thread-safe. Any attempts to use an object of
 * this class in multiple threads must be externally synchronized.
 * 
 * @author ryanmentley@google.com (Ryan Mentley)
 * 
 */
public class CldrResolver {
  /**
   * The value that denotes a non-existent value in the child that exists in the
   * truncation parent
   */
  public static final String UNDEFINED = "\uFFFDNO_VALUE\uFFFD";

  /**
   * The name of the code-fallback locale
   */
  public static final String CODE_FALLBACK = "code-fallback";

  /**
   * The name of the root locale
   */
  public static final String ROOT = "root";

  /* The command-line UOptions, along with a storage container. */
  private static final UOption LOCALE = UOption.create("locale", 'l', UOption.REQUIRES_ARG);
  private static final UOption DESTDIR = UOption.DESTDIR();
  private static final UOption SOURCEDIR = UOption.SOURCEDIR();
  private static final UOption RESOLUTION_TYPE = UOption.create("resolutiontype", 'r',
      UOption.REQUIRES_ARG);
  private static final UOption DRAFT_STATUS = UOption.create("mindraftstatus", 'm',
      UOption.REQUIRES_ARG);
  private static final UOption VERBOSITY = UOption.create("verbosity", 'v', UOption.REQUIRES_ARG);
  private static final UOption[] OPTIONS = {LOCALE, DESTDIR, SOURCEDIR, RESOLUTION_TYPE,
      DRAFT_STATUS, VERBOSITY};

  /* Private instance variables */
  private Factory cldrFactory;

  /**
   * Caches the canonical version of the paths returned by root to work around
   * CLDR bugs
   */
  private Set<String> rootPaths = null;

  public static void main(String[] args) {
    UOption.parseArgs(args, OPTIONS);

    // Defaults
    ResolutionType resolutionType = ResolutionType.SIMPLE;
    String localeRegex = ".*";
    String srcDir = CldrUtility.MAIN_DIRECTORY;
    File dest = new File(CldrUtility.GEN_DIRECTORY, "resolver");
    if (!dest.exists()) {
        dest.mkdir();
    }
    String destDir = dest.getAbsolutePath();
    
    // Parse the options
    if (RESOLUTION_TYPE.doesOccur) {
      try {
        resolutionType = ResolutionType.forString(RESOLUTION_TYPE.value);
      } catch (IllegalArgumentException e) {
        ResolverUtils.debugPrintln("Warning: " + e.getMessage(), 1);
        ResolverUtils.debugPrintln("Using default resolution type " + resolutionType.toString(), 1);
      }
    }

    if (LOCALE.doesOccur) {
      localeRegex = LOCALE.value;
    }

    if (SOURCEDIR.doesOccur) {
      srcDir = SOURCEDIR.value;
    }

    if (DESTDIR.doesOccur) {
      destDir = DESTDIR.value;
    }

    if (VERBOSITY.doesOccur) {
      int verbosityParsed;
      try {
        verbosityParsed = Integer.parseInt(VERBOSITY.value);
      } catch (NumberFormatException e) {
        ResolverUtils.debugPrintln("Warning: Error parsing verbosity value \"" + VERBOSITY.value
            + "\".  Using default value " + ResolverUtils.verbosity, 1);
        verbosityParsed = ResolverUtils.verbosity;
      }

      if (verbosityParsed < 0 || verbosityParsed > 5) {
        ResolverUtils.debugPrintln(
            "Warning: Verbosity must be between 0 and 5, inclusive.  Using default value "
                + ResolverUtils.verbosity, 1);
      } else {
        ResolverUtils.verbosity = verbosityParsed;
      }
    }

    if (srcDir == null) {
      ResolverUtils.debugPrintln(
          "Error: a source (CLDR common/main) directory must be specified via either"
              + " the -s command-line option or by the CLDR_DIR environment variable.", 1);
      System.exit(1);
    }

    CldrResolver resolver = null;
    if (DRAFT_STATUS.doesOccur) {
      DraftStatus minDraftStatus = ResolverUtils.draftStatusFromString(DRAFT_STATUS.value);
      if (minDraftStatus == null) {
        ResolverUtils.debugPrintln("Warning: " + DRAFT_STATUS.value
            + " is not a recognized draft status.", 1);
        ResolverUtils.debugPrint("Recognized draft statuses:", 1);
        for (DraftStatus status : DraftStatus.values()) {
          ResolverUtils.debugPrint(" " + status.toString(), 1);
        }
        ResolverUtils.debugPrintln("", 1);
        // This default is defined in the internals of CLDRFile, so we don't
        // output it here
        ResolverUtils.debugPrintln("Using default draft status", 1);
      } else {
        ResolverUtils.debugPrintln("\nMinimum draft status: " + minDraftStatus.toString(), 2);
        resolver = new CldrResolver(srcDir, minDraftStatus);
      }
    } else {
      ResolverUtils.debugPrintln("\nMinimum draft status: default", 2);
    }

    if (resolver == null) {
      resolver = new CldrResolver(srcDir);
    }

    // Print out the options other than draft status (which has already been
    // printed)
    ResolverUtils.debugPrintln("Locale regular expression: \"" + localeRegex + "\"", 2);
    ResolverUtils.debugPrintln("Source (CLDR common/main) directory: \"" + srcDir + "\"", 2);
    ResolverUtils.debugPrintln("Destination (resolved output) directory: \"" + destDir + "\"", 2);
    ResolverUtils.debugPrintln("Resolution type: " + resolutionType.toString(), 2);
    ResolverUtils.debugPrintln("Verbosity: " + ResolverUtils.verbosity, 2);

    // Perform the resolution
    resolver.resolve(localeRegex, destDir, resolutionType);
    ResolverUtils.debugPrintln("Execution complete.", 3);
  }

  /**
   * Constructs a CLDR partial resolver given the path to a directory of XML
   * files.
   * 
   * @param cldrDirectory the path to the common/main folder from the standard
   *        CLDR distribution. Note: this still requires the CLDR_DIR
   *        environment variable to be set.
   */
  public CldrResolver(String cldrDirectory) {
    ResolverUtils.debugPrintln("Making factory...", 3);
    /*
     * We don't do the regex filter here so that we can still resolve parent
     * files that don't match the regex
     */
    cldrFactory = Factory.make(cldrDirectory, ".*");
    ResolverUtils.debugPrintln("Factory made.\n", 3);
  }

  public CldrResolver(String cldrDirectory, DraftStatus minimumDraftStatus) {
    ResolverUtils.debugPrintln(
        "Making factory with minimum draft status " + minimumDraftStatus.toString() + "...", 3);
    /*
     * We don't do the regex filter here so that we can still resolve parent
     * files that don't match the regex
     */
    cldrFactory = Factory.make(cldrDirectory, ".*", minimumDraftStatus);
    ResolverUtils.debugPrintln("Factory made.\n", 3);
  }

  /**
   * Resolves all locales that match the given regular expression and outputs
   * their XML files to the given directory.
   * 
   * @param localeRegex a regular expression that will be matched against the
   *        names of locales
   * @param outputDir the directory to which to output the partially-resolved
   *        XML files
   * @param resolutionType the type of resolution to perform
   * @throws IllegalArgumentException if outputDir is not a directory
   */
  public void resolve(String localeRegex, File outputDir, ResolutionType resolutionType) {
    if (!outputDir.isDirectory()) {
      throw new IllegalArgumentException(outputDir.getPath() + " is not a directory");
    }

    // Iterate through all the locales
    for (String locale : getLocaleNames(localeRegex)) {
      // Resolve the file
      CLDRFile resolved = resolveLocale(locale, resolutionType);

      // Output the file to disk
      printToFile(resolved, outputDir);
    }
  }

  /**
   * Returns the locale names from the resolver that match a given regular
   * expression.
   * 
   * @param localeRegex a regular expression to match against
   * @return all of the locales that will be resolved by a call to resolve()
   *         with the same localeRegex
   */
  public Set<String> getLocaleNames(String localeRegex) {
    ResolverUtils.debugPrint("Getting list of locales...", 3);
    Set<String> allLocales = cldrFactory.getAvailable();
    Set<String> locales = new TreeSet<String>();
    // Iterate through all the locales
    for (String locale : allLocales) {
      // Check if the locale name matches the regex
      if (locale.matches(localeRegex)) {
        locales.add(locale);
      } else {
        ResolverUtils.debugPrintln("Locale " + locale
            + "does not match the pattern.  Skipping...\n", 4);
      }

    }
    ResolverUtils.debugPrintln("done.\n", 3);
    return locales;
  }

  /**
   * Accessor method for the CLDR factory.  Used for testing.
   * 
   * @return the {@link org.unicode.cldr.util.CLDRFile.Factory} used to resolve the CLDR data 
   */
  public Factory getFactory() {
    return cldrFactory;
  }

  /**
   * Resolves a locale to a {@link CLDRFile} object
   * 
   * @param locale the name of the locale to resolve
   * @param resolutionType the type of resolution to perform
   * @return a {@link CLDRFile} containing the resolved data
   */
  public CLDRFile resolveLocale(String locale, ResolutionType resolutionType) {
    ResolverUtils.debugPrintln("Processing locale " + locale + "...", 2);

    // Create CLDRFile for current (base) locale
    ResolverUtils.debugPrintln("Making base file...", 3);
    CLDRFile base = cldrFactory.make(locale, true);

    CLDRFile resolved;

    // root, having no parent, is a special case, which just gets its aliases
    // removed and then gets printed directly.
    if (locale.equals(ROOT)) {
      // Remove aliases from root.
      resolved = resolveRootLocale(base, resolutionType);
    } else {
      resolved = resolveNonRootLocale(base, resolutionType);
    }

    return resolved;
  }

  /**
   * Resolves a locale other than root to a {@link CLDRFile}
   * 
   * @param file the file to retrieve the data from
   * @param resolutionType the type of resolution to perform
   * @return a {@link CLDRFile} containing the resolved data
   */
  private CLDRFile resolveNonRootLocale(CLDRFile file, ResolutionType resolutionType) {
    String locale = file.getLocaleID();
    String parentLocale = null;
    CLDRFile truncationParent = null;
    if (resolutionType == ResolutionType.SIMPLE) {
      // Make parent file
      ResolverUtils.debugPrintln("Making parent file by truncation...", 3);
      parentLocale = LocaleIDParser.getParent(locale);
      truncationParent = cldrFactory.make(parentLocale, true);
    }

    // Create empty file to hold (partially or fully) resolved data
    ResolverUtils.debugPrint("Creating empty CLDR file to store resolved data...", 3);
    // False/unresolved because it needs to be mutable.
    CLDRFile resolved = new CLDRFile(new SimpleXMLSource(locale));
    ResolverUtils.debugPrintln("done.", 3);

    if (resolutionType == ResolutionType.SIMPLE) {
      ResolverUtils.debugPrintln("Filtering against parent " + parentLocale + "...", 2);
    } else {
      ResolverUtils.debugPrintln(
          "Removing aliases"
              + (resolutionType == ResolutionType.NO_CODE_FALLBACK ? " and code-fallback" : "")
              + "...", 2);
    }

    // Go through the XPaths, filter out appropriate values based on the
    // inheritance model,
    // then copy to the new CLDRFile.
    Set<String> basePaths = ResolverUtils.getAllPaths(file);
    String fullPath = null;
    for (String distinguishedPath : basePaths) {
      ResolverUtils.debugPrintln("Distinguished path: " + distinguishedPath, 5);

      if (resolutionType == ResolutionType.FULL
          || resolutionType == ResolutionType.NO_CODE_FALLBACK) {
        fullPath = file.getFullXPath(distinguishedPath);
        ResolverUtils.debugPrintln("Full path: " + fullPath, 5);
      }

      if (distinguishedPath.endsWith("/alias")) {
        // Ignore any aliases.
        ResolverUtils.debugPrintln("This path is an alias.  Dropping...", 5);
        continue;
      }

      String parentValue = null;
      if (resolutionType == ResolutionType.SIMPLE) {
        parentValue = getValueIfInPathSet(truncationParent, distinguishedPath);
        ResolverUtils.debugPrintln(
            "    Parent [" + parentLocale + "] value : " + ResolverUtils.strRep(parentValue), 5);
      }

      String baseValue = file.getStringValue(distinguishedPath);
      ResolverUtils.debugPrintln(
          "    Base [" + locale + "] value: " + ResolverUtils.strRep(baseValue), 5);
      if (baseValue == null && parentValue != null) {
        // This catches (and ignores) weirdness caused by aliases in older
        // versions of CLDR.
        // This shouldn't happen in the new version.
        ResolverUtils.debugPrintln(
            "Non-inherited null detected in base locale.  If you are using a version"
                + " of CLDR 2.0.0 or newer, this is cause for concern.", 1);
        continue;
      }

      /*
       * If we're fully resolving the locale (and, if code-fallback suppression
       * is enabled, if the value is not from code-fallback) or the values
       * aren't equal, add it to the resolved file.
       */
      if (resolutionType == ResolutionType.FULL
          || (resolutionType == ResolutionType.NO_CODE_FALLBACK && !file.getSourceLocaleID(
              distinguishedPath, null).equals(CODE_FALLBACK))
          || (resolutionType == ResolutionType.SIMPLE && !areEqual(parentValue, baseValue))) {
        ResolverUtils.debugPrintln("  Adding to resolved file.", 5);
        // Suppress non-distinguishing attributes in simple inheritance
        resolved.add((resolutionType == ResolutionType.SIMPLE ? distinguishedPath : fullPath),
            baseValue);
      }
    }

    // The undefined value is only needed for the simple inheritance resolution
    if (resolutionType == ResolutionType.SIMPLE) {
      // Add undefined values for anything in the parent but not the child
      ResolverUtils.debugPrintln(
          "Adding UNDEFINED values based on " + truncationParent.getLocaleID(), 3);
      for (String distinguishedPath : ResolverUtils.getAllPaths(truncationParent)) {
        // Do the comparison with distinguished paths to prevent errors
        // resulting from duplicate full paths but the same distinguished path
        if (!basePaths.contains(distinguishedPath)) {
          resolved.add(distinguishedPath, UNDEFINED);
        }
      }
    }
    return resolved;
  }

  /**
   * Resolves (using the simple inheritance model) all locales that match the
   * given regular expression and outputs their XML files to the given
   * directory.
   * 
   * @param localeRegex a regular expression that will be matched against the
   *        names of locales
   * @param outputDir the directory to which to output the partially-resolved
   *        XML files
   * @throws IllegalArgumentException if outputDir is not a directory
   */
  public void resolve(String localeRegex, String outputDir) {
    resolve(localeRegex, outputDir, ResolutionType.SIMPLE);
  }

  /**
   * Resolves all locales that match the given regular expression and outputs
   * their XML files to the given directory.
   * 
   * @param localeRegex a regular expression that will be matched against the
   *        names of locales
   * @param outputDir the directory to which to output the partially-resolved
   *        XML files
   * @param resolutionType the type of resolution to perform
   * @throws IllegalArgumentException if outputDir is not a directory
   */
  public void resolve(String localeRegex, String outputDir, ResolutionType resolutionType) {
    resolve(localeRegex, new File(outputDir), resolutionType);
  }

  /**
   * Resolves (using the simple inheritance model) all locales that match the
   * given regular expression and outputs their XML files to the given
   * directory.
   * 
   * @param localeRegex a regular expression that will be matched against the
   *        names of locales
   * @param outputDir the directory to which to output the partially-resolved
   *        XML files
   * @throws IllegalArgumentException if outputDir is not a directory
   */
  public void resolve(String localeRegex, File outputDir) {
    resolve(localeRegex, outputDir, ResolutionType.SIMPLE);
  }

  private String getValueIfInPathSet(CLDRFile file, String distinguishedPath) {
    if (file.getLocaleID().equals(ROOT)) {
      if (rootPaths == null) {
        rootPaths = new HashSet<String>();
        for (String path : ResolverUtils.getAllPaths(file)) {
          rootPaths.add(ResolverUtils.canonicalXpath(path));
        }
      }
      if (rootPaths.contains(distinguishedPath)) {
        return file.getStringValue(distinguishedPath);
      } else {
        return null;
      }
    } else {
      return file.getStringValue(distinguishedPath);
    }
  }

  /**
   * Writes out the given CLDRFile in XML form to the given directory
   * 
   * @param cldrFile the CLDRFile to print to XML
   * @param directory the directory to which to add the file
   */
  private static void printToFile(CLDRFile cldrFile, File directory) {
    ResolverUtils.debugPrint("Printing file...", 2);
    try {
      PrintWriter pw =
          new PrintWriter(new File(directory, cldrFile.getLocaleID() + ".xml"), "UTF-8");
      cldrFile.write(pw);
      pw.close();
      ResolverUtils.debugPrintln("done.\n", 2);
    } catch (FileNotFoundException e) {
      ResolverUtils.debugPrintln("\nFile not found: " + e.getMessage(), 1);
      System.exit(1);
      return;
    } catch (UnsupportedEncodingException e) {
      // This should never ever happen.
      ResolverUtils.debugPrintln("Your system does not support UTF-8 encoding: " + e.getMessage(),
          1);
      System.exit(1);
      return;
    }
  }

  /**
   * Creates a copy of a CLDRFile with the aliases removed. This is really only
   * used for root, as it's a lot more efficient to remove aliases as we iterate
   * through for other locales.
   * 
   * @param cldrFile the CLDRFile whose aliases to remove
   * @param resolutionType the type of resolution to use when processing the
   *        file
   * @return a copy of cldrFile with aliases removed
   */
  private static CLDRFile resolveRootLocale(CLDRFile cldrFile, ResolutionType resolutionType) {
    // False/unresolved because it needs to be mutable
    CLDRFile partiallyResolved =
        new CLDRFile(new SimpleXMLSource(cldrFile.getLocaleID()));
    ResolverUtils.debugPrintln("Removing aliases"
        + (resolutionType == ResolutionType.NO_CODE_FALLBACK ? " and code-fallback" : "") + "...",
        2);
    // Go through the XPaths, filter out aliases, then copy to the new CLDRFile
    for (String distinguishedPath : ResolverUtils.getAllPaths(cldrFile)) {
      ResolverUtils.debugPrintln("Path: " + distinguishedPath, 5);
      if (distinguishedPath.endsWith("/alias")) {
        ResolverUtils.debugPrintln("  This path is an alias.  Dropping...", 5);
        continue;
      } else if (resolutionType == ResolutionType.NO_CODE_FALLBACK
          && cldrFile.getSourceLocaleID(distinguishedPath, null).equals(CODE_FALLBACK)) {
        ResolverUtils.debugPrintln("  This path is in code-fallback.  Dropping...", 5);
        continue;
      } else {
        String value = cldrFile.getStringValue(distinguishedPath);
        if (resolutionType == ResolutionType.SIMPLE) {
          // Distinguished attributes only for simple inheritance model
          partiallyResolved.add(distinguishedPath, value);
        } else {
          // Full attributes for everything else
          String fullPath = cldrFile.getFullXPath(distinguishedPath);
          partiallyResolved.add(fullPath, value);
        }
      }
    }
    return partiallyResolved;
  }

  /**
   * Convenience method to compare objects that works with nulls
   * 
   * @param o1 the first object
   * @param o2 the second object
   * @return true if objects o1 == o2 or o1.equals(o2); false otherwise
   */
  private static boolean areEqual(Object o1, Object o2) {
    if (o1 == o2) {
      return true;
    } else if (o1 == null) {
      return false;
    } else {
      return o1.equals(o2);
    }
  }

}
