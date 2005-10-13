/*
 ******************************************************************************
 * Copyright (C) 2005, International Business Machines Corporation and        *
 * others. All Rights Reserved.                                               *
 ******************************************************************************
*/
package org.unicode.cldr.test;

import java.io.Serializable;
import java.text.ParsePosition;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.unicode.cldr.util.StandardCodes;

import com.ibm.icu.dev.test.util.ICUPropertyFactory;
import com.ibm.icu.dev.test.util.UnicodeMap;
import com.ibm.icu.dev.test.util.UnicodeProperty;
import com.ibm.icu.impl.UCharacterProperty;
import com.ibm.icu.impl.Utility;
import com.ibm.icu.text.BreakIterator;
import com.ibm.icu.text.Collator;
import com.ibm.icu.text.NumberFormat;
import com.ibm.icu.text.RuleBasedBreakIterator;
import com.ibm.icu.text.UTF16;
import com.ibm.icu.text.UnicodeSet;
import com.ibm.icu.text.UnicodeSetIterator;
import com.ibm.icu.util.ULocale;

/**
 * Quick class for testing proposed syntax for Segments.
 * TODO doesn't yet handle supplementaries. It looks like even Java 5 won't help, since it doesn't have syntax for them.
 * Will have to change [...X-Y] into ([...] | X1 [Y1-\uDFFF] | [X2-X3][\uDC00-\uDFFF] | X4[\uD800-Y2)
 * where the X1,Y1 is the first surrogate pair, and X4,Y2 is the last (2nd and 3rd ranges are only if X4 != X2).
 * @author davis
 */

public class TestSegments {
	/**
	 * If not null, maskes off the character properties so the UnicodeSets are easier to use when debugging.
	 */
	private static final UnicodeSet DEBUG_RETAIN = null; // new UnicodeSet("[\u0000-\u00FF\u0300-\u0310]"); // null;
	private static final UnicodeSet SUPPLEMENTARIES = new UnicodeSet(0x10000,0x10FFFF);
	/**
	 * Shows the rule that caused the result at each offset.
	 */
	private static final boolean DEBUG_SHOW_MATCHES = false;
	private static final boolean SHOW_VAR_CONTENTS = false;
	private static final boolean SHOW_RULE_LIST = false;
	private static final int monkeyLimit = 1000;
	private static final int REGEX_FLAGS = Pattern.COMMENTS | Pattern.MULTILINE | Pattern.DOTALL;

	private static final Matcher flagItems = Pattern.compile(
			"[$](BK|CR|LF|CM|NL|WJ|ZW|GL|SP|CB)").matcher("");

	/**
	 * Quick test of features for debugging
	 * @param args unused
	 */
	public static void main(String[] args) {
		if (args.length == 0) args = new String[] {"GraphemeCluster", "WordBreak", "LineBreak", "SentenceBreak"};
		List testChoice = Arrays.asList(args);
		
		// grab the rules, build a RuleList, and run against the test samples.
		
		for (int i = 0; i < tests.length; ++i) {
			String testName = tests[i][0];
			if (!testChoice.contains(testName)) continue;
			RuleListBuilder rb = new RuleListBuilder();
			System.out.println();
			System.out.println();
			System.out.println("Building: " + testName);
			int j = 1;
			for (; j < tests[i].length; ++j) {
				String line = tests[i][j];
				if (line.equals("test")) break;
				rb.addLine(line);
			}
			System.out.println(rb.toString(testName));
			System.out.println();
			System.out.println("Testing");
			RuleList rl = rb.make();
			if (false) debugRule(rb, rl);
			if (SHOW_RULE_LIST) System.out.println(rl.toString(true));
			for (++j; j < tests[i].length; ++j) {
				System.out.println();
				String line = tests[i][j];
				if (line.startsWith("compare")) {
					doCompare(rl, line);
					break;
				}
				String showingBreaks = ""; // don't bother with surrogates yet
				for (int k = 0; k <= line.length(); ++k) {
					if (rl.breaksAt(line,k)) {
						showingBreaks += '|';
					} 
					if (DEBUG_SHOW_MATCHES && rl.getBreakRule() >= 0) {
						showingBreaks += "�" + RuleList.nf.format(rl.getBreakRule()) + "�";
					}
					if (k < line.length()) showingBreaks += line.charAt(k);
				}
				System.out.println(showingBreaks);
			}
		}
		System.out.println("Done");
	}

	private static void debugRule(RuleListBuilder rb, RuleList rl) {
		Rule rule = rl.get(16.01);
		String oldAL = (String)rb.variables.get("$oldAL");
		UnicodeSet oldALSet = new UnicodeSet(oldAL);
		String testStr = "\uA80D/\u0745\u2026";
		for (int k = 0; k < testStr.length(); ++k) {
			boolean inside = oldALSet.contains(testStr.charAt(k));
			System.out.println(k + ": " + inside + Utility.escape(""+testStr.charAt(k)));
		}
		byte m = rule.matches(testStr, 3);
	}

