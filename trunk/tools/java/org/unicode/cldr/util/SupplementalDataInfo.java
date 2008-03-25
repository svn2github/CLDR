package org.unicode.cldr.util;

import org.omg.CORBA.TRANSIENT;
import org.unicode.cldr.util.SupplementalDataInfo.PluralInfo.Count;

import com.ibm.icu.text.DateFormat;
import com.ibm.icu.text.MessageFormat;
import com.ibm.icu.text.PluralRules;
import com.ibm.icu.text.SimpleDateFormat;
import com.ibm.icu.text.UnicodeSet;
import com.ibm.icu.text.UnicodeSetIterator;
import com.ibm.icu.util.Freezable;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * Singleton class to provide API access to supplemental data -- in all the supplemental data files.
 * <p>To create, use SupplementalDataInfo.getInstance
 * <p>To add API for new structure, you will generally:
 * <ul><li>add a Map or Relation as a data member,
 * <li>put a check and handler in MyHandler for the paths that you consume, 
 * <li>make the data member immutable in makeStuffSave, and
 * <li>add a getter for the data member
 * </ul>
 * @author markdavis
 */

public class SupplementalDataInfo {

//TODO add structure for items shown by TestSupplementalData to be missing
  /*[calendarData/calendar, 
   * characters/character-fallback, 
   * measurementData/measurementSystem, measurementData/paperSize, 
   * metadata/attributeOrder, metadata/blocking, metadata/coverageAdditions, metadata/deprecated, metadata/distinguishing, metadata/elementOrder, metadata/serialElements, metadata/skipDefaultLocale, metadata/suppress, metadata/validity, metazoneInfo/timezone, 
   * timezoneData/mapTimezones, 
   * weekData/firstDay, weekData/minDays, weekData/weekendEnd, weekData/weekendStart]
   */
// TODO: verify that we get everything by writing the files solely from the API, and verifying identity.
  
  /**
   * Official status of languages
   */
  public enum OfficialStatus {
    unknown, de_facto_official, official, official_regional, official_minority;

    public String toShortString() {
      switch (this) {
        case unknown: return "U";
        case de_facto_official: return "DO";
        case official: return "O";
        case official_regional: return "OR";
        case official_minority: return "OM";
      }
      throw new IllegalArgumentException("Missing value");
    }

    public boolean isMajor() {
      switch (this) {
        case de_facto_official:
        case official: return true;
        default: return false;
      }
    }
  };

  /**
   * Population data for different languages.
   */
  public static final class PopulationData implements Freezable {
    private double population = Double.NaN;

    private double literatePopulation = Double.NaN;

    private double gdp = Double.NaN;

    private OfficialStatus officialStatus = OfficialStatus.unknown;

    public double getGdp() {
      return gdp;
    }

    public double getLiteratePopulation() {
      return literatePopulation;
    }

    public double getPopulation() {
      return population;
    }

    public PopulationData setGdp(double gdp) {
      if (frozen) {
        throw new UnsupportedOperationException(
                "Attempt to modify frozen object");
      }
      this.gdp = gdp;
      return this;
    }

    public PopulationData setLiteratePopulation(double literatePopulation) {
      if (frozen) {
        throw new UnsupportedOperationException(
        "Attempt to modify frozen object");
      }
      this.literatePopulation = literatePopulation;
      return this;
    }

    public PopulationData setPopulation(double population) {
      if (frozen) {
        throw new UnsupportedOperationException(
        "Attempt to modify frozen object");
      }
      this.population = population;
      return this;
    }

    public PopulationData set(PopulationData other) {
      if (frozen) {
        throw new UnsupportedOperationException(
        "Attempt to modify frozen object");
      }
      if (other == null) {
        population = literatePopulation = gdp = Double.NaN;
      } else {
        population = other.population;
        literatePopulation = other.literatePopulation;
        gdp = other.gdp;
      }
      return this;
    }

    public void add(PopulationData other) {
      if (frozen) {
        throw new UnsupportedOperationException(
        "Attempt to modify frozen object");
      }
      population += other.population;
      literatePopulation += other.literatePopulation;
      gdp += other.gdp;
    }

    public String toString() {
      return MessageFormat
      .format(
              "[pop: {0,number,#,##0},\t lit: {1,number,#,##0.00},\t gdp: {2,number,#,##0},\t status: {3}]",
              new Object[] { population, literatePopulation, gdp, officialStatus});
    }

    private boolean frozen;

    public boolean isFrozen() {
      return frozen;
    }

    public Object freeze() {
      frozen = true;
      return this;
    }

    public Object cloneAsThawed() {
      throw new UnsupportedOperationException("not yet implemented");
    }

    public OfficialStatus getOfficialStatus() {
      return officialStatus;
    }

    public PopulationData setOfficialStatus(OfficialStatus officialStatus) {
      this.officialStatus = officialStatus;
      return this;
    }
  }

  static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");

