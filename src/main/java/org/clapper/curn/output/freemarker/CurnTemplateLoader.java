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


package org.clapper.curn.output.freemarker;

import org.clapper.curn.CurnException;

import org.clapper.util.logging.Logger;

import freemarker.cache.TemplateLoader;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.Reader;

import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;

/**
 * @version <tt>$Revision$</tt>
 */
public class CurnTemplateLoader implements TemplateLoader
{
    /*----------------------------------------------------------------------*\
                             Private Constants
    \*----------------------------------------------------------------------*/

    /*----------------------------------------------------------------------*\
                            Private Data Items
    \*----------------------------------------------------------------------*/

    /**
     * For logging
     */
    private static final Logger log = new Logger (CurnTemplateLoader.class);

    /*----------------------------------------------------------------------*\
                                Constructor
    \*----------------------------------------------------------------------*/

    /**
     * Construct a new <tt>CurnTemplateLoader</tt> object.
     */
    CurnTemplateLoader()
    {
        // Nothing to do.
    }

    /*----------------------------------------------------------------------*\
            Public Methods Required by TemplateLoader Interface
    \*----------------------------------------------------------------------*/

    /**
     * Finds the object that acts as the source of the template with the
     * given name. According to FreeMarker documentation, this method is
     * called by the TemplateCache when a template is requested, before
     * calling either {@link #getLastModified(Object)} or
     * {@link #getReader(Object, String)}.
     *
     * @param name the name of the template, already localized and normalized
     *
     * @return an object representing the template source, which can be
     *         supplied in subsequent calls to {@link #getLastModified(Object)}
     *         and {@link #getReader(Object, String)}. Null will be returned
     *         if the source for the template can not be found.
     *
     * @throws IOException on error
     */
    public Object findTemplateSource (final String name)
        throws IOException
    {
        try
        {
            return new TemplateLocation (name);
        }

        catch (CurnException ex)
        {
            log.error ("Failed to decode template location name \"" +
                       name + "\"",
                       ex);
            throw new IOException (ex.toString());
        }
    }

    /**
     * Returns the time of last modification of the specified template source.
     * This method is called after {@link #findTemplateSource(String)}.
     *
     * @param templateSource an object representing a template source,
     *                       obtained through a prior call to
     *                       {@link #findTemplateSource(String)}.
     *
     * @return the time of last modification of the specified template source,
     *         or -1 if the time is not known.
     */
    public long getLastModified (final Object templateSource)
    {
        long              result = -1;
        TemplateLocation  tl = (TemplateLocation) templateSource;
        URL               url = null;

        switch (tl.getType())
        {
            case URL:
                try
                {
                    url = new URL (tl.getLocation());
                }

                catch (MalformedURLException ex)
                {
                    log.error (ex);
                }
                break;

            case CLASSPATH:
                ClassLoader classLoader = this.getClass().getClassLoader();
                url = classLoader.getResource (tl.getLocation());
                break;

            case FILE:
                File file = new File (tl.getLocation());
                try
                {
                    url = file.toURI().toURL();
                }

                catch (MalformedURLException ex)
                {
                    log.error (ex);
                }
                break;

            default:
                assert (false);
        }

        if (url != null)
        {
            try
            {
                URLConnection conn = url.openConnection();
                result = conn.getLastModified();
                if (result == 0)
                    result = -1;
            }

            catch (IOException ex)
            {
                log.error (ex);
            }
        }

        return result;
    }

    /**
     * Returns the character stream of a template represented by the specified
     * template source. This method is called after {@link #getLastModified}
     * if it is determined that a cached copy of the template is unavailable
     * or stale.
     *
     * @param templateSource an object representing a template source, obtained
     *                       through a prior call to
                             {@link #findTemplateSource(String)}.
     * @param encoding       the character encoding used to translate source
     *                       bytes to characters.

     * @return a <tt>Reader</tt> representing the template character stream.
     *
     * @throws IOException if an I/O error occurs while accessing the stream.
     */
    public Reader getReader (final Object templateSource,
                             final String encoding)
        throws IOException
    {
        TemplateLocation  tl = (TemplateLocation) templateSource;
        URL               url = null;
        Reader            result = null;

        log.debug ("Getting reader for template location: " + tl.toString());
        switch (tl.getType())
        {
            case URL:
                try
                {
                    url = new URL (tl.getLocation());
                }

                catch (MalformedURLException ex)
                {
                    log.error (ex);
                }
                break;

            case CLASSPATH:
                ClassLoader classLoader = this.getClass().getClassLoader();
                url = classLoader.getResource (tl.getLocation());
                break;

            case FILE:
                File file = new File (tl.getLocation());
                try
                {
                    url = file.toURI().toURL();
                }

                catch (MalformedURLException ex)
                {
                    log.error (ex);
                }
                break;

            default:
                assert (false);
        }

        if (url == null)
        {
            throw new IOException ("Unable to locate template file: " +
                                   tl.toString());
        }

        log.debug ("Opening template " + url.toString());

        InputStream is = url.openStream();
        if (encoding == null)
            result = new InputStreamReader (is);
        else
            result = new InputStreamReader (is, encoding);

        return result;
    }

    /**
     * Closes the template source. This is the last method that is called
     * by the FreeMarker <tt>TemplateCache</tt> for a template source. The
     * framework guarantees that this method will be called on every object
     * that is returned from {@link #findTemplateSource(String)}.
     *
     * @param templateSource the template source that should be closed.
     */
    public void closeTemplateSource (final Object templateSource)
        throws IOException
    {
        // Must be provided, but there's nothing to do here.
    }
}
