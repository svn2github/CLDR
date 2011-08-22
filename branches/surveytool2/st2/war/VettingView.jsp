<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="com.ibm.icu.lang.CharSequences" %>
<%@ page import="java.util.EnumSet" %>
<%@ page import="org.unicode.cldr.surveytool.server.CloudFactory" %>
<%@ page import="org.unicode.cldr.test.CheckCLDR" %>
<%@ page import="org.unicode.cldr.util.CLDRFile" %>
<%@ page import="org.unicode.cldr.util.CLDRFile.DraftStatus" %>
<%@ page import="org.unicode.cldr.util.CLDRFile.Factory" %>
<%@ page import="org.unicode.cldr.util.Level" %>
<%@ page import="org.unicode.cldr.util.SupplementalDataInfo" %>
<%@ page import="org.unicode.cldr.util.VettingViewer" %>
<%@ page import="org.unicode.cldr.util.VettingViewer.CodeChoice" %>
<%@ page import="org.unicode.cldr.util.VettingViewer.VoteStatus" %>
<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">
<%
  // The contents of this file are adapted from VettingViewer.main().
  // here are per-view parameters
  boolean memory = Boolean.valueOf(request.getParameter("memory"));
  final EnumSet<VettingViewer.Choice> choiceSet = EnumSet.allOf(VettingViewer.Choice.class);
  String localeStringID = request.getParameter("locale");
  if (localeStringID == null) {
      localeStringID = "de";
  }
  int userNumericID;
  try {
      userNumericID = Integer.parseInt(request.getParameter("userNumericID"));
  } catch (NumberFormatException e) {
      userNumericID = 666;
  }
  Level usersLevel = Level.MODERN;
  try {
      String levelParam = request.getParameter("usersLevel");
      if (levelParam != null) usersLevel = Level.valueOf(levelParam);
  } catch (IllegalArgumentException e) {}
  CodeChoice code = CodeChoice.newCode;
  try {
      String codeParam = request.getParameter("code");
      if (codeParam != null)  code = CodeChoice.valueOf(codeParam);
  } catch (IllegalArgumentException e) {}
  String baseUrl = "http://kwanyin.unicode.org/cldr-apps/survey";
  boolean testing = false;
  boolean showAll = false;
%>
<html>
<head>
<meta http-equiv='Content-Type' content='text/html; charset=UTF-8'>
<title>mockup</title>
<script type="text/javascript" src="vettingView.js"></script>
<link rel="stylesheet" type="text/css" href="vettingView.css">
<style type='text/css'>
.hide {display:none}
.vve {}
.vvn {}
.vvl {}
.vvm {}
.vvu {}
.vvw {}
.vvd {}
.vvo {}
</style>
</head>
<body>
    <p>Note: this is just a sample run. The user, locale, user's coverage level, and choices of tests will change the output. In a real ST page using these, the first three would 
    come from context, and the choices of tests would be set with radio buttons. Demo settings are: </p>
    <ol>
      <li>choices: <%=choiceSet %></li>
      <li>localeStringID: <%=localeStringID %></li>
      <li>userNumericID: <%= userNumericID %></li>
      <li>usersLevel: <%= usersLevel %></li>
    </ol>
    <p>Notes: This is a static version, using old values (1.9.1) and faked values (L) just for testing.
    <%= testing ? "Also, the white cell after the Fix column is just for testing." : "" %>
    </p>
    <hr>
    <%
    long startTime = System.currentTimeMillis();

    final String currentMain = "WEB-INF/2.1/common/main";
    final String lastMain = "WEB-INF/1.9.1/common/main";
    final String SUPPLEMENTAL_DIRECTORY = "WEB-INF/2.1/common/supplemental/";
    Factory cldrFactory, cldrFactoryOld;
    if (memory) {
        // load from datastore
        cldrFactory = CloudFactory.make(currentMain, ".*", DraftStatus.unconfirmed, "2.1");
        cldrFactoryOld = CloudFactory.make(lastMain, ".*", DraftStatus.unconfirmed, "1.9.1");
        CLDRFile cldrFile = cldrFactory.make("en", true);
        CheckCLDR.setDisplayInformation(cldrFile);
    } else {
        // load from disk
        cldrFactory = Factory.make(currentMain, ".*");
        cldrFactoryOld = Factory.make(lastMain, ".*");
        CheckCLDR.setDisplayInformation(cldrFactory.make("en", true));
    }
    SupplementalDataInfo supplementalDataInfo = SupplementalDataInfo.getInstance(SUPPLEMENTAL_DIRECTORY);
    long factoryTime = System.currentTimeMillis();

    //FAKE this, because we don't have access to ST data
    VettingViewer.UsersChoice<Integer> usersChoice = new VettingViewer.UsersChoice<Integer>() {
        // Fake values for now
        public String getWinningValueForUsersOrganization(CLDRFile cldrFile, String path, Integer user) {
            if (path.contains("AFN")) {
                return "dummy &mdash; losing &mdash; value";
            }
            return null; // assume we didn't vote on anything else.
        }

        // Fake values for now
        public VoteStatus getStatusForUsersOrganization(CLDRFile cldrFile, String path, Integer user) {
            String usersValue = getWinningValueForUsersOrganization(cldrFile, path, user);
            String winningValue = cldrFile.getWinningValue(path);
            if (CharSequences.equals(usersValue, winningValue)) {
                return VoteStatus.ok;
            }
            String fullPath = cldrFile.getFullXPath(path);
            if (fullPath.contains("AMD") || fullPath.contains("unconfirmed") || fullPath.contains("provisional")) {
                return VoteStatus.provisionalOrWorse;
            }
            if (fullPath.contains("AED")) {
                return VoteStatus.disputed;
            }
            return VoteStatus.ok;
        }
    };

    // create the tableView and set the options desired.
    // The Options should come from a GUI; from each you can get a long
    // description and a button label.
    // Assuming user can be identified by an int
    VettingViewer<Integer> tableView = new VettingViewer<Integer>(supplementalDataInfo, cldrFactory, cldrFactoryOld, usersChoice, "CLDR 1.9.1",
        "Winning 2.1");

    tableView.setBaseUrl(baseUrl);

    switch (code) {
    case newCode:
        tableView.generateHtmlErrorTablesNew(out, choiceSet, localeStringID, userNumericID, usersLevel, showAll);
        break;
    case oldCode:
        tableView.generateHtmlErrorTablesOld(out, choiceSet, localeStringID, userNumericID, usersLevel, showAll);
        break;
    case summary:
        tableView.generateSummaryHtmlErrorTables(out, choiceSet, VettingViewer.HackIncludeLocalesWithVotes);
        break;
    }
    
    long genTime = System.currentTimeMillis();
    %>
    <br>
    Time taken to generate report <%= memory ? "from the datastore" : "from disk" %>: <%= genTime - startTime %> ms
</body>
</html>