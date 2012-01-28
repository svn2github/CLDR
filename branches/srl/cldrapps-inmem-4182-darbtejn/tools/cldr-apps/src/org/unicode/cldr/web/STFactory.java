/**
 * 
 */
package org.unicode.cldr.web;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.unicode.cldr.icu.LDMLConstants;
import org.unicode.cldr.test.CheckCLDR;
import org.unicode.cldr.test.CheckCLDR.CheckStatus;
import org.unicode.cldr.test.ExampleGenerator;
import org.unicode.cldr.util.CLDRFile;
import org.unicode.cldr.util.CLDRFile.DraftStatus;
import org.unicode.cldr.util.Factory;
import org.unicode.cldr.util.CLDRLocale;
import org.unicode.cldr.util.SimpleXMLSource;
import org.unicode.cldr.util.StackTracker;
import org.unicode.cldr.util.VoteResolver;
import org.unicode.cldr.util.VoteResolver.Status;
import org.unicode.cldr.util.XMLSource;
import org.unicode.cldr.util.XPathParts;
import org.unicode.cldr.util.XPathParts.Comments;
import org.unicode.cldr.web.UserRegistry.User;

import com.ibm.icu.dev.test.util.ElapsedTimer;

/**
 * @author srl
 *
 */
public class STFactory extends Factory implements BallotBoxFactory<UserRegistry.User> {
    public class DataBackedSource extends ReadOnlyAliasTo {
		PerLocaleData ballotBox;
		public DataBackedSource(PerLocaleData makeFrom) {
			super(makeFrom.getLocale());
			ballotBox = makeFrom;
			ballotBox.aliasOf = aliasOf;
		}

		
		/* (non-Javadoc)
		 * @see com.ibm.icu.util.Freezable#freeze()
		 */
		@Override
		public Object freeze() {
			readonly();
			return null;
		}

		/* (non-Javadoc)
		 * @see org.unicode.cldr.util.XMLSource#getFullPathAtDPath(java.lang.String)
		 */
		@Override
		public String getFullPathAtDPath(String path) {
			Map<User,String> m = ballotBox.peekXpathToVotes(path);
			if(m==null || m.isEmpty()) {
				return aliasOf.getFullPathAtDPath(path);
			} else {
				System.err.println("Note: DBS.getFullPathAtDPath() todo!!");
				return aliasOf.getFullPathAtDPath(path);
			}
		}

		/* (non-Javadoc)
		 * @see org.unicode.cldr.util.XMLSource#getValueAtDPath(java.lang.String)
		 */
		@Override
		public String getValueAtDPath(String path) {
			Map<User,String> m = ballotBox.peekXpathToVotes(path);
			if(m==null || m.isEmpty()) {
                String res =  aliasOf.getValueAtDPath(path);
//			    System.err.println("@@@@ ("+this.getLocaleID()+")" + path+"= [alias] "+res);
			    return res;
			} else {
				String res = ballotBox.getResolver(m,path).getWinningValue();
//                System.err.println("@@@@ ("+this.getLocaleID()+")" + path+"= [res] "+res);
                return res;
//				System.err.println("Note: DBS.getValueAtDPath() blindly returning 1st value,  todo!!");
//				return m.entrySet().iterator().next().getValue();
			}
		}

		/* (non-Javadoc)
		 * @see org.unicode.cldr.util.XMLSource#getXpathComments()
		 */
		@Override
		public Comments getXpathComments() {
			return aliasOf.getXpathComments();
		}

		/* (non-Javadoc)
		 * @see org.unicode.cldr.util.XMLSource#iterator()
		 */
		@Override
		public Iterator<String> iterator() {
			if(ballotBox.xpathToVotes == null || ballotBox.xpathToVotes.isEmpty()) {
				return aliasOf.iterator();
			} else {
				System.err.println("Note: DBS.iterator() todo!!");
				return aliasOf.iterator();
			}
		}

