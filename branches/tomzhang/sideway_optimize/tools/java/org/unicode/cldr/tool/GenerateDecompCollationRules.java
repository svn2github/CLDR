package org.unicode.cldr.tool;


import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.unicode.cldr.icu.LDMLConstants;
import org.unicode.cldr.tool.CheckHtmlFiles.MyOptions;
import org.unicode.cldr.tool.Option.Options;
import org.unicode.cldr.util.CLDRConfig;
import org.unicode.cldr.util.CLDRFile;
import org.unicode.cldr.util.CLDRPaths;
import org.unicode.cldr.util.CLDRTool;
import org.unicode.cldr.util.SimpleXMLSource;
import org.unicode.cldr.util.XPathParts;
import org.unicode.cldr.util.XPathParts.Comments.CommentType;

import com.ibm.icu.dev.util.BagFormatter;
import com.ibm.icu.dev.util.Relation;
import com.ibm.icu.dev.util.TransliteratorUtilities;
import com.ibm.icu.text.Normalizer;
import com.ibm.icu.text.Normalizer2;
import com.ibm.icu.text.Transliterator;
import com.ibm.icu.text.UTF16;
import com.ibm.icu.text.UnicodeSet;

/**
 * By Steven R. Loomis (srl) thx Markus Scherer
 */
@CLDRTool(alias="generatedecompcollrules",description="based on decomposition, generate identical collation rules")
public class GenerateDecompCollationRules {
    
    private final static UnicodeSet isWord = new UnicodeSet("[\\uFDF0-\\uFDFF]");
    
    private final static String RESET = "\u200E&";
    private final static String IDENTICAL = "\u200E=";
    private final static String TERTIARY = "\u200E<<<";
    private final static String COMMENT = "# ";
    private final static String NL = "\n";
    
    private static final Options myOptions = new Options(GenerateDecompCollationRules.class);
    
    enum MyOptions {
        unicodeset(".*", "[[:dt=init:][:dt=med:][:dt=fin:][:dt=iso:]]", "UnicodeSet of input chars"),
        verbose(null, null, "verbose debugging messages");

        // boilerplate
        final Option option;

