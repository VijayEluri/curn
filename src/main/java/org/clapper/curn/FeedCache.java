/*---------------------------------------------------------------------------*\
  This software is released under a BSD license, adapted from
  <http://opensource.org/licenses/bsd-license.php>

  Copyright &copy; 2004-2010 Brian M. Clapper.
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


package org.clapper.curn;

import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.clapper.curn.parser.RSSItem;
import org.clapper.curn.parser.RSSLink;
import org.clapper.util.logging.Logger;

/**
 * Defines the in-memory format of the <i>curn</i> cache, and provides
 * methods for saving and restoring the cache.
 *
 * @see Curn
 * @see org.clapper.curn.parser.RSSChannel
 *
 * @version <tt>$Revision$</tt>
 */
public class FeedCache
{
    /*----------------------------------------------------------------------*\
                             Private Constants
    \*----------------------------------------------------------------------*/

    /*----------------------------------------------------------------------*\
                              Private Classes
    \*----------------------------------------------------------------------*/

    /*----------------------------------------------------------------------*\
                            Private Data Items
    \*----------------------------------------------------------------------*/

    /**
     * The configuration
     */
    private final CurnConfig config;

    /**
     * The actual cache, indexed by unique ID.
     */
    private Map<String,FeedCacheEntry> cacheByURL = null;

    /**
     * A list of feed entries, used only during load.
     */
    private List<FeedCacheEntry> loadedEntries =
        new LinkedList<FeedCacheEntry>();

    /**
     * Current time
     */
    private long currentTime = System.currentTimeMillis();

    /**
     * For log messages
     */
    private static final Logger log = new Logger (FeedCache.class);

    /*----------------------------------------------------------------------*\
                                Constructor
    \*----------------------------------------------------------------------*/

    /**
     * Construct a new, empty cache object.
     *
     * @param config  the <i>curn</i> configuration
     */
    FeedCache (CurnConfig config)
    {
        this.config = config;
    }

    /*----------------------------------------------------------------------*\
                              Public Methods
    \*----------------------------------------------------------------------*/

    /**
     * Determine whether the cache contains the specified URL.
     *
     * @param url  the URL to check. This method normalizes it.
     *
     * @return <tt>true</tt> if the ID is present in the cache,
     *         <tt>false</tt> if not
     */
    public boolean containsURL(final URL url)
    {
        boolean hasURL = false;

        if (cacheByURL != null)
        {
            String  urlKey = CurnUtil.urlToLookupKey(url);
            hasURL = cacheByURL.containsKey(urlKey);
            log.debug("Cache contains \"" + urlKey + "\"? " + hasURL);
        }

        return hasURL;
    }

    /**
     * Get an entry from the cache by its URL.
     *
     * @param url the URL
     *
     * @return the corresponding <tt>FeedCacheEntry</tt> object, or null if
     *         not found
     */
    public FeedCacheEntry getEntryByURL(final URL url)
    {
        FeedCacheEntry result = null;

        if (cacheByURL != null)
            result = cacheByURL.get(CurnUtil.urlToLookupKey(url));

        return result;
    }

    /**
     * Get an entry for an {@link RSSItem} from the cache. This method
     * attempts to find the item by its unique ID. If the item has no ID,
     * then this method attempts to find the item by its URL.
     *
     * @param item the {@link RSSItem} to find in the cache
     *
     * @return the corresponding {@link FeedCacheEntry} object, or null if
     *         not found
     */
    public FeedCacheEntry getEntryForItem(RSSItem item)
    {
        FeedCacheEntry entry    = null;
        RSSLink        itemLink = item.getURL();
        URL            itemURL  = null;

        if (itemLink != null)
        {
            itemURL = itemLink.getURL();
            if (itemURL != null)
                itemURL = CurnUtil.normalizeURL(itemURL);
        }

        if (itemURL == null)
            log.info("Item has no URL. Ignoring it.");

        else
        {
            log.debug("Locating item by URL: " + itemURL.toString());
            entry = getEntryByURL(itemURL);
        }

        return entry;
    }

    /**
     * Add (or replace) a cached URL.
     *
     * @param url        the URL to cache. May be an individual item URL, or
     *                   the URL for an entire feed.
     * @param pubDate    the publication date, if known; or null
     * @param parentFeed the associated feed
     *
     * @see CurnUtil#normalizeURL
     */
    public void addToCache(final URL      url,
                           final Date     pubDate,
                           final FeedInfo parentFeed)
    {
        synchronized (this)
        {
            if (cacheByURL == null)
                cacheByURL = new HashMap<String,FeedCacheEntry>();
        }

        URL parentURL = parentFeed.getURL();
        FeedCacheEntry entry = new FeedCacheEntry(parentURL,
                                                  url,
                                                  pubDate,
                                                  System.currentTimeMillis());

        log.debug ("Adding cache entry for URL \"" +
                   entry.getEntryURL().toExternalForm() +
                   "\", channel URL: \"" +
                   entry.getChannelURL().toExternalForm() +
                   "\"");

        cacheByURL.put(CurnUtil.urlToLookupKey(url), entry);
    }

