/**
 * 
 */
package org.unicode.cldr.web;

import java.io.File;
import java.io.IOException;
import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;

import org.unicode.cldr.icu.LDMLConstants;
import org.unicode.cldr.test.CheckCLDR;
import org.unicode.cldr.test.ExampleGenerator;
import org.unicode.cldr.test.SimpleTestCache;
import org.unicode.cldr.test.TestCache;
import org.unicode.cldr.test.TestCache.TestResultBundle;
import org.unicode.cldr.util.CLDRConfig;
import org.unicode.cldr.util.CLDRConfig.Environment;
import org.unicode.cldr.util.CLDRFile;
import org.unicode.cldr.util.CLDRFile.DraftStatus;
import org.unicode.cldr.util.CLDRLocale;
import org.unicode.cldr.util.Factory;
import org.unicode.cldr.util.LDMLUtilities;
import org.unicode.cldr.util.LruMap;
import org.unicode.cldr.util.Pair;
import org.unicode.cldr.util.PathHeader;
import org.unicode.cldr.util.SimpleXMLSource;
import org.unicode.cldr.util.VoteResolver;
import org.unicode.cldr.util.VoteResolver.Status;
import org.unicode.cldr.util.XMLFileReader;
import org.unicode.cldr.util.XMLSource;
import org.unicode.cldr.util.XPathParts;
import org.unicode.cldr.util.XPathParts.Comments;
import org.unicode.cldr.web.UserRegistry.ModifyDenial;
import org.unicode.cldr.web.UserRegistry.User;

import com.ibm.icu.dev.util.ElapsedTimer;
import com.ibm.icu.util.VersionInfo;

/**
 * @author srl
 * 
 */
public class STFactory extends Factory implements BallotBoxFactory<UserRegistry.User>, UserRegistry.UserChangedListener {
    private static final String VOTE_OVERRIDE = "vote_override";

    /**
     * If true: run EVERY xpath through the resolver.
     */
    public static final boolean RESOLVE_ALL_XPATHS = false;

    public class DataBackedSource extends DelegateXMLSource {
        PerLocaleData ballotBox;
        XMLSource aliasOf; // original XMLSource

        public DataBackedSource(PerLocaleData makeFrom) {
            super((XMLSource) makeFrom.diskData.cloneAsThawed());
            ballotBox = makeFrom;
        }

        /*
         * (non-Javadoc)
         * 
         * @see com.ibm.icu.util.Freezable#freeze()
         */
        @Override
        public XMLSource freeze() {
            readonly();
            return null;
        }

        /*
         * (non-Javadoc)
         * 
         * @see
         * org.unicode.cldr.util.XMLSource#getFullPathAtDPath(java.lang.String)
         */
        @Override
        public String getFullPathAtDPath(String path) {
            // Map<User,String> m = ballotBox.peekXpathToVotes(path);
            // if(m==null || m.isEmpty()) {
            // return aliasOf.getFullPathAtDPath(path);
            // } else {
            // SurveyLog.logger.warning("Note: DBS.getFullPathAtDPath() todo!!");
            // TODO: show losing values
            return delegate.getFullPathAtDPath(path);
            // }
        }

        /*
         * (non-Javadoc)
         * 
         * @see
         * org.unicode.cldr.util.XMLSource#getValueAtDPath(java.lang.String)
         */
        @Override
        public String getValueAtDPath(String path) {
            return delegate.getValueAtDPath(path);
        }

        /**
         * This is the bottleneck for processing values.
         * 
         * @param path
         * @param resolver
         * @return
         */
        public VoteResolver<String> setValueFromResolver(String path, VoteResolver<String> resolver) {
            Map<User, String> m = ballotBox.peekXpathToVotes(path);
            String res;
            String fullPath = null;
            if ((m == null || m.isEmpty()) && !RESOLVE_ALL_XPATHS) { // no
                                                                     // votes,
                                                                     // so..
                res = ballotBox.diskData.getValueAtDPath(path);
                fullPath = ballotBox.diskData.getFullPathAtDPath(path);
                // System.err.println("SVFR: " + fullPath +
                // " due to disk data");
            } else {
                res = (resolver = ballotBox.getResolver(m, path, resolver)).getWinningValue();
                String diskFullPath = ballotBox.diskData.getFullPathAtDPath(path);
                if (diskFullPath == null) {
                    diskFullPath = path; // if the disk didn't have a full path,
                                         // just use the inbound path.
                }
                String baseXPath = XPathTable.removeDraftAltProposed(diskFullPath); // Remove
                                                                                    // JUST
                                                                                    // draft
                                                                                    // alt
                                                                                    // proposed.
                                                                                    // Leave
                                                                                    // 'numbers='
                                                                                    // etc.

                Status win = resolver.getWinningStatus();
                if (win == Status.approved) {
                    fullPath = baseXPath;
                } else {
                    fullPath = baseXPath + "[@draft=\"" + win + "\"]";
                }
                // System.err.println(" SVFR: " + fullPath + " due to " + win +
                // " from " + resolver.toString());
            }
            // SurveyLog.logger.info(path+"="+res+", by resolver.");
            if (res != null) {
                delegate.removeValueAtDPath(path); // TODO: needed to clear
                                                   // fullpath? Otherwise,
                                                   // fullpath may be ignored if
                                                   // value is extant.
                delegate.putValueAtPath(fullPath, res);
            } else {
                delegate.removeValueAtDPath(path);
            }
            notifyListeners(path);
            return resolver;
        }

        /*
         * (non-Javadoc)
         * 
         * @see org.unicode.cldr.util.XMLSource#getXpathComments()
         */
        @Override
        public Comments getXpathComments() {
            return delegate.getXpathComments();
        }

        /*
         * (non-Javadoc)
         * 
         * @see org.unicode.cldr.util.XMLSource#iterator()
         */
        @Override
        public Iterator<String> iterator() {
            if (ballotBox.xpathToVotes == null || ballotBox.xpathToVotes.isEmpty()) {
                return delegate.iterator();
            } else {
                // SurveyLog.debug("Note: DBS.iterator() todo -- iterate over losing values?");
                // // losing values are available in the raw xml.
                return delegate.iterator();
            }
        }

        /*
         * (non-Javadoc)
         * 
         * @see
         * org.unicode.cldr.util.XMLSource#putFullPathAtDPath(java.lang.String,
         * java.lang.String)
         */
        @Override
        public void putFullPathAtDPath(String distinguishingXPath, String fullxpath) {
            readonly();
        }

        /*
         * (non-Javadoc)
         * 
         * @see
         * org.unicode.cldr.util.XMLSource#putValueAtDPath(java.lang.String,
         * java.lang.String)
         */
        @Override
        public void putValueAtDPath(String distinguishingXPath, String value) {
            readonly();
        }

        /*
         * (non-Javadoc)
         * 
         * @see
         * org.unicode.cldr.util.XMLSource#removeValueAtDPath(java.lang.String)
         */
        @Override
        public void removeValueAtDPath(String distinguishingXPath) {
            readonly();
        }

        /*
         * (non-Javadoc)
         * 
         * @see
         * org.unicode.cldr.util.XMLSource#setXpathComments(org.unicode.cldr
         * .util.XPathParts.Comments)
         */
        @Override
        public void setXpathComments(Comments comments) {
            readonly();
        }

        // /* (non-Javadoc)
        // * @see org.unicode.cldr.util.XMLSource#make(java.lang.String)
        // */
        // @Override
        // public XMLSource make(String localeID) {
        // return makeSource(localeID, this.isResolving());
        // }

        // /* (non-Javadoc)
        // * @see org.unicode.cldr.util.XMLSource#getAvailableLocales()
        // */
        // @SuppressWarnings("rawtypes")
        // @Override
        // public Set getAvailableLocales() {
        // return handleGetAvailable();
        // }

        // /* (non-Javadoc)
        // * @see org.unicode.cldr.util.XMLSource#getSupplementalDirectory()
        // */
        // @Override
        // public File getSupplementalDirectory() {
        // File suppDir = new File(getSourceDirectory()+"/../"+"supplemental");
        // return suppDir;
        // }

        // @Override
        // protected synchronized TreeMap<String, String> getAliases() {
        // if(true) throw new InternalError("NOT IMPLEMENTED.");
        // return null;
        // }

    }

    /**
     * The max string length accepted of any value.
     */
    private static final int MAX_VAL_LEN = 4096;

    /**
     * the STFactory maintains exactly one instance of this class per locale it
     * is working with. It contains the XMLSource, Example Generator, etc..
     * 
     * @author srl
     * 
     */
    private class PerLocaleData implements Comparable<PerLocaleData>, BallotBox<User> {
        private CLDRFile file = null, rFile = null;
        private CLDRLocale locale;
        private CLDRFile oldFile;
        private boolean readonly;
        private MutableStamp stamp = null;

