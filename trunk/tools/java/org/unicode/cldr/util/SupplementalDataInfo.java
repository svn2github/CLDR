package org.unicode.cldr.util;

import com.ibm.icu.text.MessageFormat;
import com.ibm.icu.util.Freezable;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;

public class SupplementalDataInfo {

  /**
   * Struct for return data
   */
  public static final class PopulationData implements Freezable {
    private double population = Double.NaN;

    private double literatePopulation = Double.NaN;

    private double gdp = Double.NaN;

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
              "[pop: {0,number,#,##0},\t lit: {1,number,#,##0.00},\t gdp: {2,number,#,##0}]",
              new Object[] { population, literatePopulation, gdp });
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
  }

  static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");

  public static class BasicLanguageData implements
      Comparable<BasicLanguageData> {
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
      // TODO add error checking
      scripts = scriptTokens == null ? Collections.EMPTY_SET : Collections
          .unmodifiableSet(new TreeSet(scriptTokens));
      return this;
    }

    public BasicLanguageData setTerritories(Collection<String> territoryTokens) {
      // TODO add error checking
      territories = territoryTokens == null ? Collections.EMPTY_SET
          : Collections.unmodifiableSet(new TreeSet(territoryTokens));
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
  }

  private Map<String, PopulationData> territoryToPopulationData = new TreeMap();

  private Map<String, Map<String, PopulationData>> territoryToLanguageToPopulationData = new TreeMap();

  private Map<String, PopulationData> languageToPopulation = new TreeMap();

  private Map<String, PopulationData> baseLanguageToPopulation = new TreeMap();

  private Relation<String, String> languageToScriptVariants = new Relation(new TreeMap(), TreeSet.class);

  private Relation<String, String> languageToTerritories = new Relation(new TreeMap(), TreeSet.class);

  private Relation<String, BasicLanguageData> languageToBasicLanguageData = new Relation(new TreeMap(), TreeSet.class);

  // private Map<String, BasicLanguageData> languageToBasicLanguageData2 = new
  // TreeMap();

  // Relation(new TreeMap(), TreeSet.class, null);

  private Set<String> allLanguages = new TreeSet();

  private Relation<String, String> containment = new Relation(new TreeMap(),
      TreeSet.class);

  private Set<String> multizone = new TreeSet();

  private Map<String, String> zone_territory = new TreeMap();

  private Relation<String, String> zone_aliases = new Relation(new TreeMap(),
      TreeSet.class);

  private Map<String, String> alias_zone = new TreeMap();

  static Map<String, SupplementalDataInfo> directory_instance = new HashMap();

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
      XMLFileReader xfr = new XMLFileReader()
          .setHandler(instance.new MyHandler());
      xfr.read(canonicalpath + "/supplementalData.xml", -1, true);
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

    containment.freeze();
    languageToBasicLanguageData.freeze();
    languageToTerritories.freeze();
    zone_aliases.freeze();
    languageToScriptVariants.freeze();

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
  }

  class MyHandler extends XMLFileReader.SimpleHandler {
    private static final double MAX_POPULATION = 3000000000.0;

    XPathParts parts = new XPathParts();

    LanguageTagParser languageTagParser = new LanguageTagParser();

    public void handlePathValue(String path, String value) {
      try {
        parts.set(path);
        String level1 = parts.getElement(1);
        // copy the rest from ShowLanguages later
        if (level1.equals("territoryInfo")) {
          // <territoryInfo>
          // <territory type="AD" gdp="1840000000" literacyPercent="100"
          // population="66000"> <!--Andorra-->
          // <languagePopulation type="ca" populationPercent="50"/>
          // <!--Catalan-->

          Map<String, String> territoryAttributes = parts.getAttributes(2);
          String territory = territoryAttributes.get("type");
          double territoryPopulation = parseDouble(territoryAttributes.get("population"));
          if (failsRangeCheck("population", territoryPopulation, 0, MAX_POPULATION)) return;
          
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
            PopulationData newData = new PopulationData()
            .setPopulation(languagePopulation)
            .setLiteratePopulation(languageLiteracyPercent * languagePopulation / 100)
            //.setGdp(languageGdp)
            ;
            newData.freeze();
            if (territoryLanguageToPopulation.get(language) != null) {
              System.out
                  .println("Internal Problem in supplementalData: multiple data items for "
                      + language + ", " + territory + "\tSkipping " + newData);
              return;
            }
            
            territoryLanguageToPopulation.put(language, newData);
            languageToTerritories.put(language, territory);

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
          return;
        }
        if (level1.equals("languageData")) {
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
          return;
        }
        if (level1.equals("generation") || level1.equals("version")) {
          // skip
          return;
        }
        if (level1.equals("territoryContainment")) {
          // <group type="001" contains="002 009 019 142 150"/>
          containment.putAll(parts.getAttributeValue(-1, "type"), Arrays
              .asList(parts.getAttributeValue(-1, "contains").split("\\s+")));
          return;
        }
        if (level1.equals("timezoneData")) {
          String level2 = parts.getElement(2);
          // <zoneFormatting multizone="001 AQ AR AU BR CA CD CL CN EC ES FM GL
          // ID KI KZ MH MN MX MY NZ PF PT RU SJ UA UM US UZ"
          // tzidVersion="2007c">
          // <zoneItem type="Africa/Abidjan" territory="CI"/>
          // <zoneItem type="Africa/Asmera" territory="ER"
          // aliases="Africa/Asmara"/>
          if (level2.equals("zoneFormatting")) {
            if (multizone.size() == 0) {
              multizone.addAll(Arrays.asList(parts.getAttributeValue(2,
                  "multizone").trim().split("\\s+")));
            }
            String zone = parts.getAttributeValue(3, "type");
            String territory = parts.getAttributeValue(3, "territory");
            String aliases = parts.getAttributeValue(3, "aliases");
            if (territory != null) {
              zone_territory.put(zone, territory);
            }
            if (aliases != null) {
              String[] aliasArray = aliases.split("\\s+");
              zone_aliases.putAll(zone, Arrays.asList(aliasArray));
              for (String alias : aliasArray) {
                alias_zone.put(alias, zone);
              }
            }

            return;
          }

          if (!skippedElements.contains(level1 + "/" + level2)) {
            skippedElements.add(level1 + "/" + level2);
            if (false)
              System.out.println("TODO: Skipped Element: " + level1 + " - ... "
                  + path + "...");
          }
          // <mapTimezones type="windows"> <mapZone other="Dateline"
          // type="Etc/GMT+12"/> <!-- S (GMT-12:00) International Date Line
          // West-->
        }
        
        if (level1.equals("references")) {
          String type = parts.getAttributeValue(-1, "type");
          String uri = parts.getAttributeValue(-1, "uri");
          references.put(type, (Pair)new Pair(uri, value).freeze());
        }

        // capture elements we didn't look at, since we should cover everything.
        // this helps for updates

        if (!skippedElements.contains(level1)) {
          skippedElements.add(level1);
        }
        // System.out.println("Skipped Element: " + path);
      } catch (Exception e) {
        throw (IllegalArgumentException) new IllegalArgumentException("path: "
            + path + ",\tvalue: " + value).initCause(e);
      }
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

  public PopulationData getLangaugeAndTerritoryPopulationData(String language,
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
    return zone_aliases.getAll(zone);
  }

  public String getZone_territory(String zone) {
    return zone_territory.get(zone);
  }

  public Set<String> getMultizones() {
    // TODO Auto-generated method stub
    return multizone;
  }

  public Set<String> getTerritoriesForPopulationData(String language) {
    return languageToTerritories.getAll(language);
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
        PopulationData populationData = getLangaugeAndTerritoryPopulationData(
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
}