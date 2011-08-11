/*
 * Copyright (C) 2004-2011, Unicode, Inc., Google, Inc., and others.
 * For terms of use, see http://www.unicode.org/terms_of_use.html
 */
package org.unicode.cldr.tool.resolver;

import com.ibm.icu.dev.tool.UOption;

import org.unicode.cldr.util.CLDRFile;
import org.unicode.cldr.util.CLDRFile.DraftStatus;
import org.unicode.cldr.util.CLDRFile.Factory;
import org.unicode.cldr.util.LanguageTagParser;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
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
   * Output level from 0-5. 0 is nothing, 1 is errors, 2-3 is pretty sane, 5
   * will flood your terminal.
   */
  private static final int VERBOSITY = 2;

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
  private static final UOption[] options = {LOCALE, DESTDIR, SOURCEDIR, RESOLUTION_TYPE,
      DRAFT_STATUS};

  /**
   * This list is not used for anything practical, just for test cases for weird
   * ones.
   */
  private static final String[] weirdCasesArray = { // Parent is root
      "az_Cyrl", "ha_Arab", "ku_Latn", "mn_Mong", "pa_Arab", "shi_Tfng", "sr_Latn", "uz_Arab",
          "uz_Latn", "vai_Latn", "zh_Hant",
          // Parent is es_419
          "es_AR", "es_BO", "es_CL", "es_CO", "es_CR", "es_DO", "es_EC", "es_GT", "es_HN", "es_MX",
          "es_NI", "es_PA", "es_PE", "es_PR", "es_PY", "es_SV", "es_US", "es_UY", "es_VE",
          // Parent is pt_PT
          "pt_AO", "pt_GW", "pt_MZ", "pt_ST",
          // Things I need
          "root", "az",
          // Non-weird cases
          "en", "en_AU", "de_DE", "de", "de_AT"};
  private static final java.util.List<String> weirdCases = Arrays.asList(weirdCasesArray);

  /* Private instance variables */
  private Factory cldrFactory;
  private Set<String> rootPaths = null;

  public static void main(String[] args) {
    debugPrintln("Working directory is: " + System.getProperty("user.dir") + "\n", 2);
    UOption.parseArgs(args, options);

    // Defaults
    ResolutionType resolutionType = ResolutionType.SIMPLE;
    String localeRegex = ".*";
    // TODO(ryanmentley): Make these not the defaults
    String srcDir = "/usr/local/google/users/ryanmentley/cldr/trunk/common/main";
    String destDir = "/usr/local/google/users/ryanmentley/cldrtest";

    // Parse the options
    if (RESOLUTION_TYPE.doesOccur) {
      try {
        resolutionType = ResolutionType.forString(RESOLUTION_TYPE.value);
      } catch (IllegalArgumentException e) {
        System.out.println("Warning: " + e.getMessage());
        System.out.println("Using default resolution type " + resolutionType.toString());
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

    CldrResolver resolver = null;
    if (DRAFT_STATUS.doesOccur) {
      DraftStatus minDraftStatus = draftStatusFromString(DRAFT_STATUS.value);
      if (minDraftStatus == null) {
        System.out.println("Warning: " + DRAFT_STATUS.value + " is not a recognized draft status.");
        System.out.print("Recognized draft statuses:");
        for (DraftStatus status : DraftStatus.values()) {
          System.out.print(" " + status.toString());
        }
        System.out.println();
        // This default is defined in the internals of CLDRFile, so we don't
        // output it here
        System.out.println("Using default draft status");
      } else {
        debugPrintln("\nMinimum draft status: " + minDraftStatus.toString(), 2);
        resolver = new CldrResolver(srcDir, minDraftStatus);
      }
    } else {
      debugPrintln("\nMinimum draft status: default", 2);
    }

    if (resolver == null) {
      resolver = new CldrResolver(srcDir);
    }

    // Print out the options other than draft status (which has already been
    // printed)
    debugPrintln("Locale regular expression: " + localeRegex + "\"", 2);
    debugPrintln("Source (CLDR common/main) directory: \"" + srcDir + "\"", 2);
    debugPrintln("Destination (resolved output) directory: \"" + destDir + "\"", 2);
    debugPrintln("Resolution type: " + resolutionType.toString(), 2);

    resolver.resolve(localeRegex, destDir, resolutionType);
    debugPrintln("Execution complete.", 3);
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
    debugPrintln("Making factory...", 3);
    /*
     * We don't do the regex filter here so that we can still resolve parent
     * files that don't match the regex
     */
    cldrFactory = Factory.make(cldrDirectory, ".*");
    debugPrintln("Factory made.\n", 3);
  }

  public CldrResolver(String cldrDirectory, DraftStatus minimumDraftStatus) {
    debugPrintln("Making factory with minimum draft status " + minimumDraftStatus.toString()
        + "...", 3);
    /*
     * We don't do the regex filter here so that we can still resolve parent
     * files that don't match the regex
     */
    cldrFactory = Factory.make(cldrDirectory, ".*", minimumDraftStatus);
    debugPrintln("Factory made.\n", 3);
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
      // if (!weirdCases.contains(locale)) {
      // continue locales;
      // }
      // if (!locale.equals("ku_Latn")) {
      // continue locales;
      // }

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
    debugPrint("Getting list of locales...", 3);
    Set<String> allLocales = cldrFactory.getAvailable();
    Set<String> locales = new TreeSet<String>();
    // Iterate through all the locales
    for (String locale : allLocales) {
      // Check if the locale name matches the regex
      if (locale.matches(localeRegex)) {
        locales.add(locale);
      } else {
        debugPrintln("Locale " + locale + "does not match the pattern.  Skipping...\n", 4);
      }
      
    }
    debugPrintln("done.\n", 3);
    return locales;
  }
  
  public Factory getFactory() {
    return cldrFactory;
  }

  public CLDRFile resolveLocale(String locale, ResolutionType resolutionType) {
    debugPrintln("Processing locale " + locale + "...", 2);

    // Create CLDRFile for current (base) locale
    debugPrintln("Making base file...", 3);
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

  private CLDRFile resolveNonRootLocale(CLDRFile file, ResolutionType resolutionType) {
    String locale = file.getLocaleID();
    String parentLocale = null;
    CLDRFile truncationParent = null;
    String realParent = null;
    if (resolutionType == ResolutionType.SIMPLE) {
      // Make parent file
      debugPrintln("Making parent file by truncation...", 3);
      parentLocale = LanguageTagParser.getParent(locale);
      truncationParent = cldrFactory.make(parentLocale, true);
      realParent = CLDRFile.getParent(locale);
    }

    // Create empty file to hold (partially or fully) resolved data
    debugPrint("Creating empty CLDR file to store resolved data...", 3);
    // False/unresolved because it needs to be mutable.
    CLDRFile resolved = new CLDRFile(new CLDRFile.SimpleXMLSource(null, locale), false);
    debugPrintln("done.", 3);

    if (resolutionType == ResolutionType.SIMPLE) {
      debugPrintln("Filtering against truncation parent " + parentLocale + " (real parent: "
          + realParent + ")...", 2);
    } else {
      debugPrintln(
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
      debugPrintln("Distinguished path: " + distinguishedPath, 5);

      if (resolutionType == ResolutionType.FULL
          || resolutionType == ResolutionType.NO_CODE_FALLBACK) {
        fullPath = file.getFullXPath(distinguishedPath);
        debugPrintln("Full path: " + fullPath, 5);
      }

      if (distinguishedPath.endsWith("/alias")) {
        // Ignore any aliases.
        debugPrintln("This path is an alias.  Dropping...", 5);
        continue;
      }

      String parentValue = null;
      if (resolutionType == ResolutionType.SIMPLE) {
        parentValue = getValueIfInPathSet(truncationParent, distinguishedPath);
        debugPrintln("    Parent [" + parentLocale + "] value : " + ResolverUtils.strRep(parentValue), 5);
      }
      
      String baseValue = file.getStringValue(distinguishedPath);
      debugPrintln("    Base [" + locale + "] value: " + ResolverUtils.strRep(baseValue), 5);
      if (baseValue == null && parentValue != null) {
        // This catches (and ignores) weirdness caused by aliases in older
        // versions of CLDR.
        // This shouldn't happen in the new version.
        debugPrintln("Non-inherited null detected in base locale.  If you are using a version"
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
        debugPrintln("  Adding to resolved file.", 5);
        // Suppress non-distinguishing attributes in simple inheritance
        resolved.add((resolutionType == ResolutionType.SIMPLE ? distinguishedPath : fullPath),
            baseValue);
      }
    }

    // The undefined value is only needed for the simple inheritance resolution
    if (resolutionType == ResolutionType.SIMPLE) {
      // Add undefined values for anything in the parent but not the child
      debugPrintln("Adding UNDEFINED values based on " + truncationParent.getLocaleID(), 3);
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
    debugPrint("Printing file...", 2);
    try {
      PrintWriter pw =
          new PrintWriter(new File(directory, cldrFile.getLocaleID() + ".xml"), "UTF-8");
      cldrFile.write(pw);
      pw.close();
      debugPrintln("done.\n", 2);
    } catch (FileNotFoundException e) {
      debugPrintln("\nFile not found: " + e.getMessage(), 1);
      System.exit(1);
      return;
    } catch (UnsupportedEncodingException e) {
      // This should never ever happen.
      debugPrintln("Your system does not support UTF-8 encoding: " + e.getMessage(), 1);
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
        new CLDRFile(new CLDRFile.SimpleXMLSource(null, cldrFile.getLocaleID()), false);
    debugPrintln("Removing aliases"
        + (resolutionType == ResolutionType.NO_CODE_FALLBACK ? " and code-fallback" : "") + "...",
        2);
    // Go through the XPaths, filter out aliases, then copy to the new CLDRFile
    for (String distinguishedPath : ResolverUtils.getAllPaths(cldrFile)) {
      debugPrintln("Path: " + distinguishedPath, 5);
      if (distinguishedPath.endsWith("/alias")) {
        debugPrintln("  This path is an alias.  Dropping...", 5);
        continue;
      } else if (resolutionType == ResolutionType.NO_CODE_FALLBACK
          && cldrFile.getSourceLocaleID(distinguishedPath, null).equals(CODE_FALLBACK)) {
        debugPrintln("  This path is in code-fallback.  Dropping...", 5);
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
   * Resolves a string to a draft status enum object. The resolution is
   * performed by selecting a value from the DraftStatus enum that starts with
   * or is equivalent to the given string (case-insensitive), as long as it is
   * the only DraftStatus that does so.
   * 
   * @param str the string to resolve
   * @return an object of type CLDRFile.DraftStatus, or null if the string
   *         cannot be unambiguously resolved to a DraftStatus
   */
  private static DraftStatus draftStatusFromString(String str) {
    DraftStatus value = null;
    str = str.toLowerCase(Locale.ENGLISH);
    for (DraftStatus status : DraftStatus.values()) {
      if (status.toString().toLowerCase(Locale.ENGLISH).startsWith(str)) {
        if (value == null) {
          // This is the first time we've found a DraftStatus that matches
          value = status;
        } else {
          // This string is ambiguous - two DraftStatus names start with it
          value = null;
          break;
        }
      }
    }
    return value;
  }

  /**
   * Debugging method to print things based on verbosity.
   * 
   * @param str The string to print
   * @param verbosity The minimum VERBOSITY level at which to print this message
   */
  private static void debugPrint(String str, int verbosity) {
    if (VERBOSITY >= verbosity) {
      System.out.print(str);
    }
  }

  /**
   * Debugging method to print things based on verbosity.
   * 
   * @param str
   * @param verbosity
   */
  private static void debugPrintln(String str, int verbosity) {
    debugPrint(str + "\n", verbosity);
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
