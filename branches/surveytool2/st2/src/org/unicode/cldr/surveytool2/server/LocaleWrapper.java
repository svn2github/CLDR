// Copyright 2011 Google Inc. All Rights Reserved.

package org.unicode.cldr.surveytool2.server;
//import javax.jdo.annotations.IdGeneratorStrategy;
import javax.jdo.annotations.IdentityType;
import javax.jdo.annotations.PersistenceCapable;
import javax.jdo.annotations.Persistent;
import javax.jdo.annotations.PrimaryKey;
import org.unicode.cldr.util.CLDRFile;
import org.unicode.cldr.util.CLDRFile.SimpleXMLSource;
import org.unicode.cldr.util.XMLSource;
import org.unicode.cldr.util.XPathParts.Comments;

import java.util.HashMap;
import java.util.Iterator;

/**
 * @author anthonyef@google.com (Tony Fernandez)
 * A generic object that can contain whatever we end up needing to put into
 * the datastore.
 */

/* Quick notes, actually important TODO's exist in CLDRFile and in the files I
 * created.
 */
@PersistenceCapable(identityType = IdentityType.APPLICATION)
public class LocaleWrapper {
  @PrimaryKey
  @Persistent
  private String localeID;
  @Persistent(serialized="true")
  private HashMap<String,String> xpath_value;
  @Persistent(serialized="true")
  private HashMap<String,String> xpath_fullXPath;
  /*@Persistent
  private Comments xpath_comments;*/
  /**
   * Creates a truncated LocaleWrapper from just a localeID. Used for testing
   * gets and stores in the AppEngine datastore.
   * TODO delete this once we leave testing. 
   * @param locID the localeID
   */
  public LocaleWrapper(String locID){
    localeID=locID;
  }
  /**
   * Builds a new LocaleWrapper from the given CLDRFile, and clones the
   * underlying data.
   * @param refFile
   */
  public LocaleWrapper(CLDRFile refFile){
    localeID = refFile.getLocaleID();
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
    /*xpath_value = (HashMap<String,String>) mySource.getValMap().clone();
    xpath_fullXPath = (HashMap<String,String>) mySource.getFullPathMap().clone();*/
    //xpath_comments = (mySource.xpath_comments != null ?(Comments) mySource.xpath_comments.clone() : null);
  }
  /**
   * 
   * @return the localeID
   */
  public String getID()
  {
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
  /*public Comments getComments(){
    return xpath_comments;
  }*/
  
}
