<%@page import="com.ibm.icu.dev.util.ElapsedTimer"%>
<%@page import="java.io.PrintWriter,org.unicode.cldr.web.*"%><%@ page language="java" contentType="text/html; charset=UTF-8"
	import="com.ibm.icu.util.ULocale,org.unicode.cldr.util.*,org.json.*"%><%--  Copyright (C) 2012 IBM and Others. All Rights Reserved  --%><%WebContext ctx = new WebContext(request, response);
	ElapsedTimer et = new ElapsedTimer();
			String what = request.getParameter(SurveyAjax.REQ_WHAT);
			String sess = request.getParameter(SurveyMain.QUERY_SESSION);
			String loc = request.getParameter(SurveyMain.QUERY_LOCALE);
			
			CLDRLocale l = SurveyAjax.validateLocale(new PrintWriter(out), loc);
			if(l==null) return;
			ctx.setLocale(l);
            String xpath = WebContext.decodeFieldString(request.getParameter(SurveyForum.F_XPATH));
            String strid = WebContext.decodeFieldString(request.getParameter("strid"));
            if(strid!=null&&strid.isEmpty()) strid=null;
            String sectionName = WebContext.decodeFieldString(request.getParameter("x"));
            if(sectionName!=null&&sectionName.isEmpty()) sectionName=null;
			String voteinfo = request.getParameter("voteinfo");
			String vhash = request.getParameter("vhash");
			String fieldHash = request.getParameter(SurveyMain.QUERY_FIELDHASH);
			String covlev = request.getParameter("p_covlev");
			Level coverage = Level.OPTIONAL;
			if(covlev!=null && covlev.length()>0) {
				coverage = Level.get(covlev);
			}
			CookieSession mySession = null;
			mySession = CookieSession.retrieve(sess);
			boolean isJson = request.getParameter("json")!=null;
			if (isJson) {
				request.setCharacterEncoding("UTF-8");
				response.setCharacterEncoding("UTF-8");
				response.setContentType("application/json");
			}
			Thread curThread = Thread.currentThread();
	    	String threadName = curThread.getName();

	    	try {
			curThread.setName(request.getServletPath()+":"+loc+":"+xpath);
	    	
			if (l == null) {
				if(!isJson) {
					response.sendError(500, "Bad locale.");
				} else {
					JSONWriter r = new JSONWriter(out).object().
							key("err").value("Bad locale.").endObject();
				}
				return;
			}

			if (mySession == null) {
				if(!isJson) {
					response.sendError(500, "Your session has timed out or the SurveyTool has restarted.");
				} else {
					JSONWriter r = new JSONWriter(out).object().
							key("err").value("Your session has timed out or the SurveyTool has restarted.").endObject();
				}
				return;
			}
			
			if(request.getParameter("automatic")==null || request.getParameter("automatic").isEmpty()) {
                mySession.userDidAction(); // don't touch for auto refresh
			}
			
			if(xpath!=null && xpath.isEmpty()) {
				xpath = null;
			}
			String xp = xpath;
			XPathMatcher matcher = null;
			String pageIdStr = xp;
			if(xp == null && sectionName!=null)  {
				pageIdStr = sectionName;
			}
			
			PathHeader.PageId pageId = WebContext.getPageId(pageIdStr);
			
			if(pageId == null && xpath != null ) {
				try {
					int id = Integer.parseInt(xpath);
					xp = mySession.sm.xpt.getById(id);
					if(xp!=null) {
						matcher = XPathMatcher.getMatcherForString(xp);
					}
				} catch (NumberFormatException nfe) {
	
				}
			}
			if(pageId == null && xpath == null && strid!=null) {
				try {
				    				xp = mySession.sm.xpt.getByStringID(strid);
				} catch(Throwable t) {
                    JSONWriter r = new JSONWriter(out).object().
                            key("err").value("Exception getting stringid " + strid).
                            key("err_code").value("E_BAD_SECTION").endObject();
                    return;
				}
                if(xp!=null) {
                	try {
	                	pageId = mySession.sm.getSTFactory().getPathHeader(xp).getPageId(); // section containing
                	} catch(Throwable t) {
	                    matcher = XPathMatcher.getMatcherForString(xp); // single string
                	}
                }
			}
			
			ctx.session = mySession;
			ctx.sm = ctx.session.sm;
			ctx.setServletPath(ctx.sm.defaultServletPath);
			CLDRLocale locale = CLDRLocale.getInstance(loc);
			
			ctx.setLocale(locale);
			
			// don't return dc content
            SupplementalDataInfo sdi = ctx.sm.getSupplementalDataInfo();
            CLDRLocale dcParent = sdi.getBaseFromDefaultContent(locale);
            if(dcParent != null) {
                JSONWriter r = new JSONWriter(out).object().
                        key("section").value(new JSONObject().put("nocontent", "Default Content, see " + dcParent.getBaseName())).endObject();
                return; // short circuit.
            }

			boolean dataEmpty = false;
			boolean zoomedIn = request.getParameter("zoomedIn") != null
					&& request.getParameter("zoomedIn").length() > 0;
			synchronized (mySession) {
				/*         SurveyMain.UserLocaleStuff uf = mySession.sm.getUserFile(
				 mySession, locale);
				 */
				DataSection section = null;
				 String baseXp = null;
				try {
                    if(pageId!=null) {
                        section = ctx.getSection(pageId,
                                coverage.toString(),
                                WebContext.LoadingShow.dontShowLoading);
                        section.setUserAndFileForVotelist(mySession.user,null);
                    } else if (xp!=null) {
					    baseXp = XPathTable.xpathToBaseXpath(xp);
						section = ctx.getSection(baseXp,matcher,
								coverage.toString(),
								WebContext.LoadingShow.dontShowLoading);
                    } else {
                        JSONWriter r = new JSONWriter(out).object().
                                key("err").value("Could not understand that section, xpath, or ID. Bad URL?").
                                key("err_code").value("E_BAD_SECTION").endObject();
                        return;
                    }
				} catch (Throwable t) {
					SurveyLog.logException(t,"on loading " + locale+":"+ baseXp);
					if(!isJson) {
						response.sendError(500, "Exception on getSection:"+t.toString());
					} else {
						JSONWriter r = new JSONWriter(out).object().
								key("err").value("Exception on getSection:"+t.toString()).
                                key("err_code").value("E_BAD_SECTION").endObject();

					}
					return;
				}

				if (request.getParameter("json") != null) { // JSON (new) mode
					request.setCharacterEncoding("UTF-8");
					response.setCharacterEncoding("UTF-8");
					response.setContentType("application/json");
					JSONObject dsets = new JSONObject();
					if(pageId==null) { // requested an xp, not a pageid?
						for (String n : SortMode.getSortModesFor(xp)) {
							dsets.put(
									n,
									section.createDisplaySet(
											SortMode.getInstance(n), matcher));
						}
						//DataSection.DisplaySet ds = section.getDisplaySet(ctx, matcher);
						dsets.put("default", SortMode.getSortMode(ctx, section));
						pageId = section.getPageId();
					} else {
					    dsets.put("default",PathHeaderSort.name);
					    dsets.put(PathHeaderSort.name,section.createDisplaySet(SortMode.getInstance(PathHeaderSort.name),null)); // the section creates the sort
					}
							
					if(pageId!=null) {
						if(pageId.getSectionId() == org.unicode.cldr.util.PathHeader.SectionId.Special) {
	                        JSONWriter r = new JSONWriter(out).object().
	                                key("err").value("Items not visible - page " + pageId + " section " + pageId.getSectionId()).
	                                key("err_code").value("E_SPECIAL_SECTION").endObject();
	                        return;
						}
					}
					
					try {
						JSONWriter r = new JSONWriter(out).object()
								.key("stro").value(STFactory.isReadOnlyLocale(ctx.getLocale()))
								.key("baseXpath").value(baseXp)
                                .key("pageId").value((pageId!=null)?pageId.name():null)
								.key("section").value(section)
								.key("localeDisplayName").value(ctx.getLocale().getDisplayName())
								.key("displaySets").value(dsets)
								.key("dir").value(ctx.getDirectionForLocale())
								.key("canModify").value(ctx.canModify())
								.key("locale").value(ctx.getLocale())
								.key("dataLoadTime").value(et.toString())
								.endObject();
					} catch(Throwable t) {
						SurveyLog.logException(t, "RefreshRow.jsp write");
                        JSONWriter r = new JSONWriter(out).object().
                                key("err").value("Exception on writeSection:"+t.toString()).endObject();
					}
					return;
				}

				/*         DataSection section = DataSection.make(null, mySession, locale,
				 xp, false, Level.COMPREHENSIVE.toString());
				 */// r.put("testResults", JSONWriter.wrap(result));
					//r.put("testsRun", cc.toString());
				int oldSize = section.getAll().size();
				DataSection.DataRow row = section.getDataRow(xp);
				if (row != null) {
					if (voteinfo != null && voteinfo.length() > 0) {
						row.showVotingResults(ctx);
					} else {
						row.showDataRow(ctx, ctx.getUserFile(), true, null,
								zoomedIn, DataSection.kAjaxRows);
					}
					ctx.flush();

					if (false) {%><td>
            ROw: <%=row%><br>
            current: <%=row.getCurrentItem()%>
            uf: <%=ctx.getUserFile().cldrfile.isEmpty()%>
            section size: <%=section.getAll().size()%> (was <%=oldSize%>), 
            xpath: <%=section.xpathPrefix%>
            skippedDueToCoverage: <%=section.skippedDueToCoverage%>,
            items: <%=row.items.size()%>
            </td>

            <%
            	}
            		} else {
    					if(!isJson) {
    						response.sendError(500, "Row not found");
    					} else {
    						JSONWriter r = new JSONWriter(out).object().
    								key("err").value("Row not found.").endObject();
    					}
    					return;
            		}
            	}
			
	    	} finally {
	    		// put the name back.
				curThread.setName(threadName);
			}
            %>