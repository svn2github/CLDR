/*
 ******************************************************************************
 * Copyright (C) 2005-2008, International Business Machines Corporation and   *
 * others. All Rights Reserved.                                               *
 ******************************************************************************
 */

//  Created by Steven R. Loomis on 31/10/2005.
//
//  an XMLSource which is implemented in a database

// TODO: if readonly (frozen), cache

package org.unicode.cldr.web;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Logger;

import org.unicode.cldr.icu.LDMLConstants;
import org.unicode.cldr.util.CLDRFile;
import org.unicode.cldr.util.CLDRFile.DraftStatus;
import org.unicode.cldr.util.Factory;
import org.unicode.cldr.util.SimpleFactory;
import org.unicode.cldr.util.CLDRLocale;
import org.unicode.cldr.util.CldrUtility;
import org.unicode.cldr.util.LDMLUtilities;
import org.unicode.cldr.util.LruMap;
import org.unicode.cldr.util.VettingViewer.ErrorChecker;
import org.unicode.cldr.util.XMLSource;
import org.unicode.cldr.util.XPathParts;
import org.unicode.cldr.util.XPathParts.Comments;
import org.unicode.cldr.web.CLDRFileCache.CacheableXMLSource;
import org.unicode.cldr.web.CLDRProgressIndicator.CLDRProgressTask;
import org.unicode.cldr.web.DBUtils.ConnectionHolder;
import org.unicode.cldr.web.ErrorCheckManager.CachingErrorChecker;
import org.unicode.cldr.web.MuxedSource.MuxFactory;

public class CLDRDBSourceFactory extends Factory implements MuxFactory {
	public class SubFactory extends Factory {
		public boolean finalData = false;
		private DBEntry dbEntry = null;
		public SubFactory(boolean finalData) {
			this.finalData=finalData;
		}

		@Override
		public String getSourceDirectory() {
			return CLDRDBSourceFactory.this.getSourceDirectory();
		}

		@Override
		protected CLDRFile handleMake(String localeID, boolean resolved,
				DraftStatus madeWithMinimalDraftStatus) {
		    XMLSource source;
		    if (resolved) {
		        source = makeResolvingSource(localeID, madeWithMinimalDraftStatus);
		    } else {
		        source = getInstance(CLDRLocale.getInstance(localeID), false);
	            if(dbEntry!=null) dbEntry.add(source);
		    }
			return new CLDRFile(source).setAlternateSupplementalDirectory(getAlternateSupplementalDirectory());
		}

		@Override
		protected DraftStatus getMinimalDraftStatus() {
			return CLDRDBSourceFactory.this.getMinimalDraftStatus();
		}

		@Override
		protected Set<String> handleGetAvailable() {
			return CLDRDBSourceFactory.this.handleGetAvailable();
		}

		public void setDBEntry(DBEntry dbEntry) {
			this.dbEntry  = dbEntry;
		}

	}

	public enum Key { OLDKEYSET };

	/**
	 * Thread unsafe wrapper for connection state.
	 * @author srl
	 *
	 */
    public class DBEntry implements org.unicode.cldr.web.DBUtils.ConnectionHolder,org.unicode.cldr.web.DBUtils.DBCloseable {
    	private Connection conn = null;
    	
    	private Set<CLDRDBSource> allSources = new HashSet<CLDRDBSource>();
    	private Set<SubFactory> allFactories = new HashSet<SubFactory>();
		public DBEntry(CLDRDBSource x) {
			add(x);
			allOpen.add(this);
			conn = sm.dbUtils.getDBConnection();
		}
		public void add(XMLSource x) {
			if(x instanceof CLDRDBSource) {
				add((CLDRDBSource)x);
			} else {
				throw new IllegalArgumentException("Can't cast a " + x.getClass().getName() + " to a CLDRDBSource");
			}
		}
		public DBEntry(SubFactory x) {
			allOpen.add(this);
			add(x);
		}
		
		public void add(CLDRDBSource x) {
			x.setDBEntry(this);
			allSources.add(x);
		}
		public void add(SubFactory x) {
			x.setDBEntry(this);
			allFactories.add(x);
		}
		
		@Override
		public Connection getConnectionAlias() {
			return conn;
//			if(conn==null) {
//				conn = 
//				conn = sm.dbUtils.getDBConnection();
//			}
//			return conn;
		}

		@Override
		public void close() throws SQLException {
			if(conn!=null) {
				DBUtils.close(conn);
				conn=null;
			}
			allOpen .remove(this);
		}

		@Override
		public String toString() {
			return "{DBEntry: (conn:"+conn+"), isInAllOpen:"+allOpen.contains(this)+", }";
		}
		
		private final class LocaleEntry extends Registerable{
			protected LocaleEntry(LocaleChangeRegistry lcr, CLDRLocale locale) {
				super(lcr, locale);
				register();
			}

			public Map<String,Object> entries = new TreeMap<String,Object>();
		}
		private Map<CLDRLocale,LocaleEntry> stuff = new HashMap<CLDRLocale,LocaleEntry>();
		
		public String stack = DEBUG?StackTracker.currentStack():null;
		
		public final Map<String,Object> get(CLDRLocale loc) {
			LocaleEntry rr = stuff.get(loc);
			if(rr==null||!rr.isValid()) { 
				// TODO: revalidate (save an obj)
				rr = new LocaleEntry(sm.lcr,loc);
				stuff.put(loc, rr);
			}
			return rr.entries;
		}
		public final Object get(CLDRLocale loc, String key) {
			return get(loc).get(key);
		}
		public final Object put(CLDRLocale loc, String key, Object o) {
			return get(loc).put(key, o);
		}

		public final Object get(String locale, Key oldkeyset) {
			return get(CLDRLocale.getInstance(locale),oldkeyset.name());
		}

		public final Object put(String locale, Key oldkeyset,Object o) {
			return put(CLDRLocale.getInstance(locale),oldkeyset.name(),o);
		}
	}
    
	private static final boolean DEBUG = CldrUtility.getProperty("TEST", false);

	public static Set<DBEntry>  allOpen = new HashSet<DBEntry>();
	public static void closeAllEntries() throws SQLException {
		if(allOpen.isEmpty()) {
			System.err.println(DBEntry.class.getName()+": 0 items open.");
		} else {
			System.err.println(DBEntry.class.getName()+": Closing " + allOpen.size() + " items.");
			for(DBEntry e : allOpen) {
				e.close();
				if(e.stack!=null) {
					System.err.println("DBEntry still open: " + e.stack);
				}
			}
			allOpen.clear();
		}
		
		if(tracker!=null&&!tracker.isEmpty()) {
			System.err.println("Open MyStatements: " + tracker);
		}
	}

	private static final boolean SHOW_TIMES=false;
	private static final boolean SHOW_DEBUG=false;
	private static final boolean TRACE_CONN=false;
	
	
	private static final StackTracker tracker = DEBUG?new StackTracker():null;

	/**
	 * the logger to use, from SurveyMain
	 **/
	private static Logger logger;

	/**
	 * TODO: 0 make private
	 * factory for producing XMLFiles that go with the original source xml data.
	 */
	public Factory  rawXmlFactory = null; 

	CLDRFileCache cache = null; 
	/**
	 * location of LDML data.
	 */
	private String dir = null;

	// DB things

	/**
	 * The table containing CLDR data
	 */
	public static final String CLDR_DATA = "cldr_data";

	/**
	 * The table containing the Sources (i.e. list of LDML files
	 */
	public static final String CLDR_SRC = "cldr_src";

	/**
	 * For now, we are only concerned with the main/ tree
	 */
	private String tree = "main";

	/**
	 * XPathTable (shared) that is keyed to this database.
	 */
	public XPathTable xpt = null;         

	/**
	 * A referece back to the main SurveyMain. (for xpt, etc)
	 */
	public static SurveyMain sm = null;

	private List<CLDRLocale> needUpdate = new ArrayList<CLDRLocale>();
	File cacheDir = null;
	boolean vetterReady = false;
	
	// PreparedStatement queries
    private static final String keySet = "SELECT " + XPathTable.CLDR_XPATHS + ".id, " +
            XPathTable.CLDR_XPATHS + ".xpath FROM " + CLDR_DATA + ", " + XPathTable.CLDR_XPATHS +// was origxpath
            " WHERE " + XPathTable.CLDR_XPATHS + ".id=" + CLDR_DATA+ ".xpath AND locale=?";

    private static final String keyVettingSet = "SELECT "+Vetting.CLDR_OUTPUT+
            ".output_xpath from "+Vetting.CLDR_OUTPUT+" where "+Vetting.CLDR_OUTPUT+".locale=? AND EXISTS "+
            " ( SELECT * from "+CLDR_DATA+" where "+CLDR_DATA+".locale="+Vetting.CLDR_OUTPUT+".locale AND "+
            CLDR_DATA+".xpath="+Vetting.CLDR_OUTPUT+".data_xpath )";
    
    private static final String insert = "INSERT INTO " + CLDR_DATA +
            " (xpath,locale,source,origxpath,value,type,alt_type,txpath,submitter,base_xpath,modtime) " +
    "VALUES (?,?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP)";

    // xpath - contains type, no draft
    // origxpath - original xpath (full) - 
    // txpath - tiny xpath  (no TYPE)

    private static final String queryXpathPrefixes =
            "select "+XPathTable.CLDR_XPATHS+".id,"+XPathTable.CLDR_XPATHS+".xpath from "+
            XPathTable.CLDR_XPATHS+","+CLDR_DATA+" where "+CLDR_DATA+".xpath="+
            XPathTable.CLDR_XPATHS+".id AND "+XPathTable.CLDR_XPATHS+".xpath like ? AND "+CLDR_DATA+".locale=?";

    private static final String queryXpathTypes =
            "select " +CLDR_DATA+".type from "+
            CLDR_DATA+","+XPathTable.CLDR_XPATHS+" where "+CLDR_DATA+".xpath="+
            XPathTable.CLDR_XPATHS+".id AND "+XPathTable.CLDR_XPATHS+".xpath like ?";

    private static final String oxpathFromXpath =
            "select " +CLDR_DATA+".origxpath from "+CLDR_DATA+" where "+CLDR_DATA+".xpath=? AND "+CLDR_DATA+".locale=?";

