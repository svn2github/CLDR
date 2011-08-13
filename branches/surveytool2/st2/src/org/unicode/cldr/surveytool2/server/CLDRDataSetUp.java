// Copyright 2011 Google Inc. All Rights Reserved.

package org.unicode.cldr.surveytool2.server;

import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.EntityNotFoundException;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.KeyFactory;

import org.unicode.cldr.surveytool2.server.PersistentXMLSource.CloudFactory;
import org.unicode.cldr.surveytool2.server.PersistentXMLSource.LocalFactory;
import org.unicode.cldr.util.CLDRFile;
import org.unicode.cldr.util.CLDRFile.DraftStatus;
import org.unicode.cldr.util.CLDRFile.Factory;
import org.unicode.cldr.util.CLDRFile.SimpleFactory;



import java.io.File;
import java.io.FileInputStream;
import java.util.Iterator;

import javax.jdo.JDOHelper;
import javax.jdo.PersistenceManager;
import javax.jdo.PersistenceManagerFactory;


//How in the devil am I supposed to run this class?

/**
 * @author anthonyef@google.com (Tony Fernandez)
 *
 */
public class CLDRDataSetUp {
  /*
   * The purpose of this class is to put data into the AppEngine datastore for
   * testing purposes. We'll start with faked data
   * 
   */
  //PersistenceManagerFactory PMF = new PersistenceManagerFactory();
  //public CLDRFile TOMBSTONE = LocalFactory.
  String locales[];
  public CLDRDataSetUp(){
    locales = new String[10];
    locales[0]="es_BO";
    locales[1]="fr";
    locales[2]="fr_CA";
    locales[3]="fr_SN";
    locales[4]="gaa";
    locales[5]="he";
    locales[6]="hi";
    locales[7]="hi_IN";
    locales[8]="ja";
    locales[9]="is";
  }
  public void setUp(PersistenceManager pm)
  {
   /* for(String s:locales)
    {
      LocaleWrapper loc = new LocaleWrapper(s);
      pm.makePersistent(loc);
    }*/
    LocalFactory factory = (LocalFactory) LocalFactory.make("common/main/", ".*", DraftStatus.unconfirmed);
    CLDRFile ref;
    LocaleWrapper loc;
    for(String locID: factory.getAvailable())
    {
      ref = factory.handleMake(locID, false, DraftStatus.unconfirmed);
      loc = new LocaleWrapper(ref);
      pm.makePersistent(loc);
      System.out.println(locID);
    }
    System.out.println("Data loaded and persisted.");
    /*CLDRFile engRef = factory.handleMake("en", false, DraftStatus.unconfirmed);
    LocaleWrapper locTest = new LocaleWrapper(engRef);
    pm.makePersistent(locTest);
    Factory fact = SimpleFactory.make("main",".*");
    CLDRFile englishRef = fact.make("en_US", false);*/
    
  }
  public static String verifyAvailableList()
  {
    Factory fact = LocalFactory.make("common/main/", ".*", DraftStatus.unconfirmed);
    return fact.getAvailable().toString();
  }
  public static String Test(PersistenceManager pm, String regex, boolean equivTest, boolean resolving)
  {
    LocalFactory factory =(LocalFactory) LocalFactory.make("common/main", regex, DraftStatus.unconfirmed);
    CloudFactory clFactory = (CloudFactory) CloudFactory.make("common/main", regex, DraftStatus.unconfirmed,pm);
    Iterator<String> localeItr = factory.getAvailable().iterator();
    String ret = "";
    String locale;
    if(equivTest) ret = "Everything matches!";
    while(localeItr.hasNext())
    {
      locale = localeItr.next();
      ret += locale+"\n";
      CLDRFile local = factory.handleMake(locale, resolving, DraftStatus.unconfirmed);
      //PersistentXMLSource pxs = new PersistentXMLSource(local.getLocaleID(), pm);
      CLDRFile cloud = clFactory.handleMake(locale, resolving, DraftStatus.unconfirmed);//new CLDRFile(pxs, resolving);
      Iterator<String> itr;
      if(equivTest)
      {
        itr = local.iterator();
        String key;
        while(itr.hasNext())
        {
          key = itr.next();
          if(!(local.getStringValue(key).equals(cloud.getStringValue(key))))
          {
            return "Equivalence value failure at locale: " + locale + " at xpath: " + key;
          }
          if(!(local.getFullXPath(key).equals(cloud.getFullXPath(key))))
          {
            return "FullXPath equivalence failure at locale: "+locale+" at xpath: "+key;
          }
        }
      }
      else
      {
        long start = System.currentTimeMillis();
        int localKeys = 0;
        for(int i = 0; i <10; ++i){
          itr  = local.iterator();
          String key;
          localKeys = 0;
          while(itr.hasNext())
          {
            key=itr.next();
            local.getDataSource().getValueAtDPath(key);
            localKeys++;
          }
        }
        System.out.println("Finished cloud");
        long elapsed = System.currentTimeMillis()-start;
        start = System.currentTimeMillis();
        int cloudKeys=0;
        for(int i = 0; i <10; ++i){
          itr  = cloud.iterator();
          String key;
          cloudKeys = 0;
          while(itr.hasNext())
          {
            key=itr.next();
            cloud.getDataSource().getValueAtDPath(key);
            cloudKeys++;
          }
        }
        long cloudElapsed = System.currentTimeMillis() - start;
        ret+= locale+": Local run time: "+Long.toString(elapsed)+" Over "+Integer.toString(localKeys)+
        "\nCloud run time: "+Long.toString(cloudElapsed) + " Over " + Integer.toString(cloudKeys);
      }
    }
    return ret;
    // 3215485(local) 139459 (cloud)
  }
  
}
