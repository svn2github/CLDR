package org.unicode.cldr.util;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import org.unicode.cldr.tool.ChartAnnotations;
import org.unicode.cldr.util.XMLFileReader.SimpleHandler;

import com.google.common.base.Objects;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSet.Builder;
import com.ibm.icu.dev.util.CollectionUtilities;
import com.ibm.icu.dev.util.UnicodeMap;
import com.ibm.icu.impl.Utility;
import com.ibm.icu.lang.CharSequences;
import com.ibm.icu.text.SimpleFormatter;
import com.ibm.icu.text.UTF16;
import com.ibm.icu.text.UnicodeSet;
import com.ibm.icu.util.ICUUncheckedIOException;

public class Annotations {
    private static final boolean DEBUG = false;

    public static final String BAD_MARKER = "⊗";
    public static final String MISSING_MARKER = "⊖";
    public static final String ENGLISH_MARKER = "⊕";
    public static final String EQUIVALENT = "≣";

    static final Splitter splitter = Splitter.on(Pattern.compile("[|;]")).trimResults().omitEmptyStrings();
    static final Splitter dotSplitter = Splitter.on(".").trimResults();

    static final Map<String, AnnotationSet> cache = new ConcurrentHashMap<>();
    static final Set<String> LOCALES;
    static final String DIR;

    private final Set<String> annotations;
    private final String tts;

    static {
        File directory = new File(CLDRPaths.COMMON_DIRECTORY, "annotations");
        try {
            DIR = directory.getCanonicalPath();
        } catch (IOException e) {
            throw new ICUUncheckedIOException(e);
        }
        if (DEBUG) {
            System.out.println(DIR);
        }
        Builder<String> temp = ImmutableSet.builder();
        for (File file : directory.listFiles()) {
            if (DEBUG) {
                try {
                    System.out.println(file.getCanonicalPath());
                } catch (IOException e) {
                }
            }
            String name = file.toString();
            String shortName = file.getName();
            if (!shortName.endsWith(".xml") || // skip non-XML
                shortName.startsWith("#") || // skip other junk files
                shortName.startsWith(".")
//                || shortName.contains("001") // skip world english for now
                ) continue; // skip dot files (backups, etc)
            temp.add(dotSplitter.split(shortName).iterator().next());
        }
        LOCALES = temp.build();
    }

    static class MyHandler extends SimpleHandler {
        private final String locale;
        private final UnicodeMap<Annotations> localeData = new UnicodeMap<>();
        private final AnnotationSet parentData;

        public MyHandler(String locale, AnnotationSet parentData) {
            this.locale = locale;
            this.parentData = parentData;
        }

        public AnnotationSet cleanup() {
            // add parent data (may be overridden)
            UnicodeMap<Annotations> templocaleData = null;
            if (parentData != null) {
                templocaleData = new UnicodeMap<>();
                UnicodeSet keys = new UnicodeSet(parentData.baseData.keySet()).addAll(localeData.keySet());
                for (String key : keys) {
                    Annotations parentValue = parentData.baseData.get(key);
                    Annotations myValue = localeData.get(key);
                    if (parentValue == null) {
                        templocaleData.put(key, myValue);
                    } else if (myValue == null) {
                        templocaleData.put(key, parentValue);
                    } else { // need to combine
                        String tts = myValue.tts == null 
                            ? parentValue.tts : myValue.tts;
                        Set<String> annotations = myValue.annotations == null || myValue.annotations.isEmpty() 
                            ? parentValue.annotations : myValue.annotations;
                        templocaleData.put(key, new Annotations(annotations, tts));
                    }
                }
            }

            final AnnotationSet result = new AnnotationSet(locale, localeData, templocaleData);
            cache.put(locale, result);
            return result;
        }