        /**
         * The held XMLSource.
         */
        private DataBackedSource xmlsource = null;
        /**
         * The on-disk data. May be == to xmlsource for readonly data.
         */
        private XMLSource diskData = null;
        private CLDRFile diskFile = null;

        /* SIMPLE IMP */
        private Map<String, Map<User, String>> xpathToVotes = new HashMap<String, Map<User, String>>();
        private Map<String, Map<User, Integer>> xpathToOverrides = new HashMap<String, Map<User, Integer>>();
        private Map<Integer, Set<String>> xpathToOtherValues = new HashMap<Integer, Set<String>>();
        private Set<User> allVoters = new TreeSet<User>();
        private boolean oldFileMissing;
        private XMLSource resolvedXmlsource = null;

        PerLocaleData(CLDRLocale locale) {
            this.locale = locale;
            readonly = isReadOnlyLocale(locale);
            diskData = (XMLSource) sm.getDiskFactory().makeSource(locale.getBaseName()).freeze();
            sm.xpt.loadXPaths(diskData);
            diskFile = sm.getDiskFactory().make(locale.getBaseName(), true).freeze();
            pathsForFile = phf.pathsForFile(diskFile);

//            if (checkHadVotesSometimeThisRelease) {
//                votesSometimeThisRelease = loadVotesSometimeThisRelease(locale);
//                if (votesSometimeThisRelease == null) {
//                    SurveyLog.warnOnce("Note: giving up on loading 'sometime this release' votes. The database name would be "
//                        + getVotesSometimeTableName());
//                    checkHadVotesSometimeThisRelease = false; // don't try
//                                                              // anymore.
//                }
//            }
            stamp = mintLocaleStamp(locale);
        }

        public final Stamp getStamp() {
            return stamp;
        }

        private Status getStatus(CLDRFile anOldFile, String path, final String lastValue) {
            Status lastStatus;
            {
                XPathParts xpp = new XPathParts(null, null);
                String fullXPath = anOldFile.getFullXPath(path);
                if (fullXPath == null)
                    fullXPath = path; // throw new
                                      // InternalError("null full xpath for " +
                                      // path);
                xpp.set(fullXPath);
                String draft = xpp.getAttributeValue(-1, LDMLConstants.DRAFT);
                lastStatus = draft == null ? Status.approved : VoteResolver.Status.fromString(draft);
                final String srcid = anOldFile.getSourceLocaleID(path, null);
                if (!srcid.equals(diskFile.getLocaleID())) {
                    lastStatus = Status.missing;
                }
                if (false)
                    System.err.println(fullXPath + " : " + xpp.getAttributeValue(-1, LDMLConstants.DRAFT) + " == " + lastStatus
                        + " ('" + lastValue + "')");
            }
            return lastStatus;
        }

        /**
         * 
         * @param user
         *            - The user voting on the path
         * @param xpath
         *            - The xpath being voted on.
         * @return true - If pathHeader and coverage would indicate a value that
         *         the user should have been able to vote on.
         * 
         */
        private boolean isValidSurveyToolVote(UserRegistry.User user, String xpath) {
            PathHeader ph = getPathHeader(xpath);
            if (ph == null)
                return false;
            if (ph.getSurveyToolStatus() == PathHeader.SurveyToolStatus.DEPRECATED)
                return false;
            if (ph.getSurveyToolStatus() == PathHeader.SurveyToolStatus.HIDE
                || ph.getSurveyToolStatus() == PathHeader.SurveyToolStatus.READ_ONLY) {
                if (user == null || !UserRegistry.userIsTC(user))
                    return false;
            }

            if (sm.getSupplementalDataInfo().getCoverageValue(xpath, locale.getBaseName()) > org.unicode.cldr.util.Level.COMPREHENSIVE.getLevel()) {
                return false;
            }
            return true;
        }

        /**
         * Load internal data , push into source.
         * 
         * @param dataBackedSource
         * @return
         */
        private DataBackedSource loadVoteValues(DataBackedSource dataBackedSource) {
            if (!readonly) {
                VoteResolver<String> resolver = null; // save recalculating
                                                      // this.
                Set<String> hitXpaths = new HashSet<String>();
                ElapsedTimer et = (SurveyLog.DEBUG) ? new ElapsedTimer("Loading PLD for " + locale) : null;
                Connection conn = null;
                PreparedStatement ps = null;
                PreparedStatement ps2 = null;
                ResultSet rs = null;
                ResultSet rs2 = null;
                int n = 0;
                int n2 = 0;
                int del = 0;
                try {
                    conn = DBUtils.getInstance().getDBConnection();
                    ps = openQueryByLocaleRW(conn);
                    ps.setString(1, locale.getBaseName());
                    rs = ps.executeQuery();

                    while (rs.next()) {
                        int xp = rs.getInt(1);
                        String xpath = sm.xpt.getById(xp);
                        hitXpaths.add(xpath);
                        int submitter = rs.getInt(2);
                        String value = DBUtils.getStringUTF8(rs, 3);
                        // 4 = locale
                        Integer voteOverride = rs.getInt(5);
                        if(voteOverride==0 && rs.wasNull()) {
                            voteOverride=null;
                        } else {
                        }
                        User theSubmitter = sm.reg.getInfo(submitter);
                        if (theSubmitter == null) {
                            if (true) SurveyLog.warnOnce("Ignoring votes for deleted user #" + submitter);
                        }
                        if (!UserRegistry.countUserVoteForLocale(theSubmitter, locale)) { // check user permission to submit
                            continue;
                        }
                        if (!isValidSurveyToolVote(theSubmitter, xpath)) { // Make sure it is a visible path
                            continue;
                        }
                        try {
                            internalSetVoteForValue(theSubmitter, xpath, value, resolver, dataBackedSource, voteOverride);
                            n++;
                        } catch (BallotBox.InvalidXPathException e) {
                            System.err.println("InvalidXPathException: Deleting vote for " + theSubmitter + ":" + locale + ":" + xpath);
                            rs.deleteRow();
                            del++;
                        }
                    }

                    ps2 = DBUtils.prepareStatementWithArgs(conn, "select xpath,value from " + DBUtils.Table.VOTE_VALUE_ALT + " where locale=?",
                        locale);
                    rs2 = ps2.executeQuery();
                    while (rs2.next()) {
                        int xp = rs2.getInt(1);
                        String value = DBUtils.getStringUTF8(rs2, 2);
                        getXpathToOthers(xp).add(value);
                        n2++;
                    }

                    if (del > 0) {
                        System.out.println("Committing delete of " + del + " invalid votes from " + locale);
                        conn.commit();
                    }
                } catch (SQLException e) {
                    SurveyLog.logException(e);
                    SurveyMain.busted("Could not read locale " + locale, e);
                    throw new InternalError("Could not load locale " + locale + " : " + DBUtils.unchainSqlException(e));
                } finally {
                    DBUtils.close(rs2, ps2, rs, ps, conn);
                }
                SurveyLog.debug(et + " - read " + n + " items  (" + xpathToVotes.size() + " xpaths.) and " + n2
                    + " alternate values (" + xpathToOtherValues.size() + " xpaths.)");
                if (RESOLVE_ALL_XPATHS) {
                    et = (SurveyLog.DEBUG) ? new ElapsedTimer("Loading PLD for " + locale) : null;
                    int j = 0;
                    for (String xp : diskData) {
                        if (hitXpaths.contains(xp))
                            continue;
                        resolver = dataBackedSource.setValueFromResolver(xp, resolver);
                        j++;
                    }
                    SurveyLog.debug(et + " - RESOLVE_ALL_XPATHS  - resolved " + j + " additional items, " + n + " total.");
                }
            }
            stamp.next();
            dataBackedSource.addListener(gTestCache);
            return dataBackedSource;
        }

        @Override
        public int compareTo(PerLocaleData arg0) {
            if (this == arg0) {
                return 0;
            } else {
                return locale.compareTo(arg0.locale);
            }
        }

        @Override
        public boolean equals(Object other) {
            if (other == this) {
                return true;
            } else if (!(other instanceof PerLocaleData)) {
                return false;
            } else {
                return ((PerLocaleData) other).locale.equals(locale);
            }
        }
        public synchronized CLDRFile getFile(boolean resolved) {
            if (resolved) {
                if (rFile == null) {
                    if (getSupplementalDirectory() == null)
                        throw new InternalError("getSupplementalDirectory() == null!");
                    rFile = new CLDRFile(makeSource(true)).setSupplementalDirectory(getSupplementalDirectory());
                    rFile.getSupplementalDirectory();
                }
                return rFile;
            } else {
                if (file == null) {
                    if (getSupplementalDirectory() == null)
                        throw new InternalError("getSupplementalDirectory() == null!");
                    file = new CLDRFile(makeSource(false)).setSupplementalDirectory(getSupplementalDirectory());
                }
                return file;
            }
        }

