/*
 ******************************************************************************
 * Copyright (C) 2005, International Business Machines Corporation and        *
 * others. All Rights Reserved.                                               *
 ******************************************************************************
 */

package org.unicode.cldr.test;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.text.ParsePosition;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.unicode.cldr.test.CoverageLevel.Level;
import org.unicode.cldr.util.CLDRFile;
import org.unicode.cldr.util.Counter;
import org.unicode.cldr.util.LocaleIDParser;
import org.unicode.cldr.util.PrettyPath;
import org.unicode.cldr.util.StandardCodes;
import org.unicode.cldr.util.Utility;
import org.unicode.cldr.util.XMLSource;
import org.unicode.cldr.util.CLDRFile.Factory;
import org.unicode.cldr.util.CLDRFile.Status;

import com.ibm.icu.impl.CollectionUtilities;
import com.ibm.icu.lang.UCharacter;
import com.ibm.icu.text.MessageFormat;
import com.ibm.icu.text.Transliterator;
import com.ibm.icu.text.UnicodeSet;
import com.ibm.icu.dev.test.util.ElapsedTimer;
import com.ibm.icu.dev.test.util.TransliteratorUtilities;
import com.ibm.icu.dev.tool.UOption;

/**
 * This class provides a foundation for both console-driven CLDR tests, and Survey Tool Tests.
 * <p>To add a test, subclass CLDRFile and override handleCheck and possibly setCldrFileToCheck.
 * Then put the test into getCheckAll.
 * <p>To use the test, take a look at the main below. Note that you need to call setDisplayInformation
 * with the CLDRFile for the locale that you want the display information (eg names for codes) to be in.
 * <p>TODO
 * <br>add CheckCoverage
 * <br>add CheckAttributeValue
 * @author davis
 */
abstract public class CheckCLDR {
  private CLDRFile cldrFileToCheck;
  private CLDRFile resolvedCldrFileToCheck;
  private CLDRFile displayInformation;
  
  static boolean SHOW_LOCALE = true;
  static boolean SHOW_EXAMPLES = false;
  public static boolean SHOW_TIMES = false;
  public static boolean showStackTrace = false;
  public static boolean errorsOnly = false;
  public static String finalErrorType = CheckStatus.errorType;
  
  /**
   * Here is where the list of all checks is found. 
   * @param nameMatcher Regex pattern that determines which checks are run,
   * based on their class name (such as .* for all checks, .*Collisions.* for CheckDisplayCollisions, etc.)
   * @return
   */
  public static CompoundCheckCLDR getCheckAll(String nameMatcher) {
    return new CompoundCheckCLDR()
    .setFilter(Pattern.compile(nameMatcher,Pattern.CASE_INSENSITIVE).matcher(""))
    .add(new CheckAttributeValues())
    //.add(new CheckChildren()) // don't enable this; will do in code.
    .add(new CheckCoverage())
    .add(new CheckDates())
    .add(new CheckDisplayCollisions())
    .add(new CheckExemplars())
    .add(new CheckForExemplars())
    .add(new CheckNumbers())
    .add(new CheckZones())
    //.add(new CheckAlt())
    .add(new CheckCurrencies())
    .add(new CheckCasing())
    .add(new CheckNew()) // this is at the end; it will check for other certain other errors and warnings and not add a message if there are any.
    ;
  }
  
  /**
   * These determine what language is used to display information. Must be set before use.
   * @param locale
   * @return
   */
  public CLDRFile getDisplayInformation() {
    return displayInformation;
  }
  public void setDisplayInformation(CLDRFile displayInformation) {
    setDisplayInformation(displayInformation, new ExampleGenerator(displayInformation, Utility.SUPPLEMENTAL_DIRECTORY));
  }
  public void setDisplayInformation(CLDRFile displayInformation, ExampleGenerator exampleGenerator) {
    this.displayInformation = displayInformation;
    englishExampleGenerator = exampleGenerator;
  }
  private ExampleGenerator englishExampleGenerator;
  
  private static final int
  HELP1 = 0,
  HELP2 = 1,
  COVERAGE = 2,
  EXAMPLES = 3,
  FILE_FILTER = 4,
  TEST_FILTER = 5,
  DATE_FORMATS = 6,
  ORGANIZATION = 7,
  SHOWALL = 8,
  PATH_FILTER = 9,
  ERRORS_ONLY = 10,
  CHECK_ON_SUBMIT = 11,
  NO_ALIASES = 12,
  SOURCE_DIRECTORY = 13,
  USER = 14
  ;
  
  private static final UOption[] options = {
    UOption.HELP_H(),
    UOption.HELP_QUESTION_MARK(),
    UOption.create("coverage", 'c', UOption.REQUIRES_ARG),
    UOption.create("examples", 'x', UOption.NO_ARG),
    UOption.create("file_filter", 'f', UOption.REQUIRES_ARG).setDefault(".*"),
    UOption.create("test_filter", 't', UOption.REQUIRES_ARG).setDefault(".*"),
    UOption.create("date_formats", 'd', UOption.NO_ARG),
    UOption.create("organization", 'o', UOption.REQUIRES_ARG),
    UOption.create("showall", 'a', UOption.NO_ARG),
    UOption.create("path_filter", 'p',  UOption.REQUIRES_ARG).setDefault(".*"),
    UOption.create("errors_only", 'e', UOption.NO_ARG),
    UOption.create("check-on-submit", 'k', UOption.NO_ARG),
    UOption.create("noaliases", 'n', UOption.NO_ARG),
    UOption.create("source_directory", 's',  UOption.REQUIRES_ARG).setDefault(Utility.MAIN_DIRECTORY),
    UOption.create("user", 'u',  UOption.REQUIRES_ARG),
  };
  
