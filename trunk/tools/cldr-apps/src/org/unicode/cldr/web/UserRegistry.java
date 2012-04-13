//  UserRegistry.java
//
//  Created by Steven R. Loomis on 14/10/2005.
//  Copyright 2005-2012 IBM. All rights reserved.
//

package org.unicode.cldr.web;


import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.unicode.cldr.util.CLDRLocale;
import org.unicode.cldr.util.VoteResolver;
import org.unicode.cldr.util.VoteResolver.Organization;
import org.unicode.cldr.util.VoteResolver.VoterInfo;
import org.unicode.cldr.web.UserRegistry.InfoType;

import com.ibm.icu.dev.test.util.ElapsedTimer;
import com.ibm.icu.lang.UCharacter;
import com.ibm.icu.util.ULocale;

/**
 * This class represents the list of all registered users.  It contains an inner class, UserRegistry.User, 
 * which represents an individual user.
 * @see UserRegistry.User
 * @see OldUserRegistry
 **/
public class UserRegistry {
    public interface UserChangedListener {
    	public void handleUserChanged(User u);
	}

    private List<UserChangedListener> listeners = new LinkedList<UserChangedListener>();
    public synchronized void addListener(UserChangedListener l) {
    	listeners.add(l);
    }
    private  synchronized void notify(User u) {
    	for(UserChangedListener l : listeners) {
    		l.handleUserChanged(u);
    	}
    }
	private static java.util.logging.Logger logger;
    // user levels
    public static final int ADMIN   = VoteResolver.Level.admin.getSTLevel();  /** Administrator **/
    public static final int TC      = VoteResolver.Level.tc.getSTLevel();  /** Technical Committee **/
    public static final int EXPERT  = VoteResolver.Level.expert.getSTLevel();  /** Expert Vetter **/
    public static final int VETTER  = VoteResolver.Level.vetter.getSTLevel();  /** regular Vetter **/
    public static final int STREET  = VoteResolver.Level.street.getSTLevel(); /** Guest Vetter **/
    public static final int LOCKED  = VoteResolver.Level.locked.getSTLevel();/** Locked user - can't login **/
	
	public static final int LIMIT_LEVEL = 10000;  /** max level **/
	public static final int NO_LEVEL  = -1;  /** min level **/
    
    public static final String FOR_ADDING= "(for adding)"; /** special "IP" value referring to a user being added **/ 
    private static final String INTERNAL = "INTERNAL";
    

    /**
     * List of all user levels - for UI presentation
     **/
    public static final int ALL_LEVELS[] = {
        ADMIN, TC, EXPERT, VETTER, STREET, LOCKED };
    
    /**
     * get a level as a string - presentation form
     **/
    public static String levelToStr(WebContext ctx, int level) {
        return level + ": (" + levelAsStr(level) + ")";
    }
    /**
     * get just the raw level as a string
     */
    public static String levelAsStr(int level) {
        VoteResolver.Level l = VoteResolver.Level.fromSTLevel(level);
        if(l==null) {
            return "??";
        } else {
            return l.name().toUpperCase();
        }
    }
    
	/**
     * The name of the user sql database
     */
    public static final String CLDR_USERS = "cldr_users";
    public static final String CLDR_INTEREST = "cldr_interest";
    
    
    
    public static final String SQL_insertStmt = "INSERT INTO " + CLDR_USERS + "(userlevel,name,org,email,password,locales,lastlogin) " +
    "VALUES(?,?,?,?,?,?,NULL)";
    public static final String SQL_queryStmt_FRO = "SELECT id,name,userlevel,org,locales,intlocs,lastlogin from " + CLDR_USERS +" where email=? AND password=?"; 
    	//            ResultSet.TYPE_FORWARD_ONLY,ResultSet.CONCUR_READ_ONLY);
    public static final String SQL_queryIdStmt_FRO = "SELECT name,org,email,userlevel,intlocs,locales,lastlogin,password from " + CLDR_USERS +" where id=?"; 
    	//            ResultSet.TYPE_FORWARD_ONLY,ResultSet.CONCUR_READ_ONLY);
    public static final String SQL_queryEmailStmt_FRO = "SELECT id,name,userlevel,org,locales,intlocs,lastlogin,password from " + CLDR_USERS +" where email=?"; 
    	//            ResultSet.TYPE_FORWARD_ONLY,ResultSet.CONCUR_READ_ONLY);
    public static final String SQL_touchStmt = "UPDATE "+CLDR_USERS+" set lastlogin=CURRENT_TIMESTAMP where id=?";
    public static final String SQL_removeIntLoc = "DELETE FROM "+CLDR_INTEREST+" WHERE uid=?";
    public static final String SQL_updateIntLoc = "INSERT INTO " + CLDR_INTEREST + " (uid,forum) VALUES(?,?)";
    
    private UserSettingsData userSettings;
    
    /**
     * This nested class is the representation of an individual user. 
     * It may not have all fields filled out, if it is simply from the cache.
     */
    public class User implements Comparable<User> {
        public int    id;  // id number
        public int    userlevel=LOCKED;    // user level
        public String password;       // password
        public String email;    // 
        public String org;  // organization
        public String name;     // full name
        public java.sql.Timestamp last_connect;
        public String locales;
        public String intlocs = null;
        public String ip;
        
        private UserSettings settings;
        
        /**
         * @deprecated may not use
         */
        private User() {
            this.id = -1;
            settings = userSettings.getSettings(id); // may not use settings.
        }
        public User(int id) {
           this.id = id;
           settings = userSettings.getSettings(id);
        }
        /**
         * Get a settings object for use with this user.
         * @return
         */
        public UserSettings settings() {
            return settings;
        }
        public void touch()  {
            UserRegistry.this.touch(id);
        }
        public boolean equals(Object other ) {
            if(!(other instanceof User)) {
                return false;
            }
            User u = (User)other;
            return(u.id == id);
        }
        public void printPasswordLink(WebContext ctx) {
            UserRegistry.printPasswordLink(ctx, email, password);
        }
        public String toString() {
            return email + "("+org+")-" + levelAsStr(userlevel)+"#"+userlevel + " - " + name;
        }
        public String toHtml(User forUser) {
            if(forUser==null||!userIsTC(forUser)) {
                return "("+org+"#"+id+")";
            } else {
                return "<a href='mailto:"+email+"'>"+name+"</a>-"+levelAsStr(userlevel).toLowerCase();
            }
        }
        public String toString(User forUser) {
            if(forUser==null||!userIsTC(forUser)) {
                return "("+org+"#"+id+")";
            } else {
                return email + "("+org+")-" + levelAsStr(userlevel)+"#"+userlevel + " - " + name;
            }
        }
        public int hashCode() { 
            return id;
        }
        /**
         * is the user interested in this locale?
         */
        public boolean interestedIn(CLDRLocale locale) {
            return UserRegistry.localeMatchesLocaleList(intlocs, locale);
        }
        
        /** 
         * List of interest groups the user is interested in.
         * @return list of locales, or null for ALL locales, or a 0-length list for NO locales.
         */
        public String[] getInterestList() {
            if(userIsExpert(this)) {
                if(intlocs == null || intlocs.length()==0) {
                    return null;
                } else {
                    if(intlocs.equals("none")) {
                        return new String[0];
                    }
                    return tokenizeLocale(intlocs);
                }
            } else if(userIsStreet(this)) {
                return tokenizeLocale(locales);
            } else {
                return new String[0];
            }
        }

        public final boolean userIsSpecialForCLDR15(CLDRLocale locale) {
            return false;
        }
//            if(locale.equals("be")||locale.startsWith("be_")) {
//                if( ( id == 315 /* V. P. */ ) || (id == 8 /* S. M. */ ) ) {
//                    return true;
//                } else {
//                    return false;
//                }
//            } else if ( id == 7 ) {  // Erkki
//                return true;
//            } else {
//                return false;
//            }
        
