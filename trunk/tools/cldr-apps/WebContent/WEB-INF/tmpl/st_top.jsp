<%@page import="com.ibm.icu.lang.UCharacter"%>
<%@ include file="/WEB-INF/jspf/stcontext.jspf" %><%
   String title = (ctx==null)?"?":((String)ctx.get("TITLE"));
%>
<!--  st_top.jsp --></head>
<body>
	<div id="toparea">
    <img id="stlogo" width="44" height="48" src='<%= WebContext.context(request, "STLogo"+".png") %>' title="[ST Logo]" alt="[ST Logo]" />
    <div id="toptitle" title='Phase: <%= ctx.sm.phase().toString() %>'>
        <span class='title-cldr'>CLDR <%= ctx.sm.getNewVersion() %> Survey Tool: </span>

    <% CLDRLocale toplocale = ctx.getLocale();
        if(toplocale!=null) { 
            WebContext subCtx2 = (WebContext)ctx.clone();
            subCtx2.addQuery(SurveyMain.QUERY_LOCALE,toplocale.toString());
        %>

        <a class='locales' href="<%= ctx.base() %>">Locales</a> &nbsp;

        <%
        int n = ctx.docLocale.length; // how many levels deep
        int i,j;
         for(i=(n-1);i>0;i--) {
            boolean canModifyL = UserRegistry.userCanModifyLocale(ctx.session.user,ctx.docLocale[i]);
            ctx.print("&raquo;&nbsp; <a title='"+ctx.docLocale[i]+"' class='notselected' href=\"" + ctx.url() + ctx.urlConnector() +SurveyMain.QUERY_LOCALE+"=" + ctx.docLocale[i] + 
                "\">");
            ctx.print(SurveyMain.decoratedLocaleName(ctx.docLocale[i],ctx.docLocale[i].getDisplayName(),""));
            ctx.print("</a> ");
        }
        boolean canModifyL = false&&UserRegistry.userCanModifyLocale(ctx.session.user,ctx.getLocale());
        ctx.print("&raquo;&nbsp;");
        ctx.print("<span title='"+ctx.getLocale()+"' class='curLocale'>");
        SurveyMain.printMenu(subCtx2, ctx.field(SurveyMain.QUERY_DO), SurveyMain.xMAIN, 
            SurveyMain.decoratedLocaleName(ctx.getLocale(), ctx.getLocale().getDisplayName()+(canModifyL?SurveyMain.modifyThing(ctx):""), "") );
        ctx.print("</span>");

        CLDRLocale dcParent = CLDRLocale.getInstance(ctx.sm.supplemental.defaultContentToParent(toplocale.toString()));
        String dcChild = ctx.sm.supplemental.defaultContentToChild(ctx.getLocale().toString());
        if (dcChild != null) {
            String dcChildDisplay = ctx.getLocaleDisplayName(dcChild);
            ctx.println("<span class='dcbox'>" +
            "= "+dcChildDisplay+
                    "<a class='dchelp' target='CLDR-ST-DOCS' href='http://cldr.unicode.org/translation/default-content'>content</a>" 
                 + "</span>");
        }

        
        if(title!=null&&!title.trim().isEmpty()) {%>
        |
    <% } } %>

        <span class='normal-title'><%= title %></span>
    </div>
    </div>
<%@ include file="/WEB-INF/tmpl/stnotices.jspf" %>
<!-- end st_top.jsp -->