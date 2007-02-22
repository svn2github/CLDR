package org.unicode.cldr.util;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.ibm.icu.impl.CollectionUtilities;
import com.ibm.icu.text.ChineseDateFormat;
import com.ibm.icu.text.ChineseDateFormatSymbols;
import com.ibm.icu.text.DateFormat;
import com.ibm.icu.text.DateFormatSymbols;
import com.ibm.icu.text.DecimalFormat;
import com.ibm.icu.text.DecimalFormatSymbols;
import com.ibm.icu.text.MessageFormat;
import com.ibm.icu.text.NumberFormat;
import com.ibm.icu.text.SimpleDateFormat;
import com.ibm.icu.text.UTF16;
import com.ibm.icu.text.UnicodeSet;
import com.ibm.icu.util.Calendar;
import com.ibm.icu.util.Currency;
import com.ibm.icu.util.TimeZone;
import com.ibm.icu.util.ULocale;

import org.unicode.cldr.util.CLDRFile.Factory;

public class ICUServiceBuilder {
  public static Currency NO_CURRENCY = Currency.getInstance("XXX");
  private CLDRFile cldrFile;
  
  private static TimeZone utc = TimeZone.getTimeZone("GMT");
  private static DateFormat iso = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", ULocale.ENGLISH);
  static {
    iso.setTimeZone(utc);
  }
  
  static public String isoDateFormat(Date date) {
    return iso.format(date);
  }
  public static String isoDateFormat(long value) {
    // TODO Auto-generated method stub
    return iso.format(new Date(value));
  }
  static public Date isoDateParse(String date) throws ParseException {
    return iso.parse(date);
  }
  
  private Map<String, SimpleDateFormat> cacheDateFormats = new HashMap();
  private Map<String, DateFormatSymbols> cacheDateFormatSymbols = new HashMap();
  private Map<String, NumberFormat> cacheNumberFormats = new HashMap();
  DecimalFormatSymbols cacheDecimalFormatSymbols = null;
  
//private Factory cldrFactory;
//public ICUServiceBuilder setCLDRFactory(Factory cldrFactory) {
//this.cldrFactory = cldrFactory;
//dateFormatCache.clear();
//return this; // for chaining
//}
  
  private static int[] DateFormatValues = {-1, DateFormat.SHORT, DateFormat.MEDIUM, DateFormat.LONG, DateFormat.FULL};
  private static String[] DateFormatNames = {"none", "short", "medium", "long", "full"};
  
  public static String getDateNames(int i) {
    return DateFormatNames[i];
  }
  
  public static int LIMIT_DATE_FORMAT_INDEX = DateFormatValues.length;
  
  private static final String[] Days = {"sun", "mon", "tue", "wed", "thu", "fri", "sat"};
  
//public SimpleDateFormat getDateFormat(CLDRFile cldrFile, int dateIndex, int timeIndex) {
////CLDRFile cldrFile = cldrFactory.make(localeID.toString(), true);
//return getDateFormat(dateIndex, timeIndex);
//}
  
  public CLDRFile getCldrFile() {
    return cldrFile;
  }
  public ICUServiceBuilder setCldrFile(CLDRFile cldrFile) {
    this.cldrFile = cldrFile.getResolved();
    cacheDateFormats.clear();
    cacheNumberFormats.clear();
    cacheDateFormatSymbols.clear();
    cacheDecimalFormatSymbols = null;
    return this;
  }
  
