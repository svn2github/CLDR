package org.unicode.cldr.util;

import java.io.FileInputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.XMLReader;
import org.xml.sax.ext.DeclHandler;

import com.ibm.icu.dev.test.util.Differ;
import com.ibm.icu.dev.test.util.XEquivalenceClass;
import com.ibm.icu.dev.test.util.XEquivalenceMap;

class FindDTDOrder implements DeclHandler, ContentHandler, ErrorHandler {
	static final boolean SHOW_ALL = false;
	static final boolean SHOW_PROGRESS = true;
	
	public static void main(String[] args){
		//StringBufferInputStream fis = new StringBufferInputStream(
		//"<!DOCTYPE ldml SYSTEM \"http://www.unicode.org/cldr/dtd/1.2/ldml.dtd\"><ldml></ldml>");
		try {
			FindDTDOrder me = new FindDTDOrder();
			XMLReader xmlReader = CLDRFile.createXMLReader(true);
			xmlReader.setContentHandler(me);
			xmlReader.setErrorHandler(me);
			xmlReader.setProperty("http://xml.org/sax/properties/declaration-handler", me);

			FileInputStream fis;
			InputSource is;
			fis = new FileInputStream(Utility.COMMON_DIRECTORY + "main/root.xml");
			is = new InputSource(fis);
			xmlReader.parse(is);
			fis = new FileInputStream(Utility.COMMON_DIRECTORY + "supplemental/supplementalData.xml");
			is = new InputSource(fis);
			xmlReader.parse(is);
			me.checkData();
		} catch (Exception e) {
			e.printStackTrace();
		}	
	}
	
	PrintWriter log = null;
	Map element_childComparator = new LinkedHashMap();
	boolean showReason = false;
	Object DONE = new Object(); // marker
	
	FindDTDOrder () {
		log = new PrintWriter(System.out);
	}
	
	public void checkData() {
		// verify that the ordering is the consistent for all child elements
		// do this by building an ordering from the lists.
		// The first item has no greater item in any set. So find an item that is only first
		showReason = false;
		List orderingList = new ArrayList();
		orderingList.add("ldml");
		if (log != null) log.println("structure: ");
		for (Iterator it = element_childComparator.keySet().iterator(); it.hasNext();) {
			Object key = it.next();
			Object value = element_childComparator.get(key);
			log.println(key + "=\t" + value);
		}
		while (true) {
			Object first = getFirst();
			if (first == DONE) break;
			if (first != null) {
				//log.println("Adding:\t" + first);
				if (orderingList.contains(first)) {
					throw new IllegalArgumentException("Already present: " + first);
				}
				orderingList.add(first);
			} else {
				showReason = true;
				getFirst();
				if (log != null) log.println();
				if (log != null) log.println("Failed ordering. So far:");
				for (Iterator it = orderingList.iterator(); it.hasNext();) if (log != null) log.print("\t" + it.next());
				if (log != null) log.println();
				if (log != null) log.println("Items:");
				for (Iterator it =  element_childComparator.keySet().iterator(); it.hasNext();) showRow(it.next(), true);
				if (log != null) log.println();
				break;
			}
		}
		// finish up
		if (log != null) log.println("Successful Ordering");
		log.print("Attributes: ");
		log.println(getJavaList(attributeList));
		attributeList.removeAll(CLDRFile.attributeOrdering.getOrder());
		log.print("New Attributes: ");
		log.println(getJavaList(attributeList));
		XEquivalenceClass xec = new XEquivalenceClass(null);
		for (Iterator it = attribEquiv.keySet().iterator(); it.hasNext();) {
			Object ename = it.next();
			Set s = (Set) attribEquiv.get(ename);
			Iterator it2 = s.iterator();
			Object first = it2.next();
			while (it2.hasNext()) {
				xec.add(first, it2.next(), ename);
			}
		}
		log.println("Attribute Eq: ");
		for (Iterator it = xec.getSamples().iterator(); it.hasNext();) {
			log.println("\t" + getJavaList(new TreeSet(xec.getEquivalences(it.next()))));
		}
		for (Iterator it = xec.getEquivalenceSets().iterator(); it.hasNext();) {
			Object last = null;
			Set s = (Set)it.next();
			for (Iterator it2 = s.iterator(); it2.hasNext();) {
				Object temp = it2.next();
				if (last != null) log.println(last + " ~ " + temp + "\t" + xec.getReasons(last, temp));
				last = temp;
			}
			log.println();
		}
		
		log.println(getJavaList(orderingList));
		log.println(getJavaList(CLDRFile.elementOrdering.getOrder()));
		
		log.println("Old Size: " + CLDRFile.elementOrdering.getOrder().size());
		Set temp = new HashSet(CLDRFile.elementOrdering.getOrder());
		temp.removeAll(orderingList);
		log.println("Old - New: " + temp);
		log.println("New Size: " + orderingList.size());
		temp = new HashSet(orderingList);
		temp.removeAll(CLDRFile.elementOrdering.getOrder());
		log.println("New - Old: " + temp);
		
		Differ differ = new Differ(200, 1);
		Iterator oldIt = CLDRFile.elementOrdering.getOrder().iterator();
		Iterator newIt = orderingList.iterator();
		while (oldIt.hasNext() || newIt.hasNext()) {
			if (oldIt.hasNext()) differ.addA(oldIt.next());
			if (newIt.hasNext()) differ.addB(newIt.next());
			differ.checkMatch(!oldIt.hasNext() && !newIt.hasNext());
			
			if (differ.getACount() != 0 || differ.getBCount() != 0) {
				log.println("Same: " + differ.getA(-1));
				for (int i = 0; i < differ.getACount(); ++i) {
					log.println("\tOld: " + differ.getA(i));
				}
				for (int i = 0; i < differ.getBCount(); ++i) {
					log.println("\t\tNew: " + differ.getB(i));
				}
				log.println("Same: " + differ.getA(differ.getACount()));
			}
		}
		log.println("Done with differences");

		if (log != null) log.flush();
	}
	
