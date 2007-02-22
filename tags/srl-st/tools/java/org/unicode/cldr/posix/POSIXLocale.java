/*
**********************************************************************
* Copyright (c) 2005, International Business Machines
* Corporation and others.  All Rights Reserved.
**********************************************************************
* Author: John Emmons
**********************************************************************
*/
package org.unicode.cldr.posix;

import java.io.PrintWriter;
import java.io.File;
import java.nio.charset.Charset;

import org.w3c.dom.Document;
import org.w3c.dom.Node;

import com.ibm.icu.lang.UCharacter;
import com.ibm.icu.lang.UProperty;
import com.ibm.icu.lang.UScript;
import com.ibm.icu.text.UnicodeSet;
import com.ibm.icu.text.UnicodeSetIterator;

import org.unicode.cldr.icu.SimpleConverter;
import org.unicode.cldr.util.LDMLUtilities;


/**
 * Class to generate POSIX format from CLDR. 
 * @author jcemmons
 */

public class POSIXLocale {

   String locale_name;
   String codeset;
   Document doc;
   Document supp;
   Document collrules;
   Document char_fallbk;
   POSIX_LCCtype lc_ctype;
   POSIX_LCCollate lc_collate;
   POSIX_LCNumeric lc_numeric;
   POSIX_LCMonetary lc_monetary;
   POSIX_LCTime lc_time;
   POSIX_LCMessages lc_messages;
   POSIXVariant variant;