	private static void doCompare(RuleList rl, String line) {
		RandomStringGenerator rsg;
		RuleBasedBreakIterator icuBreak;
		if (line.equals("compareGrapheme")) {
			rsg = new RandomStringGenerator("GraphemeClusterBreak");
			icuBreak = (RuleBasedBreakIterator) BreakIterator.getCharacterInstance();
		} else if (line.equals("compareWord")) {
			rsg = new RandomStringGenerator("WordBreak", false, true);
			icuBreak = (RuleBasedBreakIterator) BreakIterator.getWordInstance();
		} else if (line.equals("compareSentence")) {
			rsg = new RandomStringGenerator("SentenceBreak", false, true);
			icuBreak = (RuleBasedBreakIterator) BreakIterator.getSentenceInstance();
		} else if (line.equals("compareLine")) {
			rsg = new RandomStringGenerator("LineBreak", true, false);
			icuBreak = (RuleBasedBreakIterator) BreakIterator.getLineInstance();
		} else {
			throw new IllegalArgumentException("Bad tag: " + line);
		}
		System.out.println("Monkey Test: " + line + "\t icuBreaks = $\t ruleBreaks =@");
		boolean gotDot = false;
		for (int i = 0; i < monkeyLimit; ++i) {
			System.out.print('.');
			gotDot = true;
			String test = rsg.next(10);
			icuBreak.setText(test);
			int[] icuStatus = new int[20];
			int[] ruleStatus = new int[20];
			for (int j = 0; j <= test.length(); ++j) {
				boolean icuBreakResults = icuBreak.isBoundary(j);
				boolean ruleListResults = rl.breaksAt(test, j);
				if (icuBreakResults == ruleListResults) continue;
				if (gotDot) {
					System.out.println();
					gotDot = false;
				}
				System.out.println(i + ") Mismatch " + rl.getBreakRule() + ": "
					+ showResults(test, j, rsg, icuBreakResults)
				);
				rl.breaksAt(test, j); // for debugging
			}
		}		
	}
	
	private static String showStatus(int[] icuStatus, int icuStatusLen) {
		String result = "";
		for (int i = 0; i < icuStatusLen; ++i) {
			if (result.length() != 0) result += ", ";
			result += icuStatus[i];
		}
		return "[" + result + "]";
	}

	static boolean equalStatus(int[] status1, int len1, int[] status2, int len2) {
		if (len1 != len2) return false;
		for (int i = 0; i < len1; ++i) {
			if (status1[i] != status2[i]) return false;
		}
		return true;
	}
	
	private static String showResults(String test, int j, RandomStringGenerator rsg, boolean icuBreakResults) {
		StringBuffer results = new StringBuffer();
		int cp;
		for (int i = 0; i < test.length(); i += UTF16.getCharCount(cp)) {
			if (i == j) results.append(icuBreakResults ? "<\r\n$ >" : "<\r\n@ >");
			cp = UTF16.charAt(test, i);
			results.append("[" + rsg.getValue(cp) + ":" + Utility.escape(UTF16.valueOf(cp)) + "]");
		}
		if (test.length() == j) results.append(icuBreakResults ? "<\r\n$ >" : "<\r\n@ >");
		return results.toString();
	}
	
	private static class RandomStringGenerator {
		private Random random = new Random(0);
		private UnicodeSet[] sets;
		private UnicodeMap map;
		private UnicodeMap shortMap;
		private static UnicodeMap extendedMap;
		static {
			extendedMap = new UnicodeMap();
			UnicodeMap tempMap = ICUPropertyFactory.make().getProperty("GraphemeClusterBreak").getUnicodeMap();
			extendedMap.putAll(tempMap.getSet("CR"), "CR");
			extendedMap.putAll(tempMap.getSet("LF"), "LF");
			extendedMap.putAll(tempMap.getSet("Extend"), "GCExtend");
			extendedMap.putAll(tempMap.getSet("Control"), "GCControl");
		}

		RandomStringGenerator(String propertyName) {
			this(propertyName, false, false);
		}
		
		RandomStringGenerator(String propertyName, boolean useShortName, boolean addGCStuff) {
			this(ICUPropertyFactory.make().getProperty(propertyName).getUnicodeMap(),
					useShortName ? ICUPropertyFactory.make().getProperty(propertyName).getUnicodeMap(true) : null,
					addGCStuff);
		}
		RandomStringGenerator(UnicodeMap longNameMap, UnicodeMap shortNameMap, boolean addGCStuff) {
			map = !addGCStuff ? longNameMap 
					: longNameMap.composeWith(extendedMap, MyComposer);
			shortMap = (shortNameMap == null ? longNameMap 
					: !addGCStuff ? shortNameMap 
							: shortNameMap.composeWith(extendedMap, MyComposer));
			List values = new ArrayList(map.getAvailableValues());
			sets = new UnicodeSet[values.size()];
			for (int i = 0; i < sets.length; ++i) {
				sets[i] = map.getSet(values.get(i));
				sets[i].removeAll(SUPPLEMENTARIES);
				if (DEBUG_RETAIN != null) {
					int first = sets[i].charAt(0);
					sets[i].retainAll(DEBUG_RETAIN);
					if (sets[i].size() == 0) sets[i].add(first);
				}
			}
		}
		static UnicodeMap.Composer MyComposer = new UnicodeMap.Composer(){
			public Object compose(int codePoint, Object a, Object b) {
				if (a == null) return b;
				if (b == null) return a;
				return a + "_" + b;
			}		
		};
		String getValue(int cp) {
			return (String)shortMap.getValue(cp);
		}
		private String next(int len) {
			StringBuffer result = new StringBuffer();
			for (int i = 0; i < len; ++i) {
				UnicodeSet us = sets[random.nextInt(sets.length)];
				int cp = us.charAt(random.nextInt(us.size()));
				UTF16.append(result, cp);
			}
			return result.toString();
		}
	}
	
	/**
	 * For quickly checking regex syntax implications in Java
	 */
	private static boolean quickCheck() {
		String[][] rtests = {{
			".*" + new UnicodeSet("[\\p{Grapheme_Cluster_Break=LVT}]").complement().complement(), "\u001E\uC237\u1123\n\uC91B"
		},{
			"(?<=a)b", "ab"
		},{
			"[$]\\p{Alpha}\\p{Alnum}*", "$Letter"
		}};
		for (int i = 0; i < rtests.length; ++i) {
			Matcher m = Pattern.compile(rtests[i][0], REGEX_FLAGS).matcher("");
			m.reset(rtests[i][1]);
			boolean matches = m.matches();
			System.out.println(rtests[i][0] + ",\t" + rtests[i][1] + ",\t" + matches);
		}
		return false;
	}
	
