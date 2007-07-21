/*
 ******************************************************************************
 * Copyright (C) 2004, 2007 International Business Machines Corporation and   *
 * others. All Rights Reserved.                                               *
 ******************************************************************************
 */
/**
 * @author Ram Viswanadha
 */

package org.unicode.cldr.icu;

import com.ibm.icu.dev.test.util.ElapsedTimer;
import com.ibm.icu.dev.tool.UOption;
import com.ibm.icu.impl.Utility;
import com.ibm.icu.text.Normalizer;
import com.ibm.icu.text.SimpleDateFormat;
import com.ibm.icu.text.UCharacterIterator;
import com.ibm.icu.text.UTF16;
import com.ibm.icu.text.UnicodeSet;
import com.ibm.icu.util.ULocale;

import org.unicode.cldr.util.LDMLUtilities;
import org.unicode.cldr.util.CLDRFile;
import org.unicode.cldr.util.SupplementalDataInfo;
import org.unicode.cldr.util.XPathParts;
import org.unicode.cldr.util.XPathTokenizer;
import org.unicode.cldr.ant.CLDRConverterTool;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.NodeList;

import java.io.*;
import java.text.ParseException;
import java.util.*;

import javax.xml.transform.TransformerException;

public class LDML2ICUConverter extends CLDRConverterTool {
    /**
     * These must be kept in sync with getOptions().
     */
    private static final int HELP1 = 0;
    private static final int HELP2 = 1;
    private static final int SOURCEDIR = 2;
    private static final int DESTDIR = 3;
    private static final int SPECIALSDIR = 4;
    private static final int WRITE_DEPRECATED = 5;
    private static final int WRITE_DRAFT = 6;
    private static final int SUPPLEMENTALDIR = 7;
    private static final int SUPPLEMENTALONLY = 8;
    private static final int VERBOSE = 9;

    private static final UOption[] options = new UOption[] { UOption.HELP_H(),
            UOption.HELP_QUESTION_MARK(), UOption.SOURCEDIR(),
            UOption.DESTDIR(),
            UOption.create("specialsdir", 'p', UOption.REQUIRES_ARG),
            UOption.create("write-deprecated", 'w', UOption.REQUIRES_ARG),
            UOption.create("write-draft", 'f', UOption.NO_ARG),
            UOption.create("supplementaldir", 'm', UOption.REQUIRES_ARG),
            UOption.create("supplemental-only", 'l', UOption.NO_ARG),
            UOption.VERBOSE(), };

    private String sourceDir = null;
    private String fileName = null;
    private String destDir = null;
    private String specialsDir = null;
    private String supplementalDir = null;
    private boolean writeDeprecated = false;
    private boolean writeDraft = false;
    private boolean writeSupplemental = false;
    private boolean verbose = false;
    /**
     * Add comments on the item to indicate where fallbacks came from. Good for
     * information, bad for diffs.
     */
    private static boolean verboseFallbackComments = false;

    private static final String LINESEP = System.getProperty("line.separator");
    private static final String BOM = "\uFEFF";
    private static final String CHARSET = "UTF-8";
    private static final String DEPRECATED_LIST = "icu-config.xml & build.xml";

    private Document fullyResolvedDoc = null;
    private Document specialsDoc = null;
    private String locName = null;
    private Document supplementalDoc = null;
    private static final boolean DEBUG = false;

    // TreeMap overrideMap = new TreeMap(); // list of locales to take
    // regardless of draft status. Written by writeDeprecated

    public static void main(String[] args) {
        LDML2ICUConverter cnv = new LDML2ICUConverter();
        cnv.processArgs(args);
    }

    private void usage() {
        System.out
                .println("\nUsage: LDML2ICUConverter [OPTIONS] [FILES]\nLDML2ICUConverter [OPTIONS] -w [DIRECTORY] \n"
                        + "This program is used to convert LDML files to ICU ResourceBundle TXT files.\n"
                        + "Please refer to the following options. Options are not case sensitive.\n"
                        + "Options:\n"
                        + "-s or --sourcedir          source directory for files followed by path, default is current directory.\n"
                        + "-d or --destdir            destination directory, followed by the path, default is current directory.\n"
                        + "-p or --specialsdir        source directory for files containing special data followed by the path. None if not spcified\n"
                        + "-f or --write-draft        write data for LDML nodes marked draft.\n"
                        + "-m or --suplementaldir     source directory for finding the supplemental data.\n"
                        + "-l or --supplemental-only  read supplementalData.xml file from the given directory and write appropriate files to destination directory\n"
                        + "-w [dir] or --write-deprecated [dir]   write data for deprecated locales. 'dir' is a directory of source xml files.\n"
                        + "-h or -? or --help         this usage text.\n"
                        + "-v or --verbose            print out verbose output.\n"
                        + "example: org.unicode.cldr.icu.LDML2ICUConverter -s xxx -d yyy en.xml");
        System.exit(-1);
    }

    private void printInfo(String message) {
        if (verbose) {
            System.out.println("INFO : " + message);
        }
    }

    private void printXPathWarning(InputLocale loc, String xpath) {
        // int len = xpath.length();
        // getXPath(node, xpath);
        System.err.println("WARNING : Not producing resource for : "
                + xpath.toString());
        // xpath.setLength(len);
    }

    /**
     * @deprecated
     * @param node
     * @param xpath
     */
    private void printXPathWarning(Node node, StringBuffer xpath) {
        int len = xpath.length();
        getXPath(node, xpath);
        System.err.println("WARNING : Not producing resource for : "
                + xpath.toString());
        xpath.setLength(len);
    }

    private void printWarning(String fileName, String message) {
        System.err.println(fileName + ": WARNING : " + message);
    }

    private void printError(String fileName, String message) {
        System.err.println(fileName + ": ERROR : " + message);
    }

    public void processArgs(String[] args) {
        int remainingArgc = 0;
        // for some reason when
        // Class classDefinition = Class.forName(className);
        // object = classDefinition.newInstance();
        // is done then the options are not reset!!
        for (int i = 0; i < options.length; i++) {
            options[i].doesOccur = false;
        }
        try {
            remainingArgc = UOption.parseArgs(args, options);
        } catch (Exception e) {
            printError("", "(parsing args): " + e.toString());
            e.printStackTrace();
            usage();
        }
        if (args.length == 0 || options[HELP1].doesOccur
                || options[HELP2].doesOccur) {
            usage();
        }

        if (options[SOURCEDIR].doesOccur) {
            sourceDir = options[SOURCEDIR].value;
        }
        if (options[DESTDIR].doesOccur) {
            destDir = options[DESTDIR].value;
        }
        if (options[SPECIALSDIR].doesOccur) {
            specialsDir = options[SPECIALSDIR].value;
        }
        if (options[WRITE_DRAFT].doesOccur) {
            writeDraft = true;
        }
        if (options[SUPPLEMENTALDIR].doesOccur) {
            supplementalDir = options[SUPPLEMENTALDIR].value;
        }
        if (options[SUPPLEMENTALONLY].doesOccur) {
            writeSupplemental = true;
        }
        if (options[VERBOSE].doesOccur) {
            verbose = true;
        }
        if (destDir == null) {
            destDir = ".";
        }
        if (options[WRITE_DEPRECATED].doesOccur) {
            writeDeprecated = true;
            if (remainingArgc > 0) {
                printError("",
                        "-w takes one argument, the directory, and no other XML files.\n");
                usage();
                return; // NOTREACHED
            }
            writeDeprecated();
            return;
        }
        // if((writeDraft == false) && (specialsDir != null)) {
        // printInfo("Reading alias table searching for draft overrides");
        // writeDeprecated(); // actually just reads the alias
        // }
        if (remainingArgc == 0
                && (localesMap == null || localesMap.size() == 0)) {
            printError("",
                    "No files specified for processing. Please check the arguments and try again");
            usage();
        }

        if (supplementalDir != null) {
            // supplementalFileName =
            // LDMLUtilities.getFullPath(LDMLUtilities.XML, "supplementalData",
            // supplementalDir);
            supplementalDoc = createSupplementalDoc();
            supplementalDataInfo = SupplementalDataInfo
                    .getInstance(supplementalDir);
        }
        if (writeSupplemental == true) {
            if (false != true) {
                System.err.println("THIS broken- writesupp");
                return;
            }
            if (true != false)
                throw new InternalError("TODO: this broken");
            // makeXPathList(supplementalDoc);
            // Create the Resource linked list which will hold the
            // data after parsing
            // The assumption here is that the top
            // level resource is always a table in ICU
            // TODO: hard code the file name for now
            String fileName = "supplementalData.xml";
            System.out.println("Processing: " + fileName);
            ICUResourceWriter.Resource res = parseSupplemental(supplementalDoc,
                    fileName);

            if (res != null
                    && ((ICUResourceWriter.ResourceTable) res).first != null) {
                // write out the bundle
                writeResource(res, fileName);
            }
        } else {
            if (localesMap != null && localesMap.size() > 0) {
                for (Iterator iter = localesMap.keySet().iterator(); iter
                        .hasNext();) {
                    String fileName = (String) iter.next();
                    String draft = (String) localesMap.get(fileName);
                    if (draft != null && !draft.equals("false")) {
                        writeDraft = true;
                    } else {
                        writeDraft = false;
                    }
                    processFile(fileName);
                }
            } else if (remainingArgc > 0) {
                for (int i = 0; i < remainingArgc; i++) {
                    processFile(args[i]);
                }
            } else {
                printError("", " No files specified!");
            }
        }
    }

    private List<String> xpathList = new ArrayList<String>();

    CLDRFile.Factory cldrFactory = null;
    CLDRFile.Factory specialsFactory = null;
    Set<String> allLocales = new TreeSet<String>();
    SupplementalDataInfo supplementalDataInfo = null;

    private class InputLocale {
        String locale;
        CLDRFile rawFile = null;
        CLDRFile file;
        CLDRFile specialsFile = null;
        private CLDRFile fResolved = null;

        public CLDRFile resolved() {
            if (fResolved == null) {
                // System.err.println("** spinning up resolved for " + locale);
                fResolved = cldrFactory.make(locale, true);
            }
            return fResolved;
        }

        InputLocale(CLDRFile fromFile) {
            rawFile = file = fResolved = fromFile;
            locale = file.getLocaleID();
        }

        InputLocale(String locale) {
            this.locale = locale;
            rawFile = cldrFactory.make(locale, false);
            if (specialsFactory != null) {
                String icuSpecialFile = specialsDir + "/" + locale + ".xml";
                if (new File(icuSpecialFile).exists()) {
                    printInfo("Parsing ICU specials from: " + icuSpecialFile);
                    specialsFile = specialsFactory.make(locale, false);
                    file = (CLDRFile) rawFile.cloneAsThawed();
                    file.putAll(specialsFile, CLDRFile.MERGE_REPLACE_MINE);
                } else {
                    file = rawFile;
                }
            } else {
                file = rawFile; // frozen
            }
        }

        private XPathParts xpp = new XPathParts(null, null);

        Set<String> getByType(String baseXpath, String element) {
            return getByType(baseXpath, element, LDMLConstants.TYPE);
        }

        Set<String> getByType(String baseXpath, String element, String attribute) {
            Set<String> typeList = new HashSet<String>();
            for (Iterator<String> iter = file.iterator(baseXpath); iter
                    .hasNext();) {
                String somePath = iter.next();
                String type = getAttributeValue(somePath, element, attribute);
                if (type == null) {
                    continue;
                } else {
                    typeList.add(type);
                }
            }
            return typeList;
        }

        // convenience functions
        String getXpathName(String xpath) {
            xpp.set(xpath);
            return xpp.getElement(-1);
        }

        String getAttributeValue(String xpath, String element, String attribute) {
            xpp.set(xpath);
            int el = xpp.findElement(element);
            if (el == -1) {
                return null;
            } else {
                return xpp.getAttributeValue(el, attribute);
            }
        }

        String getAttributeValue(String xpath, String attribute) {
            xpp.set(xpath);
            return xpp.getAttributeValue(-1, attribute);
        }

        String getBasicAttributeValue(String xpath, String attribute) {
            String fullPath = file.getFullXPath(xpath);
            if (fullPath == null) {
                // System.err.println("No full path for " + xpath);
                return null;
            } else {
                // System.err.println(" >>> " + fullPath);
            }
            return getAttributeValue(fullPath, attribute);
        }

        String getBasicAttributeValue(CLDRFile whichFile, String xpath,
                String attribute) {
            String fullPath = whichFile.getFullXPath(xpath);
            if (fullPath == null) {
                // System.err.println("No full path for " + xpath);
                return null;
            } else {
                // System.err.println(" >>> " + fullPath);
            }
            return getAttributeValue(fullPath, attribute);
        }

        String findAttributeValue(String xpath, String attribute) {
            String fullPath = file.getFullXPath(xpath);
            xpp.set(fullPath);
            for (int j = 1; j <= xpp.size(); j++) {
                String v = xpp.getAttributeValue(0 - j, attribute);
                if (v != null)
                    return v;
            }
            return null;
        }

        String getResolvedString(String xpath) {
            String rv = file.getStringValue(xpath);
            if (rv == null) {
                rv = resolved().getStringValue(xpath);
                // System.err.println("Fallin back:" + xpath + " -> " + rv);
            }
            return rv;
        }

        Set<String> alreadyDone = new HashSet<String>();

        /**
         * Determine whether a particular section has been done
         * 
         * @param where
         *            the name of the section, i.e. LDMLConstants.IDENTITY
         * @return true if this part has already been processed, otherwise
         *         false. If false, it will return true the next time called.
         */
        boolean beenHere(String where) {
            if (alreadyDone.contains(where)) {
                return true;
            } else {
                alreadyDone.add(where);
                return false;
            }
        }

        boolean isPathNotConvertible(String xpath) {
            String alt = getBasicAttributeValue(xpath, "alt");
            if (alt != null) {
                return true;
            } else {
                return false;
            }
        }

        boolean isPathNotConvertible(CLDRFile file, String xpath) {
            String alt = getBasicAttributeValue(file, xpath, "alt");
            if (alt != null) {
                return true;
            } else {
                return false;
            }
        }
    }

    private void processFile(String fileName) {
        int lastIndex = fileName.lastIndexOf(File.separator, fileName.length()) + 1 /*
                                                                                     * add
                                                                                     * 1 to
                                                                                     * skip
                                                                                     * past
                                                                                     * the
                                                                                     * separator
                                                                                     */;
        fileName = fileName.substring(lastIndex, fileName.length());
        String xmlfileName = LDMLUtilities.getFullPath(LDMLUtilities.XML,
                fileName, sourceDir);

        locName = fileName;
        int index = locName.indexOf(".xml");
        if (index > -1) {
            locName = locName.substring(0, index);
        }

        if (cldrFactory == null) {
            System.out.println("* Spinning up CLDRFactory on " + sourceDir);
            cldrFactory = CLDRFile.Factory.make(sourceDir, ".*");
            if (specialsDir != null) {
                System.out.println("* Spinning up specials CLDRFactory on "
                        + specialsDir);
                specialsFactory = CLDRFile.Factory.make(specialsDir, ".*");
            }
            allLocales = cldrFactory.getAvailable();
        }

        // if (CLDRFile.isSupplementalName(localeID)) continue;
        // if
        // (supplementalDataInfo.getDefaultContentLocales().contains(localeID))
        // {
        // boolean isLanguageLocale =
        // localeID.equals(localeIDParser.set(localeID).getLanguageScript());

        System.out.println("Processing: " + xmlfileName);
        ElapsedTimer timer = new ElapsedTimer();
        InputLocale loc = new InputLocale(locName);

        // printInfo("Parsing: "+xmlfileName);
        String icuSpecialFile = "";
        if (specialsDir != null) {
            icuSpecialFile = specialsDir + "/" + fileName;
            if (!new File(icuSpecialFile).exists()) {
                if (ULocale.getCountry(locName).length() == 0) {
                    printWarning(icuSpecialFile,
                            "ICU special not found for language-locale \""
                                    + locName + "\"");
                    // System.exit(-1);
                } else {
                    printInfo("ICU special " + icuSpecialFile
                            + " not found, continuing.");
                }
                specialsDoc = null;
            }

        }

        if ((writeDraft == false) && (isDraftStatusOverridable(locName))) {
            printInfo("Overriding draft status, and including: " + locName);
            writeDraft = true;
            // TODO: save/restore writeDraft
        }
        // System.out.println("Creating the resource bundle.");
        createResourceBundle(loc);
        printInfo("Elapsed time: " + timer + "s");
    }

    private void makeXPathList(Node node, StringBuffer xpath) {
        if (xpath == null) {
            xpath = new StringBuffer("/");
        }
        for (Node child = node.getFirstChild(); child != null; child = child
                .getNextSibling()) {
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            String name = child.getNodeName();
            int savedLength = xpath.length();
            xpath.append("/");
            xpath.append(name);
            LDMLUtilities.appendXPathAttribute(child, xpath, false, false);
            if (name.equals("collation")) {
                // special case for collation: draft attribute is set on the top
                // level element
                xpathList.add(xpath.toString());
            } else if (LDMLUtilities.areChildrenElementNodes(child)) {
                makeXPathList(child, xpath);
            } else {
                // reached leaf node add to list
                xpathList.add(xpath.toString());
            }
            xpath.delete(savedLength, xpath.length());
        }
    }

    private void makeXPathList(CLDRFile file) {
        Document doc = null;
        if (doc == null) {
            return;
        }
        makeXPathList((Node) doc, null);
        makeXPathList((Node) supplementalDoc, null);
        Collections.sort(xpathList);
        if (DEBUG) {
            try {
                PrintWriter log1 = new PrintWriter(new FileOutputStream(
                        "log1.txt"));
                log1.println("BEGIN: Before computeConvertibleXPaths");
                for (int i = 0; i < xpathList.size(); i++) {
                    log1.println((String) xpathList.get(i));
                }
                log1.println("END: Before computeConvertibleXPaths");
                log1.flush();
                log1.close();
            } catch (Exception ex) {
                // debugging throw away.
            }
        }
        // Ok now figure out which XPaths should be converted
        xpathList = computeConvertibleXPaths(xpathList,
                exemplarsContainAZ(fullyResolvedDoc), locName, supplementalDir);
        if (DEBUG) {
            try {
                PrintWriter log2 = new PrintWriter(new FileOutputStream(
                        "log2.txt"));
                log2.println("BEGIN: After computeConvertibleXPaths");
                for (int i = 0; i < xpathList.size(); i++) {
                    log2.println((String) xpathList.get(i));
                }
                log2.println("END: After computeConvertibleXPaths");
                log2.flush();
                log2.close();
            } catch (Exception ex) {
                // debugging throw away.
            }
        }
    }

    private boolean exemplarsContainAZ(Document fullyResolvedDoc) {
        if (fullyResolvedDoc == null) {
            return false;
        }
        Node node = LDMLUtilities.getNode(fullyResolvedDoc,
                "//ldml/characters/exemplarCharacters");
        if (node == null) {
            return false;
        }
        String ex = LDMLUtilities.getNodeValue(node);
        UnicodeSet set = new UnicodeSet(ex);
        return set.containsAll(new UnicodeSet("[A-Z a-z]"));
    }

    private Document createSupplementalDoc() {

        FilenameFilter filter = new FilenameFilter() {
            public boolean accept(File dir, String name) {
                if (name.matches(".*\\.xml") && !name.equals("characters.xml")) {
                    return true;
                }
                return false;
            }
        };
        File myDir = new File(supplementalDir);
        String[] files = myDir.list(filter);
        Document doc = null;
        for (int i = 0; i < files.length; i++) {
            try {
                printInfo("Parsing document " + files[i]);
                String fileName = myDir.getAbsolutePath() + File.separator
                        + files[i];
                Document child = LDMLUtilities.parse(fileName, false);
                if (doc == null) {
                    doc = child;
                    continue;
                }
                StringBuffer xpath = new StringBuffer();
                LDMLUtilities.mergeLDMLDocuments(doc, child, xpath, files[i],
                        myDir.getAbsolutePath(), true, false);
            } catch (Throwable se) {
                printError(fileName, "Parsing: " + files[i] + " "
                        + se.toString());
                se.printStackTrace();
                System.exit(1);
            }
        }
        return doc;
    }

    private void createResourceBundle(InputLocale loc) {
        try {
            // calculate the list of vettable xpaths.
            // TODO: for now, 'all'
            makeXPathList(loc.file);
            // Create the Resource linked list which will hold the
            // data after parsing
            // The assumption here is that the top
            // level resource is always a table in ICU
            ICUResourceWriter.Resource res = parseBundle(loc);
            if (res != null
                    && ((ICUResourceWriter.ResourceTable) res).first != null) {
                if (loc.specialsFile != null) {
                    String dir = specialsDir.replace('\\', '/');
                    dir = "<path>"
                            + dir.substring(dir.indexOf("/xml"), dir.length());
                    if (res.comment == null) {
                        res.comment = " ICU <specials> source: " + dir + "/"
                                + locName + ".xml";
                    } else {
                        res.comment = res.comment + " ICU <specials> source: "
                                + dir + "/" + locName + ".xml";
                    }
                }
                // write out the bundle
                writeResource(res, loc.locale);
            }

            // writeAliasedResource();
        } catch (Throwable se) {
            printError(loc.locale, "(parsing and writing) " + se.toString());
            se.printStackTrace();
            System.exit(1);
        }
    }

    /*
     * private void createAliasedResource(Document doc, String xmlfileName,
     * String icuSpecialFile){ if(locName==null || writeDeprecated==false){
     * return; } String lang = null; // REMOVE //String lang = (String)
     * deprecatedMap.get(ULocale.getLanguage(locName)); //System.out.println("In
     * aliased resource"); if(lang!=null){ ICUResourceWriter.ResourceTable table =
     * new ICUResourceWriter.ResourceTable(); ICUResourceWriter.ResourceString
     * str = new ICUResourceWriter.ResourceString(); str.name = "\"%%ALIAS\"";
     * if(lang.indexOf("_")<0){ table.name = lang; String c =
     * ULocale.getCountry(locName); if(c!=null && c.length()>0){ table.name =
     * lang + "_" + c; } str.val = locName; }else{ table.name = lang; str.val =
     * ULocale.getLanguage(locName); } table.first = str; writeResource(table,
     * ""); } //System.out.println("exiting aliased resource"); }
     */
    private static final String LOCALE_SCRIPT = "LocaleScript";
    private static final String NUMBER_ELEMENTS = "NumberElements";
    private static final String NUMBER_PATTERNS = "NumberPatterns";
    private static final String AM_PM_MARKERS = "AmPmMarkers";
    private static final String DTP = "DateTimePatterns";
    public static final String DTE = "DateTimeElements";

    private static Map<String, String> keyNameMap = new TreeMap<String, String>();
    private static final Map<String, String> deprecatedTerritories = new TreeMap<String, String>(); // TODO:
                                                                                                    // should
                                                                                                    // be a
                                                                                                    // set?
    static {
        keyNameMap.put("days", "dayNames");
        keyNameMap.put("months", "monthNames");
        keyNameMap.put("territories", "Countries");
        keyNameMap.put("languages", "Languages");
        keyNameMap.put("currencies", "Currencies");
        keyNameMap.put("variants", "Variants");
        keyNameMap.put("scripts", "Scripts");
        keyNameMap.put("keys", "Keys");
        keyNameMap.put("types", "Types");
        keyNameMap.put("version", "Version");
        keyNameMap.put("exemplarCharacters", "ExemplarCharacters");
        keyNameMap.put("auxiliary", "AuxExemplarCharacters");
        keyNameMap.put("timeZoneNames", "zoneStrings");
        keyNameMap.put("localizedPatternChars", "localPatternChars");
        keyNameMap.put("paperSize", "PaperSize");
        keyNameMap.put("measurementSystem", "MeasurementSystem");
        keyNameMap.put("measurementSystemNames", "measurementSystemNames");
        keyNameMap.put("fractions", "CurrencyData");
        keyNameMap.put("quarters", "quarters");
        keyNameMap.put("displayName", "dn");
        keyNameMap.put("icu:breakDictionaryData", "BreakDictionaryData");
        deprecatedTerritories.put("BQ", "");
        deprecatedTerritories.put("CT", "");
        deprecatedTerritories.put("DD", "");
        deprecatedTerritories.put("FQ", "");
        deprecatedTerritories.put("FX", "");
        deprecatedTerritories.put("JT", "");
        deprecatedTerritories.put("MI", "");
        deprecatedTerritories.put("NQ", "");
        deprecatedTerritories.put("NT", "");
        deprecatedTerritories.put("PC", "");
        deprecatedTerritories.put("PU", "");
        deprecatedTerritories.put("PZ", "");
        deprecatedTerritories.put("SU", "");
        deprecatedTerritories.put("VD", "");
        deprecatedTerritories.put("WK", "");
        deprecatedTerritories.put("YD", "");
        // TODO: "FX", "RO", "TP", "ZR", /* obsolete country codes */
    }
    private static final String commentForCurrencyMeta = "Currency metadata.  Unlike the \"Currencies\" element, this is \n"
            + "NOT true locale data.  It exists only in root.  The two \n"
            + "integers are the fraction digits for each currency, and the \n"
            + "rounding increment.  The fraction digits must be an integer \n"
            + "from 0..9.  If there is no rounding, the rounding increment is \n"
            + "zero.  Otherwise the rounding increment is given in units of \n"
            + "10^(-fraction_digits).  The special tag \"DEFAULT\" gives the \n"
            + "meta data for all currencies not otherwise listed.";

    private static final String commentForCurrencyMap = "Map from ISO 3166 country codes to ISO 4217 currency codes \n"
            + "NOTE: This is not true locale data; it exists only in ROOT";

    private ICUResourceWriter.Resource parseSupplemental(Node root, String file) {
        ICUResourceWriter.ResourceTable table = new ICUResourceWriter.ResourceTable();
        ICUResourceWriter.Resource current = null;
        StringBuffer xpath = new StringBuffer();
        xpath.append("//");
        xpath.append(LDMLConstants.SUPPLEMENTAL_DATA);
        table.name = LDMLConstants.SUPPLEMENTAL_DATA;
        table.annotation = ICUResourceWriter.ResourceTable.NO_FALLBACK;
        int savedLength = xpath.length();
        for (Node node = root.getFirstChild(); node != null; node = node
                .getNextSibling()) {
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            String name = node.getNodeName();
            ICUResourceWriter.Resource res = null;
            if (name.equals(LDMLConstants.SUPPLEMENTAL_DATA)) {
                // if(isNodeNotConvertible(node,xpath) && writeDraft==false){
                // printWarning(file,"The supplementalData.xml file is marked
                // draft! Not producing ICU file. ");
                // System.exit(-1);
                // return null;
                // }
                node = node.getFirstChild();
                continue;
            } else if (name.equals(LDMLConstants.SPECIAL)) {
                /*
                 * IGNORE SPECIALS FOR NOW
                 */
                node = node.getFirstChild();
                continue;
            } else if (name.equals(LDMLConstants.CURRENCY_DATA)) {
                res = parseCurrencyData(node, xpath);
            } else if (name.equals(LDMLConstants.TERRITORY_CONTAINMENT)) {
                // if(DEBUG)printXPathWarning(node, xpath);
                res = parseTerritoryContainment(node, xpath);
            } else if (name.equals(LDMLConstants.LANGUAGE_DATA)) {
                // if(DEBUG)printXPathWarning(node, xpath);
                res = parseLanguageData(node, xpath);
            } else if (name.equals(LDMLConstants.TERRITORY_DATA)) {
                // if(DEBUG)printXPathWarning(node, xpath);
                res = parseTerritoryData(node, xpath);
            } else if (name.equals(LDMLConstants.META_DATA)) {
                // Ignore this
                // if(DEBUG)printXPathWarning(node, xpath);
            } else if (name.equals(LDMLConstants.TERRITORY_INFO)) {
                // Ignore this
            } else if (name.equals(LDMLConstants.REFERENCES)) {
                // Ignore this
            } else if (name.equals(LDMLConstants.VERSION)) {
                // Ignore this
                // if(DEBUG)printXPathWarning(node, xpath);
            } else if (name.equals(LDMLConstants.GENERATION)) {
                // Ignore this
                // if(DEBUG)printXPathWarning(node, xpath);
            } else if (name.equals(LDMLConstants.CALENDAR_DATA)) {
                res = parseCalendarData(node, xpath);
            } else if (name.equals(LDMLConstants.TIMEZONE_DATA)) {
                res = parseTimeZoneData(node, xpath);
            } else if (name.equals(LDMLConstants.WEEK_DATA)) {
                // res = parseWeekData(node, xpath);
            } else if (name.equals(LDMLConstants.CHARACTERS)) {
                // continue .. these are required for posix
            } else if (name.equals(LDMLConstants.MEASUREMENT_DATA)) {
                // res = parseMeasurementData(node, xpath);
                if (DEBUG)
                    printXPathWarning(node, getXPath(node, xpath));
            } else {
                printError(file, "Encountered unknown element "
                        + getXPath(node, xpath).toString());
                System.exit(-1);
            }
            if (res != null) {
                if (current == null) {
                    table.first = res;
                    current = findLast(res);
                } else {
                    current.next = res;
                    current = findLast(res);
                }
                res = null;
            }
            xpath.delete(savedLength, xpath.length());
        }

        return table;
    }

