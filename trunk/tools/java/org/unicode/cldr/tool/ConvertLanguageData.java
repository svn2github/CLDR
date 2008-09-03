package org.unicode.cldr.tool;


import org.unicode.cldr.util.CLDRFile;
import org.unicode.cldr.util.Iso639Data;
import org.unicode.cldr.util.LanguageTagParser;
import org.unicode.cldr.util.LocaleIDParser;
import org.unicode.cldr.util.Log;
import org.unicode.cldr.util.Pair;
import org.unicode.cldr.util.Relation;
import org.unicode.cldr.util.SpreadSheet;
import org.unicode.cldr.util.StandardCodes;
import org.unicode.cldr.util.SupplementalDataInfo;
import org.unicode.cldr.util.Utility;
import org.unicode.cldr.util.XPathParts;
import org.unicode.cldr.util.CLDRFile.Factory;
import org.unicode.cldr.util.Iso639Data.Scope;
import org.unicode.cldr.util.Iso639Data.Source;
import org.unicode.cldr.util.Iso639Data.Type;
import org.unicode.cldr.util.LocaleIDParser.Level;
import org.unicode.cldr.util.SupplementalDataInfo.BasicLanguageData;
import org.unicode.cldr.util.SupplementalDataInfo.OfficialStatus;
import org.unicode.cldr.util.SupplementalDataInfo.PopulationData;
import org.unicode.cldr.util.XPathParts.Comments;

import com.ibm.icu.dev.test.util.BagFormatter;
import com.ibm.icu.dev.test.util.TransliteratorUtilities;
import com.ibm.icu.text.Collator;
import com.ibm.icu.text.NumberFormat;
import com.ibm.icu.text.RuleBasedCollator;
import com.ibm.icu.text.UTF16;
import com.ibm.icu.util.ULocale;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author markdavis
 *
 */
public class ConvertLanguageData {
  
  // change this if you need to override what is generated for the default contents.
  private static final List<String> defaultOverrides = Arrays.asList("es_ES".split("\\s+")); //  und_ZZ

  private static final boolean ALLOW_SMALL_NUMBERS = true;
 
  static final Comparator GENERAL_COLLATOR = new GeneralCollator();
  static final Comparator INVERSE_GENERAL = new InverseComparator(GENERAL_COLLATOR);
  
  private static StandardCodes sc = StandardCodes.make();

  static final double populationFactor = 1;
  static final double gdpFactor = 1;
  // static final int COUNTRY_CODE = 2, LANGUAGE_POPULATION = 3, LANGUAGE_LITERACY = 4, BAD_LANGUAGE_NAME = 5, LANGUAGE_CODE = 6, BAD_LANGUAGE_CODE = 7, COUNTRY_POPULATION = 8, COUNTRY_LITERACY = 9, COUNTRY_GDP = 10, COMMENT=17;
  static final int BAD_COUNTRY_NAME = 0, COUNTRY_CODE = 1, COUNTRY_POPULATION = 2, COUNTRY_LITERACY = 3, COUNTRY_GDP = 4, OFFICIAL_STATUS = 5, BAD_LANGUAGE_NAME = 6, LANGUAGE_CODE = 7, LANGUAGE_POPULATION = 8, LANGUAGE_LITERACY = 9, COMMENT=10, NOTES=11;
  static final Map<String, CodeAndPopulation> languageToMaxCountry = new TreeMap<String, CodeAndPopulation>();
  static final Map<String, CodeAndPopulation> languageToMaxScript = new TreeMap<String, CodeAndPopulation>();

  private static final double NON_OFFICIAL_WEIGHT = 0.40;
  
  static Map<String,String> defaultContent = new TreeMap<String,String>();
  
  static CLDRFile english;
  static Set locales;
  static Factory cldrFactory;
  static Set skipLocales = new HashSet(Arrays.asList("sh sh_BA sh_CS sh_YU characters supplementalData supplementalData-old supplementalData-old2 supplementalData-old3 supplementalMetadata root".split("\\s")));
  
  static NumberFormat nf = NumberFormat.getInstance(ULocale.ENGLISH);
  static NumberFormat pf = NumberFormat.getPercentInstance(ULocale.ENGLISH);
  static NumberFormat pf2 = NumberFormat.getPercentInstance(ULocale.ENGLISH);
  static {
    pf2.setMinimumFractionDigits(2);
  }
  
  static SupplementalDataInfo supplementalData = SupplementalDataInfo.getInstance(Utility.SUPPLEMENTAL_DIRECTORY);
  
  public static void main(String[] args) throws IOException, ParseException {
    BufferedReader oldFile = null;
    try {
      // load elements we care about
      Log.setLogNoBOM(Utility.GEN_DIRECTORY + "/supplemental/supplementalData.xml");
      //Log.println("<?xml version=\"1.0\" encoding=\"UTF-8\" ?>");
      //Log.println("<!DOCTYPE supplementalData SYSTEM \"http://www.unicode.org/cldr/data/dtd/ldmlSupplemental.dtd\">");
      //Log.println("<supplementalData version=\"1.5\">");
      
      oldFile = BagFormatter.openUTF8Reader(Utility.SUPPLEMENTAL_DIRECTORY, "supplementalData.xml");
      Utility.copyUpTo(oldFile, Pattern.compile("\\s*<languageData>\\s*"), Log.getLog(), false);

      cldrFactory = Factory.make(Utility.MAIN_DIRECTORY, ".*");
      Set available = cldrFactory.getAvailable();
      english = cldrFactory.make("en",true);
      
      Set<String> cldrParents = getCldrParents(available);
      
      List<String> failures = new ArrayList<String>();
      Map<String,RowData> localeToRowData = new TreeMap<String,RowData>();
      
      Set<RowData> sortedInput = getExcelData(failures, localeToRowData);
      
      // TODO sort by country code, then functionalPopulation, then language code
      // and keep the top country for each language code (even if < 1%)
      
      addLanguageScriptData();
      
      //showAllBasicLanguageData(allLanguageData, "old");
      getLanguage2Scripts(sortedInput);
      
      writeNewBasicData2(sortedInput);
      //writeNewBasicData(sortedInput);

      writeTerritoryLanguageData(failures, sortedInput);

      checkBasicData(localeToRowData);
      
      Set<String> defaultLocaleContent = new TreeSet();

      showDefaults(cldrParents, nf, defaultContent, localeToRowData, defaultLocaleContent);
      
      //showContent(available);
      
      // certain items are overridden
      
      List<String> toRemove = new ArrayList();
      for (String override : defaultOverrides) {
        String replacement = getReplacement(override, defaultLocaleContent);
        toRemove.add(replacement);
      }
      defaultLocaleContent.removeAll(toRemove);
      defaultLocaleContent.addAll(defaultOverrides);
      
      showDefaultContentDifferences(defaultLocaleContent);
      
      showFailures(failures);
      
      Utility.copyUpTo(oldFile, Pattern.compile("\\s*</territoryInfo>\\s*"), null, false);
      Utility.copyUpTo(oldFile, Pattern.compile("\\s*<references>\\s*"), Log.getLog(), false);
      //generateIso639_2Data();
      references.printReferences();
      Utility.copyUpTo(oldFile, Pattern.compile("\\s*</references>\\s*"), null, false);
      Utility.copyUpTo(oldFile, null, Log.getLog(), false);
      //Log.println("</supplementalData>");
      Log.close();
      oldFile.close();
      
//      Log.setLogNoBOM(Utility.GEN_DIRECTORY + "/supplemental/supplementalMetadata.xml");
//      oldFile = BagFormatter.openUTF8Reader(Utility.SUPPLEMENTAL_DIRECTORY, "supplementalMetadata.xml");
//      copyUpTo(oldFile, Pattern.compile("\\s*<defaultContent locales=\"\\s*"), Log.getLog(), false);
// 
////      Log.println("<?xml version=\"1.0\" encoding=\"UTF-8\" ?>");
////      Log.println("<!DOCTYPE supplementalData SYSTEM \"http://www.unicode.org/cldr/data/dtd/ldmlSupplemental.dtd\">");
////      Log.println("<supplementalData version=\"1.5\">");
//      printDefaultContent(defaultLocaleContent);
////      Log.println("</supplementalData>");
//      copyUpTo(oldFile, Pattern.compile("\\s*/>\\s*"), null, false);
//      copyUpTo(oldFile, null, Log.getLog(), false);
//
//      Log.close();
//      oldFile.close();
      
      Log.setLog(Utility.GEN_DIRECTORY + "/supplemental/language_script_raw.txt");
      getLanguageScriptSpreadsheet(Log.getLog());
      Log.close();
    } catch (Exception e) {
      e.printStackTrace();
    } finally {
      if (oldFile != null) {
        oldFile.close();
      }
      System.out.println("DONE");
    } 
  }

  private static void showDefaultContentDifferences(Set<String> defaultLocaleContent) {
    Set<String> oldDefaultContent = supplementalData.getDefaultContentLocales();
    for (String oldDefault : oldDefaultContent) {
      if (!defaultLocaleContent.contains(oldDefault)) {
        String replacement = getReplacement(oldDefault, defaultLocaleContent);
        System.out.println("Changed default content: examine carefully: " 
            + getLanguageCodeAndName(oldDefault)
            + "\t=>\t" + getLanguageCodeAndName(replacement)
            );
      }
    }
  }
  
  public static String getLanguageCodeAndName(String code) {
    if (code == null) return null;
    return english.getName(code) + " [" + code + "]";
  }

  private static String getReplacement(String oldDefault, Set<String> defaultLocaleContent) {
    String parent = LocaleIDParser.getParent(oldDefault);
    for (String replacement : defaultLocaleContent) {
      if (replacement.startsWith(parent)) {
        if (parent.equals(LocaleIDParser.getParent(replacement))) {
          return replacement;
        }
      }
    }
    return null;
  }