  public SimpleDateFormat getDateFormat(String calendar, int dateIndex, int timeIndex) {
    String key = cldrFile.getLocaleID() + "," + calendar + "," + dateIndex + "," + timeIndex;
    SimpleDateFormat result = (SimpleDateFormat) cacheDateFormats.get(key);
    if (result != null) return (SimpleDateFormat) result.clone();
    if (false && dateIndex == 2 && timeIndex == 0) {
      System.out.println("");
    }
    
    //Document doc = LDMLUtilities.getFullyResolvedLDML(sourceDir, locale.toString(), false, false, false);
    //Node dates = LDMLUtilities.getNode(doc, "//ldml/dates/calendars/calendar[@type=\"gregorian\"]");
    
    String pattern = getPattern(calendar, dateIndex, timeIndex);
    
    result = getFullFormat(calendar, pattern);
    cacheDateFormats.put(key, result);
    //System.out.println("created " + key);
    return (SimpleDateFormat) result.clone();
  }
  
  public SimpleDateFormat getDateFormat(String calendar, String pattern) {
    String key = cldrFile.getLocaleID() + "," + calendar + ",," + pattern;
    SimpleDateFormat result = (SimpleDateFormat) cacheDateFormats.get(key);
    if (result != null) return (SimpleDateFormat) result.clone();
    result = getFullFormat(calendar, pattern);
    cacheDateFormats.put(key, result);
    //System.out.println("created " + key);
    return (SimpleDateFormat) result.clone();
  }
  
  private SimpleDateFormat getFullFormat(String calendar, String pattern) {
    SimpleDateFormat result = calendar.equals("chinese")
      ? new ChineseDateFormat(pattern, new ULocale(cldrFile.getLocaleID())) // formatData
      : new SimpleDateFormat(pattern, new ULocale(cldrFile.getLocaleID())); // formatData      
    // TODO Serious Hack, until #4915 is fixed
    Calendar cal = Calendar.getInstance(new ULocale("en@calendar=" + calendar));
    // TODO look these up and set them
    //cal.setFirstDayOfWeek()
    //cal.setMinimalDaysInFirstWeek()
    cal.setTimeZone(utc);
    result.setCalendar(cal);
    
    result.setDateFormatSymbols((DateFormatSymbols) _getDateFormatSymbols(calendar).clone());

    //formatData.setZoneStrings();  
    
    DecimalFormat numberFormat = (DecimalFormat) getNumberFormat(1);
    numberFormat.setGroupingUsed(false);
    numberFormat.setDecimalSeparatorAlwaysShown(false);
    numberFormat.setParseIntegerOnly(true); /* So that dd.MM.yy can be parsed */
    numberFormat.setMinimumFractionDigits(0); // To prevent "Jan 1.00, 1997.00"
    
    result.setNumberFormat((NumberFormat)numberFormat.clone());
    return result;
  }
  
