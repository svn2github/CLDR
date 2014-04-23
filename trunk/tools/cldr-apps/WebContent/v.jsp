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
    %>
    <html>
    <head>
    <link rel="stylesheet" href="<%= request.getContextPath() %>/surveytool.css" />
    <title>SurveyTool | Offline.</title>
    </head>
    <body>
    <p class='ferrorbox'>
    <a href="<%= request.getContextPath() + "/survey" %>">Survey Tool</a> is offline.
    </p>
    </body>
    </html>
    
    <%
    return;
} else if(sm==null || !SurveyMain.isSetup) {
    String url = request.getContextPath() + request.getServletPath(); // TODO add query
        %>
    <html>
    <head>
    <link rel="stylesheet" href="<%= request.getContextPath() %>/surveytool.css" />
    <title>Survey Tool | Starting</title>
                  <script type="application/javascript">
                  window.setTimeout(function(){
                      window.location.reload(true);                
                        //document.location='<%= url %>' + document.location.search +  document.location.hash;
                  },10000);
                  </script>
    </head>
    <body>
        <img src="<%= request.getContextPath() %>/STLogo.png" align=right>
        <div id='st_err'></div>
        <%@include file="/WEB-INF/tmpl/ajax_status.jsp" %>
        
        <div class='finfobox'>
        <p>        Attempting to start SurveyTool..</p>
        </div>
        
        <%
            // JavaScript based redirect
            %>
            <noscript>
            <h1>
                JavaScript is required.
            </h1></noscript>
              If you are not redirected in a few seconds, you can click: <a id='redir' href='<%= url %>'>here</a> to retry, or <a id='redir2' href='<%= survURL %>'>here</a>.
              <script type="application/javascript">
              var newUrl = '<%= url %>' + document.location.search +  document.location.hash;
              var survURL = '<%= survURL %>';
                            document.getElementById("redir").href = newUrl;
                           var dstatus = document.getElementById('st_err');
                           dstatus.appendChild(document.createElement('br'));
                           dstatus.appendChild(document.createTextNode('.'));
                           dojo.ready(function(){
                               dstatus.appendChild(document.createTextNode('.'));
                            	   window.setTimeout(function(){
                                       dstatus.appendChild(document.createTextNode('.'));
                            		    dojo.xhrGet({url: survURL, load: function(data) {   
                            		        dstatus.appendChild(document.createTextNode('Loaded  '+data.length + ' bytes from SurveyTool. Reloading this page..')); window.location.reload(true);
                            		        if(false)   window.setTimeout(function(){     window.location.search='?'+window.location.search.substr(1)+'&'; },5000);  }  }); 
                            	   }, 2000);
                           	   });

                            
                            </script>
            <%
            
            if(sm!=null) {
        %>
                <%= sm.startupThread.htmlStatus() %>
                <hr>
            </body></html>                                
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
    	 <title>Survey Tool: Redirect</title>
	       <script type="application/javascript">
	        document.location='<%= url %>' + document.location.hash;
	       </script>
     </head>
	 <body>
	   If you are not redirected, please click: <a href='<%= url %>'>here</a>, or <a id='redir2' href='<%= survURL %>'>here</a> (may lose your place).
	 <%
 } else if(ctx.session==null) {
	 %>
				<html class='claro'>
				<head class='claro'>
				<meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
				<title>CLDR  <%= ctx.sm.getNewVersion() %> Survey Tool</title>
				</head>
				<body>
                <div style='float: right'>
                    <a href='<%= request.getContextPath() %>/login.jsp' id='loginlink' class='notselected'>Login…</a> 
                 </div>
				    <h2>CLDR Survey Tool | Problem</h2>
				    <div>
				        <p><img src='stop.png' width='16'><%= status %></p>
				    </div>
				    
				    <hr>
				    <p><%= ctx.sm.getGuestsAndUsers() %></p>
				</body>
				</html>
	 <%
	 return;
 }
%>
<html lang='<%= SurveyMain.BASELINE_LOCALE.toLanguageTag() %>' class='claro'>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
<title>CLDR Survey Tool </title>

<meta name='robots' content='noindex,nofollow'>
<meta name="gigabot" content="noindex">
<meta name="gigabot" content="noarchive">
<meta name="gigabot" content="nofollow">
<link rel="stylesheet" href="<%= request.getContextPath() %>/surveytool.css" />
<%@include file="/WEB-INF/tmpl/ajax_status.jsp" %>
<script type="text/javascript">
// set from incoming session
surveySessionId = '<%= ctx.session.id %>';
survURL = '<%= survURL %>';
<% if(ctx.session.user == null || UserRegistry.userIsLocked(ctx.session.user)) { %>
surveyUser = null;
<%} else { %>
surveyUser =  <%= ctx.session.user.toJSONString() %>;
<% } %>
  showV();
</script>
</head>
<body lang='<%= SurveyMain.BASELINE_LOCALE.toLanguageTag() %>' data-spy="scroll" data-target="#itemInfo">

<div data-dojo-type="dijit/Dialog" data-dojo-id="ariDialog" title="CLDR Survey Tool"
    execute="" data-dojo-props="onHide: function(){ariReload.style.display='';ariRetry.style.display='none';   if(disconnected) { unbust();}}">

    <div id='ariContent' class="dijitDialogPaneContentArea">
    	<div id='ariHelp'><a href='http://cldr.unicode.org/index/survey-tool#disconnected'>Help</a></div>
        <p id='ariMessage'>
            This page is still loading. 
        </p>
        <h3 id='ariSub'>Details:</h3>
        <p id='ariSubMessage'>
            This page is still loading. 
        </p>
        <p id='ariScroller'>
        </p>
    </div>

    <div class="dijitDialogPaneActionBar">
    <%--
        <button data-dojo-type="dijit/form/Button" type="submit" onClick="return ariDialog.isValid();">
            Report Bug…
        </button>
        --%>
        <button id='ariMain' style='margin-right: 2em;' data-dojo-type="dijit/form/Button" type="button" onClick="window.location = survURL;">
            Back to Locales
        </button>
        <button id='ariRetryBtn'  data-dojo-type="dijit/form/Button" type="button" onClick="ariRetry()">
            <b>Reload</b>
        </button>
    </div>
</div>

<%--
<h1>ARITester</h1>
<p>When pressing this button the dialog will popup:</p>
<button id="buttonThree" data-dojo-type="dijit/form/Button" type="button" onClick="ariDialog.show();">
    Show me!
</button>
--%>



			
         
				

<div class="navbar navbar-fixed-top" role="navigation">
      <div class="container-fluid">
        
        <div class="collapse navbar-collapse">
        <p class="nav navbar-text">Survey Tool <%= ctx.sm.getNewVersion() %>
        <%=  (ctx.sm.phase()!=SurveyMain.Phase.SUBMIT)?ctx.sm.phase().toString():"" %>
        </p>
          <ul class="nav navbar-nav">
            <li><a href="<%= survURL  %>?do=options"><span class="glyphicon glyphicon-share"></span>&nbsp;&nbsp;Manage</a></li>
            
            <li class="dropdown" id='title-coverage'>
                      <a href="#" class="dropdown-toggle" data-toggle="dropdown">Coverage <span id="coverage-info"></span></a>
                      <ul class="dropdown-menu">
                      </ul>
           </li>
            
            <li id="help-menu" data-toggle="popover">
		          <a href="#"><%= SurveyMain.GENERAL_HELP_NAME %> <b class="caret"></b></a>
		          <ul class="nav nav-pills nav-stacked" style="display:none">
		            <li><a href="<%= SurveyMain.GENERAL_HELP_URL %>"><%= SurveyMain.GENERAL_HELP_NAME %> <span class="glyphicon glyphicon-share"></span></a></li>
		            <li class="nav-divider"></li>
		            <li id="help-content"></li>
		          </ul>
		      </li>
          
          </ul>

          <p class="navbar-text navbar-right">
              <span id="flag-info"></span>
          
          	  <% if(ctx.session !=null && ctx.session.user != null) {
                  boolean haveCookies = (ctx.getCookie(SurveyMain.QUERY_EMAIL)!=null&&ctx.getCookie(SurveyMain.QUERY_PASSWORD)!=null);
                  String cookieMessage = haveCookies?"<!-- and Forget Me-->":"";
	              %>
	               <span class="hasTooltip" title="<%= ctx.session.user.email %>"><%= ctx.session.user.name %></span>
	               (<%= ctx.session.user.org %>)
	              
	              <% Integer reducedLevelVote =ctx.session.user.getLevel().canVoteAtReducedLevel(); 
	              	 int regularVote = ctx.session.user.getLevel().getVotes(); %>
	              <% if(reducedLevelVote != null) { %>
	              	<select title="vote with a reduced number of votes" id="voteReduced" name="voteReduced">
	              		<option selected="selected" value="<%= regularVote %>"><%= regularVote %> votes</option>
	              		<option value="<%= reducedLevelVote %>"><%= reducedLevelVote %> votes</option>
	             		</select>
	              	 |
	              <% } %>
	               (<a class='navbar-link' href='<%= survURL + "?do=logout" %>'>Logout<%= cookieMessage %></a>)
	                <script type="text/javascript">var isVisitor = 0</script>
	        <% } else { %>
                   (<a href='<%= request.getContextPath() %>/login.jsp' class='navbar-link'>Login…</a>)
      		        <script type="text/javascript">var isVisitor = 1</script>
            		
            <% } %>
            
            
          </p>
          
        </div>
      </div>
</div>

<div id="left-sidebar">
	<div id="content-sidebar">
		<div id="locale-info">
			<div class="input-group input-group-sm">
				  <span class="input-group-addon  refresh-search"><span class="glyphicon glyphicon-search"></span></span>
				  <input type="text" class="form-control local-search" placeholder="Locale">
			</div>
			<span id="locale-clear" class="refresh-search">x</span>
			
			<div class="input-group input-group-sm" id="locale-check-group">
					<label class="checkbox-inline">
						      <input type="checkbox" id="show-read"> Show read-only
					</label>
					<label class="checkbox-inline">
						      <input type="checkbox" id="show-locked"> Show locked
					</label>
			</div>
		</div>
		<div id="locale-list">
			
		</div>
		<div id="locale-menu">
		
		</div>
	</div>
	
	<div id="dragger">
		<span class="glyphicon glyphicon-chevron-right"></span>
		<div id="dragger-info"></div>
	</div>
</div>



<div class="container-fluid" id="main-container">
 <div class="row menu-position">
    <div class="col-md-12">
    
    
    <%
    if(status!=null) { %>
        <div class="v-status"><%= status %></div>
        <% } %>

        

        <%-- abbreviated form of usermenu  --%>
        <div id='toptitle'>
        
      
         <div id='title-locale-container' class='menu-container' style="display:none">
                <h1><a href='#locales///' id='title-locale'></a></h1>
                <span id='title-dcontent-container'><a href='http://cldr.unicode.org/translation/default-content' id='title-content'></a></span>
         </div>
         
         <div id='title-section-container' class='menu-container'>
         	 <h1 id="section-current"></h1>
	         <div style="display:none" id='title-section' data-dojo-type="dijit/form/DropDownButton">
	              <span>(section)</span>
	              <div id='menu-section' data-dojo-type="dijit/DropDownMenu"></div>
	         </div>
         </div> 

        <div id='title-page-container' class='menu-container'>
<!-- 	         <div id='title-page' data-dojo-type="dijit/form/DropDownButton">
	              <span>(page)</span>
	              <div id='menu-page' data-dojo-type="dijit/DropDownMenu"></div>
	         </div>
 -->        
  		</div>

        </div> <%-- end of toptitle --%>         
        
         <div id="additional-top">
	         	        <%@include file="/WEB-INF/tmpl/stnotices.jspf" %>
	     
	 	</div>
        <!-- top info -->
       
    </div> 
  </div>  

    <div class="row" id="main-row" style="padding-top:147px;">
    	<div class="col-md-9">
		    <div data-dojo-type="dijit/layout/ContentPane" id="MainContentPane" data-dojo-props="splitter:true, region:'center'" >
		        <div id="LoadingMessageSection"><%-- Loading messages --%>Please Wait</div>
		        <div  id="DynamicDataSection" ><%-- the actual scrolling table --%></div>
		        <div id="OtherSection"><%-- other content --%></div>
		    </div>
	    </div>
	    <div class="col-md-3">
	    	<div id="itemInfo" class="right-info" data-dojo-type="dijit/layout/ContentPane" data-dojo-props="splitter:true, region:'trailing'" ></div>
	   	</div>
	    
    </div>
    
    <jsp:include page="feedback.jsp"/>
    <div id="ressources" style="display:none">
    
	</div>
</div>
<div id="overlay"></div>
<div class="modal fade" id="post-modal" tabindex="-1" role="dialog" aria-labelledby="myLargeModalLabel" aria-hidden="true">
  <div class="modal-dialog modal-lg">
    <div class="modal-content">
      <div class="modal-header">
        <button type="button" class="close" data-dismiss="modal" aria-hidden="true">&times;</button>
        <h4 class="modal-title">Post</h4>
      </div>
      <div class="modal-body">
        ...
      </div>
    </div>
  </div>
</div>
</body>
</html>