  private static void getLanguageScriptSpreadsheet(PrintWriter out) {
    out.println("#Lcode LanguageName  Status  Scode ScriptName  References");
    Pair<String,String> languageScript = new Pair("","");
    for (String language : language_status_scripts.keySet()) {
      Relation<BasicLanguageData.Type, String> status_scripts = language_status_scripts.get(language);
      for (BasicLanguageData.Type status : status_scripts.keySet()) {
        for (String script : status_scripts.getAll(status)) {
          String reference = language_script_references.get(languageScript.setFirst(language).setSecond(script));
          out.println(language + "\t" + getLanguageName(language) + "\t" + status + "\t" + script + "\t" + getDisplayScript(script)
              + (reference == null ? "" : "\t" + reference));
        }
      }
    }
  }

  /**
   * Write data in format:
   * <languageData>
   *   <language type="aa" scripts="Latn" territories="DJ ER ET"/>
   * @param sortedInput
   */
  private static void writeNewBasicData(Set<RowData> sortedInput) {
    double cutoff = 0.2; // 20%
    
    // get current scripts
    Relation<String,String> languageToDefaultScript = new Relation(new TreeMap(), TreeSet.class);
    Relation<String,String> secondaryLanguageToDefaultScript = new Relation(new TreeMap(), TreeSet.class);
    for (String languageSubtag : language2BasicLanguageData.keySet()) {
      for (BasicLanguageData item : language2BasicLanguageData.getAll(languageSubtag)) {
        Set<String> scripts = item.getScripts();
        if (item.getType() == BasicLanguageData.Type.secondary) {
          secondaryLanguageToDefaultScript.putAll(languageSubtag, item.getScripts());;
        } else {
          languageToDefaultScript.putAll(languageSubtag, item.getScripts());
        }
      }
    }
    
//    // if primary has no scripts, add secondary, and vice versa.
//    for (String language : secondaryLanguageToDefaultScript.keySet()) {
//      if (!languageToDefaultScript.containsKey(language)) {
//        languageToDefaultScript.putAll(language, secondaryLanguageToDefaultScript.getAll(language));
//      }
//    }
//    for (String language : languageToDefaultScript.keySet()) {
//      if (!secondaryLanguageToDefaultScript.containsKey(language)) {
//        secondaryLanguageToDefaultScript.putAll(language, languageToDefaultScript.getAll(language));
//      }
//    }

    Relation<String, BasicLanguageData> newLanguageData = new Relation(new TreeMap(), TreeSet.class);
    LanguageTagParser ltp = new LanguageTagParser();
    Map<String,Map<String,Relation<String,String>>> language2script2type2territory = new TreeMap();
    for (RowData rowData : sortedInput) {
      ltp.set(rowData.languageCode);
      String languageCode = ltp.getLanguage();
      BasicLanguageData.Type type;
      Relation<String, String> oldLanguageData;
      if (rowData.officialStatus == OfficialStatus.official 
          || rowData.officialStatus == OfficialStatus.de_facto_official) {
        type = BasicLanguageData.Type.primary;
        oldLanguageData = languageToDefaultScript;
      } else if (rowData.officialStatus != OfficialStatus.unknown 
          || rowData.languagePopulation >= cutoff * rowData.countryPopulation
          || rowData.languagePopulation >= 1000000
          ) {
        type = BasicLanguageData.Type.secondary;
        oldLanguageData = secondaryLanguageToDefaultScript;
      } else {
        continue; // skip
      }
      BasicLanguageData basicLanguageData = new BasicLanguageData().setType(type).addTerritory(rowData.countryCode);
      String script = ltp.getScript();
      if (script.length() != 0) {
        basicLanguageData.addScript(script);
      }
      Set<String> scripts = oldLanguageData.getAll(languageCode);
      if (scripts != null) {
        basicLanguageData.addScripts(scripts);
        oldLanguageData.removeAll(languageCode);
      }
      newLanguageData.put(languageCode, (BasicLanguageData) basicLanguageData.freeze());
    }
    
    // now add all the remaining language-script info
    for (String languageSubtag : language2BasicLanguageData.keySet()) {
      Set<String> scripts;
      scripts = languageToDefaultScript.getAll(languageSubtag);
      if (scripts != null) {
        newLanguageData.put(languageSubtag, new BasicLanguageData().setType(BasicLanguageData.Type.primary).setScripts(scripts));
      }
      scripts = secondaryLanguageToDefaultScript.getAll(languageSubtag);
      if (scripts != null) {
        newLanguageData.put(languageSubtag, new BasicLanguageData().setType(BasicLanguageData.Type.secondary).setScripts(scripts));
      }
    }
    
    // show missing scripts
    Set<String> languageWithoutScripts = new TreeSet();
    for (String languageSubtag : newLanguageData.keySet()) {
      if (languageSubtag.equals("und")) {
        continue;
      }
      for (BasicLanguageData item : newLanguageData.getAll(languageSubtag)) {
        if (item.getScripts().size() == 0) {
          languageWithoutScripts.add(languageSubtag);
        }
      }
    }
    System.out.println("Languages without script: ");
    for (String languageSubtag : languageWithoutScripts) {
      System.out.println(languageSubtag + "\t" + getLanguageName(languageSubtag));
    }
    
    // now output
    showAllBasicLanguageData(newLanguageData, "revised");
  }
  
  private static void writeNewBasicData2(Set<RowData> sortedInput) {
    double cutoff = 0.2; // 20%
    
    Relation<String, BasicLanguageData> newLanguageData = new Relation(new TreeMap(), TreeSet.class);
    LanguageTagParser ltp = new LanguageTagParser();
    Map<String,Relation<BasicLanguageData.Type,String>> language_status_territories = new TreeMap();
    for (RowData rowData : sortedInput) {
      if (rowData.countryCode.equals("ZZ")) continue;
      ltp.set(rowData.languageCode);
      String languageCode = ltp.getLanguage();
      Relation<BasicLanguageData.Type,String> status_territories = language_status_territories.get(languageCode);
      if (status_territories == null) {
        language_status_territories.put(languageCode, status_territories = new Relation(new TreeMap(), TreeSet.class));
      }
      if (rowData.officialStatus == OfficialStatus.official 
          || rowData.officialStatus == OfficialStatus.de_facto_official) {
        status_territories.put(BasicLanguageData.Type.primary, rowData.countryCode);
      } else if (rowData.officialStatus != OfficialStatus.unknown 
          || rowData.languagePopulation >= cutoff * rowData.countryPopulation
          || rowData.languagePopulation >= 1000000
          ) {
        status_territories.put(BasicLanguageData.Type.secondary, rowData.countryCode);
      }
    }
    
    Set<String> allLanguages = new TreeSet(language_status_territories.keySet());
    allLanguages.addAll(language_status_scripts.keySet());
    // now add all the remaining language-script info
    // <language type="sv" scripts="Latn" territories="AX FI SE"/>
    Log.println("\t<languageData>");
    for (String languageSubtag : allLanguages) {
      Relation<BasicLanguageData.Type, String> status_scripts = language_status_scripts.get(languageSubtag);
      Relation<BasicLanguageData.Type, String> status_territories = language_status_territories.get(languageSubtag);
      for (BasicLanguageData.Type status : BasicLanguageData.Type.values()) {
        Set<String> scripts = status_scripts == null ? null : status_scripts.getAll(status);
        Set<String> territories = status_territories == null ? null : status_territories.getAll(status);
        if (scripts == null && territories == null) continue;
        Log.println("\t\t<language type=\"" + languageSubtag + "\""
            + (scripts == null ? "" : " scripts=\"" + Utility.join(scripts, " ") + "\"")
            + (territories == null ? "" : " territories=\"" + Utility.join(territories, " ") + "\"")
            + (status == BasicLanguageData.Type.primary ? "" :  " alt=\"secondary\"")
            + "/>");
      }
    }
    Log.println("\t</languageData>");
  }
  
