package org.unicode.cldr.draft;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.unicode.cldr.ant.CLDRConverterTool;
import org.unicode.cldr.util.Factory;
import org.unicode.cldr.util.SupplementalDataInfo;

import com.ibm.icu.dev.tool.UOption;

/**
 * Simpler mechanism for converting CLDR data to ICU Resource Bundles, intended
 * to replace LDML2ICUConverter. The format is almost entirely data-driven
 * instead of having lots of special-case code.
 *
 * The flags used to specify the data to be generated are copied directly from
 * LDML2ICUConverter.
 *
 * Unlike the instructions in CLDRConverterTool, this converter does not invoke
 * computeConvertibleXPaths to check if each xpath is convertible because the
 * xpaths that are convertible have already been filtered out by the regex lookups.
 * It may make more sense down the road to refactor CLDRConverterTool such that
 * this class doesn't inherit unnecessary functionality.
 * 
 * @author markdavis
 */
public class LDMLConverter extends CLDRConverterTool {
    static final Pattern SEMI = Pattern.compile("\\s*+;\\s*+");
    
    /**
     * These must be kept in sync with getOptions().
     */
    private static final int HELP1 = 0;
    private static final int HELP2 = 1;
    private static final int SOURCEDIR = 2;
    private static final int DESTDIR = 3;
    private static final int SPECIALSDIR = 4;
    private static final int SUPPLEMENTALDIR = 5;
    // Debugging: doesn't split up locale into separate directories.
    private static final int KEEP_TOGETHER = 6;
    private static final int PLURALS = 7;
    
    private static final String LOCALES_DIR = "locales";

    private boolean keepTogether = false;
    private Map<String, String> dirMapping;
    private Set<String> allDirs;
    private String destinationDir;

    /**
     * Maps ICU paths to the directories they should end up in.
     */
    private Map<String, String> getDirMapping() {
        if (dirMapping == null) {
            dirMapping = loadMapFromFile("ldml2icu_dir_mapping.txt");
            allDirs = new HashSet<String>(dirMapping.values());
            allDirs.remove("*");
            allDirs.add(LOCALES_DIR);
        }
        return dirMapping;
    }
    
    static Map<String, String> loadMapFromFile(String filename) {
        Map<String, String> map = new HashMap<String, String>();
        BufferedReader reader = FileUtilities.openFile(LDMLConverter.class, filename);
        String line;
        try {
            int lineNum = 1;
            while((line = reader.readLine()) != null) {
                if (line.length() == 0 || line.startsWith("#")) continue;
                String[] content = line.split(SEMI.toString());
                if (content.length != 2) {
                    throw new IllegalArgumentException("Invalid syntax of " + filename + " at line " + lineNum);
                }
                map.put(content[0], content[1]);
                lineNum++;
            }
        } catch(IOException e) {
            System.err.println("Failed to read fallback file.");
            e.printStackTrace();
        }
        return map;
    }

    private Map<String, Set<String>> mapDirToPaths(Set<String> paths) {
        Map<String, String> dirMapping = getDirMapping();
        Map<String, Set<String>> dirPaths = new HashMap<String, Set<String>>();
        dirPaths.put(LOCALES_DIR, new HashSet<String>());
        for (String path : paths) {
            boolean matched = false;
            for (String prefix : dirMapping.keySet()) {
                if (path.startsWith(prefix)) {
                    String dir = dirMapping.get(prefix);
                    // Handle wildcard folder.
                    if (dir.equals("*")) {
                        for (String currDir : allDirs) {
                            addToMap(currDir, path, dirPaths);
                        }
                    } else {
                        addToMap(dir, path, dirPaths);
                    }
                    matched = true;
                    break;
                }
                if (!matched) {
                    dirPaths.get(LOCALES_DIR).add(path);
                }
            }
        }
        return dirPaths;
    }
    
    private void addToMap(String key, String value, Map<String, Set<String>> map) {
        Set<String> set = map.get(key);
        if (set == null) {
            set = new HashSet<String>();
            map.put(key, set);
        }
        set.add(value);
    }
    
    private void usage() {
        System.out.println(
            "\nUsage: LDML2ICUConverter [OPTIONS] [FILES]\n" +
            "This program is used to convert LDML files to ICU data text files.\n" +
            "Please refer to the following options. Options are not case sensitive.\n" +
            "Options:\n" +
            "-s or --sourcedir          source directory for files followed by path, " +
                                       "default is current directory.\n" +
            "-d or --destdir            destination directory, followed by the path, "+
                                       "default is current directory.\n" +
            "-p or --specialsdir        source directory for files containing special data " +
                                       "followed by the path. None if not specified\n" +
            "-m or --suplementaldir     source directory for finding the supplemental data.\n" +
            "-k or --keeptogether       write locale data to one file instead of splitting.\n" +
            "-r or --plurals-only       read the plurals files from the supplemental directory and " +
                                       "write the output to the destination directory\n" +
            "-h or -? or --help         this usage text.\n" +
            "example: org.unicode.cldr.drafts.LDMLConverter -s xxx -d yyy en.xml");
        System.exit(-1);
    }

