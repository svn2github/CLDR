package org.unicode.cldr.unittest.web;

/**
 * Copyright (C) 2012
 */

import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import org.unicode.cldr.util.CLDRConfig;
import org.unicode.cldr.util.CLDRFile;
import org.unicode.cldr.util.CLDRLocale;
import org.unicode.cldr.util.Factory;
import org.unicode.cldr.util.XPathParts;
import org.unicode.cldr.web.CookieSession;
import org.unicode.cldr.web.DBUtils;
import org.unicode.cldr.web.STFactory;
import org.unicode.cldr.web.UserRegistry;
import org.unicode.cldr.web.STFactory.LocaleMaxSizer;
import org.unicode.cldr.web.SurveyMain;
import org.unicode.cldr.web.XPathTable;

import com.ibm.icu.dev.test.TestFmwk;

/**
 * @author srl
 * 
 */
public class TestMisc extends TestFmwk {
    public static void main(String[] args) {
        new TestMisc().run(args);
    }

    public void TestLocaleMaxSizer() {
        logln("Creating new sizer..");
        STFactory.LocaleMaxSizer lms = new STFactory.LocaleMaxSizer();
        
        Factory f = CLDRConfig.getInstance().getCldrFactory();
        logln("Populating sizer..");
        for(CLDRLocale l : f.getAvailableCLDRLocales()) {
            lms.add(l);
        }
        
        final String TESTCASES[] = {
            "root", "//foo/bar", Integer.toString(STFactory.LocaleMaxSizer.MAX_VAL_LEN),
            "zh", "//foo/bar", Integer.toString(STFactory.LocaleMaxSizer.MAX_VAL_LEN),
            "zh", "//ldml/characters/exemplarCharacters", Integer.toString(STFactory.LocaleMaxSizer.EXEMPLAR_CHARACTERS_MAX),
            "zh_Hans_CN", "//ldml/characters/exemplarCharacters", Integer.toString(STFactory.LocaleMaxSizer.EXEMPLAR_CHARACTERS_MAX),
            "zh_Hans_CN", "//ldml/characters/exemplarCharacters", Integer.toString(STFactory.LocaleMaxSizer.EXEMPLAR_CHARACTERS_MAX),
            "zh_Hant", "//ldml/characters/exemplarCharacters[@type=\"auxiliary\"]", Integer.toString(STFactory.LocaleMaxSizer.EXEMPLAR_CHARACTERS_MAX),
            "zh_Hant_HK", "//ldml/characters/exemplarCharacters", Integer.toString(STFactory.LocaleMaxSizer.EXEMPLAR_CHARACTERS_MAX),
        };
        
        for(int i=0;i<TESTCASES.length;i+=3) {
            final CLDRLocale loc = CLDRLocale.getInstance(TESTCASES[i+0]);
            final String xpath = TESTCASES[i+1];
            final Integer expectedSize = Integer.parseInt(TESTCASES[i+2]);
            
            assertEquals(loc+":"+xpath, (long)expectedSize, (long)lms.getSize(loc, xpath));
        }
    }
}