  private static void checkBasicData(Map<String, RowData> localeToRowData) {
    // find languages with multiple scripts
    Relation<String,String> languageToScripts = new Relation(new TreeMap(), TreeSet.class);
    for (String languageSubtag : language2BasicLanguageData.keySet()) {
      for (BasicLanguageData item : language2BasicLanguageData.getAll(languageSubtag)) {
        Set<String> scripts = new TreeSet();
        languageToScripts.putAll(StandardCodes.fixLanguageTag(languageSubtag), item.getScripts());
      }
    }
    // get primary combinations
    Set<String> primaryCombos = new TreeSet();
    Set<String> basicCombos = new TreeSet();
    for (String languageSubtag : language2BasicLanguageData.keySet()) {
      for (BasicLanguageData item : language2BasicLanguageData.getAll(languageSubtag)) {
        Set<String> scripts = new TreeSet();
        scripts.addAll(item.getScripts());
        languageToScripts.putAll(StandardCodes.fixLanguageTag(languageSubtag), scripts);
        if (scripts.size() == 0) {
          scripts.add("Zzzz");
        }
        Set<String> territories = new TreeSet();
        territories.addAll(item.getTerritories());
        if (territories.size() == 0) {
          territories.add("ZZ");
          continue;
        }
        
        for (String script : scripts) {
          for (String territory : territories) {
            String locale = StandardCodes.fixLanguageTag(languageSubtag)
            // + (script.equals("Zzzz") ? "" : languageToScripts.getAll(languageSubtag).size() <= 1 ? "" : "_" + script)
            + (territories.equals("ZZ") ? "" : "_" + territory)
            ;
            if (item.getType() != BasicLanguageData.Type.secondary) {
              primaryCombos.add(locale);
            }
            basicCombos.add(locale);
          }
        }
      }
    }
    Set<String> populationOver20 = new TreeSet();
    Set<String> population = new TreeSet();
    LanguageTagParser ltp = new LanguageTagParser();
    for (String rawLocale : localeToRowData.keySet()) {
      ltp.set(rawLocale);
      String locale = ltp.getLanguage() + (ltp.getRegion().length() == 0 ? "" : "_" + ltp.getRegion());
      population.add(locale);
      RowData rowData = localeToRowData.get(rawLocale);
      if (rowData.languagePopulation / rowData.countryPopulation >= 0.2) {
        populationOver20.add(locale);
      } else {
        PopulationData popData = supplementalData.getLanguageAndTerritoryPopulationData(ltp.getLanguageScript(), ltp.getRegion());
        if (popData != null && popData.getOfficialStatus() != OfficialStatus.unknown) {
          populationOver20.add(locale);
        }
      }
    }
    Set<String> inBasicButNotPopulation = new TreeSet(primaryCombos);
    
    inBasicButNotPopulation.removeAll(population);
    System.out.println("In Basic Data but not Population > 20%:\t" + inBasicButNotPopulation);
    for (String locale : inBasicButNotPopulation) {
      ltp.set(locale);
      String region = ltp.getRegion();
      String language = ltp.getLanguage();
      if (!sc.isModernLanguage(language)) continue;
      PopulationData popData = supplementalData.getPopulationDataForTerritory(region);
      // Afghanistan  AF  "29,928,987"  28.10%  "21,500,000,000"    Hazaragi  haz "1,770,000" 28.10%
      System.out.println(
          getDisplayCountry(region)
              + "\t" + region
              + "\t\"" + nf.format(popData.getPopulation()) + "\""
              + "\t\"" + pf.format(popData.getLiteratePopulation()/popData.getPopulation()) + "\""
              + "\t\"" + pf.format(popData.getGdp()) + "\""
              + "\t" + ""
              + "\t" + getLanguageName(language)
              + "\t" + language
              + "\t" + -1
              + "\t\"" + pf.format(popData.getLiteratePopulation()/popData.getPopulation()) + "\""
              );
    }

    Set<String> inPopulationButNotBasic = new TreeSet(populationOver20);
    inPopulationButNotBasic.removeAll(basicCombos);
    for (Iterator<String> it = inPopulationButNotBasic.iterator(); it.hasNext();) {
      String locale = it.next();
      if (locale.endsWith("_ZZ")) {
        it.remove();
      }
    }
    System.out.println("In Population>20% but not Basic Data:\t" + inPopulationButNotBasic);
    for (String locale : inPopulationButNotBasic) {
      System.out.println("\t" + locale + "\t" + getLanguageName(locale) + "\t" + localeToRowData.get(locale));
    }
  }
  
  static class LanguageInfo {
    static LanguageInfo INSTANCE = new LanguageInfo();
    
    Map<String,Set<String>> languageToScripts = new TreeMap<String,Set<String>>();
    Map<String,Set<String>> languageToRegions = new TreeMap<String,Set<String>>();
    Map<String,Comments> languageToComments = new TreeMap<String,Comments>();
    
    Map<String,Set<String>> languageToScriptsAlt = new TreeMap<String,Set<String>>();
    Map<String,Set<String>> languageToRegionsAlt = new TreeMap<String,Set<String>>();
    Map<String,Comments> languageToCommentsAlt = new TreeMap<String,Comments>();
    
    private LanguageInfo() {
      cldrFactory = Factory.make(Utility.MAIN_DIRECTORY, ".*");
      Set available = cldrFactory.getAvailable();
      CLDRFile supplemental = cldrFactory.make("supplementalData", true);
      XPathParts parts = new XPathParts();
      for (Iterator it = supplemental.iterator("//supplementalData/languageData/language"); it.hasNext();) {
        String xpath = (String) it.next();
        Map x = parts.set(xpath).getAttributes(-1);
        boolean alt = x.containsKey("alt");
        String lang = (String) x.get("type");
        List scripts = getAttributeList(x, "scripts");
        if (scripts != null) {
          if (alt) {
            putAll(languageToScriptsAlt, lang, new LinkedHashSet(scripts));
          } else {
            putAll(languageToScripts, lang, new LinkedHashSet(scripts));        
          }
        }
        List regions = getAttributeList(x, "territories");
        if (regions != null) {
          if (alt) {
            putAll(languageToRegionsAlt, lang, new LinkedHashSet(regions));
          } else {
            putAll(languageToRegions, lang, new LinkedHashSet(regions));
          }
        }
      }
    }
    
    private List getAttributeList(Map x, String attribute) {
      List scripts = null;
      String scriptString = (String) x.get(attribute);
      if (scriptString != null) {
        scripts = Arrays.asList(scriptString.split("\\s+"));
      }
      return scripts;
    }
  }
  
  private static <K,V> void putUnique(Map<K, V> map, K key, V value) {
    V oldValue = map.get(key);
    if (oldValue != null && !oldValue.equals(value)) {
      throw new IllegalArgumentException("Duplicate value for <" + key + ">: <" + oldValue + ">, <" + value + ">");
    }
    map.put(key, value);
  }
  
  private static <K, W> void putAll(Map<K, Set<W>> map, K key, Set<W> values) {
    Set<W> oldValue = map.get(key);
    if (oldValue == null) {
      map.put(key, values);
    } else {
      oldValue.addAll(values);
    }
  }
  
  // public enum OfficialStatus {unknown, de_facto_official, official, official_regional, official_minority};
  
  static class RowData implements Comparable {
    String countryCode = "";
    double countryGdp;
    double countryLiteracy;
    double countryPopulation;
    String languageCode = "";
    OfficialStatus officialStatus = OfficialStatus.unknown;
    double languagePopulation;
    double languageLiteracy;
    String comment = "";
    String notes = "";
    String badLanguageName = "";
    //String badLanguageCode = "";
    
    static Set<String> countryCodes = sc.getGoodAvailableCodes("territory");
    
    RowData() {
      
    }
    
    RowData(List<String> row) throws ParseException {
      countryCode = row.get(COUNTRY_CODE);
      if (!countryCodes.contains(countryCode)) {
        System.err.println("WRONG COUNTRY CODE: " + row);
      }
      languagePopulation = parseDecimal(row.get(LANGUAGE_POPULATION));
      String officialStatusString = row.get(OFFICIAL_STATUS).trim().replace(' ', '_');
      if (officialStatusString.equals("national")) {
        officialStatusString = "official";
      } else if (officialStatusString.equals("regional_official")) {
        officialStatusString = "official_regional";
      } else if (officialStatusString.length() == 0 || officialStatusString.equals("uninhabited")) {
        officialStatusString = "unknown";
      }
      try {
        officialStatus = OfficialStatus.valueOf(officialStatusString);
      } catch (RuntimeException e) {
        throw new IllegalArgumentException("Can't interpret offical-status: " + officialStatusString);
      }
      languageCode = row.get(LANGUAGE_CODE);
        if (languageCode.startsWith("*") || languageCode.startsWith("\u00A7")) {
        languageCode = languageCode.substring(1);
      }
      countryPopulation = parseDecimal(row.get(COUNTRY_POPULATION));
      countryGdp = parseDecimal(row.get(COUNTRY_GDP));
      countryLiteracy = parsePercent(row.get(COUNTRY_LITERACY));
      String stringLanguageLiteracy = row.get(LANGUAGE_LITERACY);
      languageLiteracy = stringLanguageLiteracy.length() == 0 
      ? countryLiteracy 
          : parsePercent(stringLanguageLiteracy);
      if (row.size() > COMMENT) {
        comment = row.get(COMMENT);
      }
      if (row.size() > NOTES) {
        notes = row.get(NOTES);
      }
      badLanguageName = row.get(BAD_LANGUAGE_NAME);
    }
    
    double parseDecimal(String numericRepresentation) throws ParseException {
      try {
        //if (numericRepresentation == null || numericRepresentation.length() == 0) return Double.NaN;
        Number result = nf.parse(numericRepresentation);
        //if (result == null) return Double.NaN;
        return result.doubleValue();
      } catch (ParseException e) {
        throw e;
        // (RuntimeException) new IllegalArgumentException("can't parse <" + numericRepresentation + ">").initCause(e);
      }
    }

    double parsePercent(String numericRepresentation) throws ParseException {
      try {
        //if (numericRepresentation == null || numericRepresentation.length() == 0) return Double.NaN;
        Number result = pf.parse(numericRepresentation);
        //if (result == null) return Double.NaN;
        return result.doubleValue();
      } catch (ParseException e) {
        throw e;
        // (RuntimeException) new IllegalArgumentException("can't parse <" + numericRepresentation + ">").initCause(e);
      }
    }

    public double getLanguageLiteratePopulation() {
      return languageLiteracy * languagePopulation;
    }

    /**
     * Get the weighted population
     * @param weightIfNotOfficial
     * @return
     */
    public double getLanguageLiteratePopulation(double weightIfNotOfficial) {
      double result = languageLiteracy * languagePopulation;
      if (!officialStatus.isMajor()) {
        result *= weightIfNotOfficial;
      }
      return result;
    }

    public int compareTo(Object o) {
      RowData that = (RowData)o;
      int result;
      if (0 != (result = GENERAL_COLLATOR.compare(countryCode,that.countryCode))) return result;
      if (languagePopulation > that.languagePopulation) return -1; // descending
      if (languagePopulation < that.languagePopulation) return 1;
      if (0 != (result = GENERAL_COLLATOR.compare(languageCode,that.languageCode))) return result;
      return 0;
    }
    
    public static String toStringHeader() {
      return "countryCode" + "\t" + "countryPopulation" + "\t" + "countryGdp"
      + "\t" + "countryLiteracy"
      + "\t" + "languagePopulation" + "\t" + "languageCode" 
      + "\t" + "writingPopulation"
      ;
    }

    public String toString() {
      return countryCode + "\t" + countryPopulation + "\t" + countryGdp
      + "\t" + countryLiteracy
      + "\t" + languagePopulation + "\t" + languageCode 
      + "\t" + languageLiteracy
      ;
    }
    
    static boolean MARK_OUTPUT = false;
    
