package org.unicode.cldr.util;

import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.unicode.cldr.util.StacktraceUtils.StackTraceSanitizer;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.MultimapBuilder.ListMultimapBuilder;


public class FileOpeningCounter {
    
    /**
     * COUNT_FILE_OPENINGS  controls the type of implementation: 
     * false - do not count file opening
     * true - count file opening
     */
    private static final boolean COUNT_FILE_OPENINGS=false;
    
    /**
     * DEBUG_FILE_READS: true - more verbose output of what files are being read
     */
    private static final boolean DEBUG_READS=false;
    
    /**
     * Static instance, initialized/returned by getInstance
     */
    private static FileOpeningCounter instance=null;
    
    /**
     * Pointer to the implementation of the fileOpeningCounter 
     * (this can avoid allocating data structures that are not needed
     */
    private final FileOpeningCounterInterface internalCounter;
    
   
    
    /**
     * Helper class removing a lock of an item that has been removed.
     * @author ribnitz
     *
     */
    private  static class ItemLockRemover implements Runnable {
        private final String nameRemoved;
        private final LockSupportMap<String> locks;
        private final Map<? extends String,?> controlledMap;

        public ItemLockRemover(String nameRemoved,LockSupportMap<String> locks,Map<? extends String,?> countMap) {
            this.nameRemoved = nameRemoved;
            this.locks=locks;
            this.controlledMap=countMap;
        }

        @Override
        public void run() {
           synchronized(locks.getItemLock(nameRemoved)) {
               if (!controlledMap.containsKey(nameRemoved)) {
                   locks.removeItemLock(nameRemoved);
               }
           }
            
        }
    }
    
    /**
     * Public interface used to return the Collections of Stacktraces.
     * @author ribnitz
     *
     */
    public static interface StacktraceGettable {
        /**
         * Get a Stacktrace as List of StacktraceElements
         * @return
         */
        Collection<StackTraceElement> getStackTrace();
        
        /** 
         * get the creation time as a unix timestamp
         * @return
         */
        long getCreationTime();
    }
    
    /**
     * Internal class to generate the Stacktraces
     * @author ribnitz
     *
     */
    private static class FileOpeningCounterData implements StacktraceGettable {
        private final Collection<StackTraceElement> stackTrace;
        private String  callingClass;
        private String callingMethod;
        private int lineNo;
        private final long creationTime;
        /*
         * This class contains data that is unchanging, once assigned; for this 
         * reason, calculating and storing the hashCode is feasible.
         */
        private final int hashCode;
       
        /**
         * Instantiate getting stacktrace automatically
         */
        public FileOpeningCounterData() {
            this(Arrays.asList(Thread.currentThread().getStackTrace()));
        }
        
        /**
         * Instantiate with stacktrace given
         * @param stack
         */
        public FileOpeningCounterData(Collection<StackTraceElement> stack) {
            stackTrace=stack;
            creationTime=System.nanoTime();
            
            // find the other interesting data
            Iterator<StackTraceElement> iter=stackTrace.iterator();
            while (iter.hasNext()) {
                StackTraceElement cur=iter.next();
               String curCls=cur.getClassName();
               // Thread was used to generate the stacktrace, so this is uninteresting
               if (curCls.equals(Thread.class.getCanonicalName())) {
                   continue;
               }
               // If the stacktrace was generated in this class, the entry containing this class is 
               // probably not interesting
               if (curCls.contains("FileOpeningCounterData")) {
                   continue;
               }
               // The call to FileOpeningCounter is probably not interesting either
               if (curCls.equals(FileOpeningCounter.class.getCanonicalName())) {
                   // these are not interesting
                   continue;
               }
               callingClass=curCls;
               callingMethod=cur.getMethodName();
               lineNo=cur.getLineNumber();
               break;
            }
            // done assigning all fields, calculate the hashCode
            hashCode=Objects.hash(stackTrace,creationTime,callingClass,callingMethod,lineNo);
        }
        public Collection<StackTraceElement> getStackTrace() {
            return Collections.unmodifiableCollection(stackTrace);
        }
        public String getCallingClass() {
            return callingClass;
        }
        public String getCallingMethod() {
            return callingMethod;
        }
        public int getLineNo() {
            return lineNo;
        }
        
        public long getCreationTime() {
            return creationTime;
        }

        @Override
        public int hashCode() {
          return hashCode;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            FileOpeningCounterData other = (FileOpeningCounterData) obj;
            if (hashCode!=other.hashCode) {
                return false;
            }
            // calling class is assumed to be non-null
            if (!callingClass.equals(other.callingClass)) {
                return false;
            }
           // callingMethod is asasumed to be non-null
            if (!callingMethod.equals(other.callingMethod)) {
                return false;
            }
            if (lineNo != other.lineNo) {
                return false;
            }
           // stackTrace is assumed to be non-null
            if (!stackTrace.equals(other.stackTrace)) {
                return false;
            }
            return true;
        }
        
    }
    /**
     * Interface for the different strategies to implement
     * @author ribnitz
     *
     */
    private static interface FileOpeningCounterInterface {
        /**
         * Add the file with the given name to the counting
         * @param fileName
         */
        void add(String fileName);
        
