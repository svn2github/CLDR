package org.unicode.cldr.unittest;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.unicode.cldr.test.ExampleGenerator;
import org.unicode.cldr.test.ExampleGenerator.ExampleType;
import org.unicode.cldr.test.ExampleGenerator.UnitLength;
import org.unicode.cldr.test.ExampleGenerator.Zoomed;
import org.unicode.cldr.unittest.TestAll.TestInfo;
import org.unicode.cldr.util.CLDRFile;
import org.unicode.cldr.util.CldrUtility;
import org.unicode.cldr.util.PathStarrer;
import org.unicode.cldr.util.With;
import org.unicode.cldr.util.SupplementalDataInfo.PluralInfo.Count;

import com.ibm.icu.dev.test.TestFmwk;

public class TestExampleGenerator extends TestFmwk {
    TestInfo info = TestAll.TestInfo.getInstance();

    public static void main(String[] args) {
        new TestExampleGenerator().run(args);
    }

    static final Set<String> OK_TO_MISS_EXAMPLES = new HashSet<String>(Arrays.asList(
            "//ldml/numbers/symbols[@numberSystem=\"([^\"]*+)\"]/infinity",
            "//ldml/numbers/symbols[@numberSystem=\"([^\"]*+)\"]/list",
            "//ldml/numbers/symbols[@numberSystem=\"([^\"]*+)\"]/nan",
            "//ldml/layout/orientation/characterOrder",
            "//ldml/layout/orientation/lineOrder",
            "//ldml/characters/moreInformation",
            "//ldml/numbers/symbols[@numberSystem=\"([^\"]*+)\"]/infinity",
            "//ldml/numbers/symbols[@numberSystem=\"([^\"]*+)\"]/list",
            "//ldml/numbers/symbols[@numberSystem=\"([^\"]*+)\"]/nan",
            // old format
            "//ldml/numbers/currencyFormats/currencySpacing/afterCurrency/currencyMatch",
            "//ldml/numbers/currencyFormats/currencySpacing/afterCurrency/insertBetween",
            "//ldml/numbers/currencyFormats/currencySpacing/afterCurrency/surroundingMatch",
            "//ldml/numbers/currencyFormats/currencySpacing/beforeCurrency/currencyMatch",
            "//ldml/numbers/currencyFormats/currencySpacing/beforeCurrency/insertBetween",
            "//ldml/numbers/currencyFormats/currencySpacing/beforeCurrency/surroundingMatch",
            "//ldml/numbers/symbols/infinity",
            "//ldml/numbers/symbols/list",
            "//ldml/numbers/symbols/nan",
            "//ldml/posix/messages/nostr",
            "//ldml/posix/messages/yesstr"
            ));
    public void TestAllPaths() {
        ExampleGenerator exampleGenerator = getExampleGenerator("en");
        PathStarrer ps = new PathStarrer();
        Set<String> seen = new HashSet<String>();
        CLDRFile cldrFile = exampleGenerator.getCldrFile();
        for (String path : cldrFile) {
            String starred = ps.set(path).replace("\"", "\\\"");
            if (path.endsWith("/alias") 
                    || path.startsWith("//ldml/identity") 
                    || OK_TO_MISS_EXAMPLES.contains(starred)) {
                continue;
            }
            String value = cldrFile.getStringValue(path);
            String example = exampleGenerator.getExampleHtml(path, value, Zoomed.IN);
            if (example == null) {
                if (!seen.contains(starred)) {
                    errln("No example:\t<" + value
                    		+ ">\t'\"" + starred + "\",");
                }
            } else {
                String simplified = simplify(example, false);
                if (!simplified.startsWith("〖")) {
                    if (!seen.contains(starred)) {
                        errln("Funny HTML:\t" + simplified + "\t" + starred);
                    }
                } else if (!simplified.contains("❬")){
                    if (!seen.contains(starred)) {
                        errln("No background:\t" + simplified + "\t" + starred);
                    }
                }
            }
            seen.add(starred);
        }
    }

