package org.unicode.cldr.util;

import java.util.List;
import java.util.regex.Pattern;

import com.google.common.base.Splitter;

/**
 * Helper class, contains a number of commonly-used patterns (which are pre-compiled, for speed), and a number of
 * utility functions for splitting
 * @author ribnitz
 *
 */
public class Patterns {
    /** 
     * Lowercase letters, [a-z]+ 
     */
    public static final Pattern LOWERCASE_LETTERS=Pattern.compile("[a-z]+");
    /**
     * Uppercase letters, {A-Z]+
     */
    public static final Pattern UPPERCASE_LETTERS=Pattern.compile("[A-Z]+");
    /**
     * Lowercase or uppercase letters, "[a-zA-Z]+"
     */
    public static final Pattern LOWERCASE_AND_UPPERCASE_LETTERS=Pattern.compile("[a-zA-Z]+");
    
    /**
     * Numbers 0-9: "[0-9]+"
     */
    public static final Pattern NUMBERS=Pattern.compile("[0-9]+");
    
    /**
     * A single whitespace character, \\s
     */
    public static final Pattern WHITESPACE_ONCE=Pattern.compile("\\s");
    
    /**
     * A single space character (" ")
     */
    public static final Pattern SPACE_CHARACTER=Pattern.compile(" ");
    /**
     * Whitespace, at least once, \\s+
     */
    public static final Pattern WHITESPACE=Pattern.compile("\\s+");
    
    /**
     * Whitespace, at least once, possessive version, \\s++
     */
    public static final Pattern POSSESSIVE_WHITESPACE=Pattern.compile("\\s++");
    
    /**
     * Vertical bar, \\|
     */
    public static final Pattern BAR=Pattern.compile("\\|");
    
   // public static final Pattern BACKSLASH=Pattern.compile("\\");
    
    /**
     * A comma (,)
     */
    public static final Pattern COMMA=Pattern.compile(",");
    
    /**
     * A comma, possibly surrrounded by spacing ('\\s*,\\s*')
     */
    public final static Pattern COMMA_WITH_SPACING=Pattern.compile("\\s*,\\s*");
    /**
     * Tabulator (\t)
     */
    public static final Pattern TABULATOR=Pattern.compile("\t");
    
    /**
     * Semicolon (;)
     */
    public static final Pattern SEMICOLON=Pattern.compile(";");
    
    /**
     * Semicolon, optionally enclosed in whitespace (\\s*;\\s*)
     */
    public static final Pattern SEMICOLON_WITH_WHITESPACE=Pattern.compile("\\s*;\\s*");
    
    /**
     * Colon (:)
     */
    public static final Pattern COLON=Pattern.compile(":");
    
    /**
     * Newline "\r\n|\n"
     */
    public static final Pattern NEWLINE=Pattern.compile("\r\n|\n");
    
    /**
     * Forward slash (/)
     */
    public static final Pattern SLASH=Pattern.compile("/");
    
    /**
     * Equals sign (=)
     */
    public static final Pattern EQUALS=Pattern.compile("=");
  
    /**
     * Hash mark (#)
     */
    public static final Pattern HASH=Pattern.compile("#");
    
    /**
     * Underscore (_)
     */
    public static final Pattern UNDERSCORE = Pattern.compile("_");
    
    // Each pattern has a private Splitter associated with it
    private final static Splitter WHITESPACE_ONCE_SPLITTER=Splitter.on(WHITESPACE_ONCE).omitEmptyStrings();
    private final static Splitter SPACE_CHAR_SPLITTER=Splitter.on(SPACE_CHARACTER).omitEmptyStrings();
    private final static Splitter WHITESPACE_SPLITTER=Splitter.on(WHITESPACE).omitEmptyStrings();
    private final static Splitter SEMICOLON_WITH_WHITESPACE_SPLITTER=Splitter.on(SEMICOLON_WITH_WHITESPACE).omitEmptyStrings();
    private final static Splitter POSSESSIVE_WHITESPACE_SPLITTER=Splitter.on(POSSESSIVE_WHITESPACE).omitEmptyStrings();
    private final static Splitter SEMICOLON_SPLITTER=Splitter.on(SEMICOLON);
    private final static Splitter SLASH_SPLITTER=Splitter.on(SLASH);
    private final static Splitter COMMA_SPLITTER=Splitter.on(COMMA);
    private final static Splitter COLON_SPLITTER=Splitter.on(COLON);
    private final static Splitter BAR_SPLITTER=Splitter.on(BAR);
    private final static Splitter EQUALS_SPLITTER=Splitter.on(EQUALS);
    private final static Splitter HASH_SPLITTER=Splitter.on(HASH);
    private final static Splitter NEWLINE_SPLITTER=Splitter.on(NEWLINE);
    private final static Splitter NUMBERS_SPLITTER=Splitter.on(NUMBERS);
    private final static Splitter LOWERCASE_AND_UPPERCASE_SPLITTER=Splitter.on(LOWERCASE_AND_UPPERCASE_LETTERS);
    private final static Splitter LOWERCASE_SPLITTER=Splitter.on(LOWERCASE_LETTERS);
    private final static Splitter TABULATOR_SPLITTER=Splitter.on(TABULATOR);
    private final static Splitter UPPERCASE_SPLITTER=Splitter.on(UPPERCASE_LETTERS);
    private final static Splitter UNDERSCORE_SPLITTER=Splitter.on(UNDERSCORE);
    private final static Splitter COMMA_WITH_SPACING_SPLITTER=Splitter.on(COMMA_WITH_SPACING);
    
    
    