    private static final String queryTypeValues =
            "select "+CLDR_DATA+".value,"+CLDR_DATA+".alt_type,"+CLDR_DATA+".alt_proposed,"+CLDR_DATA+".submitter,"+CLDR_DATA+".xpath,"+CLDR_DATA+".origxpath " +
            " from "+
            CLDR_DATA+" where "+CLDR_DATA+".txpath=? AND "+CLDR_DATA+".locale=? AND "+CLDR_DATA+".type=?";

    private static final String queryValue =
            "SELECT value FROM " + CLDR_DATA + " WHERE locale=? AND xpath=?"; 


    private static final String queryVetValue =
            "SELECT "+CLDR_DATA+".value FROM "+Vetting.CLDR_OUTPUT+"," + CLDR_DATA +
            " WHERE "+Vetting.CLDR_OUTPUT+".locale=? AND "+Vetting.CLDR_OUTPUT+".output_xpath=? AND "
            +" ("+Vetting.CLDR_OUTPUT+".locale="+CLDR_DATA+".locale) AND ("+Vetting.CLDR_OUTPUT+".data_xpath="+CLDR_DATA+".xpath)"; 

    private static final String oxpathFromVetXpath =
            "SELECT "+Vetting.CLDR_OUTPUT+".output_full_xpath FROM "+Vetting.CLDR_OUTPUT+" " +
            " WHERE "+Vetting.CLDR_OUTPUT+".locale=? AND "+Vetting.CLDR_OUTPUT+".output_xpath=? "; 

    private static final String queryVetXpath =
            "SELECT "+Vetting.CLDR_RESULT+".result_xpath FROM "+Vetting.CLDR_RESULT+" " +
            " WHERE "+Vetting.CLDR_RESULT+".locale=? AND "+Vetting.CLDR_RESULT+".base_xpath=? "; 

    //keyVettingSet = "keyVettingSet",
    //                               "SELECT output_xpath from "+Vetting.CLDR_OUTPUT+" where locale=?" ); // wow, that is pretty straightforward...

    private static final String keyASet =
            /*
                                "SELECT "+CLDR_DATA+".xpath from "+
                                XPathTable.CLDR_XPATHS+","+CLDR_DATA+" where "+CLDR_DATA+".xpath="+
                                XPathTable.CLDR_XPATHS+".id AND "+XPathTable.CLDR_XPATHS+".xpath like ? AND "+CLDR_DATA+".locale=?"
             */
            //    "SELECT "+CLDR_DATA+".xpath from CLDR_XPATHS,CLDR_DATA where CLDR_DATA.xpath=CLDR_XPATHS.id AND CLDR_XPATHS.xpath like '%/alias%' AND CLDR_DATA.locale=?"
            "SELECT "+CLDR_DATA+".origxpath, " + XPathTable.CLDR_XPATHS + ".xpath from "+XPathTable.CLDR_XPATHS+","+CLDR_DATA+
            " where "+CLDR_DATA+".origxpath="+XPathTable.CLDR_XPATHS+".id AND "+XPathTable.CLDR_XPATHS+".xpath like '%/alias%' AND "+CLDR_DATA+".locale=?";

    private static final String keyUnconfirmedSet =
            "select distinct "+Vetting.CLDR_VET+".vote_xpath from "+Vetting.CLDR_VET+" where "+
            Vetting.CLDR_VET+".vote_xpath!=-1 AND "+Vetting.CLDR_VET+
            ".locale=? AND NOT EXISTS ( SELECT "+Vetting.CLDR_RESULT+
            ".result_xpath from "+Vetting.CLDR_RESULT+" where "+
            Vetting.CLDR_RESULT+".result_xpath="+Vetting.CLDR_VET+".vote_xpath and "+
            Vetting.CLDR_RESULT+".locale="+Vetting.CLDR_VET+".locale AND "+
            Vetting.CLDR_RESULT+".type>="+Vetting.RES_ADMIN+") AND NOT EXISTS ( SELECT "+
            Vetting.CLDR_RESULT+".base_xpath from "+Vetting.CLDR_RESULT+" where "+
            Vetting.CLDR_RESULT+".base_xpath=CLDR_VET.base_xpath and "+
            Vetting.CLDR_RESULT+".locale="+Vetting.CLDR_VET+".locale AND "+Vetting.CLDR_RESULT+".type="+
            Vetting.RES_ADMIN+") AND EXISTS (select * from "+CLDR_DATA+" where "+
            CLDR_DATA+".locale="+
            Vetting.CLDR_VET+".locale AND "+CLDR_DATA+".xpath="+
            Vetting.CLDR_VET+".vote_xpath and "+CLDR_DATA+".value != '')";
    private static final String keyNoVotesSet = 
            "select distinct "+CLDR_DATA+".xpath from "+CLDR_DATA+","+Vetting.CLDR_RESULT+" where "+CLDR_DATA+".locale=? AND "+CLDR_DATA+".locale="+Vetting.CLDR_RESULT+".locale AND "+CLDR_DATA+".xpath="+Vetting.CLDR_RESULT+".base_xpath AND "+Vetting.CLDR_RESULT+".type="+Vetting.RES_NO_VOTES;
    private static final String querySource =
            "SELECT id,rev FROM " + CLDR_SRC + " where locale=? AND tree=? AND inactive IS NULL";

    private static final String querySourceInfo =
            "SELECT rev FROM " + CLDR_SRC + " where id=?";

    private static final String querySourceActives =
            "SELECT id,locale,rev FROM " + CLDR_SRC + " where inactive IS NULL";

    private static final String insertSource =
            "INSERT INTO " + CLDR_SRC + " (locale,tree,rev,inactive) VALUES (?,?,?,null)";

    private static final String getSubmitterId =
            "SELECT submitter from " + CLDR_DATA + " where locale=? AND xpath=? AND ( submitter is not null )"; // don't return anything if the submitter isn't set.

    private static final String removeItem =
            "DELETE FROM " + CLDR_DATA + " where locale=? AND xpath=?";

    public CLDRDBSourceFactory(SurveyMain sm, String theDir, Logger xlogger, File cacheDir) throws SQLException {
		this.xpt = sm.xpt;
		this.dir = theDir;
		setAlternateSupplementalDirectory(new File(dir + "/../"+"supplemental"));
		Connection sconn = sm.dbUtils.getDBConnection();
		CLDRDBSourceFactory.sm = sm;
		logger = xlogger; // set static
		setupDB(sconn);
		// initconn done later, in vetterReady
		this.cacheDir = cacheDir;
		DBUtils.closeDBConnection(sconn);
	}

	public void vetterReady() {
		vetterReady(null);
	}
	public void vetterReady(CLDRProgressIndicator progress) {
		if(DEBUG) System.err.println("DBSRCFAC: processing vetterReady()... initializing connection");
		Factory afactory = SimpleFactory.make(this.dir,".*");
		this.initConn(afactory);
		if(DEBUG) System.err.println("DBSRCFAC: processing vetterReady()...");
		cache= new CLDRFileCache(this, cacheDir, sm);

		vetterReady = true;
		update(progress, null);
	}

	/**
	 * Mark a locale as needing update.
	 * Do NOT have the conn locked when you call this, because you will deadlock.
	 * @param loc
	 */
	public void needUpdate(CLDRLocale loc) {
		if(!needUpdate.contains(loc)) {
			synchronized(needUpdate) {
				needUpdate.add(loc);
			}
		}
	}


	/**
	 * Execute deferred updates. Call this at a high level when all updates are complete.
	 * @param inConn TODO
	 */
	public int update(CLDRProgressIndicator surveyTask, Connection inConn) {
		int n = 0;
		CLDRProgressTask progress = null;
		if(surveyTask!=null) progress = surveyTask.openProgress("DeferredUpdates", needUpdate.size());
	    Set<CLDRLocale> toUpdate = new HashSet<CLDRLocale>();
		synchronized(needUpdate) {
		    toUpdate.addAll(needUpdate);
		    needUpdate.clear();
		}
		Connection conn = null;
		try {
			if(inConn!=null) {
				conn=inConn;
			} else {
				conn = sm.dbUtils.getDBConnection();
			}
			for(CLDRLocale l : toUpdate) {
				n++;
				if(progress!=null) progress.update(n);
				if(DEBUG) System.err.println("CLDRDBSRCFAC: executing deferred update of " + l +"("+needUpdate.size()+" on queue)");
				synchronized(sm.vet) {
					sm.vet.updateResults(l,conn);
				}
//				synchronized(this) {
//					XMLSource inst = getInstance(l,false);
//					// TODO: fix broken layering
//					XMLSource cached = ((MuxedSource)inst).cachedSource;
//					((CacheableXMLSource)cached).reloadWinning(((MuxedSource)inst).dbSource);
//					((CacheableXMLSource)cached).save();
//				}
			}
		} finally {
			if(inConn==null) {
				DBUtils.close(conn);
			}
            if(progress!=null) {
                progress.close();
            }
		}
		return n;
	}

	/**
	 * Return a non-vetted result.
	 * @param locale
	 * @return
	 */
	public XMLSource getInstance(CLDRLocale locale) {
		return getInstance(locale, false);
	}
	
	public XMLSource getInstance(String localeID) {
	    return getInstance(localeID, false);
	}

	/**
	 * The Muxed sources automatically are cached on read, and update on write.
	 */
	Map<CLDRLocale, MuxedSource> mux = new HashMap<CLDRLocale, MuxedSource>();

    private LruMap<String, XMLSource>[] sourceCache = new LruMap[] {
        new LruMap<String, XMLSource>(5),
        new LruMap<String, XMLSource>(5)
    };

    public XMLSource getInstance(String localeID, boolean finalData) {
        if(localeID == null) return null; // ???
        XMLSource result = null;
        Map<String, XMLSource> cache = sourceCache[finalData ? 1 : 0];
        if(MAKE_CACHE) {
            result = cache.get(localeID);
        }
        if(result == null) {
            if(localeID.startsWith(CLDRFile.SUPPLEMENTAL_PREFIX)) {
                XMLSource msource = rawXmlFactory.makeSource(localeID);
                msource.freeze();
                //                System.err.println("Getting simpleXMLSource for " + localeID);
                result = msource; 
            } else {
                CLDRDBSource dbresult = new CLDRDBSource(CLDRLocale.ROOT, finalData);
                if(!localeID.equals(dbresult.getLocaleID())) {
                    //dbresult.setLocaleID(localeID);
                    //dbresult.initConn(conn, rawXmlFactory); // set up connection & prepared statements. conn/factory may be set twice.
                    dbresult.setLocaleAndValidate(localeID);
                }
                result = dbresult;
            }
            if(MAKE_CACHE) cache.put(localeID, result);
        } else if(TRACE_CONN && SurveyMain.isUnofficial) {
            if(cache.size()>maxMakeHashSize) {
                maxMakeHashSize=cache.size();
            }
            makeHashHitCount++;
            if((makeHashHitCount%1000) == 0) {
                System.err.println("make: cache hit "+makeHashHitCount+" times, hash size " + cache.size() + " (max " + maxMakeHashSize+"), initConn count " + nn);
                if(false&& (makeHashHitCount % 1000)==0){
                    try {
                        throw new Throwable("cache hit make() called here");
                    } catch(Throwable t) {
                        t.printStackTrace();
                    }                
                }                    
            }
        }
        return result;
    }