  // C:\cvsdata\\unicode\cldr\incoming\vetted\main
  private static String[] HelpMessage = {
    "-h \t This message",
    "-s \t Source directory, default = " + Utility.MAIN_DIRECTORY,
    "-fxxx \t Pick the locales (files) to check: xxx is a regular expression, eg -f fr, or -f fr.*, or -f (fr|en-.*)",
    "-pxxx \t Pick the paths to check, eg -p(.*languages.*)",
    "-cxxx \t Set the coverage: eg -c comprehensive or -c modern or -c moderate or -c basic",
    "-txxx \t Filter the Checks: xxx is a regular expression, eg -t.*number.*",
    "-oxxx \t Organization: ibm, google, ....; filters locales and uses Locales.txt for coverage tests",
    "-x \t Turn on examples (actually a summary of the demo)",
    "-d \t Turn on special date format checks",
    "-A \t Show all paths",
    "-e \t Show errors only",
    "-n \t No aliases",
    "-u \t User, eg -uu148",
  };
  
  /**
   * This will be the test framework way of using these tests. It is preliminary for now.
   * The Survey Tool will call setDisplayInformation, and getCheckAll.
   * For each cldrfile, it will set the cldrFile.
   * Then on each path in the file it will call check.
   * Right now it doesn't work with resolved files, so just use unresolved ones.
   * @param args
   * @throws IOException 
   */
  public static void main(String[] args) throws IOException {
    Utility.showOptions(args);
    double deltaTime = System.currentTimeMillis();
    UOption.parseArgs(args, options);
    if (options[HELP1].doesOccur || options[HELP2].doesOccur) {
      for (int i = 0; i < HelpMessage.length; ++i) {
        System.out.println(HelpMessage[i]);
      }
      return;
    }
    String factoryFilter = options[FILE_FILTER].value; 
    String checkFilter = options[TEST_FILTER].value;
    errorsOnly = options[ERRORS_ONLY].doesOccur;
    if (errorsOnly) finalErrorType = CheckStatus.warningType;
    
    SHOW_EXAMPLES = options[EXAMPLES].doesOccur; // eg .*Collision.* 
    boolean showAll = options[SHOWALL].doesOccur; 
    boolean checkFlexibleDates = options[DATE_FORMATS].doesOccur; 
    String pathFilterString = options[PATH_FILTER].value;
    Matcher pathFilter = null;
    if (!pathFilterString.equals(".*")) {
      pathFilter = Pattern.compile(pathFilterString).matcher("");
    }
    boolean checkOnSubmit = options[CHECK_ON_SUBMIT].doesOccur; 
    boolean noaliases = options[NO_ALIASES].doesOccur; 
    
    Level coverageLevel = null;
    String coverageLevelInput = options[COVERAGE].value;
    if (coverageLevelInput != null) {
      coverageLevel = Level.get(coverageLevelInput);
      if (coverageLevel == Level.UNDETERMINED) {
        throw new IllegalArgumentException("-c" + coverageLevelInput + "\t is invalid: must be one of: " + "basic,moderate,...");
      }
    }
    
    String organization = options[ORGANIZATION].value;
    if (organization != null) {
      Set<String> organizations = StandardCodes.make().getLocaleCoverageOrganizations();
      if (!organizations.contains(organization)) {
        throw new IllegalArgumentException("-o" + organization + "\t is invalid: must be one of: " + organizations);
      }
    }
    
    // check stuff
//  Comparator cc = StandardCodes.make().getTZIDComparator();
//  System.out.println(cc.compare("Antarctica/Rothera", "America/Cordoba"));
//  System.out.println(cc.compare("Antarctica/Rothera", "America/Indianapolis"));
    
    
    String sourceDirectory = options[SOURCE_DIRECTORY].value;
    String user = options[USER].value;
    
    System.out.println("source directory: " + sourceDirectory + "\t" + new File(sourceDirectory).getCanonicalPath());
    System.out.println("factoryFilter: " + factoryFilter);
    System.out.println("test filter: " + checkFilter);
    System.out.println("organization: " + organization);
    System.out.println("show examples: " + SHOW_EXAMPLES);
    System.out.println("coverage level: " + coverageLevel);
    System.out.println("checking dates: " + checkFlexibleDates);
    System.out.println("only check-on-submit: " + checkOnSubmit);
    System.out.println("show all: " + showAll);
    System.out.println("errors only?: " + errorsOnly);
    
    // set up the test
    Factory cldrFactory = CLDRFile.Factory.make(sourceDirectory, factoryFilter);
    CheckCLDR checkCldr = getCheckAll(checkFilter);
    checkCldr.setDisplayInformation(cldrFactory.make("en", true));
    PathShower pathShower = checkCldr.new PathShower();
    
    // call on the files
    Set locales = cldrFactory.getAvailable();
    List result = new ArrayList();
    Set paths = new TreeSet(CLDRFile.ldmlComparator);
    Map m = new TreeMap();
    //double testNumber = 0;
    Map options = new HashMap();
    Counter totalCount = new Counter();
    Counter subtotalCount = new Counter();
    FlexibleDateFromCLDR fset = new FlexibleDateFromCLDR();
    
    PrettyPath prettyPath = new PrettyPath();
    
    for (Iterator it = locales.iterator(); it.hasNext();) {
      String localeID = (String) it.next();
      if (CLDRFile.isSupplementalName(localeID)) continue;
      boolean isLanguageLocale = localeID.equals(new LocaleIDParser().set(localeID).getLanguageScript());
      options.clear();
      
      // if the organization is set, skip any locale that doesn't have a value in Locales.txt
      Level level = coverageLevel;
      if (level == null) {
        level = Level.BASIC;
      }
      if (organization != null) {
        Map<String,Level> locale_status = StandardCodes.make().getLocaleTypes().get(organization);
        if (locale_status == null) continue;
        level = locale_status.get(localeID);
        if (level == null) continue;
        if (level.compareTo(Level.BASIC) <= 0) continue;
      } else if (!isLanguageLocale) {
        // otherwise, skip all language locales
        options.put("CheckCoverage.skip","true");
      }
      
      if (coverageLevel != null) options.put("CheckCoverage.requiredLevel", coverageLevel.toString());
      if (organization != null) options.put("CoverageLevel.localeType", organization);
      if (true) options.put("submission", "true");
      if (SHOW_LOCALE) System.out.println("Locale\tStatus\tCode\tEng.Value\tEng.Ex.\tLoc.Value\tLoc.Ex\tError/Warning\tPath");
      
      //options.put("CheckCoverage.requiredLevel","comprehensive");
      
      CLDRFile file = cldrFactory.make(localeID, isLanguageLocale);
      if (user != null) {
        file = new CLDRFile.TestUser(file, user, isLanguageLocale);
      }
      checkCldr.setCldrFileToCheck(file, options, result);
      
      subtotalCount.clear();
      
      for (Iterator it3 = result.iterator(); it3.hasNext();) {
        CheckStatus status = (CheckStatus) it3.next();
        String statusString = status.toString(); // com.ibm.icu.impl.Utility.escape(
        String statusType = status.getType();
        
        if (errorsOnly && !statusType.equals(status.errorType)) continue;
        if (checkOnSubmit) {
          if (!status.isCheckOnSubmit() || !statusType.equals(status.errorType)) continue;
        }
        showSummary(checkCldr, localeID, level, statusString);
        subtotalCount.add(status.type, 1);
      }
      paths.clear();
      CollectionUtilities.addAll(file.iterator(pathFilter), paths);
      UnicodeSet missingExemplars = new UnicodeSet();
      if (checkFlexibleDates) {
        fset.set(file);
      }
      pathShower.set(localeID);
//      if (pretty) {
//        System.out.println("Showing Pretty Paths");
//        Map prettyMap = new TreeMap();
//        Set prettySet = new TreeSet();
//        for (Iterator it2 = paths.iterator(); it2.hasNext();) {
//          String path = (String)it2.next();
//          String prettyString = prettyPath.getPrettyPath(path);
//          if (prettyString.indexOf("%%") >= 0) prettyString = "unmatched/" + prettyString;
//          Object old = prettyMap.get(prettyString);
//          if (old != null) {
//            System.out.println("Collision with: ");
//            System.out.println("\t" + prettyString);
//            System.out.println("\t\t" + path);
//            System.out.println("\t\t" + old);
//          }
//          prettyMap.put(prettyString, path);
//          String cleanPath = prettyString;
//          int last = prettyString.lastIndexOf('|');
//          if (last >= 0) cleanPath = cleanPath.substring(0,last);
//          prettySet.add(cleanPath);
//          System.out.println(prettyString + " => " + path);
//        }
//        System.out.println("Showing Structure");
//        String oldSplit = pathShower.getSplitChar();
//        pathShower.setSplitChar("\\|");
//        for (Iterator it2 = prettyMap.keySet().iterator(); it2.hasNext();) {
//          String prettyString = (String) it2.next();
//          String path = (String) prettyMap.get(prettyString);
//          pathShower.showHeader(prettyString, file.getStringValue(path));
//        }
//        System.out.println("Showing Non-Leaves");
//        pathShower.setSplitChar(oldSplit);
//        for (Iterator it2 = prettySet.iterator(); it2.hasNext();) {
//          String prettyString = (String) it2.next();
//          System.out.println(prettyString);
//        }
//        System.out.println("Done Showing Pretty Paths");
//        return;
//      }
      
      ExampleGenerator exampleGenerator = new ExampleGenerator(file, Utility.SUPPLEMENTAL_DIRECTORY);
      Status pathStatus = new Status();
      int pathCount = 0;
      for (Iterator it2 = paths.iterator(); it2.hasNext();) {
        pathCount++;
        String path = (String) it2.next();
        String value = file.getStringValue(path);
//        if (value == null) {
//          value = file.getStringValue(path);
//        }
        String fullPath = file.getFullXPath(path);
        if (noaliases) { // this is just for console testing, the survey tool shouldn't do it.
          String sourceLocale = file.getSourceLocaleID(path, pathStatus);
          if (!path.equals(pathStatus.pathWhereFound)) {
            continue;
          }
        }
        
        String example = exampleGenerator.getExampleHtml(path, value, ExampleGenerator.Zoomed.OUT);

        if (SHOW_EXAMPLES) {
          showExamples(checkCldr, prettyPath, localeID, exampleGenerator, path, value, fullPath, example);
          //continue; // don't show problems
        }

        if (checkFlexibleDates) {
          fset.checkFlexibles(path, value, fullPath);
        }
        
        int limit = 1;
        if (SHOW_EXAMPLES) limit = 2;
        for (int jj = 0; jj < limit; ++jj) {
          if (jj == 0) {
            checkCldr.check(path, fullPath, value, options, result);
          } else {
            checkCldr.getExamples(path, fullPath, value, options, result);
          }
          
          if (showAll) pathShower.showHeader(path, value);
                    
          for (Iterator it3 = result.iterator(); it3.hasNext();) {
            CheckStatus status = (CheckStatus) it3.next();
            String statusString = status.toString(); // com.ibm.icu.impl.Utility.escape(
            String statusType = status.getType();
            if (errorsOnly && !statusType.equals(status.errorType)) continue;
            if (checkOnSubmit) {
              if (!status.isCheckOnSubmit() || !statusType.equals(status.errorType)) continue;
            }
            //pathShower.showHeader(path, value);
            
            
            //System.out.print("Locale:\t" + getLocaleAndName(localeID) + "\t");
            if (statusType.equals(CheckStatus.demoType)) {
              SimpleDemo d = status.getDemo();
              if (d != null && d instanceof FormatDemo) {
                FormatDemo fd = (FormatDemo)d;
                m.clear();
                //m.put("pattern", fd.getPattern());
                //m.put("input", fd.getRandomInput());
                if (d.processPost(m)) System.out.println("\tDemo:\t" + fd.getPlainText(m));
              }
              continue;
            }
            checkCldr.showValue(prettyPath, localeID, example, path, value, fullPath, statusString);

            subtotalCount.add(status.type, 1);
            totalCount.add(status.type, 1);
            Object[] parameters = status.getParameters();
            if (parameters != null) for (int i = 0; i < parameters.length; ++i) {
              if (showStackTrace && parameters[i] instanceof Throwable) {
                ((Throwable)parameters[i]).printStackTrace();
              }
              if (status.getMessage().startsWith("Not in exemplars")) {
                missingExemplars.addAll(new UnicodeSet(parameters[i].toString()));
              }
            }
            // survey tool will use: if (status.hasHTMLMessage()) System.out.println(status.getHTMLMessage());
          }
        }
      }
      showSummary(checkCldr, localeID, level, "Paths:\t" + pathCount);
      if (missingExemplars.size() != 0) {
        showSummary(checkCldr, localeID, level, "Total missing:\t" + missingExemplars);
      }
      for (Iterator it2 = new TreeSet(subtotalCount.keySet()).iterator(); it2.hasNext();) {
        String type = (String)it2.next();
        showSummary(checkCldr, localeID, level, "Subtotal " + type + ":\t" + subtotalCount.getCount(type));
      }
      if (checkFlexibleDates) {
        fset.showFlexibles();
      }
      if (SHOW_EXAMPLES) {
//      ldml/dates/timeZoneNames/zone[@type="America/Argentina/San_Juan"]/exemplarCity
        for (String zone : StandardCodes.make().getGoodAvailableCodes("tzid")) {
          String path = "//ldml/dates/timeZoneNames/zone[@type=\"" + zone + "\"]/exemplarCity";
          if (!pathFilter.reset(path).matches()) continue;
          String fullPath = file.getStringValue(path);
          if (fullPath != null) continue;
          String example = exampleGenerator.getExampleHtml(path, null, ExampleGenerator.Zoomed.OUT);
          showExamples(checkCldr, prettyPath, localeID, exampleGenerator, path, null, fullPath, example);
        }
      }
    }
    for (Iterator it2 = new TreeSet(totalCount.keySet()).iterator(); it2.hasNext();) {
      String type = (String)it2.next();
      System.out.println("Total " + type + ":\t" + totalCount.getCount(type));
    }
    
    deltaTime = System.currentTimeMillis() - deltaTime;
    System.out.println("Elapsed: " + deltaTime/1000.0 + " seconds");
  }

