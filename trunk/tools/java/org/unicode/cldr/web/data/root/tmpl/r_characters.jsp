<%@ include file="stcontext.jspf" %><%-- setup 'ctx' --%>
<%@ include file="report.jspf" %>

<%--
    <%@ include file="report_top.jspf" %>
--%>

<p> First, we need to get the characters used to write your language </p>

<%
response.flushBuffer();

SurveyForum.showXpath(subCtx, "//ldml/characters/exemplarCharacters");

%>