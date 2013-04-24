package org.unicode.cldr.unittest.web;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Date;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Logger;

import org.json.JSONException;
import org.json.JSONObject;
import org.unicode.cldr.unittest.web.TestAll.WebTestInfo;
import org.unicode.cldr.util.CLDRConfig;
import org.unicode.cldr.util.CLDRFile;
import org.unicode.cldr.util.CLDRFile.DraftStatus;
import org.unicode.cldr.util.CLDRLocale;
import org.unicode.cldr.util.CldrUtility;
import org.unicode.cldr.util.StackTracker;
import org.unicode.cldr.util.VoteResolver;
import org.unicode.cldr.util.VoteResolver.Status;
import org.unicode.cldr.util.XMLFileReader;
import org.unicode.cldr.util.XPathParts;
import org.unicode.cldr.web.BallotBox;
import org.unicode.cldr.web.CookieSession;
import org.unicode.cldr.web.DBUtils;
import org.unicode.cldr.web.STFactory;
import org.unicode.cldr.web.SurveyLog;
import org.unicode.cldr.web.SurveyMain;
import org.unicode.cldr.web.UserRegistry;
import org.unicode.cldr.web.UserRegistry.LogoutException;
import org.unicode.cldr.web.UserRegistry.User;
import org.unicode.cldr.web.XPathTable;

import com.ibm.icu.dev.test.TestFmwk;
import com.ibm.icu.dev.util.BagFormatter;
import com.ibm.icu.dev.util.ElapsedTimer;
import com.ibm.icu.text.SimpleDateFormat;

public class TestSTFactory extends TestFmwk {

    private static final String CACHETEST = "cachetest";

    TestAll.WebTestInfo testInfo = WebTestInfo.getInstance();

    STFactory gFac = null;
    UserRegistry.User gUser = null;

    public static void main(String[] args) {
        new TestSTFactory().run(TestAll.doResetDb(args));
    }

    public void TestBasicFactory() throws SQLException {
        CLDRLocale locale = CLDRLocale.getInstance("aa");
        STFactory fac = getFactory();
        CLDRFile mt = fac.make(locale, false);
        BallotBox<User> box = fac.ballotBoxForLocale(locale);
        mt.iterator();
        final String somePath = "//ldml/localeDisplayNames/keys/key[@type=\"collation\"]";
        box.getValues(somePath);
    }

    public void TestReadonlyLocales() throws SQLException {
        STFactory fac = getFactory();

        verifyReadOnly(fac.make("root", false));
        verifyReadOnly(fac.make("en", false));
    }

    private static final String ANY = "*";
    private static final String NULL = "<NULL>";

    private String expect(String path, String expectString, boolean expectVoted, CLDRFile file, BallotBox<User> box) {
        CLDRLocale locale = CLDRLocale.getInstance(file.getLocaleID());
        String currentWinner = file.getStringValue(path);
        boolean didVote = box.userDidVote(getMyUser(), path);
        StackTraceElement them = StackTracker.currentElement(1);
        String where = " (" + them.getFileName() + ":" + them.getLineNumber() + "): ";

        if (expectString == null)
            expectString = NULL;
        if (currentWinner == null)
            currentWinner = NULL;

        if (expectString != ANY && !expectString.equals(currentWinner)) {
            errln("ERR:" + where + "Expected '" + expectString + "': " + locale + ":" + path + " ='" + currentWinner + "', "
                    + votedToString(didVote) + box.getResolver(path));
        } else if (expectVoted != didVote) {
            errln("ERR:" + where + "Expected VOTING=" + votedToString(expectVoted) + ":  " + locale + ":" + path + " ='"
                    + currentWinner + "', " + votedToString(didVote) + box.getResolver(path));
        } else {
            logln(where + locale + ":" + path + " ='" + currentWinner + "', " + votedToString(didVote) + box.getResolver(path));
        }
        return currentWinner;
    }

    /**
     * @param didVote
     * @return
     */
    private String votedToString(boolean didVote) {
        return didVote ? "(I VOTED)" : "( did NOT VOTE) ";
    }