  private static void showSummary(CheckCLDR checkCldr, String localeID, Level level, String value) {
    System.out.println(checkCldr.getLocaleAndName(localeID) + "\tSummary\t" + level + "\t" + value);
  }

  private static void showExamples(CheckCLDR checkCldr, PrettyPath prettyPath, String localeID, ExampleGenerator exampleGenerator, String path, String value, String fullPath, String example) {
    if (example != null) {
      checkCldr.showValue(prettyPath, localeID, example, path, value, fullPath, "ok");
    }
    String longExample = exampleGenerator.getExampleHtml(path, value, ExampleGenerator.Zoomed.IN);
    if (longExample != null && !longExample.equals(example)) {
      checkCldr.showValue(prettyPath, localeID, longExample, path, value, fullPath, "ok-in");
    }
    String help = exampleGenerator.getHelpHtml(path, value);
    if (help != null) {
      checkCldr.showValue(prettyPath, localeID, help, path, value, fullPath, "ok-help");
    }
  }

  private void showValue(PrettyPath prettyPath, String localeID, String example, String path, String value, String fullPath, String statusString) {
    example = example == null ? "" : "<" + example + ">";
    String englishExample = englishExampleGenerator.getExampleHtml(path, getEnglishPathValue(path), ExampleGenerator.Zoomed.OUT);
    englishExample = englishExample == null ? "" : "<" + englishExample + ">";
    String shortStatus = statusString.equals("ok") ? "ok" : statusString.startsWith("Warning") ? "warn" : statusString.startsWith("Error") ? "err" : "???";
    System.out.println(getLocaleAndName(localeID)
        + "\t" + shortStatus
        + "\t" + prettyPath.getPrettyPath(path, false)
        + "\t" + getEnglishPathValue(path)
        + "\t" + englishExample
        + "\t" + value
        + "\t" + example
        + "\t" + statusString
        + "\t" + fullPath
        );
  }
  
