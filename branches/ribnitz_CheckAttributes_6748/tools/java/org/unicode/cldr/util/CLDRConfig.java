package org.unicode.cldr.util;

import java.io.File;
import java.io.FilenameFilter;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.unicode.cldr.test.CheckCLDR.Phase;

import com.ibm.icu.dev.test.TestFmwk;
import com.ibm.icu.dev.test.TestLog;
import com.ibm.icu.text.Collator;
import com.ibm.icu.text.RuleBasedCollator;

public class CLDRConfig extends Properties {
    /**
     *
     */
    private static final long serialVersionUID = -2605254975303398336L;
    public static boolean DEBUG = false;
    private static CLDRConfig INSTANCE = null;
    public static final String SUBCLASS = CLDRConfig.class.getName() + "Impl";

    /**
     * Object to use for synchronization when interacting with Factory
     */
    private static final Object CLDR_FACTORY_SYNC = new Object();

    /**
     * Object to use for synchronization when interacting with Factory
     */
    private static final Object FULL_FACTORY_SYNC = new Object();

    /**
     * Object used for synchronization when interacting with SupplementalData
     */
    private static final Object SUPPLEMENTAL_DATA_SYNC = new Object();

    /**
     * Object used for synchronization in getCollator()
     */
    private static final Object GET_COLLATOR_SYNC = new Object();

    /**
     * Object to use for Synchronization in getRoot()
     */
    private static final Object GET_ROOT_SYNC = new Object();

    /**
     * Object to use for synchronization in getEnglish()
     */
    private static final Object GET_ENGLISH_SYNC = new Object();

    /**
     * Object used for synchronization in getStandardCodes()
     */
    private static final Object GET_STANDARD_CODES_SYNC = new Object();
    
    /**
     * Object used for synchronization in getCoverageInfo()
     */
    private static Object COVERAGE_INFO_SYNC=new Object(); 

    public enum Environment {
        LOCAL, // < == unknown.
        SMOKETEST, // staging area
        PRODUCTION, // production server!
        UNITTEST // unit test setting
    };

    public static CLDRConfig getInstance() {
        synchronized (CLDRConfig.class) {
            if (INSTANCE == null) {
                final String env = System.getProperty("CLDR_ENVIRONMENT");
                if (env != null && env.equals(Environment.UNITTEST.name())) {
                    if (DEBUG) {
                        System.err.println("-DCLDR_ENVIRONMENT=" + env + " - not loading " + SUBCLASS);
                    }
                } else {
                    try {
                        // System.err.println("Attempting to new up a " + SUBCLASS);
                        INSTANCE = (CLDRConfig) (Class.forName(SUBCLASS).newInstance());

                        if (INSTANCE != null) {
                            System.err.println("Using CLDRConfig: " + INSTANCE.toString() + " - "
                                + INSTANCE.getClass().getName());
                        } else {
                            if (DEBUG) {
                                // Probably occurred because ( config.getEnvironment() == Environment.UNITTEST )
                                // see CLDRConfigImpl
                                System.err.println("Note: CLDRConfig Subclass " +
                                    SUBCLASS + ".newInstance() returned NULL " +
                                    "( this is OK if we aren't inside the SurveyTool's web server )");
                            }
                        }
                    } catch (ClassNotFoundException e) {
                        // Expected - when not under cldr-apps, this class doesn't exist.
                    } catch (InstantiationException | IllegalAccessException e) {
                        // TODO: log a useful message
                    }
                }
            }
            if (INSTANCE == null) {
                INSTANCE = new CLDRConfig();
                CldrUtility.checkValidDirectory(INSTANCE.getProperty("CLDR_DIR"),
                    "You have to set -DCLDR_DIR=<validdirectory>");
            }
        }
        return INSTANCE;
    }

    String initStack = null;

    protected CLDRConfig() {
        initStack = StackTracker.currentStack();
    }

    public String getInitStack() {
        return initStack;
    }
  
