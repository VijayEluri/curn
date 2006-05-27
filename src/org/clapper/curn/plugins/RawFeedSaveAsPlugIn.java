/*---------------------------------------------------------------------------*\
  $Id$
  ---------------------------------------------------------------------------
  This software is released under a Berkeley-style license:

  Copyright (c) 2004-2006 Brian M. Clapper. All rights reserved.

  Redistribution and use in source and binary forms are permitted provided
  that: (1) source distributions retain this entire copyright notice and
  comment; and (2) modifications made to the software are prominently
  mentioned, and a copy of the original software (or a pointer to its
  location) are included. The name of the author may not be used to endorse
  or promote products derived from this software without specific prior
  written permission.

  THIS SOFTWARE IS PROVIDED ``AS IS'' AND WITHOUT ANY EXPRESS OR IMPLIED
  WARRANTIES, INCLUDING, WITHOUT LIMITATION, THE IMPLIED WARRANTIES OF
  MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE.

  Effectively, this means you can do what you want with the software except
  remove this notice or take advantage of the author's name. If you modify
  the software and redistribute your modified version, you must indicate that
  your version is a modification of the original, and you must provide either
  a pointer to or a copy of the original.
\*---------------------------------------------------------------------------*/

package org.clapper.curn.plugins;

import org.clapper.curn.Constants;
import org.clapper.curn.Curn;
import org.clapper.curn.CurnConfig;
import org.clapper.curn.CurnException;
import org.clapper.curn.FeedInfo;
import org.clapper.curn.parser.RSSChannel;
import org.clapper.curn.FeedConfigItemPlugIn;
import org.clapper.curn.PostConfigPlugIn;
import org.clapper.curn.PreFeedDownloadPlugIn;
import org.clapper.curn.PostFeedDownloadPlugIn;

import org.clapper.util.config.ConfigurationException;
import org.clapper.util.io.FileUtil;
import org.clapper.util.logging.Logger;

import java.io.File;
import java.io.IOException;

import java.util.Map;
import java.util.HashMap;

/**
 * The <tt>RawFeedSaveAsPlugIn</tt> handles saving a feed to a known location.
 * It intercepts the following per-feed configuration parameters:
 *
 * <table>
 *   <tr valign="top">
 *     <td><tt>SaveAs</tt></td>
 *     <td>Path to file where raw XML should be saved.</td>
 *   </tr>
 *   <tr valign="top">
 *     <td><tt>SaveOnly</tt></td>
 *     <td>Indicates that raw XML should be saved, but not parsed. This
 *         parameter can only be specified if <tt>SaveAs</tt> is also
 *         specified.</td>
 *   </tr>
 *   <tr valign="top">
 *     <td><tt>SaveAsEncoding</tt></td>
 *     <td>The character set encoding to use when saving the file. Default:
 *     "utf-8"</td>
 *   </tr>
 * </table>
 *
 * @version <tt>$Revision$</tt>
 */