  /**
   * Simple language/script/region information
   */
  public static class BasicLanguageData implements
  Comparable<BasicLanguageData>, Freezable {
    public enum Type {
      primary, secondary
    };

    private Type type = Type.primary;

    private Set<String> scripts = Collections.EMPTY_SET;

    private Set<String> territories = Collections.EMPTY_SET;

    public Type getType() {
      return type;
    }

    public BasicLanguageData setType(Type type) {
      this.type = type;
      return this;
    }

    public BasicLanguageData setScripts(String scriptTokens) {
      return setScripts(scriptTokens == null ? null : Arrays
              .asList(WHITESPACE_PATTERN.split(scriptTokens)));
    }

    public BasicLanguageData setTerritories(String territoryTokens) {
      return setTerritories(territoryTokens == null ? null : Arrays
              .asList(WHITESPACE_PATTERN.split(territoryTokens)));
    }

    public BasicLanguageData setScripts(Collection<String> scriptTokens) {
      if (frozen) {
        throw new UnsupportedOperationException();
      }
      // TODO add error checking
      scripts = Collections.EMPTY_SET;
      if (scriptTokens != null) {
        for (String script : scriptTokens) {
          addScript(script);
        }
      }
      return this;
    }

    public BasicLanguageData setTerritories(Collection<String> territoryTokens) {
      if (frozen) {
        throw new UnsupportedOperationException();
      }
      territories = Collections.EMPTY_SET;
      if (territoryTokens != null) {
        for (String territory : territoryTokens) {
          addTerritory(territory);
        }
      }
      return this;
    }

    public BasicLanguageData set(BasicLanguageData other) {
      scripts = other.scripts;
      territories = other.territories;
      return this;
    }

    public Set<String> getScripts() {
      return scripts;
    }

    public Set<String> getTerritories() {
      return territories;
    }

    public String toString(String languageSubtag) {
      if (scripts.size() == 0 && territories.size() == 0)
        return "";
      return "\t\t<language type=\""
      + languageSubtag
      + "\""
      + (scripts.size() == 0 ? "" : " scripts=\""
        + Utility.join(scripts, " ") + "\"")
        + (territories.size() == 0 ? "" : " territories=\""
          + Utility.join(territories, " ") + "\"")
          + (type == Type.primary ? "" : " alt=\"" + type + "\"") + "/>";
    }

    public int compareTo(BasicLanguageData o) {
      int result;
      if (0 != (result = type.compareTo(o.type)))
        return result;
      if (0 != (result = Utility.compare(scripts, o.scripts)))
        return result;
      if (0 != (result = Utility.compare(territories, o.territories)))
        return result;
      return 0;
    }

    public BasicLanguageData addScript(String script) {
      // simple error checking
      if (script.length() != 4) {
        throw new IllegalArgumentException("Illegal Script: " + script);
      }
      if (scripts == Collections.EMPTY_SET) {
        scripts = new TreeSet();
      }
      scripts.add(script);
      return this;
    }

    public BasicLanguageData addTerritory(String territory) {
      // simple error checking
      if (territory.length() != 2) {
        throw new IllegalArgumentException("Illegal Territory: " + territory);
      }
      if (territories == Collections.EMPTY_SET) {
        territories = new TreeSet();
      }
      territories.add(territory);
      return this;
    }

    boolean frozen = false;

    public boolean isFrozen() {
      // TODO Auto-generated method stub
      return frozen;
    }

    public Object freeze() {
      frozen = true;
      if (scripts != Collections.EMPTY_SET) {
        scripts = Collections.unmodifiableSet(scripts);
      }
      if (territories != Collections.EMPTY_SET) {
        territories = Collections.unmodifiableSet(territories);
      }
      return this;
    }

    public Object cloneAsThawed() {
      throw new UnsupportedOperationException();
    }

    public void addScripts(Set<String> scripts2) {
      for (String script : scripts2) {
        addScript(script);
      }
    }
  }

  /**
   * Information about currency digits and rounding.
   */
  public static class CurrencyNumberInfo {
    int digits;
    int rounding;
    double roundingIncrement;

    public int getDigits() {
      return digits;
    }
    public int getRounding() {
      return rounding;
    }
    public double getRoundingIncrement() {
      return roundingIncrement;
    }
    public CurrencyNumberInfo(int digits, int rounding) {
      this.digits = digits;
      this.rounding = rounding;
      roundingIncrement = rounding * Math.pow(10.0, -digits);
    }
  }

  /**
   * Information about when currencies are in use in territories
   */
  public static class CurrencyDateInfo implements Comparable<CurrencyDateInfo> {
    public static final Date END_OF_TIME = new Date(Long.MAX_VALUE);
    public static final Date START_OF_TIME = new Date(Long.MIN_VALUE);
    private static final DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");

    private String currency;
    private Date start;
    private Date end;
    private String errors = "";

    public CurrencyDateInfo(String currency, String startDate, String endDate) {
      this.currency = currency;
      start = parseDate(startDate, START_OF_TIME);
      end = parseDate(endDate, END_OF_TIME);
    }

    static DateFormat[] simpleFormats = { 
      new SimpleDateFormat("yyyy-MM-dd"),
      new SimpleDateFormat("yyyy-MM"), 
      new SimpleDateFormat("yyyy"), };

    Date parseDate(String dateString, Date defaultDate) {
      if (dateString == null) {
        return defaultDate;
      }
      ParseException e2 = null;
      for (int i = 0; i < simpleFormats.length; ++i) {
        try {
          Date result = simpleFormats[i].parse(dateString);
          return result;
        } catch (ParseException e) {
          if (i == 0) {
            errors += dateString + " ";
          }
          if (e2 == null) {
            e2 = e;
          }
        }
      }
      throw (IllegalArgumentException)new IllegalArgumentException().initCause(e2);  
    }

    public String getCurrency() {
      return currency;
    }

    public Date getStart() {
      return start;
    }

    public Date getEnd() {
      return end;
    }

    public String getErrors() {
      return errors;
    }

    public int compareTo(CurrencyDateInfo o) {
      int result = start.getDate() - o.start.getDate();
      if (result != 0) return result;
      result = end.getDate() - o.end.getDate();
      if (result != 0) return result;
      return currency.compareTo(o.currency);
    }

    public String toString() {
      return "{" + formatDate(start) + ", " + formatDate(end) + ", " + currency + "}";
    }