    private CoverageInfo coverageInfo=null;
    private SupplementalDataInfo supplementalDataInfo;
    private StandardCodes sc;
    private Factory cldrFactory;
    private Factory fullFactory;
    private Factory supplementalFactory;
    private CLDRFile english;
    private CLDRFile root;
    private RuleBasedCollator col;
    private Phase phase = null; // default

    private TestLog testLog = null;

    // base level
    public TestLog setTestLog(TestLog log) {
        testLog = log;
        return log;
    }

    // for calling "run"
    public TestFmwk setTestLog(TestFmwk log) {
        testLog = log;
        return log;
    }

    protected void logln(String msg) {
        if (testLog != null) {
            testLog.logln(msg);
        } else {
            System.out.println(msg);
            System.out.flush();
        }
    }

    public SupplementalDataInfo getSupplementalDataInfo() {
        synchronized (SUPPLEMENTAL_DATA_SYNC) {
            if (supplementalDataInfo == null) {
                supplementalDataInfo = SupplementalDataInfo.getInstance(CLDRPaths.DEFAULT_SUPPLEMENTAL_DIRECTORY);
            }
        }
        return supplementalDataInfo;
    }

    public StandardCodes getStandardCodes() {
        synchronized (GET_STANDARD_CODES_SYNC) {
            if (sc == null) {
                sc = StandardCodes.make();
            }
        }
        return sc;
    }

    public CoverageInfo getCoverageInfo() { 
        synchronized(COVERAGE_INFO_SYNC) { 
            if (coverageInfo==null) { 
                coverageInfo=new CoverageInfo(getSupplementalDataInfo()); 
            } 
        } 
        return coverageInfo; 
    } 
    
    public Factory getCldrFactory() {
        synchronized (CLDR_FACTORY_SYNC) {
            if (cldrFactory == null) {
                cldrFactory = Factory.make(CLDRPaths.MAIN_DIRECTORY, ".*");
            }
        }
        return cldrFactory;
    }

    public Factory getFullCldrFactory() {
        synchronized (FULL_FACTORY_SYNC) {
            if (fullFactory == null) {
                File[] paths = { new File(CLDRPaths.MAIN_DIRECTORY), new File(CLDRPaths.SEED_DIRECTORY) };
                fullFactory = SimpleFactory.make(paths, ".*");
            }
        }
        return fullFactory;
    }

    public Factory getSupplementalFactory() {
        synchronized (CLDR_FACTORY_SYNC) {
            if (supplementalFactory == null) {
                supplementalFactory = Factory.make(CLDRPaths.DEFAULT_SUPPLEMENTAL_DIRECTORY, ".*");
            }
        }
        return supplementalFactory;
    }

    public CLDRFile getEnglish() {
        synchronized (GET_ENGLISH_SYNC) {

            if (english == null) {
                english = getCldrFactory().make("en", true);
            }
        }
        return english;
    }

    public CLDRFile getRoot() {
        synchronized (GET_ROOT_SYNC) {
            if (root == null) {
                root = getCldrFactory().make("root", true);
            }
        }
        return root;
    }

    public Collator getCollator() {
        synchronized (GET_COLLATOR_SYNC) {
            if (col == null) {
                col = (RuleBasedCollator) Collator.getInstance();
                col.setNumericCollation(true);
            }
        }
        return col;
    }

    public synchronized Phase getPhase() {
        if (phase == null) {
            if (getEnvironment() == Environment.UNITTEST) {
                phase = Phase.BUILD;
            } else {
                phase = Phase.SUBMISSION;
            }
        }
        return phase;
    }

    @Override
    public String getProperty(String key, String d) {
        String result = getProperty(key);
        if (result == null) return d;
        return result;
    }

    private Set<String> shown = new HashSet<String>();

    private Map<String, String> localSet = null;

    @Override
    public String get(Object key) {
        return getProperty(key.toString());
    }

    @Override
    public String getProperty(String key) {
        String result = null;
        if (localSet != null) {
            result = localSet.get(key);
        }
        if (result == null) {
            result = System.getProperty(key);
        }
        if (result == null) {
            result = System.getProperty(key.toUpperCase(Locale.ENGLISH));
        }
        if (result == null) {
            result = System.getProperty(key.toLowerCase(Locale.ENGLISH));
        }
        if (result == null) {
            result = System.getenv(key);
        }
        if (DEBUG && !shown.contains(key)) {
            logln("-D" + key + "=" + result);
            shown.add(key);
        }
        return result;
    }