        public synchronized CLDRFile getOldFile() {
            if (oldFile == null && !oldFileMissing) {
                oldFileMissing = !sm.getOldFactory().getAvailable().contains(locale.getBaseName());
                if (!oldFileMissing) {
                    oldFile = sm.getOldFactory().make(locale.getBaseName(), true);
                }
            }
            return oldFile;
        }

        // public VoteResolver<String> getResolver(Map<User, String> m, String
        // path) {
        // return getResolver(m, path, null);
        // }

        /**
         * Utility class for testing values
         * @author srl
         *
         */
        private class ValueChecker {
            private final String path;
            HashSet<String> allValues = new HashSet<String>(8); // 16 is
            // probably too
            // many values
            HashSet<String> badValues = new HashSet<String>(8); // 16 is
            // probably too
            // many values

            LinkedList<CheckCLDR.CheckStatus> result = null;
            TestResultBundle testBundle = null;

            ValueChecker(String path) {
                this.path = path;
            }

            boolean canUseValue(String value) {
                if (value == null || allValues.contains(value)) {
                    return true;
                } else if (badValues.contains(value)) {
                    return false;
                } else {
                    if (testBundle == null) {
                        testBundle = getDiskTestBundle(locale);
                        result = new LinkedList<CheckCLDR.CheckStatus>();
                    } else {
                        result.clear();
                    }

                    testBundle.check(path, result, value);
                    if (false) System.out.println("Checking result of " + path + " = " + value + " := haserr " + CheckCLDR.CheckStatus.hasError(result));
                    if (CheckCLDR.CheckStatus.hasError(result)) {
                        badValues.add(value);
                        return false;
                    } else {
                        allValues.add(value);
                        return true; // OK
                    }
                }
            }

        }

        private static final boolean ERRORS_ALLOWED_IN_VETTING = true;

        /**
         * Create or update a VoteResolver for this item
         * 
         * @param userToVoteMap
         *            map of users to vote values
         * @param path
         *            xpath voted on
         * @param r
         *            if non-null, resolver to re-use.
         * @return the new or updated resolver
         */
        private VoteResolver<String> getResolverInternal(Map<User, String> userToVoteMap, String path, VoteResolver<String> r) {
            if (path == null)
                throw new IllegalArgumentException("path must not be null");

            if (r == null) {
                r = new VoteResolver<String>(); // create
            } else {
                r.clear(); // reuse
            }

            final ValueChecker vc = ERRORS_ALLOWED_IN_VETTING ? null : new ValueChecker(path);

            // Set established locale
            r.setLocale(locale, getPathHeader(path));

            CLDRFile anOldFile = getOldFile();
            if (anOldFile == null)
                anOldFile = diskFile; // use 'current' for 'previous' if
                                      // previous is missing.

            // set prior release (if present)
            final String lastValue = anOldFile.getStringValue(path);
            final Status lastStatus = getStatus(anOldFile, path, lastValue);
            if (ERRORS_ALLOWED_IN_VETTING || vc.canUseValue(lastValue)) {
                r.setLastRelease(lastValue, lastValue == null ? Status.missing : lastStatus); /* add the last release value */
            } else {
                r.setLastRelease(null, Status.missing); /* missing last release value  due to error. */
            }

            // set current Trunk value (if present)
            final String currentValue = diskData.getValueAtDPath(path);
            final Status currentStatus = getStatus(diskFile, path, currentValue);
            if (ERRORS_ALLOWED_IN_VETTING || vc.canUseValue(currentValue)) {
                r.setTrunk(currentValue, currentStatus);
                r.add(currentValue);
            }

            // add each vote
            if (userToVoteMap != null && !userToVoteMap.isEmpty()) {
                for (Map.Entry<User, String> e : userToVoteMap.entrySet()) {
                    String v = e.getValue();

                    Map<User, Integer> voteOverrideMap = getXpathToOverride(path);

                    if (ERRORS_ALLOWED_IN_VETTING || vc.canUseValue(v)) {
                        Integer voteOverride = null;
                        if(voteOverrideMap!=null) { // map may not even be present
                            voteOverride = voteOverrideMap.get(e.getKey());
                        }
                        r.add(v, // user's vote
                            e.getKey().id, voteOverride); // user's id
                    }
                }
            }
            return r;
        }

        public VoteResolver<String> getResolver(Map<User, String> m, String path, VoteResolver<String> r) {
            try {
                r = getResolverInternal(m, path, r);
            } catch (VoteResolver.UnknownVoterException uve) {
                handleUserChanged(null);
                try {
                    r = getResolverInternal(m, path, r);
                } catch (VoteResolver.UnknownVoterException uve2) {
                    SurveyLog.logException(uve2);
                    SurveyMain.busted(uve2.toString(), uve2);
                    throw new InternalError(uve2.toString());
                }
            }
            return r;
        }

        @Override
        public VoteResolver<String> getResolver(String path) {
            return getResolver(peekXpathToVotes(path), path, null);
        }

        @Override
        public Set<String> getValues(String xpath) {
            Set<String> other = xpathToOtherValues.get(sm.xpt.getByXpath(xpath));

            Set<String> ts = other != null ? new TreeSet<String>(other) : new TreeSet<String>();

            Map<User, String> m = peekXpathToVotes(xpath);
            if (m != null) {
                ts.addAll(m.values());
            }
            // include the on-disk value, if not present.
            String fbValue = diskData.getValueAtDPath(xpath);
            if (fbValue != null) {
                ts.add(fbValue);
            }

            if (ts.isEmpty())
                return null; // or empty?
            return ts;
        }

        @Override
        public Set<User> getVotesForValue(String xpath, String value) {
            Map<User, String> m = peekXpathToVotes(xpath);
            if (m == null) {
                return null;
            } else {
                TreeSet<User> ts = new TreeSet<User>();
                for (Map.Entry<User, String> e : m.entrySet()) {
                    if (e.getValue().equals(value)) {
                        ts.add(e.getKey());
                    }
                }
                if (ts.isEmpty())
                    return null;
                return ts;
            }
        }

        @Override
        public String getVoteValue(User user, String distinguishingXpath) {
            Map<User, String> m = peekXpathToVotes(distinguishingXpath);
            if (m != null) {
                return m.get(user);
            } else {
                return null;
            }
        }

        /**
         * x->v map, create if not there
         * 
         * @param xpath
         * @return
         */
        private synchronized final Map<User, String> getXpathToVotes(String xpath) {
            Map<User, String> m = peekXpathToVotes(xpath);
            if (m == null) {
                m = new TreeMap<User, String>(); // use a treemap, don't expect
                                                 // it to be large enough to
                                                 // need a hash
                xpathToVotes.put(xpath, m);
            }
            return m;
        }

        private Map<User, Integer> getXpathToOverride(String distinguishingXpath) {
            Map<User, Integer> s = xpathToOverrides.get(distinguishingXpath);
            if (s == null) {
                s = new TreeMap<User, Integer>();
                xpathToOverrides.put(distinguishingXpath, s);
            }
            return s;
        }
        
        public synchronized XMLSource makeSource(boolean resolved) {
            if (resolved == true) {
                if (resolvedXmlsource == null) {
                    resolvedXmlsource = makeResolvingSource(locale.getBaseName(), getMinimalDraftStatus());
                }
                return resolvedXmlsource;
            } else {
                if (readonly) {
                    return diskData;
                } else {
                    if (xmlsource == null) {
                        xmlsource = loadVoteValues(new DataBackedSource(this));
                    }
                    return xmlsource;
                }
            }
        }

        /**
         * get x->v map, DONT create it if not there
         * 
         * @param xpath
         * @return
         */
        private final Map<User, String> peekXpathToVotes(String xpath) {
            return xpathToVotes.get(xpath);
        }

        @Override
        public void unvoteFor(User user, String distinguishingXpath) throws BallotBox.InvalidXPathException {
            voteForValue(user, distinguishingXpath, null);
        }

        @Override
        public void revoteFor(User user, String distinguishingXpath) throws BallotBox.InvalidXPathException {
            String oldValue = getVoteValue(user, distinguishingXpath);
            voteForValue(user, distinguishingXpath, oldValue);
        }


        public void voteForValue(User user, String distinguishingXpath, String value) throws InvalidXPathException {
            voteForValue(user, distinguishingXpath, value, null);
        }
        