        /**
         * Convert this User to a VoteREsolver.VoterInfo. Not cached.
         */
        private VoterInfo createVoterInfo() {
            //VoterInfo(Organization.google, Level.vetter, &quot;J. Smith&quot;) },
            VoteResolver.Organization o = this.computeVROrganization();
            VoteResolver.Level l = this.computeVRLevel();
            Set<String> localesSet = new HashSet<String>();
            for(String s: tokenizeLocale(locales)) {
                localesSet.add(s);
            }
            VoterInfo v = new VoterInfo(o, l, this.name, localesSet);
            return v;
        }
        
        /**
         * Return the value of this voter info, out of the cache
         */
        public VoterInfo voterInfo() {
            return getVoterToInfo(id);
        }
        
        /**
         * Convert the level to a VoteResolver.Level
         * @return VoteResolver.Level format
         */
        public VoteResolver.Level computeVRLevel() {
            return VoteResolver.Level.fromSTLevel(this.userlevel);
       }
        
        /**
         * Convert the Organization into a VoteResolver.Organization
         * @return VoteResolver.Organization format
         */
        private VoteResolver.Organization computeVROrganization() {
        	return UserRegistry.computeVROrganization(this.org);
        }
        
        private String voterOrg = null;
        
        /**
         * Convenience function for returning the "VoteResult friendly" organization.
         */
        public String voterOrg() {
            if(voterOrg==null) {
                voterOrg=voterInfo().getOrganization().name();
            }
            return voterOrg;
        }
        /**
         * Is this user an administrator 'over' this user?  Always true if admin, orif TC in same org. 
         * @param other
         */
        public boolean isAdminFor(User other) {
            boolean adminOrRelevantTc = UserRegistry.userIsAdmin(this) || 
                        ( UserRegistry.userIsTC(this) && (other!=null) && this.org.equals(other.org)); 
            return adminOrRelevantTc;
        }
        @Override
        public int compareTo(User other) {
            if(other==this || other.equals(this)) return 0;
            if(this.id < other.id){
                return -1;
            } else {
                return 1;
            }
        }
    }
        
    public static void printPasswordLink(WebContext ctx, String email, String password) {
        ctx.println("<a href='" + ctx.base() + "?email=" + email + "&amp;uid=" + password + "'>Login for " + 
            email + "</a>");
    }
        
    public static Organization computeVROrganization(String org) {
        VoteResolver.Organization o = null;
        try {
            String arg = org
                            .replaceAll("Utilika Foundation", "utilika")
                            .replaceAll("Government of Pakistan - National Language Authority", "pakistan")
			.replaceAll("ICT Agency of Sri Lanka", "srilanka")
                            .toLowerCase().replaceAll("[.-]", "_");
            o = VoteResolver.Organization.valueOf(arg);
        } catch(IllegalArgumentException iae) {
            o = VoteResolver.Organization.guest;
            System.err.println("Unknown organization: "+org);
        }
        return o;
	}


    /** 
     * Called by SM to create the reg
     * @param xlogger the logger to use
     * @param ourConn the conn to use
     */
    public static UserRegistry createRegistry(java.util.logging.Logger xlogger, SurveyMain theSm) 
      throws SQLException
    {
        sm = theSm;
        UserRegistry reg = new UserRegistry(xlogger);
        reg.setupDB();
//        logger.info("UserRegistry DB: created");
        return reg;
    }
    
    /**
     * Called by SM to shutdown
     */
    public void shutdownDB() throws SQLException {
    //    DBUtils.closeDBConnection(conn);
    }

    /**
     * internal - called to setup db
     */
    private void setupDB() throws SQLException
    {
    	// must be set up first.
        userSettings = UserSettingsData.getInstance(sm);
        
        String sql = null;
        Connection conn = DBUtils.getInstance().getDBConnection();
        try{
            synchronized(conn) {
    //            logger.info("UserRegistry DB: initializing...");
                boolean hadUserTable = DBUtils.hasTable(conn,CLDR_USERS);
                if(!hadUserTable) {
                    Statement s = conn.createStatement();
                
                    sql = ("create table " + CLDR_USERS + "(id INT NOT NULL "+DBUtils.DB_SQL_IDENTITY+", " +
                                                            "userlevel int not null, " +
                                                            "name "+DBUtils.DB_SQL_UNICODE+" not null, " +
                                                            "email varchar(128) not null UNIQUE, " +
                                                            "org varchar(256) not null, " +
                                                            "password varchar(100) not null, " +
                                                            "audit varchar(1024) , " +
                                                            "locales varchar(1024) , " +
                                                            //"prefs varchar(1024) , " + /* deprecated Dec 2010. Not used anywhere */
                                                            "intlocs varchar(1024) , " + // added apr 2006: ALTER table CLDR_USERS ADD COLUMN intlocs VARCHAR(1024)
                                                            "lastlogin " + DBUtils.DB_SQL_TIMESTAMP0 + // added may 2006:  alter table CLDR_USERS ADD COLUMN lastlogin TIMESTAMP
                                                            (!DBUtils.db_Mysql?",primary key(id)":"") +  ")"); 
                    s.execute(sql);
                    sql=("INSERT INTO " + CLDR_USERS + "(userlevel,name,org,email,password) " +
                                                            "VALUES(" + ADMIN +"," + 
                                                            "'admin'," + 
                                                            "'SurveyTool'," +
                                                            "'admin@'," +
                                                            "'" + sm.vap +"')");
                    s.execute(sql);
                    sql = null;
                    SurveyLog.debug("DB: added user Admin");
                    
                    s.close();
                    conn.commit();
                } else if(!DBUtils.db_Derby) {
                    /* update table to DATETIME instead of TIMESTAMP */
                    Statement s = conn.createStatement();
                    sql = "alter table cldr_users change lastlogin lastlogin DATETIME";
                    s.execute(sql);
                    s.close();
                    conn.commit();
                }
    
                boolean hadInterestTable = DBUtils.hasTable(conn,CLDR_INTEREST);
                if(!hadInterestTable) {
                    Statement s = conn.createStatement();
                
                    sql=("create table " + CLDR_INTEREST + " (uid INT NOT NULL , " +
                                                            "forum  varchar(256) not null " +
                                                            ")"); 
                    s.execute(sql);
                    sql = "CREATE  INDEX " + CLDR_INTEREST + "_id_loc ON " + CLDR_INTEREST + " (uid) ";
                    s.execute(sql); 
                    sql = "CREATE  INDEX " + CLDR_INTEREST + "_id_for ON " + CLDR_INTEREST + " (forum) ";
                    s.execute(sql); 
                    SurveyLog.debug("DB: created "+CLDR_INTEREST);
                    sql=null;
                    s.close();
                    conn.commit();
                }
                
                myinit(); // initialize the prepared statements
                
                if(!hadInterestTable) {
                    setupIntLocs();  // set up user -> interest table mapping
                }
    
            }
        } catch(SQLException se) {
            se.printStackTrace();
            System.err.println("SQL err: " + DBUtils.unchainSqlException(se));
            System.err.println("Last SQL run: " + sql);
            throw se;
        } finally {
        	DBUtils.close(conn);
        }
    }
    
    /**
     * ID# of the user
     */
    static final int ADMIN_ID = 1;
    
    /**
     * special ID meaning 'all'
     */
    static final int ALL_ID = -1;
	
    private void myinit() throws SQLException {}
    
    /**
     * info = name/email/org
     * immutable info, keep it in a separate list for quick lookup.
     */
    public final static int CHUNKSIZE = 128;
    int arraySize = 0;
    UserRegistry.User infoArray[] = new UserRegistry.User[arraySize];
    
