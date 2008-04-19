package org.unicode.cldr.test;

import org.unicode.cldr.test.CheckCLDR.CheckStatus;
import org.unicode.cldr.test.CheckCLDR.FormatDemo;
import org.unicode.cldr.test.CheckCLDR.Phase;
import org.unicode.cldr.test.CheckCLDR.SimpleDemo;
import org.unicode.cldr.test.CoverageLevel.Level;
import org.unicode.cldr.test.ExampleGenerator.ExampleContext;
import org.unicode.cldr.test.ExampleGenerator.ExampleType;
import org.unicode.cldr.test.ExampleGenerator.Zoomed;
import org.unicode.cldr.tool.TablePrinter;
import org.unicode.cldr.util.CLDRFile;
import org.unicode.cldr.util.Counter;
import org.unicode.cldr.util.LanguageTagParser;
import org.unicode.cldr.util.LocaleIDParser;
import org.unicode.cldr.util.Pair;
import org.unicode.cldr.util.PathUtilities;
import org.unicode.cldr.util.PrettyPath;
import org.unicode.cldr.util.StandardCodes;
import org.unicode.cldr.util.SupplementalDataInfo;
import org.unicode.cldr.util.Utility;
import org.unicode.cldr.util.XMLSource;
import org.unicode.cldr.util.CLDRFile.Factory;
import org.unicode.cldr.util.CLDRFile.Status;

import com.ibm.icu.dev.test.util.BagFormatter;
import com.ibm.icu.dev.test.util.ElapsedTimer;
import com.ibm.icu.dev.test.util.TransliteratorUtilities;
import com.ibm.icu.dev.tool.UOption;
import com.ibm.icu.lang.UCharacter;
import com.ibm.icu.text.UnicodeSet;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
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
  static Zoomed SHOW_EXAMPLES = null;
  static PrintWriter generated_html = null;
  static PrintWriter generated_html_count = null;
  static  PrettyPath prettyPathMaker = new PrettyPath();
  static  String generated_html_directory = null;

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
  PHASE = 15,
  GENERATE_HTML = 16
  ;
  
  private static final UOption[] options = {
    UOption.HELP_H(),
    UOption.HELP_QUESTION_MARK(),
    UOption.create("coverage", 'c', UOption.REQUIRES_ARG),
    UOption.create("examples", 'x', UOption.OPTIONAL_ARG),
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
    UOption.create("generate_html", 'g',  UOption.OPTIONAL_ARG),
  };
  private static final Comparator<String> baseFirstCollator = new Comparator<String>() {
    LanguageTagParser languageTagParser1 = new LanguageTagParser();
    LanguageTagParser languageTagParser2 = new LanguageTagParser();
    
    public int compare(String o1, String o2) {
      String ls1 = languageTagParser1.set(o1).getLanguageScript();
      String ls2 = languageTagParser2.set(o2).getLanguageScript();
      int result = ls1.compareTo(ls2);
      if (result != 0) return result;
      return o1.compareTo(o2);
    }
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
    "-a \t Show all paths",
    "-e \t Show errors only (with -ef, only final processing errors)",
    "-n \t No aliases",
    "-u \t User, eg -uu148",
  };
  
  static ErrorCount subtotalCount = new ErrorCount();
  static ErrorCount totalCount = new ErrorCount();

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
    ElapsedTimer totalTimer = new ElapsedTimer();
    Utility.showOptions(args);
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
//    if ("f".equals(options[ERRORS_ONLY].value)) {
//      CheckCLDR.finalErrorType = CheckStatus.warningType;
//    }
    
    SHOW_EXAMPLES = !options[EXAMPLES].doesOccur ? null
            : options[EXAMPLES].value == null ? Zoomed.OUT
                    : Zoomed.valueOf(options[EXAMPLES].value.toUpperCase());
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
    
    Phase phase = Phase.SUBMISSION;
    if (options[PHASE].doesOccur) {
      try {
        phase = Phase.valueOf(options[PHASE].value.toUpperCase());
      } catch (RuntimeException e) {
        throw (IllegalArgumentException) new IllegalArgumentException("Incorrect Phase value: should be one of " + Arrays.asList(Phase.values())).initCause(e);
      }
    }
    
    if (options[GENERATE_HTML].doesOccur) {
      coverageLevel = Level.MODERN; // reset
      generated_html_directory = options[GENERATE_HTML].value;
      if (generated_html_directory == null) {
        generated_html_directory = Utility.GEN_DIRECTORY + "errors/";
      }
      generated_html_count = BagFormatter.openUTF8Writer(generated_html_directory, "count.txt");
      //PrintWriter cssFile = BagFormatter.openUTF8Writer(generated_html_directory, "index.css");
      //Utility;
    }
    
    // check stuff