  /**
   * [Warnings - please zoom in]  dates/timeZoneNames/singleCountries   
(empty)
        [refs][hide] Ref:     [Zoom...]
[Warnings - please zoom in]   dates/timeZoneNames/hours   {0}/{1}   {0}/{1}
        [refs][hide] Ref:     [Zoom...]
[Warnings - please zoom in]   dates/timeZoneNames/hour  +HH:mm;-HH:mm   
+HH:mm;-HH:mm
      [refs][hide] Ref:     [Zoom...]
[ok]  layout/orientation     (empty)
        [refs][hide] Ref:     [Zoom...]
[ok]  dates/localizedPatternChars  GyMdkHmsSEDFwWahKzYeugAZvcL 
GaMjkHmsSEDFwWxhKzAeugXZvcL
        [refs][hide] Ref:     [Zoom...]*/
  
  public static final Pattern skipShowingInSurvey = Pattern.compile(
      ".*/(" +
      "beforeCurrency" + // hard to explain, use bug
      "|afterCurrency" + // hard to explain, use bug
      "|orientation" + // hard to explain, use bug
      "|appendItems" + // hard to explain, use bug
      "|singleCountries" + // hard to explain, use bug
      // from deprecatedItems in supplemental metadata
      "|hoursFormat" + // deprecated
      "|localizedPatternChars" + // deprecated
      "|abbreviationFallback" + // deprecated
      "|default" + // deprecated
      "|mapping" + // deprecated
      "|measurementSystem" + // deprecated
      "|preferenceOrdering" + // deprecated
      ")((\\[|/).*)?", Pattern.COMMENTS); // the last bit is to ensure whole element
  