        @Override
        public void handlePathValue(String path, String value) {
            XPathParts parts = XPathParts.getFrozenInstance(path);
            String lastElement = parts.getElement(-1);
            if (!lastElement.equals("annotation")) {
                if (!"identity".equals(parts.getElement(1))) {
                    throw new IllegalArgumentException("Unexpected path");
                }
                return;
            }
            String usString = parts.getAttributeValue(-1, "cp");
            UnicodeSet us = usString.startsWith("[") && usString.endsWith("]") ? new UnicodeSet(usString) : new UnicodeSet().add(usString);
            String tts = parts.getAttributeValue(-1, "tts");
            String type = parts.getAttributeValue(-1, "type");

            if ("tts".equals(type)) {
                addItems(localeData, us, Collections.<String>emptySet(), value);
            } else {
                Set<String> attributes = new TreeSet<>(splitter.splitToList(value));
                addItems(localeData, us, attributes, tts);
            }
        }

        private void addItems(UnicodeMap<Annotations> unicodeMap, UnicodeSet us, Set<String> attributes, String tts) {
            for (String entry : us) {
                addItems(unicodeMap, entry, attributes, tts);
            }
        }

        private void addItems(UnicodeMap<Annotations> unicodeMap, String entry, Set<String> attributes, String tts) {
            Annotations annotations = unicodeMap.get(entry);
            if (annotations == null) {
                unicodeMap.put(entry, new Annotations(attributes, tts));
            } else {
                unicodeMap.put(entry, annotations.add(attributes, tts)); // creates new item
            }
        }
    }

    public Annotations(Set<String> attributes, String tts2) {
        annotations = attributes == null ? Collections.<String>emptySet() : Collections.unmodifiableSet(attributes);
        tts = tts2;
    }

    public Annotations add(Set<String> attributes, String tts2) {
        return new Annotations(getKeywords() == null ? attributes : attributes == null ? getKeywords() : union(attributes, getKeywords()), 
            getShortName() == null ? tts2 : tts2 == null ? getShortName() : throwDup());
    }

    private String throwDup() {
        throw new IllegalArgumentException("Duplicate tts");
    }

    private Set<String> union(Set<String> a, Set<String> b) {
        TreeSet<String> result = new TreeSet<>(a);
        result.addAll(b);
        return result;
    }

    public static Set<String> getAvailable() {
        return LOCALES;
    }

    public static Set<String> getAvailableLocales() {
        return LOCALES;
    }

    static class EmojiConstants {
        public static final String EMOJI_VARIANT_STRING = "\uFE0F";
        static final int FIRST_REGIONAL = 0x1F1E6;
        static final int LAST_REGIONAL = 0x1F1FF;
        public static final UnicodeSet REGIONAL_INDICATORS = new UnicodeSet(FIRST_REGIONAL, LAST_REGIONAL).freeze();
        public static final String KEYCAP_MARK_STRING = "\u20E3";
        public static final UnicodeSet MODIFIERS = new UnicodeSet(0x1F3FB, 0x1F3FF).freeze();
        public static final String JOINER_STRING = "\u200D";
        public static final String KISS = "💋";
        public static final String HEART = "❤";
        public static final String MALE_SIGN = "♂";
        public static final String FEMALE_SIGN = "♀";
        public static String getFlagCode(String s) {
            return String.valueOf((char)(s.codePointAt(0) - FIRST_REGIONAL + 'A')) + (char) (s.codePointAt(2) - FIRST_REGIONAL + 'A');
        }
        public static final UnicodeSet FAMILY_MARKERS = new UnicodeSet().add(0x1F466, 0x1F469).add(0x1F476).freeze(); // boy, girl, man, woman, baby
        public static final UnicodeSet SKIP_SET = new UnicodeSet().add(EmojiConstants.HEART).add(EmojiConstants.KISS).add(MALE_SIGN).add(FEMALE_SIGN)
            .freeze();
    }

    public static final class AnnotationSet {

        private static final CLDRConfig CONFIG = CLDRConfig.getInstance();

        static Factory factory = CONFIG.getCldrFactory();
        static CLDRFile ENGLISH = CONFIG.getEnglish();

