/*
 ******************************************************************************
 * Copyright (C) 2005-2008, International Business Machines Corporation and        *
 * others. All Rights Reserved.                                               *
 ******************************************************************************
 */
package org.unicode.cldr.test;

import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.unicode.cldr.test.CheckCLDR.CheckStatus.Subtype;
import org.unicode.cldr.util.CLDRFile;
import org.unicode.cldr.util.InternalCldrException;
import org.unicode.cldr.util.XMLSource;
import org.unicode.cldr.util.CLDRFile.Status;

import com.ibm.icu.dev.test.util.PrettyPrinter;
import com.ibm.icu.lang.UScript;
import com.ibm.icu.text.Collator;
import com.ibm.icu.text.Normalizer2;
import com.ibm.icu.text.UnicodeSet;
import com.ibm.icu.util.ULocale;

public class CheckForExemplars extends CheckCLDR {
    //private final UnicodeSet commonAndInherited = new UnicodeSet(CheckExemplars.Allowed).complement(); 
    // "[[:script=common:][:script=inherited:][:alphabetic=false:]]");
    static String[] EXEMPLAR_SKIPS = {"/currencySpacing", "/hourFormat", "/exemplarCharacters", "/pattern",
        "/localizedPatternChars", "/segmentations", "/dateFormatItem", "/references", "/unitPattern",
        "/intervalFormatItem",
        "/localeDisplayNames/variants/",
        "/commonlyUsed",
        "/defaultNumberingSystem",
        "/exponential",
        "/nan",
        "/inText"
    };

    static final UnicodeSet START_PAREN = new UnicodeSet("[(\\[（［]").freeze();
    static final UnicodeSet END_PAREN = new UnicodeSet("[)\\]］）]").freeze();
    private UnicodeSet exemplars;
    private static final UnicodeSet DISALLOWED_IN_scriptRegionExemplars = new UnicodeSet("[()（）;,；，]").freeze();
    private static final UnicodeSet DISALLOWED_IN_scriptRegionExemplarsWithParens = new UnicodeSet("[;,；，]").freeze();
    private UnicodeSet currencySymbolExemplars;
    private boolean skip;
    private Collator col;
    private Collator spaceCol;
    private String informationMessage;
    PrettyPrinter prettyPrint;
    private Status otherPathStatus = new Status();
    private Matcher patternMatcher = ExampleGenerator.PARAMETER.matcher("");
    public static final Pattern SUPPOSED_TO_BE_MESSAGE_FORMAT_PATTERN = Pattern.compile("/(" +
            "codePattern" +
            "|dateRangePattern" +
            "|dateTimeFormat[^/]*?/pattern" +
            "|appendItem" +
            "|intervalFormatFallback" +
            "|hoursFormat" +
            "|gmtFormat" +
            "|regionFormat" +
            "|fallbackRegionFormat" +
            "|fallbackFormat" +
            "|unitPattern.*@count=\"(zero|one|two|few|many|other)\"" +
            "|localePattern" +
            "|listPatternPart" +
            "|ellipsis" +
    ")");
    private Matcher supposedToBeMessageFormat = SUPPOSED_TO_BE_MESSAGE_FORMAT_PATTERN.matcher("");

    public static final Pattern LEAD_OR_TRAIL_WHITESPACE_OK = Pattern.compile("/(" +
            "localeSeparator" +
            "|references/reference" +
            "|insertBetween" +
    ")");
    private Matcher leadOrTrailWhitespaceOk = LEAD_OR_TRAIL_WHITESPACE_OK.matcher("");

    private static UnicodeSet ASCII_UPPERCASE = (UnicodeSet) new UnicodeSet("[A-Z]").freeze();
    private static UnicodeSet ASCII = (UnicodeSet) new UnicodeSet("[\\u0020-\\u007F]").freeze();

    static final Pattern IS_COUNT_ZERO_ONE_TWO = Pattern.compile("/units.*\\[@count=\"(zero|one|two)\"");
    private Matcher isCountZeroOneTwo = IS_COUNT_ZERO_ONE_TWO.matcher("");
    private boolean hasSpecialPlurals;