    /**
     * Mark user as modified
     * @param id
     */
    void userModified(int id) {
        synchronized(infoArray) {
            try {
                infoArray[id] = null;
            } catch(IndexOutOfBoundsException ioob) {
                // nothing to do
            }
        }
        userModified(); // do this if any users are modified
    }

    
    /**
     * Mark the UserRegistry as changed, purging the VoterInfo map
     * @see #getVoterToInfo()
     */
    private void userModified() {
        voterInfo = null;
    }

    /**
     * Get the singleton user for this ID. 
     * @param id
     * @return singleton, or null if not found/invalid
     */
    public UserRegistry.User getInfo(int id) {
        if(id<0) {
            return null;
        }
//    System.err.println("Fetching info for id " + id);
        synchronized(infoArray) {
            User ret = null;
            try {
//    System.err.println("attempting array lookup for id " + id);
                //ret = (User)infoArray.get(id);
                ret = infoArray[id];
            } catch(IndexOutOfBoundsException ioob) {
//    System.err.println("Index out of bounds for id " + id + " - " + ioob);
                ret = null; // not found
            }
            
            if(ret == null) { // synchronized(conn) {
//    System.err.println("go fish for id " + id);
//          queryIdStmt = conn.prepareStatement("SELECT name,org,email from " + CLDR_USERS +" where id=?");
                ResultSet rs = null;
                PreparedStatement pstmt = null;
                Connection conn = DBUtils.getInstance().getDBConnection();
                try{ 
                    pstmt = DBUtils.prepareForwardReadOnly(conn, this.SQL_queryIdStmt_FRO);
                    pstmt.setInt(1,id);
                    // First, try to query it back from the DB.
                    rs = pstmt.executeQuery();                
                    if(!rs.next()) {
//                        System.err.println("Unknown user#:" + id);
                        return null;
                    }
                    User u = new UserRegistry.User(id);                    
                    // from params:
                    u.name = DBUtils.getStringUTF8(rs, 1);// rs.getString(1);
                    u.org = rs.getString(2);
                    u.email = rs.getString(3);
                    u.userlevel = rs.getInt(4);
                    u.intlocs = rs.getString(5);
                    u.locales = rs.getString(6);
                    u.last_connect = rs.getTimestamp(7);
                    //          queryIdStmt = conn.prepareStatement("SELECT name,org,email,userlevel,intlocs,lastlogin,password from " + CLDR_USERS +" where id=?",

//                    System.err.println("SQL Loaded info for U#"+u.id + " - "+u.name +"/"+u.org+"/"+u.email);
                    ret = u; // let it finish..

                    if(id >= arraySize) {
                        int newchunk = (((id+1)/CHUNKSIZE)+1)*CHUNKSIZE;
//                        System.err.println("UR: userInfo resize from " + infoArray.length + " to " + newchunk);
                        infoArray = new UserRegistry.User[newchunk];
                        arraySize = newchunk;
                    }
                    infoArray[id]=u;
                    // good so far..
                    if(rs.next()) {
                        // dup returned!
                        throw new InternalError("Dup user id # " + id);
                    }
                } catch (SQLException se) {
                    logger.log(java.util.logging.Level.SEVERE, "UserRegistry: SQL error trying to get #" + id + " - " + DBUtils.unchainSqlException(se),se);
		    throw new InternalError("UserRegistry: SQL error trying to get #" + id + " - " + DBUtils.unchainSqlException(se));
                    //return ret;
                } catch (Throwable t) {
                    logger.log(java.util.logging.Level.SEVERE, "UserRegistry: some error trying to get #" + id,t);
                    throw new InternalError("UserRegistry: some error trying to get #" + id+" - "+t.toString());
                    //return ret;
                } finally {
                    // close out the RS
                	DBUtils.close(rs,pstmt,conn);
                } // end try
            }
///*srl*/    if(ret==null) { System.err.println("returning NULL for " + id); } else  { User u = ret; System.err.println("Returned info for U#"+u.id + " - "+u.name +"/"+u.org+"/"+u.email); }
            return ret;
        } // end synch array
    }

    public final UserRegistry.User get(String pass, String email, String ip) {
        return get(pass, email, ip, false);
    }
    
    
    public void touch(int id)  {
        //System.err.println("Touching: " + id);
    	Connection conn = null; 
    	PreparedStatement pstmt = null;
//        synchronized(conn) {
            try {
            	conn = DBUtils.getInstance().getDBConnection();
            	pstmt = conn.prepareStatement(SQL_touchStmt);
            	pstmt .setInt(1, id);
            	pstmt .executeUpdate();
                conn.commit();
            } catch(SQLException se) {
                logger.log(java.util.logging.Level.SEVERE, "UserRegistry: SQL error trying to touch " + id + " - " + DBUtils.unchainSqlException(se),se);
                throw new InternalError("UserRegistry: SQL error trying to touch " + id + " - " + DBUtils.unchainSqlException(se));
            } finally {
        		DBUtils.close(pstmt,conn);
            }
            
  //      }
    }
    

    /**
     * @param letmein The VAP was given - allow the user in regardless 
     * @param pass the password to match. If NULL, means just do a lookup
     */
    public  UserRegistry.User get(String pass, String email, String ip, boolean letmein) {
        if((email == null)||(email.length()<=0)) {
            return null; // nothing to do
        }
        if(((pass!=null&&pass.length()<=0)) && !letmein ) {
            return null; // nothing to do
        }
        
        if(email.startsWith("!") && pass!=null&&pass.equals(sm.vap)) {
        	email=email.substring(1);
        	letmein=true;
        }
        
        ResultSet rs = null;
        //synchronized(conn) {
        Connection conn  = null;
        PreparedStatement pstmt = null;
            try{ 
            	conn = DBUtils.getInstance().getDBConnection();
                if((pass != null) && !letmein) {
//                    logger.info("Looking up " + email + " : " + pass);
                    pstmt = DBUtils.prepareForwardReadOnly(conn, SQL_queryStmt_FRO);
                    pstmt.setString(1,email);
                    pstmt.setString(2,pass);
                } else {
//                    logger.info("Looking up " + email);
                    pstmt = DBUtils.prepareForwardReadOnly(conn, SQL_queryEmailStmt_FRO);
                    pstmt.setString(1,email);
                }
                // First, try to query it back from the DB.
                rs = pstmt.executeQuery();                
                if(!rs.next()) {
                    logger.info("Unknown user or bad login: " + email + " @ " + ip);
                    return null;
                }
                User u = new UserRegistry.User(rs.getInt(1));
                
                // from params:
                u.password = pass;
                if(letmein) {
                    u.password = rs.getString(8);
                }
                u.email = email;
                // from db:   (id,name,userlevel,org,locales)
                u.name = DBUtils.getStringUTF8(rs, 2);//rs.getString(2);
                u.userlevel = rs.getInt(3);
                u.org = rs.getString(4);
                u.locales = rs.getString(5);
                u.intlocs = rs.getString(6);
                u.last_connect = rs.getTimestamp(7);
                
                // good so far..
                
                if(rs.next()) {
                    // dup returned!
                    logger.severe("Duplicate user for " + email + " - ids " + u.id + " and " + rs.getInt(1));
                    return null;
                }
                if(!ip.startsWith("RSS@") && !ip.equals(INTERNAL)) {
//                    logger.info("Login: " + email + " @ " + ip);
                }
                
                return u;
            } catch (SQLException se) {
                logger.log(java.util.logging.Level.SEVERE, "UserRegistry: SQL error trying to get " + email + " - " + DBUtils.unchainSqlException(se),se);
                throw new InternalError("UserRegistry: SQL error trying to get " + email + " - " + DBUtils.unchainSqlException(se));
                //return null;
            } catch (Throwable t) {
                logger.log(java.util.logging.Level.SEVERE, "UserRegistry: some error trying to get " + email,t);
                throw new InternalError( "UserRegistry: some error trying to get " + email + " - "+t.toString());
                //return null;
            } finally {
                // close out the RS
            	DBUtils.close(rs,pstmt,conn);
            } // end try
//        } // end synch(conn)
    } // end get

