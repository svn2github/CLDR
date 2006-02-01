//
//  XPathTable.java
//  fourjay
//
//  Created by Steven R. Loomis on 20/10/2005.
//  Copyright 2005 IBM. All rights reserved.
//
//

package org.unicode.cldr.web;

import java.io.*;
import java.util.*;

import java.sql.Connection;
import java.sql.Statement;
import java.sql.SQLException;
import java.sql.ResultSet;

import org.unicode.cldr.util.XPathParts;
import org.unicode.cldr.icu.LDMLConstants;

public class XPathTable {
    private static java.util.logging.Logger logger;
    
    public static final String CLDR_XPATHS = "cldr_xpaths";
    
    /** 
     * Called by SM to create the reg
     * @param xlogger the logger to use
     * @param ourConn the conn to use
     * @param isNew  true if should CREATE TABLEs
     */
    public static XPathTable createTable(java.util.logging.Logger xlogger, Connection ourConn, boolean isNew) throws SQLException {
        XPathTable reg = new XPathTable(xlogger,ourConn);
        if(isNew) {
            reg.setupDB();
        }
        reg.myinit();
        logger.info("XPathTable DB: Created.");
        return reg;
    }
    
    /**
     * Called by SM to shutdown
     */
    public void shutdownDB() throws SQLException {
        synchronized(conn) {
            conn.close();
            conn = null;
        }
    }

    /**
     * internal - called to setup db
     */
    private void setupDB() throws SQLException
    {
        logger.info("XPathTable DB: initializing...");
        synchronized(conn) {
            Statement s = conn.createStatement();
            s.execute("create table " + CLDR_XPATHS + "(id INT NOT NULL GENERATED ALWAYS AS IDENTITY, " +
                                                    "xpath varchar(1024) not null, " +
                                                    "unique(xpath))");
            s.execute("CREATE UNIQUE INDEX unique_xpath on " + CLDR_XPATHS +"(xpath)");
            s.execute("CREATE INDEX "+CLDR_XPATHS+"_xpath on " + CLDR_XPATHS +"(xpath)");
            s.execute("CREATE INDEX "+CLDR_XPATHS+"_id on " + CLDR_XPATHS +"(id)");
            s.close();
            conn.commit();
        }
    }
    
    Connection conn = null;
    SurveyMain sm = null;
    public Hashtable xstringHash = new Hashtable();  // public for statistics only
    public Hashtable idToString = new Hashtable();  // public for statistics only
    public Hashtable stringToId = new Hashtable();  // public for statistics only

    java.sql.PreparedStatement insertStmt = null;
    java.sql.PreparedStatement queryStmt = null;
    java.sql.PreparedStatement queryIdStmt = null;
    
    public String statistics() {
        return "xstringHash has " + xstringHash.size() + " items.  DB: " + stat_dbAdd +"add/" + stat_dbFetch +
                "fetch/" + stat_allAdds +"total.";
    }
    
    private static int stat_dbAdd = 0;
    private static int stat_dbFetch = 0;
    private static int stat_allAdds = 0;

    public XPathTable(java.util.logging.Logger xlogger, Connection ourConn) {
        logger = xlogger;
        conn = ourConn;
    }
    
    public void myinit() throws SQLException {
        synchronized(conn) {
            insertStmt = conn.prepareStatement("INSERT INTO " + CLDR_XPATHS +" (xpath ) " + 
                                            " values (?)");
            queryStmt = conn.prepareStatement("SELECT id FROM " + CLDR_XPATHS + "   " + 
                                        " where XPATH=?");
            queryIdStmt = conn.prepareStatement("SELECT XPATH FROM " + CLDR_XPATHS + "   " + 
                                        " where ID=?");
        }
    }

    /**
     * Bottleneck for adding xpaths
     * @return the xpath's id (as an Integer)
     */
    private Integer addXpath(String xpath)
    {
        synchronized(conn) {
            try {
                    queryStmt.setString(1,xpath);
                // First, try to query it back from the DB.
                    ResultSet rs = queryStmt.executeQuery();                
                    if(!rs.next()) {
                        insertStmt.setString(1, xpath);
                        insertStmt.execute();
                        conn.commit();
                        // TODO: Shouldn't there be a way to get the new row's id back??
    //                    logger.info("xpt: added " + xpath);
                        rs = queryStmt.executeQuery();
                        if(!rs.next()) {
                            logger.severe("Couldn't retrieve newly added xpath " + xpath);
                        } else {
                            stat_dbAdd++;
                        }
                    } else {
                        stat_dbFetch++;
                    }
                                    
                    int id = rs.getInt(1);
                    Integer nid = new Integer(id);
                    idToString.put(nid,xpath);
                    stringToId.put(xpath,nid);
    //                logger.info("Mapped " + id + " back to " + xpath);
                    rs.close();
                    stat_allAdds++;
                    return nid;
            } catch(SQLException sqe) {
                logger.severe("XPathTable: Failed in addXPath("+xpath+"): " + SurveyMain.unchainSqlException(sqe));
    //            sm.busted("XPathTable: Failed in addXPath: " + SurveyMain.unchainSqlException(sqe));
            }
        }
        return null; // an exception occured.
    }
    
