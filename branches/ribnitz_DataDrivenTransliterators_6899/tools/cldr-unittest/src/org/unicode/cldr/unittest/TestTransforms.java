package org.unicode.cldr.unittest;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

import org.unicode.cldr.unittest.TestAll.TestInfo;
import org.unicode.cldr.util.CLDRFile;
import org.unicode.cldr.util.CLDRTransforms;
import org.unicode.cldr.util.Factory;

import com.google.common.base.Splitter;
import com.ibm.icu.dev.util.BagFormatter;
import com.ibm.icu.dev.util.CollectionUtilities;
import com.ibm.icu.impl.Utility;
import com.ibm.icu.lang.UCharacter;
import com.ibm.icu.text.Transliterator;
import com.ibm.icu.text.UnicodeSet;
import com.ibm.icu.util.ULocale;


public class TestTransforms extends TestFmwkPlus {
	/**
	 * Path to the testtransforms directory, will be
	 * prepended with the location of the current class. 
	 */
	private static final String TRANSFORMTEST_DIR = "./../unittest/data/transformtest/";
	/**
	 * Relative path to the file containing the Casing transformations
	 */
    private static final String CASING_TRANSFORMS = "./TestCasingTransforms.txt";
    
	TestInfo testInfo = TestInfo.getInstance();

    /**
     * Value holder object for a line of the Transform tests. Meant to be a Data Transfer Object
     * 
     * @author ribnitz
     *
     */
    private static class TestTransformLine {
    	public final String source;
    	public final String result;
    	public final String locale;
    	public final Casing casing;
    	public final boolean specialCasing;
    	
    	public TestTransformLine(String source, String result, String locale, Casing casing,boolean special) {
    		this.source=source;
    		this.result=result;
    		this.casing=casing;
    		this.locale=locale;
    		this.specialCasing=special;
    	}
    }
    
    private static class TransformLineProcessor { 
    	private  List<String> lines;
    	private List<TestTransformLine> ttlList=new ArrayList<>();
    	public TransformLineProcessor(String file) throws IOException {
			lines=Files.readAllLines(new File(file).toPath(), Charset.forName("UTF-8"));
			Iterator<String> iter=lines.iterator();
			while (iter.hasNext()) {
				String curLine=iter.next();
				if (!lineNeedsProcessing(curLine)) {
					iter.remove();
				}
			}
			List<TestTransformLine> ttlList=new ArrayList<>();
			for (String line: lines ) {
				ttlList.add(processLine(line));
			}
		}
    	
    	private static boolean lineNeedsProcessing(String line) {
    		return (line!=null && line.length()>0 && !line.trim().startsWith("#"));
    	}
		public Iterable<TestTransformLine> getLines() throws IOException {
    		return ttlList;
    	}
		
		private final static Splitter SEMICOLON_SPLITTER=Splitter.on(";");
    	private final static Splitter DASH_SPLITTER=Splitter.on("-");
    	private  TestTransformLine oldValues=new TestTransformLine(null, null, null, null, true);
    	
    	
    	private TestTransformLine processLine(String line) {
    		String locale;
    		Casing casing;
    		// Line structure: src; locale-casing; result (; special casing)
    		List<String> lineElements=SEMICOLON_SPLITTER.splitToList(line);
    		String currentSource=lineElements.get(0);
    		String src=currentSource.isEmpty()?oldValues.source:currentSource;
    		String currentTransform=lineElements.get(1);
    		if (!currentTransform.isEmpty()) {
    			List<String> tmpSplit=DASH_SPLITTER.splitToList(currentTransform);
    			locale=tmpSplit.get(0);
    			String casingStr=tmpSplit.get(1);
    			casing=Casing.valueOf(casingStr);
    		} else {
    			locale=oldValues.locale;
    			casing=oldValues.casing;
    		}
    		String res=lineElements.get(2).trim();
    		Boolean special=null;
    		if (lineElements.size()>3) {
    			String specialStr=lineElements.get(3).trim();
    			if (specialStr.equals("true")||specialStr.equals("TRUE")) {
    				special=true;
    			} else {
    				special=false;
    			}
    		}
    		// Assume last parameter to be true, if it was not specified.
    		if (special==null) {
    			special=oldValues.specialCasing;
    		}
    		// update old values, as we may need them next time
    		oldValues=new TestTransformLine(src, res, locale, casing, special);
    		return new TestTransformLine(src, res, locale, casing, special);
    	}
    }

	
    public static void main(String[] args) {
        new TestTransforms().run(args);
    }

