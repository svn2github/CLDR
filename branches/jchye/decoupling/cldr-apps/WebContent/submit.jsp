<%@page import="org.unicode.cldr.web.CLDRProgressIndicator.CLDRProgressTask"%>
<%@page import="com.ibm.icu.util.ULocale"%>
<%@page import="org.xml.sax.SAXParseException"%>
<%@page import="org.unicode.cldr.util.CLDRFile.DraftStatus"%>
<%@page import="org.unicode.cldr.util.*"%>
<%@page import="org.unicode.cldr.test.*"%>
<%@page import="org.unicode.cldr.web.*"%>
<%@page import="org.unicode.cldr.util.CLDRFile"%>
<%@page import="org.unicode.cldr.util.SimpleXMLSource"%>
<%@page import="org.unicode.cldr.util.XMLSource"%>
<%@page import="java.io.*"%><%@page import="java.util.*,org.apache.commons.fileupload.*,org.apache.commons.fileupload.servlet.*,org.apache.commons.io.FileCleaningTracker,org.apache.commons.fileupload.util.*,org.apache.commons.fileupload.disk.*,java.io.File" %>
<%@ page language="java" contentType="text/html; charset=UTF-8"
    pageEncoding="UTF-8"%>
<!DOCTYPE html PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">
<%
	if (!request.getMethod().equals("POST")) {
		response.sendRedirect(request.getContextPath() + "/upload.jsp");
	}
	
	CLDRFile cf = null;
	
	String sid = request.getParameter("s");
	final CookieSession cs = CookieSession.retrieve(sid);
	if(cs==null) {
		response.sendRedirect(request.getContextPath()+"/survey");
		return;
	}
	boolean isSubmit = true;
	
	boolean doFinal = (request.getParameter("dosubmit")!=null);
	
	String title = isSubmit ? "Submitted As You"
			: "Submitted As Your Org";
	
	if(!doFinal) {
		title = title + " <i>(Trial)</i>";
	}
	
	cf = (CLDRFile)cs.stuff.get("SubmitLocale");
%>
<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
<title>SurveyTool File Submission | <%=title%></title>
		<link rel='stylesheet' type='text/css' href='./surveytool.css' />
</head>
<body>

<a href="upload.jsp?s=<%= sid %>">Re-Upload File/Try Another</a> | <a href="<%=request.getContextPath()%>/survey">Return to the SurveyTool <img src='STLogo.png' style='float:right;' /></a>
<hr/>
<h3>SurveyTool File Submission | <%=title%> | <%= cs.user.name %></h3>

<i>Checking upload...</i>

<%

final CLDRLocale loc = CLDRLocale.getInstance(cf.getLocaleID());

%>

<h3>Locale: <%= loc + " - " + loc.getDisplayName(SurveyMain.BASELINE_LOCALE)  %></h3>
<%
//cs.stuff.put("SubmitLocale",cf);
	CLDRFile baseFile = cs.sm.dbsrcfac.make(loc.getBaseName(),false);
    XMLSource stSource = cs.sm.dbsrcfac.getInstance(loc);

Set<String> all = new TreeSet<String>();
for(String x : cf) {
	if(x.startsWith("//ldml/identity")) {
		continue;
	}
	all.add(x);
}
int updCnt=0;
%>

<h4>Please review these <%= all.size() %> entries.</h4>

<form action='<%= request.getContextPath()+request.getServletPath() %>' method='POST'>
<input type='hidden' name='s' value='<%= sid %>'/>
<input type='submit' name='dosubmit' value='Really Submit As My Vote' />
</form>

<div class='helpHtml'>
<ul>
	<li><b>Path not Present</b> - you are trying to vote for something that's not already entered, and cannot be entered at this point.</li>
	<li><b><i>base</i></b> - You are voting for the base (existing) item.</li>
	<li><b>proposed-xxx</b> - You are voting for a proposal.</li>
	<li><b><i>(Current Vote)</i></b> - This item is already your current vote. No change.</li>
	<li><b><i>(same)</i></b> - You are already voting for the winner.</li>
	<li><b><i>(none)</i></b> - There is no item at base, you are attempting to add an item.</li>
</ul>
</div>

<table class='data'>

<thead><tr><th>xpath</th><th>Current Winner</th><th>My Value</th><th>Comment</th></tr></thead>

