// survey.js  -Copyright (C) 2012 IBM Corporation and Others. All Rights Reserved.
// move anything that's not dynamically generated here.

// These need to be available @ bootstrap time.

dojo.require("dojo.i18n");
dojo.require("dojo.string");
dojo.requireLocalization("surveyTool", "stui");

var stui = {online: "Online", 			disconnected: "Disconnected", startup: "Starting up..."};

var onIE =  (navigator && navigator.appName && navigator.appName == 'Microsoft Internet Explorer');


stui_str = function(x) {
    if(stui && stui[x]) {
    	return stui[x];
    } else {
    	return x;
    }
};

function stStopPropagation(e) {
	if(!e) {
		return false;
	} else if(e.stopPropagation) {
		return e.stopPropagation();
	} else if(e.cancelBubble) {
		return e.cancelBubble();
	} else {
		// hope for the best
	}
}

var disconnected = false;

var stdebug_enabled=(window.location.search.indexOf('&stdebug=')>-1);

var queueOfXhr=[];

var queueOfXhrTimeout=null;

var myLoad0= null;
var myErr0 = null;

var processXhrQueue = function() {
	if(disconnected) return;
	if(!queueOfXhr || queueOfXhr.length==0) {
		queueOfXhr=[];
		stdebug("PXQ: 0");
		queueOfXhrTimeout=null;
		return; // nothing to do, reset.
	} else {
		var top =queueOfXhr.shift();
		
		top.load2 = top.load;
		top.err2 = top.err;
		top.load=function(){return myLoad0(top,arguments); };
		top.err=function(){return myErr0(top,arguments); };
		if(top.postData) {
			stdebug("PXQ("+queueOfXhr.length+"): dispatch POST " + top.url);
			dojo.xhrPost(top);
		} else {
			stdebug("PXQ("+queueOfXhr.length+"): dispatch GET " + top.url);
			dojo.xhrGet(top);
		}
	}
};

var xhrQueueTimeout = 3;
myLoad0 = function(top,args) {
	stdebug("myLoad0!:" + top.url + " - a="+args.length);
	var r = top.load2(args[0],args[1]);
	queueOfXhrTimeout = setTimeout(processXhrQueue, xhrQueueTimeout);
	return r;
};

myErr0 = function(top,args) {
	stdebug("myErr0!:" + top.url+ " - a="+args.toString());
	var r = top.err2.call(args[0],args[1]);
	queueOfXhrTimeout = setTimeout(processXhrQueue, xhrQueueTimeout);
	return r;
};


function queueXhr(xhr) {
	queueOfXhr.push(xhr);
	stdebug("pushed:  PXQ="+queueOfXhr.length + ", postData: " + xhr.postData);
	if(!queueOfXhrTimeout) {
		queueOfXhrTimeout = setTimeout(processXhrQueue, xhrQueueTimeout);
	}
}

function stdebug(x) {
	if(stdebug_enabled) {
		console.log(x);
	}
}

stdebug('stdebug is enabled.');

function removeAllChildNodes(td) {
	while(td.firstChild) {
		td.removeChild(td.firstChild);
	}
}

function readyToSubmit(fieldhash) {
//    var ch_input = document.getElementById('ch_'+fieldhash);
//    var submit_btn = document.getElementById('submit_'+fieldhash);
//    var cancel_btn = document.getElementById('cancel_'+fieldhash);
    
//    ch_input.disabled='1';
//    submit_btn.style.display='block';
//    cancel_btn.style.display='block';
}

function hideSubmit(fieldhash) {
//    var ch_input = document.getElementById('ch_'+fieldhash);
//    var submit_btn = document.getElementById('submit_'+fieldhash);
    var cancel_btn = document.getElementById('cancel_'+fieldhash);

//    submit_btn.style.display='none';
    cancel_btn.style.display='none';
//    ch_input.disabled=null;
}

function isubmit(fieldhash,xpid,locale,session) {
    var ch_input = document.getElementById('ch_'+fieldhash);
//    var submit_btn = document.getElementById('submit_'+fieldhash);
//    var cancel_btn = document.getElementById('cancel_'+fieldhash);
    
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
//    var chtd_input = document.getElementById('chtd_'+fieldhash);
//    var submit_btn = document.getElementById('submit_'+fieldhash);
    var cancel_btn = document.getElementById('cancel_'+fieldhash);
    
    if(!ch_input) {
        console.log("Not a field hash: submit " + fieldhash);
        return;
    }
    cancel_btn.style.display='none';
//    submit_btn.style.display='none';
    ch_input.disabled=null;
//    chtd_input.style.width='25%';
    ch_input.className='inputboxbig';
    alreadyVerifyValue[fieldhash]=null;
}

var timerID = -1;

function updateIf(id, txt) {
    var something = document.getElementById(id);
    if(something != null) {
        something.innerHTML = txt;
    }
}

// work around IE8 fail

function listenFor(what, event, fn, ievent) {
	if(!(what._stlisteners)) {
		what._stlisteners={};
	}
	
	if(what.addEventListener) {
		if(what._stlisteners[event]) {
			if(what.removeEventListener) {
				what.removeEventListener(event,what._stlisteners[event],false);
			} else {
				console.log("Err: no removeEventListener on " + what);
			}
		}
		what.addEventListener(event,fn,false);
	} else {
		if(!ievent) {
			ievent = "on"+event;
		}
		if(what._stlisteners[event]) {
			what.detachEvent(ievent,what._stlisteners[event]);
		}
		what.attachEvent(ievent,fn);
	}
	what._stlisteners[event]=fn;
}
//function unListen(what, event, ievent) {
//	if(what.removeEventListener) {
//		what.removeEventListener(event,false);
//	} else {
//		if(!ievent) {
//			ievent = "on"+event;
//		}
//		what.attachEvent(ievent,fn);
//	}
//}
// ?!!!!
if(!Object.keys) {
	Object.keys = function(x) {
		var r = [];
		for (j in x) {
			r.push(j);
		}
		return r;
	};
}

function getAbsolutePosition (x) {
    var hh = 0;
    var vv = 0;
    for(var xx=x;xx.offsetParent;xx=xx.offsetParent) {
        hh += xx.offsetLeft;
        vv += xx.offsetTop;
    }
    return {left:hh, top: vv};
}

var wasBusted = false;
var wasOk = false;
var loadOnOk = null;
var clickContinue = null;
 var surveyNextLocaleStamp = 0;
 
 function busted() {
	 disconnected = true;
	 //console.log("disconnected.");
	 document.getElementsByTagName("body")[0].className="disconnected"; // hide buttons when disconnected.
 }

// hashtable of items already verified
var alreadyVerifyValue = {};

var showers={};

var deferUpdates = 0;
var deferUpdateFn = {};


function doDeferredUpdates() {
	if(deferUpdateFn==null) {
		return;
	}
	for(i in deferUpdateFn) {
		if(deferUpdateFn[i]) {
			var fn = deferUpdateFn[i];
			deferUpdateFn[i]=null;
			fn();
		}
	}
}

function setDefer(defer) {
	if(defer) {
		deferUpdates++;
	} else {
		deferUpdates--;
	}
	if(deferUpdates<=0) {
		doDeferredUpdates();
	}
	stdebug("deferUpdates="+deferUpdates);
}

function deferUpdate(what, fn) {
	deferUpdateFn[what]=fn;
}

function undeferUpdate(what) {
	deferUpdateFn[what]=null;
}

function doUpdate(what,fn) {
	if(deferUpdates>0) {
		updateAjaxWord(stui_str('newDataWaiting'));
		deferUpdate(what,fn);
	} else {
		fn();
		undeferUpdate(what);
	}
}

function handleChangedLocaleStamp(stamp,name) {
	if(disconnected) return;
	if(stamp <= surveyNextLocaleStamp) {
		return;
	}
	if(Object.keys(showers).length==0) {
        //console.log("STATUS>: " + json.localeStampName + "="+json.localeStamp);
        updateIf('stchanged_loc',name);
        var locDiv = document.getElementById('stchanged');
        if(locDiv) {
            locDiv.style.display='block';
        }
	} else {
		for(i in showers) {
			showers[i]();
		}
	}
	stdebug("Reloaded due to change: " + stamp);
    surveyNextLocaleStamp = stamp;
}

////    updateStatusBox({err: err.message, err_name: err.name, disconnected: true});
/*
 * {
"status": {
"memfree": 378.6183984375,
"lockOut": false,
"pages": 16,
"specialHeader": "Welcome to SurveyTool@oc7426272865.ibm.com. Please edit /home/srl/apache-tomcat-7.0.8/cldr/cldr.properties to change CLDR_HEADER (to change this message), or comment it out entirely.",
"dbopen": 0,
"users": 1,
"uptime": "uptime: 49:47",
"isUnofficial": true,
"memtotal": 492.274,
"sysprocs": 8,
"isSetup": true,
"sysload": 0.33,
"dbused": 1439,
"guests": 0,
"surveyRunningStamp": 1331941056940
},
"isSetup": "0",
"err": "",
"visitors": "",
"SurveyOK": "1",
"uptime": "",
"isBusted": "0",
"progress": "(obsolete-progress)"
}
 */

var progressWord = null;
var ajaxWord = null;

function showWord() {
	var p = dojo.byId("progress");	
	var oneword = dojo.byId("progress_oneword");
	if(oneword==null) { // nowhere to show
		return;
	}
	if(disconnected || (progressWord&&progressWord=="disconnected")) { // top priority
		oneword.innerHTML = stopIcon +  stui_str('disconnected');
		p.className = "progress-disconnected";
		busted();
	} else if(ajaxWord) {
		p.className = "progress-ok";
		oneword.innerHTML = ajaxWord;
	} else if(!progressWord || progressWord == "ok") {
		p.className = "progress-ok";
		oneword.innerHTML = stui_str('online');
	} else if(progressWord=="startup") {
		p.className = "progress-ok";
		oneword.innerHTML = stui_str('startup');
	}
}

