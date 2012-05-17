package org.unicode.cldr.unittest;

import org.unicode.cldr.test.DisplayAndInputProcessor;
import org.unicode.cldr.unittest.TestAll.TestInfo;
import org.unicode.cldr.util.CLDRFile;

import com.ibm.icu.dev.test.TestFmwk;
import com.ibm.icu.lang.CharSequences;
import com.ibm.icu.text.UnicodeSet;

public class TestDisplayAndInputProcessor extends TestFmwk{

    TestInfo info = TestAll.TestInfo.getInstance();

    public static void main(String[] args) {
        new TestDisplayAndInputProcessor().run(args);
    }

    public void TestAll() {
        showCldrFile(info.getEnglish());
        showCldrFile(info.getCldrFactory().make("wae", true));
    }

    public void TestTasawaq() {
        DisplayAndInputProcessor daip = new DisplayAndInputProcessor(info.getCldrFactory().make("twq", false));
        // time for data driven test
        final String input = "[Z \u017E ]";
        final String expect = "[z \u017E]"; // lower case
        String value = daip.processInput(
            "//ldml/characters/exemplarCharacters",
            input, null);
        if (!value.equals(expect)) {
            errln("Tasawaq incorrectly normalized with output: '" + value+"', expected '"+expect+"'");
        }
    }

    public void TestMalayalam() {
        DisplayAndInputProcessor daip = new DisplayAndInputProcessor(info.getCldrFactory().make("ml", false));
        String value = daip.processInput(
            "//ldml/localeDisplayNames/languages/language[@type=\"alg\"]",
            "അല്‍ഗോണ്‍ക്യന്‍ ഭാഷ", null);
        if (!value.equals("\u0D05\u0D7D\u0D17\u0D4B\u0D7A\u0D15\u0D4D\u0D2F\u0D7B \u0D2D\u0D3E\u0D37")) {
            errln("Malayalam incorrectly normalized with output: " + value);
        }
    }

    private void showCldrFile(final CLDRFile cldrFile) {
        DisplayAndInputProcessor daip = new DisplayAndInputProcessor(cldrFile);
        Exception[] internalException = new Exception[1];
        for (String path : cldrFile) {
            String value = cldrFile.getStringValue(path);
            String display = daip.processForDisplay(path, value);
            internalException[0] = null;
            String input = daip.processInput(path, display, internalException);
            String diff = diff(value, input, path);
            if (diff != null) {
                errln("No roundtrip in DAIP:"
                        + "\n\tvalue<" + value 
                        + ">\n\tdisplay<" + display 
                        + ">\n\tinput<" + input 
                        + ">\n\tdiff<" + diff
                        + (internalException[0] != null ? ">\n\texcep<" + internalException[0] : "" )
                        + ">\n\tpath<" + path + ">");
                daip.processInput(path, value, internalException); // for debugging
            } else if (!CharSequences.equals(value, display) 
                    || !CharSequences.equals(value, input) 
                    || internalException[0] != null) {
                logln("DAIP Changes"
                		+ "\n\tvalue<" + value 
                        + ">\n\tdisplay<" + display 
                        + ">\n\tinput<" + input 
                        + ">\n\tdiff<" + diff
                        + (internalException[0] != null ? ">\n\texcep<" + internalException[0] : "" )
                        + ">\n\tpath<" + path + ">");
            }
        }
    }

    private String diff(String value, String input, String path) {
        if (value.equals(input)) {
            return null;
        }
        if (path.contains("/exemplarCharacters")) {
            try {
                UnicodeSet s1 = new UnicodeSet(value);
                UnicodeSet s2 = new UnicodeSet(input);
                if (!s1.equals(s2)) {
                    UnicodeSet temp = new UnicodeSet(s1).removeAll(s2);
                    UnicodeSet temp2 = new UnicodeSet(s2).removeAll(s1);
                    temp.addAll(temp2);
                    return temp.toPattern(false);
                }
            } catch (Exception e) {
                // TODO: handle exception
            }   
        }
        return "?";
    }
}