    public String getRickLanguageCode() {
      if (languageCode.contains("_")) return languageCode;
        Source source = Iso639Data.getSource(languageCode);
      if (source == null) {
        return "§" + languageCode;
      }
      if (MARK_OUTPUT) {
      if (source == Source.ISO_639_3) {
        return "*" + languageCode;
      }
      }
      return languageCode;
    }
    public String getRickLanguageName() {
      String result = new ULocale(languageCode).getDisplayName();
      if (!result.equals(languageCode)) return getExcelQuote(result);
      Set<String> names = Iso639Data.getNames(languageCode);
      if (names != null && names.size() != 0) {
        if (MARK_OUTPUT) {
          return getExcelQuote("*" + names.iterator().next());
        } else {
        return getExcelQuote(names.iterator().next());
        }
      }
      return getExcelQuote("§" + badLanguageName);
    }

    public String getCountryName() {
      return getExcelQuote(getDisplayCountry(countryCode));
    }

    public String getCountryGdpString() {
      return getExcelQuote(nf.format(countryGdp));
    }

    public String getCountryLiteracyString() {
      return pf2.format(countryLiteracy);
    }

    public String getCountryPopulationString() {
      return getExcelQuote(nf.format(countryPopulation));
    }

    public String getLanguageLiteracyString() {
      return pf2.format(languageLiteracy);
    }

    public String getLanguagePopulationString() {
      return getExcelQuote(nf.format(languagePopulation));
    }

  }
  
  public static String getExcelQuote (String comment) {
    return comment == null || comment.length() == 0 ? "" 
        : comment.contains(",") ?  '"' + comment + '"'
            : comment.contains("\"") ?  '"' + comment.replace("\"", "\"\"") + '"'
            : comment;
  }

  static class RickComparator implements Comparator<RowData> {
    public int compare(RowData me, RowData that) {
        int result;
        if (0 != (result = GENERAL_COLLATOR.compare(me.getCountryName(),that.getCountryName()))) return result;
        if (0 != (result = GENERAL_COLLATOR.compare(me.getRickLanguageName(),that.getRickLanguageName()))) return result;
        return me.compareTo(that);
    }  
  }

  private static void writeTerritoryLanguageData(List<String> failures, Set<RowData> sortedInput) {
    
    System.out.println();
    System.out.println("Territory Language Data");
    System.out.println();
    System.out.println("Possible Failures");
    System.out.println();
   
    String lastCountryCode = "";
    boolean first = true;
    LanguageTagParser ltp = new LanguageTagParser();
    
    Log.println(" <!-- See http://unicode.org/cldr/data/diff/supplemental/territory_language_information.html for more information on territoryInfo. -->");
    Log.println("\t<territoryInfo>");
    
    for (RowData row : sortedInput) {
      String countryCode = row.countryCode;
      
      double countryPopulationRaw = row.countryPopulation;
      long countryPopulation = (long) Utility.roundToDecimals(countryPopulationRaw, 2);
      double languageLiteracy = row.languageLiteracy*100;
      double countryLiteracy = row.countryLiteracy*100;
      
      double countryGDPRaw = row.countryGdp;
      long countryGDP = Math.round(countryGDPRaw/gdpFactor);
      
      String languageCode = row.languageCode;
      
      double languagePopulationRaw = row.languagePopulation;
      long languagePopulation = (long) Utility.roundToDecimals(languagePopulationRaw, 2);
      
      double languagePopulationPercent = Utility.roundToDecimals(Math.min(100, Math.max(0, 
          languagePopulation*100 / (double)countryPopulation)),3);
      
      if (!countryCode.equals(lastCountryCode)) {
        if (first) {
          first = false;
        } else {
          Log.println("\t\t</territory>");
        }
        Log.print("\t\t<territory type=\"" + countryCode + "\""
            + " gdp=\"" + countryGDP + "\""
            + " literacyPercent=\"" + nf.format(countryLiteracy) + "\""
            + " population=\"" + countryPopulation + "\">");
        lastCountryCode = countryCode;
        Log.println("\t<!--" + getDisplayCountry(countryCode) + "-->");
      }
      
      if (languageCode.length() != 0 && languagePopulationPercent > 0.0001
          && (ALLOW_SMALL_NUMBERS || languagePopulationPercent >= 1 || languagePopulationRaw > 100000 || languageCode.equals("haw"))
      ) {
        // add best case
        addBestRegion(languageCode, countryCode, languagePopulationRaw);
        String baseScriptLanguage = ltp.set(languageCode).getLanguageScript();
        if (!baseScriptLanguage.equals(languageCode)) {
          addBestRegion(baseScriptLanguage, countryCode, languagePopulationRaw);
        }
        String baseLanguage = ltp.set(baseScriptLanguage).getLanguage();
        if (!baseLanguage.equals(baseScriptLanguage)) {
          addBestRegion(baseLanguage, countryCode, languagePopulationRaw);
          addBestScript(baseLanguage, ltp.set(languageCode).getScript(), languagePopulationRaw);
        }
        
        Log.print("\t\t\t<languagePopulation type=\"" + languageCode + "\""
            + (languageLiteracy != countryLiteracy ? " writingPercent=\"" + nf.format(languageLiteracy) + "\"" : "")
            + " populationPercent=\"" + nf.format(languagePopulationPercent) + "\""
            + (row.officialStatus != OfficialStatus.unknown ? " officialStatus=\"" + row.officialStatus + "\"" : "")
            + references.addReference(row.comment)
            + "/>");
        Log.println("\t<!--" + getLanguageName(languageCode) + "-->");
      } else {
        failures.add(row + "\tToo few speakers: suspect line.");
      }
      //if (first) {
      if (false) System.out.print(
          "countryCode: " + countryCode + "\t"
          + "countryPopulation: " + countryPopulation + "\t"
          + "countryGDP: " + countryGDP + "\t"
          + "languageCode: " + languageCode + "\t"
          + "languagePopulation: " + languagePopulation + Utility.LINE_SEPARATOR
      );
      //}
    }
    
    Log.println("\t\t</territory>");
    Log.println("\t</territoryInfo>");
  }

  private static String getDisplayCountry(String countryCode) {
    String result = ULocale.getDisplayCountry("und_" + countryCode, ULocale.ENGLISH);
    if (!result.equals(countryCode)) {
      return result;
    }
    result = sc.getData("territory", countryCode);
    if (result != null) {
      return result;
    }
    return countryCode;
    // new ULocale("und-" + countryCode).getDisplayCountry()
  }
  
  private static String getDisplayScript(String scriptCode) {
    String result = ULocale.getDisplayScript("und_" + scriptCode, ULocale.ENGLISH);
    if (!result.equals(scriptCode)) {
      return result;
    }
    result = sc.getData("territory", scriptCode);
    if (result != null) {
      return result;
    }
    return scriptCode;
    // new ULocale("und-" + countryCode).getDisplayCountry()
  }


  private static String getLanguageName(String languageCode) {
    String result = new ULocale(languageCode).getDisplayName();
    if (!result.equals(languageCode)) return result;
    Set<String> names = Iso639Data.getNames(languageCode);
    if (names != null && names.size() != 0) {
        return names.iterator().next();
    }
    return languageCode;
  }
  
  static class References {
    Map<String,Pair<String,String>> Rxxx_to_reference = new TreeMap();
    Map<Pair<String,String>,String> reference_to_Rxxx = new TreeMap();
    Map<String,Pair<String,String>> Rxxx_to_oldReferences = supplementalData.getReferences();
    Map<Pair<String,String>,String> oldReferences_to_Rxxx = new TreeMap();
    {
      for (String Rxxx : Rxxx_to_oldReferences.keySet()) {
        oldReferences_to_Rxxx.put(Rxxx_to_oldReferences.get(Rxxx), Rxxx);
      }
    }
    Matcher URI = Pattern.compile("([a-z]+\\://[\\S]+)\\s?(.*)").matcher("");
    
    static int referenceStart = 1000;
    
    /**
     * Returns " references=\"" + Rxxx + "\"" or "" if there is no reference.
     * @param rawReferenceText
     * @return
     */
    private String addReference(String rawReferenceText) {
      if (rawReferenceText == null || rawReferenceText.length() == 0) return "";
      Pair p;
      if (URI.reset(rawReferenceText).matches()) {
        p = (Pair) new Pair(URI.group(1), URI.group(2) == null || URI.group(2).length() == 0 ? "[missing]" : URI.group(2)).freeze();
      } else {
        p = (Pair) new Pair(null, rawReferenceText).freeze();
      }
      
      String Rxxx = reference_to_Rxxx.get(p);
      if (Rxxx == null) { // add new
        Rxxx = oldReferences_to_Rxxx.get(p);
        if (Rxxx != null) { // if old, just keep number
          p = Rxxx_to_oldReferences.get(Rxxx);
        } else { // find an empty number
          while (true) {
            Rxxx = "R" + (referenceStart++);
            if (Rxxx_to_reference.get(Rxxx) == null && Rxxx_to_oldReferences.get(Rxxx) == null) {
              break;
            }
          }
        }
        // add to new references
        reference_to_Rxxx.put(p, Rxxx);
        Rxxx_to_reference.put(Rxxx, p);
      }
      // references="R034"
      return " references=\"" + Rxxx + "\"";
    }
    
    String getReferenceHTML(String Rxxx) {
      Pair<String,String> p = Rxxx_to_reference.get(Rxxx); // exception if fails.
      String uri = p.getFirst();
      String value = p.getSecond();
      uri = uri == null ? "" : " uri=\"" + TransliteratorUtilities.toHTML.transliterate(uri) + "\"";
      value = value == null ? "[missing]" : TransliteratorUtilities.toHTML.transliterate(value);
      return "\t\t<reference type=\"" + Rxxx + "\"" + uri + ">" + value + "</reference>";
    }
    
    void printReferences() {
      // <reference type="R034" uri="isbn:0-321-18578-1">The Unicode Standard 4.0</reference>
      Log.println("\t<references>");
      for (String Rxxx : Rxxx_to_reference.keySet()) {
        Log.println(getReferenceHTML(Rxxx));
      }
      Log.println("\t</references>");
    }
  }
  
