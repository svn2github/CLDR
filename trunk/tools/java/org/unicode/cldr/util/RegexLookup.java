package org.unicode.cldr.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.unicode.cldr.util.CldrUtility.VariableReplacer;
import org.unicode.cldr.util.RegexFileParser.RegexLineParser;
import org.unicode.cldr.util.RegexFileParser.VariableProcessor;
import org.unicode.cldr.util.RegexLookup.Finder;

import com.ibm.icu.text.Transform;
import com.ibm.icu.util.Output;

/**
 * Lookup items according to a set of regex patterns. Returns the value according to the first pattern that matches. Not
 * thread-safe.
 * 
 * @param <T>
 */
public class RegexLookup<T> implements Iterable<Map.Entry<Finder, T>> {
    private VariableReplacer variables = new VariableReplacer();
    private static final boolean DEBUG = true;
    public final RegexTree entries = new RegexTree();
    private Transform<String, ? extends Finder> patternTransform = RegexFinderTransform;
    private Transform<String, ? extends T> valueTransform;
    private Merger<T> valueMerger;
    private final boolean allowNull = false;

    public abstract static class Finder {
        abstract public String[] getInfo();

        abstract public boolean find(String item, Object context);

        public int getFailPoint(String source) {
            return -1;
        }
        // must also define toString
    }

    public static class RegexFinder extends Finder {
        protected final Matcher matcher;

        public RegexFinder(String pattern) {
            matcher = Pattern.compile(pattern, Pattern.COMMENTS).matcher("");
        }

        public boolean find(String item, Object context) {
            try {
                return matcher.reset(item).find();
            } catch (StringIndexOutOfBoundsException e) {
                // We don't know what causes this error (cldrbug 5051) so
                // make the exception message more detailed.
                throw new IllegalArgumentException("Matching error caused by pattern: ["
                    + matcher.toString() + "] on text: [" + item + "]", e);
            }
        }

        @Override
        public String[] getInfo() {
            int limit = matcher.groupCount() + 1;
            String[] value = new String[limit];
            for (int i = 0; i < limit; ++i) {
                value[i] = matcher.group(i);
            }
            return value;
        }

        public String toString() {
            return matcher.pattern().pattern();
        }

        @Override
        public boolean equals(Object obj) {
            if(obj == null) {
                return false;
            } else {
                return toString().equals(obj.toString());
            }
        }

        @Override
        public int hashCode() {
            return toString().hashCode();
        }

        @Override
        public int getFailPoint(String source) {
            return RegexUtilities.findMismatch(matcher, source);
        }
    }
    
    public class RegexTree{
        private RTNode root;
        private int _size;
        private RTNodeRankComparator rankComparator = new RTNodeRankComparator();
        
        public RegexTree(){
            root = new RTNode("", null);
            _size = 0;
        }
        
        public int size(){
            return _size;
        }
        
        public void put(Finder pattern, T value){
            root.put(new RTNode(pattern, value, _size));
            _size++;
        }
        
        public T get(Finder finder){
            return root.get(finder);
        }
        
        public List<T> getAll(String pattern, Object context, List<Finder> matcherList) {
            List<RTNode> list = new ArrayList<RTNode>();
            List<T> retList = new ArrayList<T>();
            
            root.addToList(pattern, context, list);
            Collections.sort(list, rankComparator);
            
            for( RTNode n: list ) {
                retList.add(n._val);
                if(matcherList != null){
                    matcherList.add(n._finder);
                }
            }
            
            return retList;
        }
        
        public T get(String pattern, Object context, Output<String[]> arguments, Output<Finder> matcherFound) {
            List<Finder> matcherList = new ArrayList<Finder>();
            List<T> matches = getAll(pattern, context, matcherList);  //need to get whole list because we want value that was entered first
            if (arguments != null) {
                arguments.value = (matcherList.size() > 0) ? matcherList.get(0).getInfo() : null;
            }
            if(matcherFound != null) {
                matcherFound.value = (matcherList.size() > 0) ? matcherList.get(0) : null;
            }
            return (matches.size() > 0) ? matches.get(0) : null;
        }
        