  /**
   * These are paths for items that are complicated, and we need to force a zoom on.
   */
  public static final Pattern FORCE_ZOOMED_EDIT = Pattern.compile(
      ".*/(" +
      "exemplarCharacters" +
      "|metazone" +
      "|pattern" +
      "|dateFormatItem" +
      "|relative" +
      "|hourFormat" +
      "|gmtFormat" +
      "|regionFormat" +
      ")((\\[|/).*)?", Pattern.COMMENTS); // the last bit is to ensure whole element

  public class PathShower {
    String localeID;
    boolean newLocale = true;
    String lastPath;
    String[] lastSplitPath;
    boolean showEnglish;
    String splitChar = "/";
    
    static final String lead = "****************************************";
    
    public void set(String localeID) {
      this.localeID = localeID;
      newLocale = true;
      LocaleIDParser localeIDParser = new LocaleIDParser();
      showEnglish = !localeIDParser.set(localeID).getLanguageScript().equals("en");
      //localeID.equals(CheckCLDR.displayInformation.getLocaleID());
      lastPath = null;
      lastSplitPath = null;
    }
    
    public void setDisplayInformation(CLDRFile displayInformation) {
      setDisplayInformation(displayInformation); 
    }
    
    public void showHeader(String path, String value) {
      if (newLocale) {
        System.out.println("Locale:\t" + getLocaleAndName(localeID));
        newLocale = false;
      }
      if (path.equals(lastPath)) return;

//    This logic keeps us from splitting on an attribute value that contains a /
//    such as time zone names.
//
      StringBuffer newPath = new StringBuffer();
      boolean inQuotes = false;
      for ( int i = 0 ; i < path.length() ; i++ ) {
         if ( (path.charAt(i) == '/') && !inQuotes )
             newPath.append('%');
         else
             newPath.append(path.charAt(i));

         if ( path.charAt(i) == '\"' )
            inQuotes = !inQuotes;
      }
      
      String[] splitPath = newPath.toString().split("%");
      
      for (int i = 0; i < splitPath.length; ++i) {
        if (lastSplitPath != null && i < lastSplitPath.length && splitPath[i].equals(lastSplitPath[i])) {
          continue;
        }
        lastSplitPath = null; // mark so we continue printing now
        System.out.print(lead.substring(0,i));
        System.out.print(splitPath[i]);
        if (i == splitPath.length - 1) {
          showValue(path, value, showEnglish, localeID);				
        } else {
          System.out.print(":");
        }
        System.out.println();				
      }
//    String prettierPath = path;
//    if (false) {
//    prettierPath = prettyPath.transliterate(path);
//    }
      
      lastPath = path;
      lastSplitPath = splitPath;
    }
    