  static References references = new References();
  
  private static Set<RowData> getExcelData(List<String> failures, Map<String,RowData> localeToRowData) throws IOException {
    System.out.println();
    System.out.println("Get Excel Data");
    System.out.println();
    
    LanguageTagParser ltp = new LanguageTagParser();
    
    String dir = Utility.GEN_DIRECTORY + "countryLanguagePopulation/";
    List<List<String>> input = SpreadSheet.convert(Utility.getUTF8Data("country_language_population_raw.txt"));
    
    Set<String> languages = languagesNeeded; // sc.getGoodAvailableCodes("language");
    
    Set<String> territories = new TreeSet(sc.getGoodAvailableCodes("territory"));
    territories.removeAll(supplementalData.getContainers());
    territories.remove("QU");
    territories.remove("QO");
    
    Set<String> countriesNotFound = new TreeSet(territories);
    Set<OfficialStatus> statusFound = new TreeSet();
    Set<String> countriesWithoutOfficial = new TreeSet(territories);
    countriesWithoutOfficial.remove("ZZ");
    
    Set<String> languagesNotFound = new TreeSet(languages);
    Set<RowData> sortedInput = new TreeSet();
    int count = 0;
    for (List<String> row : input) {
      ++count;
      if (count == 1 || row.size() <= COUNTRY_GDP) {
        failures.add(join(row,"\t") + "\tShort row");
        continue;
      }
      try {
        RowData x = new RowData(row);
        if (x.officialStatus == OfficialStatus.de_facto_official || x.officialStatus == OfficialStatus.official || x.countryPopulation < 1000) {
          countriesWithoutOfficial.remove(x.countryCode);
        }
        if (!checkCode("territory", x.countryCode, null)) continue;
        statusFound.add(x.officialStatus);
        countriesNotFound.remove(x.countryCode);
        languagesNotFound.remove(x.languageCode);
        if (x.languageCode.contains("_")) {
          ltp.set(x.languageCode);
          languagesNotFound.remove(ltp.getLanguage());
          if (!checkCode("language", ltp.getLanguage(), null)) continue;
          if (!checkCode("script", ltp.getScript(), null)) continue;
        }
        String locale = x.languageCode + "_" + x.countryCode;
        if (localeToRowData.get(locale) != null) {
          System.out.println("# Warning: duplicate data for: " + x.languageCode + " with " + x.countryCode);
        }
        localeToRowData.put(locale, x);
        sortedInput.add(x);
      } catch (ParseException e) {
        failures.add(join(row,"\t") + "\t" + e.getMessage() + "\t" + join(Arrays.asList(e.getStackTrace()),";\t"));
      }
    }
    System.out.println("Status found: " + Utility.join(statusFound, " | "));
    for (String country : countriesNotFound) {
      RowData x = new RowData();
      x.countryCode=country;
      x.languageCode="und";
      sortedInput.add(x);
    }
    for (String language : languagesNotFound) {
      RowData x = new RowData();
      x.countryCode="ZZ";
      x.languageCode=language;
      sortedInput.add(x);
    }
    // see which countries are missing an official language
    for (RowData row : sortedInput) {
      if (!countriesWithoutOfficial.contains(row.countryCode)) continue;   
      System.out.println("Error: missing official language for " + 
          row.getCountryName()
          + "\t" + row.countryCode);
      countriesWithoutOfficial.remove(row.countryCode);
    }

    // write out file for rick
    PrintWriter log = BagFormatter.openUTF8Writer(dir,"output_for_rick.txt");
    log.println(
        "CName" +
        "\tCCode" +
        "\tCPopulation" +
        "\tCLiteracy" +
        "\tCGdp" +
        "\tOfficialStatus" +
        "\tLanguage" +
        "\tLCode" +
        "\tLPopulation" +
        "\tWritingPop" +
        "\tReferences" +
        "\tNotes"
    );
    RickComparator rickSorting = new RickComparator();
    Set<RowData> rickSorted = new TreeSet(rickSorting);
    rickSorted.addAll(sortedInput);
    
    for (RowData row : rickSorted) {
      log.println(
          row.getCountryName()
          + "\t" + row.countryCode
          + "\t" + row.getCountryPopulationString()
          + "\t" + row.getCountryLiteracyString()
          + "\t" + row.getCountryGdpString()
          + "\t" + row.officialStatus
          + "\t" + row.getRickLanguageName()
          + "\t" + row.getRickLanguageCode()
          + "\t" + row.getLanguagePopulationString()
          + "\t" + row.getLanguageLiteracyString()
          + "\t" + getExcelQuote(row.comment)
          + "\t" + getExcelQuote(row.notes)
      );  
    }
    log.close();
    return sortedInput;
  }
  
  private static Set<String> getCldrParents(Set available) {
    LanguageTagParser ltp2 = new LanguageTagParser();
    Set<String> cldrParents = new TreeSet<String>();
    for (String locale : (Set<String>) available) {
      if (skipLocales.contains(locale)) continue;
      try {
        ltp2.set(locale);
      } catch (RuntimeException e) {
        System.out.println("Skipping CLDR file: " + locale);
        continue;
      }
      String locale2 = ltp2.getLanguageScript();
      if (locale2.equals("sh")) continue;
      //int lastPos = locale.lastIndexOf('_');
      //if (lastPos < 0) continue;
      //String locale2 = locale.substring(0,lastPos);
      cldrParents.add(locale2);
      languageToMaxCountry.put(locale2,null);
    }
    System.out.println("CLDR Parents: " + cldrParents);
    return cldrParents;
  }
  
  private static void showFailures(List<String> failures) {
    if (failures.size() == 0) {
      return;
    }
    System.out.println();
    System.out.println("Failures in Output");
    System.out.println();
    
    System.out.println(RowData.toStringHeader());
    for (String failure : failures) {
      System.out.println(failure);
    }
  }
  
  private static void showContent(Set available) {
    System.out.println();
    System.out.println("CLDR Content");
    System.out.println();
    Set<String> languagesLeft = new TreeSet<String>(defaultContent.keySet());
    languagesLeft.remove("und");
    for (String languageLeft : languagesLeft) {
      Log.println("\t\t<defaultContent type=\"" + languageLeft + "\" content=\"" + defaultContent.get(languageLeft) + "\"/>");
    }
//  Set<String> warnings = new LinkedHashSet<String>();
//  
//  CLDRFile supplemental = cldrFactory.make("supplementalData", true);
//  Comments tempComments = supplemental.getXpath_comments();
//  PrintWriter pw = new PrintWriter(System.out);
//  Comparator attributeOrdering = supplemental.getAttributeComparator();
//  Map defaultSuppressionMap = supplemental.getDefaultSuppressionMap();
//  
//  XPathParts last = new XPathParts(attributeOrdering, defaultSuppressionMap);
//  XPathParts current = new XPathParts(attributeOrdering, defaultSuppressionMap);
//  XPathParts lastFiltered = new XPathParts(attributeOrdering, defaultSuppressionMap);
//  XPathParts currentFiltered = new XPathParts(attributeOrdering, defaultSuppressionMap);
//  
//  Set orderedSet = new TreeSet(supplemental.ldmlComparator);
//  CollectionUtilities.addAll(supplemental.iterator("//supplementalData/languageData/language"), orderedSet);
//  Set<String> languagesLeft = new TreeSet<String>(defaultContent.keySet());
//  
//  for (Iterator it2 = orderedSet.iterator(); it2.hasNext();) {
//  String xpath = (String)it2.next();
//  currentFiltered.set(xpath);
//  current.set(xpath);
//  
//  Map x = current.set(xpath).getAttributes(-1);
//  boolean alt = x.containsKey("alt");
//  String lang = (String) x.get("type");
//  String defaultValue = defaultContent.get(lang);
//  if (alt) {
//  // skip
//  } else if (defaultValue == null) {
//  warnings.add("Missing default value for " + lang);
//  } else if (!defaultValue.equals(lang)) {
//  x.put("defaultContent", defaultValue);
//  languagesLeft.remove(lang);
//  }
//  
//  current.writeDifference(pw, currentFiltered, last, lastFiltered, "", tempComments);
//  // exchange pairs of parts
//  XPathParts temp = current;
//  current = last;
//  last = temp;
//  temp = currentFiltered;
//  currentFiltered = lastFiltered;
//  lastFiltered = temp;
//  }
//  pw.flush();
    
    
//  for (String warning : warnings) {
//  System.out.println(warning);
//  }
    
    
//  for (String localeCode : (Set<String>) available) {
//  if (skipLocales.contains(localeCode)) continue;
//  String resolvedLanguageCode = getFullyResolved(localeCode);
//  // a locale will be empty if its parent has the same resolved code
//  String parent = getProcessedParent(localeCode);
//  String resolvedParent = getFullyResolved(parent);
//  System.out.println(
//  (resolvedLanguageCode.equals(resolvedParent) ? "empty" : "")
//  + "\t" + localeCode 
//  + "\t" + resolvedLanguageCode 
//  + "\t" + parent 
//  + "\t" + ULocale.getDisplayName(localeCode, ULocale.ENGLISH));
//  }
  }
  
  public static String getProcessedParent(String localeCode) {
    if (localeCode == null || localeCode.equals("root")) return null;
    int pos = localeCode.lastIndexOf('_');
    if (pos < 0) return "root";
    LanguageTagParser ltp = new LanguageTagParser();
    String script = ltp.set(localeCode).getScript();
    if (script.length() == 0) {
      return getFullyResolved(localeCode);
    }
    return localeCode.substring(0,pos);
  }
  
  
  private static String getFullyResolved(String languageCode) {
    String result = defaultContent.get(languageCode);
    if (result != null) return result;
    // we missed. Try taking parent and trying again
    int pos = languageCode.length() + 1;
    while (true) {
      pos = languageCode.lastIndexOf('_', pos-1);
      if (pos < 0) {
        return "***" + languageCode;
      }
      result = defaultContent.get(languageCode.substring(0,pos));
      if (result != null) {
        LanguageTagParser ltp = new LanguageTagParser().set(languageCode);
        LanguageTagParser ltp2 = new LanguageTagParser().set(result);
        String region = ltp.getRegion();
        if (region.length() == 0) {
          ltp.setRegion(ltp2.getRegion());
        }
        String script = ltp.getScript();        
        if (script.length() == 0) {
          ltp.setScript(ltp2.getScript());
        }
        return ltp.toString();
      }
    }
  }
  