        private final String locale;
        private final UnicodeMap<Annotations> baseData;
        private final UnicodeMap<Annotations> unresolvedData;
        private final CLDRFile cldrFile;
        private final SimpleFormatter initialPattern;
        private final SimpleFormatter listPattern;
        private final String flagLabel;
        private final String keycapLabel;
        private final String maleLabel;
        private final String femaleLabel;
        private final Map<String, Annotations> localeCache = new ConcurrentHashMap<>();

        public AnnotationSet(String locale, UnicodeMap<Annotations> source, UnicodeMap<Annotations> resolvedSource) {
            this.locale = locale;
            unresolvedData = source.freeze();
            this.baseData = resolvedSource == null ? unresolvedData : resolvedSource.freeze();
            cldrFile = factory.make(locale, true);
            listPattern = SimpleFormatter.compile(getStringValue("//ldml/listPatterns/listPattern[@type=\"unit-short\"]/listPatternPart[@type=\"2\"]"));
            initialPattern = SimpleFormatter.compile(getStringValue("//ldml/characterLabels/characterLabelPattern[@type=\"category-list\"]"));
            keycapLabel = getStringValue("//ldml/characterLabels/characterLabel[@type=\"keycap\"]");
            flagLabel = getStringValue("//ldml/characterLabels/characterLabel[@type=\"flag\"]");
            maleLabel = getStringValue("//ldml/characterLabels/characterLabel[@type=\"male\"]");
            femaleLabel = getStringValue("//ldml/characterLabels/characterLabel[@type=\"female\"]");
        }
        private String getStringValue(String xpath) {
            String result = cldrFile.getStringValue(xpath);
            if (result == null) {
                return ENGLISH_MARKER + ENGLISH.getStringValue(xpath);
            }
            String sourceLocale = cldrFile.getSourceLocaleID(xpath, null);
            if (sourceLocale.equals(XMLSource.CODE_FALLBACK_ID) || sourceLocale.equals(XMLSource.ROOT_ID)) {
                return BAD_MARKER + result;
            }
            return result;
        }
        public String getShortName(String code) {
            code = code.replace(EmojiConstants.EMOJI_VARIANT_STRING,"");
            Annotations stock = baseData.get(code);
            if (stock != null && stock.tts != null) {
                return stock.tts;
            }
            stock = localeCache.get(code);
            if (stock != null) {
                return stock.tts;
            }
            stock = synthesize(code);
            if (stock != null) {
                localeCache.put(code, stock);
                return stock.tts;
            }
            return null;
        }
        public Set<String> getKeywords(String code) {
            code = code.replace(EmojiConstants.EMOJI_VARIANT_STRING,"");
            Annotations stock = baseData.get(code);
            if (stock != null && stock.annotations != null) {
                return stock.annotations;
            }
            stock = localeCache.get(code);
            if (stock != null) {
                return stock.annotations;
            }
            stock = synthesize(code);
            if (stock != null) {
                localeCache.put(code, stock);
                return stock.annotations;
            }
            return Collections.<String>emptySet();
        }
        public UnicodeSet keySet() {
            return baseData.keySet();
        }

