<%@page import="org.unicode.cldr.util.VettingViewer"%>
<%@ page import="org.unicode.cldr.web.*" %>
<!--  begin ajax_status.jsp -->
<script type='text/javascript' src='<%= request.getContextPath()+"/dojoroot/dojo/dojo.js" %>'
    djConfig='parseOnLoad: true, isDebug: false'></script>
<script type="text/javascript">
var timerID = -1;

function updateIf(id, txt) {
    var something = document.getElementById(id);
    if(something != null) {
        something.innerHTML = txt;
    }
}

var wasBusted = false;
var wasOk = false;
var loadOnOk = null;
var clickContinue = null;
var surveyRunningStamp = '<%= SurveyMain.surveyRunningStamp.current() %>';
<%
String surveyCurrentLocale = request.getParameter(SurveyMain.QUERY_LOCALE);
String surveyCurrentSection = request.getParameter(SurveyMain.QUERY_SECTION);
String surveyCurrentForum = request.getParameter(SurveyForum.F_XPATH);
if(surveyCurrentLocale!=null&&surveyCurrentLocale.length()>0&&
    (surveyCurrentSection!=null||surveyCurrentForum!=null)) {
%>
 var surveyCurrentLocale = '<%= surveyCurrentLocale %>';
 var surveyCurrentLocaleStamp = 0;
 var surveyNextLocaleStamp = 0;
 var surveyLocaleUrl='&<%= SurveyMain.QUERY_LOCALE %>=<%= surveyCurrentLocale %>';
<% }else{ %>
var surveyCurrentLocale = null;
var surveyCurrentLocaleStamp = 0;
var surveyLocaleUrl='';
<% } %>

function dismissChangeNotice() {
	surveyCurrentLocaleStamp = surveyNextLocaleStamp;
    var locDiv = document.getElementById('stchanged');
    if(locDiv) {
        locDiv.style.display='none';
    }
}

function updateStatus() {
    dojo.xhrGet({
        url:"<%= request.getContextPath() %>/SurveyAjax?what=status"+surveyLocaleUrl,
        handleAs:"json",
        load: function(json){
            if(json.isBusted == 1) {
                wasBusted = true;
            }
            var st_err =  document.getElementById('st_err');
            if(json.err.length > 0) {
               st_err.innerHTML=json.err;
               if(json.surveyRunningStamp!=surveyRunningStamp) {
            	   st_err.innerHTML = st_err.innerHTML + " <b>Note: Lost connection with Survey Tool or it restarted.</b>"
               }
               st_err.className = "ferrbox";
               wasBusted = true;
            } else {
            	if(json.surveyRunningStamp!=surveyRunningStamp) {
                    st_err.className = "ferrbox";
                    st_err.innerHTML="The SurveyTool has been restarted. Please reload this page to continue.";
                    wasBusted=true;
            	}else if(wasBusted == true && (json.isBusted == 0) && (json.isSetup == 1)
                      || (json.surveyRunningStamp!=surveyRunningStamp)) {
                    st_err.innerHTML="Note: Lost connection with Survey Tool or it restarted.";
                    if(clickContinue != null) {
                        st_err.innerHTML = st_err.innerHTML + " Please <a href='"+clickContinue+"'>click here</a> to continue.";
                    } else {
                    	st_err.innerHTML = st_err.innerHTML + " Please reload this page to continue.";
                    }
                    st_err.className = "ferrbox";
                } else {
                   st_err.className = "";
                   st_err.innerHTML="";
                }
            }
            updateIf('progress',json.progress);
            updateIf('uptime',json.uptime);
            updateIf('visitors',json.visitors);
            
            if(json.localeStamp) {
                if(surveyCurrentLocaleStamp==0) {
                	surveyCurrentLocaleStamp = json.localeStamp;
                    //console.log("STATUS0: " + json.localeStampName + "="+json.localeStamp);
                } else {
                	if(json.localeStamp > surveyCurrentLocaleStamp) {
                        //console.log("STATUS>: " + json.localeStampName + "="+json.localeStamp);
                        updateIf('stchanged_loc',json.localeStampName);
                        var locDiv = document.getElementById('stchanged');
                        if(locDiv) {
                            locDiv.style.display='block';
                        }
                        surveyNextLocaleStamp = json.localeStamp;
                	} else {
                        //console.log("STATUS=: " + json.localeStampName + "="+json.localeStamp);
                	}
                }
            }
            
            if((wasBusted == false) && (json.isSetup == 1) && (loadOnOk != null)) {
                window.location.replace(loadOnOk);
            }
        },
        error: function(err, ioArgs){
            var st_err =  document.getElementById('st_err');
            wasBusted = true;
            st_err.className = "ferrbox";
            st_err.innerHTML="Disconnected from Survey Tool: "+err.name + " <br> " + err.message;
            updateIf('progress','<hr><i>(disconnected from Survey Tool)</i></hr>');
            updateIf('uptime','down');
            updateIf('visitors','nobody');
        }
    });
}

