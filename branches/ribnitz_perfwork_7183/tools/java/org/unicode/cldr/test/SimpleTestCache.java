/**
 * 
 */
package org.unicode.cldr.test;

import java.util.Map.Entry;
import java.util.Vector;

import org.unicode.cldr.test.CheckCLDR.Options;
import org.unicode.cldr.util.CLDRConfig;
import org.unicode.cldr.util.CLDRLocale;
import org.unicode.cldr.util.CLDRLocale.SublocaleProvider;
import org.unicode.cldr.util.XMLSource;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

/**
 * @author srl
 * 
 *         Factory must implement CLDRLocale.SublocaleProvider
 */
public class SimpleTestCache extends TestCache {
    private static final boolean DEBUG = false;
    
    /**
     * Hash the options map
     * 
     * @param o
     * @return
     */
//    private LruMap<CheckCLDR.Options, Reference<TestResultBundle>> map = new LruMap<CheckCLDR.Options, Reference<TestResultBundle>>(CLDRConfig.getInstance()
//        .getProperty("CLDR_TESTCACHE_SIZE", 12));

    private Cache<CheckCLDR.Options, TestResultBundle> cache =CacheBuilder.newBuilder().maximumSize(CLDRConfig.getInstance()
        .getProperty("CLDR_TESTCACHE_SIZE", 12)).softValues().build();
    /*
     * (non-Javadoc)
     * 
     * @see org.unicode.cldr.test.TestCache#notifyChange(org.unicode.cldr.util.CLDRLocale, java.lang.String)
     */
    @Override
    public void valueChanged(String xpath, XMLSource source) {
        CLDRLocale locale = CLDRLocale.getInstance(source.getLocaleID());
        valueChanged(xpath, locale);
    }

    private void valueChanged(String xpath, CLDRLocale locale) {
        if (DEBUG) System.err.println("BundDelLoc " + locale + " @ " + xpath);
        for (CLDRLocale sub : ((SublocaleProvider) getFactory()).subLocalesOf(locale)) {
            valueChanged(xpath, sub);
        }
        Vector<CheckCLDR.Options> toRemove = new Vector<CheckCLDR.Options>();
        for (CheckCLDR.Options k : cache.asMap().keySet()) {
            if (k.getLocale() == locale) {
                toRemove.add(k);
            }
        }
        // avoid concurrent remove
        for (CheckCLDR.Options k : toRemove) {
            cache.asMap().remove(k);
            if (DEBUG) System.err.println("BundDel " + k);
        }
    }

    @Override
    public String toString() {
        StringBuilder stats = new StringBuilder();
        stats.append("{" + this.getClass().getSimpleName() + super.toString() + " Size: " + cache.size() + " (");
        int good = 0;
        int total = 0;
        for (Entry<Options,TestResultBundle> k : cache.asMap().entrySet()) {
            if (k.getValue()!= null) good++;
            if (DEBUG && true) stats.append("," + k.getKey() + "=" + k.getValue());
            total++;
        }
        stats.append(" " + good + "/" + total + "}");
        return stats.toString();
    }

    @Override
    public TestResultBundle getBundle(CheckCLDR.Options options) {
       TestResultBundle ref = cache.getIfPresent(options);
        if (DEBUG && ref != null) System.err.println("Bundle refvalid: " + options + " -> " + (ref != null));
        TestResultBundle b = (ref != null ? ref : null);
        if (DEBUG) System.err.println("Bundle " + b + " for " + options + " in " + this.toString());
        if (b == null) {
            // ElapsedTimer et = new ElapsedTimer("New test bundle " + locale + " opt " + options);
            b = new TestResultBundle(options);
            // System.err.println(et.toString());
            cache.put(options,b);
        }
        return b;
    }
}