        private Annotations synthesize(String code) {
            String shortName = null;
            Set<String> annotations = null;
            int len = code.codePointCount(0, code.length());
            if (len == 1) {
                return new Annotations(Collections.<String>emptySet(), ENGLISH_MARKER + getDataSet("en").getShortName(code));
            } else if (EmojiConstants.REGIONAL_INDICATORS.containsAll(code)) {
                String countryCode = EmojiConstants.getFlagCode(code);
                String path = CLDRFile.getKey(CLDRFile.TERRITORY_NAME, countryCode);
                return new Annotations(Collections.singleton(flagLabel), getStringValue(path));
            } else if (code.contains(EmojiConstants.KEYCAP_MARK_STRING) || code.equals("🔟")) {
                if (locale.equals("ga")) {
                    int debug = 0;
                }
                final String rem = code.equals("🔟") ? "10" : UTF16.valueOf(code.charAt(0));
                shortName = initialPattern.format(keycapLabel, rem);
                return new Annotations(Collections.singleton(keycapLabel), shortName);
            } else if (EmojiConstants.MODIFIERS.containsSome(code)) {
                String rem = EmojiConstants.MODIFIERS.stripFrom(code, false);
                code = EmojiConstants.MODIFIERS.stripFrom(code, true);
                return getBasePlusRemainder(cldrFile, code, rem, UnicodeSet.EMPTY);
            } else if (code.contains(EmojiConstants.JOINER_STRING)) {
                code = code.replace(EmojiConstants.JOINER_STRING,"");
                if (code.contains(EmojiConstants.KISS)) {
                    return getBasePlusRemainder(cldrFile, "💏", code, EmojiConstants.SKIP_SET);
                } else if (code.contains(EmojiConstants.HEART)) {
                    return getBasePlusRemainder(cldrFile, "💑", code, EmojiConstants.SKIP_SET);
                } else if (EmojiConstants.FAMILY_MARKERS.containsAll(code)) {
                    return getBasePlusRemainder(cldrFile, "👪", code, UnicodeSet.EMPTY);
                } else if (code.contains(EmojiConstants.MALE_SIGN)){
                    return getBasePlusRemainder(cldrFile, BAD_MARKER, maleLabel, code, EmojiConstants.SKIP_SET);
                } else if (code.contains(EmojiConstants.FEMALE_SIGN)){
                    return getBasePlusRemainder(cldrFile, BAD_MARKER, femaleLabel, code, EmojiConstants.SKIP_SET);
                }
            }
            return getBasePlusRemainder(cldrFile, BAD_MARKER, null, code, UnicodeSet.EMPTY);
        }

        private Annotations getBasePlusRemainder(CLDRFile cldrFile, String base, String rem, UnicodeSet ignore) {
            return getBasePlusRemainder(cldrFile, "", base, rem, ignore);
        }
        private Annotations getBasePlusRemainder(CLDRFile cldrFile, String marker, String base, String rem, UnicodeSet ignore) {
            String shortName = null;
            Set<String> annotations = new LinkedHashSet<>();
            SimpleFormatter pattern = listPattern;
            if (base != null) {
                pattern = initialPattern;
                if (marker.isEmpty()) {
                    shortName = getShortName(base);
                    annotations.addAll(getKeywords(base));
                } else {
                    shortName = base;
                    annotations.add(base);
                }
            }
            for (int mod : CharSequences.codePoints(rem)) {
                if (ignore.contains(mod)) {
                    continue;
                }
                final String modStr = UTF16.valueOf(mod);
                String modName = getShortName(modStr);
                shortName = shortName == null ? modName : pattern.format(shortName, modName);
                annotations.addAll(getKeywords(modStr));
                pattern = listPattern;
            }
            Annotations result = new Annotations(annotations, marker + shortName);
            return result;
        }

        /**
         * @deprecated Use {@link #toString(String,boolean,AnnotationSet)} instead
         */
        public String toString(String code, boolean html) {
            return toString(code, html, null);
        }
        public String toString(String code, boolean html, AnnotationSet parentAnnotations) {
            String shortName = getShortName(code);
            if (shortName == null || shortName.startsWith(BAD_MARKER) || shortName.startsWith(ENGLISH_MARKER)) {
                return MISSING_MARKER;
            }

            String parentShortName = parentAnnotations == null ? null : parentAnnotations.getShortName(code);
            if (shortName != null && Objects.equal(shortName, parentShortName)) {
                shortName = EQUIVALENT;
            }

            Set<String> keywords = getKeywordsMinus(code);
            Set<String> parentKeywords = parentAnnotations == null ? null : parentAnnotations.getKeywordsMinus(code);
            if (keywords != null && Objects.equal(keywords, parentKeywords)) {
                keywords = Collections.singleton(EQUIVALENT);
            }

            String result = CollectionUtilities.join(keywords, " |\u00a0");
            if (shortName != null) {
                String ttsString = (html ? "*<b>" : "*") + shortName + (html ? "</b>" : "*");
                if (result.isEmpty()) {
                    result = ttsString;
                } else {
                    result = ttsString + (html ? "<br>|\u00a0" : " |\u00a0") + result;
                }
            }
            return result;
        }
        public UnicodeMap<Annotations> getExplicitValues() {
            return baseData;
        }
        public UnicodeMap<Annotations> getUnresolvedExplicitValues() {
            return unresolvedData;
        }

