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

import java.io.StringWriter;

import java.util.List;

import org.clapper.util.io.WordWrapWriter;

import freemarker.template.SimpleScalar;
import freemarker.template.TemplateModel;
import freemarker.template.TemplateMethodModel;
import freemarker.template.TemplateModelException;

/**
 * FreeMarker method (put in the FreeMarker data model) that permits a
 * template to wrap plain text via the <tt>WordWrapWriter</tt> class.
 *
 * @version <tt>$Revision$</tt>
 */
class WrapTextMethod implements TemplateMethodModel
{
    /*----------------------------------------------------------------------*\
                            Private Data Items
    \*----------------------------------------------------------------------*/

    private StringWriter   stringWriter = new StringWriter();
    private WordWrapWriter wrapWriter   = new WordWrapWriter (stringWriter);

    /*----------------------------------------------------------------------*\
                                Constructor
    \*----------------------------------------------------------------------*/

    /**
     * Construct a new <tt>WrapTextMethod</tt> object.
     */
    public WrapTextMethod()
    {
        // Nothing to do.
    }

    /*----------------------------------------------------------------------*\
                              Public Methods
    \*----------------------------------------------------------------------*/

    /**
     * Execute the method.
     *
     * @param args the arguments:
     *             <ul>
     *               <li> text to be wrapped (required)
     *               <li> indentation (optional; defaults to 0)
     *               <li> wrap column (optional; defaults to 79)
     *             </ul>
     */
    public TemplateModel exec (List args) throws TemplateModelException
    {
        int totalArgs = args.size();
        StringBuffer buf = stringWriter.getBuffer();

        buf.setLength (0);

        switch (totalArgs)
        {
            case 3:
                String sLineLen = (String) args.get (2);
                try
                {
                    wrapWriter.setLineLength (Integer.parseInt (sLineLen));
                }

                catch (NumberFormatException ex)
                {
                    throw new TemplateModelException ("Bad line length " +
                                                      "value \"" + sLineLen +
                                                      "\"");
                }
                // Fall through intentional

            case 2:
                String sIndent = (String) args.get (1);
                try
                {
                    wrapWriter.setIndentation (Integer.parseInt (sIndent));
                }

                catch (NumberFormatException ex)
                {
                    throw new TemplateModelException ("Bad indentation " +
                                                      "value \"" + sIndent +
                                                      "\"");
                }
                // Fall through intentional

            case 1:
                wrapWriter.println ((String) args.get (0));
                break;

            default:
                throw new TemplateModelException ("Wrong number of arguments");
        }

        // Strip the last trailing newline from the wrapped string and return
        // it.

        String s = buf.deleteCharAt (buf.length() - 1).toString();
        return new SimpleScalar (s);
    }
}
