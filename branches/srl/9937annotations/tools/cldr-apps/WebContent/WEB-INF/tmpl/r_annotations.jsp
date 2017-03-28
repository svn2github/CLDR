<%@ include file="/WEB-INF/jspf/stcontext.jspf" %><%-- setup 'ctx' --%>
<%@ include file="/WEB-INF/jspf/report.jspf"  %>
<%@ page import="org.unicode.cldr.util.*" %>


<%
%>
<h1>DANGER- HARD HAT AREA.¡PELIGRO!</h1>
<h3>Annotations</h3>
<p>Please read the <a target='CLDR-ST-DOCS' href='http://cldr.unicode.org/translation/annotations'>instructions</a> before continuing.</p>


<table class="data table table-bordered vetting-page hideCov100">
<thead>
	<td>Code</td>
	<th>English</th>
	<th><%= ctx.getLocaleDisplayName() %></th>
	</thead>
	<tbody>
<%
{
	Annotations.AnnotationSet en = Annotations.getDataSet("en");
	Annotations.AnnotationSet as = Annotations.getDataSet(ctx.getLocale().toString());
	
	for(final String k : as.keySet()) {
		%>
	<tr>
		<td><%= k %></td>
		<td><%= en.getShortName(k) %></td>
		<td><input type="radio" /><%= as.getShortName(k) %><br />
		 	<input type="radio" /><input value="" />
		 </td>
	</tr>
		<%
	}
}
%>
</tbody>
</table>



<ul>
<%
// OLD CLDRFile englishFile = ctx.sm.getSTFactory().getOldFile(CLDRLocale.getInstance("en"));
// NEW
CLDRFile nativeFile = ctx.sm.getDiskFactory(Factory.DirectoryType.annotations).make(ctx.getLocale().toString(), false);

final UnicodeSet us = new UnicodeSet();
final XPathParts xpp = new XPathParts(null,null);
if(false) for(final Iterator<String> i = nativeFile.iterator("//ldml/annotations/annotation");i.hasNext();) {
	final String x = i.next();
	final String sv =  nativeFile.getStringValue(x);
 %>
	<pre><%= x %></pre>
	<%
	xpp.clear();
	xpp.initialize(x);
	final String cp = xpp.getAttributeValue(-1, "cp");
	final String type = xpp.getAttributeValue(-1, "type");
	//us.applyPattern(cp);
	%>
		<li>
			us#<%= us.size() %>
			type=<%= type!=null?type:"<i>default</i>" %>
			<ul>
				<%
				%>
	<pre>
				<%= sv %>
	</pre>
				<%
					
				%>
			</ul>
		</li>
	<%
	
}
%>

</ul>