UPDATING CODES
Periodically, the language, territory, script, currency, and country codes change.
This document describes how to update CLDR when this happens.

1. Do the following to update to a new language tag registry 
(which updates language codes, script codes, and territory codes):

- Go to http://www.iana.org/assignments/language-subtag-registry
  (you can set up a watch for changes in this page with WatchThatPage.org)
- Copy into org.unicode.cldr.util.data
- Diff with the old copy (via CVS)
- Any new codes need a corresponding update in supplementalMetadata.xml
	- languages: search for <variable id="$language", add to list
	- territories: search for <variable id="$territory", add to list
	- scripts: search for <variable id="$territory", add to list
- Edit common/main/en.xml to add the new names, based on the Descriptions in the registry file.
- If the associations to script or territory are known, edit supplementalData.xml
	- for example, add to <languageData>
		<language type="gsw" scripts="Latn" territories="CH"/>
- Run CheckCLDR -fen
	- If you missed any codes, you will get error message: "Unexpected Attribute Value"
- Run ShowLanguages to regenerate the supplemental.html file
	- Post to http://www.unicode.org/cldr/data/diff/supplemental/supplemental.html
	
2. Do the following to update to new currency codes.

- Go to http://www.iso.org/iso/en/prods-services/popstds/currencycodeslist.html
  (you can set up a watch for changes in this page with WatchThatPage.org)
  WARNING: this needs to be checked periodically, since ISO does not keep accurate histories.
[TBD]

3. Do the following to update to a new timezone database.
[TBD]