		/* (non-Javadoc)
		 * @see org.unicode.cldr.util.XMLSource#putFullPathAtDPath(java.lang.String, java.lang.String)
		 */
		@Override
		public void putFullPathAtDPath(String distinguishingXPath,
				String fullxpath) {
				readonly();
		}

		/* (non-Javadoc)
		 * @see org.unicode.cldr.util.XMLSource#putValueAtDPath(java.lang.String, java.lang.String)
		 */
		@Override
		public void putValueAtDPath(String distinguishingXPath, String value) {
			readonly();
		}

		/* (non-Javadoc)
		 * @see org.unicode.cldr.util.XMLSource#removeValueAtDPath(java.lang.String)
		 */
		@Override
		public void removeValueAtDPath(String distinguishingXPath) {
			readonly();
		}

		/* (non-Javadoc)
		 * @see org.unicode.cldr.util.XMLSource#setXpathComments(org.unicode.cldr.util.XPathParts.Comments)
		 */
		@Override
		public void setXpathComments(Comments comments) {
			readonly();
		}


//		/* (non-Javadoc)
//		 * @see org.unicode.cldr.util.XMLSource#make(java.lang.String)
//		 */
//		@Override
//		public XMLSource make(String localeID) {
//			return makeSource(localeID, this.isResolving());
//		}

//		/* (non-Javadoc)
//		 * @see org.unicode.cldr.util.XMLSource#getAvailableLocales()
//		 */
//		@SuppressWarnings("rawtypes")
//		@Override
//		public Set getAvailableLocales() {
//			return handleGetAvailable();
//		}

//		/* (non-Javadoc)
//		 * @see org.unicode.cldr.util.XMLSource#getSupplementalDirectory()
//		 */
//		@Override
//		public File getSupplementalDirectory() {
//			File suppDir =  new File(getSourceDirectory()+"/../"+"supplemental");
//			return suppDir;
//		}
		
		
//		@Override
//		protected synchronized TreeMap<String, String> getAliases() {
//			if(true) throw new InternalError("NOT IMPLEMENTED.");
//			return null;
//		}

	}

    public static abstract class DoIfNotRecent {
		private long every = 0;
		private long lastTime = 0;
		protected DoIfNotRecent(long every) {
			this.every = every;
		}
		public void doIf() {
			long now = System.currentTimeMillis();
			if((now-lastTime)>every) {
				try {
					handleDo();
					lastTime = now;
				} finally {
					//
				}
			}
		}
		public abstract void handleDo();
	}

	/**
	 * the STFactory maintains exactly one instance of this class per locale it is working with. It contains the XMLSource, Example Generator, etc..
	 * @author srl
	 *
	 */
	private class PerLocaleData implements Comparable<PerLocaleData>, BallotBox<User>  {
        public XMLSource aliasOf;
		private CLDRFile file = null, rFile = null;
		private CLDRLocale locale;
		private CLDRFile oldFile;
		private boolean readonly;
		
		private XMLSource xmlsource;
		
		/* SIMPLE IMP */
		private Map<String, Map<User,String>> xpathToVotes = new HashMap<String,Map<User,String>>();
		
		
		PerLocaleData(CLDRLocale locale) {
			this.locale = locale;
			readonly = isReadOnlyLocale(locale);
			//System.err.println("PerLocaleData:  Hello, " + locale + "   " + (readonly?"(readonly)":""));
			if(!readonly) {
				ElapsedTimer et = new ElapsedTimer("Loading PLD for " + locale);
				Connection conn = null;
				PreparedStatement ps = null;
				ResultSet rs = null;
				int n = 0;
				try {
					conn = DBUtils.getInstance().getDBConnection();
					ps = openQueryByLocale(conn);
					ps.setString(1, locale.getBaseName());
					rs = ps.executeQuery();
					
					while(rs.next()) {
						int xp = rs.getInt(1);
						int submitter = rs.getInt(2);
						String value = DBUtils.getStringUTF8(rs, 3);
						internalSetVoteForValue(sm.reg.getInfo(submitter), sm.xpt.getById(xp), value);
						n++;
					}
					
				} catch (SQLException e) {
					sm.logException(e);
					sm.busted("Could not read locale " + locale, e);
					throw new InternalError("Could not load locale " + locale + " : " + DBUtils.unchainSqlException(e));
				} finally {
					DBUtils.close(rs,ps,conn);
				}
				System.err.println(et + " - read " + n + " items.");
 			}
		}
		
