package org.unicode.cldr.web;

import java.io.File;
import java.io.FileFilter;
import java.util.Date;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Properties;
import java.util.Set;

import org.unicode.cldr.icu.LDMLConstants;
import org.unicode.cldr.util.CLDRLocale;
import org.unicode.cldr.util.LDMLUtilities;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

import com.ibm.icu.dev.test.util.ElapsedTimer;

public class SourceDirectory {
	private File sourcePath;
	private Set<CLDRLocale> localeListSet;
    private static Hashtable<CLDRLocale,CLDRLocale> aliasMap = null;
    private static Hashtable<CLDRLocale,String> directionMap = null;
	
	/**
	 * @deprecated use a File
	 * @param path
	 */
	SourceDirectory(String path) {
		sourcePath = new File(path);
	}
	
	SourceDirectory(File path) {
		sourcePath = path;
	}
	
	public Set<CLDRLocale> getLocalesSet() {
	    if(localeListSet == null ) {
	        File inFiles[] = getInFiles();
	        int nrInFiles = inFiles.length;
	        Set<CLDRLocale> s = new HashSet<CLDRLocale>();
	        for(int i=0;i<nrInFiles;i++) {
	            String fileName = inFiles[i].getName();
	            int dot = fileName.indexOf('.');
	            if(dot !=  -1) {
	                String locale = fileName.substring(0,dot);
	                s.add(CLDRLocale.getInstance(locale));
	            }
	        }
	        localeListSet = s;
	    }
	    return localeListSet;
	}
    public File[] getInFiles() {
    	return getInFiles(sourcePath);    
    }
    
    /**
     * Utility function
     * @deprecated
     * @param base
     * @return
     */
    static protected File[] getInFiles(String base) {
        File baseDir = new File(base);
        return getInFiles(baseDir);
    }
    
    /**
     * Utility function
     * @param baseDir
     * @return
     */
    static protected File[] getInFiles(File baseDir) {
        // 1. get the list of input XML files
        FileFilter myFilter = getXmlFileFilter();
        return baseDir.listFiles(myFilter);
    }

	public CLDRLocale[] getLocales() {
        return (CLDRLocale[])getLocalesSet().toArray(new CLDRLocale[0]);		
	}
	/**
	 * Get a file filter that uses only xml data and skips supplemental
	 * @return a new FileFilter
	 */
    public static FileFilter getXmlFileFilter() {
    	return new FileFilter() {
            public boolean accept(File f) {
                String n = f.getName();
                return(!f.isDirectory()
                       &&n.endsWith(".xml")
                       &&!n.startsWith(".")
                       &&!n.startsWith("supplementalData") // not a locale
                       /*&&!n.startsWith("root")*/); // root is implied, will be included elsewhere.
            }
        };
    }

	public String getDirectionalityFor(CLDRLocale id) {
        final boolean DDEBUG=false;
        if (DDEBUG) System.err.println("Checking directionality for " + id);
        if(aliasMap==null) {
            checkAllLocales();
        }
        while(id != null) {
            // TODO use iterator
            CLDRLocale aliasTo = isLocaleAliased(id);
            if (DDEBUG) System.err.println("Alias -> "+aliasTo);
            if(aliasTo != null 
                    && !aliasTo.equals(id)) { // prevent loops
                id = aliasTo;
                if (DDEBUG) System.err.println(" -> "+id);
                continue;
            }
            String dir = directionMap.get(id);
            if (DDEBUG) System.err.println(" dir:"+dir);
            if(dir!=null) {
                return dir;
            }
            id = id.getParent();
            if (DDEBUG) System.err.println(" .. -> :"+id);
        }
        if (DDEBUG) System.err.println("err: could not get directionality of root");
        return "left-to-right"; //fallback	}
	}

	public CLDRLocale isLocaleAliased(CLDRLocale id) {
        if(aliasMap==null) {
            checkAllLocales();
        }
        return aliasMap.get(id);
	}

    
    /**
     * "Hash" a file to a string, including mod time and size
     * @param f
     * @return
     */
    private static String fileHash(File f) {
        return("["+f.getAbsolutePath()+"|"+f.length()+"|"+f.hashCode()+"|"+f.lastModified()+"]");
    }

