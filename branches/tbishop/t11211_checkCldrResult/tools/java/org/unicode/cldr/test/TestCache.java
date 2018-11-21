package org.unicode.cldr.test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.unicode.cldr.test.CheckCLDR.CheckStatus;
import org.unicode.cldr.util.CLDRFile;
import org.unicode.cldr.util.Factory;
import org.unicode.cldr.util.Pair;
import org.unicode.cldr.util.XMLSource;

import com.google.common.collect.ImmutableList;

/**
 * Caches tests
 * Call XMLSource.addListener() on the instance to notify it of changes to the XMLSource.
 *
 * @author srl
 * @see XMLSource#addListener(org.unicode.cldr.util.XMLSource.Listener)
 */
public abstract class TestCache implements XMLSource.Listener {
    public class TestResultBundle {
        protected List<CheckStatus> possibleProblems = new ArrayList<CheckStatus>();
        final CLDRFile file;
        final private CheckCLDR cc = createCheck();
        final private CheckCLDR.Options options;
        final private ConcurrentHashMap<Pair<String, String>, List<CheckStatus>> pathCache;
        
        protected TestResultBundle(CheckCLDR.Options cldrOptions) {
            options = cldrOptions;
            pathCache = new ConcurrentHashMap<Pair<String, String>, List<CheckStatus>>();
            file = getFactory().make(options.getLocale().getBaseName(), true);
            cc.setCldrFileToCheck(file, options, possibleProblems);
        }

        public List<CheckStatus> getPossibleProblems() {
            return possibleProblems;
        }

       /**
        * Check the given value for the given path, using this TestResultBundle for
        * options, pathCache and cc (CheckCLDR).
        * 
        * @param path the path
        * @param result the list to which CheckStatus objects may be added; this function
        *               clears any objects that might already be in it
        * @param value the value to be checked
        */
        public void check(String path, List<CheckStatus> result, String value) {
            /*
             * result.clear() is needed to avoid phantom warnings in the Info Panel, if we're called
             * with non-empty result (leftover from another row) and we get cachedResult != null.
             * cc.check() also calls result.clear() (at least as of 2018-11-20) so in that case it's
             * currently redundant here. Clear it here unconditionally to be sure.
             */
            result.clear();
            Pair<String, String> key = new Pair<String, String>(path, value);
            List<CheckStatus> cachedResult = pathCache.get(key);
            if (cachedResult != null) {
                result.addAll(cachedResult);
            }
            else {
                cc.check(path, file.getFullXPath(path), value, options, result);
                pathCache.put(key, ImmutableList.copyOf(result));
            }
        }

        public void getExamples(String path, String value, List<CheckStatus> result) {
            cc.getExamples(path, file.getFullXPath(path), value, options, result);
        }
    }

    private Factory factory = null;
    private String nameMatcher = null;;

    protected Factory getFactory() {
        return factory;
    }

    /**
     * Set up the basic info needed for tests
     *
     * @param factory
     * @param nameMatcher
     * @param displayInformation
     */
    public void setFactory(Factory factory, String nameMatcher, CLDRFile displayInformation) {
        if (this.factory != null) {
            throw new InternalError("setFactory() can only be called once.");
        }
        this.factory = factory;
        this.nameMatcher = nameMatcher;
    }

    /**
     * Get the bundle for this test
     */
    public abstract TestResultBundle getBundle(CheckCLDR.Options options);

    /**
     * Create a check using the options
     */
    protected CheckCLDR createCheck() {
        CheckCLDR checkCldr;
        checkCldr = CheckCLDR.getCheckAll(getFactory(), nameMatcher);
        return checkCldr;
    }
}
