/*---------------------------------------------------------------------------*\
  This software is released under a BSD license, adapted from
  <http://opensource.org/licenses/bsd-license.php>

  Copyright &copy; 2004-2012 Brian M. Clapper.
  All rights reserved.

  Redistribution and use in source and binary forms, with or without
  modification, are permitted provided that the following conditions are met:

  * Redistributions of source code must retain the above copyright notice,
    this list of conditions and the following disclaimer.

  * Redistributions in binary form must reproduce the above copyright notice,
    this list of conditions and the following disclaimer in the documentation
    and/or other materials provided with the distribution.

  * Neither the name "clapper.org", "curn", nor the names of the project's
    contributors may be used to endorse or promote products derived from
    this software without specific prior written permission.

  THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
  IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
  THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
  PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR
  CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
  EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
  PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
  PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
  LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
  NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
  SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
\*---------------------------------------------------------------------------*/


package org.clapper.curn.plugins;

import org.clapper.curn.CurnConfig;
import org.clapper.curn.CurnException;
import org.clapper.curn.FeedInfo;
import org.clapper.curn.MainConfigItemPlugIn;
import org.clapper.curn.FeedConfigItemPlugIn;
import org.clapper.curn.PostFeedProcessPlugIn;
import org.clapper.curn.parser.RSSChannel;
import org.clapper.curn.parser.RSSItem;

import org.clapper.util.classutil.ClassUtil;
import org.clapper.util.config.ConfigurationException;
import org.clapper.util.logging.Logger;

import java.util.HashMap;
import java.util.Map;
import org.clapper.curn.FeedCache;

/**
 * The <tt>ShowAuthorsPlugIn</tt> handles enabling/disabling display of the
 * "author" fields on feeds and feed items. It intercepts the following
 * configuration parameters:
 *
 * <table border="1">
 *   <tr valign="top" align="left">
 *     <th align="left">Section</th>
 *     <th align="left">Parameter</th>
 *     <th align="left">Meaning</th>
 *   </tr>
 *   <tr valign="top">
 *     <td><tt>[curn]</tt></td>
 *     <td><tt>ShowAuthors</tt></td>
 *     <td>Default (global) value for the show authors capability.
 *         Defaults to false.</td>
 *   </tr>
 *   <tr valign="top">
 *     <td><tt>[Feed<i>xxx</i>]</tt></td>
 *     <td><tt>ShowAuthors</tt></td>
 *     <td>Whether or not to show author data for the feed. If not specified,
 *         the global default is used.</td>
 *   </tr>
 * </table>
 *
 * @version <tt>$Revision$</tt>
 */
