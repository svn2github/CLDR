package org.unicode.cldr.unittest.web;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.logging.Logger;

import org.unicode.cldr.icu.LDMLConstants;
import org.unicode.cldr.unittest.web.TestAll.WebTestInfo;
import org.unicode.cldr.util.CLDRFile;
import org.unicode.cldr.util.CLDRLocale;
import org.unicode.cldr.util.CldrUtility;
import org.unicode.cldr.util.XMLSource;
import org.unicode.cldr.web.CLDRDBSourceFactory;
import org.unicode.cldr.web.CLDRFileCache;
import org.unicode.cldr.web.CLDRProgressIndicator;
import org.unicode.cldr.web.CookieSession;
import org.unicode.cldr.web.UserRegistry;
import org.unicode.cldr.web.Vetting;
import org.unicode.cldr.web.XPathTable;
import org.unicode.cldr.web.CLDRDBSourceFactory.CLDRDBSource;
import org.unicode.cldr.web.DBUtils;
import org.unicode.cldr.web.SurveyMain;

import com.ibm.icu.dev.test.TestFmwk;

public class TestCacheAndDataSource  extends TestFmwk {
	
	TestAll.WebTestInfo testInfo = WebTestInfo.getInstance();
	
	CLDRDBSourceFactory gFac = null;
	
	public static void main(String[] args) {
		new TestCacheAndDataSource().run(args);
	}

	public TestCacheAndDataSource() {
		TestAll.setupTestDb();
	}
	String someLocales[] = { "mt" };

	
	
	public void TestCacheLoadAll() throws SQLException {
		CLDRDBSourceFactory dbsrcfac = getFactory();
		
		int n= dbsrcfac.update();
		logln("update: " + n);
		
		CLDRFile.Factory f = testInfo.getCldrFactory();
		
		//for(CLDRLocale l : dbsrcfac.sm.getLocales() ) {
		
		for(String s : someLocales ) {
			CLDRLocale l = CLDRLocale.getInstance(s);
					logln("Loading: " + l);
			dbsrcfac.getInstance(l, false);
		}
		for(String s : someLocales ) {
			CLDRLocale l = CLDRLocale.getInstance(s);
					logln("Loading: " + l);
			dbsrcfac.getInstance(l, false);
		}
	}
	
	public static final String LANGDISP = "//ldml/localeDisplayNames/languages";
	
