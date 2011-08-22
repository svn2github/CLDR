package org.unicode.cldr.surveytool.client;

import com.google.gwt.user.client.rpc.AsyncCallback;

/**
 * The async counterpart of <code>GreetingService</code>.
 */
public interface CloudTestServiceAsync {
  void greetServer(String input, String keyRegex, String valRegex, AsyncCallback<String> callback) throws IllegalArgumentException;
  void setResolved(boolean resolve, AsyncCallback<String> callback);
  void setTestType(int type, AsyncCallback<String> callback);
  void loadData(int i, AsyncCallback<String> callback);
}
