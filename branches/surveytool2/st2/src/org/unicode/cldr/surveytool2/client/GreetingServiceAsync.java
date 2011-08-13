package org.unicode.cldr.surveytool2.client;

import com.google.gwt.user.client.rpc.AsyncCallback;

/**
 * The async counterpart of <code>GreetingService</code>.
 */
public interface GreetingServiceAsync {
  boolean resolved = false;
  boolean equivTest = false;
  void greetServer(String input, AsyncCallback<String> callback) throws IllegalArgumentException;
  void setResolved(boolean resolve, AsyncCallback<String> callback);
  void setEquivTest(boolean equiv, AsyncCallback<String> callback);
  void loadData(AsyncCallback<String> callback);
}