    public void TestUnits() {
        ExampleGenerator exampleGenerator = getExampleGenerator("en");
        checkValue("Duration hm", "〖5:37〗", exampleGenerator, "//ldml/units/unitLength[@type=\"short\"]/durationUnit[@type=\"hm\"]/durationUnitPattern");
        checkValue("Length m", "〖❬1.99❭ m〗", exampleGenerator, "//ldml/units/unitLength[@type=\"short\"]/unit[@type=\"length-m\"]/unitPattern[@count=\"other\"]");
    }

    private void checkValue(String message, String expected, ExampleGenerator exampleGenerator, String path) {
        String value = exampleGenerator.getCldrFile().getStringValue(path);
        String actual = exampleGenerator.getExampleHtml(path, value, null);
        assertEquals(message, expected, simplify(actual, false));
    }

    public void TestCompoundUnit() {
        String[][] tests = {
                {"LONG", "one", "〖❬1 meter❭ per ❬second❭〗"},
                {"SHORT", "one", "〖❬1 m❭/❬sec❭〗"},
                {"NARROW", "one", "〖❬1 m❭/❬s❭〗"},
                {"LONG", "other", "〖❬1.99 meters❭ per ❬second❭〗"},
                {"SHORT", "other", "〖❬1.99 m❭/❬sec❭〗"},
                {"NARROW", "other", "〖❬1.99 m❭/❬s❭〗"},
        };
        checkCompoundUnits("en", tests);
        // reenable these after Arabic has meter translated
        //        String[][] tests2 = {
        //                {"LONG", "few", "〖❬1 meter❭ per ❬second❭〗"},
        //        };
        //        checkCompoundUnits("ar", tests2);
    }

    private void checkCompoundUnits(String locale, String[][] tests) {
        ExampleGenerator exampleGenerator = getExampleGenerator(locale);
        for (String[] pair : tests) {
            String actual = exampleGenerator.handleCompoundUnit(
                    UnitLength.valueOf(pair[0]), Count.valueOf(pair[1]), "");
            assertEquals("CompoundUnit", pair[2], simplify(actual, true));
        }
    }

    private ExampleGenerator getExampleGenerator(String locale) {
        final CLDRFile nativeCldrFile = info.getCldrFactory().make(locale, true);
        ExampleGenerator exampleGenerator = new ExampleGenerator(nativeCldrFile,
                info.getEnglish(), CldrUtility.DEFAULT_SUPPLEMENTAL_DIRECTORY);
        return exampleGenerator;
    }

    public void TestEllipsis() {
        ExampleGenerator exampleGenerator = getExampleGenerator("it");
        String[][] tests = {
                {"initial", "〖…❬iappone❭〗"},
                {"medial", "〖❬Svizzer❭…❬iappone❭〗"},
                {"final", "〖❬Svizzer❭…〗"},
                {"word-initial", "〖… ❬Giappone❭〗"},
                {"word-medial", "〖❬Svizzera❭ … ❬Giappone❭〗"},
                {"word-final", "〖❬Svizzera❭ …〗"},
        };
        for (String[] pair : tests) {
            checkValue(exampleGenerator, "//ldml/characters/ellipsis[@type=\"" + pair[0] + "\"]", pair[1]);
        }
    }

    private void checkValue(ExampleGenerator exampleGenerator, String path, String expected) {
        String value = exampleGenerator.getCldrFile().getStringValue(path);
        String result = simplify(exampleGenerator.getExampleHtml(path, value, Zoomed.IN), false);
        assertEquals("Ellipsis", expected, result);
    }

    private String simplify(String exampleHtml, boolean internal) {
        return internal ? "〖" + exampleHtml
                .replace("", "❬")
                .replace("", "❭") + "〗"
                : exampleHtml
                .replace("<div class='cldr_example'>", "〖")
                .replace("</div>", "〗")
                .replace("<span class='cldr_substituted'>", "❬")
                .replace("</span>", "❭")
                ;
    }

