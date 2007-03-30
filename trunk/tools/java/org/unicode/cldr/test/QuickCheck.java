package org.unicode.cldr.test;

import org.unicode.cldr.util.CLDRFile;
import org.unicode.cldr.util.Log;
import org.unicode.cldr.util.PrettyPath;
import org.unicode.cldr.util.Relation;
import org.unicode.cldr.util.Utility;
import org.unicode.cldr.util.XMLFileReader;
import org.unicode.cldr.util.XPathParts;
import org.unicode.cldr.util.CLDRFile.Factory;

import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.XMLReader;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Simple test that loads each file in the cldr directory, thus verifying that
 * the DTD works, and also checks that the PrettyPaths work.
 * 
 * @author markdavis
 */
public class QuickCheck {
  private static final Set skipAttributes = new HashSet(Arrays.asList(new String[]{
      "alt", "draft", "references"}));

  private static String localeRegex;
  
  private static boolean showInfo = false;
  
  public static void main(String[] args) throws IOException {
    localeRegex = System.getProperty("LOCALE");
    if (localeRegex == null) localeRegex = ".*";
    showInfo = System.getProperty("SHOW") != null;
    System.out.println("ShowInfo: " + showInfo + "\t\t(use -DSHOW) to enable");
    double deltaTime = System.currentTimeMillis();
    checkDtds();
    checkPaths();
    deltaTime = System.currentTimeMillis() - deltaTime;
    System.out.println("Elapsed: " + deltaTime/1000.0 + " seconds");
    System.out.println("Basic Test Passes");
  }

  private static void checkDtds() throws IOException {
    checkDtds(Utility.COMMON_DIRECTORY + "collation");
    checkDtds(Utility.COMMON_DIRECTORY + "main");
    checkDtds(Utility.COMMON_DIRECTORY + "segments");
    checkDtds(Utility.COMMON_DIRECTORY + "supplemental");
    checkDtds(Utility.COMMON_DIRECTORY + "transforms");
  }

  private static void checkDtds(String directory) throws IOException {
    File directoryFile = new File(directory);
    File[] listFiles = directoryFile.listFiles();
    String canonicalPath = directoryFile.getCanonicalPath();
    if (listFiles == null) {
      throw new IllegalArgumentException("Empty directory: " + canonicalPath);
    }
    System.out.println("Checking files in " + canonicalPath);
    for (File fileName : listFiles) {
      if (!fileName.toString().endsWith(".xml")) {
        continue;
      }
      check(fileName);
    }
  }

  static class MyErrorHandler implements ErrorHandler {
    public void error(SAXParseException exception) throws SAXException {
      System.out.println("error: " + XMLFileReader.showSAX(exception));
      throw exception;
    }
    public void fatalError(SAXParseException exception) throws SAXException {
      System.out.println("fatalError: " + XMLFileReader.showSAX(exception));
      throw exception;
    }
    public void warning(SAXParseException exception) throws SAXException {
      System.out.println("warning: " + XMLFileReader.showSAX(exception));
      throw exception;
    }
  }
  
  public static void check(File systemID) {
    try {
      FileInputStream fis = new FileInputStream(systemID);
      XMLReader xmlReader = XMLFileReader.createXMLReader(true);
      xmlReader.setErrorHandler(new MyErrorHandler());
      InputSource is = new InputSource(fis);
      is.setSystemId(systemID.toString());
      xmlReader.parse(is);
      fis.close();
    } catch (SAXParseException e) {
      System.out.println("\t" + "Can't read " + systemID);
      System.out.println("\t" + e.getClass() + "\t" + e.getMessage());
    } catch (SAXException e) {
      System.out.println("\t" + "Can't read " + systemID);
      System.out.println("\t" + e.getClass() + "\t" + e.getMessage());
    } catch (IOException e) {
      System.out.println("\t" + "Can't read " + systemID);
      System.out.println("\t" + e.getClass() + "\t" + e.getMessage());
    }      
  }

  static Matcher skipPaths = Pattern.compile("/identity" + "|/alias" + "|\\[@alt=\"proposed").matcher("");

  private static void checkPaths() {
    Relation<String,String> distinguishing = new Relation(new TreeMap(), TreeSet.class, null);
    Relation<String,String> nonDistinguishing = new Relation(new TreeMap(), TreeSet.class, null);
    XPathParts parts = new XPathParts();
    Factory cldrFactory = Factory.make(Utility.MAIN_DIRECTORY, localeRegex);
    
    Relation<String, String> pathToLocale = new Relation(new TreeMap(CLDRFile.ldmlComparator), TreeSet.class, null);
    for (String locale : cldrFactory.getAvailable()) {
      if (locale.equals("root") && !localeRegex.equals("root"))
        continue;
      CLDRFile file = cldrFactory.make(locale, false);
      if (file.isNonInheriting())
        continue;
      System.out.println(locale);
      for (Iterator<String> it = file.iterator(); it.hasNext();) {
        String path = it.next();
        pathToLocale.put(path, locale);
        
        // also check for non-distinguishing attributes
        if (path.contains("/identity")) continue;
        String fullPath = file.getFullXPath(path);
        parts.set(fullPath);
        for (int i = 0; i < parts.size(); ++i) {
          if (parts.getAttributeCount(i) == 0) continue;
          String element = parts.getElement(i);
          for (String attribute : parts.getAttributeKeys(i)) {
            if (skipAttributes.contains(attribute)) continue;
            if (file.isDistinguishing(element, attribute)) {
              distinguishing.put(element, attribute);
            } else {
              nonDistinguishing.put(element, attribute);
            }
          }
        }
      }
    }

    System.out.format("Distinguishing Elements: %s\r\n", distinguishing);
    System.out.format("Nondistinguishing Elements: %s\r\n", nonDistinguishing);
    System.out.format("Skipped %s\r\n", skipAttributes);
    
    System.out.println("\r\nPaths to skip in Survey Tool");
    for (String path : pathToLocale.keySet()) {
      if (CheckCLDR.skipShowingInSurvey.matcher(path).matches()) {
        System.out.println("Skipping: " + path);
      }
    }
    
    if (showInfo) {
      System.out.println("\r\nShowing Path to PrettyPath mapping\r\n");
    }
    PrettyPath prettyPath = new PrettyPath().setShowErrors(true);
    Set<String> badPaths = new TreeSet();
    for (String path : pathToLocale.keySet()) {
      String prettied = prettyPath.getPrettyPath(path, false);
      if (showInfo) System.out.println(prettied + "\t\t" + path);
      if (prettied.contains("%%") && !path.contains("/alias")) {
        badPaths.add(path);
      }
    }
    // now remove root
    
    if (showInfo) {
      System.out.println("\r\nShowing Paths not in root\r\n");
    }

    CLDRFile root = cldrFactory.make("root", true);
    for (Iterator<String> it = root.iterator(); it.hasNext();) {
      pathToLocale.removeAll(it.next());
    }
    if (showInfo) for (String path : pathToLocale.keySet()) {
      if (skipPaths.reset(path).find()) {
        continue;
      }
      System.out.println(path + "\t" + pathToLocale.getAll(path));
    }

    if (badPaths.size() != 0) {
      System.out.println("Error: " + badPaths.size() + " Paths were not prettied: use -DSHOW and look for ones with %% in them.");
    }
  }

}