  private DateFormatSymbols _getDateFormatSymbols(String calendar) {
    String key = cldrFile.getLocaleID() + "," + calendar;
    DateFormatSymbols result = cacheDateFormatSymbols.get(key);
    if (result != null) return (DateFormatSymbols) result.clone();

    DateFormatSymbols gregorianBackup = null;
    boolean notGregorian = !calendar.equals("gregorian");
    String[] last;
    DateFormatSymbols formatData = calendar.equals("chinese") ? new ChineseDateFormatSymbols() : new DateFormatSymbols();
    
    String prefix = "//ldml/dates/calendars/calendar[@type=\""+ calendar + "\"]/";
    formatData.setAmPmStrings(last = new String[] {
        cldrFile.getStringValue(prefix + "am"),
        cldrFile.getStringValue(prefix + "pm")});
    checkFound(last);
//    if (last[0] == null && notGregorian) {
//      if (gregorianBackup == null) gregorianBackup = _getDateFormatSymbols("gregorian");
//      formatData.setAmPmStrings(last = gregorianBackup.getAmPmStrings());
//    }
    
    int minEras = calendar.equals("chinese") ? 0 : 1;
    
    List temp = getArray(prefix + "eras/eraAbbr/era[@type=\"", 0, null, "\"]", minEras);
    formatData.setEras(last = (String[])temp.toArray(new String[temp.size()]));
    if (minEras != 0) checkFound(last);
//    if (temp.size() < 2 && notGregorian) {
//      if (gregorianBackup == null) gregorianBackup = _getDateFormatSymbols("gregorian");
//      formatData.setEras(last = gregorianBackup.getEras());
//    }
    
    temp = getArray(prefix + "eras/eraNames/era[@type=\"", 0, null, "\"]", minEras);
    formatData.setEraNames(last = (String[])temp.toArray(new String[temp.size()]));
    if (minEras != 0) checkFound(last);
//    if (temp.size() < 2 && notGregorian) {
//      if (gregorianBackup == null) gregorianBackup = _getDateFormatSymbols("gregorian");
//      formatData.setEraNames(last = gregorianBackup.getEraNames());
//    }
    
    formatData.setMonths(getArray(prefix, "month", "format", "wide"), DateFormatSymbols.FORMAT, DateFormatSymbols.WIDE);  
    formatData.setMonths(getArray(prefix, "month", "format", "abbreviated"), DateFormatSymbols.FORMAT, DateFormatSymbols.ABBREVIATED);
    formatData.setMonths(getArray(prefix, "month", "format", "narrow"), DateFormatSymbols.FORMAT, DateFormatSymbols.NARROW);
    
    formatData.setMonths(getArray(prefix, "month", "stand-alone", "wide"), DateFormatSymbols.STANDALONE, DateFormatSymbols.WIDE);
    formatData.setMonths(getArray(prefix, "month", "stand-alone", "abbreviated"), DateFormatSymbols.STANDALONE, DateFormatSymbols.ABBREVIATED);
    formatData.setMonths(getArray(prefix, "month", "stand-alone", "narrow"), DateFormatSymbols.STANDALONE, DateFormatSymbols.NARROW);
    
    //formatData.setWeekdays(getArray(prefix, "day", "format", "wide"));
//    if (last == null && notGregorian) {
//      if (gregorianBackup == null) gregorianBackup = _getDateFormatSymbols("gregorian");
//      formatData.setWeekdays(gregorianBackup.getWeekdays());
//    }
    
    formatData.setWeekdays(getArray(prefix, "day", "format", "wide"), DateFormatSymbols.FORMAT, DateFormatSymbols.WIDE);
    formatData.setWeekdays(getArray(prefix, "day", "format", "abbreviated"), DateFormatSymbols.FORMAT, DateFormatSymbols.ABBREVIATED);
    formatData.setWeekdays(getArray(prefix, "day", "format", "narrow"), DateFormatSymbols.FORMAT, DateFormatSymbols.NARROW);
    
    formatData.setWeekdays(getArray(prefix, "day", "stand-alone", "wide"), DateFormatSymbols.STANDALONE, DateFormatSymbols.WIDE);
    formatData.setWeekdays(getArray(prefix, "day", "stand-alone", "abbreviated"), DateFormatSymbols.STANDALONE, DateFormatSymbols.ABBREVIATED);
    formatData.setWeekdays(getArray(prefix, "day", "stand-alone", "narrow"), DateFormatSymbols.STANDALONE, DateFormatSymbols.NARROW);

    cacheDateFormatSymbols.put(key, formatData);
    return formatData;
  }
  
  private void checkFound(String[] last) {
    if (last == null || last.length == 0 || last[0] == null) {
      throw new IllegalArgumentException("Failed to load array");
    }
  }
  private String getPattern(String calendar, int dateIndex, int timeIndex) {
    String pattern;
    if (DateFormatValues[timeIndex] == -1) pattern = getDateTimePattern(calendar, "date", DateFormatNames[dateIndex]);
    else if (DateFormatValues[dateIndex] == -1) pattern = getDateTimePattern(calendar, "time", DateFormatNames[timeIndex]);
    else {
      String p0 = getDateTimePattern(calendar, "time", DateFormatNames[timeIndex]);
      String p1 = getDateTimePattern(calendar, "date", DateFormatNames[dateIndex]);
      String datetimePat = getDateTimePattern(calendar, "dateTime", "");
      pattern = MessageFormat.format(datetimePat, new String[]{p0, p1});
    }
    return pattern;
  }
  
