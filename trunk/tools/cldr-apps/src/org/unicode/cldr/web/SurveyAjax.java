package org.unicode.cldr.web;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.json.JSONException;
import org.json.JSONObject;
import org.unicode.cldr.test.CheckCLDR;
import org.unicode.cldr.test.CheckCLDR.CheckStatus;
import org.unicode.cldr.test.CheckCLDR.CheckStatus.Subtype;
import org.unicode.cldr.test.CheckCLDR.StatusAction;
import org.unicode.cldr.test.DisplayAndInputProcessor;
import org.unicode.cldr.test.TestCache.TestResultBundle;
import org.unicode.cldr.util.CLDRConfig;
import org.unicode.cldr.util.CLDRFile;
import org.unicode.cldr.util.CLDRInfo.CandidateInfo;
import org.unicode.cldr.util.CLDRInfo.PathValueInfo;
import org.unicode.cldr.util.CLDRInfo.UserInfo;
import org.unicode.cldr.util.CLDRLocale;
import org.unicode.cldr.util.Level;
import org.unicode.cldr.util.PathHeader;
import org.unicode.cldr.util.PathHeader.SurveyToolStatus;
import org.unicode.cldr.util.SupplementalDataInfo;
import org.unicode.cldr.util.VoteResolver;
import org.unicode.cldr.web.DataSection.DataRow;


/**
 * Servlet implementation class SurveyAjax
 * 
 * URL/JSON Usage:  
 *   get/set prefs
 *   
 *     to get a preference:
 *       http://...../SurveyAjax?s=YOURSESSIONID&what=pref&pref=dummy
 *       will reply with JSON of  {... "pref":"dummy","_v":"true" ...}
 *     to set a preference:
 *       http://...../SurveyAjax?s=YOURSESSIONID&what=pref&pref=dummy&_v=true
 *       ( can also use a POST instead of the _v parameter )
 *     Note, add the preference to the settablePrefsList
 * 
 */
public class SurveyAjax extends HttpServlet {
    final boolean DEBUG = true||SurveyLog.isDebug();
    public final static String WHAT_MY_LOCALES ="mylocales";
    /**
     * Consolidate my JSONify functions here.
     * @author srl
     *
     */
    public static final class JSONWriter {
        private final JSONObject j = new JSONObject();

        public JSONWriter() {
        }

        public final void put(String k, Object v) {
            try {
                j.put(k, v);
            } catch (JSONException e) {
                throw new IllegalArgumentException(e.toString(),e);
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
                    if(cs.getCause()!=null) {
                        put("cause", wrap(cs.getCause()));
                    }
                    if(cs.getSubtype()!=null) {
                        put("subType", cs.getSubtype().name());
                    }
 //                   put("parameters",new JSONArray(cs.getParameters()).toString());    // NPE.
                }
            };
        }

        public static JSONObject wrap(UserRegistry.User u) throws JSONException {
        	return new JSONObject()
        	.put("id", u.id)
        	.put("email", u.email)
        	.put("name", u.name)
        	.put("userlevel", u.userlevel)
        	.put("userlevelname", u.computeVRLevel());
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
        	if(list==null || list.isEmpty()) return null;
            List<Object> newList = new ArrayList<Object>();
            for(final CheckStatus cs : list) {
                newList.add(wrap(cs));
            }
            return newList;
        }

