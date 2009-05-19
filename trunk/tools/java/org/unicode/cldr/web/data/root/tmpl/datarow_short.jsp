<%@ include file="stcontext.jspf" %><%-- setup 'ctx' --%>

<tr>
  <th><%= dataRow.getDisplayName() %></th>
  <td> <input name="<%= dataRow.fullFieldHash() %>" value="<%= SurveyMain.CHANGETO %>" type='hidden'>
  <input class="inputbox" name="<%= dataRow.fullFieldHash() %>_v" value="<%= dataRow.getWinningValue() %>"></td>

<%
SummarizingSubmissionResultHandler ssrh = (SummarizingSubmissionResultHandler)ctx.get("ssrh");
SummarizingSubmissionResultHandler.ItemInfo itemInfo = null;
if(ssrh != null) itemInfo = ssrh.infoFor(dataRow);

if(itemInfo != null) {
	String iconHtml;
	if(itemInfo.getStatus()==SummarizingSubmissionResultHandler.ItemStatus.ITEM_GOOD) {
		iconHtml = ctx.iconHtml("okay","Item OK");
	} else if(itemInfo.getStatus()==SummarizingSubmissionResultHandler.ItemStatus.ITEM_BAD) {
		iconHtml = ctx.iconHtml("stop", "Item Not OK");
	} else {
		iconHtml = ctx.iconHtml("ques", "Unknown State");
	}
  %>
  	<td><%= iconHtml %><%= itemInfo.getDescription() %></td>
  <%
}
%>

</tr>