	private String getJavaList(Collection orderingList) {
		boolean first2 = true;
		StringBuffer result = new StringBuffer();
		result.append("{");
		for (Iterator it = orderingList.iterator(); it.hasNext();) {
			if (first2) first2 = false;
			else result.append(", ");
			result.append("\"" + it.next().toString() + "\"");
		}
		result.append("}");
		return result.toString();
	}
	
	/**
	 * @param parent
	 * @param skipEmpty TODO
	 */
	private void showRow(Object parent, boolean skipEmpty) {
		List items = (List) element_childComparator.get(parent);
		if (skipEmpty && items.size() == 0) return;
		if (log != null) log.print(parent);
		for (Iterator it2 = items.iterator(); it2.hasNext();) if (log != null) log.print("\t" + it2.next());
		if (log != null) log.println();
	}
	
	/**
	 * @param orderingList
	 */
	private Object getFirst() {
		Set keys = element_childComparator.keySet();
		Set failures = new HashSet();
		boolean allZero = true;
		for (Iterator it = keys.iterator(); it.hasNext();) {
			List list = (List) element_childComparator.get(it.next());
			if (list.size() != 0) {
				allZero = false;
				Object possibleFirst = list.get(0);
				if (!failures.contains(possibleFirst) && isNeverNotFirst(possibleFirst)) {
					// we survived the guantlet. add to ordering list, remove from the mappings
					removeEverywhere(possibleFirst);
					return possibleFirst;
				} else {
					failures.add(possibleFirst);
				}
			}
		}
		if (allZero) return DONE;
		return null;
	}
	/**
	 * @param possibleFirst
	 */
	private void removeEverywhere(Object possibleFirst) {
		// and remove from all the lists
		for (Iterator it2 = element_childComparator.keySet().iterator(); it2.hasNext();) {
			List list2 = (List) element_childComparator.get(it2.next());
			if (SHOW_PROGRESS && list2.contains(possibleFirst)) {
				if (log != null) log.println("Removing " + possibleFirst + " from " + list2);
			}
			list2.remove(possibleFirst);
		}
	}
	