        public Set<Entry<Finder,T>> entrySet(){
            LinkedHashMap<Finder, T> ret = new LinkedHashMap<Finder, T>();
            TreeSet<RTNode> s = new TreeSet<RTNode>(rankComparator);
            root.addToEntrySet(s);
            
            for( RTNode n : s ){
                ret.put(n._finder, n._val);
            }
            
            return ret.entrySet();
        }
        
        public class RTNode{
            Finder _finder;
            T _val;
            List<RTNode> _children = new ArrayList<RTNode>();
            int _rank;      //rank -1 means the node was not inserted, but only used for structural purposes
            
            //constructor for regular nodes with a Finder
            public RTNode(Finder finder, T val, int rank){
                _finder = finder;
                _val = val;
                _rank = rank;
            }
            
            //constructors for nodes without a Finder
            public RTNode(String key, T val){
                _finder = new RegexFinder(key);
                _val = val;
                _rank = -1;
            }
            
            public void put(RTNode node){
                if(_children.size() == 0){
                    _children.add(node);
                } else {
                    String maxSimilarChars = "";  //most similar characters up to the last similar directory
                    int insertIndex = 0;
                    for(int i = 0; i < _children.size(); i++){
                        RTNode child = _children.get(i);
                        String childFinderPattern = child._finder.toString();
                        if( childFinderPattern.length() > 0 && childFinderPattern.charAt(childFinderPattern.length()-1) == '$'){
                            childFinderPattern = childFinderPattern.substring(0, childFinderPattern.length() - 1);  //takes into account the added "$"
                        }
                        else if( child._rank == -1 ){
                            childFinderPattern = childFinderPattern.substring(0, childFinderPattern.length() - 2);  //takes into account the added ".*"
                        }
                        
                        //check if child has the same Finder as node to insert, then replace it
                        if(node._finder.equals(child._finder)){
                            child._finder = node._finder;
                            child._val = node._val;
                            if(child._rank == -1) {
                                child._rank = node._rank;
                            } else {
                                _size--;
                            }
                            return;
                        }
                        
                        //check if child is the parent of node
                        if(child._rank == -1 && node._finder.toString().startsWith(childFinderPattern)){
                            child.put(node);
                            return;
                        }
                        
                        //if not parent then check if candidate for most similar RTNode
                        String gcp = greatestCommonPrefix(childFinderPattern, node._finder.toString());
                        gcp = removeExtraChars(gcp);
                        if(gcp.length() > maxSimilarChars.length()){
                            maxSimilarChars = gcp;
                            insertIndex = i;
                        }
                    }
                    
                    String finderPattern = this._finder.toString();
                    if( finderPattern.length() > 0 && finderPattern.charAt(finderPattern.length()-1) == '$'){
                        finderPattern = finderPattern.substring(0, finderPattern.length() - 1);  //takes into account the added "$"
                    }
                    else if( !(finderPattern.equals("")) && this._rank == -1 ){
                        finderPattern = finderPattern.substring(0, finderPattern.length() - 2);  //takes into account the added ".*"
                    }
                    
                    if((maxSimilarChars).equals(finderPattern)){  //add under this if no similar children
                        _children.add(node);
                    } else {
                        //create the common parent of the chosen candidate above and node, then add to the insert index
                        RTNode newParent = new RTNode(maxSimilarChars + ".*", null);
                        newParent._children.add(this._children.get(insertIndex));
                        newParent._children.add(node);
                        this._children.remove(insertIndex);
                        this._children.add(insertIndex, newParent);
                    }
                }
            }
            