    public void TestBasicVote() throws SQLException, IOException {
        STFactory fac = getFactory();

        final String somePath = "//ldml/localeDisplayNames/keys/key[@type=\"collation\"]";
        String originalValue = null;
        String changedTo = null;

        CLDRLocale locale = CLDRLocale.getInstance("de");
        {
            CLDRFile mt = fac.make(locale, false);
            BallotBox<User> box = fac.ballotBoxForLocale(locale);

            originalValue = expect(somePath, ANY, false, mt, box);

            changedTo = "The main pump fixing screws with the correct strength class"; // as
                                                                                       // per
                                                                                       // ticket:2260

            if (originalValue.equals(changedTo)) {
                errln("for " + locale + " value " + somePath + " winner is already= " + originalValue);
            }

            box.voteForValue(getMyUser(), somePath, changedTo); // vote
            expect(somePath, changedTo, true, mt, box);

            box.voteForValue(getMyUser(), somePath, null); // unvote
            expect(somePath, originalValue, false, mt, box);

            box.voteForValue(getMyUser(), somePath, changedTo); // vote again
            expect(somePath, changedTo, true, mt, box);

        }

        // Restart STFactory.
        fac = resetFactory();
        {
            CLDRFile mt = fac.make(locale, false);
            BallotBox<User> box = fac.ballotBoxForLocale(locale);

            expect(somePath, changedTo, true, mt, box);

            // unvote
            box.voteForValue(getMyUser(), somePath, null);

            expect(somePath, originalValue, false, mt, box);
        }
        fac = resetFactory();
        {
            CLDRFile mt = fac.make(locale, false);
            BallotBox<User> box = fac.ballotBoxForLocale(locale);

            expect(somePath, originalValue, false, mt, box);

            // vote for ____2
            changedTo = changedTo + "2";

            logln("VoteFor: " + changedTo);
            box.voteForValue(getMyUser(), somePath, changedTo);

            expect(somePath, changedTo, true, mt, box);

            logln("Write out..");
            File targDir = TestAll.getEmptyDir(TestSTFactory.class.getName() + "_output");
            File outFile = new File(targDir, locale.getBaseName() + ".xml");
            PrintWriter pw = BagFormatter.openUTF8Writer(targDir.getAbsolutePath(), locale.getBaseName() + ".xml");
            mt.write(pw, noDtdPlease);
            pw.close();

            logln("Read back..");
            CLDRFile readBack = null;
            try {
                readBack = CLDRFile.loadFromFile(outFile, locale.getBaseName(), DraftStatus.unconfirmed);
            } catch (IllegalArgumentException iae) {
                iae.getCause().printStackTrace();
                System.err.println(iae.getCause().toString());
                handleException(iae);
            }
            String reRead = readBack.getStringValue(somePath);

            logln("reread:  " + outFile.getAbsolutePath() + " value " + somePath + " = " + reRead);
            if (!changedTo.equals(reRead)) {
                logln("reread:  " + outFile.getAbsolutePath() + " value " + somePath + " = " + reRead + ", should be "
                        + changedTo);
            }
        }
    }

    public void TestDenyVote() throws SQLException, IOException {
        STFactory fac = getFactory();
        final String somePath2 = "//ldml/localeDisplayNames/keys/key[@type=\"numbers\"]";
        String originalValue2 = null;
        String changedTo2 = null;
        // test votring for a bad locale
        {
            CLDRLocale locale2 = CLDRLocale.getInstance("mt_MT");
            CLDRFile mt_MT = fac.make(locale2, false);
            BallotBox<User> box = fac.ballotBoxForLocale(locale2);

            try {
                box.voteForValue(getMyUser(), somePath2, changedTo2);
                errln("Error! should have failed to vote for " + locale2);
            } catch (Throwable t) {
                logln("Good - caught " + t.toString() + " as this locale is a default content.");
            }
        }
        {
            CLDRLocale locale2 = CLDRLocale.getInstance("en");
            CLDRFile mt_MT = fac.make(locale2, false);
            BallotBox<User> box = fac.ballotBoxForLocale(locale2);

            try {
                box.voteForValue(getMyUser(), somePath2, changedTo2);
                errln("Error! should have failed to vote for " + locale2);
            } catch (Throwable t) {
                logln("Good - caught " + t.toString() + " as this locale is readonly english.");
            }
        }
    }

