/*
 * Copyright (C) 2004-2011 IBM Corporation and Others. All Rights Reserved.
 */
package org.unicode.cldr.web;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.Properties;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;

import org.unicode.cldr.util.CLDRLocale;

import com.ibm.icu.text.UnicodeSet;

//import org.apache.tomcat.dbcp.dbcp.BasicDataSource;

/**
 * All of the database related stuff has been moved here.
 * 
 * @author srl
 *
 */
public class DBUtils {

	private static DBUtils instance = null;
	private static final String JDBC_SURVEYTOOL = ("jdbc/SurveyTool");
	private static DataSource datasource = null;
	// DB stuff
	public static String db_driver = null;
	public static String db_protocol = null;
	public static String CLDR_DB_U = null;
	public static String CLDR_DB_P = null;
	public static String cldrdb_u = null;
	public static String CLDR_DB;
	public static String cldrdb = null;
	public static String CLDR_DB_CREATESUFFIX = null;
	public static String CLDR_DB_SHUTDOWNSUFFIX = null;
	public static boolean db_Derby = false;
	public static boolean db_Mysql = false;
	// === DB workarounds :(
	public static String DB_SQL_IDENTITY = "GENERATED ALWAYS AS IDENTITY";
	public static String DB_SQL_VARCHARXPATH = "varchar(1024)";
	public static String DB_SQL_WITHDEFAULT = "WITH DEFAULT";
	public static String DB_SQL_TIMESTAMP0 = "TIMESTAMP";
	public static String DB_SQL_CURRENT_TIMESTAMP0 = "CURRENT_TIMESTAMP";
	public static String DB_SQL_MIDTEXT = "VARCHAR(1024)";
	public static String DB_SQL_BIGTEXT = "VARCHAR(16384)";
	public static String DB_SQL_UNICODE = "VARCHAR(16384)"; // unicode type
															// string
	public static String DB_SQL_ALLTABLES = "select tablename from SYS.SYSTABLES where tabletype='T'";
	public static String DB_SQL_BINCOLLATE = "";
	public static String DB_SQL_BINTRODUCER = "";
	static int db_number_cons = 0;
	static int db_number_pool_cons = 0;
    private static StackTracker tracker = null; // new StackTracker(); - enable, to track unclosed connections
	
	public static void closeDBConnection(Connection conn) {
		if (conn != null) {
		    if(SurveyMain.isUnofficial && tracker!=null) {
		        tracker.remove(conn);
		    }
			try {
				conn.close();
			} catch (SQLException e) {
				System.err.println(DBUtils.unchainSqlException(e));
				e.printStackTrace();
			}
			db_number_cons--;
			if (datasource != null) {
				db_number_pool_cons--;
			}
			if (false && SurveyMain.isUnofficial) {
				System.err.println("SQL -conns: "
						+ db_number_cons
						+ " "
						+ ((datasource == null) ? ""
								: (" pool:" + db_number_pool_cons)));
			}
		}
	}
	public static final String escapeBasic(byte what[]) {
		return escapeLiterals(what);
	}

	public static final String escapeForMysql(byte what[]) {
		boolean hasEscapeable = false;
		boolean hasNonEscapeable = false;
		for (byte b : what) {
			int j = ((int) b) & 0xff;
			char c = (char) j;
			if (escapeIsBasic(c)) {
				continue;
			} else if (escapeIsEscapeable(c)) {
				hasEscapeable = true;
			} else {
				hasNonEscapeable = true;
			}
		}
		if (hasNonEscapeable) {
			return escapeHex(what);
		} else if (hasEscapeable) {
			return escapeLiterals(what);
		} else {
			return escapeBasic(what);
		}
	}

	public static String escapeForMysql(String what)
			throws UnsupportedEncodingException {
		if (what == null) {
			return "NULL";
		} else if (what.length() == 0) {
			return "\"\"";
		} else {
			return escapeForMysql(what.getBytes("ASCII"));
		}
	}