   public POSIXLocale ( String locale_name , String cldr_data_location , UnicodeSet repertoire, Charset cs, String codeset, UnicodeSet collateset , POSIXVariant variant ) throws Exception {

      this.locale_name = locale_name;
      this.codeset = codeset;
      this.variant = variant;
      
      boolean ignoreDraft = true;
      doc = LDMLUtilities.getFullyResolvedLDML ( cldr_data_location+File.separator+"main", locale_name, false, false, false, ignoreDraft);
      supp = LDMLUtilities.parse ( cldr_data_location+File.separator+"supplemental"+File.separator+"supplementalData.xml",  true );
      char_fallbk = LDMLUtilities.parse ( cldr_data_location+File.separator+"supplemental"+File.separator+"characters.xml",  true );
      collrules = LDMLUtilities.getFullyResolvedLDML ( cldr_data_location+File.separator+"collation", locale_name, true, true, true, ignoreDraft );


     if ( repertoire.isEmpty() && codeset.equals("UTF-8")) // Generate default repertoire set from exemplar characters;
     {
        String SearchLocation = "//ldml/characters/exemplarCharacters";
        Node n = LDMLUtilities.getNode(doc,SearchLocation);
        UnicodeSet ExemplarCharacters = new UnicodeSet(LDMLUtilities.getNodeValue(n));
        UnicodeSetIterator ec = new UnicodeSetIterator(ExemplarCharacters);
        while ( ec.next() )
        {
           if ( (ec.codepoint != UnicodeSetIterator.IS_STRING) && (ec.codepoint <= 0x00ffff) )
           repertoire.add(ec.codepoint);
        }
        UnicodeSet CaseFoldedExemplars = new UnicodeSet(ExemplarCharacters.closeOver(UnicodeSet.CASE));
        UnicodeSetIterator cf = new UnicodeSetIterator(CaseFoldedExemplars);
        while ( cf.next() )
        {
           if ( (cf.codepoint != UnicodeSetIterator.IS_STRING) && (cf.codepoint <= 0x00ffff) )
           repertoire.add(cf.codepoint);
        }

        UnicodeSetIterator it = new UnicodeSetIterator(repertoire);
        int PreviousScript = UScript.INVALID_CODE;
        while ( it.next() )
        {
           if ( (it.codepoint != UnicodeSetIterator.IS_STRING) && (it.codepoint <= 0x00ffff) )
           {
              int Script = UScript.getScript(it.codepoint);
              if ( Script != UScript.COMMON && 
                   Script != UScript.INHERITED && 
                   Script != UScript.INVALID_CODE &&
                   Script != UScript.HAN &&
                   Script != PreviousScript ) // Hopefully this speeds up the process...
              {
                 UnicodeSet ThisScript = new UnicodeSet().applyIntPropertyValue(UProperty.SCRIPT,Script);
                 UnicodeSetIterator ts = new UnicodeSetIterator(ThisScript);
                 while ( ts.next() ) 
                 {
                    if ( (ts.codepoint != UnicodeSetIterator.IS_STRING) && (ts.codepoint <= 0x00ffff) )
                       repertoire.add(ts.codepoint);
                 }
                 PreviousScript = Script;
              }
           } 
        }
 
        repertoire.add(0x0000,0x007f);        // Always add the ASCII set
        
     }
     else if ( ! codeset.equals("UTF-8") )
     {
        UnicodeSet csset = new SimpleConverter(cs).getCharset();
        repertoire = new UnicodeSet(UnicodeSet.MIN_VALUE,UnicodeSet.MAX_VALUE).retainAll(csset);
        POSIXUtilities.setRepertoire(repertoire);
     }

     UnicodeSetIterator rep = new UnicodeSetIterator(repertoire);
     while ( rep.next() )
     {
        if ( !UCharacter.isDefined(rep.codepoint) && (rep.codepoint != UnicodeSetIterator.IS_STRING))
           repertoire.remove(rep.codepoint);
     }

     POSIXUtilities.setCharFallback(char_fallbk);

     lc_collate = new POSIX_LCCollate( doc, repertoire, collrules , collateset , codeset , variant );

      if ( codeset.equals("UTF-8") )
      {
         UnicodeSet tailored = lc_collate.col.getTailoredSet();

         // Add the tailored characters, and close over script

         UnicodeSetIterator it = new UnicodeSetIterator(tailored);
         int PreviousScript = UScript.INVALID_CODE;
         while ( it.next() )
         {
            if ( it.codepoint != UnicodeSetIterator.IS_STRING )
            {
               int Script = UScript.getScript(it.codepoint);
               if ( Script != UScript.COMMON && 
                    Script != UScript.INHERITED && 
                    Script != UScript.INVALID_CODE &&
                    Script != UScript.HAN &&
                    Script != PreviousScript ) // Hopefully this speeds up the process...
               {
                  UnicodeSet ThisScript = new UnicodeSet().applyIntPropertyValue(UProperty.SCRIPT,Script);
                  repertoire.addAll(ThisScript);
                  PreviousScript = Script;
               }
            } 
         }
       }
      
      lc_ctype = new POSIX_LCCtype ( doc, repertoire );
      lc_numeric = new POSIX_LCNumeric( doc );
      lc_monetary = new POSIX_LCMonetary( doc , supp , variant );
      lc_time = new POSIX_LCTime( doc , variant);
      lc_messages = new POSIX_LCMessages( doc , locale_name , variant );

   } // end POSIXLocale ( String locale_name, String cldr_data_location );

   public void write(PrintWriter out) {
   
      out.println("comment_char *");
      out.println("escape_char /");
      out.println("");
      out.println("*************************************************************************************************");
      out.println("* POSIX Locale                                                                                  *");
      out.println("* Generated automatically from the Unicode Character Database and Common Locale Data Repository *");
      out.println("* see http://www.opengroup.org/onlinepubs/009695399/basedefs/xbd_chap07.html                    *");
      out.println("* Locale Name : " + locale_name + "   Codeset : " + codeset ); 
      out.println("*************************************************************************************************");
      out.println("");

      lc_ctype.write(out);
      lc_collate.write(out);
      lc_numeric.write(out);
      lc_monetary.write(out);
      lc_time.write(out, variant);
      lc_messages.write(out);

   } // end write(PrintWriter out);

}
