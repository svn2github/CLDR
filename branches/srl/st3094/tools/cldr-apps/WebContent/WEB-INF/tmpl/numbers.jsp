<html>
  <head>
    <title>Numbers</title>
    <%@ include file="/WEB-INF/jspf/report_top.jspf" %>    
    <%@ include file="/WEB-INF/jspf/dojoheader.jspf" %>
    <script>
      dojo.require("dijit.form.FilteringSelect");
      dojo.require("dijit.form.ComboBox");
      function showVal(value) {
          alert("You selected <"+value+">");
      }
    </script>
  </head>

  <body class="claro">

    <p align="center"><font size="5" color="#FF0000">Numbers</font></p>

    <p>The data you enter for these fields will control how numbers are to be displayed in your locale.</p>
  
    <h1>Numbering System</h1>
    <p>Select the numbering system that is most commonly used to display numbers in your locale.
  The numbering system simply defines the set of characters that is used for the digits zero through nine.
  The most common numbering system is the "latn" numbering system, which uses the western digit shapes (0123456789).
  If your locale uses a numbering system that is not on the list, click here to file a problem report so that
  your numbering system can be added to the list.</p>
  
    <select name="numsys" dojoType="dijit.form.FilteringSelect" onChange="showVal">
<%
    subCtx.println("<option value=latn>Western Digits</option>");
    String[] nsNames = NumberingSystem.getAvailableNames();
    for (int i = 0; i < nsNames.length ; i++) {
        NumberingSystem ns = NumberingSystem.getInstanceByName(nsNames[i]);
        if ( !ns.isAlgorithmic() && !nsNames[i].equals("latn")) {
            subCtx.println("<option value="+nsNames[i]+">"+
                ULocale.getDisplayKeywordValue("en-u-nu-"+nsNames[i],"numbers","en")+"</option>");
           
        }
    }
%>
    </select>
    
    <h1>Decimal Point</h1>
    <p>Enter or select the character that is used to separate the whole number portion
  of a number from its decimal places.  You can either select the desired separator from the list or enter your own.</p>
    <select name="decpt" dojoType="dijit.form.ComboBox" required="true" onChange="showVal">
      <option>.</option>
      <option>,</option>
    </select>
    
    <h1>Grouping Separator</h1>
    <p>Enter or select the character that is used to separate the whole number portion
  of a number from its decimal places.  You can either select the desired separator from the list or enter your own.</p>
    <select name="grpsep" dojoType="dijit.form.ComboBox" required="true" onChange="showVal">
      <option>,</option>
      <option>.</option>
      <option value=" "> </option>
      <option>'</option>
    </select>   

    <h1>Decimal Formatting Pattern</h1>
    <p>Enter or select the pattern that can be used to define   
  the position and placement of the grouping separators. If you enter your own pattern, the pattern should contain
  3 digits after the decimal point.  Do NOT use localized decimal and/or grouping separators in the pattern.  They
  will be substituted into the formatted number during number formatting.</p>
    <select name="decpat" dojoType="dijit.form.ComboBox" required="true" onChange="showVal">
      <option>#,##0.###</option>
      <option>#,##,##0.###</option>
    </select>   
  <br/>
  
  <h1>Sample number formatted with these settings: 
  <%
    NumberFormat nf = NumberFormat.getInstance();
    double sample = 1234567890.123;
    subCtx.println(nf.format(sample));
  %></h1>
  </body>
</html>