    public void TestSparseVote() throws SQLException, IOException {
        STFactory fac = getFactory();

        final String somePath2 = "//ldml/localeDisplayNames/keys/key[@type=\"calendar\"]";
        String originalValue2 = null;
        String changedTo2 = null;
        CLDRLocale locale2 = CLDRLocale.getInstance("de_CH");
        // test sparsity
        {
            CLDRFile mt_MT = fac.make(locale2, false);
            BallotBox<User> box = fac.ballotBoxForLocale(locale2);

            originalValue2 = expect(somePath2, null, false, mt_MT, box);

            changedTo2 = "The alternate pump fixing screws with the incorrect strength class";

            if (originalValue2.equals(changedTo2)) {
                errln("for " + locale2 + " value " + somePath2 + " winner is already= " + originalValue2);
            }

            box.voteForValue(getMyUser(), somePath2, changedTo2);

            expect(somePath2, changedTo2, true, mt_MT, box);
        }
        // Restart STFactory.
        fac = resetFactory();
        {
            CLDRFile mt_MT = fac.make(locale2, false);
            BallotBox<User> box = fac.ballotBoxForLocale(locale2);

            expect(somePath2, changedTo2, true, mt_MT, box);

            // unvote
            box.voteForValue(getMyUser(), somePath2, null);

            expect(somePath2, null, false, mt_MT, box); // Expect null - no one
                                                        // has voted.
        }
        fac = resetFactory();
        {
            CLDRFile mt_MT = fac.make(locale2, false);
            BallotBox<User> box = fac.ballotBoxForLocale(locale2);

            expect(somePath2, null, false, mt_MT, box);

            // vote for ____2
            changedTo2 = changedTo2 + "2";

            logln("VoteFor: " + changedTo2);
            box.voteForValue(getMyUser(), somePath2, changedTo2);

            expect(somePath2, changedTo2, true, mt_MT, box);

            logln("Write out..");
            File targDir = TestAll.getEmptyDir(TestSTFactory.class.getName() + "_output");
            File outFile = new File(targDir, locale2.getBaseName() + ".xml");
            PrintWriter pw = BagFormatter.openUTF8Writer(targDir.getAbsolutePath(), locale2.getBaseName() + ".xml");
            mt_MT.write(pw, noDtdPlease);
            pw.close();

            logln("Read back..");
            CLDRFile readBack = CLDRFile.loadFromFile(outFile, locale2.getBaseName(), DraftStatus.unconfirmed);

            String reRead = readBack.getStringValue(somePath2);

            logln("reread:  " + outFile.getAbsolutePath() + " value " + somePath2 + " = " + reRead);
            if (!changedTo2.equals(reRead)) {
                logln("reread:  " + outFile.getAbsolutePath() + " value " + somePath2 + " = " + reRead + ", should be "
                        + changedTo2);
            }
        }
    }

