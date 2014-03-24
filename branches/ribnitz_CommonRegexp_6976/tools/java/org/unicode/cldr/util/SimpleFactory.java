package org.unicode.cldr.util;

import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.unicode.cldr.util.CLDRFile.DraftStatus;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

public class SimpleFactory extends Factory {

    /**
     * Variable to control the behaviour of the class. 
     *  TRUE -  use a (non-static) array of Maps, indexed by locale String (old behaviour)
     *  FALSE - use a single static map, indexed with a more elaborate key.
     */
    private static final boolean USE_OLD_HANDLEMAKE_CODE = false;

    /**
     * Variable that customizes the caching of the results of SimpleFactory.make
     * 
     */
    private static final boolean CACHE_SIMPLE_FACTORIES = true;

    /**
     * Number of Factories that should be cached, if caching of factories is enabled
     */
    private static final int FACTORY_CACHE_LIMIT = 10;

    /**
     * Object that is used for synchronization when looking up simple factories
     */
    private static final Object FACTORY_LOOKUP_SYNC = new Object();
    /**
     * The maximum cache size the caches in
     * 15 is a safe limit for instances with limited amounts of memory (around 128MB).
     * Larger numbers are tolerable if more memory is available.
     * This constant may be moved to CldrUtilities in future if needed.
     */
//    private static final int CACHE_LIMIT = 15;

    private static final int CACHE_LIMIT = 75;

    /**
     * When set to true, more verbose output will be generated, which is useful for debugging.
     */
    private static final boolean DEBUG_SIMPLEFACTORY = false;

    /**
     * Base class for different Objects which are used as keys in Maps. It is assumed that the objects are basically immutable; once 
     * instantiated their fields do not change; which allows to calculate a hashCode once, which can then be returned on the invocation
     * of hashCode();
     * @author ribnitz
     *
     */
    private static abstract class HashableKey {
        /**
         * Variable to hold the hashCode; note that this is an Integer, so it can be null (if unassigned). A good way to assign it
         * would be a line of code similar to
         * <code>hashCode=Objects.hash(field1,field2,field3);</code>
         * at the end of the constructor of an implementing class.
         */
        protected  Integer hashCode=null;
       
        /**
         * Object to use for synchronization in the hash calculation function
         */
        protected static final Object HASH_CALCULATION_SYNC=new Object();
       
        /**
         * Return a previously-calculated hashCode, or calculate one based on the fields of the implementing class
         */
        @Override
        public int hashCode() {
            synchronized(HASH_CALCULATION_SYNC) {
                if (hashCode==null) {
                    // calculate it
                   Field[] fields=getClass().getDeclaredFields();
                    if (fields.length>0) {
                        // Can safely be cased to object, because varargs are not expected to be written to
                        hashCode=Objects.hash((Object[])fields);
                    } else {
                        // do not call Objects.hash(this), as this would introduce a loop.
                        hashCode=37;
                    }
                }
                return hashCode;
            }
        }
        /**
         * Equals of this base class compares the hash value, as it is the only field the class has.
         */
        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            // Two objects that are the same will never have a different hashCode
            if (hashCode()!=obj.hashCode()) {
                return false;
            }
            return true;
        }
        
        /**
         * Method to set the hashCode to return
         * @param hashCode
         */
        protected void setHashCode(int hashCode) {
            synchronized (HASH_CALCULATION_SYNC) {
                this.hashCode=hashCode;
            }
        }
        