//  Comparator cc = StandardCodes.make().getTZIDComparator();
//  System.out.println(cc.compare("Antarctica/Rothera", "America/Cordoba"));
//  System.out.println(cc.compare("Antarctica/Rothera", "America/Indianapolis"));
    
    
    String sourceDirectory = checkValidDirectory(options[SOURCE_DIRECTORY].value, "Fix with -s. Use -h for help.");

    String user = options[USER].value;
    
    System.out.println("source directory: " + sourceDirectory + "\t(" + new File(sourceDirectory).getCanonicalPath() + ")");
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
    System.out.println("generate html: " + generated_html_directory);
    
    // set up the test
    Factory cldrFactory = CLDRFile.Factory.make(sourceDirectory, factoryFilter);
    CheckCLDR checkCldr = CheckCLDR.getCheckAll(checkFilter);
    checkCldr.setDisplayInformation(cldrFactory.make("en", true));
    PathShower pathShower = new PathShower();
    
    // call on the files
    Set locales = new TreeSet(baseFirstCollator);
    locales.addAll(cldrFactory.getAvailable());
    
    List result = new ArrayList();
    Set<String> paths = new TreeSet<String>(); // CLDRFile.ldmlComparator);
    Map m = new TreeMap();
    //double testNumber = 0;
    Map<String,String> options = new HashMap<String,String>();
    FlexibleDateFromCLDR fset = new FlexibleDateFromCLDR();
    Set<String> englishPaths = null;
    
    
    Set<String> fatalErrors = new TreeSet<String>();
    
    if (SHOW_LOCALE) System.out.println("Locale\tStatus\tCode\tEng.Value\tEng.Ex.\tLoc.Value\tLoc.Ex\tError/Warning\tPath");
    
    SupplementalDataInfo supplementalDataInfo = SupplementalDataInfo.getInstance(Utility.SUPPLEMENTAL_DIRECTORY);

    LocaleIDParser localeIDParser = new LocaleIDParser();
    String lastBaseLanguage = "";
    
    for (Iterator it = locales.iterator(); it.hasNext();) {
      String localeID = (String) it.next();
      if (CLDRFile.isSupplementalName(localeID)) continue;
      if (supplementalDataInfo.getDefaultContentLocales().contains(localeID)) {
        System.out.println("Skipping default content locale: " + localeID);
        continue;
      }
      
      boolean isLanguageLocale = localeID.equals(localeIDParser.set(localeID).getLanguageScript());
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
      
      if (coverageLevel != null) options.put("CoverageLevel.requiredLevel", coverageLevel.toString());
      if (organization != null) options.put("CoverageLevel.localeType", organization);
      options.put("phase", phase.toString());
      //options.put("SHOW_TIMES", "");

      if (SHOW_LOCALE) System.out.println();
      
      //options.put("CheckCoverage.requiredLevel","comprehensive");
      
      CLDRFile file;
      CLDRFile parent = null;
      
      ElapsedTimer timer = new ElapsedTimer();
      try {
        file = cldrFactory.make(localeID, true);
        final String parentID = file.getParent(localeID);
        if (parentID != null) {
          parent = cldrFactory.make(parentID, true);
        }
      } catch (RuntimeException e) {
        fatalErrors.add(localeID);
        System.out.println("FATAL ERROR: " + localeID);
        e.printStackTrace(System.out);
        continue;
      }
      
      // generate HTML if asked for
      if (generated_html_directory != null) {
        String baseLanguage = localeIDParser.set(localeID).getLanguageScript();

        if (!baseLanguage.equals(lastBaseLanguage)) {
          lastBaseLanguage = baseLanguage;
          openGeneratedHtml(localeID, baseLanguage);
        }

      }
      
      if (user != null) {
        file = new CLDRFile.TestUser(file, user, isLanguageLocale);
        if (parent != null) {
          parent = new CLDRFile.TestUser(parent, user, isLanguageLocale);
        }
      }
      checkCldr.setCldrFileToCheck(file, options, result);
      
      subtotalCount.clear();
      
      for (Iterator it3 = result.iterator(); it3.hasNext();) {
        CheckStatus status = (CheckStatus) it3.next();
        String statusString = status.toString(); // com.ibm.icu.impl.Utility.escape(
        String statusType = status.getType();
        
        if (errorsOnly) {
          if (!statusType.equals(status.errorType)) continue;
        }
        if (checkOnSubmit) {
          if (!status.isCheckOnSubmit() || !statusType.equals(status.errorType)) continue;
        }
        showValue(file, null, localeID, null, null, null, null, statusString, null);
        // showSummary(checkCldr, localeID, level, statusString);
      }
      paths.clear();
      //CollectionUtilities.addAll(file.iterator(pathFilter), paths);
      addPrettyPaths(file, pathFilter, prettyPathMaker, noaliases, false, paths);
      addPrettyPaths(file, file.getExtraPaths(), pathFilter, prettyPathMaker, noaliases, false, paths);
      
      // also add the English paths
      //CollectionUtilities.addAll(checkCldr.getDisplayInformation().iterator(pathFilter), paths);
      // initialize the first time in.
      if (englishPaths == null) {
        englishPaths = new HashSet<String>();
        final CLDRFile displayFile = checkCldr.getDisplayInformation();
        addPrettyPaths(displayFile, pathFilter, prettyPathMaker, noaliases, true, englishPaths);
        addPrettyPaths(displayFile, displayFile.getExtraPaths(), pathFilter, prettyPathMaker, noaliases, true, englishPaths);
        englishPaths = Collections.unmodifiableSet(englishPaths); // for robustness
      }
      // paths.addAll(englishPaths);
      
      UnicodeSet missingExemplars = new UnicodeSet();
      if (checkFlexibleDates) {
        fset.set(file);
      }
      pathShower.set(localeID);
      
      // only create if we are going to use
      ExampleGenerator exampleGenerator = SHOW_EXAMPLES != null ? new ExampleGenerator(file, Utility.SUPPLEMENTAL_DIRECTORY) : null;
      ExampleContext exampleContext = new ExampleContext();
      
      //Status pathStatus = new Status();
      int pathCount = 0;
      Status otherPath = new Status();
      
      for (Iterator it2 = paths.iterator(); it2.hasNext();) {
        pathCount++;
        String prettyPath = (String) it2.next();
        String path = prettyPathMaker.getOriginal(prettyPath);
        if (path == null) {
          prettyPathMaker.getOriginal(prettyPath);
        }
        
        if (!file.isWinningPath(path)) {
          continue;
        }
        final String sourceLocaleID = file.getSourceLocaleID(path, otherPath);
        if (!isLanguageLocale) {
          if (!localeID.equals(sourceLocaleID)) {
            continue;
          }
          // also skip aliases
          if (!path.equals(otherPath.pathWhereFound)) {
            continue;
          }
        }
        
        if (path.contains("@alt")) {
            if (path.contains("proposed")) continue;
        }
        String value = file.getStringValue(path);
        String fullPath = file.getFullXPath(path);


        String example = "";

        if (SHOW_EXAMPLES != null) {
          example = exampleGenerator.getExampleHtml(path, value, ExampleGenerator.Zoomed.OUT, exampleContext, ExampleType.NATIVE);
          showExamples(checkCldr, prettyPath, localeID, exampleGenerator, path, value, fullPath, example, exampleContext);
          //continue; // don't show problems
        }

        if (checkFlexibleDates) {
          fset.checkFlexibles(path, value, fullPath);
        }
        
        int limit = 1;
        if (SHOW_EXAMPLES == Zoomed.IN) limit = 2;
        for (int jj = 0; jj < limit; ++jj) {
          if (jj == 0) {
            checkCldr.check(path, fullPath, value, options, result);
          } else {
            checkCldr.getExamples(path, fullPath, value, options, result);
          }
          
          boolean showedOne = false;
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
            showValue(file, prettyPath, localeID, example, path, value, fullPath, statusString, exampleContext);
            showedOne = true;

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
          if (!showedOne) {
            if (fullPath != null && draftStatusMatcher.reset(fullPath).find() && localeID.equals(sourceLocaleID) && path.equals(otherPath.pathWhereFound)) {
              final String draftStatus = draftStatusMatcher.group(1);
              // see if value is same as parents, then skip
              String parentValue = parent == null ? null : parent.getStringValue(path);
              if (parentValue == null || !parentValue.equals(value)) {
                showValue(file, prettyPath, localeID, example, path, value, fullPath, draftStatus, exampleContext);
                showedOne = true;
              }
            }
            if (!showedOne && showAll) {
              showValue(file, prettyPath, localeID, example, path, value, fullPath, "ok", exampleContext);
              //pathShower.showHeader(path, value);
            }
          }

        }
      }
      showSummary(checkCldr, localeID, level, "Items (including inherited):\t" + pathCount);
      if (missingExemplars.size() != 0) {
        showSummary(checkCldr, localeID, level, "Total missing:\t" + missingExemplars);
      }
      for (ErrorType type : subtotalCount.keySet()) {
        showSummary(checkCldr, localeID, level, "Subtotal " + type + ":\t" + subtotalCount.getCount(type));
      }
      if (checkFlexibleDates) {
        fset.showFlexibles();
      }
      if (SHOW_EXAMPLES != null) {
//      ldml/dates/timeZoneNames/zone[@type="America/Argentina/San_Juan"]/exemplarCity
        for (String zone : StandardCodes.make().getGoodAvailableCodes("tzid")) {
          String path = "//ldml/dates/timeZoneNames/zone[@type=\"" + zone + "\"]/exemplarCity";
          String prettyPath = prettyPathMaker.getPrettyPath(path, false);
          if (pathFilter != null && !pathFilter.reset(path).matches()) continue;
          String fullPath = file.getStringValue(path);
          if (fullPath != null) continue;
          String example = exampleGenerator.getExampleHtml(path, null, ExampleGenerator.Zoomed.OUT, exampleContext, ExampleType.NATIVE);
          showExamples(checkCldr, prettyPath, localeID, exampleGenerator, path, null, fullPath, example, exampleContext);
        }
      }
      System.out.println("Elapsed time: " + timer);
      System.out.flush();
    }
    
    if (generated_html != null) {
      closeGeneratedHtml();
    }
    
    if (generated_html_directory != null) {
      writeErrorCountsText();

      PrintWriter generated_html_index = BagFormatter.openUTF8Writer(generated_html_directory, "index.html");
      showIndexHead(generated_html_index);
      showSummaryTable(generated_html_index);
      generated_html_index.close();
    }
    for (ErrorType type : totalCount.keySet()) {
      System.out.println("Total " + type + ":\t" + totalCount.getCount(type));
    }
    
    System.out.println("Total Elapsed: " + totalTimer);
    if (fatalErrors.size() != 0) {
      System.out.println("FATAL ERRORS:" );
    }
  }

