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
import java.util.Iterator;
import java.util.Set;

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
   * The value that denotes a non-existant value in the child that exists in the
   * truncation parent
   */
  private static final String UNDEFINED = "\uFFFDNO_VALUE\uFFFD";

  /**
   * The name of the code-fallback locale
   */
  private static final String CODE_FALLBACK = "code-fallback";

  /* The command-line UOptions, along with a storage container. */
  private static final UOption LOCALE = UOption.create("locale", 'l', UOption.REQUIRES_ARG);
  private static final UOption DESTDIR = UOption.DESTDIR();
  private static final UOption SOURCEDIR = UOption.SOURCEDIR();
  private static final UOption RESOLUTION_TYPE = UOption.create("resolutiontype", 'r',
      UOption.REQUIRES_ARG);
  private static final UOption[] options = {LOCALE, DESTDIR, SOURCEDIR, RESOLUTION_TYPE};

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
  Factory cldrFactory;

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
        System.out.println("Error: " + e.getMessage());
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

    CldrResolver resolver =
        new CldrResolver(srcDir);
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
    debugPrintln("Making factory with minimum draft status " + minimumDraftStatus.toString() + "...", 3);
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

    debugPrint("Getting available locales...", 3);
    Set<String> locales = cldrFactory.getAvailable();
    debugPrintln("done.\n", 3);

    // Iterate through all the locales
    locales: for (Iterator<String> localeIter = locales.iterator(); localeIter.hasNext();) {
      String locale = localeIter.next();
      // if (!weirdCases.contains(locale)) {
      // continue locales;
      // }
      // if (!locale.equals("ku_Latn")) {
      // continue locales;
      // }
      // Check if the locale name matches the regex
      if (!locale.matches(localeRegex)) {
        debugPrintln("Locale " + locale + "does not match the pattern.  Skipping...\n", 4);
        continue locales;
      }

      debugPrintln("Processing locale " + locale + "...", 2);

      // Create CLDRFile for current (base) locale
      debugPrintln("Making base file...", 3);
      CLDRFile base = cldrFactory.make(locale, true);

      // root, having no parent, is a special case, which just gets its aliases
      // removed and then gets printed directly.
      if (locale.equals("root")) {
        debugPrintln("Locale is root.", 3);

        // Remove aliases from root.
        CLDRFile rootWithoutAliases =
            removeAliases(base, resolutionType == ResolutionType.NO_CODE_FALLBACK);
        printToFile(rootWithoutAliases, outputDir);
      } else {
        // Make parent file
        debugPrintln("Making parent file by truncation...", 3);
        String parentLocale = LanguageTagParser.getParent(locale);
        CLDRFile truncationParent = cldrFactory.make(parentLocale, true);
        String realParent = CLDRFile.getParent(locale);


        // Create empty file to hold (partially or fully) resolved data
        debugPrint("Creating empty CLDR file to store resolved data...", 3);
        // False/unresolved because it needs to be mutable.
        CLDRFile resolved = new CLDRFile(new CLDRFile.SimpleXMLSource(null, locale), false);
        debugPrintln("done.", 3);

        // Go through the XPaths, filter out aliases and inherited values,
        // then copy to the new CLDRFile.
        debugPrintln("Filtering against truncation parent " + parentLocale + " (real parent: "
            + realParent + ")...", 2);
        Set<String> basePaths = new HashSet<String>();
        String distinguishedPath = null;
        String fullPath = null;
        paths: for (Iterator<String> baseIter = base.iterator("", CLDRFile.ldmlComparator); baseIter
            .hasNext();) {
          distinguishedPath = baseIter.next();
          fullPath = base.getFullXPath(distinguishedPath);
          basePaths.add(distinguishedPath);
          debugPrintln("Distinguished path: " + distinguishedPath, 5);
          debugPrintln("Full path: " + fullPath, 5);
          if (distinguishedPath.equals("//ldml/alias")) {
            // If the entire locale is an alias, we don't output a file. Such
            // locales have an element at the XPath //ldml/alias
            // This appears to be obsolete with CLDR 2.0
            debugPrintln("Entire-locale alias found.  Skipping...\n", 2);
            continue locales;
          } else if (distinguishedPath.endsWith("/alias")) {
            // Ignore any aliases.
            debugPrintln("This path is an alias.  Dropping...", 5);
            continue paths;
          }

          String parentValue = truncationParent.getStringValue(distinguishedPath);
          debugPrintln("    Parent [" + parentLocale + "] value : " + strRep(parentValue), 5);
          String baseValue = base.getStringValue(distinguishedPath);
          debugPrintln("    Base [" + locale + "] value: " + strRep(baseValue), 5);
          if (baseValue == null && parentValue != null) {
            // This catches (and ignores) weirdness caused by aliases in older
            // versions of CLDR.
            // This shouldn't happen in the new version.
            debugPrintln("Non-inherited null detected in base locale.  If you are using a version"
                + " of CLDR 2.0.0 or newer, this is cause for concern.", 1);
            continue paths;
          }
          /*
           * If we're fully resolving the locale (and, if code-fallback
           * suppression is enabled, if the value is not from code-fallback) or
           * the values aren't equal, add it to the resolved file.
           */
          if (resolutionType == ResolutionType.FULL
              || (resolutionType == ResolutionType.NO_CODE_FALLBACK && !base.getSourceLocaleID(
                  distinguishedPath, null).equals(CODE_FALLBACK))
              || !areEqual(parentValue, baseValue)) {
            debugPrintln("  Adding to resolved file.", 5);
            resolved.add(fullPath, baseValue);
          }
        }
        // Add undefined values for anything in the parent but not the child
        debugPrintln("Checking values in " + base.getLocaleID(), 3);
        for (Iterator<String> parentIter = truncationParent.iterator("", CLDRFile.ldmlComparator); parentIter
            .hasNext();) {
          distinguishedPath = parentIter.next();
          // Do the comparison with distinguished paths to prevent errors
          // resulting from duplicate full paths but the same distinguished path
          if (!basePaths.contains(distinguishedPath)) {
            // Then add the full path
            resolved.add(fullPath, UNDEFINED);
          }
        }

        // Output the file to disk
        printToFile(resolved, outputDir);
      }
    }
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
   * @param suppressCodeFallback if true, entries from code-fallback will be
   *        suppressed as well
   * @return a copy of cldrFile with aliases removed
   */
  private static CLDRFile removeAliases(CLDRFile cldrFile, boolean suppressCodeFallback) {
    // False/unresolved because it needs to be mutable
    CLDRFile partiallyResolved =
        new CLDRFile(new CLDRFile.SimpleXMLSource(null, cldrFile.getLocaleID()), false);
    debugPrintln("Removing aliases" + (suppressCodeFallback ? " and code-fallback" : "") + "...", 2);
    // Go through the XPaths, filter out aliases, then copy to the new CLDRFile
    String path = null;
    paths: for (Iterator<String> fileIter = cldrFile.iterator("", CLDRFile.ldmlComparator); fileIter
        .hasNext();) {
      path = fileIter.next();
      debugPrintln("Path: " + path, 5);
      if (path.endsWith("/alias")) {
        debugPrintln("  This path is an alias.  Dropping...", 5);
        continue paths;
      } else if (suppressCodeFallback
          && cldrFile.getSourceLocaleID(path, null).equals(CODE_FALLBACK)) {
        debugPrintln("  This path is in code-fallback.  Dropping...", 5);
        continue paths;
      } else {
        String value = cldrFile.getStringValue(path);
        String fullPath = cldrFile.getFullXPath(path);
        partiallyResolved.add(fullPath, value);
      }
    }
    return partiallyResolved;
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
   * Debugging method used to make null and empty strings more obvious in
   * printouts
   * 
   * @param str the string
   * @return "[null]" if str==null, "[empty]" if str is the empty string, str
   *         otherwise
   */
  private static String strRep(String str) {
    if (str == null) {
      return "[null]";
    } else if (str.isEmpty()) {
      return "[empty]";
    } else {
      return str;
    }
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