  static Comparator<Iterable> firstElementComparator = new Comparator<Iterable>() {
    public int compare(Iterable o1, Iterable o2) {
      int result = ((Comparable)o1.iterator().next()).compareTo(((Comparable)o2.iterator().next()));
      assert result != 0;
      return result;
    }  
  };
  
  private static void showDefaults(Set<String> cldrParents, NumberFormat nf, Map<String,String> defaultContent, Map<String, RowData> localeToRowData,
      Set<String> defaultLocaleContent) {
    
    System.out.println();
    System.out.println("Defaults???");
    System.out.println();
    
    Factory cldrFactory = Factory.make(Utility.MAIN_DIRECTORY, ".*");
    Set<String> locales = new TreeSet(cldrFactory.getAvailable());
    LocaleIDParser lidp = new LocaleIDParser();
    
    // add all the combinations of language, script, and territory.
    for (String locale : localeToRowData.keySet()) {
      String baseLanguage = lidp.set(locale).getLanguage();
      if (locales.contains(baseLanguage) && !locales.contains(locale)) {
        locales.add(locale);
        System.out.println("\tadding: " + locale);
      }
    }

    // adding parents
    Set<String> toAdd = new TreeSet();
    while (true) {
      for (String locale : locales) {
        String newguy = lidp.set(locale).getParent();
        if (newguy != null && !locales.contains(newguy) && !toAdd.contains(newguy)) {
          toAdd.add(newguy);
          System.out.println("\tadding parent: " + newguy);
        }
      }
      if (toAdd.size() == 0) {
        break;
      }
      locales.addAll(toAdd);
      toAdd.clear();
    }
    
    // get sets of siblings
    Set<Set<String>> siblingSets  = new TreeSet<Set<String>>(firstElementComparator);
    Set<String> needsADoin = new TreeSet<String>(locales);
    
    Set<String> deprecatedLanguages = new TreeSet();
    deprecatedLanguages.add("sh");
    Set<String> deprecatedRegions = new TreeSet();
    deprecatedRegions.add("YU");
    deprecatedRegions.add("CS");
    deprecatedRegions.add("ZZ");
    
    // first find all the language subtags that have scripts, and those we need to skip. Those are aliased-only
    Set<String> skippingItems = new TreeSet();
    Set<String> hasAScript = new TreeSet();
    Set<LocaleIDParser.Level> languageOnly = EnumSet.of(LocaleIDParser.Level.Language);
    for (String locale : locales) {
      lidp.set(locale);
      if (lidp.getScript().length() != 0) {
        hasAScript.add(lidp.getLanguage());
      }
      Set<LocaleIDParser.Level> levels = lidp.getLevels();
      // must have no variants, must have either script or region, no deprecated elements
      if (levels.contains(LocaleIDParser.Level.Variants) // no variants
          || !(levels.contains(LocaleIDParser.Level.Script) 
                  || levels.contains(LocaleIDParser.Level.Region))
          || deprecatedLanguages.contains(lidp.getLanguage()) 
          || deprecatedRegions.contains(lidp.getRegion())) {
        // skip language-only locales, and ones with variants
        needsADoin.remove(locale);
        skippingItems.add(locale);
        System.out.println("\tremoving: " + locale);
        continue;
      }
    }
    // walk through the locales, getting the ones we care about.
    Map<String,Double> scriptLocaleToLanguageLiteratePopulation = new TreeMap();
    
    for (String locale : new TreeSet<String>(needsADoin)) {
      if (!needsADoin.contains(locale)) continue;
      lidp.set(locale);
      Set<Level> level = lidp.getLevels();
      // skip locales that need scripts and don't have them
      if (!level.contains(LocaleIDParser.Level.Script) // no script
          && hasAScript.contains(lidp.getLanguage())) {
        needsADoin.remove(locale);
        skippingItems.add(locale);
        continue;
      }
      // get siblings
      Set<String> siblingSet = lidp.getSiblings(needsADoin);
      // if it has a script and region
      if (level.contains(LocaleIDParser.Level.Script) && level.contains(LocaleIDParser.Level.Region)) {
        double languageLiteratePopulation = 0;
        for (String localeID2 : siblingSet) {
          RowData rowData = localeToRowData.get(localeID2);
          if (rowData != null) {
            languageLiteratePopulation += rowData.getLanguageLiteratePopulation(NON_OFFICIAL_WEIGHT);
          }
        }
        String parentID = lidp.getParent();
        scriptLocaleToLanguageLiteratePopulation.put(parentID, languageLiteratePopulation);
      }
      
      try {
        siblingSets.add(siblingSet);
      } catch (RuntimeException e) {
        e.printStackTrace();
      }
      needsADoin.removeAll(siblingSet);
    }
    System.out.println("Skipping: " + skippingItems);
    if (needsADoin.size() != 0) {
      System.out.println("Missing: " + needsADoin);
    }
    
    // walk through the data
    Set<String> skippingSingletons = new TreeSet();
    
    Set<String> missingData = new TreeSet();
    for (Set<String> siblingSet : siblingSets) {
      System.out.println("** From siblings: " + siblingSet);
      
      if (false & siblingSet.size() == 1) {
        skippingSingletons.add(siblingSet.iterator().next());
        continue;
      }
      // get best
      double best = Double.NEGATIVE_INFINITY;
      String bestLocale = "???";
      Set<Pair<Double,String>> data = new TreeSet();
      for (String locale : siblingSet) {
        RowData rowData = localeToRowData.get(locale);
        double languageLiteratePopulation = -1;
        if (rowData != null) {
          languageLiteratePopulation = rowData.getLanguageLiteratePopulation(NON_OFFICIAL_WEIGHT);
        } else {
          Double d = scriptLocaleToLanguageLiteratePopulation.get(locale);
          if (d != null) {
            languageLiteratePopulation = d;
          } else {
            missingData.add(locale);
          }
        }
        data.add(new Pair(languageLiteratePopulation,locale));
        if (best < languageLiteratePopulation) {
          best = languageLiteratePopulation;
          bestLocale = locale;
        }
      }
      // show it
      for (Pair<Double,String> datum : data) {
        System.out.format("\tContenders: %s %f (based on literate population)" + Utility.LINE_SEPARATOR, datum.getSecond(), datum.getFirst());
      }
      //System.out.format("\tPicking default content: %s %f (based on literate population)" + Utility.LINE_SEPARATOR, bestLocale, best);
      // Hack to fix English
      // TODO Generalize in the future for other locales with non-primary scripts
      if (bestLocale.startsWith("en_")) {
        defaultLocaleContent.add("en_US");
      } else {
        defaultLocaleContent.add(bestLocale);
      }
    }
    
    System.out.format("Skipping Singletons %s" + Utility.LINE_SEPARATOR, skippingSingletons);
    System.out.format("Missing Data %s" + Utility.LINE_SEPARATOR, missingData);
    
//  LanguageTagParser ltp = new LanguageTagParser();
//  Set<String> warnings = new LinkedHashSet();
//  for (String languageCode : languageToMaxCountry.keySet()) {
//  CodeAndPopulation best = languageToMaxCountry.get(languageCode);
//  String languageSubtag = ltp.set(languageCode).getLanguage();
//  String countryCode = "ZZ";
//  double rawLanguagePopulation = -1;
//  if (best != null) {
//  countryCode = best.code;
//  rawLanguagePopulation = best.population;
//  Set<String> regions = LanguageInfo.INSTANCE.languageToRegions.get(languageSubtag);
//  if (regions == null || !regions.contains(countryCode)) {
//  Set<String> regions2 = LanguageInfo.INSTANCE.languageToRegionsAlt.get(languageSubtag);
//  if (regions2 == null || !regions2.contains(countryCode)) {
//  warnings.add("WARNING: " + languageCode + " => " + countryCode + ", not in " + regions + "/" + regions2);
//  }
//  }
//  }
//  String resolvedLanguageCode = languageCode + "_" + countryCode;
//  ltp.set(languageCode);
//  Set<String> scripts = LanguageInfo.INSTANCE.languageToScripts.get(languageCode);
//  String script = ltp.getScript();
//  if (script.length() == 0) {
//  CodeAndPopulation bestScript = languageToMaxScript.get(languageCode);
//  if (bestScript != null) {
//  script = bestScript.code;
//  if (scripts == null || !scripts.contains(script)) {
//  warnings.add("WARNING: " + languageCode + " => " + script + ", not in " + scripts);
//  }
//  } else {
//  script = "Zzzz";
//  if (scripts == null) {
//  scripts = LanguageInfo.INSTANCE.languageToScriptsAlt.get(languageCode);
//  }
//  if (scripts != null) {
//  script = scripts.iterator().next();
//  if (scripts.size() != 1) {
//  warnings.add("WARNING: " + languageCode + " => " + scripts);
//  }
//  }
//  }
//  if (scripts == null) {
//  warnings.add("Missing scripts for: " + languageCode);
//  } else if (scripts.size() == 1){
//  script = "";
//  }
//  resolvedLanguageCode = languageCode 
//  + (script.length() == 0 ? "" : "_" + script) 
//  + "_" + countryCode;
//  }
//  
//  
//  System.out.println(
//  resolvedLanguageCode
//  + "\t" + languageCode
//  + "\t" + ULocale.getDisplayName(languageCode, ULocale.ENGLISH)
//  + "\t" + countryCode
//  + "\t" + ULocale.getDisplayCountry("und_" + countryCode, ULocale.ENGLISH)
//  + "\t" + nf.format(rawLanguagePopulation)
//  + (cldrParents.contains(languageCode) ? "\tCLDR" : "")
//  );
//  if (languageCode.length() == 0) continue;
//  defaultContent.put(languageCode, resolvedLanguageCode);
//  }
//  for (String warning : warnings) {
//  System.out.println(warning);
//  }
  }

//  private static void printDefaultContent(Set<String> defaultLocaleContent) {
//    String sep = Utility.LINE_SEPARATOR + "\t\t\t";
//    String broken = Utility.breakLines(join(defaultLocaleContent," "), sep, Pattern.compile("(\\S)\\S*").matcher(""), 80);
//    
//    Log.println("\t\t<defaultContent locales=\"" + broken + "\"");
//    Log.println("\t\t/>");
//  }
  
