/*
*******************************************************************************
*
*   Copyright (C) 2003, International Business Machines
*   Corporation and others.  All Rights Reserved.
*
*******************************************************************************
*   file name:  genldml.h
*/
#ifndef GENLDML_H
#define GENLDML_H

#include "unicode/ures.h"
#include "unicode/format.h"
#include "unicode/unistr.h"
#include "unicode/resbund.h"
#include "unicode/ustdio.h"
#include "unicode/fmtable.h"
#include "unicode/ucol.h"
#include "unicode/smpdtfmt.h"
#include "unicode/ustring.h"
#include "unicode/msgfmt.h"
#include "unicode/fmtable.h"
#include "unicode/locid.h"
#include "unicode/rbt.h"
#include "unicode/rep.h"
#include "unicode/numfmt.h"
#include "unicode/decimfmt.h"
#include "unicode/dcfmtsym.h"
#include "unicode/hextouni.h"
#include "unicode/ures.h"
#include "unicode/ucol.h"
#include "unicode/unum.h"
#include "unicode/unitohex.h"
#include "unicode/udata.h"     /* ICU API for data handling.                 */
#include "unicode/uset.h"
#include "unicode/ucnv.h"
#include "uoptions.h"
#include "unicode/uchar.h"
//#include "ucol_tok.h"

#include <stdio.h>

class GenerateXML {
private:
	/*
	 *
	 *  "&		a	<<		befg	<<		c		<<		d		<<		d"
	 *   ^			^				^										^
	 *   start		prevCurrent		current 								end
	 */
	struct Token{
		UChar* start;
		UChar* end ;
		UChar* current;
		UChar* prevCurrent; 
		UChar* chars;
		int32_t charsCapacity;
		int32_t charsLen;

	};
	/* Full path to the resource bundle eg: root.res */
	ResourceBundle mSourceBundle;
    
	/*Bundle to retrieve the xml strings */
	ResourceBundle mStringsBundle;

	/* output file stream handle */
	FILE* mFile;
	
	/* error code */
	UErrorCode mError;

	/* locale */
	Locale mLocale;

	/* indenting string */
	UnicodeString indentOffset;
    
    const char* locName;
    const char* path;
    /* destination directory */
    const char* destDir;
	
    /* default private constructor */
	GenerateXML();

	/*overloaded utility function with 3 args for formatting the string*/
	UnicodeString formatString(UnicodeString& str, const Formattable* args, int32_t num,UnicodeString& result);

	/*overloaded utility function with 2 args for formatting the string*/
	UnicodeString formatString(UnicodeString& str,UnicodeString& argument, UnicodeString& result);

	/* get the file handle */
	FILE* getFileHandle(const char* path, Locale loc);
	
	/* print the unicode string */
	void printString( UnicodeString* uString);

	/* chop the indent string*/ 
	void chopIndent();
	
	/**
	 * writeXMLVersionAndComments()
	 * Prints version and comments strings in the output xml file
	 * Creation date: (6/29/00 3:56:37 PM)
	 * @return void
	 * @param void
	 */
	void writeXMLVersionAndComments();

	/**
	 * writeDocVersion()
	 * Prints document version and IBM copytright info strings in the output xml file
	 * Creation date: (6/29/00 3:56:37 PM)
	 * @return void
	 * @param void
	 */
	void writeVersion(UnicodeString& xmlString);

	/**
	 * writeIdentity()
	 * Prints identity info strings in the output xml file
	 * <identity></identity> tags
	 * Creation date: (6/29/00 3:56:37 PM)
	 * @return void
	 * @param void
	 */
	void writeIdentity();

	void writeScript(UnicodeString& xmlString);

	void writeLanguage(UnicodeString& xmlString);

	void writeCountryNames(UnicodeString& xmlString);

	void writeVariantNames(UnicodeString& xmlString);
	
	void writeKeywordNames(UnicodeString& xmlString);
	
	void writeTypeNames(UnicodeString& xmlString);

	void writeLayout();

	void writeEncodings();

	void writeDelimiters();

	void writeMeasurement();
	
	void writeDisplayNames();

	void writeCalendar(UnicodeString& calendar,UBool isDefault, UnicodeString& xmlString);

