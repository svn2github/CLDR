package org.unicode.cldr.web;

public interface CLDRProgressIndicator {

    /**
     * Done with progress, reset to nothing.
     */
    public abstract void clearProgress();

    /**
     * Initialize a progress that will not show the actual count, but treat the count as a number from 0-100
     * @param what the user-visible string
     */
    public abstract void setProgress(String what);

    /**
     * Initialize a Progress. 
     * @param what what is progressing
     * @param max the max count, or <0 if it is an un-numbered percentage (0-100 without specific numbers)
     */
    public abstract void setProgress(String what, int max);

    /**
     * Update on the progress
     * @param count current count - up to Max
     */
    public abstract void updateProgress(int count);

    public abstract void updateProgress(int count, String what);
}