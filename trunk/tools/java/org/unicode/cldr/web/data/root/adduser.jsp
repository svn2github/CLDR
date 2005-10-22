<html>
	<head>
		<title>CLDR Web Applications : Add a user</title>
		<link rel='stylesheet' type='text/css' href='./surveytool.css' />
	</head>
	<body>
		<h1>Add a Survey Tool user</h1>
		<form action="<%= request.getParameter("a") %>" method="POST">
            <input type="hidden" name="s" value="<%= request.getParameter("s") %>" />
            <input type="hidden" name="do" value="new" />
			<label>Name: <input size=40 name="new_name" /></label><br/>
			<label>Email: <input size=40 name="new_email" /></label><br/>
			<label>Organization: <input name="new_org" /></label>   (note: leave blank if same as yours)<br/>
			<label>Userlevel: <input name="new_userlevel" value="5" /></label>   (5=vetter, 10=street, ...)<br/>
			<input type="submit" value="Add" />
		</form>
		<hr/>
		<a href="<%= request.getParameter("a") %>?s=<%= request.getParameter("s") %>">Cancel, return to Survey Tool</a> |
		<a href="./index.jsp">Return to CLDR Applications</a>
	</body>
</html>