//  private static void showLocaleTable(PrintWriter generated_html_index, ErrorCount counts) {
//    generated_html_index.println("<table  border='1' style='border-collapse: collapse' bordercolor='blue'>"); 
//
//    TablePrinter indexTablePrinter = new TablePrinter();
//    for (ErrorType type : ErrorType.toShow) {
//      String columnTitle = UCharacter.toTitleCase(type.toString(), null);
//      if (ErrorType.coverage.contains(type)) {
//        columnTitle = "Missing Coverage: " + columnTitle;
//      } else if (ErrorType.unapproved.contains(type)) {
//        columnTitle = "Missing Votes: " + columnTitle;
//      }
//      indexTablePrinter.addColumn(columnTitle).setCellAttributes("align='right'");
//    }
//    indexTablePrinter.addRow();
//    for (ErrorType type : ErrorType.toShow) {
//      indexTablePrinter.addCell(counts.getCount(type));
//    }
//    indexTablePrinter.finishRow();
//    generated_html_index.println(indexTablePrinter.toTable());
//    generated_html_index.println("</table></html>");
//  }

  
  private static void showSummaryTable(PrintWriter generated_html_index) {
    TablePrinter indexTablePrinter = new TablePrinter()
      .setTableAttributes("border='1' style='border-collapse: collapse' bordercolor='blue'")
      .addColumn("BASE").setRepeatHeader(true).setHidden(true)
      .addColumn("Locale")
      .addColumn("Name");
    for (ErrorType type : ErrorType.toShow) {
      String columnTitle = UCharacter.toTitleCase(type.toString(), null);
      if (ErrorType.coverage.contains(type)) {
        columnTitle = "Missing Coverage: " + columnTitle;
      } else if (ErrorType.unapproved.contains(type)) {
        columnTitle = "Missing Votes: " + columnTitle;
      }
      indexTablePrinter.addColumn(columnTitle).setCellAttributes("align='right'");
    }
    LanguageTagParser ltp = new LanguageTagParser();
    for (String key : sortedHtmlIndexLines.keySet()) {
      Pair<String,ErrorCount> pair = sortedHtmlIndexLines.get(key);
      String htmlOpenedFileLanguage = pair.getFirst();
      ErrorCount counts = pair.getSecond();
      if (counts.total() == 0) {
        continue;
      }
      final String baseLanguage = ltp.set(htmlOpenedFileLanguage).getLanguage();
      indexTablePrinter.addRow()
      .addCell(baseLanguage)
      .addCell("<a href='" + baseLanguage + ".html'>" + htmlOpenedFileLanguage + "</a>")
      .addCell(getLocaleName(htmlOpenedFileLanguage));
      for (ErrorType type : ErrorType.toShow) {
        indexTablePrinter.addCell(counts.getCount(type));
      }
      indexTablePrinter.finishRow();
    }      
    generated_html_index.println(indexTablePrinter.toTable());
    generated_html_index.println("</html>");
  }

  static Matcher draftStatusMatcher = Pattern.compile("\\[@draft=\"([^\"]*)\"]").matcher("");
  
  private static String checkValidDirectory(String sourceDirectory, String correction) {
	  File temp = new File(sourceDirectory);
	  String canonicalPath = null;
	  try {
		  canonicalPath = temp.getCanonicalPath();
	  } catch (IOException e) {
	  }
	  if (!temp.isDirectory() || canonicalPath == null) {
		  throw new RuntimeException("Directory not found: " + sourceDirectory + (canonicalPath == null ? "" : " => " + canonicalPath) 
				  + "\r\n" + correction);
	  }
	  return canonicalPath;
  }

