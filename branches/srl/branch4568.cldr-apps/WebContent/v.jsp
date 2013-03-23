<%@page import="org.unicode.cldr.web.WebContext"%>
<%@ page language="java" contentType="text/html; charset=UTF-8"
    pageEncoding="UTF-8"%>
<!DOCTYPE html PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">
<%--
If not booted, attempt to boot..
 --%>
<%
final String survURL = request.getContextPath() + "/survey";
SurveyMain sm = SurveyMain.getInstance(request);
if(SurveyMain.isBusted!=null) {
    %><a href="<%= request.getContextPath() + "/survey" %>">Survey Tool</a> is offline.<%
    return;
} else if(sm==null || !SurveyMain.isSetup) {
        %>Attempting to start SurveyTool..
        
        <iframe src="<%= survURL %>" width=10 height=10 %></iframe>
        <%
            String url = request.getContextPath() + request.getServletPath(); // TODO add query
            // JavaScript based redirect
            %>
            <head>
                <title>SurveyTool: Redirect</title>
                  <script type="application/javascript">
                  window.setTimeout(function(){
                	                       document.location='<%= url %>' + document.location.search +  document.location.hash;
                  },10000);
                  </script>
            </head>
            <body>
              If you are not redirected in a few seconds, you can click: <a href='<%= url %>'>here.</a>
            <%
            
            if(sm!=null) {
        %>
                <%= sm.startupThread.htmlStatus() %>
        <%
            }
        return;
}else
%>
<%--
    Validate the session. If we don't have a session, go back to SurveyMain.
 --%>
 <%
 WebContext ctx = new WebContext(request,response);
 String status = ctx.setSession();
if(false) { // if we need to redirect for some reason..
	 ctx.addAllParametersAsQuery();
 	 String url = request.getContextPath() + "/survey?" + ctx.query().replaceAll("&amp;", "\\&") + "&fromv=true";
	 // JavaScript based redirect
	 %>
	 <head>
    	 <title>SurveyTool: Redirect</title>
	       <script type="application/javascript">
	        document.location='<%= url %>' + document.location.hash;
	       </script>
     </head>
	 <body>
	   If you are not redirected, please click: <a href='<%= url %>'>here</a>
	 <%
 }
%>
<html class='claro'>
<head class='claro'>
<meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
<title>CLDR  <%= ctx.sm.getNewVersion() %> SurveyTool | view</title>

<meta name='robots' content='noindex,nofollow'>
<meta name="gigabot" content="noindex">
<meta name="gigabot" content="noarchive">
<meta name="gigabot" content="nofollow">
<link rel="stylesheet" href="<%= request.getContextPath() %>/surveytool.css" />
<%@include file="/WEB-INF/tmpl/ajax_status.jsp" %>
<script type="text/javascript">
// set from incoming session
surveySessionId = '<%= ctx.session.id %>';
  showV();
</script>
</head>
<body class='claro'>
 
        <% if( ctx.session == null || ctx.session.user == null) { %>
        <form id="login" method="POST" action="<%= request.getContextPath() + "/survey" %>">
           <%@ include file="/WEB-INF/tmpl/small_login.jsp"    %>
            
           </form>
          <% } %>
 <div data-dojo-type="dijit/layout/BorderContainer" data-dojo-props="design:'sidebar', gutters:true, liveSplitters:true" id="borderContainer">
    <div id="topstuff" data-dojo-type="dijit/layout/ContentPane" data-dojo-props="splitter:true, region:'top'" >
    <% if(status!=null) { %>
        <div class="v-status"><%= status %></div>
        <% } %>

        <%-- abbreviated form of usermenu  --%>
        <div id='toptitle'>
                <span class='title-cldr'>CLDR <%= ctx.sm.getNewVersion() %> Survey Tool
        <%=  (ctx.sm.phase()!=SurveyMain.Phase.SUBMIT)?ctx.sm.phase().toString():"" %>
         </span>
         <span id='title-locale'></span>
         <span id='title-page'></span>
         <span id='title-item'></span>
        </div>
        



    </div> <%-- end of topstuff --%>
    <div id="DynamicDataSection" data-dojo-type="dijit/layout/ContentPane" data-dojo-props="splitter:true, region:'center'" ></div>
    <div id="itemInfo" data-dojo-type="dijit/layout/ContentPane" data-dojo-props="splitter:true, region:'trailing'" ></div>
    <div id="botstuff" data-dojo-type="dijit/layout/ContentPane" data-dojo-props="splitter:true, region:'bottom'" >
         <%@include file="/WEB-INF/tmpl/stnotices.jspf" %>
    </div>
</div>
</body>
</html>