    public static String formatDate(Date date) {
      if (date.equals(START_OF_TIME)) return "-∞";
      if (date.equals(END_OF_TIME)) return "∞";
      return dateFormat.format(date);
    }
  }

  private Map<String, PopulationData> territoryToPopulationData = new TreeMap();

  private Map<String, Map<String, PopulationData>> territoryToLanguageToPopulationData = new TreeMap();

  private Map<String, PopulationData> languageToPopulation = new TreeMap();

  private Map<String, PopulationData> baseLanguageToPopulation = new TreeMap();

  private Relation<String, String> languageToScriptVariants = new Relation(new TreeMap(), TreeSet.class);

  private Relation<String, String> languageToTerritories = new Relation(new TreeMap(), LinkedHashSet.class);

  transient private Relation<String, Pair<Boolean, Pair<Integer, String>>> languageToTerritories2 = new Relation(new TreeMap(), TreeSet.class);

  private Relation<String, BasicLanguageData> languageToBasicLanguageData = new Relation(new TreeMap(), TreeSet.class);

  // private Map<String, BasicLanguageData> languageToBasicLanguageData2 = new
  // TreeMap();

  // Relation(new TreeMap(), TreeSet.class, null);

  private Set<String> allLanguages = new TreeSet();

  private Relation<String, String> containment = new Relation(new TreeMap(),
          TreeSet.class);

  private Map<String, CurrencyNumberInfo> currencyToCurrencyNumberInfo = new TreeMap();

  private Relation<String, CurrencyDateInfo> territoryToCurrencyDateInfo = new Relation(new TreeMap(), LinkedHashSet.class);

  private Set<String> multizone = new TreeSet();

  private Map<String, String> zone_territory = new TreeMap();

  private Relation<String, String> zone_aliases = new Relation(new TreeMap(),
          LinkedHashSet.class);

  private  Map<String, Map<String,String>> metazoneToRegionToZone = new HashMap<String, Map<String,String>>();

  private Map<String, String> alias_zone = new TreeMap();

  public Relation<String, Integer> numericTerritoryMapping = new Relation(new HashMap(), HashSet.class);

  public Relation<String, String> alpha3TerritoryMapping = new Relation(new HashMap(), HashSet.class);

  static Map<String, SupplementalDataInfo> directory_instance = new HashMap();

  public Map<String, Map<String,List<String>>> typeToTagToReplacement = new TreeMap<String, Map<String,List<String>>>();


  public Relation<String, String> getAlpha3TerritoryMapping() {
    return alpha3TerritoryMapping;
  }

  public Relation<String, Integer> getNumericTerritoryMapping() {
    return numericTerritoryMapping;
  }

  /**
   * Returns type -> tag -> replacement, like "language" -> "sh" -> "sr_Latn"
   * @return
   */
  public Map<String, Map<String,List<String>>> getLocaleAliasInfo() {
    return typeToTagToReplacement;
  }

  public static SupplementalDataInfo getInstance(File supplementalDirectory)  {
    try {
      return getInstance(supplementalDirectory.getCanonicalPath());
    } catch (IOException e) {
      throw (IllegalArgumentException) new IllegalArgumentException()
      .initCause(e);
    }
  }
  public static SupplementalDataInfo getInstance(String supplementalDirectory) {
    synchronized (SupplementalDataInfo.class) {
      SupplementalDataInfo instance = directory_instance
      .get(supplementalDirectory);
      if (instance != null) {
        return instance;
      }
      // canonicalize name & try again
      String canonicalpath;
      try {
        canonicalpath = new File(supplementalDirectory).getCanonicalPath();
      } catch (IOException e) {
        throw (IllegalArgumentException) new IllegalArgumentException()
        .initCause(e);
      }
      if (!canonicalpath.equals(supplementalDirectory)) {
        instance = directory_instance.get(canonicalpath);
        if (instance != null) {
          directory_instance.put(supplementalDirectory, instance);
          return instance;
        }
      }
      instance = new SupplementalDataInfo();
      MyHandler myHandler = instance.new MyHandler();
      XMLFileReader xfr = new XMLFileReader().setHandler(myHandler);
      File files[] = new File(canonicalpath).listFiles();
      if(files==null) {
        throw new InternalError("Could not list XML Files from " + canonicalpath);
      }
      for (File file : files) {
        String name = file.toString();
        if (!name.endsWith(".xml")) continue;
        xfr.read(name, -1, true);
      }

      //xfr = new XMLFileReader().setHandler(instance.new MyHandler());
      //.xfr.read(canonicalpath + "/supplementalMetadata.xml", -1, true);


      instance.makeStuffSafe();
      // cache
      directory_instance.put(supplementalDirectory, instance);
      if (!canonicalpath.equals(supplementalDirectory)) {
        directory_instance.put(canonicalpath, instance);
      }
      return instance;
    }
  }

  private SupplementalDataInfo() {
  }; // hide

