// Copyright 2011 Google Inc. All Rights Reserved.

package com.ibm.icu.impl;

/**
 * @author anthonyef@google.com (Tony Fernandez)
 *
 */
public class ICULogger {
  private boolean logging;
  public ICULogger(String name){logging=false;}
  private static enum LOGGER_STATUS { ON, OFF, NULL };
  public static ICULogger getICULogger(String name){
    return new ICULogger(name);
  }
  public static ICULogger getICULogger(String name, String resourceBundleName)
  {
    return new ICULogger(name);
  }
  public boolean isLoggingOn()
  {
    return logging;
  }
  public void turnOnLogging()
  {
    logging=true;
  }
  public void turnOffLogging()
  {
    logging=false;
  }
}
