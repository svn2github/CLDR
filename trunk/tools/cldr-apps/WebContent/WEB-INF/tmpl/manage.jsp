<%@ include file="/WEB-INF/jspf/stcontext.jspf"%><!--  manage.jsp begin -->

<h3>Subpages</h3>

<%
WebContext subCtx = new WebContext(ctx);
subCtx.removeQuery("do");

String helpLink = subCtx.getString("helpLink");
String helpName = subCtx.getString("helpName");

        String doWhat = subCtx.field(SurveyMain.QUERY_DO);

        //        subCtx.println("The SurveyTool is in phase <b><span title='"+phase().name()+"'>"+phase().toString()+"</span></b> for version <b>"+getNewVersion()+"</b><br>" );

        if(subCtx.session.user != null)  {
            boolean haveCookies = (subCtx.getCookie(SurveyMain.QUERY_EMAIL)!=null&&subCtx.getCookie(SurveyMain.QUERY_PASSWORD)!=null);
            subCtx.println(subCtx.session.user.name + " (" + subCtx.session.user.org + ") ");
            if(!haveCookies && !subCtx.hasField(SurveyMain.QUERY_SAVE_COOKIE)) {
                subCtx.println(" <a class='notselected' href='"+subCtx.url()+subCtx.urlConnector()+SurveyMain.QUERY_SAVE_COOKIE+"=iva'><b>Remember Me!</b></a>");
            }
            subCtx.print(" | ");
            subCtx.sm.printMenu(subCtx, doWhat, "listu", "My Account", SurveyMain.QUERY_DO);
            if(UserRegistry.userIsTC(subCtx.session.user)) {
                subCtx.print(" | ");
                subCtx.sm.printMenu(subCtx, doWhat, "list", "Manage " + subCtx.session.user.org + " Users", SurveyMain.QUERY_DO);
                subCtx.print(" | ");
                //              if(this.phase()==Phase.VETTING || this.phase() == Phase.SUBMIT) {
            } else {
                if(UserRegistry.userIsVetter(subCtx.session.user)) {
                    subCtx.print(" | ");
                    subCtx.sm.printMenu(subCtx, doWhat, "list", "List " + subCtx.session.user.org + " Users", subCtx.sm.QUERY_DO);
                } else if(UserRegistry.userIsLocked(subCtx.session.user)) {
                    subCtx.println("<b>LOCKED: Note: your account is currently locked. Please contact " + subCtx.session.user.org + "'s CLDR Technical Committee member.</b> ");
                }
            }
            subCtx.sm.printMenu(subCtx, doWhat, "disputed", "Dispute Resolution", SurveyMain.QUERY_DO); 
            if(SurveyMain.isPhaseReadonly()) {
                subCtx.println("<br>(The SurveyTool is in a read-only state, no changes may be made.)");
            } else if(SurveyMain.isPhaseVetting() 
                    && UserRegistry.userIsStreet(subCtx.session.user)
                    && !UserRegistry.userIsExpert(subCtx.session.user)) {
                subCtx.println("<br> (Note: in the Vetting phase, you may not submit new data.) ");
            } else if(SurveyMain.isPhaseClosed() && !UserRegistry.userIsTC(subCtx.session.user)) {
                subCtx.println("<br>(SurveyTool is closed to vetting and data submissions.)");
            }
            subCtx.println("<br/>");
            if((subCtx.session != null) && (subCtx.session.user != null) && (SurveyMain.isPhaseVettingClosed() && subCtx.session.user.userIsSpecialForCLDR15(null))) {
                subCtx.println("<b class='selected'> you have been granted extended privileges for the CLDR "+subCtx.sm.getNewVersion()+" vetting period.</b><br>");
            }
        }
        if(subCtx.sm.dbUtils.hasDataSource()) {
            subCtx.println(" | <a class='notselected' href='"+subCtx.jspUrl("statistics.jsp")+"'>Statistics</a>");
        }
        if(subCtx.sm.isUnofficial && (subCtx.session!=null&&subCtx.session.user!=null)) {
            subCtx.println(" | <i class='scary'>Experimental:</i>&nbsp;");
            subCtx.println("<a class='notselected' href='"+subCtx.jspUrl("upload.jsp"  )+ "&amp;s=" + subCtx.session.id+"'>Upload XML</a>");
            if(subCtx.session.user.userlevel<=UserRegistry.TC) {
                subCtx.println("| <a class='notselected' href='"+subCtx.jspUrl("vsummary.jsp"  ) +"'>Vetting Summary</a>");
            }
        }
        subCtx.flush();
 %>
<!--  manage.jsp ends -->