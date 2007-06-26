package org.unicode.cldr.test;

import org.unicode.cldr.test.CheckCLDR.CheckStatus;
import org.unicode.cldr.test.CheckCLDR.FormatDemo;
import org.unicode.cldr.test.CheckCLDR.Phase;
import org.unicode.cldr.test.CheckCLDR.SimpleDemo;
import org.unicode.cldr.test.CoverageLevel.Level;
import org.unicode.cldr.util.CLDRFile;
import org.unicode.cldr.util.Counter;
import org.unicode.cldr.util.LocaleIDParser;
import org.unicode.cldr.util.PrettyPath;
import org.unicode.cldr.util.StandardCodes;
import org.unicode.cldr.util.Utility;
import org.unicode.cldr.util.XMLSource;
import org.unicode.cldr.util.CLDRFile.Factory;

import com.ibm.icu.dev.tool.UOption;
import com.ibm.icu.text.UnicodeSet;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Console test for CheckCLDR.
 * <br> Some common source directories:
 * 
 * <pre>
 *  -s C:/cvsdata/unicode/cldr/incoming/vetted/main
 *  -s C:/cvsdata/unicode/cldr/incoming/proposed/main
 *  -s C:/cvsdata/unicode/cldr/incoming/proposed/main
 *  -s C:/cvsdata/unicode/cldr/testdata/main
 * </pre>
 * @author markdavis
 *
 */