    public UserRegistry.User get(String email) {
        return get(null,email,INTERNAL);
    }
    /**
     * @deprecated
     * @return
     */
    public UserRegistry.User getEmptyUser() {
        User u = new User();
        u.name = "UNKNOWN";
        u.email = "UN@KNOWN.example.com";
        u.org = "NONE"; 
        u.password = null;
        u.locales="";
        
       return u;   
    }
    
    static SurveyMain sm = null; // static for static checking of defaultContent..

    private UserRegistry(java.util.logging.Logger xlogger) {
        logger = xlogger;
    }

    // ------- special things for "list" mode:
    
    public java.sql.ResultSet list(String organization, Connection conn) throws SQLException {
        ResultSet rs = null;
        Statement s = null;
        final String ORDER = " ORDER BY org,userlevel,name ";
//        synchronized(conn) {
//            try {
                s = conn.createStatement();
                if(organization == null) {
                    rs = s.executeQuery("SELECT id,userlevel,name,email,org,locales,intlocs,lastlogin FROM " + CLDR_USERS + ORDER);
                } else {
                    rs = s.executeQuery("SELECT id,userlevel,name,email,org,locales,intlocs,lastlogin FROM " + CLDR_USERS + " WHERE org='" + organization + "'" + ORDER);
                }
//            } finally  {
//                s.close();
//            }
//        }
        
        return rs;
    }
    public java.sql.ResultSet listPass(Connection conn) throws SQLException {
        ResultSet rs = null;
        Statement s = null;
        final String ORDER = " ORDER BY id ";
//        synchronized(conn) {
//            try {
                s = conn.createStatement();
                rs = s.executeQuery("SELECT id,userlevel,name,email,org,locales,intlocs, password FROM " + CLDR_USERS + ORDER);
//            } finally  {
//                s.close();
//            }
//        }
        
        return rs;
    }
    
    void setupIntLocs() throws SQLException {
    	Connection conn = DBUtils.getInstance().getDBConnection();
        PreparedStatement removeIntLoc=null;
		PreparedStatement updateIntLoc=null;
    	try {
    		removeIntLoc = conn.prepareStatement(SQL_removeIntLoc);
    		updateIntLoc = conn.prepareStatement(SQL_updateIntLoc);
	        ResultSet rs = list(null,conn);
	        ElapsedTimer et = new ElapsedTimer();
	        int count=0;
	        while(rs.next()) {
	            int user = rs.getInt(1);
	//            String who = rs.getString(4);
	            
				updateIntLocs(user, false, conn, removeIntLoc, updateIntLoc);
	            count++;
	        }
	        conn.commit();
	        SurveyLog.debug("update:" + count + " user's locales updated " + et);
    	} finally {
    		DBUtils.close(removeIntLoc, updateIntLoc, conn);
    	}
    }
    /**
     * assumes caller has a lock on conn
     */
    String updateIntLocs(int user, Connection conn) throws SQLException {
        PreparedStatement removeIntLoc=null;
		PreparedStatement updateIntLoc=null;
    	try {
    		removeIntLoc = conn.prepareStatement(SQL_removeIntLoc);
    		updateIntLoc = conn.prepareStatement(SQL_updateIntLoc);
    		return updateIntLocs(user, true, conn, removeIntLoc, updateIntLoc);
    	} finally {
    		DBUtils.close(removeIntLoc, updateIntLoc);
    	}
    }
    
    static String normalizeLocaleList(String list) {
        list = list.trim();
        if(list.length()>0) {
            if(list.equals("none")) {
                return "none";
            }
            Set<String> s = new TreeSet<String>();
            for(String l : UserRegistry.tokenizeLocale(list) ) {
                String forum = new ULocale(l).getLanguage();
                s.add(forum);
            }
            list = null;
            for(String forum : s) {
                if(list == null) {
                    list = forum;
                } else {
                    list = list+" "+forum;
                }
            }
        }
        return list;
    }
    
    /**
     * assumes caller has a lock on conn
     */
    String updateIntLocs(int id, boolean doCommit, Connection conn, PreparedStatement removeIntLoc, PreparedStatement updateIntLoc) throws SQLException {
        // do something
        User user = getInfo(id);
        if(user==null) {
            return "";
        }
        
        removeIntLoc.setInt(1,id);
        int n = removeIntLoc.executeUpdate();
        //System.err.println(id+":"+user.email+" - removed intlocs " + n);
        
        n = 0;
        
        String[] il = user.getInterestList();
        if(il != null ) {
            updateIntLoc.setInt(1,id);
            Set<String> s = new HashSet<String>();
            for(String l : il ) {
                //System.err.println(" << " + l);
                String forum = new ULocale(l).getLanguage();
                s.add(forum);
            }
            for(String forum : s) {
                //System.err.println(" >> " + forum);
                updateIntLoc.setString(2,forum);
                n += updateIntLoc.executeUpdate();
            }
        }
        
        //System.err.println(id+":"+user.email+" - updated intlocs " + n);
        
        if(doCommit) {
            conn.commit();
        }
        return "";
    }

    String setUserLevel(WebContext ctx, int theirId, String theirEmail, int newLevel) {
        if((newLevel < ctx.session.user.userlevel) || (ctx.session.user.userlevel > TC)) {
            return ("[Permission Denied]");
        }

        String orgConstraint = null;
        String msg = "";
        if(ctx.session.user.userlevel == ADMIN) {
            orgConstraint = ""; // no constraint
        } else {
            orgConstraint = " AND org='" + ctx.session.user.org + "' ";
        }
        Connection conn = null;
            try {
            	conn = DBUtils.getInstance().getDBConnection();
                Statement s = conn.createStatement();
                String theSql = "UPDATE " + CLDR_USERS + " SET userlevel=" + newLevel + 
                    " WHERE id=" + theirId + " AND email='" + theirEmail + "' "  + orgConstraint;
      //           msg = msg + " (<br /><pre> " + theSql + " </pre><br />) ";
                logger.info("Attempt user update by " + ctx.session.user.email + ": " + theSql);
                int n = s.executeUpdate(theSql);
                conn.commit();
                userModified(theirId);
                if(n == 0) {
                    msg = msg + " [Error: no users were updated!] ";
                    logger.severe("Error: 0 records updated.");
                } else if(n != 1) {
                    msg = msg + " [Error in updating users!] ";
                    logger.severe("Error: " + n + " records updated!");
                } else {
                    msg = msg + " [user level set]";
                    msg = msg + updateIntLocs(theirId, conn);
                }
            } catch (SQLException se) {
                msg = msg + " exception: " + DBUtils.unchainSqlException(se);
            } catch (Throwable t) {
                msg = msg + " exception: " + t.toString();
            } finally  {
                DBUtils.getInstance().closeDBConnection(conn);
              //  s.close();
//            }
        }
        
        return msg;
    }

    String setLocales(WebContext ctx, int theirId, String theirEmail, String newLocales) {
        return setLocales(ctx, theirId, theirEmail, newLocales, false);
    }
    
