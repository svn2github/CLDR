// Copyright 2011 Google Inc. All Rights Reserved.

package org.unicode.cldr.surveytool.server;
//import javax.jdo.annotations.IdGeneratorStrategy;
import javax.jdo.annotations.IdentityType;
import javax.jdo.annotations.PersistenceCapable;
import javax.jdo.annotations.Persistent;
import javax.jdo.annotations.PrimaryKey;
import org.unicode.cldr.util.CLDRFile;
import org.unicode.cldr.util.XPathParts.Comments;
import org.unicode.cldr.util.XMLSource;

import java.util.HashMap;
import java.util.Iterator;

/**
 * @author anthonyfernandez.af@gmail.com (Tony Fernandez)
 * A generic object that can contain whatever we end up needing to put into
 * the datastore.
 */

/* Quick notes, actually important TODO's exist in CLDRFile and in the files I
 * created.
 */
@PersistenceCapable(identityType = IdentityType.APPLICATION)
class LocaleWrapper {
  @PrimaryKey
  @Persistent
  private String key;
  @Persistent
  private String localeID;
  @Persistent(serialized="true")
  private HashMap<String,String> xpath_value;
  @Persistent(serialized="true")
  private HashMap<String,String> xpath_fullXPath;
  @Persistent(serialized="true")
  private Comments xpath_comments;

  /**
   * Creates a truncated LocaleWrapper from just a localeID. Used for testing
   * gets and stores in the AppEngine datastore.
   * TODO delete this once we leave testing. 
   * @param locID the localeID
   */
  public LocaleWrapper(String locID){
    key = localeID = locID;
  }

  public LocaleWrapper(String key, String localeID, HashMap<String,String> xpathValues, HashMap<String, String> xpathFull) {
      this.key = key;
      this.localeID = localeID;
      xpath_value = xpathValues;
      xpath_fullXPath = xpathFull;
  }
  
  public LocaleWrapper(String key, String localeID, HashMap<String,String> xpathValues, HashMap<String, String> xpathFull, Comments comments) {
      this.key = key;
      this.localeID = localeID;
      xpath_value = xpathValues;
      xpath_fullXPath = xpathFull;
      //xpath_comments = comments;
      System.out.println("key " + key + " localeID " + localeID);
  }
  
  /**
   * Builds a new LocaleWrapper from the given CLDRFile, and clones the
   * underlying data.
   * @param refFile
   */
  public LocaleWrapper(CLDRFile refFile){
    key = localeID = refFile.getLocaleID();
    XMLSource mySource = refFile.getDataSource();
    xpath_value = new HashMap<String,String>();
    xpath_fullXPath = new HashMap<String,String>();
    Iterator<String> itr = mySource.iterator();
    String key;
    while(itr.hasNext())
    {
      key=itr.next();
      xpath_value.put(key, mySource.getValueAtDPath(key));
      xpath_fullXPath.put(key, mySource.getFullPath(key));
    }
    //xpath_comments = refFile.getXpath_comments();
    /*xpath_value = (HashMap<String,String>) mySource.getValMap().clone();
    xpath_fullXPath = (HashMap<String,String>) mySource.getFullPathMap().clone();*/
  }
  
  /**
   * 
   * @return the key
   */
  public String getKey() {
    return key;
  }
  
  /**
   * 
   * @return the localeID
   */
  public String getLocaleID() {
    return localeID;
  }
  
  /**
   * 
   * @return the Hashmap containing all the xpath,value pairs.
   */
  public HashMap<String,String> getValMap(){
    return xpath_value;
  }
  /**
   * 
   * @return the Hashmap containing all the xpath,fullXpath pairs.
   */
  public HashMap<String,String> getFullPath(){
    return xpath_fullXPath;
  }

  public Comments getComments(){
    return xpath_comments;
  }
}