	private boolean isNeverNotFirst(Object possibleFirst) {
		if (showReason) if (log != null) log.println("Trying: " + possibleFirst);
		for (Iterator it2 = element_childComparator.keySet().iterator(); it2.hasNext();) {
			Object key = it2.next();
			List list2 = (List) element_childComparator.get(key);
			int pos = list2.indexOf(possibleFirst);
			if (pos > 0) {
				if (showReason) {
					if (log != null) log.print("Failed at:\t");
					showRow(key, false);
				}
				return false;
			}
		}
		return true;
	}
	
	static final Set ELEMENT_SKIP_LIST = new HashSet(Arrays.asList(new String[] {
			"collation", "base", "settings", "suppress_contractions", "optimize", "rules", "reset",
			"context", "p", "pc", "s", "sc", "t", "tc", "q", "qc", "i", "ic", "extend", "x"
	}));
	
	static final Set SUBELEMENT_SKIP_LIST = new HashSet(Arrays.asList(new String[] {"PCDATA", "EMPTY", "ANY"}));
	
	// refine later; right now, doesn't handle multiple elements well.
	public void elementDecl(String name, String model) throws SAXException {
		if (ELEMENT_SKIP_LIST.contains(name)) return;
		if (log != null) log.println("Element\t" + name + "\t" + model);
		String[] list = model.split("[^A-Z0-9a-z]");
		List mc = new ArrayList();
		/*if (name.equals("currency")) {
		 mc.add("alias");
		 mc.add("symbol");
		 mc.add("pattern");
		 }
		 */
		for (int i = 0; i < list.length; ++i) {
			if (list[i].length() == 0) continue;
			if (SUBELEMENT_SKIP_LIST.contains(list[i])) continue;
			//if (log != null) log.print("\t" + list[i]);
			if (mc.contains(list[i])) {
				if (log != null) log.println("Duplicate attribute " + name + ", " + list[i]
				                                                                         + ":\t" + Arrays.asList(list)
				                                                                         + ":\t" + mc
				);    
			} else {
				mc.add(list[i]);
			}
		}

			if (mc.size() < 1) {
				log.println("\tSKIPPING\t" + name + "\t" + mc);
			} else {
				log.println("\t" + name + "\t" + mc);
				element_childComparator.put(name, mc);
			}

		//if (log != null) log.println();
	}
	Set skipCommon = new HashSet(Arrays.asList(new String[]{"alt", "draft", "standard", "references", "validSubLocales"}));
	final Set attributeList = new TreeSet();
	TreeMap attribEquiv = new TreeMap();
	public void attributeDecl(String eName, String aName, String type, String mode, String value) throws SAXException {
		if (SHOW_ALL && log != null) log.println("attributeDecl");			
		// if (SHOW_ALL && log != null) log.println("Attribute\t" + eName + "\t" + aName + "\t" + type + "\t" + mode + "\t" + value);
		System.out.println("Attribute\t" + eName + "\t" + aName + "\t" + type + "\t" + mode + "\t" + value);
		if (!skipCommon.contains(aName)) {
		attributeList.add(aName);
			Set l = (Set) attribEquiv.get(eName);
			if (l == null) attribEquiv.put(eName, l = new TreeSet());
			l.add(aName);
		}
	}
	public void internalEntityDecl(String name, String value) throws SAXException {
		if (SHOW_ALL && log != null) log.println("internalEntityDecl");			
		//if (SHOW_ALL && log != null) log.println("Internal Entity\t" + name + "\t" + value);
	}
	public void externalEntityDecl(String name, String publicId, String systemId) throws SAXException {
		if (SHOW_ALL && log != null) log.println("externalEntityDecl");			
		//if (SHOW_ALL && log != null) log.println("Internal Entity\t" + name + "\t" + publicId + "\t" + systemId);
	}
	