    private ICUResourceWriter.Resource parseTerritoryContainment(Node root,
            StringBuffer xpath) {
        int savedLength = xpath.length();
        getXPath(root, xpath);
        int oldLength = xpath.length();
        ICUResourceWriter.Resource current = null;
        ICUResourceWriter.Resource res = null;
        if (isNodeNotConvertible(root, xpath)) {
            xpath.setLength(savedLength);
            return null;
        }

        ICUResourceWriter.ResourceTable table = new ICUResourceWriter.ResourceTable();
        table.name = LDMLConstants.TERRITORY_CONTAINMENT;

        for (Node node = root.getFirstChild(); node != null; node = node
                .getNextSibling()) {
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            String name = node.getNodeName();
            getXPath(node, xpath);
            if (isNodeNotConvertible(node, xpath)) {
                xpath.setLength(oldLength);
                continue;
            }
            if (name.equals(LDMLConstants.GROUP)) {
                String cnt = LDMLUtilities.getAttributeValue(node,
                        LDMLConstants.CONTAINS);
                String value = LDMLUtilities.getAttributeValue(node,
                        LDMLConstants.TYPE);
                res = getResourceArray(cnt, value);
            } else {
                System.err.println("Encountered unknown <" + root.getNodeName()
                        + "> subelement: " + name);
                System.exit(-1);
            }
            if (res != null) {
                if (current == null) {
                    table.first = res;
                    current = findLast(res);
                } else {
                    current.next = res;
                    current = findLast(res);
                }
                res = null;
            }
            xpath.delete(oldLength, xpath.length());
        }
        xpath.delete(savedLength, xpath.length());
        if (table.first != null) {
            return table;
        }
        return null;
    }

    private ICUResourceWriter.Resource parseCalendarData(Node root,
            StringBuffer xpath) {
        int savedLength = xpath.length();
        getXPath(root, xpath);
        int oldLength = xpath.length();
        ICUResourceWriter.Resource current = null;
        ICUResourceWriter.Resource res = null;

        if (isNodeNotConvertible(root, xpath)) {
            xpath.setLength(savedLength);
            return null;
        }

        ICUResourceWriter.ResourceTable table = new ICUResourceWriter.ResourceTable();
        table.name = LDMLConstants.CALENDAR_DATA;

        for (Node node = root.getFirstChild(); node != null; node = node
                .getNextSibling()) {
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            String name = node.getNodeName();
            getXPath(node, xpath);
            if (isNodeNotConvertible(node, xpath)) {
                xpath.setLength(oldLength);
                continue;
            }
            if (name.equals(LDMLConstants.CALENDAR)) {
                String cnt = LDMLUtilities.getAttributeValue(node,
                        LDMLConstants.TERRITORIES);
                res = getResourceArray(cnt, LDMLUtilities.getAttributeValue(
                        node, LDMLConstants.TYPE));
            } else {
                System.err.println("Encountered unknown " + xpath.toString());
                System.exit(-1);
            }
            if (res != null) {
                if (current == null) {
                    table.first = res;
                    current = findLast(res);
                } else {
                    current.next = res;
                    current = findLast(res);
                }
                res = null;
            }
            xpath.delete(oldLength, xpath.length());
        }
        xpath.delete(savedLength, xpath.length());
        if (table.first != null) {
            return table;
        }
        return null;
    }

    private ICUResourceWriter.Resource parseTerritoryData(Node root,
            StringBuffer xpath) {
        int savedLength = xpath.length();
        getXPath(root, xpath);
        int oldLength = xpath.length();
        ICUResourceWriter.Resource current = null;
        ICUResourceWriter.Resource res = null;

        if (isNodeNotConvertible(root, xpath)) {
            xpath.setLength(savedLength);
            return null;
        }

        ICUResourceWriter.ResourceTable table = new ICUResourceWriter.ResourceTable();
        table.name = LDMLConstants.TERRITORY_DATA;

        for (Node node = root.getFirstChild(); node != null; node = node
                .getNextSibling()) {
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            String name = node.getNodeName();
            getXPath(node, xpath);
            if (isNodeNotConvertible(node, xpath)) {
                xpath.setLength(oldLength);
                continue;
            }
            if (name.equals(LDMLConstants.TERRITORY)) {
                ICUResourceWriter.ResourceString str = new ICUResourceWriter.ResourceString();
                String type = LDMLUtilities.getAttributeValue(node,
                        LDMLConstants.TYPE);
                if (type == null) {
                    printError(fileName,
                            "Could not get type attribute for xpath: "
                                    + xpath.toString());
                }
                str.name = type;
                str.val = LDMLUtilities.getAttributeValue(node,
                        LDMLConstants.MPTZ);
                res = str;
            } else {
                System.err.println("Encountered unknown <" + root.getNodeName()
                        + "> subelement: " + name);
                System.exit(-1);
            }
            if (res != null) {
                if (current == null) {
                    table.first = res;
                    current = findLast(res);
                } else {
                    current.next = res;
                    current = findLast(res);
                }
                res = null;
            }
            xpath.delete(oldLength, xpath.length());
        }
        xpath.delete(savedLength, xpath.length());
        if (table.first != null) {
            return table;
        }
        return null;
    }

    private ICUResourceWriter.ResourceArray getResourceArray(String str,
            String name) {
        if (str != null) {
            String[] strs = str.split("\\s+");
            ICUResourceWriter.ResourceArray arr = new ICUResourceWriter.ResourceArray();
            arr.name = name;
            ICUResourceWriter.Resource curr = null;
            for (int i = 0; i < strs.length; i++) {
                ICUResourceWriter.ResourceString string = new ICUResourceWriter.ResourceString();
                string.val = strs[i];
                if (curr == null) {
                    curr = arr.first = string;
                } else {
                    curr.next = string;
                    curr = curr.next;
                }
            }
            return arr;
        }
        return null;
    }

    private ICUResourceWriter.Resource parseLanguageData(Node root,
            StringBuffer xpath) {
        int savedLength = xpath.length();
        getXPath(root, xpath);
        int oldLength = xpath.length();

        if (isNodeNotConvertible(root, xpath)) {
            xpath.setLength(savedLength);
            return null;
        }
        Hashtable hash = new Hashtable();

        ICUResourceWriter.ResourceTable table = new ICUResourceWriter.ResourceTable();
        table.name = LDMLConstants.LANGUAGE_DATA;

        for (Node node = root.getFirstChild(); node != null; node = node
                .getNextSibling()) {
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            String name = node.getNodeName();
            getXPath(node, xpath);
            if (isNodeNotConvertible(node, xpath)) {
                xpath.setLength(oldLength);
                continue;
            }
            if (name.equals(LDMLConstants.LANGUAGE)) {
                String key = LDMLUtilities.getAttributeValue(node,
                        LDMLConstants.TYPE);
                if (key == null) {
                    printError(fileName,
                            "<language> element does not have type attribute! "
                                    + xpath.toString());
                    return null;
                }

                String scs = LDMLUtilities.getAttributeValue(node,
                        LDMLConstants.SCRIPTS);
                String trs = LDMLUtilities.getAttributeValue(node,
                        LDMLConstants.TERRITORIES);
                String mpt = LDMLUtilities.getAttributeValue(node,
                        LDMLConstants.MPT);

                String alt = LDMLUtilities.getAttributeValue(node,
                        LDMLConstants.ALT);
                if (alt == null) {
                    alt = LDMLConstants.PRIMARY;
                }
                ICUResourceWriter.ResourceTable tbl = new ICUResourceWriter.ResourceTable();
                tbl.name = alt;
                ICUResourceWriter.ResourceArray scripts = getResourceArray(scs,
                        LDMLConstants.SCRIPTS);
                ICUResourceWriter.ResourceArray terrs = getResourceArray(trs,
                        LDMLConstants.TERRITORIES);
                ICUResourceWriter.ResourceArray mpts = getResourceArray(mpt,
                        LDMLConstants.MPT);
                if (scripts != null) {
                    tbl.first = scripts;
                }
                if (terrs != null) {
                    if (tbl.first != null) {
                        findLast(tbl.first).next = terrs;
                    } else {
                        tbl.first = terrs;
                    }
                }
                if (mpts != null) {
                    if (tbl.first != null) {
                        findLast(tbl.first).next = mpts;
                    } else {
                        tbl.first = terrs;
                    }
                }
                // now find in the Hashtable
                ICUResourceWriter.ResourceTable main = (ICUResourceWriter.ResourceTable) hash
                        .get(key);
                if (main == null) {
                    main = new ICUResourceWriter.ResourceTable();
                    main.name = key;
                    hash.put(key, main);
                }
                if (main.first != null) {
                    findLast(main.first).next = tbl;
                } else {
                    main.first = tbl;
                }
            } else {
                System.err.println("Encountered unknown <" + root.getNodeName()
                        + "> subelement: " + name);
                System.exit(-1);
            }
            xpath.setLength(oldLength);
        }
        Enumeration iter = hash.keys();
        ICUResourceWriter.Resource current = null, res = null;
        while (iter.hasMoreElements()) {
            String key = (String) iter.nextElement();
            res = (ICUResourceWriter.Resource) hash.get(key);
            if (current == null) {
                current = table.first = res;
            } else {
                current.next = res;
                current = current.next;
            }
        }
        xpath.delete(savedLength, xpath.length());
        if (table.first != null) {
            return table;
        }
        return null;
    }

    private ICUResourceWriter.Resource parseTimeZoneData(Node root,
            StringBuffer xpath) {
        ICUResourceWriter.Resource current = null;
        ICUResourceWriter.Resource first = null;
        int savedLength = xpath.length();
        getXPath(root, xpath);
        int oldLength = xpath.length();
        ICUResourceWriter.ResourceTable mapZones = new ICUResourceWriter.ResourceTable();
        mapZones.name = LDMLConstants.MAP_TIMEZONES;
        for (Node node = root.getFirstChild(); node != null; node = node
                .getNextSibling()) {
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            String name = node.getNodeName();

            getXPath(node, xpath);
            if (isNodeNotConvertible(node, xpath)) {
                xpath.setLength(oldLength);
                continue;
            }
            ICUResourceWriter.Resource res = null;

            if (name.equals(LDMLConstants.ALIAS)) {
                res = parseAliasResource(node, xpath);
                res.name = name;
                return res;
            } else if (name.equals(LDMLConstants.DEFAULT)) {
                res = getDefaultResource(node, xpath, name);
            } else if (name.equals(LDMLConstants.MAP_TIMEZONES)) {

                // if(DEBUG)printXPathWarning(node, xpath);
                res = parseMapTimezones(node, xpath);
                if (res != null) {
                    if (mapZones.first == null) {
                        mapZones.first = res;
                    } else {
                        findLast(mapZones.first).next = res;
                    }
                }
                res = null;
            } else if (name.equals(LDMLConstants.ZONE_FORMATTING)) {
                res = parseZoneFormatting(node, xpath);
            } else {
                System.err.println("Encountered unknown <" + xpath
                        + "> subelement: " + name);
                System.exit(-1);
            }
            if (res != null) {
                if (current == null) {
                    first = res;
                    current = findLast(res);
                } else {
                    current.next = res;
                    current = findLast(res);
                }
                res = null;
            }
        }
        if (mapZones.first != null) {
            if (current == null) {
                first = current = mapZones;
            } else {
                current.next = mapZones;
                current = findLast(mapZones);
            }
        }
        if (first != null) {
            return first;
        }
        return null;
    }

    private ICUResourceWriter.Resource parseMapTimezones(Node root,
            StringBuffer xpath) {
        int savedLength = xpath.length();
        getXPath(root, xpath);
        int oldLength = xpath.length();
        ICUResourceWriter.Resource current = null;
        ICUResourceWriter.Resource res = null;

        if (isNodeNotConvertible(root, xpath)) {
            xpath.setLength(savedLength);
            return null;
        }

        ICUResourceWriter.ResourceTable table = new ICUResourceWriter.ResourceTable();
        table.name = LDMLUtilities.getAttributeValue(root, LDMLConstants.TYPE);

        for (Node node = root.getFirstChild(); node != null; node = node
                .getNextSibling()) {
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            String name = node.getNodeName();

            getXPath(node, xpath);
            if (isNodeNotConvertible(node, xpath)) {
                xpath.setLength(oldLength);
                continue;
            }

            if (name.equals(LDMLConstants.MAP_ZONE)) {
                String type = LDMLUtilities.getAttributeValue(node,
                        LDMLConstants.TYPE);
                String other = LDMLUtilities.getAttributeValue(node,
                        LDMLConstants.OTHER);
                String territory = LDMLUtilities.getAttributeValue(node,
                        LDMLConstants.TERRITORY);
                String result;
                ICUResourceWriter.ResourceString str = new ICUResourceWriter.ResourceString();
                if (territory != null && territory.length() > 0) {
                    result = "meta:" + other + "_" + territory;
                    str.name = "\"" + result + "\"";
                    str.val = type;
                } else {
                    result = type;
                    str.name = "\"" + result.replaceAll("/", ":") + "\"";
                    str.val = other;
                }
                res = str;
            } else {
                System.err.println("Encountered unknown <" + root.getNodeName()
                        + "> subelement: " + name);
                System.exit(-1);
            }
            if (res != null) {
                if (current == null) {
                    table.first = res;
                    current = findLast(res);
                } else {
                    current.next = res;
                    current = findLast(res);
                }
                res = null;
            }
            xpath.setLength(oldLength);
        }
        xpath.setLength(savedLength);
        if (table.first != null) {
            return table;
        }
        return null;
    }

    private ICUResourceWriter.Resource parseZoneFormatting(Node root,
            StringBuffer xpath) {
        ICUResourceWriter.ResourceTable table = new ICUResourceWriter.ResourceTable();
        ICUResourceWriter.Resource current = null;

        int savedLength = xpath.length();
        getXPath(root, xpath);
        int oldLength = xpath.length();

        // if the whole node is marked draft then
        // dont write anything
        if (isNodeNotConvertible(root, xpath)) {
            xpath.setLength(savedLength);
            return null;
        }

        table.name = "zoneFormatting";
        table.noSort = true;
        for (Node node = root.getFirstChild(); node != null; node = node
                .getNextSibling()) {
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            String name = node.getNodeName();
            ICUResourceWriter.Resource res = null;
            getXPath(node, xpath);
            if (isNodeNotConvertible(node, xpath)) {
                xpath.setLength(oldLength);
                continue;
            }

            if (name.equals(LDMLConstants.ALIAS)) {
                res = parseAliasResource(node, xpath);
                res.name = name;
                return res;
            } else if (name.equals(LDMLConstants.DEFAULT)) {
                res = getDefaultResource(node, xpath, name);
            } else if (name.equals(LDMLConstants.ZONE_ITEM)) {

                ICUResourceWriter.ResourceTable zi = new ICUResourceWriter.ResourceTable();
                zi.name = "\""
                        + LDMLUtilities.getAttributeValue(node,
                                LDMLConstants.TYPE).replaceAll("/", ":") + "\"";
                String territory = LDMLUtilities.getAttributeValue(node,
                        LDMLConstants.TERRITORY);
                ICUResourceWriter.ResourceString ter = new ICUResourceWriter.ResourceString();
                ter.name = LDMLConstants.TERRITORY;
                ter.val = territory;
                zi.first = ter;
                String aliases = LDMLUtilities.getAttributeValue(node,
                        LDMLConstants.ALIASES);
                String icu_aliases = getICUAlias(LDMLUtilities
                        .getAttributeValue(node, LDMLConstants.TYPE));
                String all_aliases = aliases;
                if (icu_aliases != null) {
                    if (aliases == null) {
                        all_aliases = icu_aliases;
                    } else {
                        all_aliases = aliases + " " + icu_aliases;
                    }
                }
                if (all_aliases != null) {
                    String[] arr = all_aliases.split("\\s+");
                    ICUResourceWriter.ResourceArray als = new ICUResourceWriter.ResourceArray();
                    als.name = LDMLConstants.ALIASES;
                    ICUResourceWriter.Resource cur = null;
                    for (int i = 0; i < arr.length; i++) {
                        ICUResourceWriter.ResourceString str = new ICUResourceWriter.ResourceString();
                        str.val = arr[i];
                        if (cur == null) {
                            als.first = cur = str;
                        } else {
                            cur.next = str;
                            cur = cur.next;
                        }
                    }
                    ter.next = als;
                }
                res = zi;
            } else {
                System.err.println("Encountered unknown <" + root.getNodeName()
                        + "> subelement: " + name);
                System.exit(-1);
            }
            if (res != null) {
                if (current == null) {
                    table.first = res;
                    current = findLast(res);
                } else {
                    current.next = res;
                    current = findLast(res);
                }
                res = null;
            }
            xpath.delete(oldLength, xpath.length());
        }
        xpath.delete(savedLength, xpath.length());

        // Now add the multi-zone list to the table
        String multizone = LDMLUtilities.getAttributeValue(root,
                LDMLConstants.MULTIZONE);
        ICUResourceWriter.ResourceArray mz;
        mz = getResourceArray(multizone, LDMLConstants.MULTIZONE);
        if (current == null) {
            table.first = mz;
            current = findLast(mz);
        } else {
            current.next = mz;
            current = findLast(mz);
        }

        if (table.first != null) {
            return table;
        }
        return null;
    }

    private String getICUAlias(String tzid) {
        // This function is used to return the compatibility aliases for ICU.
        // It should match the ICUZONES file in ICU4C
        // source/tools/tzcode/icuzones.
        // Note that since we don't expect this to change AT ALL over time, it
        // is
        // easier to just hard code the information here. We only include those
        // aliases that are NOT in CLDR.

        if (tzid.equals("Australia/Darwin"))
            return ("ACT");
        if (tzid.equals("Australia/Sydney"))
            return ("AET");
        if (tzid.equals("America/Argentina/Buenos_Aires"))
            return ("AGT");
        if (tzid.equals("Africa/Cairo"))
            return ("ART");
        if (tzid.equals("America/Anchorage"))
            return ("AST");
        if (tzid.equals("America/Sao_Paulo"))
            return ("BET");
        if (tzid.equals("Asia/Dhaka"))
            return ("BST");
        if (tzid.equals("Africa/Harare"))
            return ("CAT");
        if (tzid.equals("America/St_Johns"))
            return ("CNT");
        if (tzid.equals("America/Chicago"))
            return ("CST");
        if (tzid.equals("Asia/Shanghai"))
            return ("CTT");
        if (tzid.equals("Africa/Addis_Ababa"))
            return ("EAT");
        if (tzid.equals("Europe/Paris"))
            return ("ECT");
        if (tzid.equals("America/Indianapolis"))
            return ("IET");
        if (tzid.equals("Asia/Calcutta"))
            return ("IST");
        if (tzid.equals("Asia/Tokyo"))
            return ("JST");
        if (tzid.equals("Pacific/Apia"))
            return ("MIT");
        if (tzid.equals("Asia/Yerevan"))
            return ("NET");
        if (tzid.equals("Pacific/Auckland"))
            return ("NST");
        if (tzid.equals("Asia/Karachi"))
            return ("PLT");
        if (tzid.equals("America/Phoenix"))
            return ("PNT");
        if (tzid.equals("America/Puerto_Rico"))
            return ("PRT");
        if (tzid.equals("America/Los_Angeles"))
            return ("PST");
        if (tzid.equals("Pacific/Guadalcanal"))
            return ("SST");
        if (tzid.equals("Asia/Saigon"))
            return ("VST");

        return null;
    }

    private ICUResourceWriter.Resource parseCurrencyFraction(Node root,
            StringBuffer xpath) {
        ICUResourceWriter.ResourceTable table = new ICUResourceWriter.ResourceTable();
        ICUResourceWriter.Resource current = null;

        int savedLength = xpath.length();
        getXPath(root, xpath);
        int oldLength = xpath.length();

        // if the whole node is marked draft then
        // dont write anything
        if (isNodeNotConvertible(root, xpath)) {
            xpath.setLength(savedLength);
            return null;
        }

        table.name = "CurrencyMeta";
        table.noSort = true;
        for (Node node = root.getFirstChild(); node != null; node = node
                .getNextSibling()) {
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            String name = node.getNodeName();
            ICUResourceWriter.Resource res = null;
            getXPath(node, xpath);
            if (isNodeNotConvertible(node, xpath)) {
                xpath.setLength(oldLength);
                continue;
            }

            if (name.equals(LDMLConstants.ALIAS)) {
                res = parseAliasResource(node, xpath);
                res.name = name;
                return res;
            } else if (name.equals(LDMLConstants.DEFAULT)) {
                res = getDefaultResource(node, xpath, name);
            } else if (name.equals(LDMLConstants.INFO)) {
                ICUResourceWriter.ResourceIntVector vector = new ICUResourceWriter.ResourceIntVector();
                vector.name = LDMLUtilities.getAttributeValue(node,
                        LDMLConstants.ISO_4217);
                ICUResourceWriter.ResourceInt zero = new ICUResourceWriter.ResourceInt();
                ICUResourceWriter.ResourceInt one = new ICUResourceWriter.ResourceInt();
                zero.val = LDMLUtilities.getAttributeValue(node,
                        LDMLConstants.DIGITS);
                one.val = LDMLUtilities.getAttributeValue(node,
                        LDMLConstants.ROUNDING);
                vector.first = zero;
                zero.next = one;
                res = vector;
            } else {
                System.err.println("Encountered unknown <" + root.getNodeName()
                        + "> subelement: " + name);
                System.exit(-1);
            }
            if (res != null) {
                if (current == null) {
                    table.first = res;
                    current = findLast(res);
                } else {
                    current.next = res;
                    current = findLast(res);
                }
                res = null;
            }
            xpath.delete(oldLength, xpath.length());
        }
        xpath.delete(savedLength, xpath.length());
        if (table.first != null) {
            return table;
        }
        return null;
    }

    private int countHyphens(String str) {
        int ret = 0;
        for (int i = 0; i < str.length(); i++) {
            if (str.charAt(i) == '-') {
                ret++;
            }
        }
        return ret;
    }

    private long getMilliSeconds(String dateStr) {
        try {
            if (dateStr != null) {
                int count = countHyphens(dateStr);
                SimpleDateFormat format = new SimpleDateFormat();
                Date date = null;
                if (count == 2) {
                    format.applyPattern("yyyy-mm-dd");
                    date = format.parse(dateStr);
                } else if (count == 1) {
                    format.applyPattern("yyyy-mm");
                    date = format.parse(dateStr);
                } else {
                    format.applyPattern("yyyy");
                    date = format.parse(dateStr);
                }
                return date.getTime();
            }
        } catch (ParseException ex) {
            System.err.println("Could not parse date: " + dateStr);
            System.err.println(ex.getMessage());
            System.exit(-1);
        }
        return -1;
    }

    private ICUResourceWriter.ResourceIntVector getSeconds(String dateStr) {
        long millis = getMilliSeconds(dateStr);
        if (millis == -1) {
            return null;
        }
        int top = (int) ((millis & 0xFFFFFFFF00000000L) >>> 32);
        int bottom = (int) ((millis & 0x00000000FFFFFFFFL));
        ICUResourceWriter.ResourceIntVector vector = new ICUResourceWriter.ResourceIntVector();
        ICUResourceWriter.ResourceInt int1 = new ICUResourceWriter.ResourceInt();
        ICUResourceWriter.ResourceInt int2 = new ICUResourceWriter.ResourceInt();
        int1.val = Integer.toString(top);
        int2.val = Integer.toString(bottom);
        vector.first = int1;
        int1.next = int2;
        if (DEBUG) {
            top = Integer.parseInt(int1.val);
            bottom = Integer.parseInt(int2.val);
            long bot = 0xffffffffL & bottom;
            long full = ((long) (top) << 32);
            full += (long) bot;
            if (full != millis) {
                System.out.println("Did not get the value back.");
            }

        }
        return vector;
    }

    private ICUResourceWriter.Resource parseCurrencyRegion(Node root,
            StringBuffer xpath) {
        ICUResourceWriter.ResourceTable table = new ICUResourceWriter.ResourceTable();
        ICUResourceWriter.Resource current = null;

        int savedLength = xpath.length();
        getXPath(root, xpath);
        int oldLength = xpath.length();

        // if the whole node is marked draft then
        // dont write anything
        if (isNodeNotConvertible(root, xpath)) {
            xpath.setLength(savedLength);
            return null;
        }
        table.name = LDMLUtilities.getAttributeValue(root,
                LDMLConstants.ISO_3166);
        for (Node node = root.getFirstChild(); node != null; node = node
                .getNextSibling()) {
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            String name = node.getNodeName();
            ICUResourceWriter.Resource res = null;
            getXPath(node, xpath);
            if (name.equals(LDMLConstants.ALIAS)) {
                res = parseAliasResource(node, xpath);
                res.name = name;
                return res;
            } else if (name.equals(LDMLConstants.DEFAULT)) {
                res = getDefaultResource(node, xpath, name);
            } else if (name.equals(LDMLConstants.CURRENCY)) {
                // getXPath(node, xpath);
                if (isNodeNotConvertible(node, xpath)) {
                    xpath.setLength(oldLength);
                    continue;
                }
                ICUResourceWriter.ResourceTable curr = new ICUResourceWriter.ResourceTable();
                curr.name = "";
                ICUResourceWriter.ResourceString id = new ICUResourceWriter.ResourceString();
                id.name = "id";
                id.val = LDMLUtilities.getAttributeValue(node,
                        LDMLConstants.ISO_4217);

                ICUResourceWriter.ResourceIntVector fromRes = getSeconds(LDMLUtilities
                        .getAttributeValue(node, LDMLConstants.FROM));
                ICUResourceWriter.ResourceIntVector toRes = getSeconds(LDMLUtilities
                        .getAttributeValue(node, LDMLConstants.TO));

                if (fromRes != null) {
                    fromRes.name = LDMLConstants.FROM;
                    curr.first = id;
                    id.next = fromRes;
                }
                if (toRes != null) {
                    toRes.name = LDMLConstants.TO;
                    fromRes.next = toRes;
                }
                res = curr;
            } else {
                System.err.println("Encountered unknown <" + root.getNodeName()
                        + "> subelement: " + name);
                System.exit(-1);
            }
            if (res != null) {
                if (current == null) {
                    table.first = res;
                    current = findLast(res);
                } else {
                    current.next = res;
                    current = findLast(res);
                }
                res = null;
            }
            xpath.delete(oldLength, xpath.length());
        }
        xpath.delete(savedLength, xpath.length());
        if (table.first != null) {
            return table;
        }
        return null;
    }

    private ICUResourceWriter.Resource parseCurrencyData(Node root,
            StringBuffer xpath) {
        ICUResourceWriter.Resource currencyMeta = null;
        ICUResourceWriter.ResourceTable currencyMap = new ICUResourceWriter.ResourceTable();
        currencyMap.name = "CurrencyMap";
        currencyMap.comment = commentForCurrencyMap;
        currencyMap.noSort = true;
        ICUResourceWriter.Resource currentMap = null;

        int savedLength = xpath.length();
        getXPath(root, xpath);
        int oldLength = xpath.length();

        // if the whole collatoin node is marked draft then
        // dont write anything
        if (isNodeNotConvertible(root, xpath)) {
            xpath.setLength(savedLength);
            return null;
        }
        for (Node node = root.getFirstChild(); node != null; node = node
                .getNextSibling()) {
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            String name = node.getNodeName();
            ICUResourceWriter.Resource res = null;
            // getXPath(node, xpath);
            if (name.equals(LDMLConstants.REGION)) {
                res = parseCurrencyRegion(node, xpath);
                if (res != null) {
                    if (currentMap == null) {
                        currencyMap.first = res;
                        currentMap = findLast(res);
                    } else {
                        currentMap.next = res;
                        currentMap = findLast(res);
                    }
                    res = null;
                }
            } else if (name.equals(LDMLConstants.FRACTIONS)) {
                currencyMeta = parseCurrencyFraction(node, xpath);
                currencyMeta.comment = commentForCurrencyMeta;

            } else {
                System.err.println("Encountered unknown <" + root.getNodeName()
                        + "> subelement: " + name);
                System.exit(-1);
            }

            xpath.delete(oldLength, xpath.length());
        }
        xpath.delete(savedLength, xpath.length());
        currencyMeta.next = currencyMap;
        return currencyMeta;
    }

    private String ldmlVersion = null;

