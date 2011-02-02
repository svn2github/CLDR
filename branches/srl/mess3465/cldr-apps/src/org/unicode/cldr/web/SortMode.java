//  Copyright 2011 IBM Corporation and Others. All rights reserved.

package org.unicode.cldr.web;

import java.util.Comparator;

import org.unicode.cldr.web.DataSection.DataRow;

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

	static String getSortMode(WebContext ctx, DataSection section) {
	    return getSortMode(ctx, section.xpathPrefix);
	}
	
	
	static SortMode getInstance(String mode) {
		if(mode.equals(CodeSortMode.name)) {
			return new CodeSortMode();
		} else if(mode.equals(CalendarSortMode.name)) {
			return new CalendarSortMode();
		} else if(mode.equals(InterestSort.name)) {
			return new InterestSort();
		} else if(mode.equals(NameSort.name)) {
			return new NameSort();
		} else {
			return new CodeSortMode();
		}
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
}
