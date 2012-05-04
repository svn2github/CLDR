<%@page import="org.unicode.cldr.util.PathHeader.SurveyToolStatus"%>
<%@page
	import="org.unicode.cldr.web.CLDRProgressIndicator.CLDRProgressTask"%>
<%@page import="com.ibm.icu.util.ULocale"%>
<%@page import="org.xml.sax.SAXParseException"%>
<%@page import="org.unicode.cldr.util.CLDRFile.DraftStatus"%>
<%@page import="org.unicode.cldr.util.*"%>
<%@page import="org.unicode.cldr.test.*"%>
<%@page import="org.unicode.cldr.web.*"%>
<%@page import="org.unicode.cldr.util.CLDRFile"%>
<%@page import="org.unicode.cldr.util.SimpleXMLSource"%>
<%@page import="org.unicode.cldr.util.XMLSource"%>
<%@page import="java.io.*"%><%@page
	import="java.util.*,org.apache.commons.fileupload.*,org.apache.commons.fileupload.servlet.*,org.apache.commons.io.FileCleaningTracker,org.apache.commons.fileupload.util.*,org.apache.commons.fileupload.disk.*,java.io.File"%>
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
	if (cs == null) {
		response.sendRedirect(request.getContextPath() + "/survey");
		return;
	}
	boolean isSubmit = true;

	boolean doFinal = (request.getParameter("dosubmit") != null);

	String title = isSubmit ? "Submitted As You"
			: "Submitted As Your Org";

	if (!doFinal) {
		title = title + " <i>(Trial)</i>";
	}

	cf = (CLDRFile) cs.stuff.get("SubmitLocale");
%>
<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
<title>SurveyTool File Submission | <%=title%></title>
<link rel='stylesheet' type='text/css' href='./surveytool.css' />

			<script>
			// TODO: from survey.js
				function testsToHtml(tests) {
					var newHtml = "";
					for ( var i = 0; i < tests.length; i++) {
						var testItem = tests[i];
						newHtml += "<p class='tr_" + testItem.type + "' title='" + testItem.type
								+ "'>";
						if (testItem.type == 'Warning') {
							newHtml += warnIcon;
							// what='warn';
						} else if (testItem.type == 'Error') {
							//td.className = "tr_err";
							newHtml += stopIcon;
				//			what = 'error';
						}
						newHtml += tests[i].message;
						newHtml += "</p>";
					}
					return newHtml;
				}
			
			// TODO: from ajax_status.jsp
			var warnIcon = "<%= WebContext.iconHtml(request,"warn","Test Warning") %>";
			var stopIcon = "<%= WebContext.iconHtml(request,"stop","Test Error") %>";
			</script>


</head>
<body>

	<a href="upload.jsp?s=<%=sid%>">Re-Upload File/Try Another</a> |
	<a href="<%=request.getContextPath()%>/survey">Return to the
		SurveyTool <img src='STLogo.png' style='float: right;' />
	</a>
	<hr />
	<h3>
		SurveyTool File Submission |
		<%=title%>
		|
		<%=cs.user.name%></h3>

	<i>Checking upload...</i>

	<%
		final CLDRLocale loc = CLDRLocale.getInstance(cf.getLocaleID());
	%>

	<h3>
		Locale:
		<%=loc + " - "
					+ loc.getDisplayName(SurveyMain.BASELINE_LOCALE)%></h3>
	<%
		//cs.stuff.put("SubmitLocale",cf);
		CLDRFile baseFile = cs.sm.getSTFactory().make(loc.getBaseName(),
				false);
		XMLSource stSource = cs.sm.getSTFactory().makeSource(
				loc.getBaseName());

		Set<String> all = new TreeSet<String>();
		for (String x : cf) {
			if (x.startsWith("//ldml/identity")) {
				continue;
			}
			all.add(x);
		}
		int updCnt = 0;
	%>

	<h4>
		Please review these
		<%=all.size()%>
		entries.
	</h4>

<% if(!doFinal) { %>
	<div class='helpHtml'>
		Please review these items carefully.
		<br>
		For help, see: <a target='CLDR-ST-DOCS' href='http://cldr.unicode.org/index/survey-tool/upload'>Using Bulk Upload</a> 
	</div>
	<form action='<%=request.getContextPath() + request.getServletPath()%>'
		method='POST'>
		<input type='hidden' name='s' value='<%=sid%>' /> <input
			type='submit' name='dosubmit' value='Really Submit As My Vote' />
	</form>
<% } else { %>
	<div class='helpHtml'>
		Your items have been submitted.
		<br>
		For help, see: <a target='CLDR-ST-DOCS' href='http://cldr.unicode.org/index/survey-tool/upload'>Using Bulk Upload</a> 
	</div>
<% } %>

	<table class='data'>

		<thead>
			<tr>
				<th>xpath</th>
<!-- 				<th>Current Winner</th> -->
				<th>My Value</th>
				<th>Comment</th>
			</tr>
		</thead>

