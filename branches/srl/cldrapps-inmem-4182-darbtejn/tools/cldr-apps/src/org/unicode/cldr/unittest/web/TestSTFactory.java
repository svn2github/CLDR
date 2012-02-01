package org.unicode.cldr.unittest.web;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Logger;

import org.unicode.cldr.unittest.web.TestAll.WebTestInfo;
import org.unicode.cldr.util.CLDRFile;
import org.unicode.cldr.util.CLDRLocale;
import org.unicode.cldr.util.CldrUtility;
import org.unicode.cldr.util.Factory;
import org.unicode.cldr.util.SimpleXMLSource;
import org.unicode.cldr.util.CLDRFile.DraftStatus;
import org.unicode.cldr.web.BallotBox;
import org.unicode.cldr.web.CLDRDBSourceFactory;
import org.unicode.cldr.web.DBUtils;
import org.unicode.cldr.web.STFactory;
import org.unicode.cldr.web.SurveyLog;
import org.unicode.cldr.web.SurveyMain;
import org.unicode.cldr.web.UserRegistry;
import org.unicode.cldr.web.UserRegistry.User;
import org.unicode.cldr.web.Vetting;
import org.unicode.cldr.web.XPathTable;

import com.ibm.icu.dev.test.TestFmwk;

public class TestSTFactory extends TestFmwk {
	
	private static final String CACHETEST = "cachetest";

	TestAll.WebTestInfo testInfo = WebTestInfo.getInstance();
	
	STFactory gFac = null;
	UserRegistry.User gUser = null;
	
	public static void main(String[] args) {
		new TestSTFactory().run(TestAll.doResetDb(args));
	}

	public void TestReadonlyLocales() throws SQLException {
		logln("Setting up factory..");
		STFactory fac = getFactory();
		
		verifyReadOnly(fac.make("root",false));
		verifyReadOnly(fac.make("en",false));
		
		logln("Test done.");
	}
	
	public void TestVoteBasic() throws SQLException, IOException {
		logln("Setting up factory..");
		STFactory fac = getFactory();
		
		final String somePath =  "//ldml/localeDisplayNames/keys/key[@type=\"collation\"]";
		String originalValue = null;
		String currentWinner = null;
		String changedTo = null;
		String nowIs = null;
		boolean didVote = false;
		
		CLDRLocale locale = CLDRLocale.getInstance("mt");
		{
			CLDRFile mt = fac.make(locale, false);
			BallotBox<User> box = fac.ballotBoxForLocale(locale);
			didVote = box.userDidVote(getMyUser(),somePath);
			originalValue = currentWinner = mt.getStringValue(somePath);
			logln("for " + locale + " value " + somePath + " winner= " + currentWinner + ", ivoted = " + didVote + ", resolver: " + box.getResolver(somePath));
			changedTo = "COLL_ATION!!!";
			if(currentWinner.equals(changedTo)) {
				errln("for " + locale + " value " + somePath + " winner is already= " + currentWinner);
			}
			if(didVote) {
				errln("Hey, I didn't vote yet!");
			}
			logln("VoteFor: " + changedTo);
			box.voteForValue(getMyUser(), somePath, changedTo);
			currentWinner= mt.getStringValue(somePath);
			didVote = box.userDidVote(getMyUser(),somePath);
			logln("for " + locale + " value " + somePath + " winner= " + currentWinner + ", ivoted = " + didVote + ", resolver: " + box.getResolver(somePath));
			if(!didVote) {
				errln("Hey, I did vote!");
			}
			if(!changedTo.equals(currentWinner)) {
				errln("for " + locale + " value " + somePath + " winner is = " + currentWinner + " , should be " + changedTo);
			}
		}
		
		// Restart STFactory.
		fac = resetFactory();
		{
			CLDRFile mt = fac.make(locale, false);
			BallotBox<User> box = fac.ballotBoxForLocale(locale);
			currentWinner= mt.getStringValue(somePath);
			didVote = box.userDidVote(getMyUser(),somePath);
			logln("after reset " + locale + " value " + somePath + " winner= " + currentWinner + ", ivoted = " + didVote + ", resolver: " + box.getResolver(somePath));
			if(!didVote) {
				errln("Hey, I did vote!");
			}
			if(!changedTo.equals(currentWinner)) {
				errln("after reset: for " + locale + " value " + somePath + " winner is = " + currentWinner + " , should be " + changedTo);
			}
			
			// unvote
			box.voteForValue(getMyUser(), somePath, null);
			currentWinner= mt.getStringValue(somePath);
			didVote = box.userDidVote(getMyUser(),somePath);
			logln("for " + locale + " value " + somePath + " winner= " + currentWinner + ", ivoted = " + didVote + ", resolver: " + box.getResolver(somePath));
			if(!didVote) {
				errln("Hey, I did vote, for null!");
			}
			if(!originalValue.equals(currentWinner)) {
				errln("for " + locale + " value " + somePath + " winner is = " + currentWinner + " , should be " + originalValue);
			}
		}
		fac = resetFactory();
		{
			CLDRFile mt = fac.make(locale, false);
			BallotBox<User> box = fac.ballotBoxForLocale(locale);
			currentWinner= mt.getStringValue(somePath);
			didVote = box.userDidVote(getMyUser(),somePath);
			logln("after reset- for " + locale + " value " + somePath + " winner= " + currentWinner + ", ivoted = " + didVote + ", resolver: " + box.getResolver(somePath));
			if(!didVote) {
				errln("after reset- Hey, I did vote, for null!");
			}
			if(!originalValue.equals(currentWinner)) {
				errln("after reset - for " + locale + " value " + somePath + " winner is = " + currentWinner + " , should be " + originalValue);
			}

			// vote for ____2
			changedTo = changedTo+"2";
			logln("VoteFor: " + changedTo);
			box.voteForValue(getMyUser(), somePath, changedTo);
			currentWinner= mt.getStringValue(somePath);
			didVote = box.userDidVote(getMyUser(),somePath);
			logln("for " + locale + " value " + somePath + " winner= " + currentWinner + ", ivoted = " + didVote + ", resolver: " + box.getResolver(somePath));
			if(!didVote) {
				errln("Hey, I did revote!");
			}
			if(!changedTo.equals(currentWinner)) {
				errln("for " + locale + " value " + somePath + " winner is = " + currentWinner + " , should be " + changedTo);
			}
			

			logln("Write out..");
			File targDir = TestAll.getEmptyDir(TestSTFactory.class.getName()+"_output");
			File outFile = new File(targDir,locale.getBaseName()+".xml");
			FileOutputStream fos = new FileOutputStream(outFile);
			PrintWriter pw = new PrintWriter(fos);
			mt.write(pw,noDtdPlease);
			pw.close();
			
			logln("Read back..");
			CLDRFile readBack = CLDRFile.loadFromFile(outFile, locale.getBaseName(), DraftStatus.unconfirmed);
			
			String reRead = readBack.getStringValue(somePath);
			
			logln("reread:  " + outFile.getAbsolutePath()+ " value " + somePath + " = " + reRead);
			if(!changedTo.equals(reRead)) {
				logln("reread:  " + outFile.getAbsolutePath()+ " value " + somePath + " = " + reRead + ", should be " + changedTo);
			}
		}
		
		
		
		
		logln("Test done.");
	}
	
	
	