  /**
   * @param calendar TODO
   * 
   */
  private String getDateTimePattern(String calendar, String dateOrTime, String type) {
    if (type.length() > 0) type = "[@type=\"" + type + "\"]";
    String key = "//ldml/dates/calendars/calendar[@type=\"" + calendar + "\"]/"
    + dateOrTime + "Formats/" 
    + dateOrTime + "FormatLength"
    + type + "/" + dateOrTime + "Format[@type=\"standard\"]/pattern[@type=\"standard\"]";
    
    String value = cldrFile.getStringValue(key);
    if (value == null) throw new IllegalArgumentException("locale: " + cldrFile.getLocaleID() + "\tpath: " + key + "\r\nvalue: " + value);
    return value;
  }
  
  private String[] getArray(String key, String type, String context, String width) {
    String prefix = key + type + "s/" 
    + type + "Context[@type=\"" + context + "\"]/"
    + type + "Width[@type=\"" + width + "\"]/"
    + type + "[@type=\"";
    String postfix = "\"]";
    boolean isDay = type.equals("day");
    List temp = getArray(prefix, isDay ? 0 : 1, isDay ? Days : null, postfix, isDay ? 7 : 12);
    if (isDay) temp.add(0,"");
    String[] result = (String[])temp.toArray(new String[temp.size()]);
    checkFound(result);
    return result;
  }
  
  static final Matcher gregorianMonthsMatcher = Pattern.compile(".*gregorian.*months.*").matcher("");
  
  private List getArray(String prefix, int firstIndex, String[] itemNames, String postfix, int minimumSize) {
    //int length = isMonth ? 12 : 7;
    //String[] result = new String[length];
    ArrayList result = new ArrayList();
    String lastType;
    for (int i = firstIndex; ; ++i) {
      lastType = itemNames != null && i < itemNames.length ? itemNames[i] : String.valueOf(i);
      String item = cldrFile.getStringValue(prefix + lastType + postfix);
      if (item == null) break;
      result.add(item);
    }
//  the following code didn't do anything, so I'm wondering what it was there for?
    // it's to catch errors
    if (result.size() < minimumSize) {
      throw new RuntimeException(); 
      //Collection s = CollectionUtilities.addAll(cldrFile.iterator(prefix), new TreeSet());//cldrFile.keySet(".*gregorian.*months.*", );
      //String item = cldrFile.getStringValue(prefix + lastType + postfix);
      //throw new IllegalArgumentException("Can't find enough items");
    }
    /* <months>
     <monthContext type="format">
     <monthWidth type="abbreviated">
     <month type="1">1</month>
     */
    return result;
  }
  
  private static String[] NumberNames = {"integer", "decimal", "percent", "scientific"}; //// "standard", , "INR", "CHF", "GBP"
  private static int firstReal = 1;
  private static int firstCurrency = 4;
  
  public String getNumberNames(int i) {
    return NumberNames[i];
  }
  
  public static int LIMIT_NUMBER_INDEX = NumberNames.length;
  
  private static class MyCurrency extends Currency {
    String symbol;
    String displayName;
    int fractDigits;
    double roundingIncrement;
    MyCurrency(String code, String symbol, String displayName, String fractDigits, String roundingIncrement) {
      super(code);
      this.symbol = symbol == null ? code : symbol;
      this.displayName = displayName == null ? code : displayName;
      this.fractDigits = fractDigits == null ? 2 : Integer.parseInt(fractDigits);
      this.roundingIncrement = roundingIncrement == null ? 0.0 
          : Integer.parseInt(roundingIncrement) * Math.pow(10.0, -this.fractDigits);
    }
    public String getName(ULocale locale,
        int nameStyle,
        boolean[] isChoiceFormat) {
      
      String result = nameStyle == 0 ? this.symbol
          : nameStyle == 1 ? getCurrencyCode()
              : nameStyle == 2 ? displayName
                  : null;
          if (result == null) throw new IllegalArgumentException();
          // snagged from currency
          isChoiceFormat[0] = false;
          int i=0;
          while (i < result.length() && result.charAt(i) == '=' && i < 2) {
            ++i;
          }
          isChoiceFormat[0]= (i == 1);
          if (i != 0) {
            // Skip over first mark
            result = result.substring(1);
          }
          return result;
    }
    
