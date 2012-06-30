package org.unicode.cldr.icu;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import com.ibm.icu.text.SimpleDateFormat;
import com.ibm.icu.util.TimeZone;

/**
 * Wrapper class for converted ICU data.
 */
class IcuData {
    private boolean hasFallback;
    private String sourceFile;
    private String name;
    private Map<String, List<String[]>> rbPathToValues;
    private boolean hasSpecial;
    private Map<String, String> enumMap;

    public IcuData(String sourceFile, String name, boolean hasFallback) {
        this(sourceFile, name, hasFallback, new HashMap<String, String>());
    }

    public IcuData(String sourceFile, String name, boolean hasFallback, Map<String, String> enumMap) {
        this.hasFallback = hasFallback;
        this.sourceFile = sourceFile;
        this.name = name;
        rbPathToValues = new HashMap<String,List<String[]>>();
        this.enumMap = enumMap;
    }

    /**
     * @return true if data should fallback on data in other files, true by default
     */
    public boolean hasFallback() {
        return hasFallback;
    }

    /**
     * Returns the the relative path of the source file used to generate the
     * ICU data. Used when writing the data to file.
     * @return 
     */
    public String getSourceFile() {
        return sourceFile;
    }

    /**
     * @return the name to be used for the data.
     */
    public String getName() {
        return name;
    }
    
    public void setHasSpecial(boolean hasSpecial) {
        this.hasSpecial = hasSpecial;
    }
    
    /**
     * @return true if special data is included in this IcuData, false by default
     */
    public boolean hasSpecial() {
        return hasSpecial;
    }
    /**
     * The RB path,value pair actually has an array as the value. So when we
     * add to it, add to a list.
     * 
     * @param path
     * @param value
     * @return
     */
    public void add(String path, String[] values) {
        List<String[]> list = rbPathToValues.get(path);
        if (list == null) {
            rbPathToValues.put(path, list = new ArrayList<String[]>(1));
        }
        list.add(normalizeValues(path, values));
    }

    /**
     * The RB path,value pair actually has an array as the value. So when we
     * add to it, add to a list.
     * 
     * @param path
     * @param value
     * @return
     */
    void add(String path, String value) {
        add(path, new String[]{value});
    }
    
    void addAll(String path, Collection<String[]> valueList) {
        for (String[] values : valueList) {
            add(path, values);
        }
    }

    private String[] normalizeValues(String rbPath, String[] values) {
        if (isIntRbPath(rbPath)) {
            List<String> normalizedValues = new ArrayList<String>();
            for (int i = 0; i < values.length; i++) {
                String curValue = values[i];
                String enumValue = enumMap.get(curValue);
                if (enumValue != null) curValue = enumValue;
                // Convert date into a number format if necessary.
                if (isDatePath(rbPath)) {
                    int[] dateValues = getSeconds(curValue);
                    normalizedValues.add(dateValues[0] + "");
                    normalizedValues.add(dateValues[1] + "");
                } else {
                    normalizedValues.add(curValue);
                }
            }
            return normalizedValues.toArray(values);
        } else {
            return values;
        }
    }

    /**
     * Naive method for checking if the given value is a date
     * (yyyy-mm-dd or yyyy-mm).
     */
    public static boolean isDatePath(String rbPath) {
        String lastNode = rbPath.substring(rbPath.lastIndexOf('/') + 1);
        return (lastNode.startsWith("from") || lastNode.startsWith("to"));
    }

    private int[] getSeconds(String dateStr) {
        long millis = getMilliSeconds(dateStr);
        if (millis == -1) {
            return null;
        }

        int top =(int)((millis & 0xFFFFFFFF00000000L)>>>32); // top
        int bottom = (int)((millis & 0x00000000FFFFFFFFL)); // bottom
        int[] result = { top, bottom };

        if (NewLdml2IcuConverter.DEBUG) {
            long bot = 0xffffffffL & bottom;
            long full = ((long)(top) << 32);
            full += bot;
            if (full != millis) {
                System.err.println("Error when converting " + millis + ": " +
                    top + ", " + bottom + " was converted back into " + full);
            }
        }

        return result;
    }

    private long getMilliSeconds(String dateStr) {
        try {
            if (dateStr != null) {
                int count = countHyphens(dateStr);
                SimpleDateFormat format = new SimpleDateFormat();
                format.setTimeZone(TimeZone.getTimeZone("GMT"));
                if (count == 2) {
                    format.applyPattern("yyyy-mm-dd");
                } else if (count == 1) {
                    format.applyPattern("yyyy-mm");
                } else {
                    format.applyPattern("yyyy");
                }
                return format.parse(dateStr).getTime();
            }
        } catch(ParseException ex) {
            System.err.println("Could not parse date: " + dateStr);
        }
        return -1;
    }

    private static int countHyphens(String str) {
        int lastPos = 0;
        int numHyphens = 0;
        while ((lastPos = str.indexOf('-', lastPos + 1))  > -1) {
            numHyphens++;
        }
        return numHyphens;
    }

    /**
     * Get items
     * 
     * @return
     */
    public Set<Entry<String, List<String[]>>> entrySet() {
        return rbPathToValues.entrySet();
    }

    /**
     * Get items
     * @return
     */
    public Set<String> keySet() {
        return rbPathToValues.keySet();
    }
    
    public int size() {
        return rbPathToValues.size();
    }

    public boolean containsKey(String key) {
        return rbPathToValues.containsKey(key);
    }
    
    public List<String[]> get(String path) {
        return rbPathToValues.get(path);
    }

    public static boolean isIntRbPath(String rbPath) {
        return rbPath.endsWith(":int") || rbPath.endsWith(":intvector");
    }

    private boolean mightNormalize(String rbPath) {
        return enumMap != null && isIntRbPath(rbPath);
    }
}