function updateTestResults(fieldhash, testResults, what) {
    var e_div = document.getElementById('e_'+fieldhash);
    var v_td = document.getElementById('i_'+fieldhash);
    var v_tr = document.getElementById('r_'+fieldhash);
    var v_tr2 = document.getElementById('r2_'+fieldhash);
    var newHtml = "";
    e_div.className="";
    v_td.className="v_warn";
    if(v_tr!=null) {
            v_tr.className="";
    }
    v_tr2.className="tr_warn";
    newHtml = "";
    
    for(var i=0;i<testResults.length;i++) {
        var tr = testResults[i];
        newHtml += "<p class='tr_"+tr.type+"' title='"+tr.type+"'>";
        if(tr.type == 'Warning') {
            newHtml += "<%= WebContext.iconHtml(request,"warn","Test Warning") %>";
        } else if(tr.type == 'Error') {
            v_tr2.className="tr_err";
            newHtml += "<%= WebContext.iconHtml(request,"stop","Test Error") %>";
            what='error';
        }
        newHtml += testResults[i].message;
        newHtml += "</p>";
    }
    e_div.innerHTML = newHtml;
    return what;
}

function readyToSubmit(fieldhash) {
    var ch_input = document.getElementById('ch_'+fieldhash);
    var submit_btn = document.getElementById('submit_'+fieldhash);
    var cancel_btn = document.getElementById('cancel_'+fieldhash);
    
    ch_input.disabled='1';
    submit_btn.style.display='block';
    cancel_btn.style.display='block';
}

function hideSubmit(fieldhash) {
    var ch_input = document.getElementById('ch_'+fieldhash);
    var submit_btn = document.getElementById('submit_'+fieldhash);
    var cancel_btn = document.getElementById('cancel_'+fieldhash);

    submit_btn.style.display='none';
    cancel_btn.style.display='none';
    ch_input.disabled=null;
}

function isubmit(fieldhash,xpid,locale,session) {
    var ch_input = document.getElementById('ch_'+fieldhash);
    var submit_btn = document.getElementById('submit_'+fieldhash);
    var cancel_btn = document.getElementById('cancel_'+fieldhash);
    
    if(!ch_input) {
    	console.log("Not a field hash: submit " + fieldhash);
    	return;
    }
    hideSubmit(fieldhash);
    ch_input.disabled=null;
    do_change(fieldhash, ch_input.value, '', xpid,locale,session,'submit');
}

function icancel(fieldhash,xpid,locale,session) {
    var ch_input = document.getElementById('ch_'+fieldhash);
    var chtd_input = document.getElementById('chtd_'+fieldhash);
    var submit_btn = document.getElementById('submit_'+fieldhash);
    var cancel_btn = document.getElementById('cancel_'+fieldhash);
    
    if(!ch_input) {
        console.log("Not a field hash: submit " + fieldhash);
        return;
    }
    cancel_btn.style.display='none';
    submit_btn.style.display='none';
    ch_input.disabled=null;
//    chtd_input.style.width='25%';
    ch_input.className='inputboxbig';
}