private static void showIndexHead(PrintWriter generated_html_index) {
    generated_html_index.println("<html>" +
        "<head><meta http-equiv='Content-Type' content='text/html; charset=utf-8'>" +
        "<title>Error Report Index</title></head>" +
        "<link rel='stylesheet' href='errors.css' type='text/css'>" +
        "<body>" +
        "<h1>Error Report Index</h1>" +
        "<p>The following errors have been detected in the locales. " +
        "Please review and correct them.</p>" +
        "<p><i>This list is only generated daily, and so may not reflect fixes you have made until tomorrow. " +
        "(There were production problems in integrating it fully into the Survey tool. " +
        "However, it should let you see the problems and make sure that they get taken care of.)</i></p>");
  }

  enum ErrorType {
    ok,
    error, warning, 
    contributed, provisional, unconfirmed, 
    posix, minimal, basic, moderate, modern, comprehensive, optional,
    unknown;
    static EnumSet<ErrorType> unapproved = EnumSet.range(ErrorType.contributed, ErrorType.unconfirmed);
    static EnumSet<ErrorType> coverage = EnumSet.range(ErrorType.posix, ErrorType.optional);
    static EnumSet<ErrorType> toShow = EnumSet.complementOf(EnumSet.of(
            ErrorType.ok, ErrorType.contributed, ErrorType.posix, ErrorType.comprehensive, ErrorType.optional, ErrorType.unknown));
    
    static ErrorType fromStatusString(String statusString) {
      ErrorType shortStatus = statusString.equals("ok") ? ErrorType.ok 
              : statusString.startsWith("Error") ? ErrorType.error 
                      : statusString.startsWith("Warning") ? ErrorType.warning 
                              : statusString.equals("contributed") ? ErrorType.contributed 
                                      : statusString.equals("provisional") ? ErrorType.provisional 
                                              : statusString.equals("unconfirmed") ? ErrorType.unconfirmed 
                                                      : ErrorType.unknown ;
      if (shortStatus == ErrorType.unknown) {
        throw new IllegalArgumentException("Unknown error type: " + statusString);
      } else if (shortStatus == ErrorType.warning) {
        if (coverageMatcher.reset(statusString).find()) {
          shortStatus = ErrorType.valueOf(coverageMatcher.group(1));
        }
      }
      return shortStatus;
    }
  };
  
  static class ErrorCount implements Comparable<ErrorCount> {
    private Counter<ErrorType> counter = new Counter<ErrorType>();

    public int compareTo(ErrorCount o) {
      // we don't really need a good comparison - aren't going to be sorting
      return total() < o.total() ? -1 : total() > o.total() ? 1 : 0;
    }
    public long total() {
      return counter.getTotal();
    }
    public void clear() {
      counter.clear();
    }
    public Set<ErrorType> keySet() {
      return counter.getKeysetSortedByKey();
    }
    public long getCount(ErrorType input) {
      return counter.getCount(input);
    }
    public void increment(ErrorType errorType) {
      counter.add(errorType, 1);
    }
  }
  
  private static ErrorCount htmlErrorsPerBaseLanguage = new ErrorCount();
  private static String htmlOpenedFileLanguage = null;
  private static TreeMap<String, Pair<String,ErrorCount>> sortedHtmlIndexLines = new TreeMap();  
  
  private static void closeGeneratedHtml() {
    //generated_html.println("<table border='1' style='border-collapse: collapse' bordercolor='#CCCCFF'>");
    // Locale Group Error Warning Missing Votes: Contributed  Missing Votes: Provisional  Missing Votes: Unconfirmed  Missing Coverage: Posix Missing Coverage: Minimal Missing Coverage: Basic Missing Coverage: Moderate  Missing Coverage: Modern
    generated_html.println(generated_html_table.toTable());
    generated_html.println("</body></html>");
    generated_html.close();
    generated_html_table = null;
  }

  private static void openGeneratedHtml(String localeID, String baseLanguage) throws IOException {
    if (generated_html != null) {
      closeGeneratedHtml();
    }
    generated_html = BagFormatter.openUTF8Writer(generated_html_directory, baseLanguage + ".html");
    generated_html_table = new TablePrinter();
    showLineHeaders(generated_html_table);
    //showLineHeaders(generated_html_table);

    htmlOpenedFileLanguage = baseLanguage;
    generated_html.println("<html>" +
        "<head><meta http-equiv='Content-Type' content='text/html; charset=utf-8'>" +
        "<title>Errors in " + getNameAndLocale(localeID, false) + "</title></head>" +
        "<link rel='stylesheet' href='errors.css' type='text/css'>" +
        "<body>" +
        "<h1>Errors in " + getNameAndLocale(localeID, false) + "</h1>" +
        "<p><a href='index.html'>Index</a></p>" +
        "<p>The following errors have been detected in the locale " + getNameAndLocale(localeID, false) + ". " +
            "Please review and correct them. " +
            "Note that errors in <i>sublocales</i> are often fixed by fixing the main locale.</p>" +
            "<p><i>This list is only generated daily, and so may not reflect fixes you have made until tomorrow. " +
            "(There were production problems in integrating it fully into the Survey tool. " +
            "However, it should let you see the problems and make sure that they get taken care of.)</i></p>"); 
  }