    private Environment curEnvironment = null;

    public Environment getEnvironment() {
        if (curEnvironment == null) {
            String envString = getProperty("CLDR_ENVIRONMENT");
            if (envString != null) {
                curEnvironment = Environment.valueOf(envString.trim());
            }
            if (curEnvironment == null) {
                curEnvironment = getDefaultEnvironment();
            }
        }
        return curEnvironment;
    }
    
    /**
     * If no environment is defined, what is the default?
     * @return
     */
    protected Environment getDefaultEnvironment() {
        return Environment.LOCAL;
    }

    public void setEnvironment(Environment environment) {
        curEnvironment = environment;
    }

    /**
     * For test use only. Will throw an exception in non test environments.
     * @param k
     * @param v
     * @return
     */
    @Override
    public Object setProperty(String k, String v) {
        if (getEnvironment() != Environment.UNITTEST) {
            throw new InternalError("setProperty() only valid in UNITTEST Environment.");
        }
        if (localSet == null) {
            localSet = new ConcurrentHashMap<String, String>();
        }
        shown.remove(k); // show it again with -D
        return localSet.put(k, v);
    }

    @Override
    public Object put(Object k, Object v) {
        return setProperty(k.toString(), v.toString());
    }

    /**
     * Return true if the value indicates 'true'
     * @param k key
     * @param defVal default value
     * @return
     */
    public boolean getProperty(String k, boolean defVal) {
        String val = getProperty(k, defVal ? "true" : null);
        if (val == null) {
            return false;
        } else {
            val = val.trim().toLowerCase();
            return (val.equals("true") || val.equals("t") || val.equals("yes") || val.equals("y"));
        }
    }

    /**
     * Return a numeric property
     * @param k key
     * @param defVal default value
     * @return
     */
    public int getProperty(String k, int defVal) {
        String val = getProperty(k, Integer.toString(defVal));
        if (val == null) {
            return defVal;
        } else {
            try {
                return Integer.parseInt(val);
            } catch (NumberFormatException nfe) {
                return defVal;
            }
        }
    }

    public File getCldrBaseDirectory() {
        String dir = getProperty("CLDR_DIR", null);
        if (dir != null) {
            return new File(dir);
        } else {
            return null;
        }
    }

    /**
     * Get all CLDR XML files in the CLDR base directory.
     * @return
     */
    public Set<File> getAllCLDRFilesEndingWith(final String suffix) {
        FilenameFilter filter = new FilenameFilter() {
            public boolean accept(File dir, String name) {
                return name.endsWith(suffix) && !isJunkFile(name); // skip junk and backup files
            }
        };
        final File dir = getCldrBaseDirectory();
        Set<File> list;
        list = getCLDRFilesMatching(filter, dir);
        return list;
    }

    /**
     * Return all CLDR data files matching this filter
     * @param filter matching filter
     * @param baseDir base directory, see {@link #getCldrBaseDirectory()}
     * @return set of files
     */
    public Set<File> getCLDRFilesMatching(FilenameFilter filter, final File baseDir) {
        Set<File> list;
        list = new LinkedHashSet<File>();
        for (String subdir : getCLDRDataDirectories()) {
            getFilesRecursively(new File(baseDir, subdir), filter, list);
        }
        return list;
    }

    /**
     * TODO: better place for these constants?
     */
    private static final String COMMON_DIR = "common";
    /**
     * TODO: better place for these constants?
     */
    private static final String EXEMPLARS_DIR = "exemplars";
    /**
     * TODO: better place for these constants?
     */
    private static final String SEED_DIR = "seed";
    /**
     * TODO: better place for these constants?
     */
    private static final String KEYBOARDS_DIR = "keyboards";
    private static final String MAIN_DIR = "main";
    /**
     * TODO: better place for these constants?
     */
    private static final String CLDR_DATA_DIRECTORIES[] = { COMMON_DIR, SEED_DIR, KEYBOARDS_DIR, EXEMPLARS_DIR };

