/*
 ******************************************************************************
 * Copyright (C) 2004, International Business Machines Corporation and        *
 * others. All Rights Reserved.                                               *
 ******************************************************************************
*/
package org.unicode.cldr.util;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.unicode.cldr.util.CLDRFile.StringValue;
import org.unicode.cldr.util.CLDRFile.Value;

import com.ibm.icu.dev.test.util.BagFormatter;

/**
 * Parser for XPath
 */
public class XPathParts {
	private List elements = new ArrayList();
	Comparator attributeComparator;
	
	public XPathParts(Comparator attributeComparator) {
		this.attributeComparator = attributeComparator;
	}
	
	//private static MapComparator AttributeComparator = new MapComparator().add("alt").add("draft").add("type");

	/**
	 * See if the xpath contains an element
	 */
	public boolean containsElement(String element) {
		for (int i = 0; i < elements.size(); ++i) {
			if (((Element)elements.get(i)).element.equals(element)) return true;
		}
		return false;
	}
	/**
	 * Empty the xpath (pretty much the same as set(""))
	 */
	public XPathParts clear() {
		elements.clear();
		return this;
	}
	/**
	 * Write out the difference form this xpath and the last, putting the value in the right place. Closes up the elements
	 * that were not closed, and opens up the new.
	 * @param pw
	 * @param last
	 * @param attributeComparator TODO
	 */
	public void writeDifference(PrintWriter pw, XPathParts last, Value v, Comparator attributeComparator) {
		int limit = findFirstDifference(last);
		// write the end of the last one
		for (int i = last.size()-2; i >= limit; --i) {
			CLDRFile.indent(pw, i);
			pw.println(((Element)last.elements.get(i)).toString(XML_CLOSE, attributeComparator));
		}
		if (v == null) return; // end
		// now write the start of the current
		for (int i = limit; i < size()-1; ++i) {
			CLDRFile.indent(pw, i);
			pw.println(((Element)elements.get(i)).toString(XML_OPEN, attributeComparator));
		}
		CLDRFile.writeComment(pw, size()-1, v.getComment());
		// now write element itself
		CLDRFile.indent(pw, size()-1);
		Element e = (Element)elements.get(size()-1);
		String eValue = ((StringValue)v).getStringValue();
		if (eValue.length() == 0) {
			pw.println(e.toString(XML_NO_VALUE, attributeComparator));
		} else {
			pw.print(e.toString(XML_OPEN, attributeComparator));
			pw.print(BagFormatter.toHTML.transliterate(eValue));
			pw.println(e.toString(XML_CLOSE, attributeComparator));
		}
		//if (v.)
		pw.flush();
	}
	
	/**
	 * Finds the first place where the xpaths differ.
	 * @param last
	 * @return
	 */
	public int findFirstDifference(XPathParts last) {
		int min = elements.size();
		if (last.elements.size() < min) min = last.elements.size();
		for (int i = 0; i < min; ++i) {
			Element e1 = (Element) elements.get(i);
			Element e2 = (Element) last.elements.get(i);
			if (!e1.equals(e2)) return i;
		}
		return min;
	}
	/**
	 * Does this xpath contain the attribute at all?
	 * @param attribute
	 * @return
	 */
	public boolean containsAttribute(String attribute) {
		for (int i = 0; i < elements.size(); ++i) {
			Element element = (Element) elements.get(i);
			if (element.attributes.keySet().contains(attribute)) return true;
		}
		return false;
	}
	/**
	 * Does it contain the attribute/value pair?
	 * @param attribute
	 * @param value
	 * @return
	 */
	public boolean containsAttributeValue(String attribute, String value) {
		for (int i = 0; i < elements.size(); ++i) {
			Map attributes = ((Element)elements.get(i)).attributes;
			for (Iterator it = attributes.keySet().iterator(); it.hasNext();) {
				String a = (String) it.next();
				if (a.equals(attribute)) {
					String v = (String)attributes.get(a);
					if (v.equals(value)) return true;
				}
			}
		}
		return false;
	}
	
