package org.unicode.cldr.surveytool.server;

import org.unicode.cldr.util.CLDRFile.Factory;
import org.unicode.cldr.util.CLDRFile.SimpleXMLSource;

/**
 * An XMLSource that can be loaded from and saved to a LocaleWrapper.
 * @author anthonyfernandez.af@gmail.com (Tony Fernandez)
 */
public class PersistentXMLSource extends SimpleXMLSource {
    public PersistentXMLSource(Factory factory, LocaleWrapper wrapper) {
        super(factory, wrapper.getLocaleID());
        xpath_value = wrapper.getValMap();
        xpath_fullXPath = wrapper.getFullPath();
        xpath_comments = wrapper.getComments();
    }

    /**
     * creates a new PersistentXMLSource from the contents of a SimpleXMLSource
     * 
     * @param copyAsLockedFrom
     */
    protected PersistentXMLSource(SimpleXMLSource copyAsLockedFrom) {
        super(copyAsLockedFrom);
    }

    /**
     * creates a LocaleWrapper from the contents of this PersistentXMLSource
     * @param key   the key to assign to the LocaleWrapper
     * @return the created LocaleWrapper
     */
    public LocaleWrapper createLocaleWrapper(String key) {
        return new LocaleWrapper(key, getLocaleID(), xpath_value,
                xpath_fullXPath, xpath_comments);
    }
}
