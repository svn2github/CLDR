package org.unicode.cldr.web;

import java.io.File;
import java.sql.Connection;
import java.util.logging.Logger;

import org.unicode.cldr.util.CLDRLocale;
import org.unicode.cldr.util.XMLSource;
import org.unicode.cldr.web.CLDRDBSourceFactory.DBEntry;
import org.unicode.cldr.web.CLDRDBSourceFactory.SubFactory;


/**
 * Dummy, to remove
 * @deprecated
 * @author srl
 *
 */
public class CLDRDBSourceFactory {
    public class SubFactory {

    }
    public CLDRDBSourceFactory(SurveyMain sm2, String fileBase,
            Logger anonymousLogger, File cacheDir) {
        // TODO Auto-generated constructor stub
    }
    public static class DBEntry {

        public void close() {
            // TODO Auto-generated method stub
            
        }

        public Connection getConnectionAlias() {
            // TODO Auto-generated method stub
            return null;
        }

    }
    public static final String CLDR_DATA = null;
    public static SurveyMain sm = new SurveyMain();
    public XMLSource getInstance(CLDRLocale l, boolean b) {
        // TODO Auto-generated method stub
        return null;
    }
    public int update() {
        // TODO Auto-generated method stub
        return 0;
    }
    public SubFactory getFactory(boolean b) {
        // TODO Auto-generated method stub
        return null;
    }
    public XMLSource getInstance(CLDRLocale loc) {
        // TODO Auto-generated method stub
        return null;
    }
    public void doDbUpdate(WebContext subCtx, SurveyMain surveyMain) {
        // TODO Auto-generated method stub
        
    }
    public DBEntry openEntry(SubFactory ourFactory) {
        // TODO Auto-generated method stub
        return null;
    }
}
