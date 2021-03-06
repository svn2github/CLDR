package org.unicode.cldr.json;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import org.unicode.cldr.util.Builder;
import org.unicode.cldr.util.CLDRFile;

class LdmlConvertRules {

    /** File set that will not be processed in JSON transformation. */
    public static final Set<String> IGNORE_FILE_SET = Builder.with(new HashSet<String>())
        .add("coverageLevels").add("pluralRanges").freeze();

    /**
     * The attribute list that should become part of the name in form of
     * name-(attribute)-(value).
     * [parent_element]:[element]:[attribute]
     */
    private static final String[] NAME_PART_DISTINGUISHING_ATTR_LIST = {
        // common/main
        "monthWidth:month:yeartype",
        "dateFormat:pattern:numbers",
        "currencyFormats:unitPattern:count",
        "currency:displayName:count",
        "numbers:symbols:numberSystem",
        "numbers:decimalFormats:numberSystem",
        "numbers:currencyFormats:numberSystem",
        "numbers:percentFormats:numberSystem",
        "numbers:scientificFormats:numberSystem",
        "numbers:miscPatterns:numberSystem",
        "territoryContainment:group:status",
        "decimalFormat:pattern:count",
        "unit:unitPattern:count",
        "field:relative:type",
        "field:relativeTime:type",
        "relativeTime:relativeTimePattern:count",
        "listPatterns:listPattern:type",
        "timeZoneNames:regionFormat:type",
        "units:durationUnit:type",
        "weekData:minDays:territories",
        "weekData:firstDay:territories",
        "weekData:weekendStart:territories",
        "weekData:weekendEnd:territories",
        "supplemental:plurals:type",
        "pluralRules:pluralRule:count"
    };

    /**
     * The set of attributes that should become part of the name in form of
     * name-(attribute)-(value).
     */
    public static final Set<String> NAME_PART_DISTINGUISHING_ATTR_SET =
        new HashSet<String>(Arrays.asList(NAME_PART_DISTINGUISHING_ATTR_LIST));

    /**
     * Following is a list of element:attribute pair. These attributes should be
     * treated as values. For example,
     * <type type="arab" key="numbers">Arabic-Indic Digits</type>
     * should be really converted as,
     * "arab": {
     * "_value": "Arabic-Indic Digits",
     * "_key": "numbers"
     * }
     */
    private static final String[] ATTR_AS_VALUE_LIST = {

        // in common/supplemental/dayPeriods.xml
        "dayPeriodRules:dayPeriodRule:from",

        // in common/supplemental/likelySubtags.xml
        "likelySubtags:likelySubtag:to",

        // in common/supplemental/metaZones.xml
        "timezone:usesMetazone:mzone",
        // Only the current usesMetazone will be kept, it is not necessary to keep
        // "to" and "from" attributes to make key unique. This is needed as their
        // value is not good if used as key.
        "timezone:usesMetazone:to",
        "timezone:usesMetazone:from",

        "mapTimezones:mapZone:other",
        "mapTimezones:mapZone:type",
        "mapTimezones:mapZone:territory",

        // in common/supplemental/numberingSystems.xml
        "numberingSystems:numberingSystem:type",

        // in common/supplemental/supplementalData.xml
        "region:currency:from",
        "region:currency:to",
        "region:currency:tender",
        "calendar:calendarSystem:type",
        "codeMappings:territoryCodes:numeric",
        "codeMappings:territoryCodes:alpha3",

        // common/supplemental/supplementalMetaData.xml
        "validity:variable:type",
        "deprecated:deprecatedItems:elements",
        "deprecated:deprecatedItems:attributes",
        "deprecated:deprecatedItems:type",

        // in common/supplemental/telephoneCodeData.xml
        "codesByTerritory:telephoneCountryCode:code",

        // in common/supplemental/windowsZones.xml
        "mapTimezones:mapZone:other",

        // in common/bcp47/*.xml
        "keyword:key:alias",
        "key:type:alias",
        "key:type:name",

        // identity elements
        "identity:language:type",
        "identity:script:type",
        "identity:territory:type",
        "identity:variant:type",

    };

    /**
     * The set of element:attribute pair in which the attribute should be
     * treated as value. All the attribute here are non-distinguishing attributes.
     */
    public static final Set<String> ATTR_AS_VALUE_SET =
        new HashSet<String>(Arrays.asList(ATTR_AS_VALUE_LIST));