    public void TestVettingDataDriven() throws SQLException, IOException {
        final STFactory fac = getFactory();
        final File targDir = TestAll.getEmptyDir(TestSTFactory.class.getName() + "_output");

        XMLFileReader myReader = new XMLFileReader();
        final XPathParts xpp = new XPathParts(null, null);
        final XPathParts xpp2 = new XPathParts(null, null);
        final Map<String, String> attrs = new TreeMap<String, String>();
        final Map<String, UserRegistry.User> users = new TreeMap<String, UserRegistry.User>();
        myReader.setHandler(new XMLFileReader.SimpleHandler() {
            public void handlePathValue(String path, String value) {
                xpp.clear();
                xpp.initialize(path);
                attrs.clear();
                for (String k : xpp.getAttributeKeys(-1)) {
                    attrs.put(k, xpp.getAttributeValue(-1, k));
                }
                String elem = xpp.getElement(-1);
                logln("* <" + elem + " " + attrs.toString() + ">" + value + "</" + elem + ">");
                String xpath = attrs.get("xpath");
                if (xpath != null) {
                    xpath = xpath.trim().replace("'", "\"");
                }
                if (elem.equals("user")) {
                    String name = attrs.get("name");
                    String org = attrs.get("org");
                    VoteResolver.Level level = VoteResolver.Level.valueOf(attrs.get("level").toLowerCase());

                    String email = name + "@" + org + ".example.com";
                    UserRegistry.User u = fac.sm.reg.get(email);
                    if (u == null) {
                        UserRegistry.User proto = fac.sm.reg.getEmptyUser();
                        proto.email = email;
                        proto.name = name;
                        proto.org = org;
                        proto.password = fac.sm.reg.makePassword(proto.email);
                        proto.userlevel = level.getSTLevel();

                        u = fac.sm.reg.newUser(null, proto);
                    }
                    if (u == null) {
                        throw new InternalError("Couldn't find/register user " + name);
                    } else {
                        logln(name + " = " + u);
                        users.put(name, u);
                    }
                } else if (elem.equals("vote") || elem.equals("unvote")) {
                    UserRegistry.User u = users.get(attrs.get("name"));
                    if (u == null) {
                        throw new IllegalArgumentException("Unknown user " + attrs.get("name") + " - need a <user> element.");
                    }

                    CLDRLocale locale = CLDRLocale.getInstance(attrs.get("locale"));
                    BallotBox<User> box = fac.ballotBoxForLocale(locale);
                    value = value.trim();
                    if (elem.equals("unvote")) {
                        value = null;
                    }
                    box.voteForValue(u, xpath, value);
                    logln(u + " " + elem + "d for " + xpath + " = " + value);
                } else if (elem.equals("verify")) {
                    value = value.trim();
                    if (value.isEmpty())
                        value = null;
                    CLDRLocale locale = CLDRLocale.getInstance(attrs.get("locale"));
                    BallotBox<User> box = fac.ballotBoxForLocale(locale);
                    CLDRFile cf = fac.make(locale, true);
                    String stringValue = cf.getStringValue(xpath);
                    String fullXpath = cf.getFullXPath(xpath);
                    // logln("V"+ xpath + " = " + stringValue + ", " +
                    // fullXpath);
                    // logln("Resolver=" + box.getResolver(xpath));
                    if (value == null && stringValue != null) {
                        errln("Expected null value at " + locale + ":" + xpath + " got " + stringValue);
                    } else if (value != null && !value.equals(stringValue)) {
                        errln("Expected " + value + " at " + locale + ":" + xpath + " got " + stringValue);
                    } else {
                        logln("OK: " + locale + ":" + xpath + " = " + value);
                    }
                    VoteResolver<String> r = box.getResolver(xpath);
                    Status winStatus = r.getWinningStatus();
                    Status expStatus = Status.fromString(attrs.get("status"));
                    if (winStatus == expStatus) {
                        logln("OK: Status=" + winStatus + " " + locale + ":" + xpath + " Resolver=" + box.getResolver(xpath));
                    } else {
                        errln("Expected: Status=" + expStatus + " got " + winStatus + " " + locale + ":" + xpath + " Resolver="
                                + box.getResolver(xpath));
                    }

                    xpp2.clear();
                    Status xpathStatus;
                    if (fullXpath == null) {
                        xpathStatus = Status.missing;
                    } else {
                        xpp2.set(fullXpath);
                        String statusFromXpath = xpp2.getAttributeValue(-1, "draft");

                        if (statusFromXpath == null) {
                            statusFromXpath = "approved"; // no draft = approved
                        }
                        xpathStatus = Status.fromString(statusFromXpath);
                    }
                    if (xpathStatus == expStatus) {
                        logln("OK from fullxpath: Status=" + xpathStatus + " " + locale + ":" + fullXpath + " Resolver="
                                + box.getResolver(xpath));
                    } else {
                        errln("Expected from fullxpath: Status=" + expStatus + " got " + xpathStatus + " " + locale + ":"
                                + fullXpath + " Resolver=" + box.getResolver(xpath));
                    }

                    // Verify from XML
                    File outFile = new File(targDir, locale.getBaseName() + ".xml");
                    if (outFile.exists())
                        outFile.delete();
                    try {
                        PrintWriter pw;
                        pw = BagFormatter.openUTF8Writer(targDir.getAbsolutePath(), locale.getBaseName() + ".xml");
                        cf.write(pw, noDtdPlease);
                        pw.close();
                    } catch (IOException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                        handleException(e);
                        return;
                    }

                    // logln("Read back..");
                    CLDRFile readBack = null;
                    try {
                        readBack = CLDRFile.loadFromFile(outFile, locale.getBaseName(), DraftStatus.unconfirmed);
                    } catch (IllegalArgumentException iae) {
                        iae.getCause().printStackTrace();
                        System.err.println(iae.getCause().toString());
                        handleException(iae);
                    }
                    String reRead = readBack.getStringValue(xpath);
                    xpp2.clear();
                    String fullXpathBack = readBack.getFullXPath(xpath);
                    Status xpathStatusBack;
                    if (fullXpathBack == null) {
                        xpathStatusBack = Status.missing;
                    } else {
                        xpp2.set(fullXpathBack);
                        String statusFromXpathBack = xpp2.getAttributeValue(-1, "draft");

                        if (statusFromXpathBack == null) {
                            statusFromXpathBack = "approved"; // no draft =
                                                              // approved
                        }
                        xpathStatusBack = Status.fromString(statusFromXpathBack);
                    }

                    if (value == null && reRead != null) {
                        errln("Expected null value from XML at " + locale + ":" + xpath + " got " + reRead);
                    } else if (value != null && !value.equals(reRead)) {
                        errln("Expected from XML " + value + " at " + locale + ":" + xpath + " got " + reRead);
                    } else {
                        logln("OK from XML: " + locale + ":" + xpath + " = " + reRead);
                    }

                    if (xpathStatusBack == expStatus) {
                        logln("OK from XML: Status=" + xpathStatusBack + " " + locale + ":" + fullXpathBack + " Resolver="
                                + box.getResolver(xpath));
                    } else {
                        errln("Expected from XML: Status=" + expStatus + " got " + xpathStatusBack + " " + locale + ":"
                                + fullXpathBack + " Resolver=" + box.getResolver(xpath));
                    }

                } else if (elem.equals("echo")) {
                    logln("*** \"" + value.trim() + "\"");
                } else {
                    throw new IllegalArgumentException("Unknown test element type " + elem);
                }
            };
            // public void handleComment(String path, String comment) {};
            // public void handleElementDecl(String name, String model) {};
            // public void handleAttributeDecl(String eName, String aName,
            // String type, String mode, String value) {};
        });
        String fileName = TestSTFactory.class.getSimpleName() + ".xml";
        myReader.read(TestSTFactory.class.getResource("data/" + fileName).toString(), TestAll.getUTF8Data(fileName), -1, true);
    }

