// Copyright 2011 Google Inc. All Rights Reserved.

package org.unicode.cldr.surveytool.server;

import org.unicode.cldr.util.CLDRFile;
import org.unicode.cldr.util.CLDRFile.DraftStatus;
import org.unicode.cldr.util.CLDRFile.Factory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.jdo.PersistenceManager;

/**
 * @author anthonyfernandez.af@gmail.com (Tony Fernandez)
 * 
 */
public class CloudTestSetUp {
    private static String CLDR_VERSION = "2.1";
    private static String CLDR_DIRECTORY = "WEB-INF/2.1/common/main";

    /**
     * 
     * @param pm
     * @param regex     regex of locales
     * @param keyRegex  regex of keys in hashmaps
     * @param valRegex  regex of values in xpath_values (XMLSource)
     * @param resolving
     * @return table of search results formatted in HTML
     */
    public static String find(PersistenceManager pm, String regex, String keyRegex,
        String valRegex, boolean resolving) {
        StringBuilder result = new StringBuilder();
        result.append("<table border=\"1\">\n<tr><th>Locale</th><th>Xpath</th><th>Value</th></tr>\n");
        Factory factory = CloudFactory.make(CLDR_DIRECTORY, regex, DraftStatus.unconfirmed,
                                        CLDR_VERSION);
        Iterator<String> itr = factory.getAvailable().iterator();
        String locale;
        while (itr.hasNext()) {
            locale = itr.next();
            CLDRFile myCLDR = factory.make(locale, resolving, DraftStatus.unconfirmed);
            Iterator<String> keyItr = myCLDR.iterator();
            String key;
            while (keyItr.hasNext()) {
                key = keyItr.next();
                Pattern p = Pattern.compile(keyRegex);
                Matcher m = p.matcher(key);
                if (m.find()) {
                    String val = myCLDR.getDataSource().getValueAtPath(key); // (key);
                    p = Pattern.compile(valRegex);
                    m = p.matcher(val);
                    if (m.find()) {
                        result.append("<tr><td>").append(locale).append("</td><td>").append(key)
                                                        .append("</td><td>").append(val)
                                                        .append("</td></tr>\n");
                    }
                }
            }
        }
        return result.append("</table>").toString();
    }

    /**
     * Persists CLDR data from disk and saves it to the datastore. Only needs to
     * be done once.
     * 
     * @param pm
     * @param i the int representation of the first letter of locales to load.
     */
    public static void setUp(PersistenceManager pm, int i) {
        // 'a' == 97
        String alphaRange = ((char) (i + 97)) + ".*";
        Factory factory = Factory.make(CLDR_DIRECTORY, alphaRange,
                                        DraftStatus.unconfirmed);
        CLDRFile ref;
        LocaleWrapper loc;
        ArrayList<LocaleWrapper> arr = new ArrayList<LocaleWrapper>();
        for (String locID : factory.getAvailable()) {
            ref = factory.make(locID, false, DraftStatus.unconfirmed);
            loc = new LocaleWrapper(ref);
            arr.add(loc);
        }
        pm.makePersistentAll(arr);
        System.out.println("Data loaded and persisted.");
    }

    /**
     * DEBUG. shows what's visible to factory in cloud.
     * 
     * @return
     */
    public static String verifyAvailableList() {
        Factory fact = Factory.make(CLDR_DIRECTORY, ".*", DraftStatus.unconfirmed);
        return fact.getAvailable().toString();
    }

    /**
     * @param pm
     * @param regex
     * @param resolving
     * @return
     */
    public static String SpeedTest(PersistenceManager pm, String regex, boolean resolving) {
        int numIterations = 2; // # iterations for each factory.
        Factory factory = Factory.make(CLDR_DIRECTORY, regex,
                                        DraftStatus.unconfirmed);
        CloudFactory clFactory = (CloudFactory) CloudFactory.make(CLDR_DIRECTORY, regex,
                                        DraftStatus.unconfirmed, CLDR_VERSION);
        Iterator<String> localeItr = factory.getAvailable().iterator();
        String ret = "";
        String locale;
        while (localeItr.hasNext()) {
            locale = localeItr.next();
            Iterator<String> itr;
            long start = System.currentTimeMillis();
            CLDRFile cloud = clFactory.handleMake(locale, resolving, DraftStatus.unconfirmed);
            int cloudKeys = 0;
            for (int i = 0; i < numIterations; ++i) {
                itr = cloud.iterator();
                String key;
                cloudKeys = 0;
                while (itr.hasNext()) {
                    key = itr.next();
                    cloud.getDataSource().getValueAtDPath(key);
                    cloudKeys++;
                }
            }
            long elapsed = System.currentTimeMillis() - start;
            start = System.currentTimeMillis();
            CLDRFile local = factory.make(locale, resolving, DraftStatus.unconfirmed);
            int localKeys = 0;
            for (int i = 0; i < numIterations; ++i) {
                itr = local.iterator();
                String key;
                localKeys = 0;
                while (itr.hasNext()) {
                    key = itr.next();
                    local.getDataSource().getValueAtDPath(key);
                    localKeys++;
                }
            }
            long cloudElapsed = System.currentTimeMillis() - start;
            ret += locale + ": Cloud run time: " + Long.toString(elapsed) + " Over "
                                            + Integer.toString(cloudKeys) + "\nLocal run time: "
                                            + Long.toString(cloudElapsed) + " Over "
                                            + Integer.toString(localKeys);

        }
        return ret;
        // 3215485(local) 139459 (cloud)
    }

    /**
     * Verfies equivalence of datastore locales and locales from disk (XML).
     * 
     * @param pm
     * @param regex
     * @param resolving
     * @return
     */
    public static String EquivTest(PersistenceManager pm, String regex, boolean resolving) {
        Factory factory = Factory.make(CLDR_DIRECTORY, regex,
                                        DraftStatus.unconfirmed);
        Factory clFactory = CloudFactory.make(CLDR_DIRECTORY, regex,
                                        DraftStatus.unconfirmed, CLDR_VERSION);
        Iterator<String> localeItr = factory.getAvailable().iterator();
        String ret = "Everything matches!";
        String locale;
        while (localeItr.hasNext()) {
            locale = localeItr.next();
            Iterator<String> itr;
            CLDRFile local = factory.make(locale, resolving, DraftStatus.unconfirmed);
            CLDRFile cloud = clFactory.make(locale, resolving, DraftStatus.unconfirmed);
            itr = local.iterator();
            String key;
            while (itr.hasNext()) {
                key = itr.next();
                if (!(local.getStringValue(key).equals(cloud.getStringValue(key)))) {
                    return "Equivalence value failure at locale: " + locale + " at xpath: " + key;
                }
                if (!(local.getFullXPath(key).equals(cloud.getFullXPath(key)))) {
                    return "FullXPath equivalence failure at locale: " + locale + " at xpath: "
                                                    + key;
                }
            }
        }
        return ret;
    }

}
