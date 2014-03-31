package org.unicode.cldr.util;

import java.util.regex.Pattern;

import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;

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
     * Lowercase or uppercase letters
     */
    public static final Pattern LOWERCASE_AND_UPPERCASE_LETTERS=Pattern.compile("[a-zA-Z]+");
    
    /**
     * Numbers 0-9
     */
    public static final Pattern NUMBERS=Pattern.compile("[0-9]+");
    
    /**
     * A single whitespace character (\\s)
     */
    public static final Pattern WHITESPACE_ONCE=Pattern.compile("\\s");
    
    /**
     * A single space character
     */
    public static final Pattern SPACE_CHARACTER=Pattern.compile(" ");
    /**
     * Whitespace, at least once (\\s+)
     */
    public static final Pattern WHITESPACE=Pattern.compile("\\s+");
    
    /**
     * Whitespace, at least once, possessive version, (\\s++)
     */
    public static final Pattern POSSESSIVE_WHITESPACE=Pattern.compile("\\s++");
    
    /**
     * Vertical bar
     */
    public static final Pattern BAR=Pattern.compile("\\|");
    
   // public static final Pattern BACKSLASH=Pattern.compile("\\");
    
    /**
     * A comma
     */
    public static final Pattern COMMA=Pattern.compile(",");
    
    /**
     * Tabulator (\t)
     */
    public static final Pattern TABULATOR=Pattern.compile("\t");
    
    /**
     * Semicolon (;)
     */
    public static final Pattern SEMICOLON=Pattern.compile(";");
    
    /**
     * Semicolon, optiomnally enclosed in whitespace (\\s*;\\s*)
     */
    public static final Pattern SEMICOLON_WITH_WHITESPACE=Pattern.compile("\\s*;\\s*");
    
    /**
     * Colon (:)
     */
    public static final Pattern COLON=Pattern.compile(":");
    
    /**
     * Newline 
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
    
    private final static Splitter WHITESPACE_ONCE_SPLITTER=Splitter.on(WHITESPACE_ONCE).omitEmptyStrings();
    // Not omitting empties because a test check splitting on space char
    private final static Splitter SPACE_CHAR_SPLITTER=Splitter.on(SPACE_CHARACTER);
    private final static Splitter WHITESPACE_SPLITTER=Splitter.on(WHITESPACE).omitEmptyStrings();
    private final static Splitter SEMICOLON_WITH_WHITESPACE_SPLITTER=Splitter.on(SEMICOLON_WITH_WHITESPACE).omitEmptyStrings();
    private final static Splitter POSSESSIVE_WHITESPACE_SPLITTER=Splitter.on(POSSESSIVE_WHITESPACE).omitEmptyStrings();
    
    
    
    public Patterns() {
        // Class only contains static methods or fields, nothing to
        // initialize
    }
    private static String[] splitOn(Pattern pat, String s) {
        return pat.split(s);
    }
    
    private static String[] splitOn(Pattern pat, String s,int limit) {
        return pat.split(s, limit);
    }
    
    public static String[] splitOnBar(String s) {
        return splitOn(BAR,s);
    }
    
    
    public static String[] splitOnColon(String s) {
       return splitOn(COLON, s);
    }
    
    public static String[] splitOnColon(String s,int limit) {
        return splitOn(COLON, s,limit);
    }
    
    public static String[] splitOnComma(String s) {
        return splitOn(COMMA, s);
    }
    
    public static String[] splitOnEquals(String s) {
        return splitOn(EQUALS, s);
    }
    
    public static String[] splitOnHash(String s) {
        return splitOn(HASH, s);
    }
    
    
    public static String[] splitOnLowercaseAndUppercase(String s) {
        return splitOn(LOWERCASE_AND_UPPERCASE_LETTERS, s);
    }
    
    public static String[] splitOnLowercase(String s) {
        return splitOn(LOWERCASE_LETTERS, s);
    }
    
    public static String[] splitOnNewline(String s) {
        return splitOn(NEWLINE, s);
    }
    
    public static String[] splitOnNumbers(String s) {
        return splitOn(NUMBERS, s);
    }

    
    /***
     * Split to iterable, using the pattern given
     * @param p
     * @param s
     * @return
     */
    private static Iterable<String> splitToIterable(Pattern p, String s) {
        return Splitter.on(p).split(s);
    }
    /**
     * Split to Iterable using the splitter given.
     * @param sp
     * @param s
     * @param doTrim
     * @return
     */
    private static Iterable<String> splitToIterable(Splitter sp,String s) {
        return sp.split(s);
    }
    
    /**
     * Split to Array, using the pattern given
     * @param p
     * @param s
     * @return
     */
    private static String[] splitToArray(Pattern p, String s) {
        Iterable<String> tmpList=splitToIterable(p, s);
        return Iterables.toArray(tmpList, String.class);
        }
    
    /**
     * Split to array, using the splitter given
     * @param sp
     * @param s
     * @param doTrim
     * @return
     */
    public static String[] splitToArray(Splitter sp,String s) {
        Iterable<String> tmpList=splitToIterable(sp, s);
        return Iterables.toArray(tmpList, String.class);
    }
    
    
    public static String[] splitOnPossessiveWhitespace(String s) {
        return splitToArray(POSSESSIVE_WHITESPACE_SPLITTER, s);
    }
   
    public static String[] splitOnSingleWhitespace(String s) {
        return splitToArray(WHITESPACE_ONCE_SPLITTER, s);
    }
    
    public static Iterable<String> splitOnSingleWhitespaceToIterable(String s) {
        return splitToIterable(WHITESPACE_ONCE_SPLITTER, s);
    }
    

    public static String[] splitOnSpaceCharacter(String s) {
        return splitToArray(SPACE_CHAR_SPLITTER, s);
//        return splitToArray(SPACE_CHARACTER, s);
    }
   
    public static Iterable<String> splitOnSpaceCharacterToIterable(String s) {
        return splitToIterable(SPACE_CHAR_SPLITTER, s);
//        return splitToIterable(SPACE_CHARACTER, s);
    }
    
 
    public static String[] splitOnWhitespace(String s) {
        return splitToArray(WHITESPACE_SPLITTER, s);
    }
   
    public static Iterable<String> splitOnWhitespaceToIterable(String s) {
        return splitToIterable(WHITESPACE_SPLITTER, s);
        
    }
    
    public static String[] splitOnSemicolon(String s) {
        return splitOn(SEMICOLON, s);
    }
    
    public static String[] splitOnSemicolon(String s,int limit) {
        return splitOn(SEMICOLON, s,limit);
    }
    public static String[] splitOnSemicolonWithWhiteSpace(String s) {
        return splitToArray(SEMICOLON_WITH_WHITESPACE_SPLITTER, s);
    }
    
    public static String[] splitOnSlash(String s) {
        return splitOn(SLASH, s);
    }
    
    public static String[] splitOnTabulator(String s) {
        return splitOn(TABULATOR, s);
    }
    
    public static String[] splitOnUppercase(String s) {
        return splitOn(UPPERCASE_LETTERS, s);
    }
    
    public static String[] splitOnUnderscore(String s) {
        return splitOn(UNDERSCORE, s);
    }
}