function updateProgressWord(prog) {
	progressWord = prog;
	showWord();
}

function updateAjaxWord(ajax) {
	ajaxWord = ajax;
	showWord();
}

var saidDisconnect=false;
function handleDisconnect(why, json) {
	updateProgressWord("disconnected");
	if(!saidDisconnect) {
		saidDisconnect=true;
		if(json&&json.err) {
			why = why + " err="+json.err;
		}
		console.log("Disconnect: " + why);
		var oneword = dojo.byId("progress_oneword");
		if(oneword) {
			oneword.title = "Disconnected: " + why;
		}
		if(json) {
			stdebug("JSON: " + json.toString());
		}
	}
}

var updateParts = null;

var cacheKillStamp = surveyRunningStamp;

function cacheKill() {
	if(!cacheKillStamp || cacheKillStamp<surveyRunningStamp) {
		cacheKillStamp=surveyRunningStamp;
	}
	cacheKillStamp++;
	
	return "&cacheKill="+cacheKillStamp;
}

function updateStatusBox(json) {
	if(json.disconnected) {
		handleDisconnect("Misc Disconnect", json);
	} else if (json.SurveyOK==0) {
		handleDisconnect("Server says: SurveyOK=0 - ", json);
	} else if (json.status && json.status.isBusted) {
		handleDisconnect("Server says: busted " + json.status.isBusted, json);
	} else if(!json.status) {
		handleDisconnect("!json.status",json);
	} else if(json.status.surveyRunningStamp!=surveyRunningStamp) {
		handleDisconnect("Server restarted since page was loaded",json);
	} else if(json.status && json.status.isSetup==false && json.SurveyOK==1) {
		updateProgressWord("startup");
	} else {
		updateProgressWord("ok");
	}
	
	/* 
"memfree": 378.6183984375,
"lockOut": false,
"pages": 16,
"specialHeader": "Welcome to SurveyTool@oc7426272865.ibm.com. Please edit /home/srl/apache-tomcat-7.0.8/cldr/cldr.properties to change CLDR_HEADER (to change this message), or comment it out entirely.",
"dbopen": 0,
"users": 1,
"uptime": "uptime: 49:47",
"isUnofficial": true,
"memtotal": 492.274,
"sysprocs": 8,
"isSetup": true,
"sysload": 0.33,
"dbused": 1439,
"guests": 0,
"surveyRunningStamp": 1331941056940
	 */
	if(json.status) {
		if(!updateParts) {
			var visitors = dojo.byId("visitors");
			updateParts = {
					visitors: visitors,
					ug: document.createElement("span"),
					load: document.createElement("span"),
					db: document.createElement("span")
			};
		}
		//"~1 users, 8pg/uptime: 38:44/load:28% db:0/1"
		
		
		var ugtext = "~";
		ugtext = ugtext + (json.status.users) + " users, ";
		if(json.status.guests > 0) {
			ugtext= ugtext + (json.status.guests) + " guests, ";
		}
		ugtext=ugtext+(json.status.pages)+"pg/"+json.status.uptime;
		removeAllChildNodes(updateParts.ug);
		updateParts.ug.appendChild(document.createTextNode(ugtext));

		removeAllChildNodes(updateParts.load);
		updateParts.load.appendChild(document.createTextNode("Load:"+json.status.sysload));

		removeAllChildNodes(updateParts.db);
		updateParts.db.appendChild(document.createTextNode("db:"+json.status.dbopen+"/"+json.status.dbused));


		
		var fragment = document.createDocumentFragment();
		fragment.appendChild(updateParts.ug);
		fragment.appendChild(document.createTextNode(" "));
		fragment.appendChild(updateParts.load);
		fragment.appendChild(document.createTextNode(" "));
		fragment.appendChild(updateParts.db);
		removeAllChildNodes(updateParts.visitors);
		updateParts.visitors.appendChild(fragment);
	}
}

var timerSpeed = 15000;

function updateStatus() {
	if(disconnected) return;
//	stdebug("UpdateStatus...");
    dojo.xhrGet({
        url: contextPath + "/SurveyAjax?what=status"+surveyLocaleUrl+cacheKill(),
        handleAs:"json",
        timeout: 15000,
        load: function(json){
            if(json.status&&json.status.isBusted) {
                wasBusted = true;
                busted();
            }
            var st_err =  document.getElementById('st_err');
            if(json.err != null && json.err.length > 0) {
               st_err.innerHTML=json.err;
               if(json.status&&json.status.surveyRunningStamp!=surveyRunningStamp) {
            	   st_err.innerHTML = st_err.innerHTML + " <b>Note: Lost connection with Survey Tool or it restarted.</b>";
                   updateStatusBox({disconnected: true});
               }
               st_err.className = "ferrbox";
               wasBusted = true;
               busted();
            } else {
            	if(json.status.surveyRunningStamp!=surveyRunningStamp) {
                    st_err.className = "ferrbox";
                    st_err.innerHTML="The SurveyTool has been restarted. Please reload this page to continue.";
                    wasBusted=true;
                    busted();
            	}else if(wasBusted == true && 
            			(!json.status.isBusted) 
                      || (json.status.surveyRunningStamp!=surveyRunningStamp)) {
                    st_err.innerHTML="Note: Lost connection with Survey Tool or it restarted.";
                    if(clickContinue != null) {
                        st_err.innerHTML = st_err.innerHTML + " Please <a href='"+clickContinue+"'>click here</a> to continue.";
                    } else {
                    	st_err.innerHTML = st_err.innerHTML + " Please reload this page to continue.";
                    }
                    st_err.className = "ferrbox";
                    busted();
                } else {
                   st_err.className = "";
                   removeAllChildNodes(st_err);
                }
            }
            updateStatusBox(json);
            
            if(json.localeStamp) {
                if(surveyNextLocaleStamp==0) {
                	surveyNextLocaleStamp = json.localeStamp;
                    stdebug("STATUS0: " + json.localeStampName + "="+json.localeStamp);
                } else {
                	if(json.localeStamp > surveyNextLocaleStamp) {
                        stdebug("STATUS=: " + json.localeStampName + "="+json.localeStamp + " > " + surveyNextLocaleStamp);
                		handleChangedLocaleStamp(json.localeStamp, json.localeStampName);
                	} else {
                        stdebug("STATUS=: " + json.localeStampName + "="+json.localeStamp + " <= " + surveyNextLocaleStamp);
                	}
                }
            }
            
            if((wasBusted == false) && (json.status.isSetup) && (loadOnOk != null)) {
                window.location.replace(loadOnOk);
            } else {
            	setTimeout(updateStatus, timerSpeed)
            }
        },
        error: function(err, ioArgs){
//            var st_err =  document.getElementById('st_err');
            wasBusted = true;
//            st_err.className = "ferrbox";
//            st_err.innerHTML="Disconnected from Survey Tool: "+err.name + " <br> " + err.message;
            updateStatusBox({err: err.message, err_name: err.name, disconnected: true});
//            updateIf('uptime','down');
//            updateIf('visitors','nobody');
        }
    });
}


function setTimerOn() {
    updateStatus();
//    timerID = setInterval(updateStatus, timerSpeed);
    
}

function resetTimerSpeed(speed) {
	timerSpeed = speed;
//	clearInterval(timerID);
//	timerID = setInterval(updateStatus, timerSpeed);
}

listenFor(window,'load',setTimerOn);


function getTestKind(testResults) {
	if(!testResults) {
		return null;
	}
	var theKind =  null;
    for(var i=0;i<testResults.length;i++) {
        var tr = testResults[i];
        if(tr.type == 'Warning') {
        	theKind = tr.type;
        } else if(tr.type == 'Error') {
        	return tr.type;
        }
    }
    return theKind;
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
    
    if(testResults)
    for(var i=0;i<testResults.length;i++) {
        var tr = testResults[i];
        newHtml += "<p class='tr_"+tr.type+"' title='"+tr.type+"'>";
        if(tr.type == 'Warning') {
            newHtml += warnIcon;
            //what='warn';
        } else if(tr.type == 'Error') {
            v_tr2.className="tr_err";
            newHtml += stopIcon;
            what='error';
        }
        newHtml += testResults[i].message;
        newHtml += "</p>";
    }
    e_div.innerHTML = newHtml;
    return what;
}


