/*
 ******************************************************************************
 * Copyright (C) 2005, International Business Machines Corporation and        *
 * others. All Rights Reserved.                                               *
 ******************************************************************************
*/
package org.unicode.cldr.test;

import java.util.List;
import java.util.Map;

import org.unicode.cldr.util.CLDRFile;
import org.unicode.cldr.util.TimezoneFormatter;
import org.unicode.cldr.util.XMLSource;
import org.unicode.cldr.util.XPathParts;

import com.ibm.icu.impl.CollectionUtilities;
import com.ibm.icu.text.Collator;
import com.ibm.icu.text.UnicodeSet;
import com.ibm.icu.util.TimeZone;
import com.ibm.icu.util.ULocale;

public class CheckZones extends CheckCLDR {
	//private final UnicodeSet commonAndInherited = new UnicodeSet(CheckExemplars.Allowed).complement(); 
	// "[[:script=common:][:script=inherited:][:alphabetic=false:]]");
	static String[] EXEMPLAR_SKIPS = {"/currencySpacing", "/hourFormat", "/exemplarCharacters", "/pattern",
        "/localizedPatternChars", "/segmentations", "/dateFormatItem", "/references"};

	private TimezoneFormatter timezoneFormatter;
	
	public CheckCLDR setCldrFileToCheck(CLDRFile cldrFile, Map options, List possibleErrors) {
		if (cldrFile == null) return this;
		super.setCldrFileToCheck(cldrFile, options, possibleErrors);
		try {
			timezoneFormatter = new TimezoneFormatter(getResolvedCldrFileToCheck(), true);
		} catch (RuntimeException e) {
			e.printStackTrace();
			throw new InternalError("Couldn't create TimezoneFormatter");
		}
		return this;
	}
	
	XPathParts parts = new XPathParts(null, null);

	public CheckCLDR handleCheck(String path, String fullPath, String value,
			Map options, List result) {
		if (path.indexOf("timeZoneNames") < 0)
			return this;
		if (timezoneFormatter == null) {
			throw new InternalError("This should not occur: setCldrFileToCheck must create a TimezoneFormatter.");
		}
		parts.set(path);
		if (parts.containsElement("zone")) {
			String id = (String) parts.getAttributeValue(3, "type");
			TimeZone tz = TimeZone.getTimeZone(id);
			if (!parts.containsElement("exemplarCity")) {
				result.add(new CheckStatus().setCause(this).setType(CheckStatus.warningType)
						.setMessage("Remove this field unless always understood in the language."));
			}
		}
		return this;
	}

	public CheckCLDR handleGetExamples(String path, String fullPath, String value,
			Map options, List result) {
		if (path.indexOf("timeZoneNames") < 0)
			return this;
		if (timezoneFormatter == null) {
			throw new InternalError("This should not occur: setCldrFileToCheck must create a TimezoneFormatter.");
		}
		parts.set(path);
		if (parts.containsElement("zone")) {
			String id = (String) parts.getAttributeValue(3,"type");
			TimeZone tz = TimeZone.getTimeZone(id);
			String pat = "vvvv";
			if (parts.containsElement("short")) pat = "v";
			if (parts.containsElement("exemplarCity")) {
				String formatted = timezoneFormatter.getFormattedZone(id, pat,
						false, tz.getRawOffset(), false);
				result.add(new CheckStatus().setCause(this).setType(
						CheckStatus.exampleType).setMessage("Formatted value: \"{0}\"",
						new Object[] { formatted }));
			} else {
//				boolean daylight = parts.containsElement("daylight");
//				if (daylight || parts.containsElement("standard")) {				
//					pat = "zzzz";
//					if (parts.containsElement("short")) pat = "z";
//				}
//
//				String formatted = timezoneFormatter.getFormattedZone(id, pat,
//						daylight, tz.getRawOffset(), false);
//				result.add(new CheckStatus().setCause(this).setType(
//						CheckStatus.exampleType).setMessage("Formatted value: \"{0}\"",
//						new Object[] { formatted }));
				pat = "vvvv";
				if (parts.containsElement("short")) pat = "v";
				String formatted = timezoneFormatter.getFormattedZone(id, pat,
						false, tz.getRawOffset(), true);
				result.add(new CheckStatus().setCause(this).setType(CheckStatus.exampleType)
						.setMessage("Formatted value (if removed!): \"{0}\"", new Object[] {formatted}));
			}
		}
		return this;
	}

}