    private ICUResourceWriter.Resource parseBundle(InputLocale loc) {
        ICUResourceWriter.ResourceTable table = new ICUResourceWriter.ResourceTable();

        ldmlVersion = "0.0";

        // OK. This is no longer a reactive, but a proactive program.
        ICUResourceWriter.Resource res = null;
        // * Fetch the identity
        if ((res = parseIdentity(table, loc, "//ldml/identity")) != null) {
            // table.appendContents(res);
            res = null;
        }
        // * Verify the locale's identity.
        if (loc.file.isHere("//ldml/alias")) {
            // locale is an alias
            res = ICUResourceWriter.createString("\"%%ALIAS\"", loc
                    .getBasicAttributeValue("//ldml/alias",
                            LDMLConstants.SOURCE));
            table.replaceContents(res); // overwrite anything else set so far.

            return table;
        }

        // Now, loop over other stuff.

        String stuff[] = {
                // Don't do these:
                // LDMLConstants.ALIAS,
                // LDMLConstants.IDENTITY,

                // these are OK:
                LDMLConstants.SPECIAL, LDMLConstants.LDN,
                LDMLConstants.LAYOUT,
                LDMLConstants.FALLBACK, // noop
                LDMLConstants.CHARACTERS, LDMLConstants.DELIMITERS,
                LDMLConstants.DATES, LDMLConstants.NUMBERS,
                // LDMLConstants.COLLATIONS,
                // LDMLConstants.POSIX,
                // LDMLConstants.SEGMENTATIONS,
                LDMLConstants.REFERENCES, };

        for (int jj = 0; jj < stuff.length; jj++) {
            res = null;
            // String xpath = (String) it.next();
            // String name = parts.getElement(1);
            String name = stuff[jj];
            String xpath = "//ldml/" + stuff[jj];
            if (verbose) {
                System.out.println(name + " ");
            }

            if (name.equals(LDMLConstants.SPECIAL)) {
                res = parseSpecialElements(loc, xpath);
            } else if (name.equals(LDMLConstants.LDN)) {
                res = parseLocaleDisplayNames(loc);
            } else if (name.equals(LDMLConstants.LAYOUT)) {
                res = parseLayout(loc, xpath);
            } else if (name.equals(LDMLConstants.FALLBACK)) {
            } else if (name.equals(LDMLConstants.CHARACTERS)) {
                res = parseCharacters(loc, xpath);
            } else if (name.equals(LDMLConstants.DELIMITERS)) {
                res = parseDelimiters(loc, xpath);
            } else if (name.equals(LDMLConstants.DATES)) {
                res = parseDates(loc, xpath);
            } else if (name.equals(LDMLConstants.NUMBERS)) {
                res = parseNumbers(loc, xpath);
            } else if (name.equals(LDMLConstants.COLLATIONS)) {
                if (locName.equals("root")) {
                    ICUResourceWriter.ResourceProcess process = new ICUResourceWriter.ResourceProcess();
                    process.name = "UCARules";
                    process.ext = "uca_rules";
                    process.val = "../unidata/UCARules.txt";
                    res = process;
                    process.next = parseCollations(loc, true);
                } else {
                    res = parseCollations(loc, true);
                }
            } else if (name.equals(LDMLConstants.POSIX)) {
                res = parsePosix(loc, xpath);
            } else if (name.equals(LDMLConstants.SEGMENTATIONS)) {
                // TODO: FIX ME with parseSegmentations();
                if (DEBUG)
                    printXPathWarning(loc, xpath);
            } else if (name.indexOf("icu:") > -1
                    || name.indexOf("openOffice:") > -1) {
                // TODO: these are specials .. ignore for now ... figure out
                // what to do later
            } else if (name.equals(LDMLConstants.REFERENCES)) {
                // TODO: This is special documentation... ignore for now
                if (DEBUG)
                    printXPathWarning(loc, xpath);
            } else {
                System.err.println("Encountered unknown <" + "//ldml"
                        + "> subelement: " + name);
                System.exit(-1);
            }
            if (res != null) { // have an item
                table.appendContents(res);
            }
        }
        // ???
        // TODO: this needs to be added!
        // if(sourceDir.indexOf("main")>0 &&
        // !LDMLUtilities.isLocaleAlias(root)){
        // ICUResourceWriter.Resource temp = parseWeek();
        // if(temp!=null){
        // ICUResourceWriter.Resource greg = findResource(table,
        // LDMLConstants.GREGORIAN);
        // ICUResourceWriter.Resource cals = findResource(table,
        // LDMLConstants.CALENDAR);
        // if(greg!=null){
        // findLast(greg.first).next = temp;
        // }else if(cals!=null){
        // greg = new ICUResourceWriter.ResourceTable();
        // greg.name = LDMLConstants.GREGORIAN;
        // greg.first = temp;
        // findLast(cals.first).next = greg;
        // }else{
        // greg = new ICUResourceWriter.ResourceTable();
        // greg.name = LDMLConstants.GREGORIAN;
        // greg.first = temp;

        // ICUResourceWriter.ResourceTable cal = new
        // ICUResourceWriter.ResourceTable();
        // cal.name = LDMLConstants.CALENDAR;
        // cal.first = greg;

        // if(table.first!=null){
        // current.next = cal;
        // }else{
        // table.first = cal;
        // }
        // }
        // }
        // temp = parseMeasurement();
        // findLast(table.first).next = temp;
        // }
        if (verbose) {
            System.out.println();
        }
        if (supplementalDoc != null) {
            /*
             * TODO: comment this out for now. We shall revisit when we have
             * information on how to present the script data with new API
             * ICUResourceWriter.Resource res =
             * parseLocaleScript(supplementalDoc); if(res!=null){ if(current ==
             * null){ table.first = res; current = findLast(res); }else{
             * current.next = res; current = findLast(res); } res = null; }
             * 
             * ICUResourceWriter.Resource res = parseMetaData(supplementalDoc);
             */
        }
        return table;
    }

    /**
     * return the end of a res chain
     * 
     * @param res
     *            res to start with
     * @return the end
     * @deprecated - use ICUResourceWriter.Resource.end()
     */
    private ICUResourceWriter.Resource findLast(ICUResourceWriter.Resource res) {
        return res.end();
    }

    private ICUResourceWriter.Resource findResource(
            ICUResourceWriter.Resource res, String type) {
        ICUResourceWriter.Resource current = res;
        ICUResourceWriter.Resource ret = null;
        while (current != null) {
            if (current.name != null && current.name.equals(type)) {
                return current;
            }
            if (current.first != null) {
                ret = findResource(current.first, type);
            }
            if (ret != null) {
                break;
            }
            current = current.next;
        }
        return ret;
    }

    /**
     * Higher convenience level than parseAliasResource Check to see if there is
     * an alias at xpath+"/alias", if so, create & return it.
     * 
     * @param loc
     * @param xpath
     * @return
     */
    private ICUResourceWriter.Resource getAliasResource(InputLocale loc,
            String xpath) {
        String name = loc.getXpathName(xpath);
        String aliasPath = xpath + "/alias";
        ICUResourceWriter.Resource aRes = parseAliasResource(loc, aliasPath);
        if (aRes != null) {
            aRes.name = name;
        }
        return aRes;
    }

    private ICUResourceWriter.Resource parseAliasResource(InputLocale loc,
            String xpath) {
        String source = loc.getBasicAttributeValue(xpath, LDMLConstants.SOURCE);
        String path = loc.getBasicAttributeValue(xpath, LDMLConstants.PATH);
        if (source == null && path == null) {
            if (!loc.file.isHere(xpath)) {
                return null;
            }
        }
        try {
            // if (node != null && (!isNodeNotConvertible(node, xpath))) { ??
            // aliases always convertible
            ICUResourceWriter.ResourceAlias alias = new ICUResourceWriter.ResourceAlias();
            String basePath = xpath.replaceAll("/alias.*$", "");
            String fullPath = loc.file.getFullXPath(xpath).replaceAll(
                    "/alias.*$", "");
            if (path != null) {
                path = path.replaceAll("='", "=\"").replaceAll("']", "\"]");
            }

            String val = LDMLUtilities.convertXPath2ICU(source, path, basePath,
                    fullPath);
            alias.val = val;
            alias.name = basePath;
            // System.err.println("BBase: " + basePath +", FFull: " + fullPath +
            // " >>> " + val);
            return alias;
            // }
        } catch (TransformerException ex) {
            System.err.println("Could not compile XPATH for" + " source:  "
                    + source + " path: " + path + " Node: " + xpath);
            ex.printStackTrace();
            System.exit(-1);
        }
        return null;
        // TODO update when XPATH is integrated into LDML
    }

    /**
     * @deprecated
     * @param node
     * @param xpath
     * @return
     */
    private ICUResourceWriter.Resource parseAliasResource(Node node,
            StringBuffer xpath) {
        int saveLength = xpath.length();
        getXPath(node, xpath);
        try {
            if (node != null && (!isNodeNotConvertible(node, xpath))) {
                ICUResourceWriter.ResourceAlias alias = new ICUResourceWriter.ResourceAlias();
                xpath.setLength(saveLength);
                String val = LDMLUtilities.convertXPath2ICU(node, null, xpath);
                alias.val = val;
                alias.name = node.getParentNode().getNodeName();
                xpath.setLength(saveLength);
                return alias;
            }
        } catch (TransformerException ex) {
            System.err.println("Could not compile XPATH for"
                    + " source:  "
                    + LDMLUtilities.getAttributeValue(node,
                            LDMLConstants.SOURCE) + " path: "
                    + LDMLUtilities.getAttributeValue(node, LDMLConstants.PATH)
                    + " Node: " + node.getParentNode().getNodeName());
            ex.printStackTrace();
            System.exit(-1);
        }
        xpath.setLength(saveLength);
        // TODO update when XPATH is integrated into LDML
        return null;
    }

    private StringBuffer getXPath(Node node, StringBuffer xpath) {
        xpath.append("/");
        xpath.append(node.getNodeName());
        LDMLUtilities.appendXPathAttribute(node, xpath);
        return xpath;
    }

    /*
     * private StringBuffer getXPathAllAttributes(Node node, StringBuffer
     * xpath){ xpath.append("/"); xpath.append(node.getNodeName());
     * LDMLUtilities.appendAllAttributes(node,xpath); return xpath; }
     */
    private ICUResourceWriter.Resource parseIdentity(
            ICUResourceWriter.ResourceTable table, InputLocale loc, String xpath) {

        // version #
        String version = loc.getBasicAttributeValue("//ldml/"
                + LDMLConstants.IDENTITY + "/" + LDMLConstants.VERSION,
                LDMLConstants.NUMBER);
        if (version == null) {
            // System.err.println("No version #??");
        } else {
            version = version.replaceAll(".*?Revision: (.*?) .*", "$1");

            ICUResourceWriter.Resource res = ICUResourceWriter.createString(
                    keyNameMap.get(LDMLConstants.VERSION), version);
            // write the version string
            if (res != null) {
                table.appendContents(res);
            }
        }

        String localeID = loc.file.getLocaleID();
        table.name = localeID;

        // TODO: alias in the identity??
        // }else if(name.equals(LDMLConstants.ALIAS)){
        // res = parseAliasResource(node, xpath);

        return table; // modified TL resource
    }

    private static final String[] registeredKeys = new String[] { "collation",
            "calendar", "currency" };

    private ICUResourceWriter.Resource parseLocaleDisplayNames(InputLocale loc) {
        ICUResourceWriter.Resource first = null;
        ICUResourceWriter.Resource current = null;

        ICUResourceWriter.Resource res = null;
        String stuff[] = { LDMLConstants.LANGUAGES, LDMLConstants.SCRIPTS,
                LDMLConstants.TERRITORIES, LDMLConstants.KEYS,
                LDMLConstants.VARIANTS, LDMLConstants.MSNS,
                LDMLConstants.TYPES, LDMLConstants.ALIAS,
                // LDMLConstants.MSNS,
                LDMLConstants.CODE_PATTERNS, };

        for (String name : stuff) {
            if (name.equals(LDMLConstants.LANGUAGES)
                    || name.equals(LDMLConstants.SCRIPTS)
                    || name.equals(LDMLConstants.TERRITORIES)
                    || name.equals(LDMLConstants.KEYS)
                    || name.equals(LDMLConstants.VARIANTS)
                    || name.equals(LDMLConstants.MSNS)) {
                res = parseList(loc, name);
            } else if (name.equals(LDMLConstants.TYPES)) {
                res = parseDisplayTypes(loc, name);
            } else if (name.equals(LDMLConstants.ALIAS)) {
                // res = parseAliasResource(loc, name);
                // TODO: parseAliasResource
            } else if (name.equals(LDMLConstants.MSNS)) {
            } else if (name.equals(LDMLConstants.CODE_PATTERNS)) {
            } else {
                System.err.println("Unknown element found: " + name);
                System.exit(-1);
            }
            if (res != null) {
                if (current == null) {
                    current = first = res;
                } else {
                    current.next = res;
                    current = current.next;
                }
                res = null;
            }
        }
        return first;
    }

    private ICUResourceWriter.Resource parseDisplayTypes(InputLocale loc,
            String name) {
        StringBuffer myXpath = new StringBuffer();
        myXpath.append("//ldml/localeDisplayNames/types/type[@key='");
        int sl = myXpath.length();
        ICUResourceWriter.ResourceTable table = new ICUResourceWriter.ResourceTable();
        table.name = (String) keyNameMap.get(LDMLConstants.TYPES);
        ICUResourceWriter.ResourceTable current = null;
        // int saveLength = xpath.length();
        // getXPath(root, xpath);
        // int oldLength = xpath.length();
        for (int i = 0; i < registeredKeys.length; i++) {
            myXpath.append(registeredKeys[i]);
            myXpath.append("']");
            for (Iterator<String> iter = loc.file.iterator(myXpath.toString()); iter
                    .hasNext();) {
                String xpath = iter.next();
                System.err.println("FF " + myXpath + " - > " + xpath);

                ICUResourceWriter.ResourceTable subTable = new ICUResourceWriter.ResourceTable();
                subTable.name = registeredKeys[i];
                //
                // ICUResourceWriter.ResourceString currentString = null;
                // for (int j = 0; j < list.getLength(); j++) {
                // Node item = list.item(j);
                // getXPath(item, xpath);
                //
                // String type = LDMLUtilities.getAttributeValue(item,
                // LDMLConstants.TYPE);
                // String value = LDMLUtilities.getNodeValue(item);
                //
                // if (isNodeNotConvertible(item, xpath)) {
                // xpath.setLength(oldLength);
                // continue;
                // }
                //
                // ICUResourceWriter.ResourceString string = new
                // ICUResourceWriter.ResourceString();
                // string.name = type;
                // string.val = value;
                // if (currentString == null) {
                // subTable.first = currentString = string;
                // } else {
                // currentString.next = string;
                // currentString = (ICUResourceWriter.ResourceString)
                // currentString.next;
                // }
                // xpath.setLength(oldLength);
                // }
                if (subTable.first != null) {
                    if (table.first == null) {
                        table.first = current = subTable;
                    } else {
                        current.next = subTable;
                        current = (ICUResourceWriter.ResourceTable) current.next;
                    }
                }
            }
            myXpath.delete(sl, myXpath.length());
        }
        // xpath.setLength(saveLength);
        if (table.first != null) {
            return table;
        }
        return null;
    }

    private ICUResourceWriter.Resource parseList(InputLocale loc, String name) {
        ICUResourceWriter.ResourceTable table = new ICUResourceWriter.ResourceTable();
        String rootNodeName = name;
        table.name = (String) keyNameMap.get(rootNodeName);
        ICUResourceWriter.Resource current = null;
        boolean uc = rootNodeName.equals(LDMLConstants.VARIANTS);
        boolean prohibit = rootNodeName.equals(LDMLConstants.TERRITORIES);
        String origXpath = "//ldml/localeDisplayNames/" + name;
        if ((current = getAliasResource(loc, origXpath)) != null) {
            return current;
        }
        for (Iterator<String> iter = loc.file.iterator(origXpath); iter
                .hasNext();) {
            String xpath = (String) iter.next();
            // a ceratain element of the list
            // is marked draft .. just dont
            // output that item
            // if(isNodeNotConvertible(node, xpath)){
            // xpath.setLength(oldLength);
            // continue;
            // }
            if (loc.isPathNotConvertible(xpath)) {
                // System.err.println("PNC: " + xpath);
                continue;
            }

            ICUResourceWriter.ResourceString res = new ICUResourceWriter.ResourceString();

            res.name = loc.getBasicAttributeValue(xpath, LDMLConstants.TYPE);
            if (uc) {
                res.name = res.name.toUpperCase();
            }
            res.val = loc.file.getStringValue(xpath);

            if (res.name == null) {
                System.err.println(name + " - " + res.name + " = " + res.val);
            }

            if (prohibit == true && deprecatedTerritories.get(res.name) != null) {
                res = null;
            }
            if (res != null) {
                if (current == null) {
                    current = table.first = res;
                } else {
                    current.next = res;
                    current = current.next;
                }
            }
        }
        // xpath.delete(savedLength, xpath.length());
        if (table.first != null) {
            return table;
        }
        return null;
    }

    private ICUResourceWriter.Resource parseArray(InputLocale loc, String xpath) {
        ICUResourceWriter.ResourceArray array = new ICUResourceWriter.ResourceArray();
        String name = loc.getXpathName(xpath);
        array.name = keyNameMap.get(name);
        ICUResourceWriter.Resource current = null;
        String origXpath = xpath;
        for (Iterator<String> iter = loc.file.iterator(xpath); iter.hasNext();) {
            xpath = iter.next();
            name = loc.getXpathName(xpath);
            // if(loc.isPathNotConvertible(xpath)) continue;
            if (current == null) {
                current = array.first = new ICUResourceWriter.ResourceString();
            } else {
                current.next = new ICUResourceWriter.ResourceString();
                current = current.next;
            }
            // current.name = LDMLUtilities.getAttributeValue(node,
            // LDMLConstants.TYPE);

            ((ICUResourceWriter.ResourceString) current).val = loc.file
                    .getStringValue(xpath);
        }
        if (array.first != null) {
            return array;
        }
        return null;
    }

    /**
     * Parse a table (k/v pair) into an ICU table
     * @param loc locale
     * @param xpath base xpath of items
     * @param element the item to search for
     * @param attribute the attribute which will become the 'key' in icu
     * @return the table, or null
     */
    private ICUResourceWriter.Resource parseTable(InputLocale loc, String xpath, String element, String attribute) {
        ICUResourceWriter.ResourceTable array = new ICUResourceWriter.ResourceTable();
        String name = loc.getXpathName(xpath);
        array.name = keyNameMap.get(name); // attempt
        ICUResourceWriter.Resource current = null;
        String origXpath = xpath;
//        System.err.println("parseTable: parsing " + xpath + " looking for " + element+"'s for their " + attribute);
        for (Iterator<String> iter = loc.file.iterator(xpath); iter.hasNext();) {
            xpath = iter.next();
//            System.err.println("parseTable: gotch " + xpath);
            if(loc.isPathNotConvertible(xpath)) {
                continue;
            }
            name = loc.getXpathName(xpath);
            if(!name.equals(element)) {
                System.err.println("Err: unknown item " + xpath + " / " + name + " - expected " + element);
                continue;
            }
            String type = loc.getBasicAttributeValue(xpath, attribute);
            String val =  loc.file.getStringValue(xpath);

            array.appendContents(ICUResourceWriter.createString(type, val));            
        }
        if (array.first != null) {
            return array;
        }
        return null;
    }

    private static final String ICU_SCRIPTS = "icu:scripts";
    private static final String ICU_SCRIPT = "icu:script";

    private ICUResourceWriter.Resource parseCharacters(InputLocale loc,
            String xpath) {
        ICUResourceWriter.Resource current = null, first = null;
        for (Iterator<String> iter = loc.file.iterator(xpath); iter.hasNext();) {
            String aPath = iter.next();

            if (loc.isPathNotConvertible(aPath)) {
                continue;
            }

            String name = loc.getXpathName(aPath);

            ICUResourceWriter.Resource res = null;
            if (name.equals(LDMLConstants.EXEMPLAR_CHARACTERS)) {
                String type = loc.getBasicAttributeValue(aPath,
                        LDMLConstants.TYPE);
                res = parseStringResource(loc, aPath);
                if (type != null && type.equals(LDMLConstants.AUXILIARY)) {
                    res.name = (String) keyNameMap.get(LDMLConstants.AUXILIARY);
                } else if (type != null
                        && type.equals(LDMLConstants.CURRENCY_SYMBOL)) {
                    res = null;
                } else {
                    res.name = (String) keyNameMap.get(name);
                }
            } else if (name.equals(LDMLConstants.ALIAS)) {
                res = parseAliasResource(loc, aPath);
            } else if (name.equals(LDMLConstants.MAPPING)) {
                // Currently we dont have a way to represent this data in ICU!
                // And we don't need to
                // if(DEBUG)printXPathWarning(node, xpath);
            } else if (aPath.indexOf("/" + LDMLConstants.SPECIAL) > 0) {
                res = parseSpecialElements(loc, aPath);
            } else {
                System.err.println("Unknown  character element found: " + aPath
                        + " / " + name + " -> " + loc.file.getFullXPath(aPath));
                System.exit(-1);
            }
            if (res != null) {
                first = ICUResourceWriter.Resource.addAfter(first, res);
            }
        }
        return first;
    }

    /**
     * @deprecated
     * @param node
     * @return
     */
    private ICUResourceWriter.Resource parseStringResource(Node node) {
        ICUResourceWriter.ResourceString str = new ICUResourceWriter.ResourceString();
        str.val = LDMLUtilities.getNodeValue(node);
        str.name = node.getNodeName();
        return str;
    }

    private ICUResourceWriter.Resource parseStringResource(InputLocale loc,
            String xpath) {
        ICUResourceWriter.ResourceString str = new ICUResourceWriter.ResourceString();
        str.val = loc.file.getStringValue(xpath);
        str.name = loc.getXpathName(xpath);
        return str;
    }

    private ICUResourceWriter.Resource parseDelimiters(InputLocale loc,
            String xpath) {
        if (loc.isPathNotConvertible(xpath)) {
            return null;
        }

        ICUResourceWriter.ResourceTable table = new ICUResourceWriter.ResourceTable();
        table.name = loc.getXpathName(xpath);

        ICUResourceWriter.Resource current = table.first;
        String origXpath = xpath;
        for (Iterator<String> iter = loc.file.iterator(xpath); iter.hasNext();) {
            xpath = iter.next();
            String name = loc.getXpathName(xpath);
            if (loc.isPathNotConvertible(xpath))
                continue;
            ICUResourceWriter.Resource res = null;
            if (name.equals(LDMLConstants.QS) || name.equals(LDMLConstants.QE)
                    || name.equals(LDMLConstants.AQS)
                    || name.equals(LDMLConstants.AQE)) {
                // getXPath(node, xpath);
                if (loc.isPathNotConvertible(xpath)) {
                    continue;
                }
                res = parseStringResource(loc, xpath);
            } else if (name.equals(LDMLConstants.ALIAS)) {
                res = parseAliasResource(loc, xpath);
            } else {
                System.err.println("Unknown element found: " + xpath);
                System.exit(-1);
            }
            if (res != null) {
                if (current == null) {
                    current = table.first = res;
                } else {
                    current.next = res;
                    current = current.next;
                }
                res = null;
            }
        }
        if (table.first != null) {
            return table;
        }
        return null;
    }

