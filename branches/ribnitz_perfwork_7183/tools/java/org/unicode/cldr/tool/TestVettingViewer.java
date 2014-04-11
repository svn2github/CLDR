package org.unicode.cldr.tool;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.Set;

import org.unicode.cldr.test.CheckCLDR;
import org.unicode.cldr.tool.Option.Options;
import org.unicode.cldr.util.CLDRFile;
import org.unicode.cldr.util.CLDRPaths;
import org.unicode.cldr.util.Factory;
import org.unicode.cldr.util.Level;
import org.unicode.cldr.util.RegexLogger;
import org.unicode.cldr.util.SupplementalDataInfo;
import org.unicode.cldr.util.Timer;
import org.unicode.cldr.util.VettingViewer;
import org.unicode.cldr.util.RegexLogger.PatternCountInterface;
import org.unicode.cldr.util.RegexLogger.RegexLoggerInterface;
import org.unicode.cldr.util.VettingViewer.Choice;
import org.unicode.cldr.util.VettingViewer.CodeChoice;
import org.unicode.cldr.util.VettingViewer.UsersChoice;
import org.unicode.cldr.util.VettingViewer.VoteStatus;
import org.unicode.cldr.util.VoteResolver.Organization;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.ibm.icu.text.ListFormatter;

import java.io.StringWriter;

public class TestVettingViewer {

    /**
     * Simple example of usage
     * 
     * @param args
     * @throws IOException
     */
    private  static final Options myOptions = new Options();
     private static final double NANOSECS = 1000000000.0;

     private enum MyOptions {
         repeat(null, null, "Repeat indefinitely"),
         filter(".*", ".*", "Filter files"),
         locale(".*", "af", "Single locale for testing"),
         source(".*", CLDRPaths.MAIN_DIRECTORY, // CldrUtility.TMP2_DIRECTORY + "/vxml/common/main"
             "if summary, creates filtered version (eg -d main): does a find in the name, which is of the form dir/file"),
         verbose(null, null, "verbose debugging messages"),
         output(".*", CLDRPaths.TMP_DIRECTORY + "dropbox/mark/vetting/", "filter the raw files (non-summary, mostly for debugging)"), ;
         // boilerplate
         final Option option;

         MyOptions(String argumentPattern, String defaultArgument, String helpText) {
             option = myOptions.add(this, argumentPattern, defaultArgument, helpText);
         }
     }
     
     private static Splitter SEMICOLON_SPLITTER=Splitter.on(";").omitEmptyStrings().trimResults();
     private static Splitter COLON_SPLITTER=Splitter.on(":").omitEmptyStrings().trimResults();
     private static Joiner ARROW_JOINER=Joiner.on("->").skipNulls();
     