    public CheckCLDR setCldrFileToCheck(CLDRFile cldrFile, Map<String, String> options, List<CheckStatus> possibleErrors) {
        if (cldrFile == null) return this;
        skip = true;
        super.setCldrFileToCheck(cldrFile, options, possibleErrors);
        if (cldrFile.getLocaleID().equals("root")) {
            return this;
        }
        String locale = cldrFile.getLocaleID();
        hasSpecialPlurals = locale.equals("ar") || locale.startsWith("ar_");
        informationMessage = "<a href='http://unicode.org/cldr/apps/survey?_=" + locale + "&x=characters'>characters</a>";
        col = Collator.getInstance(new ULocale(locale));
        spaceCol = Collator.getInstance(new ULocale(locale));
        spaceCol.setStrength(col.PRIMARY);

        CLDRFile resolvedFile = cldrFile.getResolved();
        boolean[] ok = new boolean[1];
        exemplars = safeGetExemplars("", possibleErrors, resolvedFile, ok);
        if (!ok[0]) exemplars = new UnicodeSet();

        if (exemplars == null) {
            CheckStatus item = new CheckStatus().setCause(this).setMainType(CheckStatus.errorType).setSubtype(Subtype.noExemplarCharacters)
            .setMessage("No Exemplar Characters: {0}", new Object[]{this.getClass().getName()});
            possibleErrors.add(item);
            return this;
        }
        //UnicodeSet temp = resolvedFile.getExemplarSet("standard");
        //if (temp != null) exemplars.addAll(temp);
        UnicodeSet auxiliary = safeGetExemplars("auxiliary", possibleErrors, resolvedFile, ok); // resolvedFile.getExemplarSet("auxiliary", CLDRFile.WinningChoice.WINNING);
        if (auxiliary != null) exemplars.addAll(auxiliary);
        exemplars.addAll(CheckExemplars.AlwaysOK).freeze();

        currencySymbolExemplars = safeGetExemplars("currencySymbol", possibleErrors, resolvedFile, ok); // resolvedFile.getExemplarSet("currencySymbol", CLDRFile.WinningChoice.WINNING);
        if (currencySymbolExemplars == null) {
            currencySymbolExemplars = new UnicodeSet(exemplars);
        } else {
            currencySymbolExemplars.addAll(exemplars);
        }
        skip = false;
        prettyPrint = new PrettyPrinter()
        .setOrdering(col != null ? col : Collator.getInstance(ULocale.ROOT))
        .setSpaceComparator(col != null ? col : Collator.getInstance(ULocale.ROOT)
                .setStrength2(Collator.PRIMARY))
                .setCompressRanges(true);
        return this;
    }

    private UnicodeSet safeGetExemplars(String type, List possibleErrors, CLDRFile resolvedFile, boolean[] ok) {
        UnicodeSet result = null;
        try {
            result = resolvedFile.getExemplarSet(type, CLDRFile.WinningChoice.WINNING);
            ok[0] = true;
        } catch(IllegalArgumentException iae) {
            possibleErrors.add(new CheckStatus()
            .setCause(this).setMainType(CheckStatus.errorType).setSubtype(Subtype.couldNotAccessExemplars)
            .setMessage("Could not get exemplar set: " + iae.toString()));
            ok[0] = false;
        }
        return result;
    }

    public CheckCLDR handleCheck(String path, String fullPath, String value,
            Map<String, String> options, List<CheckStatus> result) {
        if (fullPath == null) return this; // skip paths that we don't have
        if (value == null) return this; // skip values that we don't have ?
        if (skip) return this;
        if(path == null) { 
            throw new InternalCldrException("Empty path!");
        } else if(getCldrFileToCheck() == null) {
            throw new InternalCldrException("no file to check!");
        }
        String sourceLocale = getResolvedCldrFileToCheck().getSourceLocaleID(path, otherPathStatus);

        // if we are an alias to another path, then skip
        if (!path.equals(otherPathStatus.pathWhereFound)) {
            return this;
        }

        // now check locale source
        if (XMLSource.CODE_FALLBACK_ID.equals(sourceLocale)) {
            return this;
        } else if ("root".equals(sourceLocale)) {
            // skip eras for non-gregorian
            if (true) return this;
            if (path.indexOf("/calendar") >= 0 && path.indexOf("gregorian") <= 0) return this;
        }

        // add checks for patterns. Make sure that all and only the message format patterns have {n}
        boolean hasMessageFormatFields = patternMatcher.reset(value).find();
        boolean supposedToHaveMessageFormatFields = 
            supposedToBeMessageFormat.reset(path).find()
            && !(hasSpecialPlurals 
                    && isCountZeroOneTwo.reset(path).find());
        if (hasMessageFormatFields != supposedToHaveMessageFormatFields) {
            result.add(new CheckStatus().setCause(this).setMainType(CheckStatus.errorType)
                    .setSubtype(supposedToHaveMessageFormatFields ? Subtype.missingPlaceholders : Subtype.shouldntHavePlaceholders)
                    .setMessage(supposedToHaveMessageFormatFields 
                            ? "This field is a message pattern, and should have '{0}, {1},' etc. See the English for an example."
                                    : "This field is not a message pattern, and should not have '{0}, {1},' etc. See the English for an example.",
                                    new Object[]{}));

        }
        if (supposedToHaveMessageFormatFields) {
            // check the other characters in the message format patterns
            value = patternMatcher.replaceAll("#");
        } else {
            // end checks for patterns
            for (int i = 0; i < EXEMPLAR_SKIPS.length; ++i) {
                if (path.indexOf(EXEMPLAR_SKIPS[i]) > 0 ) return this; // skip some items.
            }
        }

        if (path.startsWith("//ldml/posix/messages")) return this;

        if (path.contains("/currency") && path.endsWith("/symbol")) {
            if (!containsAllCountingParens(currencySymbolExemplars, value)) {
                UnicodeSet missing = new UnicodeSet().addAll(value).removeAll(currencySymbolExemplars);
                addMissingMessage(missing, CheckStatus.warningType, Subtype.charactersNotInCurrencyExemplars, Subtype.asciiCharactersNotInCurrencyExemplars, "are not in the currency exemplar characters", result);
            }
        } else if (path.contains("/localeDisplayNames") && !path.contains("/localeDisplayPattern")) {
            // test first for outside of the set.
            if (!containsAllCountingParens(exemplars, value)) {
                UnicodeSet missing = new UnicodeSet().addAll(value).removeAll(exemplars);
                addMissingMessage(missing, CheckStatus.warningType, Subtype.charactersNotInMainOrAuxiliaryExemplars, Subtype.asciiCharactersNotInMainOrAuxiliaryExemplars, "are not in the exemplar characters", result);
            }
            UnicodeSet disallowedInExemplars = path.contains("_") ? DISALLOWED_IN_scriptRegionExemplarsWithParens : DISALLOWED_IN_scriptRegionExemplars;
            if (disallowedInExemplars.containsSome(value)) {
                UnicodeSet disallowed = new UnicodeSet().addAll(value).retainAll(disallowedInExemplars);
                addMissingMessage(disallowed, CheckStatus.warningType, Subtype.discouragedCharactersInTranslation, Subtype.discouragedCharactersInTranslation, "should not be used in this context", result);
                //
                //                String fixedMissing = prettyPrint
                //                .setToQuote(null)
                //                .setQuoter(null).format(missing);
                //                result.add(new CheckStatus().setCause(this).setMainType(CheckStatus.warningType).setSubtype(Subtype.discouragedCharactersInTranslation)
                //                        .setMessage("The characters \u200E{1}\u200E are discouraged in display names. Please avoid these characters.", new Object[]{null,fixedMissing}));
                //                // note: we are using {1} so that we don't include these in the console summary of bad characters.
            }
        } else if (!containsAllCountingParens(exemplars, value)) {
            UnicodeSet missing = new UnicodeSet().addAll(value).removeAll(exemplars);
            addMissingMessage(missing, CheckStatus.warningType, Subtype.charactersNotInMainOrAuxiliaryExemplars, Subtype.asciiCharactersNotInMainOrAuxiliaryExemplars, "are not in the exemplar characters", result);
        }

        // check for spaces 

        if (!value.equals(value.trim())) {
            if (!leadOrTrailWhitespaceOk.reset(path).find()) {
                result.add(new CheckStatus().setCause(this).setMainType(CheckStatus.errorType).setSubtype(Subtype.mustNotStartOrEndWithSpace)
                        .setMessage("This item must not start or end with whitespace, or be empty."));
            }
        }
        return this;
    }