    String setLocales(WebContext ctx, int theirId, String theirEmail, String newLocales, boolean intLocs) {
        if(!intLocs && ctx.session.user.userlevel > TC) { // Note- we dont' check that a TC isn't modifying an Admin's locale. 
            return ("[Permission Denied]");
        }
        
        newLocales = normalizeLocaleList(newLocales);

        String orgConstraint = null;
        String msg = "";
        if(ctx.session.user.userlevel == ADMIN) {
            orgConstraint = ""; // no constraint
        } else {
            orgConstraint = " AND org='" + ctx.session.user.org + "' ";
        }
        Connection conn = null;
        PreparedStatement ps = null;
        try {
        	conn = DBUtils.getInstance().getDBConnection();
                String theSql = "UPDATE " + CLDR_USERS + " SET "+
                    (intLocs?"intlocs":"locales") + "=? WHERE id=" + theirId + " AND email='" + theirEmail + "' "  + orgConstraint;
                ps = conn.prepareStatement(theSql);
      //           msg = msg + " (<br /><pre> " + theSql + " </pre><br />) ";
                logger.info("Attempt user locales update by " + ctx.session.user.email + ": " + theSql + " - " + newLocales);
                ps.setString(1, newLocales);
                int n = ps.executeUpdate();
                conn.commit();
                userModified(theirId);
                if(n == 0) {
                    msg = msg + " [Error: no users were updated!] ";
                    logger.severe("Error: 0 records updated.");
                } else if(n != 1) {
                    msg = msg + " [Error in updating users!] ";
                    logger.severe("Error: " + n + " records updated!");
                } else {
                    msg = msg + " [locales set]";
                    msg = msg + updateIntLocs(theirId, conn);
                    /*if(intLocs) { 
                        return updateIntLocs(theirId);
                    }*/
                }
            } catch (SQLException se) {
                msg = msg + " exception: " + DBUtils.unchainSqlException(se);
            } catch (Throwable t) {
                msg = msg + " exception: " + t.toString();
            } finally  {
                try {
	            	if(ps!=null) ps.close();
	            	if(conn!=null) conn.close();
                } catch(SQLException se) {
                    logger.log(java.util.logging.Level.SEVERE, "UserRegistry: SQL error trying to close. " + DBUtils.unchainSqlException(se),se);
                }
            }
        //}
        
        return msg;
    }


    String delete(WebContext ctx, int theirId, String theirEmail) {
        if(ctx.session.user.userlevel > TC) {
            return ("[Permission Denied]");
        }

        String orgConstraint = null; // keep org constraint in place
        String msg = "";
        if(ctx.session.user.userlevel == ADMIN) {
            orgConstraint = ""; // no constraint
        } else {
            orgConstraint = " AND org='" + ctx.session.user.org + "' ";
        }
        Connection conn = null;
        Statement s = null;
        try {
        		conn = DBUtils.getInstance().getDBConnection();
                s = conn.createStatement();
                String theSql = "DELETE FROM " + CLDR_USERS + 
                    " WHERE id=" + theirId + " AND email='" + theirEmail + "' "  + orgConstraint;
//                 msg = msg + " (<br /><pre> " + theSql + " </pre><br />) ";
                logger.info("Attempt user DELETE by " + ctx.session.user.email + ": " + theSql);
                int n = s.executeUpdate(theSql);
                conn.commit();
                userModified(theirId);
                if(n == 0) {
                    msg = msg + " [Error: no users were removed!] ";
                    logger.severe("Error: 0 users removed.");
                } else if(n != 1) {
                    msg = msg + " [Error in removing users!] ";
                    logger.severe("Error: " + n + " records removed!");
                } else {
                    msg = msg + " [removed OK]";
                }
            } catch (SQLException se) {
                msg = msg + " exception: " + DBUtils.unchainSqlException(se);
            } catch (Throwable t) {
                msg = msg + " exception: " + t.toString();
            } finally  {
        		DBUtils.close(s,conn);
              //  s.close();
            }
//        }
        
        return msg;
    }
    
    public enum InfoType { INFO_EMAIL("E-mail","email"), INFO_NAME("Name","name"), INFO_PASSWORD("Password","password"), INFO_ORG("Organization","org") ;
    	private static final String CHANGE = "change_";
		private String sqlField;
    	private String title;
    	InfoType(String title, String sqlField) {
    		this.title=title;
    		this.sqlField = sqlField;
    	}
    	public String toString() {
    		return title;
    	}
		public String field() {
			return sqlField;
		}
		public static InfoType fromAction(String action) {
			if(action!=null&&action.startsWith(CHANGE)) {
				 String which = action.substring(CHANGE.length());
				 return InfoType.valueOf(which);
			} else {
				return null;
			}
		}
		public String toAction() {
			return CHANGE+name();
		}
    };
    
    String updateInfo(WebContext ctx, int theirId, String theirEmail, InfoType type, String value) {
        if(ctx.session.user.userlevel > TC) {
            return ("[Permission Denied]");
        }

        String msg = "";
        Connection conn = null;
        PreparedStatement updateInfoStmt = null;
        try {
        		conn = DBUtils.getInstance().getDBConnection();
                
                updateInfoStmt = conn.prepareStatement("UPDATE "+CLDR_USERS+" set "+type.field()+"=? WHERE id=? AND email=?");
                if(type==UserRegistry.InfoType.INFO_NAME) { // unicode treatment
                    DBUtils.setStringUTF8(updateInfoStmt, 1, value);
                } else {
                    updateInfoStmt.setString(1, value);
                }
                updateInfoStmt.setInt(2,theirId);
                updateInfoStmt.setString(3,theirEmail);
                
                logger.info("Attempt user UPDATE by " + ctx.session.user.email + ": " + type.toString() + " = " + value);
                int n = updateInfoStmt.executeUpdate();
                conn.commit();
                userModified(theirId);
                if(n == 0) {
                    msg = msg + " [Error: no users were updated!] ";
                    logger.severe("Error: 0 users updated.");
                } else if(n != 1) {
                    msg = msg + " [Error in updated users!] ";
                    logger.severe("Error: " + n + " updated removed!");
                } else {
                    msg = msg + " [updated OK]";
                }
            } catch (SQLException se) {
                msg = msg + " exception: " + DBUtils.unchainSqlException(se);
            } catch (Throwable t) {
                msg = msg + " exception: " + t.toString();
            } finally  {
            	DBUtils.close(updateInfoStmt,conn);
            }
    //    }
        
        return msg;
    }
    

    public String getPassword(WebContext ctx, int theirId)  {
    	ResultSet rs = null;
    	Statement s = null;
    	String result = null;
    	Connection conn = null;
    	//        try {
    	logger.info("UR: Attempt getPassword by " + ctx.session.user.email + ": of #" + theirId);
    	try {
    		conn = DBUtils.getInstance().getDBConnection();
    		s = conn.createStatement();
    		rs = s.executeQuery("SELECT password FROM " + CLDR_USERS + " WHERE id=" + theirId);
    		if(!rs.next()) {
    			ctx.println("Couldn't find user.");
    			return null;
    		}
    		result = rs.getString(1);
    		if(rs.next()) {
    			ctx.println("Matched duplicate user (?)");
    			return null;
    		}                
    	} catch (SQLException se) {
    		logger.severe("UR:  exception: " + DBUtils.unchainSqlException(se));
    		ctx.println(" An error occured: " + DBUtils.unchainSqlException(se));
    	} catch (Throwable t) {
    		logger.severe("UR:  exception: " + t.toString());
    		ctx.println(" An error occured: " + t.toString());
        } finally  {
        	DBUtils.close(s,conn);
        }
//    }
        
        return result;
    }

    public static String makePassword(String email) {
        return CookieSession.newId(false).substring(0,9);
//        return  CookieSession.cheapEncode((System.currentTimeMillis()*100) + SurveyMain.pages) + "x" + 
//            CookieSession.cheapEncode(email.hashCode() * SurveyMain.vap.hashCode());
    }