	/**
	 * How many elements are in this xpath?
	 * @return
	 */
	public int size() {
		return elements.size();
	}
	/**
	 * Get the nth element
	 * @param elementIndex
	 * @return
	 */
	public String getElement(int elementIndex) {
		return ((Element)elements.get(elementIndex)).element;
	}
	/**
	 * Get the attributes for the nth element. Returns null or an empty map if there's nothing.
	 * @param elementIndex
	 * @return
	 */
	public Map getAttributes(int elementIndex) {
		return ((Element)elements.get(elementIndex)).attributes;
	}
	
	/**
	 * Get the attributes for the nth element. Returns null or an empty map if there's nothing.
	 * @param elementIndex
	 * @return
	 */
	public Map findAttributes(String elementName) {
		int index = findElement(elementName);
		if (index == -1) return null;
		return getAttributes(index);
	}

	/**
	 * Add an element
	 * @param element
	 */
	public void addElement(String element) {
		elements.add(new Element(element));
	}
	/**
	 * Add an attribute/value pair to the current last element.
	 * @param attribute
	 * @param value
	 */
	public void addAttribute(String attribute, String value) {
		Element e = (Element)elements.get(elements.size()-1);
		attribute = attribute.intern();
		//AttributeComparator.add(attribute);
		e.attributes.put(attribute, value);
	}

	/**
	 * Parse out an xpath, and pull in the elements and attributes.
	 * @param xPath
	 * @return
	 */
	public XPathParts set(String xPath) {
    	elements.clear();
    	return setInternal(xPath);
	}
	
	private XPathParts setInternal(String xPath) {
    	String lastAttributeName = "";
		if (xPath.length() == 0 || xPath.charAt(0) != '/') return parseError(xPath, 0);
		int stringStart = 1;
		char state = 'p';
		// since only ascii chars are relevant, use char
		for (int i = 1; i < xPath.length(); ++i) {
			char cp = xPath.charAt(i);
			if (cp != state && (state == '\"' || state == '\'')) continue; // stay in quotation
			switch(cp) {
			case '/':
				if (state != 'p' || stringStart >= i) return parseError(xPath,i);
    			if (stringStart > 0) addElement(xPath.substring(stringStart, i));
				stringStart = i+1;
				break;
			case '[':
				if (state != 'p' || stringStart >= i) return parseError(xPath,i);
				if (stringStart > 0) addElement(xPath.substring(stringStart, i));
				state = cp;
				break;
			case '@': 
				if (state != '[') return parseError(xPath,i);
				stringStart = i+1;
				state = cp;
				break;
			case '=': 
				if (state != '@' || stringStart >= i) return parseError(xPath,i);
				lastAttributeName = xPath.substring(stringStart, i);
				state = cp;
				break;
			case '\"':
			case '\'':
				if (state == cp) { // finished
					if (stringStart >= i) return parseError(xPath,i);
					addAttribute(lastAttributeName, xPath.substring(stringStart, i));
					state = 'e';
					break;
				}
				if (state != '=') return parseError(xPath,i);
				stringStart = i+1;
				state = cp;
				break;
			case ']': 
				if (state != 'e') return parseError(xPath,i);
				state = 'p';
				stringStart = -1;
				break;
			}
		}
		// check to make sure terminated
		if (state != 'p' || stringStart >= xPath.length()) return parseError(xPath,xPath.length());
		if (stringStart > 0) addElement(xPath.substring(stringStart, xPath.length()));
		return this;
	}

	/**
	 * boilerplate
	 */
	public String toString() {
		return toString(elements.size(), null);
	}
	
	public String toString(int limit, Comparator attributeComparator) {
		String result = "";
		for (int i = 0; i < limit; ++i) {
			result += ((Element)elements.get(i)).toString(XPATH_STYLE, attributeComparator);
		}
		return result;
	}
	/**
	 * boilerplate
	 */
	public boolean equals(Object other) {
		if (other == null || !getClass().equals(other.getClass())) return false;
		XPathParts that = (XPathParts)other;
		if (elements.size() != that.elements.size()) return false;
		for (int i = 0; i < elements.size(); ++i) {
			if (!elements.get(i).equals(that.elements.get(i))) return false;
		}
		return true;
	}
	/**
	 * boilerplate
	 */
	public int hashCode() {
		int result = elements.size();
		for (int i = 0; i < elements.size(); ++i) {
			result = result*37 + elements.get(i).hashCode();
		}
		return result;
	}
	
	// ========== Privates ==========
	