    private synchronized void checkAllLocales() {
        if(aliasMap!=null) return;
        
        boolean useCache = SurveyMain.isUnofficial; // NB: do NOT use the cache if we are in unofficial mode.  Parsing here doesn't take very long (about 16s), but 
        // we want to save some time during development iterations.
        
        Hashtable<CLDRLocale,CLDRLocale> aliasMapNew = new Hashtable<CLDRLocale,CLDRLocale>();
        Hashtable<CLDRLocale,String> directionMapNew = new Hashtable<CLDRLocale,String>();
        Set<CLDRLocale> locales  = getLocalesSet();
        ElapsedTimer et = new ElapsedTimer();
        //setProgress("Parse locales from XML", locales.size());
        int nn=0;
        File xmlCache = new File(SurveyMain.vetdir, SurveyMain.XML_CACHE_PROPERTIES);
        File xmlCacheBack = new File(SurveyMain.vetdir, SurveyMain.XML_CACHE_PROPERTIES+".backup");
        Properties xmlCacheProps = new java.util.Properties(); 
        Properties xmlCachePropsNew = new java.util.Properties(); 
        if(useCache && xmlCache.exists()) try {
            java.io.FileInputStream is = new java.io.FileInputStream(xmlCache);
            xmlCacheProps.load(is);
            is.close();
        } catch(java.io.IOException ioe) {
            /*throw new UnavailableException*/
            SurveyMain.logger.log(java.util.logging.Level.SEVERE, "Couldn't load XML cache ': ",ioe);
            SurveyMain.busted("Couldn't load XML cache': ", ioe);
            return;
        }
        
        int n=0;
        int cachehit=0;
        System.err.println("Parse " + locales.size() + " locales from XML to look for aliases or errors...");
        for(CLDRLocale loc : locales) {
            String locString = loc.toString();
           // updateProgress(n++, loc.toString() /* + " - " + uloc.getDisplayName(uloc) */);
            try {
                File  f = new File(sourcePath, loc.toString()+".xml");
                String fileHash = fileHash(f);
                String aliasTo = null;
                String direction = null;
                
                String oldHash = xmlCacheProps.getProperty(locString);
                if(useCache && oldHash != null && oldHash.equals(fileHash)) {
                    // cache hit! load from cache
                    aliasTo = xmlCacheProps.getProperty(locString+".a",null);
                    direction = xmlCacheProps.getProperty(locString+".d",null);
                    cachehit++;
                } else {
                    Document d = LDMLUtilities.parse(f.getAbsolutePath(), false);
                    
                    // look for directionality
                    Node[] directionalityItems = 
                        LDMLUtilities.getNodeListAsArray(d,"//ldml/layout/orientation");
                    if(directionalityItems!=null&&directionalityItems.length>0) {
                        direction = LDMLUtilities.getAttributeValue(directionalityItems[0], LDMLConstants.CHARACTERS);
                        if(direction != null&& direction.length()>0) {
                        } else {
                            direction = null;
                        }
                    }
                    
                    
                    Node[] aliasItems = 
                                LDMLUtilities.getNodeListAsArray(d,"//ldml/alias");
                                
                    if((aliasItems==null) || (aliasItems.length==0)) {
                        aliasTo=null;
                    } else if(aliasItems.length>1) {
                        throw new InternalError("found " + aliasItems + " items at " + "//ldml/alias" + " - should have only found 1");
                    } else {
                        aliasTo = LDMLUtilities.getAttributeValue(aliasItems[0],"source");
                    }
                }
                
                // now, set it into the new map
                xmlCachePropsNew.put(locString, fileHash);
                if(direction != null) {
                    directionMapNew.put((loc), direction);
                    xmlCachePropsNew.put(locString+".d", direction);
                }
                if(aliasTo!=null) {
                    aliasMapNew.put((loc),CLDRLocale.getInstance(aliasTo));
                    xmlCachePropsNew.put(locString+".a", aliasTo);
                }
            } catch (Throwable t) {
                System.err.println("isLocaleAliased: Failed load/validate on: " + loc + " - " + t.toString());
                t.printStackTrace();
                SurveyMain.busted("isLocaleAliased: Failed load/validate on: " + loc + " - ", t);
                throw new InternalError("isLocaleAliased: Failed load/validate on: " + loc + " - " + t.toString());
            }
        }
        
        if(useCache) try {
            // delete old stuff
            if(xmlCacheBack.exists()) { 
                xmlCacheBack.delete();
            }
            if(xmlCache.exists()) {
                xmlCache.renameTo(xmlCacheBack);
            }
            java.io.FileOutputStream os = new java.io.FileOutputStream(xmlCache);
            xmlCachePropsNew.store(os, "YOU MAY DELETE THIS CACHE. Cache updated at " + new Date());
            //updateProgress(nn++, "Loading configuration..");
            os.close();
        } catch(java.io.IOException ioe) {
            /*throw new UnavailableException*/
            SurveyMain.logger.log(java.util.logging.Level.SEVERE, "Couldn't write "+xmlCache+" file from '" +"" + "': ",ioe);
            SurveyMain.busted("Couldn't write "+xmlCache+" file from '" + ""+"': ", ioe);
            return;
        }
        
        System.err.println("Finished verify+alias check of " + locales.size()+ ", " + aliasMapNew.size() + " aliased locales ("+cachehit+" in cache) found in " + et.toString());
        aliasMap = aliasMapNew;
        directionMap = directionMapNew;
        //clearProgress();
    }

	public void reset() {
        aliasMap = null;
	}

}