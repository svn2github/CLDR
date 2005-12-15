package org.unicode.cldr.test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.unicode.cldr.util.CLDRFile;
import org.unicode.cldr.util.LocaleIDParser;
import org.unicode.cldr.util.StandardCodes;
import org.unicode.cldr.util.Utility;
import org.unicode.cldr.util.XMLFileReader;
import org.unicode.cldr.util.XMLSource;
import org.unicode.cldr.util.XPathParts;

import com.ibm.icu.text.UnicodeSet;

public class CheckCoverage extends CheckCLDR {
    static final boolean DEBUG = false;
    private static CoverageLevel coverageLevel = new CoverageLevel();

    private CLDRFile resolved;

    public CheckCLDR handleCheck(String path, String fullPath, String value,
            Map options, List result) {
        // for now, skip all but localeDisplayNames
        if (resolved == null) return this;
        if (path.indexOf("localeDisplayNames") < 0 && path.indexOf("currencies") < 0) return this;

        // skip all items that are in anything but raw codes
        String source = resolved.getSourceLocaleID(path);
        if (!source.equals(XMLSource.CODE_FALLBACK_ID)) return this;
        
        // check to see if the level is good enough
        Level level = coverageLevel.getCoverageLevel(path, fullPath, value);
        if (level == Level.SKIP) return this;
        if (coverageLevel.getRequiredLevel().compareTo(level) >= 0) {
            result.add(new CheckStatus().setType(CheckStatus.errorType)
                    .setMessage("Needed to meet {0} coverage level.", new Object[] { level }));
        } else if (DEBUG) {
            System.out.println(level + "\t" + path);
        }
        return this;
    }

    public CheckCLDR setCldrFileToCheck(CLDRFile cldrFileToCheck,
            Map options, List possibleErrors) {
        if (cldrFileToCheck == null) return this;
        super.setCldrFileToCheck(cldrFileToCheck, options, possibleErrors);
        resolved = null;
        if (cldrFileToCheck.getLocaleID().equals("root")) return this;
        resolved = getResolvedCldrFileToCheck();
        coverageLevel.setFile(cldrFileToCheck, options);
        return this;
    }

    static class Level implements Comparable {
        static List all = new ArrayList();

        private byte level;

        private String name;

        static final Level SKIP = new Level(0, "none"), BASIC = new Level(2,
                "basic"), MODERATE = new Level(4, "moderate"),
                MODERN = new Level(6, "modern"), COMPREHENSIVE = new Level(10,
                        "comprehensive");

        private Level(int i, String string) {
            level = (byte) i;
            name = string;
            all.add(this);
        }

        public static Level get(String name) {
            for (int i = 0; i < all.size(); ++i) {
                Level item = (Level) all.get(i);
                if (item.name.equals(name)) return item;
            }
            return SKIP;
        }

        public String toString() {
            return name;
        }

        public int compareTo(Object o) {
            int otherLevel = ((Level) o).level;
            return level < otherLevel ? -1 : level > otherLevel ? 1 : 0;
        }
    }

