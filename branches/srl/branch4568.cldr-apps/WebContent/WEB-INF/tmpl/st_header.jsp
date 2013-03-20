<%@ page language="java" contentType="text/html; charset=UTF-8"
    pageEncoding="UTF-8"%>
<%-- TODO merge st_header.jsp and st_top.jsp --%>
<%@ include file="/WEB-INF/jspf/stcontext.jspf" %> 
<%
	String htmlClass = "stOther";
	if(ctx!=null && ctx.getPageId()!=null) {
		htmlClass = "claro";
	}
%>
<!--  begin st_header.jsp -->
<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.01 Transitional//EN\" "http://www.w3.org/TR/html4/loose.dtd">
<html class="<%= htmlClass %>">
<head>

<!--  end st_header.jsp -->