    public void TestClip() {
        assertEquals("Clipping", "bc", ExampleGenerator.clip("abc", 1, 0));
        assertEquals("Clipping", "ab", ExampleGenerator.clip("abc", 0, 1));
        assertEquals("Clipping", "b\u0308c\u0308", ExampleGenerator.clip("a\u0308b\u0308c\u0308", 1, 0));
        assertEquals("Clipping", "a\u0308b\u0308", ExampleGenerator.clip("a\u0308b\u0308c\u0308", 0, 1));
    }

    public void TestPaths() {
        showCldrFile(info.getEnglish());
        showCldrFile(info.getCldrFactory().make("fr", true));
    }

    public void TestMiscPatterns() {
        ExampleGenerator exampleGenerator = getExampleGenerator("it");
        checkValue("At least", "〖❬99❭+〗", exampleGenerator, "//ldml/numbers/miscPatterns[@numberSystem=\"latn\"]/pattern[@type=\"atLeast\"]");
        checkValue("Range", "〖❬99❭-❬144❭〗", exampleGenerator, "//ldml/numbers/miscPatterns[@numberSystem=\"latn\"]/pattern[@type=\"range\"]");
        //        String actual = exampleGenerator.getExampleHtml(
        //                "//ldml/numbers/miscPatterns[@type=\"arab\"]/pattern[@type=\"atLeast\"]",
        //                "at least {0}", Zoomed.IN);
        //        assertEquals("Invalid format", "<div class='cldr_example'>at least 99</div>", actual);
    }

    public void TestLocaleDisplayPatterns() {
        ExampleGenerator exampleGenerator = getExampleGenerator("it");
        String actual = exampleGenerator.getExampleHtml("//ldml/localeDisplayNames/localeDisplayPattern/localePattern",
                "{0} [{1}]", Zoomed.IN);
        assertEquals("localePattern example faulty",
                "<div class='cldr_example'><span class='cldr_substituted'>usbeco</span> [<span class='cldr_substituted'>Afghanistan</span>]</div>" +
                        "<div class='cldr_example'><span class='cldr_substituted'>usbeco</span> [<span class='cldr_substituted'>arabo, Afghanistan</span>]</div>" +
                        "<div class='cldr_example'><span class='cldr_substituted'>usbeco</span> [<span class='cldr_substituted'>arabo, Afghanistan, Fuso orario: Africa/Addis_Ababa, Cifre indo-arabe</span>]</div>",
                        actual);
        actual = exampleGenerator.getExampleHtml("//ldml/localeDisplayNames/localeDisplayPattern/localeSeparator",
                "{0}. {1}", Zoomed.IN);
        assertEquals("localeSeparator example faulty",
                "<div class='cldr_example'><span class='cldr_substituted'>usbeco (arabo</span>. <span class='cldr_substituted'>Afghanistan)</span></div>" +
                        "<div class='cldr_example'><span class='cldr_substituted'>usbeco (arabo</span>. <span class='cldr_substituted'>Afghanistan</span>. <span class='cldr_substituted'>Fuso orario: Africa/Addis_Ababa</span>. <span class='cldr_substituted'>Cifre indo-arabe)</span></div>",
                        actual);
    }

    public void TestCurrencyFormats() {
        ExampleGenerator exampleGenerator = getExampleGenerator("it");
        String actual = exampleGenerator.getExampleHtml("//ldml/numbers/currencyFormats[@numberSystem=\"latn\"]/currencyFormatLength/currencyFormat[@type=\"standard\"]/pattern[@type=\"standard\"]",
                "¤ #0.00", Zoomed.IN);
        assertEquals("Currency format example faulty",
                "<div class='cldr_example'>XXX\u00A0<span class='cldr_substituted'>5</span>,<span class='cldr_substituted'>43</span></div>" +
                        "<div class='cldr_example'>XXX\u00A0<span class='cldr_substituted'>123456</span>,<span class='cldr_substituted'>79</span></div>" +
                        "<div class='cldr_example'>-XXX\u00A0<span class='cldr_substituted'>123456</span>,<span class='cldr_substituted'>79</span></div>",
                        actual);
    }