	/**
	 * A rule that determines the status of an offset.
	 */
	static class Rule {
		/**
		 * Status of a breaking rule
		 */
		public static final byte NO_BREAK = -1, UNKNOWN_BREAK = 0, BREAK = 1;
		
		/**
		 * @param before pattern for the text after the offset. All variables must be resolved.
		 * @param result the break status to return when the rule is invoked
		 * @param after pattern for the text before the offset. All variables must be resolved.
		 * @param line 
		 */
		public Rule(String before, byte result, String after, String line) {
			breaks = result;
			before = ".*(" + before + ")";
			String parsing = null;
			try {
				matchPrevious = Pattern.compile(parsing = before, REGEX_FLAGS).matcher("");
				matchSucceeding = Pattern.compile(parsing = after, REGEX_FLAGS).matcher("");
			} catch (PatternSyntaxException e) {
				// Format: Unclosed character class near index 927
				int index = e.getIndex();
				throw (RuntimeException)new IllegalArgumentException("On <" + line + ">, Can't parse: " + parsing.substring(0,index)
						+ "<<<>>>" + parsing.substring(index))
				.initCause(e);
			} catch (RuntimeException e) {
				// Unclosed character class near index 927
				throw (RuntimeException)new IllegalArgumentException("On <" + line + ">, Can't parse: " + parsing)
				.initCause(e);
			}
			name = line; 
			resolved = Utility.escape(before) + (result == NO_BREAK ? " � " : " � ") + Utility.escape(after);
			// COMMENTS allows whitespace
		}
		
		//Matcher numberMatcher = Pattern.compile("[0-9]+").matcher("");
		
		/**
		 * Match the rule against text, at a position
		 * @param text
		 * @param position
		 * @return break status
		 */
		public byte matches(CharSequence text, int position) {
			if (!matchAfter(matchSucceeding, text, position)) return UNKNOWN_BREAK;
			if (!matchBefore(matchPrevious, text, position)) return UNKNOWN_BREAK;
			return breaks;
		}
		/**
		 * Debugging aid
		 */
		public String toString() {
			return toString(false);
		}
		public String toString(boolean showResolved) {
			String result = name;
			if (showResolved) result += ": " + resolved;
			return result;
		}
		
		//============== Internals ================
		// in Java 5, this can be more efficient, and use a single regex
		// of the form "(?<= before) after". MUST then have transparent bounds
		private Matcher matchPrevious;
		private Matcher matchSucceeding;
		private String name;
		private String resolved;
		private byte breaks;		
	}
	
	/**
	 * utility, since we are using Java 1.4
	 */
	static boolean matchAfter(Matcher matcher, CharSequence text, int position) {
		return matcher.reset(text.subSequence(position, text.length())).lookingAt();
	}

	/**
	 * utility, since we are using Java 1.4
	 * depends on the pattern having been built with .*
	 * not very efficient, works for testing and the best we can do.
	 */
	static boolean matchBefore(Matcher matcher, CharSequence text, int position) {
		return matcher.reset(text.subSequence(0, position)).matches();
	}
	
	/**
	 * Ordered list of rules, with variables resolved before building. Use RuleListBuilder to make.
	 */
	static class RuleList {
		/**
		 * Certain rules are generated, and have artificial numbers
		 */
		public static final double SOT = -3, EOT = -2, ANY = -1;
		/**
		 * Convenience for formatting doubles
		 */
		public static NumberFormat nf = NumberFormat.getInstance(ULocale.ENGLISH);
		static {
			nf.setMinimumFractionDigits(0);
		}
		
		/**
		 * Does the rule list give a break at this point? 
		 * Also sets the rule number that matches, for return by getBreakRule.
		 * @param text
		 * @param position
		 * @return
		 */
		public boolean breaksAt(CharSequence text, int position) {
			if (position == 0) {
				breakRule = SOT;
				return true;
			}
			if (position == text.length()) {
				breakRule = EOT;
				return true;
			}
			for (int i = 0; i < rules.size(); ++i) {
				Rule rule = (Rule)rules.get(i);
				if (false && rule.toString().indexOf("$H2") >= 0) {
					System.out.println("Debug");
				}
				byte result = rule.matches(text, position);
				if (result != Rule.UNKNOWN_BREAK) {
					breakRule = ((Double)orders.get(i)).doubleValue();
					return result == Rule.BREAK;
				}
			}
			breakRule = ANY;
			return true; // default
		}
		public int getRuleStatusVec(int[] ruleStatus) {
			ruleStatus[0] = 0;
			return 1;
		}
		/**
		 * Add a numbered rule.
		 * @param order
		 * @param rule
		 */
		public void add(double order, Rule rule) {
			orders.add(new Double(order));
			rules.add(rule);
		}
		public Rule get(double order) {
			int loc = orders.indexOf(new Double(order));
			if (loc < 0) return null;
			return (Rule) rules.get(loc);
		}
		/**
		 * Gets the rule number that matched at the point. Only valid after calling breaksAt
		 * @return
		 */
		public double getBreakRule() {
			return breakRule;
		}
		/**
		 * Debugging aid
		 */
		public String toString() {
			return toString(false);
		}
		public String toString(boolean showResolved) {
			String result = "";
			for (int i = 0; i < rules.size(); ++i) {
				if (i != 0) result += "\r\n";
				result += orders.get(i) + ") " + ((Rule)rules.get(i)).toString(showResolved);
			}
			return result;
		}
		
		//============== Internals ================
		
		private List rules = new ArrayList(1);
		private List orders = new ArrayList(1);
		private double breakRule;
	}
	
	/**
	 * Separate the builder for clarity
	 */
	
