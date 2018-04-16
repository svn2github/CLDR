The folder tools/SurveyConsole/WebContent/js/dojo contains a version of the Dojo framework.

Prior to 2018-4-16, the folder contained Dojo version 1.7.2. It's not clear how it was being used, exactly, since there were also references to other Dojo versions on ajax.googleapis.com, including Dojo 1.10.4 which appears mainly to be what has been in use.

As of 2018-4-16, on the branch branches/tbishop/t10396, the folder contains a human-readable "source" version of Dojo 1.13.0. It's from:

http://download.dojotoolkit.org/release-1.13.0/dojo-release-1.13.0-src.tar.gz

The purpose is to enable debugging of Dojo to help fix cldrbug 10396. For this testing, the idea is to use only this copy of Dojo, not the one served from ajax.googleapis.com.

Whether any version of Dojo should stay here for deployment is uncertain. If so, a minified version will be faster.

See dojoheader.jspf for the Dojo configuration.