            //takes a string in a directory regex form and removes all characters up to the last full directory
            private String removeExtraChars( String input ){
                String ret = input.substring(0, Math.max(0, input.lastIndexOf('/')) );
                while( (ret.lastIndexOf('(') != -1 && ret.lastIndexOf('(') > ret.lastIndexOf(')')) ||
                       (ret.lastIndexOf('[') != -1 && ret.lastIndexOf('[') > ret.lastIndexOf(']')) ||
                       (ret.lastIndexOf('{') != -1 && ret.lastIndexOf('{') > ret.lastIndexOf('}'))) {
                    ret = ret.substring(0, Math.max(0, ret.lastIndexOf('/')) );
                }
                return ret;
            }
            
            //traverse tree to get value
            public T get(Finder finder){
                T ret = null;  //return value
                
                if(_children.size() == 0){
                    return null;
                } else {
                    for(RTNode child : _children){
                        
                        //check if child is the node
                        if(child._rank != -1 && finder.equals(child._finder)){
                            return child._val;
                        }
                        
                        String childFinderPattern = child._finder.toString();
                        
                        if( childFinderPattern.length() > 0 && childFinderPattern.charAt(childFinderPattern.length()-1) == '$'){
                            childFinderPattern = childFinderPattern.substring(0, childFinderPattern.length() - 1);  //takes into account the added "$"
                        }
                        else if( child._rank == -1 ){
                            childFinderPattern = childFinderPattern.substring(0, childFinderPattern.length() - 2);  //takes into account the added ".*"
                        }
                        
                        //check if child is the parent of node
                        if(finder.toString().startsWith(childFinderPattern)){
                            ret = child.get(finder);
                            if(ret != null){
                                break;
                            }
                        }
                    }
                    
                    return ret;
                }
            }
            
            //traverse tree to get an entry set
            public void addToEntrySet(TreeSet<RTNode> s){
                if(_children.size() == 0){
                    return;
                } else {
                    for(RTNode child : _children){
                        if( child._rank != -1 ) {
                            s.add(child);
                        }
                        child.addToEntrySet(s);
                    }
                }
            }
            
            //traverse tree to get list of all values who's key matcher matches pattern
            public void addToList( String pattern, Object context, List<RTNode> list ){
                if(_children.size() == 0){
                    return;
                } else {
                    for(RTNode child : _children){
                        
                        boolean found;
                        synchronized(child._finder){
                            found = child._finder.find(pattern, context);
                        }
                        
                        //check if child matches pattern
                        if(found){
                            if(child._rank != -1){
                                list.add(child);
                            }
                            
                            //check if child is the parent of node then enter that node
                            child.addToList(pattern, context, list);
                        }
                    }
                }
            }
            
            public String toString() {
                return this._finder.toString();
            }
            
            //greatest common prefix between two strings
            public String greatestCommonPrefix(String a, String b) {
                int minLength = Math.min(a.length(), b.length());
                for (int i = 0; i < minLength; i++) {
                    if (a.charAt(i) != b.charAt(i)) {
                        return a.substring(0, i);
                    }
                }
                return a.substring(0, minLength);
            }
        }
        
        class RTNodeRankComparator implements Comparator<RTNode>{
            public int compare(RTNode a, RTNode b) {
                if(a == b){
                    return 0;
                } else if(a == null) {
                    return -1;
                } else if(b == null) {
                    return 1;
                } else if(a._rank == b._rank) {
                    return 0;
                } else if(a._rank > b._rank) {
                    return 1;
                } else {
                    return -1;
                }
            }
        }
    }

    public static Transform<String, RegexFinder> RegexFinderTransform = new Transform<String, RegexFinder>() {
        public RegexFinder transform(String source) {
            return new RegexFinder(source);
        }
    };

    /**
     * The same as a RegexFinderTransform, except that [@ is changed to \[@, and ^ is added before //. To work better
     * with XPaths.
     */
    public static Transform<String, RegexFinder> RegexFinderTransformPath = new Transform<String, RegexFinder>() {
        public RegexFinder transform(String source) {
            final String newSource = source.replace("[@", "\\[@");
            return new RegexFinder(newSource.startsWith("//")
                ? "^" + newSource
                : newSource);
        }
    };