    public void TestUzbek() {
        register();
        Transliterator cyrillicToLatin = Transliterator.getInstance("uz_Cyrl-uz_Latn");
        Transliterator latinToCyrillic = cyrillicToLatin.getInverse();
        //        for (Transliterator t2 : t.getElements()) {
        //            System.out.println(t2.getSourceSet().toPattern(false) + " => " + t2.getTargetSet().toPattern(false));
        //        }
        String cyrillic = "аА бБ вВ гГ ғҒ   дД ЕеЕ    ЁёЁ    жЖ зЗ иИ йЙ кК қҚ лЛ мМ нН оО пП рР сС тТ уУ ўЎ   фФ хХ ҳҲ ЦцЦ    ЧчЧ    ШшШ    бъ Ъ эЭ ЮюЮ    ЯяЯ";
        String latin = "aA bB vV gG gʻGʻ dD YeyeYE YoyoYO jJ zZ iI yY kK qQ lL mM nN oO pP rR sS tT uU oʻOʻ fF xX hH TstsTS ChchCH ShshSH bʼ ʼ eE YuyuYU YayaYA";
        UnicodeSet vowelsAndSigns = new UnicodeSet("[аА еЕёЁ иИ оО уУўЎ эЭ юЮ яЯ ьЬ ъЪ]").freeze();
        UnicodeSet consonants = new UnicodeSet().addAll(cyrillic).removeAll(vowelsAndSigns).remove(" ").freeze();

        //        UnicodeSet englishVowels = new UnicodeSet();
        //        for (String s : vowelsAndSigns) {
        //            String result = cyrillicToLatin.transform(s);
        //            if (!result.isEmpty()) {
        //                englishVowels.add(result);
        //            }
        //        }
        //        System.out.println(englishVowels.toPattern(false));

        String[] cyrillicSplit = cyrillic.split("\\s+");
        String[] latinSplit = latin.split("\\s+");
        for (int i = 0; i < cyrillicSplit.length; ++i) {
            assertTransformsTo("Uzbek to Latin", latinSplit[i], cyrillicToLatin, cyrillicSplit[i]);
            assertTransformsTo("Uzbek to Cyrillic", cyrillicSplit[i], latinToCyrillic, latinSplit[i]);
        }

        // # е → 'ye' at the beginning of a syllable, after a vowel, ъ or ь, otherwise 'e'

        assertEquals("Uzbek to Latin", "Belgiya", cyrillicToLatin.transform("Бельгия"));
        UnicodeSet lower = new UnicodeSet("[:lowercase:]");
        for (String e : new UnicodeSet("[еЕ]")) {
            String ysuffix = lower.containsAll(e) ? "ye" : "YE";
            String suffix = lower.containsAll(e) ? "e" : "E";
            for (String s : vowelsAndSigns) {
                String expected = getPrefix(cyrillicToLatin, s, ysuffix);
                assertTransformsTo("Uzbek to Latin ye", expected, cyrillicToLatin, s + e);
            }
            for (String s : consonants) {
                String expected = getPrefix(cyrillicToLatin, s, suffix);
                assertTransformsTo("Uzbek to Latin e", expected, cyrillicToLatin, s + e);
            }
            for (String s : Arrays.asList(" ", "")) { // start of string, non-letter
                String expected = getPrefix(cyrillicToLatin, s, ysuffix);
                assertTransformsTo("Uzbek to Latin ye", expected, cyrillicToLatin, s + e);
            }
        }

        if (isVerbose()) {
            // Now check for correspondences
            Factory factory = testInfo.getCldrFactory();
            CLDRFile uzLatn = factory.make("uz_Latn", false);
            CLDRFile uzCyrl = factory.make("uz", false);

            Set<String> latinFromCyrillicSucceeds = new TreeSet<String>();
            Set<String> latinFromCyrillicFails = new TreeSet<String>();
            for (String path : uzCyrl) {
                String latnValue = uzLatn.getStringValue(path);
                if (latnValue == null) {
                    continue;
                }
                String cyrlValue = uzCyrl.getStringValue(path);
                if (cyrlValue == null) {
                    continue;
                }
                String latnFromCyrl = cyrillicToLatin.transform(latnValue);
                if (latnValue.equals(latnFromCyrl)) {
                    latinFromCyrillicSucceeds.add(latnValue + "\t←\t" + cyrlValue);
                } else {
                    latinFromCyrillicFails.add(latnValue + "\t≠\t" + latnFromCyrl + "\t←\t" + cyrlValue);
                }
            }
            logln("Success! " + latinFromCyrillicSucceeds.size() + "\n" + CollectionUtilities.join(latinFromCyrillicSucceeds, "\n"));
            logln("\nFAILS!" + latinFromCyrillicFails.size() + "\n" + CollectionUtilities.join(latinFromCyrillicFails, "\n"));
        }
    }