  private static Object getSuppressScript(String languageCode) {
    // TODO Auto-generated method stub
    return null;
  }
  
  public static String join (Collection c, String separator) {
    StringBuffer result = new StringBuffer();
    boolean first = true;
    for (Object x : c) {
      if (first) first = false;
      else result.append(separator);
      result.append(x);
    }
    return result.toString();
  }
  
  private static void addBestRegion(String languageCode, String countryCode, double languagePopulationRaw) {
    addBest(languageCode, languagePopulationRaw, countryCode, languageToMaxCountry);
  }
  
  private static void addBestScript(String languageCode, String scriptCode, double languagePopulationRaw) {
    addBest(languageCode, languagePopulationRaw, scriptCode, languageToMaxScript);
  }
  
  private static void addBest(String languageCode, double languagePopulationRaw, String code, Map<String, CodeAndPopulation> languageToMaxCode) {
    if (languageCode.length() == 0) {
      throw new IllegalArgumentException();
    }
    CodeAndPopulation best = languageToMaxCode.get(languageCode);
    if (best == null) {
      languageToMaxCode.put(languageCode, best = new CodeAndPopulation());
    } else if (best.population >= languagePopulationRaw) {
      return;
    }
    best.population = languagePopulationRaw;
    best.code = code;
  }
  
  static class CodeAndPopulation {
    String code = null;
    double population = Double.NaN;
    public String toString() {
      return "{" + code + "," + population + "}";
    }
  }
  
  static public class GeneralCollator implements Comparator {
    static UTF16.StringComparator cpCompare = new UTF16.StringComparator(true, false,0);
    static RuleBasedCollator UCA = (RuleBasedCollator) Collator
    .getInstance(ULocale.ROOT);
    static {
      UCA.setNumericCollation(true);
    }
    
    public int compare(Object o1, Object o2) {
      if (o1 == null) {
        return o2 == null ? 0 : -1;
      } else if (o2 == null) {
        return 1;
      }
      String s1 = o1.toString();
      String s2 = o2.toString();
      int result = UCA.compare(s1, s2);
      if (result != 0) return result;
      return cpCompare.compare(s1, s2);
    }
  };
  
  public static class InverseComparator implements Comparator {
    private Comparator other;
    
    public InverseComparator() {
      this.other = null;
    }

    public InverseComparator(Comparator other) {
      this.other = other;
    }

    public int compare(Object a, Object b) {
      return other == null 
      ? ((Comparable)b).compareTo(a) 
              : other.compare(b, a);
    }
  }
  
  static Set languagesNeeded = new TreeSet(Arrays.asList("ab ba bh bi bo fj fy gd ha ht ik iu ks ku ky lg mi na nb rm sa sd sg si sm sn su tg tk to tw vo yi za lb dv chr syr kha sco gv".split("\\s")));
  
  static void generateIso639_2Data() {
    for (String languageSubtag : sc.getAvailableCodes("language")) {
      String alpha3 = Iso639Data.toAlpha3(languageSubtag);
      Type type = Iso639Data.getType(languageSubtag);
      Scope scope = Iso639Data.getScope(languageSubtag);
      if (type != null || alpha3 != null || scope != null) {
        Log.println("\t\t<languageCode type=\"" + languageSubtag + "\"" + 
            (alpha3 == null ? "" : " iso639Alpha3=\"" + alpha3 + "\"") +
            (type == null ? "" : " iso639Type=\"" + type + "\"") +
            (scope == null ? "" : " iso639Scope=\"" + scope + "\"") +
        "/>");
      }
      
    }
  }
  
  static Relation<String, BasicLanguageData> language2BasicLanguageData = new Relation(new TreeMap(), TreeSet.class);
  
  static Map<String, Relation<BasicLanguageData.Type,String>> language_status_scripts;
  static Map<Pair<String,String>,String> language_script_references = new TreeMap();

  static void getLanguage2Scripts(Set<RowData> sortedInput) throws IOException {
    language_status_scripts = new TreeMap();
    
//    // get current scripts
//    Relation<String,String> languageToDefaultScript = new Relation(new TreeMap(), TreeSet.class);
//    Relation<String,String> secondaryLanguageToDefaultScript = new Relation(new TreeMap(), TreeSet.class);
//    for (String languageSubtag : language2BasicLanguageData.keySet()) {
//      for (BasicLanguageData item : language2BasicLanguageData.getAll(languageSubtag)) {
//        for (String script : item.getScripts()) {
//          addLanguage2Script(languageSubtag, item.getType(), script);
//        }
//      }
//    }
//    System.out.println("Language 2 scripts: " + language_status_scripts);
    
    // #Lcode LanguageName  Status  Scode ScriptName  References
    List<List<String>> input = SpreadSheet.convert(Utility.getUTF8Data("language_script_raw.txt"));
    // /Users/markdavis/Documents/workspace/cldr-code/java/org/unicode/cldr/util/data/language_script_raw.txt
    System.out.println(Utility.LINE_SEPARATOR + "Problems in language_script_raw.txt" + Utility.LINE_SEPARATOR);
    int count = -1;
    for (List<String> row : input) {
      try {
        if (row.size() == 0) continue;
        ++count;
        String language = row.get(0).trim();
        if (language.length() == 0 || language.startsWith("#")) continue;
        BasicLanguageData.Type status = BasicLanguageData.Type.valueOf(row.get(2));
        String scripts = row.get(3);
        if (!checkCode("language", language, row)) continue;
        for (String script : scripts.split("\\s+")) {
        if (!checkCode("script", script, row)) continue;
        // if the script is not modern, demote
        if (status == BasicLanguageData.Type.primary && !StandardCodes.isScriptModern(script)) {
          System.out.println("# Warning: Should be secondary, script is not modern: " + script + "\t" + ULocale.getDisplayScript("und-" + script, ULocale.ENGLISH));
          status = BasicLanguageData.Type.secondary;
        }
        // if the language is not modern, demote
        if (status == BasicLanguageData.Type.primary && !sc.isModernLanguage(language)) {
          System.out.println("# Warning: Should be secondary, language is not modern: " + language + "\t" + getLanguageName(language));
          status = BasicLanguageData.Type.secondary;
        }

        addLanguage2Script(language, status, script);
        if (row.size() > 5) {
          String reference = row.get(5);
          if (reference != null && reference.length() == 0) {
            language_script_references.put(new Pair(language, script), reference);
          }
        }
        }
      } catch (RuntimeException e) {
        System.err.println(row);
        throw e;
      }
    }
    
    // System.out.println("Language 2 scripts: " + language_status_scripts);
    
    for (String language : sc.getGoodAvailableCodes("language")) {
      Map<String, String> registryData = sc.getLangData("language", language);
      if (registryData != null) {
        String script = registryData.get("Suppress-Script");
        if (script == null) continue;
        // if there is something already there, we have a problem.
        Relation<BasicLanguageData.Type,String> status_scripts = language_status_scripts.get(language);
        if (status_scripts != null) {
          Set<String> secondaryScripts = status_scripts.getAll(BasicLanguageData.Type.primary);
          if (secondaryScripts == null || secondaryScripts.size() == 0 || secondaryScripts.size() == 1 && secondaryScripts.contains(script)) {
            // ok
          } else {
            System.out.println("Conflict with Suppress-Script: " + language + "\t" + script + "\t" + secondaryScripts);
            status_scripts.removeAll(BasicLanguageData.Type.primary); // fix it
          }
        }        
        addLanguage2Script(language, BasicLanguageData.Type.primary, script);
      }
    }
    
    // remove primaries from secondaries
    // check for primaries for scripts
    for (String language : language_status_scripts.keySet()) {
      Relation<BasicLanguageData.Type,String> status_scripts = language_status_scripts.get(language);
      Set<String> secondaryScripts = status_scripts.getAll(BasicLanguageData.Type.secondary);
      if (secondaryScripts == null) continue;
      Set<String> primaryScripts = status_scripts.getAll(BasicLanguageData.Type.primary);
      if (primaryScripts == null) {
//        status_scripts.putAll(BasicLanguageData.Type.primary, secondaryScripts);
//        status_scripts.removeAll(BasicLanguageData.Type.secondary);
        if (sc.isModernLanguage(language)) {
          System.out.println("# Warning: modern language without primary script: " + language + "\t" + getLanguageName(language));
        }
      } else {
        status_scripts.removeAll(BasicLanguageData.Type.secondary, primaryScripts);
      }
    }
    
    // check that every living language in the row data has a script
    Set<String> livingLanguagesWithTerritories = new TreeSet();
    for (RowData rowData : sortedInput) {
      String language = rowData.languageCode;
      if (sc.isModernLanguage(language) && Iso639Data.getSource(language) != Iso639Data.Source.ISO_639_3) {
        livingLanguagesWithTerritories.add(language);
      }
    }
    for (String language : livingLanguagesWithTerritories) {
      Relation<BasicLanguageData.Type,String> status_scripts = language_status_scripts.get(language);
      if (status_scripts != null) {
        Set<String> primaryScripts = status_scripts.getAll(BasicLanguageData.Type.primary);
        if (primaryScripts != null && primaryScripts.size() > 0) {
          continue;
        }
      }
      System.out.println("# Warning: ISO 639-1/2 language in language-territory list without primary script: " + language + "\t" + getLanguageName(language));
    }
    
    // System.out.println("Language 2 scripts: " + language_status_scripts); 
}