		@Override
		public int compareTo(PerLocaleData arg0) {
			if(this==arg0) {
				return 0;
			} else {
				return locale.compareTo(arg0.locale);
			}
		}

        @Override
		public boolean equals(Object other) {
			if(other==this) {
				return true;
			} else if(!(other instanceof PerLocaleData)) {
				return false;
			} else {
				return ((PerLocaleData)other).locale.equals(locale);
			}
		}
		
		public synchronized CLDRFile getFile(boolean resolved) {
            if(resolved) {
                if(rFile == null) {
                	if(getSupplementalDirectory()==null) throw new InternalError("getSupplementalDirectory() == null!");
                    rFile = new CLDRFile(makeSource(true)).setSupplementalDirectory(getSupplementalDirectory());
                    rFile.getSupplementalDirectory();
                }
                return rFile;
            } else {
                if(file == null) {
                	if(getSupplementalDirectory()==null) throw new InternalError("getSupplementalDirectory() == null!");
                    file = new CLDRFile(makeSource()).setSupplementalDirectory(getSupplementalDirectory());
                }
                return file;
            }
        }

		public CLDRLocale getLocale() { 
			return locale;
		}
		
		public synchronized CLDRFile getOldFile() {
		    if(oldFile==null) {
	            oldFile = sm.getOldFactory().make(locale.getBaseName(), true);
		    }
		    return oldFile;
		}
		
		public VoteResolver<String> getResolver(Map<User, String> m, String path) {
//			if(m==null) throw new InternalError("no Map for " + path);
			if(path==null) throw new IllegalArgumentException("path must not be null");
			updateVoteInfo.doIf();
			VoteResolver<String> r = new VoteResolver<String>();
			XPathParts xpp = new XPathParts(null,null);
			String fullXPath = getOldFile().getFullXPath(path);
			if(fullXPath==null) fullXPath = path; // throw new InternalError("null full xpath for " + path);
			xpp.set(fullXPath);
			final String lastValue = getOldFile().getStringValue(path);
			final Status lastStatus = VoteResolver.Status.fromString(xpp.getAttributeValue(-1, LDMLConstants.DRAFT));
			r.setLastRelease(lastValue, lastStatus);
			r.add(aliasOf.getValueAtDPath(path)); /* add the current value. */
//			System.err.println(path + ": LR '"+lastValue+"', " + lastStatus);
			if(m!=null) {
				for(Map.Entry<User, String>e : m.entrySet()) {
					r.add(e.getValue(), e.getKey().id);
				}
			} else {
//				System.err.println("m is null for " + path + " , but last release value is " + getOldFile().getStringValue(path));
			}
//			System.err.println("RESOLVER for " + path + " --> " + r.toString());
			return r;
		}

		@Override
		public VoteResolver<String> getResolver(String path) {
			return getResolver(peekXpathToVotes(path),path);
		}

		@Override
		public Set<String> getValues(String xpath) {
			Map<User,String> m = peekXpathToVotes(xpath);
			if(m==null) {
				return null;
			} else {
				Set<String> ts = new TreeSet<String>();
				ts.addAll(m.values());
				
				// include the alias value, if not present.
				String fbValue = aliasOf.getValueAtDPath(xpath);
				ts.add(fbValue);
				return ts;
			}
		}
		
		@Override
		public Set<User> getVotesForValue(String xpath, String value) {
			Map<User,String> m = peekXpathToVotes(xpath);
			if(m==null) {
				return null;
			} else {
				TreeSet<User> ts = new TreeSet<User>();
				for(Map.Entry<User,String> e : m.entrySet()) {
					if(e.getValue().equals(value)) {
						ts.add(e.getKey());
					}
				}
				if(ts.isEmpty()) return null;
				return ts;
			}
		}
		
