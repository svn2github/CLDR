<%@ include file="/WEB-INF/jspf/stcontext.jspf" %><%-- setup 'ctx' --%><%
String helpLink = ctx.getString("helpLink");
String helpName = ctx.getString("helpName");

%>
<table id='usertable' summary='header' border='0' cellpadding='0' cellspacing='0' style='border-collapse: collapse' "+
    			" width='100%' bgcolor='#EEEEEE'>
	<tr>
		<td>
			<a id='generalHelpLink' class='notselected'  href='<%= SurveyMain.GENERAL_HELP_URL %>'><%= SurveyMain.GENERAL_HELP_NAME %></a>
<%
/*     	if(helpLink != null) {
    		ctx.println(" | ");
    		if(helpName != null) {
    			ctx.printHelpLink(helpLink, helpName);
    		} else {
    			ctx.printHelpLink(helpLink, "Page&nbsp;Instructions");
    		}
    	}
 */    	ctx.println("</td><td align='right'>");
    	String doWhat = ctx.field(SurveyMain.QUERY_DO);

    	//        ctx.println("The SurveyTool is in phase <b><span title='"+phase().name()+"'>"+phase().toString()+"</span></b> for version <b>"+getNewVersion()+"</b><br>" );

    	if(ctx.session.user == null)  {
    		//ctx.println("<a class='notselected' href='" + ctx.jspLink("login.jsp") +"'>Login</a>");
    		ctx.println("<form id='login' method='POST' action='"+ctx.base()+"'>");
    		%><%@ include file="/WEB-INF/tmpl/small_login.jsp" %><%
    		ctx.println("</form>");
    		//            if(this.phase()==Phase.VETTING || this.phase() == Phase.SUBMIT) {
    		//                printMenu(ctx, doWhat, "disputed", "Disputed", QUERY_DO);
    		//                ctx.print(" | ");
    		//            }

    		String curSetting = ctx.getCoverageSetting();
    		if(!curSetting.equals(WebContext.COVLEV_RECOMMENDED)) {
    			ctx.println("<span style='border: 2px solid blue'>");
    		}
    		if(!(ctx.hasField("xpath")||ctx.hasField("forum")) && ( ctx.hasField(SurveyMain.QUERY_LOCALE) || ctx.field(SurveyMain.QUERY_DO).equals("disputed"))) {
    			WebContext subCtx = new WebContext(ctx);
    			for(String field : SurveyMain.REDO_FIELD_LIST) {
    				if(ctx.hasField(field)) {
    					subCtx.addQuery(field, ctx.field(field));
    				}
    			}
    			if(ctx.hasField(SurveyMain.QUERY_LOCALE)) {
    				subCtx.showCoverageSettingForLocale();	
    			} else {
    				subCtx.showCoverageSetting();
    			}
    		} else {
    			ctx.print(" <smaller>Coverage Level: "+curSetting+"</smaller>");
    		}
    		if(!curSetting.equals(WebContext.COVLEV_RECOMMENDED)) {
    			ctx.println("</span>");
    		}

            ctx.print(" | ");
            ctx.sm.printMenu(ctx, doWhat, "options", "Manage", SurveyMain.QUERY_DO);
    	} else {
    		boolean haveCookies = (ctx.getCookie(SurveyMain.QUERY_EMAIL)!=null&&ctx.getCookie(SurveyMain.QUERY_PASSWORD)!=null);
    		ctx.println(ctx.session.user.name + " (" + ctx.session.user.org + ") ");
    		if(!haveCookies && !ctx.hasField(SurveyMain.QUERY_SAVE_COOKIE)) {
    			ctx.println(" <a class='notselected' href='"+ctx.url()+ctx.urlConnector()+SurveyMain.QUERY_SAVE_COOKIE+"=iva'><b>Remember Me!</b></a>");
    		}
    		ctx.print(" | ");
    		String cookieMessage = haveCookies?"<!-- and Forget Me-->":"";
    		ctx.println("<a class='notselected' href='" + ctx.base() + "?do=logout'>Logout"+cookieMessage+"</a> | ");
    		//            if(this.phase()==Phase.VETTING || this.phase() == Phase.SUBMIT || isPhaseVettingClosed()) {
    		//                ctx.sm.printMenu(ctx, doWhat, "disputed", "Disputed", QUERY_DO);
    		//                ctx.print(" | ");
    		//            }
    		String curSetting = ctx.getCoverageSetting();
    		if(!(ctx.hasField("xpath")||ctx.hasField("forum")) && ( ctx.hasField(SurveyMain.QUERY_LOCALE) || ctx.field(SurveyMain.QUERY_DO).equals("disputed"))) {
                if(!curSetting.equals(WebContext.COVLEV_RECOMMENDED)) {
                    ctx.println("<span style='border: 2px solid blue'>");
                }
    			WebContext subCtx = new WebContext(ctx);
    			for(String field : SurveyMain.REDO_FIELD_LIST) {
    				if(ctx.hasField(field)) {
    					subCtx.addQuery(field, ctx.field(field));
    				}
    			}
    			if(ctx.hasField(SurveyMain.QUERY_LOCALE)) {
    				subCtx.showCoverageSettingForLocale();	
    			} else {
    				subCtx.showCoverageSetting();
    			}
                if(!curSetting.equals(WebContext.COVLEV_RECOMMENDED)) {
                    ctx.println("</span>");
                }
                String forum = ctx.getLocale().getLanguage();
    			%><%=SurveyForum.forumLink(subCtx,forum)%><%=SurveyForum.forumFeedIcon(subCtx, forum)%><%
    		} else {
    			ctx.print(" <smaller>Coverage Level: "+curSetting+"</smaller>");
    		}
    		ctx.print(" | ");
            ctx.sm.printMenu(ctx, doWhat, "options", "Manage", SurveyMain.QUERY_DO);
    		//ctx.println(" | <a class='deactivated' _href='"+ctx.url()+ctx.urlConnector()+"do=mylocs"+"'>My locales</a>");
    		if(UserRegistry.userIsAdmin(ctx.session.user)) {
    			ctx.println("| <a href='" + ctx.context("AdminPanel.jsp") + "?vap=" + ctx.sm.vap + "'>[Admin Panel]</a>");
    			if(ctx.session.user.id == 1) {
    				ctx.println(" | <a href='" + ctx.base() + "?sql=" + ctx.sm.vap + "'>[Raw SQL]</a>");
    			}
    		}
    	}
    	ctx.println("</td></tr></table>");

    	ctx.flush();
 %>