/*---------------------------------------------------------------------------*\
  $Id$
\*---------------------------------------------------------------------------*/

package org.clapper.rssget.parser;

import java.net.URL;
import java.io.IOException;

/**
 * This interface defines a simplified view of an RSS channel, providing
 * only the methods necessary for <i>rssget</i> to work. <i>rssget</i> uses
 * the {@link RSSParserFactory} class to get a specific implementation of
 * an <tt>RSSParser</tt>. This strategy isolates the bulk of the code from
 * the underlying RSS parser, making it easier to substitute different
 * parsers as more of them become available.
 *
 * @see RSSParserFactory
 * @see RSSChannel
 * @see RSSItem
 *
 * @version <tt>$Revision$</tt>
 */
public interface RSSParser
{
    /*----------------------------------------------------------------------*\
                              Public Methods
    \*----------------------------------------------------------------------*/

    /**
     * Parse an RSS feed.
     *
     * @param url  The URL for the feed
     *
     * @return an <tt>RSSChannel</tt> object representing the RSS data from
     *         the site.
     *
     * @throws IOException        unable to read from URL
     * @throws RSSParserException unable to parse RSS XML
     */
    public RSSChannel parseRSSFeed (URL url)
        throws IOException,
               RSSParserException;
}
