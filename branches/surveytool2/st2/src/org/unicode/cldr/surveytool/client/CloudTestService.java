package org.unicode.cldr.surveytool.client;

import com.google.gwt.user.client.rpc.RemoteService;
import com.google.gwt.user.client.rpc.RemoteServiceRelativePath;

/**
 * The client side stub for the RPC service.
 */
@RemoteServiceRelativePath("cloudtest")
public interface CloudTestService extends RemoteService {
  String greetServer(String name, String keyRegex, String valRegex) throws IllegalArgumentException;
  String setResolved(boolean resolve);
  String setTestType(int type);
  String loadData(int i);
}