	/**
	 * Sort the longest strings first. Used for variable lists.
	 */
	static Comparator LONGEST_STRING_FIRST = new Comparator() {
		public int compare(Object arg0, Object arg1) {
			String s0 = arg0.toString();
			String s1 = arg1.toString();
			int len0 = s0.length();
			int len1 = s1.length();
			if (len0 < len1) return 1; // longest first
			if (len0 > len1) return -1;
			// lengths equal, use string order
			return s0.compareTo(s1);
		}
	};
	
	/**
	 * Used to build RuleLists. Can be used to do inheritance, since (a) adding a variable overrides any previous value, and
	 * any variables used in its value are resolved before adding, and (b) adding a rule sorts/overrides according to numeric value.
	 */
	static class RuleListBuilder {
		private List rawVariables = new ArrayList();
		private Map rawRules = new TreeMap();
		private List lastComments = new ArrayList();
		
		public String toString(String testName) {
			StringBuffer result = new StringBuffer();
			result.append("\t\t<segmentation type=\"" + testName + "\">").append("\r\n");
			result.append("\t\t\t<variables>").append("\r\n");
			for (int i = 0; i < rawVariables.size(); ++i) {
				result.append("\t\t\t\t").append(rawVariables.get(i)).append("\r\n");
			}
			result.append("\t\t\t</variables>").append("\r\n");
			result.append("\t\t\t<rules>").append("\r\n");
			for (Iterator it = rawRules.keySet().iterator(); it.hasNext();) {
				Object key = it.next();
				result.append("\t\t\t\t").append(rawRules.get(key)).append("\r\n");
			}
			result.append("\t\t\t</rules>").append("\r\n");
			for (int i = 0; i < lastComments.size(); ++i) {
				result.append("\t\t\t").append(lastComments.get(i)).append("\r\n");
			}
			result.append("\t\t</segmentation>").append("\r\n");
			return result.toString();
		}
		
		/**
		 * Add a line. If contains a =, is a variable definition.
		 * Otherwise, is of the form nn) rule, where nn is the number of the rule.
		 * For now, pretty lame parsing, because we can't easily determine whether =, etc is part of the regex or not.
		 * So any 'real' =, etc in a regex must be expressed with unicode escapes, \\u....
		 * @param line
		 * @return
		 */
		boolean addLine(String line) {
			// for debugging
			if (line.startsWith("show")) {
				line = line.substring(4).trim();
				System.out.println("# " + line + ": ");
				System.out.println("\t" + replaceVariables(line));
				return false;
			}
			// dumb parsing for now
			if (line.startsWith("#")) {
				lastComments.add("<!-- " + line.substring(1).trim() + " -->");
				return false;
			}
			int relationPosition = line.indexOf('=');
			if (relationPosition >= 0) {
				addVariable(line.substring(0,relationPosition).trim(), line.substring(relationPosition+1).trim());
				return false;
			}
			relationPosition = line.indexOf(')');
			Double order;
			try {
				order = new Double(Double.parseDouble(line.substring(0,relationPosition).trim()));
			} catch (Exception e) {
				throw new IllegalArgumentException("Rule must be of form '1)...': " + line);
			}
			line = line.substring(relationPosition + 1).trim();
			relationPosition = line.indexOf('�');
			byte breaks = Rule.BREAK;
			if (relationPosition < 0) {
				relationPosition = line.indexOf('�');
				if (relationPosition < 0) throw new IllegalArgumentException("Couldn't find =, �, or �");
				breaks = Rule.NO_BREAK;
			}
			addRule(order, line.substring(0,relationPosition).trim(), breaks, line.substring(relationPosition + 1).trim(), line);		
			return true;
		}
		
		private transient Matcher whiteSpace = Pattern.compile("\\s+", REGEX_FLAGS).matcher("");
		private transient Matcher identifierMatcher = Pattern.compile("[$]\\p{Alpha}\\p{Alnum}*", REGEX_FLAGS).matcher("");
		private transient Matcher brokenIdentifierMatcher = Pattern.compile("[^$\\p{Alpha}]\\p{Alnum}", REGEX_FLAGS).matcher("");
		
		/**
		 * Add a variable and value. Resolves the internal references in the value.
		 * @param name
		 * @param value
		 * @return
		 */
		RuleListBuilder addVariable(String name, String value) {
			if (lastComments.size() != 0) {
				rawVariables.addAll(lastComments);
				lastComments.clear();
			}
			rawVariables.add("<variable id=\"" + name + "\">" + value + "</variable>");
			if (!identifierMatcher.reset(name).matches()) {
				throw new IllegalArgumentException("Variable name must be $id: '" + name + "'");
			}
			value = replaceVariables(value);
			if (SHOW_VAR_CONTENTS) System.out.println(name + "=" + value);
			// verify that the value is a valid REGEX
			Pattern.compile(value, REGEX_FLAGS).matcher("");
//			if (false && name.equals("$AL")) {
//				findRegexProblem(value);
//			}
			variables.put(name, value);
			return this;
		}
		private void findRegexProblem(String value) {
			UnicodeSet us = new UnicodeSet(value);
			// progressively get larger and larger set
			String parsing = null;
			try {
				for (int i = 0; i < us.size(); ++i) {
					UnicodeSet temp = new UnicodeSet(us).retain(0, us.charAt(i));
					parsing = getInsertablePattern(temp);
					Pattern.compile(parsing, REGEX_FLAGS).matcher("");
				}
			} catch (PatternSyntaxException e) {
				// Format: Unclosed character class near index 927
				int index = e.getIndex();
				throw (RuntimeException)new IllegalArgumentException("Can't parse: " + parsing.substring(0,index)
						+ "<<<>>>" + parsing.substring(index))
				.initCause(e);
			}
		}
		/**
		 * Add a numbered rule, already broken into the parts before and after.
		 * @param order
		 * @param before
		 * @param result
		 * @param after
		 * @param line 
		 * @return
		 */
		RuleListBuilder addRule(Double order, String before, byte result, String after, String line) {
			if (brokenIdentifierMatcher.reset(line).find()) {
				int pos = brokenIdentifierMatcher.start();
				throw new IllegalArgumentException("Illegal identifier at:" + line.substring(0,pos) + "<<>>" + line.substring(pos));
			}
			line = whiteSpace.reset(line).replaceAll(" ");
			// insert comments before current line, in order.
			if (lastComments.size() != 0) {
				double increment = 0.0001;
				double temp = order.doubleValue() - increment*lastComments.size();
				for (int i = 0; i < lastComments.size(); ++i) {
					rawRules.put(new Double(temp), lastComments.get(i));
					temp += increment;
				}
				lastComments.clear();
			}
			rawRules.put(order, "<rule id=\"" + RuleList.nf.format(order) + "\""
					+ (flagItems.reset(line).find() ? " normative=\"true\"" : "")
					+ "> " + line + " </rule>");
			rules.put(order, new Rule(replaceVariables(before), result, replaceVariables(after), line));
			return this;	
		}
		
