/*
 ******************************************************************************
 * Copyright (C) 2004, International Business Machines Corporation and        *
 * others. All Rights Reserved.                                               *
 ******************************************************************************
*/
package org.unicode.cldr.test;

import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.xml.sax.SAXException;

import com.ibm.icu.dev.test.TestFmwk;
import org.unicode.cldr.util.CLDRFile;
import org.unicode.cldr.util.CLDRFile.StringValue;
import org.unicode.cldr.util.CLDRFile.Value;
import org.unicode.cldr.util.CLDRFile.XPathParts;
import com.ibm.icu.text.DecimalFormat;

public class CLDRTest extends TestFmwk {
	
	public static void main(String[] args) throws Exception {
		double deltaTime = System.currentTimeMillis();
        new CLDRTest().run(args);
        deltaTime = System.currentTimeMillis() - deltaTime;
        System.out.println("Seconds: " + deltaTime/1000);
    }
	
	Set locales;
	Set languageLocales;
	CLDRFile.Factory cldrFactory;
	
	public CLDRTest() throws SAXException, IOException {
		// TODO parameterize the directory and filter
		cldrFactory = CLDRFile.Factory.make("C:\\ICU4C\\locale\\common\\main\\", ".*", null);
		//CLDRKey.main(new String[]{"-mde.*"});
		locales = cldrFactory.getAvailable();
		languageLocales = cldrFactory.getAvailableLanguages();
	}
	
	public void TestCurrencyFormats() {
	    //String decimal = "/ldml/numbers/decimalFormats/decimalFormatLength/decimalFormat[@type=\"standard\"]/";
	    //String currency = "/ldml/numbers/currencyFormats/currencyFormatLength/currencyFormat[@type=\"standard\"]/";
		for (Iterator it = locales.iterator(); it.hasNext();) {
			String locale = (String)it.next();
			boolean isPOSIX = locale.indexOf("POSIX") >= 0;
			logln("Testing: " + locale);
			CLDRFile item = cldrFactory.make(locale, false);
			for (Iterator it2 = item.keySet().iterator(); it2.hasNext();) {
				String xpath = (String) it2.next();
				if (!xpath.startsWith("/ldml/numbers/")) {
					continue;
				}
				CLDRFile.StringValue value = (StringValue) item.getValue(xpath);
				byte type;
				if (xpath.startsWith("/ldml/numbers/currencyFormats/")) {
					type = CURRENCY_TYPE;
				} else if (xpath.startsWith("/ldml/numbers/decimalFormats/")) {
					type = DECIMAL_TYPE;
				} else if (xpath.startsWith("/ldml/numbers/percentFormats/")) {
					type = PERCENT_TYPE;
				} else if (xpath.startsWith("/ldml/numbers/scientificFormats/")) {
					type = SCIENTIFIC_TYPE;
				} else if (xpath.startsWith("/ldml/numbers/currencies/currency/")
						&& xpath.indexOf("pattern") >= 0) {
					type = CURRENCY_TYPE;
					System.out.println(xpath + value);
				} else {
					continue;
				}
				// at this point, we only have currency formats
				String pattern = getCanonicalPattern(value.getStringValue(), type, isPOSIX);
				if (!pattern.equals(value.getStringValue())) {
					String draft = "";
					if (value.getFullXPath().indexOf("[@draft=\"true\"]") >= 0) draft = " [draft]";
					assertEquals(locale + draft + " " + TYPE_NAME[type] + " pattern incorrect", pattern, value.getStringValue());
				}
			}
		}
	}
	
	static final byte CURRENCY_TYPE = 0, DECIMAL_TYPE = 1, PERCENT_TYPE = 2, SCIENTIFIC_TYPE = 3;
	static final String[] TYPE_NAME = {"currency", "decimal", "percent", "scientific"};
	static int[][] DIGIT_COUNT = {{1,2,2}, {1,0,3}, {1,0,0}, {0,0,0}};
	static int[][] POSIX_DIGIT_COUNT = {{1,2,2}, {1,0,6}, {1,0,0}, {1,6,6}};
	
	String getCanonicalPattern(String inpattern, byte type, boolean isPOSIX) {
		// TODO fix later to properly handle quoted ;
		DecimalFormat df = new DecimalFormat(inpattern);
		int decimals = type == CURRENCY_TYPE ? 2 : 1;
		int[] digits = isPOSIX ? POSIX_DIGIT_COUNT[type] : DIGIT_COUNT[type];
		df.setMinimumIntegerDigits(digits[0]);
		df.setMinimumFractionDigits(digits[1]);
		df.setMaximumFractionDigits(digits[2]);
		String pattern = df.toPattern();

		int pos = pattern.indexOf(';');
		if (pos < 0) return pattern + ";-" + pattern;
		return pattern;
	}
	
	static class ValueCount {
		int count = 1;
		Value value;
	}
	