    private String getPrefix(Transliterator cyrillicToLatin, String prefixSource, String suffix) {
        String result = cyrillicToLatin.transform(prefixSource);
        if (!result.isEmpty() && UCharacter.getType(suffix.codePointAt(0)) != UCharacter.UPPERCASE_LETTER
            && UCharacter.getType(result.codePointAt(0)) == UCharacter.UPPERCASE_LETTER) {
            result = UCharacter.toTitleCase(result, null);
        }
        return result + suffix;
    }

    public void TestBackslashHalfwidth() throws Exception {
        register();
        // CLDRTransforms.registerCldrTransforms(null, "(?i)(Fullwidth-Halfwidth|Halfwidth-Fullwidth)", isVerbose() ?
        // getLogPrintWriter() : null);
        // Transliterator.DEBUG = true;

        String input = "＼"; // FF3C
        String expected = "\\"; // 005C
        Transliterator t = Transliterator.getInstance("Fullwidth-Halfwidth");
        String output = t.transliterate(input);
        assertEquals("To Halfwidth", expected, output);

        input = "\\"; // FF3C
        expected = "＼"; // 005C
        Transliterator t2 = t.getInverse();
        output = t2.transliterate(input);
        assertEquals("To FullWidth", expected, output);
    }

    public void TestASimple() {
        Transliterator foo = Transliterator.getInstance("cs-cs_FONIPA");
    }

    boolean registered = false;

    void register() {
        if (!registered) {
            CLDRTransforms.registerCldrTransforms(null, null, isVerbose() ? getLogPrintWriter() : null);
            registered = true;
        }
    }

    enum Options {
        transliterator, roundtrip
    };

    public void Test1461() {
        register();

        String[][] tests = {
            { "transliterator=", "Katakana-Latin" },
            { "\u30CF \u30CF\uFF70 \u30CF\uFF9E \u30CF\uFF9F", "ha hā ba pa" },
            { "transliterator=", "Hangul-Latin" },
            { "roundtrip=", "true" },
            { "갗", "gach" },
            { "느", "neu" },
        };

        Transliterator transform = null;
        Transliterator inverse = null;
        String id = null;
        boolean roundtrip = false;
        for (String[] items : tests) {
            String source = items[0];
            String target = items[1];
            if (source.endsWith("=")) {
                switch (Options.valueOf(source.substring(0, source.length() - 1).toLowerCase(Locale.ENGLISH))) {
                case transliterator:
                    id = target;
                    transform = Transliterator.getInstance(id);
                    inverse = Transliterator.getInstance(id, Transliterator.REVERSE);
                    break;
                case roundtrip:
                    roundtrip = target.toLowerCase(Locale.ENGLISH).charAt(0) == 't';
                    break;
                }
                continue;
            }
            String result = transform.transliterate(source);
            assertEquals(id + ":from " + source, target, result);
            if (roundtrip) {
                String result2 = inverse.transliterate(target);
                assertEquals(id + " (inv): from " + target, source, result2);
            }
        }
    }