        public static JSONObject wrap(final VoteResolver<String> r) throws JSONException {
            JSONObject ret = new JSONObject()
                .put("raw",r.toString())
                .put("isDisputed", r.isDisputed())
                .put("isEstablished", r.isEstablished())
                .put("lastReleaseStatus",  r.getLastReleaseStatus())
                .put("winningValue", r.getWinningValue())
                .put("lastReleaseValue",  r.getLastReleaseValue())
                .put("winningStatus", r.getWinningStatus());
            
                EnumSet<VoteResolver.Organization> conflictedOrgs = r.getConflictedOrganizations();
                JSONObject orgs = new JSONObject();
                for(VoteResolver.Organization o:VoteResolver.Organization.values()) {
                    String orgVote = r.getOrgVote(o);
                    if(orgVote==null) continue;
        
                    Map<String,Long> votes = r.getOrgToVotes(o);
                    
                    JSONObject org = new JSONObject()
                        .put("status",r.getStatusForOrganization(o))
                        .put("orgVote", orgVote)
                        .put("votes", votes);
                    if(conflictedOrgs.contains(org)) {
                        org.put("conflicted", true);
                    }
                    orgs.put(o.name(), org);
                }
                ret.put("orgs", orgs);
                return ret;
        }
    }


    private static final long serialVersionUID = 1L;
    public static final String REQ_WHAT = "what";
    public static final String REQ_SESS = "s";
    public static final String WHAT_STATUS = "status";
    public static final String AJAX_STATUS_SCRIPT = "ajax_status.jspf";
    public static final String WHAT_VERIFY = "verify";
    public static final String WHAT_SUBMIT= "submit";
    public static final String WHAT_GETROW= "getrow";
    public static final String WHAT_PREF = "pref";
    public static final String WHAT_GETVV = "vettingviewer";
    private static final Object WHAT_STATS_BYDAY = "stats_byday";
    private static final Object WHAT_RECENT_ITEMS = "recent_items";

    String settablePrefsList[] = { SurveyMain.PREF_CODES_PER_PAGE, "dummy" }; // list of prefs OK to get/set


    private Set<String> prefsList = new HashSet<String>();
    /**
     * @see HttpServlet#HttpServlet()
     */
    public SurveyAjax() {
        super();

        for(String p : settablePrefsList) {
            prefsList.add(p);
        }
    }


    /**
     * @see HttpServlet#doPost(HttpServletRequest request, HttpServletResponse response)
     */
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        setup(request,response);
        StringBuilder sb = new StringBuilder();
        Reader r = request.getReader();
        int ch;
        while((ch = r.read())>-1) {
            if (DEBUG) System.err.println(" POST >> " + Integer.toHexString(ch));
            sb.append((char)ch);
        }		
        processRequest(request, response, sb.toString());
    }

    /**
     * @see HttpServlet#doGet(HttpServletRequest request, HttpServletResponse response)
     */
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {	
        setup(request,response);
        processRequest(request,response,WebContext.decodeFieldString(request.getParameter(SurveyMain.QUERY_VALUE_SUFFIX)));
    }

    private void setup(HttpServletRequest request, HttpServletResponse response)  throws ServletException, IOException  {
        request.setCharacterEncoding("UTF-8");
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");
    }

    private void processRequest(HttpServletRequest request, HttpServletResponse response, String val)  throws ServletException, IOException {
        //		if(val != null) {
        //			System.err.println("val="+val);
        //		}
        SurveyMain sm = SurveyMain.getInstance(request);
        PrintWriter out = response.getWriter();
        String what = request.getParameter(REQ_WHAT);
        String sess = request.getParameter(SurveyMain.QUERY_SESSION);
        String loc = request.getParameter(SurveyMain.QUERY_LOCALE);
        CLDRLocale l = null;
        if(loc!=null&&!loc.isEmpty()) {
            l  = CLDRLocale.getInstance(loc);
        }
        String xpath = request.getParameter(SurveyForum.F_XPATH);
        String vhash = request.getParameter("vhash");
        String fieldHash = request.getParameter(SurveyMain.QUERY_FIELDHASH);
        CookieSession mySession = null;
        try {
            if(sm == null) {
                sendNoSurveyMain(out);
            } else if(what==null) {
                sendError(out, "Missing parameter: " + REQ_WHAT);
            } else if(what.equals(WHAT_STATS_BYDAY)) {
                JSONWriter r = newJSONStatus(sm);
                JSONObject query = DBUtils.queryToJSON("select count(*) as count ,last_mod from cldr_votevalue group by Year(last_mod) desc ,Month(last_mod) desc,Date(last_mod) desc");
                r.put("byday",query);
                send(r,out);
            } else if(what.equals(WHAT_MY_LOCALES)) {
                JSONWriter r = newJSONStatus(sm);
                String q1 = "select count(*) as count, cldr_votevalue.locale as locale from cldr_votevalue WHERE cldr_votevalue.submitter=? AND cldr_votevalue.value is not NULL group by cldr_votevalue.locale order by cldr_votevalue.locale desc";
                String user = request.getParameter("user");
                UserRegistry.User u = null;
                if(user!=null&&!user.isEmpty()) try {
                    u = sm.reg.getInfo(Integer.parseInt(user));
                } catch(Throwable t) {
                    SurveyLog.logException(t, "Parsing user " + user);
                }
                
                JSONObject query;
                query = DBUtils.queryToJSON(q1, u.id );
                r.put("mine",query);
                send(r,out);
            } else if(what.equals(WHAT_RECENT_ITEMS)) {
                JSONWriter r = newJSONStatus(sm);
                int limit = 15;
                try {
                    limit = Integer.parseInt(request.getParameter("limit"));
                } catch (Throwable t) {
                    limit = 15;
                }
                String q1 = "select cldr_votevalue.locale,cldr_xpaths.xpath,cldr_users.org, cldr_votevalue.value, cldr_votevalue.last_mod  from cldr_xpaths,cldr_votevalue,cldr_users  where ";
                String q2 = "cldr_xpaths.id=cldr_votevalue.xpath and cldr_users.id=cldr_votevalue.submitter  order by cldr_votevalue.last_mod desc limit ?";
                String user = request.getParameter("user");
                UserRegistry.User u = null;
                if(user!=null&&!user.isEmpty()) try {
                    u = sm.reg.getInfo(Integer.parseInt(user));
                } catch(Throwable t) {
                    SurveyLog.logException(t, "Parsing user " + user);
                }
                
                JSONObject query;
                if(l==null && u==null ) {
                    query = DBUtils.queryToJSON(q1+q2, limit );
                } else if(u==null && l!=null) {
                    query = DBUtils.queryToJSON(q1+" cldr_votevalue.locale=? AND " + q2, l,limit );
                } else if(u!=null && l==null) {
                    query = DBUtils.queryToJSON(q1+" cldr_votevalue.submitter=? AND " + q2, u.id ,limit);
                } else {
                    query = DBUtils.queryToJSON(q1+" cldr_votevalue.locale=? AND cldr_votevalue.submitter=? " + q2, l, u.id,limit );
                }
                r.put("recent",query);
                send(r,out);
            } else if(what.equals(WHAT_STATUS)) {
                sendStatus(sm,out,loc);
            } else if(sess!=null && !sess.isEmpty()) { // this and following: session needed
                mySession = CookieSession.retrieve(sess);
                if(mySession==null) {
                    sendError(out, "Missing Session: " + sess);
                } else {
//                    if(what.equals(WHAT_GETROW)) {
//                        int id = Integer.parseInt(xpath);
//                        String xp = sm.xpt.getById(id);
//                        
//
//;
//                        boolean dataEmpty = false;
//                        boolean zoomedIn = request.getParameter("zoomedIn")!=null&&request.getParameter("zoomedIn").length()>0;
//                        JSONWriter r = newJSONStatus(sm);
//                        synchronized(mySession) {
//                            CLDRLocale locale = CLDRLocale.getInstance(loc);
//                            SurveyMain.UserLocaleStuff uf =  mySession.sm.getUserFile(mySession, locale);
//                            DataSection section = DataSection.make(null, mySession, locale, xp, false,Level.COMPREHENSIVE.toString());
//                           // r.put("testResults", JSONWriter.wrap(result));
//                            //r.put("testsRun", cc.toString());
//                            DataRow row = section.getDataRow(xp);
//                            if(row!=null) {
//                                CheckCLDR cc = sm.createCheckWithoutCollisions();
//                                StringWriter sw = new StringWriter();
//                                WebContext sctx = new WebContext(sw);
//                                sctx.session=mySession;
//                                row.showDataRow(sctx, uf, true, cc, zoomedIn, DataSection.kAjaxRows);
//                                sctx.flush();
//                                sctx.close();
//                                r.put("rowHtml",sw.toString());
//                            }
//                        }
//                        r.put("locTested", loc);
//                        r.put("xpathTested", xp);
//                        send(r,out);
                    //} else 
                   if(what.equals(WHAT_VERIFY) || what.equals(WHAT_SUBMIT)) {
                       CLDRLocale locale = CLDRLocale.getInstance(loc);
                        Map<String,String> options = DataSection.getOptions(null, mySession, locale);
                        STFactory stf = sm.getSTFactory();
                        TestResultBundle cc = stf.getTestResult(locale, options);
                        int id = Integer.parseInt(xpath);
                        String xp = sm.xpt.getById(id);
                        final List<CheckStatus> result = new ArrayList<CheckStatus>();
                        //CLDRFile file = CLDRFile.make(loc);
                        //CLDRFile file = mySession.
                        SurveyMain.UserLocaleStuff uf = null;
                        boolean dataEmpty = false;
                        JSONWriter r = newJSONStatus(sm);
                        synchronized(mySession) {
                            try {
                                BallotBox<UserRegistry.User> ballotBox = sm.getSTFactory().ballotBoxForLocale(locale);
                                boolean foundVhash = false;
                                Exception[] exceptionList = new Exception[1];
                                String otherErr = null;
                                String origValue = val;
                                if(vhash!=null && vhash.length()>0) {
                                    if(vhash.equals("null")) {
                                        val = null;
                                        foundVhash=true;
                                    } else {
//                                        String newValue = null;
//                                        for(String s : ballotBox.getValues(xp)) {
//                                            if(vhash.equals(DataSection.getValueHash(s))) {
//                                                val = newValue = s;
//                                                foundVhash=true;
//                                            }
//                                        }
//                                        if(newValue == null) {
//                                            sendError(out, "Missing value hash: " + vhash);
//                                            return;
//                                        }
                                        
                                        val = DataSection.fromValueHash(vhash);
//                                        System.err.println("'"+vhash+"' -> '"+val+"'");
                                        foundVhash=true;
                                    }
                                } else {
                                    if(val!=null) {
                                        if(DEBUG) System.err.println("val WAS "+escapeString(val));
                                        DisplayAndInputProcessor daip = new DisplayAndInputProcessor(locale);
                                        val = daip.processInput(xp, val, exceptionList);
                                        if(DEBUG) System.err.println("val IS "+escapeString(val));
                                        if(val.isEmpty()) {
                                            otherErr = ("DAIP returned a 0 length string");
                                        }
                                    }
                                }
                                
                                if(val!=null && !foundVhash) {
                                    uf = sm.getUserFile(mySession, locale);
                                    CLDRFile file = uf.cldrfile;
                                    cc.check(xp,result, val);
                                    dataEmpty = file.isEmpty();
                                }
                            
                                r.put(SurveyMain.QUERY_FIELDHASH, fieldHash);
        
                                if(exceptionList[0]!=null) {
                                    result.add(new CheckStatus().setMainType(CheckStatus.errorType).setSubtype(Subtype.internalError)
                                            .setMessage("Input Processor Exception: {0}")
                                            .setParameters(exceptionList));
                                    SurveyLog.logException(exceptionList[0],"DAIP, Processing "+loc+":"+xp+"='"+val+"' (was '"+origValue+"')");
                                }
                                
                                if(otherErr!=null) {
                                    String list[] = { otherErr };
                                    result.add(new CheckStatus().setMainType(CheckStatus.errorType).setSubtype(Subtype.internalError)
                                            .setMessage("Input Processor Error: {0}")
                                            .setParameters(list));
                                    SurveyLog.logException(null,"DAIP, Processing "+loc+":"+xp+"='"+val+"' (was '"+origValue+"'): "+otherErr);
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
                                
                                if(what.equals(WHAT_SUBMIT)) {
                                    if(!UserRegistry.userCanModifyLocale(mySession.user,locale)) {
                                        throw new InternalError("User cannot modify locale.");
                                    }

                                    SupplementalDataInfo sdi = mySession.sm.getSupplementalDataInfo();
                                    int coverageValue = sdi.getCoverageValue(xp, locale.toULocale()); // TODO: get from DataRow
                                    PathHeader ph = stf.getPathHeader(xp);
                                    CheckCLDR.Phase cPhase = CLDRConfig.getInstance().getPhase();
                                    SurveyToolStatus phStatus = ph.getSurveyToolStatus();
                                    Level covLev = org.unicode.cldr.util.Level.fromLevel(coverageValue);

                                    final String candVal = val;

                                    DataSection section = DataSection.make(null,null,mySession, locale, xp, null, false,Level.COMPREHENSIVE.toString());
                                    section.setUserAndFileForVotelist(mySession.user, null);

                                    DataRow pvi = section.getDataRow(xp);
                                    CheckCLDR.StatusAction showRowAction = pvi.getStatusAction();

                                    if(otherErr!=null) {
                                        r.put("didNotSubmit","Did not submit.");
                                    } else if(!showRowAction.isForbidden()) {
                                        CandidateInfo ci;
                                        if(candVal==null) {
                                            ci = null; // abstention
                                        } else {
                                            ci = pvi.getItem(candVal);  // existing item?
                                            if(ci==null) { // no, new item
                                                ci = new CandidateInfo() {
                                                    @Override
                                                    public String getValue() {
                                                        return candVal;
                                                    }
    
                                                    @Override
                                                    public Collection<UserInfo> getUsersVotingOn() {
                                                        return Collections.emptyList(); // No users voting - yet.
                                                    }
    
                                                    @Override
                                                    public List<CheckStatus> getCheckStatusList() {
                                                        return result;
                                                    }
                                                };
                                            }
                                        }
                                        CheckCLDR.StatusAction statusActionNewItem =   cPhase.getAcceptNewItemAction(ci, pvi, CheckCLDR.InputMethod.DIRECT, phStatus, mySession.user);
                                        if(statusActionNewItem.isForbidden()) {
                                            r.put("statusAction", statusActionNewItem);
                                            if(DEBUG) System.err.println("Not voting: ::  " + statusActionNewItem);
                                        } else {
                                            if(DEBUG) System.err.println("Voting for::  " + val);
                                            ballotBox.voteForValue(mySession.user, xp, val);
                                            String subRes = ballotBox.getResolver(xp).toString();
                                            if(DEBUG) System.err.println("Voting result ::  " + subRes);
                                            r.put("submitResultRaw", subRes);
                                        }
                                    } else {
                                        if(DEBUG) System.err.println("Not voting: ::  " + showRowAction);
                                        r.put("statusAction", showRowAction);
                                    }
                                    // don't allow adding items if ALLOW_VOTING_BUT_NO_ADD

                                    // informational
                                    r.put("cPhase",cPhase);
                                    r.put("phStatus",phStatus);
                                    r.put("covLev", covLev);
                                }
                            } catch(Throwable t) {
                                SurveyLog.logException(t,"Processing submission " + locale + ":" + xp);
                                r.put("err","Exception: " + t.toString());
                            } finally {
                                if(uf!=null) uf.close();
                            }
                        }
                        
                        send(r,out);
                    } else if(what.equals(WHAT_PREF)) {
                        String pref = request.getParameter("pref");


                        JSONWriter r = newJSONStatus(sm);
                        r.put("pref",pref);

                        if(!prefsList.contains(pref)) {
                            sendError(out, "Bad or unsupported pref: " + pref);
                        }

                        if(val != null && !val.isEmpty()) {
                            mySession.settings().set(pref,val);
                        }
                        r.put(SurveyMain.QUERY_VALUE_SUFFIX,mySession.settings().get(pref,null));
                        send(r,out);
                    } else if(what.equals(WHAT_GETVV)) {
                        JSONWriter r = newJSONStatus(sm);
                        r.put("what", what);

                        CLDRLocale locale = CLDRLocale.getInstance(loc);

                        VettingViewerQueue.Status status[] = new VettingViewerQueue.Status[1];
                        String str = VettingViewerQueue.getInstance().getVettingViewerOutput(null,mySession,locale,status,VettingViewerQueue.LoadingPolicy.NOSTART,null);

                        r.put("status", status[0]);
                        r.put("ret", str);

                        send(r,out);
                    }
                }
            } else {
                sendError(out,"Unknown Request: " + what);
            }
        } catch (JSONException e) {
            SurveyLog.logException(e, "Processing: " + what);
            sendError(out, "JSONException: " + e);
        } catch (SQLException e) {
            SurveyLog.logException(e, "Processing: " + what);
            sendError(out, "SQLException: " + e);
        }
    }

    private String escapeString(String val) {
        StringBuilder ret =  new StringBuilder(val.replaceAll("\u00a0", "U+00A0"));
        ret.insert(0,'\u201c');
        ret.append("\u201d,len=")
           .append(""+val.length());
        return ret.toString();
    }


    public static  boolean has(List<CheckStatus>result, String type) {
    	if(result!=null) {
    		for(CheckStatus s : result) {
	    		if(s.getType().equals(type)) {
	    			return true;
	    		}
    		}
    	}
    	return false;
    }
    
    public static boolean hasWarnings(List<CheckStatus> result) {
    	return(has(result,CheckStatus.warningType));
	}


	public static boolean hasErrors(List<CheckStatus> result) {
    	return(has(result,CheckStatus.errorType));
	}


	private void sendStatus(SurveyMain sm, PrintWriter out, String locale) throws IOException {
        JSONWriter r = newJSONStatus(sm);
        //        StringBuffer progress = new StringBuffer(sm.getProgress());
        //        String threadInfo = sm.startupThread.htmlStatus();
        //        if(threadInfo!=null) {
        //            progress.append("<br/><b>Processing:"+threadInfo+"</b><br>");
        //        }
        //r.put("progress", progress.toString());
        
        setLocaleStatus(sm, locale, r);
        send(r,out);
    }


    /**
     * @param sm
     * @param locale
     * @param r
     */
    private void setLocaleStatus(SurveyMain sm, String locale, JSONWriter r) {
        if(locale!=null&&
                locale.length()>0&&
                sm.isBusted==null&&
                sm.isSetup) {
            CLDRLocale loc = CLDRLocale.getInstance(locale);
            if(loc!=null && SurveyMain.getLocalesSet().contains(loc)) {
                r.put("localeStampName", loc.getDisplayName());
                r.put("localeStamp", sm.getSTFactory().stampForLocale(loc).current());
            }
        }
    }

    private void setupStatus(SurveyMain sm, JSONWriter r) {
        r.put("SurveyOK","1");
//        r.put("progress", sm.getTopBox(false));
        try {
			r.put("status", sm.statusJSON());
		} catch (JSONException e) {
			SurveyLog.logException(e,"getting status");
		}
    }

    private JSONWriter newJSONStatus(SurveyMain sm) {
        JSONWriter r = newJSON();
        setupStatus(sm, r);
        return r;
    }

    private JSONWriter newJSON() {
        JSONWriter r = new JSONWriter();
        r.put("progress", "(obsolete-progress)");
        r.put("visitors", "");
        r.put("uptime", "");
        r.put("err", "");
        r.put("SurveyOK","0");
        r.put("isSetup","0");
        r.put("isBusted","0");
        return r;
        
    }

    private void sendNoSurveyMain(PrintWriter out) throws IOException {
        JSONWriter r = newJSON();
        r.put("SurveyOK","0");
        r.put("err","The Survey Tool is awaiting the first visitor.");
        send(r,out);
    }

    private void sendError(PrintWriter out, String string) throws IOException {
        JSONWriter r = newJSON();
        r.put("SurveyOK","0");
        r.put("err",string);
        send(r,out);
    }

    private void send(JSONWriter r, PrintWriter out) throws IOException {
        out.print(r.toString());
    }

    public enum AjaxType { STATUS, VERIFY  };

    /**
     * Helper function for getting the basic AJAX status script included.
     */
    public static void includeAjaxScript(HttpServletRequest request, HttpServletResponse response, AjaxType type) throws ServletException, IOException
    {
        WebContext.includeFragment(request, response, "ajax_"+type.name().toLowerCase()+".jsp");
    }

}