        /**
         * Method to clear the hashCode (and to trigger calculartion on the next invocation of hashCode()
         */
        protected void clearHashCode() {
            synchronized (HASH_CALCULATION_SYNC) {
                hashCode=null;
            }
        }
    }
    /**
     * Simple class used as a key for the map that holds the CLDRFiles -only used in the new version of the code
     * @author ribnitz
     *
     */
    private static final class CLDRCacheKey extends HashableKey {
        private final String localeName;
        private final boolean resolved;
        private final DraftStatus draftStatus;
        private final String directory;
        
        public CLDRCacheKey(String localeName, boolean resolved, DraftStatus draftStatus, File directory) {
            if (directory==null) {
                throw new IllegalArgumentException("Directory must not be null");
            }
            if (localeName==null) {
                throw new IllegalArgumentException("Locale must not be null");
            }
            if (draftStatus==null) {
                throw new IllegalArgumentException("DraftStatus must not be null");
            }
            this.localeName = localeName;
            this.resolved = resolved;
            this.draftStatus = draftStatus;
            this.directory = directory.toString();
            // calculate the hashCode
            setHashCode(Objects.hash(localeName,resolved,draftStatus,directory));
        }

        
        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            // Two objects that are the same will never have a different hashCode
            if (hashCode()!=obj.hashCode()) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            CLDRCacheKey other = (CLDRCacheKey) obj;
           // directory is never null
           if  (!directory.equals(other.directory)) {
                return false;
            }
            if (draftStatus != other.draftStatus) {
                return false;
            }
            // localeName is never null
            if (!localeName.equals(other.localeName)) {
                return false;
            }
            if (resolved != other.resolved) {
                return false;
            }
            return true;
        }

        public String toString() {
            return "CLDRCacheKey [ LocaleName: "+localeName+" Resolved: "+resolved+" Draft status: "+draftStatus+" Direcrory: "+directory+" ]";

        }
    }

    /**
     * If a SimpleDFactory covers more than one directory, SimpleFactoryLookupKey Objects may
     * be needed to find the SimpleFactory that is responsible for the given directory
     * @author ribnitz
     *
     */
    private static final class SimpleFactoryLookupKey extends HashableKey {
        private final String directory;
        private final String matchString;
        
        public SimpleFactoryLookupKey(String directory, String matchString) {
            // Sanity check: do not allow either to be uninitialized/null
            if (directory==null) {
                throw new IllegalArgumentException("Directory must not be null");
            }
            if (matchString==null) {
                throw new IllegalArgumentException("MatchString must not be null");
            }
            this.directory=directory;
            this.matchString=matchString;  
            // calculate the HashCode
            setHashCode(Objects.hash(directory,matchString));
        }

        
        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            // Two objects that are the same will never have a different hashCode
            if (hashCode()!=obj.hashCode()) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            SimpleFactoryLookupKey other = (SimpleFactoryLookupKey) obj;
            // directory must not be null
           if (!directory.equals(other.directory)) {
                return false;
            }
          // matchString must not be null either
           if (!matchString.equals(other.matchString)) {
                return false;
            }
            return true;
        }

        public String getDirectory() {
            return directory;
        }

        public String getMatchString() {
            return matchString;
        }

        @Override
        public String toString() {
            return "SimpleFactoryLookupKey [directory=" + directory + ", matchString=" + matchString + "]";
        }

    }

    /**
     * Simple class to use as a Key in a map that caches SimpleFacotry instances.
     * @author ribnitz
     *
     */
    private static final class SimpleFactoryCacheKey  extends HashableKey {
        private List<String> sourceDirectories;
        private String matchString;
        private DraftStatus mimimalDraftStatus;
        
        public SimpleFactoryCacheKey(List<String> sourceDirectories, String matchString, DraftStatus mimimalDraftStatus) {
            if (sourceDirectories==null) {
                throw new IllegalArgumentException("The list of SourceDirectories must not be null");
            }
            if (matchString==null) {
                throw new IllegalArgumentException("The string to be matched must not be null");
            }
            if (mimimalDraftStatus==null) {
                throw new IllegalArgumentException("The minimal draft status assigned must not be null");
            }
            this.sourceDirectories = sourceDirectories;
            this.matchString = matchString;
            this.mimimalDraftStatus = mimimalDraftStatus;
            // calculate the hashCode
            setHashCode(Objects.hash(sourceDirectories,matchString,mimimalDraftStatus));
        }

        
        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            // Two objects that are the same will never have a different hashCode
            if (hashCode()!=obj.hashCode()) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            SimpleFactoryCacheKey other = (SimpleFactoryCacheKey) obj;
            // matchString must not be null
            if (!matchString.equals(other.matchString)) {
                return false;
            }
            if (mimimalDraftStatus != other.mimimalDraftStatus) {
                return false;
            }
           // sourceDirectories must not be null
            if (!sourceDirectories.equals(other.sourceDirectories)) {
                return false;
            }
            return true;
        }

        public List<String> getSourceDirectories() {
            return sourceDirectories;
        }

        public String getMatchString() {
            return matchString;
        }

        public DraftStatus getMimimalDraftStatus() {
            return mimimalDraftStatus;
        }

        @Override
        public String toString() {
            return "SimpleFactoryCacheKey [sourceDirectories="+sourceDirectories+
                ", matchString="+matchString+", mimimalDraftStatus="+mimimalDraftStatus+"]";
        }

    }

    // private volatile CLDRFile result; // used in handleMake
    private File sourceDirectories[];
    private Set<String> localeList = new TreeSet<String>();
    private Cache<CLDRCacheKey, CLDRFile> combinedCache = null;
    //private   Map<CLDRCacheKey,CLDRFile> combinedCache=  null;
    //     Collections.synchronizedMap(new LruMap<CLDRCacheKey, CLDRFile>(CACHE_LIMIT));

    private Map<String, CLDRFile>[] mainCache = null; /* new Map[DraftStatus.values().length]; */
    private Map<String, CLDRFile>[] resolvedCache = null; /*new Map[DraftStatus.values().length]; */