    /**
     * <!-- Target-Language is the language under consideration.
     * Target-Territories is the list of territories found by looking up
     * Target-Language in the <languageData> elements in supplementalData.xml
     * Language-List is Target-Language, plus o basic: Chinese, English, French,
     * German, Italian, Japanese, Portuguese, Russian, Spanish (de, en, es, fr,
     * it, ja, pt, ru, zh) o moderate: basic + Arabic, Hindi, Korean,
     * Indonesian, Dutch, Bengali, Turkish, Thai, Polish (ar, hi, ko, in, nl,
     * bn, tr, th, pl). If an EU language, add the remaining official EU
     * languages, currently: Danish, Greek, Finnish, Swedish, Czech, Estonian,
     * Latvian, Lithuanian, Hungarian, Maltese, Slovak, Slovene (da, el, fi, sv,
     * cs, et, lv, lt, hu, mt, sk, sl) o modern: all languages that are official
     * or major commercial languages of modern territories Target-Scripts is the
     * list of scripts in which Target-Language can be customarily written
     * (found by looking up Target-Language in the <languageData> elements in
     * supplementalData.xml). Script-List is the Target-Scripts plus the major
     * scripts used for multiple languages o Latin, Simplified Chinese,
     * Traditional Chinese, Cyrillic, Arabic (Latn, Hans, Hant, Cyrl, Arab)
     * Territory-List is the list of territories formed by taking the
     * Target-Territories and adding: o basic: Brazil, China, France, Germany,
     * India, Italy, Japan, Russia, United Kingdom, United States (BR, CN, DE,
     * GB, FR, IN, IT, JP, RU, US) o moderate: basic + Spain, Canada, Korea,
     * Mexico, Australia, Netherlands, Switzerland, Belgium, Sweden, Turkey,
     * Austria, Indonesia, Saudi Arabia, Norway, Denmark, Poland, South Africa,
     * Greece, Finland, Ireland, Portugal, Thailand, Hong Kong SAR China, Taiwan
     * (ES, BE, SE, TR, AT, ID, SA, NO, DK, PL, ZA, GR, FI, IE, PT, TH, HK, TW).
     * If an EU language, add the remaining member EU countries: Luxembourg,
     * Czech Republic, Hungary, Estonia, Lithuania, Latvia, Slovenia, Slovakia,
     * Malta (LU, CZ, HU, ES, LT, LV, SI, SK, MT). o modern: all current ISO
     * 3166 territories, plus the UN M.49 regions in supplementalData.xml
     * Currency-List is the list of current official currencies used in any of
     * the territories in Territory-List, found by looking at the region
     * elements in supplementalData.xml. Calendar-List is the set of calendars
     * in customary use in any of Target-Territories, plus Gregorian.
     * Timezone-List is the set of all timezones for multi-zone territories in
     * Target-Territories, plus each of following timezones whose territories is
     * in Territory-List: o Brazil: America/Buenos_Aires, America/Rio_Branco,
     * America/Campo_Grande, America/Sao_Paulo o Australia: Australia/Perth,
     * Australia/Darwin, Australia/Brisbane, Australia/Adelaide,
     * Australia/Sydney, Australia/Hobart o Canada: America/Vancouver,
     * America/Edmonton, America/Regina, America/Winnipeg, America/Toronto,
     * America/Halifax, America/St_Johns o Mexico: America/Tijuana,
     * America/Hermosillo, America/Chihuahua, America/Mexico_City o US:
     * Pacific/Honolulu, America/Anchorage, America/Los_Angeles,
     * America/Phoenix, America/Denver, America/Chicago, America/Indianapolis,
     * America/New_York
     */
    static public class CoverageLevel {
        private static Object sync = new Object();

        private static Map coverageData = new TreeMap();
        private static Map base_language_level = new TreeMap();
        private static Map base_script_level = new TreeMap();
        private static Map base_territory_level = new TreeMap();

        private static Map language_scripts = new TreeMap();

        private static Map language_territories = new TreeMap();
        
        private static Set modernLanguages = new TreeSet();
        private static Set modernScripts = new TreeSet();
        private static Set modernTerritories = new TreeSet();

        private boolean initialized = false;

        private transient LocaleIDParser parser = new LocaleIDParser();

        private transient XPathParts parts = new XPathParts(null, null);

        private Map language_level = new TreeMap();

        private Level requiredLevel;

        private Map script_level = new TreeMap();

        private Map territory_level = new TreeMap();
        private Map currency_level = new TreeMap();
        
        StandardCodes sc = StandardCodes.make();
        
        static Map locale_requiredLevel = new TreeMap();
        boolean latinScript = false;

        public void setFile(CLDRFile file, Map options) {
            init();
            UnicodeSet exemplars = file.getExemplarSet("");
            UnicodeSet auxexemplars = file.getExemplarSet("auxiliary");
            if (auxexemplars != null) exemplars.addAll(auxexemplars);
            latinScript = exemplars.contains('A','Z');
            
            parser.set(file.getLocaleID());
            String language = parser.getLanguage();
            requiredLevel = (Level) locale_requiredLevel.get(parser.getLanguageScript());
            if (requiredLevel == null) requiredLevel = (Level) locale_requiredLevel.get(language);
            if (requiredLevel == null) requiredLevel = Level.BASIC;
            
            // do the work of putting together the coverage info
            language_level.clear();
            script_level.clear();

            language_level.putAll(base_language_level);
            language_level.put(language, Level.BASIC);

            script_level.putAll(base_script_level);
            putAll(script_level, (Set) language_scripts.get(language), Level.BASIC);

            territory_level.putAll(base_territory_level);
            putAll(territory_level, (Set) language_territories.get(language), Level.BASIC);
            
            // set currencies according to territory level
            currency_level.clear();
            putAll(currency_level, modernCurrencies, Level.MODERN);
            for (Iterator it = territory_level.keySet().iterator(); it.hasNext();) {
                String territory = (String) it.next();
                Level level = (Level) territory_level.get(territory);
                Set currencies = (Set) territory_currency.get(territory);
                setIfBetter(currencies, level, currency_level);
            }

            if (DEBUG) {
                System.out.println("Required Level: " + requiredLevel);
                System.out.println(language_level);               
                System.out.println(script_level);
                System.out.println(territory_level);
                System.out.println(currency_level);
                System.out.println("file-specific info set");
                System.out.flush();
            }
        }