        /**
         * return the Stacktraces associated with the filename 
         * @param filename
         * @return
         */
        Collection<? extends StacktraceGettable> get(String filename);
        
        /**
         * Get a consolidated list of entries (stacktraces, and number of occurrences associated with the given file
         * @param filename
         * @return
         */
        Collection<Entry<? extends StacktraceGettable, Integer>> getConsolidated(String filename);
        
        /**
         * Get a collection of Files, which occur at least minOccurs times
         * @param minOccurs
         * @return
         */
        Collection<Map.Entry<String,Integer>> getKeysWithOcurrences(int minOccurs);
        
        /**
         * Get the number of times Filename occurs
         * @param fileName
         * @return
         */
        int getNumOccurrences(String fileName);
        
        /**
         * Remove all entries associated with filename, and return them as a Collection
         * @param filename
         * @return
         */
        Collection<? extends StacktraceGettable> removeAll(String filename);
    }
    
    /**
     * Null Implementation; does nothing, returns 0/empty structures where needed. Used for the case
     * no logging is done.
     * @author ribnitz
     *
     */
    private static class NullFileOpeningCounter implements FileOpeningCounterInterface {

        @Override
        public void add(String fileName) {
           // do nothing
            
        }

        @Override
        public Collection<? extends StacktraceGettable> get(String filename) {
            return Collections.emptyList();
        }

        @Override
        public Collection<Entry<? extends StacktraceGettable, Integer>> getConsolidated(String filename) {
            return Collections.emptyList();
        }

        @Override
        public Collection<Entry<String, Integer>> getKeysWithOcurrences(int minOccurs) {
            return Collections.emptyList();
        }

        @Override
        public int getNumOccurrences(String fileName) {
            return 0;
        }

        @Override
        public Collection<? extends StacktraceGettable> removeAll(String filename) {
            return Collections.emptyList();
        }
    }
    
    /**
     * Implementation for the case that logging of file opening is enabled.
     * @author ribnitz
     *
     */
    private  class FileOpeningCounterInternal implements FileOpeningCounterInterface {
        /**
         * Multimap to keep a List of FileOpeningCounterData objects associated to a Filename (String)
         */
        private final ListMultimap<String,FileOpeningCounterData> counts;
        /**
         * ExecutorService for map cleanup tasks.
         */
        private final ExecutorService timer;
        /**
         * Wrapper Object to obtain/keep the objects used for synchronization
         */
        private final LockSupportMap<String> locks=new LockSupportMap<>();

        public FileOpeningCounterInternal() {
            counts=ListMultimapBuilder.hashKeys().arrayListValues().build();
            timer=Executors.newSingleThreadExecutor();
        }
        
        @Override 
        public  void add(String filename) {
            synchronized(locks.getItemLock(filename)) {
                FileOpeningCounterData data=new FileOpeningCounterData();
                List<FileOpeningCounterData> oldList=counts.get(filename);
                //List<FileOpeningCounterData> oldList=counts.putIfAbsent(filename,al);
                if (oldList!=null) {
                    oldList.add(data);
                }
//                counts.put(filename, data);
                if (DEBUG_READS) {
                    System.out.println("Read: "+filename);
                }
            }
        }
        @Override
        public Collection<? extends StacktraceGettable> get(String fileName) {
            synchronized(locks.getItemLock(fileName)) {
                if (!counts.containsKey(fileName)) {
                    return Collections.emptyList();
                }
                return counts.get(fileName);
//                return ImmutableList.copyOf(counts.get(fileName));            
            }
        }
        @Override
        public Collection<Map.Entry<? extends StacktraceGettable, Integer>> getConsolidated(String filename) {
            synchronized(locks.getItemLock(filename)) {
                if (!counts.containsKey(filename)) {
                    return Collections.emptyList();
                }
                Collection<FileOpeningCounterData> coll=counts.get(filename);
                Collection<Map.Entry<? extends StacktraceGettable, Integer>> returned=new ArrayList<>();
                Iterator<FileOpeningCounterData> iter=coll.iterator();
                Set<FileOpeningCounterData> seenItems=new HashSet<>(coll.size()+1);
                StackTraceSanitizer sanitizer=new StackTraceSanitizer(
                    Arrays.asList(
                        new String[]{"java.lang.Thread"}), 
                    Arrays.asList(
                        new String[]{"FileOpeningCounterData"}));
                while (iter.hasNext()) {
                    FileOpeningCounterData cur=iter.next();
                    if (!seenItems.contains(cur)) {
                        int numOccurs=Iterables.frequency(coll, cur);
                        FileOpeningCounterData dta=
                            new FileOpeningCounterData(sanitizer.sanitizeStackTrace(cur.getStackTrace()));
                        Map.Entry<? extends StacktraceGettable, Integer> entry=
                            new SimpleImmutableEntry<>(dta,numOccurs);
                        returned.add(entry);
                        seenItems.add(cur);
                    }
                }
                return ImmutableList.copyOf(returned);
            }
        }
 