    public static void main(String[] args) {
        myOptions.parse(MyOptions.source, args, true);
        boolean repeat = MyOptions.repeat.option.doesOccur();
        String fileFilter = MyOptions.filter.option.getValue();
        String myOutputDir = repeat ? null : MyOptions.output.option.getValue();
        String LOCALE = MyOptions.locale.option.getValue();
        String CURRENT_MAIN = "/Users/ribnitz/Documents/workspace/cldr/common/main/";
        final String version = "24.0";
        //final String lastMain = CLDRPaths.ARCHIVE_DIRECTORY + "/cldr-" + version + "/common/main";
        final String lastMain = CLDRPaths.ARCHIVE_DIRECTORY + "/common/main";
//        do {
            Timer timer = new Timer();
            timer.start();

            Factory cldrFactory = Factory.make(CURRENT_MAIN, fileFilter);
            cldrFactory.setSupplementalDirectory(new File(CLDRPaths.SUPPLEMENTAL_DIRECTORY));
            Factory cldrFactoryOld = Factory.make(lastMain, fileFilter);
            SupplementalDataInfo supplementalDataInfo = SupplementalDataInfo
                .getInstance(CLDRPaths.SUPPLEMENTAL_DIRECTORY);
            CheckCLDR.setDisplayInformation(cldrFactory.make("en", true));

            // FAKE this, because we don't have access to ST data

            UsersChoice<Organization> usersChoice = new UsersChoice<Organization>() {
                // Fake values for now
                public String getWinningValueForUsersOrganization(CLDRFile cldrFile, String path, Organization user) {
                    if (path.contains("USD")) {
                        return "&dummy ‘losing’ value";
                    }
                    return null; // assume we didn't vote on anything else.
                }

                // Fake values for now
                public VoteStatus getStatusForUsersOrganization(CLDRFile cldrFile, String path, Organization user) {
                    String usersValue = getWinningValueForUsersOrganization(cldrFile, path, user);
                    String winningValue = cldrFile.getWinningValue(path);
                    if (usersValue != null && !Objects.equals(usersValue, winningValue)) {
                        //!CharSequences.equals(usersValue, winningValue)) {
                        return VoteStatus.losing;
                    }
                    String fullPath = cldrFile.getFullXPath(path);
                    if (fullPath.contains("AMD") || fullPath.contains("unconfirmed") || fullPath.contains("provisional")) {
                        return VoteStatus.provisionalOrWorse;
                    } else if (fullPath.contains("AED")) {
                        return VoteStatus.disputed;
                    } else if (fullPath.contains("AED")) {
                        return VoteStatus.ok_novotes;
                    }
                    return VoteStatus.ok;
                }
            };

            // create the tableView and set the options desired.
            // The Options should come from a GUI; from each you can get a long
            // description and a button label.
            // Assuming user can be identified by an int
           final VettingViewer<Organization> tableView = new VettingViewer<Organization>(supplementalDataInfo, cldrFactory,
                cldrFactoryOld, usersChoice, "CLDR " + version,
                "Winning Proposed");

            // here are per-view parameters

            final EnumSet<Choice> choiceSet = EnumSet.allOf(Choice.class);
            String localeStringID = LOCALE;
            int userNumericID = 666;
            Level usersLevel = Level.MODERN;
            tableView.setBaseUrl("http://st.unicode.org/smoketest/survey");
            // http: // unicode.org/cldr-apps/survey?_=ur

//            if (!repeat) {
//                FileUtilities.copyFile(VettingViewer.class, "vettingView.css", myOutputDir);
//                FileUtilities.copyFile(VettingViewer.class, "vettingView.js", myOutputDir);
//            }
            System.out.println("Creation: " + timer.getDuration() / NANOSECS + " secs");

            // timer.start();
            // writeFile(tableView, choiceSet, "", localeStringID, userNumericID, usersLevel, CodeChoice.oldCode);
            // System.out.println(timer.getDuration() / NANOSECS + " secs");
//            java.util.concurrent.ExecutorService es=Executors.newFixedThreadPool(5);
//            final String outDirStr=myOutputDir;
//            final String finalLocaleStringId=localeStringID;
//            final int finalUserNumericID=userNumericID;
//            final Level finalUserLevel=usersLevel;
//            for (int i=0;i<5;i++) {
//            new Thread(new Runnable() {
//
//                    @Override
//                    public void run() {
//                        try {
//                            VettingViewer.writeFile(outDirStr, tableView, choiceSet, "", finalLocaleStringId, finalUserNumericID, finalUserLevel, CodeChoice.summary,
//                                    Organization.google);
//                        } catch (IOException ex) {
//                            Logger.getLogger(TestVettingViewer.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
//                        }
//                    }
//                }).start();
//            }
//            es.shutdown();
//            try {
//            if (!es.awaitTermination(3, TimeUnit.HOURS)) {
//                es.shutdownNow();
//            }
//            } catch (InterruptedException ie) {
//                 Logger.getLogger(TestVettingViewer.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
//            }
            try {
                List<Double> shortTestDuration=new ArrayList<>();
                List<Double> longTestDurations=new ArrayList<>();
                Timer t0=new Timer();
                t0.start();
//                for (int i=0;i<3;i++) {
                    timer.start();
                    
                    VettingViewer.writeFile(myOutputDir, tableView, choiceSet, "", localeStringID, userNumericID, usersLevel, CodeChoice.newCode, null);
                    double duration=timer.getDuration() / NANOSECS;
                    shortTestDuration.add(duration);
                    System.out.println("Code: " + duration+ " secs");

                    timer.start();
                    VettingViewer.writeFile(myOutputDir, tableView, choiceSet, "", localeStringID, userNumericID, usersLevel, CodeChoice.summary,
                        Organization.google);
                    duration=timer.getDuration() / NANOSECS;
                    longTestDurations.add(duration);
                    System.out.println("Summary: " + duration+ " secs");
//                }
                double dd=t0.getDuration()/NANOSECS;
                System.out.println("Total duration: "+dd+" secs");
                ListFormatter lf=ListFormatter.getInstance();
                System.out.println("Short tests: "+lf.format(shortTestDuration));
                System.out.println("Long tests: "+lf.format(longTestDurations));
//                File f=new File("/users/ribnitz/regexes.txt");
//                f.createNewFile();
                try (PrintWriter pw=new PrintWriter(new StringWriter())) {
                    RegexLoggerInterface logger=RegexLogger.getInstance();
                    NavigableSet<PatternCountInterface> logSet=logger.getEntries();
                    System.out.println("Writing a total of "+logSet.size()+" entries");
                    pw.println("Found\tNot found\tFound+Not found\tMatched\tNot matched\tMatched+Not Matched\tFrom RegexLookup\tPattern\tPossible matches");
                    StringBuilder sb=new StringBuilder();
                    for (PatternCountInterface key:logSet.descendingSet()) {
                        String pattern=key.getPattern();
                        int numFound=key.getNumberOfFindMatches();
                        int numFoundFailed=key.getNumberOfFindFailures();
                        int matched=key.getNumberOfMatchMatches();
                        int matchFails=key.getNumberOfMatchFailures();
                        boolean fromRegexFinder=key.isCalledFromRegexFinder();
                        String pat=pattern.startsWith("=")?"'"+pattern:pattern;
                        sb.append(numFound+"\t"+numFoundFailed+"\t"+(numFound+numFoundFailed)+"\t"+matched+"\t"+
                            matchFails+"\t"+(matched+matchFails)+"\t"+fromRegexFinder+"\t"+pat);
                        sb.append("\t");
                        Set<String> callLocations=key.getCallLocations();
                        if (!callLocations.isEmpty()) {
                            for (String cur:callLocations) {
                                  sb.append(ARROW_JOINER.join(SEMICOLON_SPLITTER.split(cur)));
//                                String previous=null;
//                                boolean isFirst=true;
//                                // different classes are separated by semicolons
//                                for (String curCell: SEMICOLON_SPLITTER.split(cur)) {
//                                    // classname and linenumber are split by colons
//                                    List<String> curLocation=COLON_SPLITTER.splitToList(curCell);
//                                    String className=curLocation.get(0);
//                                    String lineNo=curLocation.get(1);
//                                    
//                                    if (previous==null||!className.equals(previous)) {
//                                        if (!isFirst) {
//                                            sb.append(")->");
//                                        } 
//                                        sb.append(className);
//                                        sb.append("(");
//                                    } else {
//                                        sb.append(";");
//                                    }
//                                    sb.append(lineNo);
//                                    previous=className;
//                                }
//                                sb.append(")");
//                                sb.append(cur);
                                sb.append(", ");
                            }
                            String s=sb.substring(0, sb.lastIndexOf(", "));
                            sb.setLength(0);
                            sb.append(s);
                        }
                        pw.println(sb.toString());
                        sb.setLength(0);
                    }
                }

            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }

            //        timer.start();
            //        writeFile(tableView, choiceSet, "", localeStringID, userNumericID, usersLevel, CodeChoice.summary,
            //                Organization.ibm);
            //        System.out.println(timer.getDuration() / NANOSECS + " secs");

            // // check that the choices work.
            // for (Choice choice : choiceSet) {
            // timer.start();
            // writeFile(tableView, EnumSet.of(choice), "-" + choice.abbreviation, localeStringID, userNumericID,
            // usersLevel);
            // System.out.println(timer.getDuration() / NANOSECS + " secs");
            // }
//        } while (repeat);
    }
}
