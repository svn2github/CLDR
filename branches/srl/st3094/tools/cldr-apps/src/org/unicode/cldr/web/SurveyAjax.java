package org.unicode.cldr.web;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.json.JSONException;
import org.json.JSONObject;
import org.unicode.cldr.test.CheckCLDR;
import org.unicode.cldr.test.CheckCLDR.CheckStatus;
import org.unicode.cldr.util.CLDRFile;


/**
 * Servlet implementation class SurveyAjax
 */
public class SurveyAjax extends HttpServlet {
    public static final class JSONWriter {
        private final JSONObject j = new JSONObject();
        
        public JSONWriter() {
        }
        
        public final void put(String k, String v) {
            try {
                j.put(k, v);
            } catch (JSONException e) {
                throw new IllegalArgumentException(e.toString(),e);
            }
        }
        
        public final String toString() {
            return j.toString();
        }
    }

    
    private static final long serialVersionUID = 1L;
    public static final String REQ_WHAT = "what";
    public static final String REQ_SESS = "s";
    public static final String WHAT_STATUS = "status";
    public static final String AJAX_STATUS_SCRIPT = "ajax_status.jspf";
    public static final Object WHAT_VERIFY = "verify";
       
    /**
     * @see HttpServlet#HttpServlet()
     */
    public SurveyAjax() {
        super();
    }

	/**
	 * @see HttpServlet#doGet(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
	    request.setCharacterEncoding("UTF-8");
	    response.setCharacterEncoding("UTF-8");
	    response.setContentType("application/json");
	    SurveyMain sm = SurveyMain.getInstance(request);
	    PrintWriter out = response.getWriter();
        String what = request.getParameter(REQ_WHAT);
        String sess = request.getParameter(SurveyMain.QUERY_SESSION);
        String loc = request.getParameter(SurveyMain.QUERY_LOCALE);
        String xpath = request.getParameter(SurveyForum.F_XPATH);
        String val = request.getParameter(SurveyMain.QUERY_VALUE_SUFFIX);
        val = WebContext.decodeFieldString(val); // from utf-8 - needed?
        String fieldHash = request.getParameter(SurveyMain.QUERY_FIELDHASH);
        CookieSession mySession = null;
	    if(sm == null) {
	        sendNoSurveyMain(out);
	    } else if(what==null) {
	        sendError(out, "Missing parameter: " + REQ_WHAT);
	    } else if(what.equals(WHAT_STATUS)) {
	        sendStatus(sm,out);
	        
	    } else if(what.equals(WHAT_VERIFY)) {
                CheckCLDR cc = sm.createCheckWithoutCollisions();
                int id = Integer.parseInt(xpath);
                String xp = sm.xpt.getById(id);
                Map<String, String> options = null;
               List<CheckStatus> result = new ArrayList<CheckStatus>();
               cc.setCldrFileToCheck(CLDRFile.make(loc), SurveyMain.basicOptionsMap(), result);
               cc.check(xp, xp, val, options, result);
               
               JSONWriter r = newJSON();
               r.put(SurveyMain.QUERY_FIELDHASH, fieldHash);
               r.put("testResultsEmpty", Boolean.toString(result.isEmpty()));
               r.put("testResults", result.toString());
               
               send(r,out);
        } else if(sess!=null && !sess.isEmpty()) { // this and following: session needed
             mySession = CookieSession.retrieve(sess);
             if(mySession==null) {
                 sendError(out, "Missing Session: " + sess);
             } 
	    } else {
	        sendError(out,"Unknown Request: " + what);
	    }
	}

    private void sendStatus(SurveyMain sm, PrintWriter out) throws IOException {
        JSONWriter r = newJSON();
        r.put("SurveyOK","1");
        r.put("isSetup", (sm.isSetup)?"1":"0");
        r.put("isBusted", (sm.isBusted!=null)?"1":"0");
        r.put("visitors", sm.getGuestsAndUsers());
        r.put("uptime", sm.uptime.toString());
        r.put("progress", sm.getTopBox(false));
//        StringBuffer progress = new StringBuffer(sm.getProgress());
//        String threadInfo = sm.startupThread.htmlStatus();
//        if(threadInfo!=null) {
//            progress.append("<br/><b>Processing:"+threadInfo+"</b><br>");
//        }
        //r.put("progress", progress.toString());
        send(r,out);
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

    /**
	 * @see HttpServlet#doPost(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
	    doGet(request, response);
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