	public XMLSource getInstance(CLDRLocale locale, boolean finalData) {
	    return getInstance(locale.toString(), finalData);
	}

	@Override
	public MuxedSource getMuxedInstance(CLDRLocale locale) {
		//synchronized(mux) {
			MuxedSource src = mux.get(locale);
			if(src!=null && src.invalid()) {
				src = null; // invalid
				mux.remove(locale);
			}
			if(src == null) {
				CLDRLocale parent = locale.getParent();
				if(parent != null) {
					MuxedSource ignored = mux.get(parent);
					if(ignored == null || ignored.invalid()) {
						if(DEBUG) System.err.println("First loading parent locale "+locale.toString()+"->" + parent.toString());
						ignored = getMuxedInstance(parent);
					}
				}
				src = new MuxedSource(this, locale, false);
				mux.put(locale, src);
			}
			return src;
			//}
	}

	/** 
	 * called once (at DB setup time) to initialize the database.
	 * @param sconn the database connection to be used for initial table creation
	 */
	public void setupDB(Connection sconn) throws SQLException
	{
		boolean isNew = !DBUtils.hasTable(sconn, CLDR_DATA);
		if(!isNew) {
			return; // nothing to setup
		}
		logger.info("CLDRDBSource DB: initializing...");
		synchronized(sconn) {
			String sql; // this points to 
			Statement s = sconn.createStatement();

			sql = "create table " + CLDR_DATA + " (id INT NOT NULL "+DBUtils.DB_SQL_IDENTITY+", " +
			"xpath INT not null, " + // normal
			"txpath INT not null, " + // tiny
			"locale varchar(20), " +
			"source INT, " +
			"origxpath INT not null, " + // full
			"alt_proposed varchar(50), " +
			"alt_type varchar(50), " +
			"type varchar(50), " +
			"value "+DBUtils.DB_SQL_UNICODE+" not null, " +
			"submitter INT, " +
			"modtime TIMESTAMP, " +
			// new additions, April 2006
			"base_xpath INT NOT NULL "+DBUtils.DB_SQL_WITHDEFAULT+" -1 " + // alter table CLDR_DATA add column base_xpath INT NOT NULL WITH DEFAULT -1
			" )";
			//            System.out.println(sql);
			s.execute(sql);
			sql = "create table " + CLDR_SRC + " (id INT NOT NULL "+DBUtils.DB_SQL_IDENTITY+", " +
			"locale varchar(20), " +
			"tree varchar(20) NOT NULL, " +
			"rev varchar(20), " +
			"modtime TIMESTAMP, "+
			"inactive INT)";
			// System.out.println(sql);
			s.execute(sql);
			// s.execute("CREATE UNIQUE INDEX unique_xpath on " + CLDR_DATA +"(xpath)");
			s.execute("CREATE INDEX "+CLDR_DATA+"_qxpath on " + CLDR_DATA + "(locale,xpath)");
			// New for April 2006.
			s.execute("create INDEX "+CLDR_DATA+"_qbxpath on "+CLDR_DATA+"(locale,base_xpath)");
			s.execute("CREATE INDEX "+CLDR_SRC+"_src on " + CLDR_SRC + "(locale,tree)");
			s.execute("CREATE INDEX "+CLDR_SRC+"_src_id on " + CLDR_SRC + "(id)");
			s.close();
			sconn.commit();
		}
		logger.info("CLDRDBSource DB: done.");
	}

    /**
     * called to initialize a preparedstatement.
     * @param sql the SQL to initialize the statement
     * @return the new prepared statement, or throws an error..
     */
	private PreparedStatement prepareStatement(Connection conn, String sql) throws SQLException {
	    return DBUtils.prepareForwardReadOnly(conn, sql);
	}

	/** 
	 * Initialize with (a reference to the shared) Connection
	 * set up statements, etc.
	 *
	 * Verify that the Source data is available.
	 */

	static int nn=0;

	private void initConn(Factory theFactory) {
		if (rawXmlFactory == null) {
			rawXmlFactory = theFactory;
		}
	}


	/**
	 * the hashtable of ("tree_locale") -> Integer(srcId)
	 */
	Hashtable srcHash = new Hashtable();
	//    
	public static final boolean USE_XPATH_CACHE=false; 

