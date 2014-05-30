//
//  SurveyForum.java
//
//  Created by Steven R. Loomis on 27/10/2006.
//  Copyright 2006-2013 IBM. All rights reserved.
//

package org.unicode.cldr.web;

import java.io.IOException;
import java.io.Writer;
import java.net.URLEncoder;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Set;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.unicode.cldr.util.CLDRConfig;
import org.unicode.cldr.util.CLDRFile;
import org.unicode.cldr.util.CLDRLocale;
import org.unicode.cldr.util.PathUtilities;
import org.unicode.cldr.util.VoteResolver;
import org.unicode.cldr.web.UserRegistry.LogoutException;
import org.unicode.cldr.web.UserRegistry.User;

import com.ibm.icu.dev.util.ElapsedTimer;
import com.ibm.icu.text.DateFormat;
import com.ibm.icu.util.ULocale;
import com.sun.syndication.feed.synd.SyndContent;
import com.sun.syndication.feed.synd.SyndContentImpl;
import com.sun.syndication.feed.synd.SyndEntry;
import com.sun.syndication.feed.synd.SyndEntryImpl;
import com.sun.syndication.feed.synd.SyndFeed;
import com.sun.syndication.feed.synd.SyndFeedImpl;
import com.sun.syndication.io.SyndFeedOutput;

/**
 * This class implements a discussion forum per language (ISO code)
 */
public class SurveyForum {
    private static java.util.logging.Logger logger;

    public static String DB_FORA = "sf_fora"; // forum name -> id
    public static String DB_READERS = "sf_readers"; //

    public static String DB_LOC2FORUM = "sf_loc2forum"; // locale -> forum.. for
                                                        // selects.

    /* --------- FORUM ------------- */
    static final String F_FORUM = "forum";
    public static final String F_XPATH = "xpath";
    static final String F_PATH = "path";
    static final String F_DO = "d";
    static final String F_LIST = "list";
    static final String F_VIEW = "view";
    static final String F_ADD = "add";
    static final String F_REPLY = "reply";
    static final String F_POST = "post";

    static final String POST_SPIEL = "Post a comment to other vetters. (Don't use this to report SurveyTool issues or propose data changes: use the bug forms.)";

    /**
     * prepare text for posting
     */
    static public String preparePostText(String intext) {
        return intext.replaceAll("\r", "").replaceAll("\n", "<p>");
    }

    public static String HTMLSafe(String s) {
        if (s == null)
            return null;

        return s.replaceAll("&", "&amp;").replaceAll("<", "&lt;").replaceAll(">", "&gt;").replaceAll("\"", "&quot;");
    }

    /**
     * Oops. HTML escaped into the DB
     * @param s
     * @return
     */
    public static String HTMLUnsafe(String s) {
        return s.replaceAll("<p>", "\n")
            .replaceAll("&quot;", "\"")
            .replaceAll("&gt;", ">")
            .replaceAll("&lt;", "<")
            .replaceAll("&amp;", "&");
    }

    Hashtable<Integer, String> numToName = new Hashtable<Integer, String>();
    Hashtable<String, Integer> nameToNum = new Hashtable<String, Integer>();

    static final int GOOD_FORUM = 0; // 0 or greater
    static final int BAD_FORUM = -1;
    static final int NO_FORUM = -2;

    /**
     * May return
     * @param forum
     * @return forum number, or  BAD_FORUM or NO_FORUM
     */
    synchronized int getForumNumber(String forum) {
        if (forum.length() == 0) {
            return NO_FORUM; // all forums
        }
        // make sure it is a valid src!
        if ((forum == null) || (forum.indexOf('_') >= 0) || !sm.isValidLocale(CLDRLocale.getInstance(forum))) {
            // // <explain>
            // StringBuffer why = new StringBuffer();
            // if(forum==null) why.append("(forum==null) ");
            // if(forum.indexOf('_')>=0) why.append("forum.indexOf(_)>=0) ");
            // if(!sm.isValidLocale(CLDRLocale.getInstance(forum)))
            // why.append("!valid ");
            // // </explain>
            return BAD_FORUM;
        }

        // now with that out of the way..
        Integer i = (Integer) nameToNum.get(forum);
        if (i == null) {
            return createForum(forum);
        } else {
            return i.intValue();
        }
    }

    private int getForumNumberFromDB(String forum) {
        try {
            Connection conn = null;
            PreparedStatement fGetByLoc = null;
            try {
                conn = sm.dbUtils.getDBConnection();
                fGetByLoc = prepare_fGetByLoc(conn);
                fGetByLoc.setString(1, forum);
                ResultSet rs = fGetByLoc.executeQuery();
                if (!rs.next()) {
                    rs.close();
                    return BAD_FORUM;
                } else {
                    int j = rs.getInt(1);
                    rs.close();
                    return j;
                }
            } finally {
                DBUtils.close(fGetByLoc, conn);
            }
        } catch (SQLException se) {
            String complaint = "SurveyForum:  Couldn't add forum " + forum + " - " + DBUtils.unchainSqlException(se)
                + " - fGetByLoc";
            logger.severe(complaint);
            throw new RuntimeException(complaint);
        }
    }

    private int createForum(String forum) {
        int num = getForumNumberFromDB(forum);
        if (num == BAD_FORUM) {
            try {
                Connection conn = null;
                PreparedStatement fAdd = null;
                try {
                    conn = sm.dbUtils.getDBConnection();
                    fAdd = prepare_fAdd(conn);
                    fAdd.setString(1, forum);
                    fAdd.executeUpdate();
                    conn.commit();
                } finally {
                    DBUtils.close(fAdd, conn);
                }
            } catch (SQLException se) {
                String complaint = "SurveyForum:  Couldn't add forum " + forum + " - " + DBUtils.unchainSqlException(se)
                    + " - fAdd";
                logger.severe(complaint);
                throw new RuntimeException(complaint);
            }
            num = getForumNumberFromDB(forum);
        }

        if (num == BAD_FORUM) {
            throw new RuntimeException("Couldn't query ID for forum " + forum);
        }
        // Add to list
        Integer i = new Integer(num);
        nameToNum.put(forum, i);
        numToName.put(i, forum);
        return num;
    }

    public int gatherInterestedUsers(String forum, Set<Integer> cc_emails, Set<Integer> bcc_emails) {
        int emailCount = 0;
        try {
            Connection conn = null;
            PreparedStatement pIntUsers = null;
            try {
                conn = sm.dbUtils.getDBConnection();
                pIntUsers = prepare_pIntUsers(conn);
                pIntUsers.setString(1, forum);

                ResultSet rs = pIntUsers.executeQuery();

                while (rs.next()) {
                    int uid = rs.getInt(1);

                    UserRegistry.User u = sm.reg.getInfo(uid);
                    if (u != null && u.email != null && u.email.length() > 0 && !UserRegistry.userIsLocked(u)) {
                        if (UserRegistry.userIsVetter(u)) {
                            cc_emails.add(u.id);
                        } else {
                            bcc_emails.add(u.id);
                        }
                        emailCount++;
                    }
                }
            } finally {
                DBUtils.close(pIntUsers, conn);
            }
        } catch (SQLException se) {
            String complaint = "SurveyForum:  Couldn't gather interested users for " + forum + " - "
                + DBUtils.unchainSqlException(se) + " - pIntUsers";
            logger.severe(complaint);
            throw new RuntimeException(complaint);
        }

        return emailCount;
    }

    void doForum(WebContext ctx, String sessionMessage) throws IOException {
        /* OK, let's see what we are doing here. */
        String forum = ctx.field(F_FORUM);
        String msg = null;
        int base_xpath = ctx.fieldInt(F_XPATH);
        String xstr = ctx.field(F_XPATH);
        if (xstr.startsWith("//ldml")) {
            base_xpath = sm.xpt.peekByXpath(xstr);
            if (base_xpath == -1) {
                msg = "XPath lookup failed.";
            }
        }
        if (ctx.hasField(F_XPATH) && !ctx.hasField(F_FORUM)) {
            forum = localeToForum(ctx.field("_"));
        }
        boolean loggedout = ((ctx.session == null) || (ctx.session.user == null));
        // User isnt logged in.
        if (loggedout) {
            sm.printHeader(ctx, "Forum | Please login.");
            sm.printUserTable(ctx);
            if (sessionMessage != null) {
                ctx.println(sessionMessage + "<hr>");
            }
            ctx.println("<hr><strong>You aren't logged in. Please login to continue.</strong><p>");
            sm.printFooter(ctx);
            return;
        }
        int forumNumber = getForumNumber(forum);
        if (forumNumber < GOOD_FORUM) {
            sm.printHeader(ctx, "Forum | bad forum.");
            sm.printUserTable(ctx);
            if (sessionMessage != null) {
                ctx.println(sessionMessage + "<hr>");
            }
            ctx.println("<hr><strong>Forum '" + forum + "' is not valid.</strong><p>");
            sm.printFooter(ctx);
            return;
        }
        String pD = ctx.field(F_DO); // do
        boolean canModify = false;

        if ((ctx.getLocale() == null) && (forumNumber >= GOOD_FORUM)) {
            ctx.setLocale(CLDRLocale.getInstance(forum));
        }

        if (!loggedout && (forumNumber >= GOOD_FORUM)) {
            canModify = (UserRegistry.userCanAccessForum(ctx.session.user, ctx.getLocale()));
        }

        /* can we accept a string xpath? (ignore if 'forum' is set) */
        if (!ctx.hasField(F_FORUM) && base_xpath == -1 && ctx.hasField(F_XPATH) && ctx.field(F_XPATH).length() > 0) {
            if (base_xpath == -1) {
                String str = ctx.jspUrl("xpath.jsp") + "&_=" + URLEncoder.encode(ctx.getLocale().toString()) + "&xpath="
                    + URLEncoder.encode(xstr) + "&msg=" + URLEncoder.encode(msg);
                ctx.redirect(str);
                return;
            }
            // System.err.println("XP ["+xstr+"] -> " + base_xpath);
            // TODO: may need fixup here.
        }

        // fixup base_xpath - might have alt on it.
        if (base_xpath != -1) {
            String zoom_xpath = sm.xpt.getById(base_xpath);
            String noalt_base = XPathTable.removeAlt(zoom_xpath);
            int noalt_base_xpath = sm.xpt.getByXpath(noalt_base);

            base_xpath = noalt_base_xpath;
        }

        // Are they just zooming in?
        if (!canModify && (base_xpath != -1)) {
            doZoom(ctx, base_xpath, sessionMessage);
            return;
        }

        // User has an account, but does not have access to this forum.
        if (!canModify && !(forumNumber == NO_FORUM && UserRegistry.userIsVetter(ctx.session.user))) {
            sm.printHeader(ctx, "Forum | Access Denied.");
            sm.printUserTable(ctx);
            if (sessionMessage != null) {
                ctx.println(sessionMessage + "<hr>");
            }
            ctx.println("<hr><strong>You do not have access to this forum. <!-- canModify = "
                + canModify
                + " -->  If you believe this to be in error, contact your CLDR TC member, and/or the person who set up your account.</strong><p>");
            sm.printFooter(ctx);
            return;
        }

        // User is logged in and has access.

        if ((base_xpath != -1) || (ctx.hasField("replyto"))) {
            // Post to a specific xpath
            doXpathPost(ctx, forum, forumNumber, base_xpath);
        } else if (F_VIEW.equals(pD) && ctx.hasField("id")) {
            doForumView(ctx, forum, forumNumber);
        } else if (forumNumber == BAD_FORUM) {
            sm.printHeader(ctx, "Forum");
            sm.printUserTable(ctx);
            // no forum or bad forum. Do general stuff.
            // doForumForum(ctx, pF, pD);
        } else {
            // list what is in a certain forum
            doForumForum(ctx, forum, forumNumber);
        }

        if (sessionMessage != null) {
            ctx.println("<hr>" + sessionMessage);
        }
        if(!ctx.field("isReview").equals("1"))
            sm.printFooter(ctx);
    }

