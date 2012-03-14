//  Copyright 2011 IBM Corporation and Others. All rights reserved.

package org.unicode.cldr.web;

import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;

import org.unicode.cldr.web.DataSection.DataRow;
import org.unicode.cldr.web.Partition.Membership;

/**
 * This class represents a mode of sorting: i.e., by code, etc.
 * 
 * @author srl
 *
 */

public abstract class SortMode {

	static String getSortMode(WebContext ctx, String prefix) {
	    String sortMode = null;
	    sortMode = ctx.pref(SurveyMain.PREF_SORTMODE, SurveyMain.PREF_SORTMODE_DEFAULT);
	    return sortMode;
	}

	public static String getSortMode(WebContext ctx, DataSection section) {
	    return getSortMode(ctx, section.xpathPrefix);
	}
	
	
	public static SortMode getInstance(String mode) {
		if(mode.equals(CodeSortMode.name)) {
			return new CodeSortMode();
		} else if(mode.equals(CalendarSortMode.name)) {
			return new CalendarSortMode();
		} else if(mode.equals(MetazoneSortMode.name)) {
			return new MetazoneSortMode();
		} else if(mode.equals(InterestSort.name)) {
			return new InterestSort();
		} else if(mode.equals(NameSort.name)) {
			return new NameSort();
		} else {
			return new CodeSortMode();
		}
	}
	
	public static List<String> getSortModesFor(String xpath) {
		List<String> list = new LinkedList<String>();
		list.add(CodeSortMode.name);
		list.add(InterestSort.name);
		list.add(NameSort.name);
		if(xpath.contains("/calendars")) 
			list.add(CalendarSortMode.name);
		if(xpath.contains("zone")) 
			list.add(MetazoneSortMode.name);
		return list;
	}

	/**
	 * For subclasses
	 * @param p
	 * @param memberships
	 * @return
	 */
    protected static final int categorizeDataRow(DataRow p, Partition.Membership[] memberships) {
        int rv = -1;
        for(int i=0;(rv==-1)&&(i<memberships.length);i++) {
          if(memberships[i].isMember(p)) {
            rv = i;
          }
        }
        return rv;
      }

	/**
	 * Name of this mode.
	 * @return
	 */
	abstract String getName();
	
	/**
	 * 
	 * @return
	 */
	abstract Partition.Membership[] memberships();

	/**
	 * 
	 * @return
	 */
	abstract Comparator<DataRow> createComparator();

	public String getDisplayName(DataRow p) {
		if(p==null) {
			return "(null)";
		} else if(p.getDisplayName() != null) {
			return p.getDisplayName();
		} else {
			return p.prettyPath;
		}
	}

	enum SortKeyType { SORTKEY_INTEREST, SORTKEY_CALENDAR, SORTKEY_METAZONE };
	
	
	public static final int[] reserveForSort() {
		int[] x =  new int[SortKeyType.values().length];
		for(int i=0;i<x.length;i++) {
			x[i]=-1;
		}
		return x;
	}

	protected static int compareMembers(DataRow p1, DataRow p2,
			Membership[] memberships, int ourKey) {
		if(p1.reservedForSort[ourKey]==-1) {
			p1.reservedForSort[ourKey] = categorizeDataRow(p1, memberships);
		}
		if(p2.reservedForSort[ourKey]==-1) {
			p2.reservedForSort[ourKey] = categorizeDataRow(p2, memberships);
		}

		if(p1.reservedForSort[ourKey] < p2.reservedForSort[ourKey]) {
			return -1;
		} else if(p1.reservedForSort[ourKey] > p2.reservedForSort[ourKey]) {
			return 1;
		} else {
			return 0;
		}
	}

}