    public void TestSymbols() {
        CLDRFile english = info.getEnglish();
        ExampleGenerator exampleGenerator = new ExampleGenerator(english, english,
                CldrUtility.DEFAULT_SUPPLEMENTAL_DIRECTORY);
        String actual = exampleGenerator.getExampleHtml("//ldml/numbers/symbols[@numberSystem=\"latn\"]/superscriptingExponent",
                "x", Zoomed.IN);
        assertEquals("superscriptingExponent faulty",
                "<div class='cldr_example'><span class='cldr_substituted'>1.23456789</span>x10<span class='cldr_substituted'><sup>5</sup></span></div>",
                actual);

    }

    public void Test4897() {
        ExampleGenerator exampleGenerator = getExampleGenerator("it");
        for (String xpath : With.in(exampleGenerator.getCldrFile().iterator("//ldml/dates/timeZoneNames", CLDRFile.ldmlComparator))) {
            String value = exampleGenerator.getCldrFile().getStringValue(xpath);
            String actual = exampleGenerator.getExampleHtml(xpath, value, Zoomed.IN, null, ExampleType.NATIVE);
            if (actual == null) {
                if (!xpath.contains("singleCountries") && !xpath.contains("gmtZeroFormat")) {
                    errln("Null value for " + value + "\t" + xpath);
                    // for debugging
                    exampleGenerator.getExampleHtml(xpath, value, Zoomed.IN, null, ExampleType.NATIVE);
                }
            } else {
                logln(actual + "\t" + value + "\t" + xpath);
            }
        }
    }

    public void Test4528() {
        String[][] testPairs = {
                { "//ldml/numbers/currencies/currency[@type=\"BMD\"]/displayName[@count=\"other\"]",
                    "<div class='cldr_example'><span class='cldr_substituted'>2,00 </span>dollari delle Bermuda</div>" +
                            "<div class='cldr_example'><span class='cldr_substituted'>1,20 </span>dollari delle Bermuda</div>" +
                            "<div class='cldr_example'><span class='cldr_substituted'>2,07 </span>dollari delle Bermuda</div>"
                },
                {
                    "//ldml/numbers/currencyFormats[@numberSystem=\"latn\"]/unitPattern[@count=\"other\"]",
                    "<div class='cldr_example'><span class='cldr_substituted'>2,00</span> <span class='cldr_substituted'>dollari statunitensi</span></div>"
                            +
                            "<div class='cldr_example'><span class='cldr_substituted'>1,20</span> <span class='cldr_substituted'>dollari statunitensi</span></div>"
                            +
                            "<div class='cldr_example'><span class='cldr_substituted'>2,07</span> <span class='cldr_substituted'>dollari statunitensi</span></div>"
                },
                { "//ldml/numbers/currencies/currency[@type=\"BMD\"]/symbol",
                    "<div class='cldr_example'>BMD<span class='cldr_substituted'> 123.456,79</span></div>"
                },
        };

        ExampleGenerator exampleGenerator = getExampleGenerator("it");
        for (String[] testPair : testPairs) {
            String xpath = testPair[0];
            String expected = testPair[1];
            String value = exampleGenerator.getCldrFile().getStringValue(xpath);
            String actual = exampleGenerator.getExampleHtml(xpath, value, Zoomed.IN, null, ExampleType.NATIVE);
            assertEquals("specifics", expected, actual);
        }
    }