        private void putAll(Map targetMap, Collection keyset, Object value) {
            if (keyset == null) return;
            for (Iterator it2 = keyset.iterator(); it2.hasNext();) {
                targetMap.put(it2.next(), value);
            }
        }

        private void setIfBetter(Set keySet, Level level, Map targetMap) {
            if (keySet == null) return;
            for (Iterator it2 = keySet.iterator(); it2.hasNext();) {
                Object script = it2.next();
                Level old = (Level) targetMap.get(script);
                if (old == null || level.compareTo(old) < 0) {
                    //System.out.println("\t" + script + "\t(" + old + ")");
                    targetMap.put(script, level);
                }
            }
        }

        public Level getCoverageLevel(String path, String fullPath, String value) {
            parts.set(fullPath);
            String lastElement = parts.getElement(-1);
            String type = (String) parts.getAttributes(-1).get("type");
            Level result = null;
            String part1 = parts.getElement(1);
            if (part1.equals("localeDisplayNames")) {
                if (lastElement.equals("language")) {
                    // <language type=\"aa\">Afar</language>"
                    result = (Level) language_level.get(type);
                } else if (lastElement.equals("territory")) {
                    // <language type=\"aa\">Afar</language>"
                    result = (Level) territory_level.get(type);
                } else if (lastElement.equals("script")) {
                    // <language type=\"aa\">Afar</language>"
                    result = (Level) script_level.get(type);
                }
            } else if (part1.equals("numbers")) {
                /*
                 * <numbers> ? <currencies> ? <currency type="BRL"> <displayName draft="true">Brazilian Real</displayName>
                 */
                if (latinScript && lastElement.equals("symbol")) {
                    result = Level.SKIP;
                } else if (lastElement.equals("displayName") || lastElement.equals("symbol")) {
                    String currency = (String) parts.getAttributes(-2).get("type");
                    result = (Level) currency_level.get(currency);
                }
            }
            if (result == null) result = Level.COMPREHENSIVE;
            return result;
        }
        
        // ========== Initialization Stuff ===================

        public void init() {
            synchronized (sync) {
                if (!initialized) {
                    try {
                        XMLFileReader xfr = new XMLFileReader()
                                .setHandler(new MetaDataHandler());
                        xfr.read(Utility.COMMON_DIRECTORY
                                + "/supplemental/metaData.xml",
                                xfr.CONTENT_HANDLER, true);

                        xfr = new XMLFileReader()
                                .setHandler(new SupplementalHandler());
                        xfr.read(Utility.COMMON_DIRECTORY
                                + "/supplemental/supplementalData.xml",
                                xfr.CONTENT_HANDLER, true);
                        
                        // put into an easier form to use
                        
                        Map type_languages = (Map) coverageData.get("languageCoverage");
                        Utility.putAllTransposed(type_languages, base_language_level);
                        Map type_scripts = (Map) coverageData.get("scriptCoverage");
                        Utility.putAllTransposed(type_scripts, base_script_level);
                        Map type_territories = (Map) coverageData.get("territoryCoverage");
                        Utility.putAllTransposed(type_territories, base_territory_level);

                        // add the modern stuff, after doing both of the above
                        
                        modernLanguages.removeAll(base_language_level.keySet());
                        putAll(base_language_level, modernLanguages, Level.MODERN);

                        modernScripts.removeAll(base_script_level.keySet());
                        putAll(base_script_level, modernScripts, Level.MODERN);

                        modernTerritories.removeAll(base_territory_level.keySet());
                        putAll(base_territory_level, modernTerritories, Level.MODERN);
                        
                        // set up the required levels
                        try {
                            // just for now
                            Map platform_local_level = sc.getLocaleTypes();
                            Map locale_level = (Map) platform_local_level.get("IBM");
                            for (Iterator it = locale_level.keySet().iterator(); it.hasNext();) {
                                String locale = (String) it.next();
                                parser.set(locale);
                                String level = (String) locale_level.get(locale);
                                requiredLevel = Level.BASIC;
                                if ("G0".equals(level)) requiredLevel = Level.COMPREHENSIVE;
                                else if ("G1".equals(level)) requiredLevel = Level.MODERN;
                                else if ("G2".equals(level)) requiredLevel = Level.MODERATE;
                                String key = parser.getLanguage();
                                Level old = (Level) locale_requiredLevel.get(key);
                                if (old == null || old.compareTo(requiredLevel) > 0) {
                                    locale_requiredLevel.put(key, requiredLevel);
                                }
                                String oldKey = key;
                                key = parser.getLanguageScript();
                                if (!key.equals(oldKey)) {
                                    old = (Level) locale_requiredLevel.get(key);
                                    if (old == null || old.compareTo(requiredLevel) > 0) {
                                        locale_requiredLevel.put(key, requiredLevel);
                                    }
                                }
                            }
                         } catch (IOException e) {
                            // TODO Auto-generated catch block
                            e.printStackTrace();
                        }

                         if (DEBUG) {
                             System.out.println(base_language_level);               
                             System.out.println(base_script_level);
                             System.out.println(base_territory_level);
                             System.out.println("common info set");
                             System.out.flush();
                         }
                        initialized = true;
                    } catch (RuntimeException e) {
                        throw e; // just for debugging
                    }
                }
            }
        }

