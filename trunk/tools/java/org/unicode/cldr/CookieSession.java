/* Copyright (C) 2004, International Business Machines Corporation and   *
 * others. All Rights Reserved.                                               */
//
//  CookieJar.java
//  cldrtools
//
//  Created by Steven R. Loomis on 3/17/2005.
//  Copyright 2005 IBM. All rights reserved.
//

package org.unicode.cldr.web;

import java.io.*;
import java.util.*;

// DOM imports
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.NodeList;

import com.ibm.icu.text.DecimalFormat;
import com.ibm.icu.text.Normalizer;
import com.ibm.icu.util.ULocale;
import com.ibm.icu.lang.UCharacter;

import org.unicode.cldr.util.*;
import org.unicode.cldr.icu.*;


import com.fastcgi.FCGIInterface;
import com.fastcgi.FCGIGlobalDefs;
import com.ibm.icu.lang.UCharacter;
import org.html.*;
import org.html.utility.*;
import org.html.table.*;


public class CookieSession {
    public String id;
    public long last;
    public Hashtable stuff = new Hashtable();
    
    private CookieSession(String s) {
        id = s;
    }
    
    static Hashtable gHash = new Hashtable();
    
    public static CookieSession retrieve(String s) {
        CookieSession c = (CookieSession)gHash.get(s);
        if(c != null) {
            c.touch();
        }
        return c;
    }
    
    public CookieSession() {
        id = newId();
        touch();
        gHash.put(id,this);
    }
    
    protected void touch() {
        last = System.currentTimeMillis();
    }
    
    protected long age() {
        return (System.currentTimeMillis()-last);
    }
    
    static int n = 4000;
    static String j = cheapEncode(System.currentTimeMillis());
    protected String newId() {  
        return cheapEncode(n++)+"|" + j;
    }
    
    
    // convenience functions
    Object get(String key) { 
        return stuff.get(key);
    }
    
    void put(String key, Object value) {
        stuff.put(key,value);
    }
    
    public Hashtable getLocales() {
        Hashtable l = (Hashtable)get("locales");
        if(l == null) {
            l = new Hashtable();
            put("locales",l);
        }
        return l;
    }
    
    static String cheapEncode(long l) {
        String out = "";
        if(l < 0) {
            l = 0 - l;
        } else if (l == 0) {
            return "0";
        }
        while(l > 0) {
            char c = (char)(l%(26));
            char o;
            c += 'a';
            o = c;
            out = out + o;
            l /= 26;
        }
        return out;
    }
}
