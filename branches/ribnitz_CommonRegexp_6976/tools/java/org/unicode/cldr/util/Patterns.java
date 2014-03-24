package org.unicode.cldr.util;

import java.util.regex.Pattern;

/**
 * Helper class, contains a number of commonly-used patterns (which are pre-compiled, for speed)
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
}