    String returnUrl(WebContext ctx, CLDRLocale locale, int base_xpath) {
        String xpath = sm.xpt.getById(base_xpath);
        if (xpath == null)
            return ctx.base() + "?" + "_=" + locale;
        String theMenu = PathUtilities.xpathToMenu(xpath);
        if (theMenu == null) {
            theMenu = "raw";
        }
        return ctx.base() + "?" + "_=" + locale + "&amp;x=" + theMenu + "&amp;" + SurveyMain.QUERY_XFIND + "=" + base_xpath
            + "#x" + base_xpath;
    }

    void doZoom(WebContext ctx, int base_xpath, String sessionMessage) {
        String xpath = sm.xpt.getById(base_xpath);
        if ((xpath == null) && (base_xpath != -1)) {
            sm.printHeader(ctx, "Missing Item");
            sm.printUserTable(ctx);
            ctx.println("<div class='ferrbox'>Sorry, the item you were attempting to view does not exist.  Note that zoomed-in URLs are not permanent.</div>");
            sm.printFooter(ctx);
            return;
        }
        String prettyPath = sm.xpt.getPrettyPath(base_xpath);
        if (prettyPath != null) {
            sm.printHeader(ctx, prettyPath.replaceAll("\\|", " | ")); // TODO:
                                                                      // pretty
                                                                      // path?
        } else {
            sm.printHeader(ctx, "Forum");
        }
        sm.printUserTable(ctx);
        printMiniMenu(ctx, null, prettyPath);
        if (sessionMessage != null) {
            ctx.println(sessionMessage + "<hr>");
        }
        // boolean nopopups = ctx.prefBool(SurveyMain.PREF_NOPOPUPS);
        String returnText = returnText(ctx, base_xpath);
        // if(nopopups) {
        ctx.println(returnText + "<hr>");
        // }
        showXpath(ctx, xpath, base_xpath, ctx.getLocale());
        // if(nopopups) {
        ctx.println("<hr>" + returnText + "<br/>");
        // }
        sm.printFooter(ctx);
    }