	void writeCalendars(UnicodeString& xmlString);

	void writeMonthNames(UnicodeString& xmlString);

	void writeMonthAbbr(UnicodeString& xmlString);

	void writeDayNames(UnicodeString& xmlString);

	void writeDayAbbr(UnicodeString& xmlString);
	
	void writeWeek(UnicodeString& xmlString);
	
	void writeEra(UnicodeString& xmlString);

	void writeFormats(UnicodeString& xmlString);
	void writeFormat(const char* style, const char* start, const char* end, const char* type,UnicodeString& pattern, UnicodeString& xmlString, UBool split=FALSE);

	void writeLocalePatternChars(UnicodeString& xmlString);

	void writeTimeFormat(UnicodeString& xmlString);
	void writeDateFormat(UnicodeString& xmlString);
	void writeDateTimeFormat(UnicodeString& xmlString);
    
    void writeDateTimeElements(UnicodeString& xmlString);

	void writeNumberFormat();

	void writeRuleBasedNumberFormat(UnicodeString& xmlString);

	void writeNumberElements(UnicodeString& xmlString);

	void writeNumberPatterns(UnicodeString& xmlString);
	void writeDates();
	
	void writeCurrency(UnicodeString&);

	void writeCurrencies(UnicodeString&);

	void writeCollations();

	void writeCollation(ResourceBundle& bundle, UnicodeString& xmlString,UnicodeString* collKey=NULL);

	void writeTimeZoneNames(UnicodeString& xmlString);

	void writeBoundary(UnicodeString& xmlString);

	void closeFileHandle();
	
	void closeXMLDocument();

	void writeAMPMmarkers(UnicodeString& xmlString);

	void writeTransliteration();

	void writeCharBrkRules(UnicodeString& xmlString);

	void writeSentBrkRules(UnicodeString& xmlString);

	void writeLineBrkRules(UnicodeString& xmlString);
	
	void writeMisc();

	void escape(UnicodeString& str);

    void writePosixAdditions();

    void writeMeasurement(UnicodeString& xmlString);
    void writeCountryPost(UnicodeString& xmlString);
    void writeCountryCar(UnicodeString& xmlString);  
    void writeCountryISBNNumber(UnicodeString& xmlString);
    void writeLanguageLibraryUse(UnicodeString& xmlString);
    void writePaperSize(UnicodeString& xmlString);
    void getStringRes(const char* key,UnicodeString& xmlString,UnicodeString pattern);
    void getStringRes(const char *key,ResourceBundle& bundle,UnicodeString& xmlString,UnicodeString pattern);
  
    void writePosixCompData();

    void writeMessages(ResourceBundle& bundle,UnicodeString& xmlString);

    void addressFormat(ResourceBundle& bundle,UnicodeString& xmlString);

    void nameFormat(ResourceBundle& bundle,UnicodeString& xmlString);

    void identity(ResourceBundle& bundle,UnicodeString& xmlString);

    void telephoneFormat(ResourceBundle& bundle,UnicodeString& xmlString);

	void writeTable(const char* key, const char* resMain, const char* resElement, UnicodeString& xmlString);

    UnicodeString parseRules(UChar* rules, int32_t ruleLen, UnicodeString& ruleXML);

	uint32_t parseRules(Token* src, UBool startOfRules);

    int32_t copyUnicodeStringToChars(const UnicodeString& str, char* buf,int32_t bufCapacity);

	void writeSpecial();
	
	int32_t fillOutputString(const UnicodeString &temp, UChar *dest, int32_t destCapacity) ;
	
	void writeSettings(UnicodeString& src , UnicodeString& xmlString);
	
	void writeReset(UnicodeString& src, UnicodeString& xmlString);

	void growBuffer(UChar* src, int32_t len, int32_t size, int32_t requiredCapacity, UErrorCode* status);
	

public:
	
	/* constructor */
	GenerateXML(const char* path,const char* locName,const char* destDir, UErrorCode& error);
	
	/* destructor */
	~GenerateXML();
	
	void DoIt();

};

int32_t 
my_fillOutputString(const UnicodeString &temp,
                      UChar *dest, 
                      int32_t destCapacity,
                      UErrorCode *status); 
#endif