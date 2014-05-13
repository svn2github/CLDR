package org.unicode.cldr.util;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import org.unicode.cldr.util.DayPeriodInfo.DayPeriod;
import org.unicode.cldr.util.SupplementalDataInfo.PluralInfo;
import org.unicode.cldr.util.SupplementalDataInfo.PluralInfo.Count;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;

class PathGenerators {
    public static interface Predicate<E> {
        boolean matches(E item);
    }
    
    public interface GeneratablePath extends Iterable<String> {

        /**
         * Return an unmodifiable view of the resulting Set
         * @return
         */
        Set<String> get();

        /**
         * Provide an unmodifiable set of all resulting elements that match the predicate
         * @param predicate
         * @return
         */
        Set<String> get(Predicate<String> predicate);

        /**
         * Provide an unmodifiable iterator over the resulting elements
         */
        Iterator<String> iterator();

        /**
         * Provide an unmodifiable iteraotr over all elments that match the predicate
         * @param predicate
         * @return
         */
        Iterator<String> iterator(Predicate<String> predicate);

    }
    private static class GuavaPredicateAdapter<E> implements com.google.common.base.Predicate<E> {
        private final Predicate<E> pred;

        public GuavaPredicateAdapter(Predicate<E> aPredicate) {
            this.pred=aPredicate;
        }
        @Override
        public boolean apply(E input) {
            return pred.matches(input);
        }
    }
    /**
     * Simple predicate that looks for a string in the form 'value="foo"', comparing the value of 
     * foo to a Collection, first transforming it as appropriate. Implementing classes only need to
     * provide the method responsible for the transformation.
     * 
     * It will return true for all values that are conrained in the collection.
     * @author ribnitz
     *
     * @param <E>
     */
    public static abstract class KeyInCollectionPredicate<E> implements Predicate<String> {
        private final String searchStr;
        private final Collection<E> coll;
        private final String endSearchStr;
        
        public KeyInCollectionPredicate(String searchStr,Collection<E> aColl) {
            this(searchStr,"\"",aColl);
        }
        
        public  KeyInCollectionPredicate(String searchStr,String endSearchStr,Collection<E> aColl) {
            this.searchStr=searchStr;
            this.coll=Collections.unmodifiableCollection(aColl);
            this.endSearchStr=endSearchStr;
        }
        @Override
        public boolean matches(String item) {
            int countPos=item.indexOf(searchStr);
            if (countPos==-1) {
                return false;
            }
            int startPos=countPos+searchStr.length();
            int pos2=item.indexOf(endSearchStr,startPos+1);
            String countStr=item.substring(startPos,pos2);
            return coll.contains(transform(countStr));
        }
        public abstract E transform(String item);
    }
    /**
     * Base class for all path generators; child classes only need to override the default
     * constructor, in which they fill the result into resultSet. 
     * @author ribnitz
     *
     */
    private static abstract class PathGeneratorBase  implements GeneratablePath {
        protected  final Set<String> resultSet;
     
        public PathGeneratorBase() {
            this(new HashSet<String>());
        }
        
        public PathGeneratorBase(Set<String> aSet) {
            resultSet=aSet;
        }
        
        /**
         * Return an unmodifiable view of the resulting Set
         * @return
         */
        public Set<String> get() {
            if (resultSet==null||resultSet.isEmpty()) {
                return Collections.emptySet();
            }
            return Collections.unmodifiableSet(resultSet);
        }
        
        private FluentIterable<String> iterable(Predicate<String> predicate) {
            return FluentIterable.from(resultSet).filter(new GuavaPredicateAdapter<String>(predicate));
        }
        
        /**
         * Provide an unmodifiable set of all elements that match the predicate
         * @param predicate
         * @return
         */
        public Set<String> get(Predicate<String> predicate) {
            if (resultSet==null||resultSet.isEmpty()) {
                return Collections.emptySet();
            }
            return iterable(predicate).toSet();
        }

        /**
         * Provide an unmodifiable iterator over the resulting elements
         */
        public Iterator<String> iterator() {
            return Collections.unmodifiableSet(resultSet).iterator();
        }
        
        /**
         * Provide an unmodifiable iterator over all elments that match the predicate
         * @param predicate
         * @return
         */
        public Iterator<String> iterator(Predicate<String> predicate) {
            return iterable(predicate).iterator();
        }
    }
    
    /***
     * A class that will generate the plural paths
     * @author ribnitz
     *
     */
    private static class PluralPathGenerator extends PathGeneratorBase {
        
        public PluralPathGenerator(Iterator<String> keys) {
            super(new TreeSet<String>());
            final String countAttr="[@count=\"other\"]";
            Iterable<String> iter=Iterables.filter(Sets.newHashSet(keys), 
                new GuavaPredicateAdapter<String>(
                    new Predicate<String>() {

                @Override
                public boolean matches(String item) {
                    if (item==null||item.isEmpty()) {
                        return false;
                    }
                    return item.contains(countAttr);
                }

            }));
           
            Set<String> workSet=new TreeSet<>();
            Iterables.addAll(workSet, iter);
            for (String item: workSet) {
                int countPos = item.indexOf(countAttr);
                if (countPos < 0) {
                    continue;
                }
                String start = item.substring(0, countPos) + "[@count=\"";
                String end = item.substring(countPos + countAttr.length()) + "\"]";
                for (Count count : Count.values()) {
                    if (count == Count.other) {
                        continue;
                    }
                    resultSet.add(start + count + end);
                }
            }
        }
    }
    