    public void TestVettingWithNonDistinguishing() throws SQLException, IOException {
        STFactory fac = getFactory();

        final String somePath2 = "//ldml/dates/calendars/calendar[@type=\"hebrew\"]/dateFormats/dateFormatLength[@type=\"full\"]/dateFormat[@type=\"standard\"]/pattern[@type=\"standard\"]";
        String originalValue2 = null;
        String changedTo2 = null;
        CLDRLocale locale2 = CLDRLocale.getInstance("he");
        String fullPath = null;
        {
            CLDRFile mt_MT = fac.make(locale2, false);
            BallotBox<User> box = fac.ballotBoxForLocale(locale2);

            originalValue2 = expect(somePath2, ANY, false, mt_MT, box);

            fullPath = mt_MT.getFullXPath(somePath2);
            logln("locale " + locale2 + " path " + somePath2 + " full = " + fullPath);
            if (!fullPath.contains("numbers=")) {
                logln("Warning: " + locale2 + ":" + somePath2 + " fullpath doesn't contain numbers= - test skipped, got path "
                        + fullPath);
                return;
            }

            changedTo2 = "EEEE, d _MMMM y";

            if (originalValue2.equals(changedTo2)) {
                errln("for " + locale2 + " value " + somePath2 + " winner is already= " + originalValue2);
            }

            box.voteForValue(getMyUser(), somePath2, changedTo2);

            expect(somePath2, changedTo2, true, mt_MT, box);
        }
        // Restart STFactory.
        fac = resetFactory();
        {
            CLDRFile mt_MT = fac.make(locale2, false);
            BallotBox<User> box = fac.ballotBoxForLocale(locale2);

            expect(somePath2, changedTo2, true, mt_MT, box);

            // unvote
            box.voteForValue(getMyUser(), somePath2, null);

            expect(somePath2, originalValue2, false, mt_MT, box); // Expect
                                                                  // original
                                                                  // value - no
                                                                  // one has
                                                                  // voted.

            String fullPath2 = mt_MT.getFullXPath(somePath2);
            if (!fullPath2.contains("numbers=")) {
                errln("Error - voted, but full path lost numbers= - " + fullPath2);
            }
        }
        fac = resetFactory();
        {
            CLDRFile mt_MT = fac.make(locale2, false);
            BallotBox<User> box = fac.ballotBoxForLocale(locale2);

            expect(somePath2, originalValue2, false, mt_MT, box); // still
                                                                  // original
                                                                  // value

            // vote for ____2
            changedTo2 = changedTo2 + "__";

            logln("VoteFor: " + changedTo2);
            box.voteForValue(getMyUser(), somePath2, changedTo2);

            expect(somePath2, changedTo2, true, mt_MT, box);

            logln("Write out..");
            File targDir = TestAll.getEmptyDir(TestSTFactory.class.getName() + "_output");
            File outFile = new File(targDir, locale2.getBaseName() + ".xml");
            PrintWriter pw = BagFormatter.openUTF8Writer(targDir.getAbsolutePath(), locale2.getBaseName() + ".xml");
            mt_MT.write(pw, noDtdPlease);
            pw.close();

            logln("Read back..");
            CLDRFile readBack = CLDRFile.loadFromFile(outFile, locale2.getBaseName(), DraftStatus.unconfirmed);

            String reRead = readBack.getStringValue(somePath2);

            logln("reread:  " + outFile.getAbsolutePath() + " value " + somePath2 + " = " + reRead);
            if (!changedTo2.equals(reRead)) {
                logln("reread:  " + outFile.getAbsolutePath() + " value " + somePath2 + " = " + reRead + ", should be "
                        + changedTo2);
            }
            String fullPath2 = readBack.getFullXPath(somePath2);
            if (!fullPath2.contains("numbers=")) {
                errln("Error - readBack's full path lost numbers= - " + fullPath2);
            }
        }
    }
        
