package org.unicode.cldr.util;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheStats;

/**
 * Simple class for caching Patterns, possibly avoiding the cost of 
 * compilation if they are in the cache.
 *
 * The class can be parametrized using the following keys in the CLDR properties file:
 * 
 * org.unicode.cldr.util.PatternCache.initialCapacity: initialCapacity of the Cache (default: 30)
 * org.unicode.cldr.util.PatternCache.maximumCapacity: maximum capacity of the Cache (default: 1000)
 * org.unicode.cldr.util.PatternCache.enable: enable caching (default: true)
 * org.unicode.cldr.util.PatternCache.debugRecordStatistics: record statistics for debugging purposes (default: false)
 * 
 * @author ribnitz
 *
 */
public class PatternCache {
    
    /**
     * Configuration to get default values from,
     */
    private static final CLDRConfig config=CLDRConfig.getInstance();
    
    /**
     * Logger for logging, better than System.out.println
     */
    private static final Logger LOGGER=Logger.getLogger(PatternCache.class.getName());
    
    /**
     * Initial capacity of the cache, key org.unicode.cldr.util.PatternCache.initialCapacity
     */
    private final static int INITIAL_CAPACITY=config.getProperty("org.unicode.cldr.util.PatternCache.initialCapacity", 30);
    
    /**
     * Maximum capacity of the cache, key org.unicode.cldr.util.PatternCache.maximumCapacity
     */
    private final static int MAX_CAPACITY=config.getProperty("org.unicode.cldr.util.PatternCache.maximumCapacity", 1000);
    
    /** 
     * Variable to control whether patterns are cached (true);
     *  or whether they are created each time.
     *  
     *   key org.unicode.cldr.util.PatternCache.enable
     */
    private final static boolean USE_CACHE=config.getProperty("org.unicode.cldr.util.PatternCache.enable", true);
    
    /**
     * Variable that controls whether statistics are recorded for the caching.
     * key org.unicode.cldr.util.PatternCache.debugRecordStatistics
     */
    private final static boolean DEBUG_RECORD_STATISTICS=config.getProperty("org.unicode.cldr.util.PatternCache.debugRecordStatistics", false);
  
    /**
     * The cache object
     */
    private final static Cache<String,Pattern> cache;
    
    /**
     * Static initialization of the cache object
     */
    static {
        if (USE_CACHE) {
            if (DEBUG_RECORD_STATISTICS) {
                cache=CacheBuilder.newBuilder().
                    initialCapacity(INITIAL_CAPACITY).
                    maximumSize(MAX_CAPACITY). recordStats().
                    build();
            } else {
                cache=CacheBuilder.newBuilder().
                    initialCapacity(INITIAL_CAPACITY).
                    maximumSize(MAX_CAPACITY).
                    build();
            }
        } else {
            cache=null;
        }
        if (!USE_CACHE) {
            LOGGER.log(Level.CONFIG,"PatternCache initialized; caching  Patterns is disabled");
        } else {
            LOGGER.log(Level.CONFIG,"PatternCache initialized: caching is enabled, minimum size: "+INITIAL_CAPACITY+", max size "+MAX_CAPACITY+"."+(!DEBUG_RECORD_STATISTICS?"not ":"")+" recording usage statistics");
        }
        
    }
            
    /**
     * Obtain a compiled Pattern from the String given; Results of the lookup may be cached, a cached result will be returned if
     * possible. 
     * @param patternStr the string to use for compilation
     * @throws IllegalArgumentException The string provided was null or empty, or there was a problem compiling the Pattern from the String
     */
    public static Pattern get(final String patternStr) {
        // Pre-conditions: non-null, non-empty string
        if (patternStr==null) {
            throw new IllegalArgumentException("Please call with non-null argument");
        }
        if (patternStr.isEmpty()) {
            throw new IllegalArgumentException("Please call with non-empty argument");
        }
        // If patterns are not cached, simply return a new compiled Pattern
        if (!USE_CACHE || cache == null) {
            try {
                return Pattern.compile(patternStr);
            } catch (PatternSyntaxException pse) {
                throw new IllegalArgumentException("The supplied pattern is not valid: "+patternStr,pse);
            }
        }
        Pattern result=null;
        try {
            result=cache.get(patternStr, new Callable<Pattern>() {
        
                @Override
                public Pattern call() throws Exception {
                    return Pattern.compile(patternStr);
                }
            });
        } catch (ExecutionException e) {
            // realistically, this is a PatternSyntaxException
            throw new IllegalArgumentException("The supplied pattern is not valid: "+patternStr,e);
        }
        return result;
    }
    
    /**
     * Obtain collected statistics about this cache object
     * @return
     */
    public static CacheStats getStatistics() {
        if (cache!=null) {
            return cache.stats();
        }
        return new CacheStats(0, 0, 0, 0, 0, 0);
    }
    
    /**
     * Obtain information whether this cache is caching the patterns it returns
     * @return
     */
    public static boolean isCachingEnabled() {
        return USE_CACHE;
    }
    
    /**
     * Obtain information about whether this cache is recording statistics for debugging purposes
     * @return
     */
    public static boolean isDebugRecordStatistics() {
        return DEBUG_RECORD_STATISTICS;
    }
    
    /**
     * Get the current size of the cache
     * @return
     */
    public static long size() {
        return cache.size();
    }
   
}