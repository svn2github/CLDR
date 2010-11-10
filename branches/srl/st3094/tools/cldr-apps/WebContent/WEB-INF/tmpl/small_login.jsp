<%@ page contentType="text/html; charset=UTF-8"%>
<div id='smalllogin'>
    <b class='notselected'>Login</b>
		<label for="email">Email:</label><input id="email" name="email" />
		<label for="pw">Password:</label> <input id="pw" type="password"			name="pw" />
		<input type="submit" value="Login" />
		<!--  detect javascript. Not a problem, just figure out if we can rely on it or no. -->
		<script type="text/javascript">
            <!--
             document.write("<input type='hidden' name='p_nojavascript' value='f'>");
            // -->
            </script>
		<noscript><input name='p_nojavascript' type='hidden'
			value='t'></noscript>
</div>