//  private static void startGeneratedTable(PrintWriter output, TablePrinter table) {
//    showLineHeaders(table);
//  }
  
  private static void showSummary(CheckCLDR checkCldr, String localeID, Level level, String value) {
    String line = getLocaleAndName(localeID) + "\tSummary\t" + level + "\t" + value;
    System.out.println(line);
//    if (generated_html != null) {
//      line = TransliteratorUtilities.toHTML.transform(line);
//      line = line.replace("\t", "</td><td>");
//      generated_html.println("<table><tr><td>" + line + "</td></tr></table>");
//    }
  }

  private static void showExamples(CheckCLDR checkCldr, String prettyPath, String localeID, ExampleGenerator exampleGenerator, String path, String value, String fullPath, String example, ExampleContext exampleContext) {
//    if (example != null) {
//      showValue(prettyPath, localeID, example, path, value, fullPath, "ok", exampleContext);
//    }
    if (SHOW_EXAMPLES == Zoomed.IN) {
      String longExample = exampleGenerator.getExampleHtml(path, value, ExampleGenerator.Zoomed.IN, exampleContext, ExampleType.NATIVE);
      if (longExample != null && !longExample.equals(example)) {
        showValue(checkCldr.getCldrFileToCheck(), prettyPath, localeID, longExample, path, value, fullPath, "ok-in", exampleContext);
      }
      String help = exampleGenerator.getHelpHtml(path, value);
      if (help != null) {
        showValue(checkCldr.getCldrFileToCheck(), prettyPath, localeID, help, path, value, fullPath, "ok-help", exampleContext);
      }
    }
  }

  private static void addPrettyPaths(CLDRFile file, Matcher pathFilter, PrettyPath prettyPathMaker, boolean noaliases, boolean filterDraft, Collection<String> target) {
//  Status pathStatus = new Status();
    for (Iterator<String> pit = file.iterator(pathFilter); pit.hasNext();) {
      String path = pit.next();
      if (file.isPathExcludedForSurvey(path)) {
        continue;
      }
      addPrettyPath(file, prettyPathMaker, noaliases, filterDraft, target, path);
    }
  }

  private static void addPrettyPaths(CLDRFile file, Collection<String> paths, Matcher pathFilter, PrettyPath prettyPathMaker, boolean noaliases, boolean filterDraft, Collection<String> target) {
//  Status pathStatus = new Status();
    for (String path : paths) {
      if (pathFilter != null && !pathFilter.reset(path).matches()) continue;
      addPrettyPath(file, prettyPathMaker, noaliases, filterDraft, target, path);
    }
  }

  private static void addPrettyPath(CLDRFile file, PrettyPath prettyPathMaker, boolean noaliases,
          boolean filterDraft, Collection<String> target, String path) {
    if (noaliases && XMLSource.Alias.isAliasPath(path)) { // this is just for console testing, the survey tool shouldn't do it.
      return;
//    file.getSourceLocaleID(path, pathStatus);
//    if (!path.equals(pathStatus.pathWhereFound)) {
//    continue;
//    }
    }
    if (filterDraft) {
      String newPath = CLDRFile.getNondraftNonaltXPath(path);
      if (!newPath.equals(path)) {
        String value = file.getStringValue(newPath);
        if (value != null) {
          return;
        }
      }
    }
    String prettyPath = prettyPathMaker.getPrettyPath(path, true); // get sortable version
    target.add(prettyPath);
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
  private static Object lastLocaleID = null;
  
  static Matcher coverageMatcher = Pattern.compile("meet ([a-z]*) coverage").matcher(""); // HACK TODO fix
  
  private static void showValue(CLDRFile cldrFile, String prettyPath, String localeID, String example, String path, String value, String fullPath, String statusString, ExampleContext exampleContext) {
    ErrorType shortStatus = ErrorType.fromStatusString(statusString);
    subtotalCount.increment(shortStatus);
    totalCount.increment(shortStatus);
    
    if (generated_html == null) {
      example = example == null ? "" : example;
      String englishExample = null;
      final String englishPathValue = path == null ? null : getEnglishPathValue(path);
      if (SHOW_EXAMPLES != null && path != null) {
        if (getExampleGenerator() == null) {
          setExampleGenerator(new ExampleGenerator(CheckCLDR.getDisplayInformation(), Utility.SUPPLEMENTAL_DIRECTORY));
        }
        englishExample = getExampleGenerator().getExampleHtml(path, englishPathValue, ExampleGenerator.Zoomed.OUT, exampleContext, ExampleType.ENGLISH);
      }
      englishExample = englishExample == null ? "" : "<" + englishExample + ">";
      String cleanPrettyPath = path == null ? null : prettyPathMaker.getOutputForm(prettyPath);
      Status status = new Status();
      String source = path == null ? null : cldrFile.getSourceLocaleID(path, status);
      String fillinValue = path == null ? null : cldrFile.getFillInValue(path);
      fillinValue = fillinValue == null ? "" : fillinValue.equals(value) ? "=" : fillinValue;
      
      final String otherSource = path == null ? null : (source.equals(localeID) ? "" : "\t" + source);
      final String otherPath = path == null ? null : (status.pathWhereFound.equals(path) ? "" : "\t" + status.pathWhereFound);
      System.out.println(
              getLocaleAndName(localeID)
              + "\t" + subtotalCount.getCount(shortStatus)
              + "\t" + shortStatus
              + "\t" + cleanPrettyPath
              + "\t〈" + englishPathValue + "〉"
              + "\t【" + englishExample + "】"
              + "\t〈" + value + "〉"
              + "\t«" + fillinValue + "»"
              + "\t【" + example + "】"
              + "\t" + statusString
              + "\t" + fullPath
              + otherSource
              + otherPath
      );
    } else if (generated_html != null) {
      if (shortStatus == ErrorType.contributed) {
        return;
      }
      if (shortStatus == ErrorType.posix) {
        shortStatus = ErrorType.minimal;
      }
      if (!localeID.equals(lastHtmlLocaleID)) {
        writeErrorCountsText();
        //startGeneratedTable(generated_html, generated_html_table);
        lastHtmlLocaleID = localeID;
      }
      htmlErrorsPerLocale.increment(shortStatus);
      htmlErrorsPerBaseLanguage.increment(shortStatus);

      String menuPath = path == null ? null : PathUtilities.xpathToMenu(path);
      String link = path == null ? null : "http://unicode.org/cldr/apps/survey?_=" + localeID + "&x=" + menuPath;
      showLine(generated_html_table, localeID, path, value, shortStatus, menuPath, link);
    }
    if (generated_html_count != null) {
      generated_html_count.println(lastHtmlLocaleID + ";\tpath:\t" + path);
    }
  }

  private static void writeErrorCountsText() {
    if (htmlErrorsPerLocale.total() != 0) {
      generated_html_count.print(lastHtmlLocaleID + ";\tcounts");
      for (ErrorType type : ErrorType.toShow) {
        generated_html_count.print(";\t" + type + "=" + htmlErrorsPerLocale.getCount(type));
      }
      generated_html_count.println();
      generated_html_count.flush();

      sortedHtmlIndexLines.put(lastHtmlLocaleID, new Pair<String,ErrorCount>(lastHtmlLocaleID, htmlErrorsPerLocale));
      htmlErrorsPerLocale = new ErrorCount();
    }
  }

  static TablePrinter generated_html_table = new TablePrinter();
  
  private static void showLineHeaders(TablePrinter table) {
    table
    .setTableAttributes("border='1px' style='border-collapse: collapse' bordercolor='#CCCCFF'")
    .addColumn("Problem").setSortPriority(0).setSpanRows(true).setBreakSpans(true).setRepeatHeader(true)
    .addColumn("Locale").setSortPriority(1).setSpanRows(true).setBreakSpans(true).setRepeatHeader(true)
    .addColumn("Name").setSpanRows(true).setBreakSpans(true)
    .addColumn("HIDDEN").setSortPriority(2).setHidden(true)
    .addColumn("Path").setCellAttributes("width=30%")
    .addColumn("English").setCellAttributes("width=20%")
    .addColumn("Native").setCellAttributes("width=20%");
  }
  
  private static void showLine(TablePrinter table, String localeID, String path, String value, ErrorType shortStatus, String menuPath, String link) {
    final String prettyPath = path == null ? "general" : prettyPathMaker.getPrettyPath(path, true);
    final String outputForm = path == null ? "general" : prettyPathMaker.getOutputForm(prettyPath);
    table.addRow()
      .addCell(shortStatus)
      .addCell(getLinkedLocale(localeID))
      .addCell(getLocaleName(localeID))
      .addCell(prettyPath) // menuPath == null ? "" : "<a href='" + link + "'>" + menuPath + "</a>"
      .addCell(outputForm) // menuPath == null ? "" : "<a href='" + link + "'>" + menuPath + "</a>"
      .addCell(safeForHtml(path == null ? null : getEnglishPathValue(path)))
      .addCell(safeForHtml(value))
      .finishRow();
  }

  
  static String lastHtmlLocaleID = "";
  static ErrorCount htmlErrorsPerLocale = new ErrorCount();

  private static String safeForHtml(String value) {
    return value == null ? "" : TransliteratorUtilities.toHTML.transliterate(value);
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
    String localizedName = CheckCLDR.getDisplayInformation().getName(locale);
    if (localizedName == null || localizedName.equals(locale)) return locale;
    return locale + " [" + localizedName + "]";
  }
  
  /**
   * Utility for getting information.
   * @param locale
   * @param linkToXml TODO
   * @return
   */
  public static String getNameAndLocale(String locale, boolean linkToXml) {
    String localizedName = CheckCLDR.getDisplayInformation().getName(locale);
    if (localizedName == null || localizedName.equals(locale)) return locale;
    if (linkToXml) {
      locale = "<a href='http://unicode.org/cldr/data/common/main/" + locale + ".xml'>" + locale + "</a>";
    }
    return localizedName  + " [" + locale + "]";
  }
  
  public static String getLocaleName(String locale) {
    String localizedName = CheckCLDR.getDisplayInformation().getName(locale);
    if (localizedName == null || localizedName.equals(locale)) return locale;
    return localizedName;
  }
  public static String getLinkedLocale(String locale) {
    return "<a href='http://unicode.org/cldr/apps/survey?_=" + locale + "'>" + locale + "</a>";
  }
}