	/**
	 * given a tree and locale, return the source ID.
	 * @param tree which tree. should be "main". TODO: support multiple trees
	 * @param locale the locale to fetch.
	 * @return the source id, or -1 if not found.
	 */
	public int getSourceId(String tree, String locale) {
		String key = tree + "_" + locale;

		// first, is it in the hashtable?
		Integer r = null;
		r = (Integer) srcHash.get(key);
		if(r != null) {
			return r.intValue(); // quick check
		}
		Connection conn = sm.dbUtils.getDBConnection();
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
		    ps = prepareStatement(conn, querySource);
		    ps.setString(1,locale);
			ps.setString(2,tree);
			rs = ps.executeQuery();
			if(!rs.next()) {
				rs.close();
				return -1;
			}
			int result = rs.getInt(1);
			if(rs.next()) {
				logger.severe("Source returns two results: " + tree + "/" + locale);
				throw new InternalError("Issue with this Source: " + tree + "/" + locale);
			}
			rs.close();

			r = new Integer(result);
			srcHash.put(key,r); // add back to hash
			//logger.info(key + " - =" + r);
			return result;
		} catch(SQLException se) {
			logger.severe("CLDRDBSource: Failed to find source ("+tree + "/" + locale +"): " + DBUtils.unchainSqlException(se));
			return -1;
		} finally {
		    DBUtils.close(rs, ps, conn);
		}
	}


	/**
	 * given a source ID, return the CVS revision # from the DB
	 */
	public String getSourceRevision(int id) {
		if(id==-1) {
			return null;
		}
		String rev = null;
        Connection conn = sm.dbUtils.getDBConnection();
        PreparedStatement ps = null;
        ResultSet rs = null;
		try {
			ps = prepareStatement(conn, querySourceInfo);
			ps.setInt(1, id);
			rs = ps.executeQuery();
			if(rs.next()) {
				rev = rs.getString(1); // rev
				if(rs.next()) {
					throw new InternalError("Duplicate source info for source " + id);
				}
			} 
			// auto close
			return rev;
		} catch (SQLException se) {
			String what = ("CLDRDBSource: Failed to find source info ("+id +"): " + DBUtils.unchainSqlException(se));
			logger.severe(what);
			throw new InternalError(what);
		} finally {
            DBUtils.close(rs, ps, conn);
		}
	}

	/**
	 * We are adding a new source ID to the database.  Add it, and get the #
	 * @param tree which tree
	 * @param locale which locale
	 * @param rev CVS revision
	 * @return the new source ID
	 */
	private int setSourceId(String tree, String locale, String rev) {
        Connection conn = sm.dbUtils.getDBConnection();
        PreparedStatement ps = null;
        ResultSet rs = null;
		try {
		    ps = prepareStatement(conn, insertSource);
			ps.setString(1,locale);
			ps.setString(2,tree);
			ps.setString(3,rev);

			if(!ps.execute()) {
				conn.commit();
				return getSourceId(tree, locale); // adds to hash
			} else {
				conn.commit();
				throw new InternalError("CLDRDBSource: SQL failed to set source ("+tree + "/" + locale +")");
				//                        return -1;
			}
		} catch(SQLException se) {
			throw new InternalError("CLDRDBSource: Failed to set source ("+tree + "/" + locale +"): " + DBUtils.unchainSqlException(se));
			//                    return -1;
		} finally {
		    DBUtils.close(rs, ps, conn);
		}
	}

	/** 
	 * Utility function called by manageSourceUpdates()
	 * @see manageSourceUpdates
	 */
	private void manageSourceUpdates_locale(WebContext ctx, SurveyMain sm, int id, CLDRLocale loc, boolean quietUpdateAll, Connection conn)
	throws SQLException {
		// TODO: use prepared statement
		String mySql = ("DELETE from "+CLDR_DATA+" where source="+id+" AND submitter IS NULL");
		//        logger.severe("srcupdate: "+loc+" - "+ mySql);
		Statement s = conn.createStatement();
		int r = s.executeUpdate(mySql);
		//ctx.println("<br>Deleting data from src " + id + " ... " + r + " rows.<br />");
		mySql = "UPDATE "+CLDR_SRC+" set inactive=1 WHERE id="+id;
		//logger.severe("srcupdate:  "+loc+" - " + mySql);
		int j = s.executeUpdate(mySql);
		//ctx.println(" Deactivating src: " + j + " rows<br />");
		ctx.println("Deactivated Source #"+id+"  and "+r+" rows of data<br>");
		logger.severe("srcupdate: " +loc + " - deleted " + r + " rows of data, and deactivated " + j + " rows of src ( id " + id +"). committing.. ");
		s.close();
		conn.commit();
		sm.lcr.invalidateLocale(loc); // force a reload.
	}

	public int manageSourceUpdates(WebContext ctx, SurveyMain sm) {
		return manageSourceUpdates(ctx, sm, false);
	}

	/**
	 * Administrative hook called by the Admin user from SurveyMain
	 * presents the "manage source updates" interface, allowing new XML files to be updated
	 * @param ctx the webcontext
	 * @param sm alias to the SurveyMain
	 * @param quietUpdateAll if true, quietly do 'update all'
	 */
	public int manageSourceUpdates(WebContext ctx, SurveyMain sm, boolean quietUpdateAll) {
		String what = ctx.field("src_update");
		boolean updAll = quietUpdateAll || what.equals("all_locs");
		int updated = 0;
		if(!quietUpdateAll) {
			ctx.println("<h4>Source Update Manager</h4>");
		}
		synchronized (sm.vet) {
	        Connection conn = sm.dbUtils.getDBConnection();
	        PreparedStatement ps = null;
	        ResultSet rs = null;
			try {
				boolean hadDiffs = false; // were there any differences? (used for 'update all')
				ps = prepareStatement(conn, querySourceActives);
				rs = ps.executeQuery();
				if(!quietUpdateAll) {
					ctx.println("<table border='1'>");
					ctx.println("<tr><th>#</th><th>loc</th><th>DB Version</th><th>CVS/Disk</th><th>update</th></tr>");
				}
				while(rs.next()) {
					int id = rs.getInt(1);
					CLDRLocale loc = CLDRLocale.getInstance(rs.getString(2));
					String rev = rs.getString(3);
					String disk = LDMLUtilities.loadFileRevision(dir, loc+".xml");
					if(!quietUpdateAll) {
						ctx.println("<tr><th><a name='"+id+"'><tt>"+id+"</tt></a></th><td>" +loc + "</td>");
						ctx.println("<td>db="+rev+"</td>");
					}
					if(rev == null )  {
						rev = "null";
					}
					if(disk == null) {
						disk = "null";
					}
					if(rev.equals(disk)) {
						if(!quietUpdateAll) {
							ctx.println("<td class='proposed'>-</td><td></td>"); // no update available
						}
					} else {
						WebContext subCtx = new WebContext(ctx);
						hadDiffs = true;
						if(!quietUpdateAll) {
							ctx.println("<td class='missing'>disk="+disk+ " </td> ");
						}
						subCtx.addQuery("src_update",loc.toString());
						// ...
						if(updAll || what.equals(loc.getBaseName())) { // did we request update of this one?
							if(!quietUpdateAll) {
								ctx.println("<td class='proposed'>Updating...</td></tr><tr><td colspan='5'>");
							} else {
								ctx.println("<br><b>"+loc + "</b> " + rev + " &mdash;&gt; "+disk + " ");
							}
							manageSourceUpdates_locale(ctx,sm,id,loc, quietUpdateAll, conn);
							updated++;
							if(!quietUpdateAll) {
								ctx.println("</td>");
							}
						} else if(!quietUpdateAll) {
							ctx.println("<td><a href='"+subCtx.url()+"#"+id+"'>Update</a></td>"); // update available
						}
					}
					if(!quietUpdateAll) {
						ctx.println("</tr>");
					}
				}
				if(!quietUpdateAll) {
					ctx.println("</table>");
					if(hadDiffs) {
						WebContext subCtx = new WebContext(ctx);
						subCtx.addQuery("src_update","all_locs");
						ctx.println("<p><b><a href='"+subCtx.url()+"'>Update ALL</b></p>");
					}
				}
			} catch(SQLException se) {
				String complaint = ("CLDRDBSource: err in manageSourceUpdates["+what+"] ("+tree + "/" + "*" +"): " + DBUtils.unchainSqlException(se));
				logger.severe(complaint);
				ctx.println("<hr /><pre>" + complaint + "</pre><br />");
				return updated;
			} finally {
			    DBUtils.close(rs, ps, conn);
			}
		}
		return updated;
	}

	/**
	 * This is also called from the administrative pane and implements an old database migration function (adding base_xpaths).
	 */
	public void doDbUpdate(WebContext ctx, SurveyMain sm) {

		if(true==true) {
			throw new InternalError("CLDRDBSource.doDbUpdate (to add base_xpaths) is obsolete and has been disabled."); // ---- obsolete.  Code left here for future use.
		}
		/* //       querySourceActives = prepareStatement("querySourceActives",
         //           "SELECT id,locale,rev FROM " + CLDR_SRC + " where inactive IS NULL");

         String what = ctx.field("db_update");
         //   boolean updAll = what.equals("all_locs");
         ctx.println("<h4>DB Update Manager (srl use only)</h4>");
         System.err.println("doDbUpdate: "+SurveyMain.freeMem());
         int n = 0, nd = 0;
         String loc = "_";
         synchronized (conn) {
            synchronized(xpt) {
                String sql="??";
                try {
                    boolean hadDiffs = false;
                    sql = "select xpath,base_xpath from CLDR_DATA WHERE ((BASE_XPATH IS NULL) OR (BASE_XPATH = -1)) AND locale=? FOR UPDATE";
                    PreparedStatement ps = conn.prepareStatement(sql,ResultSet.TYPE_FORWARD_ONLY,ResultSet.CONCUR_UPDATABLE);
                    long t0 = System.currentTimeMillis();
                    for(Iterator iter = getAvailableLocales().iterator();iter.hasNext();) {
                        int thisn = 0;
                        loc = (String)iter.next();
                        ps.setString(1,loc);
                        ResultSet rs = ps.executeQuery();
                        while(rs.next()) {
                            int oldXpath = rs.getInt(1);
                            int newXpath = xpt.xpathToBaseXpathId(oldXpath);
                            n++;
                            thisn++;
                            if(newXpath!=oldXpath) {
                                nd++;
                            }
                            rs.updateInt(2,newXpath);
                            rs.updateRow();
                            if((n%1000)==0) {
                                ctx.println(loc + " - " + n + " updated, " + nd + " had differences<br>");
                                long td = System.currentTimeMillis()-t0;
                                double per = ((double)n/(double)td)*1000.0;
                                System.err.println("CLDBSource.doDbUpdate: "  + n + "update, @"+loc + ", " + nd + " had difference.  Avg " + per + "/sec. "+SurveyMain.freeMem());
                            }
                        }
                        if(thisn>0) {
                            System.err.println(loc + " Committing " + thisn + " items ... "+SurveyMain.freeMem());
                            conn.commit();
                        }
                    }
                    ctx.println("DONE: " + n + "patched, " + nd + " had difference.<br>");
                    System.err.println("CLDBSource.doDbUpdate:  DONE, " + n + "patched, " + nd + " had difference. "+SurveyMain.freeMem());

                } catch(SQLException se) {
                    String complaint = ("CLDRDBSource: err in doDbUpdate["+what+"] ("+tree + "/" + "*" +"): " + SurveyMain.unchainSqlException(se) + " - loc was = " + loc + " and SQL was: " + sql);
                    logger.severe(complaint);
                    ctx.println("<hr /><pre>" + complaint + "</pre><br />");
                    return;
                }
            }
         }
		 */
	}



	public synchronized int getSubmitterId(CLDRLocale locale, int xpath) {
        Connection conn = sm.dbUtils.getDBConnection();
        PreparedStatement ps = null;
        ResultSet rs = null;
		try {
		    ps = prepareStatement(conn, getSubmitterId);
			ps.setString(1,locale.toString());
			ps.setInt(2,xpath);
			rs = ps.executeQuery();
			if(!rs.next()) {
				///*srl*/     System.err.println("GSI[-1]: " + locale+":"+xpath);
				return -1;
			}
			int rp = rs.getInt(1);
			rs.close();
			///*srl*/ System.err.println("GSI["+rp+"]: " + locale+":"+xpath);
			if(rp > 0) {
				return rp;
			} else {
				return -1;
			}
		} catch(SQLException se) {
			logger.severe("CLDRDBSource: Failed to getSubmitterId ("+tree + "/" + locale + ":" + xpt.getById(xpath) + "#"+xpath+"): " + DBUtils.unchainSqlException(se));
			throw new InternalError("Failed to getSubmitterId ("+tree + "/" + locale + ":" + xpt.getById(xpath) + "#"+xpath + "): "+se.toString()+"//"+DBUtils.unchainSqlException(se));
		} finally {
		    DBUtils.close(rs, ps, conn);
		}
	}



	///*srl*/        boolean showDebug = (path.indexOf("dak")!=-1);
	//if(showDebug) /*srl*/logger.info(locale + ":" + path);
	public int getWinningPathId(int xpath, CLDRLocale locale, boolean finalData) {
		return getWinningPathId(xpath, locale, finalData, null);
	}
	
	public int getWinningPathId(int xpath, CLDRLocale locale, boolean finalData, ConnectionHolder ch) {
		if(finalData) {
			return sm.xpt.xpathToBaseXpathId(xpath);
		}

		//      if(false) {
		//          try {
		//              ResultSet rs;
		//              if(finalData) {
		//                  return sm.xpt.xpathToBaseXpathId(xpath);
		//                  //throw new InternalError("Unsupported: getWinningPath("+xpath+","+locale+") on finalData");
		//              } else {
		//                  stmts.queryVetXpath.setString(1,locale.toString());
		//                  stmts.queryVetXpath.setInt(2,xpath); 
		//                  rs = stmts.queryVetXpath.executeQuery();
		//              }
		//              if(!rs.next()) {
		//                  return -1;
		//              }
		//              int rp = rs.getInt(1);
		//              rs.close();
		//              if(rp != 0) {  // 0  means, fallback xpath
		//                  return rp;
		//              } else {
		//                  return -1;
		//              }
		//              //if(showDebug)/*srl*/if(finalData) {    logger.info(locale + ":" + path+" -> " + rv);}
		//          } catch(SQLException se) {
		//              se.printStackTrace();
		//              logger.severe("CLDRDBSource: Failed to getWinningPath ("+tree + "/" + locale + ":" + xpt.getById(xpath) + "#"+xpath+"): " + DBUtils.unchainSqlException(se));
		//              throw new InternalError("Failed to getWinningPath ("+tree + "/" + locale + ":" + xpt.getById(xpath) + "#"+xpath + "): "+se.toString()+"//"+DBUtils.unchainSqlException(se));
		//          }
		//      } else {
		return sm.vet.getWinningXPath(xpath, locale, ch);
		//      }
	}

	public String getWinningPath(int xpath, CLDRLocale locale, boolean finalData, ConnectionHolder ch) {
		int rp = getWinningPathId(xpath, locale, finalData, ch);
		if(rp > 0) {
			return xpt.getById(rp);
		} else {
			return null;
		}
	}

	private static int makeHashHitCount = 0;
	private static int maxMakeHashSize = 0;
	static final boolean MAKE_CACHE = false;

	/**
	 * return a list fo XML input files
	 */
	private File[] getInFiles() {
		// 1. get the list of input XML files
		FileFilter myFilter = new FileFilter() {
			public boolean accept(File f) {
				String n = f.getName();
				return(!f.isDirectory()
						&&n.endsWith(".xml")
						&&!n.startsWith(CLDRFile.SUPPLEMENTAL_PREFIX) // not a locale
				/*&&!n.startsWith("root")*/); // root is implied, will be included elsewhere.
			}
		};
		File baseDir = new File(dir);
		return baseDir.listFiles(myFilter);
	}


	/*
	 * 
========= ========= ========= ========= ========= ========= ========= ========= ========= ========= ========= ========= ========= ========= ========= ========= ========= ========= ========= ========= ========= 
========= ========= ========= ========= ========= ========= ========= ========= ========= ========= ========= ========= ========= ========= ========= 
========= ========= ========= ========= ========= ========= ========= ========= ========= ========= ========= ========= ========= 
	 */
	/**
	 * This class implements an XMLSource which is read out of a database.
	 * it is not directly modifiable, but it does have routines for modifying the database again.
	 **/
	public class CLDRDBSource extends XMLSource {

		////    
		////     void setup() {
		////         if(!loadAndValidate(getLocaleID(), null)) {
		////             throw new InternalError("Couldn't load and validate: " + getLocaleID());
		////         }
		//
		//     }
		/**
		 * show final, vetted data only?
		 */
		public boolean finalData = false;

		/**
		 * for vetting? 
		 */
		//private Vetting vetting = null;


		// local things
		private Comments xpath_comments = new Comments(); 

		/**
		 * User this File belongs to (or null)
		 * @deprecated
		 */
		protected UserRegistry.User user= null; 


		/** 
		 * source ID of this CLDRDBSource. 
		 * @see #getSourceId
		 */
		public int srcId = -1;

		private DBEntry dbEntry; 
		
		/**
		 * load and validate the item, if not already in the DB. Sets srcId and other state.
		 * Note that this is not a fully resolved operation at this level.
		 * @param locale the locale to load. Ex:  "mt_MT"
		 **/
		synchronized boolean setLocaleAndValidate(String locale) {
		    setLocaleID(locale);

		    CLDRLocale updateNeeded = null;
		    CLDRProgressTask progress = null;
            Connection conn = sm.dbUtils.getDBConnection();
            PreparedStatement ps = null;
	        try {
	            try {
	                ps = prepareStatement(conn, insert);
	            } catch (SQLException e) {
	                throw new InternalError("Error creating " + insert + " - " + DBUtils.unchainSqlException(e));
	            }
		        //        	synchronized(conn) {  // Synchronize on the conn to ensure that no other state is changing under us..
		        // double check..
		        srcId = getSourceId(tree, locale); // double checked lock- noone else loaded the src since then

		        if(srcId != -1) { 
		            return true;  // common case.
		        }            
		        if(DEBUG) System.err.println("DB Load: "+locale+"/"+srcId);
		        progress = sm.openProgress("DB Load " + locale);

		        String rev = LDMLUtilities.loadFileRevision(dir, locale+".xml");  // Load the CVS version # as a string
		        if(rev == null) rev = "null";
		        srcId = setSourceId(tree, locale, rev); // TODO: we had better fill it in..
		        //    synchronized(conn) {            
		        // logger.info("srcid: " + srcId +"/"+locale);
		        // if(locale.equals("el__POLYTON")) locale="el_POLYTON";
		        CLDRFile file = rawXmlFactory.make(locale, false, true); // create the CLDRFile pointing to the raw XML

		        if(file == null) {
		            System.err.println("Couldn't load CLDRFile for " + locale);
		            return false ;
		        }


		        if(DEBUG) System.err.println("loading rev: " + rev + " for " + dir + ":" + srcId +"/"+locale+".xml"); // LOG that a new item is loaded.
		        int rowCount=0;
		        //sm.fora.updateBaseLoc(locale); // in case this is a new locale.
		        // Now, start loading stuff in
		        XPathParts xpp=new XPathParts(null,null); // for parsing various xpaths

		        for (Iterator it = file.iterator(); it.hasNext();) {  // loop over the contents of the raw XML ..
		            rowCount++;
		            String rawXpath = (String) it.next();

		            // Make it distinguished
		            String xpath = CLDRFile.getDistinguishingXPath(rawXpath, null, false);

		            //if(!xpath.equals(rawXpath)) {
		            //    logger.info("NORMALIZED:  was " + rawXpath + " now " + xpath);
		            //}

		            String oxpath = file.getFullXPath(xpath); // orig-xpath.  

		            if(!oxpath.equals(file.getFullXPath(rawXpath))) {
		                // Failed the sanity check.  This should Never Happen(TM)
		                // What's happened here, is that the full xpath given the raw xpath, ought to be the full xpath given the distinguished xpath.
		                // SurveyTool depends on this being reversable thus.
		                throw new InternalError("FATAL: oxpath and file.getFullXPath(raw) are different: " + oxpath + " VS. " + file.getFullXPath(rawXpath));
		            }

		            int xpid = xpt.getByXpath(xpath);       // the numeric ID of the xpath
		            int oxpid = xpt.getByXpath(oxpath);     // the numeric ID of the orig-xpath
		            progress.update("x#"+xpid);

		            String value = file.getStringValue(xpath); // data value from XML

		            // Now, munge the xpaths around a bit.
		            xpp.clear();
		            xpp.initialize(oxpath);
		            String lelement = xpp.getElement(-1);
		            /* all of these are always at the end */
		            String eAlt = xpp.findAttributeValue(lelement,LDMLConstants.ALT);
		            String eDraft = xpp.findAttributeValue(lelement,LDMLConstants.DRAFT);

		            /* we call a special function to find the "tiny" xpath.  Which see */
		            String eType = xpt.typeFromPathToTinyXpath(xpath, xpp);  // etype = the element's type
		            String tinyXpath = xpp.toString(); // the tiny xpath

		            int txpid = xpt.getByXpath(tinyXpath); // the numeric ID of the tiny xpath

		            int base_xpid = xpt.xpathToBaseXpathId(xpath);  // the BASE xpath 

		            /* Some debugging to print these various things*/ 
		            //                System.out.println(xpath + " l: " + locale);
		            //                System.out.println(" <- " + oxpath);
		            //                System.out.println(" t=" + eType + ", a=" + eAlt + ", d=" + eDraft);
		            //                System.out.println(" => "+txpid+"#" + tinyXpath);

		            // insert it into the DB
		            try {
		                ps.setInt(1,xpid); // full
		                ps.setString(2,locale);
		                ps.setInt(3,srcId);
		                ps.setInt(4,oxpid); // Note: assumes XPIX = orig XPID! TODO: fix
		                DBUtils.setStringUTF8(ps, 5, value); // ps.setString(5,value);
		                ps.setString(6,eType);
		                ps.setString(7,eAlt);
		                ps.setInt(8,txpid); // tiny
		                ps.setNull(9, java.sql.Types.INTEGER); // Null integer for Submitter. NB: we do NOT ever consider data coming from XML as 'submitter' data.
		                ps.setInt(10, base_xpid);

		                ps.execute();

		            } catch(SQLException se) {
		                String complaint = 
		                    "CLDRDBSource: Couldn't insert " + locale + ":" + xpid + "(" + xpath +
		                    ")='" + value + "' -- " + DBUtils.unchainSqlException(se);
		                logger.severe(complaint);
		                throw new InternalError(complaint);
		            }
		        }

		        try{
		            conn.commit();
		        } catch(SQLException se) {
		            String complaint = 
		                "CLDRDBSource: Couldn't commit " + locale +
		                ":" + DBUtils.unchainSqlException(se);
		            logger.severe(complaint);
		            throw new InternalError(complaint);
		        }

		        if(DEBUG) System.err.println("loaded " + rowCount + " rows into  rev: " + rev + " for " + dir + ":" + srcId +"/"+locale+".xml"); // LOG that a new item is loaded.
		        updateNeeded = CLDRLocale.getInstance(locale); // update out of lock
		        return true;
		        //   }
		        //} // end: synch(xpt)
		    } finally {
		        if(progress!=null) progress.close();
		        DBUtils.close(ps, conn);
		        if(updateNeeded != null) {
		            needUpdate(updateNeeded);
		        }
		    }
		}
		/*
		@Override
	    public void getPathsWithValue(String valueToMatch, String pathPrefix, Set<String> result) {
			if(pathPrefix!=null) {
	    		super.getPathsWithValue(valueToMatch, pathPrefix, result);
	    		return;
			}

			long t0 = System.currentTimeMillis();
			
			super.getPathsWithValue(valueToMatch, pathPrefix, result);
			
			System.err.println("PWV: super took " + ElapsedTimer.elapsedTime(t0));
			return;
		}
		    MyStatements stmts = null;
		    try {
		    	stmts = openStatements();
			
		    	
		    	Object[][] array;
		    	
		    	if(pathPrefix==null) {
		    		array = sm.dbUtils.sqlQueryArrayArrayObj(stmts.getConnectionAlias(), "select xpath from cldr_data where cldr_data.locale=? and cldr_data.value=?",
		    					getLocaleID(), valueToMatch);
		    	} else {
		    		throw new InternalError("Not handled here"); //dead
		    	}
		    	
		    	for(int i=0;i<array.length;i++) {
		    		result.add(xpt.getById((Integer)array[i][0],stmts.getConnectionAlias()));
		    	}
		    	
		    } catch (SQLException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} finally {
		    	stmts.closeOrThrow();
		    }
			
			System.err.println("GPWV: " +getLocaleID()+ valueToMatch + "  - " + result.size() + " results in " + ElapsedTimer.elapsedTime(t0));
		}
*/

		public void setDBEntry(DBEntry dbEntry) {
			this.dbEntry = dbEntry;
		}


		/**
		 * Return the SCM ID of this source.
		 */
		public String getSourceRevision() {
			return CLDRDBSourceFactory.this.getSourceRevision(srcId); // TODO: awkward
		}

		public void putFullPathAtDPath(String distinguishingXPath, String fullxpath) { 
			complain_about_slower_api();
			putValueAtPath(fullxpath, this.getValueAtDPath(distinguishingXPath));
		}

		public void putValueAtDPath(String distinguishingXPath, String value) { 
			complain_about_slower_api();
			putValueAtPath(this.getFullPathAtDPath(distinguishingXPath), value);
		}
		public String putValueAtPath(String xpath, String value)
		{
			String loc = getLocaleID();
			String dpath = CLDRFile.getDistinguishingXPath(xpath, null, false);
			CLDRLocale locale = CLDRLocale.getInstance(loc);
			int xpid = sm.xpt.getByXpath(dpath); // the numeric ID of the xpath
			int oxpid = sm.xpt.getByXpath(xpath); // the numeric ID of the
			// orig-xpath
			// Make it distinguished
			// if(!xpath.equals(rawXpath)) {
			// logger.info("NORMALIZED:  was " + rawXpath + " now " + xpath);
			// }

			// String oxpath = file.getFullXPath(xpath); // orig-xpath.

			// if(!oxpath.equals(file.getFullXPath(rawXpath))) {
			// // Failed the sanity check. This should Never Happen(TM)
			// // What's happened here, is that the full xpath given the raw
			// xpath, ought to be the full xpath given the distinguished xpath.
			// // SurveyTool depends on this being reversable thus.
			// throw new
			// InternalError("FATAL: oxpath and file.getFullXPath(raw) are different: "
			// + oxpath + " VS. " + file.getFullXPath(rawXpath));
			// }
			//

			XPathParts xpp = new XPathParts(null, null);
			// Now, munge the xpaths around a bit.
			xpp.clear();
			xpp.initialize(xpath);
			String lelement = xpp.getElement(-1);
			/* all of these are always at the end */
			String eAlt = xpp.findAttributeValue(lelement, LDMLConstants.ALT);
			int submitter = XPathTable.altProposedToUserid(eAlt);
			String eDraft = xpp.findAttributeValue(lelement,
					LDMLConstants.DRAFT);

			/* we call a special function to find the "tiny" xpath. Which see */
			String eType = sm.xpt.typeFromPathToTinyXpath(xpath, xpp); // etype
			// = the
			// element's
			// type
			String tinyXpath = xpp.toString(); // the tiny xpath

			int txpid = sm.xpt.getByXpath(tinyXpath); // the numeric ID of the
			// tiny xpath

			int base_xpid = sm.xpt.xpathToBaseXpathId(dpath); // the BASE xpath

			/* Some debugging to print these various things */
			// System.out.println(xpath + " l: " + locale);
			// System.out.println(" <- " + oxpath);
			// System.out.println(" t=" + eType + ", a=" + eAlt + ", d=" +
			// eDraft);
			// System.out.println(" => "+txpid+"#" + tinyXpath);

			// insert it into the DB
			// synchronized(conn) {
			Connection conn = sm.dbUtils.getDBConnection();
			PreparedStatement ps = null;
			try {
			    ps = prepareStatement(conn, insert);
				ps.setInt(1, xpid); // / dpath
				ps.setString(2, loc);
				ps.setInt(3, srcId);
				ps.setInt(4, oxpid); // origxpath = full (original)
				// xpath
				DBUtils.setStringUTF8(ps, 5, value); // ps.setString(5,value);
				ps.setString(6, eType);
				ps.setString(7, eAlt);
				ps.setInt(8, txpid); // tiny xpath
				if (submitter == -1) {
					ps.setNull(9, java.sql.Types.INTEGER); // getId(alt...)
				} else {
					ps.setInt(9, submitter);
				}
				ps.setInt(10, base_xpid);

				ps.execute();

				if(DEBUG) System.err.println("Inserted: " + xpath);
				conn.commit();
			} catch (SQLException se) {
				String complaint = "CLDRDBSource: Couldn't insert/commit "
					+ getLocaleID() + ":" + xpid + "(" + xpath + ")='"
					+ value + "' -- " + DBUtils.unchainSqlException(se);
				logger.severe(complaint);
				throw new InternalError(complaint);
			} finally {
				DBUtils.close(ps, conn);
			}

			// System.err.println("loaded " + rowCount + " rows into  rev: " +
			// rev + " for " + dir + ":" + srcId +"/"+locale+".xml"); // LOG
			// that a new item is loaded.
			// if(vetterReady) {
			// synchronized(sm.vet) {
			// sm.vet.updateResults(loc);
			// }
			// } else {
			// System.err.println("CLDRDBSource " + loc +
			// " - deferring vet update on " + loc + " until vetter ready.");
			// }
			// } /* release the outer conn connection before calling needUpdate
			// */
			needUpdate(locale);
			return dpath;
		}

		/**
		 * XMLSource API. Unimplemented, read only.
		 */
		public void removeValueAtDPath(String distinguishingXPath) {  
			//String dpath = CLDRFile.getDistinguishingXPath(xpath, null, false);
			String loc = getLocaleID();
			CLDRLocale locale = CLDRLocale.getInstance(loc);
			int xpid = sm.xpt.getByXpath(distinguishingXPath);       // the numeric ID of the xpath
			removeItem(locale, xpid);
		}


		/**
		 * Remove an item from the DB. 
		 * @param locale locale of item
		 * @param xpathId id to remove
		 * @return number of rows deleted
		 */
		public int removeItem(CLDRLocale locale, int xpathId) {
		    Connection conn = sm.dbUtils.getDBConnection();
		    PreparedStatement ps = null;
			try {
			    ps = prepareStatement(conn, removeItem);
				ps.setString(1, locale.toString());
				ps.setInt(2, xpathId); // base xpath
				//           stmts.removeItem.setInt(3, submitter);
				int n = ps.executeUpdate();
				if(n != 1) {
					throw new InternalError("Trying to remove "+locale+":"+xpathId+"@" + " and the path wasn't found.");
				}
				conn.commit();
				return n;
			} catch(SQLException se) {
				String problem = ("CLDRDBSource: "+"Trying to remove "+locale+":"+xpathId+"@" +" : " + DBUtils.unchainSqlException(se));
				logger.severe(problem);
				throw new InternalError(problem);
			} finally {
			    DBUtils.close(ps, conn);
			}
		}

		/** 
		 * XMLSource API. Returns whether or not a value exists. 
		 * @param path a distinguished path
		 * @return true if the value exists
		 */
		public boolean hasValueAtDPath(String path) {
			long t0;
			String locale = getLocaleID();
			if (SHOW_TIMES) {
				t0 = System.currentTimeMillis();
			}
			// System.out.println("srl: hv@dp[f="+(finalData)+"] " + path);
			if (finalData) {
				boolean rv = (getValueAtDPath(path) != null); // TODO: optimize
				// this
				if (SHOW_TIMES)
					System.err.println("hasValueAtDPath:final " + locale + ":"
							+ path + " " + (System.currentTimeMillis() - t0));
				return rv;
			}

            int pathInt = xpt.getByXpath(path,getConnectionAlias());
			if (SHOW_TIMES)
				System.err.println("hasValueAtDPath:>> " + locale + ":"
						+ pathInt + " " + (System.currentTimeMillis() - t0));

			// logger.info(locale + ":" + path);
			// synchronized (conn) {
            Connection conn = sm.dbUtils.getDBConnection();
            PreparedStatement ps = null;
			ResultSet rs = null;
			try {
				ps = prepareStatement(conn, queryValue);
				ps.setString(1, locale);
				ps.setInt(2, pathInt);
				rs = ps.executeQuery();
				boolean hasValue = rs.next();
                if (SHOW_TIMES) {
                    System.err.println("hasValueAtDPath:" + hasValue + " " + locale + ":"
                            + path + " "
                            + (System.currentTimeMillis() - t0));
				}
                return hasValue;
			} catch (SQLException se) {
				logger.severe("CLDRDBSource: Failed to check data (" + tree
						+ "/" + locale + ":" + path + "): "
						+ DBUtils.unchainSqlException(se));
				return false;
			} finally {
                DBUtils.close(rs, ps, conn);
            }
		}

		public String getWinningPath(String path) 
		{
			String baseXpath = XPathTable.xpathToBaseXpath(path);
			int xpath=xpt.getByXpath(baseXpath,getConnectionAlias());

			// look for it in parents
			for(CLDRLocale locale : CLDRLocale.getInstance(getLocaleID()).getParentIterator()) {
				String rv = CLDRDBSourceFactory.this.getWinningPath(xpath, locale, finalData, dbEntry);

				if(rv != null) {
					return rv;
				}
			}
			return xpt.getById(xpath); // default: winner is original path
			//    throw new InternalError("Can't find winning path for getWinningPath("+path + "#"+xpath+","+getLocaleID()+")");
		}


		/**
		 * 
		 * @return null if no dbEntry
		 */
		private final Connection getConnectionAlias() {
			if(dbEntry==null) {
				return null;
			} else {
				return dbEntry.getConnectionAlias();
			}
		}

		/**
		 * XMLSource API. Returns the value of a distringuished path
		 * @param path distinguished path
		 * @return value or else null if none exists
		 */

		public String getValueAtDPath(String path) {
			//			synchronized(this.conn) {
			long t0;
			if(SHOW_TIMES) t0 = System.currentTimeMillis();
//			if(conn == null) {
//				throw new InternalError("No DB connection!");
//			}

			String locale = getLocaleID();
			int xpath = xpt.getByXpath(path,getConnectionAlias());
			if(SHOW_TIMES) System.err.println("getValueAtDPath:>> "+locale + ":" + xpath + " " + (System.currentTimeMillis()-t0));

			///*srl*/        boolean showDebug = (path.indexOf("dak")!=-1);
			//        try {
			//            throw new InternalError("bar");
			//        } catch(InternalError ie) {
			//            ie.printStackTrace();
			//        }
			if(SHOW_DEBUG) /*srl*/logger.info(locale + ":" + path);
			Connection conn = sm.dbUtils.getDBConnection();
			PreparedStatement ps = null;
            ResultSet rs = null;
            try {
                ps = prepareStatement(conn, finalData ? queryVetValue : queryValue);
                ps.setString(1,locale);
                ps.setInt(2,xpath);
                rs = ps.executeQuery();
                String rv;
                if(!rs.next()) {
                    if(SHOW_TIMES) System.err.println("getValueAtDPath:0 "+locale + ":" + path + " " + (System.currentTimeMillis()-t0));
                    if(!finalData) {
                        rs.close();                    
                        if(SHOW_DEBUG) System.err.println("Nonfinal - no match for "+locale+":"+xpath + "");
                        return null;
                    } else {
                        if(SHOW_DEBUG) System.err.println("Couldn't find "+ locale+":"+xpath + " - trying original - @ " + path);
                        //if(locale.equals("de")) {
                        //    return "fork";
                        //} else {

                        if(false && sm.isUnofficial) {
                            if(keySet().contains(path)) { // TODO: cache this!
                                System.err.println("Ad but missing: "+ locale+":"+xpath + " - @ " + path);
                            } else {
                                if(SHOW_DEBUG) System.err.println("notad: "+ locale+":"+xpath + " - @ " + path);
                            }
                        }
                        rs.close();
                        return null;
                    }                      
                }
                rv = DBUtils.getStringUTF8(rs, 1); //            rv = rs.getString(1); // unicode
                if(SHOW_TIMES) System.err.println("getValueAtDPath:+ "+locale + ":" + path + " " + (System.currentTimeMillis()-t0));
                if(rs.next()) {
                    String complaint = "warning: multi return: " + locale + ":" + path + " #"+xpath;
                    logger.severe(complaint);
                    // throw new InternalError(complaint);                    
                }
                if(SHOW_DEBUG) if(finalData) {    logger.info(locale + ":" + path+" -> " + rv);}
                return rv;

            } catch(SQLException se) {
				se.printStackTrace();
				logger.severe("CLDRDBSource: Failed to query data ("+tree + "/" + locale + ":" + path + "): " + DBUtils.unchainSqlException(se));
				return null;
            } finally {
                DBUtils.close(rs, ps, conn);
            }
		}

		/*
		 * convert a distinguished path to a full path
		 * @param path cleaned (distinguished) path
		 * @return the full path, or null if n/a
		 */
		public String getFullPathAtDPath(String path) {
			int xpathid = xpt.getByXpath(path, getConnectionAlias());
			String ret =  getOrigXpathFromCache(xpathid);
			return ret;
		}

		public void setLocaleID(String localeID) {
			super.setLocaleID(localeID);
			reset();
		}

		/**
		 * Reset per-locale cache, re-register.
		 */
		private void reset() {
			origXpaths.clear();
			token = new Registerable(sm.lcr, CLDRLocale.getInstance(getLocaleID()));
			token.register();
		}

		Registerable token = null;

		/**
		 * get the 'original' xpath from a path-id#
		 * @param pathid ID# of a path
		 * @return the original xpath string
		 * @see XPathTable
		 */
		public String getOrigXpathFromCache(int pathid) {
			if(!USE_XPATH_CACHE) {
				return getOrigXpath(pathid, finalData);
			} else {
				if(token==null||!token.isValid()) {
					reset();
				}
				Integer orig = origXpaths.get(pathid);
				if(orig == null) {
					orig = getOrigXpathId(pathid, finalData);
					origXpaths.put(pathid, orig);
					System.err.println("Rescan: locale,"+pathid+","+orig);
				}
				if(orig==null || orig==-1) {
					return null;
				} else {
					return sm.xpt.getById(orig);
				}
			}
		}

		IntHash<Integer> origXpaths = new IntHash<Integer>();

		/**
		 * get the 'original' xpath from a path-id#
		 * @param pathid ID# of a path
		 * @return the original xpath string
		 * @see XPathTable
		 */
		public String getOrigXpath(int pathid, boolean useFinalData) {
			return getOrigXpathString(pathid, useFinalData);
		}


		private final String getOrigXpathString(int pathid, boolean useFinalData) {
			int n = getOrigXpathId(pathid, useFinalData);
			return sm.xpt.getById(n, dbEntry!=null?dbEntry.getConnectionAlias():null);
		}

		public int getOrigXpathId(int pathid, boolean useFinalData) {
			String locale = getLocaleID();
			//			synchronized (conn) { // NB: many of these synchronizeds were removed as unnecessary.
			Connection conn = sm.dbUtils.getDBConnection();
			PreparedStatement ps = null;
			ResultSet rs = null;
			try {
				if(!useFinalData) {
	                ps = prepareStatement(conn, oxpathFromXpath);
					ps.setInt(1,pathid);
					ps.setString(2,locale);
					//System.err.println("oxpathFromXpath " + pathid + "  / " + locale);
				} else {
                    ps = prepareStatement(conn, oxpathFromVetXpath);
					ps.setString(1, locale);
					ps.setInt(2, pathid);
				}
                rs = ps.executeQuery();

				if(!rs.next()) {
					rs.close();
					//if(DEBUG) System.err.println("getOrigXpath["+finalData+"/"+useFinalData+"] not found, falling back: " + locale + ":"+pathid+" " + xpt.getById(pathid));
					return -1;
					//                    throw new InternalError  /* logger.severe */ ("getOrigXpath["+finalData+"] not found, falling back: " + locale + ":"+pathid+" " + xpt.getById(pathid));
					//return xpt.getById(pathid); // not found - should be null?

				}
				int result = rs.getInt(1);
				
                if(DEBUG&&rs.next()) {
                    logger.severe("getOrigXpath returns two results: " + locale + " " + xpt.getById(pathid));
                    // fail?? what?
                }
				rs.close();
				return result;
			} catch(SQLException se) {
				logger.severe("CLDRDBSource: Failed to find orig xpath ("+tree + "/" + locale +"/"+xpt.getById(pathid)+"): " + DBUtils.unchainSqlException(se));
				return pathid; //? should be null?
			} finally {
				DBUtils.close(rs, ps, conn);
			}
		}

		/**
		 * return the comments array, which comes from the raw XML.
		 * @return comments
		 * @see Comments
		 */
		public Comments getXpathComments() {
			CLDRFile file = rawXmlFactory.make(getLocaleID(), false, true);
			return file.getXpath_comments();
		}

		/**
		 * set the comments array, which comes from the raw XML.
		 * @see Comments
		 */
		public void setXpathComments(Comments path) {
			this.xpath_comments = path;
		}

		/** 
		 * TODO: This could take a while (given vetting rules)- do we need it?
		 */
		public int size() {
			throw new InternalError("not implemented yet");
		}

		/**
		 * Cache of iterators over various things
		 */
		Hashtable keySets = new Hashtable();

		/**
		 * Return an iterator for the current set.
		 */
		@Override
		public Iterator iterator() {
			String k = getLocaleID();
			Set s = null; // = (Set)keySets.get(k);
			if(s==null) {
//				System.err.println("CCLCDRDBSource iterator: " + k);
				s = keySet();
				//keySets.put(k,s);
			}
			return s.iterator();
		}

		/** 
		 * Return an iterator for a specific xpath prefix. This is faster than iterating over all
		 * functions, and discarding the ones the caller doesn't want. 
		 * @param prefix prefix of xpaths 
		 * @return an iterator over the specified paths.
		 */
		@Override
		public Iterator<String> iterator(String prefix) {
			if(finalData) {
				return super.iterator(prefix); // no optimization for this, yet
			} else {
				com.ibm.icu.dev.test.util.ElapsedTimer et;
				if(SHOW_TIMES) et= new com.ibm.icu.dev.test.util.ElapsedTimer();
				Iterator i =  prefixKeySet(prefix).iterator();
				if(SHOW_TIMES)   logger.info(et + " for iterator on " + getLocaleID() + " prefix " + prefix);
				return i;
			}
		}

		/**
		 * Returns the set of all xpaths in this source.
		 */
		private Set keySet() {
			String locale = getLocaleID();
			Connection conn = sm.dbUtils.getDBConnection();
			PreparedStatement ps = null;
			ResultSet rs = null;
			try {
				Set<String> s = null;
				
				if(dbEntry!=null) {
					s = (Set<String>) dbEntry.get(locale,Key.OLDKEYSET);
					if(s!=null) {
						//if(DEBUG) System.err.println("Re-used keyset");
						return s;
					}
				}
				
				ps = prepareStatement(conn, finalData ? keyVettingSet : keySet);
				ps.setString(1, locale);
				rs = ps.executeQuery();

				// TODO: is there a better way to map a ResultSet into a Set?
				s = new HashSet<String>();
				while(rs.next()) {
					int xpathid = rs.getInt(1);
					String xpath = rs.getString(2);
					xpt.setById(xpathid, xpath); // Add xpath to table for caching.
					if(finalData==true) {
						if(xpathThatNeedsOrig(xpath)) {
							//                            System.err.println("@@ munging xpath:"+xpath+" ("+xpathid+")");
							xpath = getOrigXpathFromCache(xpathid);
							//                            System.err.println("-> "+xpath);
						}


					}
					s.add(xpath); // xpath
				}
				
				if(dbEntry!=null) {
					dbEntry.put(locale, Key.OLDKEYSET, s);
				}
				// if(finalData) System.err.println("@@ end KS of "+locale);
				/*
			// keySet has  prov/unc already.
			if(finalData) {
				// also add provisional and unconfirmed items

				// provisional: "at least one Vetter has voted for it"
				// unconfirmed: "a Guest Vetter has voted for it"
				stmts.keyUnconfirmedSet.setString(1,locale);
				rs = stmts.keyUnconfirmedSet.executeQuery();
				while(rs.next()) {
					int xpathid = rs.getInt(1);
					String xpath = (xpt.getById(xpathid));
					s.add(xpath);
				}
				rs.close();

				// Now, add items that had no votes. 
				stmts.keyNoVotesSet.setString(1,locale);
				rs = stmts.keyNoVotesSet.executeQuery();
				while(rs.next()) {
					int xpathid = rs.getInt(1);
					String xpath = (xpt.getById(xpathid));
					s.add(xpath);
				}
				rs.close();
			}
				 */
				return Collections.unmodifiableSet(s);
			} catch(SQLException se) {
				logger.severe("CLDRDBSource: Failed to query source ("+tree + "/" + locale +"): " + DBUtils.unchainSqlException(se));
				throw new InternalError("CLDRDBSource: Failed to query source ("+tree + "/" + locale +"): " + DBUtils.unchainSqlException(se));
			} finally {
			    DBUtils.close(rs, ps, conn);
			}
		}

		/**
		 * is this an xpath which requires the 'origXpath' to make sense of it?
		 * i.e. 'alias' contains attribute data 
		 * TODO: discover this dynamically 
		 */
		final private boolean xpathThatNeedsOrig(String xpath) {
			if(xpath.endsWith("/minDays") ||
					xpath.endsWith("/default") ||
					xpath.endsWith("/alias") ||
					xpath.endsWith("/orientation") ||
					xpath.endsWith("/weekendStart") ||
					xpath.endsWith("/weekendEnd") ||
					xpath.endsWith("/measurementSystem") ||
					xpath.endsWith("/singleCountries") ||
					xpath.endsWith("/abbreviationFallback") ||
					xpath.endsWith("/preferenceOrdering") ||
					xpath.endsWith("/inList") ||
					xpath.endsWith("/firstDay") ) {
				return true;
			}
			return false;
		}


		/**
		 * return the keyset over a certain prefix
		 */
		private Set prefixKeySet(String prefix) {
			//        String locale = getLocaleID();
			//        synchronized (conn) {
			try {
				//                stmts.keySet.setString(1,locale);
				ResultSet rs = getPrefixKeySet(prefix);

				// TODO: is there a better way to map a ResultSet into a Set?
				Set<String> s = new HashSet<String>();
				//                System.err.println("@tlh: " + "BEGIN");
				while(rs.next()) {
				    int id = rs.getInt(1);
				    String xpath = rs.getString(2);
				    xpt.setById(id, xpath); // cache id-xpathid pair
					//if(-1!=xpath.indexOf("tlh")) {
					//    xpath = xpath.replaceAll("\\[@draft=\"true\"\\]","");
					//                        System.err.println("@tlh: " + xpath);
					//}
					s.add(xpath); // xpath
					//rs.getString(2); // origXpath
				}
				//                System.err.println("@tlh: " + "END");
				return Collections.unmodifiableSet(s);
				// TODO: 0
			} catch(SQLException se) {
				logger.severe("CLDRDBSource: Failed to query source ("+tree + "/" + getLocaleID() +"): " + DBUtils.unchainSqlException(se));
				return null;
			}
			//        }
		}

		/**
		 * Table of all aliases
		 */
		private HashMap<String, TreeMap<String, String>> aliasTable = new HashMap();

		/**
		 * get a copy of all aliases 
		 * @return list of aliases
		 */
		@Override
		protected synchronized TreeMap<String, String> getAliases() {
			String locale = getLocaleID();
			TreeMap<String, String> output = aliasTable.get(locale);
			if(output == null) {
				output = new TreeMap<String, String>();
				Connection conn = sm.dbUtils.getDBConnection();
				PreparedStatement ps = null;
				ResultSet rs = null;
				try {
				    ps = prepareStatement(conn, keyASet);
					ps.setString(1,locale);
					rs = ps.executeQuery();

					// TODO: is there a better way to map a ResultSet into a Set?
//						Set s = new HashSet();
					while(rs.next()) {
						int fullPathId=rs.getInt(1);
						String fullPath = rs.getString(2);
						xpt.setById(fullPathId, fullPath); // Add xpath to table for caching.
						// if(path.indexOf("/alias")<0) { throw new InternalError("aliasIteratorBroken: " + path); }
						//     String fullPath = getFullPathAtDPath(path);
						//System.out.println("oa: " + locale +" : " + path + " - " + fullPath);
						Alias temp = XMLSource.Alias.make(fullPath);
						if (temp == null) continue;
						//System.out.println("aa: " + path + " - " + fullPath + " -> " + temp.toString());
						output.put(temp.getOldPath(), temp.getNewPath());
					}
					aliasTable.put(locale,output);
					// TODO: 0
				} catch(SQLException se) {
					logger.severe("CLDRDBSource: Failed to query A source ("+tree + "/" + locale +"): " + DBUtils.unchainSqlException(se));
					return null;
				} finally {
					DBUtils.close(rs, ps, conn);
				}
			}
			return output;
		}

		/**
		 * @deprecated
		 */
		public final XPathTable xpt = CLDRDBSourceFactory.this.xpt;

		//    private void initConn(Connection conn, Factory rawXmlFactory) {
		//        throw new InternalError("not imp: initConn()");
		//    }



		/** 
		 * private c'tor.  inherits factory and xpath table.
		 * Caller wil fill in other stuff
		 */
		//    private CLDRDBSource(CLDRFile.Factory nFactory) {
		//            rawXmlFactory = nFactory; 
		////            xpt = nXpt; 
		//    }
		//    /**
		//     * Bootstrap Factory function for internal use. 
		//     * @param theDir directory for XML data
		//     * @param xpt XPathTable to use
		//     * @param localeID locale id to use
		//     * @param conn the database connection (shared)
		//     * @param user the user to create for
		//     */
		//    public static CLDRDBSource createInstance(String theDir, XPathTable xpt, CLDRLocale localeID, Connection conn,
		//        UserRegistry.User user) {
		//        return createInstance(theDir, xpt, localeID, conn, user, false);
		//    }
		//    /**
		//     * Factory function for internal use
		//     * @param theDir directory for XML data
		//     * @param xpt XPathTable to use
		//     * @param locale locale id to use
		//     * @param conn the database connection (shared)
		//     * @param user the user to create for
		//     * @param finalData true if to only return final (vettd) data
		//     */
		//    public static CLDRDBSource createInstance(String theDir, XPathTable xpt, CLDRLocale locale,
		//            Connection conn, UserRegistry.User user, boolean finalData) {
		//        CLDRFile.Factory afactory = CLDRFile.SimpleFactory.make(theDir,".*");
		//        CLDRDBSource result =  new CLDRDBSource(afactory, xpt);
		//        result.dir = theDir;
		//        result.setLocaleID(locale.toString());
		//        result.initConn(conn, afactory);
		//        result.user = user;
		//        result.finalData = finalData;
		//        return result;
		//    }



		public CLDRDBSource(CLDRLocale locale, boolean finalData) {
			this.finalData=finalData;
			this.setLocaleAndValidate(locale.toString());
		}



		//    public void vettingMode(Vetting v) {
		//        vetting = v;
		//    }

		/**
		 * Cloner. Shares the DB connection. 
		 */
		public Object clone() {
			try {
				CLDRDBSource result = (CLDRDBSource) super.clone();
				// copy junk
				//            result.xpath_comments = xpath_comments; // TODO: clone it.
				// Copy SHARED things
				//            result.xpt = xpt; 
				//            result.dir = dir;
				result.user = user;
				//            result.conn = conn;  // gets set twice. but don't call initConn because other fields are still valid if it's just a clone.
				//            result.rawXmlFactory = rawXmlFactory;
				//            result.stmts = stmts;
				//            result.srcHash = srcHash;
				result.aliasTable = aliasTable;
				result.finalData = finalData;
				//            result.vetting = vetting;
				result.dbEntry = dbEntry;
				// do something here?
				return result;
			} catch (CloneNotSupportedException e) {
				throw new InternalError("should never happen");
			}
		}

		/**
		 * @see com.ibm.icu.util.Freezable#freeze()
		 */
		// Lockable things
		public Object freeze() {
			locked = true;
			return this;
		}


		// TODO: remove this, implement as iterator( stringPrefix)
		/**
		 * get the keyset over a prefix
		 */
		public java.sql.ResultSet getPrefixKeySet(String prefix) {
			String locale = getLocaleID();
			Connection conn = sm.dbUtils.getDBConnection();
			PreparedStatement ps = null;
			ResultSet rs = null;
			try {
			    ps = prepareStatement(conn, queryXpathPrefixes);
				ps.setString(1,prefix+"%");
				ps.setString(2,locale);
				rs = ps.executeQuery();
			} catch(SQLException se) {
				logger.severe("CLDRDBSource: Failed to getPrefixKeySet ("+tree + "/" + locale +"): " + DBUtils.unchainSqlException(se));
				return null;
			} finally {
				DBUtils.close(rs, ps, conn);
			}

			return rs;
		}
	}

	public String getSourceRevision(CLDRLocale locale) {
		int id = getSourceId(tree, locale.toString());
		if(id != -1) {
			return getSourceRevision(id);
		} else {
			return "[Unknown source:"+locale.toString()+"]";
		}
	}

	static boolean complained_about_slower_api= false;
	static protected void complain_about_slower_api() {
		if(!complained_about_slower_api) {
			complained_about_slower_api = true;
			System.err.println("CLDRDBSourceFactory: Note: instead of CLDRDBSource.putValueAtDPath() and CLDRDBSource.putFullPathAtDPath(), use CLDRDBSource.putValueAtPath().");
		}
		return;
	}

	public int update() {
		return update(null, null);
	}
	public int update(Connection inConn) {
		return update(null, inConn);
	}

    @Override
    public String getSourceDirectory() {
        throw new UnsupportedOperationException();
    }

    @Override
    protected CLDRFile handleMake(String localeID, boolean resolved,
            DraftStatus madeWithMinimalDraftStatus) {
        CLDRFile file;
        if (resolved) {
            file = new CLDRFile(makeResolvingSource(localeID, madeWithMinimalDraftStatus));
        } else {
            file = new CLDRFile(getInstance(CLDRLocale.getInstance(localeID), false));
        }
        return file.setAlternateSupplementalDirectory(getAlternateSupplementalDirectory());
    }

    @Override
    protected DraftStatus getMinimalDraftStatus() {
        // TODO Auto-generated method stub
        return DraftStatus.unconfirmed;
    }

    @SuppressWarnings("unchecked")
    @Override
    protected Set<String> handleGetAvailable() {
        // TODO: optimize
        File inFiles[] = getInFiles();
        Set<String> s = new HashSet<String>();
        if(inFiles == null) {
            return null;
            //            throw new RuntimeException("Can't load CLDR data files from " + dir);
        }
        int nrInFiles = inFiles.length;

        for(int i=0;i<nrInFiles;i++) {
            String localeName = inFiles[i].getName();
            int dot = localeName.indexOf('.');
            if(dot !=  -1) {
                localeName = localeName.substring(0,dot);
                s.add(localeName);
            }
        }
        return s;
    }
    
    private ErrorCheckManager ecm = null;

	public synchronized ErrorCheckManager getErrorCheckManager() {
		if(ecm == null) {
			ecm = new ErrorCheckManager(sm);
		}
		return ecm;
	}
	public synchronized ErrorChecker getErrorChecker() {
		//return getErrorCheckManager().getErrorChecker();
		
		return new CachingErrorChecker(sm);
	}

	@Override
	public CacheableXMLSource getSourceFromCache(CLDRLocale locale,
			boolean finalData) {
		// TODO Auto-generated method stub
		return cache.getSource(locale, finalData);
	}
	
	/**
	 * Open a 'usage entry' over this 
	 * @param x
	 * @return
	 */
	public DBEntry openEntry(XMLSource x) {
		if(x instanceof CLDRDBSource) {
			return new DBEntry((CLDRDBSource)x);
		} else {
			return null; 
		}
	}
	/**
	 * Open a 'usage entry' over this 
	 * @param x
	 * @return
	 */
	public DBEntry openEntry(Factory x) {
		if(x instanceof SubFactory) {
			return new DBEntry((SubFactory)x);
		} else {
			return null; 
		}
	}

	
	public SubFactory getFactory(final boolean finalData) {
		return new SubFactory(finalData);
	}

	public Appendable stats(Appendable output) throws IOException {
		return output.append("DBSrc: #OpenEntries"+allOpen.size());
	}
}