		/**
		 * Return a RuleList from what we have currently.
		 * @return
		 */
		RuleList make() {
			RuleList result = new RuleList();
			for (Iterator it = rules.keySet().iterator(); it.hasNext();) {
				Double key = (Double)it.next();
				result.add(key.doubleValue(), (Rule)rules.get(key));
			}
			return result;
		}
		
		// ============== internals ===================
		private Map variables = new TreeMap(LONGEST_STRING_FIRST); // sorted by length, longest first, to make substitution easy
		private Map rules = new TreeMap();
		
		/**
		 * A workhorse. Replaces all variable references: anything of the form $id.
		 * Flags an error if anything of that form is not a variable.
		 * Since we are using Java regex, the properties support
		 * are extremely week. So replace them by literals. 
		 * @param input
		 * @return
		 */
		private String replaceVariables(String input) {
			// to do, optimize
			String result = input;
			int position = -1;
			main:
			while (true) {
				position = result.indexOf('$', position);
				if (position < 0) break;
				for (Iterator it = variables.keySet().iterator(); it.hasNext();) {
					String name = (String)it.next();
					if (result.regionMatches(position, name, 0, name.length())) {
						String value = (String)variables.get(name);
						result = result.substring(0,position) + value + result.substring(position + name.length());
						position += value.length(); // don't allow overlap
						continue main;
					}
				}
				if (identifierMatcher.reset(result.substring(position)).lookingAt()) {
					throw new IllegalArgumentException("Illegal variable at: '" + result.substring(position) + "'");
				}
			}
			// replace properties
			// TODO really dumb parse for now, fix later
			for (int i = 0; i < result.length(); ++i) {
				if (UnicodeSet.resemblesPattern(result, i)) {
					parsePosition.setIndex(i);
					UnicodeSet temp = new UnicodeSet(result, parsePosition, null);
					String insert = getInsertablePattern(temp);
					result = result.substring(0,i) + insert + result.substring(parsePosition.getIndex());
					i += insert.length() - 1; // skip over inserted stuff; -1 since the loop will add
				}
			}
			return result;
		}
		
		transient ParsePosition parsePosition = new ParsePosition(0);
		
		/**
		 * Transform a unicode pattern into stuff we can use in Java.
		 * @param temp
		 * @return
		 */
		private String getInsertablePattern(UnicodeSet temp) {
			temp.complement().complement();
			temp.remove(0x10000,0x10FFFF); // TODO Fix with Hack
			if (DEBUG_RETAIN != null) {
				temp.retainAll(DEBUG_RETAIN);
				if (temp.size() == 0) temp.add(0xFFFF); // just so not empty
			}
			
			String result = toPattern(temp, JavaRegexShower);
			// double check the pattern!!
			UnicodeSet reversal = new UnicodeSet(result);
			if (!reversal.equals(temp)) throw new IllegalArgumentException("Failure on UnicodeSet print");
			return result;
		}

		static UnicodeSet JavaRegex_uxxx = new UnicodeSet("[[:White_Space:][:defaultignorablecodepoint:]#]"); // hack to fix # in Java
		static UnicodeSet JavaRegex_slash = new UnicodeSet("[[:Pattern_White_Space:]" +
				"\\[\\]\\-\\^\\&\\\\\\{\\}\\$\\:]");		
		static CodePointShower JavaRegexShower = new CodePointShower() {
			public String show(int codePoint) {
				if (JavaRegex_uxxx.contains(codePoint)) return "\\u" + Utility.hex(codePoint);
				if (JavaRegex_slash.contains(codePoint)) return "\\" + UTF16.valueOf(codePoint);
				return UTF16.valueOf(codePoint);
			}
		};
		
		private static String toPattern(UnicodeSet temp, CodePointShower shower) {
			StringBuffer result = new StringBuffer();
			result.append('[');
			for (UnicodeSetIterator it = new UnicodeSetIterator(temp); it.nextRange();) {
				// three cases: single, adjacent, range
				int first = it.codepoint;
				result.append(shower.show(first++));
				if (first > it.codepointEnd) continue;
				if (first != it.codepointEnd) result.append('-');
				result.append(shower.show(it.codepointEnd));
			}
			result.append(']');
			return result.toString();
		}
	}
	static public interface CodePointShower {
		String show(int codePoint);
	}
	