	private XPathParts parseError(String s, int i) {
		throw new IllegalArgumentException("Malformed xPath " + s + " at " + i);
	}

	public static final int XPATH_STYLE = 0, XML_OPEN = 1, XML_CLOSE = 2, XML_NO_VALUE = 3;
	
	private class Element {
		private String element;
		private Map attributes = new TreeMap(attributeComparator); // = new TreeMap(AttributeComparator);

		public Element(String element) {
			this.element = element;
		}
		
		public String toString() {
			throw new IllegalArgumentException("Don't use");
		}
		/**
		 * @param style from XPATH_STYLE
		 * @param attributeComparator TODO
		 * @return
		 */
		public String toString(int style, Comparator attributeComparator) {
			StringBuffer result = new StringBuffer();
			Set keys;
			switch (style) {
			case XPathParts.XPATH_STYLE:
				result.append('/').append(element);
				writeAttributes("[@", "\"]", attributeComparator, result);
				break;
			case XPathParts.XML_OPEN:
			case XPathParts.XML_NO_VALUE:
				result.append('<').append(element);
				if (false && element.equals("orientation")) {
					System.out.println();
				}
				writeAttributes(" ", "\"", attributeComparator, result);
				/*
				keys = attributes.keySet();
				if (attributeComparator != null) {
					Set temp = new TreeSet(attributeComparator);
					temp.addAll(keys);
					keys = temp;
				}
				for (Iterator it = keys.iterator(); it.hasNext();) {
					String attribute = (String) it.next();
					String value = (String) attributes.get(attribute);
					if (attribute.equals("type") && value.equals("standard")) continue; // HACK
					if (attribute.equals("version") && value.equals("1.2")) continue; // HACK
					result.append(' ').append(attribute).append("=\"")
							.append(value).append('\"');
				}
				*/
				if (style == XML_NO_VALUE) result.append('/');
				if (CLDRFile.HACK_ORDER && element.equals("ldml")) result.append(' ');
				result.append('>');
				break;
			case XML_CLOSE:
				result.append("</").append(element).append('>');
				break;
			}
			return result.toString();
		}
		/**
		 * @param prefix TODO
		 * @param postfix TODO
		 * @param attributeComparator
		 * @param result
		 */
		private void writeAttributes(String prefix, String postfix, Comparator attributeComparator, StringBuffer result) {
			Set keys = attributes.keySet();
			if (attributeComparator != null) {
				Set temp = new TreeSet(attributeComparator);
				temp.addAll(keys);
				keys = temp;
			}
			for (Iterator it = keys.iterator(); it.hasNext();) {
				String attribute = (String) it.next();
				String value = (String) attributes.get(attribute);
				if (attribute.equals("type") && value.equals("standard")) continue; // HACK
				if (attribute.equals("version") && value.equals("1.2")) continue; // HACK
				result.append(prefix).append(attribute).append("=\"")
						.append(value).append(postfix);
			}
		}

		public boolean equals(Object other) {
			if (other == null || !getClass().equals(other.getClass())) return false;
			Element that = (Element)other;
			return element.equals(that.element) && attributes.equals(that.attributes);
		}
		public int hashCode() {
			return element.hashCode()*37 + attributes.hashCode();
		}
	}

	/**
	 * @param string
	 * @return
	 */
	public int findElement(String elementName) {
		for (int i = 0; i < elements.size(); ++i) {
			Element e = (Element) elements.get(i);
			if (!e.element.equals(elementName)) continue;
			return i;
		}
		return -1;
	}
	/**
	 * @param path
	 * @return
	 */
	public XPathParts addRelative(String path) {
		while(path.startsWith("../")) {
			path = path.substring(3);
			trim();
		}
		if (!path.startsWith("/")) path = "/" + path;
		return setInternal(path);
	}
	/**
	 * @param i
	 */
	public void trim() {
		elements.remove(elements.size()-1);
	}
	/**
	 * @param parts
	 */
	public void set(XPathParts parts) {
		elements.clear();
		elements.addAll(parts.elements);
	}
	/**
	 * Replace up to i with parts
	 * @param i
	 * @param parts
	 */
	public void replace(int i, XPathParts parts) {
		List temp = elements;
		elements = new ArrayList();
		set(parts);
		for (;i < temp.size(); ++i) {
			elements.add(temp.get(i));
		}
	}
}