	public User newUser(WebContext ctx, User u) {

		// prepare quotes
		u.email = u.email.replace('\'', '_').toLowerCase();
		u.org = u.org.replace('\'', '_');
		u.name = u.name.replace('\'', '_');
		u.locales = u.locales.replace('\'', '_');

		Connection conn = null;
		PreparedStatement insertStmt = null;
		try {
			conn = DBUtils.getInstance().getDBConnection();
			insertStmt = conn.prepareStatement(SQL_insertStmt);
			insertStmt.setInt(1, u.userlevel);
			DBUtils.setStringUTF8(insertStmt, 2, u.name); // insertStmt.setString(2,
															// u.name);
			insertStmt.setString(3, u.org);
			insertStmt.setString(4, u.email);
			insertStmt.setString(5, u.password);
			insertStmt.setString(6, normalizeLocaleList(u.locales));
			if (!insertStmt.execute()) {
				logger.info("Added.");
				conn.commit();
				if(ctx!=null) ctx.println("<p>Added user.<p>");
				User newu = get(u.password, u.email, FOR_ADDING); // throw away
																	// old user
				updateIntLocs(newu.id, conn);
				resetOrgList(); // update with new org spelling.
				notify(newu);
				return newu;
			} else {
			    if(ctx!=null) ctx.println("Couldn't add user.");
				conn.commit();
				return null;
			}
		} catch (SQLException se) {
			SurveyLog.logException(se,"Adding User");
			logger.severe("UR: Adding " + u.toString() + ": exception: "
					+ DBUtils.unchainSqlException(se));
		} catch (Throwable t) {
			SurveyLog.logException(t,"Adding User");
			logger.severe("UR: Adding  " + u.toString() + ": exception: " + t.toString());
		} finally {
			userModified(); // new user
			DBUtils.close(insertStmt,conn);
		}

		return null;
	}
    
    // All of the userlevel policy is concentrated here, or in above functions (search for 'userlevel')
    
    // * user types
    public static final boolean userIsAdmin(User u) {
        return (u!=null)&&(u.userlevel <= UserRegistry.ADMIN);
    }
    public static final boolean userIsTC(User u) {
        return (u!=null)&&(u.userlevel <= UserRegistry.TC);
    }
    public static final boolean userIsExpert(User u) {
        return (u!=null)&&(u.userlevel <= UserRegistry.EXPERT);
    }
    public static final boolean userIsVetter(User u) {
        return (u!=null)&&(u.userlevel <= UserRegistry.VETTER);
    }
   public static final boolean userIsStreet(User u) {
        return (u!=null)&&(u.userlevel <= UserRegistry.STREET);
    }
    public static final boolean userIsLocked(User u) {
        return (u!=null)&&(u.userlevel == UserRegistry.LOCKED);
    }
    // * user rights
    /** can create a user in a different organization? */
    public  static final boolean userCreateOtherOrgs(User u) {
        return userIsAdmin(u);
    }
    /** What level can the new user be, given requested? */
    static final int userCanCreateUserOfLevel(User u, int requestedLevel) {
        if(requestedLevel < 0) {
            requestedLevel = 0;
        }
        if(requestedLevel < u.userlevel) { // pin to creator
            requestedLevel = u.userlevel;
        }
        return requestedLevel;
    }
    /** Can the user modify anyone's level? */
    static final boolean userCanModifyUsers(User u) {
        return userIsTC(u);
    }
    static final boolean userCanEmailUsers(User u) {
        return userIsTC(u);
    }
    /** can the user modify this particular user? */
    static final boolean userCanModifyUser(User u, int theirId, int theirLevel) {
        return (  userCanModifyUsers(u) &&
                 (theirId != ADMIN_ID) &&
                 (theirId != u.id) &&
                 (theirLevel >= u.userlevel) );
    }
    static final boolean userCanDeleteUser(User u, int theirId, int theirLevel) {
        return (userCanModifyUser(u,theirId,theirLevel) &&
                theirLevel > u.userlevel); // must be at a lower level
    }
    static final boolean userCanChangeLevel(User u, int theirLevel, int newLevel) {
        int ourLevel = u.userlevel;
        return (userCanModifyUser(u, ALL_ID, theirLevel) &&
            (newLevel >= ourLevel) && // new level is equal to or greater than our level
           (newLevel != theirLevel) ); // not existing level 
    }
    static final boolean userCanDoList(User u) {
        return (userIsVetter(u));
    }
    static final boolean userCanCreateUsers(User u) {
        return (userIsTC(u));
    }
    static final boolean userCanSubmit(User u) {
		if(SurveyMain.isPhaseReadonly()) return false;
        return((u!=null) && userIsStreet(u));
    }
    
    // TODO: move to CLDRLocale
    static final boolean localeMatchesLocale(CLDRLocale smallLocale, CLDRLocale bigLocale) {
        if(bigLocale.toString().startsWith(smallLocale.toString())) {
            int blen = bigLocale.toString().length();
            int slen = smallLocale.toString().length();
            
            if(blen==slen) {
                return true;  // exact match.   'ro' matches 'ro'
            } else if(!java.lang.Character.isLetter(bigLocale.toString().charAt(slen))) {
                return true; // next char is NOT a letter. 'ro' matches 'ro_...'
            } else {
                return false; // next char IS a letter.  'ro' DOES NOT MATCH 'root'
            }
        } else {
            return false; // no substring (common case)
        }
    }
    
    static final boolean userCanModifyLocale(CLDRLocale uLocale, CLDRLocale aliasTarget) {
        if(SurveyMain.isPhaseReadonly()) return false;
        return localeMatchesLocale(uLocale, aliasTarget);
    }

    static boolean localeMatchesLocaleList(String localeArray[], CLDRLocale locale) {
        return localeMatchesLocaleList(stringArrayToLocaleArray(localeArray),locale);
    }
    static boolean localeMatchesLocaleList(CLDRLocale localeArray[], CLDRLocale locale)
    {
        for(int i=0;i<localeArray.length;i++) {
            if(localeMatchesLocale(localeArray[i],locale)) {
                return true;
            }
        }
        return false;
    }

    static boolean localeMatchesLocaleList(String localeList, CLDRLocale locale)
    {
        String localeArray[] = tokenizeLocale(localeList);
        return localeMatchesLocaleList(localeArray, locale);
    }
        
    
    static final boolean userCanModifyLocale(CLDRLocale localeArray[], CLDRLocale locale) {
		if(SurveyMain.isPhaseReadonly()) return false;
        if(localeArray.length == 0) {
            return true; // all 
        }
        
        return localeMatchesLocaleList(localeArray, locale);
    }
    
    public static final boolean userCanModifyLocale(User u, CLDRLocale locale) {
        if(u==null) return false; // no user, no dice
        if(STFactory.isReadOnlyLocale(locale)) return false;

        if(!userIsStreet(u)) return false; // at least street level
        if(SurveyMain.isPhaseReadonly()) return false; // readonly = locked for ALL
        if((sm.isLocaleAliased(locale)!=null) ||
            sm.supplemental.defaultContentToParent(locale.toString())!=null) return false; // it's a defaultcontent locale or a pure alias.
        if(userIsAdmin(u)) return true; // Admin can modify all
        if(userIsTC(u)) return true; // TC can modify all
        if((SurveyMain.phase() == SurveyMain.Phase.VETTING_CLOSED)) {
        }
        if(userIsTC(u)) return true; // TC can modify all
        if(SurveyMain.isPhaseClosed()) return false;
        if(SurveyMain.isPhaseSubmit() && !userIsStreet(u)) return false;
        if(SurveyMain.isPhaseVetting() && !userIsStreet (u)) return false;
        if(locale.getLanguage().equals("und")) {  // all user accounts can write to und.
            return true;
        }
//        if(SurveyMain.phaseVetting && !userIsStreet(u)) return false;
        if((u.locales == null) && userIsExpert(u)) return true; // empty = ALL
        String localeArray[] = tokenizeLocale(u.locales);
        return userCanModifyLocale(localeArray,locale);
    }