    @Override
    public void processArgs(String[] args) {
        UOption[] options = new UOption[] {
            UOption.HELP_H(),
            UOption.HELP_QUESTION_MARK(),
            UOption.SOURCEDIR(),
            UOption.DESTDIR(),
            UOption.create("specialsdir", 'p', UOption.REQUIRES_ARG),
            UOption.create("supplementaldir", 'm', UOption.REQUIRES_ARG),
            UOption.create("keeptogether", 'k', UOption.NO_ARG),
            UOption.create("plurals-only", 'r', UOption.NO_ARG)
        };

        int remainingArgs = 0;
        try {
            remainingArgs = UOption.parseArgs(args, options);
        } catch (Exception e) {
            System.out.println("Error parsing args: " + e.getMessage());
            e.printStackTrace();
            usage();
        }
        if (args.length == 0 || options[HELP1].doesOccur || options[HELP2].doesOccur) {
            usage();
        }

        // plurals.txt only requires the supplemental directory to be specified.
        boolean sourceDirRequired = !options[PLURALS].doesOccur;
        if (sourceDirRequired && !options[SOURCEDIR].doesOccur) {
            throw new IllegalArgumentException("Source directory must be specified.");
        }
        String sourceDir = options[SOURCEDIR].value;

        SupplementalDataInfo supplementalDataInfo = null;
        if (options[SUPPLEMENTALDIR].doesOccur) {
            supplementalDataInfo = SupplementalDataInfo.getInstance(options[SUPPLEMENTALDIR].value);
        } else {
            throw new IllegalArgumentException("Supplemental directory must be specified.");
        }

        destinationDir = options[DESTDIR].doesOccur ? options[DESTDIR].value : ".";
        Factory specialFactory = null;
        if (options[SPECIALSDIR].doesOccur) {
            specialFactory = Factory.make(options[SPECIALSDIR].value, ".*");
        }
        keepTogether = options[KEEP_TOGETHER].doesOccur;

        // Process files.
        if (options[PLURALS].doesOccur) {
            processPlurals(supplementalDataInfo);
        } else {
            // LocalesMap passed in from ant
            Set<String> locales = new HashSet<String>();
            Factory factory = null;
            if (getLocalesMap() != null && getLocalesMap().size() > 0) {
                for (String filename : getLocalesMap().keySet()) {
                    // Remove ".xml" from the end.
                    locales.add(filename.substring(0, filename.length() - 4));
                }
                factory = Factory.make(sourceDir, ".*");
            } else if (remainingArgs > 0) {
                factory = Factory.make(sourceDir, args[0]);
                locales.addAll(factory.getAvailable());
            } else {
                throw new IllegalArgumentException("No files specified!");
            }

            LdmlLocaleMapper mapper = new LdmlLocaleMapper(factory, specialFactory, supplementalDataInfo);
            processLocales(mapper, locales);
        }
    }
    
    private void processPlurals(SupplementalDataInfo supplementalDataInfo) {
        PluralsMapper mapper = new PluralsMapper(supplementalDataInfo);
        writeIcuData(mapper.fillFromCldr(), destinationDir);
    }
    
    private static void writeIcuData(IcuData icuData, String outputDir) {
        try {
            IcuTextWriter.writeToFile(icuData, outputDir);
        } catch (IOException e) {
            System.err.println("Error while converting " + icuData.getSourceFile());
            e.printStackTrace();
        }
    }

    private void processLocales(LdmlLocaleMapper mapper, Set<String> locales) {
        for (String locale : locales) {
            long time = System.currentTimeMillis();
            IcuData icuData = mapper.fillFromCLDR(locale);
            if (keepTogether) {
                writeIcuData(icuData, destinationDir);
            } else {
                Map<String, Set<String>> dirPaths = mapDirToPaths(icuData.keySet());
                for (String dir : dirPaths.keySet()) {
                    IcuData dirData = new IcuData("common/main/" + locale + ".xml", locale);
                    Set<String> paths = dirPaths.get(dir);
                    for (String path : paths) {
                        dirData.addAll(path, icuData.get(path));
                    }
                    writeIcuData(dirData, destinationDir + '/' + dir);
                }
            }
            System.out.println("Converted " + locale + ".xml in " + (System.currentTimeMillis() - time) + "ms");
        }
    }

    /**
     * In this prototype, just convert one file.
     * 
     * @param args
     * @throws IOException
     */
    public static void main(String[] args) throws IOException {
        long totalTime = System.currentTimeMillis();
        LDMLConverter converter = new LDMLConverter();
        converter.processArgs(args);
        System.out.println("Total time taken: " + (System.currentTimeMillis() - totalTime));
    }
}
