    <%@ include file="report_top.jspf" %>

<%@ page import="com.ibm.icu.util.Currency" %>
<%@ page import="com.ibm.icu.util.ULocale" %>

<h2>These are new items added in 1.8, or cases where the English has changed</h2>
<p>The first set are territories. All of these should be translated. They are followed by two language names and two Chinese transliteration names. These are optional.
<%
//  Copy "x=___"  from input to output URL
subCtx.setQuery(SurveyMain.QUERY_SECTION,subCtx.field(SurveyMain.QUERY_SECTION));

%><p>The first set are new territories. All of these should be translated.</p><%
SurveyForum.printSectionTableOpenShort(subCtx, thisBaseXpath);

SurveyForum.showXpathShort(subCtx, "//ldml/localeDisplayNames/territories/territory[@type=\"AC\"]");
SurveyForum.showXpathShort(subCtx, "//ldml/localeDisplayNames/territories/territory[@type=\"CP\"]");
SurveyForum.showXpathShort(subCtx, "//ldml/localeDisplayNames/territories/territory[@type=\"DG\"]");
SurveyForum.showXpathShort(subCtx, "//ldml/localeDisplayNames/territories/territory[@type=\"EA\"]");
SurveyForum.showXpathShort(subCtx, "//ldml/localeDisplayNames/territories/territory[@type=\"IC\"]");
SurveyForum.showXpathShort(subCtx, "//ldml/localeDisplayNames/territories/territory[@type=\"TA\"]");

SurveyForum.printSectionTableCloseShort(subCtx, thisBaseXpath);
%><p>Some of these have alternative values for geopoliticly sensitive cases. All of these should be translated.</p><%
SurveyForum.printSectionTableOpenShort(subCtx, thisBaseXpath);

SurveyForum.showXpathShort(subCtx, "//ldml/localeDisplayNames/territories/territory[@type=\"MM\"]");
SurveyForum.showXpathShort(subCtx, "//ldml/localeDisplayNames/territories/territory[@type=\"PS\"]");

SurveyForum.showXpathShort(subCtx, "//ldml/localeDisplayNames/territories/territory[@type=\"CD\"]");
SurveyForum.showXpathShort(subCtx, "//ldml/localeDisplayNames/territories/territory[@type=\"CD\"][@alt=\"variant\"]");
SurveyForum.showXpathShort(subCtx, "//ldml/localeDisplayNames/territories/territory[@type=\"CG\"]");
SurveyForum.showXpathShort(subCtx, "//ldml/localeDisplayNames/territories/territory[@type=\"CG\"][@alt=\"variant\"]");
SurveyForum.showXpathShort(subCtx, "//ldml/localeDisplayNames/territories/territory[@type=\"CI\"]");
SurveyForum.showXpathShort(subCtx, "//ldml/localeDisplayNames/territories/territory[@type=\"CI\"][@alt=\"variant\"]");
SurveyForum.showXpathShort(subCtx, "//ldml/localeDisplayNames/territories/territory[@type=\"FK\"]");
SurveyForum.showXpathShort(subCtx, "//ldml/localeDisplayNames/territories/territory[@type=\"FK\"][@alt=\"variant\"]");
SurveyForum.showXpathShort(subCtx, "//ldml/localeDisplayNames/territories/territory[@type=\"HK\"]");
SurveyForum.showXpathShort(subCtx, "//ldml/localeDisplayNames/territories/territory[@type=\"HK\"][@alt=\"short\"]");
SurveyForum.showXpathShort(subCtx, "//ldml/localeDisplayNames/territories/territory[@type=\"MK\"]");
SurveyForum.showXpathShort(subCtx, "//ldml/localeDisplayNames/territories/territory[@type=\"MK\"][@alt=\"variant\"]");
SurveyForum.showXpathShort(subCtx, "//ldml/localeDisplayNames/territories/territory[@type=\"MO\"]");
SurveyForum.showXpathShort(subCtx, "//ldml/localeDisplayNames/territories/territory[@type=\"MO\"][@alt=\"short\"]");
SurveyForum.showXpathShort(subCtx, "//ldml/localeDisplayNames/territories/territory[@type=\"TL\"]");
SurveyForum.showXpathShort(subCtx, "//ldml/localeDisplayNames/territories/territory[@type=\"TL\"][@alt=\"variant\"]");

SurveyForum.printSectionTableCloseShort(subCtx, thisBaseXpath);
%><p>The following are new cases of language or language variants. These are optional but recommended.</p><%
SurveyForum.printSectionTableOpenShort(subCtx, thisBaseXpath);

SurveyForum.showXpathShort(subCtx, "//ldml/localeDisplayNames/languages/language[@type=\"yue\"]");
SurveyForum.showXpathShort(subCtx, "//ldml/localeDisplayNames/languages/language[@type=\"swb\"]");
            
SurveyForum.showXpathShort(subCtx, "//ldml/localeDisplayNames/variants/variant[@type=\"PINYIN\"]");
SurveyForum.showXpathShort(subCtx, "//ldml/localeDisplayNames/variants/variant[@type=\"WADEGILE\"]");

SurveyForum.printSectionTableCloseShort(subCtx, thisBaseXpath);
%>
