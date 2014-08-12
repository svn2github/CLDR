package org.unicode.cldr.web;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.unicode.cldr.test.CheckCLDR;
import org.unicode.cldr.test.CheckCLDR.CheckStatus;
import org.unicode.cldr.test.CheckCLDR.CheckStatus.Subtype;
import org.unicode.cldr.test.DisplayAndInputProcessor;
import org.unicode.cldr.test.TestCache.TestResultBundle;
import org.unicode.cldr.util.CLDRConfig;
import org.unicode.cldr.util.CLDRFile;
import org.unicode.cldr.util.CLDRInfo.CandidateInfo;
import org.unicode.cldr.util.CLDRInfo.UserInfo;
import org.unicode.cldr.util.CLDRConfigImpl;
import org.unicode.cldr.util.CLDRLocale;
import org.unicode.cldr.util.CoverageInfo;
import org.unicode.cldr.util.Factory;
import org.unicode.cldr.util.Level;
import org.unicode.cldr.util.PathHeader;
import org.unicode.cldr.util.PathHeader.SurveyToolStatus;
import org.unicode.cldr.util.SpecialLocales;
import org.unicode.cldr.util.SupplementalDataInfo;
import org.unicode.cldr.util.VoteResolver;
import org.unicode.cldr.util.XMLSource;
import org.unicode.cldr.web.BallotBox.InvalidXPathException;
import org.unicode.cldr.web.BallotBox.VoteNotAcceptedException;
import org.unicode.cldr.web.DataSection.DataRow;
import org.unicode.cldr.web.SurveyException.ErrorCode;
import org.unicode.cldr.web.SurveyMain.UserLocaleStuff;
import org.unicode.cldr.web.UserRegistry.User;
import org.unicode.cldr.web.WebContext.HTMLDirection;

import com.ibm.icu.dev.util.ElapsedTimer;
import com.ibm.icu.text.UnicodeSet;

/**
 * Servlet implementation class SurveyAjax
 * 
 * URL/JSON Usage: get/set prefs
 * 
 * to get a preference:
 * http://...../SurveyAjax?s=YOURSESSIONID&what=pref&pref=dummy will reply with
 * JSON of {... "pref":"dummy","_v":"true" ...} to set a preference:
 * http://...../SurveyAjax?s=YOURSESSIONID&what=pref&pref=dummy&_v=true ( can
 * also use a POST instead of the _v parameter ) Note, add the preference to the
 * settablePrefsList
 * 
 */
public class SurveyAjax extends HttpServlet {
    public static final String E_NO_ACCESS = ErrorCode.E_NO_PERMISSION.name();
    final boolean DEBUG = false; //  || SurveyLog.isDebug();
    public final static String WHAT_MY_LOCALES = "mylocales";

    /**
     * Consolidate my JSONify functions here.
     * 
     * @author srl
     * 
     */
    public static final class JSONWriter {
        private final JSONObject j = new JSONObject();

        public JSONWriter() {
        }

        public final Object get(String k) {
            try {
                return j.get(k);
            } catch (JSONException e) {
                throw new IllegalArgumentException(e.toString(), e);
            }
        }
        
        public final void put(String k, Object v) {
            try {
                j.put(k, v);
            } catch (JSONException e) {
                throw new IllegalArgumentException(e.toString(), e);
            }
        }
        
        public final String toString() {
            return j.toString();
        }

        public static JSONObject wrap(CheckStatus status) throws JSONException {
            final CheckStatus cs = status;
            return new JSONObject() {
                {
                    put("message", cs.getMessage());
                    put("htmlMessage", cs.getHTMLMessage());
                    put("type", cs.getType());
                    if (cs.getCause() != null) {
                        put("cause", wrap(cs.getCause()));
                    }
                    if (cs.getSubtype() != null) {
                        put("subType", cs.getSubtype().name());
                    }
                    // put("parameters",new
                    // JSONArray(cs.getParameters()).toString()); // NPE.
                }
            };
        }

        public static JSONObject wrap(UserRegistry.User u) throws JSONException {
            return new JSONObject().put("id", u.id).put("email", u.email).put("name", u.name).put("userlevel", u.userlevel)
                .put("emailHash", u.getEmailHash())
                .put("userlevelName", u.getLevel()).put("org", u.org);
        }

        public static JSONObject wrap(CheckCLDR check) throws JSONException {
            final CheckCLDR cc = check;
            return new JSONObject() {
                {
                    put("class", cc.getClass().getSimpleName());
                    put("phase", cc.getPhase());
                }
            };
        }

        public static JSONObject wrap(ResultSet rs) throws SQLException, IOException, JSONException {
            return DBUtils.getJSON(rs);
        }

        public static List<Object> wrap(List<CheckStatus> list) throws JSONException {
            if (list == null || list.isEmpty())
                return null;
            List<Object> newList = new ArrayList<Object>();
            for (final CheckStatus cs : list) {
                newList.add(wrap(cs));
            }
            return newList;
        }

        public static JSONObject wrap(final VoteResolver<String> r) throws JSONException {
            JSONObject ret = new JSONObject().put("raw", r.toString()).put("isDisputed", r.isDisputed())
                /* .put("isEstablished", r.isEstablished()) NOTUSED */
                .put("lastReleaseStatus", r.getLastReleaseStatus())
                .put("winningValue", r.getWinningValue()).put("lastReleaseValue", r.getLastReleaseValue())
                .put("requiredVotes", r.getRequiredVotes())
                .put("winningStatus", r.getWinningStatus());

            EnumSet<VoteResolver.Organization> conflictedOrgs = r.getConflictedOrganizations();

            Map<String, Long> valueToVote = r.getResolvedVoteCounts();

            JSONObject orgs = new JSONObject();
            for (VoteResolver.Organization o : VoteResolver.Organization.values()) {
                String orgVote = r.getOrgVote(o);
                if (orgVote == null)
                    continue;
                Map<String, Long> votes = r.getOrgToVotes(o);

                JSONObject org = new JSONObject().put("status", r.getStatusForOrganization(o)).put("orgVote", orgVote)
                    .put("votes", votes);
                if (conflictedOrgs.contains(org)) {
                    org.put("conflicted", true);
                }
                orgs.put(o.name(), org);
            }
            ret.put("orgs", orgs);
            JSONArray valueToVoteA = new JSONArray();
            for (Map.Entry<String, Long> e : valueToVote.entrySet()) {
                valueToVoteA.put(e.getKey()).put(e.getValue());
            }
            ret.put("value_vote", valueToVoteA);
            return ret;
        }

        public static JSONObject wrap(PathHeader pathHeader) throws JSONException {
            if (pathHeader == null) return null;
            return new JSONObject().put("section", pathHeader.getSectionId().name())
                .put("page", pathHeader.getPageId().name())
                .put("header", pathHeader.getCode())
                .put("code", pathHeader.getCode())
                .put("str", pathHeader.toString());
        }

        public static void putException(JSONWriter r, Throwable t) {
            r.put("err", "Exception: " + t.toString());
            if(t instanceof SurveyException) {
                SurveyException se = (SurveyException) t;
                r.put("err_code", se.getErrCode());
                try {
                    se.addDataTo(r);
                } catch (JSONException e) {
                    r.put("err_data", e.toString());
                }
            } else {
                r.put("err_code", ErrorCode.E_INTERNAL);
            }
        }
    }

    private static final long serialVersionUID = 1L;
    public static final String REQ_WHAT = "what";
    public static final String REQ_SESS = "s";
    public static final String WHAT_STATUS = "status";
    public static final String AJAX_STATUS_SCRIPT = "ajax_status.jspf";
    public static final String WHAT_VERIFY = "verify";
    public static final String WHAT_SUBMIT = "submit";
    public static final String WHAT_DELETE = "delete";
    public static final String WHAT_GETROW = "getrow";
    public static final String WHAT_GETSIDEWAYS = "getsideways";
    public static final String WHAT_GETXPATH = "getxpath";
    public static final String WHAT_PREF = "pref";
    public static final String WHAT_VSUMMARY = "vsummary";
    public static final String WHAT_STATS_BYLOC = "stats_byloc";
    public static final String WHAT_STATS_BYDAY = "stats_byday";
    public static final String WHAT_STATS_BYDAYUSERLOC = "stats_bydayuserloc";
    public static final String WHAT_RECENT_ITEMS = "recent_items";
    public static final String WHAT_FORUM_FETCH = "forum_fetch";
    public static final String WHAT_FORUM_COUNT = "forum_count";
    public static final String WHAT_FORUM_POST = "forum_post";
    public static final String WHAT_POSS_PROBLEMS = "possibleProblems";
    public static final String WHAT_GET_MENUS = "menus";
    public static final String WHAT_REPORT = "report";
    public static final String WHAT_SEARCH = "search";
    public static final String WHAT_REVIEW_HIDE = "review_hide";
    public static final String WHAT_REVIEW_ADD_POST = "add_post";
    public static final String WHAT_REVIEW_GET_POST = "get_post";
    public static final String WHAT_PARTICIPATING_USERS = "participating_users";

    
    String settablePrefsList[] = { SurveyMain.PREF_CODES_PER_PAGE, SurveyMain.PREF_COVLEV,
        "oldVoteRemind", "dummy" }; // list
                                    // of
                                    // prefs
                                    // OK
                                    // to
                                    // get/set