		@Override
		public String getVoteValue(User user, String distinguishingXpath) {
			Map<User,String> m = peekXpathToVotes(distinguishingXpath);
			if(m!=null) {
				return m.get(user);
			} else {
				return null;
			}
		}
		/**
		 * x->v map, create if not there
		 * @param xpath
		 * @return
		 */
		private synchronized final Map<User,String> getXpathToVotes(String xpath) {
			Map<User,String> m = peekXpathToVotes(xpath);
			if(m==null) {
				m = new TreeMap<User,String>(); // use a treemap, don't expect it to be large enough to need a hash
				xpathToVotes.put(xpath, m);
			}
			return m;
		}

		public final synchronized XMLSource makeSource() {
			if(xmlsource == null) {
				XMLSource s;
				if(readonly) {
					s = new ReadOnlyAliasTo(locale);
				} else {
					s = new DataBackedSource(this);
				}
//				System.err.println("@@@@ PLD("+locale+")="+s.getClass().getName()+", ro="+readonly+", cl="+s.getValueAtPath(SOME_KEY));				
				xmlsource = s;
			}
			return xmlsource;
		}
		
		public XMLSource makeSource(boolean resolved) {
			if(resolved==true) {
//System.err.println("@@@@ STFactory " + locale + " requested resolved. Stack:\n" + StackTracker.currentStack());
			    return makeResolvingSource(locale.getBaseName(), getMinimalDraftStatus());
			} else {
				return makeSource();
			}
		}
		
		/**
		 * get x->v map, DONT create it if not there
		 * @param xpath
		 * @return
		 */
		private final Map<User,String> peekXpathToVotes(String xpath) {
			return xpathToVotes.get(xpath);
		}
		
		@Override
		public String voteForValue(User user, String distinguishingXpath,
				String value) {
			System.err.println("V4v: "+locale+" "+distinguishingXpath + " : " + user + " voting for '" + value + "'");
			
			if(!readonly) {
				ElapsedTimer et = new ElapsedTimer("Recording PLD for " + locale+" "+distinguishingXpath + " : " + user + " voting for '" + value);
				Connection conn = null;
				PreparedStatement ps = null;
				PreparedStatement ps2 = null;
				ResultSet rs = null;
				int xpathId = sm.xpt.getByXpath(distinguishingXpath);
				int submitter = user.id;
				int n = 0;
				try {
					conn = DBUtils.getInstance().getDBConnection();
					if(DBUtils.db_Mysql) { //  use 'on duplicate key' syntax 
			            ps = DBUtils.prepareForwardReadOnly(conn,"INSERT INTO " + CLDR_VBV + " (locale,xpath,submitter,value) values (?,?,?,?) " + 
			                "ON DUPLICATE KEY UPDATE locale=?,xpath=?,submitter=?,value=?");
			            
			            ps.setString(5, locale.getBaseName());
			            ps.setInt(6, xpathId);
			            ps.setInt(7,submitter);
			            DBUtils.setStringUTF8(ps, 8, value);
					} else {
			            ps2 =  DBUtils.prepareForwardReadOnly(conn, "DELETE FROM " + CLDR_VBV + " where locale=? and xpath=? and submitter=? ");
			            ps =  DBUtils.prepareForwardReadOnly(conn, "INSERT INTO  " + CLDR_VBV + " (locale,xpath,submitter,value) VALUES (?,?,?,?) ");

			            ps2.setString(1, locale.getBaseName());
			            ps2.setInt(2, xpathId);
			            ps2.setInt(3,submitter);
					}
					
					ps.setString(1, locale.getBaseName());
					ps.setInt(2,xpathId);
					ps.setInt(3,submitter);
		            DBUtils.setStringUTF8(ps, 4, value);
		            

		            if(!DBUtils.db_Mysql) {
		            	ps2.executeUpdate();
		            }
		            ps.executeUpdate();
				} catch (SQLException e) {
					sm.logException(e);
					sm.busted("Could not read locale " + locale, e);
					throw new InternalError("Could not load locale " + locale + " : " + DBUtils.unchainSqlException(e));
				} finally {
					DBUtils.close(rs,ps,conn);
				}
				System.err.println(et);
			} else {
				readonly();
			}
				
			return internalSetVoteForValue(user, distinguishingXpath, value);
		}