    /**
     * Get all entries in the cache, in no particular order.
     *
     * @return a <tt>Collection</tt> of entries
     */
    public Collection<FeedCacheEntry> getAllEntries()
    {
        Collection<FeedCacheEntry> result = null;

        if (cacheByURL != null)
            result = Collections.unmodifiableCollection(cacheByURL.values());
        else
            result = new ArrayList<FeedCacheEntry>();

        return result;
    }

    /**
     * Set the cache's notion of the current time, which affects how elements
     * are pruned when loaded from the cache. Only meaningful if set before
     * the <tt>load()</tt> method is called. If this method is never
     * called, then the cache uses the current time.
     *
     * @param datetime  the time to use
     */
    public void setCurrentTime (final Date datetime)
    {
        this.currentTime = datetime.getTime();
    }

    /*----------------------------------------------------------------------*\
                            Package-visible Methods
    \*----------------------------------------------------------------------*/

    /**
     * Add a {@link FeedCacheEntry} to the cache. This method exists primarily
     * for use during deserialization of the cache.
     *
     * @param entry  the entry
     */
    void loadFeedCacheEntry(FeedCacheEntry entry)
    {
        // Load onto the end of a list, for speed. pruneCache()
        // will sift through the list when we're done.

        loadedEntries.add(entry);
   }

    /**
     * Signify that the cache is finished loading (i.e., that all calls to
     * loadFeedCacheEntry are done).
     */
    void optimizeAfterLoad()
    {
        pruneCache();
    }

    /*----------------------------------------------------------------------*\
                              Private Methods
    \*----------------------------------------------------------------------*/

    /**
     * Prune the loaded cache of out-of-date data.
     */
    private void pruneCache()
    {
        log.debug ("PRUNING CACHE");
        log.debug ("Cache's notion of current time: " +
                   new Date (currentTime));
        Map<URL,FeedInfo> feedInfoMap = config.getFeedInfoMap();

        int maxEntries = loadedEntries.size();
        if (maxEntries == 0)
            maxEntries = 100;

        // Use default load factor (0.75), and rely on the HashMap class's
        // documented behavior: "If the initial capacity is greater than the
        // maximum number of entries divided by the load factor, no rehash
        // operations will ever occur."

        int initialCapacity = (int) (((float) maxEntries) / 0.75f);

        log.debug("HashMap sizing: Max entries=" + maxEntries + ", " +
                  "initialCapacity=" +  initialCapacity);
        cacheByURL = new HashMap<String,FeedCacheEntry>(initialCapacity);

        for (FeedCacheEntry entry : loadedEntries)
        {
            boolean removed = false;
            URL channelURL = entry.getChannelURL();
            String itemKey = entry.getEntryURL().toString();

            if (log.isDebugEnabled())
                dumpCacheEntry (itemKey, entry, "");

            FeedInfo feedInfo = feedInfoMap.get(channelURL);

            if (feedInfo == null)
            {
                // Cached URL no longer corresponds to a configured site
                // URL. Kill it.

                log.debug ("Cached item \"" + itemKey +
                           "\", with base URL \"" + channelURL.toString() +
                           "\" no longer corresponds to a configured feed. " +
                           "Tossing it.");
                removed = true;
            }

            else
            {
                long timestamp  = entry.getTimestamp();
                long maxCacheMS = feedInfo.getMillisecondsToCache();
                long expires    = timestamp + maxCacheMS;

                if (log.isDebugEnabled())
                {
                    log.debug ("    Cache time: " + feedInfo.getDaysToCache() +
                               " days (" + maxCacheMS + " ms)");
                    log.debug ("    Expires: " +
                               new Date (expires).toString());
                }

                if (timestamp > currentTime)
                {
                    log.debug ("Cache time for item \"" + itemKey +
                               "\" is in the future, relative to cache's " +
                               "notion of current time. Setting its " +
                               "timestamp to the current time.");
                    entry.setTimestamp (currentTime);
                }

                else if (expires < currentTime)
                {
                    log.debug ("Cache time for item \"" + itemKey +
                               "\" has expired. Deleting cache entry.");
                    removed = true;
                }
            }

            if (! removed)
            {
                // Add to URL cache.

                URL url = CurnUtil.normalizeURL(entry.getEntryURL());
                String strURL = url.toString();
                log.debug("Loading entry for URL \"" + strURL +
                          "\" into in-memory URL lookup cache.");
                cacheByURL.put(strURL, entry);
                log.debug("Loading entry for URL \"" + strURL +
                          "\" into in-memory ID lookup cache.");
            }
        }

        log.debug("Cache now has " + cacheByURL.size() + " elements.");
        log.debug("DONE PRUNING CACHE");
    }

    /**
     * Dump a single cache entry via the "debug" log facility.
     *
     * @param itemKey the hash table key for the item
     * @param entry   the cache entry
     * @param indent  string to use to indent output, if desired
     */
    private void dumpCacheEntry (final Object         itemKey,
                                 final FeedCacheEntry entry,
                                       String         indent)
    {
        long timestamp  = entry.getTimestamp();

        if (indent == null)
            indent = "";

        log.debug (indent + "Cached item \"" + itemKey.toString() + "\"");
        log.debug (indent + "    Item URL: " + entry.getEntryURL().toString());
        log.debug (indent + "    Channel URL: " +
                   entry.getChannelURL().toString());
        log.debug (indent + "    Cached on: " +
                   new Date (timestamp).toString());
    }

}
