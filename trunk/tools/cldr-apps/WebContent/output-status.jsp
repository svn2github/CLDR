<%@ page language="java" contentType="text/html; charset=UTF-8"
    pageEncoding="UTF-8"
    import="org.unicode.cldr.util.*,java.util.*,java.sql.Connection,java.sql.Timestamp"
    %><%@ page import="org.unicode.cldr.web.*" %>
<!DOCTYPE html PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">
<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
<title>Output Status</title>
		<link rel='stylesheet' type='text/css' href='./surveytool.css' />

</head>
<body>

<h1>Status of File Output</h1>
<a href="<%=request.getContextPath()%>/survey">Return to the SurveyTool <img src='STLogo.png' style='float:right;' /></a>




<%
SurveyMain sm = CookieSession.sm;
if(sm==null || !sm.isSetup) {
	out.println("<i>Not booted yet, come back later.</i>");
	return;
}
int totals[] = new int[OutputFileManager.Kind.values().length];
%>

<table class='sqlbox' style='float: left; font-size: 140%;'>
<%
for(int i=0;i<totals.length;i++) {totals[i]=0;}
int count=0;
int k=0;
boolean flip=false;
	Set<CLDRLocale> sortSet = new TreeSet<CLDRLocale>();
	sortSet.addAll(SurveyMain.getLocalesSet());
	Connection conn = null;
	try {
		conn = sm.dbUtils.getDBConnection();
		for (CLDRLocale loc : sortSet) {
			k++;
			if(k>(sortSet.size()/2)&&flip==false) {
				flip=true;
				%></table><table class='sqlbox' style='float: right; font-size: 140%;'>
				<%
			}
	         STFactory.unimp(); Timestamp locTime=null;// = sm.outputFileManager.getLocaleTime(conn, loc);
			%>
			<tr>
			<th>
			<%=loc%>
			</th>
			<td><%= locTime %></td>
			<%
			    int j=0;
							for(OutputFileManager.Kind kind : OutputFileManager.Kind.values()) {
							boolean nu=false; STFactory.unimp(); // = sm.outputFileManager.fileNeedsUpdate(locTime,loc,kind.name());
							if(nu) totals[j]++;
							j++;
			%>
					<td style='background-color: <%= nu?"red":"green" %>'>
						<%= kind %>
					</td>
				
			<% } %>
			</tr>
			<%
		}
	} catch (NullPointerException npe) {
		%> <p><i>NPE - SurveyTool may not be started yet.</i></p><%
	} finally {
		DBUtils.close(conn);
	}
%>
<tr>
	<th>TOTAL</th>
	<th><%= sortSet.size() %></th>
	<% for(int i=0;i<totals.length;i++) { %>
		<th><%= totals[i] %></th>
	<% } %>
</tr>
</table>

</body>
</html>