		/**
		 * @param user
		 * @param distinguishingXpath
		 * @param value
		 * @return
		 */
		private final String internalSetVoteForValue(User user,
				String distinguishingXpath, String value) {
			if(value!=null) {
				getXpathToVotes(distinguishingXpath).put(user, value);
				return distinguishingXpath;
			} else {
				getXpathToVotes(distinguishingXpath).remove(user);
				return null;
			}
		}
		

		
	}

	/**
	 * @author srl
	 *
	 */
	public class ReadOnlyAliasTo extends XMLSource {
		protected XMLSource aliasOf;

		public ReadOnlyAliasTo(CLDRLocale locale) {			
            setLocaleID(locale.getBaseName());
			aliasOf=sm.getDiskFactory().makeSource(locale.getBaseName()) /* .getUnresolving() */;
//			//CLDRFile loader = new CLDRFile(aliasOf/*,false*/);
//			CLDRFile loader = CLDRFile.loadFromFile(new File(SurveyMain.fileBase,getLocaleID()+".xml"), getLocaleID(), getMinimalDraftStatus(), aliasOf);
//			System.out.println("@@@@[roa2] Our id: " + this.getLocaleID() + ", ");
////			System.out.println("Parent = " + LocaleIDParser.getParent(this.getLocaleID()));
//			System.out.println("@@@@XParent = " + CLDRLocale.getInstance(this.getLocaleID()).getParent());
////            System.err.println("@@@@ loader["+getLocaleID()+"].cl = " + loader.getStringValue(SOME_KEY));
//            System.err.println("@@@@ aliasOf["+getLocaleID()+"].cl = " + aliasOf.getValueAtPath(SOME_KEY));
		}

		
		/* (non-Javadoc)
		 * @see com.ibm.icu.util.Freezable#freeze()
		 */
		@Override
		public Object freeze() {
			readonly();
			return null;
		}

		/* (non-Javadoc)
		 * @see org.unicode.cldr.util.XMLSource#getFullPathAtDPath(java.lang.String)
		 */
		@Override
		public String getFullPathAtDPath(String path) {
			return aliasOf.getFullPathAtDPath(path);
		}

		/* (non-Javadoc)
		 * @see org.unicode.cldr.util.XMLSource#getValueAtDPath(java.lang.String)
		 */
		@Override
		public String getValueAtDPath(String path) {
			String v =  aliasOf.getValueAtDPath(path);
//System.err.println("@@@@ ("+this.getLocaleID()+")" + path+"="+v);
			return v;
		}

		/* (non-Javadoc)
		 * @see org.unicode.cldr.util.XMLSource#getXpathComments()
		 */
		@Override
		public Comments getXpathComments() {
			return aliasOf.getXpathComments();
		}

		/* (non-Javadoc)
		 * @see org.unicode.cldr.util.XMLSource#iterator()
		 */
		@Override
		public Iterator<String> iterator() {
			return aliasOf.iterator();
		}

		/* (non-Javadoc)
		 * @see org.unicode.cldr.util.XMLSource#putFullPathAtDPath(java.lang.String, java.lang.String)
		 */
		@Override
		public void putFullPathAtDPath(String distinguishingXPath,
				String fullxpath) {
				readonly();
		}

		/* (non-Javadoc)
		 * @see org.unicode.cldr.util.XMLSource#putValueAtDPath(java.lang.String, java.lang.String)
		 */
		@Override
		public void putValueAtDPath(String distinguishingXPath, String value) {
			readonly();
		}

		/* (non-Javadoc)
		 * @see org.unicode.cldr.util.XMLSource#removeValueAtDPath(java.lang.String)
		 */
		@Override
		public void removeValueAtDPath(String distinguishingXPath) {
			readonly();
		}