//    {
//        for (int i = 0; i < mainCache.length; ++i) {
//            mainCache[i] = Collections.synchronizedMap(new LruMap<String, CLDRFile>(CACHE_LIMIT));
//            resolvedCache[i] = Collections.synchronizedMap(new LruMap<String, CLDRFile>(CACHE_LIMIT));
//        }
//    }
    private DraftStatus minimalDraftStatus = DraftStatus.unconfirmed;

    /**
     * Cache that provides a Mapping SimpleFactoryCacheKey -> SimpleFactory. 
     * 
     */
    private static Cache<SimpleFactoryCacheKey, SimpleFactory> factoryCache = null;
   
    /**
     * Since a SimpleFactoryCacheKey can contain several directories, this Cache provides a way
     * to lookup the key to use for the factory lookup map, provided a SimpleFactoryLookupKey
     * (which contains just one directory)
     * 
     */
    private static Cache<SimpleFactoryLookupKey, SimpleFactoryCacheKey> factoryLookupMap = null;

    private SimpleFactory() {
    }

    public DraftStatus getMinimalDraftStatus() {
        return minimalDraftStatus;
    }

    /**
     * Create a factory from a source directory, matchingString
     * For the matchString meaning, see {@link getMatchingXMLFiles}
     */
    public static Factory make(String sourceDirectory, String matchString) {
        return make(sourceDirectory, matchString, DraftStatus.unconfirmed);
    }

    public static Factory make(String sourceDirectory, String matchString, DraftStatus minimalDraftStatus) {
        File list[] = { new File(sourceDirectory) };
        if (!CACHE_SIMPLE_FACTORIES) {
            return new SimpleFactory(list, matchString, minimalDraftStatus);
        }
        // we cache simple factories
        final String sourceDirPathName = list[0].getAbsolutePath();
        List<String> strList = Arrays.asList(new String[] { sourceDirPathName });
        final SimpleFactoryCacheKey key = new SimpleFactoryCacheKey(strList, matchString, minimalDraftStatus);

        synchronized (FACTORY_LOOKUP_SYNC) {
            if (factoryCache == null) {
                factoryCache = CacheBuilder.newBuilder().maximumSize(FACTORY_CACHE_LIMIT).build();
            }
            SimpleFactory fact = factoryCache.getIfPresent(key);
            if (fact == null) {
                // try looking it up
                SimpleFactoryLookupKey lookupKey = new SimpleFactoryLookupKey(sourceDirPathName, matchString);
                if (factoryLookupMap == null) {
                    factoryLookupMap = CacheBuilder.newBuilder().maximumSize(FACTORY_CACHE_LIMIT).build();
                }
                SimpleFactoryCacheKey key2 = factoryLookupMap.getIfPresent(lookupKey);
                if (key2 != null) {
                    return factoryCache.asMap().get(key2);
                }
                // out of luck
                SimpleFactory sf = new SimpleFactory(list, matchString, minimalDraftStatus);
                factoryCache.put(key, sf);
                if (DEBUG_SIMPLEFACTORY) {
                    System.out.println("Created new Factory with parameters " + key);
                }
                factoryLookupMap.put(lookupKey, key);
            }
            return factoryCache.asMap().get(key);
        }
    }

    /**
     * Create a factory from a source directory list, matchingString.
     * For the matchString meaning, see {@link getMatchingXMLFiles}
     */
    public static Factory make(File sourceDirectory[], String matchString) {
        return make(sourceDirectory, matchString, DraftStatus.unconfirmed);
    }
    
    
    /**
     * Create a factory from a source directory list
     * 
     * @param sourceDirectory
     * @param matchString
     * @param minimalDraftStatus
     * @return
     */
    public static Factory make(File sourceDirectory[], String matchString, DraftStatus minimalDraftStatus) {
        if (!CACHE_SIMPLE_FACTORIES) {
            return new SimpleFactory(sourceDirectory, matchString, minimalDraftStatus);
        }

        // we cache simple factories
        List<String> strList = new ArrayList<>();
        List<SimpleFactoryLookupKey> lookupList = new ArrayList<>();
        for (File sourceDir: sourceDirectory) {
            String cur=sourceDir.getAbsolutePath();
            strList.add(cur);
            lookupList.add(new SimpleFactoryLookupKey(cur, matchString));
        }
        final SimpleFactoryCacheKey key = new SimpleFactoryCacheKey(strList, matchString, minimalDraftStatus);
        synchronized (FACTORY_LOOKUP_SYNC) {
            if (factoryCache == null) {
                factoryCache = CacheBuilder.newBuilder().maximumSize(FACTORY_CACHE_LIMIT).build();
            }
            SimpleFactory fact = factoryCache.getIfPresent(key);
            if (fact == null) {
                if (factoryLookupMap == null) {
                    factoryLookupMap = CacheBuilder.newBuilder().maximumSize(FACTORY_CACHE_LIMIT).build();
                }
                Iterator<SimpleFactoryLookupKey> iter = lookupList.iterator();
                while (iter.hasNext()) {
                    SimpleFactoryLookupKey curKey = iter.next();
                    SimpleFactoryCacheKey key2 = factoryLookupMap.asMap().get(curKey);
                    if ((key2 != null) && factoryCache.asMap().containsKey(key2)) {
                        if (DEBUG_SIMPLEFACTORY) {
                            System.out.println("Using key " + key2 + " instead of " + key + " for factory lookup");
                        }
                        return factoryCache.asMap().get(key2);
                    }
                }
                SimpleFactory sf = new SimpleFactory(sourceDirectory, matchString, minimalDraftStatus);
                if (DEBUG_SIMPLEFACTORY) {
                    System.out.println("Created new Factory with parameters " + key);
                }
                factoryCache.put(key, sf);
                iter = lookupList.iterator();
                while (iter.hasNext()) {
                    factoryLookupMap.put(iter.next(), key);
                }
            }
            return factoryCache.asMap().get(key);
        }
    }
    
    @SuppressWarnings("unchecked")
    private SimpleFactory(File sourceDirectories[], String matchString, DraftStatus minimalDraftStatus) {
        // initialize class based
        if (USE_OLD_HANDLEMAKE_CODE) {
            mainCache = new Map[DraftStatus.values().length];
            resolvedCache = new Map[DraftStatus.values().length];
            for (int i = 0; i < mainCache.length; ++i) {
                mainCache[i] = Collections.synchronizedMap(new LruMap<String, CLDRFile>(CACHE_LIMIT));
                resolvedCache[i] = Collections.synchronizedMap(new LruMap<String, CLDRFile>(CACHE_LIMIT));
            }
        } else {
            combinedCache = CacheBuilder.newBuilder().maximumSize(CACHE_LIMIT).build();
        }
        //
        this.sourceDirectories = sourceDirectories;
        this.minimalDraftStatus = minimalDraftStatus;
        Matcher m = Pattern.compile(matchString).matcher("");
        this.localeList = CLDRFile.getMatchingXMLFiles(sourceDirectories, m);
        File goodSuppDir = null;
        for (File sourceDirectoryPossibility : sourceDirectories) {
            File suppDir = new File(sourceDirectoryPossibility, "../supplemental");
            if (suppDir.isDirectory()) {
                goodSuppDir = suppDir;
                break;
            }
        }
        if (goodSuppDir != null) {
            setSupplementalDirectory(goodSuppDir);
        }
    }

    @Override
    public String toString() {
        if (sourceDirectories.length==1) {
            return "{" + getClass().getName() +" dirs="+sourceDirectories[0].getName()+" }";
        }
        List<String> fileList=new ArrayList<>(sourceDirectories.length);
        for (File f: sourceDirectories) {
            fileList.add(f.getName());
        }
        return  "{" + getClass().getName() +" dirs="+fileList+" }";
    }

    protected Set<String> handleGetAvailable() {
        return localeList;
    }

    /**
     * Make a CLDR file. The result is a locked file, so that it can be cached. If you want to modify it,
     * use clone().
     */
    @SuppressWarnings("unchecked")
    public CLDRFile handleMake(String localeName, boolean resolved, DraftStatus minimalDraftStatus) {
        @SuppressWarnings("rawtypes")
        final Map mapToSynchronizeOn;
        final File parentDir = getSourceDirectoryForLocale(localeName);
        final Object cacheKey;
        CLDRFile result; // result of the lookup / generation
        if (USE_OLD_HANDLEMAKE_CODE) {
            final Map<String, CLDRFile> cache = resolved ?
                resolvedCache[minimalDraftStatus.ordinal()] :
                mainCache[minimalDraftStatus.ordinal()];
            mapToSynchronizeOn = cache;
            cacheKey = localeName;
            result = cache.get(localeName);
        } else {
            // Use double-check idiom
            cacheKey = new CLDRCacheKey(localeName, resolved, minimalDraftStatus, parentDir);
            //        result = cache.get(localeName);
            //  result=combinedCache.asMap().get(cacheKey);
            result = combinedCache.getIfPresent(cacheKey);
            mapToSynchronizeOn = combinedCache.asMap();
        }
        if (result != null) {
            if (DEBUG_SIMPLEFACTORY) {
                System.out.println("HandleMake:Returning cached result for locale " + localeName);
            }
            return result;
        }
//        synchronized (cache) {
        synchronized (mapToSynchronizeOn) {
            // Check cache twice to ensure that CLDRFile is only loaded once
            // even with multiple threads.
            //            result = cache.get(localeName);
            //     result=combinedCache.get(cacheKey);
            if (result != null) {
                if (DEBUG_SIMPLEFACTORY) {
                    System.out.println("HandleMake:Returning cached result for locale " + localeName);
                }
                return result;
            }
            if (resolved) {
                result = new CLDRFile(makeResolvingSource(localeName, minimalDraftStatus));
            } else {
                if (parentDir != null) {
                    if (DEBUG_SIMPLEFACTORY) {
                        System.out.println("HandleMake: Calling makeFile with locale: "+localeName+
                            ", parentDir: "+parentDir.getAbsolutePath()+", DraftStatus: "+minimalDraftStatus);
                    }
                    result = makeFile(localeName, parentDir, minimalDraftStatus);
                    result.freeze();
                }
            }
            if (result != null) {
                mapToSynchronizeOn.put(cacheKey,result);
            }
            return result;
        }
    }

    /**
     * Produce a CLDRFile from a localeName, given a directory.
     * 
     * @param localeName
     * @param dir
     *            directory
     */
    // TODO make the directory a URL
    public static CLDRFile makeFromFile(String fullFileName, String localeName, DraftStatus minimalDraftStatus) {
        return makeFromFile(new File(fullFileName), localeName, minimalDraftStatus);
    }

    private static CLDRFile makeFromFile(File file, String localeName, DraftStatus minimalDraftStatus) {
        return CLDRFile.loadFromFile(file, localeName, minimalDraftStatus);
    }

    /**
     * Create a CLDRFile for the given localename.
     * 
     * @param localeName
     */
    public static CLDRFile makeSupplemental(String localeName) {
        XMLSource source = new SimpleXMLSource(localeName);
        CLDRFile result = new CLDRFile(source);
        result.setNonInheriting(true);
        return result;
    }

    /**
     * CLDRFile from a file input stream. Set the locale ID from the same input stream.
     * 
     * @param fileName
     * @param fis
     * @param minimalDraftStatus
     * @return
     */
    public static CLDRFile makeFile(String fileName, InputStream fis, CLDRFile.DraftStatus minimalDraftStatus) {
        CLDRFile file = CLDRFile.load(fileName, null, fis, minimalDraftStatus);
        return file;
    }

    /**
     * Produce a CLDRFile from a file input stream.
     * 
     * @param localeName
     * @param fis
     */
    public static CLDRFile makeFile(String fileName, String localeName, InputStream fis,
        CLDRFile.DraftStatus minimalDraftStatus) {
        return CLDRFile.load(fileName, localeName, fis, minimalDraftStatus);
    }

    public static CLDRFile makeFile(String localeName, String dir, CLDRFile.DraftStatus minimalDraftStatus) {
        return makeFile(localeName, new File(dir), minimalDraftStatus);
    }

    public static CLDRFile makeFile(String localeName, File dir, CLDRFile.DraftStatus minimalDraftStatus) {
        CLDRFile file = makeFromFile(makeFileName(localeName, dir), localeName, minimalDraftStatus);
        return file;
    }

    /**
     * @param localeName
     * @param dir
     * @return
     */
    private static File makeFileName(String localeName, File dir) {
        return new File(dir, localeName + ".xml");
    }

    /**
     * Create a CLDRFile for the given localename.
     * SimpleXMLSource will be used as the source.
     * 
     * @param localeName
     */
    public static CLDRFile makeFile(String localeName) {
        XMLSource source = new SimpleXMLSource(localeName);
        return new CLDRFile(source);
    }

    /**
     * Produce a CLDRFile from a localeName and filename, given a directory.
     * 
     * @param localeName
     * @param dir
     *            directory
     */
    public static CLDRFile makeFile(String localeName, String dir, boolean includeDraft) {
        return makeFile(localeName, dir, includeDraft ? CLDRFile.DraftStatus.unconfirmed
            : CLDRFile.DraftStatus.approved);
    }

    @Override
    public File[] getSourceDirectories() {
        return sourceDirectories;
    }

   
    
    @Override
    public File getSourceDirectoryForLocale(String localeName) {
        boolean isSupplemental = CLDRFile.isSupplementalName(localeName);
        for (File sourceDirectory : this.sourceDirectories) {
            if (isSupplemental) {
                sourceDirectory = new File(sourceDirectory.getAbsolutePath().
                    replace("incoming" + File.separator + "vetted" + File.separator, "common" + File.separator));
            }
            final File dir = isSupplemental ? new File(sourceDirectory, "../supplemental") : sourceDirectory;
            final File xmlFile = makeFileName(localeName, dir);
            if (xmlFile.canRead()) {
                return dir;
            }
        }
        return null;
    }

}