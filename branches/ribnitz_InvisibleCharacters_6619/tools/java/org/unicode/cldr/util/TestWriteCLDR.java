package org.unicode.cldr.util;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.apache.tools.ant.taskdefs.condition.Xor;
import org.unicode.cldr.util.XPathParts.Comments;
import org.unicode.cldr.util.XPathParts.Comments.CommentType;

public class TestWriteCLDR {
    private static class DummyXMLSource extends XMLSource {
        private Map<String, String> valueMap = CldrUtility.newConcurrentHashMap();
        private Comments xpComments=new Comments();
        
        @Override
        public XMLSource freeze() {
            return null;
        }

        @Override
        public void putFullPathAtDPath(String distinguishingXPath, String fullxpath) {
        }

        @Override
        public void putValueAtDPath(String distinguishingXPath, String value) {
            valueMap.put(distinguishingXPath, value);
        }

        @Override
        public void removeValueAtDPath(String distinguishingXPath) {
        }

        @Override
        public String getValueAtDPath(String path) {
            return valueMap.get(path);
        }

        @Override
        public String getFullPathAtDPath(String path) {
            return null;
        }

        @Override
        public Comments getXpathComments() {
            return xpComments;
        }

        @Override
        public void setXpathComments(Comments comments) {
            xpComments=comments;
        }

        @Override
        public Iterator<String> iterator() {
            return valueMap.keySet().iterator();
        }

        @Override
        public void getPathsWithValue(String valueToMatch, String pathPrefix, Set<String> result) {
        }
    }
 public static void main(String[] args) {
     XMLSource xmlSource=new DummyXMLSource();
    xmlSource.putValueAtDPath("//ldml/numbers/currencies/currency[@type=\"SRD\"]/symbol", "ab\u00ADc");
    Comments comments=xmlSource.getXpathComments();
    comments.addComment(CommentType.LINE,
            "//ldml/numbers/currencies/currency[@type=\"SRD\"]/symbol", "This is a comment");
    comments.addComment(CommentType.PREBLOCK, "//ldml/numbers/currencies/currency[@type=\"SRD\"]/symbol", "Preblock comment");
    comments.addComment(CommentType.POSTBLOCK, "//ldml/numbers/currencies/currency[@type=\"SRD\"]/symbol", "Postblock comment");
    CLDRFile f=new CLDRFile(xmlSource);
    StringWriter sw=new StringWriter();
    PrintWriter pw=new PrintWriter(sw);
    for (String i: f.fullIterable()) {
   
    // now do the rest
    final String COPYRIGHT_STRING = CldrUtility.getCopyrightString();
    
    String initialComment = f.dataSource.getXpathComments().getInitialComment();
    if (!initialComment.contains("Copyright") || !initialComment.contains("Unicode")) {
        initialComment = initialComment + COPYRIGHT_STRING;
    }
    XPathParts.writeComment(pw, 0, initialComment, true);

    XPathParts.Comments tempComments = (XPathParts.Comments) f.dataSource.getXpathComments().clone();
    tempComments.fixLineEndings();

    //        MapComparator<String> modAttComp = attributeOrdering;
    //        if (HACK_ORDER) modAttComp = new MapComparator<String>()
    //            .add("alt").add("draft").add(modAttComp.getOrder());

    MapComparator<String> attributeOrdering2 = f.getAttributeOrdering();
    XPathParts last = new XPathParts(attributeOrdering2, f.getDefaultSuppressionMap());
    XPathParts current = new XPathParts(attributeOrdering2, f.getDefaultSuppressionMap());
    XPathParts lastFiltered = new XPathParts(attributeOrdering2, f.getDefaultSuppressionMap());
    XPathParts currentFiltered = new XPathParts(attributeOrdering2, f.getDefaultSuppressionMap());
    boolean isResolved = f.dataSource.isResolving();

    for (String xpath: new String[]{"//ldml/numbers/currencies/currency[@type=\"SRD\"]/symbol"}) {
//    for (Iterator<String> it2 = identitySet.iterator(); it2.hasNext();) {
//        String xpath = (String) it2.next();
        currentFiltered.set(xpath);
        current.set(xpath);
      
        tempComments.addComment(CommentType.LINE, "//ldml/numbers/currencies/currency[@type=\"SRD\"]/symbol", "This is a comment about currency SRD");
        current.writeDifference(pw, currentFiltered, last, lastFiltered, "", tempComments);
        // exchange pairs of parts
        XPathParts temp = current;
        current = last;
        last = temp;
        temp = currentFiltered;
        currentFiltered = lastFiltered;
        lastFiltered = temp;
    }
    int jj=1;
}}}
