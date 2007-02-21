package org.unicode.cldr.test;

import org.unicode.cldr.util.CLDRFile;
import org.unicode.cldr.util.ICUServiceBuilder;
import org.unicode.cldr.util.XPathParts;

import com.ibm.icu.dev.test.util.TransliteratorUtilities;
import com.ibm.icu.impl.CollectionUtilities;
import com.ibm.icu.text.Collator;
import com.ibm.icu.text.DateFormat;
import com.ibm.icu.text.DecimalFormat;
import com.ibm.icu.text.MessageFormat;
import com.ibm.icu.text.SimpleDateFormat;
import com.ibm.icu.text.UnicodeSet;
import com.ibm.icu.util.Calendar;
import com.ibm.icu.util.TimeZone;
import com.ibm.icu.util.ULocale;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ExampleGenerator {
  public enum Zoomed {
    /** For the zoomed-out view. */
    OUT,
    /** For the zoomed-in view */
    IN
  };

  private final static boolean CACHING = false;

  private final double NUMBER_SAMPLE = 12345.6789;

  private final static TimeZone ZONE_SAMPLE = TimeZone.getTimeZone("America/Indianapolis");

  private final static Date DATE_SAMPLE;

  private final static Date DATE_SAMPLE2;

  private final static String EXEMPLAR_CITY = "Europe/Rome";

  private String backgroundStart = "<span style='background-color: gray'>";

  private String backgroundEnd = "</span>";

  static {
    Calendar c = Calendar.getInstance(ZONE_SAMPLE, ULocale.ENGLISH);
    c.set(1999, 8, 14, 13, 25, 59); // 1999-09-13 13:25:59
    DATE_SAMPLE = c.getTime();
    c.set(1999, 9, 27, 13, 25, 59); // 1999-09-13 13:25:59
    DATE_SAMPLE2 = c.getTime();
  }

  private Collator col;

  private CLDRFile cldrFile;

  private Map<String, String> cache = new HashMap();

  private static final String NONE = "\uFFFF";

  // Matcher skipMatcher = Pattern.compile(
  // "/localeDisplayNames(?!"
  // ).matcher("");
  private XPathParts parts = new XPathParts();

  private ICUServiceBuilder icuServiceBuilder = new ICUServiceBuilder();

  public String getBackgroundEnd() {
    return backgroundEnd;
  }

  /**
   * For setting the end of the "background" style. Default is "</span>". 
   * It is used in composing patterns, so it can show the part that corresponds to the value. 
   * @return
   */
  public void setBackgroundEnd(String backgroundEnd) {
    this.backgroundEnd = backgroundEnd;
  }

  public String getBackgroundStart() {
    return backgroundStart;
  }

  /**
   * For setting the "background" style. Default is "<span style='background-color: gray'>". 
   * It is used in composing patterns, so it can show the part that corresponds to the value. 
   * @return
   */
  public void setBackgroundStart(String backgroundStart) {
    this.backgroundStart = backgroundStart;
  }

  public ExampleGenerator(CLDRFile resolvedCLDRFile) {
    this.cldrFile = resolvedCLDRFile;
    icuServiceBuilder.setCldrFile(resolvedCLDRFile);
    col = Collator.getInstance(new ULocale(resolvedCLDRFile.getLocaleID()));
  }

  /**
   * Returns an example string, in html, if there is one for this path,
   * otherwise null. For use in the survey tool, an example might be returned
   * *even* if there is no value in the locale. For example, the locale might
   * have a path that Engish doesn't, but you want to return the best English
   * example. <br>
   * The result is valid HTML.
   * 
   * @param xpath
   * @param zoomed
   * @return
   */
public String getExampleHtml(String xpath, String value, Zoomed zoomed) {
    if (value == null) {
      return null; // for now
    }
    String result = null;
    String cacheKey;
    if (CACHING) {
      cacheKey = xpath + "," + value + "," + zoomed;
      result = cache.get(cacheKey);
      if (result != null) {
        if (result == NONE) {
          return null;
        }
        return result;
      }
    }
    // result is null at this point. Get the real value if we can.
    
    main: {
      parts.set(xpath);
      if (parts.contains("dateRangePattern")) { // {0} - {1}
        SimpleDateFormat dateFormat = icuServiceBuilder.getDateFormat("gregorian", 2, 0);
        result = MessageFormat.format(value, new Object[]{setBackground(dateFormat.format(DATE_SAMPLE)), setBackground(dateFormat.format(DATE_SAMPLE2))});
        break main;
      }
      if (parts.contains("timeZoneNames")) {
        if (parts.contains("regionFormat")) { // {0} Time
          String sampleTerritory = cldrFile.getName(CLDRFile.TERRITORY_NAME, "JP", false);
          result = MessageFormat.format(value, new Object[]{setBackground(sampleTerritory)});
        } else if (parts.contains("fallbackFormat")) { // {1} ({0})
          String timeFormat = setBackground(cldrFile.getStringValue("//ldml/dates/timeZoneNames/regionFormat"));
          String us = setBackground(cldrFile.getName(CLDRFile.TERRITORY_NAME, "US", false));
          //ldml/dates/timeZoneNames/zone[@type="America/Los_Angeles"]/exemplarCity =  Λος Άντζελες
          String LosAngeles = setBackground(cldrFile.getStringValue("//ldml/dates/timeZoneNames/zone[@type=\"America/Los_Angeles\"]/exemplarCity"));
          result = MessageFormat.format(value, new Object[]{LosAngeles, us});
          result = MessageFormat.format(timeFormat, new Object[]{result});
        } else if (parts.contains("gmtFormat")) { // GMT{0}
          result = getGMTFormat(null, value);          
        } else if (parts.contains("hourFormat")) { // +HH:mm;-HH:mm
          result = getGMTFormat(value, null);
        }
        break main;
      }
      if (true) break main; // for now
      if (xpath.contains("/exemplarCharacters")) {
        if (zoomed == Zoomed.IN) {
          UnicodeSet unicodeSet = new UnicodeSet(value);
          if (unicodeSet.size() < 500) {
            result = CollectionUtilities.prettyPrint(unicodeSet, false, null, null, col, col);
          }
        }
        break main;
      }
      if (xpath.contains("/localeDisplayNames")) {
        if (xpath.contains("/codePatterns")) {
          parts.set(xpath);
          result = MessageFormat.format(value, new Object[] { setBackground("CODE") });
        }
        break main;
      }
      if (parts.contains("currency") && parts.contains("symbol")) {
        String currency = parts.getAttributeValue(-2, "type");
        DecimalFormat x = icuServiceBuilder.getCurrencyFormat(currency);
        result = x.format(12345.6789);
        break main;
      }
      if (parts.contains("pattern") || parts.contains("dateFormatItem")) {
        if (parts.contains("calendar")) {
          String calendar = parts.findAttributeValue("calendar", "type");
          SimpleDateFormat dateFormat;
          if (parts.contains("dateTimeFormat")) {
            SimpleDateFormat date2 = icuServiceBuilder.getDateFormat(calendar, 2, 0); // date
            SimpleDateFormat time = icuServiceBuilder.getDateFormat(calendar, 0, 2); // time
            date2.applyPattern(MessageFormat.format(value, new Object[]{
                setBackground(time.toPattern()), setBackground(date2.toPattern())}));
            dateFormat = date2;
          } else if (parts.contains("[@id=\"NEW\"]")) {
            break main;
          } else {
            dateFormat = icuServiceBuilder.getDateFormat(calendar, value);
          }
          dateFormat.setTimeZone(ZONE_SAMPLE);
          result = dateFormat.format(DATE_SAMPLE);
        } else if (parts.contains("numbers")) {
          DecimalFormat numberFormat = icuServiceBuilder.getNumberFormat(value);
          result = numberFormat.format(NUMBER_SAMPLE);
        }
        break main;
      }
      if (parts.contains("symbol")) {
        DecimalFormat x = icuServiceBuilder.getNumberFormat(2);
        result = x.format(NUMBER_SAMPLE);
        break main;
      }
    }
    if (CACHING) {
      if (result == null) {
        cache.put(cacheKey, NONE);
      } else {
        // fix HTML, cache
        result = TransliteratorUtilities.toHTML.transliterate(result);
        cache.put(cacheKey, result);
      }
    }
    return result;
  }
  /**
   * Put a background on an item, skipping enclosed patterns.
   * @param sampleTerritory
   * @return
   */
  private String setBackground(String inputPattern) {
    Matcher m = PARAMETER.matcher(inputPattern);
    return backgroundStart + m.replaceAll(backgroundEnd + "$1" + backgroundStart) + backgroundEnd;
  }

  static final Pattern PARAMETER = Pattern.compile("(\\{[0-9]\\})");

  private String getGMTFormat(String gmtHourString, String gmtFormat) {
    if (gmtFormat == null) {
      gmtFormat = setBackground(cldrFile.getStringValue("//ldml/dates/timeZoneNames/gmtFormat"));
    }
    boolean hoursBackground = false;
    if (gmtHourString == null) {
      hoursBackground = true;
      gmtHourString = cldrFile.getStringValue("//ldml/dates/timeZoneNames/hourFormat");
    }
    String[] plusMinus = gmtHourString.split(";");
    SimpleDateFormat dateFormat = icuServiceBuilder.getDateFormat("gregorian", plusMinus[0]);
    dateFormat.setTimeZone(ZONE_SAMPLE);
    String hours = dateFormat.format(DATE_SAMPLE);
    if (hoursBackground) {
      hours = setBackground(hours);
    }
    String result = MessageFormat.format(gmtFormat, new Object[] { hours });
    return result;
  }

  /**
   * Return a help string, in html, that should be shown in the Zoomed view.
   * Presumably at the end of each help section is something like: <br>
   * &lt;br&gt;For more information, see <a
   * href='http://unicode.org/cldr/wiki?SurveyToolHelp/characters'>help</a>.
   * <br>
   * The result is valid HTML. <br>
   * TODO: add more help, and modify to get from property or xml file for easy
   * modification.
   * 
   * @return null if none available.
   */
  public String getHelpHtml(String xpath, String value) {
    String result = null;
    if (xpath.contains("/exemplarCharacters")) {
      result = "The standard exemplar characters are those used in customary writing ([a-z] for English; "
          + "the auxiliary characters are used in foreign words found in typical magazines, newspapers, &c.; "
          + "currency auxilliary characters are those used in currency symbols, like 'US$ 1,234'. ";
    }
    return result == null ? null : TransliteratorUtilities.toHTML.transliterate(result);
  }
}