    public String getSplitChar() {
      return splitChar;
    }
    
    public PathShower setSplitChar(String splitChar) {
      this.splitChar = splitChar;
      return this;
    }
  }
  
  private void showValue(String path, String value, boolean showEnglish, String localeID) {
    System.out.println( "\tValue:\t" + value + (showEnglish ? "\t" + getEnglishPathValue(path) : "") + "\tLocale:\t" + localeID);
  }

  private String getEnglishPathValue(String path) {
    String englishValue = displayInformation.getWinningValue(path);
    if (englishValue == null) {
      String path2 = CLDRFile.getNondraftNonaltXPath(path);
      englishValue = displayInformation.getWinningValue(path2);
    }
    return englishValue;
  }
  

  /**
   * Get the CLDRFile.
   * @param cldrFileToCheck
   */
  public final CLDRFile getCldrFileToCheck() {
    return cldrFileToCheck;
  }
  
  public final CLDRFile getResolvedCldrFileToCheck() {
    if (resolvedCldrFileToCheck == null) resolvedCldrFileToCheck = cldrFileToCheck.getResolved();
    return resolvedCldrFileToCheck;
  }
  /**
   * Set the CLDRFile. Must be done before calling check. If null is called, just skip
   * Often subclassed for initializing. If so, make the first 2 lines:
   * 		if (cldrFileToCheck == null) return this;
   * 		super.setCldrFileToCheck(cldrFileToCheck);
   * 		do stuff
   * @param cldrFileToCheck
   * @param options TODO
   * @param possibleErrors TODO
   */
  public CheckCLDR setCldrFileToCheck(CLDRFile cldrFileToCheck, Map options, List possibleErrors) {
    this.cldrFileToCheck = cldrFileToCheck;
    resolvedCldrFileToCheck = null;
    return this;
  }
  /**
   * Status value returned from check
   */
  public static class CheckStatus {
    public static final String 
    alertType = "Comment", 
    warningType = "Warning", 
    errorType = "Error", 
    exampleType = "Example",
    demoType = "Demo";
    private String type;
    private String messageFormat;
    private Object[] parameters;
    private String htmlMessage;
    private CheckCLDR cause;
    private boolean checkOnSubmit = true;
    
    public CheckStatus() {
      
    }
    public boolean isCheckOnSubmit() {
      return checkOnSubmit;
    }
    public CheckStatus setCheckOnSubmit(boolean dependent) {
      this.checkOnSubmit = dependent;
      return this;
    }
    public String getType() {
      return type;
    }
    public CheckStatus setType(String type) {
      this.type = type;
      return this;
    }
    public String getMessage() {
      if (messageFormat == null) return messageFormat;
      return MessageFormat.format(MessageFormat.autoQuoteApostrophe(messageFormat), parameters);
    }
    /*
     * If this is not null, wrap it in a <form>...</form> and display. When you get a submit, call getDemo
     * to get a demo that you can call to change values of the fields. See CheckNumbers for an example. 
     */
    public String getHTMLMessage() {
      return htmlMessage;
    }
    public CheckStatus setHTMLMessage(String message) {
      htmlMessage = message;
      return this;
    }
    public CheckStatus setMessage(String message) {
      this.messageFormat = message;
      return this;
    }
    public CheckStatus setMessage(String message, Object... messageArguments) {
      this.messageFormat = message;
      this.parameters = messageArguments;
      return this;
    }
    public String toString() {
      return getType() + ": " + getMessage();
    }
    /**
     * Warning: don't change the contents of the parameters after retrieving.
     */
    public Object[] getParameters() {
      return parameters;
    }
    /**
     * Warning: don't change the contents of the parameters after passing in.
     */
    public CheckStatus setParameters(Object[] parameters) {
      this.parameters = parameters;
      return this;
    }
    public SimpleDemo getDemo() {
      return null;
    }
    public CheckCLDR getCause() {
      return cause;
    }
    public CheckStatus setCause(CheckCLDR cause) {
      this.cause = cause;
      return this;
    }
  }
  
  public static abstract class SimpleDemo {
    Map internalPostArguments = new HashMap();
    
    /**
     * @param postArguments A read-write map containing post-style arguments. eg TEXTBOX=abcd, etc.
     * <br>The first time this is called, the Map should be empty.
     * @return true if the map has been changed
     */ 
    public abstract String getHTML(Map postArguments) throws Exception;
    
    /**
     * Only here for compatibiltiy. Use the other getHTML instead
     */
    public final String getHTML(String path, String fullPath, String value) throws Exception {
      return getHTML(internalPostArguments);
    }
    