		/* (non-Javadoc)
		 * @see org.unicode.cldr.util.XMLSource#setXpathComments(org.unicode.cldr.util.XPathParts.Comments)
		 */
		@Override
		public void setXpathComments(Comments comments) {
			readonly();
		}

//		/* (non-Javadoc)
//		 * @see org.unicode.cldr.util.XMLSource#make(java.lang.String)
//		 */
//		@Override
//		public XMLSource make(String localeID) {
//			return makeSource(localeID, this.isResolving());
//		}

//		/* (non-Javadoc)
//		 * @see org.unicode.cldr.util.XMLSource#getAvailableLocales()
//		 */
//		@SuppressWarnings("rawtypes")
//		@Override
//		public Set getAvailableLocales() {
//			return handleGetAvailable();
//		}

		
		
//		@Override
//		protected synchronized TreeMap<String, String> getAliases() {
//			if(true) throw new InternalError("NOT IMPLEMENTED.");
//			return null;
//		}

	}

	// Database stuff here.
	private static String CLDR_VBV = "cldr_votevalue";

	/**
	 * These locales can not be modified.
	 */
	private static String readOnlyLocales[] = { "root", "en" };

	
	private static final String SOME_KEY = "//ldml/localeDisplayNames/keys/key[@type=\"collation\"]";
	
	/**
	 * Is this a locale that can't be modified?
	 * @param loc
	 * @return
	 */
	public static final boolean isReadOnlyLocale(CLDRLocale loc) {
		return isReadOnlyLocale(loc.getBaseName());
	}
	
	/**
	 * Is this a locale that can't be modified?
	 * @param loc
	 * @return
	 */
	public static final boolean isReadOnlyLocale(String loc) {
		for(int i=0;i<readOnlyLocales.length;i++) {
			if(readOnlyLocales[i].equals(loc)) return true;
		}
		return false;
	}
	
	private static void readonly() {
		throw new InternalError("This is a readonly instance.");
	}
	
	/**
	 * Throw an error. 
	 */
	@SuppressWarnings("unused")
    static void unimp() {
		throw new InternalError("NOT YET IMPLEMENTED - TODO!.");
	}

	boolean dbIsSetup = false;

	/**
	 * Per locale map
	 */
	private Map<CLDRLocale,PerLocaleData> locales = new HashMap<CLDRLocale,PerLocaleData>();

	/**
	 * The infamous back-pointer.
	 */
	public SurveyMain sm = null;

	DoIfNotRecent updateVoteInfo = new DoIfNotRecent(1000*60*5) {
		@Override
		public void handleDo() {
			// update voter info
    		VoteResolver.setVoterToInfo(sm.reg.getVoterToInfo());	
		}
	};

	/**
	 * Construct one.
	 */
	public STFactory(SurveyMain sm) {
		super();
		this.sm = sm;
		setSupplementalDirectory(new File(getSourceDirectory()+"/../"+"supplemental"));
	}

	@Override
	public BallotBox<User> ballotBoxForLocale(CLDRLocale locale) {
		return get(locale);
	}
	/**
	 * Fetch a locale from the per locale data, create if not there. 
	 * @param locale
	 * @return
	 */
	private final PerLocaleData get(CLDRLocale locale) { 
		PerLocaleData pld = locales.get(locale);
		if(pld==null) {
			pld = new PerLocaleData(locale);
			locales.put(locale, pld);
		}
		return pld;
	}

	private final PerLocaleData get(String locale) {
		return get(CLDRLocale.getInstance(locale));
	}

	@SuppressWarnings("unchecked")
	public CheckCLDR getCheck(CLDRLocale loc) {
		System.err.println("TODO:  STFactory.getCheck()  - slow and bad.");
		CheckCLDR cc = sm.createCheck();
		cc.setCldrFileToCheck(handleMake(loc.getBaseName(),true,getMinimalDraftStatus()), SurveyMain.basicOptionsMap(), new ArrayList<CheckStatus>());
		return cc;
	}
	