  private static boolean checkScript(String script) {
    // TODO Auto-generated method stub
    return false;
  }

  private static boolean checkCode(String type, String code, Object sourceLine) {
    if (sc.getGoodAvailableCodes(type).contains(code)) {
      if (code.equals("no")) {
        System.out.println("Illegitimate Code for " + type + ": " + code + (sourceLine != null ? "\tfrom: " + sourceLine : ""));
        return false;
      }
      return true;
    }
    if (type.equals("language")) {
      // also allow the 639-3 codes that are living individual or macro
      if (Iso639Data.getSource(code) == Iso639Data.Source.ISO_639_3) {
        return true;
      }
//      if (StandardCodes.isModernLanguage(code)) {
//        return true;
//      }
    }
    System.out.println("Illegitimate Code for " + type + ": " + code + (sourceLine != null ? "\tfrom: " + sourceLine : ""));
    return false;
  }

  private static void addLanguage2Script(String language, BasicLanguageData.Type type, String script) {
    Relation<BasicLanguageData.Type,String> status_scripts = language_status_scripts.get(language);
    if (status_scripts == null) language_status_scripts.put(language, status_scripts = new Relation(new TreeMap(), TreeSet.class));
    status_scripts.put(type, script);
  }

  static void addLanguageScriptData() throws IOException {
    // check to make sure that every language subtag is in 639-3
    Set<String> langRegistryCodes = sc.getGoodAvailableCodes("language");
    Set<String> iso639_2_missing = new TreeSet(langRegistryCodes);
    iso639_2_missing.removeAll(Iso639Data.getAvailable());
    iso639_2_missing.remove("root");
    if (iso639_2_missing.size() != 0) {
      System.out.println("Missing Lang/Script data:\t" + iso639_2_missing);
    }
    
    Map<String,String> nameToTerritoryCode = new TreeMap();
    for (String territoryCode : sc.getGoodAvailableCodes("territory")) {
      nameToTerritoryCode.put(sc.getData("territory", territoryCode).toLowerCase(), territoryCode);
    }
    nameToTerritoryCode.put("iran",nameToTerritoryCode.get("iran, islamic republic of")); // 
    
    BasicLanguageData languageData = new BasicLanguageData();
    
    
    BufferedReader in = Utility.getUTF8Data("extraLanguagesAndScripts.txt");
    while (true) {
      String line = in.readLine();
      if (line == null) break;
      String[] parts = line.split("\\t");
      String alpha3 = parts[0];
      alpha3 = stripBrackets(alpha3);
      String languageSubtag = Iso639Data.fromAlpha3(alpha3);
      if (languageSubtag == null) {
        if (langRegistryCodes.contains(alpha3)) {
          languageSubtag = alpha3;
        } else {
          System.out.println("Language subtag <" + alpha3 + "> not found, on line:\t" + line);
          continue;
        }
      }
      String name = parts[1];
      Set<String> names = Iso639Data.getNames(languageSubtag);
      if (names == null) {
        Map<String,String> name2 = sc.getLangData("language", languageSubtag);
        if (name2 != null) {
          String name3 = name2.get("Description");
          if (name3 != null) {
            names = new TreeSet();
            names.add(name3);
          }
        }
      }
//      if (names == null || !names.contains(name)) {
//        System.out.println("Name <" + name + "> for <" + languageSubtag + "> not found in " + names);
//      }
      
      // names all straight, now get scripts and territories
      // [Cyrl]; [Latn]
      Set<String> fullScriptList = sc.getGoodAvailableCodes("script");

     String[] scriptList = parts[2].split("[;,]\\s*");
     Set<String> scripts = new TreeSet();
     Set<String> scriptsAlt = new TreeSet();
     for (String script : scriptList) {
       if (script.length() == 0) continue;
       boolean alt = false;
       if (script.endsWith("*")) {
         alt = true;
         script = script.substring(0,script.length()-1);
       }
       script = stripBrackets(script);
       if (!fullScriptList.contains(script)) {
         System.out.println("Script <" + script + "> for <" + languageSubtag + "> not found in " + fullScriptList);
       } else if (alt) {
         scriptsAlt.add(script);
       } else {
         scripts.add(script);
       }
     }
     // now territories
     Set<String> territories = new TreeSet();
     if (parts.length > 4) {
     String[] territoryList = parts[4].split("\\s*[;,-]\\s*");
     for (String territoryName : territoryList) {
       if (territoryName.equals("ISO/DIS 639") || territoryName.equals("3")) continue;
       String territoryCode = nameToTerritoryCode.get(territoryName.toLowerCase());
       if (territoryCode == null) {
         System.out.println("Territory <" + territoryName + "> for <" + languageSubtag + "> not found in " + nameToTerritoryCode.keySet());
       } else {
         territories.add(territoryCode);
       }
     }
     }
     //     <language type="de" scripts="Latn" territories="IT" alt="secondary"/>
     // we're going to go ahead and set these all to secondary.
     if (scripts.size() != 0) {
       language2BasicLanguageData.put(languageSubtag, new BasicLanguageData().setType(BasicLanguageData.Type.secondary).setScripts(scripts).setTerritories(territories));
     }
     if (scriptsAlt.size() != 0) {
       language2BasicLanguageData.put(languageSubtag, new BasicLanguageData().setType(BasicLanguageData.Type.secondary).setScripts(scriptsAlt).setTerritories(territories));
     }
    }
    in.close();
    
    // add other data
    for (String languageSubtag : supplementalData.getBasicLanguageDataLanguages()) {
      Set<BasicLanguageData> otherData = supplementalData.getBasicLanguageData(languageSubtag);
      language2BasicLanguageData.putAll(languageSubtag, otherData);
    }
  }

  private static void showAllBasicLanguageData(Relation<String, BasicLanguageData> language2basicData, String comment) {
    // now print
    Relation<String,String> primaryCombos = new Relation(new TreeMap(), TreeSet.class);
    Relation<String,String> secondaryCombos = new Relation(new TreeMap(), TreeSet.class);

    Log.println("\t<languageData>" + (comment == null ? "" : " <!-- " + comment + " -->"));

    for (String languageSubtag : language2basicData.keySet()) {
      String duplicate = "";
      // script,territory
      primaryCombos.clear();
      secondaryCombos.clear();
      
      for (BasicLanguageData item : language2basicData.getAll(languageSubtag)) {
        Set<String> scripts = item.getScripts();
        if (scripts.size() == 0) scripts = new TreeSet(Arrays.asList(new String[]{"Zzzz"}));
        for (String script : scripts) {
          Set<String> territories = item.getTerritories();
          if (territories.size() == 0) territories = new TreeSet(Arrays.asList(new String[]{"ZZ"}));
          for (String territory : territories) {
            if (item.getType().equals(BasicLanguageData.Type.primary)) {
              primaryCombos.put(script, territory);
            } else {
              secondaryCombos.put(script, territory);
            }
          }
        }
      }
      secondaryCombos.removeAll(primaryCombos);
      showBasicLanguageData(languageSubtag, primaryCombos, null, BasicLanguageData.Type.primary);
      showBasicLanguageData(languageSubtag, secondaryCombos, primaryCombos.keySet(), BasicLanguageData.Type.secondary);
      //System.out.println(item.toString(languageSubtag) + duplicate);
      //duplicate = " <!-- " + "**" + " -->";
    }
    Log.println("\t</languageData>");
  }

  private static void showBasicLanguageData(String languageSubtag, Relation<String,String> primaryCombos, Set<String> suppressEmptyScripts, BasicLanguageData.Type type) {
    Set<String> scriptsWithSameTerritories = new TreeSet<String>();
    Set<String> lastTerritories = Collections.EMPTY_SET;
    for (String script : primaryCombos.keySet()) {
      Set<String> territories = primaryCombos.getAll(script);
      if (lastTerritories == Collections.EMPTY_SET) {
        // skip first
      } else if (lastTerritories.equals(territories)) {
        scriptsWithSameTerritories.add(script);
      } else {
        showBasicLanguageData2(languageSubtag, scriptsWithSameTerritories, suppressEmptyScripts, lastTerritories, type);
        scriptsWithSameTerritories.clear();
      }
      lastTerritories = territories;
      scriptsWithSameTerritories.add(script);
    }
    showBasicLanguageData2(languageSubtag, scriptsWithSameTerritories, suppressEmptyScripts, lastTerritories, type);
  }

  private static void showBasicLanguageData2(String languageSubtag, Set<String> scripts, Set<String> suppressEmptyScripts, Set<String> territories, BasicLanguageData.Type type) {
    scripts.remove("Zzzz");
    territories.remove("ZZ");
    if (territories.size() == 0 && suppressEmptyScripts != null) {
      scripts.removeAll(suppressEmptyScripts);
    }
    if (scripts.size() == 0 && territories.size() == 0) return;
    Log.println("\t\t<language type=\"" + languageSubtag + "\"" +
        (scripts.size() == 0 ? "" : " scripts=\"" + Utility.join(scripts, " ") + "\"") +
        (territories.size() == 0 ? "" : " territories=\"" + Utility.join(territories, " ") + "\"") + 
        (type == BasicLanguageData.Type.primary ? "" : " alt=\"" + type + "\"") + 
    "/>");
  }
  
  /*
   *      System.out.println(
         "\t\t<language type=\"" + languageSubtag + "\"" +
         " scripts=\"" + Utility.join(scripts," ") + "\"" +
         (territories.size() == 0 ? "" : " territories=\"" + Utility.join(territories," ") + "\"") +
         "/>"
         );

   */

  private static String stripBrackets(String alpha3) {
    if (alpha3.startsWith("[") && alpha3.endsWith("]")) {
      alpha3 = alpha3.substring(1,alpha3.length()-1);
    }
    return alpha3;
  }
}