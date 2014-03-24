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
     * Lowercase or uppercase letters
     */
    public static final Pattern LOWERCASE_AND_UPPERCASE_LETTERS=Pattern.compile("[a-zA-Z]+");
    
    /**
     * Numbers 0-9
     */
    public static final Pattern NUMBERS=Pattern.compile("[0-9]+");
    
    public static final Pattern WHITESPACE_ONCE=Pattern.compile("\\s");
    public static final Pattern SPACE_CHARACTER=Pattern.compile(" ");
    /**
     * Whitespace, at least once
     */
    public static final Pattern WHITESPACE=Pattern.compile("\\s+");
    
    /**
     * Whitespace, at least once, possessive version
     */
    public static final Pattern POSSESSIVE_WHITESPACE=Pattern.compile("\\s++");
    
    public static final Pattern BAR=Pattern.compile("\\|");
    
   // public static final Pattern BACKSLASH=Pattern.compile("\\");
    
    public static final Pattern COMMA=Pattern.compile(",");
    
    public static final Pattern TABULATOR=Pattern.compile("\t");
    
    public static final Pattern SEMICOLON=Pattern.compile(";");
    
    public static final Pattern SEMICOLON_WITH_WHITESPACE=Pattern.compile("\\s*;\\s*");
    
    public static final Pattern COLON=Pattern.compile(":");
    
    public static final Pattern NEWLINE=Pattern.compile("\r\n|\n");
    
    public static final Pattern SLASH=Pattern.compile("/");
    
    public static final Pattern EQUALS=Pattern.compile("=");
  
    public static final Pattern HASH=Pattern.compile("#");
    
    public static final Pattern UNDERSCORE = Pattern.compile("_");
    
    public Patterns() {
        
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
    
    public static String[] splitOnPossessiveWhitespace(String s) {
        List<String> tmpList=Splitter.on(POSSESSIVE_WHITESPACE).omitEmptyStrings().trimResults().splitToList(s);
        String[] result=new String[tmpList.size()];
        System.arraycopy(tmpList.toArray(), 0, result, 0, tmpList.size());
        return result;
//        return splitOn(POSSESSIVE_WHITESPACE, s);
    }
   
    public static String[] splitOnSingleWhitespace(String s) {
        List<String> tmpList=Splitter.on(WHITESPACE_ONCE).trimResults().splitToList(s);
        String[] result=new String[tmpList.size()];
        System.arraycopy(tmpList.toArray(), 0, result, 0, tmpList.size());
        return result;
//        return splitOn(WHITESPACE_ONCE, s);
    }
    
    public static String[] splitOnSpaceCharacter(String s) {
        List<String> tmpList=Splitter.on(SPACE_CHARACTER).trimResults().splitToList(s);
        String[] result=new String[tmpList.size()];
        System.arraycopy(tmpList.toArray(), 0, result, 0, tmpList.size());
        return result;
//        return result; return splitOn(SPACE_CHARACTER, s);
    }
   
    public static String[] splitOnWhitespace(String s) {
        List<String> tmpList=Splitter.on(WHITESPACE).trimResults().splitToList(s);
        String[] result=new String[tmpList.size()];
        System.arraycopy(tmpList.toArray(), 0, result, 0, tmpList.size());
        return result;
//        return splitOn(WHITESPACE, s);
    }
   
    public static String[] splitOnSemicolon(String s) {
        List<String> tmpList=Splitter.on(SEMICOLON).trimResults().splitToList(s);
        String[] result=new String[tmpList.size()];
        System.arraycopy(tmpList.toArray(), 0, result, 0, tmpList.size());
        return result;
//        return splitOn(SEMICOLON, s);
    }
    
    public static String[] splitOnSemicolon(String s,int limit) {
        return splitOn(SEMICOLON, s,limit);
    }
    public static String[] splitOnSemicolonWithWhiteSpace(String s) {
        List<String> tmpList=Splitter.on(SEMICOLON).trimResults().splitToList(s);
        String[] result=new String[tmpList.size()];
        System.arraycopy(tmpList.toArray(), 0, result, 0, tmpList.size());
        return result;
//        return splitOn(SEMICOLON_WITH_WHITESPACE, s);
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