	static final String[][] tests = {{
		"QuickCheck",
		"1) � b",
		"2) � .",
		"0.5) a �",
		"test",
		"abcbdb"
	},{
		"QuickCheck2",
		"$Letter=\\p{Alphabetic}",
		"$Digit=\\p{Digit}",
		"1) $Digit � $Digit",
		"2) $Letter � $Letter",
		"test",
		"The quick 100 brown foxes."
	},{
		"GraphemeCluster",
		"$CR=\\p{Grapheme_Cluster_Break=CR}",
		"$LF=\\p{Grapheme_Cluster_Break=LF}",
		"$Control=\\p{Grapheme_Cluster_Break=Control}",
		"$Extend=\\p{Grapheme_Cluster_Break=Extend}",
		"$L=\\p{Grapheme_Cluster_Break=L}",
		"$V=\\p{Grapheme_Cluster_Break=V}",
		"$T=\\p{Grapheme_Cluster_Break=T}",
		"$LV=\\p{Grapheme_Cluster_Break=LV}",
		"$LVT=\\p{Grapheme_Cluster_Break=LVT}",
		"3) $CR  	�  	$LF",
		"4) ( $Control | $CR | $LF ) 	�",
		"5) � 	( $Control | $CR | $LF )",
		"6) $L 	� 	( $L | $V | $LV | $LVT )",
		"7) ( $LV | $V ) 	� 	( $V | $T )",
		"8) ( $LVT | $T) 	� 	$T",
		"9) � 	$Extend",
		"test",
		"The qui\u0300ck 100 brown foxes.",
		"compareGrapheme"
	},{
		"WordBreak",
		"# GC stuff",
		"$GCCR=\\p{Grapheme_Cluster_Break=CR}",
		"$GCLF=\\p{Grapheme_Cluster_Break=LF}",
		"$GCControl=\\p{Grapheme_Cluster_Break=Control}",
		"$GCExtend=\\p{Grapheme_Cluster_Break=Extend}",
		"# Now normal variables",
		"$Format=\\p{Word_Break=Format}",
		"$Katakana=\\p{Word_Break=Katakana}",
		"$ALetter=\\p{Word_Break=ALetter}",
		"$MidLetter=\\p{Word_Break=MidLetter}",
		"$MidNum=\\p{Word_Break=MidNum}",
		"$Numeric=\\p{Word_Break=Numeric}",
		"$ExtendNumLet=\\p{Word_Break=ExtendNumLet}",
		
		"# Fixes for GC, Format",
		"# Subtract Format from Control, since we don't want to break before/after",
		//"$GCControl=[$GCControl-$Format]", 
		"# Add format and extend to everything",
		//"$X=[$Format $GCExtend]*",
		"$X= $GCExtend* $Format*",
		"$Katakana=($Katakana $X)",
		"$ALetter=($ALetter $X)",
		"$MidLetter=($MidLetter $X)",
		"$MidNum=($MidNum $X)",
		"$Numeric=($Numeric $X)",
		"$ExtendNumLet=($ExtendNumLet $X)",
		"# Keep GC together; don't need GC rules 6-8, since they are covered by the other rules",
		"3.3) $GCCR  	�  	$GCLF",
		//"3.4) ( $Control | $CR | $LF ) 	�",
		//"3.5) � 	( $Control | $CR | $LF )",
		//"3.9) � 	$Extend",
		"3.91) [^$GCControl | $GCCR | $GCLF] � 	$GCExtend",
		"# Don't break within X + Format. Otherwise Format is added because of variables below",
		"4) � 	$Format", 
		"# Vanilla rules",
		"5)$ALetter  	�  	$ALetter",
		"6)$ALetter 	� 	$MidLetter $ALetter",
		"7)$ALetter $MidLetter 	� 	$ALetter",
		"8)$Numeric 	� 	$Numeric",
		"9)$ALetter 	� 	$Numeric",
		"10)$Numeric 	� 	$ALetter",
		"11)$Numeric $MidNum 	� 	$Numeric",
		"12)$Numeric 	� 	$MidNum $Numeric",
		"13)$Katakana 	� 	$Katakana",
		"13.1)($ALetter | $Numeric | $Katakana | $ExtendNumLet) 	� 	$ExtendNumLet",
		"13.2)$ExtendNumLet 	� 	($ALetter | $Numeric | $Katakana)",
		"#15.1,100)$ALetter �",
		"#15.1,100)$Numeric �",
		"#15.1,100)$Katakana �",
		"#15.1,100)$Ideographic �",


		"test",
		"T\u0300he qui\u0300ck 100.1 brown\r\n\u0300foxes.",
		"compareWord"
	},{
		"SentenceBreak",
		"# GC stuff",
		"$GCCR=\\p{Grapheme_Cluster_Break=CR}",
		"$GCLF=\\p{Grapheme_Cluster_Break=LF}",
		"$GCControl=\\p{Grapheme_Cluster_Break=Control}",
		"$GCExtend=\\p{Grapheme_Cluster_Break=Extend}",
		"# Normal variables",
		"$Format=\\p{Sentence_Break=Format}",
		"$Sep=\\p{Sentence_Break=Sep}",
		"$Sp=\\p{Sentence_Break=Sp}",
		"$Lower=\\p{Sentence_Break=Lower}",
		"$Upper=\\p{Sentence_Break=Upper}",
		"$OLetter=\\p{Sentence_Break=OLetter}",
		"$Numeric=\\p{Sentence_Break=Numeric}",
		"$ATerm=\\p{Sentence_Break=ATerm}",
		"$STerm=\\p{Sentence_Break=STerm}",
		"$Close=\\p{Sentence_Break=Close}",
		"$Any=.",
		//"# subtract Format from Control, since we don't want to break before/after",
		//"$Control=[$Control-$Format]", 
		"# Expresses the negation in rule 8; can't do this with normal regex, but works with UnicodeSet, which is all we need.",
		"$NotStuff=[^$OLetter $Upper $Lower $Sep]",

		"# Now add format and extend to everything but Sep",
		"$X=[$Format $GCExtend]*",
		"$Sp=($Sp $X)",
		"$Lower=($Lower $X)",
		"$Upper=($Upper $X)",
		"$OLetter=($OLetter $X)",
		"$Numeric=($Numeric $X)",
		"$ATerm=($ATerm $X)",
		"$STerm=($STerm $X)",
		"$Close=($Close $X)",

		"# keep GC together; don't need 6-8, since they are covered by the other rules",
		"3.3) $GCCR  	�  	$GCLF",
		"# Sep needs to be inserted here, to keep CRLF together. Needs fix in TR29",
		"3.35) $Sep  	�",
		//"3.4) ( $Control | $CR | $LF ) 	�",
		//"3.5) � 	( $Control | $CR | $LF )",
		"3.91) [^$GCControl | $GCCR | $GCLF] � 	$GCExtend",
		"4) � 	$Format", 
		"# 4 means don't break within X + Format. Otherwise Format is added because of variables below",
		"#Do not break after ambiguous terminators like period, if immediately followed by a number or lowercase letter, is between uppercase letters, or if the first following letter (optionally after certain punctuation) is lowercase. For example, a period may be an abbreviation or numeric period, and not mark the end of a sentence.",
		"6) $ATerm 	� 	$Numeric",
		"7) $Upper $ATerm 	� 	$Upper",
		"8) $ATerm $Close* $Sp* 	� 	$NotStuff* $Lower",
		"#Break after sentence terminators, but include closing punctuation, trailing spaces, and (optionally) a paragraph separator.",
		"9) ( $STerm | $ATerm ) $Close* 	� 	( $Close | $Sp | $Sep )",
		"# Note the fix to $Sp*, $Sep?",
		"10) ( $STerm | $ATerm ) $Close* $Sp* 	� 	( $Sp | $Sep )",
		"11) ( $STerm | $ATerm ) $Close* $Sp* $Sep? �",
		"#Otherwise, do not break",
		"12) � 	$Any",
		"test",
		"T\u0300he qui\u0300ck 100.1 brown\r\n\u0300foxes. And the beginning. \"Hi?\" Nope! or not.",
		"compareSentence"
	},{
		"LineBreak",
		"# Variables",
		"$AI=\\p{Line_Break=Ambiguous}",
		"$AL=\\p{Line_Break=Alphabetic}",
		"$B2=\\p{Line_Break=Break_Both}",
		"$BA=\\p{Line_Break=Break_After}",
		"$BB=\\p{Line_Break=Break_Before}",
		"$BK=\\p{Line_Break=Mandatory_Break}",
		"$CB=\\p{Line_Break=Contingent_Break}",
		"$CL=\\p{Line_Break=Close_Punctuation}",
		"$CM=\\p{Line_Break=Combining_Mark}",
		"$CR=\\p{Line_Break=Carriage_Return}",
		"$EX=\\p{Line_Break=Exclamation}",
		"$GL=\\p{Line_Break=Glue}",
		"$H2=\\p{Line_Break=H2}",
		"$H3=\\p{Line_Break=H3}",
		"$HY=\\p{Line_Break=Hyphen}",
		"$ID=\\p{Line_Break=Ideographic}",
		"$IN=\\p{Line_Break=Inseparable}",
		"$IS=\\p{Line_Break=Infix_Numeric}",
		"$JL=\\p{Line_Break=JL}",
		"$JT=\\p{Line_Break=JT}",
		"$JV=\\p{Line_Break=JV}",
		"$LF=\\p{Line_Break=Line_Feed}",
		"$NL=\\p{Line_Break=Next_Line}",
		"$NS=\\p{Line_Break=Nonstarter}",
		"$NU=\\p{Line_Break=Numeric}",
		"$OP=\\p{Line_Break=Open_Punctuation}",
		"$PO=\\p{Line_Break=Postfix_Numeric}",
		"$PR=\\p{Line_Break=Prefix_Numeric}",
		"$QU=\\p{Line_Break=Quotation}",
		"$SA=\\p{Line_Break=Complex_Context}",
		"$SG=\\p{Line_Break=Surrogate}",
		"$SP=\\p{Line_Break=Space}",
		"$SY=\\p{Line_Break=Break_Symbols}",
		"$WJ=\\p{Line_Break=Word_Joiner}",
		"$XX=\\p{Line_Break=Unknown}",
		"$ZW=\\p{Line_Break=ZWSpace}",
		//"$NotNL=[^$NL]",
		"# LB 1  Assign a line breaking class to each code point of the input. " +
		"Resolve AI, CB, SA, SG, and XX into other line breaking classes depending on criteria outside the scope of this algorithm.",
		"# NOTE: CB is ok to fall through, but must handle others here.",
		//"show $AL",
		"$AL=[$AI $AL $XX $SA $SG]",
		//"show $AL",
		//"$oldAL=$AL", // for debugging
		"# Fixes for Rule 7",
		"# Treat X CM* as if it were X.",
		"# Where X is any line break class except SP, BK, CR, LF, NL or ZW.",
		"$X=$CM*",
		"$AI=($AI $X)",
		"$AL=($AL $X)",
		"$B2=($B2 $X)",
		"$BA=($BA $X)",
		"$BB=($BB $X)",
		"$CB=($CB $X)",
		"$CL=($CL $X)",
		"$CM=($CM $X)",
		"$CM=($CM $X)",
		"$GL=($GL $X)",
		"$H2=($H2 $X)",
		"$H3=($H3 $X)",
		"$HY=($HY $X)",
		"$ID=($ID $X)",
		"$IN=($IN $X)",
		"$IS=($IS $X)",
		"$JL=($JL $X)",
		"$JT=($JT $X)",
		"$JV=($JV $X)",
		"$NS=($NS $X)",
		"$NU=($NU $X)",
		"$OP=($OP $X)",
		"$PO=($PO $X)",
		"$PR=($PR $X)",
		"$QU=($QU $X)",
		"$SA=($SA $X)",
		"$SG=($SG $X)",
		"$SY=($SY $X)",
		"$WJ=($WJ $X)",
		"$XX=($XX $X)",
		"# LB 7c  Treat any remaining combining mark as AL.",
		"$AL=($AL | ^ $CM | (?<=[$SP $BK $CR $LF $NL $ZW]) $CM)",

		"# LB 3a  Always break after hard line breaks (but never between CR and LF).",
		"3.1) $BK �",
		"# LB 3b  Treat CR followed by LF, as well as CR, LF and NL as hard line breaks.",
		"3.21) $CR � $LF",
		"3.22) $CR �",
		"3.23) $LF �",
		"3.24) $NL �",
		"# LB 3c  Do not break before hard line breaks.",
		"3.3) � ( $BK | $CR | $LF | $NL )",
		"# LB 4  Do not break before spaces or zero-width space.",
		"4.01) � $SP",
		"4.02) � $ZW",
		"# LB 5  Break after zero-width space.",
		"5) $ZW �",
		"# LB 7b  Do not break a combining character sequence; treat it as if it has the LB class of the base character" +
		" in all of the following rules. (Where X is any line break class except SP, BK, CR, LF, NL or ZW.)",
		"7.2) � $CM",
		"#WARNING: this is done by modifying the variable values for all but SP.... That is, $AL is really ($AI $CM*)!",
		"# LB 8  Do not break before �]� or �!� or �;� or �/�, even after spaces.",
		"# Using customization 7.",
		//"8.01) $NotNL � $CL",
		//"8.02) � $EX",
		//"8.03) $NotNL � $IS",
		//"8.04) $NotNL � $SY",
		"8.01) � $CL",
		"8.02) � $EX",
		"8.03) � $IS",
		"8.04) � $SY",
		"#LB 9  Do not break after �[�, even after spaces.",
		"9) $OP $SP* �",
		"# LB 10  Do not break within �\"[�, even with intervening spaces.",
		"10) $QU $SP* � $OP",
		"# LB 11  Do not break within �]h�, even with intervening spaces.",
		"11) $CL $SP* � $NS",
		"# LB 11a  Do not break within ����, even with intervening spaces.",
		"11.1) $B2 $SP* � $B2",
		"# LB 11b  Do not break before or after WORD JOINER and related characters.",
		"11.21) � $WJ",
		"11.22) $WJ �",
		"# LB 12  Break after spaces.",
		"12) $SP �",
		"# LB 13  Do not break before or after NBSP and related characters.",
		"13.01) � $GL",
		"13.02) $GL �",
		"# LB 14  Do not break before or after �\"�.",
		"14.01)  � $QU",
		"14.02) $QU �",
		"# LB 14a  Break before and after unresolved CB.",
		"14.12)  � $CB",
		"14.13) $CB �",
		"# LB 15  Do not break before hyphen-minus, other hyphens, fixed-width spaces, small kana and other non-starters, or after acute accents.",
		"15.01) � $BA",
		"15.02) � $HY",
		"15.03) � $NS",
		"15.04) $BB �",
		"# LB 16  Do not break between two ellipses, or between letters or numbers and ellipsis.",
		//"show $AL",
		"16.01) $AL � $IN",
		"16.02) $ID � $IN",
		"16.03) $IN � $IN",
		"16.04) $NU � $IN",
		"# LB 17  Do not break within �a9�, �3a�, or �H%�.",
		"17.01) $ID � $PO",
		"17.02) $AL � $NU",
		"17.03) $NU � $AL",
		"# Using customization 7",
		"# LB 18  Do not break between the following pairs of classes.",
		"# LB 18-alternative: $PR? ( $OP | $HY )? $NU ($NU | $SY | $IS)* $CL? $PO?",
		"# Insert � every place it could go. However, make sure that at least one thing is concrete, otherwise would cause $NU to not break before or after ",
		"18.111) $PR � ( $OP | $HY )? $NU",
		"18.112) ( $OP | $HY ) � $NU",
		"18.113) $NU � ($NU | $SY | $IS)",
		"18.114) $NU ($NU | $SY | $IS)* � ($NU | $SY | $IS)",
		"18.115) $NU ($NU | $SY | $IS)* � $CL",
		"18.115) $NU ($NU | $SY | $IS)* $CL? � $PO",
		"#18.11) $CL � $PO",
		"#18.12) $HY � $NU",
		"#18.13) $IS � $NU",
		"#18.13) $NU � $NU",
		"#18.14) $NU � $PO",
		"18.15) $PR � $AL",
		"#18.16) $PR � $HY",
		"18.17) $PR � $ID",
		"#18.18) $PR � $NU",
		"#18.19) $PR � $OP",
		"#18.195) $SY � $NU",
		"#LB 18b Do not break a Korean syllable.",
		"18.21) $JL  � $JL | $JV | $H2 | $H3",
		"18.22) $JV | $H2 � $JV | $JT",
		"18.23) $JT | $H3 � $JT",
		"# LB 18c Treat a Korean Syllable Block the same as ID.",
		"18.31) $JL | $JV | $JT | $H2 | $H3 � $IN",
		"18.32) $JL | $JV | $JT | $H2 | $H3  � $PO",
		"18.33) $PR � $JL | $JV | $JT | $H2 | $H3",
		"# LB 19  Do not break between alphabetics (\"at\").",
		"19) $AL � $AL",
		"# LB 19b  Do not break between numeric punctuation and alphabetics (\"e.g.\").",
		"19.1) $IS � $AL",
		"test",
		"\uCD40\u1185",
		"http://www.cs.tut.fi/%7Ejkorpela/html/nobr.html?abcd=high&hijk=low#anchor",
		"T\u0300he qui\u0300ck 100.1 brown\r\n\u0300foxes. And the beginning. \"Hi?\" Nope! or not.",
		"compareLine"
	}};
}