	public static String escapeForMysqlUtf8(String what)
			throws UnsupportedEncodingException {
		if (what == null) {
			return "NULL";
		} else if (what.length() == 0) {
			return "\"\"";
		} else {
			return escapeForMysql(what.getBytes("UTF-8"));
		}
	}

	public static final String escapeHex(byte what[]) {
		StringBuffer out = new StringBuffer("x'");
		for (byte b : what) {
			int j = ((int) b) & 0xff;
			if (j < 0x10) {
				out.append('0');
			}
			out.append(Integer.toHexString(j));
		}
		out.append("'");
		return out.toString();
	}

	public static final boolean escapeIsBasic(char c) {
		return ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
				|| (c >= '0' && c <= '9') || (c == ' ' || c == '.' || c == '/'
				|| c == '[' || c == ']' || c == '=' || c == '@' || c == '_'
				|| c == ',' || c == '&' || c == '-' || c == '(' || c == ')'
				|| c == '#' || c == '$' || c == '!'));
	}

	public static final boolean escapeIsEscapeable(char c) {
		return (c == 0 || c == '\'' || c == '"' || c == '\b' || c == '\n'
				|| c == '\r' || c == '\t' || c == 26 || c == '\\');
	}

	public static final String escapeLiterals(byte what[]) {
		StringBuffer out = new StringBuffer("'");
		for (byte b : what) {
			int j = ((int) b) & 0xff;
			char c = (char) j;
			switch (c) {
			case 0:
				out.append("\\0");
				break;
			case '\'':
				out.append("'");
				break;
			case '"':
				out.append("\\");
				break;
			case '\b':
				out.append("\\b");
				break;
			case '\n':
				out.append("\\n");
				break;
			case '\r':
				out.append("\\r");
				break;
			case '\t':
				out.append("\\t");
				break;
			case 26:
				out.append("\\z");
				break;
			case '\\':
				out.append("\\\\");
				break;
			default:
				out.append(c);
			}
		}
		out.append("'");
		return out.toString();
	}

	public synchronized static DBUtils getInstance() {
		if (instance == null) {
			instance = new DBUtils();
		}
		return instance;
	}

	public static void makeInstanceFrom(DataSource dataSource2) {
		if(instance==null) {
			instance = new DBUtils(dataSource2);
		} else {
			throw new IllegalArgumentException("Already initted.");
		}
	}
	// fix the UTF-8 fail
	public static final String getStringUTF8(ResultSet rs, int which)
			throws SQLException {
		if (db_Derby) { // unicode
			return rs.getString(which);
		}
		byte rv[] = rs.getBytes(which);
		if (rv != null) {
			String unicode;
			try {
				unicode = new String(rv, "UTF-8");
			} catch (UnsupportedEncodingException e) {
				e.printStackTrace();
				throw new InternalError(e.toString());
			}
			return unicode;
		} else {
			return null;
		}
	}

	public static boolean hasTable(Connection conn, String table) {
		String canonName = db_Derby ? table.toUpperCase() : table;
		try {
			ResultSet rs;

			if (db_Derby) {
				DatabaseMetaData dbmd = conn.getMetaData();
				rs = dbmd.getTables(null, null, canonName, null);
			} else {
				Statement s = conn.createStatement();
				rs = s.executeQuery("show tables like '" + canonName + "'");
			}

			if (rs.next() == true) {
				rs.close();
				// System.err.println("table " + canonName + " did exist.");
				return true;
			} else {
				System.err.println("table " + canonName + " did not exist.");
				return false;
			}
		} catch (SQLException se) {
			SurveyMain.busted("While looking for table '" + table + "': ", se);
			return false; // NOTREACHED
		}
	}

	public static final void setStringUTF8(PreparedStatement s, int which,
			String what) throws SQLException {
		if (db_Derby) {
			s.setString(which, what);
		} else {
			byte u8[];
			if (what == null) {
				u8 = null;
			} else {
				try {
					u8 = what.getBytes("UTF-8");
				} catch (UnsupportedEncodingException e) {
					e.printStackTrace();
					throw new InternalError(e.toString());
				}
			}
			s.setBytes(which, u8);
		}
	}

	static int sqlCount(WebContext ctx, Connection conn, PreparedStatement ps) {
		int rv = -1;
		try {
			ResultSet rs = ps.executeQuery();
			if (rs.next()) {
				rv = rs.getInt(1);
			}
			rs.close();
		} catch (SQLException se) {
			String complaint = " Couldn't query count - "
					+ unchainSqlException(se) + " -  ps";
			System.err.println(complaint);
			ctx.println("<hr><font color='red'>ERR: " + complaint
					+ "</font><hr>");
		}
		return rv;
	}

	static int sqlCount(WebContext ctx, Connection conn, String sql) {
		int rv = -1;
		try {
			Statement s = conn.createStatement();
			ResultSet rs = s.executeQuery(sql);
			if (rs.next()) {
				rv = rs.getInt(1);
			}
			rs.close();
			s.close();
		} catch (SQLException se) {
			String complaint = " Couldn't query count - "
					+ unchainSqlException(se) + " - " + sql;
			System.err.println(complaint);
			ctx.println("<hr><font color='red'>ERR: " + complaint
					+ "</font><hr>");
		}
		return rv;
	}
	
	public String[] sqlQueryArray(Connection conn, String str) throws SQLException {
		return sqlQueryArrayArray(conn,str)[0];
	}
	
	public String[][] sqlQueryArrayArray(Connection conn, String str) throws SQLException {
		Statement s  = null;
		ResultSet rs  = null;
		try {
			s = conn.createStatement();
			rs = s.executeQuery(str);
			ArrayList<String[]> al = new ArrayList<String[]>();
			while(rs.next()) {
				al.add(arrayOfResult(rs));
			}
			return al.toArray(new String[al.size()][]);
		} finally {
			if(rs!=null) {
				rs.close();
			}
			if(s!=null) {
				s.close();
			}
		}
	}