    /**
     * THIS IS ONLY FOR COMPATIBILITY: you can call this, then the non-postArguments form of getHTML; or better, call
     * getHTML with the postArguments.
     * @param postArguments A read-write map containing post-style arguments. eg TEXTBOX=abcd, etc.
     * @return true if the map has been changed
     */ 
    public final boolean processPost(Map postArguments) {
      internalPostArguments.clear();
      internalPostArguments.putAll(postArguments);
      return true;
    }
//  /**
//  * Utility for setting map. Use the paradigm in CheckNumbers.
//  */
//  public boolean putIfDifferent(Map inout, String key, String value) {
//  Object oldValue = inout.put(key, value);
//  return !value.equals(oldValue);
//  }
  }
  
  public static abstract class FormatDemo extends SimpleDemo {
    protected String currentPattern, currentInput, currentFormatted, currentReparsed;
    protected ParsePosition parsePosition = new ParsePosition(0);
    
    protected abstract String getPattern();
    protected abstract String getSampleInput(); 
    protected abstract void getArguments(Map postArguments);
    
    public String getHTML(Map postArguments) throws Exception {
      getArguments(postArguments);
      StringBuffer htmlMessage = new StringBuffer();
      FormatDemo.appendTitle(htmlMessage);
      FormatDemo.appendLine(htmlMessage, currentPattern, currentInput, currentFormatted, currentReparsed);
      htmlMessage.append("</table>");
      return htmlMessage.toString();
    }
    
    public String getPlainText(Map postArguments) {
      getArguments(postArguments);
      return MessageFormat.format("<\"\u200E{0}\u200E\", \"{1}\"> \u2192 \"\u200E{2}\u200E\" \u2192 \"{3}\"",
          new String[] {currentPattern, currentInput, currentFormatted, currentReparsed});
    }
    
    /**
     * @param htmlMessage
     * @param pattern
     * @param input
     * @param formatted
     * @param reparsed
     */
    public static void appendLine(StringBuffer htmlMessage, String pattern, String input, String formatted, String reparsed) {
      htmlMessage.append("<tr><td><input type='text' name='pattern' value='")
      .append(TransliteratorUtilities.toXML.transliterate(pattern))
      .append("'></td><td><input type='text' name='input' value='")
      .append(TransliteratorUtilities.toXML.transliterate(input))
      .append("'></td><td>")
      .append("<input type='submit' value='Test' name='Test'>")
      .append("</td><td>" + "<input type='text' name='formatted' value='")
      .append(TransliteratorUtilities.toXML.transliterate(formatted))
      .append("'></td><td>" + "<input type='text' name='reparsed' value='")
      .append(TransliteratorUtilities.toXML.transliterate(reparsed))
      .append("'></td></tr>");
    }
    
    /**
     * @param htmlMessage
     */
    public static void appendTitle(StringBuffer htmlMessage) {
      htmlMessage.append("<table border='1' cellspacing='0' cellpadding='2'" +
          //" style='border-collapse: collapse' style='width: 100%'" +
          "><tr>" +
          "<th>Pattern</th>" +
          "<th>Unlocalized Input</th>" +
          "<th></th>" +
          "<th>Localized Format</th>" +
          "<th>Re-Parsed</th>" +
      "</tr>");
    }
  }
  
  /**
   * Checks the path/value in the cldrFileToCheck for correctness, according to some criterion.
   * If the path is relevant to the check, there is an alert or warning, then a CheckStatus is added to List.
   * @param path Must be a distinguished path, such as what comes out of CLDRFile.iterator()
   * @param fullPath Must be the full path
   * @param value the value associated with the path
   * @param options A set of test-specific options. Set these with code of the form:<br>
   * options.put("CoverageLevel.localeType", "G0")<br>
   * That is, the key is of the form <testname>.<optiontype>, and the value is of the form <optionvalue>.<br>
   * There is one general option; the following will cause the tests that depend on the rest of the CLDRFile to be abbreviated.<br>
   * options.put("submission", "true") // actually, any value will work, not just "true". Remove "submission" to restore it.
   * It can be used for new data entry.
   * @param result
   */
  public final CheckCLDR check(String path, String fullPath, String value, Map<String, String> options, List<CheckStatus> result) {
    if(cldrFileToCheck == null) {
      throw new InternalError("CheckCLDR problem: cldrFileToCheck must not be null");
    }
    if (path == null) {
      throw new InternalError("CheckCLDR problem: path must not be null");
    }
    if (fullPath == null) {
      throw new InternalError("CheckCLDR problem: fullPath must not be null");
    }
    if (value == null) {
      throw new InternalError("CheckCLDR problem: value must not be null");
    }
    result.clear();
    return handleCheck(path, fullPath, value, options, result);
  }
  
  
  /**
   * Returns any examples in the result parameter. Both examples and demos can
   * be returned. A demo will have getType() == CheckStatus.demoType. In that
   * case, there will be no getMessage or getHTMLMessage available; instead,
   * call getDemo() to get the demo, then call getHTML() to get the initial
   * HTML.
   */ 
  public final CheckCLDR getExamples(String path, String fullPath, String value, Map options, List result) {
    result.clear();
    return handleGetExamples(path, fullPath, value, options, result);		
  }
  
  
  protected CheckCLDR handleGetExamples(String path, String fullPath, String value, Map options2, List result) {
    return this; // NOOP unless overridden
  }
  