function refreshRow(fieldhash, xpid, locale, session) {
    var v_tr = document.getElementById('r_'+fieldhash);
    var e_div = document.getElementById('e_'+fieldhash);
    var what = WHAT_GETROW;
    var ourUrl = contextPath + "/RefreshRow.jsp?what="+what+"&xpath="+xpid +"&_="+locale+"&fhash="+fieldhash+"&vhash="+''+"&s="+session;
    var ourUrlVI =  ourUrl+"&voteinfo=t";
    //console.log("refreshRow('" + fieldhash +"','"+value+"','"+vhash+"','"+xpid+"','"+locale+"','"+session+"')");
    //console.log(" url = " + ourUrl);
    var errorHandler = function(err, ioArgs){
        console.log('Error in refreshRow: ' + err + ' response ' + ioArgs.xhr.responseText);
        v_tr.className="tr_err";
        v_tr.innerHTML = "<td class='v_error' colspan=8>" + stopIcon + " Couldn't reload this row- please refresh the page. <br>Error: " + err+"</td>";
        removeAllChildNodes(e_div);
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
//             var newHtml = "";
             if(text) {
                 v_tr.className='topbar';
                 v_tr.innerHTML = text;
                 removeAllChildNodes(e_div);
                 e_div.className="";
             } else {
                 v_tr.className='';
                 v_tr.innerHTML = "<td colspan=4>" + stopIcon + " Couldn't reload this row- please refresh the page.</td>";
             }
           }catch(e) {
               console.log("Error in ajax get ",e.message);
               console.log(" response: " + text);
               e_div.innerHTML = "<i>JavaScript Error: " + e.message + "</i>";
           }
    };
    var xhrArgs = {
            url: ourUrl+cacheKill(),
            handleAs:"text",
            load: loadHandler,
            error: errorHandler
        };
    //window.xhrArgs = xhrArgs;
    //console.log('xhrArgs = ' + xhrArgs);
    dojo.xhrGet(xhrArgs);
    
    var voteinfo_div = document.getElementById('voteresults_'+fieldhash);
    if(voteinfo_div) {
    	voteinfo_div.innerHTML="<i>Updating...</i>";
	    var loadHandlerVI = function(text){
	        try {
//	             var newHtml = "";
	             if(text) {
	                 voteinfo_div.innerHTML = text;
	                 voteinfo_div.className="";
	             } else {
	                 voteinfo_div.className='';
	                 voteinfo_div.innerHTML = "<td colspan=4>" + stopIcon + " Couldn't reload this row- please refresh the page.</td>";
	             }
	           }catch(e) {
	               console.log("Error in ajax get ",e.message);
	               console.log(" response: " + text);
	               voteinfo_div.innerHTML = "<i>Internal Error: " + e.message + "</i>";
	           }
	    };
        var xhrArgsVI = {
                url: ourUrlVI + cacheKill(),
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
//	var v_td = document.getElementById('i_'+fieldhash);
    var v_tr = document.getElementById('r_'+fieldhash);
    var v_tr2 = document.getElementById('r2_'+fieldhash);
    alreadyVerifyValue[fieldhash]=null;
    if(what==null) {
		 what = WHAT_SUBMIT;
	}
	if((!vhash || vhash.length==0) && (!value || value.length==0)) {
		return;
	}
	var ourUrl = contextPath + "/SurveyAjax?what="+what+"&xpath="+xpid +"&_="+locale+"&fhash="+fieldhash+"&vhash="+vhash+"&s="+session;
	//console.log("do_change('" + fieldhash +"','"+value+"','"+vhash+"','"+xpid+"','"+locale+"','"+session+"')");
//	console.log(" what = " + what);
//	console.log(" url = " + ourUrl);
    hideSubmit(fieldhash);
	e_div.innerHTML = '<i>Checking...</i>';
	e_div.className="";
	if(v_tr!=null) {
	    v_tr.className="tr_checking";
	}
    v_tr2.className="tr_checking";
//    var st_err =  document.getElementById('st_err');
    var errorHandler = function(err, ioArgs){
    	console.log('Error: ' + err + ' response ' + ioArgs.xhr.responseText);
    	removeAllChildNodes(e_div);
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
             if(json.err && json.err.length >0) {
                 v_tr.className="tr_err";
                 v_tr2.className="tr_err";
                 newHtml = stopIcon + " Could not check value. Try reloading the page.<br>"+json.err;
                 e_div.innerHTML = newHtml;
             } else if(json.testResults && json.testResults.length == 0) {
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
               console.log("Error in ajax post [do_change]  ",e.message);
               e_div.innerHTML = "<i>Internal Error: " + e.message + "</i>";
           }
    };
    var xhrArgs = {
            url: ourUrl+cacheKill(),
            postData: value,
            handleAs:"json",
            load: loadHandler,
            error: errorHandler
        };
    //window.xhrArgs = xhrArgs;
//    console.log('xhrArgs = ' + xhrArgs);
    dojo.xhrPost(xhrArgs);
}



function cloneAnon(i) {
	if(i==null) return null;
	var o = i.cloneNode(true);
	if(o.id) {
		o.id = null;
	}
	return o;
}

function localizeAnon(o) {
	if(o&&o.childNodes) for(var i=0;i<o.childNodes.length;i++) {
		var k = o.childNodes[i];
		if(k.id && k.id.indexOf("stui-html")==0) {
			var key = k.id.slice(5);
			if(stui[key]) {
				k.innerHTML=stui[key];
			}
			k.id=null;
		} else {
			localizeAnon(k);
		}
	}
}
function localizeFlyover(o) {
	if(o&&o.childNodes) for(var i=0;i<o.childNodes.length;i++) {
		var k = o.childNodes[i];
		if(k.title && k.title.indexOf("$")==0) {
			var key = k.title.slice(1);
			if(stui[key]) {
				k.title=stui[key];
			} else {
				k.title=null;
			}
		} else {
			localizeFlyover(k);
		}
	}
}

function cloneLocalizeAnon(i) {
	var o = cloneAnon(i);
	if(o) localizeAnon(o);
	return o;
}

function getTagChildren(tr) {
	var rowChildren = [];
	
	for(k in tr.childNodes) {
		var t = tr.childNodes[k];
		if(t.tagName) {
			rowChildren.push(t);
		}
	}
	return rowChildren;
}

function showLoader(loaderDiv, text) {
	updateAjaxWord(text);
//	updateIf("progress_ajax",text);
//	console.log("Load: " + text);
//	return;
//	
//	var para = loaderDiv.getElementsByTagName("p");
//	if(para) {
//		para=para[0];
//	} else {
//		para = loaderDiv;
//	}
//	para.innerHTML = text;
//	loaderDiv.style.display="";
}

function hideLoader(loaderDiv) {
	updateAjaxWord(null);
//	updateIf("progress_ajax","");
//	loaderDiv.style.display="none";
}

function wireUpButton(button, tr, theRow, vHash,box) {
	if(box) {
		button.id="CHANGE_" + tr.rowHash;
		vHash="";
		box.onchange=function(){ 
			handleWiredClick(tr,theRow,vHash,box,button,'submit'); 
			return false; 
		};
		box.onkeypress=function(e){ 
			if(!e || !e.keyCode)  {
				return true; // not getting the point here.
			} else if(e.keyCode == 13) {
				handleWiredClick(tr,theRow,vHash,box,button); 
				return false;
//			} else if(e.keyCode ==9) { // TAB
//				handleWiredClick(tr,theRow,vHash,box,button); 
//				return false;
			} else {
				return true;
			}
		};
	} else if(vHash==null) {
		button.id="NO_" + tr.rowHash;
		vHash="";
	} else {
		button.id = "v"+vHash+"_"+tr.rowHash;
	}
	listenFor(button,"click",
			function(e){ handleWiredClick(tr,theRow,vHash,box,button); stStopPropagation(e); return false; });
	
	if(theRow.voteVhash==vHash && !box) {
		button.className = "ichoice-x";
	} else {
		button.className = "ichoice-o";
	}
}

function addIcon(td, className) {
	var star = document.createElement("span");
	star.className=className;
	star.innerHTML="&nbsp; &nbsp;";
	td.appendChild(star);
	return star;
}

var gPopStatus = {
		unShow: null,
		lastShown: null,
		lastTr: null,
		popToken: 0
};

function showInPop(str,tr, theObj, fn, immediate) {
}

function listenToPop(str, tr, theObj, fn) {
	listenFor(theObj, "mouseover",
			function(e) {
				showInPop(str, tr, theObj, fn, false);
				stStopPropagation(e);
				return false;
			});
	listenFor(theObj, "click",
			function(e) {
				showInPop(str, tr, theObj, fn, true);
				stStopPropagation(e);
				return false;
			});
}


function getPopToken() {
	return gPopStatus.popToken;
}

function incrPopToken(x) {
	++gPopStatus.popToken;
	//stdebug("PT@"+gPopStatus.popToken+" - " + x);
	return gPopStatus.popToken;
}


function hidePopHandler(e){ 		
	window.hidePop(null);
	stStopPropagation(e); return false; 
}
function removeClass(obj, className) {
	if(obj.className.indexOf(className)>-1) {
		obj.className = obj.className.substring(className.length+1);
	}
}
function addClass(obj, className) {
	if(obj.className.indexOf(className)==-1) {
		obj.className = className+" "+obj.className;
	}
}
dojo.ready(function() {
	var unShow = null;
//	var lastShown = null;
	var pucontent = dojo.byId("pu-content");
	var puouter=pucontent;
	var pubody=pucontent;
	var nudgeh=0;
//	var nudgev=0;
//	var nudgehpost=0;
//	var nudgevpost=0;
//	var hardleft = 10;
//	var hardtop = 10;
//	var pupeak_height = pupeak.offsetHeight;
	if(!pubody) return;
	var hideInterval=null;
	
	function setLastShown(obj) {
		if(gPopStatus.lastShown && obj!=gPopStatus.lastShown) {
			removeClass(gPopStatus.lastShown,"pu-select");
			//addClass(gPopStatus.lastShown,"pu-deselect");
		}
		if(obj) {
			//removeClass(obj,"pu-deselect");
			addClass(obj,"pu-select");
		}
		gPopStatus.lastShown = obj;
	}
	
	function clearLastShown() {
		setLastShown(null);
	}
	
	listenFor(pubody, "mouseover", function() {
		clearTimeout(hideInterval);
		hideInterval=null;
	});
	listenFor(pubody, "mouseout", hidePopHandler);
	
	var pupin =  createChunk("Pin", "button", "pu-pin");
	
	window.showInPop2 = function(str, tr, hideIfLast, fn, immediate) {
//		if(hideIfLast&&lastShown==hideIfLast) {
//			return; // keep up
//		}
		if(unShow) {
			unShow();
			unShow=null;
		}
		incrPopToken('newShow' + str);
		if(hideInterval) {
			clearTimeout(hideInterval);
			hideInterval=null;
		}
//		if(hideIfLast && lastShown==hideIfLast) {
//			lastShown=null;
//			
//			puouter.style.display="none";
//			
//			return;
//		}
		setLastShown(hideIfLast);

		var td = document.createDocumentFragment();

		var theHelp = null;
		if(tr&& tr.helpDiv) {
			theHelp =  tr.helpDiv;
		}
		if(theHelp) {
			td.appendChild(theHelp.cloneNode(true));
		}

		if(str) {
			var div2 = document.createElement("div");
			div2.innerHTML=str;
			td.appendChild(div2);
		}
		if(fn!=null) {
			unShow=fn(td);
		}

		removeAllChildNodes(pucontent);
		pucontent.appendChild(td);
		if(stdebug_enabled) {
			if(pupin) {
				pucontent.appendChild(pupin);
			}
			listenFor(pupin, "click", function(e) {
				window.hidePop = function() {

				};
				pucontent.removeChild(pupin);
				pupin = null;
				stStopPropagation(e); return false; 
			});

		}
		td=null;
		
//		var popParent = tr;
//		if(hideIfLast.popParent) {
//			popParent = hideIfLast.popParent;
//		}
		
//		if(hideIfLast) { // context
////			var loc = getAbsolutePosition(hideIfLast);
////			var newTopPeak = (loc.top+((hideIfLast.offsetHeight)/2)-8);
////			//if(newTop<hardtop) newTop=hardtop;
////			//puouter.style.top = (newTop+nudgevpost)+"px";
////			newLeftPeak = (loc.left);
////			
////			pupeak.style.left = (newLeftPeak-pupeak.offsetWidth) + "px";
////			pupeak.style.top = newTopPeak + "px";
//			
////			if(false) {
////				// now, body style
////				var bodyTop;
////				if(hideIfLast.popParent) {
////					loc = getAbsolutePosition(hideIfLast.popParent); // specifies:  move the v part here
////					bodyTop = (loc.top+hideIfLast.popParent.offsetHeight+nudgeh);
////				} else {
////					bodyTop = (loc.top+hideIfLast.offsetHeight+nudgeh);
////				}
////				pubody.style.top = bodyTop+"px";
////				pupeak.style.height = (bodyTop-newTopPeak) + "px";
////			}			
////			if(newLeft<hardleft) {
////				pupeak.style.left = (newLeft - hardleft)+"px";
////				newLeft = hardleft;
////			} else {
////				pupeak.style.left = "0px";
////			}
////			puouter.style.left = (newLeft+nudgehpost)+"px";
//			pupeak.style.display="none";
//		} else {
//			pupeak.style.display="none";
////			pupeak.style.left = "0px";
////			pupeak.style.top = "0px";
////			stdebug("Note: showPop with no td");
//		}
		puouter.style.display="block"; // show
	};
	if(false) {
		window.showInPop = window.showInPop2;
	} else {
		// delay before show
		window.showInPop = function(str,tr,hideIfLast,fn,immediate) {
			if(hideInterval) {
				clearTimeout(hideInterval);
				hideInterval=null;
			}
			if(immediate) {
				return window.showInPop2(str,tr,hideIfLast,fn);
			} else {
				hideInterval=setTimeout(function() {
					window.showInPop2(str,tr,hideIfLast,fn);
				}, 2500);
			}
		};
	}
	window.hidePop = function() {
		if(hideInterval) {
			clearTimeout(hideInterval);
		}
		hideInterval=setTimeout(function() {
			if(false) {
				puouter.style.display="none";
			} else {
				removeAllChildNodes(pucontent);
//				pupeak.style.display="none";
			}
			clearLastShown();
			incrPopToken('newHide');
		}, 2000);
	};
	window.resetPop = function() {
		lastShown = null;
	};
});

function appendItem(div,value, pClass) {
	var text = document.createTextNode(value);
	var span = document.createElement("span");
	span.appendChild(text);
	if(pClass) {
		span.className = pClass;
	} else {
		span.className = "value";
	}
	div.appendChild(span);
	return span;
}

function testsToHtml(tests) {
	var newHtml = "";
	for ( var i = 0; i < tests.length; i++) {
		var testItem = tests[i];
		newHtml += "<p class='tr_" + testItem.type + "' title='" + testItem.type
				+ "'>";
		if (testItem.type == 'Warning') {
			newHtml += warnIcon;
			// what='warn';
		} else if (testItem.type == 'Error') {
			//td.className = "tr_err";
			newHtml += stopIcon;
//			what = 'error';
		}
		newHtml += tests[i].message;
		newHtml += "</p>";
	}
	return newHtml;
}
function setDivClass(div,testKind) {
	if(!testKind) {
		div.className = "d-item";
	} else if(testKind=="Warning") {
		div.className = "d-item-warn";
	} else if(testKind=="Error") {
		div.className = "d-item-err";
	} else {
		div.className = "d-item";
		//(createChunk("(unknown testKind "+testKind+")" ,"i"));
	}
}
function findItemByValue(items, value) {
	if(!items) return null;
	for(var i in items) {
		if(value==items[i].value) {
			return items[i];
		}
	}
	return null;
}

function showProposedItem(inTd,tr,theRow,value,tests) {
	var children = getTagChildren(tr);
	var config = tr.theTable.config;
	
//	stdebug("Searching for our value " + value );
	// Find where our value went.
	var ourItem = findItemByValue(theRow.items,value);
	
	var testKind = getTestKind(tests);
	var ourDiv = null;
	if(!ourItem) {
		ourDiv = document.createElement("div");
		var newButton = cloneAnon(dojo.byId('proto-button'));
		if(newButton) {
			newButton.value=value;
			wireUpButton(newButton,tr,theRow,"[retry]", {"value":value});
			ourDiv.appendChild(newButton);
		}
		var h3 = document.createElement("span");
		var span=appendItem(h3, value, "value");
		span.dir = tr.theTable.json.dir;
		ourDiv.appendChild(h3);
		
		if(tr.myProposal) {
			children[config.othercell].removeChild(tr.myProposal);
		}
		tr.myProposal = ourDiv;
		tr.myProposal.value = value;
		children[config.othercell].appendChild(tr.myProposal);
	} else {
		ourDiv = ourItem.div;
	}
	setDivClass(ourDiv,testKind);
	
//	theRow.proposedResults = null;

	if(testKind || !ourItem) {
		var div3 = document.createElement("div");
		var newHtml = "";
		newHtml += testsToHtml(tests);

		if(!ourItem) {
			var h3 = document.createElement("h3");
			var span=appendItem(h3, value, "value");
			span.dir = tr.theTable.json.dir;
			h3.className="span";
			div3.appendChild(h3);
		}
		var newDiv = document.createElement("div");
		div3.appendChild(newDiv);
		newDiv.innerHTML = newHtml;
//		theRow.proposedResults = div3;
//		theRow.proposedResults.value = value;

		div3.popParent = tr;
		
		// will replace any existing function
		var ourShowFn = function(showDiv) {
			var retFn;
			if(ourItem && ourItem.showFn) {
				retFn =  ourItem.showFn(showDiv);
			} else {
				retFn = null;
			}
			if(tr.myProposal && (value == tr.myProposal.value)) { // make sure it wasn't submitted twice
				showDiv.appendChild(div3);
			}
			return retFn;
		};
		listenToPop(null, tr, ourDiv, ourShowFn);
		showInPop(null, tr, ourDiv, ourShowFn, true);
	}

	return false;
}

// returns a popinto function
function showItemInfoFn(theRow, item, vHash, newButton, div) {
	return function(td) {
		//div.className = 'd-item-selected';

		var h3 = document.createElement("h3");
		var span = appendItem(h3, item.value, item.pClass);
		h3.className="span";
		if(false) { // click to copy
			h3.onclick = function() {
				if(tr.inputBox) {
					tr.inputBox.value  = item.value;
				}
				return false;
			};
			h3.title = stui.clickToCopy;
		}
		td.appendChild(h3);
		
		var newDiv = document.createElement("div");
		td.appendChild(newDiv);
		var newHtml = "";
		
		if (item.tests) {
			newHtml += testsToHtml(item.tests);
		} else {
			newHtml = "<i>no tests</i>";
		}
		
		newDiv.innerHTML = newHtml;
		
		if(item.inExample) {
			appendExample(td, item.inExample);
		} else if(item.example) {
			appendExample(td, item.example);
		}
		
		//return function(){ var d2 = div; return function(){ 	d2.className="d-item";  };}();
	}; // end fn
}

function popInfoInto(tr, theRow, theChild, immediate) {
	if(theRow.voteInfoText) {
		showInPop(theRow.voteInfoText, tr, theChild, null, immediate);
		return;
	}
	showInPop("<i>" + stui.str("loading") + "</i>", tr, theChild);
	var popShowingToken = getPopToken();
	stdebug('Got token ' + popShowingToken);
//	var what = WHAT_GETROW;
	var ourUrl = contextPath + "/RefreshRow.jsp?what=" + WHAT_GETROW
			+ "&xpath=" + theRow.xpid + "&_=" + surveyCurrentLocale + "&fhash="
			+ theRow.rowHash + "&vhash=" + "&s=" + tr.theTable.session
			+ "&voteinfo=t";
	var errorHandler = function(err, ioArgs) {
		console.log('Error in refreshRow: ' + err + ' response '
				+ ioArgs.xhr.responseText);
		showInPop(
				stopIcon
						+ " Couldn't reload this row- please refresh the page. <br>Error: "
						+ err + "</td>", tr, theChild);
		return true;
	};
	var loadHandler = function(text) {
		try {
			if (text) {
				theRow.voteInfoText = text;
			} else {
				theRow.voteInfoText = stopIcon + stui.noVotingInfo;
			}
			if(getPopToken()==popShowingToken) {
				showInPop(theRow.voteInfoText, tr, theChild, null, true);
//				stdebug("success with token " + popShowingToken);
			} else { // else, something else happened meanwhile.
//				stdebug("our token was " + popShowingToken + " but now at " + getPopToken() );
			}
		} catch (e) {
			console.log("Error in ajax get ", e.message);
			console.log(" response: " + text);
			showInPop(stopIcon + " exception: " + e.message, tr, theChild, true);
		}
	};
	var xhrArgs = {
		url : ourUrl,
		handleAs : "text",
		load : loadHandler,
		error : errorHandler
	};
	// window.xhrArgs = xhrArgs;
	// console.log('xhrArgs = ' + xhrArgs);
	queueXhr(xhrArgs);
}


function appendExample(parent, text) {
	var div = document.createElement("div");
	div.className="d-example";
	div.innerHTML=text;
	parent.appendChild(div);
	return div;
}



function addVitem(td, tr,theRow,item,vHash,newButton) {
//	var canModify = tr.theTable.json.canModify;
	var div = document.createElement("div");
	var isWinner = (td==tr.proposedcell);
	var testKind = getTestKind(item.tests);
	setDivClass(div,testKind);
	item.div = div;
	
	if(item==null)  {
//		div.innerHTML = "<i>null: "+theRow.winningVhash+" </i>";
		return;
	}
	
	if(newButton) {
		newButton.value=item.value;
		wireUpButton(newButton,tr,theRow,vHash);
		div.appendChild(newButton);
	}
	var span = appendItem(div,item.value,item.pClass);
	
	span.dir = tr.theTable.json.dir;
	if(item.isOldValue==true && !isWinner) {
		addIcon(div,"i-star");
	}
	if(item.votes && !isWinner) {
		addIcon(div,"i-vote");
	}

	td.showFn = item.showFn = showItemInfoFn(theRow,item,vHash,newButton,div);
	div.popParent = tr;
	listenToPop(null, tr, div, td.showFn);
	td.appendChild(div);
	
	if(item.inExample) {
		//addIcon(div,"i-example-zoom").onclick = div.onclick;
	} else if(item.example) {
		var example = appendExample(div,item.example);
//		example.popParent = tr;
//		listenToPop(null,tr,example,td.showFn);
	}
}

function updateRow(tr, theRow) {
	if(!tr.helpDiv) {
		// this also marks this row as a 'help parent'
		tr.helpDiv = cloneAnon(dojo.byId("proto-help"));
		tr.helpDiv.innerHTML += theRow.displayHelp;
	}
	
	var canModify = tr.theTable.json.canModify;
	if(!theRow || !theRow.xpid) {
		tr.innerHTML="<td><i>ERROR: missing row</i></td>";
		return;
	}
	tr.xpid = theRow.xpid;
	var children = getTagChildren(tr);
	var config = tr.theTable.config;
	var protoButton = dojo.byId('proto-button');
	if(!canModify) {
		protoButton = null;
	}
	
	var doPopInfo = function(e) {
		popInfoInto(tr,theRow,children[config.statuscell],false);
		stStopPropagation(e); return false; 
	};
	var doPopInfoNow = function(e) {
		popInfoInto(tr,theRow,children[config.statuscell],true);
		stStopPropagation(e); return false; 
	};
		
//	if(theRow.hasErrors) {
////		children[config.errcell].className = "d-st-stop";
////		children[config.errcell].title = stui.testError;
//		children[config.proposedcell].className = 'd-win-err';
//	} else if(theRow.hasWarnings) {
////		children[config.errcell].className = "d-st-warn";
////		children[config.errcell].title = stui.testWarn;
//		children[config.proposedcell].className = 'd-win-warn';
//	} else {
////		children[config.errcell].className = "d-st-okay";
////		children[config.errcell].title = stui.testOkay;
//		children[config.proposedcell].className = 'd-win';
//	}
	
	
	
	children[config.statuscell].className = "d-dr-"+theRow.confirmStatus;
	if(!children[config.statuscell].isSetup) {
		listenFor(children[config.statuscell],"mouseover",
				doPopInfo);
		listenFor(children[config.statuscell],"click",
				doPopInfoNow);
		children[config.statuscell].isSetup=true;
	}
	children[config.statuscell].title = stui.sub('draftStatus',[stui.str(theRow.confirmStatus)]);

	if(theRow.hasVoted) {
//		children[config.votedcell].className = "d-vo-true";
//		children[config.votedcell].title=stui.voTrue;
		children[config.nocell].title=stui.voTrue;
		children[config.nocell].className= "d-no-vo-true";
	} else {
//		children[config.votedcell].className = "d-vo-false";
//		children[config.votedcell].title=stui.voFalse;
		children[config.nocell].title=stui.voFalse;
		children[config.nocell].className= "d-no-vo-false";
	}
//	if(!children[config.votedcell].isSetup) {
//		listenFor(children[config.votedcell],"mouseover",
//				doPopInfo);
//		listenFor(children[config.votedcell],"click",
//				doPopInfoNow);
//		children[config.votedcell].isSetup=true;
//	}

	if(!tr.anch || stdebug_enabled) {
		if(tr.anch) { // clear out old (only for debug)
			removeAllChildNodes(children[config.codecell]);
		}
		children[config.codecell].appendChild(createChunk(theRow.code));
		var anch = document.createElement("a");
		anch.className="anch";
		anch.id=theRow.xpid;
		anch.href="#"+anch.id;
		children[config.codecell].appendChild(anch);
		if(stdebug_enabled) {
			anch.appendChild(document.createTextNode("#"));

			var go = document.createElement("a");
			go.className="anch-go";
			go.appendChild(document.createTextNode("zoom"));
			go.href=window.location.pathname + "?_="+surveyCurrentLocale+"&x=r_rxt&xp="+theRow.xpid;
			children[config.codecell].appendChild(go);
			
			var js = document.createElement("a");
			js.className="anch-go";
			js.appendChild(document.createTextNode("{JSON}"));
			js.popParent=tr;
			js.href="#";
			listenToPop(JSON.stringify(theRow),tr,js);
			children[config.codecell].appendChild(js);
		}
//		listenFor(children[config.codecell],"click",
//				function(e){ 		
//					showInPop("XPath: " + theRow.xpath, children[config.codecell]);
//					stStopPropagation(e); return false; 
//				});
		var xpathStr = "";
		if((!window.surveyOfficial) || stdebug_enabled) {
			xpathStr = "XPath: " + theRow.xpath;
		}
		listenToPop(xpathStr, tr, children[config.codecell]);
		tr.anch = anch;
	}
	
	
	if(!children[config.comparisoncell].isSetup) {
		children[config.comparisoncell].appendChild(document.createTextNode(theRow.displayName));
		if(theRow.displayExample) {
			var theExample = appendExample(children[config.comparisoncell], theRow.displayExample);
			listenToPop(null,tr,theExample);
		}
		listenToPop(null,tr,children[config.comparisoncell]);
		children[config.comparisoncell].isSetup=true;
	}
	removeAllChildNodes(children[config.proposedcell]); // win
	tr.proposedcell = children[config.proposedcell];
	if(theRow.items&&theRow.winningVhash) {
		addVitem(children[config.proposedcell],tr,theRow,theRow.items[theRow.winningVhash],theRow.winningVhash,cloneAnon(protoButton));
	} else {
		children[config.proposedcell].showFn = null;  // nothing else to show
	}
	listenToPop(null,tr,children[config.proposedcell], children[config.proposedcell].showFn);
	listenToPop(null,tr,children[config.errcell], children[config.proposedcell].showFn);
	//listenFor(children[config.errcell],"mouseover",function(e){return children[config.errcell]._onmove(e);});
	
	var hadOtherItems  = false;
	removeAllChildNodes(children[config.othercell]); // other
	for(k in theRow.items) {
		if(k == theRow.winningVhash) {
			continue; // skip the winner
		}
		hadOtherItems=true;
		addVitem(children[config.othercell],tr,theRow,theRow.items[k],k,cloneAnon(protoButton));
	}
	if(!hadOtherItems /*!onIE*/) {
		listenToPop(null, tr, children[config.othercell]);
	}
	if(tr.myProposal && tr.myProposal.value && !findItemByValue(theRow.items, tr.myProposal.value)) {
		// add back my proposal
		children[config.othercell].appendChild(tr.myProposal);
	} else {
		tr.myProposal=null; // not needed
	}

	if(!children[config.changecell].isSetup) {
		removeAllChildNodes(children[config.changecell]);
		tr.inputTd = children[config.changecell]; // TODO: use  (getTagChildren(tr)[tr.theTable.config.changecell])
		
		if(!canModify) {
			children[config.changecell].style.display="none";
		} else if(theRow.showstatus && theRow.showstatus == "READ_ONLY") {
			children[config.changecell].className="d-change-confirmonly";
			var link = createChunk(stui.str("file_a_ticket"),"a");
			var newUrl = BUG_URL_BASE+"/newticket?component=data&summary="+surveyCurrentLocale+":"+theRow.xpath;
			if(window.surveyOfficial) {
				link.href = newUrl;
				theRow.proposedResults = createChunk(stui.str("file_ticket_must"), "a","fnotebox");
				theRow.proposedResults.href = newUrl;
			} else {
				theRow.proposedResults = createChunk(stui.str("file_ticket_unofficial") + "  <" + newUrl + ">","div","fnotebox");
			}
			children[config.changecell].appendChild(link);
		} else {
			var changeButton = cloneAnon(protoButton);
			children[config.changecell].appendChild(changeButton);
			var changeBox = cloneAnon(dojo.byId("proto-inputbox"));
			wireUpButton(changeButton,tr, theRow, "[change]",changeBox);
			tr.inputBox = changeBox;
			
			changeBox.onfocus = function() {
				setDefer(true);
				return true;
			};
			changeBox.onblur = function() {
				setDefer(false);
				return true;
			};
			
			children[config.changecell].appendChild(changeBox);
			children[config.changecell].isSetup=true;
			children[config.changecell].theButton = changeButton;
			listenToPop(null, tr, children[config.changecell]);
		}
		
	} else {
		if(children[config.changecell].theButton) {
			children[config.changecell].theButton.className="ichoice-o";
		}
	}
			
	
	if(canModify) {
		removeAllChildNodes(children[config.nocell]); // no opinion
		var noOpinion = cloneAnon(protoButton);
		wireUpButton(noOpinion,tr, theRow, null);
		noOpinion.value=null;
		children[config.nocell].appendChild(noOpinion);
		listenToPop(null, tr, children[config.nocell]);
	} else {
		children[config.nocell].style.display="none";
	}
	
	tr.className='vother';
	
	
}

function findPartition(partitions,partitionList,curPartition,i) {
	if(curPartition && 
			i>=curPartition.start &&
			i<curPartition.limit) {
		return curPartition;
	}
	for(var j in partitionList) {
		var name = partitionList[j];
		var p = partitions[name];
		if(i>=p.start &&
			i<p.limit) {
				p.name = name;
				return p;
			}
	}
	return null;
}

function insertRowsIntoTbody(theTable,tbody) {
	theTable.hitCount++;
	var theRows = theTable.json.section.rows;
	var toAdd = theTable.toAdd;
	var parRow = dojo.byId('proto-parrow');
	removeAllChildNodes(tbody);
	var theSort = theTable.json.displaySets[theTable.curSortMode];
	var partitions = theSort.partitions;
	var rowList = theSort.rows;
	//console.log("rows: " + Object.keys(theTable.myTRs)  + ", hitcount: " + theTable.hitCount);
	var partitionList = Object.keys(partitions);
	var curPartition = null;
	for(i in rowList ) {
		var newPartition = findPartition(partitions,partitionList,curPartition,i);
		
		if(newPartition != curPartition) {
			if(newPartition.name != "") {
				var newPar = cloneAnon(parRow);
				var newTd = getTagChildren(newPar);
				var newHeading = getTagChildren(newTd[0]);
				newHeading[0].innerHTML = newPartition.name;
				newHeading[0].id = newPartition.name;
				tbody.appendChild(newPar);
			}
			curPartition = newPartition;
		}
		
		var k = rowList[i];
		var theRow = theRows[k];
		
		var tr = theTable.myTRs[k];
		if(!tr) {
			//console.log("new " + k);
			tr = cloneAnon(toAdd);
			theTable.myTRs[k]=tr; // save for later use
		}
		tr.id="r_"+k;
		tr.rowHash = k;
		tr.theTable = theTable;
		if(!theRow) {
			console.log("Missing row " + k);
		}
		updateRow(tr,theRow);
		
		tbody.appendChild(tr);

	}
	
	
	//console.log("POST rows: " + Object.keys(theTable.myTRs)  + ", hitcount: " + theTable.hitCount);
}

function reSort(theTable,k) {
	if(theTable.curSortMode==k) {
		return; // no op
	}
	theTable.curSortMode=k;
	insertRowsIntoTbody(theTable,theTable.getElementsByTagName("tbody")[0]);
	var lis = theTable.sortMode.getElementsByTagName("li");
	for(i in lis) {
		var li = lis[i];
		if(li.mode==k) {
			li.className="selected";
		} else {
			li.className = "notselected";
		}
	}
}
function setupSortmode(theTable) {
	var theSortmode = theTable.sortMode;
	// ignore what's there
	removeAllChildNodes(theSortmode);
	var listOfLists = Object.keys(theTable.json.displaySets);
	var itemCount = Object.keys(theTable.json.section.rows).length;
	var size = document.createElement("span");
	size.className="d-sort-size";
	theSortmode.appendChild(size);
	var ul = document.createElement("ul");
	if(itemCount>0) {
		for(i in listOfLists) {
			var k = listOfLists[i];
			if(k=="default") continue;
			
			var a = document.createElement("li");
			a.onclick = (function() {
				var kk = k;
				return function() {
					reSort(theTable, kk);
				};
			})();
			a.appendChild(document.createTextNode(theTable.json.displaySets[k].displayName));
			a.mode=k;
			if(k==theTable.curSortMode) {
				a.className="selected";
			} else {
				a.className = "notselected";
			}
			ul.appendChild(a);
		}
		theSortmode.appendChild(ul);
	}
	
	if(itemCount==0 && theTable.json.section.skippedDueToCoverage) {
		size.appendChild(document.createTextNode(
				stui.sub("itemCountAllHidden", [itemCount,theTable.json.section.skippedDueToCoverage])
				
				));
		size.className = "d-sort-size0";
	} else if(itemCount==0) {
		size.appendChild(document.createTextNode(
				stui.sub("itemCountNone", [])
				
				));
		size.className = "d-sort-size0";
	} else if(theTable.json.section.skippedDueToCoverage) {
		size.appendChild(document.createTextNode(
				stui.sub("itemCountHidden", [itemCount,theTable.json.section.skippedDueToCoverage])
				
				));
//		var minfo = dojo.byId("info_menu_p_covlev");
//		if(minfo) {
//			minfo.innerHTML = theTable.json.section.skippedDueToCoverage + " hidden";
//		}
	} else {
		size.appendChild(document.createTextNode(
				stui.sub("itemCount", [Object.keys(theTable.json.section.rows).length])));
	}
}

function insertRows(theDiv,xpath,session,json) {
	var theTable = theDiv.theTable;

	var doInsertTable = null;
	
	if(!theTable) {
		theTable = cloneLocalizeAnon(dojo.byId('proto-datatable'));
		localizeFlyover(theTable);
		theTable.theadChildren = getTagChildren(theTable.getElementsByTagName("tr")[0]);
		var toAdd = dojo.byId('proto-datarow');
		if(!theTable.config) {
			var rowChildren = getTagChildren(toAdd);
			theTable.config={};
			for(var c in rowChildren) {
				rowChildren[c].title = theTable.theadChildren[c].title;
				if(rowChildren[c].id) {
					theTable.config[rowChildren[c].id] = c;
					stdebug("  config."+rowChildren[c].id+" = children["+c+"]");
					if(false&&stdebug_enabled) {
						removeAllChildNodes(rowChildren[c]);
						rowChildren[c].appendChild(createChunk("config."+rowChildren[c].id+"="+c));
					}
					rowChildren[c].id=null;
				} else {
					stdebug("(proto-datarow #"+c+" has no id");
				}
			}
			if(stdebug_enabled) stdebug("Table Config: " + JSON.stringify(theTable.config));
		}
		theTable.toAdd = toAdd;

		if(!json.canModify) {
				theTable.theadChildren[theTable.config.changecell].style.display=theTable.theadChildren[theTable.config.nocell].style.display="none";
		}
		theTable.sortMode = cloneAnon(dojo.byId('proto-sortmode'));
		theDiv.appendChild(theTable.sortMode);
		theTable.myTRs = [];
		theDiv.theTable = theTable;
		theTable.theDiv = theDiv;
		doInsertTable=theTable;
		listenFor(theTable,"mouseout",
				hidePopHandler);
	}
	// append header row
	
	theTable.json = json;
	theTable.xpath = xpath;
	theTable.hitCount=0;
	theTable.session = session;
	
	if(!theTable.curSortMode) { 
		theTable.curSortMode = theTable.json.displaySets["default"];
		// hack - choose one of these
		if(theTable.json.displaySets.codecal) {
			theTable.curSortMode = "codecal";
		} else if(theTable.json.displaySets.metazon) {
			theTable.curSortMode = "metazon";
		}
	}
	setupSortmode(theTable);

	var tbody = theTable.getElementsByTagName("tbody")[0];
	insertRowsIntoTbody(theTable,tbody);
	if(doInsertTable) {
		theDiv.appendChild(doInsertTable);
		if(theDiv.theLoadingMessage) {
			theDiv.theLoadingMessage.style.display="none";
		}
	}
	hideLoader(theDiv.loader);
}

// move this into showRows to handle multiple containers.

function loadStui(loc) {
	stui  = dojo.i18n.getLocalization("surveyTool", "stui", "en" /* TODO: lame */);
	stui.sub = function(x,y) { return dojo.string.substitute(stui[x], y);};
	stui.str = function(x) { if(stui[x]) return stui[x]; else return x; };
	stui.htmlbaseline = BASELINE_LANGUAGE_NAME;
	
	return stui;
}
function createChunk(text, tag, className) {
	if(!tag) {
		tag="span";
	}
	var chunk = document.createElement(tag);
	if(className) {
		chunk.className = className;
		chunk.title=stui_str(className+"_desc");
	}
	if(text) {
		chunk.appendChild(document.createTextNode(text));
	}
	return chunk;
}
////////
/// showRows() ..
function showRows(container,xpath,session,coverage) {
 dojo.ready(function(){
	if(!coverage) coverage="";
	var theDiv = dojo.byId(container);

	theDiv.stui = loadStui("en");
	theDiv.theLoadingMessage = createChunk(stui_str("loading"), "i", "loadingMsg");
	theDiv.appendChild(theDiv.theLoadingMessage);
	
	var shower = null;
	var theTable = theDiv.theTable;
	shower = function() {
	
		if(!theTable) {
			var theTableList = theDiv.getElementsByTagName("table");
			if(theTableList) {
				theTable = theTableList[0];
				theDiv.theTable = theTable;
			}
		}

		var theLoader = theDiv.loader;
		if(!theLoader) {
			theLoader =  cloneAnon(dojo.byId("proto-loading"));
			theDiv.appendChild(theLoader);
			theDiv.loader = theLoader;
		}
		
		showLoader(theDiv.loader, theDiv.stui.loading);
		
		dojo.ready(function() {
		    var errorHandler = function(err, ioArgs){
		    	console.log('Error: ' + err + ' response ' + ioArgs.xhr.responseText);
		        showLoader(theDiv.loader,stopIcon + "<h1>Could not refresh the page - you may need to <a href='javascript:window.location.reload(true);'>refresh</a> the page if the SurveyTool has restarted..</h1> <hr>Error while fetching : "+err.name + " <br> " + err.message + "<div style='border: 1px solid red;'>" + ioArgs.xhr.responseText + "</div>");
		    };
		    var loadHandler = function(json){
		        try {
		        	showLoader(theDiv.loader,stui.loading2);
		        	if(!json) {
		        		console.log("!json");
				        showLoader(theDiv.loader,"Error while  loading: <br><div style='border: 1px solid red;'>" + "no data!" + "</div>");
		        	} else if(json.err) {
		        		console.log("json.err!" + json.err);
		        		showLoader(theDiv.loader,"Error while  loading: <br><div style='border: 1px solid red;'>" + json.err + "</div>");
		        		handleDisconnect("while loading",json);
				    } else if(!json.section) {
		        		console.log("!json.section");
				        showLoader(theDiv.loader,"Error while  loading: <br><div style='border: 1px solid red;'>" + "no section" + "</div>");
		        		handleDisconnect("while loading- no section",json);
				    } else if(!json.section.rows) {
		        		console.log("!json.section.rows");
				        showLoader(theDiv.loader,"Error while  loading: <br><div style='border: 1px solid red;'>" + "no rows" + "</div>");				        
		        		handleDisconnect("while loading- no rows",json);
		        	} else {
		        		stdebug("json.section.rows OK..");
		        		showLoader(theDiv.loader, "loading..");
		        		if(json.dataLoadTime) {
		        			updateIf("dynload", json.dataLoadTime);
		        		}
		        		showInPop2("", null, null, null, true); /* show the box the first time */
		        		doUpdate(theDiv.id, function() {
		        				showLoader(theDiv.loader,stui.loading3);
		        				insertRows(theDiv,xpath,session,json);
		        		});
		        	}
		        	
		           }catch(e) {
		               console.log("Error in ajax post [showRows]  " + e.message + " / " + e.name );
				        showLoader(theDiv.loader,"Exception while  loading: " + e.message + ", n="+e.name); // in case the 2nd line doesn't work
//				        showLoader(theDiv.loader,"Exception while  loading: "+e.name + " <br> " +  "<div style='border: 1px solid red;'>" + e.message+ "</div>");
//			               console.log("Error in ajax post [showRows]  " + e.message);
		           }
		    };
		    var xhrArgs = {
		            url: contextPath + "/RefreshRow.jsp?json=t&_="+surveyCurrentLocale+"&s="+session+"&xpath="+xpath+"&p_covlev="+coverage+cacheKill(),
		            handleAs:"json",
		            load: loadHandler,
		            error: errorHandler
		        };
		    //window.xhrArgs = xhrArgs;
//		    console.log('xhrArgs = ' + xhrArgs);
		    queueXhr(xhrArgs);
		});
	};
	
	shower(); // first load
	theDiv.shower = shower;
	showers[theDiv.id]=shower;
//	console.log("Wrote shower " + theDiv.id + " as " + shower);
  });
}

function refreshRow2(tr,theRow,vHash,onSuccess, onFailure) {
	showLoader(tr.theTable.theDiv.loader,stui.loadingOneRow);
    var ourUrl = contextPath + "/RefreshRow.jsp?what="+WHAT_GETROW+"&xpath="+theRow.xpid +"&_="+surveyCurrentLocale+"&fhash="+tr.rowHash+"&vhash="+vHash+"&s="+tr.theTable.session +"&json=t";
    var loadHandler = function(json){
        try {
	    		if(json&&json.dataLoadTime) {
	    			updateIf("dynload", json.dataLoadTime);
	    		}
        		if(json.section.rows[tr.rowHash]) {
        			theRow = json.section.rows[tr.rowHash];
        			tr.theTable.json.section.rows[tr.rowHash] = theRow;
        			updateRow(tr, theRow);
        			hideLoader(tr.theTable.theDiv.loader);
        			onSuccess(theRow);
        		} else {
        	        tr.className = "ferrbox";
        	        tr.innerHTML="No content found "+tr.rowHash+ "  while  loading";
        	        console.log("could not find " + tr.rowHash + " in " + json);
        	        onFailure("no content");
        		}
           }catch(e) {
               console.log("Error in ajax post [refreshRow2] ",e.message);
 //              e_div.innerHTML = "<i>Internal Error: " + e.message + "</i>";
           }
    };
    var errorHandler = function(err, ioArgs){
    	console.log('Error: ' + err + ' response ' + ioArgs.xhr.responseText);
        tr.className = "ferrbox";
        tr.innerHTML="Error while  loading: "+err.name + " <br> " + err.message + "<div style='border: 1px solid red;'>" + ioArgs.xhr.responseText + "</div>";
        onFailure("err",err,ioArgs);
    };
    var xhrArgs = {
            url: ourUrl+cacheKill(),
            //postData: value,
            handleAs:"json",
            load: loadHandler,
            error: errorHandler,
            timeout: 15000
        };
    //window.xhrArgs = xhrArgs;
    //console.log('xhrArgs = ' + xhrArgs + ", url: " + ourUrl);
    queueXhr(xhrArgs);
}

function handleWiredClick(tr,theRow,vHash,box,button,what) {
	var value="";
	var valToShow;
	if(tr.wait) {
		return;
	}
	if(box) {
		valToShow=box.value;
		value = box.value;
		if(value.length ==0 ) {
			if(box.focus) {
				box.focus();
				myUnDefer();
			}
			return; // nothing entered.
		}
		tr.inputTd.className="d-change"; // TODO: use  (getTagChildren(tr)[tr.theTable.config.changecell])
	} else {
		valToShow=button.value;
	}
	if(!what) {
		what='submit';
	}
	if(what=='submit') {
		button.className="ichoice-x-ok";  // TODO: ichoice-inprogress?  spinner?
		showLoader(tr.theTable.theDiv.loader,stui.voting);
	} else {
		showLoader(tr.theTable.theDiv.loader, stui.checking);
	}

	if(tr.myProposal) {
		// move these 2 up if needed
		var children = getTagChildren(tr);
		var config = tr.theTable.config;
		
		children[config.othercell].removeChild(tr.myProposal);
		tr.myProposal = null; // mark any pending proposal as invalid.
	}
	
	var myUnDefer = function() {
		tr.wait=false;
		setDefer(false);
	};
	tr.wait=true;
	resetPop(tr);
	setDefer(true);
	theRow.proposedResults = null;


	console.log("Vote for " + tr.rowHash + " v='"+vHash+"', value='"+value+"'");
	var ourUrl = contextPath + "/SurveyAjax?what="+what+"&xpath="+tr.xpid +"&_="+surveyCurrentLocale+"&fhash="+tr.rowHash+"&vhash="+vHash+"&s="+tr.theTable.session;
//	tr.className='tr_checking';
	var loadHandler = function(json){
		try {
			// var newHtml = "";
			if(json.err && json.err.length >0) {
				tr.className='tr_err';
				// v_tr.className="tr_err";
				// v_tr2.className="tr_err";
//				showLoader(tr.theTable.theDiv.loader,"Error!");
				handleDisconnect('Error while voting', json);
				tr.innerHTML = "<td colspan='4'>"+stopIcon + " Could not check value. Try reloading the page.<br>"+json.err+"</td>";
				// e_div.innerHTML = newHtml;
				myUnDefer();
				handleDisconnect('Error while voting', json);
			} else {
				if(json.submitResultRaw) { // if submitted..
					tr.className='tr_checking2';
					refreshRow2(tr,theRow,vHash,function(theRow){
						tr.inputTd.className="d-change"; // TODO: use  inputTd=(getTagChildren(tr)[tr.theTable.config.changecell])

						// submit went through. Now show the pop.
						button.className='ichoice-o';
						hideLoader(tr.theTable.theDiv.loader);
						if(json.testResults && (json.testWarnings || json.testErrors)) {
							// tried to submit, have errs or warnings.
							showProposedItem(tr.inputTd,tr,theRow,valToShow,json.testResults); // TODO: use  inputTd= (getTagChildren(tr)[tr.theTable.config.changecell])
						} else {
							hidePop(tr);
						}
						if(box) {
							box.value=""; // submitted - dont show.
						}
						//tr.className = 'vother';
						myUnDefer();
					}, myUnDefer); // end refresh-loaded-fcn
					// end: async
				} else {
					// Did not submit. Show errors, etc
					if(json.testResults && (json.testWarnings || json.testErrors)) {
						showProposedItem(tr.inputTd,tr,theRow,valToShow,json.testResults); // TODO: use  inputTd= (getTagChildren(tr)[tr.theTable.config.changecell])
					} else {
						hidePop(tr);
					}
					if(box) {
						box.value=""; // submitted - dont show.
					}
					//tr.className='vother';
					button.className='ichoice-o';
					hideLoader(tr.theTable.theDiv.loader);
					myUnDefer();
				}
			}
		}catch(e) {
			tr.className='tr_err';
			// v_tr.className="tr_err";
			// v_tr2.className="tr_err";
			tr.innerHTML = stopIcon + " Could not check value. Try reloading the page.<br>"+e.message;
			console.log("Error in ajax post [handleWiredClick] ",e.message);
			//              e_div.innerHTML = "<i>Internal Error: " + e.message + "</i>";
			myUnDefer();
		}
	};
	var errorHandler = function(err, ioArgs){
		console.log('Error: ' + err + ' response ' + ioArgs.xhr.responseText);
		handleDisconnect('Error: ' + err + ' response ' + ioArgs.xhr.responseText, null);
		theRow.className = "ferrbox";
		theRow.innerHTML="Error while  loading: "+err.name + " <br> " + err.message + "<div style='border: 1px solid red;'>" + ioArgs.xhr.responseText + "</div>";
		myUnDefer();
	};
	var xhrArgs = {
			url: ourUrl+cacheKill(),
			handleAs:"json",
			timeout: 15000,
			load: loadHandler,
			error: errorHandler
	};
	//window.xhrArgs = xhrArgs;
	//stdebug('xhrArgs = ' + xhrArgs + ", url: " + ourUrl);
	if(box) {
		stdebug("this is a psot: " + value);
		xhrArgs.postData = value;
	}
	queueXhr(xhrArgs);
}


///////////////////
// admin
function loadAdminPanel() {
	if(!vap) return;
	loadStui("en");
	var adminStuff=dojo.byId("adminStuff");
	if(!adminStuff) return;
	
	var content = document.createDocumentFragment();
	
	var list = document.createElement("ul");
	list.className="adminList";
	content.appendChild(list);
	
	
	function loadOrFail(urlAppend,theDiv, loadHandler) {
		var ourUrl = contextPath + "/AdminAjax.jsp?vap="+vap+"&"+urlAppend;
		var errorHandler = function(err, ioArgs){
			console.log('adminload ' + urlAppend + ' Error: ' + err + ' response ' + ioArgs.xhr.responseText);
			theDiv.className = "ferrbox";
			theDiv.innerHTML="Error while  loading: "+err.name + " <br> " + err.message + "<div style='border: 1px solid red;'>" + ioArgs.xhr.responseText + "</div>";
		};
		var xhrArgs = {
				url: ourUrl+cacheKill(),
				handleAs:"json",
				load: loadHandler,
				error: errorHandler
		};
		if(!loadHandler) {
			xhrArgs.handleAs = "text";
			xhrArgs.load = function(text) {
				theDiv.innerHTML = text;
			};
		}
		dojo.xhrGet(xhrArgs);
		console.log("ourUrl: " + ourUrl);
	}
	var panelLast = null;
	var panels={};

	function panelSwitch(name) {
		if(panelLast) {
			panelLast.div.style.display='none';
			panelLast.listItem.className='notselected';
			panelLast=null;
		}
		if(name&&panels[name]) {
			panelLast=panels[name];
			panelLast.listItem.className='selected';
			panelLast.fn(panelLast.udiv);
			panelLast.div.style.display='block';			
		}
	}

	function addAdminPanel(type, fn) {
		var panel = panels[type]={type: type, name: stui.str(type), desc: stui.str(type+"_desc"), fn: fn};
		panel.div = document.createElement("div");
		panel.div.style.display='none';
		panel.div.className='adminPanel';
		
		var h = document.createElement("h3");
		h.className="adminTitle";
		h.appendChild(document.createTextNode(panel.desc));
		panel.div.appendChild(h);

		panel.udiv = document.createElement("div");
		panel.div.appendChild(panel.udiv);

		panel.listItem = document.createElement("li");
		panel.listItem.appendChild(document.createTextNode(panel.name));
		panel.listItem.title = panel.desc;
		panel.listItem.className="notselected";
		panel.listItem.onclick=function(e){panelSwitch(panel.type);return false;};
		list.appendChild(panel.listItem);
		
		content.appendChild(panel.div);
		
		if(!panelLast) {
			panelSwitch(panel.type);
		}
	}
	
	
	function createUser(user) {
		var div = createChunk(null,"div","adminUserUser");
		div.appendChild(createChunk(stui.str("userlevel_"+user.userlevelname),"i","userlevel_"+user.userlevelname));
		div.appendChild(createChunk(user.name,"span","adminUserName"));
		div.appendChild(createChunk(user.email,"address","adminUserAddress"));
		return div;
	}
	
	addAdminPanel("admin_users", function(div) {
		var frag = document.createDocumentFragment();
		
		//frag.appendChild(document.createTextNode("hello"));
		
		var u = document.createElement("div");
		u.appendChild(document.createTextNode("Loading..."));
		frag.appendChild(u);
		
		removeAllChildNodes(div);
		div.appendChild(frag);
		loadOrFail("do=users", u, function(json) {
			var frag2 = document.createDocumentFragment();
			
			if(!json || !json.users || Object.keys(json.users)==0) {
				frag2.appendChild(document.createTextNode(stui.str("No users.")))
			} else {
				for(sess in json.users) {
					var cs = json.users[sess];
					var user = createChunk(null,"div","adminUser");
					user.appendChild(createChunk("Session: " + sess, "span","adminUserSession"));
					if(cs.user) {
						user.appendChild(createUser(cs.user));
					} else {
						user.appendChild(createChunk("(anonymous)","div","adminUserUser"));
					}
					user.appendChild(createChunk("Last: " + cs.last + ", IP: " + cs.ip, "span","adminUserInfo"));
					
					frag2.appendChild(user);
					
					
					frag2.appendChild(document.createElement("hr"));
				}
			}
			
			removeAllChildNodes(u);
			u.appendChild(frag2);
		});
	});

	addAdminPanel("admin_threads", function(div) {
		var frag = document.createDocumentFragment();
		
		div.className="adminThreads";
		var u = createChunk("Loading...","div","adminThreadList");
		var stack = createChunk(null,"div","adminThreadStack");
		frag.appendChild(u);
		frag.appendChild(stack);
		
		removeAllChildNodes(div);
		var clicked = null;
	
		div.appendChild(frag);
		loadOrFail("do=threads", u, function(json) {
			if(!json || !json.threads || Object.keys(json.threads.all)==0) {
				removeAllChildNodes(u);
				u.appendChild(document.createTextNode(stui.str("No threads.")));
			} else {
				var frag2 = document.createDocumentFragment();
				removeAllChildNodes(stack);
				stack.innerHTML = stui.str("adminClickToViewThreads");
				if(json.threads.dead) {
					frag2.appendChunk(json.threads.dead.toString(),"span","adminDeadThreads");
					// TODO
				}
				for(id in json.threads.all) {
					var t = json.threads.all[id];
					var thread = createChunk(null,"div","adminThread");
					thread.appendChild(createChunk(id,"span","adminThreadId"));
					thread.appendChild(createChunk(t.name,"span","adminThreadName"));
					thread.appendChild(createChunk(stui.str(t.state),"span","adminThreadState_"+t.state));
					thread.onclick=(function (t,id){return (function() {
						stack.innerHTML = "<b>"+id+":"+t.name+"</b>\n";
						for(var q in t.stack) {
							stack.innerHTML = stack.innerHTML + t.stack[q] + "\n";
						}
					});})(t,id);
					frag2.appendChild(thread);
				}
				
				removeAllChildNodes(u);
				u.appendChild(frag2);
			}
		});
	});
	// last panel loaded.
		
	adminStuff.appendChild(content);
}

//////////////////
/// stats
function showstats(hname) {
	dojo.ready(function() {
		var ourUrl = contextPath + "/SurveyAjax?what=stats_byday";
		var errorHandler = function(err, ioArgs) {
			handleDisconnect('Error in showstats: ' + err + ' response '
			+ ioArgs.xhr.responseText);
		};
		showLoader(null, "Loading statistics");
		var loadHandler = function(json) {
			try {
				if (json) {
					var r = Raphael(hname);
					
					var header=json.byday.header;
					var data=json.byday.data;
					var labels = [];
					var count = [];
					for(var i in data) {
						labels.push(data[i][header.LAST_MOD]);
						count.push(data[i][header.COUNT]);
					}
					var gdata = [];
					gdata.push(count);
					showLoader(null, "Drawing");
					// this: 0,id,node,paper,attrs,transformations,_,prev,next,type,bar,value,events
					// this.bar ["0", "id", "node", "paper", "attrs", "transformations", "_", "prev", "next", "type", "x", "y", "w", "h", "value"]
                    var fin = function () {
                    	this.flag = r.g.popup(this.bar.x, this.bar.y, this.bar.value || "0").insertBefore(this);
                    },
                    fout = function () {
                    	this.flag.animate({opacity: 0}, 300, function () {this.remove();});
                    };
					var labels2 = [];
					labels2.push(labels);
					var hei = 500;
					var offh = 10;
					var toffh = 30;
					var toffv=10+(hei/(2*labels.length));
					console.log("Drawing in : " + hname + " - "  + count.toString());
					r.g.hbarchart(100,offh,600,hei, gdata )
						.hover(fin,fout);
						//.label(labels2);
					for(var i in labels) {
						r.text(toffh,toffv+(i*(hei/labels.length)), (labels[i].split(" ")[0])+"\n"+count[i]  );
					}
					hideLoader(null);
				} else {
					handleDisconnect("Failed to load JSON stats",json);
				}
			} catch (e) {
			console.log("Error in ajax get ", e.message);
			console.log(" response: " + text);
			handleDisconnect(" exception in getstats: " + e.message,null);
			}
		};
	var xhrArgs = {
		url : ourUrl,
		handleAs : "json",
		load : loadHandler,
		error : errorHandler
	};
	queueXhr(xhrArgs);
	});
}


///////////////////
// for vetting
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
    for (var i=0; i < document.checkboxes.elements.length; i++){
        var item = document.checkboxes.elements[i];
        if (!item.checked) {
            hideRegexString += "|";
            hideRegexString += item.name;
        }
    }
    var hideRegex = new RegExp(hideRegexString);
    changeStyle(hideRegex);
}
