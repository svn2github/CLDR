//
//  WebContext.java
//  cldrtools
//
//  Created by Steven R. Loomis on 3/11/2005.
//  Copyright 2005 IBM. All rights reserved.
//
package org.unicode.cldr.web;

import org.w3c.dom.Document;
import java.io.*;
import java.util.*;
import com.ibm.icu.util.ULocale;

// servlet imports
import javax.servlet.*;
import javax.servlet.http.*;


public class WebContext {
// USER fields
    public Document doc[]= new Document[0];
    public ULocale locale = null;
    public String docLocale[] = new String[0];
    public String localeName = null; 
    public CookieSession session = null;

// private fields
    protected PrintWriter out = null;
    Hashtable form_data = null;
    String outQuery = null;
    TreeMap outQueryMap = new TreeMap();
    boolean dontCloseMe = false;

    HttpServletRequest request;
    HttpServletResponse response;

    // New constructor
    public WebContext(HttpServletRequest irq, HttpServletResponse irs) throws IOException {
         request = irq;
         response = irs;
        out = response.getWriter();
        dontCloseMe = true;
        
    }
    
    public WebContext(boolean fake)throws IOException  {
        dontCloseMe=false;
        out=openUTF8Writer(System.err);
        form_data = new Hashtable();
    }
    

    
    // copy c'tor
    public WebContext( WebContext other) {
        doc = other.doc;
        docLocale = other.docLocale;
        out = other.out;
        form_data = other.form_data;
        outQuery = other.outQuery;
        locale = other.locale;
        localeName = other.localeName;
        session = other.session;
        outQueryMap = (TreeMap)other.outQueryMap.clone();
        dontCloseMe = true;
        request = other.request;
        response = other.response;
    }
    
// More API

    /* 
     * return true if field y or n.
     * given default passed in def
     */
     
    boolean fieldBool(String x, boolean def) {
        if(field(x).length()>0) {
            if(field(x).charAt(0)=='t') {
                return true;
            } else {
                return false;
            }
        } else {
            return def;
        }
    }
    
    boolean prefBool(String x)
    {
        boolean ret = fieldBool(x, session.prefGetBool(x));
        session.prefPut(x, ret);
        return ret;
    }
    
    String field(String x) {
//        String res = (String)form_data.get(x);
        String res = request.getParameter(x);
         if(res == null) {       
//              System.err.println("[[ empty query string: " + x + "]]");
            res = "";   
        }
        
        byte asBytes[] = new byte[res.length()];
        boolean wasHigh = false;
        int n;
        for(n=0;n<res.length();n++) {
            asBytes[n] = (byte)(res.charAt(n)&0x00FF);
            //println(" n : " + (int)asBytes[n] + " .. ");
            if(asBytes[n]<0) {
                wasHigh = true;
            }
        }
        if(wasHigh == false) {
            return res; // no utf-8
        } else {
            //println("[ trying to decode on: " + res + "]");
        }
        try {
            res = new String(asBytes, "UTF-8");
        } catch(Throwable t) {
            return res;
        }
        
        return res;
    }
// query api
    void addQuery(String k, String v) {
        outQueryMap.put(k,v);
        if(outQuery == null) {
            outQuery = k + "=" + v;
        } else {
            outQuery = outQuery + "&" + k + "=" + v;
        }
    }
    
    void addQuery(String k, boolean v) {
        addQuery(k,v?"t":"f");
    }

    String url() {
        if(outQuery == null) {
            return base();
        } else {
            return base() + "?" + outQuery;
        }
    }
    
    String base() { 
        return request.getContextPath() + request.getServletPath();
    }
    
    public String context() { 
        return request.getContextPath();
    }

    public String context(String s) { 
        return request.getContextPath() + "/" + s;
    }
    
    void printUrlAsHiddenFields() {
        for(Iterator e = outQueryMap.keySet().iterator();e.hasNext();) {
            String k = e.next().toString();
            String v = outQueryMap.get(k).toString();
            println("<input type='hidden' name='" + k + "' value='" + v + "'/>");
        }
    }
    
    /**
     * return the IP of the remote user.
     */
    String userIP() {
        return request.getRemoteAddr();
    }

    /**
     * return the hostname of the web server
     */
    String serverName() {
        return request.getServerName();
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
// doc api
    public static String getParent(Object l) {
        String locale = l.toString();
        int pos = locale.lastIndexOf('_');
        if (pos >= 0) {
            return locale.substring(0,pos);
        }
        if (!locale.equals("root")) return "root";
        return null;
    }

    void setLocale(ULocale l) {
        locale = l;
        String parents = null;
        Vector localesVector = new Vector();
        Vector docsVector = new Vector();
        parents = l.toString();
        do {
            try {
                Document d = SurveyMain.fetchDoc(parents);
                localesVector.add(parents);
                docsVector.add(d);
            } catch(Throwable t) {
                println("Error fetching " + parents + "<br/>");
                // error is shown elsewhere.
            }
            parents = getParent(parents);
        } while(parents != null);
        doc = (Document[])docsVector.toArray(doc);
        docLocale = (String[])localesVector.toArray(docLocale);
    }
        
// locale hash api
    // get the hashtable of modified locales

    Hashtable localeHash = null;
    public Hashtable getLocaleHash() {
        if(session == null) {
            throw new RuntimeException("Session is null in WebContext.getLocaleHash()!");
        }
        if(localeHash == null) {
            Hashtable localesHash = session.getLocales();
            if(localesHash == null) { return null; }
            localeHash = (Hashtable)localesHash.get(locale);
        }
        return localeHash;
    }
    
    public Object getByLocale(String key) {
        Hashtable localeHash = getLocaleHash();
        if(localeHash != null) {
            return localeHash.get(key);
        }
        return null;
    }

    public void putByLocale(String key, Object value) {
        Hashtable localeHash = getLocaleHash();
        if(localeHash == null) {
            localeHash = new Hashtable();
            session.getLocales().put(locale,localeHash);
        }
        localeHash.put(key, value);
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
    
    public void printHelpLink(String what)  {
        printHelpLink(what, "Help");
    }
    
    public void printHelpLink(String what, String title)
    {
        println("(<a href=\"" + SurveyMain.CLDR_HELP_LINK + what + "\">" + title +"</a>)");
    }
}