	private void verifyReadOnly(CLDRFile f) {
		String loc = f.getLocaleID();
		try {
			f.add("//ldml/foo", "bar");
			errln("Error: " +  loc + " is supposed to be readonly.");
		} catch(Throwable t) {
			logln("Pass: " +  loc + " is readonly, caught " + t.toString());
		}
	}
	String someLocales[] = { "mt" };

	public UserRegistry.User getMyUser() throws SQLException {
		if(gUser ==null) {
			gUser = getFactory().sm.reg.get(null,"admin@","[::1]",true);
		}
		return gUser;
	}
	
	private STFactory getFactory() throws SQLException {
		if(gFac==null) {
			TestAll.setupTestDb();

			File cacheDir = TestAll.getEmptyDir(CACHETEST);
			logln("Setting up STFactory");
			SurveyMain sm = new SurveyMain();
			sm.setFileBaseOld(CldrUtility.BASE_DIRECTORY);
			sm.twidPut(Vetting.TWID_VET_VERBOSE, true); // set verbose vetting
			SurveyLog.logger = Logger.getAnonymousLogger();
			Connection conn = DBUtils.getInstance().getDBConnection();
			
			sm.reg = UserRegistry.createRegistry(SurveyLog.logger, sm);
			
			sm.xpt = XPathTable.createTable(conn, sm);
			DBUtils.closeDBConnection(conn);
			
//			sm.vet = Vetting.createTable(sm.logger, sm);
			
			sm.fileBase = CldrUtility.MAIN_DIRECTORY;
//			CLDRDBSourceFactory fac = new CLDRDBSourceFactory(sm, sm.fileBase, Logger.getAnonymousLogger(), cacheDir);
//			logln("Setting up DB");
//			sm.setDBSourceFactory(fac);ignore
//			fac.setupDB(DBUtils.getInstance().getDBConnection());
//			logln("Vetter Ready (this will take a while..)");
//			fac.vetterReady(TestAll.getProgressIndicator(this));
			
			gFac = sm.getSTFactory();
		}
		return gFac;
	}
	
	private STFactory resetFactory() throws SQLException {
		logln("--- resetting STFactory() ----- [simulate reload] ------------");
		return gFac = getFactory().TESTING_shutdownAndRestart();
	}

	
	static final Map<String,Object> noDtdPlease = new TreeMap<String,Object>();
	static {
		noDtdPlease.put("DTD_DIR", CldrUtility.COMMON_DIRECTORY+File.separator+"dtd" + File.separator);
	}
}
