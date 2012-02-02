package org.unicode.cldr.util;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.unicode.cldr.util.CLDRFile.DraftStatus;

public class SimpleFactory extends Factory {
    /**
     * The maximum cache size the caches in 
     * 15 is a safe limit for instances with limited amounts of memory (around 128MB).
     * Larger numbers are tolerable if more memory is available.
     * This constant may be moved to CldrUtilities in future if needed.
     */
    private static final int CACHE_LIMIT = 15;

    private String sourceDirectories[];
    private String matchString;
    private Set<String> localeList = new TreeSet<String>();
    private Map<String,CLDRFile>[] mainCache = new Map[DraftStatus.values().length];
    private Map<String,CLDRFile>[] resolvedCache = new Map[DraftStatus.values().length];
    {
        for (int i = 0; i < mainCache.length; ++i) {
            mainCache[i] = new LruMap<String, CLDRFile>(CACHE_LIMIT);
            resolvedCache[i] = new LruMap<String, CLDRFile>(CACHE_LIMIT);
        }
    }

    private DraftStatus minimalDraftStatus = DraftStatus.unconfirmed;
    private SimpleFactory() {}

    protected DraftStatus getMinimalDraftStatus() {
        return minimalDraftStatus;
    }

    /**
     * Create a factory from a source directory, matchingString, and an optional log file.
     * For the matchString meaning, see {@link getMatchingXMLFiles}
     */
    public static Factory make(String sourceDirectory, String matchString) {
        return make(sourceDirectory, matchString, DraftStatus.unconfirmed);
    }

    public static Factory make(String sourceDirectory, String matchString, DraftStatus minimalDraftStatus) {
        String list[] = { sourceDirectory };
        return new SimpleFactory(list, matchString, minimalDraftStatus);
    }

    /**
     * Create a factory from a source directory list, matchingString, and an optional log file.
     * For the matchString meaning, see {@link getMatchingXMLFiles}
     */
    public static Factory make(String sourceDirectory[], String matchString) {
        return make(sourceDirectory, matchString, DraftStatus.unconfirmed);
    }

    /**
     * Create a factory from a source directory list
     * @param sourceDirectory
     * @param matchString
     * @param minimalDraftStatus
     * @return
     */
    public static Factory make(String sourceDirectory[], String matchString, DraftStatus minimalDraftStatus) {
        return new SimpleFactory(sourceDirectory, matchString, minimalDraftStatus);
    }

    private SimpleFactory(String sourceDirectory[], String matchString, DraftStatus minimalDraftStatus) {
        this.sourceDirectories = sourceDirectory;
        this.matchString = matchString;
        this.minimalDraftStatus = minimalDraftStatus;
        Matcher m = Pattern.compile(matchString).matcher("");
        this.localeList = CLDRFile.getMatchingXMLFiles(sourceDirectory, m);
        File goodSuppDir = null;
        for(String sourceDirectoryPossibility : sourceDirectory) {
            File suppDir = new File(sourceDirectoryPossibility, "../supplemental");
            if(suppDir.isDirectory()) {
                goodSuppDir = suppDir;
                break;
            }
        }
        if(goodSuppDir!=null) {
            setSupplementalDirectory(goodSuppDir);
        }
    }


    protected Set<String> handleGetAvailable() {
        return localeList;
    }

    /**
     * Make a CLDR file. The result is a locked file, so that it can be cached. If you want to modify it,
     * use clone().
     */
    public CLDRFile handleMake(String localeName, boolean resolved, DraftStatus minimalDraftStatus) {
        Map<String,CLDRFile> cache = resolved ? resolvedCache[minimalDraftStatus.ordinal()] : mainCache[minimalDraftStatus.ordinal()];

        CLDRFile result = cache.get(localeName);
        if (result == null) {
            if (resolved) {
                result = new CLDRFile(makeResolvingSource(localeName, minimalDraftStatus));
            } else {
                for(String sourceDirectory : this.sourceDirectories) {
                    final String dir = CLDRFile.isSupplementalName(localeName) ? sourceDirectory.replace("incoming/vetted/","common/") + File.separator + "../supplemental/" : sourceDirectory;
                    final File parentDir = new File(dir);
                    final File xmlFile = makeFileName(localeName,parentDir);
                    if(xmlFile.canRead()) {
                        result = makeFile(localeName, dir, minimalDraftStatus);
                        result.freeze();
                        break;
                    }
                }
                if(result==null) {
                    throw (IllegalArgumentException)new IllegalArgumentException("Can't find " + localeName + ".xml  in any of  " + getSourceDirectoriesList());
                }
            }
            cache.put(localeName, result);
        }
        return result;
    }

    /**
     * @return 
     * 
     */
    private StringBuilder getSourceDirectoriesList() {
        StringBuilder dirs = new StringBuilder();
        for(String dir : sourceDirectories) {
            if(dirs.length()!=0) {
                dirs.append(File.pathSeparator);
            }
            dirs.append(dir);
        }
        return dirs;
    }
    
    /**
     * TODO: note, returns only the first source directory.
     */
    @Override
    public String getSourceDirectory() {
        return sourceDirectories[0];
    }

    /**
     * Produce a CLDRFile from a localeName, given a directory.
     * @param localeName
     * @param dir directory 
     */
    // TODO make the directory a URL  
    public static CLDRFile makeFromFile(String fullFileName, String localeName, DraftStatus minimalDraftStatus) {
        return makeFromFile(new File(fullFileName),localeName,minimalDraftStatus);
    }

    private static CLDRFile makeFromFile(File file, String localeName, DraftStatus minimalDraftStatus) {
        return CLDRFile.loadFromFile(file, localeName, minimalDraftStatus);
    }

    /**
     * Create a CLDRFile for the given localename.
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
     * @param localeName
     * @param fis
     */
    public static CLDRFile makeFile(String fileName, String localeName, InputStream fis, CLDRFile.DraftStatus minimalDraftStatus) {
        return CLDRFile.load(fileName,localeName, fis, minimalDraftStatus);
    }

    public static CLDRFile makeFile(String localeName, String dir, CLDRFile.DraftStatus minimalDraftStatus) {
        CLDRFile file = makeFromFile(makeFileName(localeName, new File(dir)), localeName, minimalDraftStatus);
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
     * @param localeName
     */
    public static CLDRFile makeFile(String localeName) {
        XMLSource source = new SimpleXMLSource(localeName);
        return new CLDRFile(source);
    }

    /**
     * Produce a CLDRFile from a localeName and filename, given a directory.
     * @param localeName
     * @param dir directory 
     */
    public static CLDRFile makeFile(String localeName, String dir, boolean includeDraft) {
        return makeFile(localeName, dir, includeDraft ? CLDRFile.DraftStatus.unconfirmed : CLDRFile.DraftStatus.approved);
    }

}