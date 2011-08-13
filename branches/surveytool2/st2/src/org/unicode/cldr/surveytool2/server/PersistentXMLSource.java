// Copyright 2011 Google Inc. All Rights Reserved.

package org.unicode.cldr.surveytool2.server;

import org.unicode.cldr.surveytool2.server.PersistentXMLSource.CloudFactory;
import org.unicode.cldr.util.CLDRFile;
import org.unicode.cldr.util.CLDRFile.DraftStatus;
import org.unicode.cldr.util.CLDRFile.Factory;
import org.unicode.cldr.util.CLDRFile.SimpleXMLSource;
import org.unicode.cldr.util.CLDRFile.SimpleFactory;
import org.unicode.cldr.util.XMLSource;
import org.unicode.cldr.util.XPathParts.Comments;



import javax.jdo.PersistenceManagerFactory;
import javax.jdo.JDOHelper;
import javax.jdo.PersistenceManager;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.KeyFactory;



/**
 * @author anthonyef@google.com (Tony Fernandez)
 *
 */
/*
 * Author's notes, before building.
 * This source's data needs to be persistent.
 * In the backend, make a hashmap, from xpath to value
 * and xpath to full xpath
 * This will only be talked to by a middleman on the server
 * this middleman will handle the caching and write through for sets
 * 
 */
//Global todo list
//TODO Support regex over path and over value, use .find


// note: to show verbose resolution output on the console, use -DTRACE_FILL=true
// in the vm args.

public class PersistentXMLSource extends SimpleXMLSource {
  PersistenceManager pm;
  LocaleWrapper myLoc;
  public PersistentXMLSource(String localeID, PersistenceManager persMan) {
    //There is no default constructor for SimpleXMLSource, so we use this one.
    super(CloudFactory.make("common/main/",".*", DraftStatus.unconfirmed), localeID);
    pm = persMan;
    //generate the key for this localeID, and then grab the localeWrapper.
    Key localeKey = KeyFactory.createKey("LocaleWrapper",getLocaleID());
    myLoc = pm.getObjectById(LocaleWrapper.class, localeKey);
    //Get the data out of the wrapper into the fields.
    this.xpath_value = myLoc.getValMap();
    this.xpath_fullXPath = myLoc.getFullPath();
    //this.xpath_comments = myLoc.getComments();
  }
  public PersistentXMLSource(String localeID, PersistenceManager persMan, Factory fact){
    super(fact, localeID);
    pm = persMan;
    Key localeKey = KeyFactory.createKey("LocaleWrapper",getLocaleID());
    myLoc = pm.getObjectById(LocaleWrapper.class, localeID);//localeKey);
    this.xpath_value = myLoc.getValMap();
    this.xpath_fullXPath = myLoc.getFullPath();
  }
  
  @Override
  public void setXpathComments(Comments xpath_comments){
    throw new UnsupportedOperationException();
  }
  /*
   * Accessors
   */
  @Override
  public Comments getXpathComments(){
    throw new UnsupportedOperationException();
  }
  //All of the subclass's accessors SHOULD work, since they're the same data
  //structures, these are just persistent.
  /*
   * Mutators
   */
  /**
   * Remove the value at the given Dpath, and then write through.
   * @param String distinguishingXPath
   */
  @Override
  public void removeValueAtDPath(String distinguishingXPath){
    super.removeValueAtDPath(distinguishingXPath);
    pm.makePersistent(myLoc);
  }
  /**
   * Puts the given full path and the given dpath, then writes through
   * @param String distinguishingXPath
   * @param String fullxpath
   */
  @Override
  public void putFullPathAtDPath(String distinguishingXPath, String fullxpath){
    super.putFullPathAtDPath(distinguishingXPath, fullxpath);
    pm.makePersistent(myLoc);
  }
  /**
   * Puts the value at the dpath, and then writes through.
   * @param String distinguishingXPath
   * @param String value
   */
  @Override
  public void putValueAtDPath(String distinguishingXPath, String value) {
    super.putValueAtDPath(distinguishingXPath, value);
    pm.makePersistent(myLoc);
  }
  //The lesson here is that object inheritance is awesome.
  /*
   * Helpers
   */
  private String getLocaleLang(String locale){
    String ret="";
    
    return ret;
  }
  @Override
  public XMLSource make(String localeID){
    if(localeID == null) return null;
    PersistentXMLSource ret = new PersistentXMLSource(localeID, pm);
    return ret;
  }
  public static class LocalXMLSource extends SimpleXMLSource
  {