        public Set<String> getKeywordsMinus(String code) {
            String shortName = getShortName(code);
            Set<String> keywords = getKeywords(code);
            if (shortName != null && keywords.contains(shortName)) {
                keywords = new LinkedHashSet<String>(keywords);
                keywords.remove(shortName);
            }
            return keywords;
        }
    }


    public static AnnotationSet getDataSet(String locale) {
        AnnotationSet result = cache.get(locale);
        if (result != null) {
            return result;
        }
        if (!LOCALES.contains(locale)) {
            return null;
        }
        String parentString = LocaleIDParser.getSimpleParent(locale);
        AnnotationSet parentData = null;
        if (parentString != null && !parentString.equals("root")) {
            parentData = getDataSet(parentString);
        }
        MyHandler myHandler = new MyHandler(locale, parentData);
        XMLFileReader xfr = new XMLFileReader().setHandler(myHandler);
        xfr.read(DIR + "/" + locale + ".xml", -1, true);
        return myHandler.cleanup();
    }

    public static UnicodeMap<Annotations> getData(String locale) {
        AnnotationSet result = getDataSet(locale);
        return result == null ? null : result.baseData;
    }

    @Override
    public String toString() {
        return toString(false);
    }

    public String toString(boolean html) {
        Set<String> annotations2 = getKeywords();
        if (getShortName() != null && annotations2.contains(getShortName())) {
            annotations2 = new LinkedHashSet<String>(getKeywords());
            annotations2.remove(getShortName());
        }
        String result = CollectionUtilities.join(annotations2, " |\u00a0");
        if (getShortName() != null) {
            String ttsString = (html ? "*<b>" : "*") + getShortName() + (html ? "</b>" : "*");
            if (result.isEmpty()) {
                result = ttsString;
            } else {
                result = ttsString + (html ? "<br>|\u00a0" : " |\u00a0") + result;
            }
        }
        return result;
    }

    /**
     * @return the annotations
     */
    public Set<String> getKeywords() {
        return annotations;
    }

    /**
     * @return the tts
     */
    public String getShortName() {
        return tts;
    }
    public static void main(String[] args) {
        if (true) {
            writeList();
        } else {
            writeEnglish();
        }
    }

    private static void writeList() {
        AnnotationSet eng = Annotations.getDataSet("en");
        final UnicodeMap<Annotations> map = eng.getUnresolvedExplicitValues();
        Set<String> keys = new TreeSet<>(ChartAnnotations.RBC);
        map.keySet().addAllTo(keys);
        for (String key : keys) {
            System.out.println(Utility.hex(key, 4, "_").toLowerCase(Locale.ROOT)
                + "\t" + key
                + "\t" + map.get(key).getShortName()
                + "\t" + CollectionUtilities.join(map.get(key).getKeywords(), " | ")
                );
        }
    }

    private static void writeEnglish() {
        AnnotationSet eng = Annotations.getDataSet("en");
        System.out.println(Annotations.getAvailable());
        AnnotationSet eng100 = Annotations.getDataSet("en_001");
        UnicodeMap<Annotations> map100 = eng100.getUnresolvedExplicitValues();
        final UnicodeMap<Annotations> map = eng.getUnresolvedExplicitValues();
        Set<String> keys = new TreeSet<>(ChartAnnotations.RBC);
        map.keySet().addAllTo(keys);
        for (String key : keys) {
            Annotations value = map.get(key);
            Annotations value100 = map100.get(key);
            Set<String>  keywords100 = (value100 == null ? null : value100.getKeywords());
            System.out.println(key + "\tname\t"
                + "\t" + value.getShortName() 
                + "\t" + (value100 == null ? "" : value100.getShortName())
                + "\t" + CollectionUtilities.join(value.getKeywords(), " | ")
                + "\t" + (keywords100 == null ? "" : CollectionUtilities.join(keywords100, " | "))
                );
        }
    }
}