	public void TestCacheModify() throws SQLException {
		
		CLDRDBSourceFactory dbsrcfac = getFactory();
		dbsrcfac.sm.vet.updateResults();
		int updcount;
		updcount= dbsrcfac.update();
		logln("update: " + updcount);
		String hash = CookieSession.cheapEncode(42 /* System.currentTimeMillis() */);
		String drafty = "[@alt=\"proposed-u0-x"+hash+"\"]"; // [@draft=\"unconfirmed\"]";
		for(String s : someLocales ) {
			CLDRLocale l = CLDRLocale.getInstance(s);
					logln("Loading: " + l);
			XMLSource src = dbsrcfac.getInstance(l, false);
			
			for(String xp: someLocales) {
				String anxpath = LANGDISP+"/language[@type=\""+xp+"\"]";
				String val = src.getValueAtPath(anxpath);
				logln(l+": "+anxpath + " = "  +val);
				String newXpath =  (anxpath+drafty);
				logln("newXpath: " + newXpath);
				if(!src.hasValueAtDPath(newXpath)) {
					src.putValueAtPath(newXpath, "Something");
					logln("Put value at " + newXpath);
				} else {
					logln("Already had value at " + newXpath);
				}
				
				int baseXpid = dbsrcfac.sm.xpt.getByXpath(anxpath);
				int theXpid = dbsrcfac.sm.xpt.getByXpath(newXpath);
				dbsrcfac.sm.vet.vote(l, baseXpid, 0, -1, Vetting.VET_EXPLICIT);
				logln("User #0 voting for " + theXpid + " on base " + baseXpid);
				
			}
		}
		updcount= dbsrcfac.update();
		logln("added but voted -1, update: " + updcount);
		updcount = dbsrcfac.sm.vet.updateResults();
		logln("Results updated: " + updcount);
		
		for(String s : someLocales ) {
			CLDRLocale l = CLDRLocale.getInstance(s);
					logln("Loading: " + l);
			XMLSource src = dbsrcfac.getInstance(l, true);
			
			for(String xp: someLocales) {
				String anxpath = LANGDISP+"/language[@type=\""+xp+"\"]";
				String val = src.getValueAtPath(anxpath);
				logln(l+": "+anxpath + " FINAL = "  +val);
				if(val.equals("Something")) {
					errln(l+": "+anxpath + " FINAL = "  +val + " - should NOT have been Something");
				}
				String newXpath = (anxpath+drafty);
				
				
				int baseXpid = dbsrcfac.sm.xpt.getByXpath(anxpath);
				int theXpid = dbsrcfac.sm.xpt.getByXpath(newXpath);
				
				logln("Race: " + dbsrcfac.sm.vet.getRace(l, baseXpid).resolverToString());
				dbsrcfac.sm.vet.vote(l, baseXpid, 0, theXpid, Vetting.VET_EXPLICIT); // unvote
			}
		}
		updcount = dbsrcfac.update();
		logln("changed to (vote for), updates: " + updcount);
		updcount = dbsrcfac.sm.vet.updateResults();
		logln("Results updated: " + updcount);


		for(String s : someLocales ) {
			CLDRLocale l = CLDRLocale.getInstance(s);
					logln("Loading: " + l);
			XMLSource src = dbsrcfac.getInstance(l, true);
			
			for(String xp: someLocales) {
				String anxpath = LANGDISP+"/language[@type=\""+xp+"\"]";
				String val = src.getValueAtPath(anxpath);
				logln(l+": "+anxpath + " FINAL = "  +val);
				if(!val.equals("Something")) {
					errln(l+": "+anxpath + " FINAL = "  +val + " - should have been Something");
				}
				int baseXpid = dbsrcfac.sm.xpt.getByXpath(anxpath);
				//dbsrcfac.sm.vet.vote(l, baseXpid, 0, -1, Vetting.VET_EXPLICIT);
				logln("Race: " + dbsrcfac.sm.vet.getRace(l, baseXpid).resolverToString());
				logln("User #0 was voting  	"  + " on base " + baseXpid);
			}
		}
	}

	private CLDRDBSourceFactory getFactory() throws SQLException {
		if(gFac==null) {
			
			File cacheDir = TestAll.getEmptyDir("cachetest");
			logln("Setting up dbsrcfac");
			SurveyMain sm = new SurveyMain();
			sm.twidPut(Vetting.TWID_VET_VERBOSE, true); // set verbose vetting
			sm.logger = Logger.getAnonymousLogger();
			Connection conn = DBUtils.getInstance().getDBConnection();
			
			sm.reg = UserRegistry.createRegistry(sm.logger, sm);
			
			sm.xpt = XPathTable.createTable(Logger.getAnonymousLogger(), conn, sm);
			DBUtils.closeDBConnection(conn);
			
			sm.vet = Vetting.createTable(sm.logger, sm);
			
			sm.fileBase = CldrUtility.MAIN_DIRECTORY;
			CLDRDBSourceFactory fac = new CLDRDBSourceFactory(sm, sm.fileBase, Logger.getAnonymousLogger(), cacheDir);
			logln("Setting up DB");
			sm.dbsrcfac=fac;
			fac.setupDB(DBUtils.getInstance().getDBConnection());
			logln("Vetter Ready (this will take a while..)");
			fac.vetterReady(TestAll.getProgressIndicator(this));
			
			gFac = fac;
		}
		return gFac;
	}

}