    private ICUResourceWriter.Resource parseMeasurement() {
        String country = ULocale.getCountry(locName);
        ICUResourceWriter.Resource ret = null;
        String variant = ULocale.getVariant(locName);
        // optimization
        if (variant.length() != 0) {
            return ret;
        }
        ICUResourceWriter.Resource current = null, first = null;
        StringBuffer xpath = new StringBuffer(
                "//supplementalData/measurementData");
        Node root = LDMLUtilities.getNode(supplementalDoc, xpath.toString());
        if (root == null) {
            throw new RuntimeException("Could not load: " + xpath.toString());
        }
        int savedLength = xpath.length();
        int oldLength = xpath.length();

        // if the whole node is marked draft then
        // dont write anything
        if (isNodeNotConvertible(root, xpath)) {
            return null;
        }
        for (Node node = root.getFirstChild(); node != null; node = node
                .getNextSibling()) {
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }

            String name = node.getNodeName();
            ICUResourceWriter.Resource res = null;
            if (name.equals(LDMLConstants.MS)) {

                getXPath(node, xpath);
                if (isNodeNotConvertible(node, xpath)) {
                    xpath.setLength(oldLength);
                    continue;
                }
                String terr = LDMLUtilities.getAttributeValue(node,
                        LDMLConstants.TERRITORIES);
                if (terr != null
                        && (locName.equals("root") && terr.equals("001"))
                        || terr.equals(country)) {
                    ICUResourceWriter.ResourceInt resint = new ICUResourceWriter.ResourceInt();
                    String sys = LDMLUtilities.getAttributeValue(node,
                            LDMLConstants.TYPE);
                    if (sys.equals("US")) {
                        resint.val = "1";
                    } else {
                        resint.val = "0";
                    }
                    resint.name = (String) keyNameMap.get(LDMLConstants.MS);
                    res = resint;
                }
            } else if (name.equals(LDMLConstants.PAPER_SIZE)) {

                String terr = LDMLUtilities.getAttributeValue(node,
                        LDMLConstants.TERRITORIES);
                if (terr != null
                        && (locName.equals("root") && terr.equals("001"))
                        || terr.equals(country)) {
                    ICUResourceWriter.ResourceIntVector vector = new ICUResourceWriter.ResourceIntVector();
                    vector.name = (String) keyNameMap.get(name);
                    ICUResourceWriter.ResourceInt height = new ICUResourceWriter.ResourceInt();
                    ICUResourceWriter.ResourceInt width = new ICUResourceWriter.ResourceInt();
                    vector.first = height;
                    height.next = width;
                    String type = LDMLUtilities.getAttributeValue(node,
                            LDMLConstants.TYPE);
                    /*
                     * For A4 size paper the height and width are 297 mm and 210
                     * mm repectively, and for US letter size the height and
                     * width are 279 mm and 216 mm respectively.
                     */
                    if (type.equals("A4")) {
                        height.val = "297";
                        width.val = "210";
                    } else if (type.equals("US-Letter")) {
                        height.val = "279";
                        width.val = "216";
                    } else {
                        throw new RuntimeException("Unknown paper type: "
                                + type);
                    }
                    res = vector;
                }
            } else {
                System.err.println("Unknown element found: " + xpath + " / "
                        + name);
                System.exit(-1);
            }
            if (res != null) {
                if (current == null) {
                    current = first = res;
                } else {
                    current.next = res;
                    current = current.next;
                }
                res = null;
            }
            xpath.delete(oldLength, xpath.length());
        }
        xpath.delete(savedLength, xpath.length());
        return first;
    }

    private ICUResourceWriter.Resource parseLayout(InputLocale loc, String xpath) {
        ICUResourceWriter.ResourceTable table = new ICUResourceWriter.ResourceTable();

        table.name = loc.getXpathName(xpath);

        if (loc.isPathNotConvertible(xpath)) {
            return null;
        }

        for (Iterator<String> iter = loc.file.iterator(xpath); iter.hasNext();) {
            String aPath = iter.next();
            String name = loc.getXpathName(aPath);

            ICUResourceWriter.Resource res = null;
            if (name.equals(LDMLConstants.ALIAS)) {
                res = parseAliasResource(loc, aPath);
                return res;
            } else if (name.equals(LDMLConstants.INLIST)) {
                ICUResourceWriter.ResourceString cs = null;
                if (!loc.isPathNotConvertible(aPath)) {
                    String casing = loc.getBasicAttributeValue(xpath,
                            LDMLConstants.CASING);
                    if (casing != null) {
                        cs = new ICUResourceWriter.ResourceString();
                        cs.comment = "Used for figuring out the casing of characters in a list.";
                        cs.name = LDMLConstants.CASING;
                        cs.val = casing;
                        res = cs;
                    }
                }
            } else if (name.equals(LDMLConstants.ORIENTATION)) {
                ICUResourceWriter.ResourceString chs = null;
                ICUResourceWriter.ResourceString lns = null;
                if (!loc.isPathNotConvertible(aPath)) {
                    String characters = loc.getBasicAttributeValue(aPath,
                            LDMLConstants.CHARACTERS);
                    String lines = loc.getBasicAttributeValue(aPath,
                            LDMLConstants.LINES);
                    if (characters != null) {
                        chs = new ICUResourceWriter.ResourceString();
                        chs.name = LDMLConstants.CHARACTERS;
                        chs.val = characters;
                    }
                    if (lines != null) {
                        lns = new ICUResourceWriter.ResourceString();
                        lns.name = LDMLConstants.LINES;
                        lns.val = lines;
                    }
                    if (chs != null) {
                        res = chs;
                        chs.next = lns;
                    } else {
                        res = lns;
                    }
                }
            } else if (name.equals(LDMLConstants.INTEXT)) {
            } else {
                System.err.println("Unknown element found: " + xpath + " / "
                        + name);
                System.exit(-1);
            }
            if (res != null) {
                table.appendContents(res);
                res = null;
            }
        }
        if (table.first != null) {
            return table;
        }
        return null;
    }

    private ICUResourceWriter.Resource parseDates(InputLocale loc, String xpath) {
        ICUResourceWriter.Resource first = null;
        ICUResourceWriter.Resource current = null;

        // if the whole thing is an alias
        if ((current = getAliasResource(loc, xpath)) != null) {
            return current;
        }

        // if the whole node is marked draft then
        // dont write anything

        final String stuff[] = { LDMLConstants.DEFAULT,
        // LDMLConstants.LPC,
                LDMLConstants.CALENDARS,
        // LDMLConstants.TZN,
        // LDMLConstants.DRP,
        };

        String origXpath = xpath;
        for (int jj = 0; jj < stuff.length; jj++) {
            String name = stuff[jj];
            xpath = origXpath + "/" + name;
            if (loc.isPathNotConvertible(xpath))
                continue;
            ICUResourceWriter.Resource res = null;

            if (name.equals(LDMLConstants.ALIAS)) {
                // dont compute xpath
                // res = parseAliasResource(loc, xpath);
                // handled above
            } else if (name.equals(LDMLConstants.DEFAULT)) {
                res = getDefaultResource(loc, xpath, name);
            } else if (name.equals(LDMLConstants.LPC)) {
                // localized pattern chars are deprecated
            } else if (name.equals(LDMLConstants.CALENDARS)) {
                res = parseCalendars(loc, xpath);
            } else if (name.equals(LDMLConstants.TZN)) {
                res = parseTimeZoneNames(loc, xpath);
            } else if (name.equals(LDMLConstants.DRP)) {
            } else {
                System.err.println("Encountered unknown <" + xpath
                        + "> subelement: " + name);
                System.exit(-1);
            }
            if (res != null) {
                if (current == null) {
                    current = first = res;
                } else {
                    current.next = res;
                    current = current.next;
                }
                res = null;
            }
        }
        return first;
    }

    private ICUResourceWriter.Resource parseCalendars(InputLocale loc,
            String xpath) {
        ICUResourceWriter.ResourceTable table = new ICUResourceWriter.ResourceTable();
        ICUResourceWriter.Resource current = null;
        table.name = LDMLConstants.CALENDAR;

        // if the whole thing is an alias
        if ((current = getAliasResource(loc, xpath)) != null) {
            return current;
        }

        // if the whole node is marked draft then
        // dont write anything

        final String stuff[] = {
        // LDMLConstants.ALIAS,
                LDMLConstants.DEFAULT, LDMLConstants.CALENDAR, };

        String origXpath = xpath;
        for (int jj = 0; jj < stuff.length; jj++) {
            String name = stuff[jj];
            xpath = origXpath + "/" + name;
            if (loc.isPathNotConvertible(xpath))
                continue;
            ICUResourceWriter.Resource res = null;

            if (name.equals(LDMLConstants.ALIAS)) {
                res = parseAliasResource(loc, xpath);
                res.name = table.name;
                return res;
            } else if (name.equals(LDMLConstants.DEFAULT)) {
                res = getDefaultResource(loc, xpath, name);
            } else if (name.equals(LDMLConstants.CALENDAR)) {
                Set<String> cals = loc.getByType(xpath, LDMLConstants.CALENDAR);
                for (String cal : cals) {
                    res = parseCalendar(loc, xpath + "[@type=\"" + cal + "\"]");
                    if (res != null) {
                        table.appendContents(res);
                        res = null;
                    }
                }
                // if there was an item , resync current.
                if (table.first != null) {
                    current = table.first.end();
                }
            } else {
                System.err.println("Encountered unknown <" + xpath
                        + "> subelement: " + name);
                System.exit(-1);
            }
            if (res != null) {
                if (current == null) {
                    current = table.first = res;
                } else {
                    current.next = res;
                    current = current.next;
                }
                res = null;
            }
        }
        if (table.first != null) {
            return table;
        }
        return null;
    }

    private ICUResourceWriter.Resource parseTimeZoneNames(InputLocale loc,
            String xpath) {
        ICUResourceWriter.ResourceTable table = new ICUResourceWriter.ResourceTable();
        ICUResourceWriter.Resource current = null;
        table.name = (String) keyNameMap.get(loc.getXpathName(xpath));

        String origXpath = xpath;
        for (Iterator<String> iter = loc.file.iterator(xpath); iter.hasNext();) {
            xpath = iter.next();
            String name = loc.getXpathName(xpath);
            if (loc.isPathNotConvertible(xpath))
                continue;
            ICUResourceWriter.Resource res = null;

            if (name.equals(LDMLConstants.ALIAS)) {
                res = parseAliasResource(loc, xpath);
                res.name = table.name;
                return res;
            } else if (name.equals(LDMLConstants.DEFAULT)) {
                res = getDefaultResource(loc, xpath, name);
            } else if (name.equals(LDMLConstants.ZONE)) {
                res = parseZone(loc, xpath);
            } else if (name.equals(LDMLConstants.METAZONE)) {
                res = parseMetazone(loc, xpath);
            } else if (name.equals(LDMLConstants.HOUR_FORMAT)
                    || name.equals(LDMLConstants.HOURS_FORMAT)
                    || name.equals(LDMLConstants.GMT_FORMAT)
                    || name.equals(LDMLConstants.REGION_FORMAT)
                    || name.equals(LDMLConstants.FALLBACK_FORMAT)) {
                ICUResourceWriter.ResourceString str = new ICUResourceWriter.ResourceString();
                str.name = name;
                str.val = loc.file.getStringValue(xpath);
                if (str.val != null) {
                    res = str;
                }
            } else if (name.equals(LDMLConstants.ABBREVIATION_FALLBACK)) {
                ICUResourceWriter.ResourceString str = new ICUResourceWriter.ResourceString();
                str.name = name;
                str.val = loc.getBasicAttributeValue(xpath, LDMLConstants.TYPE);
                if (str.val != null) {
                    res = str;
                }
            } else if (name.equals(LDMLConstants.PREFERENCE_ORDERING)
                    || name.equals(LDMLConstants.SINGLE_COUNTRIES)) {
                ICUResourceWriter.ResourceArray arr = new ICUResourceWriter.ResourceArray();
                arr.name = name;
                ICUResourceWriter.Resource c = null;
                String[] values = null;
                if (name.equals(LDMLConstants.SINGLE_COUNTRIES)) {
                    values = loc.getBasicAttributeValue(xpath,
                            LDMLConstants.LIST).split(" ");
                } else {
                    String temp = loc.getBasicAttributeValue(xpath,
                            LDMLConstants.CHOICE);
                    if (temp == null) {
                        temp = loc.getBasicAttributeValue(xpath,
                                LDMLConstants.TYPE);
                        if (temp == null) {
                            throw new IllegalArgumentException(
                                    "Node: "
                                            + name
                                            + "  must have either type or choice attribute");
                        }
                    }
                    values = temp.split("\\s+");
                }

                for (int i = 0; i < values.length; i++) {
                    ICUResourceWriter.ResourceString str = new ICUResourceWriter.ResourceString();
                    str.val = values[i];
                    if (c == null) {
                        arr.first = c = str;
                    } else {
                        c.next = str;
                        c = c.next;
                    }
                }
                if (arr.first != null) {
                    res = arr;
                }
            } else {
                System.err.println("Encountered unknown <" + xpath
                        + "> subelement: " + name);
                System.exit(-1);
            }
            if (res != null) {
                if (current == null) {
                    current = table.first = res;
                } else {
                    current.next = res;
                    current = current.next;
                }
                res = null;
            }
        }
        if (table.first != null) {
            return table;
        }
        return null;
    }

    private ICUResourceWriter.Resource getStringResource(String name,
            Node node, ICUResourceWriter.Resource res) {
        ICUResourceWriter.ResourceString str = new ICUResourceWriter.ResourceString();
        str.name = name;
        str.val = LDMLUtilities.getNodeValue(node);
        if (res == null) {
            res = str;
        } else {
            findLast(res).next = str;
        }
        if (str.val == null) {
            str.val = "";
        }
        return res;
    }

    private ICUResourceWriter.ResourceString getDefaultResource(
            InputLocale loc, String xpath) {
        return getDefaultResource(loc, xpath, loc.getXpathName(xpath));
    }

    /**
     * @deprecated
     * @param node
     * @param xpath
     * @param name
     * @return
     */
    private ICUResourceWriter.ResourceString getDefaultResource(Node node,
            StringBuffer xpath, String name) {
        ICUResourceWriter.ResourceString str = new ICUResourceWriter.ResourceString();
        String temp = LDMLUtilities.getAttributeValue(node,
                LDMLConstants.CHOICE);
        if (temp == null) {
            temp = LDMLUtilities.getAttributeValue(node, LDMLConstants.TYPE);
            if (temp == null) {
                throw new IllegalArgumentException("Node: " + name
                        + "  must have either type or choice attribute");
            }
        }
        str.name = name;
        str.val = temp;
        return str;
    }

    private ICUResourceWriter.ResourceString getDefaultResource(
            InputLocale loc, String xpath, String name) {
        ICUResourceWriter.ResourceString str = new ICUResourceWriter.ResourceString();
        String temp = loc.getBasicAttributeValue(xpath, LDMLConstants.CHOICE);
        if (temp == null) {
            temp = loc.getBasicAttributeValue(xpath, LDMLConstants.TYPE);
            if (temp == null) {
                if (!loc.file.isHere(xpath)) {
                    return null;
                }
                throw new IllegalArgumentException("Node: " + xpath
                        + "  must have either type or choice attribute");
            }
        }
        str.name = name;
        str.val = temp;
        return str;
    }

    private ICUResourceWriter.Resource parseZone(InputLocale loc, String xpath) {
        ICUResourceWriter.ResourceTable table = new ICUResourceWriter.ResourceTable();
        ICUResourceWriter.ResourceTable uses_mz_table = new ICUResourceWriter.ResourceTable();

        boolean writtenEC = false;
        boolean containsUM = false;
        int mz_count = 0;
        boolean isECDraft = false;

        String id = loc.getBasicAttributeValue(xpath, LDMLConstants.TYPE);

        table.name = "\"" + id + "\"";
        table.name = table.name.replace('/', ':');
        ICUResourceWriter.Resource current = null;
        ICUResourceWriter.Resource current_mz = null;
        uses_mz_table.name = "um";

        for (Iterator<String> iter = loc.file.iterator(xpath); iter.hasNext();) {
            String aPath = iter.next();
            String name = loc.getXpathName(aPath);
            ICUResourceWriter.Resource res = null;
            // a ceratain element of the list
            // is marked draft .. just dont
            // output that item
            if (loc.isPathNotConvertible(aPath)) {
                if (name.equals(LDMLConstants.EXEMPLAR_CITY)) {
                    isECDraft = true;
                }
                continue;
            }
            if (name.equals(LDMLConstants.ALIAS)) {
                res = parseAliasResource(loc, aPath);
                if (res != null) {
                    res.name = table.name;
                }
                return res;
            } else if (name.equals(LDMLConstants.DEFAULT)) {
                res = getDefaultResource(loc, aPath, name);
            } else if (name.equals(LDMLConstants.LONG)
                    || name.equals(LDMLConstants.SHORT)) {
                throw new InternalError("Unimp: " + aPath);
                // Node standard = LDMLUtilities.getNode(node,
                // LDMLConstants.STANDARD);
                // Node generic = LDMLUtilities.getNode(node,
                // LDMLConstants.GENERIC);
                // Node daylight = LDMLUtilities.getNode(node,
                // LDMLConstants.DAYLIGHT);
                // if (standard != null) {
                // res = getStringResource(name.charAt(0) + "s", standard, res);
                // }
                // if (generic != null) {
                // res = getStringResource(name.charAt(0) + "g", generic, res);
                // }
                // if (daylight != null) {
                // res = getStringResource(name.charAt(0) + "d", daylight, res);
                // }
            } else if (name.equals(LDMLConstants.COMMONLY_USED)) {
                // TODO: Fix COMMONLY_USED
            } else if (name.equals(LDMLConstants.USES_METAZONE)) {

                ICUResourceWriter.ResourceArray this_mz = new ICUResourceWriter.ResourceArray();
                ICUResourceWriter.ResourceString mzone = new ICUResourceWriter.ResourceString();
                ICUResourceWriter.ResourceString from = new ICUResourceWriter.ResourceString();
                ICUResourceWriter.ResourceString to = new ICUResourceWriter.ResourceString();

                this_mz.name = "mz" + String.valueOf(mz_count);
                this_mz.first = mzone;
                mzone.next = from;
                from.next = to;
                mz_count++;

                mzone.val = loc.getBasicAttributeValue(aPath,
                        LDMLConstants.MZONE);
                String str = loc.getBasicAttributeValue(aPath,
                        LDMLConstants.FROM);
                if (str != null)
                    from.val = str;
                else
                    from.val = "0000-00-00";

                str = loc.getBasicAttributeValue(aPath, LDMLConstants.TO);
                if (str != null)
                    to.val = str;
                else
                    to.val = "9999-12-31";

                if (current_mz == null) {
                    uses_mz_table.first = this_mz;
                    current_mz = findLast(this_mz);
                } else {
                    current_mz.next = this_mz;
                    current_mz = findLast(this_mz);
                }
                containsUM = true;

                res = null;

            } else if (name.equals(LDMLConstants.EXEMPLAR_CITY)) {
                String ec = loc.file.getStringValue(aPath);
                if (ec != null) {
                    ICUResourceWriter.ResourceString str = new ICUResourceWriter.ResourceString();
                    str.name = "ec";
                    str.val = ec;
                    res = str;
                    writtenEC = true;
                }

            } else {
                System.err.println("Encountered unknown <" + xpath
                        + "> subelement: " + name);
                System.exit(-1);
            }
            if (res != null) {
                if (current == null) {
                    table.first = res;
                    current = findLast(res);
                } else {
                    current.next = res;
                    current = findLast(res);
                }
                res = null;
            }
        }

        // Add the metazone mapping table if mz mappings were present
        if (containsUM) {
            ICUResourceWriter.Resource res = uses_mz_table;
            if (current == null) {
                table.first = res;
                current = findLast(res);
            } else {
                current.next = res;
                current = findLast(res);
            }
        }

        // TODO fix this hack once CLDR data is fixed.
        if (writtenEC == false && isECDraft == false) {
            // try to fetch the exemplar city name from the id
            ICUResourceWriter.ResourceString str = new ICUResourceWriter.ResourceString();
            str.name = "ec";
            str.val = id.replaceAll(".*?/(.*)", "$1").replaceAll("_", " ");
            if (current == null) {
                table.first = str;
            } else {
                current.next = str;
            }
        }

        if (table.first != null) {
            return table;
        }
        return null;
    }

    private ICUResourceWriter.Resource parseMetazone(InputLocale loc,
            String xpath) {
        ICUResourceWriter.ResourceTable table = new ICUResourceWriter.ResourceTable();

        boolean writtenEC = false;
        boolean isECDraft = false;

        // if the whole node is marked draft then
        // dont write anything
        String id = loc.getBasicAttributeValue(xpath, LDMLConstants.TYPE);

        table.name = "\"" + "meta:" + id + "\"";
        table.name = table.name.replace('/', ':');
        ICUResourceWriter.Resource current = null;
        for (Iterator<String> iter = loc.file.iterator(xpath); iter.hasNext();) {
            String aPath = iter.next();
            String name = loc.getXpathName(aPath);
            if (loc.isPathNotConvertible(aPath))
                continue;

            ICUResourceWriter.Resource res = null;
            if (name.equals(LDMLConstants.ALIAS)) {
                res = parseAliasResource(loc, aPath);
                if (res != null) {
                    res.name = table.name;
                }
                return res;
            } else if (name.equals(LDMLConstants.DEFAULT)) {
                res = getDefaultResource(loc, aPath, name);
            } else if (name.equals(LDMLConstants.LONG)
                    || name.equals(LDMLConstants.SHORT)) {
                throw new InternalError("not supp: " + aPath);
                // Node standard = LDMLUtilities.getNode(node,
                // LDMLConstants.STANDARD);
                // Node generic = LDMLUtilities.getNode(node,
                // LDMLConstants.GENERIC);
                // Node daylight = LDMLUtilities.getNode(node,
                // LDMLConstants.DAYLIGHT);
                // if (standard != null) {
                // res = getStringResource(name.charAt(0) + "s", standard, res);
                // }
                // if (generic != null) {
                // res = getStringResource(name.charAt(0) + "g", generic, res);
                // }
                // if (daylight != null) {
                // res = getStringResource(name.charAt(0) + "d", daylight, res);
                // }

            } else if (name.equals(LDMLConstants.COMMONLY_USED)) {
                // TODO: Fix COMMONLY_USED
            } else {
                System.err.println("Encountered unknown <" + aPath
                        + "> subelement: " + name);
                System.exit(-1);
            }
            if (res != null) {
                if (current == null) {
                    table.first = res;
                    current = findLast(res);
                } else {
                    current.next = res;
                    current = findLast(res);
                }
                res = null;
            }
        }

        if (table.first != null) {
            return table;
        }
        return null;
    }

    /*
     * private ICUResourceWriter.Resource parseShortLong(Node root, StringBuffer
     * xpath){
     * 
     * if(isNodeConvertible(root, xpath)){ return null ; } //the alt atrribute
     * is set .. so ignore the resource if(isAlternate(root)){ return null; }
     * int savedLength = xpath.length(); getXPath(root, xpath); Node sn =
     * getVettedNode(root, LDMLConstants.STANDARD, xpath); Node dn =
     * getVettedNode(root, LDMLConstants.DAYLIGHT, xpath);
     * if(sn==null||dn==null){ System.err.println("Could not get timeZone string
     * for " + xpath.toString()); System.exit(-1); }
     * ICUResourceWriter.ResourceString ss = new
     * ICUResourceWriter.ResourceString(); ICUResourceWriter.ResourceString ds =
     * new ICUResourceWriter.ResourceString(); ss.val =
     * LDMLUtilities.getNodeValue(sn); ds.val = LDMLUtilities.getNodeValue(dn);
     * xpath.delete(savedLength, xpath.length()); ss.next = ds; return ss; }
     */
    private ICUResourceWriter.Resource parseLeapMonth() {
        if (specialsDoc != null) {
            Node root = LDMLUtilities.getNode(specialsDoc,
                    "//ldml/dates/calendars/calendar[@type='chinese']/special");
            if (root != null) {
                for (Node node = root.getFirstChild(); node != null; node = node
                        .getNextSibling()) {
                    if (node.getNodeType() != Node.ELEMENT_NODE) {
                        continue;
                    }
                    String name = node.getNodeName();
                    if (name.equals(ICU_IS_LEAP_MONTH)) {
                        Node nonLeapSymbol = LDMLUtilities.getNode(node,
                                "icu:nonLeapSymbol", root);
                        Node leapSymbol = LDMLUtilities.getNode(node,
                                "icu:leapSymbol", root);
                        if (nonLeapSymbol != null && leapSymbol != null) {
                            ICUResourceWriter.ResourceArray arr = new ICUResourceWriter.ResourceArray();
                            arr.name = "isLeapMonth";
                            ICUResourceWriter.ResourceString str1 = new ICUResourceWriter.ResourceString();
                            ICUResourceWriter.ResourceString str2 = new ICUResourceWriter.ResourceString();
                            str1.val = LDMLUtilities
                                    .getNodeValue(nonLeapSymbol);
                            if (str1.val == null) {
                                str1.val = "";
                            }
                            str2.val = LDMLUtilities.getNodeValue(leapSymbol);
                            arr.first = str1;
                            str1.next = str2;
                            return arr;
                        } else {
                            System.err
                                    .println("Did not get required number of elements for isLeapMonth resource. Please check the data.");
                            System.exit(-1);
                        }
                    } else {
                        System.err.println("Encountered unknown <"
                                + root.getNodeName() + "> subelement: " + name);
                        System.exit(-1);
                    }
                }
            }
        }
        return null;
    }

    private ICUResourceWriter.Resource parseCalendar(InputLocale loc,
            String xpath) {
        ICUResourceWriter.ResourceTable table = new ICUResourceWriter.ResourceTable();
        ICUResourceWriter.Resource current = null;
        // if the whole calendar node is marked draft then
        // dont write anything
        boolean writtenAmPm = false;
        boolean writtenDTF = false;
        String origXpath = xpath;
        // if the whole thing is an alias
        if ((current = getAliasResource(loc, xpath)) != null) {
            return current;
        }
        table.name = loc.getAttributeValue(xpath, LDMLConstants.CALENDAR,
                LDMLConstants.TYPE);

        // if the whole node is marked draft then
        // dont write anything

        final String stuff[] = {
                LDMLConstants.DEFAULT,
                LDMLConstants.MONTHS,
                LDMLConstants.DAYS,
                // LDMLConstants.WEEK,
                LDMLConstants.AM, LDMLConstants.PM, LDMLConstants.ERAS,
                LDMLConstants.DATE_FORMATS, LDMLConstants.TIME_FORMATS,
                LDMLConstants.DATE_TIME_FORMATS, LDMLConstants.SPECIAL,
                LDMLConstants.FIELDS, LDMLConstants.QUARTERS, };

        for (int jj = 0; jj < stuff.length; jj++) {
            String name = stuff[jj];
            xpath = origXpath + "/" + name;
            if (loc.isPathNotConvertible(xpath))
                continue;
            ICUResourceWriter.Resource res = null;

            if (name.equals(LDMLConstants.ALIAS)) {
                res = parseAliasResource(loc, xpath);
                if (res != null) {
                    res.name = table.name;
                }
                return res;
            } else if (name.equals(LDMLConstants.DEFAULT)) {
                res = getDefaultResource(loc, xpath, name);
            } else if (name.equals(LDMLConstants.MONTHS)
                    || name.equals(LDMLConstants.DAYS)) {
                res = parseMonthsAndDays(loc, xpath);
            } else if (name.equals(LDMLConstants.WEEK)) {
                // ICUResourceWriter.Resource temp = parseWeek(node, xpath);
                // if(temp!=null){
                // res = temp;
                // }
                // WEEK is deprecated in CLDR 1.4
                printInfo("<week> element is deprecated and the data should moved to supplementalData.xml");
            } else if (name.equals(LDMLConstants.AM)
                    || name.equals(LDMLConstants.PM)) {
                // TODO: figure out the tricky parts .. basically get the
                // missing element from
                // fully resolved locale!
                if (writtenAmPm == false) {
                    writtenAmPm = true;
                    res = parseAmPm(loc, origXpath); // We feed ampm the
                                                        // original xpath.
                }
            } else if (name.equals(LDMLConstants.ERAS)) {
                res = parseEras(loc, xpath);
            } else if (name.equals(LDMLConstants.DATE_FORMATS)
                    || name.equals(LDMLConstants.TIME_FORMATS)
                    || name.equals(LDMLConstants.DATE_TIME_FORMATS)) {
                // TODO what to do if a number of formats are present?
                if (writtenDTF == false) {
                    res = parseDTF(loc, origXpath);
                    writtenDTF = true;
                }
                if (name.equals(LDMLConstants.DATE_TIME_FORMATS)) {
                    // handle flexi formats
                    ICUResourceWriter.Resource temp;
                    
                    temp = parseTable(loc,xpath+"/"+LDMLConstants.AVAIL_FMTS, LDMLConstants.DATE_FMT_ITEM, LDMLConstants.ID);
                    if(temp != null) {
                        temp.name = LDMLConstants.AVAIL_FMTS;
                        res = ICUResourceWriter.Resource.addAfter(res, temp);    
                    }
                    
                    temp = parseTable(loc,xpath+"/"+LDMLConstants.APPEND_ITEMS, LDMLConstants.APPEND_ITEM, LDMLConstants.REQUEST);
                    if(temp != null) {
                        temp.name = LDMLConstants.APPEND_ITEMS;
                        res = ICUResourceWriter.Resource.addAfter(res, temp);    
                    }
                }
            } else if (name.equals(LDMLConstants.SPECIAL)) {
                res = parseSpecialElements(loc, xpath);
            } else if (name.equals(LDMLConstants.FIELDS)) {
                // if the whole thing is an alias
                if ((res = getAliasResource(loc, xpath)) == null) {
                    Set<String> fields = loc.getByType(xpath,
                            LDMLConstants.FIELD);
                    for (String field : fields) {
                        res = parseField(loc, xpath + "/field[@type=\"" + field
                                + "\"]", field);
                        if (res != null) {
                            table.appendContents(res);
                        }
                    }
                    // reset 'current'
                    if (table.first != null) {
                        current = table.first.end();
                    }
                    res = null; // already added.
                }
            } else if (name.equals(LDMLConstants.QUARTERS)) {
                // if(DEBUG)printXPathWarning(node, xpath);
                res = parseMonthsAndDays(loc, xpath);

            } else {
                System.err.println("Encountered unknown <" + xpath
                        + "> subelement: " + name);
                System.exit(-1);
            }
            if (res != null) {
                if (current == null) {
                    table.first = res;
                    current = findLast(res);
                } else {
                    current.next = res;
                    current = findLast(res);
                }
                res = null;
            }
        }

        // TODO remove this hack once isLeapMonth data
        // is represented in LDML (should not be necessary
        if (table != null && table.name != null && table.name.equals("chinese")
                && table.first != null) {
            findLast(table.first).next = parseLeapMonth();
        }
        if (table.first != null) {
            return table;
        }
        return null;
    }

    // private ICUResourceWriter.Resource parseFields(InputLocale loc, String
    // xpath, String type) {
    // ICUResourceWriter.ResourceTable table = new
    // ICUResourceWriter.ResourceTable();
    // ICUResourceWriter.Resource current = null;
    // table.name = type;
    //
    //
    // // if the whole node is marked draft then
    // // dont write anything
    // String origXpath = xpath;
    // for(Iterator<String> iter = loc.file.iterator(xpath); iter.hasNext();) {
    // xpath = iter.next();
    // String name = loc.getXpathName(xpath);
    // if(loc.isPathNotConvertible(xpath)) continue;
    // ICUResourceWriter.Resource res = null;
    //
    // if (name.equals(LDMLConstants.ALIAS)) {
    // res = parseAliasResource(loc, xpath);
    // res.name = table.name;
    // return res;
    // } else if (name.equals(LDMLConstants.FIELD)) {
    // res = parseField(loc, xpath);
    // } else {
    // System.err.println("Encountered unknown <" + xpath + "> field subelement:
    // " + name);
    // System.exit(-1);
    // }
    // if (res != null) {
    // if (current == null) {
    // current = table.first = res;
    // } else {
    // current.next = res;
    // current = current.next;
    // }
    // res = null;
    // }
    // }
    // if (table.first != null) {
    // return table;
    // }
    // return null;
    // }

    private ICUResourceWriter.Resource parseField(InputLocale loc,
            String xpath, String type) {
        ICUResourceWriter.ResourceTable table = new ICUResourceWriter.ResourceTable();
        ICUResourceWriter.Resource current = null;
        table.name = type;
        ICUResourceWriter.ResourceString dn = null;
        ICUResourceWriter.ResourceTable relative = new ICUResourceWriter.ResourceTable();
        relative.name = LDMLConstants.RELATIVE;

        // if the whole node is marked draft then
        // dont write anything
        if (loc.isPathNotConvertible(xpath)) {
            return null;
        }

        // if the whole thing is an alias
        if ((current = getAliasResource(loc, xpath)) != null) {
            return current;
        }

        String origXpath = xpath;
        for (Iterator<String> iter = loc.file.iterator(xpath); iter.hasNext();) {
            xpath = iter.next();
            String name = loc.getXpathName(xpath);
            if (loc.isPathNotConvertible(xpath))
                continue;
            ICUResourceWriter.Resource res = null;

            if (name.equals(LDMLConstants.ALIAS)) {
                res = parseAliasResource(loc, xpath);
                res.name = table.name;
                return res;
            } else if (name.equals(LDMLConstants.RELATIVE)) {
                ICUResourceWriter.ResourceString str = new ICUResourceWriter.ResourceString();
                str.name = "\""
                        + loc.getBasicAttributeValue(xpath, LDMLConstants.TYPE)
                        + "\"";
                str.val = loc.file.getStringValue(xpath);
                res = str;
                if (res != null) {
                    if (current == null) {
                        current = relative.first = res;
                    } else {
                        current.next = res;
                        current = current.next;
                    }
                    res = null;
                }
            } else if (name.equals(LDMLConstants.DISPLAY_NAME)) {
                dn = new ICUResourceWriter.ResourceString();
                dn.name = (String) keyNameMap.get(LDMLConstants.DISPLAY_NAME);
                dn.val = loc.file.getStringValue(xpath);
            } else {
                System.err.println("Encountered unknown <" + xpath
                        + "> subelement: " + name);
                System.exit(-1);
            }

        }
        if (dn != null) {
            table.first = dn;
        }
        if (relative.first != null) {
            if (table.first != null) {
                table.first.next = relative;
            } else {
                table.first = relative;
            }
        }
        if (table.first != null) {
            return table;
        }
        return null;
    }

    private ICUResourceWriter.Resource parseMonthsAndDays(InputLocale loc,
            String xpath) {
        ICUResourceWriter.ResourceTable table = new ICUResourceWriter.ResourceTable();
        ICUResourceWriter.Resource current = null;
        String name = loc.getXpathName(xpath);
        table.name = (String) keyNameMap.get(name);

        // if the whole thing is an alias
        if ((current = getAliasResource(loc, xpath)) != null) {
            return current;
        }

        // if the whole node is marked draft then
        // dont write anything

        final String stuff[] = { LDMLConstants.DEFAULT,
                LDMLConstants.MONTH_CONTEXT, LDMLConstants.DAY_CONTEXT,
                LDMLConstants.QUARTER_CONTEXT, };

        String origXpath = xpath;
        for (int jj = 0; jj < stuff.length; jj++) {
            name = stuff[jj];
            xpath = origXpath + "/" + name;
            if (loc.isPathNotConvertible(xpath))
                continue;
            ICUResourceWriter.Resource res = null;

            if (name.equals(LDMLConstants.ALIAS)) {
                res = parseAliasResource(loc, xpath);
                res.name = table.name;
                return res;
            } else if (name.equals(LDMLConstants.DEFAULT)) {
                res = getDefaultResource(loc, xpath, name);
                ;
            } else if (name.equals(LDMLConstants.MONTH_CONTEXT)
                    || name.equals(LDMLConstants.DAY_CONTEXT)
                    || name.equals(LDMLConstants.QUARTER_CONTEXT)) {
                Set<String> ctxs = loc.getByType(xpath, name);
                for (String ctx : ctxs) {
                    res = parseContext(loc, xpath + "[@type=\"" + ctx + "\"]");
                    if (res != null) {
                        table.appendContents(res);
                        res = null;
                    }
                }
                // if there was an item , resync current.
                if (table.first != null) {
                    current = table.first.end();
                }
            } else {
                System.err.println("Encountered unknown <" + xpath
                        + "> subelement: " + name);
                System.exit(-1);
            }
            if (res != null) {
                if (current == null) {
                    current = table.first = res;
                } else {
                    current.next = res;
                    current = current.next;
                }
                res = null;
            }
        }
        if (table.first != null) {
            return table;
        }
        return null;
    }

    private ICUResourceWriter.Resource parseContext(InputLocale loc,
            String xpath) {
        ICUResourceWriter.ResourceTable table = new ICUResourceWriter.ResourceTable();
        ICUResourceWriter.Resource current = null;

        // if the whole collation node is marked draft then
        // dont write anything
        if (loc.isPathNotConvertible(xpath)) {
            return null;
        }

        String myName = loc.getXpathName(xpath);
        String resName = myName.substring(0, myName.lastIndexOf("Context"));
        table.name = loc.getAttributeValue(xpath, myName, LDMLConstants.TYPE);
        if (table.name == null) {
            throw new InternalError("Can't get table name for " + xpath + " / "
                    + resName + " / " + LDMLConstants.TYPE);
        }

        // if the whole thing is an alias
        if ((current = getAliasResource(loc, xpath)) != null) {
            return current;
        }

        // if the whole node is marked draft then
        // dont write anything

        String stuff[] = { LDMLConstants.DEFAULT, resName + "Width", };

        String origXpath = xpath;
        for (int jj = 0; jj < stuff.length; jj++) {
            String name = stuff[jj];
            xpath = origXpath + "/" + name;
            if (loc.isPathNotConvertible(xpath))
                continue;
            ICUResourceWriter.Resource res = null;

            if (name.equals(LDMLConstants.ALIAS)) {
                res = parseAliasResource(loc, xpath);
                if (res != null) {
                    res.name = table.name;
                }
                return res; // an alias if for the resource
            } else if (name.equals(LDMLConstants.DEFAULT)) {
                res = getDefaultResource(loc, xpath, name);
                ;
            } else if (name.equals(resName + "Width")) {
                Set<String> ctxs = loc.getByType(xpath, name);
                for (String ctx : ctxs) {
                    res = parseWidth(loc, resName, xpath + "[@type=\"" + ctx
                            + "\"]");
                    if (res != null) {
                        table.appendContents(res);
                        res = null;
                    }
                }
                // if there was an item , resync current.
                if (table.first != null) {
                    current = table.first.end();
                }
            } else {
                System.err.println("Encountered unknown <" + xpath
                        + "> subelement: " + name);
                System.exit(-1);
            }
            if (res != null) {
                if (current == null) {
                    current = table.first = res;
                } else {
                    current.next = res;
                    current = current.next;
                }
                res = null;
            }
        }
        if (table.first != null) {
            return table;
        }
        return null;
    }

    private String getDayNumberAsString(String type) {
        if (type.equals("sun")) {
            return "1";
        } else if (type.equals("mon")) {
            return "2";
        } else if (type.equals("tue")) {
            return "3";
        } else if (type.equals("wed")) {
            return "4";
        } else if (type.equals("thu")) {
            return "5";
        } else if (type.equals("fri")) {
            return "6";
        } else if (type.equals("sat")) {
            return "7";
        } else {
            throw new IllegalArgumentException("Unknown type: " + type);
        }
    }

    private ICUResourceWriter.Resource parseWidth(InputLocale loc,
            String resName, String xpath) {
        ICUResourceWriter.ResourceArray array = new ICUResourceWriter.ResourceArray();
        ICUResourceWriter.Resource current = null;
        array.name = loc.getAttributeValue(xpath, resName + "Width",
                LDMLConstants.TYPE);

        // if the whole node is marked draft then
        // dont write anything
        if (loc.isPathNotConvertible(xpath)) {
            return null;
        }

        // if the whole thing is an alias
        if ((current = getAliasResource(loc, xpath)) != null) {
            current.name = array.name;
            return current;
        } else {
            // System.err.println("NO alias in: " + xpath);
        }

        Map<String, String> map = getElementsMap(loc, resName, xpath, false);

        if (map.size() == 0) {
            // System.err.println(" -- no vals, exitting " + xpath);
            return null; // no items here.
        }

        Map<String, String> defMap = null;
        if (loc.resolved() != null) {
            defMap = getElementsMap(loc, resName, xpath, true);
        }
        Set<String> allThings = new TreeSet<String>();
        allThings.addAll(map.keySet());
        if (defMap != null) {
            allThings.addAll(defMap.keySet());
        }
        // if (defMap != null && (map.size() != defMap.size())) {
        // map = defMap;
        if ((resName.equals(LDMLConstants.DAY) && allThings.size() < 7)
                || (resName.equals(LDMLConstants.MONTH) && allThings.size() < 12)) {
            printError("", "Could not get full " + resName + " array. ["
                    + xpath + "] Only found " + map.size()
                    + " items  in target locale ( " + allThings.size()
                    + " including " + ((defMap != null) ? defMap.size() : 0)
                    + " inherited). Fatal error exiting");
            throw new InternalError("data problem");
        }
        // }
        if (map.size() > 0) {
            for (int i = 0; i < allThings.size(); i++) {
                String key = Integer.toString(i);
                ICUResourceWriter.ResourceString res = new ICUResourceWriter.ResourceString();
                res.val = (String) map.get(key);
                if (res.val == null && defMap != null) {
                    res.val = defMap.get(key);
                    if (verboseFallbackComments && res.val != null) {
                        res.smallComment = " fallback";
                    }
                }
                if (res.val == null) {
                    printError("", "Could not get full " + resName
                            + " array. Missing " + key + ".  Only found "
                            + map.size() + " items ( " + allThings.size()
                            + " including inherited). Fatal error exiting");
                    throw new InternalError("data problem");
                }
                // array of unnamed strings
                if (res.val != null) {
                    if (current == null) {
                        current = array.first = res;
                    } else {
                        current.next = res;
                        current = current.next;
                    }
                }
            }
        }

        // parse the default node
        {
            ICUResourceWriter.ResourceString res = getDefaultResource(loc,
                    xpath + "/default");
            if (res != null) {
                System.err.println("Found def for " + xpath + " - " + res.val);
                if (current == null) {
                    current = array.first = res;
                } else {
                    current.next = res;
                    current = current.next;
                }
            }
        }
        if (array.first != null) {
            return array;
        }
        return null;
    }

    /**
     * @deprecated
     * @param root
     * @param xpath
     * @return
     */
    private TreeMap getElementsMap(Node root, StringBuffer xpath) {
        return getElementsMap(root, xpath, false);
    }

    private static Set<String> completion_day = null;
    private static Set<String> completion_month = null;
    private static Set<String> completion_era = null;
    private static Set<String> completion_q = null;
    private static Set<String> completion_era_j = null;

    private Set<String> createNumericStringArray(int max) {
        Set<String> set = new HashSet<String>();
        for (int i = 0; i <= max; i++) {
            set.add(new Integer(i).toString());
        }
        return set;
    }

    private Set<String> getSetCompletion(InputLocale loc, String element,
            String xpath) {
        if (element.equals(LDMLConstants.DAY)) {
            if (completion_day == null) {
                completion_day = new HashSet<String>();
                String days[] = { LDMLConstants.SUN, LDMLConstants.MON,
                        LDMLConstants.TUE, LDMLConstants.WED,
                        LDMLConstants.THU, LDMLConstants.FRI, LDMLConstants.SAT };
                for (String day : days) {
                    completion_day.add(day);
                }
            }
            return completion_day;
        } else if (element.equals(LDMLConstants.MONTH)) {
            if (completion_month == null) {
                completion_month = createNumericStringArray(13);
            }
            return completion_month;
        } else if (element.equals(LDMLConstants.ERA)) {
            if (completion_era == null) {
                completion_era = createNumericStringArray(2);
                completion_era_j = createNumericStringArray(235);
            }
            String type = loc.getAttributeValue(xpath, LDMLConstants.CALENDAR,
                    LDMLConstants.TYPE);
            if (type.equals("japanese")) {
                return completion_era_j;
            } else {
                return completion_era;
            }
        } else if (element.equals(LDMLConstants.QUARTER)) {
            if (completion_q == null) {
                completion_q = createNumericStringArray(4);
            }
            return completion_q;
        } else {
            System.err.println("Warning: no known completion for " + element);
            return null;
        }
    }

    /**
     * @param loc
     * @param xpath
     * @param isNodeFromRoot
     * @return
     */
    private Map<String, String> getElementsMap(InputLocale loc, String element,
            String xpath, boolean fromResolved) {
        Map<String, String> map = new TreeMap<String, String>();
        CLDRFile whichFile;
        if (fromResolved) {
            whichFile = loc.resolved();
        } else {
            whichFile = loc.file;
        }
        String origXpath = xpath;
        for (Iterator<String> iter = whichFile.iterator(xpath); iter.hasNext();) {
            xpath = iter.next();
            if (loc.isPathNotConvertible(whichFile, xpath)) {
                continue;
            }
            String name = loc.getXpathName(xpath);
            String val = whichFile.getStringValue(xpath);
            String type = loc
                    .getAttributeValue(xpath, name, LDMLConstants.TYPE);

            if (name.equals(LDMLConstants.DAY)) {
                map.put(LDMLUtilities.getDayIndexAsString(type), val);
            } else if (name.equals(LDMLConstants.MONTH)) {
                map.put(LDMLUtilities.getMonthIndexAsString(type), val);
            } else if (name.equals(LDMLConstants.ERA)) {
                map.put(type, val);
            } else if (name.equals(LDMLConstants.QUARTER)) {
                map.put(LDMLUtilities.getMonthIndexAsString(type), val);
            } else if (name.equals(LDMLConstants.ALIAS)) {
                if (fromResolved) {
                    continue; // OK - inherits .
                } else {
                    System.err.println("Encountered unknown alias <res:"
                            + fromResolved + " - " + xpath + " / " + name
                            + "> subelement: " + name);
                    System.exit(-1);
                }
            } else {
                System.err.println("Encountered unknown <res:" + fromResolved
                        + " - " + xpath + " / " + name + "> subelement: "
                        + name);
                System.exit(-1);
            }
        }
        int mapSize = map.size();
        Set<String> completion = getSetCompletion(loc, element, xpath);
        if (completion != null) {
            for (String type : completion) {
                xpath = origXpath + "/" + element + "[@type=\"" + type + "\"]";
                if (loc.isPathNotConvertible(whichFile, xpath)) {
                    continue;
                }
                String name = loc.getXpathName(xpath);
                String val = whichFile.getStringValue(xpath);
                if (val == null) {
                    continue;
                }

                // String type = loc.getAttributeValue(xpath, name,
                // LDMLConstants.TYPE);

                if (name.equals(LDMLConstants.DAY)) {
                    map.put(LDMLUtilities.getDayIndexAsString(type), val);
                } else if (name.equals(LDMLConstants.MONTH)) {
                    map.put(LDMLUtilities.getMonthIndexAsString(type), val);
                } else if (name.equals(LDMLConstants.ERA)) {
                    map.put(type, val);
                } else if (name.equals(LDMLConstants.QUARTER)) {
                    map.put(LDMLUtilities.getMonthIndexAsString(type), val);
                } else {
                    throw new InternalError("Unknown name " + name);
                }
            }
            // System.err.println("After completion on "+origXpath +":
            // "+mapSize+" -> " + map.size());
        }
        return map;
    }

    /**
     * @deprecated
     * @param root
     * @param xpath
     * @param isNodeFromRoot
     * @return
     */

    private TreeMap getElementsMap(Node root, StringBuffer xpath,
            boolean isNodeFromRoot) {
        TreeMap map = new TreeMap();
        int saveLength = xpath.length();
        for (Node node = root.getFirstChild(); node != null; node = node
                .getNextSibling()) {
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            getXPath(node, xpath);
            if (isNodeNotConvertible(node, xpath)) {
                xpath.setLength(saveLength);
                continue;
            }

            String name = node.getNodeName();
            String val = LDMLUtilities.getNodeValue(node);
            String type = LDMLUtilities.getAttributeValue(node,
                    LDMLConstants.TYPE);

            if (name.equals(LDMLConstants.DAY)) {
                map.put(LDMLUtilities.getDayIndexAsString(type), val);
            } else if (name.equals(LDMLConstants.MONTH)) {
                map.put(LDMLUtilities.getMonthIndexAsString(type), val);
            } else if (name.equals(LDMLConstants.ERA)) {
                map.put(type, val);
            } else if (name.equals(LDMLConstants.QUARTER)) {
                map.put(LDMLUtilities.getMonthIndexAsString(type), val);
            } else {
                System.err.println("Encountered unknown <" + root.getNodeName()
                        + "> subelement: " + name);
                System.exit(-1);
            }
            xpath.setLength(saveLength);
        }
        return map;
    }

    private ICUResourceWriter.Resource parseWeek() {
        String country = ULocale.getCountry(locName);
        ICUResourceWriter.Resource ret = null;
        String variant = ULocale.getVariant(locName);
        // optimization
        if (variant.length() != 0) {
            return ret;
        }
        StringBuffer xpath = new StringBuffer("//supplementalData/weekData");
        Node root = LDMLUtilities.getNode(supplementalDoc, xpath.toString());
        if (root != null) {
            ICUResourceWriter.Resource week = parseWeekend(root, xpath, country);
            ICUResourceWriter.Resource dte = parseDTE(root, xpath, country);
            if (week != null) {
                week.next = dte;
                ret = week;
            } else {
                ret = dte;
            }

        }
        return ret;
    }

    private int getMillis(String time) {
        String[] strings = time.split(":"); // time is in hh:mm format
        int hours = Integer.parseInt(strings[0]);
        int minutes = Integer.parseInt(strings[1]);
        return (hours * 60 + minutes) * 60 * 1000;
    }

    private Node getVettedNode(Node ctx, String node, String attrb,
            String attrbVal, StringBuffer xpath) {
        int savedLength = xpath.length();
        NodeList list = LDMLUtilities.getNodeList(ctx, node, null, xpath
                .toString());
        Node ret = null;
        for (int i = 0; i < list.getLength(); i++) {
            Node item = list.item(i);
            String val = LDMLUtilities.getAttributeValue(item, attrb);
            getXPath(item, xpath);
            if (val.matches(".*\\b" + attrbVal + "\\b.*")) {
                if (!isNodeNotConvertible(item, xpath)) {
                    ret = item;
                }
                break;
            }
            xpath.setLength(savedLength);
        }
        xpath.setLength(savedLength);
        return ret;
    }

    private ICUResourceWriter.Resource parseWeekend(Node root,
            StringBuffer xpath, String country) {
        Node wkendStart = null;
        Node wkendEnd = null;
        if (country.length() > 0) {
            wkendStart = getVettedNode(root, LDMLConstants.WENDSTART,
                    LDMLConstants.TERRITORIES, country, xpath);
            wkendEnd = getVettedNode(root, LDMLConstants.WENDEND,
                    LDMLConstants.TERRITORIES, country, xpath);
        }

        if (((wkendEnd != null) || (wkendStart != null))
                || locName.equals("root")) {
            if (wkendStart == null) {
                wkendStart = getVettedNode(null, root, LDMLConstants.WENDSTART
                        + "[@territories='001']", xpath, true);
                if (wkendStart == null) {
                    printError("parseWeekend",
                            "Could not find weekendStart resource.");
                }
            }
            if (wkendEnd == null) {
                wkendEnd = getVettedNode(null, root, LDMLConstants.WENDEND
                        + "[@territories='001']", xpath, true);
                if (wkendEnd == null) {
                    printError("parseWeekend",
                            "Could not find weekendEnd resource.");
                }
            }
        }

        ICUResourceWriter.ResourceIntVector wkend = null;

        if (wkendStart != null && wkendEnd != null) {
            try {
                wkend = new ICUResourceWriter.ResourceIntVector();
                wkend.name = LDMLConstants.WEEKEND;
                ICUResourceWriter.ResourceInt startday = new ICUResourceWriter.ResourceInt();
                startday.val = getDayNumberAsString(LDMLUtilities
                        .getAttributeValue(wkendStart, LDMLConstants.DAY));
                ICUResourceWriter.ResourceInt starttime = new ICUResourceWriter.ResourceInt();
                String time = LDMLUtilities.getAttributeValue(wkendStart,
                        LDMLConstants.TIME);
                starttime.val = Integer
                        .toString(getMillis(time == null ? "00:00" : time));
                ICUResourceWriter.ResourceInt endday = new ICUResourceWriter.ResourceInt();
                endday.val = getDayNumberAsString(LDMLUtilities
                        .getAttributeValue(wkendEnd, LDMLConstants.DAY));
                ICUResourceWriter.ResourceInt endtime = new ICUResourceWriter.ResourceInt();

                time = LDMLUtilities.getAttributeValue(wkendEnd,
                        LDMLConstants.TIME);
                endtime.val = Integer.toString(getMillis(time == null ? "24:00"
                        : time));

                wkend.first = startday;
                startday.next = starttime;
                starttime.next = endday;
                endday.next = endtime;
            } catch (NullPointerException ex) {
                throw new RuntimeException(ex);
            }
        }

        return wkend;
    }

    private ICUResourceWriter.Resource parseDTE(Node root, StringBuffer xpath,
            String country) {
        Node minDays = null;
        Node firstDay = null;
        ICUResourceWriter.ResourceIntVector dte = null;

        if (country.length() > 0) {
            minDays = getVettedNode(root, LDMLConstants.MINDAYS,
                    LDMLConstants.TERRITORIES, country, xpath);
            firstDay = getVettedNode(root, LDMLConstants.FIRSTDAY,
                    LDMLConstants.TERRITORIES, country, xpath);
        }

        if (((minDays != null) || (firstDay != null)) || locName.equals("root")) { // only
            // if
            // we
            // have
            // ONE
            // or
            // the
            // other.
            // fetch inherited to complete the resource..
            if (minDays == null) {
                minDays = getVettedNode(null, root, LDMLConstants.MINDAYS
                        + "[@territories='001']", xpath, true);
                if (minDays == null) {
                    printError("parseDTE", "Could not find minDays resource.");
                }
            }
            if (firstDay == null) {
                firstDay = getVettedNode(null, root, LDMLConstants.FIRSTDAY
                        + "[@territories='001']", xpath, true);
                if (firstDay == null) {
                    printError("parseDTE", "Could not find firstDay resource.");
                }
            }
        }

        if (minDays != null && firstDay != null) {
            dte = new ICUResourceWriter.ResourceIntVector();
            ICUResourceWriter.ResourceInt int1 = new ICUResourceWriter.ResourceInt();
            int1.val = getDayNumberAsString(LDMLUtilities.getAttributeValue(
                    firstDay, LDMLConstants.DAY));
            ICUResourceWriter.ResourceInt int2 = new ICUResourceWriter.ResourceInt();
            int2.val = LDMLUtilities.getAttributeValue(minDays,
                    LDMLConstants.COUNT);

            dte.name = DTE;
            dte.first = int1;
            int1.next = int2;
        }
        if ((minDays == null && firstDay != null)
                || (minDays != null && firstDay == null)) {
            System.err
                    .println("WARNING: Could not find minDays="
                            + minDays
                            + " or firstDay="
                            + firstDay
                            + " from fullyResolved locale. Not producing the resource. "
                            + xpath.toString());
            return null;
        }
        return dte;
    }

    private ICUResourceWriter.Resource parseEras(InputLocale loc, String xpath) {
        ICUResourceWriter.ResourceTable table = new ICUResourceWriter.ResourceTable();
        ICUResourceWriter.Resource current = null;

        table.name = LDMLConstants.ERAS;

        // if the whole thing is an alias
        if ((current = getAliasResource(loc, xpath)) != null) {
            return current;
        }

        // if the whole node is marked draft then
        // dont write anything

        final String stuff[] = { LDMLConstants.DEFAULT, LDMLConstants.ERAABBR,
                LDMLConstants.ERANAMES, LDMLConstants.ERANARROW, };

        String origXpath = xpath;
        for (int jj = 0; jj < stuff.length; jj++) {
            String name = stuff[jj];
            xpath = origXpath + "/" + name;
            if (loc.isPathNotConvertible(xpath))
                continue;
            ICUResourceWriter.Resource res = null;

            if (name.equals(LDMLConstants.ALIAS)) {
                res = parseAliasResource(loc, xpath);
                res.name = table.name;
                return res;
            } else if (name.equals(LDMLConstants.DEFAULT)) {
                res = getDefaultResource(loc, xpath, name);
            } else if (name.equals(LDMLConstants.ERAABBR)) {
                res = parseEra(loc, xpath, LDMLConstants.ABBREVIATED);
            } else if (name.equals(LDMLConstants.ERANAMES)) {
                res = parseEra(loc, xpath, LDMLConstants.WIDE);
            } else if (name.equals(LDMLConstants.ERANARROW)) {
                res = parseEra(loc, xpath, LDMLConstants.NARROW);
            } else {
                System.err.println("Encountered unknown <" + xpath
                        + "> subelement: " + name);
                System.exit(-1);
            }
            if (res != null) {
                if (current == null) {
                    table.first = res;
                    current = findLast(res);
                } else {
                    current.next = res;
                    current = findLast(res);
                }
                res = null;
            }
        }
        if (table.first != null) {
            return table;
        }
        return null;

    }

    private ICUResourceWriter.Resource parseEra(InputLocale loc, String xpath,
            String name) {
        ICUResourceWriter.ResourceArray array = new ICUResourceWriter.ResourceArray();
        ICUResourceWriter.Resource current = null;
        array.name = name;
        String resName = LDMLConstants.ERA;

        // if the whole node is marked draft then
        // dont write anything
        if (loc.isPathNotConvertible(xpath)) {
            return null;
        }

        // if the whole thing is an alias
        if ((current = getAliasResource(loc, xpath)) != null) {
            current.name = array.name;
            return current;
        } else {
            // System.err.println("NO alias in: " + xpath);
        }

        Map<String, String> map = getElementsMap(loc, resName, xpath, false);

        if (map.size() == 0) {
            // System.err.println(" -- no vals, exitting " + xpath);
            return null; // no items here.
        }

        Map<String, String> defMap = null;
        if (loc.resolved() != null) {
            defMap = getElementsMap(loc, resName, xpath, true);
        }
        Set<String> allThings = new TreeSet<String>();
        allThings.addAll(map.keySet());
        if (defMap != null) {
            allThings.addAll(defMap.keySet());
        }

        // / "special" hack for japanese narrow. If so, we'll have an alternate
        // set on standby.
        Map<String, String> nonNarrow = null;
        boolean isJapaneseNarrow = xpath
                .equals("//ldml/dates/calendars/calendar[@type=\"japanese\"]/eras/eraNarrow");
        if (isJapaneseNarrow) {
            nonNarrow = getElementsMap(loc, resName, xpath.replaceAll(
                    "eraNarrow", "eraAbbr"), true); // will NOT fallback from
                                                    // specials.
            // System.err.println("xpath: " + xpath + ", resName: " + resName +
            // " - needs japanese hack.");
            allThings.addAll(nonNarrow.keySet());
        }
        if (map.size() > 0) {
            for (int i = 0; i < allThings.size(); i++) {
                String key = Integer.toString(i);
                ICUResourceWriter.ResourceString res = new ICUResourceWriter.ResourceString();
                res.val = (String) map.get(key);
                if (res.val == null && defMap != null) {
                    res.val = defMap.get(key);
                    if (verboseFallbackComments && res.val != null) {
                        res.smallComment = " fallback";
                    }
                }
                if (res.val == null && nonNarrow != null) {
                    res.val = nonNarrow.get(key);
                    if (res.val != null) {
                        res.smallComment = "(abbr.)";
                    }
                }
                if (res.val == null) {
                    printError("", "Could not get full " + resName
                            + " array at " + xpath + " -  Missing " + key
                            + ".  Only found " + map.size() + " items ( "
                            + allThings.size()
                            + " including inherited). Fatal error exiting.");
                    // NB: see workaround for Japanese-narrow above.
                    throw new InternalError("data problem");
                }
                // array of unnamed strings
                if (res.val != null) {
                    if (current == null) {
                        current = array.first = res;
                    } else {
                        current.next = res;
                        current = current.next;
                    }
                }
            }
        }

        if (array.first != null) {
            return array;
        }
        return null;
    }

    private boolean isNodeNotConvertible(Node node, StringBuffer xpath) {
        return isNodeNotConvertible(node, xpath, false, false);
    }

    private boolean isNodeNotConvertible(Node node, StringBuffer xpath,
            boolean isCollation, boolean isNodeFromParent) {
        // only deal with leaf nodes!
        // Here we assume that the CLDR files are normalized
        // and that the draft attributes are only on leaf nodes
        if (LDMLUtilities.areChildrenElementNodes(node) && !isCollation) {
            return false;
        }
        if (isNodeFromParent) {
            return false;
        }
        return !xpathList.contains(xpath.toString());

    }

    private Node getVettedNode(Node parent, String childName, StringBuffer xpath) {
        return getVettedNode(fullyResolvedDoc, parent, childName, xpath, true);
    }

    public Node getVettedNode(Document fullyResolvedDoc, Node parent,
            String childName, StringBuffer xpath, boolean ignoreDraft) {
        // NodeList list = LDMLUtilities.getNodeList(parent, childName,
        // fullyResolvedDoc, xpath.toString());
        String ctx = "./" + childName;
        NodeList list = LDMLUtilities.getNodeList(parent, ctx);
        int saveLength = xpath.length();
        Node ret = null;
        if ((list == null || list.getLength() < 0)) {
            if (fullyResolvedDoc != null) {
                int oldLength = xpath.length();
                xpath.append("/");
                xpath.append(childName);
                // try from fully resolved
                list = LDMLUtilities.getNodeList(fullyResolvedDoc, xpath
                        .toString());
                // we can't depend on isNodeNotConvertible to return the correct
                // data since xpathList will not contain xpaths of nodes from
                // parent so we just return the first one we encounter.
                // This has a side effect of ignoring the config specifiation!
                if (list != null && list.getLength() > 0) {
                    ret = list.item(0);
                }
                xpath.setLength(oldLength);
            }
        } else {
            /*
             * getVettedNode adds the node name of final node to xpath. chop off
             * the final node name from xpath.
             */
            int end = childName.lastIndexOf('/');
            if (end > 0) {
                xpath.append('/');
                xpath.append(childName.substring(0, end));
            }
            ret = getVettedNode(list, xpath, ignoreDraft);
        }
        xpath.setLength(saveLength);
        return ret;
    }

    private Node getVettedNode(NodeList list, StringBuffer xpath,
            boolean ignoreDraft) {
        Node node = null;
        int oldLength = xpath.length();
        for (int i = 0; i < list.getLength(); i++) {
            node = list.item(i);
            getXPath(node, xpath);
            if (isNodeNotConvertible(node, xpath)) {
                xpath.setLength(oldLength);
                node = null;
                continue;
            }
            break;
        }
        xpath.setLength(oldLength);
        return node;
    }

    private ICUResourceWriter.Resource parseAmPm(InputLocale loc, String xpath) {

        String AMPM[] = { LDMLConstants.AM, LDMLConstants.PM };
        ICUResourceWriter.ResourceString strs[] = new ICUResourceWriter.ResourceString[AMPM.length];
        String paths[] = new String[AMPM.length];
        ICUResourceWriter.Resource first = null;
        int validCount = 0;
        for (int i = 0; i < AMPM.length; i++) {
            strs[i] = new ICUResourceWriter.ResourceString();
            first = ICUResourceWriter.ResourceString.addAfter(first, strs[i]);
            paths[i] = xpath + "/" + AMPM[i];
            if (!loc.isPathNotConvertible(paths[i])) {
                strs[i].val = loc.file.getStringValue(paths[i]);
                if (strs[i].val != null) {
                    validCount++;
                }
            }
        }

        if (validCount == 0) {
            // 0 ampm strings. bailing.
            return null;
        }
        if (validCount < AMPM.length) {
            for (int i = 0; i < AMPM.length; i++) {
                if (strs[i].val == null
                        && !loc.isPathNotConvertible(loc.resolved(), paths[i])) {
                    strs[i].val = loc.resolved().getStringValue(paths[i]);
                    if (strs[i].val != null) {
                        if (verboseFallbackComments) {
                            strs[i].smallComment = " fallback";
                        }
                        validCount++;
                    }
                }
            }
        }

        if (validCount != AMPM.length) {
            throw new InternalError("On " + xpath + " (AMPM) - need "
                    + AMPM.length + " strings but only have " + validCount
                    + " after inheritance.");
        }

        // ok, set up the res
        ICUResourceWriter.ResourceArray arr = new ICUResourceWriter.ResourceArray();
        arr.name = AM_PM_MARKERS;
        arr.first = first;
        return arr;
    }

    /**
     * parse all DTF
     * 
     * @param loc
     * @param xpath -
     *            the base xpath ending at calendar[@type=...]
     * @return
     */

    // TODO figure out what to do for alias, draft and alt elements
    private static final String[] dtf_paths = new String[] {
            "timeFormats/timeFormatLength[@type=\"full\"]/timeFormat[@type=\"standard\"]/pattern[@type=\"standard\"]",
            "timeFormats/timeFormatLength[@type=\"long\"]/timeFormat[@type=\"standard\"]/pattern[@type=\"standard\"]",
            "timeFormats/timeFormatLength[@type=\"medium\"]/timeFormat[@type=\"standard\"]/pattern[@type=\"standard\"]",
            "timeFormats/timeFormatLength[@type=\"short\"]/timeFormat[@type=\"standard\"]/pattern[@type=\"standard\"]",
            "dateFormats/dateFormatLength[@type=\"full\"]/dateFormat[@type=\"standard\"]/pattern[@type=\"standard\"]",
            "dateFormats/dateFormatLength[@type=\"long\"]/dateFormat[@type=\"standard\"]/pattern[@type=\"standard\"]",
            "dateFormats/dateFormatLength[@type=\"medium\"]/dateFormat[@type=\"standard\"]/pattern[@type=\"standard\"]",
            "dateFormats/dateFormatLength[@type=\"short\"]/dateFormat[@type=\"standard\"]/pattern[@type=\"standard\"]",
            "dateTimeFormats/dateTimeFormatLength/dateTimeFormat[@type=\"standard\"]/pattern[@type=\"standard\"]", };

    private ICUResourceWriter.Resource parseDTF(InputLocale loc, String xpath) {
        // TODO change the ICU format to reflect LDML format
        /*
         * The prefered ICU format would be timeFormats{ default{} full{} long{}
         * medium{} short{} .... } dateFormats{ default{} full{} long{} medium{}
         * short{} ..... } dateTimeFormats{ standard{} .... }
         */

        ICUResourceWriter.ResourceArray arr = new ICUResourceWriter.ResourceArray();
        String[] theArray = dtf_paths;
        arr.name = DTP;
        ICUResourceWriter.Resource current = null;
        ICUResourceWriter.ResourceString strs[] = new ICUResourceWriter.ResourceString[theArray.length];
        GroupStatus status = parseGroupWithFallback(loc, xpath, theArray, strs);
        if (GroupStatus.EMPTY == status) {
            // System.err.println("failur: "+ xpath + " - " + theArray[0]);
            return null; // NO items were found
        } else {
            // System.err.println("DTF: " + xpath + " - " + status);
        }
        for (ICUResourceWriter.ResourceString str : strs) {
            if (current == null) {
                current = arr.first = str;
            } else {
                current.next = str;
                current = current.next;
            }
        }

        if (arr.first != null) {
            return arr;
        }
        return null;
    }