public class ConsoleCheckCLDR {
  public static boolean showStackTrace = false;
  public static boolean errorsOnly = false;
  static boolean SHOW_LOCALE = true;
  static boolean SHOW_EXAMPLES = false;

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
  USER = 14,
  PHASE = 15
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
    UOption.create("phase", 'z',  UOption.REQUIRES_ARG),
  };
  
  private static String[] HelpMessage = {
    "-h \t This message",
    "-s \t Source directory, default = " + Utility.MAIN_DIRECTORY,
    "-fxxx \t Pick the locales (files) to check: xxx is a regular expression, eg -f fr, or -f fr.*, or -f (fr|en-.*)",
    "-pxxx \t Pick the paths to check, eg -p(.*languages.*)",
    "-cxxx \t Set the coverage: eg -c comprehensive or -c modern or -c moderate or -c basic",
    "-txxx \t Filter the Checks: xxx is a regular expression, eg -t.*number.*. To check all BUT a given test, use the style -t ((?!.*CheckZones).*)",
    "-oxxx \t Organization: ibm, google, ....; filters locales and uses Locales.txt for coverage tests",
    "-x \t Turn on examples (actually a summary of the demo)",
    "-d \t Turn on special date format checks",
    "-A \t Show all paths",
    "-e \t Show errors only (with -ef, only final processing errors)",
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
    if ("f".equals(options[ERRORS_ONLY].value)) {
      CheckCLDR.finalErrorType = CheckStatus.warningType;
    }
    
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
    
    Phase phase = Phase.VETTING;
    if (options[PHASE].doesOccur) {
      try {
        phase = Phase.valueOf(options[PHASE].value);
      } catch (RuntimeException e) {
        throw (IllegalArgumentException) new IllegalArgumentException("Incorrect Phase value: should be one of " + Arrays.asList(Phase.values())).initCause(e);
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
    System.out.println("phase: " + phase);
    System.out.println("coverage level: " + coverageLevel);
    System.out.println("checking dates: " + checkFlexibleDates);
    System.out.println("only check-on-submit: " + checkOnSubmit);
    System.out.println("show all: " + showAll);
    System.out.println("errors only?: " + errorsOnly);
    
    // set up the test
    Factory cldrFactory = CLDRFile.Factory.make(sourceDirectory, factoryFilter);
    CheckCLDR checkCldr = CheckCLDR.getCheckAll(checkFilter);
    checkCldr.setDisplayInformation(cldrFactory.make("en", true));
    PathShower pathShower = new PathShower();
    
    // call on the files
    Set locales = cldrFactory.getAvailable();
    List result = new ArrayList();
    Set<String> paths = new TreeSet<String>(); // CLDRFile.ldmlComparator);
    Map m = new TreeMap();
    //double testNumber = 0;
    Map<String,String> options = new HashMap<String,String>();
    Counter totalCount = new Counter();
    Counter subtotalCount = new Counter();
    FlexibleDateFromCLDR fset = new FlexibleDateFromCLDR();
    Set<String> englishPaths = null;
    
    PrettyPath prettyPathMaker = new PrettyPath();
    
    Set<String> fatalErrors = new TreeSet<String>();
    
    if (SHOW_LOCALE) System.out.println("Locale\tStatus\tCode\tEng.Value\tEng.Ex.\tLoc.Value\tLoc.Ex\tError/Warning\tPath");

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
      options.put("phase", phase.toString());
      //options.put("SHOW_TIMES", "");

      if (SHOW_LOCALE) System.out.println();
      
      //options.put("CheckCoverage.requiredLevel","comprehensive");
      
      CLDRFile file;
      try {
        file = cldrFactory.make(localeID, isLanguageLocale);
      } catch (RuntimeException e) {
        fatalErrors.add(localeID);
        System.out.println("FATAL ERROR: " + localeID);
        e.printStackTrace(System.out);
        continue;
      }
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
        subtotalCount.add(status.getType(), 1);
      }
      paths.clear();
      //CollectionUtilities.addAll(file.iterator(pathFilter), paths);
      addPrettyPaths(file, pathFilter, prettyPathMaker, noaliases, false, paths);
      
      // also add the English paths
      //CollectionUtilities.addAll(checkCldr.getDisplayInformation().iterator(pathFilter), paths);
      // initialize the first time in.
      if (englishPaths == null) {
        englishPaths = new HashSet<String>();
        addPrettyPaths(checkCldr.getDisplayInformation(), pathFilter, prettyPathMaker, noaliases, true, englishPaths);
        englishPaths = Collections.unmodifiableSet(englishPaths); // for robustness
      }
      paths.addAll(englishPaths);
      
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
      
      // only create if we are going to use
      ExampleGenerator exampleGenerator = SHOW_EXAMPLES ? new ExampleGenerator(file, Utility.SUPPLEMENTAL_DIRECTORY) : null;
      
      //Status pathStatus = new Status();
      int pathCount = 0;
      
      for (Iterator it2 = paths.iterator(); it2.hasNext();) {
        pathCount++;
        String prettyPath = (String) it2.next();
        String path = prettyPathMaker.getOriginal(prettyPath);
        if (path == null) {
          prettyPathMaker.getOriginal(prettyPath);
        }
        String value = file.getStringValue(path);
//        if (value == null) {
//          value = file.getStringValue(path);
//        }
        String fullPath = file.getFullXPath(path);

        String example = "";

        if (SHOW_EXAMPLES) {
          example = exampleGenerator.getExampleHtml(path, value, ExampleGenerator.Zoomed.OUT);
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
            showValue(prettyPath, localeID, example, path, value, fullPath, statusString);

            subtotalCount.add(status.getType(), 1);
            totalCount.add(status.getType(), 1);
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
          String prettyPath = prettyPathMaker.getPrettyPath(path, false);
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
    if (fatalErrors.size() != 0) {
      System.out.println("FATAL ERRORS:" );
    }
  }
  
  private static void showSummary(CheckCLDR checkCldr, String localeID, Level level, String value) {
    System.out.println(getLocaleAndName(localeID) + "\tSummary\t" + level + "\t" + value);
  }

  private static void showExamples(CheckCLDR checkCldr, String prettyPath, String localeID, ExampleGenerator exampleGenerator, String path, String value, String fullPath, String example) {
    if (example != null) {
      showValue(prettyPath, localeID, example, path, value, fullPath, "ok");
    }
    String longExample = exampleGenerator.getExampleHtml(path, value, ExampleGenerator.Zoomed.IN);
    if (longExample != null && !longExample.equals(example)) {
      showValue(prettyPath, localeID, longExample, path, value, fullPath, "ok-in");
    }
    String help = exampleGenerator.getHelpHtml(path, value);
    if (help != null) {
      showValue(prettyPath, localeID, help, path, value, fullPath, "ok-help");
    }
  }

  private static void addPrettyPaths(CLDRFile file, Matcher pathFilter, PrettyPath prettyPathMaker, boolean noaliases, boolean filterDraft, Collection<String> target) {
//  Status pathStatus = new Status();
  for (Iterator<String> pit = file.iterator(pathFilter); pit.hasNext();) {
    String path = pit.next();
    if (noaliases && XMLSource.Alias.isAliasPath(path)) { // this is just for console testing, the survey tool shouldn't do it.
        continue;
//      file.getSourceLocaleID(path, pathStatus);
//      if (!path.equals(pathStatus.pathWhereFound)) {
//        continue;
//      }
    }
    if (filterDraft) {
      String newPath = CLDRFile.getNondraftNonaltXPath(path);
      if (!newPath.equals(path)) {
        String value = file.getStringValue(newPath);
        if (value != null) {
          continue;
        }
      }
    }
    String prettyPath = prettyPathMaker.getPrettyPath(path, true); // get sortable version
    target.add(prettyPath);
  }
}
  public static synchronized void setDisplayInformation(CLDRFile inputDisplayInformation, ExampleGenerator inputExampleGenerator) {
    CheckCLDR.setDisplayInformation(inputDisplayInformation);
    englishExampleGenerator = inputExampleGenerator;
  }
  public static synchronized void setExampleGenerator(ExampleGenerator inputExampleGenerator) {
    englishExampleGenerator = inputExampleGenerator;
 }
  public static synchronized ExampleGenerator getExampleGenerator() {
    return englishExampleGenerator;
 }

  private static ExampleGenerator englishExampleGenerator;
  
  private static void showValue(String prettyPath, String localeID, String example, String path, String value, String fullPath, String statusString) {
    example = example == null ? "" : "<" + example + ">";
    String englishExample = null;
    if (SHOW_EXAMPLES) {
      if (getExampleGenerator() == null) {
        setExampleGenerator(new ExampleGenerator(CheckCLDR.getDisplayInformation(), Utility.SUPPLEMENTAL_DIRECTORY));
      }
      englishExample = getExampleGenerator().getExampleHtml(path, getEnglishPathValue(path), ExampleGenerator.Zoomed.OUT);
    }
    englishExample = englishExample == null ? "" : "<" + englishExample + ">";
    String shortStatus = statusString.equals("ok") ? "ok" : statusString.startsWith("Warning") ? "warn" : statusString.startsWith("Error") ? "err" : "???";
    System.out.println(getLocaleAndName(localeID)
        + "\t" + shortStatus
        + "\t" + prettyPath
        + "\t" + getEnglishPathValue(path)
        + "\t" + englishExample
        + "\t" + value
        + "\t" + example
        + "\t" + statusString
        + "\t" + fullPath
        );
  }

  public static class PathShower {
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
  private static void showValue(String path, String value, boolean showEnglish, String localeID) {
    System.out.println( "\tValue:\t" + value + (showEnglish ? "\t" + getEnglishPathValue(path) : "") + "\tLocale:\t" + localeID);
  }

  private static String getEnglishPathValue(String path) {
    String englishValue = CheckCLDR.getDisplayInformation().getWinningValue(path);
    if (englishValue == null) {
      String path2 = CLDRFile.getNondraftNonaltXPath(path);
      englishValue = CheckCLDR.getDisplayInformation().getWinningValue(path2);
    }
    return englishValue;
  }
  
  /**
   * Utility for getting information.
   * @param locale
   * @return
   */
  public static String getLocaleAndName(String locale) {
    String localizedName = CheckCLDR.getDisplayInformation().getName(locale, false);
    if (localizedName == null || localizedName.equals(locale)) return locale;
    return locale + " [" + localizedName + "]";
  }
}