    public Patterns() {
        // Class only contains static methods or fields, nothing to
        // initialize
    }
    
   /**
    * Equivalent of: s.split("\\|")
    * @param s
    * @return
    */
    public static String[] splitOnBar(String s) {
        return splitToArray(BAR_SPLITTER,s);
    }
    
    /**
     * Equivalent of s.split(":");
     * @param s
     * @return
     */
    public static String[] splitOnColon(String s) {
       return splitToArray(COLON_SPLITTER, s);
    }
    
    /**
     * Equivalent of s.split(":",limit)
     * @param s
     * @param limit
     * @return
     */
    public static String[] splitOnColon(String s,int limit) {
        List<String> result=COLON_SPLITTER.limit(limit).splitToList(s);
        return result.toArray(new String[0]);
    }

    /**
     * Equivalent of s.split(",") 
     * @param s
     * @return
     */
    public static String[] splitOnComma(String s) {
        return splitToArray(COMMA_SPLITTER, s);
    }
    
    /**
     * Equivalent of s.split("\\s*,\\s*")
     * @param s
     * @return
     */
   public static String[] splitOnCommaWithSpacing(String s) {
       return splitToArray(COMMA_WITH_SPACING_SPLITTER, s);
   }
    
   /**
    * Equivalent of s.split("=")
    * @param s
    * @return
    */
    public static String[] splitOnEquals(String s) {
        return splitToArray(EQUALS_SPLITTER,s);
    }
    
    /**
     * Equivalent of s.split("#")
     * @param s
     * @return
     */
    public static String[] splitOnHash(String s) {
        return splitToArray(HASH_SPLITTER, s);
    }
    
    
    /**
     * Equivalent of s.split("[a-zA-Z]+"
     * @param s
     * @return
     */
    public static String[] splitOnLowercaseAndUppercase(String s) {
        return  splitToArray(LOWERCASE_AND_UPPERCASE_SPLITTER, s);
    }
    
    /**
     * Equivalent of s.split("[a-z]+")
     * @param s
     * @return
     */
    public static String[] splitOnLowercase(String s) {
        return splitToArray(LOWERCASE_SPLITTER, s);
    }
    
    /**
     * Equivalent of s.split("\r\n|\n")
     * @param s
     * @return
     */
    public static String[] splitOnNewline(String s) {
        return splitToArray(NEWLINE_SPLITTER, s);
    }
    
    /**
     * Equivalent of s.split("[0-9]+")
     * @param s
     * @return
     */
    public static String[] splitOnNumbers(String s) {
        return splitToArray(NUMBERS_SPLITTER, s);
    }

    /**
     * Split to array, using the splitter given
     * @param sp
     * @param s
     * @return
     */
    private static String[] splitToArray(Splitter sp,String s) {
        List<String> tmpList=sp.splitToList(s);
        return tmpList.toArray(new String[0]);
       
    }
    
    /**
     * Equivalent of s.split("\\s++")
     * @param s
     * @return
     */
    public static String[] splitOnPossessiveWhitespace(String s) {
        return splitToArray(POSSESSIVE_WHITESPACE_SPLITTER, s);
    }
   
    /**
     * Equivalent of s.split(" ")
     * @param s
     * @return
     */
    public static String[] splitOnSingleWhitespace(String s) {
        return splitToArray(WHITESPACE_ONCE_SPLITTER, s);
    }
    
    /**
     * Equivalent of s.split(" ")
     * @param s
     * @return
     */
    public static List<String> splitOnSingleWhiteSpaceToList(String s) {
        return WHITESPACE_SPLITTER.splitToList(s);
    }
    /**
     * Equivalent of s.split(" ")
     * @param s
     * @return
     */
    public static String[] splitOnSpaceCharacter(String s) {
        return splitToArray(SPACE_CHAR_SPLITTER, s);
    }
   
    public static Iterable<String> splitOnSpaceCharacterToIterable(String s) {
        return SPACE_CHAR_SPLITTER.split(s);
    }
    
 
    public static String[] splitOnWhitespace(String s) {
        return splitToArray(WHITESPACE_SPLITTER, s);
    }
   
    public static Iterable<String> splitOnWhitespaceToIterable(String s) {
        return WHITESPACE_SPLITTER.split(s);
        
    }
    
    public static String[] splitOnSemicolon(String s) {
        return splitToArray(SEMICOLON_SPLITTER, s);
    }
    
    public static String[] splitOnSemicolon(String s,int limit) {
        List<String> results=SEMICOLON_SPLITTER.limit(limit).splitToList(s);
        return results.toArray(new String[0]);
    }
    
    public static String[] splitOnSemicolonWithWhiteSpace(String s) {
        return splitToArray(SEMICOLON_WITH_WHITESPACE_SPLITTER, s);
    }
    
  
    public static String[] splitOnSlash(String s) {
        return splitToArray(SLASH_SPLITTER, s);
    }
    

    public static String[] splitOnTabulator(String s) {
        return splitToArray(TABULATOR_SPLITTER, s);
    }
    
    public static String[] splitOnUppercase(String s) {
        return splitToArray(UPPERCASE_SPLITTER, s);
    }
    
    public static String[] splitOnUnderscore(String s) {
        return splitToArray(UNDERSCORE_SPLITTER, s);
    }
}
