//
//  WebContext.java
//  cldrtools
//
//  Created by Steven R. Loomis on 3/11/2005.
//  Copyright 2005 IBM. All rights reserved.
//
package org.unicode.cldr.web;

import java.io.*;
import java.util.*;
import com.ibm.icu.util.ULocale;

public class WebContext {
// USER fields
    public org.w3c.dom.Document doc= null;
    public ULocale locale = null;
    public String localeName = null; 

// private fields
    protected PrintWriter out = null;
    Hashtable form_data = null;
    String baseURL = cgi_lib.MyTinyURL();
    String outQuery = null;
    boolean dontCloseMe = false;

    // New constructor
    public WebContext(OutputStream ostr) throws IOException {
        out = openUTF8Writer(ostr);
        dontCloseMe = false;
        
        // set up cgi
      form_data = cgi_lib.ReadParse(System.in);
      if (cgi_lib.MethGet()) {
//          System.out.println("REQUEST_METHOD=GET");
          }
      if (cgi_lib.MethPost()) { 
//          System.out.println("REQUEST_METHOD=POST");
       }
        
    }
    
    // copy c'tor
    public WebContext( WebContext other) {
        doc = other.doc;
        out = other.out;
        form_data = other.form_data;
        baseURL = other.baseURL;
        outQuery = other.outQuery;
        locale = other.locale;
        localeName = other.localeName;

        dontCloseMe = true;
    }
    
// More API
    String field(String x) {
        String res = (String)form_data.get(x);
         if(res == null) {       
              System.out.println("[[ empty query string: " + x + "]]");
            res = "";   
        }
        return res;
    }
// query api
    void addQuery(String k, String v) {
        if(outQuery == null) {
            outQuery = k + "=" + v;
        } else {
            outQuery = outQuery + "&" + k + "=" + v;
        }
    }

    String url() {
        if(outQuery == null) {
            return baseURL;
        } else {
            return baseURL + "?" + outQuery;
        }
    }
    
    String base() { 
        return baseURL;
    }
// print api
    final void println(String s) {
        out.println(s);
    }
    
    final void print(String s) {
        out.print(s);
    }
    
    void close() {
        if(!dontCloseMe) {
            out.close();
            out = null;
        } else {
            // ? 
        }
    }

// Internal Utils

    // from BagFormatter
    private static PrintWriter openUTF8Writer(OutputStream out) throws IOException {
        return openWriter(out,"UTF-8");
    }
    private static PrintWriter openWriter(OutputStream out, String encoding) throws IOException {
        return new PrintWriter(
            new BufferedWriter(
                new OutputStreamWriter(
                     out,
                    encoding),
                4*1024));       
    }
}