  /**
   * This is what the subclasses override.
   * If they ever use pathParts or fullPathParts, they need to call initialize() with the respective
   * path. Otherwise they must NOT change pathParts or fullPathParts.
   * <p>If something is found, a CheckStatus is added to result. This can be done multiple times in one call,
   * if multiple errors or warnings are found. The CheckStatus may return warnings, errors,
   * examples, or demos. We may expand that in the future.
   * <p>The code to add the CheckStatus will look something like::
   * <pre> result.add(new CheckStatus()
   * 		.setType(CheckStatus.errorType)
   *		.setMessage("Value should be {0}", new Object[]{pattern}));				
   * </pre>
   * @param options TODO
   */
  abstract public CheckCLDR handleCheck(String path, String fullPath, String value,
      Map<String, String> options, List<CheckStatus> result);
  
  /**
   * Internal class used to bundle up a number of Checks.
   * @author davis
   *
   */
  static class CompoundCheckCLDR extends CheckCLDR {
    private Matcher filter;
    private List checkList = new ArrayList();
    private List filteredCheckList = new ArrayList();
    
    public CompoundCheckCLDR add(CheckCLDR item) {
      checkList.add(item);
      if (filter == null || filter.reset(item.getClass().getName()).matches()) {
        filteredCheckList.add(item);
      }
      return this;
    }
    public CheckCLDR handleCheck(String path, String fullPath, String value,
        Map<String, String> options, List<CheckStatus> result) {
      result.clear();
      for (Iterator it = filteredCheckList.iterator(); it.hasNext(); ) {
        CheckCLDR item = (CheckCLDR) it.next();
        try {
          item.handleCheck(path, fullPath, value, options, result);
        } catch (Exception e) {
          addError(result, item, e);
          return this;
        }
      }
      return this;
    }
    
    protected CheckCLDR handleGetExamples(String path, String fullPath, String value, Map options, List result) {
      result.clear();
      for (Iterator it = filteredCheckList.iterator(); it.hasNext(); ) {
        CheckCLDR item = (CheckCLDR) it.next();
        try {
          item.handleGetExamples(path, fullPath, value, options, result);
        } catch (Exception e) {
          addError(result, item, e);
          return this;
        }
      }
      return this;
    }
    
    private void addError(List result, CheckCLDR item, Exception e) {
      result.add(new CheckStatus().setType(CheckStatus.errorType)
          .setMessage("Internal error in {0}. Exception: {1}, Message: {2}, Trace: {3}", 
              new Object[]{item.getClass().getName(), e.getClass().getName(), e, 
              Arrays.asList(e.getStackTrace())}));
    }
    public CheckCLDR setCldrFileToCheck(CLDRFile cldrFileToCheck, Map options, List possibleErrors) {
      ElapsedTimer testTime = null;
      if (cldrFileToCheck == null) return this;
      super.setCldrFileToCheck(cldrFileToCheck,options,possibleErrors);
      possibleErrors.clear();
      for (Iterator it = filteredCheckList.iterator(); it.hasNext(); ) {
        CheckCLDR item = (CheckCLDR) it.next();
        if(SHOW_TIMES) testTime = new ElapsedTimer("Test setup time for " + item.getClass().toString() + " {0}");
        try {
          item.setCldrFileToCheck(cldrFileToCheck, options, possibleErrors);
          if(SHOW_TIMES) System.err.println("OK : " + testTime);
        } catch (RuntimeException e) {
          addError(possibleErrors, item, e);
          if(SHOW_TIMES) System.err.println("ERR: " + testTime + " - " + e.toString());
        }
      }
      return this;
    }
    public Matcher getFilter() {
      return filter;
    }
    
    public CompoundCheckCLDR setFilter(Matcher filter) {
      this.filter = filter;
      filteredCheckList.clear();
      for (Iterator it = checkList.iterator(); it.hasNext(); ) {
        CheckCLDR item = (CheckCLDR) it.next();
        if (filter == null || filter.reset(item.getClass().getName()).matches()) {
          filteredCheckList.add(item);
          item.setCldrFileToCheck(getCldrFileToCheck(), null, null);
        }
      }
      return this;
    }
  }
  
  /**
   * Utility for getting information.
   * @param locale
   * @return
   */
  public String getLocaleAndName(String locale) {
    String localizedName = displayInformation.getName(locale, false);
    if (localizedName == null || localizedName.equals(locale)) return locale;
    return locale + " [" + localizedName + "]";
  }
  
  //static Transliterator prettyPath = getTransliteratorFromFile("ID", "prettyPath.txt");
  
  public static Transliterator getTransliteratorFromFile(String ID, String file) {
    try {
      BufferedReader br = Utility.getUTF8Data(file);
      StringBuffer input = new StringBuffer();
      while (true) {
        String line = br.readLine();
        if (line == null) break;
        if (line.startsWith("\uFEFF")) line = line.substring(1); // remove BOM
        input.append(line);
        input.append('\n');
      }
      return Transliterator.createFromRules(ID, input.toString(), Transliterator.FORWARD);
    } catch (IOException e) {
      return null;
    }
  }
}