  private void makeStuffSafe() {
    // now make stuff safe
    allLanguages.addAll(languageToPopulation.keySet());
    allLanguages.addAll(baseLanguageToPopulation.keySet());
    allLanguages = Collections.unmodifiableSet(allLanguages);
    skippedElements = Collections.unmodifiableSet(skippedElements);
    multizone = Collections.unmodifiableSet(multizone);
    zone_territory = Collections.unmodifiableMap(zone_territory);
    alias_zone = Collections.unmodifiableMap(alias_zone);
    references = Collections.unmodifiableMap(references);
    likelySubtags = Collections.unmodifiableMap(likelySubtags);
    currencyToCurrencyNumberInfo = Collections.unmodifiableMap(currencyToCurrencyNumberInfo);
    territoryToCurrencyDateInfo.freeze();

    metazoneToRegionToZone = Utility.protectCollection(metazoneToRegionToZone);
    typeToTagToReplacement = Utility.protectCollection(typeToTagToReplacement);

    containment.freeze();
    languageToBasicLanguageData.freeze();
    for (String language : languageToTerritories2.keySet()) {
      for (Pair<Boolean, Pair<Integer, String>> pair : languageToTerritories2.getAll(language)) {
        languageToTerritories.put(language, pair.getSecond().getSecond());
      }
    }
    languageToTerritories2 = null; // free up the memory.
    languageToTerritories.freeze();
    zone_aliases.freeze();
    languageToScriptVariants.freeze();

    numericTerritoryMapping.freeze();
    alpha3TerritoryMapping.freeze();

    // freeze contents
    for (String language : languageToPopulation.keySet()) {
      languageToPopulation.get(language).freeze();
    }
    for (String language : baseLanguageToPopulation.keySet()) {
      baseLanguageToPopulation.get(language).freeze();
    }
    for (String territory : territoryToPopulationData.keySet()) {
      territoryToPopulationData.get(territory).freeze();
    }
    for (String territory : territoryToLanguageToPopulationData.keySet()) {
      Map<String, PopulationData> languageToPopulationDataTemp = territoryToLanguageToPopulationData
      .get(territory);
      for (String language : languageToPopulationDataTemp.keySet()) {
        languageToPopulationDataTemp.get(language).freeze();
      }
    }
    addPluralInfo();
    localeToPluralInfo = Collections.unmodifiableMap(localeToPluralInfo);
  }

//private Map<String, Map<String, String>> makeUnmodifiable(Map<String, Map<String, String>> metazoneToRegionToZone) {
//Map<String, Map<String, String>> temp = metazoneToRegionToZone;
//for (String mzone : metazoneToRegionToZone.keySet()) {
//temp.put(mzone, Collections.unmodifiableMap(metazoneToRegionToZone.get(mzone)));
//}
//return Collections.unmodifiableMap(temp);
//}

  /**
   * Core function used to process each of the paths, and add the data to the appropriate data member.
   */
  class MyHandler extends XMLFileReader.SimpleHandler {
    private static final double MAX_POPULATION = 3000000000.0;

    XPathParts parts = new XPathParts();

    LanguageTagParser languageTagParser = new LanguageTagParser();

    public void handlePathValue(String path, String value) {
      try {
        parts.set(path);
        String level1 = parts.getElement(1);
        String level2 = parts.size() < 3 ? null : parts.getElement(2);
        if (level1.equals("generation") || level1.equals("version")) {
          // skip
          return;
        }

        // copy the rest from ShowLanguages later
        if (level1.equals("territoryInfo")) {
          if (handleTerritoryInfo()) {
            return;
          }
        }
        if (level1.equals("languageData")) {
          handleLanguageData();
          return;
        }
        if (level1.equals("territoryContainment")) {
          handleTerritoryContainment();
          return;
        }
        if (level1.equals("currencyData")) {
          if (handleCurrencyData(level2)) {
            return;
          }
        }
        if (level1.equals("timezoneData")) {
          if (handleTimezoneData(level2)) {
            return;
          }
        }

        if (level1.equals("plurals")) {
          addPluralPath(parts, value);
          return;
        }

        if (level1.equals("references")) {
          String type = parts.getAttributeValue(-1, "type");
          String uri = parts.getAttributeValue(-1, "uri");
          references.put(type, (Pair)new Pair(uri, value).freeze());
          return;
        }

        if (level1.equals("likelySubtags")) {
          handleLikelySubtags();
          return;
        }

        if (level1.equals("metadata")) {
          if (handleMetadata(level2)) {
            return;
          }
        }

        if (level1.equals("codeMappings")) {
          if (handleCodeMappings(level2)) {
            return;
          }
        }

        // capture elements we didn't look at, since we should cover everything.
        // this helps for updates

        final String skipKey = level1 + (level2 == null ? "" : "/" + level2);
        if (!skippedElements.contains(skipKey)) {
          skippedElements.add(skipKey);
        }
        // System.out.println("Skipped Element: " + path);
      } catch (Exception e) {
        throw (IllegalArgumentException) new IllegalArgumentException("path: "
                + path + ",\tvalue: " + value).initCause(e);
      }
    }

    private boolean handleCodeMappings(String level2) {
      if (level2.equals("territoryCodes")) {
        // <territoryCodes type="VU" numeric="548" alpha3="VUT"/>
        String type = parts.getAttributeValue(-1, "type");
        numericTerritoryMapping.put(type, Integer.parseInt(parts.getAttributeValue(-1, "numeric")));
        alpha3TerritoryMapping.put(type, parts.getAttributeValue(-1, "alpha3"));
        return true;
      }
      return false;
    }

    private void handleLikelySubtags() {
      String from = parts.getAttributeValue(-1, "from");
      String to = parts.getAttributeValue(-1, "to");
      likelySubtags.put(from, to);
    }

    private boolean handleTimezoneData(String level2) {
      if (level2.equals("zoneFormatting")) {
        handleZoneFormatting();
        return true;
      }

      /*
       * <mapTimezones type="metazones">
       *  <mapZone other="Acre"  territory="001" type="America/Rio_Branco"/>
       */
      if (level2.equals("mapTimezones") && "metazones".equals(parts.getAttributeValue(2,"type"))) {
        String mzone = parts.getAttributeValue(3,"other");
        String region = parts.getAttributeValue(3,"territory");
        String zone = parts.getAttributeValue(3,"type");
        Map<String, String> regionToZone = metazoneToRegionToZone.get(mzone);
        if (regionToZone == null) metazoneToRegionToZone.put(mzone, regionToZone = new HashMap<String,String>());
        regionToZone.put(region, zone);
        return true;
      }

      // <mapTimezones type="windows"> <mapZone other="Dateline"
      // type="Etc/GMT+12"/> <!-- S (GMT-12:00) International Date Line
      // West-->
      return false;
    }

