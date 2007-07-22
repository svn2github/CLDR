package org.unicode.cldr.test;

import org.unicode.cldr.util.CLDRFile;
import org.unicode.cldr.util.ICUServiceBuilder;
import org.unicode.cldr.util.SimpleHtmlParser;
import org.unicode.cldr.util.SupplementalData;
import org.unicode.cldr.util.SupplementalDataInfo;
import org.unicode.cldr.util.TimezoneFormatter;
import org.unicode.cldr.util.Utility;
import org.unicode.cldr.util.XPathParts;
import org.unicode.cldr.util.SimpleHtmlParser.Type;

import com.ibm.icu.dev.test.util.TransliteratorUtilities;
import com.ibm.icu.impl.CollectionUtilities;
import com.ibm.icu.text.Collator;
import com.ibm.icu.text.DecimalFormat;
import com.ibm.icu.text.MessageFormat;
import com.ibm.icu.text.SimpleDateFormat;
import com.ibm.icu.text.UnicodeSet;
import com.ibm.icu.util.Calendar;
import com.ibm.icu.util.TimeZone;
import com.ibm.icu.util.ULocale;

import java.io.BufferedReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.IOException;
import java.text.ChoiceFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Class to generate examples and help messages for the Survey tool (or console version).
 * @author markdavis
 *
 */
public class ExampleGenerator {
  private final static boolean DEBUG_SHOW_HELP = false;

  private static SupplementalDataInfo supplementalDataInfo;
  private SupplementalData supplementalData;
  
  /**
   * Zoomed status.
   * @author markdavis
   *
   */
  public enum Zoomed {
    /** For the zoomed-out view. */
    OUT,
    /** For the zoomed-in view */
    IN
  };

  private final static boolean CACHING = false;

  public final static double NUMBER_SAMPLE = 12345.6789;

  public final static TimeZone ZONE_SAMPLE = TimeZone.getTimeZone("America/Indianapolis");

  public final static Date DATE_SAMPLE;

  private final static Date DATE_SAMPLE2;

  //private final static String EXEMPLAR_CITY = "Europe/Rome";

  private String backgroundStart = "<span class='substituted'>";

  private String backgroundEnd = "</span>";
  
  private boolean verboseErrors = false;

  private Calendar calendar = Calendar.getInstance(ZONE_SAMPLE, ULocale.ENGLISH);

  static {
    Calendar calendar = Calendar.getInstance(ZONE_SAMPLE, ULocale.ENGLISH);
    calendar.set(1999, 8, 14, 13, 25, 59); // 1999-09-13 13:25:59
    DATE_SAMPLE = calendar.getTime();
    calendar.set(1999, 9, 27, 13, 25, 59); // 1999-09-13 13:25:59
    DATE_SAMPLE2 = calendar.getTime();
  }

  private Collator col;

  private CLDRFile cldrFile;

  private Map<String, String> cache = new HashMap();

  private static final String NONE = "\uFFFF";

  private static final String backgroundStartSymbol = "\uE1234";

  private static final String backgroundEndSymbol = "\uE1235";

  // Matcher skipMatcher = Pattern.compile(
  // "/localeDisplayNames(?!"
  // ).matcher("");
  private XPathParts parts = new XPathParts();

  private ICUServiceBuilder icuServiceBuilder = new ICUServiceBuilder();

  private Set<String> singleCountryZones;

  /**
   * For getting the end of the "background" style. Default is "</span>". It is
   * used in composing patterns, so it can show the part that corresponds to the
   * value.
   * @return
   */
  public String getBackgroundEnd() {
    return backgroundEnd;
  }

  /**
   * For setting the end of the "background" style. Default is "</span>". It is
   * used in composing patterns, so it can show the part that corresponds to the
   * value.
   * 
   * @return
   */
  public void setBackgroundEnd(String backgroundEnd) {
    this.backgroundEnd = backgroundEnd;
  }