    static final String TEST = "؉";
    private void addMissingMessage(UnicodeSet missing, String warningVsError, Subtype subtype, Subtype subtypeAscii, String qualifier, List<CheckStatus> result) {
        if (missing.containsAll(TEST)) {
            int x = 1;
        }
        String fixedMissing = prettyPrint.format(missing);
        BitSet scripts = new BitSet();
        for (String s : missing) {
            final int script = UScript.getScript(s.codePointAt(0));
            if (script == UScript.INHERITED || script == UScript.COMMON) {
                continue;
            }
            scripts.set(script);
        }
        StringBuilder scriptString = new StringBuilder();
        if (!scripts.isEmpty()) {
            scriptString.append("{");
            for (int i = scripts.nextSetBit(0); i >= 0; i = scripts.nextSetBit(i+1)) {
                if (scriptString.length() > 1) {
                    scriptString.append(", ");
                }
                scriptString.append(UScript.getName(i));
            }
            scriptString.append("}");
        }
        result.add(new CheckStatus()
        .setCause(this)
        .setMainType(warningVsError)
        .setSubtype(ASCII.containsAll(missing) ? subtypeAscii : subtype)
        .setMessage("The characters \u200E{0}\u200E {1} {2}. " +
                "For what to do, see <i>Handling Warnings</i> in <a target='CLDR-ST-DOCS' href='http://cldr.org/translation/characters#TOC-Handing-Warnings'>Characters</a>.", 
                new Object[]{fixedMissing, scriptString, qualifier}));
    }

    static final Normalizer2 NFC = Normalizer2.getInstance(null, "nfc", Normalizer2.Mode.COMPOSE);
    
    private boolean containsAllCountingParens(UnicodeSet exemplarSet, String value) {
        if (exemplarSet.containsAll(value)) {
            return true;
        }

        // Normalize
        value = NFC.normalize(value);
        
        // if we failed, then check that everything outside of () is ok.
        // and everything inside parens is either ASCII or in the set
        int lastPos = 0;
        while (true) {
            int start = START_PAREN.findIn(value, lastPos, false);
            String outside = value.substring(lastPos, start);
            if (!exemplarSet.containsAll(outside)) {
                return false;
            }
            if (start == value.length()) {
                break; // all done
            }
            ++start;
            int end = END_PAREN.findIn(value, start, false);
            // don't worry about mixed brackets
            String inside = value.substring(start, end);
            if (!exemplarSet.containsAll(inside) && !ASCII.containsAll(inside)) {
                return false;
            }
            if (end == value.length()) {
                break; // all done
            }
            lastPos = end + 1;
        }
        return true;
    }
}