	@SuppressWarnings("rawtypes")
	public List getCheckResult(CLDRLocale loc) {
		// TODO Auto-generated method stub
		return null;
	};
	
	public ExampleGenerator getExampleGenerator() {
			CLDRFile fileForGenerator = sm.getBaselineFile();
    		
    		if(fileForGenerator==null) {
    			System.err.println("Err: fileForGenerator is null for " );
    		}
    		ExampleGenerator exampleGenerator = new ExampleGenerator(fileForGenerator, sm.getBaselineFile(), SurveyMain.fileBase + "/../supplemental/");
    		exampleGenerator.setVerboseErrors(sm.twidBool("ExampleGenerator.setVerboseErrors"));
    		//System.err.println("-revalid exgen-"+locale + " - " + exampleIsValid + " in " + this);
    		//exampleIsValid.setValid();
    		//System.err.println(" >> "+locale + " - " + exampleIsValid + " in " + this);
    		//exampleIsValid.register();
    		//System.err.println(" >>> "+locale + " - " + exampleIsValid + " in " + this);
        return exampleGenerator;
	}
	
	/* (non-Javadoc)
	 * @see org.unicode.cldr.util.Factory#getMinimalDraftStatus()
	 */
	@Override
	protected DraftStatus getMinimalDraftStatus() {
        return DraftStatus.unconfirmed;
	}
	
	/* (non-Javadoc)
	 * @see org.unicode.cldr.util.Factory#getSourceDirectory()
	 */
	@Override
	public String getSourceDirectory() {
		return SurveyMain.fileBase;
	}

	/* (non-Javadoc)
	 * @see org.unicode.cldr.util.Factory#handleGetAvailable()
	 */
	@Override
	protected Set<String> handleGetAvailable() {
		return sm.getDiskFactory().getAvailable();
	}
	
	/* (non-Javadoc)
	 * @see org.unicode.cldr.util.Factory#handleMake(java.lang.String, boolean, org.unicode.cldr.util.CLDRFile.DraftStatus)
	 */
	@Override
	protected CLDRFile handleMake(String localeID,
	        boolean resolved,
			DraftStatus madeWithMinimalDraftStatus) {
		return get(localeID).getFile(resolved);
	}
	
	public CLDRFile make(CLDRLocale loc, boolean resolved) {
		return make(loc.getBaseName(),resolved);
	}
	
	
	public XMLSource makeSource(String localeID, boolean resolved) {
		if(localeID==null) return null; // ?!
		return get(localeID).makeSource(resolved);
	}
	
	/**
	 * Prepare statement.  
	 * Args: locale
	 * Result: xpath,submitter,value
	 * @param conn
	 * @return
	 * @throws SQLException
	 */
	private PreparedStatement openQueryByLocale(Connection conn) throws SQLException {
		setupDB();
		return DBUtils.prepareForwardReadOnly(conn, "SELECT xpath,submitter,value FROM " + CLDR_VBV + " WHERE locale = ?");
	}

	private synchronized final void setupDB() 
	{
		if(dbIsSetup) return;
		dbIsSetup=true; // don't thrash.
		Connection conn = null;
		try {
			conn = DBUtils.getInstance().getDBConnection();

			boolean isNew = !DBUtils.hasTable(conn, CLDR_VBV);
			if(!isNew) {
				return; // nothing to setup
			}
			unimp();
			
			/*				
				    CREATE TABLE  cldr_votevalue (
				        locale VARCHAR(20),
				        xpath  INT NOT NULL,
				        submitter INT NOT NULL,
				        value BLOB    
				     );
				
				     CREATE UNIQUE INDEX cldr_votevalue_unique ON cldr_votevalue (locale,xpath,submitter);
			 */
			/*
		synchronized(sconn) {
			String sql; // this points to 
			Statement s = sconn.createStatement();

			sql = "create table " + CLDR_VBV + " (" + xpath INT not null, " + // normal
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


		} catch(SQLException se) {
			sm.logException(se, null);
			sm.busted("Setting up DB for STFactory", se);

		*/
		} finally {
			DBUtils.close(conn);
		}
	}
}