    /**
     * Allows for merging items of the same type.
     * 
     * @param <T>
     */
    public interface Merger<T> {
        T merge(T a, T into);
    }

    /**
     * Returns the result of a regex lookup.
     * 
     * @param source
     * @return
     */
    public final T get(String source) {
        return get(source, null, null, null, null);
    }

    /**
     * Returns the result of a regex lookup, with the group arguments that matched.
     * 
     * @param source
     * @param context
     *            TODO
     * @return
     */
    public T get(String source, Object context, Output<String[]> arguments) {
        return get(source, context, arguments, null, null);
    }

    /**
     * Returns the result of a regex lookup, with the group arguments that matched. Supplies failure cases for
     * debugging.
     * 
     * @param source
     * @param context
     *            TODO
     * @return
     */
    public T get(String source, Object context, Output<String[]> arguments,
        Output<Finder> matcherFound, List<String> failures) {
        
        T ret = entries.get(source, context, arguments, matcherFound);
        if( ret != null ){
            return ret;
        }
        
        if( failures != null ) {
            for (Map.Entry<Finder, T> entry : entries.entrySet()) {
                Finder matcher = entry.getKey();
                synchronized (matcher) {
                    int failPoint = matcher.getFailPoint(source);
                    String show = source.substring(0, failPoint) + "☹" + source.substring(failPoint) + "\t"
                        + matcher.toString();
                    failures.add(show);
                }
            }
        }
        
        // not really necessary, but makes debugging easier.
        if (arguments != null) {
            arguments.value = null;
        }
        if (matcherFound != null) {
            matcherFound.value = null;
        }
        return null;
    }

    /**
     * Returns all results of a regex lookup, with the group arguments that matched. Supplies failure cases for
     * debugging.
     * 
     * @param source
     * @param context
     *            TODO
     * @return
     */
    public List<T> getAll(String source, Object context, List<Finder> matcherList, List<String> failures) {    
        List<T> matches = entries.getAll(source, context, matcherList);
        if( matches != null ){
            return matches;
        }
        
        if( failures != null ) {
            for (Map.Entry<Finder, T> entry : entries.entrySet()) {
                Finder matcher = entry.getKey();
                synchronized (matcher) {
                    int failPoint = matcher.getFailPoint(source);
                    String show = source.substring(0, failPoint) + "☹" + source.substring(failPoint) + "\t"
                        + matcher.toString();
                    failures.add(show);
                }
            }
        }
        return null;
    }

    /**
     * Find the patterns that haven't been matched. Requires the caller to collect the patterns that have, using
     * matcherFound.
     * 
     * @return outputUnmatched
     */
    public Map<String, T> getUnmatchedPatterns(Set<String> matched, Map<String, T> outputUnmatched) {
        outputUnmatched.clear();
        for (Map.Entry<Finder, T> entry : entries.entrySet()) {
            String pattern = entry.getKey().toString();
            if (!matched.contains(pattern)) {
                outputUnmatched.put(pattern, entry.getValue());
            }
        }
        return outputUnmatched;
    }

    /**
     * Create a RegexLookup. It will take a list of key/value pairs, where the key is a regex pattern and the value is
     * what gets returned.
     * 
     * @param patternTransform
     *            Used to transform string patterns into a Pattern. Can be used to process replacements (like
     *            variables).
     * @param valueTransform
     *            Used to transform string values into another form.
     * @param valueMerger
     *            Used to merge values with the same key.
     */
    public static <T, U> RegexLookup<T> of(Transform<String, Finder> patternTransform,
        Transform<String, T> valueTransform, Merger<T> valueMerger) {
        return new RegexLookup<T>().setPatternTransform(patternTransform).setValueTransform(valueTransform)
            .setValueMerger(valueMerger);
    }

    public static <T> RegexLookup<T> of(Transform<String, T> valueTransform) {
        return new RegexLookup<T>().setValueTransform(valueTransform).setPatternTransform(RegexFinderTransform);
    }

    public static <T> RegexLookup<T> of() {
        return new RegexLookup<T>().setPatternTransform(RegexFinderTransform);
    }