    /**
     * @param copyFrom
     */
    public LocalXMLSource(SimpleXMLSource copyFrom) {
      super(copyFrom);
    }
    public XMLSource make(String localeID)
    {
      LocalFactory fact = (LocalFactory) LocalFactory.make("common/main/", ".*", DraftStatus.unconfirmed);
      CLDRFile ref = fact.handleMake(localeID, false, DraftStatus.unconfirmed);
      LocalXMLSource ret = (LocalXMLSource)ref.getDataSource();
      return ret;
    }
  }
  /**
   * 
   * @author anthonyef@google.com (Tony Fernandez)
   * This factory provides a means to produce CLDRFiles with 
   * PersistentXMLSources, and overrides the methods needed to provide drop in
   * support for things like resolving. 
   */
  public static class CloudFactory extends Factory
  {
    private String sourceDirectory;
    private String matchString;
    private DraftStatus minimalDraftStatus = DraftStatus.unconfirmed;
    private Set<String> localeList = null;
    PersistenceManager pm=null;
    public static Factory make(String sourceDirectory, String matchString) {
      return make(sourceDirectory, matchString, DraftStatus.unconfirmed);
    }
    /**
     * 
     * DO NOT USE. Instead, call the one that also has the persistence manager
     * parameter. I have no way of creating a persistence manager in here, since
     * creating another PM factory is forbidden.
     */
    public static Factory make(String sourceDirectory, String matchString, DraftStatus minimalDS)
    {
      CloudFactory result = new CloudFactory();
      result.sourceDirectory = sourceDirectory;
      result.matchString = matchString;
      result.minimalDraftStatus = minimalDS;
      Matcher m = Pattern.compile(matchString).matcher("");
      result.localeList = CLDRFile.getMatchingXMLFiles(sourceDirectory, m);
      return result;
    }
    public static CloudFactory make(
        String sourceDirectory, String matchString, DraftStatus minimalDS, PersistenceManager pm2) {
      CloudFactory ret = (CloudFactory) make(sourceDirectory, matchString, minimalDS);
      ret.pm = pm2;
      return ret;
    }
    /* (non-Javadoc)
     * @see org.unicode.cldr.util.CLDRFile.Factory#getMinimalDraftStatus()
     */
    @Override
    public DraftStatus getMinimalDraftStatus() {
      return minimalDraftStatus;
    }

    /* (non-Javadoc)
     * @see org.unicode.cldr.util.CLDRFile.Factory#getSourceDirectory()
     */
    @Override
    public String getSourceDirectory() {
      return sourceDirectory;
    }

    /* (non-Javadoc)
     * @see org.unicode.cldr.util.CLDRFile.Factory#handleGetAvailable()
     */
    @Override
    protected Set<String> handleGetAvailable() {
      return localeList;
    }

