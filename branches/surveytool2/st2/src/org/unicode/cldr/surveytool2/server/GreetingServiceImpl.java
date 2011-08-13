package org.unicode.cldr.surveytool2.server;

import org.unicode.cldr.surveytool2.client.GreetingService;
import org.unicode.cldr.surveytool2.shared.*;
import com.google.gwt.user.server.rpc.RemoteServiceServlet;
import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.EntityNotFoundException;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.KeyFactory;

import org.unicode.cldr.surveytool2.server.CLDRDataSetUp;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStreamReader;
import java.util.Iterator;
import java.util.HashMap;

import javax.jdo.JDOHelper;
import javax.jdo.PersistenceManager;
import javax.jdo.PersistenceManagerFactory;
/*import com.google.appengine.api.users.User;
import com.google.appengine.api.users.UserService;
import com.google.appengine.api.users.UserServiceFactory;*/


/**
 * The server side implementation of the RPC service.
 */
@SuppressWarnings("serial")
public class GreetingServiceImpl extends RemoteServiceServlet implements GreetingService {
  private static final PersistenceManagerFactory pmf = JDOHelper
  .getPersistenceManagerFactory("transactions-optional");
  private static final PersistenceManager pm = pmf.getPersistenceManager();
  boolean resolved = false;
  boolean equivTest = false;
  public GreetingServiceImpl(){
  }
  public String greetServer(String regex) throws IllegalArgumentException {
    //Hack to get some test data in.
    //TODO: delete this once we leave testing.
    /*if(regex.equals("initializeMarker"))
    {
      CLDRDataSetUp theSetter = new CLDRDataSetUp();
      theSetter.setUp(pm);
      return "Oi, it worked!";
    }
    
    Key localeKey = KeyFactory.createKey("LocaleWrapper",regex);
    //  Verify that the input is valid.
    LocaleWrapper locale = getLocale(localeKey);
    if (locale==null){
      // If the input is not valid, let the user know.
      return "Sorry, but I don't recognize the locale name you gave me. Check for typos, maybe?";
    }
    */
    //return testReading();
    return CLDRDataSetUp.Test(pm, regex, equivTest, resolved);
  }
  /**
   * 
   * @param localeKey a key generated from a localeID string.
   * @return the entity corresponding to the localeKey if it exists. Else null.
   */
  private LocaleWrapper getLocale(Key localeKey){
    LocaleWrapper ret;
    try{
      ret = pm.getObjectById(LocaleWrapper.class,localeKey);
    }
    catch(Exception e){
      ret = null;
    }
    return ret;
  }
  
  /**
   * Tests to see if a sample locale file can be read. I use en_US
   * @return the first line of the locale file if it can be read, "Biteme" else
   */
  public String testReading()
  {
    FileInputStream fstream;
    try {
      fstream = new FileInputStream("common/main/en_US.xml");
    
    // Get the object of DataInputStream
    DataInputStream in = new DataInputStream(fstream);
    BufferedReader br = new BufferedReader(new InputStreamReader(in));
    String ret = br.readLine();
    System.out.println(ret);
    return ret;
    } catch (Exception e) {
      return "Biteme";
    }
  }
  public String setResolved(boolean resolving)
  {
    resolved = resolving;
    //For some reason, all calls to this class's methods must be asynchronous,
    //and I can't figure out how to do async with void, so we just return
    //garbage.
    return "y";
  }
  public String setEquivTest(boolean equiv)
  {
    equivTest = equiv;
    //See setResolved.
    return "y";
  }
  /* (non-Javadoc)
   * @see cldr.backend.client.GreetingService#loadData()
   */
  @Override
  public String loadData() {
    CLDRDataSetUp theSetter = new CLDRDataSetUp();
    theSetter.setUp(pm);
    return "GREAT SUCCESS";
  }
}
