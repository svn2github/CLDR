/*
**********************************************************************
* Copyright (c) 2002-2004, International Business Machines
* Corporation and others.  All Rights Reserved.
**********************************************************************
* Author: John Emmons
**********************************************************************
*/

package org.unicode.cldr.posix;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import javax.xml.parsers.DocumentBuilder; 
import javax.xml.parsers.DocumentBuilderFactory;  
import javax.xml.parsers.FactoryConfigurationError;  
import javax.xml.parsers.ParserConfigurationException;
 

import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import org.w3c.dom.Document;
import org.w3c.dom.DOMException;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;

import com.ibm.icu.dev.test.util.BagFormatter;
import com.ibm.icu.impl.Utility;
import com.ibm.icu.lang.UCharacter;
import com.ibm.icu.text.CollationElementIterator;
import com.ibm.icu.text.Collator;
import com.ibm.icu.text.Normalizer;
import com.ibm.icu.text.RuleBasedCollator;
import com.ibm.icu.text.UTF16;
import com.ibm.icu.text.UnicodeSet;
import com.ibm.icu.text.UnicodeSetIterator;
import com.ibm.icu.dev.tool.cldr.*;

public class POSIX_LCCtype {
   UnicodeSet chars;

   public POSIX_LCCtype ( Document doc, UnicodeSet us_in, Charset cs ) {

      if (cs != null ) {
         UnicodeSet csset = new SimpleConverter(cs).getCharset();
         chars = new UnicodeSet(us_in).retainAll(csset);
      }

      String SearchLocation = "//ldml/characters/exemplarCharacters";
      Node n = LDMLUtilities.getNode(doc,SearchLocation);

      UnicodeSet ExemplarCharacters = new UnicodeSet(LDMLUtilities.getNodeValue(n));

      boolean ExemplarError = false;
      UnicodeSetIterator it = new UnicodeSetIterator(ExemplarCharacters);
      while (it.next())
           if ( it.codepoint != -1 && !chars.contains(it.codepoint))
           {
              System.out.println("WARNING: Target codeset does not contain exemplar character : "+
                                  POSIXUtilities.POSIXCharName(it.codepoint));
              ExemplarError = true;
           }

      if ( ExemplarError )
      {
         System.out.println("Locale not generated due to exemplar character errors.");
         System.exit(-1);
      }
   }

   public void write ( PrintWriter out ) throws IOException {
 

      out.println("*************");
      out.println("LC_CTYPE");
      out.println("*************");
      out.println();

      String[][] types = { 
                { "upper", "[:Uppercase:]" },
		{ "lower", "[:Lowercase:]" }, 
		{ "alpha", "[[:Alphabetic:]-[[:Uppercase:][:Lowercase:]]]" },
                { "space", "[:Whitespace:]" },
		{ "cntrl", "[:Control:]" }, 
                { "graph", "[^[:Whitespace:][:Control:][:Format:][:Surrogate:][:Unassigned:]]" },
                { "print", "[^[:Control:][:Format:][:Surrogate:][:Unassigned:]]" },
                { "punct", "[:Punctuation:]" },
		{ "digit", "[0-9]" }, 
                { "xdigit", "[0-9 a-f A-F]" },
		{ "blank", "[[:Whitespace:]-[\\u000A-\\u000D \\u0085 [:Line_Separator:][:Paragraph_Separator:]]]" } };

        // print character types, restricted to the charset
        int item, last;
        for (int i = 0; i < types.length; ++i) {
            UnicodeSet us = new UnicodeSet(types[i][1]).retainAll(chars);
            item = 0;
            last = us.size() - 1;
        	for (UnicodeSetIterator it = new UnicodeSetIterator(us); it.next(); ++item) {
                if (item == 0) out.print(types[i][0]);
                out.print("\t" + POSIXUtilities.POSIXCharName(it.codepoint));
                if (item != last) out.print(";/");
                out.println("");
            }
            out.println();
        }

        // toupper processing

        UnicodeSet us = new UnicodeSet();
        for (UnicodeSetIterator it = new UnicodeSetIterator(chars); it.next();) {
        	int low = UCharacter.toUpperCase(it.codepoint);
            if (low != it.codepoint && chars.contains(low)) us.add(it.codepoint);
        }
        item = 0;
        last = us.size() - 1;
        for (UnicodeSetIterator it = new UnicodeSetIterator(us); it.next(); ++item) {
            if (item == 0) out.print("toupper");
        	out.print("\t(" + POSIXUtilities.POSIXCharName(it.codepoint) + "," + 
                    POSIXUtilities.POSIXCharName(UCharacter.toUpperCase(it.codepoint)) + ")");
            if (item != last) out.print(";/");
            out.println("");
        }
        out.println("");

        // tolower processing

        us = new UnicodeSet();
        for (UnicodeSetIterator it = new UnicodeSetIterator(chars); it.next();) {
        	int low = UCharacter.toLowerCase(it.codepoint);
            if (low != it.codepoint && chars.contains(low)) us.add(it.codepoint);
        }
        item = 0;
        last = us.size() - 1;
        for (UnicodeSetIterator it = new UnicodeSetIterator(us); it.next(); ++item) {
            if (item == 0) out.print("tolower");
        	out.print("\t(" + POSIXUtilities.POSIXCharName(it.codepoint) + "," + 
                    POSIXUtilities.POSIXCharName(UCharacter.toLowerCase(it.codepoint)) + ")");
            if (item != last) out.print(";/");
            out.println("");
        }

      out.println();
      out.println("END LC_CTYPE");
      out.println();
      out.println();
   }

};
