/*---------------------------------------------------------------------------*\
  $Id$
\*---------------------------------------------------------------------------*/

package org.clapper.rssget.parser.minirss;

import org.clapper.rssget.parser.RSSItem;

import java.net.URL;
import java.net.MalformedURLException;

import java.util.Collection;
import java.util.ArrayList;
import java.util.Date;

/**
 *
 * @version <tt>$Revision$</tt>
 */
public class Item implements RSSItem
{
    /*----------------------------------------------------------------------*\
                           Private Instance Data
    \*----------------------------------------------------------------------*/

    private String      title       = null;
    private URL         url         = null;
    private String      description = null;
    private Date        pubDate     = null;
    private Collection  categories  = null;
    private String      author      = null;

    /*----------------------------------------------------------------------*\
                              Public Methods
    \*----------------------------------------------------------------------*/

    Item()
    {
    }

    /*----------------------------------------------------------------------*\
                              Methods
    \*----------------------------------------------------------------------*/

    /**
     * Set the item's title
     *
     * @param title the item's title, or null if there isn't one
     */
    public void setTitle (String title)
    {
        this.title = title;
    }

    /**
     * Get the item's title
     *
     * @return the item's title, or null if there isn't one
     */
    public String getTitle()
    {
        return title;
    }

    /**
     * Get the item's published URL.
     *
     * @return the URL, or null if not available
     */
    public URL getLink()
    {
        return url;
    }

    /**
     * Set the item's published URL.
     *
     * @param url  the URL, as a string
     */
    public void setLink (URL url)
    {
        this.url = url;
    }

    /**
     * Get the item's description.
     *
     * @return the description, or null if not available
     */
    public String getDescription()
    {
        return description;
    }

    /**
     * Set the item's description.
     *
     * @param desc the description, or null if not available
     */
    public void setDescription (String desc)
    {
        this.description = desc;
    }

    /**
     * Get the item's author.
     *
     * @return the author, or null if not available
     */
    public String getAuthor()
    {
        return author;
    }

    /**
     * Set the item's author.
     *
     * @param author the author, or null if not available
     */
    public void setAuthor (String author)
    {
        this.author = author;
    }

    /**
     * Get the item's publication date.
     *
     * @return the date, or null if not available
     */
    public Date getPublicationDate()
    {
        return pubDate;
    }

    /**
     * Set the item's publication date.
     *
     * @param date the date, or null if not available
     */
    public void setPublicationDate (Date date)
    {
        this.pubDate = date;
    }

    /**
     * Add a category to this item.
     *
     * @param category  the category string
     */
    public void addCategory (String category)
    {
        if (categories == null)
            categories = new ArrayList();

        categories.add (category);
    }
    
    /**
     * Get the categories the item belongs to.
     *
     * @return a <tt>Collection</tt> of category strings (<tt>String</tt>
     *         objects) or null if not applicable
     */
    public Collection getCategories()
    {
        return categories;
    }
}
