package org.unicode.cldr.test;

import org.unicode.cldr.util.CLDRFile;

import org.unicode.cldr.icu.CollectionUtilities;

import com.ibm.icu.lang.UCharacter;
import com.ibm.icu.text.Collator;
import com.ibm.icu.text.Normalizer;
import com.ibm.icu.text.UnicodeSet;
import com.ibm.icu.text.UnicodeSetIterator;

import org.unicode.cldr.test.DateTimePatternGenerator.FormatParser;
import com.ibm.icu.util.ULocale;

import java.util.List;
import java.util.Map;

/**
 * Class for processing the input and output of CLDR data for use in the
 * Survey Tool and other tools.
 */
public class DisplayAndInputProcessor {
  
  private Collator col;

  private Collator spaceCol;
  
  static final UnicodeSet RTL = new UnicodeSet("[[:Bidi_Class=Arabic_Letter:][:Bidi_Class=Right_To_Left:]]");

  /**
   * Constructor, taking cldrFile.
   * @param cldrFileToCheck
   */
  public DisplayAndInputProcessor(CLDRFile cldrFileToCheck) {
    String locale = cldrFileToCheck.getLocaleID();
    col = Collator.getInstance(new ULocale(locale));
    spaceCol = Collator.getInstance(new ULocale(locale));
  }

  /**
   * Constructor, taking locale.
   * @param locale
   */
  public DisplayAndInputProcessor(ULocale locale) {
    col = Collator.getInstance(locale);
    spaceCol = Collator.getInstance(locale);
  }

  /**
   * Process the value for display. The result is a string for display in the
   * Survey tool or similar program.
   * 
   * @param path
   * @param value
   * @param fullPath
   * @return
   */
  public String processForDisplay(String path, String value) {
    if (path.contains("exemplarCharacters")) {
      if (RTL.containsSome(value) && value.startsWith("[") && value.endsWith("]")) {
        return "\u200E[\u200E" + value.substring(1,value.length()-2) + "\u200E]\u200E";
      }
    }
    return value;
  }

  FormatParser formatDateParser = new FormatParser();
  /**
   * Process the value for input. The result is a cleaned-up value. For example,
   * an exemplar set is modified to be in the normal format, and any missing [ ]
   * are added (a common omission on entry). If there are any failures then the
   * original value is returned, so that the proper error message can be given.
   * 
   * @param path
   * @param value
   * @param internalException TODO
   * @param fullPath
   * @return
   */
  public String processInput(String path, String value, Exception[] internalException) {
    String original = value;
    if (internalException != null) {
      internalException[0] = null;
    }
    try {
      // fix grouping separator if space
      if (path.startsWith("//ldml/numbers/symbols/group")) {
        if (value.equals(" ")) {
          value = "\u00A0";
        }
      }
      // all of our values should not have leading or trailing spaces, except insertBetween
      if (!path.contains("/insertBetween") && !path.contains("/localeSeparator")) {
        value = value.trim();
      }

      // fix date patterns
      if (path.indexOf("/dates") >= 0
          && ((path.indexOf("/pattern") >= 0 && path.indexOf("/dateTimeFormat") < 0) || path
              .indexOf("/dateFormatItem") >= 0)) {
        formatDateParser.set(value);
        String newValue = formatDateParser.toString();
        if (!value.equals(newValue)) {
          value = newValue;
        }
      }
      
      if (path.startsWith("//ldml/numbers") && path.indexOf("Format[")>=0&& path.indexOf("/pattern")>=0) {
          String newValue = value.replaceAll("([%\u00A4]) ", "$1\u00A0")
                                 .replaceAll(" ([%\u00A4])", "\u00A0$1");
          if (!value.equals(newValue)) {
              value = newValue;
          }
      }

      // check specific cases
      if (path.contains("/exemplarCharacters")) {
        // clean up the user's input.
        // first, fix up the '['
        value = value.trim();

        if (!value.startsWith("[")) {
          value = "[" + value;
        }

        if (!value.endsWith("]")) {
          value = value + "]";
        }

        UnicodeSet exemplar = new UnicodeSet(value);
        UnicodeSet toAdd = new UnicodeSet();
        
        for (UnicodeSetIterator usi = new UnicodeSetIterator(exemplar); usi.next(); ) {
          final String string = usi.getString();
          if (string.equals("ß")) {
            continue;
          }
          final String newString = Normalizer.compose(UCharacter.toLowerCase(ULocale.ENGLISH, string), false);
          toAdd.add(newString);
        }
        exemplar.addAll(toAdd);

        exemplar.removeAll(CheckExemplars.TO_REMOVE_FROM_EXEMPLARS);
        String fixedExemplar = CollectionUtilities.prettyPrint(exemplar, true,
            null, null, col, col);
        UnicodeSet doubleCheck = new UnicodeSet(fixedExemplar);
        if (!exemplar.equals(doubleCheck)) {
          return original;
        }
        value = fixedExemplar;
      }
      return value;
    } catch (RuntimeException e) {
      if (internalException != null) {
        internalException[0] = e;
      }
      return original;
    }
  }
}
