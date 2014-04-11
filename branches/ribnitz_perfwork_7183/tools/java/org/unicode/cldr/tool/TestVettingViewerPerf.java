/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.unicode.cldr.tool;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;

import java.io.File;
import java.io.IOException;
import java.util.EnumSet;
import java.util.Objects;
import java.util.logging.Logger;

import org.unicode.cldr.test.CheckCLDR;
import org.unicode.cldr.tool.Option.Options;
import org.unicode.cldr.util.CLDRFile;
import org.unicode.cldr.util.CLDRPaths;
import org.unicode.cldr.util.Factory;
import org.unicode.cldr.util.Level;
import org.unicode.cldr.util.SupplementalDataInfo;
import org.unicode.cldr.util.Timer;
import org.unicode.cldr.util.VettingViewer;
import org.unicode.cldr.util.VoteResolver;

/**
 *
 * @author ribnitz
 */
public class TestVettingViewerPerf {

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

            VettingViewer.UsersChoice<VoteResolver.Organization> usersChoice = new VettingViewer.UsersChoice<VoteResolver.Organization>() {
                // Fake values for now
                public String getWinningValueForUsersOrganization(CLDRFile cldrFile, String path, VoteResolver.Organization user) {
                    if (path.contains("USD")) {
                        return "&dummy ‘losing’ value";
                    }
                    return null; // assume we didn't vote on anything else.
                }

                // Fake values for now
                public VettingViewer.VoteStatus getStatusForUsersOrganization(CLDRFile cldrFile, String path, VoteResolver.Organization user) {
                    String usersValue = getWinningValueForUsersOrganization(cldrFile, path, user);
                    String winningValue = cldrFile.getWinningValue(path);
                    if (usersValue != null && !Objects.equals(usersValue, winningValue)) {
                        //!CharSequences.equals(usersValue, winningValue)) {
                        return VettingViewer.VoteStatus.losing;
                    }
                    String fullPath = cldrFile.getFullXPath(path);
                    if (fullPath.contains("AMD") || fullPath.contains("unconfirmed") || fullPath.contains("provisional")) {
                        return VettingViewer.VoteStatus.provisionalOrWorse;
                    } else if (fullPath.contains("AED")) {
                        return VettingViewer.VoteStatus.disputed;
                    } else if (fullPath.contains("AED")) {
                        return VettingViewer.VoteStatus.ok_novotes;
                    }
                    return VettingViewer.VoteStatus.ok;
                }
            };

            // create the tableView and set the options desired.
            // The Options should come from a GUI; from each you can get a long
            // description and a button label.
            // Assuming user can be identified by an int
           final VettingViewer<VoteResolver.Organization> tableView = new VettingViewer<VoteResolver.Organization>(supplementalDataInfo, cldrFactory,
                cldrFactoryOld, usersChoice, "CLDR " + version,
                "Winning Proposed");

            // here are per-view parameters

            final EnumSet<VettingViewer.Choice> choiceSet = EnumSet.allOf(VettingViewer.Choice.class);
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
            for (int i=0;i<7;i++) {
            new Thread(new Runnable() {

                    @Override
                    public void run() {
                        try {
                            String[] args=new String[]{};
                            VettingViewer.main(args);
//                            VettingViewer.writeFile(outDirStr, tableView, choiceSet, "", finalLocaleStringId, finalUserNumericID, finalUserLevel, VettingViewer.CodeChoice.summary,
//                                    VoteResolver.Organization.google);
                        } catch (IOException ex) {
                            Logger.getLogger(TestVettingViewer.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
                        }
                    }
                }).start();
            }
    }
}
