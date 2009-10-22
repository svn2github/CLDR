    <%@ include file="report_top.jspf" %>

<%@ page import="com.ibm.icu.util.Currency" %>
<%@ page import="com.ibm.icu.util.ULocale" %>

<h2>List Patterns are also new in 1.8</h2>
<p>
These are used to represent lists. The <em>2</em> form is for a list of two items, like '<em>A</em> and <em>B</em>'.
The <em>start</em>, <em>middle</em>, and <em>end</em> forms are used for 3 or more items, such as '<em>A</em>, <em>B</em>, and <em>C</em>'.
The <em>start</em> form connects the first two items; the <em>end</em> connects the last two, and the <em>middle</em> connects the middle ones
(for lists of four or more items).</p>
<p><i>If your language needs special forms for 3, 4, or other cases, 
<a href='http://unicode.org/cldr/trac/newticket'>file a ticket</a> to add them.</i>.
</p>
<%
//  Copy "x=___"  from input to output URL

subCtx.setQuery(SurveyMain.QUERY_SECTION,subCtx.field(SurveyMain.QUERY_SECTION));
SurveyForum.printSectionTableOpenShort(subCtx, thisBaseXpath);

SurveyForum.showXpathShort(subCtx, "//ldml/listPatterns/listPattern/listPatternPart[@type=\"2\"]");
SurveyForum.showXpathShort(subCtx, "//ldml/listPatterns/listPattern/listPatternPart[@type=\"start\"]");
SurveyForum.showXpathShort(subCtx, "//ldml/listPatterns/listPattern/listPatternPart[@type=\"middle\"]");
SurveyForum.showXpathShort(subCtx, "//ldml/listPatterns/listPattern/listPatternPart[@type=\"end\"]");

SurveyForum.printSectionTableCloseShort(subCtx, thisBaseXpath);
%>
