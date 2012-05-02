<%@ include file="/WEB-INF/jspf/stcontext.jspf" %><!--  menu_top.jspf begin -->

<%!


    static String CALENDARS_ITEMS[] = SurveyMain.CALENDARS_ITEMS;
	static String METAZONES_ITEMS[] = SurveyMain.METAZONES_ITEMS;%><%
	WebContext subCtx = ctx;
	int n;
	String which = (String) subCtx.get("which");
	subCtx.addQuery(SurveyMain.QUERY_LOCALE, ctx.getLocale().toString());
%>
<%!
static void writeMenu(JspWriter jout, WebContext wCtx, SurveyMenus.Section sec, int covlev)  throws java.io.IOException {
    String which = (String) wCtx.get("which");
    PathHeader.PageId pageId = wCtx.getPageId();
    List<SurveyMenus.Section.Page> pages = new ArrayList<SurveyMenus.Section.Page>();
  //  jout.println("covlev="+covlev+"<br>");
    for(SurveyMenus.Section.Page p : sec) {
        // if coverage..
        if(true || p.getCoverageLevel(wCtx.getLocale())<=covlev) {
                    pages.add(p);
                   // jout.println(p.getKey() + " = " + p.getCoverageLevel(wCtx.getLocale()) + "<br>");
        }
    }
    
    boolean any = false;
       if(pageId!=null&& pageId.getSectionId() == sec.getSection()) {
           any = true;
       }
/*     for (int i = 0; !any && (i < pages.size()); i++) {
        if (pages.get(i).toString().equals(which))
            any = true;
    }
 */
    jout.println("<label class='"
            + (any ? "menutop-active" : "menutop-other") + "' >");
    
    if(!any) {
        WebContext ssc = new WebContext(wCtx);
        ssc.setQuery(SurveyMain.QUERY_SECTION, pages.get(0).toString());
        jout.println("<a href='"+ssc.url()+"' style='text-decoration: none;'>");
    }
    jout.println(sec.toString());
    if(!any) {
        jout.println("</a>");
    }

    jout.println("<select class='"
            + (any ? "menutop-active" : "menutop-other")
            + "' onchange='window.location=this.value'>");
    if (!any) {
        jout.println("<option selected value=\"\">Jump to...</option>");
    }
    for (int i = 0; i < pages.size(); i++) {
        String key = pages.get(i).getKey().name();
        WebContext ssc = new WebContext(wCtx);
        ssc.setQuery(SurveyMain.QUERY_SECTION, key);
        jout.print("<option ");
        if(pages.get(i).getCoverageLevel(wCtx.getLocale())>covlev) {
            jout.print(" disabled ");
        }
        if (key.equals(which)) {
            jout.print(" selected ");
        } else {
            jout.print("value=\"" + ssc.url() + "\" ");
        }
        jout.print(">" + pages.get(i).toString());
//        jout.print( " c="+pages.get(i).getCoverageLevel(wCtx.getLocale()));
        jout.println("</option>");
    }
    jout.println("</select>");
    jout.println("</label>");
}
static void writeMenu(JspWriter jout, WebContext wCtx, String title,
			String items[]) throws java.io.IOException {
		String which = (String) wCtx.get("which");
		boolean any = false;
		for (int i = 0; !any && (i < items.length); i++) {
			if (items[i].equals(which))
				any = true;
		}

		jout.println("<label class='"
				+ (any ? "menutop-active" : "menutop-other") + "' >");
		
		if(!any) {
			WebContext ssc = new WebContext(wCtx);
			ssc.setQuery(SurveyMain.QUERY_SECTION, items[0]);
			jout.println("<a href='"+ssc.url()+"' style='text-decoration: none;'>");
		}
		jout.println(title);
		if(!any) {
			jout.println("</a>");
		}

		jout.println("<select class='"
				+ (any ? "menutop-active" : "menutop-other")
				+ "' onchange='window.location=this.value'>");
		if (!any) {
			jout.println("<option selected value=\"\">Jump to...</option>");
		}
		for (int i = 0; i < items.length; i++) {
			WebContext ssc = new WebContext(wCtx);
			ssc.setQuery(SurveyMain.QUERY_SECTION, items[i]);
			jout.print("<option ");
			if (items[i].equals(which)) {
				jout.print(" selected ");
			} else {
				jout.print("value=\"" + ssc.url() + "\" ");
			}
			jout.print(">" + items[i]);
			jout.println("</option>");
		}
		jout.println("</select>");
		jout.println("</label>");
	}
	%>
	
	<%
			String xclass = SurveyMain.R_VETTING.equals(ctx.field(SurveyMain.QUERY_SECTION))?"selected":"notselected";
	        if(true && ctx.session.user!=null) {
	%><a style='font-size: small;' href="<%= ctx.base() %>?_=<%= ctx.getLocale() %>&amp;<%= SurveyMain.QUERY_SECTION %>=<%= SurveyMain.R_VETTING %>" class="<%= xclass %>">Vetting Viewer</a>
        
        <%   } %>
    
<%
            String covlev = ctx.getCoverageSetting();
            Level coverage = Level.COMPREHENSIVE;
            if(covlev!=null && covlev.length()>0) {
                coverage = Level.get(covlev);
            }
            String effectiveCoverageLevel = ctx
                    .getEffectiveCoverageLevel(ctx.getLocale().toString());
            int workingCoverageValue = Level.get(effectiveCoverageLevel)
                    .getLevel();


             for(SurveyMenus.Section sec : ctx.sm.getSTFactory().getSurveyMenus()) {
	            writeMenu(out,ctx, sec, workingCoverageValue);
	        }
		out.flush();
		ctx.flush();
		// commenting out easy steps until we have time to work on it more
		/* ctx.includeFragment("report_menu.jsp");  don't use JSP include, because of variables */
%>
   | <a <%=ctx.atarget("st:supplemental")%> class='notselected' 
            href='http://unicode.org/cldr/data/charts/supplemental/language_territory_information.html#<%=
            ctx.getLocale().getLanguage() %>'>supplemental</a>

        </p>
        <%
        	/* END NON JAVASCRIPT */
			out.flush();
			ctx.flush();
			// commenting out easy steps until we have time to work on it more
			
			/* ctx.includeFragment("report_menu.jsp");  don't use JSP include, because of variables */
			
%>
<!--  menu_top.jspf end -->