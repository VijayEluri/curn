/*---------------------------------------------------------------------------*\
  This software is released under a BSD license, adapted from
  <http://opensource.org/licenses/bsd-license.php>

  Copyright &copy; 2010 Dow Jones & Co. All rights reserved.

  ---------------------------------------------------------------------------
  NOTE: This plug-in was funded by Dow Jones & Co., under contract with
  ArdenTex, Inc.  Dow Jones and Co. and ArdenTex, Inc. have donated the
  resulting code back to the Curn project, as open source.
  ---------------------------------------------------------------------------

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

import org.clapper.curn.Constants;
import org.clapper.curn.CurnConfig;
import org.clapper.curn.CurnException;
import org.clapper.curn.FeedInfo;
import org.clapper.curn.FeedConfigItemPlugIn;
import org.clapper.curn.PostConfigPlugIn;

import org.clapper.util.classutil.ClassUtil;
import org.clapper.util.config.ConfigurationException;
import org.clapper.util.logging.Logger;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;

import java.net.MalformedURLException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import org.clapper.curn.CurnUtil;
import org.clapper.curn.FeedCache;
import org.clapper.curn.PostFeedProcessPlugIn;
import org.clapper.curn.parser.RSSChannel;
import org.clapper.curn.parser.RSSFeedType;
import org.clapper.util.cmdline.CommandLineUsageException;
import org.clapper.util.cmdline.ParameterHandler;
import org.clapper.util.cmdline.ParameterParser;
import org.clapper.util.cmdline.UsageInfo;
import org.clapper.util.io.IOExceptionExt;
import org.clapper.util.text.TextUtil;

import org.jdom.JDOMException;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.Attribute;
import org.jdom.output.XMLOutputter;

/**
 * <p>The <tt>PruneOriginalRSSPlugIn</tt> is a special-case variant of the
 * {@link SaveAsRSSPlugIn}: It saves a copy of the original RSS document
 * after removing any already-seen items. This plug-in differs from the
 * {@link SaveAsRSSPlugIn}, however, because the
 * <tt>PruneOriginalRSSPlugIn</tt> works directly off a JDOM version of the
 * RSS document (i.e., at the XML level), which means it's less likely to
 * drop non-standard XML elements from the RSS feed, since the edited DOM
 * does not go through the ROME RSS parser.</p>
 *
 * <p><b>NOTE</b>: This plug-in was funded by Dow Jones & Co., under
 * contract with <a href="http://www.ardentex.com/">ArdenTex, Inc.</a> Dow
 * Jones & Co. and ArdenTex, Inc. have donated the resulting code back to
 * the Curn project, as open source.</p>
 *
 * <p>Note: If this plug-in is used in conjunction with the
 * {@link RawFeedSaveAsPlugIn} class, and the {@link RawFeedSaveAsPlugIn}
 * class's <tt>SaveOnly</tt> parameter is specified, this plug-in will
 * <i>not</i> be invoked.</p>
 *
 * @version <tt>$Revision$</tt>
 */