    private boolean handleMetadata(String level2) {
      if (parts.contains("defaultContent")) {
        String defContent = parts.getAttributeValue(-1, "locales").trim();
        String [] defLocales = defContent.split("\\s+");
        defaultContentLocales = Collections.unmodifiableSet(new TreeSet<String>(Arrays.asList(defLocales)));
        return true;
      }
      if (level2.equals("alias")) {
//      <alias>
//      <!-- grandfathered 3066 codes -->
//      <languageAlias type="art-lojban" replacement="jbo"/> <!-- Lojban -->
        String level3 = parts.getElement(3);
        if (!level3.endsWith("Alias")) {
          throw new IllegalArgumentException();
        }
        level3 = level3.substring(0,level3.length() - "Alias".length());
        Map<String, List<String>> tagToReplacement = typeToTagToReplacement.get(level3);
        if (tagToReplacement == null) {
          typeToTagToReplacement.put(level3, tagToReplacement = new TreeMap<String, List<String>>());
        }
        final String replacement = parts.getAttributeValue(3,"replacement");
        tagToReplacement.put(parts.getAttributeValue(3,"type").replace("-","_"), replacement == null ? null : Arrays.asList(replacement.replace("-","_").split("\\s+")));
        return true;
      }
      return false;
    }

    private boolean handleTerritoryInfo() {

      // <territoryInfo>
      // <territory type="AD" gdp="1840000000" literacyPercent="100"
      // population="66000"> <!--Andorra-->
      // <languagePopulation type="ca" populationPercent="50"/>
      // <!--Catalan-->

      Map<String, String> territoryAttributes = parts.getAttributes(2);
      String territory = territoryAttributes.get("type");
      double territoryPopulation = parseDouble(territoryAttributes.get("population"));
      if (failsRangeCheck("population", territoryPopulation, 0, MAX_POPULATION)) {
        return true;
      }

      double territoryLiteracyPercent = parseDouble(territoryAttributes.get("literacyPercent"));
      double territoryGdp = parseDouble(territoryAttributes.get("gdp"));
      if (territoryToPopulationData.get(territory) == null) {
        territoryToPopulationData.put(territory, new PopulationData()
        .setPopulation(territoryPopulation)
        .setLiteratePopulation(territoryLiteracyPercent * territoryPopulation / 100)
        .setGdp(territoryGdp));
      }
      if (parts.size() > 3) {

        Map<String, String> languageInTerritoryAttributes = parts
        .getAttributes(3);
        String language = languageInTerritoryAttributes.get("type");
        double languageLiteracyPercent = parseDouble(languageInTerritoryAttributes.get("literacyPercent"));
        if (Double.isNaN(languageLiteracyPercent))
          languageLiteracyPercent = territoryLiteracyPercent;
        double languagePopulationPercent = parseDouble(languageInTerritoryAttributes.get("populationPercent"));
        double languagePopulation = languagePopulationPercent * territoryPopulation / 100;
        //double languageGdp = languagePopulationPercent * territoryGdp;

        // store
        Map<String, PopulationData> territoryLanguageToPopulation = territoryToLanguageToPopulationData
        .get(territory);
        if (territoryLanguageToPopulation == null) {
          territoryToLanguageToPopulationData.put(territory,
                  territoryLanguageToPopulation = new TreeMap());
        }
        OfficialStatus officialStatus = OfficialStatus.unknown;
        String officialStatusString = languageInTerritoryAttributes.get("officialStatus");
        if (officialStatusString != null) officialStatus = OfficialStatus.valueOf(officialStatusString);

        PopulationData newData = new PopulationData()
        .setPopulation(languagePopulation)
        .setLiteratePopulation(languageLiteracyPercent * languagePopulation / 100)
        .setOfficialStatus(officialStatus)
        //.setGdp(languageGdp)
        ;
        newData.freeze();
        if (territoryLanguageToPopulation.get(language) != null) {
          System.out
          .println("Internal Problem in supplementalData: multiple data items for "
                  + language + ", " + territory + "\tSkipping " + newData);
          return true;
        }

        territoryLanguageToPopulation.put(language, newData);
        // add the language, using the Pair fields to get the ordering right
        languageToTerritories2.put(language, new Pair(newData.getOfficialStatus().isMajor() ? 0 : 1, new Pair(-newData.getLiteratePopulation(), territory)));

        // now collect data for languages globally
        PopulationData data = languageToPopulation.get(language);
        if (data == null) {
          languageToPopulation.put(language, data = new PopulationData().set(newData));
        } else {
          data.add(newData);
        }
        if (false && language.equals("en")) {
          System.out.println(territory + "\tnewData:\t" + newData + "\tdata:\t" + data);   
        }
        String baseLanguage = languageTagParser.set(language).getLanguage();
        if (!baseLanguage.equals(language)) {
          languageToScriptVariants.put(baseLanguage,language);

          data = baseLanguageToPopulation.get(baseLanguage);
          if (data == null)
            baseLanguageToPopulation.put(baseLanguage,
                    data = new PopulationData());
          data.add(newData);
        }
      }
      return true;
    }