    public void TestVotingAge() throws SQLException, IOException, InterruptedException, JSONException {
        CLDRConfig config = CLDRConfig.getInstance();
        config.setProperty(SurveyMain.CLDR_NEWVERSION_AFTER, SurveyMain.NEWVERSION_EPOCH);
        STFactory fac = resetFactory();

        final String somePath = "//ldml/localeDisplayNames/keys/key[@type=\"collation\"]";
        final String somePath2 = "//ldml/localeDisplayNames/keys/key[@type=\"calendar\"]";
        final CLDRLocale loc = CLDRLocale.getInstance("und");
        final String aValueOld = "oldValue";
        final String aValueNew = "newValue";
        String origBase = ANY;
        
        {
            CLDRFile file = fac.make(loc, false);
            BallotBox<User> box = fac.ballotBoxForLocale(loc);
            box.voteForValue(getMyUser(), somePath, null); // unvote
            origBase = expect(somePath, ANY, false, file, box);
            logln(loc + ":" + somePath + " = " + origBase);
            
            box.voteForValue(getMyUser(), somePath, aValueOld); // unvote
            expect(somePath, aValueOld, true, file, box);

        }
        
        logln("Sleeping at .." + new Date());
        Thread.sleep(2000);  // so that the 'old' vote is prior to the cut
        Date cutTime = new Date();
        String cutEpoch = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss'.00000'").format(cutTime);
        config.setProperty(SurveyMain.CLDR_NEWVERSION_AFTER, cutEpoch);

        logln("Sleeping.. (set old release cut to " + cutEpoch);
        Thread.sleep(2000); // so that the 'new' vote is after the cut
        logln("Retesting at " + new Date());
        fac = resetFactory();
        
        {
            CLDRFile file = fac.make(loc, false);
            BallotBox<User> box = fac.ballotBoxForLocale(loc);
            box.voteForValue(getMyUser(), somePath2, aValueNew); // vote on 2nd path
            final String votesAfter = SurveyMain.getSQLVotesAfter();
            logln("votesAfter = " + votesAfter);
            {
                JSONObject query = DBUtils
                        .queryToJSON("select xpath,value,last_mod from " + STFactory.CLDR_VBV + " where locale=?", loc);
                logln("*: " + query.toString());
            }
            
            logln("Expect to find the old value gone (too old)");
            expect(somePath, origBase, false, file, box);
            logln("Expect to find the new value  in the new path OK gone (new)");
            expect(somePath2, aValueNew, true, file, box);
            
            logln("Expect to find the new value after revoting");
            box.voteForValue(getMyUser(), somePath, aValueNew); // revote
            expect(somePath, aValueNew, true, file, box);
        }

        
    }
    
