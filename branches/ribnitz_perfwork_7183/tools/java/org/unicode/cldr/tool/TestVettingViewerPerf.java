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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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

    private static interface ThreadFinishedCallback {
        void onThreadFinished(long startTime);
    }

    private static class TimeAddingRespawner implements ThreadFinishedCallback {
        private final List<Integer> respawnList;
        private final int curTstNum;
        private final ExecutorService es;
        private final long timeStarted;
        private final List<Double> executionTimes;

        private TimeAddingRespawner(List<Integer> respawnList, int curTstNum, ExecutorService es, 
            long timeStarted, List<Double> executionTimes) {
            this.respawnList = respawnList;
            this.curTstNum = curTstNum;
            this.es = es;
            this.timeStarted = timeStarted;
            this.executionTimes = executionTimes;
        }

        @Override
        public void onThreadFinished(long startTime) {
            long curTime=System.currentTimeMillis();
            synchronized(executionTimes) {
                executionTimes.add(new Double(curTime-startTime));
            }
            if (curTime-timeStarted<10*60*1000) {
                System.out.println("Re-spawning thread "+curTstNum);
                synchronized(respawnList) {
                    respawnList.set(curTstNum, respawnList.get(curTstNum)+1);
                }
                es.submit(new VettingViewerTestThread(curTstNum,this));
            }
        }
    }
  
     private  static class VettingViewerTestThread implements Runnable {
        private final int curTstNum;
        private final ThreadFinishedCallback cb;
       // private final long startNewUntil;

        public VettingViewerTestThread(int curTstNum, ThreadFinishedCallback callback) {
            this.curTstNum = curTstNum;
            this.cb=callback;
        }

        @Override
        public void run() {
            long curTime=System.currentTimeMillis();
            try {
          //      while (System.currentTimeMillis()<startNewUntil) {
                    String[] args=new String[]{};
                    VettingViewer.main(args);
//                            VettingViewer.writeFile(outDirStr, tableView, choiceSet, "", finalLocaleStringId, finalUserNumericID, finalUserLevel, VettingViewer.CodeChoice.summary,
//                                    VoteResolver.Organization.google);
                    progressMap.putIfAbsent(new String("Thread-"+curTstNum), true);
           //     }
            } catch (IOException ex) {
                Logger.getLogger(TestVettingViewer.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
            }
            if (cb!=null) {
                cb.onThreadFinished(curTime);
            }
        }
    }
//    private static class CacheStatRunable implements Runnable {
//        private final int numTests;
//        private final long sleepTime;
//        public boolean keepRunning=true;
//        private CacheStatRunable(int numTests, long sleepTime) {
//            this.numTests = numTests;
//            this.sleepTime=sleepTime;
//        }
//
//        @Override
//        public void run() {
//           while (keepRunning) {
//               if (progressMap.keySet().size()<numTests) {
//                   try {
//                    Thread.sleep(5*1000);
//                } catch (InterruptedException e) {
//                    // TODO Auto-generated catch block
//                    e.printStackTrace();
//                }
//               } else {
//                   break;
//               }
//           }
//          if (PatternCache.isCachingEnabled() && PatternCache.isRecordStatistics()) {
//              CacheStats stats=PatternCache.getStatistics();
//              System.out.println("Cache requests:"+stats.requestCount()+" hit count: "+stats.hitCount()+" hit rate: "+stats.hitRate());
//          }
//        }
//    }
    private enum MyOptions {
         repeat(null, null, "Repeat indefinitely"),
         filter(".*", ".*", "Filter files"),
         locale(".*", "af", "Single locale for testing"),
         source(".*", CLDRPaths.MAIN_DIRECTORY, // CldrUtility.TMP2_DIRECTORY + "/vxml/common/main"
             "if summary, creates filtered version (eg -d main): does a find in the name, which is of the form dir/file"),
         verbose(null, null, "verbose debugging messages"),
         output(".*", CLDRPaths.TMP_DIRECTORY + "dropbox/mark/vetting/", "filter the raw files (non-summary, mostly for debugging)"), 
         threads(".*","5","Number of threads to use"),
         minutes(".*","10", "Amount of time to run."),
         ;
         // boilerplate
         final Option option;

         MyOptions(String argumentPattern, String defaultArgument, String helpText) {
             option = myOptions.add(this, argumentPattern, defaultArgument, helpText);
         }
     }
     
     private static Splitter SEMICOLON_SPLITTER=Splitter.on(";").omitEmptyStrings().trimResults();
     private static Splitter COLON_SPLITTER=Splitter.on(":").omitEmptyStrings().trimResults();
     private static Joiner ARROW_JOINER=Joiner.on("->").skipNulls();
     
     private static class MathHelper {
     
         public static double min(List<Double> numbers) {
             if (numbers.isEmpty()) {
                 return Double.MIN_VALUE;
             }
             Double[] d = getSortedArray(numbers);
             return d[0];
         }

        private static Double[] getSortedArray(List<Double> numbers) {
            Double[] d=new Double[0];
             d=numbers.toArray(d);
             Arrays.sort(d);
            return d;
        }
         
         public static double max(List<Double> numbers) {
             if (numbers.isEmpty()) {
                 return Double.MAX_VALUE;
             }
             Double[] d = getSortedArray(numbers);
             return d[d.length-1];
         }
         
         public static double avg(List<Double> numbers) {
             if (numbers.isEmpty()) {
                 return Double.MAX_VALUE;
             }
             Double[] d = getSortedArray(numbers);
             if (d.length%2==0) {
                 return d[d.length/2];
             }
             return (d[d.length/2]+d[d.length/2+1])/2.0d;
         }
     }
     private static final ConcurrentMap<String,Boolean> progressMap=new ConcurrentHashMap<>();
    public static void main(String[] args) {
        myOptions.parse(MyOptions.source, args, true);
        boolean repeat = MyOptions.repeat.option.doesOccur();
        String fileFilter = MyOptions.filter.option.getValue();
        String myOutputDir = repeat ? null : MyOptions.output.option.getValue();
        String LOCALE = MyOptions.locale.option.getValue();
        String CURRENT_MAIN = "/Users/ribnitz/Documents/workspace/cldr/common/main/";
        String numThreads="5";
        String runtime="10";
        if (MyOptions.threads.option.doesOccur()) {
           numThreads=MyOptions.threads.option.getValue();
        }
//        if (MyOptions.runtime.option.doesOccur()) {
//            runtime=MyOptions.runtime.option.getValue();
//        }
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
             int numTests=Integer.parseInt(numThreads);
             int endTimeInt=Integer.parseInt(runtime);
             long endTime=System.currentTimeMillis()+endTimeInt*60*60*1000;
            //parse the command line
            System.out.println("Running performance tests using "+numTests+" threads for load");
//            System.out.println("Will spawn new threads for "+runtime+" minutes");
            final long timeStarted=System.currentTimeMillis();
            final List<Double> executionTimes=new ArrayList<>();
            final List<Integer> respawnList=new ArrayList<>(numTests);
            for (int i=0;i<numTests;i++) {
                respawnList.add(0);
            }
            final ExecutorService es=Executors.newFixedThreadPool(5);
            for (int i=0;i<numTests;i++) {
                final int curTstNum=i;
               es.submit(new VettingViewerTestThread(curTstNum, new TimeAddingRespawner(respawnList, curTstNum, es, timeStarted, executionTimes)));
            }
            try {
                Thread.sleep(10*60*1000);
                es.shutdown();
                if (!es.awaitTermination(5, TimeUnit.MINUTES)) {
                    es.shutdownNow();
                }
                if (!executionTimes.isEmpty()) {
                    System.out.println("min: "+MathHelper.min(executionTimes)/1000d+" s;avg:"+
                 MathHelper.avg(executionTimes)/1000d+" max: "+MathHelper.max(executionTimes)/1000d+"s");
                 }
            } catch (InterruptedException e) {}
//            CacheStatRunable r=new CacheStatRunable(numTests, 10*1000);
//            // add another thread for the stats
//            new Thread(r).start();
    }
}