    /**
     * Returns the rounding increment for this currency, or 0.0 if no
     * rounding is done by this currency.
     * @return the non-negative rounding increment, or 0.0 if none
     * @stable ICU 2.2
     */
    public double getRoundingIncrement() {
      return roundingIncrement;
    }
    
    public int getDefaultFractionDigits() {
      return fractDigits;
    }
    public boolean equals(Object other) {
      MyCurrency that = (MyCurrency) other;
      return roundingIncrement == that.roundingIncrement
      && fractDigits == that.fractDigits
      && symbol.equals(that.symbol)
      && displayName.equals(that.displayName);
    }
    private int hashCode(Object other) {
      MyCurrency that = (MyCurrency) other;
      return (((int)roundingIncrement*37 + fractDigits)*37 + symbol.hashCode())*37
      + displayName.hashCode();
    }
  }
  
  static int CURRENCY = 0, OTHER_KEY = 1, PATTERN = 2;
  
  public DecimalFormat getCurrencyFormat(String currency) {
    //CLDRFile cldrFile = cldrFactory.make(localeID, true);
    return _getNumberFormat(currency, CURRENCY, null);
  }
  
  public DecimalFormat getCurrencyFormat(String currency, String currencySymbol) {
    //CLDRFile cldrFile = cldrFactory.make(localeID, true);
    return _getNumberFormat(currency, CURRENCY, currencySymbol);
  }
  
  public DecimalFormat getNumberFormat(int index) {
    //CLDRFile cldrFile = cldrFactory.make(localeID, true);
    return _getNumberFormat(NumberNames[index], OTHER_KEY, null);
  }
  
  public DecimalFormat getNumberFormat(String pattern) {
    //CLDRFile cldrFile = cldrFactory.make(localeID, true);
    return _getNumberFormat(pattern, PATTERN, null);
  }
  
