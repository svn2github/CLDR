/**
 * Example special module that shows a users page. 
 * Modify 'js/special/users.js' below to reflect your special page's name.
 * @module users
 */
define("js/special/users.js", ["js/special/SpecialPage.js"], function(SpecialPage) {
	var _super;
	
	function Page() {
		// constructor
	}
	
	// set up the inheritance before defining other functions
	_super = Page.prototype = new SpecialPage();
	
	Page.prototype.show = function show(params) {
		// set up the DIV you want to show the world
		var ourDiv = createChunk("This is a users page.","i","warn");
		
		// ourDiv.appendChild(...)
		
		// set up the 'right sidebar'
		showInPop2("This Is A users Page", null, null, null, true); /* show the box the first time */					
		
		// No longer loading
		hideLoader(null);

		// Flip to the new DIV
		params.flipper.flipTo(params.pages.other, ourDiv);
	};


	return Page;
});