package org.unicode.cldr.tool;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import org.unicode.cldr.util.CLDRConfig;
import org.unicode.cldr.util.CLDRFile;
import org.unicode.cldr.util.Counter;
import org.unicode.cldr.util.Factory;
import org.unicode.cldr.util.Level;
import org.unicode.cldr.util.PathHeader;
import org.unicode.cldr.util.SupplementalDataInfo;
import org.unicode.cldr.util.PathHeader.PageId;
import org.unicode.cldr.util.PathHeader.SectionId;

import com.google.common.base.Joiner;

/**
 *  Tool originally written to test RegexLookup for collisions,
 * @author ribnitz
 *
 */
public class DebugRegexLookup {
    static final CLDRConfig config=  CLDRConfig.getInstance();
    static final Factory factory =config.getCldrFactory();
    static final CLDRFile english = config.getEnglish();
    static final SupplementalDataInfo supplemental = config.getSupplementalDataInfo();
    static PathHeader.Factory pathHeaderFactory = PathHeader.getFactory(english);
    private EnumSet<PageId> badZonePages = EnumSet.of(PageId.UnknownT);
 
    protected void logln(String str) {
        System.out.println(str);
    }
    protected void errln(String str) {
        StackTraceElement[] st=Thread.currentThread().getStackTrace();
        StackTraceElement[] stCleaned=new StackTraceElement[st.length-2];
        System.arraycopy(st, 2, stCleaned, 0, st.length-2);
        if (st.length>2) {
            
        }
        StringBuilder sb=new StringBuilder();
        sb.append("Error: "+str+"\r\n");
        sb.append(Joiner.on("\r\n").join(stCleaned));
        System.out.println(sb.toString());
        System.exit(1);
    }
    public void performTest() {
        final String localeId = "en";
        Counter<Level> counter = new Counter<Level>();
        Map<String, PathHeader> uniqueness = new HashMap<String, PathHeader>();
        Set<String> alreadySeen = new HashSet<String>();
        check(localeId, true, uniqueness, alreadySeen);
        // check paths
        for (Entry<SectionId, Set<PageId>> sectionAndPages : PathHeader.Factory
            .getSectionIdsToPageIds().keyValuesSet()) {
            final SectionId section = sectionAndPages.getKey();
            logln(section.toString());
            for (PageId page : sectionAndPages.getValue()) {
                final Set<String> cachedPaths = PathHeader.Factory.getCachedPaths(section, page);
                if (cachedPaths == null) {
                    if (!badZonePages.contains(page) && page != PageId.Unknown) {
                        errln("Null pages for: " + section + "\t" + page);
                    }
                } else if (section == SectionId.Special && page == PageId.Unknown) {
                    // skip
                } else if (section == SectionId.Timezones && page == PageId.UnknownT) {
                    // skip
                } else {

                    int count2 = cachedPaths.size();
                    if (count2 == 0) {
                        errln("Missing pages for: " + section + "\t" + page);
                    } else {
                        counter.clear();
                        for (String s : cachedPaths) {
                            Level coverage = supplemental.getCoverageLevel(s, localeId);
                            counter.add(coverage, 1);
                        }
                        String countString = "";
                        int total = 0;
                        for (Level item : Level.values()) {
                            long count = counter.get(item);
                            if (count != 0) {
                                if (!countString.isEmpty()) {
                                    countString += ",\t+";
                                }
                                total += count;
                                countString += item + "=" + total;
                            }
                        }
                        logln("\t" + page + "\t" + countString);
                        if (page.toString().startsWith("Unknown")) {
                            logln("\t\t" + cachedPaths);
                        }
                    }
                }
            }
        }
    }
    
    public void check(String localeID, boolean resolved, Map<String, PathHeader> uniqueness,
        Set<String> alreadySeen) {
        CLDRFile nativeFile = factory.make(localeID, resolved);
        int count = 0;
        
        String path1="//ldml/dates/calendars/calendar[@type=\"buddhist\"]/dateTimeFormats/availableFormats/dateFormatItem[@id=\"h\"]";
        String path2="//ldml/dates/calendars/calendar[@type=\"islamic-umalqura\"]/dateTimeFormats/availableFormats/dateFormatItem[@id=\"ms\"]";
        final PathHeader ph1=pathHeaderFactory.fromPath(path1);
        final PathHeader ph2=pathHeaderFactory.fromPath(path2);
        
        for (String path : nativeFile) {
            if (alreadySeen.contains(path)) {
                continue;
            }
            alreadySeen.add(path);
            if (path.equals("//ldml/dates/calendars/calendar[@type=\"buddhist\"]/dateTimeFormats/availableFormats/dateFormatItem[@id=\"h\"]") ||
                    path.equals("//ldml/dates/calendars/calendar[@type=\"buddhist\"]/dateTimeFormats/availableFormats/dateFormatItem[@id=\"h\"]")) {
                int x= 0;
            }
            final PathHeader pathHeader = pathHeaderFactory.fromPath(path);
            ++count;
            if (pathHeader == null) {
                errln("Null pathheader for " + path);
            } else {
                String visible = pathHeader.toString();
                PathHeader old = uniqueness.get(visible);
                if (pathHeader.getSectionId() == SectionId.Timezones) {
                    final PageId pageId = pathHeader.getPageId();
                    if (badZonePages.contains(pageId)) {
                        errln("Bad page ID:\t" + pageId + "\t" + pathHeader + "\t" + path);
                    }
                }
                if (old == null) {
                    if (pathHeader.getSection().equals("Special")) {
                        if (pathHeader.getSection().equals("Unknown")) {
                            errln("PathHeader has fallback: " + visible + "\t"
                                + pathHeader.getOriginalPath());
                            // } else {
                            // logln("Special:\t" + visible + "\t" +
                            // pathHeader.getOriginalPath());
                        }
                    }
                    uniqueness.put(visible, pathHeader);
                } else if (!old.equals(pathHeader)) {
                    if (pathHeader.getSectionId() == SectionId.Special) {
                        logln("Special PathHeader not unique: " + visible + "\t" + pathHeader.getOriginalPath()
                            + "\t" + old.getOriginalPath());
                    } else {
                        errln("PathHeader not unique: " + visible + "\t" + pathHeader.getOriginalPath()
                            + "\t" + old.getOriginalPath());
                    }
                }
            }
        }
        logln(localeID + "\t" + count);
    }
    public static void main(String[] args) {
       new DebugRegexLookup().performTest();
    }

}