<%
DisplayAndInputProcessor processor = new DisplayAndInputProcessor(loc.toULocale());
int r=0;
XPathParts xppMine = new XPathParts(null,null);
XPathParts xppBase = new XPathParts(null,null);
UserRegistry.User u = cs.user;
CLDRProgressTask progress = cs.sm.openProgress("Bulk:"+loc,all.size());
try {
for(String x : all) {
	progress.update(r++);
    String full = cf.getFullXPath(x);
    String alt = XPathTable.getAlt(full, xppMine);
    String val0 = cf.getStringValue(x);    
    Exception exc[] = new Exception[1];
    val0 = processor.processInput(x, val0, exc);
	String altPieces[] = LDMLUtilities.parseAlt(alt);
    String base = XPathTable.xpathToBaseXpath(x, xppMine);
    int base_xpath_id = cs.sm.xpt.getByXpath(base);

    String valb = baseFile.getWinningValue(base);

    
    String style = "";
    String stylea = "";
    String valm = val0;
    if(valb==null) {
    	valb="(<i>none</i>)";
    	stylea="background-color: #fdd";
    	style="background-color: #bfb;";
    } else if(!val0.equals(valb)) {
    	style="font-weight: bold; background-color: #bfb;";
    } else {
    	valm="(<i>same</i>)";
    	style="opacity: 0.9;";
    }

      //updUsers.add(ui);
      //String base_xpath = xpt.xpathToBaseXpath(x);
      int vet_type[] = new int[1];
      int j = cs.sm.vet.queryVote(loc, u.id, base_xpath_id, vet_type);
      //int dpathId = xpt.getByXpath(xpathStr);
      // now, find the ID to vote for.
      Set<String> resultPaths = new HashSet<String>();
      String baseNoAlt = cs.sm.xpt.removeAlt(base);
      int root_xpath_id = cs.sm.xpt.getByXpath(baseNoAlt);
      
      /*if(altPieces[0]==null) {
          stSource.getPathsWithValue(val0, base, resultPaths);
      } else*/ {
            Set<String> lotsOfPaths = new HashSet<String>();
            stSource.getPathsWithValue(val0, baseNoAlt, lotsOfPaths);
//            System.err.println("pwv["+val+","+baseNoAlt+",)="+lotsOfPaths.size());
            if(!lotsOfPaths.isEmpty()) {
                for(String s : lotsOfPaths) {
                    String alt2 = XPathTable.getAlt(s, xppBase);
                    if(alt2 != null && altPieces[0]!=null) {
                        String altPieces2[] = LDMLUtilities.parseAlt(alt2);
                        if(altPieces[0]!=null) {
                        	if (altPieces2[0]!=null && altPieces[0].equals(altPieces[0])) {
	                            resultPaths.add(s);
                        	}
                        } else if(altPieces2[0]==null) {
                        	resultPaths.add(s);
                        }
                    }
                }
            }
        }
   
    String result="";
    String resultStyle="";
      
    String resultIcon="okay";
    
    String theVote = null;
    int theVoteId = -1;
    if(resultPaths.isEmpty()) {
    	result = "Path not present.";
    	resultIcon = "stop";
    } else {
    	theVote = resultPaths.iterator().next();
    	theVoteId  = cs.sm.xpt.getByXpath(theVote);
    	String theAlt = XPathTable.getAlt(theVote);
    	String theAltProp = (theAlt!=null)?LDMLUtilities.parseAlt(theAlt)[1]:null;
    	result = "" + ((theAltProp==null)?"<i>base</i>":("<b>"+theAltProp+"</b>"));
    	if(theVoteId == j) {
    		resultIcon = "squo";
    		result = "<i>(Current Vote)</i>";
    	} else if(doFinal) {
            cs.sm.vet.vote(loc, base_xpath_id, u.id, theVoteId, Vetting.VET_EXPLICIT);
            updCnt++;
    	}
    }
    %>
<tr class='r<%= (r)%2 %>'>
	<th title='<%= base + " #" + base_xpath_id  %>' style='text-align: left; font-size: smaller;'>
		<a target='<%= WebContext.TARGET_ZOOMED %>' href='<%= request.getContextPath()+"/survey"+
			SurveyForum.forumUrlFragment(loc.toString(),root_xpath_id) %>'>
		<%= cs.sm.xpt.getPrettyPath(base) %>
		</a>
	</th>
	<td style='<%= stylea %>'><%= valb %></td>
	<td style='<%= style %>'><%= valm %></td>
	<td title='vote: <%= theVoteId %>' style='<%= resultStyle %>'><%= WebContext.iconHtml(request,resultIcon,result) %><%= result %>
</tr>
    <%
}
} finally {
	progress.close();
}
    
    %>

</table>

<hr/>
Recast <%= updCnt  %> votes.
<%
	if(doFinal && updCnt>0) {
	 cs.sm.startupThread.addTask(new SurveyThread.SurveyTask("UpdateAfterBulk:"+loc){
		    public void run() throws Throwable {
				cs.sm.updateLocale(loc);
				cs.sm.dbsrcfac.needUpdate(loc);
				cs.sm.dbsrcfac.update(this,null);
			    }

		 
	 });		
	}
%>

</body>
</html>