function refreshRow(fieldhash, xpid, locale, session) {
    var v_tr = document.getElementById('r_'+fieldhash);
    var e_div = document.getElementById('e_'+fieldhash);
    var what = "<%= SurveyAjax.WHAT_GETROW %>";
    var ourUrl = "<%= request.getContextPath() %>/RefreshRow.jsp?what="+what+"&xpath="+xpid +"&_="+locale+"&fhash="+fieldhash+"&vhash="+''+"&s="+session;
    var ourUrlVI =  ourUrl+"&voteinfo=t";
    //console.log("refreshRow('" + fieldhash +"','"+value+"','"+vhash+"','"+xpid+"','"+locale+"','"+session+"')");
    //console.log(" url = " + ourUrl);
    var errorHandler = function(err, ioArgs){
        console.log('Error in refreshRow: ' + err + ' response ' + ioArgs.xhr.responseText);
        v_tr.className="tr_err";
        v_tr.innerHTML = "<td class='v_error' colspan=8><%= WebContext.iconHtml(request,"stop","Page Error") %> Couldn't reload this row- please refresh the page. <br>Error: " + err+"</td>";
        e_div.innerHTML="";
//        var st_err =  document.getElementById('st_err');
//        wasBusted = true;
//        st_err.className = "ferrbox";
//        st_err.innerHTML="Disconnected from Survey Tool while processing a field: "+err.name + " <br> " + err.message;
//        updateIf('progress','<hr><i>(disconnected from Survey Tool)</i></hr>');
//        updateIf('uptime','down');
//        updateIf('visitors','nobody');
    };
    var loadHandler = function(text){
        try {
             var newHtml = "";
             if(text) {
                 v_tr.className='topbar';
                 v_tr.innerHTML = text;
                 e_div.innerHTML = "";
                 e_div.className="";
             } else {
                 v_tr.className='';
                 v_tr.innerHTML = "<td colspan=4><%= WebContext.iconHtml(request,"stop","Page Error") %> Couldn't reload this row- please refresh the page.</td>";
             }
           }catch(e) {
               console.log("Error in ajax get ",e.message);
               console.log(" response: " + text);
               e_div.innerHTML = "<i>JavaScript Error: " + e.message + "</i>";
           }
    };
    var xhrArgs = {
            url: ourUrl,
            handleAs:"text",
            load: loadHandler,
            error: errorHandler
        };
    window.xhrArgs = xhrArgs;
    //console.log('xhrArgs = ' + xhrArgs);
    dojo.xhrGet(xhrArgs);
    
    var voteinfo_div = document.getElementById('voteresults_'+fieldhash);
    if(voteinfo_div) {
    	voteinfo_div.innerHTML="<i>Updating...</i>";
	    var loadHandlerVI = function(text){
	        try {
	             var newHtml = "";
	             if(text) {
	                 voteinfo_div.innerHTML = text;
	                 voteinfo_div.className="";
	             } else {
	                 voteinfo_div.className='';
	                 voteinfo_div.innerHTML = "<td colspan=4><%= WebContext.iconHtml(request,"stop","Page Error") %> Couldn't reload this row- please refresh the page.</td>";
	             }
	           }catch(e) {
	               console.log("Error in ajax get ",e.message);
	               console.log(" response: " + text);
	               voteinfo_div.innerHTML = "<i>Internal Error: " + e.message + "</i>";
	           }
	    };
        var xhrArgsVI = {
                url: ourUrlVI,
                handleAs:"text",
                load: loadHandlerVI,
                error: errorHandler
            };
        //console.log('urlVI = ' + ourUrlVI);
	    dojo.xhrGet(xhrArgsVI);
    }
    
}