        @Override
        public synchronized void voteForValue(User user, String distinguishingXpath, String value, Integer withVote) throws BallotBox.InvalidXPathException {
            if (!getPathsForFile().contains(distinguishingXpath)) {
                throw new BallotBox.InvalidXPathException(distinguishingXpath);
            }
            SurveyLog.debug("V4v: " + locale + " " + distinguishingXpath + " : " + user + " voting for '" + value + "'");
            ModifyDenial denial = UserRegistry.userCanModifyLocaleWhy(user, locale); // this
                                                                                     // has
                                                                                     // to
                                                                                     // do
                                                                                     // with
                                                                                     // changing
                                                                                     // a
                                                                                     // vote
                                                                                     // -
                                                                                     // not
                                                                                     // counting
                                                                                     // it.
            if (denial != null) {
                throw new IllegalArgumentException("User " + user + " cannot modify " + locale + " " + denial);
            }
            
            if(withVote!=null) {
                if(withVote == user.getLevel().getVotes()) {
                    withVote=null; // not an override
                } else if(withVote != user.getLevel().canVoteAtReducedLevel()) {
                    throw new IllegalArgumentException("User " + user + " cannot vote at " + withVote + " level ");
                }
            }

            if (value != null && value.length() > MAX_VAL_LEN) {
                throw new IllegalArgumentException("Value exceeds limit of " + MAX_VAL_LEN);
            }

            if (!readonly) {
                boolean didClearFlag = false;
                makeSource(false);
                ElapsedTimer et = !SurveyLog.DEBUG ? null : new ElapsedTimer("{0} Recording PLD for " + locale + " "
                    + distinguishingXpath + " : " + user + " voting for '" + value);
                Connection conn = null;
                PreparedStatement saveOld = null; // save off old value
                PreparedStatement ps = null; // all for mysql, or 1st step for
                                             // derby
                PreparedStatement ps2 = null; // 2nd step for derby
                ResultSet rs = null;
                int xpathId = sm.xpt.getByXpath(distinguishingXpath);
                final boolean wasFlagged = getFlag(locale, xpathId); // do this outside of the txn..
                int submitter = user.id;
                try {
                    conn = DBUtils.getInstance().getDBConnection();

                    String add0 = "", add1 = "", add2 = "";
                    
                    // #1 - save the "VOTE_VALUE_ALT"  ( possible proposal) value.
                    if (DBUtils.db_Mysql) {
                        add0 = "IGNORE";
                        // add1="ON DUPLICATE KEY IGNORE";
                    } else {
                        add2 = "and not exists (select * from " + DBUtils.Table.VOTE_VALUE_ALT + " where " + DBUtils.Table.VOTE_VALUE_ALT + ".locale=" + DBUtils.Table.VOTE_VALUE
                            + ".locale and " + DBUtils.Table.VOTE_VALUE_ALT + ".xpath=" + DBUtils.Table.VOTE_VALUE + ".xpath " + " and " + DBUtils.Table.VOTE_VALUE_ALT
                            + ".value=" + DBUtils.Table.VOTE_VALUE + ".value )";
                    }
                    String sql = "insert " + add0 + " into " + DBUtils.Table.VOTE_VALUE_ALT + "   " + add1 + " select " + DBUtils.Table.VOTE_VALUE + ".locale,"
                        + DBUtils.Table.VOTE_VALUE + ".xpath," + DBUtils.Table.VOTE_VALUE + ".value "
                        + " from "+DBUtils.Table.VOTE_VALUE+" where locale=? and xpath=? and submitter=? and value is not null " + add2;
                    // if(DEBUG) System.out.println(sql);
                    saveOld = DBUtils.prepareStatementWithArgs(conn, sql, locale.getBaseName(), xpathId, user.id);

                    int oldSaved = saveOld.executeUpdate();
                    // System.err.println("SaveOld: saved " + oldSaved +
                    // " values");

                    // #2 - save the actual vote.
                    if (DBUtils.db_Mysql) { // use 'on duplicate key' syntax
                        ps = DBUtils.prepareForwardReadOnly(conn, "INSERT INTO " + DBUtils.Table.VOTE_VALUE
                            + " (locale,xpath,submitter,value,last_mod,"+VOTE_OVERRIDE+") values (?,?,?,?,CURRENT_TIMESTAMP,?) "
                            + "ON DUPLICATE KEY UPDATE locale=?,xpath=?,submitter=?,value=?,last_mod=CURRENT_TIMESTAMP,"+VOTE_OVERRIDE+"=?");
                        int colNum=6;
                        ps.setString(colNum++, locale.getBaseName());
                        ps.setInt(colNum++, xpathId);
                        ps.setInt(colNum++, submitter);
                        DBUtils.setStringUTF8(ps, colNum++, value);
                        DBUtils.setInteger(ps,colNum++, withVote);
                    } else { // derby
                        ps2 = DBUtils.prepareForwardReadOnly(conn, "DELETE FROM " + DBUtils.Table.VOTE_VALUE
                            + " where locale=? and xpath=? and submitter=? ");
                        ps = DBUtils.prepareForwardReadOnly(conn, "INSERT INTO " + DBUtils.Table.VOTE_VALUE
                            + " (locale,xpath,submitter,value,last_mod,"+VOTE_OVERRIDE+") VALUES (?,?,?,?,CURRENT_TIMESTAMP,?) ");
                        int colNum=1;
                        ps2.setString(colNum++, locale.getBaseName());
                        ps2.setInt(colNum++, xpathId);
                        ps2.setInt(colNum++, submitter);
                        // NB:  no "VOTE_OVERRIDE" column on delete.
                    }

                    {
                        int colNum = 1;
                        ps.setString(colNum++, locale.getBaseName());
                        ps.setInt(colNum++, xpathId);
                        ps.setInt(colNum++, submitter);
                        DBUtils.setStringUTF8(ps, colNum++, value);
                        DBUtils.setInteger(ps,colNum++, withVote);
                    }
                    if (ps2!=null) {
                        ps2.executeUpdate();
                    }
                    ps.executeUpdate();

                    
                    if(wasFlagged && UserRegistry.userIsTC(user)) {
                        clearFlag(conn, locale, xpathId, user);
                        didClearFlag = true;
                    }
                    conn.commit();
                } catch (SQLException e) {
                    SurveyLog.logException(e);
                    SurveyMain.busted("Could not vote for value in locale locale " + locale, e);
                    throw new InternalError("Could not load locale " + locale + " : " + DBUtils.unchainSqlException(e));
                } finally {
                    DBUtils.close(saveOld, rs, ps, ps2, conn);
                }
                SurveyLog.debug(et);

                
                if(didClearFlag) {
                    // now, outside of THAT txn, make a forum post about clearing the flag.
                    final String forum = SurveyForum.localeToForum(locale.toULocale());
                    final int forumNumber = sm.fora.getForumNumber(forum);
                    int newPostId = sm.fora.doPostInternal(forum,forumNumber,xpathId,-1,locale,"Flag Removed","(The flag was removed.)",false,user);
                    //sm.fora.emailNotify(ctx, forum, base_xpath, subj, text, postId);
                    SurveyLog.warnOnce("TODO: no email notify on flag clear. This may be OK, it could be a lot of mail.");
                    System.out.println("NOTE: flag was removed from " + locale + " " + distinguishingXpath + " - post ID="+newPostId + "  by " + user.toString());
                }

            } else {
                readonly();
            }

            internalSetVoteForValue(user, distinguishingXpath, value, null, xmlsource, withVote); // will create/throw away a resolver.
        }

        /**
         * @param user
         * @param distinguishingXpath
         * @param value
         * @param source
         * @return
         */
        private final VoteResolver<String> internalSetVoteForValue(User user, String distinguishingXpath, String value,
            VoteResolver<String> resolver, DataBackedSource source, Integer voteOverride) throws InvalidXPathException {
            if (!getPathsForFile().contains(distinguishingXpath)) {
                throw new InvalidXPathException(distinguishingXpath);
            }
            final Map<User,Integer> overrides = getXpathToOverride(distinguishingXpath);
            if (value != null) {
                getXpathToVotes(distinguishingXpath).put(user, value);
                getXpathToOthers(sm.xpt.getByXpath(distinguishingXpath)).add(value);
                if(voteOverride==null) {
                    overrides.remove(user);
                } else {
                    overrides.put(user, voteOverride);
                }
            } else {
                getXpathToVotes(distinguishingXpath).remove(user);
                allVoters.add(user);
                overrides.remove(user);
            }
            stamp.next();
            return resolver = source.setValueFromResolver(distinguishingXpath, resolver);
        }
        