    private void verifyReadOnly(CLDRFile f) {
        String loc = f.getLocaleID();
        try {
            f.add("//ldml/foo", "bar");
            errln("Error: " + loc + " is supposed to be readonly.");
        } catch (Throwable t) {
            logln("Pass: " + loc + " is readonly, caught " + t.toString());
        }
    }

    public UserRegistry.User getMyUser() {
        if (gUser == null) {
            try {
                gUser = getFactory().sm.reg.get(null, "admin@", "[::1]", true);
            } catch (SQLException e) {
                handleException(e);
            } catch (LogoutException e) {
                handleException(e);
            }
        }
        return gUser;
    }

    private STFactory getFactory() throws SQLException {
        if (gFac == null) {
            long start = System.currentTimeMillis();
            TestAll.setupTestDb();
            logln("Set up test DB: " + ElapsedTimer.elapsedTime(start));

            ElapsedTimer et0 = new ElapsedTimer("clearing directory");
            File cacheDir = TestAll.getEmptyDir(CACHETEST);
            logln(et0.toString());

            et0 = new ElapsedTimer("setup SurveyMain");
            SurveyMain sm = new SurveyMain();
            CookieSession.sm = sm; // hack - of course.
            logln(et0.toString());

            sm.fileBase = CldrUtility.MAIN_DIRECTORY;
            sm.fileBaseSeed = new File(CldrUtility.BASE_DIRECTORY, "seed/main/").getAbsolutePath();
            sm.setFileBaseOld(CldrUtility.BASE_DIRECTORY);
            // sm.twidPut(Vetting.TWID_VET_VERBOSE, true); // set verbose
            // vetting
            SurveyLog.logger = Logger.getAnonymousLogger();

            et0 = new ElapsedTimer("setup DB");
            Connection conn = DBUtils.getInstance().getDBConnection();
            logln(et0.toString());

            et0 = new ElapsedTimer("setup Registry");
            sm.reg = UserRegistry.createRegistry(SurveyLog.logger, sm);
            logln(et0.toString());

            et0 = new ElapsedTimer("setup XPT");
            sm.xpt = XPathTable.createTable(conn, sm);
            sm.xpt.getByXpath("//foo/bar[@type='baz']");
            logln(et0.toString());
            DBUtils.closeDBConnection(conn);
            // sm.vet = Vetting.createTable(sm.logger, sm);

            // CLDRDBSourceFactory fac = new CLDRDBSourceFactory(sm,
            // sm.fileBase, Logger.getAnonymousLogger(), cacheDir);
            // logln("Setting up DB");
            // sm.setDBSourceFactory(fac);ignore
            // fac.setupDB(DBUtils.getInstance().getDBConnection());
            // logln("Vetter Ready (this will take a while..)");
            // fac.vetterReady(TestAll.getProgressIndicator(this));

            et0 = new ElapsedTimer("Set up STFactory");
            gFac = sm.getSTFactory();
            logln(et0.toString());
        }
        return gFac;
    }

    private STFactory resetFactory() throws SQLException {
        logln("--- resetting STFactory() ----- [simulate reload] ------------");
        return gFac = getFactory().TESTING_shutdownAndRestart();
    }

    static final Map<String, Object> noDtdPlease = new TreeMap<String, Object>();
    static {
        noDtdPlease.put("DTD_DIR", CldrUtility.COMMON_DIRECTORY + File.separator + "dtd" + File.separator);
    }
}