public class PruneOriginalRSSPlugIn
    implements FeedConfigItemPlugIn,
               PostConfigPlugIn,
               PostFeedProcessPlugIn
{
    /*----------------------------------------------------------------------*\
                             Private Constants
    \*----------------------------------------------------------------------*/

    private static final String VAR_PRUNE_TO_FILE   = "PruneOriginalRSSTo";
    private static final String VAR_PRUNE_ONLY      = "PruneOriginalRSSOnly";

    /*----------------------------------------------------------------------*\
                              Private Classes
    \*----------------------------------------------------------------------*/

    /**
     * Feed save info
     */
    class PruneInfo
    {
        String  sectionName;
        File    pruneToFile;
        boolean pruneOnly;
        String  outputEncoding = "utf-8";
        int backups = 0;

        PruneInfo()
        {
            // Nothing to do
        }
    }

    /*----------------------------------------------------------------------*\
                            Private Data Items
    \*----------------------------------------------------------------------*/

    /**
     * Feed save data, by feed
     */
    private Map<FeedInfo,PruneInfo> perFeedSaveAsMap =
        new HashMap<FeedInfo,PruneInfo>();

    /**
     * Saved reference to the configuration
     */
    private CurnConfig config = null;

    /**
     * For log messages
     */
    private static final Logger log = new Logger(PruneOriginalRSSPlugIn.class);

    /*----------------------------------------------------------------------*\
                                Constructor
    \*----------------------------------------------------------------------*/

    /**
     * Default constructor (required).
     */
    public PruneOriginalRSSPlugIn()
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
        return "Prune Original RSS";
    }

    /**
     * Get the sort key for this plug-in.
     *
     * @return the sort key string.
     */
    public String getPlugInSortKey()
    {
        return ClassUtil.getShortClassName (getClass().getName());
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
            if (paramName.equals (VAR_PRUNE_TO_FILE))
            {
                handlePruneToConfigParam(sectionName,
                                         paramName,
                                         config,
                                         feedInfo);
            }

            else if (paramName.equals (VAR_PRUNE_ONLY))
            {
                PruneInfo pruneInfo = getOrMakePruneInfo(feedInfo);
                pruneInfo.pruneOnly =
                    config.getOptionalBooleanValue(sectionName,
                                                   paramName,
                                                   false);
                pruneInfo.sectionName = sectionName;
                log.debug("[" + sectionName + "]: " +
                          "PruneOriginalRSSTo.pruneOnly=" +
                          pruneInfo.pruneOnly);
            }

            return true;
        }

        catch (ConfigurationException ex)
        {
            throw new CurnException(ex);
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
    public void runPostConfigPlugIn(CurnConfig config)
        throws CurnException
    {
        this.config = config;

        for (FeedInfo feedInfo : perFeedSaveAsMap.keySet())
        {
            PruneInfo pruneInfo = perFeedSaveAsMap.get(feedInfo);

            if (pruneInfo.pruneOnly && (pruneInfo.pruneToFile == null))
            {
                throw new CurnException
                    (Constants.BUNDLE_NAME,
                     "CurnConfig.saveOnlyButNoSaveAs",
                     "Configuration section \"{0}\": " +
                     "\"[1}\" may only be specified if \"{2}\" is set.",
                     new Object[]
                     {
                         pruneInfo.sectionName,
                         VAR_PRUNE_ONLY,
                         VAR_PRUNE_TO_FILE
                     });
            }
        }
    }

    /**
     * <p>Called just after the feed has been parsed, but before it is
     * otherwise processed.
     *
     * @param feedInfo  the {@link FeedInfo} object for the feed
     * @param feedCache the feed cache
     * @param channel   the parsed feed data
     *
     * @return <tt>true</tt> if <i>curn</i> should continue to process the
     *         feed, <tt>false</tt> to skip the feed
     *
     * @throws CurnException on error
     *
     * @see FeedInfo
     * @see RSSChannel
     */
    public boolean runPostFeedProcessPlugIn(final FeedInfo   feedInfo,
                                            final FeedCache  feedCache,
                                            final RSSChannel channel)
        throws CurnException
    {
        boolean keepGoing = true;
        PruneInfo pruneInfo = perFeedSaveAsMap.get(feedInfo);

        if ((pruneInfo != null) && (pruneInfo.pruneToFile != null))
        {
            Document dom = channel.getDOM();
            int itemsLeft = pruneDOM(dom, channel);

            if (itemsLeft > 0)
            {
                log.debug("There are items left in the DOM. Writing it.");

                XMLOutputter outputter = new XMLOutputter();
                try
                {
                    log.debug("Generating RSS output file \"" +
                              pruneInfo.pruneToFile + "\" using encoding " +
                              pruneInfo.outputEncoding);
                    log.debug("Backups to keep: " + pruneInfo.backups);

                    Writer out = CurnUtil.openOutputFile(
                        pruneInfo.pruneToFile,
                        pruneInfo.outputEncoding,
                        CurnUtil.IndexMarker.BEFORE_EXTENSION,
                        pruneInfo.backups
                    );

                    outputter.output(dom, out);
                    out.close();
                }

                catch (IOExceptionExt ex)
                {
                    throw new CurnException ("Can't write RSS output to \"" +
                                             pruneInfo.pruneToFile + "\": ",
                                             ex);
                }

                catch (IOException ex)
                {
                    throw new CurnException ("Can't write RSS output to \"" +
                                             pruneInfo.pruneToFile + "\": ",
                                             ex);
                }
            }

            keepGoing = ! pruneInfo.pruneOnly;
        }

        return keepGoing;
    }

    /*----------------------------------------------------------------------*\
                              Private Methods
    \*----------------------------------------------------------------------*/

    private int pruneDOM(Document dom, RSSChannel channel)
        throws CurnException
    {
        try
        {
            int result = 0;
            switch (channel.getFeedType())
            {
                case RSS_1:
                    result = pruneRSS1DOM(dom, channel);
                    break;

                case RSS_2:
                case RSS_0_9:
                    result = pruneRSS2DOM(dom, channel);
                    break;

                case ATOM:
                    result = pruneAtomDOM(dom, channel);
                    break;

                default:
                    assert(false);
            }

            log.debug("After pruning DOM, there are " + result + " items left");
            return result;
        }

        catch (JDOMException ex)
        {
            throw new CurnException(ex);
        }
    }

    private int pruneRSS1DOM(Document dom, RSSChannel channel)
        throws JDOMException, CurnException
    {
        // Any items will be children of the root node.

        int itemsLeft = 0;
        for (Element element : namedChildren(dom.getRootElement(), "item"))
        {
            // This is an item. Get the URL children. Don't use
            // getChild(name); it's not reliable.

            for (Element link : namedChildren(element, "link"))
            {
                String url = link.getText();
                if (shouldPruneItem(url, channel))
                {
                    log.debug("RSS1 item \"" + url + "\" has been seen " +
                              "already. Removing it from the DOM.");
                    element.getParent().removeContent(element);
                }

                else
                {
                    log.debug("RSS1 item \"" + url + "\" has not been seen " +
                              "yet. Keeping it in the DOM.");
                    itemsLeft++;
                }
            }
        }

        return itemsLeft;
    }

    private int pruneRSS2DOM(Document dom, RSSChannel channel)
        throws JDOMException, CurnException
    {
        // Any elements will be below the only channel node:
        //    <rss> <channel> <item> ...

        int itemsLeft = 0;
        Element root = dom.getRootElement();
        if (! elementNameMatches(root, "rss"))
        {
            log.error("Root element of alleged RSS 2 feed is \"" +
                      root.getName() + "\", not the expected <rss>");
            return 0;
        }

        List<?> children = root.getChildren();
        if (children.size() == 0)
        {
            log.error("There are no <channel> elements in the RSS 2 feed.");
            return 0;
        }

        if (children.size() > 1)
        {
            log.error("There are multiple <channel> elements in the RSS 2 " +
                      "feed.");
            return 0;
        }

        Element channelElem = (Element) children.get(0);

        for (Element element : namedChildren(channelElem, "item"))
        {
            // This is an item. Get the URL children. Don't use
            // getChild(name); it's not reliable.

            for (Element link : namedChildren(element, "link"))
            {
                String url = link.getText();
                if (shouldPruneItem(url, channel))
                {
                    log.debug("RSS2 item \"" + url + "\" has been seen " +
                              "already. Removing it from the DOM.");
                    element.getParent().removeContent(element);
                }

                else
                {
                    log.debug("RSS2 item \"" + url + "\" has not been seen " +
                              "yet. Keeping it in the DOM.");
                    itemsLeft++;
                }
            }
        }

        return itemsLeft;
    }

    private int pruneAtomDOM(Document dom, RSSChannel channel)
        throws JDOMException, CurnException
    {
        // Any items will be children of the root node.

        int itemsLeft = 0;

        for (Element element : namedChildren(dom.getRootElement(), "entry"))
        {
            // This is an entry. Get the URL children. Don't use
            // getChild(name); it's not reliable.

            for (Element link : namedChildren(element, "link"))
            {
                Attribute urlAttr = link.getAttribute("href");
                if (urlAttr == null)
                    continue;

                String url = urlAttr.getValue();
                if (shouldPruneItem(url, channel))
                {
                    log.debug("Atom item \"" + url + "\" has been seen " +
                              "already. Removing it from the DOM.");
                    element.getParent().removeContent(element);
                }

                else
                {
                    log.debug("Atom item \"" + url + "\" has not been seen " +
                              "yet. Keeping it in the DOM.");
                    itemsLeft++;
                }
            }
        }

        return itemsLeft;
    }

    private boolean shouldPruneItem(String itemURL, RSSChannel channel)
        throws CurnException
    {
        return (itemURL != null) && (! channelHasItem(channel, itemURL.trim()));
    }

    private boolean channelHasItem(RSSChannel channel, String link)
        throws CurnException
    {
        try
        {
            String adjLink = CurnUtil.normalizeURL(link).toExternalForm();
            return channel.hasItem(adjLink);
        }

        catch (MalformedURLException ex)
        {
            throw new CurnException(ex);
        }
    }

    private boolean elementNameMatches(Element element, String name)
    {
        String elemName = element.getName().toLowerCase();

        // Allow for namespaces.
        return (elemName.equals(name) || elemName.endsWith(":" + name));
    }

    private List<Element> namedChildren(Element element, String name)
    {
        List<Element> result = new ArrayList<Element>();
        Iterator<?> children = element.getChildren().iterator();

        while (children.hasNext())
        {
            Element child = (Element) children.next();
            if (elementNameMatches(child, name))
                result.add(child);
        }

        return result;
    }

    private PruneInfo getOrMakePruneInfo (FeedInfo feedInfo)
    {
        PruneInfo pruneInfo = perFeedSaveAsMap.get(feedInfo);
        if (pruneInfo == null)
        {
            pruneInfo = new PruneInfo();
            perFeedSaveAsMap.put(feedInfo, pruneInfo);
        }

        return pruneInfo;
    }

    private void handlePruneToConfigParam(final String     sectionName,
                                          final String     paramName,
                                          final CurnConfig config,
                                          final FeedInfo   feedInfo)
        throws CurnException,
               ConfigurationException
    {
        final PruneInfo pruneInfo = getOrMakePruneInfo(feedInfo);

        // Parse the value as a command line.

        UsageInfo usageInfo = new UsageInfo();
        usageInfo.addOption('b', "backups", "<n>",
                            "Number of backups to keep");
        usageInfo.addOption('e', "encoding", "<encoding>",
                            "Desired output encoding");
        usageInfo.addParameter("<path>", "Path to RSS output file", true);

        // Inner class for handling command-line syntax of the value.

        class ConfigParameterHandler implements ParameterHandler
        {
            private String rawValue;

            ConfigParameterHandler(String rawValue)
            {
                this.rawValue = rawValue;
            }

            public void parseOption(char             shortOption,
                                    String           longOption,
                                    Iterator<String> it)
                throws CommandLineUsageException,
                       NoSuchElementException
            {
                String value;
                switch (shortOption)
                {
                    case 'b':
                        value = it.next();
                        try
                        {
                            pruneInfo.backups = Integer.parseInt(value);
                        }

                        catch (NumberFormatException ex)
                        {
                            throw new CommandLineUsageException
                                ("Section [" + sectionName +
                                 "], parameter \"" + paramName + "\": " +
                                 "Unexpected non-numeric value \"" + value +
                                 "\" for \"" +
                                  UsageInfo.SHORT_OPTION_PREFIX + shortOption +
                                  "\" option.");
                        }
                        break;

                    case 'e':
                        pruneInfo.outputEncoding = it.next();
                        break;

                    default:
                        throw new CommandLineUsageException
                            ("Section [" + sectionName +
                             "], parameter \"" + paramName + "\": " +
                             "Unknown option \"" +
                             UsageInfo.SHORT_OPTION_PREFIX + shortOption +
                            "\" in value \"" + rawValue + "\"");
                }
            }

            public void parsePostOptionParameters(Iterator<String> it)
                throws CommandLineUsageException,
                       NoSuchElementException
            {
                pruneInfo.pruneToFile = 
                    CurnUtil.mapConfiguredPathName(it.next());
            }
        };

        // Parse the parameters.

        ParameterParser paramParser = new ParameterParser(usageInfo);
        String rawValue = config.getConfigurationValue(sectionName, paramName);
        try
        {
            String[] valueTokens = config.getConfigurationTokens(sectionName,
                                                                 paramName);
            if (log.isDebugEnabled())
            {
                log.debug("[" + sectionName + "]: PruneOriginalRSS: value=\"" +
                          rawValue + "\", tokens=" +
                          TextUtil.join(valueTokens, '|'));
            }

            ConfigParameterHandler handler = 
                new ConfigParameterHandler(rawValue);
            log.debug("Parsing value \"" + rawValue + "\"");
            paramParser.parse(valueTokens, handler);
        }

        catch (CommandLineUsageException ex)
        {
            throw new CurnException("Section [" + sectionName +
                                    "], parameter \"" + paramName +
                                    "\": Error parsing value \"" + rawValue +
                                    "\"",
                                    ex);
        }
    }
}