    /**
     * For those attributes that are treated as values, they taken the form of
     * element_name: { ..., attribute: value, ...}
     * This is desirable as an element may have several attributes that are
     * treated as values. But in some cases, there is one such attribute only,
     * and it is more desirable to convert
     * element_name: { attribute: value}
     * to
     * element_name: value
     * With a solid example,
     * <likelySubtag from="zh" to="zh_Hans_CN" />
     * distinguishing attr "from" will become the key, its better to
     * omit "to" and have this simple mapping:
     * "zh" : "zh_Hans_CN",
     */
    private static final String[] COMPACTABLE_ATTR_AS_VALUE_LIST = {
        // common/main
        "calendars:default:choice",
        "dateFormats:default:choice",
        "months:default:choice",
        "monthContext:default:choice",
        "days:default:choice",
        "dayContext:default:choice",
        "timeFormats:default:choice",
        "dateTimeFormats:default:choice",
        "timeZoneNames:singleCountries:list",

        // common/supplemental
        "likelySubtags:likelySubtag:to",
        //"territoryContainment:group:type",
        "calendar:calendarSystem:type",
        "calendarPreferenceData:calendarPreference:ordering",
        "codesByTerritory:telephoneCountryCode:code",

        // common/collation
        "collations:default:choice",

        // identity elements
        "identity:language:type",
        "identity:script:type",
        "identity:territory:type",
        "identity:variant:type",
    };

    /**
     * The set of attributes that should be treated as value, and reduce to
     * simple value only form.
     */
    public static final Set<String> COMPACTABLE_ATTR_AS_VALUE_SET =
        new HashSet<String>(Arrays.asList(COMPACTABLE_ATTR_AS_VALUE_LIST));

    /**
     * Anonymous key name.
     */
    public static final String ANONYMOUS_KEY = "_";

    /**
     * Check if the attribute should be suppressed.
     * 
     * Right now only "_q" is suppressed. In most cases array is used and there
     * is no need for this information. In other cases, order is irrelevant.
     * 
     * @return True if the attribute should be suppressed.
     */
    public static boolean IsSuppresedAttr(String attr) {
        return attr.endsWith("_q") || attr.endsWith("-q");
    }

    /**
     * The set of attributes that should be ignored in the conversion process.
     */
    public static final Set<String> IGNORABLE_NONDISTINGUISHING_ATTR_SET =
        Builder.with(new HashSet<String>())
            .add("draft")
            .add("references").freeze();

    /**
     * List of attributes that should be suppressed.
     * This list comes form cldr/common/supplemental/supplementalMetadata. Each
     * three of them is a group, they are for element, value and attribute.
     * If the specified attribute appears in specified element with specified =
     * value, it should be suppressed.
     */
    public static final String[] ATTR_SUPPRESS_LIST = {
        // common/main
        "dateFormat", "standard", "type",
        "dateTimeFormat", "standard", "type",
        "timeFormat", "standard", "type",
        "decimalFormat", "standard", "type",
        "percentFormat", "standard", "type",
        "scientificFormat", "standard", "type",
        "pattern", "standard", "type",
    };

    /**
     * This is a simple class to hold the splittable attribute specification.
     */
    public static class SplittableAttributeSpec {
        public String element;
        public String attribute;
        public String attrAsValueAfterSplit;

        SplittableAttributeSpec(String el, String attr, String av) {
            element = el;
            attribute = attr;
            attrAsValueAfterSplit = av;
        }
    }

    /**
     * List of attributes that has value that can be split. Each two of them is a
     * group, and represent element and value. Occurrences of such match should
     * lead to creation of multiple node.
     * Example:
     * <weekendStart day="thu" territories="DZ KW OM SA SD YE AF IR"/>
     * should be treated as if following node is encountered.
     * <weekendStart day="thu" territories="DZ"/>
     * <weekendStart day="thu" territories="KW"/>
     * <weekendStart day="thu" territories="OM"/>
     * <weekendStart day="thu" territories="SA"/>
     * <weekendStart day="thu" territories="SD"/>
     * <weekendStart day="thu" territories="YE"/>
     * <weekendStart day="thu" territories="AF"/>
     * <weekendStart day="thu" territories="IR"/>
     */
    public static final SplittableAttributeSpec[] SPLITTABLE_ATTRS = {
        new SplittableAttributeSpec("calendarPreference", "territories", null),
        new SplittableAttributeSpec("pluralRules", "locales", null),
        new SplittableAttributeSpec("minDays", "territories", "count"),
        new SplittableAttributeSpec("firstDay", "territories", "day"),
        new SplittableAttributeSpec("weekendStart", "territories", "day"),
        new SplittableAttributeSpec("weekendEnd", "territories", "day"),
        new SplittableAttributeSpec("measurementSystem", "territories", "type"),
        new SplittableAttributeSpec("paperSize", "territories", "type"),
        new SplittableAttributeSpec("parentLocale", "locales", "parent"),
        new SplittableAttributeSpec("hours", "regions", null),
        new SplittableAttributeSpec("dayPeriodRules", "locales", null),
        // new SplittableAttributeSpec("group", "contains", "group"),
        new SplittableAttributeSpec("personList", "locales", "type")
    };