    private String getRelativeFileName(String relPath) {
    	String name = TestTransforms.class.getResource(".").toString();
		if (!name.startsWith("file:")) {
			throw new IllegalArgumentException("Internal Error");
		}
		name = name.substring(5);
		File fileDirectory = Paths.get(name,relPath).toFile();
		return fileDirectory.getAbsolutePath();
    }
    
    public void TestData() {
    	register();
    	try {
    		String fileDirectoryName=getRelativeFileName(TRANSFORMTEST_DIR);
    		File fileDirectory = new File(fileDirectoryName);
			try (DirectoryStream<Path> ds = Files.newDirectoryStream(
					fileDirectory.toPath(), "*.txt");) {
//    			String fileDirectoryName = fileDirectory.getCanonicalPath(); // TODO: use resource, not raw file
    			logln("Testing files in: " + fileDirectoryName);
    			for (Path p: ds) {
    				String file=p.getFileName().toString();
    				//    		for (String file : fileDirectory.list()) {
//    				if (!file.endsWith(".txt")) {
//    					continue;
//    				}
    				logln("Testing file: " + file);
    				String transName = file.substring(0, file.length() - 4);
    				final Transliterator trans = Transliterator.getInstance(transName);
    				try (BufferedReader in = BagFormatter.openUTF8Reader(fileDirectoryName, file);) {
    					int counter = 0;
    					String line=null;
    					while ((line = in.readLine()) !=null) {
    						line = line.trim();
    						if (line.startsWith("#")) {
    							continue;
    						}
    						String[] parts = line.split("\t");
    						String source = parts[0];
    						String expected = parts[1];
    						String result = trans.transform(source);
    						assertEquals(transName + " " + (++counter) + " Transform " + source, expected, result);
    					}
    				}
    			}
    			//                in.close();
    		}
    	} catch (IOException e) {
    		throw new IllegalArgumentException(e);
    	}
    }

    enum Casing {
        Upper, Title, Lower
    }