    /** 
     * needs a new name..
     * This uses the string pool and also adds it to the table
     */
    final String poolx(String x) {
        if(x==null) {
            return null;
        }
        String y = (String)xstringHash.get(x);
        if(y==null) {
            xstringHash.put(x,x);
            
            addXpath(x);
            
            return x;
        } else {
            return y;
        }
    }
    
    private String fetchByID(int id) {
        synchronized(conn) {
            try {
                    queryIdStmt.setInt(1,id);
                // First, try to query it back from the DB.
                    ResultSet rs = queryIdStmt.executeQuery();                
                    if(!rs.next()) {
                        rs.close();
                        logger.severe("XPath: no xpath for ID " + id);
                        return null;
                    }
                                    
                    String str = rs.getString(1);
                    Integer nid = new Integer(id);
                    rs.close();
                    return poolx(str); // adds to idtostring and stringtoid
                    // TODO optimize
            } catch(SQLException sqe) {
                logger.severe("XPathTable: Failed ingetByID (ID: "+ id+"): " + SurveyMain.unchainSqlException(sqe) );
    //            sm.busted("XPathTable: Failed in addXPath: " + SurveyMain.unchainSqlException(sqe));
                return null;
            }
        }
    }
    
    /**
     * API for get by ID 
     */
    public final String getById(int id) {
        String s = (String)idToString.get(new Integer(id));
        if(s!=null) {
            return s;
        }
        return fetchByID(id);
    }
    
    /**
     * API for get by string
     */
    public final int getByXpath(String xpath) {
        Integer nid = (Integer)stringToId.get(xpath);
        if(nid == null) {
            synchronized(conn) {
                nid = (Integer)stringToId.get(xpath); // double check
                if(nid == null) {
                    // OK, go get it
                    nid = addXpath(xpath);
                }
            }
        }
        if(nid != null) {
            return nid.intValue();
        }
        // Failing it.
        logger.severe("Coudln't get xpath for " + xpath);
        throw new InternalError("Couldn't get xpath for " + xpath);
    }
    
    public String pathToTinyXpath(String path) {
        return pathToTinyXpath(path, new XPathParts(null,null));
    }
    
    public String pathToTinyXpath(String path, XPathParts xpp) {
        typeFromPathToTinyXpath(path, xpp);
        return xpp.toString();
    }
    
    public String typeFromPathToTinyXpath(String path, XPathParts xpp) {
        return whatFromPathToTinyXpath(path, xpp, LDMLConstants.TYPE);
    }
    public String altFromPathToTinyXpath(String path, XPathParts xpp) {
        return whatFromPathToTinyXpath(path, xpp, LDMLConstants.ALT);
    }
    
    public static String removeAlt(String path) {
        return removeAlt(path, new XPathParts(null,null));
    }
    public static String removeAlt(String path, XPathParts xpp) {
        xpp.clear();
        xpp.initialize(path);
        Map lastAtts = xpp.getAttributes(-1);
        lastAtts.remove(LDMLConstants.ALT);
        return xpp.toString();
    }
    
    public String whatFromPathToTinyXpath(String path, XPathParts xpp, String what) {
        xpp.clear();
        xpp.initialize(path);
        Map lastAtts = xpp.getAttributes(-1);
        String type = (String)lastAtts.remove(what);
        lastAtts.remove(LDMLConstants.ALT);
        lastAtts.remove(LDMLConstants.TYPE);
        lastAtts.remove(LDMLConstants.DRAFT);
        lastAtts.remove(LDMLConstants.REFERENCES);
        if((type == null) && (path.indexOf(what)>=0)) try {
            // less common case - type isn't the last
            for(int n=-2;(type==null);n--) {
                lastAtts = xpp.getAttributes(n);
                if(lastAtts != null) {
                    type = (String)lastAtts.remove(what);
                }
            }
        } catch(ArrayIndexOutOfBoundsException aioobe) {
            // means we ran out of elements.
        }
        return type;
    }
    
    // proposed-u4-1
    public static final String PROPOSED_U = LDMLConstants.PROPOSED+"-u";
    public static final String PROPOSED_SEP = "-";
    public static final String altProposedPrefix(int userid) {
        return PROPOSED_U + userid + PROPOSED_SEP;
    }
    /**
     * parse an alt-proposed, such as "proposed-u4-1" into a userid (4, in this case).  
     * returns -1 if altProposed is null or in any way malformed.
     */
    public static final int altProposedToUserid(String altProposed) {
//        System.err.println("AP: " + altProposed);
        if((altProposed==null) || !altProposed.startsWith(PROPOSED_U)) {
//        System.err.println("AP: null");
            return -1;
        }
        String idStr = altProposed.substring(PROPOSED_U.length());
//        System.err.println("AP: idStr = " + idStr);
        int dash;
        if(-1 != (dash=idStr.indexOf(PROPOSED_SEP))) {
//        System.err.println("AP: dash = " + dash);
            idStr = idStr.substring(0,dash);
//        System.err.println("AP: idStr2 = " + idStr);
        }
        try {
            return Integer.parseInt(idStr);
        } catch(Throwable t) {
//        System.err.println("err on parse = " + t.toString());
//        t.printStackTrace();
            return -1;
        }
    }

}