    private boolean handleCurrencyData(String level2) {
      if (level2.equals("fractions")) {
        // <info iso4217="ADP" digits="0" rounding="0"/>
        currencyToCurrencyNumberInfo.put(parts.getAttributeValue(3, "iso4217"), 
                new CurrencyNumberInfo(Integer.parseInt(parts.getAttributeValue(3, "digits")), 
                        Integer.parseInt(parts.getAttributeValue(3, "rounding"))));
        return true;
      }
      /*
       * <region iso3166="AD">
        <currency iso4217="EUR" from="1999-01-01"/>
        <currency iso4217="ESP" from="1873" to="2002-02-28"/>
       */
      if (level2.equals("region")) {
        territoryToCurrencyDateInfo.put(parts.getAttributeValue(2, "iso3166"), 
                new CurrencyDateInfo(parts.getAttributeValue(3, "iso4217"),
                        parts.getAttributeValue(3, "from"),
                        parts.getAttributeValue(3, "to")
                ));
        return true;
      }

      return false;
    }

    private void handleZoneFormatting() {
      // <zoneFormatting multizone="001 AQ AR AU BR CA CD CL CN EC ES FM GL
      // ID KI KZ MH MN MX MY NZ PF PT RU SJ UA UM US UZ"
      // tzidVersion="2007c">
      // <zoneItem type="Africa/Abidjan" territory="CI"/>
      // <zoneItem type="Africa/Asmera" territory="ER"
      // aliases="Africa/Asmara"/>
      if (multizone.size() == 0) {
        multizone.addAll(Arrays.asList(parts.getAttributeValue(2,
        "multizone").trim().split("\\s+")));
      }
      String zone = parts.getAttributeValue(3, "type");
      String territory = parts.getAttributeValue(3, "territory");
      String aliases = parts.getAttributeValue(3, "aliases");
      if (territory != null) {
        zone_territory.put(zone, territory);
      } else {
        throw new IllegalArgumentException("Problem in data");
      }
      // include the item itself
      Collection<String> aliasArray = new LinkedHashSet<String>();
      //aliasArray.add(zone);
      if (aliases != null) {
        aliasArray.addAll(Arrays.asList(aliases.split("\\s+")));
      }
      for (String alias : aliasArray) {
        alias_zone.put(alias, zone);
        zone_aliases.put(zone, alias);
      }
    }

    private void handleTerritoryContainment() {
      // <group type="001" contains="002 009 019 142 150"/>
      containment.putAll(parts.getAttributeValue(-1, "type"), Arrays
              .asList(parts.getAttributeValue(-1, "contains").split("\\s+")));
    }

    private void handleLanguageData() {
      // <languageData>
      // <language type="aa" scripts="Latn" territories="DJ ER ET"/> <!--
      // Reflecting submitted data, cldrbug #1013 -->
      // <language type="ab" scripts="Cyrl" territories="GE"
      // alt="secondary"/>
      String language = (String) parts.getAttributeValue(2, "type");
      BasicLanguageData languageData = new BasicLanguageData();
      languageData
      .setType(parts.getAttributeValue(2, "alt") == null ? BasicLanguageData.Type.primary
              : BasicLanguageData.Type.secondary);
      languageData.setScripts(parts.getAttributeValue(2, "scripts"))
      .setTerritories(parts.getAttributeValue(2, "territories"));
      languageToBasicLanguageData.put(language, languageData);
    }

    private boolean failsRangeCheck(String path, double input, double min, double max) {
      if (input >= min && input <= max) {
        return false;
      }
      System.out
      .println("Internal Problem in supplementalData: range check fails for "
              + input + ", min: " + min + ", max:" + max + "\t" + path);

      return false;
    }

    private double parseDouble(String literacyString) {
      return literacyString == null ? Double.NaN : Double
              .parseDouble(literacyString);
    }
  }

  Set<String> skippedElements = new TreeSet();

  private Map<String, Pair<String, String>> references = new TreeMap();
  private Map<String, String> likelySubtags = new TreeMap();

  private Set<String> defaultContentLocales;

  /**
   * Get the population data for a language. Warning: if the language has script variants, cycle on those variants.
   * 
   * @param language
   * @param output
   * @return
   */
  public PopulationData getLanguagePopulationData(String language) {
    return languageToPopulation.get(language);
  }

  public Set<String> getLanguages() {
    return allLanguages;
  }

  public Set<String> getTerritoryToLanguages(String territory) {
    Map<String, PopulationData> result = territoryToLanguageToPopulationData
    .get(territory);
    if (result == null) {
      return Collections.EMPTY_SET;
    }
    return result.keySet();
  }

  public PopulationData getLanguageAndTerritoryPopulationData(String language,
          String territory) {
    Map<String, PopulationData> result = territoryToLanguageToPopulationData
    .get(territory);
    if (result == null) {
      return null;
    }
    return result.get(language);
  }

  public Set<String> getTerritoriesWithPopulationData() {
    return territoryToLanguageToPopulationData.keySet();
  }

  public Set<String> getLanguagesForTerritoryWithPopulationData(String territory) {
    return territoryToLanguageToPopulationData.get(territory).keySet();
  }

  public Set<BasicLanguageData> getBasicLanguageData(String language) {
    return languageToBasicLanguageData.getAll(language);
  }

  public Set<String> getBasicLanguageDataLanguages() {
    return languageToBasicLanguageData.keySet();
  }

  public Set<String> getContained(String territoryCode) {
    return containment.getAll(territoryCode);
  }

  public Set<String> getContainers() {
    return containment.keySet();
  }

  public Set<String> getSkippedElements() {
    return skippedElements;
  }

  public Set<String> getZone_aliases(String zone) {
    Set<String> result = zone_aliases.getAll(zone);
    if (result == null) {
      return Collections.EMPTY_SET;
    }
    return result;
  }

  public String getZone_territory(String zone) {
    return zone_territory.get(zone);
  }

