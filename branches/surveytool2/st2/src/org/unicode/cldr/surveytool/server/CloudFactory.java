package org.unicode.cldr.surveytool.server;

import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.jdo.JDOObjectNotFoundException;
import javax.jdo.PersistenceManager;

import org.unicode.cldr.util.CLDRFile;
import org.unicode.cldr.util.CLDRFile.DraftStatus;
import org.unicode.cldr.util.CLDRFile.SimpleFactory;
import org.unicode.cldr.util.CLDRFile.SimpleXMLSource;

import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.KeyFactory;

  /**
   * This factory provides a means to produce version-dependent CLDRFiles with 
   * PersistentXMLSources, and overrides the methods needed to provide drop in
   * support for things like resolving. 
   *
   * @author anthonyfernandez.af@gmail.com (Tony Fernandez)
   */
  public class CloudFactory extends SimpleFactory {
    private static PersistenceManager pm = PmfSingleton.getInstance().getPersistenceManager();
    private String version;

    protected Map<String,CLDRFile>[] mainCache = new Map[DraftStatus.values().length];
    protected Map<String,CLDRFile>[] resolvedCache = new Map[DraftStatus.values().length];
    {
      for (int i = 0; i < mainCache.length; ++i) {
        mainCache[i] = new TreeMap();
        resolvedCache[i] = new TreeMap();
      }
    }

    protected CloudFactory(String sourceDirectory, String matchString, DraftStatus minimalDraftStatus, String version) {
        this.sourceDirectory = sourceDirectory;
        this.matchString = matchString;
        this.minimalDraftStatus = minimalDraftStatus;
        Matcher m = Pattern.compile(matchString).matcher("");
        this.localeList = CLDRFile.getMatchingXMLFiles(sourceDirectory, m);
        this.version = version;
    }

    public static CloudFactory make(String sourceDirectory, String matchString) {
        return make(sourceDirectory, matchString, DraftStatus.unconfirmed, "");
      }

    /**
     * Create a factory from a source directory, matchingString, and an optional log file.
     * For the matchString meaning, see {@link getMatchingXMLFiles}
     */
    public static CloudFactory make(String sourceDirectory, String matchString, DraftStatus minimalDraftStatus, String version) {
        return new CloudFactory(sourceDirectory, matchString, minimalDraftStatus, version);
    }

    /* (non-Javadoc)
     * @see org.unicode.cldr.util.CLDRFile.Factory#handleMake(java.lang.String, boolean, org.unicode.cldr.util.CLDRFile.DraftStatus)
     */
    @Override
    public CLDRFile handleMake(String localeName, boolean resolved, DraftStatus madeWithMinimalDraftStatus) {
        Map<String,CLDRFile> cache = resolved ? resolvedCache[madeWithMinimalDraftStatus.ordinal()] : mainCache[madeWithMinimalDraftStatus.ordinal()];
        CLDRFile result = cache.get(localeName);
        if (result == null) {
            PersistentXMLSource pxs;
            String key = localeName + "-" + version;
            try {
                // Attempt to load from the datastore.
                Key localeKey = KeyFactory.createKey("LocaleWrapper", key);
                LocaleWrapper wrapper = pm.getObjectById(LocaleWrapper.class, localeKey);
                pxs = new PersistentXMLSource(this, wrapper);
            } catch(JDOObjectNotFoundException e) {  // locale not found, load from disk
                CLDRFile file = CLDRFile.make(localeName, getSourceDirectory(), DraftStatus.unconfirmed);
                pxs = new PersistentXMLSource((SimpleXMLSource)file.getDataSource());
                LocaleWrapper wrapper = pxs.createLocaleWrapper(key);
                pm.makePersistent(wrapper);
            }
  
            result = new CLDRFile(pxs, false);
            if(resolved) result = result.getResolved();
            else result.freeze();
            cache.put(localeName, result);
        }
        return result;
    }
    
  }