//
//	private String[] arrayOfResult(ResultSet rs) throws SQLException {
//		ResultSetMetaData rsm = rs.getMetaData();
//		String ret[] = new String[rsm.getColumnCount()];
//		for(int i=0;i<ret.length;i++) {
//			ret[i]=rs.getString(i+1);
//		}
//		return ret;
//	}
	public String sqlQuery(Connection conn, String str) throws SQLException {
		return sqlQueryArray(conn,str)[0];
	}
	

	static int sqlUpdate(WebContext ctx, Connection conn, PreparedStatement ps) {
		int rv = -1;
		try {
			rv = ps.executeUpdate();
		} catch (SQLException se) {
			String complaint = " Couldn't sqlUpdate  - "
					+ unchainSqlException(se) + " -  ps";
			System.err.println(complaint);
			ctx.println("<hr><font color='red'>ERR: " + complaint
					+ "</font><hr>");
		}
		return rv;
	}

	public static final String unchainSqlException(SQLException e) {
		String echain = "SQL exception: \n ";
		SQLException laste = null;
		while (e != null) {
			laste = e;
			echain = echain + " -\n " + e.toString();
			e = e.getNextException();
		}
		String stackStr = "\n unknown Stack";
		try {
			StringWriter asString = new StringWriter();
			laste.printStackTrace(new PrintWriter(asString));
			stackStr = "\n Stack: \n " + asString.toString();
		} catch (Throwable tt) {
			stackStr = "\n unknown stack (" + tt.toString() + ")";
		}
		return echain + stackStr;
	}

	File dbDir = null;

	// File dbDir_u = null;
	static String dbInfo = null;
	
	public boolean isBogus() {
		return (datasource==null);
	}

	private DBUtils() {
		// Initialize DB context
		try {
			Context initialContext = new InitialContext();
			datasource = (DataSource) initialContext.lookup("java:comp/env/" + JDBC_SURVEYTOOL);
			//datasource = (DataSource) envContext.lookup("ASDSDASDASDASD");
			
			if(datasource!=null) {
				System.err.println("Got datasource: " + datasource.toString());
			}
			Connection c = null;
			try {
				if(datasource!=null) {
					c = datasource.getConnection();
					DatabaseMetaData dmd = c.getMetaData();
					dbInfo = dmd.getDatabaseProductName()+" v"+dmd.getDatabaseProductVersion();
					loadSqlHacks();
					System.err.println("Metadata: "+ dbInfo);
				}
			} catch (SQLException  t) {
                datasource = null;
				throw new IllegalArgumentException(getClass().getName()+": WARNING: we require a JNDI datasource.  "
								+ "'"+JDBC_SURVEYTOOL+"'"
								+ ".getConnection() returns : "
								+ t.toString()+"\n"+unchainSqlException(t));
			} finally {
				if (c != null)
					try {
						c.close();
					} catch (Throwable tt) {
						System.err.println("Couldn't close datasource's conn: "
								+ tt.toString());
						tt.printStackTrace();
					}
			}
		} catch (NamingException nc) {
			nc.printStackTrace();
			datasource = null;
			throw new Error("Couldn't load context " + JDBC_SURVEYTOOL
					+ " - not using datasource.",nc);
		}
		
	}

	public DBUtils(DataSource dataSource2) {
		datasource=dataSource2;
		Connection c = null;
		try {
			if(datasource!=null) {
				c = datasource.getConnection();
				DatabaseMetaData dmd = c.getMetaData();
				dbInfo = dmd.getDatabaseProductName()+" v"+dmd.getDatabaseProductVersion();
				loadSqlHacks();
				System.err.println("Metadata: "+ dbInfo);
			}
		} catch (SQLException  t) {
            datasource = null;
			throw new IllegalArgumentException(getClass().getName()+": WARNING: we require a JNDI datasource.  "
							+ "'"+JDBC_SURVEYTOOL+"'"
							+ ".getConnection() returns : "
							+ t.toString()+"\n"+unchainSqlException(t));
		} finally {
			if (c != null)
				try {
					c.close();
				} catch (Throwable tt) {
					System.err.println("Couldn't close datasource's conn: "
							+ tt.toString());
					tt.printStackTrace();
				}
		}
	}
	private void loadSqlHacks() {
	    System.err.println("Loading hacks for " + dbInfo);
        if (dbInfo.contains("Derby")) {
            db_Derby = true;
            System.err.println("Note: derby mode");
        } else if (dbInfo.contains("MySQL")) {
            System.err.println("Note: mysql mode");
            db_Mysql = true;
            DB_SQL_IDENTITY = "AUTO_INCREMENT PRIMARY KEY";
            DB_SQL_BINCOLLATE = " COLLATE latin1_bin ";
            DB_SQL_VARCHARXPATH = "TEXT(1000) CHARACTER SET latin1 "
                    + DB_SQL_BINCOLLATE;
            DB_SQL_BINTRODUCER = "_latin1";
            DB_SQL_WITHDEFAULT = "DEFAULT";
            DB_SQL_TIMESTAMP0 = "DATETIME";
            DB_SQL_CURRENT_TIMESTAMP0 = "'1999-12-31 23:59:59'"; // NOW?
            DB_SQL_MIDTEXT = "TEXT(1024)";
            DB_SQL_BIGTEXT = "TEXT(16384)";
            DB_SQL_UNICODE = "BLOB";
            DB_SQL_ALLTABLES = "show tables";
        } else {
            System.err.println("*** WARNING: Don't know what kind of database is "
                    + dbInfo  + " - might be interesting!");
        }
    }
    public void doShutdown() throws SQLException {
		datasource = null;
		if(this.db_number_cons>0) {
		    System.err.println("DBUtils: removing my instance. " + this.db_number_cons + " still open?\n"+tracker);
		}
		if(tracker!=null) tracker.clear();
		instance = null;
	}

	/**
	 * @deprecated Use {@link #getDBConnection()} instead
	 */
	public final Connection getDBConnection(SurveyMain surveyMain) {
		return getDBConnection();
	}
	
	public final Connection getDBConnection() {
		return getDBConnection("");
	}

	/**
	 * @deprecated Use {@link #getDBConnection(String)} instead
	 */
	public final Connection getDBConnection(SurveyMain surveyMain, String options) {
		return getDBConnection(options);
	}
	
	public Connection getDBConnection(String options) {
		try {
			db_number_cons++;

			if (datasource != null) {
				db_number_pool_cons++;
				if (false&&SurveyMain.isUnofficial) {
					System.err.println("SQL  +conns: " + db_number_cons
							+ " Pconns: " + db_number_pool_cons);
				}
				Connection c = datasource.getConnection();
				c.setAutoCommit(false);
				if(SurveyMain.isUnofficial&&tracker!=null) tracker.add(c);
				return c;
			}
			throw new InternalError("Error: we only support JNDI datasources. Contact srl.\n");
		} catch (SQLException se) {
			se.printStackTrace();
			SurveyMain.busted("Fatal in getDBConnection", se);
			return null;
		}
	}

	void setupDBProperties(SurveyMain surveyMain, Properties cldrprops) {
//		db_driver = cldrprops.getProperty("CLDR_DB_DRIVER",
//				"org.apache.derby.jdbc.EmbeddedDriver");
//		db_protocol = cldrprops.getProperty("CLDR_DB_PROTOCOL", "jdbc:derby:");
//		CLDR_DB_U = cldrprops.getProperty("CLDR_DB_U", null);
//		CLDR_DB_P = cldrprops.getProperty("CLDR_DB_P", null);
		CLDR_DB = cldrprops.getProperty("CLDR_DB", "cldrdb");
		dbDir = new File(SurveyMain.cldrHome, CLDR_DB);
		cldrdb = cldrprops.getProperty("CLDR_DB_LOCATION",
				dbDir.getAbsolutePath());
		CLDR_DB_CREATESUFFIX = cldrprops.getProperty("CLDR_DB_CREATESUFFIX",
				";create=true");
		CLDR_DB_SHUTDOWNSUFFIX = cldrprops.getProperty(
				"CLDR_DB_SHUTDOWNSUFFIX", "jdbc:derby:;shutdown=true");
	}

	public void startupDB(SurveyMain sm,
			CLDRProgressIndicator.CLDRProgressTask progress) {
	    System.err.println("StartupDB: datasource="+ datasource);
	    if(datasource == null) {
	        throw new RuntimeException("JNDI required: http://cldr.unicode.org/development/running-survey-tool/cldr-properties/db");
	    }

		progress.update("Using datasource..."+dbInfo); // restore

	}
	/**
	 * Shortcut for certain statements.
	 * @param conn
	 * @param str
	 * @return
	 * @throws SQLException
	 */
    public static final PreparedStatement prepareForwardReadOnly(Connection conn, String str) throws SQLException {
    	return conn.prepareStatement(str,ResultSet.TYPE_FORWARD_ONLY,ResultSet.CONCUR_READ_ONLY);
    }

    /**
     * prepare statements for this connection 
     * @throws SQLException 
     **/ 
    public static final PreparedStatement prepareStatementForwardReadOnly(Connection conn, String name, String sql) throws SQLException {
    	PreparedStatement ps = null;
    	try {       
    		ps = prepareForwardReadOnly(conn, sql);
    	} finally {
    		if(ps==null) {
    			System.err.println("Warning: couldn't initialize "+name+" from " + sql);
    		}
    	}
    	//            if(false) System.out.println("EXPLAIN EXTENDED " + sql.replaceAll("\\?", "'?'")+";");
    	//        } catch ( SQLException se ) {
    	//            String complaint = "Vetter:  Couldn't prepare " + name + " - " + DBUtils.unchainSqlException(se) + " - " + sql;
    	//            logger.severe(complaint);
    	//            throw new RuntimeException(complaint);
    	//        }
    	return ps;
    }
    
    /**
     * prepare statements for this connection 
     * @throws SQLException 
     **/ 
    public static final PreparedStatement prepareStatement(Connection conn, String name, String sql) throws SQLException {
    	PreparedStatement ps = null;
    	try {       
    		ps =  conn.prepareStatement(sql);
    	} finally {
    		if(ps==null) {
    			System.err.println("Warning: couldn't initialize "+name+" from " + sql);
    		}
    	}
    	//            if(false) System.out.println("EXPLAIN EXTENDED " + sql.replaceAll("\\?", "'?'")+";");
    	//        } catch ( SQLException se ) {
    	//            String complaint = "Vetter:  Couldn't prepare " + name + " - " + DBUtils.unchainSqlException(se) + " - " + sql;
    	//            logger.severe(complaint);
    	//            throw new RuntimeException(complaint);
    	//        }
    	return ps;
    }
	/**
	 * Close all of the objects in order, if not null. Knows how to close Connection, Statement, ResultSet, otherwise you'll get an IAE.
	 * @param a1
	 * @throws SQLException
	 */
	public static void close(Object... list) throws SQLException {
		for(Object o : list) {
//			if(o!=null) {
//				System.err.println("Closing " + an(o.getClass().getSimpleName())+" " + o.getClass().getName());
//			}
			if(o == null) {
				continue;
			} else if(o instanceof Connection ) {
				DBUtils.closeDBConnection((Connection) o);
			} else if (o instanceof Statement) {
				((Statement)o).close();
			} else if (o instanceof ResultSet) {
				((ResultSet)o).close();
			} else if(o instanceof DBCloseable) {
				((DBCloseable)o).close();
			} else {
				throw new IllegalArgumentException("Don't know how to close "+an(o.getClass().getSimpleName())+" " + o.getClass().getName());
			}
		}
	}

	private static final UnicodeSet vowels = new UnicodeSet("[aeiouAEIOUhH]");
	/**
	 * Print A or AN appropriately.
	 * @param str
	 * @return
	 */
	private static String an(String str) {
		boolean isVowel = vowels.contains(str.charAt(0));
		return isVowel?"an":"a";
	}
	public boolean hasDataSource() {
		return(datasource!=null);
	}
    /**
	 * @param conn
	 * @param sql
	 * @param args
	 * @return
	 * @throws SQLException
	 */
	public PreparedStatement prepareStatementWithArgs(Connection conn, String sql,
			Object... args) throws SQLException {
		PreparedStatement ps;
		ps = conn.prepareStatement(sql);
		
//		while (args!=null&&args.length==1&&args[0] instanceof Object[]) {
//			System.err.println("Unwrapping " + args + " to " + args[0]);
//		}
		if(args!=null&&args.length>0) {
			for(int i=0;i<args.length;i++) {
				Object o = args[i];
				if(o instanceof String) {
					ps.setString(i+1, (String)o);
				} else if(o instanceof Integer) {
					ps.setInt(i+1, (Integer)o);
				} else if(o instanceof CLDRLocale) { /* toString compatible things */
					ps.setString(i+1, o.toString());
				} else {
					System.err.println("DBUtils: Warning: using toString for unknown object " + o.getClass().getName());
					ps.setString(i+1, o.toString());
				}
			}
		}
		return ps;
	}
    
    private String[][] resultToArrayArray(ResultSet rs) throws SQLException {
		ArrayList<String[]> al = new ArrayList<String[]>();
		while(rs.next()) {
			al.add(arrayOfResult(rs));
		}
		return al.toArray(new String[al.size()][]);
	}
    private Object[][] resultToArrayArrayObj(ResultSet rs) throws SQLException {
		ArrayList<Object[]> al = new ArrayList<Object[]>();
		int colCount = rs.getMetaData().getColumnCount();
		while(rs.next()) {
			al.add(arrayOfResultObj(rs,colCount));
		}
		return al.toArray(new Object[al.size()][]);
	}
	@SuppressWarnings("rawtypes")
	private Map[] resultToArrayAssoc(ResultSet rs) throws SQLException {
		ResultSetMetaData rsm = rs.getMetaData();
		ArrayList<Map<String,Object>> al = new ArrayList<Map<String,Object>>();
		while(rs.next()) {
			al.add(assocOfResult(rs,rsm));
		}
		return al.toArray(new Map[al.size()]);
	}
	
	private Map<String, Object> assocOfResult(ResultSet rs,ResultSetMetaData rsm) throws SQLException {
		Map<String,Object> m = new HashMap<String,Object>(rsm.getColumnCount());
		
		for(int i=1;i<=rsm.getColumnCount();i++) {
			m.put(rsm.getColumnName(i), rs.getObject(i));
		}
		
		return m;
	}

	public String sqlQuery(Connection conn, String sql, Object... args) throws SQLException {
		return sqlQueryArray(conn,sql,args)[0];
	}

	public String[] sqlQueryArray(Connection conn, String sql, Object... args) throws SQLException {
		return sqlQueryArrayArray(conn,sql,args)[0];
	}
	public String[][] sqlQueryArrayArray(Connection conn, String str, Object... args) throws SQLException {
		PreparedStatement ps= null;
		ResultSet rs  = null;
		try {
			ps = prepareStatementWithArgs(conn, str, args);
			
			rs = ps.executeQuery();
			return resultToArrayArray(rs);
		} finally {
			DBUtils.close(rs,ps);
		}
	}
	public Object[][] sqlQueryArrayArrayObj(Connection conn, String str, Object... args) throws SQLException {
		PreparedStatement ps= null;
		ResultSet rs  = null;
		try {
			ps = prepareStatementWithArgs(conn, str, args);
			
			rs = ps.executeQuery();
			return resultToArrayArrayObj(rs);
		} finally {
			DBUtils.close(rs,ps);
		}
	}
	public int sqlUpdate(Connection conn, String str, Object... args) throws SQLException {
		PreparedStatement ps= null;
		try {
			ps = prepareStatementWithArgs(conn, str, args);

			return(ps.executeUpdate());
		} finally {
			DBUtils.close(ps);
		}
	}
	@SuppressWarnings("rawtypes")
	public Map[] sqlQueryArrayAssoc(Connection conn, String sql,
			Object... args) throws SQLException {
		PreparedStatement ps= null;
		ResultSet rs  = null;
		try {
			ps = prepareStatementWithArgs(conn, sql, args);
			
			rs = ps.executeQuery();
			return resultToArrayAssoc(rs);
		} finally {
			DBUtils.close(rs,ps);
		}
	}		
	private String[] arrayOfResult(ResultSet rs) throws SQLException {
		ResultSetMetaData rsm = rs.getMetaData();
		String ret[] = new String[rsm.getColumnCount()];
		for(int i=0;i<ret.length;i++) {
			ret[i]=rs.getString(i+1);
		}
		return ret;
	}
	private Object[] arrayOfResultObj(ResultSet rs, int colCount) throws SQLException {
		Object ret[] = new Object[colCount];
		for(int i=0;i<ret.length;i++) {
			ret[i]=rs.getObject(i+1);
		}
		return ret;
	}
	
	/**
	 * Interface to an object that contains a held Connection
	 * @author srl
	 *
	 */
	public interface ConnectionHolder {
		/**
		 * @return alias to held connection
		 */
		public Connection getConnectionAlias();
	}
	/**
	 * Interface to an object that DBUtils.close can close.
	 * @author srl
	 *
	 */
	public interface DBCloseable {
		/**
		 * Close this object
		 */
		public void close() throws SQLException;
	}
}