    /**
     * The set that contains all timezone type of elements.
     */
    public static final Set<String> TIMEZONE_ELEMENT_NAME_SET =
        Builder.with(new HashSet<String>())
            .add("zone").add("timezone")
            .add("zoneItem").add("typeMap").freeze();

    /**
     * There are a handful of attribute values that are more properly represented as an array of strings rather than
     * as a single string.  
     */
    public static final Set<String> ATTRVALUE_AS_ARRAY_SET =
        Builder.with(new HashSet<String>())
            .add("territories").add("scripts").add("contains").freeze();

    /**
     * Following is the list of elements that need to be sorted before output.
     * 
     * Time zone item is split to multiple level, and each level should be
     * grouped together. The locale list in "dayPeriodRule" could be split to
     * multiple items, and items for each locale should be grouped together.
     */
    public static final String[] ELEMENT_NEED_SORT = {
        "zone", "timezone", "zoneItem", "typeMap", "dayPeriodRule",
        "pluralRules", "personList", "calendarPreferenceData", "character-fallback", "types", "timeData", "minDays",
        "firstDay", "weekendStart", "weekendEnd"
    };

    /**
     * Some elements in CLDR has multiple children of the same type of element.
     * We would like to treat them as array.
     */
    public static final Pattern ARRAY_ITEM_PATTERN = Pattern.compile(
        "(.*/collation[^/]*/rules[^/]*/" +
            "|.*/character-fallback[^/]*/character[^/]*/" +
            "|.*/dayPeriodRuleSet[^/]*/dayPeriodRules[^/]*/" +
            "|.*/languageMatching[^/]*/languageMatches[^/]*/" +
            "|.*/windowsZones[^/]*/mapTimezones[^/]*/" +
            "|.*/metaZones[^/]*/mapTimezones[^/]*/" +
            "|.*/segmentation[^/]*/variables[^/]*/" +
            "|.*/segmentation[^/]*/exceptions[^/]*/" +
            "|.*/transform[^/]*/tRules[^/]*/" +
            "|.*/region/region[^/]*/" +
            "|.*/keyword[^/]*/key[^/]*/" +
            "|.*/telephoneCodeData[^/]*/codesByTerritory[^/]*/" +
            "|.*/metazoneInfo[^/]*/timezone\\[[^\\]]*\\]/" +
            "|.*/metadata[^/]*/validity[^/]*/" +
            "|.*/metadata[^/]*/suppress[^/]*/" +
            "|.*/metadata[^/]*/deprecated[^/]*/" +
            ")(.*)");

    /**
      * Number elements without a numbering system are there only for compatibility purposes.
      * We automatically suppress generation of JSON objects for them.
      */
    public static final Pattern NO_NUMBERING_SYSTEM_PATTERN = Pattern
        .compile("//ldml/numbers/(symbols|(decimal|percent|scientific|currency)Formats)/.*");
    public static final Pattern NUMBERING_SYSTEM_PATTERN = Pattern
        .compile("//ldml/numbers/(symbols|miscPatterns|(decimal|percent|scientific|currency)Formats)\\[@numberSystem=\"([^\"]++)\"\\]/.*");
    public static final String[] ACTIVE_NUMBERING_SYSTEM_XPATHS = {
        "//ldml/numbers/defaultNumberingSystem",
        "//ldml/numbers/otherNumberingSystems/native",
        "//ldml/numbers/otherNumberingSystems/traditional",
        "//ldml/numbers/otherNumberingSystems/finance"
    };

    /**
     * A simple class to hold the specification of a path transformation.
     */
    public static class PathTransformSpec {
        public Pattern pattern;
        public String replacement;

        PathTransformSpec(String patternStr, String replacement) {
            pattern = Pattern.compile(patternStr);
            this.replacement = replacement;
        }
    }