        @Override
        public synchronized void deleteValue(User user, String distinguishingXpath, String value) throws BallotBox.InvalidXPathException {
            if (!getPathsForFile().contains(distinguishingXpath)) {
                throw new BallotBox.InvalidXPathException(distinguishingXpath);
            }
            
            //make sure user is not deleting a path with 1 or more votes
            if (getVotesForValue(distinguishingXpath, value) != null){
                SurveyLog.debug("failed to delete value: " + value + " because it has 1 or more votes");
                return;
            }
            
            SurveyLog.debug("V4v: " + locale + " " + distinguishingXpath + " : " + user + " deleting '" + value + "'");
            ModifyDenial denial = UserRegistry.userCanModifyLocaleWhy(user, locale); // this
                                                                                     // has
                                                                                     // to
                                                                                     // do
                                                                                     // with
                                                                                     // changing
                                                                                     // a
                                                                                     // vote
                                                                                     // -
                                                                                     // not
                                                                                     // counting
                                                                                     // it.
            if (denial != null) {
                throw new IllegalArgumentException("User " + user + " cannot modify " + locale + " " + denial);
            }

            if (value != null && value.length() > MAX_VAL_LEN) {
                throw new IllegalArgumentException("Value exceeds limit of " + MAX_VAL_LEN);
            }

            if (!readonly) {
                makeSource(false);
                ElapsedTimer et = !SurveyLog.DEBUG ? null : new ElapsedTimer("{0} Recording PLD for " + locale + " "
                    + distinguishingXpath + " : " + user + " deleting '" + value);
                Connection conn = null;
                PreparedStatement ps = null;
                try {
                    conn = DBUtils.getInstance().getDBConnection();
                    
                    ps = DBUtils.prepareForwardReadOnly(conn, "DELETE FROM " + DBUtils.Table.VOTE_VALUE_ALT + " where value=? ");
                    
                    DBUtils.setStringUTF8(ps, 1, value);

                    ps.executeUpdate();

                    conn.commit();
                } catch (SQLException e) {
                    SurveyLog.logException(e);
                    SurveyMain.busted("Could not delete value " + value + " in locale locale " + locale, e);
                    throw new InternalError("Could not load locale " + locale + " : " + DBUtils.unchainSqlException(e));
                } finally {
                    DBUtils.close(ps, conn);
                }
                SurveyLog.debug(et);
            } else {
                readonly();
            }

            internalDeleteValue(user, distinguishingXpath, value, null, xmlsource); // will create/throw away a resolver.
        }
        
        /**
         * @param user
         * @param distinguishingXpath
         * @param value
         * @param source
         * @return
         */
        private final VoteResolver<String> internalDeleteValue(User user, String distinguishingXpath, String value,
            VoteResolver<String> resolver, DataBackedSource source) throws InvalidXPathException {
            if (!getPathsForFile().contains(distinguishingXpath)) {
                throw new InvalidXPathException(distinguishingXpath);
            }
            getXpathToOthers(sm.xpt.getByXpath(distinguishingXpath)).remove(value);
            stamp.next();
            return resolver = source.setValueFromResolver(distinguishingXpath, resolver);
        }

        /**
         * Get the xpathToOthers set, creating if it doesn't exist.
         * 
         * @param distinguishingXpath
         * @return
         */
        private Set<String> getXpathToOthers(int id) {
            Set<String> s = xpathToOtherValues.get(id);
            if (s == null) {
                s = new TreeSet<String>();
                xpathToOtherValues.put(id, s);
            }
            return s;
        }
        
        @Override
        public boolean userDidVote(User myUser, String somePath) {
            Map<User, String> x = getXpathToVotes(somePath);
            if (x == null)
                return false;
            if (x.containsKey(myUser))
                return true;
            // if(allVoters.contains(myUser)) return true; // voted for null
            return false;
        }

        public TestResultBundle getTestResultData(CheckCLDR.Options options) {
            synchronized (gTestCache) {
                return gTestCache.getBundle(options);
            }
        }

        public Set<String> getPathsForFile() {
            return pathsForFile;
        }

        private Set<String> pathsForFile = null;

        BitSet votesSometimeThisRelease = null;

        @Override
        public boolean hadVotesSometimeThisRelease(int xpath) {
            if (votesSometimeThisRelease != null) {
                return votesSometimeThisRelease.get(xpath);
            } else {
                return false; // unknown.
            }
        }

        @Override
        public Map<User, Integer> getOverridesPerUser(String xpath) {
            return this.getXpathToOverride(xpath);
        }
    }

    private static boolean checkHadVotesSometimeThisRelease = true;

    /**
     * @author srl
     * 
     */
    public class DelegateXMLSource extends XMLSource {
        protected XMLSource delegate;

        public DelegateXMLSource(CLDRLocale locale) {
            setLocaleID(locale.getBaseName());

            delegate = sm.getDiskFactory().makeSource(locale.getBaseName());
        }

        public DelegateXMLSource(XMLSource source) {
            setLocaleID(source.getLocaleID());
            delegate = source;
        }

        /*
         * (non-Javadoc)
         * 
         * @see com.ibm.icu.util.Freezable#freeze()
         */
        @Override
        public XMLSource freeze() {
            readonly();
            return null;
        }

        /*
         * (non-Javadoc)
         * 
         * @see
         * org.unicode.cldr.util.XMLSource#getFullPathAtDPath(java.lang.String)
         */
        @Override
        public String getFullPathAtDPath(String path) {
            return delegate.getFullPathAtDPath(path);
        }

        /*
         * (non-Javadoc)
         * 
         * @see
         * org.unicode.cldr.util.XMLSource#getValueAtDPath(java.lang.String)
         */
        @Override
        public String getValueAtDPath(String path) {
            String v = delegate.getValueAtDPath(path);
            // SurveyLog.logger.warning("@@@@ ("+this.getLocaleID()+")" +
            // path+"="+v);
            return v;
        }

        /*
         * (non-Javadoc)
         * 
         * @see org.unicode.cldr.util.XMLSource#getXpathComments()
         */
        @Override
        public Comments getXpathComments() {
            return delegate.getXpathComments();
        }

        /*
         * (non-Javadoc)
         * 
         * @see org.unicode.cldr.util.XMLSource#iterator()
         */
        @Override
        public Iterator<String> iterator() {
            return delegate.iterator();
        }

        /*
         * (non-Javadoc)
         * 
         * @see
         * org.unicode.cldr.util.XMLSource#putFullPathAtDPath(java.lang.String,
         * java.lang.String)
         */
        @Override
        public void putFullPathAtDPath(String distinguishingXPath, String fullxpath) {
            readonly();
        }

        /*
         * (non-Javadoc)
         * 
         * @see
         * org.unicode.cldr.util.XMLSource#putValueAtDPath(java.lang.String,
         * java.lang.String)
         */
        @Override
        public void putValueAtDPath(String distinguishingXPath, String value) {
            readonly();
        }

        /*
         * (non-Javadoc)
         * 
         * @see
         * org.unicode.cldr.util.XMLSource#removeValueAtDPath(java.lang.String)
         */
        @Override
        public void removeValueAtDPath(String distinguishingXPath) {
            readonly();
        }

        /*
         * (non-Javadoc)
         * 
         * @see
         * org.unicode.cldr.util.XMLSource#setXpathComments(org.unicode.cldr
         * .util.XPathParts.Comments)
         */
        @Override
        public void setXpathComments(Comments comments) {
            readonly();
        }

        @Override
        public void getPathsWithValue(String valueToMatch, String pathPrefix, Set<String> result) {
            delegate.getPathsWithValue(valueToMatch, pathPrefix, result);

        }

        // /* (non-Javadoc)
        // * @see org.unicode.cldr.util.XMLSource#make(java.lang.String)
        // */
        // @Override
        // public XMLSource make(String localeID) {
        // return makeSource(localeID, this.isResolving());
        // }

        // /* (non-Javadoc)
        // * @see org.unicode.cldr.util.XMLSource#getAvailableLocales()
        // */
        // @SuppressWarnings("rawtypes")
        // @Override
        // public Set getAvailableLocales() {
        // return handleGetAvailable();
        // }

        // @Override
        // protected synchronized TreeMap<String, String> getAliases() {
        // if(true) throw new InternalError("NOT IMPLEMENTED.");
        // return null;
        // }

        @Override
        public VersionInfo getDtdVersionInfo() {
            return delegate.getDtdVersionInfo();
        }
    }

    // private static final String SOME_KEY =
    // "//ldml/localeDisplayNames/keys/key[@type=\"collation\"]";

    /**
     * Is this a locale that can't be modified?
     * 
     * @param loc
     * @return
     */
    public static final boolean isReadOnlyLocale(CLDRLocale loc) {
        return SurveyMain.getReadOnlyLocales().contains(loc);
    }

    /**
     * Is this a locale that can't be modified?
     * 
     * @param loc
     * @return
     */
    public static final boolean isReadOnlyLocale(String loc) {
        return isReadOnlyLocale(CLDRLocale.getInstance(loc));
    }

    private static void readonly() {
        throw new InternalError("This is a readonly instance.");
    }

    /**
     * Throw an error.
     */
    static public void unimp() {
        throw new InternalError("NOT YET IMPLEMENTED - TODO!.");
    }

    boolean dbIsSetup = false;

    /**
     * Test cache against (this)
     */
    TestCache gTestCache = new SimpleTestCache();
    /**
     * Test cache against disk. For rejecting items.
     */
    TestCache gDiskTestCache = new SimpleTestCache();

    /**
     * The infamous back-pointer.
     */
    public SurveyMain sm = null;

    private org.unicode.cldr.util.PathHeader.Factory phf;