  public Set<String> getCanonicalZones() {
    return zone_territory.keySet();
  }

  /**
   * Return the multizone countries (should change name).
   * @return
   */
  public Set<String> getMultizones() {
    // TODO Auto-generated method stub
    return multizone;
  }

  private Set<String> singleRegionZones;

  public Set<String> getSingleRegionZones() {
    synchronized (this) {
      if (singleRegionZones == null) {
        singleRegionZones = new HashSet<String>();
        SupplementalDataInfo supplementalData = this; // TODO: this?
        Set<String> multizoneCountries = supplementalData.getMultizones();
        for (String zone : supplementalData.getCanonicalZones()) {
          String region = supplementalData.getZone_territory(zone);
          if (!multizoneCountries.contains(region) || zone.startsWith("Etc/")) {
            singleRegionZones.add(zone);
          }
        }
        singleRegionZones.remove("Etc/Unknown"); // remove special case
        singleRegionZones = Collections.unmodifiableSet(singleRegionZones);
      }
    }
    return singleRegionZones;
  }

  public Set<String> getTerritoriesForPopulationData(String language) {
    return languageToTerritories.getAll(language);
  }

  public Set<String> getLanguagesForTerritoriesPopulationData() {
    return languageToTerritories.keySet();
  }

  /**
   * Return the canonicalized zone, or null if there is none.
   * 
   * @param alias
   * @return
   */
  public Set<String> getDefaultContentLocales() {
    return defaultContentLocales;
  }

  /**
   * Return the canonicalized zone, or null if there is none.
   * 
   * @param alias
   * @return
   */
  public String getZoneFromAlias(String alias) {
    String zone = alias_zone.get(alias);
    if (zone != null)
      return zone;
    if (zone_territory.get(alias) != null)
      return alias;
    return null;
  }

  public boolean isCanonicalZone(String alias) {
    return zone_territory.get(alias) != null;
  }

  /**
   * Return the approximate economic weight of this language, computed by taking
   * all of the languages in each territory, looking at the literate population
   * and dividing up the GDP of the territory (in PPP) according to the
   * proportion that language has of the total. This is only an approximation,
   * since the language information is not complete, languages may overlap
   * (bilingual speakers), the literacy figures may be estimated, and literacy
   * is only a rough proxy for weight of each language in the economy of the
   * territory.
   * 
   * @param language
   * @return
   */
  public double getApproximateEconomicWeight(String targetLanguage) {
    double weight = 0;
    Set<String> territories = getTerritoriesForPopulationData(targetLanguage);
    if (territories == null) return weight;
    for (String territory : territories) {
      Set<String> languagesInTerritory = getTerritoryToLanguages(territory);
      double totalLiteratePopulation = 0;
      double targetLiteratePopulation = 0;
      for (String language : languagesInTerritory) {
        PopulationData populationData = getLanguageAndTerritoryPopulationData(
                language, territory);
        totalLiteratePopulation += populationData.getLiteratePopulation();
        if (language.equals(targetLanguage)) {
          targetLiteratePopulation = populationData.getLiteratePopulation();
        }
      }
      PopulationData territoryPopulationData = getPopulationDataForTerritory(territory);
      weight += territoryPopulationData.getGdp() * targetLiteratePopulation
      / totalLiteratePopulation;
    }
    return weight;
  }

  public PopulationData getPopulationDataForTerritory(String territory) {
    return territoryToPopulationData.get(territory);
  }

  public Set<String> getScriptVariantsForPopulationData(String language) {
    return languageToScriptVariants.getAll(language);
  }

  public Map<String, Pair<String, String>> getReferences() {
    return references;
  }

  public Map<String,Map<String,String>> getMetazoneToRegionToZone() {
    return metazoneToRegionToZone;
  }

  public Map<String, String> getLikelySubtags() {
    return likelySubtags;
  }

  private Map<String,PluralInfo> localeToPluralInfo = new LinkedHashMap<String,PluralInfo>();
  private transient String lastPluralLocales = "root";
  private transient Map<Count,String> lastPluralMap = new LinkedHashMap<Count,String>();

  private void addPluralPath(XPathParts path, String value) {
    String locales = path.getAttributeValue(2, "locales");
    if (!lastPluralLocales.equals(locales)) {
      addPluralInfo();
      lastPluralLocales = locales;
    }
    final String countString = path.getAttributeValue(-1, "count");
    if (countString == null) return;
    Count count = Count.valueOf(countString);
    if (lastPluralMap.containsKey(count)) {
      throw new IllegalArgumentException("Duplicate plural count: " + count + " in " + locales);
    }
    lastPluralMap.put(count, value);
  }

  private void addPluralInfo() {
    final String[] locales = lastPluralLocales.split("\\s+");
    PluralInfo info = new PluralInfo(lastPluralMap);
    for (String locale : locales) {
      if (localeToPluralInfo.containsKey(locale)) {
        throw new IllegalArgumentException("Duplicate plural locale: " + locale);
      }
      localeToPluralInfo.put(locale, info);
    }
    lastPluralMap.clear();
  }

  /**
   * Immutable class with plural info for different locales
   * @author markdavis
   */
  public static class PluralInfo {
    public enum Count {
      zero, one, two, few, many, other;
      public static final List<Count> SEARCH_LIST = Arrays.asList(new Count[]{Count.one, Count.other, Count.zero, Count.two, Count.few, Count.many});
    }
    static final Pattern pluralPaths = Pattern.compile(".*pluralRule.*");

    private final Map<Count,List<Double>> countToExampleList;
    private final Map<Count,String> countToStringExample;
    private final Map<Integer,Count> exampleToCount;
    private final PluralRules pluralRules;
    private final String pluralRulesString;

