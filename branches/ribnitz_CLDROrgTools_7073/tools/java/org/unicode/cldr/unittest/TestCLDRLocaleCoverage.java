package org.unicode.cldr.unittest;

import java.util.EnumSet;
import java.util.Set;

import org.unicode.cldr.util.Level;
import org.unicode.cldr.util.StandardCodes;
import org.unicode.cldr.util.VoteResolver.Organization;

import com.google.common.collect.Sets;

public class TestCLDRLocaleCoverage extends TestFmwkPlus {
    private static  StandardCodes sc=StandardCodes.make();
    public static void main(String[] args) {
        new TestCLDRLocaleCoverage().run(args);
    }
    
    /**
     * Test whether there are any locales for the organization CLDR
     */
    public void TestCLDROrganizationPresence() {
        Set<String> cldrLocales=sc.getLocaleCoverageLocales(Organization.cldr, EnumSet.of(Level.MODERN));
        assertNotNull("Expected CLDR modern locales not to be null",cldrLocales);
        assertTrue("Expected locales for CLDR, but found none.",cldrLocales!=null && !cldrLocales.isEmpty());
    }
    /**
    * Tests the validity of the file names and of the English localeDisplayName types. 
    * Also tests for aliases outside root
    */
    public void TestGoogleSubset() {
        Set<String> googleLocales=sc.getLocaleCoverageLocales(Organization.google,EnumSet.of(Level.MODERN));
        Set<String> cldrLocales=sc.getLocaleCoverageLocales(Organization.cldr, EnumSet.of(Level.MODERN));
        assertNotNull("Expected CLDR modern locales not to be null", cldrLocales);
        if (!cldrLocales.containsAll(googleLocales)) {
            printDifferences(googleLocales, cldrLocales,"Google","CLDR");
        }
        assertTrue("Expected CLDR locales to be a superset of Google ones, but they were not.", 
            cldrLocales.containsAll(googleLocales));
    }

    public void TestAppleSubset() {
        Set<String> appleLocales=sc.getLocaleCoverageLocales(Organization.apple,EnumSet.of(Level.MODERN));
        Set<String> cldrLocales=sc.getLocaleCoverageLocales(Organization.cldr, EnumSet.of(Level.MODERN));
        assertNotNull("Expected CLDR modern locales not to be null", cldrLocales);
        if (!cldrLocales.containsAll(appleLocales)) {
            printDifferences(appleLocales,cldrLocales,"Apple", "CLDR");
        }
        assertTrue("Expected CLDR modern locales to be a superset of Apple ones, but they were not.", cldrLocales.containsAll(appleLocales));
    }
    
    private void printDifferences(Set<String> firstLoccaleSet, Set<String> secondLocaleSet,String firstSetName,
        String secondSetName) {
        Set<String> diff2=Sets.difference(firstLoccaleSet, secondLocaleSet);
        if (!diff2.isEmpty()) {
            warnln("The following "+firstSetName+" modern locales were absent from the "+secondSetName+" set:"+diff2.toString());
        }
    }
}
