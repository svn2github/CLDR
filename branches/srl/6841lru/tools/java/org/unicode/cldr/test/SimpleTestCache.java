/**
 * 
 */
package org.unicode.cldr.test;

import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.util.Map;
import java.util.TreeMap;
import java.util.Vector;

import org.unicode.cldr.util.CLDRLocale;
import org.unicode.cldr.util.CLDRLocale.SublocaleProvider;
import org.unicode.cldr.util.LruMap;
import org.unicode.cldr.util.Pair;
import org.unicode.cldr.util.XMLSource;

/**
 * @author srl
 * 
 *         Factory must implement CLDRLocale.SublocaleProvider
 */
public class SimpleTestCache extends TestCache {
    /**
     * Hash the options map
     * 
     * @param o
     * @return
     */
    LruMap<Pair<CLDRLocale,CheckCLDR.Options>, Reference<TestResultBundle>> map = new LruMap<Pair<CLDRLocale, CheckCLDR.Options>, Reference<TestResultBundle>>(12);

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
        for (CLDRLocale sub : ((SublocaleProvider) getFactory()).subLocalesOf(locale)) {
            valueChanged(xpath, sub);
        }
        Vector<Pair<CLDRLocale,CheckCLDR.Options>> toRemove = new Vector<Pair<CLDRLocale,CheckCLDR.Options>>();
        for(Pair<CLDRLocale,CheckCLDR.Options> k : map.keySet()) {
            if(k.getFirst()==locale) {
                toRemove.add(k);
            }
        }
        // avoid concurrent remove
        for(Pair<CLDRLocale,CheckCLDR.Options> k : toRemove) {
            map.remove(k);
        }
    }

    @Override
    public TestResultBundle getBundle(CLDRLocale locale, CheckCLDR.Options options) {
        Pair<CLDRLocale,CheckCLDR.Options> k = new Pair<CLDRLocale,CheckCLDR.Options>(locale,options);
        Reference<TestResultBundle> ref = map.get(k);
        TestResultBundle b = (ref != null ? ref.get() : null);
        if (false) System.err.println("Bundle " + b + " for " + k);
        if (b == null) {
            // ElapsedTimer et = new ElapsedTimer("New test bundle " + locale + " opt " + options);
            b = new TestResultBundle(locale, options);
            // System.err.println(et.toString());
            map.put(k, new SoftReference<TestResultBundle>(b));
        }
        return b;
    }
}