        MyOptions(String argumentPattern, String defaultArgument, String helpText) {
            option = myOptions.add(this, argumentPattern, defaultArgument, helpText);
        }
    }

            
    public static void main(String[] args) throws IOException {
        final Transliterator hex = Transliterator.getInstance("any-hex");
        final Transliterator name = Transliterator.getInstance("any-name");
        final Transliterator escapeRules = Transliterator.getInstance("nfc;[\\u0020[:Mn:]] any-hex");
        myOptions.parse(MyOptions.verbose, args, true);
        final boolean verbose = myOptions.get(MyOptions.verbose).doesOccur();
        final CLDRConfig cldrConfig = CLDRConfig.getInstance();
        final Normalizer2 nfkd = Normalizer2.getNFKDInstance();        
        final Normalizer2 nfc = Normalizer2.getNFCInstance();        
        
        if (false) {
            final String astr = "\uFE70";
            final String astr_nfkd = nfkd.normalize(astr);
            final String astr_nfkd_nfc = nfc.normalize(astr_nfkd);
            System.out.println("'"+astr+"'="+hex.transform(astr)+", NFKD: '"+astr_nfkd+"'="+hex.transform(astr_nfkd));
            System.out.println(" NFC: '"+astr_nfkd_nfc+"'="+hex.transform(astr_nfkd_nfc));
            System.out.println(" escapeRules(astr): '"+escapeRules.transform(astr));
            System.out.println(" escapeRules(astr_nfkd): '"+escapeRules.transform(astr_nfkd));
        }
        
        UnicodeSet uSet;
        Option uSetOption = myOptions.get(MyOptions.unicodeset);
        final String uSetRules = uSetOption.doesOccur() ? uSetOption.getValue() : uSetOption.getDefaultArgument();
        System.out.println("UnicodeSet rules: " + uSetRules);
        try {
            uSet = new UnicodeSet(uSetRules);
        } catch(Throwable t) {
            t.printStackTrace();
            System.err.println("Failed to construct UnicodeSet from \""+uSetRules+"\" - see http://unicode.org/cldr/utility/list-unicodeset.jsp");
            return;
        }
        System.out.println("UnicodeSet size: " + uSet.size());
        
        final Relation<String,String> reg2pres = new Relation(new TreeMap<String, Set<String>>(), TreeSet.class);
        
        for(final String presForm : uSet) {
            final String regForm = nfkd.normalize(presForm).trim(); 
            if(verbose) System.out.println("# >" + presForm + "< = "+hex.transliterate(presForm)+"... ->"+
                regForm +"="+hex.transliterate(regForm));
            if(regForm.length()>31 || presForm.length()>31) {
                System.out.println("!! Skipping, TOO LONG: "+presForm+" -> " + regForm);
            } else {
//              if(presForm.equals("\uFDFD")) { // Bismillah
//              reg2pres.put("\u0635\u0644\u0649\u0020\u0627\u0644\u0644\u0647", presForm); // CE limit will be blown otherwise.
                if(presForm.equals("\uFDFA")) { // SAW
                    //              reg2pres.put("?", presForm); // CE limit will be blown otherwise.
                } else {
                    reg2pres.put(regForm, presForm);
                }
            }
        }
        System.out.println("Relation size: " + reg2pres.size());
        
        StringBuilder rules = new StringBuilder();
        
        rules.append(COMMENT)
             .append("Generated by " + GenerateDecompCollationRules.class.getSimpleName() + NL +
              COMMENT + "from rules " + uSetRules + NL + COMMENT + NL);
        
        for(final String regForm : reg2pres.keySet()) {
            final Set<String> presForms = reg2pres.get(regForm);
            
            final String relation = (presForms.size()==1) &&
                                    isWord.containsAll(presForms.iterator().next()) ? 
                                        TERTIARY :  // only pres form is a word.
                                        IDENTICAL;  // all other cases.
            
            // COMMENT
            rules.append(COMMENT)
                 .append(RESET)
                 .append(hex.transliterate(regForm));

            for(final String presForm : presForms) {
                rules.append(relation)
                     .append(hex.transliterate(presForm));
            }
            rules.append(NL);

            // ACTUAL RULE
            rules.append(RESET)
                 .append(escapeRules.transform(regForm));
            
            for(final String presForm : presForms) {
                rules.append(relation)
                     .append(escapeRules.transform(presForm));
            }
            rules.append(NL);
        }
        
        if(verbose) {
            System.out.println(rules);
        }
        
        // now, generate the output file
        XPathParts xpp = new XPathParts(null,null)
        .addElements(LDMLConstants.LDML,
                     LDMLConstants.COLLATIONS,
                     LDMLConstants.COLLATION,
                     "cr");
        // The following crashes. Bug #XXXX
        //xpp.setAttribute(-1, LDMLConstants.COLLATION, LDMLConstants.STANDARD);
        SimpleXMLSource xmlSource = new SimpleXMLSource("ar");
        CLDRFile newFile = new CLDRFile(xmlSource);
        newFile.add(xpp.toString(), "xyzzy");
        newFile.addComment(xpp.toString(), "Generated by " + GenerateDecompCollationRules.class.getSimpleName() +  " " + new java.util.Date() + "\n" +
            "from rules " + uSetRules + "\n", CommentType.PREBLOCK);
        final String filename = newFile.getLocaleID()+".xml";
        StringWriter sw = new StringWriter();
        newFile.write(new PrintWriter(sw));
        sw.close();
        try (PrintWriter w = 
                BagFormatter.openUTF8Writer(CLDRPaths.GEN_DIRECTORY, filename)) {
            w.print(sw.toString().replace("xyzzy", 
                    "<![CDATA[\n" + 
                    rules.toString().replaceAll("\\\\u0020", "\\\\\\\\u0020") +
                    "\n" + "]]>"));
            //newFile.write(w);
            System.out.println("Wrote to " + CLDRPaths.GEN_DIRECTORY +"/"+filename );
        }

    }
}
