<%@ include file="/WEB-INF/jspf/report_top.jspf" %>

<%@ page import="com.ibm.icu.util.Currency" %>
<%@ page import="com.ibm.icu.util.ULocale" %>

<!--  debug -->
<div style='border: 1px solid blue; background-color: wheat;'><i>Under Construction</i>
<h3>AJAX result and status</h3>
<div id='st_verr' style='border: 1px dashed blue; margin: 1em; background-color: white;'>Error Message Here</div>
<%
String ajxp = "//ldml/localeDisplayNames/territories/territory[@type=\"AC\"]";
int ajxpid = ctx.sm.xpt.getByXpath(ajxp);
String ajurl = request.getContextPath()+"/SurveyAjax?what=" +  SurveyAjax.WHAT_VERIFY 
   // + "&s="+ ctx.session.id    // for now- not session based.
    + "&xpath="+ajxpid+"&_v=zz";

%>
<i>Session = <%= ctx.session.id %></i><br/>
<i>Test XPath = <%= ajxp %></i><br/>
<i>URL= <tt><a href='<%= ajurl %>'><%= ajurl %></a></tt></i><br>
<script type="text/javascript"><!-- 
function validateItem() {
    dojo.xhrGet({
        url:"SurveyAjax?what=<%= SurveyAjax.WHAT_VERIFY %>&s=<%= ctx.session.id %>&xpath=4&_v=zz",
        handleAs:"json",
        load: function(json){
            var st_err =  document.getElementById('st_verr');
            if(json.err.length > 0) {
               st_err.innerHTML=json.err;
               st_err.className = "ferrbox";
            } else {
               st_err.innerHTML="json";
            }
        },
        error: function(err, ioArgs){
            var st_err =  document.getElementById('st_verr');
            st_err.className = "ferrbox";
            st_err.innerHTML="Disconnected from Survey Tool: "+err;
        }
    });
}
// -->
</script>
</div>
<!--  end DEBUG stuff. -->

<h2>New items for 1.8, or cases where the English has changed</h2>
<%

//  Copy "x=___"  from input to output URL

%><p>The first set are new territories. All of these should be translated.</p><%


subCtx.openTable(); 

             // (OPTIONAL array notation)  
subCtx.showXpath(new String[] 
                 {"//ldml/localeDisplayNames/territories/territory[@type=\"AC\"]",
				  "//ldml/localeDisplayNames/territories/territory[@type=\"CP\"]",
				  "//ldml/localeDisplayNames/territories/territory[@type=\"DG\"]",
				  "//ldml/localeDisplayNames/territories/territory[@type=\"EA\"]",
				  "//ldml/localeDisplayNames/territories/territory[@type=\"IC\"]",
				  "//ldml/localeDisplayNames/territories/territory[@type=\"TA\"]"});

subCtx.closeTable();


             
%><p>The following have alternative values or newly modified English. All of these should be translated.</p><%

out.flush();

subCtx.openTable(); 

subCtx.showXpath("//ldml/localeDisplayNames/territories/territory[@type=\"MM\"]");
subCtx.showXpath("//ldml/localeDisplayNames/territories/territory[@type=\"PS\"]");

subCtx.showXpath("//ldml/localeDisplayNames/territories/territory[@type=\"CD\"]");
subCtx.showXpath("//ldml/localeDisplayNames/territories/territory[@type=\"CD\"][@alt=\"variant\"]");
subCtx.showXpath("//ldml/localeDisplayNames/territories/territory[@type=\"CG\"]");
subCtx.showXpath("//ldml/localeDisplayNames/territories/territory[@type=\"CG\"][@alt=\"variant\"]");
subCtx.showXpath("//ldml/localeDisplayNames/territories/territory[@type=\"CI\"]");
subCtx.showXpath("//ldml/localeDisplayNames/territories/territory[@type=\"CI\"][@alt=\"variant\"]");
subCtx.showXpath("//ldml/localeDisplayNames/territories/territory[@type=\"FK\"]");
subCtx.showXpath("//ldml/localeDisplayNames/territories/territory[@type=\"FK\"][@alt=\"variant\"]");
subCtx.showXpath("//ldml/localeDisplayNames/territories/territory[@type=\"HK\"][@alt=\"short\"]");
subCtx.showXpath("//ldml/localeDisplayNames/territories/territory[@type=\"HK\"]");
subCtx.showXpath("//ldml/localeDisplayNames/territories/territory[@type=\"MK\"]");
subCtx.showXpath("//ldml/localeDisplayNames/territories/territory[@type=\"MK\"][@alt=\"variant\"]");
subCtx.showXpath("//ldml/localeDisplayNames/territories/territory[@type=\"MO\"][@alt=\"short\"]");
subCtx.showXpath("//ldml/localeDisplayNames/territories/territory[@type=\"MO\"]");
subCtx.showXpath("//ldml/localeDisplayNames/territories/territory[@type=\"TL\"]");
subCtx.showXpath("//ldml/localeDisplayNames/territories/territory[@type=\"TL\"][@alt=\"variant\"]");

subCtx.closeTable();
%><p>The following are new cases of language or language variants. Translating these is optional but recommended.</p><%

subCtx.openTable(); 

subCtx.showXpath("//ldml/localeDisplayNames/languages/language[@type=\"yue\"]");
subCtx.showXpath("//ldml/localeDisplayNames/languages/language[@type=\"swb\"]");
            
subCtx.showXpath("//ldml/localeDisplayNames/variants/variant[@type=\"PINYIN\"]");
subCtx.showXpath("//ldml/localeDisplayNames/variants/variant[@type=\"WADEGILE\"]");

subCtx.closeTable();
subCtx.doneWithXpaths(); // print hidden field notifying which bases to accept submission for. 

%>
-->