	/**
	 * Verify that if all the children of a language locale do not have the same value for the same key.
	 */
	public void TestCommonChildren() {
		Map currentValues = new TreeMap();
		Set okValues = new TreeSet();
	
		for (Iterator it = languageLocales.iterator(); it.hasNext();) {
			String parent = (String)it.next();
			logln("Testing: " + parent);		
			currentValues.clear();
			okValues.clear();
			Set availableWithParent = cldrFactory.getAvailableWithParent(parent, true);
			for (Iterator it1 = availableWithParent.iterator(); it1.hasNext();) {
				String locale = (String)it1.next();
				logln("\tTesting: " + locale);
				CLDRFile item = cldrFactory.make(locale, false);
				// Walk through all the xpaths, adding to currentValues
				// Whenever two values for the same xpath are different, we remove from currentValues, and add to okValues
				for (Iterator it2 = item.keySet().iterator(); it2.hasNext();) {
					String xpath = (String) it2.next();
					if (okValues.contains(xpath)) continue;
					if (xpath.startsWith("/ldml/identity/")) continue; // skip identity elements
					Value v = item.getValue(xpath);
					ValueCount last = (ValueCount) currentValues.get(xpath);
					if (last == null) {
						ValueCount vc = new ValueCount();
						vc.value = v;
						currentValues.put(xpath, vc);
					} else if (v.equals(last.value)) {
						last.count++;
					} else {
						okValues.add(xpath);
						currentValues.remove(xpath);
					}
				}
				// at the end, only the keys left in currentValues are (possibly) faulty
				// they are actually bad IFF either 
				// (a) the count is equal to the total (thus all children are the same), or
				// (b) their value is the same as the parent's resolved value (thus all children are the same or the same
				// as the inherited parent value).
			}
			if (currentValues.size() == 0) continue;
			int size = availableWithParent.size();
			CLDRFile parentCLDR = cldrFactory.make(parent, true);
			XPathParts p = new XPathParts();
			for (Iterator it2 = currentValues.keySet().iterator(); it2.hasNext();) {
				String xpath = (String) it2.next();
				ValueCount vc = (ValueCount) currentValues.get(xpath);
				if (vc.count == size || vc.value.equals(parentCLDR.getValue(xpath))) {
					String draft = "";
					if (true) {
						if (vc.value.getFullXPath().indexOf("[@draft=\"true\"]") >= 0) draft = " [draft]";
					} else {
						p.set(vc.value.getFullXPath());
						if (p.containsAttributeValue("draft","true")) draft = " [draft]";
					}
					String count = (vc.count == size ? "" : vc.count + "/") + size;
					errln(parent + draft +
							", all children (" + count + ") have same value for:\t"
							+ xpath + ";\t" + vc.value.getStringValue());
				}
			}
		}

	}
}

/*    private static final int
HELP1 = 0,
HELP2 = 1,
SOURCEDIR = 2,
DESTDIR = 3,
MATCH = 4,
SKIP = 5,
TZADIR = 6,
NONVALIDATING = 7,
SHOW_DTD = 8,
TRANSLIT = 9;
options[SOURCEDIR].value

private static final UOption[] options = {
		UOption.HELP_H(),
		UOption.HELP_QUESTION_MARK(),
		UOption.SOURCEDIR().setDefault("C:\\ICU4C\\locale\\common\\main\\"),
		UOption.DESTDIR().setDefault("C:\\DATA\\GEN\\cldr\\mainCheck\\"),
		UOption.create("match", 'm', UOption.REQUIRES_ARG).setDefault(".*"),
		UOption.create("skip", 'z', UOption.REQUIRES_ARG).setDefault("zh_(C|S|HK|M).*"),
		UOption.create("tzadir", 't', UOption.REQUIRES_ARG).setDefault("C:\\ICU4J\\icu4j\\src\\com\\ibm\\icu\\dev\\tool\\cldr\\"),
		UOption.create("nonvalidating", 'n', UOption.NO_ARG),
		UOption.create("dtd", 'w', UOption.NO_ARG),
		UOption.create("transliterate", 'y', UOption.NO_ARG), };

private static String timeZoneAliasDir = null;
* /

public static void main(String[] args) throws SAXException, IOException {
	UOption.parseArgs(args, options);
	localeList = getMatchingXMLFiles(options[SOURCEDIR].value, options[MATCH].value);
	/*
    log = BagFormatter.openUTF8Writer(options[DESTDIR].value, "log.txt");
    try {
    	for (Iterator it = getMatchingXMLFiles(options[SOURCEDIR].value, options[MATCH].value).iterator(); it.hasNext();) {
    		String name = (String) it.next();
    		for (int i = 0; i <= 1; ++i) {
    			boolean resolved = i == 1;
        		CLDRKey key = make(name, resolved);
        		
        		PrintWriter pw = BagFormatter.openUTF8Writer(options[DESTDIR].value, name + (resolved ? "_r" : "") + ".txt");
				write(pw, key);
    	        pw.close();
    	        
    		}
    	}
    } finally {
    	log.close();
    	System.out.println("Done");
    }
    */