//    private ICUResourceWriter.Resource parseFlexibleFormats(InputLocale loc,
//            String xpath) {
//        xpath = xpath + "/" + LDMLConstants.AVAIL_FMTS;
//        
//        
//
//        
//         throw new InternalError("not imp "+xpath);
//         ICUResourceWriter.Resource current = null, first = null;
//        
//         int savedLength = xpath.length();
//         getXPath(root, xpath);
//         int oldLength = xpath.length();
//        
//         for (Node node = root.getFirstChild(); node != null; node = node
//         .getNextSibling()) {
//         if (node.getNodeType() != Node.ELEMENT_NODE) {
//         continue;
//         }
//         String name = node.getNodeName();
//         ICUResourceWriter.Resource res = null;
//        
//         if (name.equals(LDMLConstants.ALIAS)) {
//         // TODO: Do nothing for now, fix when ICU is updated to reflect
//         // CLDR structure
//         } else if (name.equals(LDMLConstants.AVAIL_FMTS)
//         || name.equals(LDMLConstants.APPEND_ITEMS)) {
//         res = parseItems(loc, xpath);
//         } else if (name.equals(LDMLConstants.DTFL)) {
//         // TODO: Do nothing for now, fix when ICU is updated to reflect
//         // CLDR structure
//         } else {
//         System.err.println("Encountered unknown <" + root.getNodeName()
//         + "> subelement: " + name);
//         System.exit(-1);
//         }
//         if (res != null) {
//         if (current == null) {
//         first = res;
//         current = findLast(res);
//         } else {
//         current.next = res;
//         current = findLast(res);
//         }
//         res = null;
//         }
//         xpath.delete(oldLength, xpath.length());
//         }
//         xpath.delete(savedLength, xpath.length());
//         if (first != null) {
//         return first;
//         }
//         return null;
//    }
//
//    private ICUResourceWriter.Resource parseItems(InputLocale loc, String xpath) {
//        throw new InternalError("not imp " + xpath);
//         ICUResourceWriter.Resource current = null;
//         int savedLength = xpath.length();
//         getXPath(root, xpath);
//         int oldLength = xpath.length();
//         ICUResourceWriter.ResourceTable table = new
//         ICUResourceWriter.ResourceTable();
//         table.name = root.getNodeName();
//        
//         // if the whole node is marked draft then
//         // dont write anything
//         if (isNodeNotConvertible(root, xpath)) {
//         xpath.setLength(savedLength);
//         return null;
//         }
//        
//         for (Node node = root.getFirstChild(); node != null; node = node
//         .getNextSibling()) {
//         if (node.getNodeType() != Node.ELEMENT_NODE) {
//         continue;
//         }
//         String name = node.getNodeName();
//         ICUResourceWriter.Resource res = null;
//        
//         if (name.equals(LDMLConstants.ALIAS)) {
//         res = parseAliasResource(node, xpath);
//         res.name = name;
//         return res;
//         } else if (name.equals(LDMLConstants.DATE_FMT_ITEM)) {
//         getXPath(node, xpath);
//         if (isNodeNotConvertible(node, xpath)) {
//         xpath.setLength(oldLength);
//         continue;
//         }
//         ICUResourceWriter.ResourceString str = new
//         ICUResourceWriter.ResourceString();
//         str.name = LDMLUtilities.getAttributeValue(node,
//         LDMLConstants.ID);
//         str.val = LDMLUtilities.getNodeValue(node);
//         res = str;
//         } else if (name.equals(LDMLConstants.APPEND_ITEM)) {
//         getXPath(node, xpath);
//         if (isNodeNotConvertible(node, xpath)) {
//         xpath.setLength(oldLength);
//         continue;
//         }
//         ICUResourceWriter.ResourceString str = new
//         ICUResourceWriter.ResourceString();
//         str.name = LDMLUtilities.getAttributeValue(node,
//         LDMLConstants.REQUEST);
//         str.val = LDMLUtilities.getNodeValue(node);
//         res = str;
//         } else if (name.equals(LDMLConstants.DTFL)) {
//         // Already parsed this element in parseDTF
//         continue;
//         } else {
//         System.err.println("Encountered unknown <" + root.getNodeName()
//         + "> subelement: " + name);
//         System.exit(-1);
//         }
//         if (res != null) {
//         if (current == null) {
//         table.first = res;
//         current = findLast(res);
//         } else {
//         current.next = res;
//         current = res;
//         }
//         res = null;
//         }
//         xpath.delete(oldLength, xpath.length());
//         }
//         xpath.delete(savedLength, xpath.length());
//         if (table.first != null) {
//         return table;
//         }
//         return null;
//    }

    private ICUResourceWriter.Resource parseNumbers(InputLocale loc,
            String xpath) {
        ICUResourceWriter.Resource current = null, first = null;
        boolean writtenFormats = false;

        String origXpath = xpath;
        String names[] = { LDMLConstants.ALIAS, LDMLConstants.DEFAULT,
                LDMLConstants.SYMBOLS, LDMLConstants.DECIMAL_FORMATS,
                LDMLConstants.PERCENT_FORMATS,
                LDMLConstants.SCIENTIFIC_FORMATS,
                LDMLConstants.CURRENCY_FORMATS, LDMLConstants.CURRENCIES };
        for (String name : names) {
            xpath = origXpath + "/" + name;
            if (loc.isPathNotConvertible(xpath))
                continue;
            ICUResourceWriter.Resource res = null;

            if (name.equals(LDMLConstants.ALIAS)) {
                if (!loc.file.isHere(xpath))
                    continue;
                res = parseAliasResource(loc, xpath);
                res.name = name;
                return res;
            } else if (name.equals(LDMLConstants.DEFAULT)) {
                if (!loc.file.isHere(xpath))
                    continue;
                res = getDefaultResource(loc, xpath, name);
            } else if (name.equals(LDMLConstants.SYMBOLS)) {
                res = parseSymbols(loc, xpath);
            } else if (name.equals(LDMLConstants.DECIMAL_FORMATS)
                    || name.equals(LDMLConstants.PERCENT_FORMATS)
                    || name.equals(LDMLConstants.SCIENTIFIC_FORMATS)
                    || name.equals(LDMLConstants.CURRENCY_FORMATS)) {
                if (writtenFormats == false) {
                    res = parseNumberFormats(loc, xpath);
                    writtenFormats = true;
                }
            } else if (name.equals(LDMLConstants.CURRENCIES)) {
                res = parseCurrencies(loc, xpath);
            } else {
                System.err.println("Encountered unknown <" + xpath
                        + "> subelement: " + name);
                System.exit(-1);
            }
            if (res != null) {
                if (current == null) {
                    first = res;
                    current = findLast(res);
                } else {
                    current.next = res;
                    current = findLast(res);
                }
                res = null;
            }
        }
        if (first != null) {
            return first;
        }
        return null;
    }

    enum GroupStatus {
        EMPTY, SPARSE, FALLBACK, COMPLETE
    };

    /**
     * Parse some things, with fallback behavior
     * 
     * @param loc
     * @param xpathBase
     *            the base xpath
     * @param xpaths
     *            the xpath (with slash before) to append to the base
     * @param res
     *            output array to hold res strings. must be same size as xpaths
     * @return Will return the lowest possible value that applies to any item,
     *         or GROUP_EMPTY if no items could be filled in
     */
    private GroupStatus parseGroupWithFallback(InputLocale loc,
            String xpathBase, String xpaths[],
            ICUResourceWriter.ResourceString res[]) {
        String[] values = new String[xpaths.length];

        boolean someNonDraft = false;
        boolean anyExtant = false;
        for (int i = 0; i < xpaths.length; i++) {
            String aPath = xpathBase + "/" + xpaths[i];
            if (loc.file.isHere(aPath)) {
                anyExtant = true;
                if (!loc.isPathNotConvertible(aPath)) {
                    values[i] = loc.file.getStringValue(aPath);
                    if (values[i] != null) {
                        someNonDraft = true;
                    }
                }
            }
        }
        GroupStatus status = GroupStatus.EMPTY;
        if (!anyExtant && !someNonDraft) {
            // System.err.println("No "+xpathBase+" for " + loc.locale);
            return status;
        }
        if (someNonDraft == true) {
            status = GroupStatus.COMPLETE;
            for (int i = 0; i < xpaths.length; i++) {
                res[i] = new ICUResourceWriter.ResourceString();
                String temp = values[i];
                if (temp == null) {
                    String aPath = xpathBase + "/" + xpaths[i];
                    temp = loc.resolved().getStringValue(aPath);
                    if (temp != null) {
                        CLDRFile.Status fileStatus = new CLDRFile.Status();
                        String foundIn = loc.resolved().getSourceLocaleID(
                                aPath, fileStatus);
                        if (verboseFallbackComments) {
                            res[i].smallComment = " From " + foundIn;
                        }
                        if (status != GroupStatus.SPARSE) {
                            status = GroupStatus.FALLBACK;
                        }
                        // System.err.println("Fallback from " + foundIn +" in "
                        // + loc.locale +" / " + aPath);
                    } else {
                        status = GroupStatus.SPARSE;
                        // throw new InternalError(loc.locale+" Can't complete
                        // array for "+xpathBase+" at " + aPath);
                    }
                }
                res[i].val = temp;
            }
            return status;
        }
        System.err.println(xpathBase + " - no non draft?");
        return GroupStatus.EMPTY;
    }

    private static String[] sym_paths = new String[] { LDMLConstants.DECIMAL,
            LDMLConstants.GROUP, LDMLConstants.LIST,
            LDMLConstants.PERCENT_SIGN, LDMLConstants.NATIVE_ZERO_SIGN,
            LDMLConstants.PATTERN_DIGIT, LDMLConstants.MINUS_SIGN,
            LDMLConstants.EXPONENTIAL, LDMLConstants.PER_MILLE,
            LDMLConstants.INFINITY, LDMLConstants.NAN, LDMLConstants.PLUS_SIGN };

    private ICUResourceWriter.Resource parseSymbols(InputLocale loc,
            String xpath) {

        ICUResourceWriter.ResourceArray arr = new ICUResourceWriter.ResourceArray();
        arr.name = NUMBER_ELEMENTS;
        ICUResourceWriter.Resource current = null;
        ICUResourceWriter.ResourceString strs[] = new ICUResourceWriter.ResourceString[sym_paths.length];
        GroupStatus status = parseGroupWithFallback(loc, xpath, sym_paths, strs);
        if (GroupStatus.EMPTY == status || GroupStatus.SPARSE == status) {
            return null;
        }
        for (ICUResourceWriter.ResourceString str : strs) {
            if (current == null) {
                current = arr.first = str;
            } else {
                current.next = str;
                current = current.next;
            }
        }

        if (arr.first != null) {
            return arr;
        }
        return null;

    }

    // TODO figure out what to do for alias, draft and alt elements
    private static final String[] num_paths = new String[] {
            "decimalFormats/decimalFormatLength/decimalFormat[@type=\"standard\"]/pattern[@type=\"standard\"]",
            "currencyFormats/currencyFormatLength/currencyFormat[@type=\"standard\"]/pattern[@type=\"standard\"]",
            "percentFormats/percentFormatLength/percentFormat[@type=\"standard\"]/pattern[@type=\"standard\"]",
            "scientificFormats/scientificFormatLength/scientificFormat[@type=\"standard\"]/pattern[@type=\"standard\"]" };

    private ICUResourceWriter.Resource parseNumberFormats(InputLocale loc,
            String xpath) {

        ICUResourceWriter.ResourceArray arr = new ICUResourceWriter.ResourceArray();
        String[] theArray = num_paths;
        arr.name = NUMBER_PATTERNS;
        ICUResourceWriter.Resource current = null;
        ICUResourceWriter.ResourceString strs[] = new ICUResourceWriter.ResourceString[theArray.length];
        GroupStatus status = parseGroupWithFallback(loc, "//ldml/numbers",
                theArray, strs);
        if (GroupStatus.EMPTY == status) {
            return null; // NO items were found
        }
        for (ICUResourceWriter.ResourceString str : strs) {
            if (current == null) {
                current = arr.first = str;
            } else {
                current.next = str;
                current = current.next;
            }
        }

        if (arr.first != null) {
            return arr;
        }
        return null;
    }

    private ICUResourceWriter.Resource parseCurrencies(InputLocale loc,
            String xpath) {
        ICUResourceWriter.ResourceTable table = new ICUResourceWriter.ResourceTable();
        ICUResourceWriter.Resource current = null;

        // if the whole node is marked draft then
        // dont write anything
        String origXpath = xpath;

        Set<String> currs = new HashSet<String>();
        // collect a list of all currencies, ensure no dups.
        for (Iterator<String> iter = loc.file.iterator(xpath); iter.hasNext();) {
            xpath = iter.next();
            String name = loc.getXpathName(xpath);
            // System.err.println("$: " + xpath + " // " + name);
            if (loc.isPathNotConvertible(xpath))
                continue;
            ICUResourceWriter.Resource res = null;

            if (name.equals(LDMLConstants.ALIAS)) {
                res = parseAliasResource(loc, xpath);
                res.name = name;
                return res;
            } else if (name.equals(LDMLConstants.DEFAULT)) {
                res = getDefaultResource(loc, xpath, name);
            } else /* if (name.equals(LDMLConstants.CURRENCY)) */{
                String type = loc.findAttributeValue(xpath, LDMLConstants.TYPE);
                if ((type == null) || (type.equals("standard"))) {
                    continue;
                }
                if (currs.contains(type)) {
                    // System.err.println("$$$ dup "+type);
                    continue; // dup
                } else {
                    res = parseCurrency(loc, origXpath + "/currency[@type=\""
                            + type + "\"]", type);
                    currs.add(type);
                }
            }
            if (res != null) {
                if (current == null) {
                    table.first = res;
                    current = findLast(res);
                } else {
                    current.next = res;
                    current = findLast(res);
                }
                res = null;
            }
        }
        if (table.first != null) {
            table.name = (String) keyNameMap.get(loc.getXpathName(origXpath)); // lookup
                                                                                // only
                                                                                // if
                                                                                // needed
            return table;
        }
        return null;
    }

    static final String curr_syms[] = { LDMLConstants.SYMBOL, // 0
            LDMLConstants.DISPLAY_NAME, // 1
            LDMLConstants.PATTERN, // 2
            LDMLConstants.DECIMAL, // 3
            LDMLConstants.GROUP };// 4

    private ICUResourceWriter.Resource parseCurrency(InputLocale loc,
            String xpath, String type) {

        /*
         * Node alias = LDMLUtilities.getNode(root, LDMLConstants.ALIAS,
         * fullyResolvedDoc, xpath.toString()); if (alias != null) {
         * ICUResourceWriter.Resource res = parseAliasResource(alias, xpath);
         * res.name = LDMLUtilities .getAttributeValue(root,
         * LDMLConstants.TYPE); xpath.delete(savedLength, xpath.length());
         * return res; }
         * 
         */
        ICUResourceWriter.ResourceArray arr = new ICUResourceWriter.ResourceArray();
        arr.name = type;
        ICUResourceWriter.Resource current = null;
        ICUResourceWriter.ResourceString strs[] = new ICUResourceWriter.ResourceString[curr_syms.length];
        GroupStatus status = parseGroupWithFallback(loc, xpath, curr_syms, strs);
        if (status == GroupStatus.EMPTY) {
            String full = loc.resolved().getFullXPath(xpath);
            String val = loc.resolved().getStringValue(xpath);
            System.err.println("totally empty - Failed to parse: " + type
                    + " at xpath " + xpath + " - full value " + full
                    + " value " + val);
            return null;
        }

        // aliases - for sanity
        ICUResourceWriter.ResourceString symbol = strs[0];
        ICUResourceWriter.ResourceString displayName = strs[1];
        ICUResourceWriter.ResourceString pattern = strs[2];
        ICUResourceWriter.ResourceString decimal = strs[3];
        ICUResourceWriter.ResourceString group = strs[4];

        // 0 - symb
        if (symbol.val != null) {
            String choice = loc.getBasicAttributeValue(xpath + "/"
                    + curr_syms[0], LDMLConstants.CHOICE);
            if (choice == null) {
                String fullPathInh = loc.resolved().getFullXPath(
                        xpath + "/" + curr_syms[0]);
                if (fullPathInh != null) {
                    choice = loc.getAttributeValue(fullPathInh,
                            LDMLConstants.CHOICE);
                }
            }
            if (choice != null && choice.equals("true")) {
                symbol.val = "=" + symbol.val.replace('\u2264', '#');
                if (true || verboseFallbackComments) {
                    if (symbol.smallComment != null)
                        symbol.smallComment = symbol.smallComment
                                + " - (choice)";
                    else
                        symbol.smallComment = "(choice)";
                }
            }
        } else {
            symbol.val = type;
            if (true || verboseFallbackComments) {
                symbol.smallComment = "===";
            }
        }

        // 1 - disp
        if (displayName.val == null) {
            displayName.val = type;
            if (true || verboseFallbackComments) {
                symbol.smallComment = "===";
            }
        }

        arr.first = symbol;// arr.first = symbol;
        symbol.next = displayName;// symbol.next = displayName;

        if (pattern.val != null || decimal.val != null || group.val != null) {
            boolean isPatternDup = false;
            boolean isDecimalDup = false;
            boolean isGroupDup = false;
            if (pattern.val == null) {
                pattern.val = loc
                        .getResolvedString("//ldml/numbers/currencyFormats/currencyFormatLength/currencyFormat[@type=\"standard\"]/pattern[@type=\"standard\"]");
                isPatternDup = true;
                if (pattern.val == null) {
                    throw new RuntimeException(
                            "Could not get pattern currency resource!!");
                }
            }
            if (decimal.val == null) {
                decimal.val = loc
                        .getResolvedString("//ldml/numbers/symbols/decimal");
                isDecimalDup = true;
                if (decimal.val == null) {
                    throw new RuntimeException(
                            "Could not get decimal currency resource!!");
                }
            }
            if (group.val == null) {
                group.val = loc
                        .getResolvedString("//ldml/numbers/symbols/group");
                isGroupDup = true;
                if (group.val == null) {
                    throw new RuntimeException(
                            "Could not get group currency resource!!");
                }
            }
            ICUResourceWriter.ResourceArray elementsArr = new ICUResourceWriter.ResourceArray();

            pattern.comment = isPatternDup ? "Duplicated from NumberPatterns resource"
                    : null;

            decimal.comment = isDecimalDup ? "Duplicated from NumberElements resource"
                    : null;

            group.comment = isGroupDup ? "Duplicated from NumberElements resource"
                    : null;

            elementsArr.first = pattern;
            pattern.next = decimal;
            decimal.next = group;
            if (displayName.val != null) {
                displayName.next = elementsArr;
            } else {
                System.err
                        .println("WARNING: displayName and symbol not vetted/available for currency resource "
                                + arr.name + " not generating the resource");
            }

        }

        if (arr.first != null) {
            return arr;
        }
        return null;

    }

    private ICUResourceWriter.Resource parsePosix(InputLocale loc, String xpath) {
        throw new InternalError("not imp " + xpath);
        // ICUResourceWriter.Resource first = null;
        // ICUResourceWriter.Resource current = null;
        //
        // int savedLength = xpath.length();
        // getXPath(root, xpath);
        // int oldLength = xpath.length();
        //
        // // if the whole node is marked draft then
        // // dont write anything
        // if (isNodeNotConvertible(root, xpath)) {
        // xpath.setLength(savedLength);
        // return null;
        // }
        // for (Node node = root.getFirstChild(); node != null; node = node
        // .getNextSibling()) {
        // if (node.getNodeType() != Node.ELEMENT_NODE) {
        // continue;
        // }
        //
        // String name = node.getNodeName();
        // ICUResourceWriter.Resource res = null;
        // if (name.equals(LDMLConstants.MESSAGES)) {
        // res = parseMessages(node, xpath);
        // } else if (name.equals(LDMLConstants.ALIAS)) {
        // res = parseAliasResource(node, xpath);
        // } else {
        // System.err.println("Unknown element found: " + xpath + " / " + name);
        // System.exit(-1);
        // }
        // if (res != null) {
        // if (current == null) {
        // current = first = res;
        // } else {
        // current.next = res;
        // current = current.next;
        // }
        // res = null;
        // }
        // xpath.delete(oldLength, xpath.length());
        // }
        // xpath.delete(savedLength, xpath.length());
        // return first;
    }

    private ICUResourceWriter.Resource parseMessages(InputLocale loc,
            String xpath) {
        throw new InternalError("not imp " + xpath);
        // ICUResourceWriter.ResourceTable table = new
        // ICUResourceWriter.ResourceTable();
        // ICUResourceWriter.Resource current = null;
        //
        // int savedLength = xpath.length();
        // getXPath(root, xpath);
        // int oldLength = xpath.length();
        //
        // // if the whole node is marked draft then
        // // dont write anything
        // if (isNodeNotConvertible(root, xpath)) {
        // xpath.setLength(savedLength);
        // return null;
        // }
        // table.name = root.getNodeName();
        //
        // for (Node node = root.getFirstChild(); node != null; node = node
        // .getNextSibling()) {
        // if (node.getNodeType() != Node.ELEMENT_NODE) {
        // continue;
        // }
        // String name = node.getNodeName();
        // ICUResourceWriter.Resource res = null;
        // if (name.equals(LDMLConstants.YESSTR)
        // || name.equals(LDMLConstants.YESEXPR)
        // || name.equals(LDMLConstants.NOSTR)
        // || name.equals(LDMLConstants.NOEXPR)) {
        // getXPath(node, xpath);
        // if (isNodeNotConvertible(node, xpath)) {
        // xpath.setLength(oldLength);
        // continue;
        // }
        // ICUResourceWriter.ResourceString str = new
        // ICUResourceWriter.ResourceString();
        // str.name = name;
        // str.val = LDMLUtilities.getNodeValue(node);
        // res = str;
        // } else if (name.equals(LDMLConstants.ALIAS)) {
        // res = parseAliasResource(node, xpath);
        // } else {
        // System.err.println("Unknown element found: " + xpath + " / " + name);
        // System.exit(-1);
        // }
        // if (res != null) {
        // if (current == null) {
        // current = table.first = res;
        // } else {
        // current.next = res;
        // current = current.next;
        // }
        // res = null;
        // }
        // xpath.delete(oldLength, xpath.length());
        // }
        // xpath.delete(savedLength, xpath.length());
        // if (table.first != null) {
        // return table;
        // }
        // return null;
    }

    public ICUResourceWriter.Resource parseCollations(InputLocale loc,
            boolean checkIfConvertible) {

        ICUResourceWriter.ResourceTable table = new ICUResourceWriter.ResourceTable();
        ICUResourceWriter.Resource current = null;
        throw new InternalError("not imp: colls");
        // table.name = root.getNodeName();
        // int savedLength = xpath.length();
        // getXPath(root, xpath);
        // int oldLength = xpath.length();
        //
        // // if the whole collatoin node is marked draft then
        // // dont write anything
        // if (isNodeNotConvertible(root, xpath)) {
        // xpath.setLength(savedLength);
        // return null;
        // }
        //
        // current = table.first = parseValidSubLocales(root, xpath);
        // for (Node node = root.getFirstChild(); node != null; node = node
        // .getNextSibling()) {
        // if (node.getNodeType() != Node.ELEMENT_NODE) {
        // continue;
        // }
        // String name = node.getNodeName();
        // ICUResourceWriter.Resource res = null;
        // if (name.equals(LDMLConstants.ALIAS)) {
        // res = parseAliasResource(node, xpath);
        // res.name = table.name;
        // return res;
        // } else if (name.equals(LDMLConstants.DEFAULT)) {
        // res = getDefaultResource(node, xpath, name);
        // } else if (name.equals(LDMLConstants.COLLATION)) {
        // res = parseCollation(node, xpath, checkIfConvertible);
        // } else {
        // System.err.println("Encountered unknown <" + root.getNodeName()
        // + "> subelement: " + name);
        // System.exit(-1);
        // }
        // if (res != null) {
        // if (current == null) {
        // current = table.first = res;
        // } else {
        // current.next = res;
        // current = current.next;
        // }
        // res = null;
        // }
        // xpath.delete(oldLength, xpath.length());
        // }
        // xpath.delete(savedLength, xpath.length());
        // if (table.first != null) {
        // return table;
        // }
        // return null;

    }

    private ICUResourceWriter.Resource parseValidSubLocales(Node root,
            StringBuffer xpath) {
        return null;
        /*
         * String loc =
         * LDMLUtilities.getAttributeValue(root,LDMLConstants.VALID_SUBLOCALE);
         * if(loc!=null){ String[] locales = loc.split("\u0020");
         * if(locales!=null && locales.length>0){
         * ICUResourceWriter.ResourceTable table = new
         * ICUResourceWriter.ResourceTable(); ICUResourceWriter.Resource
         * current=null; table.name = LDMLConstants.VALID_SUBLOCALE; for(int
         * i=0; i<locales.length; i++){ ICUResourceWriter.ResourceString str =
         * new ICUResourceWriter.ResourceString(); str.name = locales[i];
         * str.val = ""; if(current==null){ current = table.first = str; }else{
         * current.next = str; current = str; } } return table; } } return null;
         */
    }

    private ICUResourceWriter.Resource parseCollation(Node root,
            StringBuffer xpath, boolean checkIfConvertible) {
        throw new InternalError("no");
        // ICUResourceWriter.ResourceTable table = new
        // ICUResourceWriter.ResourceTable();
        // ICUResourceWriter.Resource current = null;
        // table.name = LDMLUtilities.getAttributeValue(root,
        // LDMLConstants.TYPE);
        // int savedLength = xpath.length();
        // getXPath(root, xpath);
        // int oldLength = xpath.length();
        //
        // // if the whole collatoin node is marked draft then
        // // dont write anything
        // if (checkIfConvertible
        // && isNodeNotConvertible(root, xpath, true, false)) {
        // xpath.setLength(savedLength);
        // return null;
        // }
        // StringBuffer rules = new StringBuffer();
        // for (Node node = root.getFirstChild(); node != null; node = node
        // .getNextSibling()) {
        // if (node.getNodeType() != Node.ELEMENT_NODE) {
        // continue;
        // }
        // String name = node.getNodeName();
        // ICUResourceWriter.Resource res = null;
        // if (name.equals(LDMLConstants.ALIAS)) {
        // res = parseAliasResource(node, xpath);
        // res.name = table.name;
        // return res;
        // } else if (name.equals(LDMLConstants.RULES)) {
        // Node alias = LDMLUtilities.getNode(node, LDMLConstants.ALIAS,
        // fullyResolvedDoc, xpath.toString());
        // getXPath(node, xpath);
        // if (alias != null) {
        // res = parseAliasResource(alias, xpath);
        // } else {
        // rules.append(parseRules(node, xpath));
        // }
        // } else if (name.equals(LDMLConstants.SETTINGS)) {
        // rules.append(parseSettings(node));
        // } else if (name.equals(LDMLConstants.SUPPRESS_CONTRACTIONS)) {
        // if (DEBUG)
        // System.out.println("");
        //
        // int index = rules.length();
        // rules.append("[suppressContractions ");
        // rules.append(LDMLUtilities.getNodeValue(node));
        // rules.append(" ]");
        // if (DEBUG)
        // System.out.println(rules.substring(index));
        // } else if (name.equals(LDMLConstants.OPTIMIZE)) {
        // rules.append("[optimize ");
        // rules.append(LDMLUtilities.getNodeValue(node));
        // rules.append(" ]");
        // } else if (name.equals(LDMLConstants.BASE)) {
        // // TODO Dont know what to do here
        // // if(DEBUG)printXPathWarning(node, xpath);
        // rules.append(parseBase(node, xpath, oldLength));
        // } else {
        // System.err.println("Encountered unknown <" + root.getNodeName()
        // + "> subelement: " + name);
        // System.exit(-1);
        // }
        // if (res != null) {
        // if (current == null) {
        // current = table.first = res;
        // } else {
        // current.next = res;
        // current = current.next;
        // }
        // res = null;
        // }
        // xpath.delete(oldLength, xpath.length());
        // }
        // if (rules != null) {
        // ICUResourceWriter.ResourceString str = new
        // ICUResourceWriter.ResourceString();
        // str.name = LDMLConstants.SEQUENCE;
        // str.val = rules.toString();
        // if (current == null) {
        // current = table.first = str;
        // } else {
        // current.next = str;
        // current = current.next;
        // }
        // str = new ICUResourceWriter.ResourceString();
        // str.name = "Version";
        // str.val = ldmlVersion; // "1.0";
        // /*
        // * Not needed anymore if(specialsDoc!=null){ Node version =
        // * LDMLUtilities.getNode(specialsDoc,
        // * xpath.append("/special").toString()); if(version!=null){ str.val =
        // * LDMLUtilities.getAttributeValue(version, "icu:version"); } }
        // */
        // current.next = str;
        //
        // }
        //
        // xpath.delete(savedLength, xpath.length());
        //
        // if (table.first != null) {
        // return table;
        // }
        // return null;
    }

    private String parseBase(Node node, StringBuffer xpath, int oldLength) {
        throw new InternalError("not imp");
        // String myxp = xpath.substring(0, oldLength);
        // // backward compatibility
        // String locale = LDMLUtilities.getNodeValue(node);
        // if (locale == null) {
        // locale = LDMLUtilities
        // .getAttributeValue(node, LDMLConstants.LOCALE);
        // }
        // if (locale != null) {
        // String fn = locale + ".xml";
        // Document colDoc = LDMLUtilities.getFullyResolvedLDML(sourceDir, fn,
        // false, false, false, true);
        // Node col = LDMLUtilities.getNode(colDoc, myxp);
        // if (col != null) {
        // ICUResourceWriter.ResourceTable table =
        // (ICUResourceWriter.ResourceTable) parseCollation(
        // col, new StringBuffer(myxp), false);
        // if (table != null) {
        // ICUResourceWriter.Resource current = table.first;
        // while (current != null) {
        // if (current instanceof ICUResourceWriter.ResourceString) {
        // ICUResourceWriter.ResourceString temp =
        // (ICUResourceWriter.ResourceString) current;
        // if (temp.name.equals(LDMLConstants.SEQUENCE)) {
        // return temp.val;
        // }
        // }
        // current = current.next;
        // }
        // } else {
        // printWarning(fn, "Collation node could not be parsed for "
        // + myxp);
        // }
        // } else {
        // printWarning(fn, "Could not find col from xpath: " + myxp);
        // }
        // } else {
        // printWarning(fileName, "Could not find locale from xpath: "
        // + xpath.toString());
        // }
        // return "";
    }

    private StringBuffer parseSettings(Node node) {
        throw new InternalError("not imp");
        // String strength = LDMLUtilities.getAttributeValue(node,
        // LDMLConstants.STRENGTH);
        // StringBuffer rules = new StringBuffer();
        // if (strength != null) {
        // rules.append(" [strength ");
        // rules.append(getStrength(strength));
        // rules.append(" ]");
        // }
        // String alternate = LDMLUtilities.getAttributeValue(node,
        // LDMLConstants.ALTERNATE);
        // if (alternate != null) {
        // rules.append(" [alternate ");
        // rules.append(alternate);
        // rules.append(" ]");
        // }
        // String backwards = LDMLUtilities.getAttributeValue(node,
        // LDMLConstants.BACKWARDS);
        // if (backwards != null && backwards.equals("on")) {
        // rules.append(" [backwards 2]");
        // }
        // String normalization = LDMLUtilities.getAttributeValue(node,
        // LDMLConstants.NORMALIZATION);
        // if (normalization != null) {
        // rules.append(" [normalization ");
        // rules.append(normalization);
        // rules.append(" ]");
        // }
        // String caseLevel = LDMLUtilities.getAttributeValue(node,
        // LDMLConstants.CASE_LEVEL);
        // if (caseLevel != null) {
        // rules.append(" [caseLevel ");
        // rules.append(caseLevel);
        // rules.append(" ]");
        // }
        //
        // String caseFirst = LDMLUtilities.getAttributeValue(node,
        // LDMLConstants.CASE_FIRST);
        // if (caseFirst != null) {
        // rules.append(" [caseFirst ");
        // rules.append(caseFirst);
        // rules.append(" ]");
        // }
        // String hiraganaQ = LDMLUtilities.getAttributeValue(node,
        // LDMLConstants.HIRAGANA_Q);
        // if (hiraganaQ == null) {
        // // try the deprecated version
        // hiraganaQ = LDMLUtilities.getAttributeValue(node,
        // LDMLConstants.HIRAGANA_Q_DEP);
        // }
        // if (hiraganaQ != null) {
        // rules.append(" [hiraganaQ ");
        // rules.append(hiraganaQ);
        // rules.append(" ]");
        // }
        // String numeric = LDMLUtilities.getAttributeValue(node,
        // LDMLConstants.NUMERIC);
        // if (numeric != null) {
        // rules.append(" [numericOrdering ");
        // rules.append(numeric);
        // rules.append(" ]");
        // }
        // return rules;
    }

    private static final TreeMap collationMap = new TreeMap();
    static {
        collationMap.put("first_tertiary_ignorable",
                "[first tertiary ignorable ]");
        collationMap.put("last_tertiary_ignorable",
                "[last tertiary ignorable ]");
        collationMap.put("first_secondary_ignorable",
                "[first secondary ignorable ]");
        collationMap.put("last_secondary_ignorable",
                "[last secondary ignorable ]");
        collationMap.put("first_primary_ignorable",
                "[first primary ignorable ]");
        collationMap.put("last_primary_ignorable", "[last primary ignorable ]");
        collationMap.put("first_variable", "[first variable ]");
        collationMap.put("last_variable", "[last variable ]");
        collationMap.put("first_non_ignorable", "[first regular]");
        collationMap.put("last_non_ignorable", "[last regular ]");
        // TODO check for implicit
        // collationMap.put("??", "[first implicit]");
        // collationMap.put("??", "[last implicit]");
        collationMap.put("first_trailing", "[first trailing ]");
        collationMap.put("last_trailing", "[last trailing ]");
    }

    private StringBuffer parseRules(Node root, StringBuffer xpath) {

        StringBuffer rules = new StringBuffer();

        for (Node node = root.getFirstChild(); node != null; node = node
                .getNextSibling()) {
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            String name = node.getNodeName();
            if (name.equals(LDMLConstants.PC) || name.equals(LDMLConstants.SC)
                    || name.equals(LDMLConstants.TC)
                    || name.equals(LDMLConstants.QC)
                    || name.equals(LDMLConstants.IC)) {
                Node lastVariable = LDMLUtilities.getNode(node,
                        LDMLConstants.LAST_VARIABLE, null, null);
                if (lastVariable != null) {
                    if (DEBUG)
                        rules.append(" ");
                    rules.append(collationMap.get(lastVariable.getNodeName()));
                } else {
                    String data = getData(node, name);
                    rules.append(data);
                }
            } else if (name.equals(LDMLConstants.P)
                    || name.equals(LDMLConstants.S)
                    || name.equals(LDMLConstants.T)
                    || name.equals(LDMLConstants.Q)
                    || name.equals(LDMLConstants.I)) {
                Node lastVariable = LDMLUtilities.getNode(node,
                        LDMLConstants.LAST_VARIABLE, null, null);
                if (lastVariable != null) {
                    if (DEBUG)
                        rules.append(" ");
                    rules.append(collationMap.get(lastVariable.getNodeName()));
                } else {

                    String data = getData(node, name);
                    rules.append(data);
                }
            } else if (name.equals(LDMLConstants.X)) {
                rules.append(parseExtension(node));
            } else if (name.equals(LDMLConstants.RESET)) {
                rules.append(parseReset(node));
            } else {
                System.err.println("Encountered unknown <" + root.getNodeName()
                        + "> subelement: " + name);
                System.exit(-1);
            }
        }
        return rules;
    }

    private static final UnicodeSet needsQuoting = new UnicodeSet(
            "[[:whitespace:][:c:][:z:][[:ascii:]-[a-zA-Z0-9]]]");
    private static StringBuffer quoteOperandBuffer = new StringBuffer(); // faster

    private static final String quoteOperand(String s) {
        s = Normalizer.normalize(s, Normalizer.NFC);
        quoteOperandBuffer.setLength(0);
        boolean noQuotes = true;
        boolean inQuote = false;
        int cp;
        for (int i = 0; i < s.length(); i += UTF16.getCharCount(cp)) {
            cp = UTF16.charAt(s, i);
            if (!needsQuoting.contains(cp)) {
                if (inQuote) {
                    quoteOperandBuffer.append('\'');
                    inQuote = false;
                }
                quoteOperandBuffer.append(UTF16.valueOf(cp));
            } else {
                noQuotes = false;
                if (cp == '\'') {
                    quoteOperandBuffer.append("''");
                } else {
                    if (!inQuote) {
                        quoteOperandBuffer.append('\'');
                        inQuote = true;
                    }
                    if (cp > 0xFFFF) {
                        quoteOperandBuffer.append("\\U").append(
                                Utility.hex(cp, 8));
                    } else if (cp <= 0x20 || cp > 0x7E) {
                        quoteOperandBuffer.append("\\u")
                                .append(Utility.hex(cp));
                    } else {
                        quoteOperandBuffer.append(UTF16.valueOf(cp));
                    }
                }
            }
        }
        if (inQuote) {
            quoteOperandBuffer.append('\'');
        }
        if (noQuotes)
            return s; // faster
        return quoteOperandBuffer.toString();
    }

    private String getData(Node root, String strength) {
        StringBuffer data = new StringBuffer();
        for (Node node = root.getFirstChild(); node != null; node = node
                .getNextSibling()) {
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                String name = node.getNodeName();
                if (name.equals(LDMLConstants.CP)) {
                    String hex = LDMLUtilities.getAttributeValue(node,
                            LDMLConstants.HEX);
                    if (DEBUG)
                        data.append(" ");
                    data.append(getStrengthSymbol(strength));
                    if (DEBUG)
                        data.append(" ");
                    String cp = UTF16.valueOf(Integer.parseInt(hex, 16));
                    data.append(quoteOperand(cp));
                }
            }
            if (node.getNodeType() == Node.TEXT_NODE) {
                String val = node.getNodeValue();
                if (val != null) {
                    if (strength.equals(LDMLConstants.PC)
                            || strength.equals(LDMLConstants.SC)
                            || strength.equals(LDMLConstants.TC)
                            || strength.equals(LDMLConstants.QC)
                            || strength.equals(LDMLConstants.IC)) {
                        data.append(getExpandedRules(val, strength));
                    } else {
                        if (DEBUG)
                            data.append(" ");
                        data.append(getStrengthSymbol(strength));
                        if (DEBUG)
                            data.append(" ");
                        data.append(quoteOperand(val));
                    }
                }
            }
        }
        return data.toString();
    }

    private String getStrengthSymbol(String name) {
        if (name.equals(LDMLConstants.PC) || name.equals(LDMLConstants.P)) {
            return "<";
        } else if (name.equals(LDMLConstants.SC)
                || name.equals(LDMLConstants.S)) {
            return "<<";
        } else if (name.equals(LDMLConstants.TC)
                || name.equals(LDMLConstants.T)) {
            return "<<<";
        } else if (name.equals(LDMLConstants.QC)
                || name.equals(LDMLConstants.Q)) {
            return "<<<<";
        } else if (name.equals(LDMLConstants.IC)
                || name.equals(LDMLConstants.I)) {
            return "=";
        } else {
            System.err.println("Encountered strength: " + name);
            System.exit(-1);
        }
        return null;
    }

    private String getStrength(String name) {
        if (name.equals(LDMLConstants.PRIMARY)) {
            return "1";
        } else if (name.equals(LDMLConstants.SECONDARY)) {
            return "2";
        } else if (name.equals(LDMLConstants.TERTIARY)) {
            return "3";
        } else if (name.equals(LDMLConstants.QUARTERNARY)) {
            return "4";
        } else if (name.equals(LDMLConstants.IDENTICAL)) {
            return "5";

        } else {
            System.err.println("Encountered strength: " + name);
            System.exit(-1);
        }
        return null;
    }

    private StringBuffer parseReset(Node root) {
        /*
         * variableTop at & x= [last variable] <reset>x</reset><i><last_variable/></i>
         * after & x < [last variable] <reset>x</reset><p><last_variable/></p>
         * before & [before 1] x< [last variable] <reset before="primary">x</reset><p><last_variable/></p>
         */
        /*
         * & [first tertiary ignorable] << \u00e1 <reset><first_tertiary_ignorable/></reset><s>?</s>
         */
        StringBuffer ret = new StringBuffer();

        if (DEBUG)
            ret.append(" ");
        ret.append("&");
        if (DEBUG)
            ret.append(" ");

        String val = LDMLUtilities
                .getAttributeValue(root, LDMLConstants.BEFORE);
        if (val != null) {
            if (DEBUG)
                ret.append(" ");
            ret.append("[before ");
            ret.append(getStrength(val));
            ret.append("]");
        }
        for (Node node = root.getFirstChild(); node != null; node = node
                .getNextSibling()) {
            short type = node.getNodeType();
            if (type == Node.ELEMENT_NODE) {

                String key = node.getNodeName();
                if (DEBUG)
                    ret.append(" ");
                ret.append(collationMap.get(key));
            }
            if (type == Node.TEXT_NODE) {
                ret.append(quoteOperand(node.getNodeValue()));
            }
        }
        return ret;
    }

    private StringBuffer getExpandedRules(String data, String name) {
        UCharacterIterator iter = UCharacterIterator.getInstance(data);
        StringBuffer ret = new StringBuffer();
        String strengthSymbol = getStrengthSymbol(name);
        int ch;
        while ((ch = iter.nextCodePoint()) != UCharacterIterator.DONE) {
            if (DEBUG)
                ret.append(" ");
            ret.append(strengthSymbol);
            if (DEBUG)
                ret.append(" ");
            ret.append(quoteOperand(UTF16.valueOf(ch)));
        }
        return ret;
    }

    private StringBuffer parseExtension(Node root) {
        /*
         * strength context string extension <strength> <context> | <string> /
         * <extension> < a | [last variable] <x><context>a</context><p><last_variable/></p></x> <
         * [last variable] / a <x><p><last_variable/></p><extend>a</extend></x> <<
         * k / h <x><s>k</s> <extend>h</extend></x> << d | a <x><context>d</context><s>a</s></x> =
         * e | a <x><context>e</context><i>a</i></x> = f | a <x><context>f</context><i>a</i></x>
         */
        StringBuffer rules = new StringBuffer();
        Node contextNode = null;
        Node extendNode = null;
        Node strengthNode = null;

        String strength = null;
        String string = null;
        String context = null;
        String extend = null;

        for (Node node = root.getFirstChild(); node != null; node = node
                .getNextSibling()) {
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            String name = node.getNodeName();
            if (name.equals(LDMLConstants.CONTEXT)) {
                contextNode = node;
            } else if (name.equals(LDMLConstants.P)
                    || name.equals(LDMLConstants.S)
                    || name.equals(LDMLConstants.T)
                    || name.equals(LDMLConstants.I)) {
                strengthNode = node;
            } else if (name.equals(LDMLConstants.EXTEND)) {
                extendNode = node;
            } else {
                System.err.println("Encountered unknown <" + root.getNodeName()
                        + "> subelement: " + name);
                System.exit(-1);
            }
        }
        if (contextNode != null) {
            context = LDMLUtilities.getNodeValue(contextNode);
        }
        if (strengthNode != null) {
            Node lastVariable = LDMLUtilities.getNode(strengthNode,
                    LDMLConstants.LAST_VARIABLE, null, null);
            if (lastVariable != null) {
                string = (String) collationMap.get(lastVariable.getNodeName());
            } else {
                strength = getStrengthSymbol(strengthNode.getNodeName());
                string = LDMLUtilities.getNodeValue(strengthNode);
            }
        }
        if (extendNode != null) {
            extend = LDMLUtilities.getNodeValue(extendNode);
        }
        if (DEBUG)
            rules.append(" ");
        rules.append(strength);
        if (DEBUG)
            rules.append(" ");
        if (context != null) {
            rules.append(quoteOperand(context));
            if (DEBUG)
                rules.append(" ");
            rules.append("|");
            if (DEBUG)
                rules.append(" ");
        }
        rules.append(string);

        if (extend != null) {
            if (DEBUG)
                rules.append(" ");
            rules.append("/");
            if (DEBUG)
                rules.append(" ");
            rules.append(quoteOperand(extend));
        }
        return rules;
    }

    private static final String ICU_BRKITR_DATA = "icu:breakIteratorData";
    private static final String ICU_DICTIONARIES = "icu:dictionaries";
    private static final String ICU_BOUNDARIES = "icu:boundaries";
    private static final String ICU_GRAPHEME = "icu:grapheme";
    private static final String ICU_WORD = "icu:word";
    private static final String ICU_SENTENCE = "icu:sentence";
    private static final String ICU_LINE = "icu:line";
    private static final String ICU_TITLE = "icu:title";
    private static final String ICU_DICTIONARY = "icu:dictionary";
    // private static final String ICU_CLASS = "icu:class";
    // private static final String ICU_IMPORT = "icu:import";
    // private static final String ICU_APPEND = "icu:append";
    private static final String ICU_DEPENDENCY = "icu:dependency";

    private ICUResourceWriter.Resource parseBoundaries(Node root,
            StringBuffer xpath) {
        ICUResourceWriter.ResourceTable table = new ICUResourceWriter.ResourceTable();
        int savedLength = xpath.length();
        getXPath(root, xpath);
        ICUResourceWriter.Resource current = null;
        String name = root.getNodeName();
        table.name = name.substring(name.indexOf(':') + 1, name.length());
        // we dont care if special elements are marked draft or not!

        for (Node node = root.getFirstChild(); node != null; node = node
                .getNextSibling()) {
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            name = node.getNodeName();
            ICUResourceWriter.Resource res = null;
            if (name.equals(ICU_GRAPHEME) || name.equals(ICU_WORD)
                    || name.equals(ICU_LINE) || name.equals(ICU_SENTENCE)
                    || name.equals(ICU_TITLE)) {
                ICUResourceWriter.ResourceProcess str = new ICUResourceWriter.ResourceProcess();
                str.ext = ICUResourceWriter.DEPENDENCY;
                str.name = name.substring(name.indexOf(':') + 1, name.length());
                str.val = LDMLUtilities.getAttributeValue(node, ICU_DEPENDENCY);
                if (str.val != null) {
                    res = str;
                }
            } else {
                System.err.println("Encountered unknown <" + root.getNodeName()
                        + "> subelement: " + name);
                System.exit(-1);
            }
            if (res != null) {
                if (current == null) {
                    table.first = res;
                    current = findLast(res);
                } else {
                    current.next = res;
                    current = findLast(res);
                }
                res = null;
            }
            xpath.delete(savedLength, xpath.length());
        }
        if (table.first != null) {
            return table;
        }
        return null;
    }

    private ICUResourceWriter.Resource parseDictionaries(Node root,
            StringBuffer xpath) {
        ICUResourceWriter.ResourceTable table = new ICUResourceWriter.ResourceTable();
        int savedLength = xpath.length();
        getXPath(root, xpath);
        ICUResourceWriter.Resource current = null;
        String name = root.getNodeName();
        table.name = name.substring(name.indexOf(':') + 1, name.length());
        // we dont care if special elements are marked draft or not!

        for (Node node = root.getFirstChild(); node != null; node = node
                .getNextSibling()) {
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            name = node.getNodeName();
            ICUResourceWriter.Resource res = null;
            if (name.equals(ICU_DICTIONARY)) {
                ICUResourceWriter.ResourceProcess str = new ICUResourceWriter.ResourceProcess();
                str.ext = ICUResourceWriter.DEPENDENCY;
                str.name = LDMLUtilities.getAttributeValue(node,
                        LDMLConstants.TYPE);
                str.val = LDMLUtilities.getAttributeValue(node, ICU_DEPENDENCY);
                if (str.val != null) {
                    res = str;
                }
            } else {
                System.err.println("Encountered unknown <" + root.getNodeName()
                        + "> subelement: " + name);
                System.exit(-1);
            }
            if (res != null) {
                if (current == null) {
                    table.first = res;
                    current = findLast(res);
                } else {
                    current.next = res;
                    current = findLast(res);
                }
                res = null;
            }
            xpath.delete(savedLength, xpath.length());
        }
        if (table.first != null) {
            return table;
        }
        return null;
    }

    /*
     * private ICUResourceWriter.Resource parseMetaData(Node doc){
     * ICUResourceWriter.ResourceTable table = new
     * ICUResourceWriter.ResourceTable(); table.name = "DeprecatedList"; String
     * xpath = "//supplementalData/metadata/alias"; Node alias =
     * LDMLUtilities.getNode(doc, xpath); if(alias==null){
     * printWarning("","Could not find : " + xpath + " in supplementalData"); }
     * for(Node node=alias.getFirstChild(); node!=null;
     * node=node.getNextSibling()){ if(node.getNodeType()!=Node.ELEMENT_NODE){
     * continue; } // DeprecatedList{ // in{ id{} } // iw{ he{} } // ji{ yi{} } //
     * BU{ MM{} } // DY{ BJ{} } // HV{ BF{} } // NH{ VU{} } // RH{ ZW{} } // TP{
     * TL{} } // YU{ CS{} } // }
     * 
     * String name = node.getNodeName(); ICUResourceWriter.Resource res = null;
     * if(name.equals(LDMLConstants.LANGUAGE_ALIAS)||
     * name.equals(LDMLConstants.TERRITORY_ALIAS)){ String deprecated =
     * LDMLUtilities.getAttributeValue(node, LDMLConstants.TYPE); String current =
     * LDMLUtilities.getAttributeValue(node, LDMLConstants.REPLACEMENT);
     * 
     * if(deprecated.indexOf('_')>=0){ //TODO: generate a locale that is aliased
     * to the replacement continue; } //TODO: Fix it after discussion with the
     * team }else{ } } if(table.first != null){ return table; } return null; }
     * 
     * private ICUResourceWriter.Resource parseLocaleScript(Node node){ String
     * language = ULocale.getLanguage(locName); String territory =
     * ULocale.getCountry(locName); String scriptCode =
     * ULocale.getScript(locName);
     * 
     * String xpath =
     * "//supplementalData/languageData/language[@type='"+language+"']"; Node
     * scriptNode = LDMLUtilities.getNode(node, xpath); if(scriptNode != null){
     * String scripts = LDMLUtilities.getAttributeValue(scriptNode,
     * LDMLConstants.SCRIPTS); // verify that the territory of this locale is
     * one of the territories if(territory.length()>0){ String territories =
     * LDMLUtilities.getAttributeValue(scriptNode, LDMLConstants.TERRITORIES);
     * if(territories !=null){ String[] list = territories.split("\\s"); boolean
     * exists = false; for(int i=0;i>list.length; i++){
     * if(list[i].equals(territory)){ exists = true; } } if(exists == false){
     * System.err.println("WARNING: Script info does not exist for locale:
     * "+locName); } } return null; }else if(scriptCode.length()>0){
     * ICUResourceWriter.ResourceArray arr = new
     * ICUResourceWriter.ResourceArray(); arr.name = LOCALE_SCRIPT;
     * ICUResourceWriter.ResourceString str = new
     * ICUResourceWriter.ResourceString(); str.val = scriptCode; arr.first =
     * str; return arr; }else{ if(scripts != null){ String[] list =
     * scripts.split("\\s"); if(list.length>0){ ICUResourceWriter.ResourceArray
     * arr = new ICUResourceWriter.ResourceArray(); arr.name = LOCALE_SCRIPT;
     * ICUResourceWriter.Resource current = null; for(int i=0; i<list.length;
     * i++){ ICUResourceWriter.ResourceString str = new
     * ICUResourceWriter.ResourceString(); str.val = list[i]; if(current==null){
     * arr.first = current = str; }else{ current.next = str; current =
     * current.next; } } return arr; } } } System.err.println("Could not find
     * script information for locale: "+locName); } return null; }
     */
    private static final String ICU_IS_LEAP_MONTH = "icu:isLeapMonth";
    private static final String ICU_LEAP_SYMBOL = "icu:leapSymbol";
    private static final String ICU_NON_LEAP_SYMBOL = "icu:nonLeapSymbol";

    private ICUResourceWriter.Resource parseSpecialElements(InputLocale loc,
            String xpath) {
        ICUResourceWriter.Resource current = null, first = null;

        String origXpath = xpath;
        for (Iterator<String> iter = loc.file.iterator(xpath); iter.hasNext();) {
            xpath = iter.next();
            String name = loc.getXpathName(xpath);
            // if(loc.isPathNotConvertible(xpath)) continue;
            // we dont care if special elements are marked draft or not!
            ICUResourceWriter.Resource res = null;
            if (name.equals(ICU_SCRIPT)) {
                if (!loc.beenHere(ICU_SCRIPT)) {
                    res = parseArray(loc,
                            "//ldml/characters/special/icu:scripts");
                    res.name = LOCALE_SCRIPT;
                }
            } else if (name.equals(ICU_BRKITR_DATA)) {
                throw new InternalError("not imp" + xpath);
                // res = parseBrkItrData(node, xpath);
            } else if (name.equals(ICU_IS_LEAP_MONTH)) {
                // just continue .. already handled
            } else if (name.equals(ICU_LEAP_SYMBOL)) {
                // just continue .. already handled
            } else if (name.equals(ICU_NON_LEAP_SYMBOL)) {
                // just continue .. already handled
            } else if (name.equals(LDMLConstants.SPECIAL)) {
                // just continue .. already handled
            } else {
                System.err.println("Encountered unknown <" + xpath
                        + "> special subelement: " + name);
                System.exit(-1);
            }
            if (res != null) {
                if (current == null) {
                    first = res;
                    current = res.end();
                } else {
                    current.next = res;
                    current = res.end();
                }
                res = null;
            }
        }
        return first;

    }

    private ICUResourceWriter.Resource parseBrkItrData(Node root,
            StringBuffer xpath) {
        ICUResourceWriter.Resource current = null, first = null;
        int savedLength = xpath.length();
        getXPath(root, xpath);

        // we dont care if special elements are marked draft or not!
        for (Node node = root.getFirstChild(); node != null; node = node
                .getNextSibling()) {
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            String name = node.getNodeName();
            ICUResourceWriter.Resource res = null;
            if (name.equals(ICU_BOUNDARIES)) {
                res = parseBoundaries(node, xpath);
            } else if (name.equals(ICU_DICTIONARIES)) {
                res = parseDictionaries(node, xpath);
            } else {
                System.err.println("Encountered unknown <" + root.getNodeName()
                        + "> subelement: " + name);
                System.exit(-1);
            }
            if (res != null) {
                if (current == null) {
                    first = res;
                    current = findLast(res);
                } else {
                    current.next = res;
                    current = findLast(res);
                }
                res = null;
            }
            xpath.delete(savedLength, xpath.length());
        }
        return first;

    }

    /*
     * coment this method out since the specials document is now merged with
     * fully resolved document and is not parsed separately
     * 
     * private ICUResourceWriter.Resource parseSpecialsDocucment(Node root){
     * 
     * ICUResourceWriter.Resource current = null, first = null; StringBuffer
     * xpath = new StringBuffer(); xpath.append("//ldml"); int savedLength =
     * xpath.length(); Node ldml = null; for(ldml=root.getFirstChild();
     * ldml!=null; ldml=ldml.getNextSibling()){
     * if(ldml.getNodeType()!=Node.ELEMENT_NODE){ continue; } String name =
     * ldml.getNodeName(); if(name.equals(LDMLConstants.LDML) ){
     * if(LDMLUtilities.isLocaleDraft(ldml) && writeDraft==false){
     * System.err.println("WARNING: The LDML file "+sourceDir+"/"+locName+".xml
     * is marked draft! Not producing ICU file. "); System.exit(-1); } break; } }
     * 
     * if(ldml == null) { throw new RuntimeException("ERROR: no <ldml> node
     * found in parseBundle()"); }
     * 
     * for(Node node=ldml.getFirstChild(); node!=null;
     * node=node.getNextSibling()){ if(node.getNodeType()!=Node.ELEMENT_NODE){
     * continue; } String name = node.getNodeName(); ICUResourceWriter.Resource
     * res = null; if(name.equals(LDMLConstants.IDENTITY)){ //TODO: add code to
     * check the identity of specials doc is equal to identity of // main
     * document
     * 
     * continue; }else if(name.equals(LDMLConstants.SPECIAL)){ res =
     * parseSpecialElements(node,xpath); }else
     * if(name.equals(LDMLConstants.CHARACTERS)){ res = parseCharacters(node,
     * xpath); }else if(name.equals(LDMLConstants.COLLATIONS)){ //collations are
     * resolved in parseCollation continue; }else
     * if(name.equals(LDMLConstants.DATES)){ // will be handled by parseCalendar
     * res = parseDates(node, xpath); }else if(name.indexOf("icu:")>-1||
     * name.indexOf("openOffice:")>-1){ //TODO: these are specials .. ignore for
     * now ... figure out // what to do later }else{
     * System.err.println("Encountered unknown <"+root.getNodeName()+">
     * subelement: "+name); System.exit(-1); } if(res!=null){ if(current ==
     * null){ first = res; current = findLast(res); }else{ current.next = res;
     * current = findLast(res); } res = null; }
     * xpath.delete(savedLength,xpath.length()); } return first; }
     */
    private void writeResource(ICUResourceWriter.Resource set,
            String sourceFileName) {
        try {
            String outputFileName = null;
            outputFileName = destDir + "/" + set.name + ".txt";

            // System.err.println("# Writing: " + outputFileName);

            FileOutputStream file = new FileOutputStream(outputFileName);
            BufferedOutputStream writer = new BufferedOutputStream(file);
            printInfo("Writing ICU: " + outputFileName);
            // TODO: fix me
            writeHeader(writer, sourceFileName);

            ICUResourceWriter.Resource current = set;
            while (current != null) {
                current.sort();
                current = current.next;
            }

            // Now start writing the resource;
            /* ICUResourceWriter.Resource */current = set;
            while (current != null) {
                current.write(writer, 0, false);
                current = current.next;
            }
            writer.flush();
            writer.close();
        } catch (Exception ie) {
            System.err.println(sourceFileName + ": ERROR (writing resource) :"
                    + ie.toString());
            ie.printStackTrace();
            System.exit(1);
            return; // NOTREACHED
        }
    }

    private boolean isType(Node node, String type) {
        NamedNodeMap attributes = node.getAttributes();
        Node attr = attributes.getNamedItem(LDMLConstants.TYPE);
        if (attr != null && attr.getNodeValue().equals(type)) {
            return true;
        }
        return false;
    }

    private void writeLine(OutputStream writer, String line) {
        try {
            byte[] bytes = line.getBytes(CHARSET);
            writer.write(bytes, 0, bytes.length);
        } catch (Exception e) {
            System.err.println(e);
            System.exit(1);
        }
    }

    private void writeHeader(OutputStream writer, String fileName) {
        writeBOM(writer);
        Calendar c = Calendar.getInstance();
        StringBuffer buffer = new StringBuffer();
        buffer
                .append("// ***************************************************************************"
                        + LINESEP);
        buffer.append("// *" + LINESEP);
        buffer.append("// * Copyright (C) " + c.get(Calendar.YEAR)
                + " International Business Machines" + LINESEP);
        buffer.append("// * Corporation and others.  All Rights Reserved."
                + LINESEP);
        buffer
                .append("// * Tool: com.ibm.icu.dev.tool.cldr.LDML2ICUConverter.java +"
                        + LINESEP);
        // buffer.append("// * Date & Time: " + c.get(Calendar.YEAR) + "/" +
        // (c.get(Calendar.MONTH)+1) + "/" + c.get(Calendar.DAY_OF_MONTH) + " "
        // + c.get(Calendar.HOUR_OF_DAY) + COLON + c.get(Calendar.MINUTE)+
        // LINESEP);
        // String ver = LDMLUtilities.getCVSVersion(fileName);
        // if(ver==null) {
        // ver = "";
        // } else {
        // ver = " v" + ver;
        // }
        String tempdir = fileName.replace('\\', '/');
        // System.out.println(tempdir);
        int index = tempdir.indexOf("/common");
        if (index > -1) {
            tempdir = "<path>" + tempdir.substring(index, tempdir.length());
        } else {
            index = tempdir.indexOf("/xml");
            if (index > -1) {
                tempdir = "<path>" + tempdir.substring(index, tempdir.length());
            } else {
                tempdir = "<path>/" + tempdir;
            }
        }
        buffer.append("// * Source File:" + tempdir + LINESEP);
        buffer.append("// *" + LINESEP);
        buffer
                .append("// ***************************************************************************"
                        + LINESEP);
        writeLine(writer, buffer.toString());

    }

    private void writeBOM(OutputStream buffer) {
        try {
            byte[] bytes = BOM.getBytes(CHARSET);
            buffer.write(bytes, 0, bytes.length);
        } catch (Exception e) {
            System.err.println(e);
            System.exit(1);
        }
    }

    private void writeDeprecated() {
        String myTreeName = null;
        File depF = null;
        File destD = new File(destDir);
        final File[] destFiles = destD.listFiles();
        if (writeDeprecated == true) {
            depF = new File(options[WRITE_DEPRECATED].value);
            if (!depF.isDirectory()) {
                printError("LDML2ICUConverter", options[WRITE_DEPRECATED].value
                        + " isn't a directory.");
                usage();
                return; // NOTREACHED
            }
            myTreeName = depF.getName();
        }
        boolean parseDraft = !writeDraft; // parse for draft status?
        boolean parseSubLocale = sourceDir.indexOf("collation") > -1;
        boolean parseThem = (parseDraft || parseSubLocale); // parse a bunch of
        // locales?
        TreeMap fromToMap = new TreeMap(); // ex: "ji" -> "yi"
        TreeMap fromXpathMap = new TreeMap(); // ex: "th_TH_TRADITIONAL" ->
        // "@some xpath.."
        Map fromFiles = new TreeMap(); // ex: "mt.xml" -> File . Ordinary XML
        // source files
        Map emptyFromFiles = new TreeMap(); // ex: "en_US.xml" -> File . empty
        // files generated by
        // validSubLocales
        Map generatedAliasFiles = new TreeMap(); // ex: th_TH_TRADITIONAL.xml
        // -> File Files generated
        // directly from the alias
        // list. (no XML actually
        // exists)
        Map aliasFromFiles = new TreeMap(); // ex: zh_MO.xml -> File Files which
        // actually exist in LDML and
        // contain aliases
        TreeMap validSubMap = new TreeMap(); // en -> "en_US en_GB ..."
        TreeMap maybeValidAlias = new TreeMap(); // for in -> id where id is
        // a synthetic alias

        // 1. get the list of input XML files
        FileFilter myFilter = new FileFilter() {
            public boolean accept(File f) {
                String n = f.getName();
                return (!f.isDirectory() && n.endsWith(".xml")
                        && !n.startsWith("supplementalData") // not a locale
                /* &&!n.startsWith("root") */
                && isInDest(n)); // root is implied, will be included
                // elsewhere.
            }

            public boolean isInDest(String n) {
                String name = n.substring(0, n.indexOf('.') + 1);
                for (int i = 0; i < destFiles.length; i++) {
                    String dest = destFiles[i].getName();
                    if (dest.indexOf(name) == 0) {
                        return true;
                    }
                }
                return false;
            }
        };
        // File destFiles[] =
        File inFiles[] = depF.listFiles(myFilter);

        int nrInFiles = inFiles.length;
        if (parseThem) {
            System.out.println("Parsing: " + nrInFiles
                    + " LDML locale files to check "
                    + (parseDraft ? "draft, " : "")
                    + (parseSubLocale ? "valid-sub-locales, " : ""));
        }
        for (int i = 0; i < nrInFiles; i++) {
            if (i > 0 && (i % 64 == 0)) {
                System.out.println(" " + i);
                System.out.flush();
            }
            boolean thisOK = true;
            String localeName = inFiles[i].getName();
            localeName = localeName.substring(0, localeName.indexOf('.'));
            if (parseThem) {
                // System.out.print(" " + inFiles[i].getName() + ":" );
                try {
                    Document doc2 = LDMLUtilities.parse(inFiles[i].toString(),
                            false);
                    // TODO: figure out if this is really required
                    if (parseDraft && LDMLUtilities.isLocaleDraft(doc2)) {
                        thisOK = false;
                    }
                    if (thisOK && parseSubLocale) {
                        Node collations = LDMLUtilities.getNode(doc2,
                                "//ldml/collations");
                        if (collations != null) {
                            String vsl = LDMLUtilities.getAttributeValue(
                                    collations, "validSubLocales");
                            if ((vsl != null) && (vsl.length() > 0)) {
                                validSubMap.put(localeName, vsl);
                                printInfo(localeName + " <- " + vsl);
                            }
                        }
                    }
                } catch (Throwable t) {
                    System.err.println("While parsing " + inFiles[i].toString()
                            + " - ");
                    System.err.println(t.toString());
                    t.printStackTrace(System.err);
                    System.exit(-1); // TODO: should be full 'parser error'
                    // stuff.
                }
            }
            if (!localeName.equals("root")) {
                // System.out.println("FN put " + inFiles[i].getName());
                if (thisOK) {
                    System.out.print("."); // regular file
                    fromFiles.put(inFiles[i].getName(), inFiles[i]); // add
                    // to
                    // hash
                } else {
                    if (isDraftStatusOverridable(localeName)) {
                        fromFiles.put(inFiles[i].getName(), inFiles[i]); // add
                        // to
                        // hash
                        System.out.print("o"); // override
                        // System.out.print("[o:"+localeName+"]");
                    } else {
                        System.out.print("d"); // draft
                        // System.out.print("[d:"+localeName+"]" );
                    }
                }
            }
        }
        if (parseThem == true) { // end the debugging line
            System.out.println("");
        }
        // End of parsing all XML files.

        if (emptyLocaleList != null && emptyLocaleList.size() > 0) {
            for (int i = 0; i < emptyLocaleList.size(); i++) {
                String loc = (String) emptyLocaleList.get(i);
                writeSimpleLocale(loc + ".txt", loc, null, null,
                        "empty locale file for dependency checking");
                // we do not want these files to show up in installed locales
                // list!
                generatedAliasFiles.put(loc + ".xml", new File(depF, loc
                        + ".xml"));
            }
        }
        // interpret the deprecated locales list
        if (aliasMap != null && aliasMap.size() > 0) {
            for (Iterator i = aliasMap.keySet().iterator(); i.hasNext();) {
                String from = (String) i.next();
                Alias value = (Alias) aliasMap.get(from);
                String to = value.to;
                String xpath = value.xpath;
                if (to.indexOf('@') != -1 && xpath == null) {
                    System.err
                            .println("Malformed alias - '@' but no xpath: from=\""
                                    + from + "\" to=\"" + to + "\"");
                    System.exit(-1);
                    return; // NOTREACHED
                }
                if ((from == null) || (to == null)) {
                    System.err
                            .println("Malformed alias - no 'from' or no 'to':from=\""
                                    + from + "\" to=\"" + to + "\"");
                    System.exit(-1);
                    return; // NOTREACHED
                }
                String toFileName = to;
                if (xpath != null) {
                    toFileName = to.substring(0, to.indexOf('@'));
                }
                if (fromFiles.containsKey(from + ".xml")) {
                    throw new IllegalArgumentException(
                            "Can't be both a synthetic alias locale AND XML - consider using <aliasLocale source=\""
                                    + from + "\"/> instead. ");
                }
                ULocale fromLocale = new ULocale(from);
                if (!fromFiles.containsKey(toFileName + ".xml")) {
                    maybeValidAlias.put(toFileName, from);
                    // System.err.println("WARNING: Alias from \"" + from + "\"
                    // not generated, because it would point to a nonexistent
                    // LDML file " + toFileName + ".xml" );
                    // writeSimpleLocale(from+".txt", fromLocale, new
                    // ULocale(to), xpath,null);
                } else {
                    // System.out.println("Had file " + toFileName + ".xml");
                    generatedAliasFiles
                            .put(from, new File(depF, from + ".xml"));
                    fromToMap.put(fromLocale.toString(), to);
                    if (xpath != null) {
                        fromXpathMap.put(fromLocale.toString(), xpath);
                    }

                    // write an individual file
                    writeSimpleLocale(from + ".txt", fromLocale,
                            new ULocale(to), xpath, null);
                }
            }
        }
        if (aliasLocaleList != null && aliasLocaleList.size() > 0) {
            for (int i = 0; i < aliasLocaleList.size(); i++) {
                String source = (String) aliasLocaleList.get(i);
                if (!fromFiles.containsKey(source + ".xml")) {
                    System.err
                            .println("WARNING: Alias file "
                                    + source
                                    + ".xml named in deprecates list but not present. Ignoring alias entry.");
                } else {
                    aliasFromFiles.put(source + ".xml", new File(depF, source
                            + ".xml"));
                    fromFiles.remove(source + ".xml");
                }
            }
        }
        // Post process: calculate any 'valid sub locales' (empty locales
        // generated due to validSubLocales attribute)
        if (!validSubMap.isEmpty() && sourceDir.indexOf("collation") > -1) {
            printInfo("Writing valid sub locs for : " + validSubMap.toString());

            for (Iterator e = validSubMap.keySet().iterator(); e.hasNext();) {
                String actualLocale = (String) e.next();
                String list = (String) validSubMap.get(actualLocale);
                String validSubs[] = list.split(" ");
                // printInfo(actualLocale + " .. ");
                for (int i = 0; i < validSubs.length; i++) {
                    String aSub = validSubs[i];
                    String testSub;
                    // printInfo(" " + aSub);

                    for (testSub = aSub; (testSub != null)
                            && !testSub.equals("root")
                            && (!testSub.equals(actualLocale)); testSub = LDMLUtilities
                            .getParent(testSub)) {
                        // printInfo(" trying " + testSub);
                        if (fromFiles.containsKey(testSub + ".xml")) {
                            printWarning(actualLocale + ".xml",
                                    " validSubLocale=" + aSub
                                            + " overridden because  " + testSub
                                            + ".xml  exists.");
                            testSub = null;
                            break;
                        }
                        if (generatedAliasFiles.containsKey(testSub)) {
                            printWarning(
                                    actualLocale + ".xml",
                                    " validSubLocale="
                                            + aSub
                                            + " overridden because  an alias locale "
                                            + testSub + ".xml  exists.");
                            testSub = null;
                            break;
                        }
                    }
                    if (testSub != null) {
                        emptyFromFiles.put(aSub + ".xml", new File(depF, aSub
                                + ".xml"));
                        // ULocale aSubL = new ULocale(aSub);
                        if (maybeValidAlias.containsKey(aSub)) {
                            String from = (String) maybeValidAlias.get(aSub);
                            // writeSimpleLocale(from+".txt", fromLocale, new
                            // ULocale(to), xpath,null);
                            writeSimpleLocale(from + ".txt", from, aSub, null,
                                    null);
                            maybeValidAlias.remove(aSub);
                            generatedAliasFiles.put(from, new File(depF, from
                                    + ".xml"));
                        }
                        writeSimpleLocale(aSub + ".txt", aSub, null, null,
                                "validSubLocale of \"" + actualLocale + "\"");
                    }
                }
            }
        }
        if (!maybeValidAlias.isEmpty()) {
            Set keys = maybeValidAlias.keySet();
            Iterator iter = keys.iterator();
            while (iter.hasNext()) {
                String to = (String) iter.next();
                String from = (String) maybeValidAlias.get(to);
                System.err
                        .println("WARNING: Alias from \""
                                + from
                                + "\" not generated, because it would point to a nonexistent LDML file "
                                + to + ".xml");
            }
        }

        // System.out.println("In Files: " + inFileText);
        String inFileText = fileMapToList(fromFiles);
        String emptyFileText = null;
        if (!emptyFromFiles.isEmpty()) {
            emptyFileText = fileMapToList(emptyFromFiles);
        }
        String aliasFilesList = fileMapToList(aliasFromFiles);
        String generatedAliasList = fileMapToList(generatedAliasFiles);

        // Now- write the actual items (resfiles.mk, etc)
        String[] brkArray = new String[2];
        if (myTreeName.equals("brkitr")) {
            getBrkCtdFilesList(options[WRITE_DEPRECATED].value, brkArray);
        }
        writeResourceMakefile(myTreeName, generatedAliasList, aliasFilesList,
                inFileText, emptyFileText, brkArray[0], brkArray[1]);
        if (writeDeprecated == false) {
            return; // just looking for overrideDraft
        }
        System.out.println("done.");
        // System.err.println("Error: did not find tree " + myTreeName + " in
        // the deprecated alias table.");
        // System.exit(0);
    }

    public String[] getBrkCtdFilesList(String dir, String[] brkArray) {
        // read all xml files in the directory and create ctd file list and brk
        // file list
        FilenameFilter myFilter = new FilenameFilter() {
            public boolean accept(File f, String name) {
                return (!f.isFile() && name.endsWith(".xml") && !name
                        .startsWith("supplementalData") // not a locale
                ); // root is implied, will be included elsewhere.
            }
        };
        File directory = new File(dir);
        String[] files = directory.list(myFilter);
        StringBuffer brkList = new StringBuffer();
        StringBuffer ctdList = new StringBuffer();

        // open each file and create the list of files for brk and ctd
        for (int i = 0; i < files.length; i++) {
            Document doc = LDMLUtilities.parse(dir + "/" + files[i], false);
            for (Node node = doc.getFirstChild(); node != null; node = node
                    .getNextSibling()) {
                if (node.getNodeType() != Node.ELEMENT_NODE) {
                    continue;
                }
                String name = node.getNodeName();
                if (name.equals(LDMLConstants.LDML)) {
                    node = node.getFirstChild();
                    continue;
                } else if (name.equals(LDMLConstants.IDENTITY)) {
                    continue;
                } else if (name.equals(LDMLConstants.SPECIAL)) {
                    node = node.getFirstChild();
                    continue;
                } else if (name.equals(ICU_BRKITR_DATA)) {
                    node = node.getFirstChild();
                    continue;
                } else if (name.equals(ICU_BOUNDARIES)) {
                    for (Node cn = node.getFirstChild(); cn != null; cn = cn
                            .getNextSibling()) {
                        if (cn.getNodeType() != Node.ELEMENT_NODE) {
                            continue;
                        }
                        String cnName = cn.getNodeName();

                        if (cnName.equals(ICU_GRAPHEME)
                                || cnName.equals(ICU_WORD)
                                || cnName.equals(ICU_TITLE)
                                || cnName.equals(ICU_SENTENCE)
                                || cnName.equals(ICU_LINE)) {
                            String val = LDMLUtilities.getAttributeValue(cn,
                                    ICU_DEPENDENCY);
                            if (val != null) {
                                brkList.append(val.substring(0, val
                                        .indexOf('.')));
                                brkList.append(".txt ");
                            }
                        } else {
                            System.err.println("Encountered unknown <" + name
                                    + "> subelement: " + cnName);
                            System.exit(-1);
                        }
                    }
                } else if (name.equals(ICU_DICTIONARIES)) {
                    for (Node cn = node.getFirstChild(); cn != null; cn = cn
                            .getNextSibling()) {
                        if (cn.getNodeType() != Node.ELEMENT_NODE) {
                            continue;
                        }
                        String cnName = cn.getNodeName();

                        if (cnName.equals(ICU_DICTIONARY)) {
                            String val = LDMLUtilities.getAttributeValue(cn,
                                    ICU_DEPENDENCY);
                            if (val != null) {
                                ctdList.append(val.substring(0, val
                                        .indexOf('.')));
                                ctdList.append(".txt ");
                            }
                        } else {
                            System.err.println("Encountered unknown <" + name
                                    + "> subelement: " + cnName);
                            System.exit(-1);
                        }
                    }
                } else {
                    System.err.println("Encountered unknown <"
                            + doc.getNodeName() + "> subelement: " + name);
                    System.exit(-1);
                }
            }
        }
        if (brkList.length() > 0) {
            brkArray[0] = brkList.toString();
        }
        if (ctdList.length() > 0) {
            brkArray[1] = ctdList.toString();
        }
        return brkArray;
    }

    public boolean isDraftStatusOverridable(String locName) {
        if (localesMap != null && localesMap.size() > 0) {
            String draft = (String) localesMap.get(locName + ".xml");
            if (draft != null
                    && (draft.equals("true") || locName.matches(draft))) {
                return true;
            } else {
                return false;
            }
        } else {
            // check to see if the destination file already exists
            // maybe override draft was specified in the run that produced
            // the txt files
            File f = new File(destDir + "/" + locName + ".txt");
            return f.exists();
        }
    }

    private static String fileIteratorToList(Iterator files) {
        String out = "";
        int i = 0;
        for (; files.hasNext();) {
            File f = (File) files.next();
            if ((++i % 5) == 0) {
                out = out + "\\" + LINESEP;
            }
            out = out + (i == 0 ? " " : " ")
                    + (f.getName()).substring(0, f.getName().indexOf('.'))
                    + ".txt";
        }
        return out;
    }

    private static String fileMapToList(Map files) {
        return fileIteratorToList(files.values().iterator());
    }

    private void writeSimpleLocale(String fileName, ULocale fromLocale,
            ULocale toLocale, String xpath, String comment) {
        writeSimpleLocale(fileName, fromLocale == null ? "" : fromLocale
                .toString(), toLocale == null ? "" : toLocale.toString(),
                xpath, comment);
    }

    private void writeSimpleLocale(String fileName, String fromLocale,
            String toLocale, String xpath, String comment) {

        if (xpath != null) {
            // with CLDRFile this is a piece of cake
            CLDRFile fakeFile = CLDRFile.make(fromLocale);
            fakeFile.add(xpath, "");
            fakeFile.freeze();
            // fakeFile.write(new PrintWriter(System.out));
            InputLocale fakeLocale = new InputLocale(fakeFile);

            locName = fromLocale.toString(); // Global!

            // Feed the bundle into our parser..
            ICUResourceWriter.Resource res = parseBundle(fakeLocale); //

            res.name = fromLocale.toString();
            if (res != null
                    && ((ICUResourceWriter.ResourceTable) res).first != null) {
                // write out the bundle
                writeResource(res, DEPRECATED_LIST);
            } else {
                System.err.println("Failed to write out alias bundle " // parse
                                                                        // error?
                        + fromLocale.toString());
            }

        } else { // no xpath - simple locale-level alias.

            String outputFileName = destDir + "/" + fileName;
            ICUResourceWriter.Resource set = null;
            try {
                ICUResourceWriter.ResourceTable table = new ICUResourceWriter.ResourceTable();
                table.name = fromLocale.toString();
                if (toLocale != null && xpath == null) {
                    ICUResourceWriter.ResourceString str = new ICUResourceWriter.ResourceString();
                    str.name = "\"%%ALIAS\"";
                    str.val = toLocale.toString();
                    table.first = str;
                } else {
                    ICUResourceWriter.ResourceString str = new ICUResourceWriter.ResourceString();
                    str.name = "___";
                    str.val = "";
                    str.comment = "so genrb doesn't issue warnings";
                    table.first = str;
                }
                set = table;
                if (comment != null) {
                    set.comment = comment;
                }
            } catch (Throwable e) {
                printError("", "building synthetic locale tree for "
                        + outputFileName + ": " + e.toString());
                e.printStackTrace();
                System.exit(1);
            }

            try {
                String info;
                if (toLocale != null) {
                    info = "(alias to " + toLocale.toString() + ")";
                } else {
                    info = comment;
                }
                printInfo("Writing synthetic: " + outputFileName + " " + info);
                FileOutputStream file = new FileOutputStream(outputFileName);
                BufferedOutputStream writer = new BufferedOutputStream(file);
                writeHeader(writer, DEPRECATED_LIST);

                ICUResourceWriter.Resource current = set;
                while (current != null) {
                    current.sort();
                    current = current.next;
                }

                // Now start writing the resource;
                /* ICUResourceWriter.Resource */current = set;
                while (current != null) {
                    current.write(writer, 0, false);
                    current = current.next;
                }
                writer.flush();
                writer.close();
            } catch (IOException e) {
                System.err.println("ERROR: While writing synthetic locale "
                        + outputFileName + ": " + e.toString());
                e.printStackTrace();
                System.exit(1);
            }
        }
    }

    private void writeResourceMakefile(String myTreeName,
            String generatedAliasList, String aliasFilesList,
            String inFileText, String emptyFileText, String brkFilesList,
            String ctdFilesList) {
        // / Write resfiles.mk
        String stub = "UNKNOWN";
        String shortstub = "unk";

        if (myTreeName.equals("main")) {
            stub = "GENRB"; // GENRB_SOURCE, GENRB_ALIAS_SOURCE
            shortstub = "res"; // resfiles.mk
        } else if (myTreeName.equals("collation")) {
            stub = "COLLATION"; // COLLATION_ALIAS_SOURCE, COLLATION_SOURCE
            shortstub = "col"; // colfiles.mk
        } else if (myTreeName.equals("brkitr")) {
            stub = "BRK_RES"; // BRK_SOURCE, BRK_CTD_SOURCE BRK_RES_SOURCE
            shortstub = "brk"; // brkfiles.mk
        } else {
            printError("", "Unknown tree name in writeResourceMakefile: "
                    + myTreeName);
            System.exit(-1);
        }

        String resfiles_mk_name = destDir + "/" + shortstub + "files.mk";
        try {
            printInfo("Writing ICU build file: " + resfiles_mk_name);
            PrintStream resfiles_mk = new PrintStream(new FileOutputStream(
                    resfiles_mk_name));
            Calendar c = Calendar.getInstance();
            resfiles_mk.println("# *   Copyright (C) 1998-"
                    + c.get(Calendar.YEAR)
                    + ", International Business Machines");
            resfiles_mk
                    .println("# *   Corporation and others.  All Rights Reserved.");
            resfiles_mk.println("# A list of txt's to build");
            resfiles_mk.println("# Note: ");
            resfiles_mk.println("#");
            resfiles_mk
                    .println("#   If you are thinking of modifying this file, READ THIS. ");
            resfiles_mk.println("#");
            resfiles_mk
                    .println("# Instead of changing this file [unless you want to check it back in],");
            resfiles_mk.println("# you should consider creating a '"
                    + shortstub + "local.mk' file in this same directory.");
            resfiles_mk
                    .println("# Then, you can have your local changes remain even if you upgrade or");
            resfiles_mk.println("# reconfigure ICU.");
            resfiles_mk.println("#");
            resfiles_mk.println("# Example '" + shortstub + "local.mk' files:");
            resfiles_mk.println("#");
            resfiles_mk
                    .println("#  * To add an additional locale to the list: ");
            resfiles_mk
                    .println("#    _____________________________________________________");
            resfiles_mk.println("#    |  " + stub
                    + "_SOURCE_LOCAL =   myLocale.txt ...");
            resfiles_mk.println("#");
            resfiles_mk
                    .println("#  * To REPLACE the default list and only build with a few");
            resfiles_mk.println("#     locale:");
            resfiles_mk
                    .println("#    _____________________________________________________");
            resfiles_mk.println("#    |  " + stub
                    + "_SOURCE = ar.txt ar_AE.txt en.txt de.txt zh.txt");
            resfiles_mk.println("#");
            resfiles_mk.println("#");
            resfiles_mk
                    .println("# Generated by LDML2ICUConverter, from LDML source files. ");
            resfiles_mk.println("");
            resfiles_mk
                    .println("# Aliases which do not have a corresponding xx.xml file (see "
                            + DEPRECATED_LIST + ")");
            resfiles_mk.println(stub + "_SYNTHETIC_ALIAS ="
                    + generatedAliasList); // note: lists start with a space.
            resfiles_mk.println("");
            resfiles_mk.println("");
            resfiles_mk
                    .println("# All aliases (to not be included under 'installed'), but not including root.");
            resfiles_mk.println(stub + "_ALIAS_SOURCE = $(" + stub
                    + "_SYNTHETIC_ALIAS)" + aliasFilesList);
            resfiles_mk.println("");
            resfiles_mk.println("");
            if (ctdFilesList != null) {
                resfiles_mk
                        .println("# List of compact trie dictionary files (ctd).");
                resfiles_mk.println("BRK_CTD_SOURCE = " + ctdFilesList);
                resfiles_mk.println("");
                resfiles_mk.println("");
            }
            if (brkFilesList != null) {
                resfiles_mk.println("# List of break iterator files (brk).");
                resfiles_mk.println("BRK_SOURCE = " + brkFilesList);
                resfiles_mk.println("");
                resfiles_mk.println("");
            }
            if (emptyFileText != null) {
                resfiles_mk
                        .println("# Empty locales, used for validSubLocale fallback.");
                resfiles_mk.println(stub + "_EMPTY_SOURCE =" + emptyFileText); // note:
                // lists
                // start
                // with
                // a
                // space.
                resfiles_mk.println("");
                resfiles_mk.println("");
            }
            resfiles_mk.println("# Ordinary resources");
            if (emptyFileText == null) {
                resfiles_mk.print(stub + "_SOURCE =" + inFileText);
            } else {
                resfiles_mk.print(stub + "_SOURCE = $(" + stub
                        + "_EMPTY_SOURCE)" + inFileText);
            }
            resfiles_mk.println("");
            resfiles_mk.println("");

            resfiles_mk.close();
        } catch (IOException e) {
            System.err.println("While writing " + resfiles_mk_name);
            e.printStackTrace();
            System.exit(1);
        }
    }
}