public class ShowAuthorsPlugIn
    implements MainConfigItemPlugIn,
               FeedConfigItemPlugIn,
               PostFeedProcessPlugIn
{
    /*----------------------------------------------------------------------*\
                             Private Constants
    \*----------------------------------------------------------------------*/

    private static final String VAR_SHOW_AUTHORS = "ShowAuthors";

    /*----------------------------------------------------------------------*\
                            Private Data Items
    \*----------------------------------------------------------------------*/

    /**
     * Feed author flag, by feed
     */
    private Map<FeedInfo,Boolean> perFeedShowAuthorsFlag =
        new HashMap<FeedInfo,Boolean>();

    /**
     * Global default
     */
    private boolean showAuthorsDefault = false;

    /**
     * For log messages
     */
    private static final Logger log = new Logger (ShowAuthorsPlugIn.class);

    /*----------------------------------------------------------------------*\
                                Constructor
    \*----------------------------------------------------------------------*/

    /**
     * Default constructor (required).
     */
    public ShowAuthorsPlugIn()
    {
        // Nothing to do
    }

    /*----------------------------------------------------------------------*\
               Public Methods Required by *PlugIn Interfaces
    \*----------------------------------------------------------------------*/

    /**
     * Get a displayable name for the plug-in.
     *
     * @return the name
     */
    public String getPlugInName()
    {
        return "Show Authors";
    }

    /**
     * Get the sort key for this plug-in.
     *
     * @return the sort key string.
     */
    public String getPlugInSortKey()
    {
        return ClassUtil.getShortClassName(getClass().getName());
    }

    /**
     * Initialize the plug-in. This method is called before any of the
     * plug-in methods are called.
     *
     * @throws CurnException on error
     */
    public void initPlugIn()
        throws CurnException
    {
    }

    /**
     * Called immediately after <i>curn</i> has read and processed a
     * configuration item in the main [curn] configuration section. All
     * configuration items are passed, one by one, to each loaded plug-in.
     * If a plug-in class is not interested in a particular configuration
     * item, this method should simply return without doing anything. Note
     * that some configuration items may simply be variable assignment;
     * there's no real way to distinguish a variable assignment from a
     * blessed configuration item.
     *
     * @param sectionName  the name of the configuration section where
     *                     the item was found
     * @param paramName    the name of the parameter
     * @param config       the {@link CurnConfig} object
     * 
     * @throws CurnException on error
     *
     * @see CurnConfig
     */
    public void runMainConfigItemPlugIn(String     sectionName,
                                        String     paramName,
                                        CurnConfig config)
        throws CurnException
    {
        try
        {
            if (paramName.equals(VAR_SHOW_AUTHORS))
            {
                showAuthorsDefault =
                    config.getRequiredBooleanValue(sectionName, paramName);
            }
        }

        catch (ConfigurationException ex)
        {
            throw new CurnException(ex);
        }
    }

    /**
     * Called immediately after <i>curn</i> has read and processed a
     * configuration item in a "feed" configuration section. All
     * configuration items are passed, one by one, to each loaded plug-in.
     * If a plug-in class is not interested in a particular configuration
     * item, this method should simply return without doing anything. Note
     * that some configuration items may simply be variable assignment;
     * there's no real way to distinguish a variable assignment from a
     * blessed configuration item.
     *
     * @param sectionName  the name of the configuration section where
     *                     the item was found
     * @param paramName    the name of the parameter
     * @param config       the active configuration
     * @param feedInfo     partially complete <tt>FeedInfo</tt> object
     *                     for the feed. The URL is guaranteed to be
     *                     present, but no other fields are.
     * 
     * @return <tt>true</tt> to continue processing the feed,
     *         <tt>false</tt> to skip it
     *
     * @throws CurnException on error
     *
     * @see CurnConfig
     * @see FeedInfo
     * @see FeedInfo#getURL
     */
    public boolean runFeedConfigItemPlugIn(String     sectionName,
                                           String     paramName,
                                           CurnConfig config,
                                           FeedInfo   feedInfo)
        throws CurnException
    {
        try
        {
            if (paramName.equals (VAR_SHOW_AUTHORS))
            {
                boolean flag = config.getRequiredBooleanValue(sectionName,
                                                              paramName);
                perFeedShowAuthorsFlag.put(feedInfo, flag);
                log.debug("[" + sectionName + "]: " + paramName + "=" + flag);
            }

            return true;
        }

        catch (ConfigurationException ex)
        {
            throw new CurnException(ex);
        }
    }

    /**
     * Called immediately after a feed is parsed, but before it is
     * otherwise processed. This method can return <tt>false</tt> to signal
     * <i>curn</i> that the feed should be skipped. For instance, a plug-in
     * that filters on the parsed feed data could use this method to weed
     * out non-matching feeds before they are downloaded. Similarly, a
     * plug-in that edits the parsed data (removing or editing individual
     * items, for instance) could use method to do so.
     *
     * @param feedInfo  the {@link FeedInfo} object for the feed that
     *                  has been downloaded and parsed.
     * @param feedCache the feed cache
     * @param channel   the parsed channel data
     *
     * @return <tt>true</tt> if <i>curn</i> should continue to process the
     *         feed, <tt>false</tt> to skip the feed. A return value of
     *         <tt>false</tt> aborts all further processing on the feed.
     *         In particular, <i>curn</i> will not pass the feed along to
     *         other plug-ins that have yet to be notified of this event.
     *
     * @throws CurnException on error
     *
     * @see RSSChannel
     * @see FeedInfo
     */
    public boolean runPostFeedProcessPlugIn(FeedInfo   feedInfo,
                                          FeedCache  feedCache,
                                          RSSChannel channel)
        throws CurnException
    {
        Boolean showBoxed = perFeedShowAuthorsFlag.get(feedInfo);
        boolean show = showAuthorsDefault;

        if (showBoxed != null)
            show = showBoxed;

        log.debug("Post-parse, " + feedInfo.getURL() + ": showAuthors=" + show);

        if (! show)
        {
            log.debug("Removing author fields from feed \"" +
                      feedInfo.getURL().toString() + "\"");

            channel.clearAuthors();
            for (RSSItem item : channel.getItems())
                item.clearAuthors();
        }

        return true;
    }
}