    private PluralInfo(Map<Count,String> countToRule) {
      // now build rules
      StringBuilder pluralRuleBuilder = new StringBuilder();
      XPathParts parts = new XPathParts();
      for (Count count : countToRule.keySet()) {
        if (pluralRuleBuilder.length() != 0) {
          pluralRuleBuilder.append(';');
        }
        pluralRuleBuilder.append(count).append(':').append(countToRule.get(count));
      }
      pluralRulesString = pluralRuleBuilder.toString();
      pluralRules = PluralRules.createRules(pluralRulesString);
      Set targetKeywords = pluralRules.getKeywords();

      Map<Count,List<Double>> typeToExample2 = new TreeMap<Count,List<Double>>();
      Map<Integer,Count> exampleToType2 = new TreeMap<Integer,Count>();
      Map<Count,UnicodeSet> typeToExamples2 = new TreeMap<Count,UnicodeSet>();

      for (int i = 0; i < 1000; ++i) {
        Count type = Count.valueOf(pluralRules.select(i));
        UnicodeSet uset = typeToExamples2.get(type);
        if (uset == null) typeToExamples2.put(type, uset = new UnicodeSet());
        uset.add(i);       
      }
      // double check
//    if (!targetKeywords.equals(typeToExamples2.keySet())) {
//    throw new IllegalArgumentException ("Problem in plurals " + targetKeywords + ", " + this);
//    }
      // now fix the longer examples
      String fractionalExamples = "";
      List<Double> fractions = new ArrayList(0);

      // add fractional samples
      Map<Count,String> typeToExamples3 = new TreeMap<Count,String>();
      for (Count type : typeToExamples2.keySet()) {
        UnicodeSet uset = typeToExamples2.get(type);
        int sample = uset.getRangeStart(0);
        if (sample == 0 && uset.size() > 1) { // pick non-zero if possible
          UnicodeSet temp = new UnicodeSet(uset);
          temp.remove(0);
          sample = temp.getRangeStart(0);
        }
        Integer sampleInteger = sample;
        final ArrayList<Double> arrayList = new ArrayList<Double>();
        arrayList.add((double)sample);
        typeToExample2.put(type, arrayList);
        exampleToType2.put(sampleInteger, type);

        // add fractional examples
        if (fractionalExamples.length() != 0) {
          fractionalExamples += ", ";
        }
        final double fraction = (sample + 0.31d);
        fractionalExamples += fraction;
        fractions.add(fraction);

        StringBuilder b = new StringBuilder();
        int limit = uset.getRangeCount();
        int count = 0;
        for (int i = 0; i < limit; ++i) {
          if (count > 5) {
            b.append("...");
            break;
          }
          int start = uset.getRangeStart(i);
          int end = uset.getRangeEnd(i);
          if (b.length() != 0) {
            b.append(", ");
          }
          if (start == end) {
            b.append(start);
            ++count;
          } else if (start == end+1) {
            b.append(start).append(", ").append(end);
            count+=2;
          } else {
            b.append(start).append('-').append(end);
            count+=2;
          }
        }
        typeToExamples3.put(type, b.toString());
      }
      String otherExamples = typeToExamples3.get(Count.other) + "; " + fractionalExamples + "...";
      typeToExamples3.put(Count.other, otherExamples);

      for (Count type : typeToExample2.keySet()) {
        List<Double> list = typeToExample2.get(type);
        if (type.equals(Count.other)) {
          list.addAll(fractions);
        }
        list = Collections.unmodifiableList(list);
      }

      countToExampleList = Collections.unmodifiableMap(typeToExample2);
      countToStringExample = Collections.unmodifiableMap(typeToExamples3);
      exampleToCount = Collections.unmodifiableMap(exampleToType2);
    }

    public String toString() {
      return countToExampleList + "; " + exampleToCount + "; " + pluralRules;
    }

    public Map<Count, List<Double>> getCountToExamplesMap() {
      return countToExampleList;
    }
    public Map<Count, String> getCountToStringExamplesMap() {
      return countToStringExample;
    }

    public Count getCount(double exampleCount) {
      // HACK for now
      try {
        final long rounded = Math.round(exampleCount);
        double diff = Math.abs(exampleCount - rounded);
        if (diff > 0.0000001) {
          return Count.other;
        }
        return Count.valueOf(pluralRules.select(rounded));
      } catch (RuntimeException e) {
        return null;
      }
    }

    public String getRules() {
      // TODO Auto-generated method stub
      return pluralRulesString;
    }

    public Count getDefault() {
      return null;
    }
  }

  public Set<String> getPluralLocales() {
    return localeToPluralInfo.keySet();
  }

  /**
   * Returns the plural info for a given locale.
   * @param locale
   * @return
   */
  public PluralInfo getPlurals(String locale) {
    while (locale != null) {
      PluralInfo result = localeToPluralInfo.get(locale);
      if (result != null) return result;
      locale = LanguageTagParser.getParent(locale);
    }
    return null;
  }

  private static CurrencyNumberInfo DEFAULT_NUMBER_INFO = new CurrencyNumberInfo(2,0);

  public CurrencyNumberInfo getCurrencyNumberInfo(String currency) {
    CurrencyNumberInfo result = currencyToCurrencyNumberInfo.get(currency);
    if (result == null) {
      result = DEFAULT_NUMBER_INFO;
    }
    return result;
  }

  /**
   * Returns ordered set of currency data information
   * @param territory
   * @return
   */
  public Set<CurrencyDateInfo> getCurrencyDateInfo(String territory) {
    return territoryToCurrencyDateInfo.getAll(territory);
  }
}