        static class MetaDataHandler extends XMLFileReader.SimpleHandler {
            XPathParts parts = new XPathParts(null, null);

            public void handlePathValue(String path, String value) {
                try {
                    if (path.indexOf("coverageAdditions") < 0) return;
                    // System.out.println(path);
                    // System.out.flush();
                    parts.set(path);
                    String lastElement = parts.getElement(-1);
                    Map attributes = parts.getAttributes(-1);
                    // <languageCoverage type="basic" values="de en es fr it ja
                    // pt ru zh"/>
                    Level type = Level.get((String) attributes.get("type"));
                    String values = (String) attributes.get("values");
                    Utility.addTreeMapChain(coverageData, new Object[] {
                            lastElement, type,
                            new TreeSet(Arrays.asList(values.split("\\s+"))) });
                } catch (RuntimeException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
        }

        static Map territory_currency = new TreeMap();
        static Set modernCurrencies = new TreeSet();
        
        static class SupplementalHandler extends XMLFileReader.SimpleHandler {
            XPathParts parts = new XPathParts(null, null);

            public void handlePathValue(String path, String value) {
                try {
//                    if (path.indexOf("calendarData") < 0
//                            && path.indexOf("languageData") < 0) return;
                    parts.set(path);
                    String lastElement = parts.getElement(-1);
                    Map attributes = parts.getAttributes(-1);
                    String type = (String) attributes.get("type");
                    if (parts.containsElement("calendarData")) {
                        // System.out.println(path);
                        // System.out.flush();
                        // we have element, type, subtype, and values
                        Set values = new TreeSet(
                                Arrays.asList(((String) attributes
                                        .get("territories")).split("\\s+")));
                        Utility.addTreeMapChain(coverageData, new Object[] {
                                lastElement, type, values });
                    } else if (parts.containsElement("languageData")) {
                        // <language type="ab" scripts="Cyrl" territories="GE"
                        // alt="secondary"/>
                        String alt = (String) attributes.get("alt");
                        if (alt != null) return;
                        modernLanguages.add(type);
                        String scripts = (String) attributes.get("scripts");
                        if (scripts != null) {
                            Set scriptSet = new TreeSet(Arrays.asList(scripts
                                    .split("\\s+")));
                            modernScripts.addAll(scriptSet);
                            Utility.addTreeMapChain(language_scripts,
                                    new Object[] {type, scriptSet});
                        }
                        String territories = (String) attributes
                                .get("territories");
                        if (territories != null) {
                            Set territorySet = new TreeSet(Arrays
                                    .asList(territories
                                            .split("\\s+")));
                            modernTerritories.addAll(territorySet);
                            Utility.addTreeMapChain(language_territories,
                                    new Object[] {type, territorySet});
                        }
                    } else if (parts.containsElement("currencyData") && lastElement.equals("currency")) {
                        //         <region iso3166="AM"><currency iso4217="AMD" from="1993-11-22"/>
                        // if the 'to' value is less than 10 years, it is not modern
                        String to = (String) attributes.get("to");
                        String currency = (String) attributes.get("iso4217");
                        if (to == null || to.compareTo("1995") >= 0) {
                            modernCurrencies.add(currency);
                            // only add current currencies to must have list
                            if (to == null) {
                                String region = (String) parts.getAttributes(-2).get("iso3166");
                                Set currencies = (Set) territory_currency.get(region);
                                if (currencies == null) territory_currency.put(region, currencies = new TreeSet());
                                currencies.add(currency);
                            }
                        }
                    }
                } catch (RuntimeException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
        }

        public Level getRequiredLevel() {
            return requiredLevel;
        }
    }

}