    private Set<String> prefsList = new HashSet<String>();

    /**
     * @see HttpServlet#HttpServlet()
     */
    public SurveyAjax() {
        super();

        for (String p : settablePrefsList) {
            prefsList.add(p);
        }
    }

    /**
     * @see HttpServlet#doPost(HttpServletRequest request, HttpServletResponse
     *      response)
     */
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        setup(request, response);
        final String qs = request.getQueryString();
        String value;
        if(qs!=null && !qs.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            Reader r = request.getReader();
            int ch;
            while ((ch = r.read()) > -1) {
                if (DEBUG)
                    System.err.println(" POST >> " + Integer.toHexString(ch));
                sb.append((char) ch);
            }
            value = sb.toString();
        } else {
            value = request.getParameter("value"); // POST based value.
        }
        processRequest(request, response, value);
    }

    /**
     * @see HttpServlet#doGet(HttpServletRequest request, HttpServletResponse
     *      response)
     */
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        setup(request, response);
        processRequest(request, response, WebContext.decodeFieldString(request.getParameter(SurveyMain.QUERY_VALUE_SUFFIX)));
    }

    private void setup(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        request.setCharacterEncoding("UTF-8");
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");
    }

    private void processRequest(HttpServletRequest request, HttpServletResponse response, String val) throws ServletException,
        IOException {
        CLDRConfigImpl.setUrls(request);
        // if(val != null) {
        // System.err.println("val="+val);
        // }
        final SurveyMain sm = SurveyMain.getInstance(request);
        PrintWriter out = response.getWriter();
        String what = request.getParameter(REQ_WHAT);
        String sess = request.getParameter(SurveyMain.QUERY_SESSION);
        String loc = request.getParameter(SurveyMain.QUERY_LOCALE);
        String xpath = request.getParameter(SurveyForum.F_XPATH);
        String vhash = request.getParameter("vhash");
        String fieldHash = request.getParameter(SurveyMain.QUERY_FIELDHASH);
        CookieSession mySession = null;

        //loc can have either - or _
        loc = (loc == null) ? null : loc.replace('-', '_');

        CLDRLocale l = null;
        if (sm != null && SurveyMain.isSetup && loc != null && !loc.isEmpty()) {
            l = validateLocale(out, loc);
            if (l == null) {
                return; // error was already thrown.
            }
        }

        try {
            if (sm == null) {
                sendNoSurveyMain(out);
            } else if (what == null) {
                sendError(out, "Missing parameter: " + REQ_WHAT, ErrorCode.E_INTERNAL);
            } else if (what.equals(WHAT_STATS_BYLOC)) {
                JSONWriter r = newJSONStatusQuick(sm);
                JSONObject query = DBUtils.queryToCachedJSON(what, 5 * 60 * 1000, StatisticsUtils.QUERY_ALL_VOTES);
                r.put(what, query);
                addGeneralStats(r);
                send(r,out);
            } else if (what.equals(WHAT_STATS_BYDAYUSERLOC)) {
                String votesAfterString = SurveyMain.getVotesAfterString();
                JSONWriter r = newJSONStatus(sm);
                final String day = DBUtils.db_Mysql ? "DATE_FORMAT(last_mod, '%Y-%m-%d')" : "last_mod ";
                final String sql = "select submitter," + day + " as day,locale,count(*) as count from " + DBUtils.Table.VOTE_VALUE
                    + " group by submitter,locale,YEAR(last_mod),MONTH(last_mod),DAYOFMONTH(last_mod)  order by day desc";
                JSONObject query = DBUtils.queryToCachedJSON(what, 5 * 60 * 1000,
                    sql);
                r.put(what, query);
                r.put("after", votesAfterString);
                send(r, out);
                // select submitter,DATE_FORMAT(last_mod, '%Y-%m-%d') as day,locale,count(*) from "+DBUtils.Table.VOTE_VALUE+" group by submitter,locale,YEAR(last_mod),MONTH(last_mod),DAYOFMONTH(last_mod) order by day desc limit 10000;
            } else if (what.equals(WHAT_STATS_BYDAY)) {
                JSONWriter r = newJSONStatus(sm);
                {
                    final String sql = DBUtils.db_Mysql ? ("select count(*) as count ,last_mod from " + DBUtils.Table.VOTE_VALUE
                        + " group by Year(last_mod) desc ,Month(last_mod) desc,Date(last_mod) desc") // mysql
                        : ("select count(*) as count ,Date(" + DBUtils.Table.VOTE_VALUE + ".last_mod) as last_mod from " + DBUtils.Table.VOTE_VALUE
                            + " group by Date(" + DBUtils.Table.VOTE_VALUE + ".last_mod)"); // derby
                    final JSONObject query = DBUtils
                        .queryToCachedJSON(what, (5 * 60 * 1000), sql);
                    r.put("byday", query);
                }
                {
                    // exclude old votes
                    final String sql2 = DBUtils.db_Mysql ? ("select count(*) as count ,last_mod from " + DBUtils.Table.VOTE_VALUE
                        + " as new_votes where " + StatisticsUtils.getExcludeOldVotesSql() + " group by Year(last_mod) desc ,Month(last_mod) desc,Date(last_mod) desc") // mysql
                        : ("select count(*) as count ,Date(" + DBUtils.Table.VOTE_VALUE + ".last_mod) as last_mod from " + DBUtils.Table.VOTE_VALUE
                            + " group by Date(" + DBUtils.Table.VOTE_VALUE + ".last_mod)"); // derby
                    final JSONObject query2 = DBUtils
                        .queryToCachedJSON(what+"_new", (5 * 60 * 1000), sql2);
                    r.put("byday_new", query2);
                }
                r.put("after", "n/a");
                send(r, out);
            } else if (what.equals(WHAT_GETXPATH)) {
                JSONWriter r = newJSONStatus(sm);
                try {
                    int xpid = XPathTable.NO_XPATH;
                    String xpath_path = null;
                    String xpath_hex = null;
                    PathHeader ph = null;
                    if(xpath.startsWith("/")) {
                        xpid = sm.xpt.getByXpath(xpath);
                        xpath_path = xpath;
                        xpath_hex = sm.xpt.getStringIDString(xpath_path);
                    } else if(xpath.startsWith("#")) {
                        xpid = Integer.parseInt(xpath.substring(1));
                        xpath_path = sm.xpt.getById(xpid);
                        xpath_hex = sm.xpt.getStringIDString(xpath_path);
                    } else {
                        xpath_path = sm.xpt.getByStringID(xpath);
                        xpid = sm.xpt.getByXpath(xpath_path);
                        xpath_hex = xpath;
                    }
                    
                    JSONObject ret = new JSONObject();
                    ret.put("path", xpath_path);
                    ret.put("id", xpid);
                    ret.put("hex", xpath_hex);
                    ret.put("ph", JSONWriter.wrap(sm.getSTFactory().getPathHeader(xpath_path)));
                    r.put(what, ret);
                } catch (Throwable t) {
                    sendError(out, t);
                    return;
                }
                send(r, out);
            } else if (what.equals(WHAT_MY_LOCALES)) {
                JSONWriter r = newJSONStatus(sm);
                String q1 = "select count(*) as count, " + DBUtils.Table.VOTE_VALUE + ".locale as locale from " + DBUtils.Table.VOTE_VALUE + " WHERE "
                    + DBUtils.Table.VOTE_VALUE + ".submitter=? AND " + DBUtils.Table.VOTE_VALUE + ".value is not NULL " +
                    " group by " + DBUtils.Table.VOTE_VALUE + ".locale order by " + DBUtils.Table.VOTE_VALUE + ".locale desc";
                String user = request.getParameter("user");
                UserRegistry.User u = null;
                if (user != null && !user.isEmpty())
                    try {
                        u = sm.reg.getInfo(Integer.parseInt(user));
                    } catch (Throwable t) {
                        SurveyLog.logException(t, "Parsing user " + user);
                    }

                JSONObject query;
                query = DBUtils.queryToJSON(q1, u.id);
                r.put("mine", query);
                send(r, out);
            } else if (what.equals(WHAT_RECENT_ITEMS)) {
                JSONWriter r = newJSONStatus(sm);
                int limit = 15;
                try {
                    limit = Integer.parseInt(request.getParameter("limit"));
                } catch (Throwable t) {
                    limit = 15;
                }
                String q1 = "select " + DBUtils.Table.VOTE_VALUE + ".locale,cldr_xpaths.xpath,cldr_users.org, " + DBUtils.Table.VOTE_VALUE + ".value, "
                    + DBUtils.Table.VOTE_VALUE + ".last_mod  from cldr_xpaths," + DBUtils.Table.VOTE_VALUE + ",cldr_users  where ";
                String q2 = "cldr_xpaths.id=" + DBUtils.Table.VOTE_VALUE + ".xpath and cldr_users.id=" + DBUtils.Table.VOTE_VALUE + ".submitter"
                    + "  order by " + DBUtils.Table.VOTE_VALUE + ".last_mod desc ";
                String user = request.getParameter("user");
                UserRegistry.User u = null;
                if (user != null && !user.isEmpty())
                    try {
                        u = sm.reg.getInfo(Integer.parseInt(user));
                    } catch (Throwable t) {
                        SurveyLog.logException(t, "Parsing user " + user);
                    }
                System.out.println("SQL: " + q1 + q2);
                JSONObject query;
                if (l == null && u == null) {
                    query = DBUtils.queryToJSONLimit(limit, q1 + q2);
                } else if (u == null && l != null) {
                    query = DBUtils.queryToJSONLimit(limit, q1 + " " + DBUtils.Table.VOTE_VALUE + ".locale=? AND " + q2, l);
                } else if (u != null && l == null) {
                    query = DBUtils.queryToJSONLimit(limit, q1 + " " + DBUtils.Table.VOTE_VALUE + ".submitter=? AND " + q2, u.id);
                } else {
                    query = DBUtils.queryToJSONLimit(limit, q1 + " " + DBUtils.Table.VOTE_VALUE + ".locale=? AND " + DBUtils.Table.VOTE_VALUE + ".submitter=? "
                        + q2, l, u.id);
                }
                r.put("recent", query);
                send(r, out);
            } else if (what.equals(WHAT_STATUS)) {
                JSONWriter r2 = newJSONStatus(sm);
                setLocaleStatus(sm, loc, r2);

                if (sess != null && !sess.isEmpty()) {
                    CookieSession.checkForExpiredSessions();
                    mySession = CookieSession.retrieve(sess);
                }

                if (sess != null && !sess.isEmpty()) {
                    mySession = CookieSession.retrieve(sess); // or peek?
                    if (mySession != null) {
                        r2.put("timeTillKick", mySession.timeTillKick());
                    } else {
                        //                        r2.put("err", "You are not logged into the survey tool)
                        r2.put("session_err", "no session");
                    }
                }
                send(r2, out);
            } else if (what.equals(WHAT_REVIEW_HIDE)) {
                CookieSession.checkForExpiredSessions();
                mySession = CookieSession.retrieve(sess);
                
                if (mySession == null) {
                    sendError(out, "Missing/Expired Session (idle too long? too many users?): " + sess, ErrorCode.E_SESSION_DISCONNECTED);
                } else {
                    mySession.userDidAction();
                    ReviewHide review = new ReviewHide();
                    review.toggleItem(request.getParameter("choice"), Integer.parseInt(request.getParameter("path")), mySession.user.id, request.getParameter("locale"));
                }
                this.send(new JSONWriter(), out);
            }   
            else if (what.equals(WHAT_REVIEW_ADD_POST)) {
                CookieSession.checkForExpiredSessions();
                mySession = CookieSession.retrieve(sess);
                
                JSONWriter postJson = new JSONWriter();
                if (mySession == null) {
                    sendError(out, "Missing/Expired Session (idle too long? too many users?): " + sess, ErrorCode.E_SESSION_DISCONNECTED);
                } else {
                    mySession.userDidAction();
                    WebContext ctx = new WebContext(request, response);
                    ctx.sm = sm;
                    ctx.session = mySession;
                    sm.fora.doForum(ctx, "");
                }
                
                this.send(postJson, out);
            } 
            else if (sess != null && !sess.isEmpty()) { // this and following:
                                                          // session needed
                CookieSession.checkForExpiredSessions();
                mySession = CookieSession.retrieve(sess);
                if (mySession == null) {
                    sendError(out, "Missing/Expired Session (idle too long? too many users?): " + sess, ErrorCode.E_SESSION_DISCONNECTED);
                } else {
                    if (what.equals(WHAT_VERIFY) || what.equals(WHAT_SUBMIT) || what.equals(WHAT_DELETE)) {
                        mySession.userDidAction();
                       
                        CLDRLocale locale = CLDRLocale.getInstance(loc);
                        CheckCLDR.Options options = DataSection.getOptions(null, mySession, locale);
                        STFactory stf = sm.getSTFactory();
                        TestResultBundle cc = stf.getTestResult(locale, options);
                        int id = Integer.parseInt(xpath);
                        String xp = sm.xpt.getById(id);
                        final List<CheckStatus> result = new ArrayList<CheckStatus>();
                        // CLDRFile file = CLDRFile.make(loc);
                        // CLDRFile file = mySession.
                        SurveyMain.UserLocaleStuff uf = null;
                        boolean dataEmpty = false;
                        JSONWriter r = newJSONStatus(sm);
                        synchronized (mySession) {
                            try {
                                BallotBox<UserRegistry.User> ballotBox = sm.getSTFactory().ballotBoxForLocale(locale);
                                boolean foundVhash = false;
                                Exception[] exceptionList = new Exception[1];
                                String otherErr = null;
                                String origValue = val;
                                if (vhash != null && vhash.length() > 0) {
                                    if (vhash.equals("null")) {
                                        val = null;
                                        foundVhash = true;
                                    } else {
                                        // String newValue = null;
                                        // for(String s :
                                        // ballotBox.getValues(xp)) {
                                        // if(vhash.equals(DataSection.getValueHash(s)))
                                        // {
                                        // val = newValue = s;
                                        // foundVhash=true;
                                        // }
                                        // }
                                        // if(newValue == null) {
                                        // sendError(out, "Missing value hash: "
                                        // + vhash);
                                        // return;
                                        // }

                                        val = DataSection.fromValueHash(vhash);
                                        // System.err.println("'"+vhash+"' -> '"+val+"'");
                                        foundVhash = true;
                                    }
                                }
                                
                                final String origValue2 = val;

                                if (val != null) {
                                    if (DEBUG)
                                        System.err.println("val WAS " + escapeString(val));
                                    DisplayAndInputProcessor daip = new DisplayAndInputProcessor(locale, false);
                                    val = daip.processInput(xp, val, exceptionList);
                                    if (DEBUG)
                                        System.err.println("val IS " + escapeString(val));
                                    if (val.isEmpty()) {
                                        otherErr = ("DAIP returned a 0 length string");
                                    }
                                }

                                if (val != null && !foundVhash) {
                                    uf = sm.getUserFile(mySession, locale);
                                    CLDRFile file = uf.cldrfile;
                                    cc.check(xp, result, val);
                                    dataEmpty = file.isEmpty();
                                }

                                r.put(SurveyMain.QUERY_FIELDHASH, fieldHash);

                                if (exceptionList[0] != null) {
                                    result.add(new CheckStatus().setMainType(CheckStatus.errorType)
                                        .setSubtype(Subtype.internalError)
                                        .setCause(new CheckCLDR() {
                                            
                                            @Override
                                            public CheckCLDR handleCheck(String path, String fullPath, String value, Options options, List<CheckStatus> result) {
                                                // TODO Auto-generated method stub
                                                return null;
                                            }
                                        })
                                        .setMessage("Input Processor Exception: {0}")
                                        .setParameters(exceptionList));
                                    SurveyLog.logException(exceptionList[0], "DAIP, Processing " + loc + ":" + xp + "='" + val
                                        + "' (was '" + origValue + "')");
                                }

                                if (otherErr != null) {
                                    String list[] = { otherErr };
                                    result.add(new CheckStatus().setMainType(CheckStatus.errorType)
                                        .setSubtype(Subtype.internalError)
                                        .setCause(new CheckCLDR() {
                                            
                                            @Override
                                            public CheckCLDR handleCheck(String path, String fullPath, String value, Options options, List<CheckStatus> result) {
                                                // TODO Auto-generated method stub
                                                return null;
                                            }
                                        })
                                        .setMessage("Input Processor Error: {0}")
                                        .setParameters(list));
                                    SurveyLog.logException(null, "DAIP, Processing " + loc + ":" + xp + "='" + val + "' (was '"
                                        + origValue + "'): " + otherErr);
                                }

                                r.put("testErrors", hasErrors(result));
                                r.put("testWarnings", hasWarnings(result));
                                r.put("testResults", JSONWriter.wrap(result));
                                r.put("testsRun", cc.toString());
                                r.put("testsV", val);
                                r.put("testsHash", DataSection.getValueHash(val));
                                r.put("testsLoc", loc);
                                r.put("xpathTested", xp);
                                r.put("dataEmpty", Boolean.toString(dataEmpty));

                                if (what.equals(WHAT_SUBMIT)) {
                                    if (!UserRegistry.userCanModifyLocale(mySession.user, locale)) {
                                        throw new InternalError("User cannot modify locale.");
                                    }

                                    PathHeader ph = stf.getPathHeader(xp);
                                    CheckCLDR.Phase cPhase = CLDRConfig.getInstance().getPhase();
                                    SurveyToolStatus phStatus = ph.getSurveyToolStatus();

                                    final String candVal = val;

                                    DataSection section = DataSection.make(null, null, mySession, locale, xp, null, false,
                                        Level.COMPREHENSIVE.toString());
                                    section.setUserAndFileForVotelist(mySession.user, null);

                                    DataRow pvi = section.getDataRow(xp);
                                    final Level covLev = pvi.getCoverageLevel();
                                    //final int coverageValue = covLev.getLevel();
                                    CheckCLDR.StatusAction showRowAction = pvi.getStatusAction();

                                    if (otherErr != null) {
                                        r.put("didNotSubmit", "Did not submit.");
                                    } else if (!showRowAction.isForbidden()) {
                                        CandidateInfo ci;
                                        if (candVal == null) {
                                            ci = null; // abstention
                                        } else {
                                            ci = pvi.getItem(candVal); // existing
                                                                       // item?
                                            if (ci == null) { // no, new item
                                                ci = new CandidateInfo() {
                                                    @Override
                                                    public String getValue() {
                                                        return candVal;
                                                    }

                                                    @Override
                                                    public Collection<UserInfo> getUsersVotingOn() {
                                                        return Collections.emptyList(); // No
                                                                                        // users
                                                                                        // voting
                                                                                        // -
                                                                                        // yet.
                                                    }

                                                    @Override
                                                    public List<CheckStatus> getCheckStatusList() {
                                                        return result;
                                                    }
                                                };
                                            }
                                        }
                                        CheckCLDR.StatusAction statusActionNewItem = cPhase.getAcceptNewItemAction(ci, pvi,
                                            CheckCLDR.InputMethod.DIRECT, phStatus, mySession.user);
                                        if (statusActionNewItem.isForbidden()) {
                                            r.put("statusAction", statusActionNewItem);
                                            if (DEBUG)
                                                System.err.println("Not voting: ::  " + statusActionNewItem);
                                        } else {
                                            if (DEBUG)
                                                System.err.println("Voting for::  " + val);
                                            Integer withVote = null;
                                            try {
                                                withVote = Integer.parseInt(request.getParameter("voteReduced"));
                                            } catch (Throwable t) {
                                                withVote = null;
                                            }
                                            ballotBox.voteForValue(mySession.user, xp, val, withVote);
                                            String subRes = ballotBox.getResolver(xp).toString();
                                            if (DEBUG)
                                                System.err.println("Voting result ::  " + subRes);
                                            r.put("submitResultRaw", subRes);
                                        }
                                    } else {
                                        if (DEBUG)
                                            System.err.println("Not voting: ::  " + showRowAction);
                                        r.put("statusAction", showRowAction);
                                    }
                                    // don't allow adding items if
                                    // ALLOW_VOTING_BUT_NO_ADD

                                    // informational
                                    r.put("cPhase", cPhase);
                                    r.put("phStatus", phStatus);
                                    r.put("covLev", covLev);
                                } else if (what.equals(WHAT_DELETE)) {
                                    if (!UserRegistry.userCanModifyLocale(mySession.user, locale)) {
                                        throw new InternalError("User cannot modify locale.");
                                    }

                                    ballotBox.deleteValue(mySession.user, xp, val);
                                    String delRes = ballotBox.getResolver(xp).toString();
                                    if (DEBUG)
                                        System.err.println("Voting result ::  " + delRes);
                                    r.put("deleteResultRaw", delRes);
                                }
                            } catch (Throwable t) {
                                SurveyLog.logException(t, "Processing submission/deletion " + locale + ":" + xp);
                                SurveyAjax.JSONWriter.putException(r,t);
                            } finally {
                                if (uf != null)
                                    uf.close();
                            }
                        }

                        send(r, out);
                    } else if (what.equals(WHAT_PREF)) {
                        mySession.userDidAction();
                        String pref = request.getParameter("pref");

                        JSONWriter r = newJSONStatus(sm);
                        r.put("pref", pref);

                        if (!prefsList.contains(pref)) {
                            sendError(out, "Bad or unsupported pref: " + pref, ErrorCode.E_INTERNAL);
                        }

                        if (pref.equals("oldVoteRemind")) {
                            pref = getOldVotesPref();
                        }

                        if (val != null && !val.isEmpty()) {
                            if (val.equals("null")) {
                                mySession.settings().set(pref, null);
                            } else {
                                mySession.settings().set(pref, val);
                            }
                        }
                        r.put(SurveyMain.QUERY_VALUE_SUFFIX, mySession.settings().get(pref, null));
                        send(r, out);
                    } else if (what.equals(WHAT_VSUMMARY)) {
                        assertCanUseVettingSummary(mySession);
                        VettingViewerQueue.LoadingPolicy policy = VettingViewerQueue.LoadingPolicy.valueOf(request.getParameter("loadingpolicy"));
                        mySession.userDidAction();
                        JSONWriter r = newJSONStatus(sm);
                        r.put("what", what);
                        r.put("loadingpolicy",policy);
                        VettingViewerQueue.Status status[] = new VettingViewerQueue.Status[1];
                        StringBuilder sb = new StringBuilder();
                        JSONObject jStatus = new JSONObject();
                        String str = VettingViewerQueue.getInstance()
                            .getVettingViewerOutput(null, mySession, VettingViewerQueue.SUMMARY_LOCALE, status,
                            policy, sb, jStatus);
                        r.put("jstatus",jStatus);
                        r.put("status", status[0]);
                        r.put("ret", str);
                        r.put("output", sb.toString());

                        send(r, out);
                    } else if (what.equals(WHAT_FORUM_COUNT)) {
                        mySession.userDidAction();
                        JSONWriter r = newJSONStatus(sm);
                        r.put("what", what);
                        CLDRLocale locale = CLDRLocale.getInstance(loc);
                        int id = Integer.parseInt(xpath);
                        r.put(what, sm.fora.postCountFor(locale, id));
                        send(r, out);
                    } else if (what.equals(WHAT_FORUM_FETCH)) {
                        mySession.userDidAction();
                        JSONWriter r = newJSONStatus(sm);
                        r.put("what", what);

                        CLDRLocale locale = CLDRLocale.getInstance(loc);
                        int id = Integer.parseInt(xpath);
                        //String xp = sm.xpt.getById(id);
                        r.put("loc", loc);
                        r.put("xpath", xpath);
                        r.put("ret", mySession.sm.fora.toJSON(mySession, locale, id));

                        send(r, out);
                    } else if(what.equals(WHAT_FORUM_POST)) {
                        mySession.userDidAction();
                        JSONWriter r = newJSONStatusQuick(sm);
                        r.put("what", what);
                        final String subjStr = request.getParameter("subj");
                        final String textStr = request.getParameter("text");
                        final String subj = SurveyForum.HTMLSafe(subjStr);
                        final String text = SurveyForum.HTMLSafe(textStr);
                        final int replyTo = getIntParameter(request, "replyTo", SurveyForum.NO_PARENT);
                        final int postId = sm.fora.doPost(mySession, xpath, l, subj, text, replyTo);
                        r.put("postId", postId);
                        if(postId > 0) {
                            r.put("ret", mySession.sm.fora.toJSON(mySession, l, XPathTable.NO_XPATH, postId));
                        }
                        send(r, out);
                    } else if (what.equals("mail")) {
                        mySession.userDidAction();
                        JSONWriter r = newJSONStatus(sm);
                        if (mySession.user == null) {
                            r.put("err", "Not logged in.");
                            r.put("err_code", E_NO_ACCESS);
                        } else {

                            String fetchAll = request.getParameter("fetchAll");
                            int markRead = -1;
                            if (request.getParameter("markRead") != null) {
                                markRead = Integer.parseInt(request.getParameter("markRead"));
                            }

                            if (fetchAll != null) {
                                r.put("mail", MailSender.getInstance().getMailFor(mySession.user.id));
                            } else if (markRead != -1) {
                                if (MailSender.getInstance().setRead(mySession.user.id, markRead)) {
                                    r.put("mail", "true");
                                } else {
                                    r.put("mail", "false"); // failed to mark
                                }
                            }
                        }
                        send(r, out);
                    } else if (what.equals(WHAT_GET_MENUS)) {

                        mySession.userDidAction();
                        JSONWriter r = newJSONStatus(sm);
                        r.put("what", what);

                        SurveyMenus menus = sm.getSTFactory().getSurveyMenus();

                        if (loc == null || loc.isEmpty()) {
                            // nothing
                            //                            CLDRLocale locale = CLDRLocale.getInstance("und");
                            //                            r.put("loc", loc);
                            //                            r.put("menus",menus.toJSON(locale));
                        } else {
                            r.put("covlev_org", mySession.getOrgCoverageLevel(loc));
                            r.put("covlev_user", mySession.settings().get(SurveyMain.PREF_COVLEV, null));
                            CLDRLocale locale = CLDRLocale.getInstance(loc);
                            r.put("loc", loc);
                            r.put("menus", menus.toJSON(locale));
                            
                            //add the report menu
                            JSONArray reports = new JSONArray();
                            for (SurveyMain.ReportMenu m : SurveyMain.ReportMenu.values()) {
                                JSONObject report = new JSONObject();
                                
                                if(m.hasQuery()) {
                                     report.put("url", m.urlQuery());
                                     report.put("hasQuery", true);
                                } else {
                                     report.put("url", m.urlStub());
                                     report.put("hasQuery", false);
                                }
                                
                                report.put("display", m.display());
                                reports.put(report);
                            }
                            
                            r.put("reports", reports);                
                        }   

                        if ("true".equals(request.getParameter("locmap"))) {
                            r.put("locmap", getJSONLocMap(sm));
                            
                            // list of modifyable locales
                            JSONArray modifyableLocs = new JSONArray();
                            Set<CLDRLocale> rolocs = sm.getReadOnlyLocales();
                            for (CLDRLocale al : SurveyMain.getLocales()) {
                                if (rolocs.contains(al)) continue;
                                if (UserRegistry.userCanModifyLocale(mySession.user, al)) {
                                    modifyableLocs.put(al.getBaseName());
                                }
                            }
                            if (modifyableLocs.length() > 0) {
                                r.put("canmodify", modifyableLocs);
                            }
                            // any special messages?
                            if (mySession.user != null && mySession.user.canImportOldVotes()) {
                               
                                // old votes?
                                String oldVotesPref = getOldVotesPref();
                                String oldVoteRemind = mySession.settings().get(oldVotesPref, null);
                                if (oldVoteRemind == null || // never been asked
                                    !oldVoteRemind.equals("*")) { //  dont ask again

                                    String oldVotesTable = STFactory.getOldVoteTable();

                                    if (DBUtils.hasTable(oldVotesTable)) {
                                        int count = DBUtils.sqlCount("select  count(*) as count from " + oldVotesTable
                                            + " where submitter=? " +
                                            " and value is not null", mySession.user.id);

                                        if (count == 0) { // may be -1 on error
                                            mySession.settings().set(oldVotesPref, "*"); // Do not ask again this release
                                        } else {
                                            if (SurveyMain.isUnofficial())
                                                System.out.println("Old Votes remaining: " + mySession.user + " oldVotesCount = " + count + " and "
                                                    + oldVotesPref
                                                    + "=" + oldVoteRemind);
                                            r.put("oldVotesRemind", new JSONObject().put("pref", oldVotesPref).put("remind", oldVoteRemind).put("count", count));
                                        }
                                    }
                                }
                            }
                        }

                        send(r, out);
                    } else if (what.equals(WHAT_POSS_PROBLEMS)) {
                        mySession.userDidAction();
                        JSONWriter r = newJSONStatus(sm);
                        r.put("what", what);

                        CLDRLocale locale = CLDRLocale.getInstance(loc);
                        r.put("loc", loc);
                        if (locale == null) {
                            r.put("err", "Bad locale: " + loc);
                            r.put("err_code", ErrorCode.E_BAD_LOCALE);
                            send(r, out);
                            return;
                        }

                        String eff = request.getParameter("eff");
                        String req = request.getParameter("req");

                        UserLocaleStuff uf = sm.getUserFile(mySession, locale);
                        String requiredLevel = null;
                        String localeType = null;
                        if (!"null".equals(req)) {
                            requiredLevel = req;
                        }
                        if (!"null".equals(eff)) {
                            localeType = eff;
                        }
//                        final CheckCLDR.Options optMap = new Options(locale, SurveyMain.getTestPhase(), requiredLevel, localeType);
                        List<CheckStatus> checkCldrResult = (List<CheckStatus>) uf.hash.get(SurveyMain.CHECKCLDR_RES + eff);

                        if (checkCldrResult == null) {
                            r.put("possibleProblems", new JSONArray());
                        } else {
                            r.put("possibleProblems", JSONWriter.wrap(checkCldrResult));
                        }

                        //                        if ((checkCldrResult != null) && (!checkCldrResult.isEmpty())
                        //                                && (/* true || */(checkCldr != null) )) {
                        //                            ctx.println("<div style='border: 1px dashed olive; padding: 0.2em; background-color: cream; overflow: auto;'>");
                        //                            ctx.println("<b>Possible problems with locale:</b><br>");
                        //                            for (Iterator it3 = checkCldrResult.iterator(); it3.hasNext();) {
                        //                                CheckCLDR.CheckStatus status = (CheckCLDR.CheckStatus) it3.next();
                        //                                try {
                        //                                    if (!status.getType().equals(status.exampleType)) {
                        //                                        String cls = shortClassName(status.getCause());
                        //                                        ctx.printHelpLink("/" + cls, "<!-- help with -->" + cls, true);
                        //                                        ctx.println(": ");
                        //                                        printShortened(ctx, status.toString(), LARGER_MAX_CHARS);
                        //                                        ctx.print("<br>");
                        //                                    } else {
                        //                                        ctx.println("<i>example available</i><br>");
                        //                                    }
                        //                                } catch (Throwable t) {
                        //                                    String result;
                        //                                    try {
                        //                                        result = status.toString();
                        //                                    } catch (Throwable tt) {
                        //                                        tt.printStackTrace();
                        //                                        result = "(Error reading error: " + tt + ")";
                        //                                    }
                        //                                    ctx.println("Error reading status item: <br><font size='-1'>" + result + "<br> - <br>" + t.toString()
                        //                                            + "<hr><br>");
                        //                                }
                        //                            }
                        //                            ctx.println("</div>");
                        //                        }

                        send(r, out);
                    } else if (what.equals("oldvotes")) {
                        mySession.userDidAction();
                        JSONWriter r = newJSONStatus(sm);
                        r.put("what", what);

                        if (mySession.user == null) {
                            r.put("err", "Must be logged in");
                            r.put("err_code", ErrorCode.E_NOT_LOGGED_IN);
                        } else if (!mySession.user.canImportOldVotes()) {
                            r.put("err", "No permission to do this (may not be the right SurveyTool phase)");
                            r.put("err_code", ErrorCode.E_NO_PERMISSION);
                        } else {
                            JSONObject oldvotes = new JSONObject();
                            final String oldVotesTable = STFactory.getOldVoteTable();
                            final String newVotesTable = DBUtils.Table.VOTE_VALUE.toString();
                            oldvotes.put("votesafter", "(version" + SurveyMain.getOldVersion() + ")");

                            if (loc == null || loc.isEmpty()) {
                                oldvotes.put(
                                    "locales",
                                    DBUtils.queryToJSON("select  locale,count(*) as count from " + oldVotesTable
                                        + " where submitter=? " +
                                        " and value is not null " +
                                        " and not exists (select * from " + newVotesTable + " where " + oldVotesTable + ".locale=" + newVotesTable + ".locale "
                                        +
                                        " and " + oldVotesTable + ".xpath=" + newVotesTable + ".xpath and " + newVotesTable + ".locale=" + oldVotesTable
                                        + ".locale )" +
                                        "group by locale order by locale", mySession.user.id));
                            } else {
                                CLDRLocale locale = CLDRLocale.getInstance(loc);
                                oldvotes.put("locale", locale);
                                oldvotes.put("localeDisplayName", locale.getDisplayName());
                                oldvotes.put("dir", sm.getDirectionalityFor(locale));
                                STFactory fac = sm.getSTFactory();
                                //CLDRFile file = fac.make(loc, false);
                                CLDRFile file = sm.getOldFile(loc, true);

                                if (null != request.getParameter("doSubmit")) {
                                    // submit time.
                                    if (SurveyMain.isUnofficial())
                                        System.out.println("User " + mySession.user.toString() + "  is migrating old votes in " + locale.getDisplayName());
                                    JSONObject list = new JSONObject(val);
                                    if (list.getString("locale").equals(locale + "snork")) {
                                        throw new IllegalArgumentException("Sanity error- locales " + locale + " and " + list.getString("locale")
                                            + " do not match");
                                    }

                                    BallotBox<User> box = fac.ballotBoxForLocale(locale);

                                    int deletions = 0;
                                    int confirmations = 0;
                                    int uncontested = 0;

                                    JSONArray confirmList = list.getJSONArray("confirmList");
                                    JSONArray deleteList = list.getJSONArray("deleteList");

                                    Set<String> deleteSet = new HashSet<String>();
                                    Set<String> confirmSet = new HashSet<String>();

                                    // deletions
                                    for (int i = 0; i < confirmList.length(); i++) {
                                        String strid = confirmList.getString(i);
                                        //String xp = sm.xpt.getByStringID(strid);
                                        //box.unvoteFor(mySession.user,xp);
                                        //deletions++;
                                        confirmSet.add(strid);
                                    }

                                    // confirmations
                                    for (int i = 0; i < deleteList.length(); i++) {
                                        String strid = deleteList.getString(i);
                                        //String xp = sm.xpt.getByStringID(strid);
                                        //box.revoteFor(mySession.user,xp);
                                        //confirmations++;
                                        deleteSet.add(strid);
                                    }

                                    // now, get all
                                    {
                                        String sqlStr = "select xpath,value from " + oldVotesTable + " where locale=? and submitter=? and value is not null " +
                                            " and not exists (select * from " + newVotesTable + " where " + oldVotesTable + ".locale=" + newVotesTable
                                            + ".locale  and " + oldVotesTable + ".xpath=" + newVotesTable + ".xpath and " + newVotesTable
                                            + ".value is not null)";
                                        Map<String, Object> rows[] = DBUtils.queryToArrayAssoc(sqlStr, locale, mySession.user.id);
                                        //                                        System.out.println("Running >> " + sqlStr + " -> " + rows.length);

                                        //JSONArray contested = new JSONArray();

                                        for (Map m : rows) {
                                            String value = m.get("value").toString();
                                            if (value == null) continue; // ignore unvotes.
                                            int xp = (Integer) m.get("xpath");
                                            String xpathString = sm.xpt.getById(xp);
                                            // String xpathStringHash = sm.xpt.getStringIDString(xp);
                                            try {
                                                String curValue = file.getStringValue(xpathString);
                                                if (false && value.equals(curValue)) {
                                                    box.voteForValue(mySession.user, xpathString, value); // auto vote for uncontested
                                                    uncontested++;
                                                } else {
                                                    String strid = sm.xpt.getStringIDString(xp);
                                                    if (deleteSet.contains(strid)) {
                                                        box.unvoteFor(mySession.user, xpathString);
                                                        deletions++;
                                                    } else if (confirmSet.contains(strid)) {
                                                        box.voteForValue(mySession.user, xpathString, value);
                                                        confirmations++;
                                                    } else {
                                                        //System.err.println("SAJ: Ignoring non mentioned strid " + xpathString + " for loc " + locale + " in user "  +mySession.user);
                                                    }
                                                }
                                            } catch (InvalidXPathException ix) {
                                                SurveyLog.logException(ix, "Bad XPath: Trying to vote for " + xpathString);
                                            } catch (VoteNotAcceptedException ix) {
                                                SurveyLog.logException(ix, "Vote not accepted: Trying to vote for " + xpathString);
                                            }
                                        }
                                    }

                                    oldvotes.put("didUnvotes", deletions);
                                    oldvotes.put("didRevotes", confirmations);
                                    oldvotes.put("didUncontested", uncontested);
                                    System.out.println("Old Vote migration for " + mySession.user + " " + locale + " - delete:" + deletions + ", confirm:"
                                        + confirmations + ", uncontestedconfirm:" + uncontested);
                                    oldvotes.put("ok", true);

                                } else {
                                    // view old votes
                                    String sqlStr = "select xpath,value from " + oldVotesTable + " where locale=? and submitter=? and value is not null " +
                                        " and not exists (select * from " + newVotesTable + " where " + oldVotesTable + ".locale=" + newVotesTable
                                        + ".locale  and " + oldVotesTable + ".xpath=" + newVotesTable + ".xpath  and " + newVotesTable + ".value is not null)";
                                    Map<String, Object> rows[] = DBUtils.queryToArrayAssoc(sqlStr, locale, mySession.user.id);
                                    //                                    System.out.println("Running >> " + sqlStr + " -> " + rows.length);

                                    // extract the pathheaders
                                    for (int i = 0; i < rows.length; i++) {
                                        Map m = rows[i];
                                        int xp = (Integer) m.get("xpath");
                                        String xpathString = sm.xpt.getById(xp);
                                        m.put("pathHeader", fac.getPathHeader(xpathString));
                                    }

                                    // sort by pathheader
                                    Arrays.sort(rows, new Comparator<Map>() {

                                        @Override
                                        public int compare(Map o1, Map o2) {
                                            return ((PathHeader) o1.get("pathHeader")).compareTo((PathHeader) o2.get("pathHeader"));
                                        }
                                    });

                                    JSONArray uncontested = new JSONArray();
                                    JSONArray contested = new JSONArray();

                                    int bad = 0;

                                    CLDRFile baseF = sm.getBaselineFile();

                                    //CoverageLevel2 cov = CoverageLevel2.getInstance(sm.getSupplementalDataInfo(),loc);

                                    Set<String> validPaths = fac.getPathsForFile(locale);
                                    CoverageInfo covInfo=CLDRConfig.getInstance().getCoverageInfo();
                                    for (Map m : rows) {
                                        String value = m.get("value").toString();
                                        if (value == null) continue; // ignore unvotes.
                                        PathHeader pathHeader = (PathHeader) m.get("pathHeader");
                                        //                                        System.err.println("PH " + pathHeader + " =" + pathHeader.getSurveyToolStatus());
                                        if (pathHeader.getSurveyToolStatus() != PathHeader.SurveyToolStatus.READ_WRITE) {
                                            bad++;
                                            continue; // skip these
                                        }
                                        int xp = (Integer) m.get("xpath");
                                        String xpathString = sm.xpt.getById(xp);
                                        if (!validPaths.contains(xpathString)) {
                                            bad++;
                                            continue;
                                        }
                                        if (covInfo.getCoverageValue(xpathString, loc) > Level.COMPREHENSIVE.getLevel()) {
                                            //System.err.println("SkipCov PH " + pathHeader + " =" + pathHeader.getSurveyToolStatus());
                                            bad++;
                                            continue; // out of coverage
                                        }
                                        String xpathStringHash = sm.xpt.getStringIDString(xp);
                                        String curValue = file.getStringValue(xpathString);
                                        JSONObject aRow = new JSONObject()
                                            .put("strid", xpathStringHash)
                                            .put("myValue", value)
                                            .put("winValue", curValue)
                                            .put("baseValue", baseF.getStringValue(xpathString))
                                            .put("pathHeader", pathHeader.toString());
                                        if (value.equals(curValue)) {
                                            uncontested.put(aRow);
                                        } else {
                                            contested.put(aRow);
                                        }
                                    }

                                    oldvotes.put("contested", contested);
                                    oldvotes.put("uncontested", uncontested);
                                    oldvotes.put("bad", bad);
                                }
                            }

                            r.put("oldvotes", oldvotes);

                            r.put("BASELINE_LANGUAGE_NAME", SurveyMain.BASELINE_LANGUAGE_NAME);
                            r.put("BASELINE_ID", SurveyMain.BASELINE_ID);
                        }

                        send(r, out);
                    } else if (what.equals(WHAT_GETSIDEWAYS)) {
                        mySession.userDidAction();

                        final String xpathString = sm.xpt.getByStringID(xpath);
                        if (xpathString == null) {
                            throw new IllegalArgumentException("could not find strid: " + xpath);
                        }

                        // initialize some used variables
                        final CLDRLocale topLocale = l.getHighestNonrootParent();
                        final Collection<CLDRLocale> relatedLocs = sm.getRelatedLocs(topLocale); // sublocales of the 'top' locale
                        JSONObject others = new JSONObject(); // values
                        JSONArray empties = new JSONArray(); // no value

                        // some variables used in the loop
                        SupplementalDataInfo sdi = sm.getSupplementalDataInfo();
                        Set<CLDRLocale> readOnlyLocales = SurveyMain.getReadOnlyLocales();
                        STFactory stf = sm.getSTFactory();
                        JSONWriter cachedSideView = stf.getCachedSideView(topLocale.getBaseName(), xpath);

                        if (cachedSideView != null) { // it has been cached, update
                            XMLSource src = stf.makeSource(loc, false);
                            User mine = mySession.user;
                            String curValue = src.getValueAtDPath(xpathString);
                            String preValue = stf.getValueForLocale(loc, xpath, curValue, mine);

                            if (preValue == curValue) { // same, directly send cached one
                                send(cachedSideView, out);
                            } else {
                                // update curValue here
                                others = (JSONObject) cachedSideView.get("others");
                                empties = (JSONArray) cachedSideView.get("novalue");

                                JSONArray other = null;
                                if (others.has(curValue)) {
                                    other = others.getJSONArray(curValue);
                                    other.put(loc);
                                } else if (curValue != null) {
                                    other = new JSONArray();
                                    others.put(curValue, other);
                                    other.put(loc);
                                } else if (curValue == null) {
                                    empties.put(loc);
                                }

                                // update topLocaleValue here
                                if (l == topLocale) {
                                    cachedSideView.put("topLocaleValue", curValue);
                                }

                                // remove preValue based on its category(novalue, others)
                                if (preValue != null) {
                                    JSONArray previous = others.getJSONArray(preValue);
                                    previous.remove(loc);
                                    if (previous.length() == 0) { // totaly remove if nothing there
                                        others.remove(preValue);
                                    }
                                    send(cachedSideView, out);
                                } else {
                                    empties.remove(loc);
                                    send(cachedSideView, out);
                                }
                            }
                        } else { // not cached, construct and cache
                            String topLocaleValue = null;
                            final JSONWriter r = newJSONStatusQuick(sm);
                            r.put("what", what);
                            r.put("loc", loc);
                            r.put("xpath", xpath);
                            r.put("topLocale", topLocale);

                            for (CLDRLocale ol : relatedLocs) {
                                CLDRLocale dcParent = sdi.getBaseFromDefaultContent(ol);
                                String baseName = ol.getBaseName();
                                XMLSource src = stf.makeSource(baseName, false);
                                String ov = src.getValueAtDPath(xpathString);
                                User mine = mySession.user;

                                // cache locale path data here
                                stf.setValueForLocale(baseName, xpath, ov, mine);

                                if (ol == topLocale) {
                                    topLocaleValue = ov;
                                }

                                // read-only has no value, and has separate entry in JSON
                                if (readOnlyLocales.contains(ol) || dcParent != null) {
                                    r.put("readOnlyLocale", baseName);
                                } else if (ov != null) {
                                    //if(ol == l) continue;
                                    JSONArray other = null;
                                    if (others.has(ov)) {
                                        other = others.getJSONArray(ov);
                                    } else {
                                        other = new JSONArray();
                                        others.put(ov, other);
                                    }
                                    other.put(baseName);
                                } else {
                                    empties.put(baseName);
                                }
                            }

                            r.put("topLocaleValue", topLocaleValue);
                            r.put("others", others);
                            r.put("novalue", empties);
                            // cache JSONWriter here
                            stf.setCachedSideView(topLocale.getBaseName(), xpath, r);
                            send(r, out);
                        }
                    } else if (what.equals(WHAT_SEARCH)) {
                        mySession.userDidAction();
                        final JSONWriter r = newJSONStatusQuick(sm);
                        final String q = val;
                        r.put("what", what);
                        r.put("loc", loc);
                        r.put("q", q);

                        JSONArray results = searchResults(q, l, mySession);
                        r.put("results", results);
                        send(r, out);
                    } else if (what.equals(WHAT_PARTICIPATING_USERS)) {
                        assertHasUser(mySession);
                        assertIsTC(mySession);
                        JSONWriter r = newJSONStatusQuick(sm);
                        final String sql = "select cldr_users.id as id, cldr_users.email as email, cldr_users.org as org from cldr_users, "+DBUtils.Table.VOTE_VALUE+" where "
                            +DBUtils.Table.VOTE_VALUE+".submitter = cldr_users.id and "+DBUtils.Table.VOTE_VALUE+".submitter is not null group by email order by cldr_users.email";
                        JSONObject query = DBUtils.queryToCachedJSON(what, 3600 * 1000, sql); // update hourly
                        r.put(what, query);
                        addGeneralStats(r);
                        send(r,out);
                    } else {
                        sendError(out, "Unknown Session-based Request: " + what, ErrorCode.E_INTERNAL);
                    }
                }
            } else if (what.equals("locmap")) {
                final JSONWriter r = newJSONStatusQuick(sm);
                r.put("locmap", getJSONLocMap(sm));
                send(r, out);
            } else {
                sendError(out, "Unknown Request: " + what, ErrorCode.E_INTERNAL);
            }
        } catch (SurveyException e) {
            SurveyLog.logException(e, "Processing: " + what);
            sendError(out, e);
        } catch (JSONException e) {
            SurveyLog.logException(e, "Processing: " + what);
            sendError(out, "JSONException: " + e, ErrorCode.E_INTERNAL);
        } catch (SQLException e) {
            SurveyLog.logException(e, "Processing: " + what);
            sendError(out, "SQLException: " + e, ErrorCode.E_INTERNAL);
        }
    }

    /**
     * Get an integer parameter, with a default
     * @param request
     * @param fieldName
     * @param defVal
     * @return
     */
    private int getIntParameter(HttpServletRequest request, String fieldName, int defVal) {
        final Integer v = getIntParameter(request, fieldName);
        if(v == null) {
            return defVal;
        } else {
            return v;
        }
    }

    /**
     * Get an integer value. Return null if missing.
     * @param request
     * @param fieldName
     * @return
     */
    private Integer getIntParameter(HttpServletRequest request, final String fieldName) {
        final String replyToString = request.getParameter(fieldName);
        if(!replyToString.isEmpty()) {
            return Integer.parseInt(replyToString);
        }
        return null;
    }

    private void assertCanUseVettingSummary(CookieSession mySession) throws SurveyException {
        assertHasUser(mySession);
        if( !UserRegistry.userCanUseVettingSummary(mySession.user)) {
            throw new SurveyException(ErrorCode.E_NO_PERMISSION);
        }
    }

    /**
     * Throw an exception if the user isn't TC level
     * @param mySession
     * @throws SurveyException
     */
    public void assertIsTC(CookieSession mySession) throws SurveyException {
        if(!UserRegistry.userIsTC(mySession.user)) {
            throw new SurveyException(ErrorCode.E_NO_PERMISSION);
        }
    }

    /**
     * Throw an exception if the user isn't logged in.
     * @param mySession
     * @throws SurveyException
     */
    public void assertHasUser(CookieSession mySession) throws SurveyException {
        if(mySession.user==null) {
            throw new SurveyException(ErrorCode.E_NOT_LOGGED_IN);
        }
    }

    /**
     * @param r
     */
    public void addGeneralStats(JSONWriter r) {
        r.put("total_items",StatisticsUtils.getTotalItems());
        r.put("total_new_items",StatisticsUtils.getTotalNewItems());
        r.put("total_submitters",StatisticsUtils.getTotalSubmitters());
        r.put("time_now",System.currentTimeMillis());
    }

    private JSONArray searchResults(String q, CLDRLocale l, CookieSession mySession) {
        JSONArray results = new JSONArray();

        if (q != null) {
            if (l == null) {
                searchLocales(results, q, mySession);
            } else {
                //ElapsedTimer et = new ElapsedTimer("search for " + q);
                // try as xpath
                searchXPath(results, l, q, mySession);

                // try PH substring
                searchPathheader(results, l, q, mySession);
                //System.err.println("Done searching for " + et);
            }
        }

        return results;
    }

    private void searchLocales(JSONArray results, String q, CookieSession mySession) {
        for (CLDRLocale l : mySession.sm.getLocales()) {
            if (l.getBaseName().equalsIgnoreCase(q) ||
                l.getDisplayName().toLowerCase().contains(q.toLowerCase()) ||
                l.toLanguageTag().toLowerCase().equalsIgnoreCase(q)) {
                try {
                    results.put(new JSONObject().put("loc", l.getBaseName()));
                } catch (JSONException e) {
                    //
                }
            }
        }
    }

    private void searchPathheader(JSONArray results, CLDRLocale l, String q, CookieSession mySession) {
        if (l == null) return; // don't search with no locale
        try {
            PathHeader.PageId page = PathHeader.PageId.valueOf(q);
            if (page != null) {
                results.put(new JSONObject().put("page", page.name())
                    .put("section", page.getSectionId().name()));
            }
        } catch (Throwable t) {
            //
        }
        try {
            PathHeader.SectionId section = PathHeader.SectionId.valueOf(q);
            if (section != null) {
                results.put(new JSONObject().put("section", section.name()));
            }
        } catch (Throwable t) {
            //
        }

        // substring search
        Set<PathHeader> resultPh = new TreeSet<PathHeader>();

        if (new UnicodeSet("[:Letter:]").containsSome(q)) {
            // check English
            Set<String> retrievedPaths = new HashSet<String>();
            mySession.sm.getBaselineFile().getPathsWithValue(q, "", null, retrievedPaths);
            final STFactory stFactory = mySession.sm.getSTFactory();
            if (l != null) {
                stFactory.make(l, true).getPathsWithValue(q, "", null, retrievedPaths);
            }
            for (String xp : retrievedPaths) {
                PathHeader ph = stFactory.getPathHeader(xp);
                if (ph != null) {
                    resultPh.add(ph);
                }
            }
        }
        // add any others
        CoverageInfo covInfo= CLDRConfig.getInstance().getCoverageInfo();
        for (PathHeader ph : resultPh) {
            try {
                final String originalPath = ph.getOriginalPath();
                if (ph.getSectionId() != PathHeader.SectionId.Special &&
                    covInfo.getCoverageLevel(originalPath, l.getBaseName()).getLevel() <= 100) {
                    results.put(new JSONObject()
                        .put("xpath", originalPath)
                        .put("strid", mySession.sm.xpt.getStringIDString(originalPath))
                        .put("ph", JSONWriter.wrap(ph)));
                }
            } catch (JSONException e) {
                //
            }
        }
    }

    private void searchXPath(JSONArray results, CLDRLocale l2, String q, CookieSession mySession) {
        // is it a stringid?
        try {
            Long l = Long.parseLong(q, 16);
            if (l != null && Long.toHexString(l).equalsIgnoreCase(q)) {
                String x = mySession.sm.xpt.getByStringID(q);
                if (x != null) {
                    results.put(new JSONObject()
                        .put("xpath", x)
                        .put("strid", mySession.sm.xpt.getStringIDString(x))
                        .put("ph", JSONWriter.wrap(mySession.sm.getSTFactory().getPathHeader(x))));
                }
            }
        } catch (Throwable t) {
            //
        }

        // is it a full XPath?
        try {
            final String xp = mySession.sm.xpt.xpathToBaseXpath(q);
            if (mySession.sm.xpt.peekByXpath(xp) != XPathTable.NO_XPATH) {
                PathHeader ph = mySession.sm.getSTFactory().getPathHeader(xp);
                if (ph != null) {
                    results.put(new JSONObject()
                        .put("xpath", xp)
                        .put("strid", mySession.sm.xpt.getStringIDString(xp))
                        .put("ph", JSONWriter.wrap(ph)));
                }
            }
        } catch (Throwable t) {
            //
        }
    }

    /**
     * @return
     */
    public String getOldVotesPref() {
        String oldVotesPref = DBUtils.appendVersionString(new StringBuilder("oldVoteRemind")).toString(); // version the preference
        return oldVotesPref;
    }

    /**
     * Validate a locale. Prints standardized error if not found.
     * @param sm
     * @param out
     * @param loc
     * @return
     * @throws IOException 
     */
    public static CLDRLocale validateLocale(PrintWriter out, String loc) throws IOException {
        CLDRLocale ret;
        if (CookieSession.sm == null || CookieSession.sm.isSetup == false) {
            sendNoSurveyMain(out);
            return null;
        }
        if (loc == null || loc.isEmpty() || (ret = CLDRLocale.getInstance(loc)) == null || !SurveyMain.getLocalesSet().contains(ret)) {
            JSONWriter r = newJSON();
            r.put("err", "Bad locale code:" + loc);
            r.put("loc", loc);
            r.put("err_code", ErrorCode.E_BAD_LOCALE);
            send(r, out);
            return null; // failed
        } else {
            return CLDRLocale.getInstance(loc);
        }
    }

    private static JSONObject createJSONLocMap(SurveyMain sm) throws JSONException {
        JSONObject locmap = new JSONObject();
        // locales will have info about each locale, including name
        JSONObject locales = new JSONObject();
        SupplementalDataInfo sdi = sm.getSupplementalDataInfo();

        Factory disk = sm.getDiskFactory();
        
        for (CLDRLocale loc : SurveyMain.getLocales()) {
            JSONObject locale = new JSONObject();

            locale.put("name", loc.getDisplayName());
            if (loc.getCountry() != null) {
                locale.put("name_rgn", loc.getDisplayRegion());
            }
            if (loc.getVariant() != null) {
                locale.put("name_var", loc.getDisplayVariant());
            }
            locale.put("bcp47", loc.toLanguageTag());

            HTMLDirection dir = sm.getHTMLDirectionFor(loc);
            if (!dir.equals("ltr")) {
                locale.put("dir", dir);
            }

            CLDRLocale dcParent = sdi.getBaseFromDefaultContent(loc);
            CLDRLocale dcChild = sdi.getDefaultContentFromBase(loc);
            locale.put("parent", loc.getParent());
            locale.put("highestParent", loc.getHighestNonrootParent());
            locale.put("dcParent", dcParent);
            locale.put("dcChild", dcChild);
            locale.put("type", Factory.getSourceTreeType(disk.getSourceDirectoryForLocale(loc.getBaseName())));
            if (SurveyMain.getReadOnlyLocales().contains(loc)) {
                locale.put("readonly", true);
                String comment = SpecialLocales.getComment(loc);
                locale.put("readonly_why", comment);
                String commentraw = SpecialLocales.getCommentRaw(loc);
                locale.put("readonly_why_raw", commentraw);
            } else if (dcParent != null) {
                locale.put("readonly", true);
            }

            JSONArray subLocales = new JSONArray();
            Map<String, CLDRLocale> subLocList = sm.getLocaleTree().getSubLocales(loc);
            if (subLocList != null && !subLocList.isEmpty()) {
                for (CLDRLocale l : subLocList.values()) {
                    subLocales.put(l.getBaseName());
                }
                if (subLocales.length() > 0) {
                    locale.put("sub", subLocales);
                }
            }
            locales.put(loc.getBaseName(), locale); // note, this is in sorted (baseline) order.
        }

        locmap.put("locales", locales);
        locmap.put("surveyBaseLocale", SurveyMain.BASELINE_LOCALE);
        JSONArray topLocales = new JSONArray();
        for (CLDRLocale l : sm.getLocaleTree().getTopCLDRLocales()) {
            topLocales.put(l.getBaseName());
        }
        locmap.put("topLocales", topLocales);

        // map non-canonicalids to localeids
        //JSONObject idmap = new JSONObject();
        //locmap.put("idmap", idmap);
        return locmap;
    }

    private static JSONObject gLocMap = null;

    private static synchronized JSONObject getJSONLocMap(SurveyMain sm) throws JSONException {
        if (gLocMap == null) {
            ElapsedTimer et = new ElapsedTimer("SurveyAjax.getJSONLocMap: created JSON locale map ");
            gLocMap = createJSONLocMap(sm);
            System.err.println(et.toString() + " - serializes to: " + gLocMap.toString().length() + "chars.");
        }
        return gLocMap;
    }

    private String escapeString(String val) {
        StringBuilder ret = new StringBuilder(val.replaceAll("\u00a0", "U+00A0"));
        ret.insert(0, '\u201c');
        ret.append("\u201d,len=").append("" + val.length());
        return ret.toString();
    }

    public static boolean has(List<CheckStatus> result, CheckStatus.Type type) {
        if (result != null) {
            for (CheckStatus s : result) {
                if (s.getType().equals(type)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean hasWarnings(List<CheckStatus> result) {
        return (has(result, CheckStatus.warningType));
    }

    public static boolean hasErrors(List<CheckStatus> result) {
        return (has(result, CheckStatus.errorType));
    }

    /**
     * @param sm
     * @param locale
     * @param r
     */
    private void setLocaleStatus(SurveyMain sm, String locale, JSONWriter r) {
        if (locale != null && locale.length() > 0 && SurveyMain.isBusted == null && SurveyMain.isSetup) {
            CLDRLocale loc = CLDRLocale.getInstance(locale);
            if (loc != null && SurveyMain.getLocalesSet().contains(loc)) {
                r.put("localeStampName", loc.getDisplayName());
                r.put("localeStampId", loc);
                r.put("localeStamp", sm.getSTFactory().mintLocaleStamp(loc).current());
            }
        }
    }

    private void setupStatus(SurveyMain sm, JSONWriter r) {
        r.put("SurveyOK", "1");
        // r.put("progress", sm.getTopBox(false));
        try {
            r.put("status", sm.statusJSON());
        } catch (JSONException e) {
            SurveyLog.logException(e, "getting status");
        }
    }

    private JSONWriter newJSONStatus(SurveyMain sm) {
        JSONWriter r = newJSON();
        setupStatus(sm, r);
        return r;
    }

    private JSONWriter newJSONStatusQuick(SurveyMain sm) {
        JSONWriter r = newJSON();
        if (sm.isSetup && !sm.isBusted()) {
            r.put("SurveyOK", "1");
            r.put("isSetup", "1");
            r.put("isBusted", "0");
        }
        return r;
    }

    private static JSONWriter newJSON() {
        JSONWriter r = new JSONWriter();
        r.put("progress", "(obsolete-progress)");
        r.put("visitors", "");
        r.put("uptime", "");
        r.put("err", "");
        r.put("SurveyOK", "0");
        r.put("isSetup", "0");
        r.put("isBusted", "0");
        return r;

    }

    private static void sendNoSurveyMain(PrintWriter out) throws IOException {
        JSONWriter r = newJSON();
        r.put("SurveyOK", "0");
        r.put("err", "The SurveyTool has not yet started.");
        r.put("err_code", ErrorCode.E_NOT_STARTED);
        send(r, out);
    }

    private void sendError(PrintWriter out, String message, ErrorCode errCode) throws IOException {
        JSONWriter r = newJSON();
        r.put("SurveyOK", "0");
        r.put("err", message);
        r.put("err_code", errCode);
        send(r, out);
    }

    private void sendError(PrintWriter out, Throwable e) throws IOException {
        if(e instanceof SurveyException) {
            sendError(out, (SurveyException)e);
        } else {
            JSONWriter r = newJSON();
            r.put("SurveyOK", "0");
            r.put("err", e.toString());
            r.put("err_code", SurveyException.ErrorCode.E_INTERNAL);
            send(r, out);
        }
    }

    private void sendError(PrintWriter out, SurveyException e) throws IOException {
        JSONWriter r = newJSON();
        r.put("SurveyOK", "0");
        r.put("err", e.getMessage());
        r.put("err_code", e.getErrCode());
        try {
            e.addDataTo(r);
        } catch (JSONException e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
        }
        send(r, out);
    }


    private static void send(JSONWriter r, PrintWriter out) throws IOException {
        out.print(r.toString());
    }

    public enum AjaxType {
        STATUS, VERIFY
    };

    /**
     * Helper function for getting the basic AJAX status script included.
     */
    public static void includeAjaxScript(HttpServletRequest request, HttpServletResponse response, AjaxType type)
        throws ServletException, IOException {
        WebContext.includeFragment(request, response, "ajax_" + type.name().toLowerCase() + ".jsp");
    }

}