  private DecimalFormat _getNumberFormat(String key1, int kind, String currencySymbol) {
    ULocale ulocale = new ULocale(cldrFile.getLocaleID());
    String key = ulocale + "/" + key1 + "/" + kind;
    DecimalFormat result = (DecimalFormat) cacheNumberFormats.get(key);
    if (result != null) return result;
    
    String pattern = kind == PATTERN ? key1 : getPattern(key1, kind);
    
    DecimalFormatSymbols symbols = _getDecimalFormatSymbols();
    /*
     currencySymbol.equals(other.currencySymbol) &&
     intlCurrencySymbol.equals(other.intlCurrencySymbol) &&
     padEscape == other.padEscape && // [NEW]
     monetarySeparator == other.monetarySeparator);
     */
    MyCurrency mc = null;
    if (kind == CURRENCY) {
      
      String prefix = "//ldml/numbers/currencies/currency[@type=\"" + key1 + "\"]/";
      // /ldml/numbers/currencies/currency[@type="GBP"]/symbol
      // /ldml/numbers/currencies/currency[@type="GBP"]
      
      if (currencySymbol == null) {
        currencySymbol = cldrFile.getStringValue(prefix + "symbol");
      }
      String currencyDecimal = cldrFile.getStringValue(prefix + "decimal");
      if (currencyDecimal != null) {
        (symbols = cloneIfNeeded(symbols)).setMonetaryDecimalSeparator(currencyDecimal.charAt(0));
      }
      String currencyPattern = cldrFile.getStringValue(prefix + "pattern");
      if (currencyPattern != null) {
        pattern = currencyPattern;
      }
      
      String currencyGrouping = cldrFile.getStringValue(prefix + "grouping");
      if (currencyGrouping != null) {
        (symbols = cloneIfNeeded(symbols)).setMonetaryGroupingSeparator(currencyGrouping.charAt(0));
      }
      
      //<decimal>,</decimal>
      //<group>.</group>
      
      // TODO This is a hack for now, since I am ignoring the possibility of quoted text next to the symbol
      if (pattern.contains(";")) { // multi pattern
        String[] pieces = pattern.split(";");
        for (int i = 0; i < pieces.length; ++i) {
          pieces[i] = fixCurrencySpacing(pieces[i], currencySymbol);
        }
        pattern = org.unicode.cldr.util.Utility.join(pieces, ";");
      } else {
        pattern = fixCurrencySpacing(pattern, currencySymbol);
      }
      
      mc = new MyCurrency(key1, 
          currencySymbol, 
          cldrFile.getStringValue(prefix + "displayName"),
          null, null);
      
//    String possible = null;
//    possible = cldrFile.getStringValue(prefix + "decimal"); 
//    symbols.setMonetaryDecimalSeparator(possible != null ? possible.charAt(0) : symbols.getDecimalSeparator());
//    if ((possible = cldrFile.getStringValue(prefix + "pattern")) != null) pattern = possible;
//    if ((possible = cldrFile.getStringValue(prefix + "group")) != null) symbols.setGroupingSeparator(possible.charAt(0));
      //; 
    }
    result = new DecimalFormat(pattern, symbols);
    if (mc != null) {
      result.setCurrency(mc);
    } else {
      result.setCurrency(NO_CURRENCY);
    }
    
    if (false) {
      System.out.println("creating " + ulocale + "\tkey: " + key + "\tpattern "
          + pattern + "\tresult: " + result.toPattern() + "\t0=>" + result.format(0));
      DecimalFormat n2 = (DecimalFormat) NumberFormat.getScientificInstance(ulocale);
      System.out.println("\tresult: " + n2.toPattern() + "\t0=>" + n2.format(0));
    }
    if (kind == OTHER_KEY && key1.equals("integer")) {
      result.setMaximumFractionDigits(0);
      result.setDecimalSeparatorAlwaysShown(false);
      result.setParseIntegerOnly(true);
    }
    cacheNumberFormats.put(key, result);
    return result;
  }
  private String fixCurrencySpacing(String pattern, String symbol) {
    int startPos = pattern.indexOf('\u00a4');
    if (startPos > 0 
        && beforeCurrencyMatch.contains(UTF16.charAt(symbol,0))) {
      int ch = UTF16.charAt(pattern, startPos-1);
      if (ch == '#') ch = '0';// fix pattern
      if (beforeSurroundingMatch.contains(ch)) {
        pattern = pattern.substring(0,startPos) + beforeInsertBetween + pattern.substring(startPos);
      }
    }
    int endPos = pattern.lastIndexOf('\u00a4') + 1;
    if (endPos < pattern.length() 
        && afterCurrencyMatch.contains(UTF16.charAt(symbol,symbol.length()-1))) {
      int ch = UTF16.charAt(pattern, endPos);
      if (ch == '#') ch = '0';// fix pattern
      if (afterSurroundingMatch.contains(ch)) {
        pattern = pattern.substring(0,endPos) + afterInsertBetween + pattern.substring(endPos);
      }
    }
    return pattern;
  }
  
  private DecimalFormatSymbols cloneIfNeeded(DecimalFormatSymbols symbols) {
    if (symbols == _getDecimalFormatSymbols()) {
      return (DecimalFormatSymbols) symbols.clone();
    }
    return symbols;
  }
  