public class RawFeedSaveAsPlugIn
    implements FeedConfigItemPlugIn,
               PostConfigPlugIn,
               PreFeedDownloadPlugIn,
               PostFeedDownloadPlugIn
{
    /*----------------------------------------------------------------------*\
                             Private Constants
    \*----------------------------------------------------------------------*/

    private static final String VAR_SAVE_FEED_AS      = "SaveAs";
    private static final String VAR_SAVE_ONLY         = "SaveOnly";
    private static final String VAR_SAVE_AS_ENCODING  = "SaveAsEncoding";

    /*----------------------------------------------------------------------*\
                              Private Classes
    \*----------------------------------------------------------------------*/

    /**
     * Feed save info
     */
    class FeedSaveInfo
    {
        String  sectionName;
        File    saveAsFile;
        boolean saveOnly;
        String  saveAsEncoding = "utf-8";

        FeedSaveInfo()
        {
        }
    }

    /*----------------------------------------------------------------------*\
                            Private Data Items
    \*----------------------------------------------------------------------*/

    /**
     * Feed save data, by feed
     */
    private Map<FeedInfo,FeedSaveInfo> perFeedSaveAsMap =
        new HashMap<FeedInfo,FeedSaveInfo>();

    /**
     * Saved reference to the configuration
     */
    private CurnConfig config = null;

    /**
     * For log messages
     */
    private static Logger log = new Logger (RawFeedSaveAsPlugIn.class);

    /*----------------------------------------------------------------------*\
                                Constructor
    \*----------------------------------------------------------------------*/

    /**
     * Default constructor (required).
     */
    public RawFeedSaveAsPlugIn()
    {
    }

    /*----------------------------------------------------------------------*\
                              Public Methods
    \*----------------------------------------------------------------------*/

    /**
     * Get a displayable name for the plug-in.
     *
     * @return the name
     */
    public String getName()
    {
        return "Save As";
    }

    /*----------------------------------------------------------------------*\
               Public Methods Required by *PlugIn Interfaces
    \*----------------------------------------------------------------------*/

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
     * @throws CurnException on error
     *
     * @see CurnConfig
     * @see FeedInfo
     * @see FeedInfo#getURL
     */
    public void runFeedConfigItemPlugIn (String     sectionName,
                                         String     paramName,
                                         CurnConfig config,
                                         FeedInfo   feedInfo)
	throws CurnException
    {
        try
        {
            if (paramName.equals (VAR_SAVE_FEED_AS))
            {
                FeedSaveInfo saveInfo = getOrMakeFeedSaveInfo (feedInfo);
                String value = config.getConfigurationValue (sectionName,
                                                             paramName);
                saveInfo.saveAsFile = new File (value);
                saveInfo.sectionName = sectionName;
                log.debug ("[" + sectionName + "]: SaveAs=" + value);
            }

            else if (paramName.equals (VAR_SAVE_ONLY))
            {
                FeedSaveInfo saveInfo = getOrMakeFeedSaveInfo (feedInfo);
                saveInfo.saveOnly =
                    config.getOptionalBooleanValue (sectionName,
                                                    paramName,
                                                    false);
                saveInfo.sectionName = sectionName;
                log.debug ("[" + sectionName + "]: SaveOnly="
                         + saveInfo.saveOnly);
            }

            else if (paramName.equals (VAR_SAVE_AS_ENCODING))
            {
                FeedSaveInfo saveInfo = getOrMakeFeedSaveInfo (feedInfo);
                saveInfo.saveAsEncoding =
                    config.getConfigurationValue (sectionName, paramName);
                saveInfo.sectionName = sectionName;
                log.debug ("[" + sectionName + "]: SaveAsEncoding="
                         + saveInfo.saveAsEncoding);
            }
        }

        catch (ConfigurationException ex)
        {
            throw new CurnException (ex);
        }
    }

    /**
     * Called after the entire configuration has been read and parsed, but
     * before any feeds are processed. Intercepting this event is useful
     * for plug-ins that want to adjust the configuration. For instance,
     * the <i>curn</i> command-line wrapper intercepts this plug-in event
     * so it can adjust the configuration to account for command line
     * options.
     *
     * @param config  the parsed {@link CurnConfig} object
     * 
     * @throws CurnException on error
     *
     * @see CurnConfig
     */
    public void runPostConfigurationPlugIn (CurnConfig config)
	throws CurnException
    {
        this.config = config;

        for (FeedInfo feedInfo : perFeedSaveAsMap.keySet())
        {
            FeedSaveInfo saveInfo = perFeedSaveAsMap.get (feedInfo);
            
            if (saveInfo.saveOnly && (saveInfo.saveAsFile == null))
            {
                throw new CurnException
                    (Constants.BUNDLE_NAME,
                     "CurnConfig.saveOnlyButNoSaveAs",
                     "Configuration section \"{0}\": "
                   + "\"[1}\" may only be specified if \"{2}\" is set.",
                     new Object[]
                     {
                         saveInfo.sectionName,
                         VAR_SAVE_ONLY,
                         VAR_SAVE_FEED_AS
                     });
            }
        }
    }

    /**
     * Called just before a feed is downloaded. This method can return
     * <tt>false</tt> to signal <i>curn</i> that the feed should be skipped.
     * For instance, a plug-in that filters on feed URL could use this
     * method to weed out non-matching feeds before they are downloaded.
     *
     * @param feedInfo  the {@link FeedInfo} object for the feed to be
     *                  downloaded
     *
     * @return <tt>true</tt> if <i>curn</i> should continue to process the
     *         feed, <tt>false</tt> to skip the feed. A return value of
     *         <tt>false</tt> aborts all further processing on the feed.
     *         In particular, <i>curn</i> will not pass the feed along to
     *         other plug-ins that have yet to be notified of this event.
     *
     * @throws CurnException on error
     *
     * @see FeedInfo
     */
    public boolean runPreFeedDownloadPlugIn (FeedInfo feedInfo)
	throws CurnException
    {
        boolean processFeed = true;

        // If this is a download-only configuration, and there's no
        // save-as file, then we can skip this feed.
        
        if (config.isDownloadOnly())
        {
            FeedSaveInfo saveInfo = perFeedSaveAsMap.get (feedInfo);
            
            if ((saveInfo == null) || (saveInfo.saveAsFile == null))
            {
                log.debug ("Feed "
                         + feedInfo.getURL().toString()
                         + " has no SaveAs file, and this is a download-only "
                         + " run. Skipping feed.");
                processFeed = false;
            }
        }

        return processFeed;
    }

    /**
     * Called immediately after a feed is downloaded. This method can
     * return <tt>false</tt> to signal <i>curn</i> that the feed should be
     * skipped. For instance, a plug-in that filters on the unparsed XML
     * feed content could use this method to weed out non-matching feeds
     * before they are downloaded.
     *
     * @param feedInfo      the {@link FeedInfo} object for the feed that
     *                      has been downloaded
     * @param feedDataFile  the file containing the downloaded, unparsed feed 
     *                      XML. <b><i>curn</i> may delete this file after all
     *                      plug-ins are notified!</b>
     * @param encoding      the encoding used to store the data in the file,
     *                      or null for the default
     *
     * @return <tt>true</tt> if <i>curn</i> should continue to process the
     *         feed, <tt>false</tt> to skip the feed. A return value of
     *         <tt>false</tt> aborts all further processing on the feed.
     *         In particular, <i>curn</i> will not pass the feed along to
     *         other plug-ins that have yet to be notified of this event.
     *
     * @throws CurnException on error
     *
     * @see FeedInfo
     */
    public boolean runPostFeedDownloadPlugIn (FeedInfo feedInfo,
                                              File     feedDataFile,
                                              String   encoding)
	throws CurnException
    {
        boolean keepGoing = true;
        FeedSaveInfo saveInfo = perFeedSaveAsMap.get (feedInfo);

        if ((saveInfo != null) && (saveInfo.saveAsFile != null))
        {
            try
            {
                String s = ((encoding == null) ? "default" : encoding);
                log.debug ("Copying temporary file \""
                         + feedDataFile.getPath()
                         + "\" (encoding "
                         + s
                         + ") to \""
                         + saveInfo.saveAsFile.getPath()
                         + "\" (encoding "
                         + saveInfo.saveAsEncoding
                         + ")");
                
                FileUtil.copyTextFile (feedDataFile,
                                       encoding,
                                       saveInfo.saveAsFile,
                                       saveInfo.saveAsEncoding);
            }

            catch (IOException ex)
            {
                throw new CurnException ("Can't copy \""
                                       + feedDataFile.getPath()
                                       + "\" to \""
                                       + saveInfo.saveAsFile.getPath()
                                       + "\": ",
                                         ex);
            }

            keepGoing = ! saveInfo.saveOnly;;
        }

        return keepGoing;
    }

    /*----------------------------------------------------------------------*\
                              Private Methods
    \*----------------------------------------------------------------------*/

    private FeedSaveInfo getOrMakeFeedSaveInfo (FeedInfo feedInfo)
    {
        FeedSaveInfo saveInfo = perFeedSaveAsMap.get (feedInfo);
        if (saveInfo == null)
        {
            saveInfo = new FeedSaveInfo();
            perFeedSaveAsMap.put (feedInfo, saveInfo);
        }

        return saveInfo;
    }
}