    /* (non-Javadoc)
     * @see org.unicode.cldr.util.CLDRFile.Factory#handleMake(java.lang.String, boolean, org.unicode.cldr.util.CLDRFile.DraftStatus)
     */
    @Override
    public CLDRFile handleMake(String localeName, boolean resolved, DraftStatus madeWithMinimalDraftStatus) {
      CLDRFile ret;
      String dir = CLDRFile.isSupplementalName(localeName) ? sourceDirectory.replace("incoming/vetted/","common/") + File.separator + "../supplemental/" : sourceDirectory;
      //ret = 
      PersistentXMLSource pxs = new PersistentXMLSource(localeName, pm);
      ret = new CLDRFile(pxs, false);
      if(resolved) ret = ret.getResolved();
      return ret;
    }
    
  }
  /**
   * 
   * @author anthonyef@google.com (Tony Fernandez)
   * 
   * This factory creates CLDRFiles with SimpleXMLSources, but is AppEngine
   * compatible (read: file open calls don't break)
   */
  public static class LocalFactory extends Factory
  {
    private String sourceDirectory;
    private String matchString;
    private DraftStatus minimalDraftStatus = DraftStatus.unconfirmed;
    private Set<String> localeList = null;
    private Map<String,CLDRFile>[] mainCache = new Map[DraftStatus.values().length];
    private Map<String,CLDRFile>[] resolvedCache = new Map[DraftStatus.values().length];
    {
      for (int i = 0; i < mainCache.length; ++i) {
        mainCache[i] = new TreeMap();
        resolvedCache[i] = new TreeMap();
      }
    }
    
    public static Factory make(String sourceDirectory, String matchString, DraftStatus minimalDS){
      
      LocalFactory result = new LocalFactory();
      result.sourceDirectory = sourceDirectory;
      result.matchString = matchString;
      result.minimalDraftStatus = minimalDS;
      Matcher m = Pattern.compile(matchString).matcher("");
      result.localeList = CLDRFile.getMatchingXMLFiles(sourceDirectory, m);
      return result;
    }
    /* (non-Javadoc)
     * @see org.unicode.cldr.util.CLDRFile.Factory#getMinimalDraftStatus()
     */
    @Override
   public DraftStatus getMinimalDraftStatus() {
      return minimalDraftStatus;
    }
 
    /* (non-Javadoc)
     * @see org.unicode.cldr.util.CLDRFile.Factory#getSourceDirectory()
     */
    @Override
    public String getSourceDirectory() {
      return sourceDirectory;
    }

    /* (non-Javadoc)
     * @see org.unicode.cldr.util.CLDRFile.Factory#handleGetAvailable()
     */
    @Override
    public Set<String> handleGetAvailable() {
      return localeList;
    }

    /* (non-Javadoc)
     * @see org.unicode.cldr.util.CLDRFile.Factory#handleMake(java.lang.String, boolean, org.unicode.cldr.util.CLDRFile.DraftStatus)
     */
    @Override
    public CLDRFile handleMake(
        String localeID, boolean resolved, DraftStatus madeWithMinimalDraftStatus) {
      Map<String,CLDRFile> cache = resolved ? resolvedCache[madeWithMinimalDraftStatus.ordinal()] : mainCache[madeWithMinimalDraftStatus.ordinal()];
      CLDRFile ret = cache.get(localeID);
      if(ret != null) return ret;
      //System.out.println("Attempting to open XML file to make CLDRFile.");
      FileInputStream fis = null;
      /*This is what makes CLDRFiles with SimpleXMLSources work on AppEngine
       *In short, all other construction methods try to create an absolute path
       *to the XML file, and that breaks things in AppEngine, so I have to open
       *the files myself.
       */
      String fileName = sourceDirectory+localeID+".xml";
      //Plenty of exception and error handling.
      try{
        fis = new FileInputStream(fileName);
      }
      catch(FileNotFoundException e){
        System.out.println("FAILED TO OPEN FILE: "+fileName);
        System.out.println(e.getMessage());
        e.printStackTrace();
        return null;
      }
      //try{
      ret = CLDRFile.make(fileName, localeID, fis, minimalDraftStatus);
      /*}
      catch(Exception e){
        System.out.println("Something failed in the call to CLDRFile.make");
        System.out.println(e.getMessage());
        e.printStackTrace();
        return null;
      }*/
      LocalXMLSource data = new LocalXMLSource((SimpleXMLSource)ret.getDataSource());
      ret = new CLDRFile(data, false);
      //resolve if need be
      if(resolved) ret = ret.getResolved();
      else ret.freeze();
      cache.put(localeID, ret);
      return ret;
    }
    
  }
  
}
