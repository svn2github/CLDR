/**
 * 
 */
package org.unicode.cldr.web;

import java.util.Set;

import org.unicode.cldr.util.VoteResolver;
import org.unicode.cldr.web.UserRegistry.User;

/**
 * @author srl
 * This is an abstract interface for allowing SurveyTool-like input to a CLDRFile.
 * It could be considered as a getter on XMLSource/CLDRFile.
 * TODO: T could eventually be nailed down to a concrete type.
 */
public interface BallotBox<T> {
	/**
	 * Record a vote for an item. Will (eventually) throw a number of exceptions.
	 * @param user voter's object
	 * @param distinguishingXpath dpath of item
	 * @param value new string value to vote for, or null for "unvote"
	 * @return the full xpath of the user's vote, or null if not applicable.
	 */
	public String voteForValue(T user, String distinguishingXpath, String value);
	
	/**
	 * Return a vote for a value, as a string
	 * @param user user id who 
	 * @param distinguishingXpath
	 * @return
	 */
	public String getVoteValue(T user, String distinguishingXpath);

	public Set<User> getVotesForValue(String xpath, String value);

	Set<String> getValues(String xpath);

	VoteResolver<String> getResolver(String path);
}
