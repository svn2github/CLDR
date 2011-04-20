<%@ page language="java" contentType="text/html; charset=UTF-8"
import="org.unicode.cldr.web.*"
    pageEncoding="UTF-8"%>
<!DOCTYPE html PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">
<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
<title>SurveyTool File Upload</title>
		<link rel='stylesheet' type='text/css' href='./surveytool.css' />
</head>
<body>

<a href="<%=request.getContextPath()%>/survey">Return to the SurveyTool <img src='STLogo.png' style='float:right;' /></a>
<hr/>

<%
String sid = request.getParameter("s");
CookieSession cs;
if((CookieSession.sm==null)||(cs = CookieSession.retrieve(sid))==null) {
	response.sendRedirect(request.getContextPath()+"/survey");
	return;
}


%>

<h3>Upload files... |  <%= cs.user.name %> </h3>
<% if(request.getParameter("s")==null) { %>
<h3>Error, not logged in.</h3>
<% } else { %>
<form method="POST" action="./check.jsp" enctype="multipart/form-data">
<input type="hidden" name="s" value="<%= request.getParameter("s") %>" />
<label>Upload a single XML file
<!-- or a ZIP file containing multiple XML files -->
:<input name="file" type="file" size="40"/></label><br/>
<!--  <input type="submit" name="bulk" value="Upload as Bulk Data (multiple vetters for which I am TC)"/><br/> -->
<input type="submit" name="submit" value="Upload as my Submission/Vetting choices"/><br/>
</form>

<% }  %>
</body>
</html>