  private DecimalFormatSymbols _getDecimalFormatSymbols() {
    DecimalFormatSymbols symbols = cacheDecimalFormatSymbols;
    if (symbols != null) return symbols;

    symbols = new DecimalFormatSymbols();
    
    // currently constants
    // symbols.setPadEscape(cldrFile.getStringValue("//ldml/numbers/symbols/xxx"));
    // symbols.setSignificantDigit(cldrFile.getStringValue("//ldml/numbers/symbols/patternDigit"));
    
    symbols.setDecimalSeparator(cldrFile.getStringValue("//ldml/numbers/symbols/decimal").charAt(0));
    symbols.setDigit(cldrFile.getStringValue("//ldml/numbers/symbols/patternDigit").charAt(0));
    symbols.setExponentSeparator(cldrFile.getStringValue("//ldml/numbers/symbols/exponential"));
    symbols.setGroupingSeparator(cldrFile.getStringValue("//ldml/numbers/symbols/group").charAt(0));
    symbols.setInfinity(cldrFile.getStringValue("//ldml/numbers/symbols/infinity"));
    symbols.setMinusSign(cldrFile.getStringValue("//ldml/numbers/symbols/minusSign").charAt(0));
    symbols.setNaN(cldrFile.getStringValue("//ldml/numbers/symbols/nan"));
    symbols.setPatternSeparator(cldrFile.getStringValue("//ldml/numbers/symbols/list").charAt(0));
    symbols.setPercent(cldrFile.getStringValue("//ldml/numbers/symbols/percentSign").charAt(0));
    symbols.setPerMill(cldrFile.getStringValue("//ldml/numbers/symbols/perMille").charAt(0));
    symbols.setPlusSign(cldrFile.getStringValue("//ldml/numbers/symbols/plusSign").charAt(0));
    symbols.setZeroDigit(cldrFile.getStringValue("//ldml/numbers/symbols/nativeZeroDigit").charAt(0));
    
    symbols.setMonetaryDecimalSeparator(symbols.getDecimalSeparator());
    symbols.setMonetaryGroupingSeparator(symbols.getGroupingSeparator());
    
    String prefix = "//ldml/numbers/currencyFormats/currencySpacing/beforeCurrency/";
    beforeCurrencyMatch = new UnicodeSet(cldrFile.getStringValue(prefix + "currencyMatch"));
    beforeSurroundingMatch = new UnicodeSet(cldrFile.getStringValue(prefix + "surroundingMatch"));
    beforeInsertBetween = cldrFile.getStringValue(prefix + "insertBetween");
    prefix = "//ldml/numbers/currencyFormats/currencySpacing/afterCurrency/";
    afterCurrencyMatch = new UnicodeSet(cldrFile.getStringValue(prefix + "currencyMatch"));
    afterSurroundingMatch = new UnicodeSet(cldrFile.getStringValue(prefix + "surroundingMatch"));
    afterInsertBetween = cldrFile.getStringValue(prefix + "insertBetween");

    cacheDecimalFormatSymbols = symbols;
    
    return symbols;
  }
  
  UnicodeSet beforeCurrencyMatch;
  UnicodeSet beforeSurroundingMatch;
  String beforeInsertBetween;
  UnicodeSet afterCurrencyMatch;
  UnicodeSet afterSurroundingMatch;
  String afterInsertBetween;

  private String getPattern(String key1, int isCurrency) {
    String prefix = "//ldml/numbers/";
    String type = key1;
    if (isCurrency == CURRENCY) type = "currency";
    else if (key1.equals("integer")) type = "decimal";
    String path = prefix
    + type + "Formats/" 
    + type + "FormatLength/"
    + type + "Format[@type=\"standard\"]/pattern[@type=\"standard\"]";
    
    String pattern = cldrFile.getStringValue(path);
    if (pattern == null) throw new IllegalArgumentException("locale: " + cldrFile.getLocaleID() + "\tpath: " + path);
    return pattern;
  }
  
}