        @Override
        public Collection<Map.Entry<String, Integer>> getKeysWithOcurrences(int minOccurs) {
            synchronized(counts) {
               Collection<Map.Entry<String,Integer>> returned=new ArrayList<>();
               Iterator<String> iter=counts.keySet().iterator();
               while (iter.hasNext()) {
                  String curKey=iter.next();
                  synchronized(locks.getItemLock(curKey)) {
                      Collection<FileOpeningCounterData> coll=counts.get(curKey);
                      if (coll.size()>=minOccurs) {
                          returned.add(new SimpleImmutableEntry<>(curKey,coll.size()));
                      }
                  }
               }
             return returned;  
            }
        }
        
        @Override
        public int getNumOccurrences(String fileName) {
            synchronized(locks.getItemLock(fileName)) {
                if (!counts.containsKey(fileName)) {
                    return 0;
                }
                return counts.get(fileName).size();
            }
        }
        
        @Override
        public Collection<? extends StacktraceGettable> removeAll(String fileName) {
            synchronized(locks.getItemLock(fileName)) {
                if (!counts.containsKey(fileName)) {
                    return Collections.emptyList();
                }
                Collection<? extends StacktraceGettable> removed=counts.removeAll(fileName);
//                Collection<? extends StacktraceGettable> removed=counts.asMap().remove(fileName);
                final String nameRemoved=fileName;
              
                // submit a cleanup runnable removing the locks to the name that was just removed
                timer.submit(new ItemLockRemover(nameRemoved,locks,counts.asMap()));

                return removed;
//                return ImmutableList.copyOf(removed); 

            }
        }

        /**
         * finalize() is needed to stop the ExecutorService/Timer 
         */
        @Override
        protected void finalize() throws Throwable {
            if (timer!=null) {
               // shutdown the timer
                timer.shutdown();
                if (!timer.awaitTermination(50000, TimeUnit.MILLISECONDS)) {
                    timer.shutdownNow();
                }
            }
            super.finalize();
        }
    }
    
    /**
     * This is a singleton, which can be obtained with getInstance
     * @return
     */
    public static FileOpeningCounter getInstance() {
        synchronized(FileOpeningCounter.class) {
            if (instance==null) {
                instance=new FileOpeningCounter();
            }
            return instance;
        }
    }

    /**
     * Private initializer called by getInstance()
     */
    private FileOpeningCounter() {
        if (!COUNT_FILE_OPENINGS) {
            internalCounter=new NullFileOpeningCounter();
        } else {
            internalCounter=new FileOpeningCounterInternal();
        }
    }
    
    /**
     * Add the filename to the map, setting its count to one, or incrementing is count if it is already present.
     * A Stacktrace will be generated and also kept in a map associated with this filename
     * @param filename
     */
    public  void add(String filename) {
       internalCounter.add(filename);
    }

    /**
     * Get the Stacktraces which are associated with the given Filename. 
     * @param fileName
     * @return
     */
    public Collection<? extends StacktraceGettable> get(String fileName) {
        return internalCounter.get(fileName);
    }
    
    /**
     * Remove all Stacktraces associated with the filename
     * @param fileName
     * @return
     */
    public Collection<? extends StacktraceGettable> removeAll(String fileName) {
        return internalCounter.removeAll(fileName);
    }
    
    /**
     * Get a consolidated collection of Stacktrace-number of occurrences pairs
     * @param filename
     * @return
     */
    public Collection<Map.Entry<? extends StacktraceGettable, Integer>> getConsolidated(String filename) {
      return internalCounter.getConsolidated(filename);
    }
    
  
    /**
     * get the number of occurrences, if there is no mapping, 0 is returned
     * @param fileName
     * @return
     */
   public int getNumOccurrences(String fileName) {
      return internalCounter.getNumOccurrences(fileName);
   }

   

    /**
     * Get a collection of Keys, with the number of times they occur
     * @param minOccurs the minimal number of occurrences.
     * @return
     */
    public Collection<Map.Entry<String, Integer>> getKeysWithOccurrences(int minOccurs) {
       return internalCounter.getKeysWithOcurrences(minOccurs);
    }
 
  
    public String toString(int minCount, String separator) {
        Collection<Map.Entry<String, Integer>> l=getKeysWithOccurrences(minCount);
        StringBuilder sb=new StringBuilder();
        Iterator<Map.Entry<String, Integer>> iter=l.iterator();
        while (iter.hasNext()) {
            Map.Entry<String, Integer> cur=iter.next();
            sb.append(cur.getKey());
            sb.append(separator);
            sb.append(cur.getValue());
            sb.append("\r\n");
        }
        return sb.toString();
    }
    /**
     * Get a string representation of all items that occur at lest minCount times
     * @param minCount
     * @return
     */
    public String toString(int minCount) {
       return toString(minCount," : ");
    }
    
    /**
     * String representation, will list all items that occur at least once
     * @return
     */
    public String toString() {
        return toString(1);
    }

    /**
     * Query whether counting file openings is enabled.
     * @return
     */
    public boolean isEnabled() {
        return COUNT_FILE_OPENINGS;
    }
    
}