// for validating CLDR data values
// do_change(hash, value, xpid, locale)
// divs:    e_HASH = div under teh item
//           v_HASH - the entire td
function do_change(fieldhash, value, vhash,xpid, locale, session,what) {
	var e_div = document.getElementById('e_'+fieldhash);
	var v_td = document.getElementById('i_'+fieldhash);
    var v_tr = document.getElementById('r_'+fieldhash);
    var v_tr2 = document.getElementById('r2_'+fieldhash);
	if(what==null) {
		   if(vhash.length>0) {
			   what = "<%= SurveyAjax.WHAT_SUBMIT %>";
		   } else {
			   what = "<%= SurveyAjax.WHAT_SUBMIT %>";
		   }
	}
	if((!vhash || vhash.length==0) && (!value || value.length==0)) {
		return;
	}
	var ourUrl = "<%= request.getContextPath() %>/SurveyAjax?what="+what+"&xpath="+xpid +"&_="+locale+"&fhash="+fieldhash+"&vhash="+vhash+"&s="+session;
	console.log("do_change('" + fieldhash +"','"+value+"','"+vhash+"','"+xpid+"','"+locale+"','"+session+"')");
//	console.log(" what = " + what);
	console.log(" url = " + ourUrl);
    hideSubmit(fieldhash);
	e_div.innerHTML = '<i>Checking...</i>';
	e_div.className="";
	if(v_tr!=null) {
	    v_tr.className="tr_checking";
	}
    v_tr2.className="tr_checking";
    var st_err =  document.getElementById('st_err');
    var errorHandler = function(err, ioArgs){
    	console.log('Error: ' + err + ' response ' + ioArgs.xhr.responseText);
        e_div.innerHTML = '';
        e_div.className="";
//      v_td.className="v_warn";
        v_tr.className="";
        v_tr2.className="tr_err";
//        var st_err =  document.getElementById('st_err');
//        wasBusted = true;
//        st_err.className = "ferrbox";
//        st_err.innerHTML="Disconnected from Survey Tool while processing a field: "+err.name + " <br> " + err.message;
//        updateIf('progress','<hr><i>(disconnected from Survey Tool)</i></hr>');
//        updateIf('uptime','down');
//        updateIf('visitors','nobody');
    };
    var loadHandler = function(json){
        try {
             var newHtml = "";
             if(json.err.length >0) {
                 v_tr.className="tr_err";
                 v_tr2.className="tr_err";
                 newHtml = "<%= WebContext.iconHtml(request,"stop","Test Error") %> Could not check value. Try reloading the page.<br>"+json.err;
                 e_div.innerHTML = newHtml;
             } else if(json.testResults.length == 0) {
            	 if(what == 'verify') {
	                 e_div.className="";
                     v_tr.className="tr_submit";
                     v_tr2.className="tr_submit";
	                 e_div.innerHTML = newHtml;
                     readyToSubmit(fieldhash);
            	 } else {
                     e_div.className="";
                     v_tr.className="tr_submit";
                     v_tr2.className="tr_submit";
                     newHtml = "<i>Vote Accepted:</i>";
                     e_div.innerHTML = newHtml;
            	 }
             } else {
                 var update = updateTestResults(fieldhash,json.testResults,what);
                 if (update == 'verify') {
                	 readyToSubmit(fieldhash);
                 }
             }
             if(json.submitResultRaw) {
                 e_div.innerHTML = e_div.innerHTML + "<b>Updating...</b><!-- <br><b>SUBMIT RESULTS:</b> <tt>" + json.submitResultRaw+"</tt> <b>Refreshing row...</b> -->";
                 refreshRow(fieldhash, xpid, locale, session);
             }
           }catch(e) {
               console.log("Error in ajax post ",e.message);
               e_div.innerHTML = "<i>Internal Error: " + e.message + "</i>";
           }
    };
    var xhrArgs = {
            url: ourUrl,
            postData: value,
            handleAs:"json",
            load: loadHandler,
            error: errorHandler
        };
    window.xhrArgs = xhrArgs;
    console.log('xhrArgs = ' + xhrArgs);
    dojo.xhrPost(xhrArgs);
}

var timerSpeed = 15000;

function setTimerOn() {
    updateStatus();
    timerID = setInterval(updateStatus, timerSpeed);
}

function resetTimerSpeed(speed) {
	timerSpeed = speed;
	clearInterval(timerID);
	timerID = setInterval(updateStatus, timerSpeed);
}

window.onload = setTimerOn;

</script>

<!--  for vetting -->
<script type="text/javascript">

function changeStyle(hideRegex) {
    for (m in document.styleSheets) {
        var theRules;
        if (document.styleSheets[m].cssRules) {
            theRules = document.styleSheets[m].cssRules;
        } else if (document.styleSheets[m].rules) {
            theRules = document.styleSheets[m].rules;
        }
        for (n in theRules) {
            var rule = theRules[n];
            var sel = rule.selectorText;
            if (sel != undefined && sel.match(/vv/))   {
                var theStyle = rule.style;
                if (sel.match(hideRegex)) {
                    if (theStyle.display == 'table-row') {
                        theStyle.display = null;
                    }
                } else {
                    if (theStyle.display != 'table-row') {
                        theStyle.display = 'table-row';
                    }
                }
            }
        }
    }
}

function setStyles() {
    var hideRegexString = "X1234X";
    for (i=0; i < document.checkboxes.elements.length; i++){
        var item = document.checkboxes.elements[i];
        if (!item.checked) {
            hideRegexString += "|";
            hideRegexString += item.name;
        }
    }
    var hideRegex = new RegExp(hideRegexString);
    changeStyle(hideRegex);
}

</script>
<!--  also for vetting viewer -->
<%= VettingViewer.getHeaderStyles() %>

<% if(!SurveyMain.isUnofficial) { out.println(org.unicode.cldr.tool.ShowData.ANALYTICS); } %>
<!--  end ajax_status.jsp -->