    /**
     * Called when user has permission to modify and is zoomed in.
     * 
     * @param ctx
     * @param forum
     * @param forumNumber
     * @param base_xpath
     * @throws IOException
     */
    void doXpathPost(WebContext ctx, String forum, int forumNumber, int base_xpath) throws IOException {
        String fieldStr = ctx.field("replyto", null);
        int replyTo = ctx.fieldInt("replyto", -1);
        boolean isNewPost = false;
        if (forum.isEmpty()) {
            forum = localeToForum(ctx.getLocale().toULocale());
            forumNumber = getForumNumber(forum);
        }
        if (replyTo == -1 && fieldStr.startsWith("x")) {
            base_xpath = Integer.parseInt(fieldStr.substring(1));
            isNewPost = true;
        } else {
            // is this a reply? get base xpath from parent item.
            base_xpath = DBUtils.sqlCount("select xpath from " + DBUtils.Table.FORUM_POSTS + " where id=?", replyTo); // default to -1
        }
        final CLDRLocale locale = ctx.getLocale();
        // if(postToXpath!=-1) base_xpath = postToXpath;
        String xpath = sm.xpt.getById(base_xpath);

        // Don't want to call printHeader here - becasue if we do a post, we're
        // going ot be
        // redirecting outta here.
        String subj = HTMLSafe(ctx.field("subj"));
        String text = HTMLSafe(ctx.field("text"));
        boolean defaultSubj = false;
        if ((subj == null) || (subj.trim().length() == 0)) {
            defaultSubj = true;
            if (xpath != null) {
                // I could really use '#if 0' right here
                subj = sm.getSTFactory().getPathHeader(xpath).toString();
                // subj = sm.xpt.getPrettyPath(xpath);
                /*
                 * int n = xpath.lastIndexOf("/"); if(n!=-1) { subj =
                 * xpath.substring(n+1,xpath.length()); }
                 */

            }
            if (subj == null) {
                subj = "item";
            }

            subj = ctx.getLocale() + ": " + subj;
        } else {
            subj = subj.trim();
        }

        if (text != null && text.length() > 0) {
            if (ctx.field("post").length() > 0) {
                // do the post!

                if (forumNumber == BAD_FORUM) {
                    throw new RuntimeException("Bad forum: " + forum);
                }

                Integer postId;
                final boolean couldFlagOnLosing = couldFlagOnLosing(ctx, xpath, ctx.getLocale()) && !sm.getSTFactory().getFlag(locale, base_xpath);

                if (couldFlagOnLosing) {
                    text = text + " <p>[This item was flagged for CLDR TC review.]";
                }
                final UserRegistry.User user = ctx.session.user;

                postId = doPostInternal(forum, forumNumber, base_xpath, replyTo, locale, subj, text, couldFlagOnLosing, user);

                // Apparently, it posted.

                emailNotify(ctx, forum, base_xpath, subj, text, postId);

                if(ctx.field("isReview").equals("1")) {
                    ctx.response.resetBuffer();
                    try {
                        JSONArray post = this.toJSON(ctx.session, locale, base_xpath, postId);
                        ctx.println(post.get(0).toString());
                    } catch (JSONException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                }
                else
                    ctx.redirect(ctx.base() + "?_=" + ctx.getLocale().toString() + "&" + F_FORUM + "=" + forum + "#post" + postId);
                return;

            } else {
                sm.printHeader(ctx, "Forum | " + forum + " | Preview post on #" + base_xpath);
                printForumMenu(ctx, forum);
            }

            ctx.println("<div class='odashbox'><h3>Preview</h3>");
            ctx.println("<b>Subject</b>: " + subj + "<br>");
            ctx.println("<b>Xpath</b>: <tt>" + xpath + "</tt><br>");
            ctx.println("<br><br> <div class='response'>" + (text == null ? ("<i>none</i>") : preparePostText(text))
                + "</div><p>");
            ctx.println("</div><hr>");
        } else {
            String prettyPath = null;
            if (xpath != null) {
                prettyPath = sm.getSTFactory().getPathHeader(xpath).toString();
            }
            if (prettyPath != null) {
                sm.printHeader(ctx, prettyPath.replaceAll("\\|", " | "));
            } else {
                sm.printHeader(ctx, forum + " | Forum");
            }

            sm.printUserTable(ctx);
            printMiniMenu(ctx, forum, prettyPath);
        }

        if (isNewPost) {
            ctx.println("<h2><a href='" + forumItemUrl(ctx, ctx.getLocale(), base_xpath) + "'>Return to original item</a></h2>");
        }

        if (replyTo != -1) {
            ctx.println("<h4>In Reply To:</h4><span class='reply-to'>");
            String subj2 = showItem(ctx, forum, forumNumber, replyTo, false);
            ctx.println("</span>");
            if (defaultSubj) {
                subj = subj2;
                if (!subj.startsWith("Re: ")) {
                    subj = "Re: " + subj;
                }
            }
        }

        if ((ctx.field("text").length() == 0) && (ctx.field("subj").length() == 0) && base_xpath != -1 && !isNewPost
            && (replyTo == -1)) {
            // hide the 'post comment' thing
            String warnHash = "post_comment" + base_xpath + "_" + forum;
            ctx.println("<div class='postcomment' id='h_" + warnHash + "'><a href='javascript:show(\"" + warnHash + "\")'>"
                + "<b>+</b> " + POST_SPIEL + "</a></div>");
            ctx.println("<!-- <noscript>Warning: </noscript> -->" + "<div style='display: none' class='pager' id='" + warnHash
                + "'>");
            ctx.println("<a href='javascript:hide(\"" + warnHash + "\")'>" + "(<b>-</b> Don't post comment)</a>");
        }

        ctx.print("<a name='replyto'></a>");
        ctx.println("<form method='POST' action='" + ctx.base() + "'>");
        ctx.println("<input type='hidden' name='" + F_FORUM + "' value='" + forum + "'>");
        if (!isNewPost) {
            ctx.println("<input type='hidden' name='" + F_XPATH + "' value='" + base_xpath + "'>");
        }
        ctx.println("<input type='hidden' name='_' value='" + ctx.getLocale() + "'>");
        if (isNewPost) {
            ctx.println("<input type='hidden' name='replyto' value='x" + base_xpath + "'>");
        } else {
            ctx.println("<input type='hidden' name='replyto' value='" + replyTo + "'>");
        }

        if (SurveyMain.isPhaseBeta()) {
            ctx.println("<div class='ferrbox'>Please remember that the Survey Tool is in Beta, therefore your post will be deleted when the beta period closes.</div>");
        }

        ctx.println("<b>Subject</b>: <input name='subj' size=40 value='" + subj + "'><br>");
        ctx.println("<textarea rows=12 cols=60 name='text'>" + (text == null ? "" : text) + "</textarea>");
        ctx.println("<br>");
        ctx.println("<input name=post " +
            // (ctx.hasField("text")?"":"disabled")+ // require preview
            " type=submit value=Post>");
        ctx.println("<input type=submit name=preview value=Preview><br>");
        if (SurveyMain.isPhaseBeta()) {
            ctx.println("<div class='ferrbox'>Please remember that the Survey Tool is in Beta, therefore your post will be deleted when the beta period closes.</div>");
        }
        ctx.println("</form>");

        if (ctx.field("post").length() > 0) {
            if (text == null || text.length() == 0) {
                ctx.println("<b>Please type some text.</b><br>");
            } else {
                // ...
            }
        }

        if ((ctx.field("text").length() == 0) && (ctx.field("subj").length() == 0) && base_xpath != -1) {
            ctx.println("</div>");
        }

        if (base_xpath != -1) {

            try {
                Connection conn = null;
                try {
                    conn = sm.dbUtils.getDBConnection();

                    Object[][] o = DBUtils.sqlQueryArrayArrayObj(conn, "select " + getPallresultfora() + "  FROM " + DBUtils.Table.FORUM_POSTS.toString()
                        + " WHERE (" + DBUtils.Table.FORUM_POSTS + ".forum =? AND " + DBUtils.Table.FORUM_POSTS + " .xpath =?) ORDER BY "
                        + DBUtils.Table.FORUM_POSTS.toString()
                        + ".last_time DESC", forumNumber, base_xpath);

                    // private final static String pAllResult =
                    // DB_POSTS+".poster,"+DB_POSTS+".subj,"+DB_POSTS+".text,"+DB_POSTS+".last_time,"+DB_POSTS+".id,"+DB_POSTS+".forum,"+DB_FORA+".loc";
                    if (o != null) {
                        for (int i = 0; i < o.length; i++) {
                            int poster = (Integer) o[i][0];
                            String subj2 = (String) o[i][1];
                            String text2 = (String) o[i][2];
                            Timestamp lastDate = (Timestamp) o[i][3];
                            int id = (Integer) o[i][4];

                            if (lastDate.after(oldOnOrBefore) || false) {
                                showPost(ctx, forum, poster, subj2, text2, id, lastDate, ctx.getLocale(), base_xpath);
                            }
                        }
                    }
                    // System.err.println("Got: " + Arrays.toString(o) +
                    // " for fn " + forumNumber + " and " + base_xpath);
                } finally {
                    DBUtils.close(conn);
                }
            } catch (SQLException se) {
                String complaint = "SurveyForum:  Couldn't show posts in forum " + forum + " - "
                    + DBUtils.unchainSqlException(se) + " - fGetByLoc";
                logger.severe(complaint);
                // ctx.println("<br>"+complaint+"</br>");
                throw new RuntimeException(complaint);
            }

            boolean nopopups = ctx.prefBool(SurveyMain.PREF_NOPOPUPS);
            String returnText = returnText(ctx, base_xpath);
            if (nopopups) {
                ctx.println(returnText + "<hr>");
            }

            if (isNewPost || (replyTo != -1)) {
                showXpath(ctx, xpath, base_xpath, ctx.getLocale());
            }
            if (nopopups) {
                ctx.println("<hr>" + returnText + "<br/>");
            }
        }
    }

    /**
     * @param ctx
     * @param forum
     * @param base_xpath
     * @param subj
     * @param text
     * @param postId
     */
    public void emailNotify(WebContext ctx, String forum, int base_xpath, String subj, String text, Integer postId) {
        ElapsedTimer et = new ElapsedTimer("Sending email to " + forum);
        // Do email-
        Set<Integer> cc_emails = new HashSet<Integer>();
        Set<Integer> bcc_emails = new HashSet<Integer>();
        
        // Collect list of users to send to.
        gatherInterestedUsers(forum, cc_emails, bcc_emails);

        String subject = "CLDR forum post (" + CLDRLocale.getInstance(forum).getDisplayName() + " - " + forum + "): " + subj;

        String body = "Do not reply to this message, instead go to http://st.unicode.org"
            + forumUrl(ctx, forum)
            + "#post" + postId + "\n"
            + "====\n\n"
            + text;
        
        if(MailSender.getInstance().DEBUG){
            System.out.println(et+": Forum notify: u#"+ctx.userId()+" x"+base_xpath+" queueing cc:" + cc_emails.size() + " and bcc:"+bcc_emails.size());
        }

        MailSender.getInstance().queue(ctx.userId(), cc_emails, bcc_emails, HTMLUnsafe(subject), HTMLUnsafe(body), ctx.getLocale(), base_xpath, postId);
    }

    /**
     * @param forum
     * @param forumNumber
     * @param base_xpath
     * @param replyTo
     * @param locale
     * @param subj
     * @param text
     * @param postId
     * @param couldFlagOnLosing
     * @param user
     * @return
     */
    public Integer doPostInternal(String forum, int forumNumber, int base_xpath, int replyTo, final CLDRLocale locale, String subj, String text,
        final boolean couldFlagOnLosing, final UserRegistry.User user) {
        int postId;
        try {
            Connection conn = null;
            PreparedStatement pAdd = null;
            try {
                conn = sm.dbUtils.getDBConnection();
                pAdd = prepare_pAdd(conn);

                pAdd.setInt(1, user.id);
                DBUtils.setStringUTF8(pAdd, 2, subj);
                DBUtils.setStringUTF8(pAdd, 3, preparePostText(text));
                pAdd.setInt(4, forumNumber);
                pAdd.setInt(5, replyTo); // record parent
                pAdd.setString(6, locale.toString()); // real
                                                      // locale
                                                      // of
                                                      // item,
                                                      // not
                                                      // furm #
                pAdd.setInt(7, base_xpath);

                int n = pAdd.executeUpdate();
                if (couldFlagOnLosing) {
                    sm.getSTFactory().setFlag(conn, locale, base_xpath, user);
                    System.out.println("NOTE: flag was set on " + locale + " " + base_xpath + " by " + user.toString());
                }
                conn.commit();
                postId = DBUtils.getLastId(pAdd);

                if (n != 1) {
                    throw new RuntimeException("Couldn't post to " + forum + " - update failed.");
                }
            } finally {
                DBUtils.close(pAdd, conn);
            }
        } catch (SQLException se) {
            String complaint = "SurveyForum:  Couldn't add post to " + forum + " - " + DBUtils.unchainSqlException(se)
                + " - pAdd";
            logger.severe(complaint);
            throw new RuntimeException(complaint);
        }
        return postId;
    }

    /**
     * @param ctx
     * @param forum
     */
    public void printMiniMenu(WebContext ctx, String forum, String prettyPath) {
        ctx.println("<div class='minimenu'>");
        ctx.println("<a class='notselected' href=\"" + ctx.url() + "\">" + "<b>Locales</b>" + "</a> &gt; ");
        ctx.println("<a class='selected' >" + ctx.getLocaleDisplayName() + "</a>");
        if (forum != null) {
            ctx.println("&gt; " + forumLink(ctx, forum));
        }
        ctx.println("</div>");
        if (prettyPath != null) {
            ctx.println("<h2 class='thisItem'>" + prettyPath + "</h2>");
        }
    }

    public static String showXpath(WebContext baseCtx, String section_xpath, int item_xpath) {
        //String base_xpath = section_xpath;
        CLDRLocale loc = baseCtx.getLocale();
        WebContext ctx = new WebContext(baseCtx);
        ctx.setLocale(loc);
        boolean canModify = (UserRegistry.userCanModifyLocale(ctx.session.user, ctx.getLocale()));
        String podBase = DataSection.xpathToSectionBase(section_xpath);
        baseCtx.sm.printPathListOpen(ctx);
        if (canModify) {
            /* hidden fields for that */
            // ctx.println("<input type='hidden' name='"+F_FORUM+"' value='"+ctx.locale.getLanguage()+"'>");
            // ctx.println("<input type='hidden' name='"+F_XPATH+"' value='"+base_xpath+"'>");
            // ctx.println("<input type='hidden' name='_' value='"+loc+"'>");
            //
            // ctx.println("<input type='submit' value='" +
            // ctx.sm.getSaveButtonText() + "'><br>"); //style='float:right'
            // ctx.sm.vet.processPodChanges(ctx, podBase);
        } else {
            // ctx.println("<br>cant modify " + ctx.locale + "<br>");
        }

        DataSection section = ctx.getSection(podBase);

        DataSection.printSectionTableOpen(ctx, section, true, canModify);
        section.showSection(ctx, canModify, ctx.sm.xpt.getById(item_xpath), true);
        baseCtx.sm.printSectionTableClose(ctx, section, canModify);
        baseCtx.sm.printPathListClose(ctx);

        // ctx.printHelpHtml(section, item_xpath);
        return podBase;
    }

    /**
     * 
     * @param ctx
     *            the current web context
     * @param baseXpath
     *            the xpath of one of the items being submitted
     * @return true if no errors were detected, otherwise false.
     * @deprecated
     */
    public static SummarizingSubmissionResultHandler processDataSubmission(WebContext ctx, String baseXpath) {
        return processDataSubmission(ctx, baseXpath, null);
    }

    /**
     * 
     * @param ctx
     *            the current web context
     * @param baseXpath
     *            the xpath of one of the items being submitted
     * @param ssrh
     *            ResultHandler, if null one will be created.
     * @return true if no errors were detected, otherwise false.
     * @deprecated
     */
    public static SummarizingSubmissionResultHandler processDataSubmission(WebContext ctx, String baseXpath,
        SummarizingSubmissionResultHandler ssrh) {
        if (ssrh == null) {
            ssrh = new SummarizingSubmissionResultHandler();
        }
        return ssrh;
    }

    public static void printSectionTableOpenShort(WebContext ctx, String base_xpath) {
        DataSection section = null;
        if (base_xpath != null) {
            String podBase = DataSection.xpathToSectionBase(base_xpath);
            section = ctx.getSection(podBase);
        }
        SurveyMain.printSectionTableOpenShort(ctx, section);
    }

    public static void printSectionTableCloseShort(WebContext ctx, String base_xpath) {
        DataSection section = null;
        if (base_xpath != null) {
            String podBase = DataSection.xpathToSectionBase(base_xpath);
            section = ctx.getSection(podBase);
        }
        ctx.sm.printSectionTableClose(ctx, section, true);
    }

    /**
     * @param baseCtx
     * @param section_xpath
     * @param item_xpath
     * @return pod base used
     */
    public static String showXpathShort(WebContext baseCtx, String section_xpath, int item_xpath) {
        String base_xpath = section_xpath;
        CLDRLocale loc = baseCtx.getLocale();
        WebContext ctx = new WebContext(baseCtx);
        ctx.setLocale(loc);
        boolean canModify = (UserRegistry.userCanModifyLocale(ctx.session.user, ctx.getLocale()));

        ctx.put(WebContext.CAN_MODIFY, canModify);
        ctx.put(WebContext.ZOOMED_IN, true);
        String podBase = DataSection.xpathToSectionBase(base_xpath);
        DataSection section = ctx.getSection(podBase);
        section.showPeasShort(ctx, item_xpath);
        return podBase;
    }

    public static String showXpathShort(WebContext baseCtx, String section_xpath, String item_xpath) {
        return showXpathShort(baseCtx, section_xpath, baseCtx.sm.xpt.getByXpath(item_xpath));
    }

    public static String showXpathShort(WebContext baseCtx, String item_xpath) {
        String section_xpath = DataSection.xpathToSectionBase(item_xpath);
        return showXpathShort(baseCtx, section_xpath, baseCtx.sm.xpt.getByXpath(item_xpath));
    }

    public static String showXpath(WebContext baseCtx, String section_xpath, String item_xpath) {
        return showXpath(baseCtx, section_xpath, baseCtx.sm.xpt.getByXpath(item_xpath));
    }

    /**
     * Get resolved CLDR File
     */
    public static CLDRFile getResolvedFile(WebContext ctx) {
        return ctx.getUserFile().resolvedFile;
        // return ctx.sm.getCLDRFile(ctx.session, ctx.getLocale());
    }

    /**
     * Get CLDR File
     */
    public static CLDRFile getCLDRFile(WebContext ctx) {
        return ctx.getUserFile().cldrfile;
        // return ctx.sm.getCLDRFile(ctx.session, ctx.getLocale());
    }

    public static void showSubmitButton(WebContext baseCtx) {
        CLDRLocale loc = baseCtx.getLocale();
        WebContext ctx = new WebContext(baseCtx);
        ctx.setLocale(loc);
        boolean canModify = (UserRegistry.userCanModifyLocale(ctx.session.user, ctx.getLocale()));
        if (false && canModify) {
            /* hidden fields for that */
            // ctx.println("<input type='hidden' name='"+F_FORUM+"' value='"+ctx.locale.getLanguage()+"'>");
            // ctx.println("<input type='hidden' name='"+F_XPATH+"' value='"+base_xpath+"'>");
            // ctx.println("<input type='hidden' name='_' value='"+loc+"'>");

            // ctx.println("<input type='submit' value='" +
            // ctx.sm.getSaveButtonText() + "'>"); //style='float:right'

        }
    }

    public void showXpath(WebContext baseCtx, String xpath, int base_xpath, CLDRLocale locale) {
        WebContext ctx = new WebContext(baseCtx);
        ctx.setLocale(locale);
        boolean isFlagged = sm.getSTFactory().getFlag(locale, base_xpath);
        if (isFlagged) {
            ctx.print(ctx.iconHtml("flag", "flag icon"));
            ctx.println("<i>This item is already flagged for CLDR technical committee review.</i>");
            ctx.println("<br>");
        } else {
            boolean couldFlagOnLosing = couldFlagOnLosing(baseCtx, xpath, locale);
            if (couldFlagOnLosing) {
                ctx.print(ctx.iconHtml("flag", "flag icon"));
                ctx.println("<i>Posting this item will flag it for CLDR technical committee review.</i>");
                ctx.println("<br>");
            }
        }
        ctx.println("<i>Note: item cannot be shown here. Click \"View Item\" once the item is posted.</i>");

        //        ctx.println("       <div id='DynamicDataSection'><noscript>" + ctx.iconHtml("stop", "sorry")
        //                + "JavaScript is required.</noscript></div>      " + " <script type='text/javascript'>   "
        //                + "surveyCurrentLocale='" + locale + "';\n" + "showRows BROKEN('DynamicDataSection', '" + xpath + "', '"
        //                + ctx.session.id + "','" + "optional" /*
        //                                                       * ctx.
        //                                                       * getEffectiveCoverageLevel
        //                                                       * (ctx.getLocale())
        //                                                       */+ "');       </script>");
        // Show the Pod in question:
        // ctx.println("<hr> \n This post Concerns:<p>");
        // boolean canModify =ctx.canModify();
        //
        // //String podBase = DataSection.xpathToSectionBase(xpath);
        // String podBase = XPathTable.xpathToBaseXpath(xpath); // each zoom-in
        // in its own spot.
        //
        // sm.printPathListOpen(ctx);
        //
        // if(canModify) {
        // /* hidden fields for that */
        // ctx.println("<input type='hidden' name='"+F_FORUM+"' value='"+ctx.getLocale().getLanguage()+"'>");
        // ctx.println("<input type='hidden' name='"+F_XPATH+"' value='"+base_xpath+"'>");
        // ctx.println("<input type='hidden' name='_' value='"+locale+"'>");
        //
        // // if(false) ctx.println("<input type='submit' value='" +
        // sm.getSaveButtonText() + "'><br>"); //style='float:right'
        // // sm.processChanges(ctx, null, null, podBase, canModify, new
        // DefaultDataSubmissionResultHandler(ctx));
        // } else {
        // ctx.println("<!-- <br>cant modify " + locale + "<br> -->");
        // }
        //
        // DataSection section =
        // ctx.getSection(podBase,Level.COMPREHENSIVE.toString(),LoadingShow.showLoading);
        // // always use comprehensive - so no cov filtering
        //
        // section.showSection(ctx, canModify,
        // BaseAndPrefixMatcher.getInstance(base_xpath,null), true);
        // sm.printPathListClose(ctx);
        //
        // ctx.printHelpHtml(xpath);
    }

    /**
     * @param baseCtx
     * @param xpath
     * @param locale
     * @param couldFlagOnLosing
     * @return
     */
    public boolean couldFlagOnLosing(WebContext baseCtx, String xpath, CLDRLocale locale) {
        if (sm.supplementalDataInfo.getRequiredVotes(locale, sm.getSTFactory().getPathHeader(xpath)) == VoteResolver.HIGH_BAR) {
            BallotBox<User> bb = sm.getSTFactory().ballotBoxForLocale(locale);
            if (bb.userDidVote(baseCtx.session.user, xpath)) {
                VoteResolver<String> vr = bb.getResolver(xpath);
                String winningValue = vr.getWinningValue();
                String userValue = bb.getVoteValue(baseCtx.session.user, xpath);
                if (userValue != null && !userValue.equals(winningValue)) {
                    return true;
                }
            }
        }
        return false;
    }

    void printForumMenu(WebContext ctx, String forum) {
        ctx.println("<table class='forumMenu' id='forumMenu' width='100%' border='0'><tr><td>");
        ctx.println("<a href=\"" + ctx.url() + "\">" + "<b>Locales</b>" + "</a><br>");
        sm.printListFromInterestGroup(ctx, forum);
        ctx.println("</td><td align='right'>");
        sm.printUserTable(ctx);
        ctx.println("</td></tr></table>");
    }

    /*
     * Show the latest posts in a forum. called by doForum()
     */
    static final int MSGS_PER_PAGE = 9925;

    private void doForumForum(WebContext ctx, String forum, int forumNumber) {
        //        boolean didpost = ctx.hasField("didpost");
        int skip = ctx.fieldInt("skip", 0);
        int count = 0;

        // print header
        sm.printHeader(ctx, "Forum  " + forum);
        printForumMenu(ctx, forum);

        ctx.print(forumFeedIcon(ctx, forum));

        ctx.println("<hr>");
        //        if (didpost) {
        //            ctx.println("<b>Posted your response. Click \"View Item\" to return to a particular forum post.</b><hr>");
        //        }

        ctx.println("<a href='" + forumUrl(ctx, forum) + "&amp;replyto='><b>+</b> " + POST_SPIEL + "</a><br>");

        int hidCount = 0;
        boolean showOld = ctx.prefBool("SHOW_OLD_MSGS");

        try {
            Connection conn = null;
            PreparedStatement pList = null;
            try {
                conn = sm.dbUtils.getDBConnection();
                pList = prepare_pList(conn);

                pList.setInt(1, forumNumber);
                ResultSet rs = pList.executeQuery();

                while (count < MSGS_PER_PAGE && rs.next()) {
                    if (count == 0) {
                        // HEADER
                    }

                    int poster = rs.getInt(1);
                    String subj = DBUtils.getStringUTF8(rs, 2);
                    String text = DBUtils.getStringUTF8(rs, 3);
                    int id = rs.getInt(4);
                    java.sql.Timestamp lastDate = rs.getTimestamp(5);
                    String loc = rs.getString(6);
                    int xpath = rs.getInt(7);

                    if (lastDate.before(oldOnOrBefore) && !showOld) {
                        hidCount++;
                    } else {
                        showPost(ctx, forum, poster, subj, text, id, lastDate, CLDRLocale.getInstance(loc), xpath);
                    }

                    count++;
                }
            } finally {
                DBUtils.close(pList, conn);
            }
        } catch (SQLException se) {
            String complaint = "SurveyForum:  Couldn't add forum " + forum + " - " + DBUtils.unchainSqlException(se)
                + " - fGetByLoc";
            logger.severe(complaint);
            throw new RuntimeException(complaint);
        }

        ctx.println("<a href='" + forumUrl(ctx, forum) + "&amp;replyto='><b>+</b> " + POST_SPIEL + "..</a><br>");
        ctx.println("<hr>" + count + " posts <br>");
        String nold = "";
        if (hidCount > 0) {
            nold = hidCount + " ";
        }
        WebContext subCtx = new WebContext(ctx);
        subCtx.setQuery("skip", skip);
        // didpost not important
        subCtx.setQuery("_", ctx.field("_"));
        subCtx.setQuery("forum", ctx.field("forum"));

        sm.showTogglePref(subCtx, "SHOW_OLD_MSGS", "Show " + nold + "old messages?");
    }

    private void doForumView(WebContext ctx, String forum, int forumNumber) {

        int id = ctx.fieldInt("id", -1);
        if (id == -1) {
            doForumForum(ctx, forum, forumNumber);
            return;
        }

        showItem(ctx, forum, forumNumber, id, true);
    }

    String showItem(WebContext ctx, String forum, int forumNumber, int id, boolean doTitle) {
        try {
            Connection conn = null;
            PreparedStatement pGet = null;
            try {
                conn = sm.dbUtils.getDBConnection();
                pGet = prepare_pGet(conn);

                pGet.setInt(1, id);
                ResultSet rs = pGet.executeQuery();

                if (!rs.next()) {
                    throw new RuntimeException("could not quer forum posting id " + id);
                }

                int poster = rs.getInt(1);
                String subj = DBUtils.getStringUTF8(rs, 2);
                String text = DBUtils.getStringUTF8(rs, 3);
                // int id = rs.getInt(4);
                java.sql.Timestamp lastDate = rs.getTimestamp(5);
                String loc = rs.getString(6);
                int xpath = rs.getInt(7);

                // now, show the normal heading

                if (doTitle) {
                    // print header
                    sm.printHeader(ctx, "Forum | " + forum + " | Post: " + subj);
                    printForumMenu(ctx, forum);

                    ctx.println("<hr>");
                    // if(didpost) {
                    // ctx.println("<b>Posted your response.</b><hr>");
                    // }
                }
                showPost(ctx, forum, poster, subj, text, id, lastDate, CLDRLocale.getInstance(loc), xpath);
                if (xpath != -1) {
                    String xpath_string = sm.xpt.getById(xpath);
                    if (xpath_string != null) {
                        showXpath(ctx, xpath_string, xpath, CLDRLocale.getInstance(loc));
                    }
                }

                return subj;
            } finally {
                DBUtils.close(pGet, conn);
            }
        } catch (SQLException se) {
            String complaint = "SurveyForum:  Couldn't add forum " + forum + " - " + DBUtils.unchainSqlException(se)
                + " - fGetByLoc";
            logger.severe(complaint);
            throw new RuntimeException(complaint);
        }
    }

    /*
     * Show one post, "long" form.
     */
    void showPost(WebContext ctx, String forum, int poster, String subj, String text, int id, Timestamp time, CLDRLocale loc,
        int xpath) {
        boolean old = time.before(oldOnOrBefore);
        // get the parent link
        int parentPost = DBUtils.sqlCount("select parent from " + DBUtils.Table.FORUM_POSTS + " where id=?", id);

        ctx.println("<div id='post" + id + "' " + (old ? "style='background-color: #dde;' " : "") + " class='respbox'>");
        if (old) {
            ctx.println("<i style='background-color: white;'>Note: This is an old post, from a previous period of CLDR vetting.</i><br><br>");
        }
        if (parentPost > 0) {
            ctx.println("<span class='reply'><a href='#post" + parentPost + "'>(show parent post)</a></span>");
        }
        String name = getNameLinkFromUid(ctx, poster);
        ctx.println("<div class='person'><a " + ctx.atarget() + " href='" + ctx.url() + "?x=list&u=" + poster + "'>" + name
            + "</a><br>" + time.toString() + "<br>");
        if (loc != null) {
            ctx.println("<span class='reply'>");
            sm.printLocaleLink(ctx, loc, loc.toString());
            ctx.println("</span> * ");
        }
        if (xpath != -1) {
            boolean isFlagged = sm.getSTFactory().getFlag(loc, xpath);
            if (isFlagged) {
                ctx.print(ctx.iconHtml("flag", "flag icon"));
            }
            ctx.println("<span class='reply'><a href='" + forumItemUrl(ctx, loc, xpath) + "'>View Item</a></span> * ");
        }
        if (!old) { // don't reply to old posts  
            ctx.println("<span class='reply'><a href='" + forumUrl(ctx, forum) + ((loc != null) ? ("&_=" + loc) : "") + "&" + F_DO
                + "=" + F_REPLY + "&replyto=" + id + "#replyto'>Reply</a></span>");
        }
        ctx.println("</div>");
        ctx.println("<h3><a href='#post" + id + "'>" + subj + "</a></h3>");

        ctx.println("<div class='response'>" + preparePostText(text) + "</div>");
        ctx.println("</div>");
    }

    public String forumItemUrl(WebContext ctx, CLDRLocale loc, int xpath) {
        WebContext subctx = new WebContext(ctx);
        // .url() + "?_="+loc+"&strid=" + ctx.sm.xpt.getStringIDString(xpath)
        subctx.addQuery("_", loc.getBaseName());
        subctx.addQuery("strid", ctx.sm.xpt.getStringIDString(xpath));
        return subctx.url();
    }

    String getNameLinkFromUid(UserRegistry.User me, int uid) {
        UserRegistry.User theU = null;
        theU = sm.reg.getInfo(uid);
        String aLink = null;
        if ((theU != null) && (me != null) && ((uid == me.id) || // if it's us
                                                                 // or..
            (UserRegistry.userIsTC(me) || // or TC..
            (UserRegistry.userIsVetter(me) && (true || // approved vetter or
                                                       // ..
            me.org.equals(theU.org)))))) { // vetter&same org
            if ((me == null) || (me.org == null)) {
                throw new InternalError("null: c.s.u.o");
            }
            if ((theU != null) && (theU.org == null)) {
                throw new InternalError("null: theU.o");
            }
            // boolean sameOrg = (ctx.session.user.org.equals(theU.org));
            aLink = "<a href='mailto:" + theU.email + "'>" + theU.name + " (" + theU.org + ")</a>";
        } else if (theU != null) {
            aLink = "(" + theU.org + " vetter #" + uid + ")";
        } else {
            aLink = "(#" + uid + ")";
        }

        return aLink;
    }

    String getNameTextFromUid(UserRegistry.User me, int uid) {
        UserRegistry.User theU = null;
        theU = sm.reg.getInfo(uid);
        String aLink = null;
        if ((theU != null) && (me != null) && ((uid == me.id) || // if it's us
                                                                 // or..
            (UserRegistry.userIsTC(me) || // or TC..
            (UserRegistry.userIsVetter(me) && (true || // approved vetter or
                                                       // ..
            me.org.equals(theU.org)))))) { // vetter&same org
            if ((me == null) || (me.org == null)) {
                throw new InternalError("null: c.s.u.o");
            }
            if ((theU != null) && (theU.org == null)) {
                throw new InternalError("null: theU.o");
            }
            // boolean sameOrg = (ctx.session.user.org.equals(theU.org));
            aLink = theU.name + " (" + theU.org + ")";
        } else if (theU != null) {
            aLink = "(" + theU.org + " vetter #" + uid + ")";
        } else {
            aLink = "(#" + uid + ")";
        }

        return aLink;
    }

    String getNameLinkFromUid(WebContext ctx, int uid) {
        if (ctx.session == null || ctx.session.user == null) {
            return getNameLinkFromUid((UserRegistry.User) null, uid);
        } else {
            return getNameLinkFromUid(ctx.session.user, uid);
        }
    }

    /**
     * Called by SM to create the reg
     * 
     * @param xlogger
     *            the logger to use
     * @param ourConn
     *            the conn to use
     */
    public static SurveyForum createTable(java.util.logging.Logger xlogger, Connection ourConn, SurveyMain sm)
        throws SQLException {
        SurveyForum reg = new SurveyForum(xlogger, sm);
        try {
            reg.setupDB(ourConn); // always call - we can figure it out.
            // logger.info("SurveyForum DB: Created.");
        } finally {
            DBUtils.closeDBConnection(ourConn);
        }
        return reg;
    }

    public SurveyForum(java.util.logging.Logger xlogger, SurveyMain ourSm) {
        logger = xlogger;
        sm = ourSm;
    }

    /**
     * Called by SM to shutdown
     * 
     * @deprecated unneeded
     */
    public void shutdownDB() throws SQLException {
    }

    Date oldOnOrBefore = null;

    public void reloadLocales(Connection conn) throws SQLException {
        String sql = "";
        synchronized (conn) {
            {
                // ElapsedTimer et = new
                // ElapsedTimer("setting up DB_LOC2FORUM");
                Statement s = conn.createStatement();
                if (!DBUtils.hasTable(conn, DB_LOC2FORUM)) { // user attribute
                    sql = "";

                    // System.err.println("setting up "+DB_LOC2FORUM);
                    sql = "CREATE TABLE " + DB_LOC2FORUM + " ( " + " locale VARCHAR(255) NOT NULL, "
                        + " forum VARCHAR(255) NOT NULL" + " )";
                    s.execute(sql);
                    sql = "CREATE UNIQUE INDEX " + DB_LOC2FORUM + "_loc ON " + DB_LOC2FORUM + " (locale) ";
                    s.execute(sql);
                    sql = "CREATE INDEX " + DB_LOC2FORUM + "_f ON " + DB_LOC2FORUM + " (forum) ";
                    s.execute(sql);
                } else {
                    int n = s.executeUpdate("delete from " + DB_LOC2FORUM);
                    // System.err.println("Deleted " + n + " from " +
                    // DB_LOC2FORUM);
                }
                s.close();

                PreparedStatement initbl = DBUtils.prepareStatement(conn, "initbl", "INSERT INTO " + DB_LOC2FORUM
                    + " (locale,forum) VALUES (?,?)");
                int updates = 0;
                int errs = 0;
                for (CLDRLocale l : SurveyMain.getLocalesSet()) {
                    initbl.setString(1, l.toString());
                    String forum = l.getLanguage();
                    initbl.setString(2, forum);
                    try {
                        int n = initbl.executeUpdate();
                        if (n > 0) {
                            updates++;
                        }
                    } catch (SQLException se) {
                        if (errs == 0) {
                            System.err.println("While updating " + DB_LOC2FORUM + " -  " + DBUtils.unchainSqlException(se)
                                + " - " + l + ":" + forum + ",  [This and further errors, ignored]");
                        }
                        errs++;
                    }
                }
                initbl.close();
                conn.commit();
                // System.err.println("Updated "+DB_LOC2FORUM+": " + et +
                // ", "+updates+" updates, and " + errs + " SQL complaints");
            }
        }

    }

    /**
     * internal - called to setup db
     */
    private void setupDB(Connection conn) throws SQLException {
        String onOrBefore = CLDRConfig.getInstance().getProperty("CLDR_OLD_POSTS_BEFORE", "12/31/69");
        DateFormat sdf = DateFormat.getDateInstance(DateFormat.SHORT, ULocale.US);
        try {
            oldOnOrBefore = sdf.parse(onOrBefore);
        } catch (Throwable t) {
            System.err.println("Error in parsing CLDR_OLD_POSTS_BEFORE : " + onOrBefore + " - err " + t.toString());
            t.printStackTrace();
            oldOnOrBefore = null;
        }
        if (oldOnOrBefore == null) {
            oldOnOrBefore = new Date(0);
        }
        System.err.println("CLDR_OLD_POSTS_BEFORE: date: " + sdf.format(oldOnOrBefore) + " (format: mm/dd/yy)");
        // synchronized(conn) {
        String sql = null;
        // logger.info("SurveyForum DB: initializing...");
        String locindex = "loc";
        if (DBUtils.db_Mysql) {
            locindex = "loc(122)";
        }

        if (!DBUtils.hasTable(conn, DB_FORA)) { // user attribute
            Statement s = conn.createStatement();
            sql = "";

            sql = "CREATE TABLE " + DB_FORA + " ( " + " id INT NOT NULL " + DBUtils.DB_SQL_IDENTITY
                + ", "
                + " loc VARCHAR(122) NOT NULL, "
                + // interest locale
                " first_time " + DBUtils.DB_SQL_TIMESTAMP0 + " NOT NULL " + DBUtils.DB_SQL_WITHDEFAULT + " "
                + DBUtils.DB_SQL_CURRENT_TIMESTAMP0 + ", " + " last_time TIMESTAMP NOT NULL " + DBUtils.DB_SQL_WITHDEFAULT
                + " CURRENT_TIMESTAMP" + " )";
            s.execute(sql);
            sql = "";
            s.close();
            conn.commit();
        }
        if (!DBUtils.hasTable(conn, DBUtils.Table.FORUM_POSTS.toString())) { // user attribute
            Statement s = conn.createStatement();
            sql = "";

            sql = "CREATE TABLE " + DBUtils.Table.FORUM_POSTS + " ( " + " id INT NOT NULL "
                + DBUtils.DB_SQL_IDENTITY
                + ", "
                + " forum INT NOT NULL, "
                + // which forum (DB_FORA), i.e. de
                " poster INT NOT NULL, " + " subj " + DBUtils.DB_SQL_UNICODE + ", " + " text " + DBUtils.DB_SQL_UNICODE
                + " NOT NULL, " + " parent INT " + DBUtils.DB_SQL_WITHDEFAULT
                + " -1, "
                + " loc VARCHAR(122), "
                + // specific locale, i.e. de_CH
                " xpath INT, "
                + // base xpath
                " first_time " + DBUtils.DB_SQL_TIMESTAMP0 + " NOT NULL " + DBUtils.DB_SQL_WITHDEFAULT + " "
                + DBUtils.DB_SQL_CURRENT_TIMESTAMP0 + ", " + " last_time TIMESTAMP NOT NULL " + DBUtils.DB_SQL_WITHDEFAULT
                + " CURRENT_TIMESTAMP" + " )";
            s.execute(sql);
            sql = "CREATE UNIQUE INDEX " + DBUtils.Table.FORUM_POSTS + "_id ON " + DBUtils.Table.FORUM_POSTS + " (id) ";
            s.execute(sql);
            sql = "CREATE INDEX " + DBUtils.Table.FORUM_POSTS + "_ut ON " + DBUtils.Table.FORUM_POSTS + " (poster, last_time) ";
            s.execute(sql);
            sql = "CREATE INDEX " + DBUtils.Table.FORUM_POSTS + "_utt ON " + DBUtils.Table.FORUM_POSTS + " (id, last_time) ";
            s.execute(sql);
            sql = "CREATE INDEX " + DBUtils.Table.FORUM_POSTS + "_chil ON " + DBUtils.Table.FORUM_POSTS + " (parent) ";
            s.execute(sql);
            sql = "CREATE INDEX " + DBUtils.Table.FORUM_POSTS + "_loc ON " + DBUtils.Table.FORUM_POSTS + " (" + locindex + ") ";
            s.execute(sql);
            sql = "CREATE INDEX " + DBUtils.Table.FORUM_POSTS + "_x ON " + DBUtils.Table.FORUM_POSTS + " (xpath) ";
            s.execute(sql);
            sql = "";
            s.close();
            conn.commit();
        }

        reloadLocales(conn);
        // }
    }

    SurveyMain sm = null;

    public String statistics() {
        return "SurveyForum: nothing to report";
    }

    //
    //
    // public PreparedStatement prepareStatement(String name, String sql) {
    // PreparedStatement ps = null;
    // try {
    // ps =
    // conn.prepareStatement(sql,ResultSet.TYPE_FORWARD_ONLY,ResultSet.CONCUR_READ_ONLY);
    // } catch ( SQLException se ) {
    // String complaint = "Vetter:  Couldn't prepare " + name + " - " +
    // DBUtils.unchainSqlException(se) + " - " + sql;
    // logger.severe(complaint);
    // throw new RuntimeException(complaint);
    // }
    // return ps;
    // }

    public static PreparedStatement prepare_fGetById(Connection conn) throws SQLException {
        return DBUtils.prepareStatement(conn, "fGetById", "SELECT loc FROM " + DB_FORA + " where id=?");
    }

    public static PreparedStatement prepare_fGetByLoc(Connection conn) throws SQLException {
        return DBUtils.prepareStatement(conn, "fGetByLoc", "SELECT id FROM " + DB_FORA + " where loc=?");
    }

    public static PreparedStatement prepare_fAdd(Connection conn) throws SQLException {
        return DBUtils.prepareStatement(conn, "fAdd", "INSERT INTO " + DB_FORA + " (loc) values (?)");
    }

    public static PreparedStatement prepare_pList(Connection conn) throws SQLException {
        return DBUtils.prepareStatement(conn, "pList", "SELECT poster,subj,text,id,last_time,loc,xpath FROM " + DBUtils.Table.FORUM_POSTS.toString()
            + " WHERE (forum = ?) ORDER BY last_time DESC ");
    }

    public static PreparedStatement prepare_pCount(Connection conn) throws SQLException {
        return DBUtils.prepareStatement(conn, "pCount", "SELECT COUNT(*) from " + DBUtils.Table.FORUM_POSTS + " WHERE (forum = ?)");
    }

    public static PreparedStatement prepare_pGet(Connection conn) throws SQLException {
        return DBUtils.prepareStatement(conn, "pGet", "SELECT poster,subj,text,id,last_time,loc,xpath,forum FROM " + DBUtils.Table.FORUM_POSTS.toString()
            + " WHERE (id = ?)");
    }

    public static PreparedStatement prepare_pAdd(Connection conn) throws SQLException {
        return DBUtils.prepareStatement(conn, "pAdd", "INSERT INTO " + DBUtils.Table.FORUM_POSTS.toString()
            + " (poster,subj,text,forum,parent,loc,xpath) values (?,?,?,?,?,?,?)");
    }

    public static PreparedStatement prepare_pAll(Connection conn) throws SQLException {
        return DBUtils.prepareStatement(conn, "pAll", "SELECT " + getPallresult() + " FROM " + DBUtils.Table.FORUM_POSTS + "," + DB_FORA + " WHERE ("
            + DBUtils.Table.FORUM_POSTS + ".forum = " + DB_FORA + ".id) ORDER BY " + DBUtils.Table.FORUM_POSTS + ".last_time DESC");
    }

    // public static PreparedStatement prepare_pMine(Connection conn) throws
    // SQLException { return DBUtils.prepareStatement(conn,"pAll",
    // "SELECT "+DB_POSTS+".poster,"+DB_POSTS+".subj,"+DB_POSTS+".text,"+DB_POSTS+".last_time,"+DB_POSTS+".id,"+DB_POSTS+".forum,"+DB_FORA+".loc"+" FROM "
    // + DB_POSTS +
    // ","+DB_FORA+" WHERE ("+DB_POSTS+".forum = "+DB_FORA+".id) ORDER BY "+DB_POSTS+".last_time DESC");
    // }
    // public static PreparedStatement prepare_pAllN(Connection conn) throws
    // SQLException { return DBUtils.prepareStatement(conn,"pAllN",
    // "SELECT "+DB_POSTS+".poster,"+DB_POSTS+".subj,"+DB_POSTS+".text,"+DB_POSTS+".last_time,"+DB_POSTS+".id,"+DB_POSTS+".forum,"+DB_FORA+".loc"+" FROM "
    // + DB_POSTS +
    // ","+DB_FORA+" WHERE ("+DB_POSTS+".forum = "+DB_FORA+".id) ORDER BY "+DB_POSTS+".last_time DESC");
    // }
    public static PreparedStatement prepare_pForMe(Connection conn) throws SQLException {
        return DBUtils.prepareStatement(conn, "pForMe", "SELECT " + getPallresult() + " FROM " + DBUtils.Table.FORUM_POSTS.toString()
            + ","
            + DB_FORA
            + " " // same as pAll
            + " where (" + DBUtils.Table.FORUM_POSTS + ".forum=" + DB_FORA + ".id) AND exists ( select " + UserRegistry.CLDR_INTEREST
            + ".forum from " + UserRegistry.CLDR_INTEREST + "," + DB_FORA + " where " + UserRegistry.CLDR_INTEREST
            + ".uid=? AND " + UserRegistry.CLDR_INTEREST + ".forum=" + DB_FORA + ".loc AND " + DB_FORA + ".id=" + DBUtils.Table.FORUM_POSTS.toString()
            + ".forum) ORDER BY " + DBUtils.Table.FORUM_POSTS + ".last_time DESC");
    }

    public static PreparedStatement prepare_pIntUsers(Connection conn) throws SQLException {
        return DBUtils.prepareStatement(conn, "pIntUsers", "SELECT uid from " + UserRegistry.CLDR_INTEREST + " where forum=?");
    }

    /**
     * @deprecated section is not needed.
     * @param ctx
     * @param section
     *            ignored
     * @param p
     * @param xpath
     * @param contents
     */
    void showForumLink(WebContext ctx, DataSection section, DataSection.DataRow p, int xpath, String contents) {
        showForumLink(ctx, p, xpath, contents);
    }

    void showForumLink(WebContext ctx, DataSection.DataRow p, int xpath, String contents) {
        // if(ctx.session.user == null) {
        // return; // no user?
        // }
        // String title;
        /*
         * if(!ctx.session.user.interestedIn(forum)) { title =
         * " (not on your interest list)"; }
         */
        // title = null /*+ title*/;
        String forumLinkContents = getForumLink(ctx, p, xpath, contents);
        ctx.println(forumLinkContents);
    }

    void showForumLink(WebContext ctx, DataSection.DataRow p, String contents) {
        // if(ctx.session.user == null) {
        // return; // no user?
        // }
        // String title;
        /*
         * if(!ctx.session.user.interestedIn(forum)) { title =
         * " (not on your interest list)"; }
         */
        // title = null /*+ title*/;
        String forumLinkContents = getForumLink(ctx, p, contents);
        ctx.println(forumLinkContents);
    }

    /**
     * @param ctx
     * @param p
     * @param xpath
     * @param contents
     * @return
     */
    public String getForumLink(WebContext ctx, DataSection.DataRow p, int xpath, String contents) {
        String forumLinkContents = "<a " + ctx.atarget(WebContext.TARGET_ZOOMED) + "  href='" + forumUrl(ctx, p, xpath) + "' >" // title='"+title+"'
            + contents + "</a>";
        return forumLinkContents;
    }

    public String getForumLink(WebContext ctx, DataSection.DataRow p, String contents) {
        String forumLinkContents = "<a " + ctx.atarget(WebContext.TARGET_ZOOMED) + "  href='" + forumUrl(ctx, p, p.getXpath())
            + "' >" // title='"+title+"'
            + contents + "</a>";
        return forumLinkContents;
    }

    void showForumLink(WebContext ctx, DataSection section, DataSection.DataRow p, int xpath) {
        showForumLink(ctx, section, p, xpath, ctx.iconHtml("zoom", "zoom"));
    }

    // "link" UI
    static public String forumUrl(WebContext ctx, DataSection.DataRow p, int xpath) {
        String xp = ctx.sm.xpt.getById(xpath);
        if (xp == null) {
            xp = Integer.toString(xpath);
        }
        return forumUrl(ctx, p, xp);
    }

    static public String forumUrl(WebContext ctx, DataSection.DataRow p, String xp) {
        xp = java.net.URLEncoder.encode(xp);
        return ctx.base() + "?_=" + ctx.getLocale() + "&" + F_FORUM + "=" + p.getIntgroup() + "&" + F_XPATH + "=" + xp;
    }

    static public String localeToForum(String locale) {
        return localeToForum(new ULocale(locale));
    }

    static public String localeToForum(ULocale locale) {
        return locale.getLanguage();
    }

    static public String forumUrl(WebContext ctx, String locale, int xpath) {
        ULocale u = new ULocale(locale);
        return ctx.base() + "?_=" + locale + "&" + F_FORUM + "=" + u.getLanguage() + "&" + F_XPATH + "=" + xpath;
    }

    static public String forumUrl(WebContext ctx, String forum) {
        return (ctx.base() + "?" + F_FORUM + "=" + forum);
    }

    static public String forumLink(WebContext ctx, String forum) {
        return "<a " + ctx.atarget(WebContext.TARGET_DOCS) + " class='forumlink' href='" + forumUrl(ctx, forum) + "' >" // title='"+title+"'
            + "Forum" + "</a>";
    }

    static public String forumUrlFragment(String locale, int xpath) {
        ULocale u = new ULocale(locale);
        return "?_=" + locale + "&" + F_FORUM + "=" + u.getLanguage() + "&" + F_XPATH + "=" + xpath;
    }

    static public String forumUrlFragment(String forum) {
        return ("?" + F_FORUM + "=" + forum);
    }

    String returnText(WebContext ctx, int base_xpath) {
        return "Zoom out to <a href='" + returnUrl(ctx, ctx.getLocale(), base_xpath) + "'>"
            + ctx.iconHtml("zoom", "zoom out to " + ctx.getLocale()) + " " + ctx.getLocale() + "</a>";
    }

    // XML/RSS
    //private static final String DATE_FORMAT = "yyyy-MM-dd";

    private static void sendErr(HttpServletRequest request, HttpServletResponse response, String err) throws IOException {
        response.setContentType("text/html; charset=utf-8");
        WebContext xctx = new WebContext(request, response);
        xctx.println("Error: " + err);
        xctx.close();
        return;
    }

    public boolean doFeed(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {

        response.setContentType("text/xml; charset=utf-8");

        String feedType = request.getParameter("feed");
        if (feedType == null) {
            feedType = "rss_0.94";
        }

        String email = request.getParameter("email");
        String pw = request.getParameter("pw");

        if (email == null || pw == null) {
            sendErr(request, response, "URL error.");
            return true;
        }

        UserRegistry.User user = null;
        try {
            user = sm.reg.get(pw, email, "RSS@" + WebContext.userIP(request));
        } catch (LogoutException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        if (user == null) {
            sendErr(request, response, "authentication err");
            return true;
        }

        String base = "http://" + request.getServerName() + ":" + request.getServerPort() + request.getContextPath()
            + request.getServletPath();
        //String kind = request.getParameter("kind");
        String loc = request.getParameter("_");

        if ((loc != null) && (!UserRegistry.userCanModifyLocale(user, CLDRLocale.getInstance(loc)))) {
            sendErr(request, response, "permission denied for locale " + loc);
            return true;
        }

        // DateFormat dateParser = new SimpleDateFormat(DATE_FORMAT);

        try {
            SyndFeed feed = new SyndFeedImpl();
            feed.setFeedType(feedType);
            List<SyndEntry> entries = new ArrayList<SyndEntry>();
            feed.setLink(base);

            if (loc != null) {
                feed.setTitle("CLDR Feed for " + loc);
                feed.setDescription("test feed");

                SyndEntry entry;
                SyndContent description;

                try {
                    Connection conn = null;
                    PreparedStatement pList = null;
                    try {
                        conn = sm.dbUtils.getDBConnection();
                        pList = prepare_pList(conn);

                        int forumNumber = getForumNumberFromDB(loc);
                        //int count = 0;
                        if (forumNumber >= GOOD_FORUM) {
                            pList.setInt(1, forumNumber);
                            ResultSet rs = pList.executeQuery();

                            while (rs.next()) {
                                int poster = rs.getInt(1);
                                String subj = rs.getString(2);
                                String text = rs.getString(3);
                                int id = rs.getInt(4);
                                java.sql.Timestamp lastDate = rs.getTimestamp(5);

                                String nameLink = getNameTextFromUid(user, poster);

                                entry = new SyndEntryImpl();
                                entry.setTitle(subj);
                                entry.setAuthor(nameLink);
                                entry.setLink(base + "?forum=" + loc + "&amp;" + F_DO + "=" + F_VIEW + "&amp;id=" + id
                                    + "&amp;email=" + email + "&amp;pw=" + pw);
                                entry.setPublishedDate(lastDate); // dateParser.parse("2004-06-08"));
                                description = new SyndContentImpl();
                                description.setType("text/html");
                                description.setValue("From: " + nameLink + "<br><hr>" + shortenText(text));
                                entry.setDescription(description);
                                entries.add(entry);

                                //count++;
                            }
                        }
                    } finally {
                        DBUtils.close(pList, conn);
                    }
                } catch (SQLException se) {
                    String complaint = "SurveyForum:  Couldn't use forum " + loc + " - " + DBUtils.unchainSqlException(se)
                        + " - fGetByLoc";
                    logger.severe(complaint);
                    throw new RuntimeException(complaint);
                }

            } else { /* loc is null */
                feed.setTitle("CLDR Feed for " + user.email);
                String locs[] = user.getInterestList();
                if (locs == null) {
                    feed.setDescription("All CLDR locales.");
                } else if (locs.length == 0) {
                    feed.setDescription("No locales.");
                } else {
                    String locslist = null;
                    for (String l : locs) {
                        if (locslist == null) {
                            locslist = l;
                        } else {
                            locslist = locslist + " " + l;
                        }
                    }
                    feed.setDescription("Your locales: " + locslist);
                }

                SyndEntry entry;
                SyndContent description;
                // not a specific locale, but ALL [of my locales]

                try {
                    Connection conn = null;
                    PreparedStatement pAll = null;
                    try {
                        conn = sm.dbUtils.getDBConnection();
                        // first, articles.
                        ResultSet rs = null; // list of articles.

                        if (locs == null) {
                            pAll = prepare_pAll(conn);
                            rs = pAll.executeQuery();
                        } else {
                            pAll = prepare_pForMe(conn);
                            pAll.setInt(1, user.id);
                            rs = pAll.executeQuery();
                        }

                        //int count = 0;

                        while (rs.next() && true) {
                            int poster = rs.getInt(1);
                            String subj = DBUtils.getStringUTF8(rs, 2);
                            String text = DBUtils.getStringUTF8(rs, 3);
                            java.sql.Timestamp lastDate = rs.getTimestamp(4); // TODO:
                                                                              // timestamp
                            int id = rs.getInt(5);
                            //String forum = rs.getString(6);
                            String ploc = rs.getString(7);

                            String forumText = new ULocale(ploc).getLanguage();
                            String nameLink = getNameTextFromUid(user, poster);

                            entry = new SyndEntryImpl();
                            //String forumPrefix = forumText + ":";
                            if (subj.startsWith(forumText)) {
                                entry.setTitle(subj);
                            } else {
                                entry.setTitle(forumText + ":" + subj);
                            }
                            entry.setAuthor(nameLink);
                            entry.setLink(base + "?forum=" + forumText + "&amp;" + F_DO + "=" + F_VIEW + "&amp;id=" + id
                                + "&amp;email=" + email + "&amp;pw=" + pw);
                            entry.setPublishedDate(lastDate); // dateParser.parse("2004-06-08"));
                            description = new SyndContentImpl();
                            description.setType("text/html");
                            description.setValue("From: " + nameLink + "<br><hr>" + shortenText(text));
                            entry.setDescription(description);
                            entries.add(entry);

                            //count++;
                        }

                        // now, data??
                        // select CLDR_DATA.value,SF_LOC2FORUM.locale from
                        // SF_LOC2FORUM,CLDR_INTEREST,CLDR_DATA where
                        // SF_LOC2FORUM.forum=CLDR_INTEREST.forum AND
                        // CLDR_INTEREST.uid=2 AND
                        // CLDR_DATA.locale=SF_LOC2FORUM.locale AND
                        // CLDR_DATA.submitter is null ORDER BY
                        // CLDR_DATA.modtime DESC
                    } finally {
                        DBUtils.close(pAll, conn);
                    }
                } catch (SQLException se) {
                    String complaint = "SurveyForum:  Couldn't use forum s for RSS- " + DBUtils.unchainSqlException(se)
                        + " - fGetByLoc";
                    logger.severe(complaint);
                    throw new RuntimeException(complaint);
                }
            }
            feed.setEntries(entries);

            Writer writer = response.getWriter();
            SyndFeedOutput output = new SyndFeedOutput();
            output.output(feed, writer);
            // writer.close();

            // System.out.println("The feed has been written to the file ["+fileName+"]");
        } catch (Throwable ie) {
            System.err.println("Error getting RSS feed: " + ie.toString());
            ie.printStackTrace();
            // todo: err
        }
        return true;
    }

    private static String shortenText(String s) {
        if (s.length() < 100) {
            return s;
        } else {
            return s.substring(0, 100) + "...";
        }
    }

    String forumFeedStuff(WebContext ctx) {
        if (ctx.session == null || ctx.session.user == null || !UserRegistry.userIsStreet(ctx.session.user)) {
            return "";
        }
        String feedUrl = ctx.schemeHostPort()
            + ctx.base()
            + ("/feed?_=" + ctx.getLocale().getLanguage() + "&amp;email=" + ctx.session.user.email + "&amp;pw="
                + ctx.session.user.password + "&amp;");
        return
        /*
         * "<link rel=\"alternate\" type=\"application/atom+xml\" title=\"Atom 1.0\" href=\""
         * +feedUrl+"&feed=atom_1.0\">" +
         */
        "<link rel=\"alternate\" type=\"application/rdf+xml\" title=\"RSS 1.0\" href=\"" + feedUrl + "&feed=rss_1.0\">"
            + "<link rel=\"alternate\" type=\"application/rss+xml\" title=\"RSS 2.0\" href=\"" + feedUrl + "&feed=rss_2.0\">";
    }

    public static String forumFeedIcon(WebContext ctx, String forum) {
        if (ctx.session == null || ctx.session.user == null || !UserRegistry.userIsStreet(ctx.session.user)) {
            return "";
        }
        String feedUrl = ctx.schemeHostPort()
            + ctx.base()
            + ("/feed?_=" + ctx.getLocale().getLanguage() + "&amp;email=" + ctx.session.user.email + "&amp;pw="
                + ctx.session.user.password + "&amp;");

        return " <a href='" + feedUrl + "&feed=rss_2.0" + "'>" + ctx.iconHtml("feed", "RSS 2.0") + "<!-- Forum&nbsp;rss --></a>"; /*
                                                                                                                                   * |
                                                                                                                                   * "
                                                                                                                                   * +
                                                                                                                                   * "<a href='"
                                                                                                                                   * +
                                                                                                                                   * feedUrl
                                                                                                                                   * +
                                                                                                                                   * "&feed=rss_2.0"
                                                                                                                                   * +
                                                                                                                                   * "'>"
                                                                                                                                   * +
                                                                                                                                   * ctx
                                                                                                                                   * .
                                                                                                                                   * iconHtml
                                                                                                                                   * (
                                                                                                                                   * "feed"
                                                                                                                                   * ,
                                                                                                                                   * "RSS 1.0"
                                                                                                                                   * )
                                                                                                                                   * +
                                                                                                                                   * "RSS 1.0</a>"
                                                                                                                                   * ;
                                                                                                                                   */

    }

    String mainFeedStuff(WebContext ctx) {
        if (ctx.session == null || ctx.session.user == null || !UserRegistry.userIsStreet(ctx.session.user)) {
            return "";
        }

        String feedUrl = ctx.schemeHostPort() + ctx.base()
            + ("/feed?email=" + ctx.session.user.email + "&amp;pw=" + ctx.session.user.password + "&amp;");
        return "<link rel=\"alternate\" type=\"application/atom+xml\" title=\"Atom 1.0\" href=\"" + feedUrl + "&feed=atom_1.0\">"
            + "<link rel=\"alternate\" type=\"application/rdf+xml\" title=\"RSS 1.0\" href=\"" + feedUrl + "&feed=rss_1.0\">"
            + "<link rel=\"alternate\" type=\"application/rss+xml\" title=\"RSS 2.0\" href=\"" + feedUrl + "&feed=rss_2.0\">";
    }

    public String mainFeedIcon(WebContext ctx) {
        if (ctx.session == null || ctx.session.user == null || !UserRegistry.userIsStreet(ctx.session.user)) {
            return "";
        }
        String feedUrl = ctx.schemeHostPort() + ctx.base()
            + ("/feed?email=" + ctx.session.user.email + "&amp;pw=" + ctx.session.user.password + "&amp;");

        return "<a href='" + feedUrl + "&feed=rss_2.0" + "'>" + ctx.iconHtml("feed", "RSS 2.0") + "RSS 2.0</a>"; /*
                                                                                                                  * |
                                                                                                                  * "
                                                                                                                  * +
                                                                                                                  * "<a href='"
                                                                                                                  * +
                                                                                                                  * feedUrl
                                                                                                                  * +
                                                                                                                  * "&feed=rss_2.0"
                                                                                                                  * +
                                                                                                                  * "'>"
                                                                                                                  * +
                                                                                                                  * ctx
                                                                                                                  * .
                                                                                                                  * iconHtml
                                                                                                                  * (
                                                                                                                  * "feed"
                                                                                                                  * ,
                                                                                                                  * "RSS 1.0"
                                                                                                                  * )
                                                                                                                  * +
                                                                                                                  * "RSS 1.0</a>"
                                                                                                                  * ;
                                                                                                                  */

    }

    public int postCountFor(CLDRLocale locale, int xpathId) {
        Connection conn = null;
        PreparedStatement ps = null;
        String tableName = DBUtils.Table.FORUM_POSTS.toString();
        try {
            conn = DBUtils.getInstance().getDBConnection();

            ps = DBUtils.prepareForwardReadOnly(conn, "select count(*) from " + tableName + " where loc=? and xpath=?");
            ps.setString(1, locale.getBaseName());
            ps.setInt(2, xpathId);

            return DBUtils.sqlCount(null, conn, ps);
        } catch (SQLException e) {
            SurveyLog.logException(e, "postCountFor for " + tableName + " " + locale + ":" + xpathId);
            return 0;
        } finally {
            DBUtils.close(ps, conn);
        }
    }

    public JSONArray toJSON(CookieSession session, CLDRLocale locale, int base_xpath) throws JSONException {
        return toJSON(session, locale, base_xpath, 0);
    }
    
    public JSONArray toJSON(CookieSession session, CLDRLocale locale, int base_xpath, int ident) throws JSONException {
        boolean canModify = (UserRegistry.userCanAccessForum(session.user, locale));
        if (!canModify)
            return null;

        JSONArray ret = new JSONArray();

        int forumNumber = getForumNumber(locale.getLanguage());

        try {
            Connection conn = null;
            try {
                conn = sm.dbUtils.getDBConnection();
                Object[][] o = null;
                if(ident == 0)
                    o = DBUtils.sqlQueryArrayArrayObj(conn, "select " + getPallresultfora() + "  FROM " + DBUtils.Table.FORUM_POSTS.toString()
                    + " WHERE (" + DBUtils.Table.FORUM_POSTS + ".forum =? AND " + DBUtils.Table.FORUM_POSTS + " .xpath =?) ORDER BY "
                    + DBUtils.Table.FORUM_POSTS.toString()
                    + ".last_time DESC", forumNumber, base_xpath);
                else
                    o = DBUtils.sqlQueryArrayArrayObj(conn, "select " + getPallresultfora() + "  FROM " + DBUtils.Table.FORUM_POSTS.toString()
                        + " WHERE (" + DBUtils.Table.FORUM_POSTS + ".forum =? AND " + DBUtils.Table.FORUM_POSTS + " .xpath =? AND " + DBUtils.Table.FORUM_POSTS + " .id =?) ORDER BY " + DBUtils.Table.FORUM_POSTS.toString()
                        + ".last_time DESC", forumNumber, base_xpath, ident);
                // private final static String pAllResult =
                // DB_POSTS+".poster,"+DB_POSTS+".subj,"+DB_POSTS+".text,"+DB_POSTS+".last_time,"+DB_POSTS+".id,"+DB_POSTS+".forum,"+DB_FORA+".loc";
                if (o != null) {
                    for (int i = 0; i < o.length; i++) {
                        int poster = (Integer) o[i][0];
                        String subj2 = (String) o[i][1];
                        String text2 = (String) o[i][2];
                        Timestamp lastDate = (Timestamp) o[i][3];
                        int id = (Integer) o[i][4];

                        if (lastDate.after(oldOnOrBefore) || false) {
                            JSONObject post = new JSONObject();
                            post.put("poster", poster)
                                .put("posterInfo", SurveyAjax.JSONWriter.wrap(CookieSession.sm.reg.getInfo(poster)))
                                .put("subject", subj2).put("text", text2).put("date", lastDate).put("id", id);
                            ret.put(post);
                        }
                    }
                }
                return ret;
                // System.err.println("Got: " + Arrays.toString(o) +
                // " for fn " + forumNumber + " and " + base_xpath);
            } finally {
                DBUtils.close(conn);
            }
        } catch (SQLException se) {
            String complaint = "SurveyForum:  Couldn't show posts in forum " + locale + " - " + DBUtils.unchainSqlException(se)
                + " - fGetByLoc";
            logger.severe(complaint);
            // ctx.println("<br>"+complaint+"</br>");
            throw new RuntimeException(complaint);
        }

    }

    /**
     * @return the pallresult
     */
    private static String getPallresult() {
        return DBUtils.Table.FORUM_POSTS + ".poster," + DBUtils.Table.FORUM_POSTS + ".subj," + DBUtils.Table.FORUM_POSTS + ".text,"
            + DBUtils.Table.FORUM_POSTS.toString()
            + ".last_time," + DBUtils.Table.FORUM_POSTS + ".id," + DBUtils.Table.FORUM_POSTS + ".forum," + DB_FORA + ".loc";
    }

    /**
     * @return the pallresultfora
     */
    private static String getPallresultfora() {
        return DBUtils.Table.FORUM_POSTS + ".poster," + DBUtils.Table.FORUM_POSTS + ".subj," + DBUtils.Table.FORUM_POSTS + ".text,"
            + DBUtils.Table.FORUM_POSTS.toString()
            + ".last_time," + DBUtils.Table.FORUM_POSTS + ".id";
    }
}
