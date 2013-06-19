<%@ page contentType="text/html; charset=UTF-8"
	import="org.unicode.cldr.web.*,org.unicode.cldr.util.*"%>

<%
String vap = request.getParameter("vap");
if(vap==null ||
    SurveyMain.isBusted() ||
    !SurveyMain.isSetup ||
    vap.length()==0 ||
    	!SurveyMain.isUnofficial() || 
			(!SurveyMain.vap.equals(vap) && !vap.equals(SurveyMain.testpw))  ) {
	response.sendRedirect(request.getContextPath() + "index.jsp");
	return;
}
	
// zap any current login
    Cookie c0 = WebContext.getCookie(request,SurveyMain.QUERY_EMAIL);
    if(c0!=null) {
        c0.setValue("");
        c0.setMaxAge(0);
        response.addCookie(c0);
    }
    Cookie c1 = WebContext.getCookie(request,SurveyMain.QUERY_PASSWORD);
    if(c1!=null) {
        c1.setValue("");
        c1.setMaxAge(0);
        response.addCookie(c1);
    }

%>
<html>
<head>
<title>CLDR Web Applications : Create and Login</title>
<link rel='stylesheet' type='text/css' href='./surveytool.css' />
<meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
</head>
<body>
<%@ include file="header.jspf"%>
				<img src="STLogo.png" align="right" border="0" title="[logo]" alt="[logo]" />
				
<%
if(SurveyMain.isSetup==false ) {
	%>
	   <div class='ferrbox'>The SurveyTool is not yet setup. <a href='<%= request.getContextPath() %>/survey'>Try clicking this link.</a></div>
	<%
	return;
}
CookieSession.sm.reg.setOrgList();
String orgs[] = UserRegistry.getOrgList();
String myorg = orgs[(int)Math.rint(Math.random()*(double)(orgs.length-1))];
%>
				
<h1>Add a Test Survey Tool user</h1>
<i>Note: the organization chosen will be random, unless you change it at the bottom of this page.</i> <br/>
<i>This account is for testing purposes and may be deleted without notice!</i> <br/>
<form class="adduser" action='survey'
	method="POST"><input type="hidden" name="dump"
	value='<%=vap%>' /> <input type="hidden"
	name="action" value="new_and_login" />

<table>
	<tr>
		<th><label for="new_name">Real Name (ONE WORD):</label></th>
		<td><input id='real' size="40" value="REALNAME" name="real" /></td>
	</tr>
	<tr class="submit">
		<td colspan="2"><button style='font-size: xx-large' type="submit">Login</button></td>
	</tr>
	</table>
	<hr>
	

	<h2>More Options...</h2>
	<table id='more'>
	<tr>
		<th><label for="new_org">Specific Organization:</label></th>
            <td>
                <select onchange="document.getElementById('new_org').value=this.value">
                    <option value='' selected="selected">Choose...</option>
                    <%
                        for(String o : UserRegistry.getOrgList()) {
                    %>
                            <option value='<%= o %>'><%= o %></option>
                    <%
                        }
                    %>
                </select>
            </td>
                  <td><input id='new_org' name="new_org" value="<%=myorg%>" /></td>
		
		<%--
			<td><input name="new_org"  value="<%=myorg%>" /></td>
			
			--%>
	</tr>
	<tr>
		<th><label for="new_userlevel">Userlevel:</label></th>
		<td><select name="new_userlevel">
			<%
				for (int lev : UserRegistry.ALL_LEVELS) {
			%>
			<option value="<%=lev%>"
				<%=(lev == UserRegistry.TC) ? "selected" : ""%>
				<%=(lev == UserRegistry.ADMIN) ? "disabled" : ""%>
				                ><%=lev%>
			(<%=UserRegistry.levelAsStr(lev)%>)</option>
			<%
				}
			%>
		</select> <!--      <br>       <label>Userlevel: <input name="new_userlevel" value="5" /></label>    (1=TC, 5=Vetter, 10=Street, ...) <br/> -->
		</td>
	</tr>
	<tr>
		<th><label for="new_locales">Languages responsible:</label></th>
		<td><input name="new_locales" value="" /> <br>
		(Space separated. Examples: "en de fr"  but not <strike>"zh_Hant"</strike> or <strike>"de_CH"</strike>. )</td>
	</tr>
</table>
</form>
<hr />
<script>
document.getElementById('real').focus()
</script>
<%-- <a
	href='<%=request.getParameter("a")%>?s=<%=request.getParameter("s")%>'>Cancel,
return to Survey Tool</a>
 --%>|
<a href="./index.jsp">Return to CLDR Applications</a>
|
<!-- <a target="_new"
	href="http://dev.icu-project.org/cgi-bin/cldrwiki.pl?SurveyToolHelp/AddModifyUser">Help
on this page (in a new window)</a>
 --></body>
</html>
