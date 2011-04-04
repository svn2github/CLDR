<%@ page language="java" contentType="text/html; charset=UTF-8"
    pageEncoding="UTF-8" import="org.unicode.cldr.web.*"%>
<%
String email = request.getParameter("email");
if(email==null&&email.isEmpty()) {
	response.sendRedirect(request.getContextPath()+"/survey#err_noemail");
}
String s = request.getParameter("s");
if(s==null&&s.isEmpty()) {
	response.sendRedirect(request.getContextPath()+"/survey#err_nosession");
}
CookieSession cs = CookieSession.retrieve(s);
if(cs==null) {
	response.sendRedirect(request.getContextPath()+"/survey#err_badsession");
}
if(cs.user!=null) {
	response.sendRedirect(request.getContextPath()+"/survey#err_alreadyloggedin");
}

Integer sumAnswer = (Integer)cs.stuff.get("sumAnswer");

String userAnswer = request.getParameter("sumAnswer");

int hashA = (int)(Math.random()*20.0);
int hashB = (int)(Math.random()*20.0);
int hashC = hashA+hashB;

%>
<!DOCTYPE html PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">
<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
<title>Password Reset | <%= email %></title>
</head>
<body>
<h3>Password Reset | <%= email %></h3>

<%
// did they get it right?
if(userAnswer!=null&&(sumAnswer==Integer.parseInt(userAnswer))) {
%>
<h3>It's on it's way (just kidding)</h3>
<%
} else {
	// put it in the hash
	cs.stuff.put("sumAnswer",new Integer(hashC));
	
	if(userAnswer!=null) {
%>
	<i>Sorry, that answer was wrong.</i><br/>
<%  } %>

	<div class='graybox'>
		Please solve this simple path problem:  What is the sum of 
			<%= hashA %>
				+
			<%= hashB %>
				?
				
		<% if(SurveyMain.isUnofficial) { %><i> <%= hashC %></i> <% } %> <br/>
	
		<form method='POST' action='<%= request.getContextPath()+request.getServletPath() %>'>
			<input name='email' type='hidden' value='<%= email %>'/>
			<input name='s' type='hidden' value='<%= s %>'/>
			<input name='sumAnswer' size=10 value='' />
			<input type='submit' value='Submit'/>
		</form>	
		
	</div>
<%
}
%>
</body>
</html>