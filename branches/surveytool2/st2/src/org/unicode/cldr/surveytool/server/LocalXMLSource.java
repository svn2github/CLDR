package org.unicode.cldr.surveytool.server;

import java.io.File;
import java.util.Iterator;
import java.util.Set;

import org.unicode.cldr.util.CLDRFile.Factory;
import org.unicode.cldr.util.XPathParts.Comments;
import org.unicode.cldr.util.CLDRFile.SimpleXMLSource;

public class LocalXMLSource extends SimpleXMLSource {
    public LocalXMLSource(String localeID, Factory factory, LocaleWrapper wrapper) {
        super(factory, localeID);
        this.xpath_value = wrapper.getValMap();
        this.xpath_fullXPath = wrapper.getFullPath();
        this.xpath_comments = wrapper.getComments();
    }
}