    public void Test4607() {
        String[][] testPairs = {
                {
                    "//ldml/numbers/decimalFormats[@numberSystem=\"latn\"]/decimalFormatLength[@type=\"long\"]/decimalFormat[@type=\"standard\"]/pattern[@type=\"1000\"][@count=\"one\"]",
                    "<div class='cldr_example'><span class='cldr_substituted'>1</span> thousand</div>"
                },
                {
                    "//ldml/numbers/percentFormats[@numberSystem=\"latn\"]/percentFormatLength/percentFormat[@type=\"standard\"]/pattern[@type=\"standard\"]",
                    "<div class='cldr_example'><span class='cldr_substituted'>5</span>%</div>" +
                            "<div class='cldr_example'><span class='cldr_substituted'>12</span>,<span class='cldr_substituted'>345</span>,<span class='cldr_substituted'>679</span>%</div>" +
                            "<div class='cldr_example'>-<span class='cldr_substituted'>12</span>,<span class='cldr_substituted'>345</span>,<span class='cldr_substituted'>679</span>%</div>"
                }
        };
        final CLDRFile nativeCldrFile = info.getEnglish();
        ExampleGenerator exampleGenerator = new ExampleGenerator(info.getEnglish(), info.getEnglish(),
                CldrUtility.DEFAULT_SUPPLEMENTAL_DIRECTORY);
        for (String[] testPair : testPairs) {
            String xpath = testPair[0];
            String expected = testPair[1];
            String value = nativeCldrFile.getStringValue(xpath);
            String actual = exampleGenerator.getExampleHtml(xpath, value, Zoomed.IN, null, ExampleType.NATIVE);
            assertEquals("specifics", expected, actual);
        }
    }

    private void showCldrFile(final CLDRFile cldrFile) {
        ExampleGenerator exampleGenerator = new ExampleGenerator(cldrFile, info.getEnglish(),
                CldrUtility.DEFAULT_SUPPLEMENTAL_DIRECTORY);
        checkPathValue(
                exampleGenerator,
                "//ldml/dates/calendars/calendar[@type=\"chinese\"]/dateFormats/dateFormatLength[@type=\"full\"]/dateFormat[@type=\"standard\"]/pattern[@type=\"standard\"][@draft=\"unconfirmed\"]",
                "EEEE d MMMMl y'x'G");

        for (String xpath : cldrFile.fullIterable()) {
            String value = cldrFile.getStringValue(xpath);
            checkPathValue(exampleGenerator, xpath, value);
            if (xpath.contains("count=\"one\"")) {
                String xpath2 = xpath.replace("count=\"one\"", "count=\"1\"");
                checkPathValue(exampleGenerator, xpath2, value);
            }
        }
    }

    private void checkPathValue(ExampleGenerator exampleGenerator, String xpath, String value) {
        Set<String> alreadySeen = new HashSet<String>();
        for (ExampleType type : ExampleType.values()) {
            for (Zoomed zoomed : Zoomed.values()) {
                try {
                    String text = exampleGenerator.getExampleHtml(xpath, value, zoomed, null, type);
                    if (text == null) continue;
                    if (text.contains("Exception")) {
                        errln("getExampleHtml\t" + type + "\t" + zoomed + "\t" + text);
                    } else if (!alreadySeen.contains(text)) {
                        if (text.contains("n/a")) {
                            if (text.contains("&lt;")) {
                                errln("Text not quoted correctly:" + "\t" + zoomed + "\t" + text + "\t" + xpath);
                            }
                        }
                        if (text.contains("&lt;")) {
                            int x = 0; // for debugging
                        }
                        text = exampleGenerator.getExampleHtml(xpath, value, zoomed, null, type);
                        logln("getExampleHtml\t" + type + "\t" + zoomed + "\t" + text + "\t" + xpath);
                        alreadySeen.add(text);
                    }
                } catch (Exception e) {
                    errln("getExampleHtml\t" + type + "\t" + zoomed + "\t" + e.getMessage());
                }
            }
        }
        try {
            String text = exampleGenerator.getHelpHtml(xpath, value);
            if (text == null) {
                // skip
            } else if (text.contains("Exception")) {
                errln("getHelpHtml\t" + text);
            } else {
                logln("getExampleHtml\t" + "\t" + text + "\t" + xpath);
            }
        } catch (Exception e) {
            if (false) {
                e.printStackTrace();
            }
            errln("getHelpHtml\t" + e.getMessage());
        }
    }
}