    public RegexLookup<T> setValueTransform(Transform<String, ? extends T> valueTransform) {
        this.valueTransform = valueTransform;
        return this;
    }

    public RegexLookup<T> setPatternTransform(Transform<String, ? extends Finder> patternTransform) {
        this.patternTransform = patternTransform != null ? patternTransform : RegexFinderTransform;
        return this;
    }

    public RegexLookup<T> setValueMerger(Merger<T> valueMerger) {
        this.valueMerger = valueMerger;
        return this;
    }

    /**
     * Load a RegexLookup from a file. Opens a file relative to the class, and adds lines separated by "; ". Lines
     * starting with # are comments.
     */
    public RegexLookup<T> loadFromFile(Class<?> baseClass, String filename) {
        RegexFileParser parser = new RegexFileParser();
        parser.setLineParser(new RegexLineParser() {
            @Override
            public void parse(String line) {
                int pos = line.indexOf("; ");
                if (pos < 0) {
                    throw new IllegalArgumentException();
                }
                String source = line.substring(0, pos).trim();
                String target = line.substring(pos + 2).trim();
                try {
                    @SuppressWarnings("unchecked")
                    T result = valueTransform == null ? (T) target : valueTransform.transform(target);
                    add(source, result);
                } catch (Exception e) {
                    throw new IllegalArgumentException("Failed to add <" + source + "> => <" + target + ">", e);
                }
            }
        });
        parser.setVariableProcessor(new VariableProcessor() {
            @Override
            public void add(String variable, String variableName) {
                addVariable(variable, variableName);
            }

            @Override
            public String replace(String str) {
                return variables.replace(str);
            }
        });
        parser.parse(baseClass, filename);
        return this;
    }

    public RegexLookup<T> addVariable(String variable, String variableValue) {
        if (!variable.startsWith("%")) {
            throw new IllegalArgumentException("Variables must start with %");
        }
        variables.add(variable.trim(), variableValue.trim());
        return this;
    }

    /**
     * Add a pattern/value pair.
     * 
     * @param stringPattern
     * @param target
     * @return this, for chaining
     */
    public RegexLookup<T> add(String stringPattern, T target) {
        if (stringPattern.contains("%")) {
            stringPattern = variables.replace(stringPattern);
        }
        Finder pattern0 = patternTransform.transform(stringPattern);
        return add(pattern0, target);
    }

    /**
     * Add a pattern/value pair.
     * 
     * @param pattern
     * @param target
     * @return this, for chaining
     */
    public RegexLookup<T> add(Finder pattern, T target) {
        if (!allowNull && target == null) {
            throw new NullPointerException("null disallowed, unless allowNull(true) is called.");
        }
        
        T old = entries.get(pattern);
        if (old == null) {
            entries.put(pattern, target);
        } else if (valueMerger != null) {
            valueMerger.merge(target, old);
        } else {
            throw new IllegalArgumentException("Duplicate matcher without Merger defined " + pattern + "; old: " + old
                + "; new: " + target);
        }
        return this;
    }

    @Override
    public Iterator<Map.Entry<Finder, T>> iterator() {
        return Collections.unmodifiableCollection(entries.entrySet()).iterator();
    }

    public static String replace(String lookup, String... arguments) {
        StringBuilder result = new StringBuilder();
        int last = 0;
        while (true) {
            int pos = lookup.indexOf("$", last);
            if (pos < 0) {
                result.append(lookup.substring(last, lookup.length()));
                break;
            }
            result.append(lookup.substring(last, pos));
            final int arg = lookup.charAt(pos + 1) - '0';
            try {
                result.append(arguments[arg]);
            } catch (Exception e) {
                throw new IllegalArgumentException("Replacing $" + arg + " in <" + lookup
                    + ">, but too few arguments supplied.");
            }
            last = pos + 2;
        }
        return result.toString();
    }

    /**
     * @return the number of entries
     */
    public int size() {
        return entries.size();
    }
}