    /**
     * Construct one.
     */
    public STFactory(SurveyMain sm) {
        super();
        this.sm = sm;
        setSupplementalDirectory(sm.getDiskFactory().getSupplementalDirectory());

        gTestCache.setFactory(this, "(?!.*(CheckCoverage).*).*", sm.getBaselineFile());
        gDiskTestCache.setFactory(sm.getDiskFactory(), "(?!.*(CheckCoverage).*).*", sm.getBaselineFile());
        sm.reg.addListener(this);
        handleUserChanged(null);
        phf = PathHeader.getFactory(sm.getBaselineFile());
        surveyMenus = new SurveyMenus(this, phf);
    }
    
    /**
     * For statistics
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(super.toString());
        sb.append("-cache:");
        int good=0;
        for(Entry<CLDRLocale, Reference<PerLocaleData>> e : locales.entrySet()) {
            if(e.getValue().get()!=null) {
                good++;
            }
        }
        sb.append(good+"/"+locales.size()+" locales. TestCache:"+gTestCache+", diskTestCache:"+gDiskTestCache+"}");
        return sb.toString();
    }

    @Override
    public BallotBox<User> ballotBoxForLocale(CLDRLocale locale) {
        return get(locale);
    }

    /**
     * Per locale map
     */
    private Map<CLDRLocale, Reference<PerLocaleData>> locales = new HashMap<CLDRLocale, Reference<PerLocaleData>>();

    private LruMap<CLDRLocale, PerLocaleData> rLocales = new LruMap<CLDRLocale, PerLocaleData>(5);

    private Map<CLDRLocale, MutableStamp> localeStamps = new ConcurrentHashMap<CLDRLocale, MutableStamp>(SurveyMain.getLocales().length);

    /**
     * Peek at the stamp (changetime) for a locale. May be null, meaning we don't know what the stamp is.
     * If the locale has gone out of scope (GC) it will return the old stamp, rather than 
     * @param loc
     * @return
     */
    public Stamp peekLocaleStamp(CLDRLocale loc) {
        MutableStamp ms = localeStamps.get(loc);
        return ms;
    }

    /**
     * Return changetime. 
     * @param locale
     * @return
     */
    public MutableStamp mintLocaleStamp(CLDRLocale locale) {
        MutableStamp s = localeStamps.get(locale);
        if (s == null) {
            s = MutableStamp.getInstance();
            localeStamps.put(locale, s);
        }
        return s;
    }

    /**
     * Get the locale stamp, loading the locale if not loaded.
     * @param loc
     * @return
     */
    public Stamp getLocaleStamp(CLDRLocale loc) {
        return get(loc).getStamp();
    }

    /**
     * Fetch a locale from the per locale data, create if not there.
     * 
     * @param locale
     * @return
     */
    private synchronized final PerLocaleData get(CLDRLocale locale) {
        PerLocaleData pld = rLocales.get(locale);
        if (pld == null) {
            Reference<PerLocaleData> ref = locales.get(locale);
            if (ref != null) {
                SurveyLog.debug("STFactory: " + locale + " was not in LRUMap.");
                pld = ref.get();
                if (pld == null) {
                    SurveyLog.debug("STFactory: " + locale + " was GC'ed." + SurveyMain.freeMem());
                    ref.clear();
                }
            }
            if (pld == null) {
                pld = new PerLocaleData(locale);
                rLocales.put(locale, pld);
                locales.put(locale, (new SoftReference<PerLocaleData>(pld)));
                // update the locale display name cache.
                OutputFileManager.updateLocaleDisplayName(pld.getFile(true), locale);
            } else {
                rLocales.put(locale, pld); // keep it in the lru
            }
        }
        return pld;
    }

    private final PerLocaleData get(String locale) {
        return get(CLDRLocale.getInstance(locale));
    }

    public TestCache.TestResultBundle getTestResult(CLDRLocale loc, CheckCLDR.Options options) {
//        System.err.println("Fetching: " + options);
        return get(loc).getTestResultData(options);
    }

