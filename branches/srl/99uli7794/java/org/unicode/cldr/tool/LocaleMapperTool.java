package org.unicode.cldr.tool;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.unicode.cldr.util.CLDRConfig;
import org.unicode.cldr.util.CLDRFile;
import org.unicode.cldr.util.CLDRLocale;
import org.unicode.cldr.util.CLDRTool;
import org.unicode.cldr.util.Iso639Data;
import org.unicode.cldr.util.StandardCodes;

import com.ibm.icu.util.ULocale;

@CLDRTool(alias="locmap", description="Map locales (passed in as argument variables) to other forms.", hidden="temporary hack tool")
public class LocaleMapperTool {
    
    public static final class WikimediaToBcp47Mapper {
        private static final Map<String,String> specialCases; 
        
        static {
            Map<String,String> aMap = new HashMap<String,String>();
            
            String specials[] = {
                // srl addition
                "simple", "en-x-simple",
                // from the bug
                "be-x-old", "be-tarask",
                "roa-rup", "rup",
                "zh-classical", "lzh",
                "zh-min-nan", "nan",
                "zh-yue", "yue",
                "bat-smg", "sgs",
                "cbk-zam","cbk",
                "fiu-vro", "vro",
                "nds-nl", "nds",
                // from the bug
                "tl", "fil",
                // https://meta.wikimedia.org/wiki/Special_language_codes
                "sh", "sr-Latn",
            };
            for(int i=0; i<specials.length; i+=2) {
                aMap.put(specials[i+0], specials[i+1]);
            }
            
            specialCases = Collections.unmodifiableMap(aMap);
        }
        
        public static final String mapWikimediaToBCP47(String wikimedia) {
            final String altReplacement = specialCases.get(wikimedia.replaceAll("_", "-"));
            if(altReplacement != null) return altReplacement;
            // canonicalize
            ULocale loc = ULocale.forLanguageTag(wikimedia);
            return loc.toLanguageTag();
        }
    }
    
    public static void main(String args[]) {
        StandardCodes sc = StandardCodes.make();
        CLDRFile eng = CLDRConfig.getInstance().getEnglish();
        Iso639Data i639d = new Iso639Data();
        for(final String str : args) {
            final String asbcp47 = WikimediaToBcp47Mapper.mapWikimediaToBCP47(str);
            ULocale loc = ULocale.forLanguageTag(asbcp47);
            String disp = loc.getDisplayName();
            if(disp.equals(asbcp47)) {
                disp = i639d.getNames(asbcp47).toString(); // have a better name?
            }
            if(true) {
                System.out.print(str+"\t");
                System.out.print(asbcp47+"\t");
                System.out.print(disp);
    //            System.out.print(CLDRLocale.getInstance(asbcp47).getDisplayName()+"\t");
                System.out.println();
            } else {
                // for ULI data
                if(!str.equals(asbcp47)) {
                    System.out.println("svn mv "+str+".tsv "+asbcp47+".tsv");
                }
            }
        }
    }
}