    private static boolean userCanModifyLocale(String[] localeArray, CLDRLocale locale) {
        return userCanModifyLocale(stringArrayToLocaleArray(localeArray), locale);
    }
    private static CLDRLocale[] stringArrayToLocaleArray(String[] localeArray) {
        CLDRLocale arr[] = new CLDRLocale[localeArray.length];
        for(int j=0;j<localeArray.length;j++) {
            arr[j]=CLDRLocale.getInstance(localeArray[j]);
        }
        return arr;
    }
    static final boolean userCanSubmitLocale(User u, CLDRLocale locale) {
		return userCanSubmitLocaleWhenDisputed(u, locale, false);
    }
	

    static final boolean userCanSubmitLocaleWhenDisputed(User u, CLDRLocale locale, boolean disputed) {
		if(SurveyMain.isPhaseReadonly()) return false;
        if(u==null) return false; // no user, no dice
        if(userIsTC(u)) return true; // TC can modify all
        if((SurveyMain.phase() == SurveyMain.Phase.VETTING_CLOSED)) {
            if(u.userIsSpecialForCLDR15(locale)) {
                return true;
            } else {
                return false;
            }
        }
        if(SurveyMain.isPhaseClosed()) return false;
		if(!u.userIsSpecialForCLDR15(locale) && SurveyMain.isPhaseVetting() && !disputed && !userIsExpert(u)) return false; // only expert can submit new data.
        return userCanModifyLocale(u,locale);
    }

    static final boolean userCanSubmitAnyLocale(User u) {
		if(SurveyMain.isPhaseReadonly()) return false;
        if(u==null) return false; // no user, no dice
        if(userIsTC(u)) return true; // TC can modify all
        if((SurveyMain.phase() == SurveyMain.Phase.VETTING_CLOSED)) {
//            if(u.userIsSpecialForCLDR15("be")) {
//                return true;
//            }
        }
        if(SurveyMain.isPhaseClosed()) return false;
        if(SurveyMain.isPhaseVetting() && !userIsExpert(u)) return false; // only expert can submit new data.
        return userCanSubmit(u);
    }

    static final boolean userCanVetLocale(User u, CLDRLocale locale) {
		if(SurveyMain.isPhaseReadonly()) return false;
        if((SurveyMain.phase() == SurveyMain.Phase.VETTING_CLOSED) && u.userIsSpecialForCLDR15(locale)) {
            return true;
        }
        if(userIsTC(u) ) return true; // TC can modify all
        if(SurveyMain.isPhaseClosed()) return false;
        return userCanModifyLocale(u,locale);
    }
    
    static final String LOCALE_PATTERN = "[, \t\u00a0\\s]+"; // whitespace
    /**
     * Invalid user ID, representing NO USER.
     */
    public static final int NO_USER = -1;
    
    static String[] tokenizeLocale(String localeList) {
        if((localeList == null)||((localeList=localeList.trim()).length()==0)) {
//            System.err.println("TKL: null input");
            return new String[0];
        }
        return localeList.trim().split(LOCALE_PATTERN);
    }
     
    /**
     * take a locale string and convert it to HTML. 
     */
    static String prettyPrintLocale(String localeList) {
//        System.err.println("TKL: ppl - " + localeList);
        String[] localeArray = tokenizeLocale(localeList);
        String ret = "";
        if((localeList == null) || (localeArray.length == 0)) {
//            System.err.println("TKL: null output");
            ret = ("<i>all locales</i>");
        } else {
            for(int i=0;i<localeArray.length;i++) {
                ret = ret + " <tt class='codebox' title='"+new ULocale(localeArray[i]).getDisplayName()+"'>"+localeArray[i]+"</tt> ";
            }
        }
//        return ret + " [" + localeList + "]";
        return ret;
    }
    
    Set<User> specialUsers = null;

    public Set<User> getSpecialUsers() {
        return getSpecialUsers(false);
    }
    
    public synchronized Set<User> getSpecialUsers(boolean reread) {
        if(specialUsers == null) {
            reread = true;
        }
        if(reread == true) {
            doReadSpecialUsers();
        }
        return specialUsers;
    }
    
    private synchronized boolean doReadSpecialUsers() {
        String externalErrorName = SurveyMain.getSurveyHome() + "/" + "specialusers.txt";

//        long now = System.currentTimeMillis();
//        
//        if((now-externalErrorLastCheck) < 8000) {
//            //System.err.println("Not rechecking errfile- only been " + (now-externalErrorLastCheck) + " ms");
//            if(externalErrorSet != null) {
//                return true;
//            } else {
//                return false;
//            }
//        }
//
//        externalErrorLastCheck = now;
        
        try {
            File extFile = new File(externalErrorName);
            
            if(!extFile.isFile() && !extFile.canRead()) {
                System.err.println("Can't read special user file: " + externalErrorName);
               // externalErrorFailed = true;
                return false;
            }
            
//            long newMod = extFile.lastModified();
//            
//            if(newMod == externalErrorLastMod) {
//                //System.err.println("** e.e. file did not change");
//                return true;
//            }
            
            // ok, now read it
            BufferedReader in
               = new BufferedReader(new FileReader(extFile));
            String line;
            int lines=0;
            Set<User> newSet = new HashSet<User>();
            System.err.println("* Reading special user file: " + externalErrorName);
            while ((line = in.readLine())!=null) {
                lines++;
                line = line.trim();
                if((line.length()<=0) ||
                  (line.charAt(0)=='#')) {
                    continue;
                }
                try {
                    int theirId = new Integer(line).intValue();
                    User u = getInfo(theirId);
                    if(u == null) {
                        System.err.println("Could not find user: " + line);
                        continue;
                    }
                    newSet.add(u);
                    System.err.println("*+ User: " + u.toString());

//                    String[] result = line.split("\t");
//                    String loc = result[0].split(";")[0];
//                    String what = result[1];
//                    String val = result[2];
//                    
//                    Set<Integer> aSet = newSet.get(loc);
//                    if(aSet == null) {
//                        aSet = new HashSet<Integer>();
//                        newSet.put(loc, aSet);
//                    }
//                    
//                    if(what.equals("path:")) {
//                        aSet.add(xpt.getByXpath(val));
//                    } else if(what.equals("count:")) {
//                        int theirCount = new Integer(val).intValue();
//                        if(theirCount != aSet.size()) {
//                            System.err.println(loc + " - count says " + val + ", we got " + aSet.size());
//                        }
//                    } else {
//                        throw new IllegalArgumentException("Unknown parameter: " + what);
//                    }
                } catch(Throwable t) {
                    System.err.println("** " + externalErrorName +":"+ lines + " -  " + t.toString());
                    //externalErrorFailed = true;
                    t.printStackTrace();
                    return false;  
                }
            }
            System.err.println(externalErrorName + " - " + lines + " and " + newSet.size() + " users loaded.");
            
//            externalErrorSet = newSet;
//            externalErrorLastMod = newMod;
//            externalErrorFailed = false;
            specialUsers = newSet;
            return true;
        } catch(IOException ioe) {
            System.err.println("Reading externalErrorFile: "  + "specialusers.txt - " + ioe.toString());
            ioe.printStackTrace();
            //externalErrorFailed = true;
            return false;
        }
    }
    
    public VoterInfo getVoterToInfo(int userid) {
        return getVoterToInfo().get(userid);
    }
    