	/* (non-Javadoc)
	 * @see org.xml.sax.ContentHandler#endDocument()
	 */
	public void endDocument() throws SAXException {
		if (SHOW_ALL && log != null) log.println("endDocument");			
	}
	
	/* (non-Javadoc)
	 * @see org.xml.sax.ContentHandler#startDocument()
	 */
	public void startDocument() throws SAXException {
		if (SHOW_ALL && log != null) log.println("startDocument");
	}
	
	/* (non-Javadoc)
	 * @see org.xml.sax.ContentHandler#characters(char[], int, int)
	 */
	public void characters(char[] ch, int start, int length) throws SAXException {
		if (SHOW_ALL && log != null) log.println("characters");
	}
	
	/* (non-Javadoc)
	 * @see org.xml.sax.ContentHandler#ignorableWhitespace(char[], int, int)
	 */
	public void ignorableWhitespace(char[] ch, int start, int length) throws SAXException {
		if (SHOW_ALL && log != null) log.println("ignorableWhitespace");
	}
	
	/* (non-Javadoc)
	 * @see org.xml.sax.ContentHandler#endPrefixMapping(java.lang.String)
	 */
	public void endPrefixMapping(String prefix) throws SAXException {
		if (SHOW_ALL && log != null) log.println("endPrefixMapping");
	}
	
	/* (non-Javadoc)
	 * @see org.xml.sax.ContentHandler#skippedEntity(java.lang.String)
	 */
	public void skippedEntity(String name) throws SAXException {
		if (SHOW_ALL && log != null) log.println("skippedEntity");
	}
	
	/* (non-Javadoc)
	 * @see org.xml.sax.ContentHandler#setDocumentLocator(org.xml.sax.Locator)
	 */
	public void setDocumentLocator(Locator locator) {
		if (SHOW_ALL && log != null) log.println("setDocumentLocator");
	}
	
	/* (non-Javadoc)
	 * @see org.xml.sax.ContentHandler#processingInstruction(java.lang.String, java.lang.String)
	 */
	public void processingInstruction(String target, String data) throws SAXException {
		if (SHOW_ALL && log != null) log.println("processingInstruction");
	}
	
	/* (non-Javadoc)
	 * @see org.xml.sax.ContentHandler#startPrefixMapping(java.lang.String, java.lang.String)
	 */
	public void startPrefixMapping(String prefix, String uri) throws SAXException {
		if (SHOW_ALL && log != null) log.println("startPrefixMapping");
	}
	
	/* (non-Javadoc)
	 * @see org.xml.sax.ContentHandler#endElement(java.lang.String, java.lang.String, java.lang.String)
	 */
	public void endElement(String namespaceURI, String localName, String qName) throws SAXException {
		if (SHOW_ALL && log != null) log.println("endElement");
	}
	
	/* (non-Javadoc)
	 * @see org.xml.sax.ContentHandler#startElement(java.lang.String, java.lang.String, java.lang.String, org.xml.sax.Attributes)
	 */
	public void startElement(String namespaceURI, String localName, String qName, Attributes atts) throws SAXException {
		if (SHOW_ALL && log != null) log.println("startElement");
	}
	
	/* (non-Javadoc)
	 * @see org.xml.sax.ErrorHandler#error(org.xml.sax.SAXParseException)
	 */
	public void error(SAXParseException exception) throws SAXException {
		if (SHOW_ALL && log != null) log.println("error");
		throw exception;
	}
	
	/* (non-Javadoc)
	 * @see org.xml.sax.ErrorHandler#fatalError(org.xml.sax.SAXParseException)
	 */
	public void fatalError(SAXParseException exception) throws SAXException {
		if (SHOW_ALL && log != null) log.println("fatalError");
		throw exception;
	}
	
	/* (non-Javadoc)
	 * @see org.xml.sax.ErrorHandler#warning(org.xml.sax.SAXParseException)
	 */
	public void warning(SAXParseException exception) throws SAXException {
		if (SHOW_ALL && log != null) log.println("warning");
		throw exception;
	}
	
}