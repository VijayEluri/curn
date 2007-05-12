/*---------------------------------------------------------------------------*\
  $Id$
  ---------------------------------------------------------------------------
  This software is released under a BSD-style license:

  Copyright (c) 2004-2007 Brian M. Clapper. All rights reserved.

  Redistribution and use in source and binary forms, with or without
  modification, are permitted provided that the following conditions are
  met:

  1.  Redistributions of source code must retain the above copyright notice,
      this list of conditions and the following disclaimer.

  2.  The end-user documentation included with the redistribution, if any,
      must include the following acknowlegement:

        "This product includes software developed by Brian M. Clapper
        (bmc@clapper.org, http://www.clapper.org/bmc/). That software is
        copyright (c) 2004-2007 Brian M. Clapper."

      Alternately, this acknowlegement may appear in the software itself,
      if wherever such third-party acknowlegements normally appear.

  3.  Neither the names "clapper.org", "clapper.org Java Utility Library",
      nor any of the names of the project contributors may be used to
      endorse or promote products derived from this software without prior
      written permission. For written permission, please contact
      bmc@clapper.org.

  4.  Products derived from this software may not be called "clapper.org
      Java Utility Library", nor may "clapper.org" appear in their names
      without prior written permission of Brian M. Clapper.

  THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED
  WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
  MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN
  NO EVENT SHALL BRIAN M. CLAPPER BE LIABLE FOR ANY DIRECT, INDIRECT,
  INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
  NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
  DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
  THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
  (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
  THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
\*---------------------------------------------------------------------------*/

package org.clapper.curn.plugins;

import org.clapper.curn.CurnException;
import org.clapper.curn.FeedInfo;
import org.clapper.curn.CurnUtil;

import org.clapper.util.io.FileUtil;
import org.clapper.util.logging.Logger;
import org.clapper.util.regex.RegexUtil;
import org.clapper.util.regex.RegexException;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;

import java.util.List;

/**
 * Useful common base class for plug-ins that perform regular expression-based
 * edits on raw XML.
 *
 * @version <tt>$Revision$</tt>
 */
public abstract class AbstractXMLEditPlugIn
{
    /*----------------------------------------------------------------------*\
                           Private Instance Data
    \*----------------------------------------------------------------------*/

    /**
     * Regular expression helper
     */
    private RegexUtil regexUtil = new RegexUtil();

    /*----------------------------------------------------------------------*\
                                Constructor
    \*----------------------------------------------------------------------*/

    /**
     * Default constructor (required).
     */
    protected AbstractXMLEditPlugIn()
    {
        // Nothing to do
    }

    /*----------------------------------------------------------------------*\
                              Public Methods
    \*----------------------------------------------------------------------*/

    /*----------------------------------------------------------------------*\
                             Protected Methods
    \*----------------------------------------------------------------------*/

    /**
     * Get the <tt>Logger</tt> object created for this object.
     *
     * @return the <tt>Logger</tt>
     */
    protected abstract Logger getLogger();

    /**
     * Perform an edit on a feed, overwriting the data file at completion.
     *
     * @param feedInfo     the feed
     * @param feedDataFile the downloaded feed XML
     * @param encoding     the encoding to use when reading/writing the XML
     * @param editCommands list of 's///' edit commands, suitable for use
     *                     with {@link RegexUtil#substitute}
     *
     * @throws CurnException on error
     */
    protected void editXML(FeedInfo     feedInfo,
                           File         feedDataFile,
                           String       encoding,
                           List<String> editCommands)
        throws CurnException
    {
        String         feedURL = feedInfo.getURL().toString();
        BufferedReader in = null;
        PrintWriter    out = null;
        Logger         log = getLogger();

        try
        {
            File tempOutputFile = CurnUtil.createTempXMLFile();

            if (encoding != null)
            {
                in = new BufferedReader
                         (new InputStreamReader
                             (new FileInputStream (feedDataFile), encoding));
                out = new PrintWriter
                          (new OutputStreamWriter
                              (new FileOutputStream (tempOutputFile),
                               encoding));
            }

            else
            {
                in  = new BufferedReader (new FileReader (feedDataFile));
                out = new PrintWriter (new FileWriter (tempOutputFile));
            }

            String line;
            int    lineNumber = 0;

            while ((line = in.readLine()) != null)
            {
                lineNumber++;
                for (String editCommand : editCommands)
                {
                    if (log.isDebugEnabled() && (lineNumber == 1))
                    {
                        log.debug("Applying edit command \"" +
                                  editCommand +
                                  "\" to downloaded XML for feed \"" +
                                  feedURL +
                                  ", line " +
                                  lineNumber);
                    }

                    line = regexUtil.substitute (editCommand, line);
                }

                out.println (line);
            }

            in.close();
            in = null;

            out.flush();
            out.close();
            out = null;

            log.debug("Copying temporary (edited) file \"" +
                      tempOutputFile.getPath() +
                      "\" back over top of file \"" +
                      feedDataFile.getPath() +
                      "\".");
            FileUtil.copyTextFile(tempOutputFile,
                                  encoding,
                                  feedDataFile,
                                  encoding);

            tempOutputFile.delete();
        }

        catch (IOException ex)
        {
            throw new CurnException (ex);
        }

        catch (RegexException ex)
        {
            throw new CurnException (ex);
        }

        finally
        {
            try
            {
                if (in != null)
                    in.close();

                if(out != null)
                    out.close();
            }

            catch (IOException ex)
            {
                log.error ("I/O error", ex);
            }
        }
    }
}