    public ExampleGenerator getExampleGenerator() {
        CLDRFile fileForGenerator = sm.getBaselineFile();

        if (fileForGenerator == null) {
            SurveyLog.logger.warning("Err: fileForGenerator is null for ");
        }
        ExampleGenerator exampleGenerator = new ExampleGenerator(fileForGenerator, sm.getBaselineFile(), SurveyMain.fileBase
            + "/../supplemental/");
        exampleGenerator.setVerboseErrors(sm.twidBool("ExampleGenerator.setVerboseErrors"));
        return exampleGenerator;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.unicode.cldr.util.Factory#getMinimalDraftStatus()
     */
    @Override
    public DraftStatus getMinimalDraftStatus() {
        return DraftStatus.unconfirmed;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.unicode.cldr.util.Factory#getSourceDirectory()
     */
    @Override
    public File[] getSourceDirectories() {
        return sm.getDiskFactory().getSourceDirectories();
    }

    @Override
    public File getSourceDirectoryForLocale(String localeID) {
        return sm.getDiskFactory().getSourceDirectoryForLocale(localeID);
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.unicode.cldr.util.Factory#handleGetAvailable()
     */
    @Override
    protected Set<String> handleGetAvailable() {
        return sm.getDiskFactory().getAvailable();
    }

    private Map<CLDRLocale, Set<CLDRLocale>> subLocaleMap = new HashMap<CLDRLocale, Set<CLDRLocale>>();
    Set<CLDRLocale> allLocales = null;

    /**
     * Cache..
     */
    public Set<CLDRLocale> subLocalesOf(CLDRLocale forLocale) {
        Set<CLDRLocale> result = subLocaleMap.get(forLocale);
        if (result == null) {
            result = calculateSubLocalesOf(forLocale, getAvailableCLDRLocales());
            subLocaleMap.put(forLocale, result);
        }
        return result;
    }

    /**
     * Cache..
     */
    public Set<CLDRLocale> getAvailableCLDRLocales() {
        if (allLocales == null) {
            allLocales = CLDRLocale.getInstance(getAvailable());
        }
        return allLocales;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.unicode.cldr.util.Factory#handleMake(java.lang.String, boolean,
     * org.unicode.cldr.util.CLDRFile.DraftStatus)
     */
    @Override
    protected CLDRFile handleMake(String localeID, boolean resolved, DraftStatus madeWithMinimalDraftStatus) {
        return get(localeID).getFile(resolved);
    }

    public CLDRFile make(CLDRLocale loc, boolean resolved) {
        return make(loc.getBaseName(), resolved);
    }

    public XMLSource makeSource(String localeID, boolean resolved) {
        if (localeID == null)
            return null; // ?!
        return get(localeID).makeSource(resolved);
    }

    /**
     * Prepare statement. Args: locale Result: xpath,submitter,value
     * 
     * @param conn
     * @return
     * @throws SQLException
     */
    private PreparedStatement openQueryByLocaleRW(Connection conn) throws SQLException {
        setupDB();
        return DBUtils
            .prepareForwardUpdateable(conn, "SELECT xpath,submitter,value,locale,"+VOTE_OVERRIDE+" FROM " + DBUtils.Table.VOTE_VALUE + " WHERE locale = ?");
    }

    private synchronized final void setupDB() {
        if (dbIsSetup)
            return;
        dbIsSetup = true; // don't thrash.
        Connection conn = null;
        String sql = "(none)"; // this points to
        Statement s = null;
        try {
            conn = DBUtils.getInstance().getDBConnection();
            if (!DBUtils.hasTable(conn, DBUtils.Table.VOTE_VALUE.toString())) {
                /*
                 * CREATE TABLE cldr_votevalue ( locale VARCHAR(20), xpath INT
                 * NOT NULL, submitter INT NOT NULL, value BLOB );
                 * 
                 * CREATE UNIQUE INDEX cldr_votevalue_unique ON cldr_votevalue
                 * (locale,xpath,submitter);
                 */
                s = conn.createStatement();

                sql = "create table " + DBUtils.Table.VOTE_VALUE + "( "
                            + "locale VARCHAR(20), "
                            + "xpath  INT NOT NULL, "
                            + "submitter INT NOT NULL, " + "value " + DBUtils.DB_SQL_UNICODE + ", " 
                            + DBUtils.DB_SQL_LAST_MOD + ", "
                            + VOTE_OVERRIDE +" INT DEFAULT NULL, "
                    + " PRIMARY KEY (locale,submitter,xpath) " +

                    " )";
                // SurveyLog.logger.info(sql);
                s.execute(sql);

                sql = "CREATE UNIQUE INDEX  " + DBUtils.Table.VOTE_VALUE + " ON " + DBUtils.Table.VOTE_VALUE +" (locale,xpath,submitter)";
                s.execute(sql);
                s.close();
                s = null; // don't close twice.
                conn.commit();
                System.err.println("Created table " + DBUtils.Table.VOTE_VALUE);
            }
            if (!DBUtils.hasTable(conn, DBUtils.Table.VOTE_VALUE_ALT.toString())) {
                s = conn.createStatement();
                String valueLen = DBUtils.db_Mysql ? "(750)" : "";
                sql = "create table " + DBUtils.Table.VOTE_VALUE_ALT + "( " + "locale VARCHAR(20), " + "xpath  INT NOT NULL, " + "value "
                    + DBUtils.DB_SQL_UNICODE + ", " +
                    // DBUtils.DB_SQL_LAST_MOD + " " +
                    " PRIMARY KEY (locale,xpath,value" + valueLen + ") " + " )";
                // SurveyLog.logger.info(sql);
                s.execute(sql);

                sql = "CREATE UNIQUE INDEX  " + DBUtils.Table.VOTE_VALUE_ALT + " ON " + DBUtils.Table.VOTE_VALUE_ALT+ " (locale,xpath,value" + valueLen + ")";
                s.execute(sql);
                // sql = "CREATE INDEX  " + DBUtils.Table.VOTE_VALUE_ALT +
                // " ON cldr_votevalue_alt (locale)";
                // s.execute(sql);
                s.close();
                s = null; // don't close twice.
                conn.commit();
                System.err.println("Created table " + DBUtils.Table.VOTE_VALUE_ALT);
            }
            if (!DBUtils.hasTable(conn, DBUtils.Table.VOTE_FLAGGED.toString())) {
                s = conn.createStatement();

                sql = "create table " + DBUtils.Table.VOTE_FLAGGED + "( " + "locale VARCHAR(20), " + "xpath  INT NOT NULL, "
                    + "submitter INT NOT NULL, " + DBUtils.DB_SQL_LAST_MOD + " "
                    + ", PRIMARY KEY (locale,xpath) " +
                    " )";
                // SurveyLog.logger.info(sql);
                s.execute(sql);

                sql = "CREATE UNIQUE INDEX  " + DBUtils.Table.VOTE_FLAGGED + " ON " + DBUtils.Table.VOTE_FLAGGED +" (locale,xpath)";
                s.execute(sql);
                s.close();
                s = null; // don't close twice.
                conn.commit();
                System.err.println("Created table " + DBUtils.Table.VOTE_FLAGGED);
            }
        } catch (SQLException se) {
            SurveyLog.logException(se, "SQL: " + sql);
            SurveyMain.busted("Setting up DB for STFactory, SQL: " + sql, se);
            throw new InternalError("Setting up DB for STFactory, SQL: " + sql);
        } finally {
            DBUtils.close(s, conn);
        }
    }
    
    /**
     * Flag the specified xpath for review.
     * @param conn
     * @param locale
     * @param xpath
     * @param user
     * @throws SQLException
     * @return number of rows changed
     */
    public int setFlag(Connection conn, CLDRLocale locale, int xpath, User user) throws SQLException {
        PreparedStatement ps = null;
        try {
            synchronized(STFactory.class) {
                final Pair<CLDRLocale, Integer> theKey = new Pair<CLDRLocale,Integer>(locale,xpath);
                final Set<Pair<CLDRLocale, Integer>> m = loadFlag();
                if(m.contains(theKey)) {
                    return 0; // already there.
                }
                m.add(theKey);
            } // make sure that the DB is loaded before we attempt to update.
            if(DBUtils.db_Mysql) {
                ps = DBUtils.prepareStatementWithArgs(conn, "INSERT IGNORE INTO " + DBUtils.Table.VOTE_FLAGGED +
                    " (locale,xpath,submitter) VALUES (?,?,?)", locale.toString(), xpath, user.id);
            } else {
                ps = DBUtils.prepareStatementWithArgs(conn, "INSERT INTO " + DBUtils.Table.VOTE_FLAGGED +
                    " (locale,xpath,submitter) VALUES (?,?,?)", locale.toString(), xpath, user.id);
            }
            int rv = ps.executeUpdate();
            return rv;
        } finally {
            DBUtils.close(ps);
        }
    }

    /**
     * Flag the specified xpath for review.
     * @param conn
     * @param locale
     * @param xpath
     * @param user
     * @throws SQLException
     * @return number of rows changed
     */
    public int clearFlag(Connection conn, CLDRLocale locale, int xpath, User user) throws SQLException {
        PreparedStatement ps = null;
        try {
            synchronized(STFactory.class) {
                loadFlag().remove(new Pair<CLDRLocale,Integer>(locale,xpath));
            } // make sure DB is loaded before we attempt to update
            ps = DBUtils.prepareStatementWithArgs(conn, "DELETE FROM " + DBUtils.Table.VOTE_FLAGGED +
                " WHERE locale=? AND xpath=?", locale.toString(), xpath);
            int rv = ps.executeUpdate();
            return rv;
        } finally {
            DBUtils.close(ps);
        }
    }
    
    /**
     * Returns userid of flagger, or null if not flagged
     * @param locale
     * @param xpath
     * @return user or null
     */
    public boolean getFlag(CLDRLocale locale, int xpath) {
        synchronized(STFactory.class) {
            return loadFlag().contains(new Pair<CLDRLocale,Integer>(locale,xpath));
        }
    }
    
    public boolean haveFlags() {
        synchronized(STFactory.class) {
            return !(loadFlag().isEmpty());
        }
    }
    
    /**
     * Bottleneck for flag functions.
     * @return
     */
    private Set<Pair<CLDRLocale, Integer>> loadFlag() {
        if(flagList == null) {
            setupDB();

            flagList = new HashSet<Pair<CLDRLocale,Integer>>();
            
            System.out.println("Loading flagged items from .." + DBUtils.Table.VOTE_FLAGGED);
            try {
                for(Map<String, Object> r : DBUtils.queryToArrayAssoc("select * from " + DBUtils.Table.VOTE_FLAGGED)) {
                    flagList.add(new Pair<CLDRLocale, Integer>(CLDRLocale.getInstance(r.get("locale").toString()),
                        (Integer)r.get("xpath")));
                }
                System.out.println("Loaded " + flagList.size() + " items into flagged list.");
            } catch(SQLException sqe) {
                SurveyMain.busted("loading flagged votes from " + DBUtils.Table.VOTE_FLAGGED, sqe);
            } catch(IOException ioe) {
                SurveyMain.busted("loading flagged votes from " + DBUtils.Table.VOTE_FLAGGED, ioe);
            }
        }
        return flagList;
    }
    
    /**
     * In memory cache.
     */
    private Set<Pair<CLDRLocale,Integer>> flagList = null;
    
    /**
     * Close and re-open the factory. For testing only!
     * 
     * @return
     */
    public STFactory TESTING_shutdownAndRestart() {
        sm.TESTING_removeSTFactory();
        return sm.getSTFactory();
    }

    @Override
    public synchronized void handleUserChanged(User u) {
        VoteResolver.setVoterToInfo(sm.reg.getVoterToInfo());
    }

    public final PathHeader getPathHeader(String xpath) {
        try {
            return phf.fromPath(xpath);
        } catch (Throwable t) {
            SurveyLog.warnOnce("PH for path " + xpath + t.toString());
            return null;
        }
    }

    private SurveyMenus surveyMenus;

    public final SurveyMenus getSurveyMenus() {
        return surveyMenus;
    }

    /**
     * Resolving old file, or null if none.
     * 
     * @param locale
     * @return
     */
    public CLDRFile getOldFile(CLDRLocale locale) {
        return get(locale).getOldFile();
    }

    /**
     * Resolving disk file, or null if none.
     * 
     * @param locale
     * @return
     */
    public CLDRFile getDiskFile(CLDRLocale locale) {
        return sm.getDiskFactory().make(locale.getBaseName(), true);
    }

    /**
     * Return all xpaths for this locale. uses CLDRFile iterator, etc
     * @param locale
     * @return
     */
    public Set<String> getPathsForFile(CLDRLocale locale) {
        return get(locale).getPathsForFile();
    }

    /**
     * Get paths for file matching a prefix. Does not cache.
     * @param locale
     * @param xpathPrefix
     * @return
     */
    public Set<String> getPathsForFile(CLDRLocale locale, String xpathPrefix) {
        Set<String> ret = new HashSet<String>();
        for (String s : getPathsForFile(locale)) {
            if (s.startsWith(xpathPrefix)) {
                ret.add(s);
            }
        }
        return ret;
    }
//
//    /**
//     * Load the 'cldr_v22submission' table.
//     * 
//     * @param forLocale
//     * @return
//     */
//    private BitSet loadVotesSometimeThisRelease(CLDRLocale forLocale) {
//        Connection conn = null;
//        PreparedStatement ps = null;
//        ResultSet rs = null;
//        int n = 0;
//        BitSet result = new BitSet(CookieSession.sm.xpt.count());
//        String tableName = getVotesSometimeTableName();
//        try {
//            conn = DBUtils.getInstance().getDBConnection();
//
//            if (!DBUtils.hasTable(conn, tableName)) {
//                SurveyLog.warnOnce(StackTracker.currentElement(0) + ": no table (this is probably OK):" + tableName);
//                return null;
//            }
//
//            ps = DBUtils.prepareForwardReadOnly(conn, "select xpath from " + tableName + " where locale=?");
//            ps.setString(1, forLocale.getBaseName());
//            rs = ps.executeQuery();
//
//            while (rs.next()) {
//                int xp = rs.getInt(1);
//                result.set(xp);
//                n++;
//            }
//        } catch (SQLException e) {
//            SurveyLog.logException(e, "loadVotesSometimeThisRelease for " + tableName + " " + forLocale);
//            return null;
//        } finally {
//            DBUtils.close(rs, ps, conn);
//        }
//        System.err.println("loadVotesSometimeThisRelease: " + n + " xpaths from " + tableName + " " + forLocale);
//        return result;
//    }
//
//    private String getVotesSometimeTableName() {
//        return ("cldr_v" + SurveyMain.getNewVersion() + "submission").toLowerCase();
//    }

    /*
     * votes sometime table
     * 
     * DERBY create table cldr_v22submission ( xpath integer not null, locale
     * varchar(20) ); create unique index cldr_v22submission_uq on
     * cldr_v22submission ( xpath, locale );
     * 
     * insert into cldr_v22submission select distinct
     * cldr_votevalue.xpath,cldr_votevalue.locale from cldr_votevalue where
     * cldr_votevalue.value is not null;
     * 
     * 
     * MYSQL drop table if exists cldr_v22submission; create table
     * cldr_v22submission ( primary key(xpath,locale),key(locale) ) select
     * distinct cldr_votevalue.xpath,cldr_votevalue.locale from cldr_votevalue
     * where cldr_votevalue.value is not null;
     */
    public CLDRFile makeProposedFile(CLDRLocale locale) {

        Connection conn = null;
        PreparedStatement ps = null; // all for mysql, or 1st step for derby
        ResultSet rs = null;
        SimpleXMLSource sxs = new SimpleXMLSource(locale.getBaseName());
        try {
            conn = DBUtils.getInstance().getDBConnection();

            ps = DBUtils.prepareStatementWithArgsFRO(conn, "select xpath,submitter,value,"+VOTE_OVERRIDE+" from " + DBUtils.Table.VOTE_VALUE
                + " where locale=? and value IS NOT NULL", locale);

            rs = ps.executeQuery();
            XPathParts xpp = new XPathParts(null, null);
            while (rs.next()) {
                String xp = sm.xpt.getById(rs.getInt(1));
                int sub = rs.getInt(2);
                Integer voteValue = rs.getInt(4);
                if(voteValue==0 && rs.wasNull()) {
                    voteValue = null;
                }

                StringBuilder sb = new StringBuilder(xp);
                String alt = null;
                if (xp.contains("[@alt")) {
                    alt = XPathTable.getAlt(xp, xpp);
                    sb = new StringBuilder(sm.xpt.removeAlt(xp, xpp)); // replace
                }

                sb.append("[@alt=\"");
                if (alt != null) {
                    sb.append(alt);
                    sb.append('-');
                }
                XPathTable.appendAltProposedPrefix(sb, sub, voteValue);
                sb.append("\"]");

                sxs.putValueAtPath(sb.toString(), DBUtils.getStringUTF8(rs, 3)); // value
                                                                                 // is
                                                                                 // never
                                                                                 // null,
                                                                                 // due
                                                                                 // to
                                                                                 // SQL
            }

            CLDRFile f = new CLDRFile(sxs);
            return f;
        } catch (SQLException e) {
            SurveyLog.logException(e);
            SurveyMain.busted("Could not read locale " + locale, e);
            throw new InternalError("Could not load locale " + locale + " : " + DBUtils.unchainSqlException(e));
        } finally {
            DBUtils.close(rs, ps, conn);
        }
    }

    /**
     * Read back a dir full of pxml files
     * 
     * @param sm
     * @param inFile
     *            dir containing pxmls
     * @return
     */
    public Integer[] readPXMLFiles(final File inFileList[]) {
        int nusers = 0;
        //int nlocs = 0;
        if (CLDRConfig.getInstance().getEnvironment() != Environment.LOCAL) {
            throw new InternalError("Error: can only do this in LOCAL"); // insanity
                                                                         // check
        }

        Vector<Integer> ret = new Vector<Integer>();

        Connection conn = null;
        PreparedStatement ps = null;
        PreparedStatement ps2 = null;
        //PreparedStatement ps3 = null;
        //int maxUserId = 0;
        try { // do this in 1 transaction. just in case.
            conn = DBUtils.getInstance().getDBConnection();

            ps = DBUtils.prepareStatementWithArgs(conn, "delete from " + DBUtils.Table.VOTE_VALUE);
            int del = ps.executeUpdate();
            ps2 = DBUtils.prepareStatementWithArgs(conn, "delete from " + DBUtils.Table.VOTE_VALUE_ALT);
            del += ps2.executeUpdate();
            System.err.println("DELETED " + del + "regular votes .. reading from files");

            XMLFileReader myReader = new XMLFileReader();
            final XPathParts xpp = new XPathParts(null, null);
            //final Map<String, String> attrs = new TreeMap<String, String>();
            // final Map<String,UserRegistry.User> users = new
            // TreeMap<String,UserRegistry.User>();

            // <user id="10" email="u_10@apple.example.com" level="vetter"
            // name="Apple#10" org="apple" locales="nl nl_BE nl_NL"/>
            // >>
            // //users/user[@id="10"][@email="__"][@level="vetter"][@name="Apple"][@org="apple"][@locales="nl.. "]
            final PreparedStatement myInsert = ps2 = DBUtils.prepareStatementForwardReadOnly(conn, "myInser", "INSERT INTO  "
                + DBUtils.Table.VOTE_VALUE + " (locale,xpath,submitter,value,"+VOTE_OVERRIDE+") VALUES (?,?,?,?) ");
            final SurveyMain sm2 = sm;
            myReader.setHandler(new XMLFileReader.SimpleHandler() {
                int nusers = 0;
                int maxUserId = 1;

                public void handlePathValue(String path, String value) {
                    String alt = XPathTable.getAlt(path, xpp);

                    if (alt == null || !alt.contains(XPathTable.PROPOSED_U))
                        return; // not an alt proposed
                    String altParts[] = LDMLUtilities.parseAlt(alt);
                    StringBuilder newPath = new StringBuilder(XPathTable.removeAlt(path, xpp));
                    if (altParts[0] != null) {
                        newPath.append("[@alt=\"" + altParts[0] + "\"]");
                    }

                    try {
                        myInsert.setInt(2, sm2.xpt.getByXpath(newPath.toString()));
                        Integer voteValueArray[] = new Integer[1];
                        // TODO: need to handle a string like 'proposed-u8v4-' to re-introduce the voting override at vote level 4.
                        if(true) throw new InternalError("TODO: don't know how to handle voting overrides on read-in");
                        //myInsert.setInt(3, XPathTable.altProposedToUserid(altParts[1], voteValueArray));
                        DBUtils.setStringUTF8(myInsert, 4, value);
                        myInsert.executeUpdate();
                    } catch (SQLException e) {
                        SurveyLog.logException(e, "importing  - " + path + " = " + value);
                        throw new IllegalArgumentException(e);
                    }
                };
                // public void handleComment(String path, String comment) {};
                // public void handleElementDecl(String name, String model) {};
                // public void handleAttributeDecl(String eName, String aName,
                // String type, String mode, String value) {};
            });

            for (File inFile : inFileList) {
                System.out.println("Reading pxmls from " + inFile.getAbsolutePath());
                for (File theFile : inFile.listFiles()) {
                    if (!theFile.isFile())
                        continue;
                    CLDRLocale loc = SurveyMain.getLocaleOf(theFile.getName());
                    System.out.println("Reading: " + loc + " from " + theFile.getAbsolutePath());
                    myInsert.setString(1, loc.getBaseName());
                    myReader.read(theFile.getAbsolutePath(), -1, false);
                    nusers++;
                }
                ret.add(nusers); // add to the list
                System.out.println("  .. read " + nusers + "  pxmls from " + inFile.getAbsolutePath());
                nusers = 0;
            }

            conn.commit();
        } catch (SQLException e) {
            SurveyLog.logException(e, "importing locale data from files");
        } finally {
            DBUtils.close(ps2, ps, conn);
        }
        return ret.toArray(new Integer[2]);
    }

    /**
     * get the bundle for testing against on-disk data.
     * @return
     */
    private TestResultBundle getDiskTestBundle(CLDRLocale locale) {
        synchronized (gDiskTestCache) {
            TestResultBundle q;
            q = gDiskTestCache.getBundle(new CheckCLDR.Options(locale,SurveyMain.getTestPhase(),null,null));
            return q;
        }
    }

    /**
     * Return the table for old votes 
     */
    public static final String getOldVoteTable() {
        return DBUtils.Table.VOTE_VALUE.forVersion(SurveyMain.getOldVersion(), false).toString();
    }
}