<%
	DisplayAndInputProcessor processor = new DisplayAndInputProcessor(loc);
	STFactory stf = CookieSession.sm.getSTFactory();
    BallotBox<UserRegistry.User> ballotBox = stf.ballotBoxForLocale(loc);
    SupplementalDataInfo sdi = cs.sm.getSupplementalDataInfo();


	int r = 0;
	XPathParts xppMine = new XPathParts(null, null);
	XPathParts xppBase = new XPathParts(null, null);
    List<CheckCLDR.CheckStatus> checkResult = new ArrayList<CheckCLDR.CheckStatus>();
    Map<String,String> options = DataSection.getOptions(null, cs, loc);
    TestCache.TestResultBundle cc = stf.getTestResult(loc, options);
	UserRegistry.User u = cs.user;
	CLDRProgressTask progress = cs.sm.openProgress("Bulk:" + loc,
			all.size());
	try {
		for (String x : all) {
			progress.update(r++);
			
			String full = cf.getFullXPath(x);
			String alt = XPathTable.getAlt(full, xppMine);
			String val0 = cf.getStringValue(x);
			Exception exc[] = new Exception[1];
			val0 = processor.processInput(x, val0, exc);
			String altPieces[] = LDMLUtilities.parseAlt(alt);
			String base = XPathTable.xpathToBaseXpath(x, xppMine);
			base = XPathTable.removeDraft(base, xppMine);
			int base_xpath_id = cs.sm.xpt.getByXpath(base);
			
			String valb = baseFile.getWinningValue(base);
			

			String style = "";
			String stylea = "";
			String valm = val0;
			if (valb == null) {
				valb = "(<i>none</i>)";
				stylea = "background-color: #fdd";
				style = "background-color: #bfb;";
			} else if (!val0.equals(valb)) {
				style = "font-weight: bold; background-color: #bfb;";
			} else {
				//valm = WebContext.iconHtml(request, "squo", "same as winner") + "<br>"+ val0;
				style = "opacity: 0.9;";
			}

			//updUsers.add(ui);
			//String base_xpath = xpt.xpathToBaseXpath(x);
			int vet_type[] = new int[1];

			int j = -1; //cs.sm.vet.queryVote(loc, u.id, base_xpath_id, vet_type);
			//int dpathId = xpt.getByXpath(xpathStr);
			// now, find the ID to vote for.
			Set<String> resultPaths = new HashSet<String>();
			String baseNoAlt = cs.sm.xpt.removeAlt(base);
			int root_xpath_id = cs.sm.xpt.getByXpath(baseNoAlt);
			
			int coverageValue = sdi.getCoverageValue(base, loc.toULocale());

			String result = "";
			String resultStyle = "";

			String resultIcon = "okay";
			
			PathHeader ph = stf.getPathHeader(base);
			
			checkResult.clear();
            cc.check(base,checkResult, val0);
            boolean hadErr = false;
            
            if(!checkResult.isEmpty()) {
            	for(CheckCLDR.CheckStatus s : checkResult) {
            		if(s.getType().equals(CheckCLDR.CheckStatus.errorType)) {
            			hadErr=true;
            			break;
            		}
            	}
            }
            
			if(ph==null) {
				result="Item is not a SurveyTool-visible LDML entity.";
				resultIcon="stop";
			} else if(ph.getSurveyToolStatus() != SurveyToolStatus.READ_WRITE) {
				result="Item is not writable in the Survey Tool. Please file a ticket.";
				resultIcon="stop";
			} else if(coverageValue > Level.COMPREHENSIVE.getLevel()) {
				result="Item is not visible for write via the Survey Tool. Please file a ticket.";
				resultIcon="stop";
			} else if(hadErr) {
				result="Correct the test errors before submitting.";
				resultIcon="stop";
			} else {
				if(doFinal) {
					ballotBox.voteForValue(cs.user, base, val0);
					result="Vote accepted";
					resultIcon="vote";
				} else {
					result = "Ready to submit.";
				}
				updCnt++;
			}
%>
		<tr class='r<%=(r) % 2%>'>
			<th title='<%=base + " #" + base_xpath_id%>'
				style='text-align: left; font-size: smaller;'><a
				target='<%=WebContext.TARGET_ZOOMED%>'
				href='<%=request.getContextPath()
							+ "/survey?_="+ loc + "&strid=" + cs.sm.xpt.getStringIDString(base_xpath_id)  %>'>
					<%=ph.toString()%></a>
			</a><br><tt><%= base %></tt></tt></th>
		<!--  	<td style='<%=stylea%>'><%=valb%></td> -->
			<td style='<%=style%>'><%=valm%></td>
			<td title='vote:' style='<%=resultStyle%>'>
			<% if(!checkResult.isEmpty()){  %>
			<script>
				document.write(testsToHtml(<%= SurveyAjax.JSONWriter.wrap(checkResult) %>));				
			</script>
			<% }  %>
				<%=WebContext.iconHtml(request, resultIcon, result)%><%=result%>
		</tr>
		<%
			}
			} finally {
				progress.close();
			}
		%>

	</table>

	<hr />
	<%	if(doFinal) { %>
	Voted on
	<%  } else { %>
	Ready to submit
	<%  } %>
	<%=updCnt%>
	votes.
	<%
		if(!doFinal && updCnt>0) {
	%>
		<form action='<%=request.getContextPath() + request.getServletPath()%>'
			method='POST'>
			<input type='hidden' name='s' value='<%=sid%>' /> <input
				type='submit' name='dosubmit' value='Submit these items as my vote' />
		</form>
	<%
		 }
	%>

</body>
</html>