    /**
     * Some special transformation, like add an additional layer, can be easily
     * done by transforming the path. Following rules covers these kind of
     * transformation.
     * Note: It is important to keep the order for these rules. Whenever a
     * rule matches, further rule won't be applied.
     */
    public static final PathTransformSpec PATH_TRANSFORMATIONS[] = {
        // Add "standard" as type attribute to exemplarCharacter element if there
        // is none, and separate them to two layers.
        new PathTransformSpec(
            "(.*ldml/exemplarCharacters)\\[@type=\"([^\"]*)\"\\](.*)", "$1/$2$3"),
        new PathTransformSpec("(.*ldml/exemplarCharacters)(.*)$", "$1/standard$2"),

        // Add cldrVersion attribute
        new PathTransformSpec("(.*/identity/version\\[@number=\"([^\"]*)\")(\\])", "$1" + "\\]\\[@cldrVersion=\""
            + CLDRFile.GEN_VERSION + "\"\\]"),
        // Add cldrVersion attribute to supplemental data
        new PathTransformSpec("(.*/supplementalData/version\\[@number=\"([^\"]*)\")(\\])", "$1" + "\\]\\[@cldrVersion=\""
            + CLDRFile.GEN_VERSION + "\"\\]"),

        // Transform underscore to hyphen-minus in language keys
        new PathTransformSpec("(.*/language\\[@type=\"[a-z]{2,3})_([^\"]*\"\\](\\[@alt=\"short\"])?)", "$1-$2"),

        // Separate "ellipsis" from its type as another layer.
        new PathTransformSpec("(.*/ellipsis)\\[@type=\"([^\"]*)\"\\](.*)$",
            "$1/$2$3"),

        // Remove unnecessary dateFormat/pattern
        new PathTransformSpec(
            "(.*/calendars)/calendar\\[@type=\"([^\"]*)\"\\](.*)Length\\[@type=\"([^\"]*)\"\\]/(date|time|dateTime)Format\\[@type=\"([^\"]*)\"\\]/pattern\\[@type=\"([^\"]*)\"\\](.*)",
            "$1/$2/$5Formats/$4$8"),

        // Separate calendar type
        new PathTransformSpec("(.*/calendars)/calendar\\[@type=\"([^\"]*)\"\\](.*)$",
            "$1/$2$3"),

        // Separate "metazone" from its type as another layer.
        new PathTransformSpec("(.*/metazone)\\[@type=\"([^\"]*)\"\\]/(.*)$", "$1/$2/$3"),

        // Split out types into its various fields
        new PathTransformSpec("(.*)/types/type\\[@type=\"([^\"]*)\"\\]\\[@key=\"([^\"]*)\"\\](.*)$",
            "$1/types/$3/$2$4"),

        new PathTransformSpec(
            "(.*/numbers/(decimal|scientific|percent|currency)Formats\\[@numberSystem=\"([^\"]*)\"\\])/(decimal|scientific|percent|currency)FormatLength/(decimal|scientific|percent|currency)Format\\[@type=\"standard\"]/pattern.*$",
            "$1/standard"),

        new PathTransformSpec(
            "(.*/numbers/currencyFormats\\[@numberSystem=\"([^\"]*)\"\\])/currencyFormatLength/currencyFormat\\[@type=\"accounting\"]/pattern.*$",
            "$1/accounting"),
        // Add "type" attribute with value "standard" if there is no "type" in
        // "decimalFormatLength".
        new PathTransformSpec(
            "(.*/numbers/(decimal|scientific|percent)Formats\\[@numberSystem=\"([^\"]*)\"\\]/(decimal|scientific|percent)FormatLength)/(.*)$",
            "$1[@type=\"standard\"]/$5"),

        new PathTransformSpec(
            "(.*/listPattern)/(.*)$", "$1[@type=\"standard\"]/$2"),

        new PathTransformSpec("(.*/languagePopulation)\\[@type=\"([^\"]*)\"\\](.*)",
            "$1/$2$3"),

        new PathTransformSpec("(.*/languageAlias)\\[@type=\"([^\"]*)\"\\](.*)", "$1/$2$3"),
        new PathTransformSpec("(.*/scriptAlias)\\[@type=\"([^\"]*)\"\\](.*)", "$1/$2$3"),
        new PathTransformSpec("(.*/territoryAlias)\\[@type=\"([^\"]*)\"\\](.*)", "$1/$2$3"),
        new PathTransformSpec("(.*/variantAlias)\\[@type=\"([^\"]*)\"\\](.*)", "$1/$2$3"),
        new PathTransformSpec("(.*/zoneAlias)\\[@type=\"([^\"]*)\"\\](.*)", "$1/$2$3"),
        new PathTransformSpec("(.*/alias)(.*)", "$1/alias$2"),

        new PathTransformSpec("(.*currencyData/region)(.*)", "$1/region$2"),

        new PathTransformSpec("(.*/transforms/transform[^/]*)/(.*)", "$1/tRules/$2"),
    };
}
