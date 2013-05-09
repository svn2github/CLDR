/**
 * 
 */
package org.unicode.cldr.web;

import java.util.Set;

import org.unicode.cldr.util.VoteResolver;
import org.unicode.cldr.web.UserRegistry.User;

/**
 * @author srl This is an abstract interface for allowing SurveyTool-like input
 *         to a CLDRFile. It could be considered as a getter on
 *         XMLSource/CLDRFile. TODO: T could eventually be nailed down to a
 *         concrete type.
 */
public interface BallotBox<T> {
    /**
     * This is thrown when an XPath isn't valid within this locale.
     * @author srl
     *
     */
    public class InvalidXPathException extends Exception {
        /**
         * 
         */
        private static final long serialVersionUID = 1310604068301637651L;
        public String xpath;
        
        public InvalidXPathException(String xpath) {
            super("Invalid XPath: " + xpath );
            this.xpath = xpath;
        }
    }

    /**
     * Record a vote for an item. Will (eventually) throw a number of
     * exceptions.
     * 
     * @param user
     *            voter's object
     * @param distinguishingXpath
     *            dpath of item
     * @param value
     *            new string value to vote for, or null for "unvote"
     * @return the full xpath of the user's vote, or null if not applicable.
     */
    public void voteForValue(T user, String distinguishingXpath, String value) throws InvalidXPathException;

    /**
     * Return a vote for a value, as a string
     * 
     * @param user
     *            user id who
     * @param distinguishingXpath
     * @return
     */
    public String getVoteValue(T user, String distinguishingXpath);

    /**
     * Get all of the voters for this xpath which are for a certain value.
     * 
     * @param xpath
     * @param value
     * @return
     */
    public Set<User> getVotesForValue(String xpath, String value);

    /**
     * Get the possible user values at this path. Could be null.
     * 
     * @param xpath
     * @return
     */
    Set<String> getValues(String xpath);

    /**
     * Get the vote resolver for this path.
     * 
     * @param path
     * @return
     */
    VoteResolver<String> getResolver(String path);

    /**
     * Whether the user voted at all. Returns false if user voted for null (no
     * opinion).
     * 
     * @param myUser
     * @param somePath
     * @return
     */
    public boolean userDidVote(User myUser, String somePath);

    /**
     * Were there any votes some-time this release?
     * 
     * @see org.unicode.cldr.util.CLDRInfo.PathValueInfo#hadVotesSometimeThisRelease()
     * @return
     */
    public boolean hadVotesSometimeThisRelease(int xpid);

    /**
     * remove vote. same as voting for null
     * @param user
     * @param xpath
     */
    public void unvoteFor(User user, String xpath) throws InvalidXPathException;

    /**
     * re-vote for the current vote. Error if no current vote.
     * @param user
     * @param xpath
     */
    public void revoteFor(User user, String xpath) throws InvalidXPathException;
}
