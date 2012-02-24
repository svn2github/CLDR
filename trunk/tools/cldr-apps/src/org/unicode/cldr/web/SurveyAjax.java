package org.unicode.cldr.web;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
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
import org.unicode.cldr.test.DisplayAndInputProcessor;
import org.unicode.cldr.util.CLDRFile;
import org.unicode.cldr.util.CLDRLocale;


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
                }
            };
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

        public static List<Object> wrap(List<CheckStatus> list) throws JSONException {
            List<Object> newList = new ArrayList<Object>();
            for(final CheckStatus cs : list) {
                newList.add(wrap(cs));
            }
            return newList;
        }
    }


    private static final long serialVersionUID = 1L;
    public static final String REQ_WHAT = "what";
    public static final String REQ_SESS = "s";
    public static final String WHAT_STATUS = "status";
    public static final String AJAX_STATUS_SCRIPT = "ajax_status.jspf";
    public static final String WHAT_VERIFY = "verify";
    public static final String WHAT_SUBMIT= "submit";
    public static final String WHAT_PREF = "pref";
    public static final String WHAT_GETVV = "vettingviewer";

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
            //			System.err.println(" >> " + Integer.toHexString(ch));
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

    private void processRequest(HttpServletRequest request, HttpServletResponse response, String val)  throws ServletException, IOException  {
        //		if(val != null) {
        //			System.err.println("val="+val);
        //		}
        SurveyMain sm = SurveyMain.getInstance(request);
        PrintWriter out = response.getWriter();
        String what = request.getParameter(REQ_WHAT);
        String sess = request.getParameter(SurveyMain.QUERY_SESSION);
        String loc = request.getParameter(SurveyMain.QUERY_LOCALE);
        String xpath = request.getParameter(SurveyForum.F_XPATH);
        String vhash = request.getParameter("vhash");
        String fieldHash = request.getParameter(SurveyMain.QUERY_FIELDHASH);
        CookieSession mySession = null;
        try {
            if(sm == null) {
                sendNoSurveyMain(out);
            } else if(what==null) {
                sendError(out, "Missing parameter: " + REQ_WHAT);
            } else if(what.equals(WHAT_STATUS)) {
                sendStatus(sm,out);

            } else if(sess!=null && !sess.isEmpty()) { // this and following: session needed
                mySession = CookieSession.retrieve(sess);
                if(mySession==null) {
                    sendError(out, "Missing Session: " + sess);
                } else {
                    if(what.equals(WHAT_VERIFY) || what.equals(WHAT_SUBMIT)) {
                        CheckCLDR cc = sm.createCheckWithoutCollisions();
                        int id = Integer.parseInt(xpath);
                        String xp = sm.xpt.getById(id);
                        Map<String, String> options = null;
                        List<CheckStatus> result = new ArrayList<CheckStatus>();
                        //CLDRFile file = CLDRFile.make(loc);
                        //CLDRFile file = mySession.
                        SurveyMain.UserLocaleStuff uf = null;
                        boolean dataEmpty = false;
                        JSONWriter r = newJSONStatus(sm);
                        synchronized(mySession) {
                            try {
                                CLDRLocale locale = CLDRLocale.getInstance(loc);
                                BallotBox<UserRegistry.User> ballotBox = sm.getSTFactory().ballotBoxForLocale(locale);
                                boolean foundVhash = false;
                                Exception[] exceptionList = new Exception[1];
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
                                        DisplayAndInputProcessor daip = new DisplayAndInputProcessor(locale.toULocale());
                                        val = daip.processInput(xp, val, exceptionList);
                                    }
                                }
                                
                                if(val!=null && !foundVhash) {
                                    uf = sm.getUserFile(mySession, locale);
                                    CLDRFile file = uf.cldrfile;
                                    cc.setCldrFileToCheck(file, SurveyMain.basicOptionsMap(), result);
                                    cc.check(xp, file.getFullXPath(xp), val, options, result);
                                    dataEmpty = file.isEmpty();
                                }
                            
                                r.put(SurveyMain.QUERY_FIELDHASH, fieldHash);
        
                                if(exceptionList[0]!=null) {
                                    for(Exception e : exceptionList) {
                                        result.add(new CheckStatus().setMainType(CheckStatus.errorType).setSubtype(Subtype.internalError)
                                                .setMessage("Input Processor Exception")
                                                .setParameters(exceptionList));
                                    }
                                }
                                
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
                                    boolean hasError = false;
                                    for(CheckStatus s : result) {
                                        if(s.getType().equals(CheckStatus.errorType)) {
                                            hasError = true;
                                        }
                                    }
                                    if(!hasError) {
                                        ballotBox.voteForValue(mySession.user, xp, val);
                                        r.put("submitResultRaw", ballotBox.getResolver(xp).toString());
                                    }
                                }
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
            e.printStackTrace();
            sendError(out, "JSONException: " + e);
        }
    }


    private void sendStatus(SurveyMain sm, PrintWriter out) throws IOException {
        JSONWriter r = newJSONStatus(sm);
        //        StringBuffer progress = new StringBuffer(sm.getProgress());
        //        String threadInfo = sm.startupThread.htmlStatus();
        //        if(threadInfo!=null) {
        //            progress.append("<br/><b>Processing:"+threadInfo+"</b><br>");
        //        }
        //r.put("progress", progress.toString());
        send(r,out);
    }

    private void setupStatus(SurveyMain sm, JSONWriter r) {
        r.put("SurveyOK","1");
        r.put("isSetup", (sm.isSetup)?"1":"0");
        r.put("isBusted", (sm.isBusted!=null)?"1":"0");
        StringBuffer memBuf = new StringBuffer();
        SurveyMain.appendMemoryInfo(memBuf, true);
        r.put("visitors", sm.getGuestsAndUsers()+memBuf.toString());
        r.put("uptime", sm.uptime.toString());
        r.put("progress", sm.getTopBox(false));
    }

    private JSONWriter newJSONStatus(SurveyMain sm) {
        JSONWriter r = newJSON();
        setupStatus(sm, r);
        return r;
    }

    private JSONWriter newJSON() {
        JSONWriter r = new JSONWriter();
        r.put("progress", "");
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
        r.put("err","The Survey Tool is not running.");
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