  /**
   * For getting the "background" style. Default is "<span
   * style='background-color: gray'>". It is used in composing patterns, so it
   * can show the part that corresponds to the value.
   * 
   * @return
   */
  public String getBackgroundStart() {
    return backgroundStart;
  }

  /**
   * For setting the "background" style. Default is "<span
   * style='background-color: gray'>". It is used in composing patterns, so it
   * can show the part that corresponds to the value.
   * 
   * @return
   */
  public void setBackgroundStart(String backgroundStart) {
    this.backgroundStart = backgroundStart;
  }
  
  /**
   * Set the verbosity level of internal errors. 
   * For example, setVerboseErrors(true) will cause 
   * full stack traces to be shown in some cases.
   */
  public void setVerboseErrors(boolean verbosity) {
    this.verboseErrors = verbosity;
  }

  /**
   * Create an Example Generator. If this is shared across threads, it must be synchronized.
   * @param resolvedCldrFile
   * @param supplementalDataDirectory
   */
  public ExampleGenerator(CLDRFile resolvedCldrFile, String supplementalDataDirectory) {
    cldrFile = resolvedCldrFile.getResolved();
    icuServiceBuilder.setCldrFile(cldrFile);
    col = Collator.getInstance(new ULocale(cldrFile.getLocaleID()));
    synchronized (ExampleGenerator.class) {
      if (supplementalDataInfo == null) {
        supplementalDataInfo = SupplementalDataInfo.getInstance(supplementalDataDirectory);
      }
      supplementalData = new SupplementalData(supplementalDataDirectory);
    }
    String singleCountriesPath = cldrFile.getFullXPath("//ldml/dates/timeZoneNames/singleCountries");
    if(singleCountriesPath == null) {
        System.err.println("Failure: in "+cldrFile.getLocaleID()+" examplegenerator- cldrFile.getFullXPath(//ldml/dates/timeZoneNames/singleCountries)==null");
    } else {
        parts.set(singleCountriesPath);
        singleCountryZones = new HashSet(Arrays.asList(parts.getAttributeValue(-1, "list").trim().split("\\s+")));
    }
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
   * @param zoomed status (IN, or OUT) Out is a longer version only called in Zoom mode. IN is called in both.
   * @return
   */
  public String getExampleHtml(String xpath, String value, Zoomed zoomed) {
    try {
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
          result = format(value, setBackground(dateFormat.format(DATE_SAMPLE)), setBackground(dateFormat.format(DATE_SAMPLE2)));
          result = finalizeBackground(result);
          break main;
        }
        if (parts.contains("timeZoneNames")) {
          if (parts.contains("exemplarCity")) {
            //        ldml/dates/timeZoneNames/zone[@type="America/Los_Angeles"]/exemplarCity
            String timezone = parts.getAttributeValue(3, "type");
            String countryCode = supplementalDataInfo.getZone_territory(timezone);
            if (countryCode == null) {
              break main; // fail, skip
            }
            if (countryCode.equals("001")) {
              // GMT code, so format.
              try {
                String hourOffset = timezone.substring(timezone.contains("+") ? 8 : 7);
                int hours = Integer.parseInt(hourOffset);
                result = getGMTFormat(null, null, hours);
              } catch (RuntimeException e) {
                break main; // fail, skip
              }
            } else {
              String countryName = setBackground(cldrFile.getName(CLDRFile.TERRITORY_NAME, countryCode));
              boolean singleZone = singleCountryZones.contains(timezone) || !supplementalDataInfo.getMultizones().contains(countryCode);
              // we show just country for singlezone countries
              if (singleZone) {
                result = countryName;
              } else {
                if (value == null) {
                  value = TimezoneFormatter.getFallbackName(timezone);
                }
                // otherwise we show the fallback with exemplar
                String fallback = setBackground(cldrFile.getWinningValue("//ldml/dates/timeZoneNames/fallbackFormat"));
                // ldml/dates/timeZoneNames/zone[@type="America/Los_Angeles"]/exemplarCity

                result = format(fallback, value, countryName);
              }
              // format with "{0} Time" or equivalent.
              String timeFormat = setBackground(cldrFile.getWinningValue("//ldml/dates/timeZoneNames/regionFormat"));
              result = format(timeFormat, result);
            }
          } else if (parts.contains("regionFormat")) { // {0} Time
            String sampleTerritory = cldrFile.getName(CLDRFile.TERRITORY_NAME, "JP");
            result = format(value, setBackground(sampleTerritory));
          } else if (parts.contains("fallbackFormat")) { // {1} ({0})
            if (value == null) {
              break main;
            }
            String timeFormat = setBackground(cldrFile.getWinningValue("//ldml/dates/timeZoneNames/regionFormat"));
            String us = setBackground(cldrFile.getName(CLDRFile.TERRITORY_NAME, "US"));
            // ldml/dates/timeZoneNames/zone[@type="America/Los_Angeles"]/exemplarCity

            String LosAngeles = setBackground(cldrFile.getWinningValue("//ldml/dates/timeZoneNames/zone[@type=\"America/Los_Angeles\"]/exemplarCity"));
            result = format(value, LosAngeles, us);
            result = format(timeFormat, result);
          } else if (parts.contains("gmtFormat")) { // GMT{0}
            result = getGMTFormat(null, value, -8);
          } else if (parts.contains("hourFormat")) { // +HH:mm;-HH:mm
            result = getGMTFormat(value, null, -8);
          } else if (parts.contains("metazone") && !parts.contains("commonlyUsed")) { // Metazone string
            if ( value != null && value.length() > 0 ) {
              result = getMZTimeFormat() + " " + value;
            }
            else {
              // TODO check for value
              if (parts.contains("generic")) {
                String metazone_name = parts.getAttributeValue(3, "type");
                String timezone = supplementalData.resolveParsedMetazone(metazone_name,"001");
                String countryCode = supplementalDataInfo.getZone_territory(timezone);
                String regionFormat = cldrFile.getWinningValue("//ldml/dates/timeZoneNames/regionFormat");
                String fallbackFormat = cldrFile.getWinningValue("//ldml/dates/timeZoneNames/fallbackFormat");
                String exemplarCity = cldrFile.getWinningValue("//ldml/dates/timeZoneNames/zone[@type=\""+timezone+"\"]/exemplarCity");
                if ( exemplarCity == null )
                   exemplarCity = timezone.substring(timezone.lastIndexOf('/')+1).replace('_',' ');

                String countryName = cldrFile.getWinningValue("//ldml/localeDisplayNames/territories/territory[@type=\""+countryCode+"\"]");
                boolean singleZone = singleCountryZones.contains(timezone) || !(supplementalDataInfo.getMultizones().contains(countryCode));

                if ( singleZone ) {
                  result = setBackground(getMZTimeFormat() + " " + 
                            format(regionFormat, countryName));
                }
                else {
                  result = setBackground(getMZTimeFormat() + " " + 
                            format(fallbackFormat, exemplarCity, countryName));
                }
              }
              else {
                String gmtFormat = cldrFile.getWinningValue("//ldml/dates/timeZoneNames/gmtFormat");
                String hourFormat = cldrFile.getWinningValue("//ldml/dates/timeZoneNames/hourFormat");
                String metazone_name = parts.getAttributeValue(3, "type");
                String tz_string = supplementalData.resolveParsedMetazone(metazone_name,"001");
                TimeZone currentZone = TimeZone.getTimeZone(tz_string);
                int tzOffset = currentZone.getRawOffset();
                if (parts.contains("daylight")) {
                   tzOffset += currentZone.getDSTSavings();
                }
                int MILLIS_PER_MINUTE = 1000 * 60;
                int MILLIS_PER_HOUR = MILLIS_PER_MINUTE * 60;
                int tm_hrs = tzOffset / MILLIS_PER_HOUR;
                int tm_mins = ( tzOffset % MILLIS_PER_HOUR ) / 60000; // millis per minute
                result = setBackground(getMZTimeFormat() + " " + getGMTFormat( hourFormat,gmtFormat,tm_hrs,tm_mins));
              }
            }
          }
          result = finalizeBackground(result);
          break main;
        }
        if (xpath.contains("/exemplarCharacters")) {
          if (value != null) {
            if (zoomed == Zoomed.IN) {
              UnicodeSet unicodeSet = new UnicodeSet(value);
              if (unicodeSet.size() < 500) {
                result = CollectionUtilities.prettyPrint(unicodeSet, false, null, null, col, col);
              }
            }
          }
          break main;
        }
        if (xpath.contains("/localeDisplayNames")) {
          if (xpath.contains("/codePatterns")) {
            //parts.set(xpath);
            result = format(value, setBackground("CODE"));
            result = finalizeBackground(result);
          } else if (parts.contains("languages") ) {
            String type = parts.getAttributeValue(-1, "type");
            if (type.contains("_")) {
              if (value != null && !value.equals(type)) {
                result = value;
              } else {
                result = cldrFile.getName(parts.set(xpath).findAttributeValue("language", "type"));
              }
            }
          }
          break main;
        }
        if (parts.contains("currency") && parts.contains("symbol")) {
          String currency = parts.getAttributeValue(-2, "type");
          String fullPath = cldrFile.getFullXPath(xpath, false);
          if (fullPath != null && fullPath.contains("[@choice=\"true\"]")) {
            ChoiceFormat cf = new ChoiceFormat(value);
            value = cf.format(NUMBER_SAMPLE);
          }
          // TODO fix to use value!!
          DecimalFormat x = icuServiceBuilder.getCurrencyFormat(currency, value);
          result = x.format(NUMBER_SAMPLE);
          result = setBackground(result).replace(value, backgroundEndSymbol + value + backgroundStartSymbol);
          result = finalizeBackground(result);
          break main;
        }
        if (parts.contains("pattern") || parts.contains("dateFormatItem")) {
          if (parts.contains("calendar")) {
            String calendar = parts.findAttributeValue("calendar", "type");
            SimpleDateFormat dateFormat;
            if (parts.contains("dateTimeFormat")) {
              SimpleDateFormat date2 = icuServiceBuilder.getDateFormat(calendar, 2, 0); // date
              SimpleDateFormat time = icuServiceBuilder.getDateFormat(calendar, 0, 2); // time
              date2.applyPattern(format(value, setBackground(time.toPattern()), setBackground(date2.toPattern())));
              dateFormat = date2;
            } else {
              String id = parts.findAttributeValue("dateFormatItem", "id");
              if ("NEW".equals(id) || value == null) {
                result = "<i>n/a</i>";
                break main;
              } else {
                dateFormat = icuServiceBuilder.getDateFormat(calendar, value);
              }
            }
            dateFormat.setTimeZone(ZONE_SAMPLE);
            result = dateFormat.format(DATE_SAMPLE);
            result = finalizeBackground(result);
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
    } catch (NullPointerException e) {
      return null;
    } catch (RuntimeException e) {
      String unchained = verboseErrors?("<br>"+unchainException(e)):"";
      return zoomed == Zoomed.OUT 
          ? "<i>internal error</i>"
          : /*TransliteratorUtilities.toHTML.transliterate*/("<i>internal error: " + e.getClass().getName() + ", " + e.getMessage() + "</i>"+unchained);
    }
  }
  
  public String format(String format, Object...objects ) {
    if (format == null) return null;
    return MessageFormat.format(format, objects);
  }

  public static final String unchainException(Exception e) {
    String stackStr = "[unknown stack]<br>";
    try {
        StringWriter asString = new StringWriter();
        e.printStackTrace(new PrintWriter(asString));
        stackStr = "<pre>" + asString.toString() +"</pre>";
    } catch ( Throwable tt ) {
        // ...
    }
    return stackStr;
  }


  /**
   * Put a background on an item, skipping enclosed patterns.
   * 
   * @param sampleTerritory
   * @return
   */
  private String setBackground(String inputPattern) {
    Matcher m = PARAMETER.matcher(inputPattern);
    return backgroundStartSymbol + m.replaceAll(backgroundEndSymbol + "$1" + backgroundStartSymbol) + backgroundEndSymbol;
  }

  /**
   * This is called just before we return a result. It fixes the special characters that were added by setBackground.
   * @param input string with special characters from setBackground.
   * @return string with HTML for the background.
   */
  private String finalizeBackground(String input) {
    return input == null ? input : input.replace(backgroundStartSymbol + backgroundEndSymbol, "") // remove
        // null
        // runs
        .replace(backgroundEndSymbol + backgroundStartSymbol, "") // remove null
        // runs
        .replace(backgroundStartSymbol, backgroundStart).replace(backgroundEndSymbol, backgroundEnd);
  }

  static final Pattern PARAMETER = Pattern.compile("(\\{[0-9]\\})");

  /**
   * Utility to format using a gmtHourString, gmtFormat, and an integer hours. We only need the hours because that's all
   * the TZDB IDs need. Should merge this eventually into TimeZoneFormatter and call there.
   * @param gmtHourString
   * @param gmtFormat
   * @param hours
   * @return
   */
  private String getGMTFormat(String gmtHourString, String gmtFormat, int hours) {
     return getGMTFormat(gmtHourString,gmtFormat,hours,0);
  }

  private String getGMTFormat(String gmtHourString, String gmtFormat, int hours, int minutes) {
    boolean hoursBackground = false;
    if (gmtHourString == null) {
      hoursBackground = true;
      gmtHourString = cldrFile.getWinningValue("//ldml/dates/timeZoneNames/hourFormat");
    }
    if (gmtFormat == null) {
      hoursBackground = false; // for the hours case
      gmtFormat = setBackground(cldrFile.getWinningValue("//ldml/dates/timeZoneNames/gmtFormat"));
    }
    String[] plusMinus = gmtHourString.split(";");

    SimpleDateFormat dateFormat = icuServiceBuilder.getDateFormat("gregorian", plusMinus[hours >= 0 ? 0 : 1]);
    dateFormat.setTimeZone(ZONE_SAMPLE);
    calendar.set(1999, 9, 27, Math.abs(hours), minutes, 0); // 1999-09-13 13:25:59
    Date sample = calendar.getTime();
    String hourString = dateFormat.format(sample);
    if (hoursBackground) {
      hourString = setBackground(hourString);
    }
    String result = MessageFormat.format(gmtFormat, new Object[] { hourString });
    return result;
  }

  private String getMZTimeFormat() {
    String timeFormat = cldrFile.getWinningValue("//ldml/dates/calendars/calendar[@type=\"gregorian\"]/timeFormats/timeFormatLength[@type=\"short\"]/timeFormat[@type=\"standard\"]/pattern[@type=\"standard\"]");
    if ( timeFormat == null ) {
       timeFormat = "HH:mm";
    }
    // the following is <= because the TZDB inverts the hours
    SimpleDateFormat dateFormat = icuServiceBuilder.getDateFormat("gregorian", timeFormat);
    dateFormat.setTimeZone(ZONE_SAMPLE);
    calendar.set(1999, 9, 13, 13, 25, 59); // 1999-09-13 13:25:59
    Date sample = calendar.getTime();
    String result = dateFormat.format(sample);
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
    synchronized (this) {
      if (helpMessages == null) {
        helpMessages = new HelpMessages("test_help_messages.html");
      }
    }
    return helpMessages.find(xpath);
    //  if (xpath.contains("/exemplarCharacters")) {
    //  result = "The standard exemplar characters are those used in customary writing ([a-z] for English; "
    //  + "the auxiliary characters are used in foreign words found in typical magazines, newspapers, &c.; "
    //  + "currency auxilliary characters are those used in currency symbols, like 'US$ 1,234'. ";
    //  }
    //  return result == null ? null : TransliteratorUtilities.toHTML.transliterate(result);
  }

  HelpMessages helpMessages;

  /**
   * Private class to get the messages from a help file.
   */
  public static class HelpMessages {
    List<Matcher> keys = new ArrayList();

    List<String> values = new ArrayList();

    enum Status {
      BASE, BEFORE_CELL, IN_CELL, IN_INSIDE_TABLE
    };

    StringBuilder[] currentColumn = new StringBuilder[2];

    int column = 0;

    public HelpMessages(String filename) {
      currentColumn[0] = new StringBuilder();
      currentColumn[1] = new StringBuilder();
      BufferedReader in;
      try {
        in = Utility.getUTF8Data(filename);
        Status status = Status.BASE;
        int count = 0;
        int tableCount = 0;

        boolean inContent = false;
        // if the table level is 1 (we are in the main table), then we look for <td>...</td><td>...</td>. That means that we have column 1 and column 2.
        
        SimpleHtmlParser simple = new SimpleHtmlParser().setReader(in);
        StringBuilder result = new StringBuilder();
        boolean hadPop = false;
        main:
          while (true) {
            Type x = simple.next(result);
            switch (x) {
              case ELEMENT: // with /table we pop the count
                if (SimpleHtmlParser.equals("table", result)) {
                  if (hadPop) { 
                    --tableCount;
                  } else {
                    ++tableCount;
                  }
                } else if (tableCount == 1) { 
                  if (SimpleHtmlParser.equals("tr", result)) {
                    if (hadPop) {
                      addHelpMessages();
                    }
                    column = 0;
                  } else if (SimpleHtmlParser.equals("td", result)) {
                    if (hadPop) { 
                      inContent = false;
                      ++column;
                    } else {
                      inContent = true;
                      continue main; // skip adding
                    }
                  }
                }
                break;
              case ELEMENT_POP:
                hadPop = true;
                break;
              case ELEMENT_END:
                hadPop = false;
                break;
              case DONE:
                break main;
            }
            if (inContent) {
              SimpleHtmlParser.writeResult(x, result, currentColumn[column]);
            }
          }
      
        in.close();
      } catch (IOException e) {
        System.err.println("Can't initialize help text");
      }
    }

    private void appendLine(String line) {
      if (line.length() != 0) {
        if (currentColumn[column].length() > 0) {
          currentColumn[column].append(" ");
        }
        currentColumn[column].append(line);
      }
    }

    public String find(String xpath) {
      StringBuilder result = new StringBuilder();
      for (int i = 0; i < keys.size(); ++i) {
        if (keys.get(i).reset(xpath).matches()) {
          if (result.length() != 0) {
            result.append("\r\n");
          }
          result.append(values.get(i));
        }
      }
      if (result.length() != 0) {
        return result.toString();
      }
      return null;
    }

    private void addHelpMessages() {
      if (column == 2) { // must have two columns
        try {
          // remove the first character and the last two characters, since the are >....</
          String key = currentColumn[0].substring(1, currentColumn[0].length()-2).trim();
          String value = currentColumn[1].substring(1, currentColumn[1].length()-2).trim();
          if (DEBUG_SHOW_HELP) {
            System.out.println("{" + key + "} => {" + value + "}");
          }
          Matcher m = Pattern.compile(TransliteratorUtilities.fromHTML.transliterate(key), Pattern.COMMENTS).matcher("");
          keys.add(m);
          values.add(value);
        } catch (RuntimeException e) {
          System.err.println("Help file has illegal regex: " + currentColumn[0]);
        }
      }
      currentColumn[0].setLength(0);
      currentColumn[1].setLength(0);
      column = 0;
    }
  }
}