    /**
     * Get a list of CLDR directories containing actual data
     * @return an iterable containing the names of all CLDR data subdirectories
     */
    public Iterable<String> getCLDRDataDirectories() {
        return Arrays.asList(CLDR_DATA_DIRECTORIES);
    }

    /**
     * given comma separated list "common" or "common,main" return a list of actual files
     */
    public File[] getCLDRDataDirectories(String list) {
        final File dir = getCldrBaseDirectory();
        String stubs[] = list.split(",");
        File[] ret = new File[stubs.length];
        for (int i = 0; i < stubs.length; i++) {
            ret[i] = new File(dir, stubs[i]);
        }
        return ret;
    }

    /**
     * map "common","seed" -> "common/main", "seed/main"
     */
    public File[] getMainDataDirectories(File base[]) {
        File[] ret = new File[base.length];
        for (int i = 0; i < base.length; i++) {
            ret[i] = new File(base[i], MAIN_DIR);
        }
        return ret;
    }

    /**
     * Utility function. Recursively add to a list of files. Skips ".svn" and junk directories.
     * @param directory base directory
     * @param filter filter to restrict files added
     * @param toAddTo set to add to
     * @return returns toAddTo.
     */
    public Set<File> getFilesRecursively(File directory, FilenameFilter filter, Set<File> toAddTo) {
        File files[] = directory.listFiles();
        if (files != null) {
            for (File subfile : files) {
                if (subfile.isDirectory()) {
                    if (!isJunkFile(subfile.getName())) {
                        getFilesRecursively(subfile, filter, toAddTo);
                    }
                } else if (filter.accept(directory, subfile.getName())) {
                    toAddTo.add(subfile);
                }
            }
        }
        return toAddTo;
    }

    /**
     * Is the filename junk?  (subversion, backup, etc)
     * @param name
     * @return
     */
    public static final boolean isJunkFile(String name) {
        return name.startsWith(".") || (name.startsWith("#")); // Skip:  .svn, .BACKUP,  #backup# files.
    }

    /**
     * Get the value of the debug setting for the calling class; assuming that no debugging is wanted if the property
     * value cannot be found
     * @param callingClass
     * @return
     * @see {@link #getDebugSettingsFor(Class, boolean)}
     */
    public boolean getDebugSettingsFor(Class<?> callingClass) {
        return getDebugSettingsFor(callingClass, false);
    }
    
    /**
     * Get the debug settings (whether debugging is enabled for the calling class; This will look for a property corresponding 
     * to the canonical classname +".debug"; if that property cannot be found, the default value will be returned.
     * @param callingClass
     * @param defaultValue
     * @return
     */
    public boolean getDebugSettingsFor(Class<?> callingClass, boolean defaultValue) {
        // avoid NPE
        if (callingClass==null) {
            return defaultValue;
        }
       return getProperty(callingClass.getCanonicalName()+".debug", defaultValue);
    }
    
    /**
     * Get the URL generator for "general purpose" (non chart) use.
     * @return
     */
    public CLDRURLS urls() {
        if(urls == null) {
            synchronized(this) {
                urls = internalGetUrls();
            }
        }
        return urls;
    }
    
    /**
     * Get the URL generator for "absolute" (chart, email) use.
     * By default, this is the same as urls.
     */
    public CLDRURLS absoluteUrls() {
        if(absoluteUrls == null) {
            synchronized(this) {
                absoluteUrls = internalGetAbsoluteUrls();
            }
        }
        return absoluteUrls;
    }

    /**
     * Probably would not need to override this.
     */
    protected CLDRURLS internalGetAbsoluteUrls() {
        return new StaticCLDRURLS(this.getProperty(CLDRURLS.CLDR_SURVEY_BASE, CLDRURLS.DEFAULT_BASE));
    }

    /**
     * Override this to provide a different URL source for non-absolute URLs.
     */
    protected CLDRURLS internalGetUrls() {
        return absoluteUrls();
    }

    private CLDRURLS urls = null;
    private CLDRURLS absoluteUrls = null;
}
