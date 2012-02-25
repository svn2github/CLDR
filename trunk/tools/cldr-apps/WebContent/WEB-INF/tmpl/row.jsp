<%@ page language="java" contentType="text/html; charset=UTF-8"
    pageEncoding="UTF-8"%><%@ include file="/WEB-INF/jspf/stcontext.jspf" %><%-- setup 'ctx' --%><%
    
    DataSection.DataRow p  = dataRow;
    BallotBox<UserRegistry.User> ballotBox = (BallotBox<UserRegistry.User>)ctx.get("DataRow.ballotBox");
    String xpath = ctx.get("DataRow.xpath").toString();
	String ourVote = null;
	if (ctx.session.user != null) {
		ourVote = ballotBox.getVoteValue(ctx.session.user, xpath);
	}
	List<DataSection.DataRow.CandidateItem> currentItems = p.getCurrentItems();
	List<DataSection.DataRow.CandidateItem> proposedItems = p.getProposedItems();
	String statusIcon = p.getStatusIcon(ctx);
	String specialUrl = p.getSpecialURL(ctx);
	String rclass =  p.getRowClass();
	int base_xpath = ctx.sm.xpt.xpathToBaseXpathId(xpath);
	int xfind = ctx.fieldInt(SurveyMain.QUERY_XFIND);

	// Does the inheritedValue contain a test that we need to display?
	// calculate the max height of the current row.
	int rowSpan = Math.max(proposedItems.size(), currentItems.size());
	rowSpan = Math.max(rowSpan, 1);
	String baseExample = (String)ctx.get("DataRow.baseExample");
%>


<th rowspan='<%= rowSpan %>' class='<%= rclass %>' valign='top' width='30'>
<% if(!zoomedIn) { %>
	<% if(specialUrl!=null) { %>
		<a class='notselected' <%= ctx.atarget() %> href='<%= specialUrl %>'><%= statusIcon %></a>
	<% } else { %>
		<%= ctx.sm.fora.getForumLink(ctx, p.parentRow, statusIcon) %>
	<% }
   } else { %>
   	<%= statusIcon %>
 <% } %>
 </th>
 
<th rowspan='<%= rowSpan %>' class='<%= rclass %>' valign='top' width='30'>
	<%= p.getDraftIcon(ctx) %>
</th>
<% if(canModify) { %>
	<th rowspan='<%= rowSpan %>' class='<%= rclass %>' valign='top' width='30'>
		<%= p.getVotedIcon(ctx) %>
	</th>
<% } %>
<th rowspan='<%= rowSpan %>' class='botgray' valign='top' align='left'>
	<%= p.itemTypeName(ctx,canModify,zoomedIn,specialUrl) %>
	<% if (p.altType !=null) { %>
		<br> (<%= p.altType %> alternative)
	<% } %>
	<%-- <%= ctx.base() --%>
</th>
<th rowspan='<%= rowSpan %>' style='padding-left: 4px;' colspan='1' valign='top' align='left' class='botgray'>
	<%= p.getDisplayName()  
	      // + " " + p.fullFieldHash()
	%>
<% if(false) { %>	       <br/>
	X=<%= p.getXpath() %>
	       <br/>
	W=<%= ballotBox.getResolver(p.getXpath()).getWinningValue()  %>
	       <br/>
	<% if(currentItems==null){ %><i>null currentitems!</i> <% } else if(currentItems.size()<1){%>no current items!<%}else { %>
	P=<%= currentItems.get(0).getPClass(ctx) %>
	       <br/>
	I=<%= currentItems.get(0).value %>
	       <br/>
    CurrentItemsCount:<%= currentItems.size() %>, prop: <%= proposedItems.size() %>	       
	W? = <%= Boolean.toString(currentItems.get(0).isWinner()) %>
<% }} %></th>
<% if(p.hasExamples()) { 
			if(baseExample!=null) { %>
				<td rowspan='<%= rowSpan %>' align='left' valign='top' class='generatedexample'>
						<%= baseExample.replaceAll("\\\\", "\u200b\\\\")  %>
				</td>
			<% } else { %>
				<td rowspan='<%= rowSpan %>'></td>
			<% } 
} %>
	
<% /* TODO: more from showDataRow needs to move here. */ %>