    /**
     * Generator for the currency paths
     * @author ribnitz
     *
     */
    private static class CurrencyPathGenerator extends PathGeneratorBase {
        public CurrencyPathGenerator(SupplementalDataInfo sdi) {
           super(new TreeSet<String>());
            Set<String> codes = sdi.getBcp47Keys().getAll("cu");
            for (String code : codes) {
                String currencyCode = code.toUpperCase();
                resultSet.add("//ldml/numbers/currencies/currency[@type=\"" + currencyCode + "\"]/symbol");
                resultSet.add("//ldml/numbers/currencies/currency[@type=\"" + currencyCode + "\"]/displayName");
                for (Count count : PluralInfo.Count.values()) {
                    resultSet.add("//ldml/numbers/currencies/currency[@type=\"" + currencyCode + "\"]/displayName[@count=\"" + count.toString() + "\"]");
                }
            }
        }
    }
    
    /**
     * Generator that will generate the path for day periods.
     * @author ribnitz
     *
     */
    private static class DayPeriodsPathGenerator extends PathGeneratorBase {
        public DayPeriodsPathGenerator(/*SupplementalDataInfo sdi*/) {
            super();
            for (String context : new String[] { "format", "stand-alone" }) {
                for (String width : new String[] { "narrow", "abbreviated", "wide" }) {
                    for (DayPeriod dayPeriod : DayPeriod.values()) {
                        // ldml/dates/calendars/calendar[@type="gregorian"]/dayPeriods/dayPeriodContext[@type="format"]/dayPeriodWidth[@type="wide"]/dayPeriod[@type="am"]
                        resultSet.add("//ldml/dates/calendars/calendar[@type=\"gregorian\"]/dayPeriods/" +
                            "dayPeriodContext[@type=\"" + context
                            + "\"]/dayPeriodWidth[@type=\"" + width
                            + "\"]/dayPeriod[@type=\"" + dayPeriod + "\"]");
                    }
                }
            }
        }
    }
    
    /***
     * Generator that will generate the metazone paths
     * @author ribnitz
     *
     */
    private static class MetaZonePathGenerator extends PathGeneratorBase {
        
        public MetaZonePathGenerator(SupplementalDataInfo sdi) {
            this(sdi, new String[] {
                "Pacific/Honolulu\"]/short/generic",
                "Pacific/Honolulu\"]/short/standard",
                "Pacific/Honolulu\"]/short/daylight",
                "Europe/Dublin\"]/long/daylight",
                "Europe/London\"]/long/daylight"
            });
        }
        private MetaZonePathGenerator(SupplementalDataInfo sdi,String[] overrides) {
            super(new TreeSet<String>());
            Set<String> zones = sdi.getAllMetazones();

            for (String zone : zones) {
                for (String width : new String[] { "long", "short" }) {
                    for (String type : new String[] { "generic", "standard", "daylight" }) {
                        resultSet.add("//ldml/dates/timeZoneNames/metazone[@type=\"" + zone + "\"]/" + width + "/" + type);
                    }
                }
            }

            for (String override : overrides) {
                resultSet.add("//ldml/dates/timeZoneNames/zone[@type=\"" + override);
            }
        }
    }
    public static GeneratablePath getMetaZonePaths(SupplementalDataInfo sdi) {
        return new MetaZonePathGenerator(sdi);
    }
    private static GeneratablePath DAY_PERIODS=null;
    private static Object DAY_PERIODS_SYNC=new Object();
    public static GeneratablePath getDayPeriodPaths() {
        synchronized (DAY_PERIODS_SYNC) {
            if (DAY_PERIODS==null) {
                DAY_PERIODS=new DayPeriodsPathGenerator();
            }
            return  DAY_PERIODS;
        }
       
    }
    private static Cache<SupplementalDataInfo,GeneratablePath> CURRENCY_PATHS=
        CacheBuilder.newBuilder().initialCapacity(10).maximumSize(50).build();
     public static GeneratablePath getCurrencyPaths(final SupplementalDataInfo sdi) {
         GeneratablePath returned=null;
         synchronized(CURRENCY_PATHS) {
             try {
                returned=CURRENCY_PATHS.get(sdi, new Callable<GeneratablePath>(){

                    @Override
                    public GeneratablePath call() throws Exception {
                      return new CurrencyPathGenerator(sdi);
                    }});
            } catch (ExecutionException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
         }
         return returned;
    }
     private static Cache<Set<String>,GeneratablePath> PLURAL_PATHS=
         CacheBuilder.newBuilder().initialCapacity(10).maximumSize(50).build();
    public static GeneratablePath getPluralPaths(final Iterator<String> paths) {
        GeneratablePath returned=null;
        Set<String> p=Sets.newHashSet(paths);
        synchronized(PLURAL_PATHS) {
            try {
               returned=PLURAL_PATHS.get(p, new Callable<GeneratablePath>(){

                   @Override
                   public GeneratablePath call() throws Exception {
                     return new PluralPathGenerator(paths);
                   }});
           } catch (ExecutionException e) {
               // TODO Auto-generated catch block
               e.printStackTrace();
           }
        }
        return returned;
    }
}
