<%@ include file="/WEB-INF/jspf/stcontext.jspf" %><%-- setup 'ctx' --%>
<%@ include file="/WEB-INF/jspf/report.jspf"  %>

<%
/* set the '_' parameter, to the current locale */
subCtx.setQuery(SurveyMain.QUERY_LOCALE,ctx.localeString());
/* flush output (sync between subCtx' stream and JSP stream ) */
subCtx.flush();
%>
<%@ include file="/WEB-INF/jspf/debug_jsp.jspf" %>

<%--
    <%@ include file="/WEB-INF/jspf/report_top.jspf" %>
--%>
<%
	// set up the 'x' parameter to the current secrtion (r_steps, etc)
subCtx.setQuery(SurveyMain.QUERY_SECTION,subCtx.field(SurveyMain.QUERY_SECTION));
ctx.setQuery(SurveyMain.QUERY_LOCALE,subCtx.field(SurveyMain.QUERY_LOCALE));

/**
 * Set query fields to be propagated to the individual steps 
 */
WebContext topCtx = (WebContext) request.getAttribute(WebContext.CLDR_WEBCONTEXT);
topCtx.setQuery(SurveyMain.QUERY_SECTION, subCtx.field(SurveyMain.QUERY_SECTION));
topCtx.setQuery(SurveyMain.QUERY_LOCALE, subCtx.field(SurveyMain.QUERY_LOCALE));

    // step parameter determines which 'stage' we are at. Stage 1: characters, stage 2: two currencies, stage 3: congratulations.

	// get the stage
	String myStageString = ctx.field("step");
	int myStage = stepNameToNumber(myStageString);         // requested 'new' stage
	if(myStage<1) myStage=1;
	String oldStageString = ctx.field("old_step");
	int oldStage = stepNameToNumber(oldStageString);   // outgoing 'old' stage (where we came from). 
	
	// what's the base xpath for the previous stage (for data submission)?
	String baseXpathForOldStage = stageToBaseXpath(oldStage);
	
	// True if we are going on to the next stage ('step'), false if we are remaining at 'old_step'
	boolean moveForward = true;
	
	// This handler will report back any items which couldn't be added.
	SummarizingSubmissionResultHandler ssrh = null;
	
	if(baseXpathForOldStage!=null) {  // handle submit of old data?
		if(ctx.debugJsp()) {
			%>  <h2>Processing data for xpath <%= baseXpathForOldStage %>  </h2>   <%
		}

		// process the submission. ssrh contains detailed results.
		ssrh = ctx.processDataSubmission(baseXpathForOldStage);
		moveForward = !ssrh.hadErrors(); // move forward if no errors.
		if(moveForward == false) {
			%><b>Please correct the errs first</b><br><%
			myStage = oldStage; // stuck here, until no errs.
		} else {
			%><i>Data added.</i><%
		}
	} else {
		/* No base xpath - nothing to process. */
	}
	
	// What is the base xpath for the next stage?
	String baseXpathForNextStage = stageToBaseXpath(myStage);

	// begin the form.
	if(baseXpathForNextStage != null) {
		topCtx.setQuery("old_step", stepNumberToName(myStage));
	}
%>

	<!--  Old stage: <%= oldStage %>, Current stage: <%= myStage %>
	
	-->
<% 
	int nextStage = myStage;

	// correct stage #'s
	if(myStage<1) myStage=1;
	if(myStage>(reports.length))  {
		myStage=reports.length+1;
	}
	
	ctx.put("thisStep",(Integer)myStage);
	
	
	%>

<b>Easy Steps: </b>
<% for(int i=1;i<reports.length+1;i++) { %>

		<%= subCtx.sm.getMenu(subCtx,stepNumberToName(myStage),	stepNumberToName(i), report_name[i-1],"step") %>

	|	<% if(subCtx.debugJsp()) { %>#<%= i  %><% } %>
	
<% } %>
	<%= /* Thanks page */
		subCtx.sm.getMenu(subCtx,stepNumberToName(myStage),	stepNumberToName(reports.length+1),
					"Easy Step Complete.","step")
	%>

	<br>
	

	<%
	
	if(myStage>(reports.length))  {
		%>
		<%@ include file="/WEB-INF/jspf/r_steps_complete.jspf" %>
		
		<%
	} else {
		ctx.put("thisBaseXpath",baseXpathForNextStage);
		ctx.put("thisStep",(Integer)myStage);
		if(baseXpathForNextStage != null) {
			ctx.put("nextStep",stepNumberToName(nextStage));
			topCtx.put("nextStep",stepNumberToName(nextStage));
			ctx.println("nextStep = " + stepNumberToName(nextStage));
		}
		ctx.flush();
		ctx.includeFragment(reports[myStage-1]+".jsp");
		ctx.flush();
		nextStage = myStage+1;
	}
	

	// now, enter the close of the form
	
	
	subCtx.flush();

%>