    // Interface for VoteResolver interface
    /**
     * Fetch the user map in VoterInfo format.
     * @see #userModified()
     */
    public synchronized Map<Integer, VoterInfo> getVoterToInfo() {
        if(voterInfo == null) {
            Map<Integer, VoterInfo> map = new TreeMap<Integer, VoterInfo>();
            
            ResultSet rs = null;
            Connection conn = null;
            try {
            	conn = DBUtils.getInstance().getDBConnection();
                rs = list(null,conn);
                // id,userlevel,name,email,org,locales,intlocs,lastlogin    
                while(rs.next()){
                    // We don't go through the cache, because not all users may be loaded.
                    
                    User u = new UserRegistry.User(rs.getInt(1));
                    // from params:
                    u.userlevel = rs.getInt(2);
                    u.name = DBUtils.getStringUTF8(rs, 3);
                    u.email = rs.getString(4);
                    u.org = rs.getString(5);
                    u.locales = rs.getString(6);
                    u.intlocs = rs.getString(7);
                    u.last_connect = rs.getTimestamp(8);
                    
                    // now, map it to a UserInfo
                    VoterInfo v = u.createVoterInfo();
                    
                    map.put(u.id, v);
                }
                voterInfo = map;
            } catch (SQLException se) {
                logger.log(java.util.logging.Level.SEVERE, "UserRegistry: SQL error trying to  update VoterInfo - " + DBUtils.unchainSqlException(se),se);
            } catch (Throwable t) {
                logger.log(java.util.logging.Level.SEVERE, "UserRegistry: some error trying to update VoterInfo - "  + t.toString(),t);
            } finally {
                // close out the RS
                DBUtils.close(rs,conn);
            } // end try
        }
        return voterInfo;
    }
    
    /**
     * VoterInfo map
     */
    private Map<Integer, VoterInfo> voterInfo = null;
    
    /**
     * Not yet implemented.
     * @return
     */
    private static String[] orgList = new String[0];
    public static String[] getOrgList() {
    	return orgList;
    }
    /**
     * Update the organization list.
     */
    public void setOrgList() {
    	if(orgList.length > 0) {
    		return; // already set.
    	}
    	resetOrgList();
    }

	private void resetOrgList() {
		// get all orgs in use...
		Set<String> orgs = new TreeSet<String>();
		Connection conn = null;
		Statement s = null;
		try {
			conn=DBUtils.getInstance().getDBConnection();
			s = conn.createStatement();
			ResultSet rs = s.executeQuery("SELECT distinct org FROM "
					+ CLDR_USERS + " order by org");
			// System.err.println("Adding orgs...");
			while (rs.next()) {
				String org = rs.getString(1);
				// System.err.println("Adding org: "+ org);
				orgs.add(org);
			}
		} catch (SQLException se) {
			/* logger.severe */System.err
					.println(/* java.util.logging.Level.SEVERE, */"UserRegistry: SQL error trying to get orgs resultset for: VI "
							+ " - " + DBUtils.unchainSqlException(se)/* ,se */);
		} finally {
			// close out the RS
			try {
				if (s != null) {
					s.close();
				}
				if (conn != null) {
					DBUtils.closeDBConnection(conn);
				}
			} catch (SQLException se) {
				/* logger.severe */System.err
						.println(/* java.util.logging.Level.SEVERE, */"UserRegistry: SQL error trying to close out: "
								+ DBUtils.unchainSqlException(se)/* ,se */);
			}
		} // end try


		// get all possible VR orgs..
		Set<VoteResolver.Organization> allvr = new HashSet<VoteResolver.Organization>();
		for (VoteResolver.Organization org : VoteResolver.Organization.values()) {
			allvr.add(org);
		}
		// Subtract out ones already in use
		for (String org : orgs) {
			allvr.remove(UserRegistry.computeVROrganization(org));
		}
		// Add back any ones not yet in use
		for (VoteResolver.Organization org : allvr) {
			String orgName = org.name();
			orgName = UCharacter.toTitleCase(orgName, null);
			orgs.add(orgName);
		}

		orgList = orgs.toArray(orgList);
	}
	
	/*
<user id="460" email="?@??.??">
> <level n="5"/>
> <org>IBM</org>
> <locales type="edit">
> <locale id="sq"/>
> </locales>
> </user>  

 It's probably better to just give VETTER, seems more portable than '5'.

> If it is real info, make it an element. If not (and I think not, for
> "ibm"), omit it.  

 In the comments are the VoteResolver enum value.  I'll probably just
 use that value.

> 5. More issues with that. The structure is inconsistent, with some
> info in attributes and some in elements. Should be one or the other.
> 
> all attributes:
> 
> <user id="460" email="?@??.??" level="5" org="IBM" edit="sq de"/>
> 
> all elements
> 
> <user id="460">
>                 <email>?@??.??</email>
> <level/>5</level>
> <org>IBM</org>
> <edit>sq</edit>
> <edit>de</edit>
> </user>
> 	 */
	/**
	 * @param sm TODO
	 * @param ourDate
	 * @param obscured
	 * @param outFile
	 * @throws UnsupportedEncodingException
	 * @throws FileNotFoundException
	 */
	int writeUserFile(SurveyMain sm, String ourDate, boolean obscured, File outFile)
			throws UnsupportedEncodingException, FileNotFoundException {
		PrintWriter out = new PrintWriter(
		    new OutputStreamWriter(
		        new FileOutputStream(outFile), "UTF8"));
	//            } catch (UnsupportedEncodingException e) {
	//                throw new InternalError("UTF8 unsupported?").setCause(e);
		out.println("<?xml version=\"1.0\" encoding=\"UTF-8\" ?>");
	  //        ctx.println("<!DOCTYPE ldml SYSTEM \"http://.../.../stusers.dtd\">");
		out.println("<users generated=\""+ourDate+"\" obscured=\""+obscured+"\">");
		String org = null;
		Connection conn = null;
		try {
			conn = DBUtils.getInstance().getDBConnection();
			synchronized(this) {
		    java.sql.ResultSet rs = list(org, conn);
		    if(rs == null) {
		        out.println("\t<!-- No results -->");
		        return 0;
		    }
		    String lastOrg = null;
		    while(rs.next()) {
		        int theirId = rs.getInt(1);
		        int theirLevel = rs.getInt(2);
		        String theirName = obscured?("#"+theirId):DBUtils.getStringUTF8(rs, 3).trim();//rs.getString(3);
		        String theirEmail = obscured?/*"?@??.??"*/"":rs.getString(4).trim();
		        String theirOrg = rs.getString(5);
		        String theirLocales = rs.getString(6);
		        
		        String orgMunged = theirOrg;
		        try {
		        	orgMunged = VoteResolver.Organization.fromString(theirOrg).name();
		        } catch(IllegalArgumentException iae) {
		        	// illegal org
		        }
		        if(orgMunged==null || orgMunged.length()<=0) {
		        	orgMunged = theirOrg;
		        }
		        if(!orgMunged.equals(lastOrg)) {
		        	out.println("<!-- " + SurveyMain.xmlescape(theirOrg) + " -->");
		        	lastOrg = orgMunged;
		        }
		        out.print("\t<user id=\""+theirId+"\" ");
		        if(theirEmail.length()>0) out.print("email=\""+theirEmail+"\" ");
		        out.print("level=\""+UserRegistry.levelAsStr(theirLevel).toLowerCase()+"\"");
		        if(theirEmail.length()>0) out.print(" name=\""+SurveyMain.xmlescape(theirName)+"\"");
		        out.print(" "+
		        "org=\""+orgMunged+"\" locales=\"");
		        String theirLocalesList[] = UserRegistry.tokenizeLocale(theirLocales);
		        for(int i=0;i<theirLocalesList.length;i++) {
		            if(i>0) out.print(" ");
		        	out.print(theirLocalesList[i]);
		        }
		        out.println("\"/>");
		    }            
		}/*end synchronized(reg)*/ } catch(SQLException se) {
		    SurveyLog.logger.log(java.util.logging.Level.WARNING,"Query for org " + org + " failed: " + DBUtils.unchainSqlException(se),se);
		    out.println("<!-- Failure: " + DBUtils.unchainSqlException(se) + " -->");
		} finally {
			DBUtils.close(conn);
		}
		out.println("</users>");
		out.close();
		return 1;
	}
}
