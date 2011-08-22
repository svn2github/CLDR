package org.unicode.cldr.surveytool.server;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.util.HashMap;

import org.junit.Test;
import org.unicode.cldr.util.CLDRFile.Factory;
import org.unicode.cldr.util.XPathParts.Comments;

public class PersistentXMLSourceTest {
    // String value for the key of a LocaleWrapper.
    private static String KEY = "key";

    @Test
    public final void testCreateLocaleWrapper() {
        HashMap<String, String> values = new HashMap<String, String>();
        values.put("//ldml/localeDisplayNames/scripts/script[@type=\"Thaa\"]", "Thaana");
        HashMap<String, String> fullXpath = new HashMap<String, String>();
        Comments comments = new Comments();
        LocaleWrapper wrapper = new LocaleWrapper(KEY, "en", values, fullXpath, comments);
        Factory factory = mock(Factory.class);
        PersistentXMLSource source = new PersistentXMLSource(factory, wrapper);
        LocaleWrapper newWrapper = source.createLocaleWrapper(KEY);

        assertEquals(wrapper.getKey(), newWrapper.getKey());
        assertEquals(wrapper.getLocaleID(), newWrapper.getLocaleID());
        assertEquals(wrapper.getValMap(), newWrapper.getValMap());
        assertEquals(wrapper.getFullPath(), newWrapper.getFullPath());
        assertEquals(wrapper.getComments(), newWrapper.getComments());
        verifyZeroInteractions(factory);
    }
}