    public void TestCasing() throws IOException {
    	register();
         String casingFileStr = getRelativeFileName(CASING_TRANSFORMS);
         Iterable<TestTransformLine> ttIter=new TransformLineProcessor(casingFileStr).getLines();
    	for (TestTransformLine ttl: ttIter) {
    		checkString(ttl.locale, ttl.casing, ttl.result,ttl.source,ttl.specialCasing);
    	}
//        String greekSource = "ΟΔΌΣ Οδός Σο ΣΟ oΣ ΟΣ σ ἕξ";
//        // Transliterator.DEBUG = true;
//        Transliterator elTitle = checkString("el", Casing.Title, "Οδός Οδός Σο Σο Oς Ος Σ Ἕξ", greekSource, true);
//        Transliterator elLower = checkString("el", Casing.Lower, "οδός οδός σο σο oς ος σ ἕξ", greekSource, true);
//        Transliterator elUpper = checkString("el", Casing.Upper, "ΟΔΟΣ ΟΔΟΣ ΣΟ ΣΟ OΣ ΟΣ Σ ΕΞ", greekSource, false);
//
//        String turkishSource = "Isiİ İsıI";
//        Transliterator trTitle = checkString("tr", Casing.Title, "Isii İsıı", turkishSource, true);
//        Transliterator trLower = checkString("tr", Casing.Lower, "ısii isıı", turkishSource, true);
//        Transliterator trUpper = checkString("tr", Casing.Upper, "ISİİ İSII", turkishSource, true);
//        Transliterator azTitle = checkString("az", Casing.Title, "Isii İsıı", turkishSource, true);
//        Transliterator azLower = checkString("az", Casing.Lower, "ısii isıı", turkishSource, true);
//        Transliterator azUpper = checkString("az", Casing.Upper, "ISİİ İSII", turkishSource, true);
//
//        if (!logKnownIssue("cldrbug:7010", "Investigate/fix lt casing transforms")) {
//            String lithuanianSource = "I Ï J J̈ Į Į̈ Ì Í Ĩ xi̇̈ xj̇̈ xį̇̈ xi̇̀ xi̇́ xi̇̃ XI XÏ XJ XJ̈ XĮ XĮ̈";
//            Transliterator ltTitle = checkString("lt", Casing.Title,
//                "I Ï J J̈ Į Į̈ Ì Í Ĩ Xi̇̈ Xj̇̈ Xį̇̈ Xi̇̀ Xi̇́ Xi̇̃ Xi Xi̇̈ Xj Xj̇̈ Xį Xį̇̈", lithuanianSource, true);
//            Transliterator ltLower = checkString("lt", Casing.Lower,
//                "i i̇̈ j j̇̈ į į̇̈ i̇̀ i̇́ i̇̃ xi̇̈ xj̇̈ xį̇̈ xi̇̀ xi̇́ xi̇̃ xi xi̇̈ xj xj̇̈ xį xį̇̈", lithuanianSource, true);
//            Transliterator ltUpper = checkString("lt", Casing.Upper, "I Ï J J̈ Į Į̈ Ì Í Ĩ XÏ XJ̈ XĮ̈ XÌ XÍ XĨ XI XÏ XJ XJ̈ XĮ XĮ̈",
//                lithuanianSource, true);
//        }
//        String dutchSource = "IJKIJ ijkij IjkIj";
//        Transliterator nlTitle = checkString("nl", Casing.Title, "IJkij IJkij IJkij", dutchSource, true);
        //        Transliterator nlLower = checkString("nl", Casing.Lower, "ısii isıı", turkishSource);
        //        Transliterator nlUpper = checkString("tr", Casing.Upper, "ISİİ İSII", turkishSource);
    }

    private Transliterator checkString(String locale, Casing casing, String expected, String source, boolean sameAsSpecialCasing) {
        Transliterator translit = Transliterator.getInstance(locale + "-" + casing);
        String result = checkString(locale, expected, source, translit);
        ULocale ulocale = new ULocale(locale);
        String specialCasing;
        switch (casing) {
        case Upper:
            specialCasing = UCharacter.toUpperCase(ulocale, source);
            break;
        case Title:
            specialCasing = UCharacter.toTitleCase(ulocale, source, null);
            break;
        case Lower:
            specialCasing = UCharacter.toLowerCase(ulocale, source);
            break;
        default:
            throw new IllegalArgumentException();
        }
        if (sameAsSpecialCasing) {
            if (!assertEquals(locale + "-" + casing + " Vs SpecialCasing", specialCasing, result)) {
                showFirstDifference("Special: ", specialCasing, "Transform: ", result);
            }
        } else {
            assertNotEquals(locale + "-" + casing + "Vs SpecialCasing", specialCasing, result);
        }
        return translit;
    }

    private String checkString(String locale, String expected, String source, Transliterator translit) {
        String transformed = translit.transform(source);
        if (!assertEquals(locale, expected, transformed)) {
            showTransliterator(translit);
        }
        return transformed;
    }

    private void showFirstDifference(String titleA, String a, String titleB, String b) {
        StringBuilder buffer = new StringBuilder();
        for (int i = 0; i < Math.min(a.length(), b.length()); ++i) {
            char aChar = a.charAt(i);
            char bChar = b.charAt(i);
            if (aChar == bChar) {
                buffer.append(aChar);
            } else {
                errln("\t" + buffer + "\n\t\t" + titleA + "\t" + Utility.hex(a.substring(i))
                    + "\n\t\t" + titleB + "\t" + Utility.hex(b.substring(i)));
                return;
            }
        }
        errln("different length");
    }

    private void showTransliterator(Transliterator t) {
        org.unicode.cldr.test.TestTransforms.showTransliterator("", t, 999);
    }
}