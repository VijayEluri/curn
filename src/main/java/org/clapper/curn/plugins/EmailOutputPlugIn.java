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


package org.clapper.curn.plugins;

import org.clapper.curn.Constants;
import org.clapper.curn.CurnConfig;
import org.clapper.curn.CurnException;
import org.clapper.curn.FeedInfo;
import org.clapper.curn.MainConfigItemPlugIn;
import org.clapper.curn.OutputHandler;
import org.clapper.curn.PreFeedOutputPlugIn;
import org.clapper.curn.PostOutputPlugIn;
import org.clapper.curn.Version;
import org.clapper.curn.parser.RSSItem;
import org.clapper.curn.parser.RSSChannel;

import org.clapper.util.classutil.ClassUtil;
import org.clapper.util.config.ConfigurationException;
import org.clapper.util.logging.Logger;
import org.clapper.util.mail.EmailMessage;
import org.clapper.util.mail.EmailTransport;
import org.clapper.util.mail.SMTPEmailTransport;
import org.clapper.util.mail.EmailAddress;
import org.clapper.util.mail.EmailException;
import org.clapper.util.misc.MIMETypeUtil;
import org.clapper.util.text.TextUtil;

import java.io.File;

import java.text.DecimalFormat;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.TreeSet;
import org.clapper.util.misc.MultiValueMap;

/**
 * The <tt>EmailOutputPlugIn</tt> handles emailing the output from a
 * <i>curn</i> run, if one or more email addresses are specified in the
 * configuration file. It intercepts the following main (<tt>[curn]</tt>)
 * section configuration parameters:
 *
 * <table border="1">
 *   <tr valign="top">
 *     <th align="left">Parameter</th>
 *     <th align="left">Meaning</th>
 *     <th align="left">Default</th>
 *   </tr>
 *   <tr valign="top">
 *     <td><tt>MailOutputTo</tt></td>
 *     <td>One or more comma- or blank-separated email addresses to receive
 *         an email containing the output.</td>
 *     <td>None</td>
 *   </tr>
 *   <tr valign="top">
 *     <td><tt>MailFrom</tt></td>
 *     <td>The email address to use as the sender of the message.</td>
 *     <td>The user running curn, and the current machine.</td>
 *   </tr>
 *   <tr valign="top">
 *     <td><tt>MailSubject</tt></td>
 *     <td>The subject to use for email messages.</td>
 *     <td>"RSS Feeds"</td>
 *   </tr>
 *   <tr valign="top">
 *     <td><tt>MailIndividualArticles</tt></td>
 *     <td>If set to <tt>true</tt> (or <tt>yes</tt> or <tt>1</tt>),
 *         <i>curn</i> will send each RSS article individually--i.e.,
 *         one article per email. Otherwise, it sends all the articles from
 *         all feeds in a single email.</td>
 *     <td><tt>false</tt> (i.e., send one email with all articles)</td>
 *   </tr>
 * </table>
 *
 * @version <tt>$Revision$</tt>
 */
public class EmailOutputPlugIn
    implements MainConfigItemPlugIn,
               PreFeedOutputPlugIn,
               PostOutputPlugIn
{
    /*----------------------------------------------------------------------*\
                             Private Constants
    \*----------------------------------------------------------------------*/

    private static final String VAR_EMAIL_OUTPUT_TO       = "MailOutputTo";
    private static final String VAR_SMTP_HOST             = "SMTPHost";
    private static final String VAR_SMTP_LOCALHOST        = "SMTPLocalHost";
    private static final String DEF_SMTP_HOST             = "localhost";
    private static final String VAR_EMAIL_SENDER          = "MailSender";
    private static final String VAR_EMAIL_SUBJECT         = "MailSubject";
    private static final String DEF_EMAIL_SUBJECT         = "RSS Feeds";
    private static final String VAR_MAIL_INDIVIDUAL_ITEMS = "MailIndividualArticles";

    /*----------------------------------------------------------------------*\
                                Inner Classes
    \*----------------------------------------------------------------------*/

    private class GeneratedOutput
    {
        final File outputFile;
        final String mimeType;

        GeneratedOutput(File outputFile, String mimeType)
        {
            this.outputFile = outputFile;
            this.mimeType = mimeType;
        }
    }

    /*----------------------------------------------------------------------*\
                            Private Data Items
    \*----------------------------------------------------------------------*/

    /**
     * Collection of email addresses
     */
    private Collection<EmailAddress> emailAddresses = null;

    /**
     * SMTP host to use
     */
    private String smtpHost = DEF_SMTP_HOST;

    /**
     * Name to use for local host.
     */
    private String smtpLocalhost = null;

    /**
     * Email sender address
     */
    private EmailAddress emailSender = null;

    /**
     * Email subject
     */
    private String emailSubject = DEF_EMAIL_SUBJECT;

    /**
     * For log messages
     */
    private static final Logger log = new Logger (EmailOutputPlugIn.class);

    /**
     * If set, mail individual items, one at a time. If not set, mail
     * all items at the end, in one email.
     */
    private boolean mailIndividualItems = false;

    /**
     * A map containing all the output generated for each article.
     * Use to construct individual multipart/alternative messages for
     * each article. Only used if mailIndividualItems=true
     */
    private MultiValueMap<RSSItem,GeneratedOutput> itemOutputMap = null;

    /**
     * Copy of all items seen. Only used if mailIndividualItems=true
     */
    private Collection<RSSItem> itemsSeen = null;

    /*----------------------------------------------------------------------*\
                                Constructor
    \*----------------------------------------------------------------------*/

    /**
     * Default constructor (required).
     */
    public EmailOutputPlugIn()
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
        return "Email Output";
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
            if (paramName.equals(VAR_SMTP_HOST))
            {
                smtpHost = config.getConfigurationValue(sectionName,
                                                        paramName);
            }
            
            else if (paramName.equals(VAR_SMTP_LOCALHOST))
            {
                smtpLocalhost = config.getConfigurationValue(sectionName,
                                                             paramName);
            }

            else if (paramName.equals(VAR_EMAIL_SENDER))
            {
                if (emailSender != null)
                {
                    throw new CurnException
                        (Constants.BUNDLE_NAME,
                         "EmailOutputPlugIn.senderAlreadyDefined",
                         "Section [{0}], configuration item \"{1}\": Email " +
                         "sender has already been defined.",
                         new Object[] {sectionName, paramName});
                }

                String sender = config.getConfigurationValue(sectionName,
                                                             paramName);
                try
                {
                    emailSender = new EmailAddress(sender);

                }

                catch (EmailException ex)
                {
                    throw new CurnException
                        (Constants.BUNDLE_NAME,
                         "EmailOutputPlugIn.badEmailAddress",
                         "Section [{0}], configuration item \"{1}\": " +
                         "\"{2}\" is an invalid email address",
                         new Object[] {sectionName, paramName, sender},
                         ex);
                }
            }

            else if (paramName.equals(VAR_EMAIL_SUBJECT))
            {
                emailSubject = config.getConfigurationValue(sectionName,
                                                            paramName);
            }

            else if (paramName.equals(VAR_EMAIL_OUTPUT_TO))
            {
                String addrList = config.getConfigurationValue(sectionName,
                                                               paramName);
                String[] addrs = TextUtil.split(addrList, ",");

                if ((addrs == null) || (addrs.length == 0))
                {
                    throw new CurnException
                        (Constants.BUNDLE_NAME,
                         "EmailOutputPlugIn.missingEmailAddresses",
                         "Missing email address(es) in {0} section " +
                         "configuration item \"{1}\"",
                         new Object[] {sectionName, paramName});
                }

                // Might as well validate them here.

                emailAddresses = new ArrayList<EmailAddress>();
                for (String addr : addrs)
                {
                    try
                    {
                        addr = addr.trim();
                        emailAddresses.add(new EmailAddress(addr));
                    }

                    catch (EmailException ex)
                    {
                        emailAddresses = null;
                        throw new CurnException
                            (Constants.BUNDLE_NAME,
                             "EmailOutputPlugIn.badEmailAddress",
                             "Section [{0}], configuration item \"{1}\": " +
                             "\"{2}\" is an invalid email address",
                             new Object[] {sectionName, paramName, addr},
                             ex);
                    }
                }
            }

            else if (paramName.equals(VAR_MAIL_INDIVIDUAL_ITEMS))
            {
                mailIndividualItems = config.getRequiredBooleanValue(sectionName,
                                                                     paramName);
                if (mailIndividualItems)
                {
                    itemOutputMap = new MultiValueMap<RSSItem,GeneratedOutput>();
                    itemsSeen = new TreeSet<RSSItem>();
                }
            }
        }

        catch (ConfigurationException ex)
        {
            throw new CurnException (ex);
        }
    }

    /**
     * Called immediately before a parsed feed is passed to an output
     * handler. This method cannot affect the feed's processing. (The time
     * to stop the processing of a feed is in one of the other, preceding
     * phases.) This method will be called multiple times for each feed if
     * there are multiple output handlers.
     *
     * @param feedInfo      the {@link FeedInfo} object for the feed that
     *                      has been downloaded and parsed.
     * @param channel       the parsed channel data. The plug-in is free
     *                      to edit this data; it's receiving a copy
     *                      that's specific to the output handler.
     * @param outputHandler the {@link OutputHandler} that is about to be
     *                      called. This object is read-only.
     *
     * @throws CurnException on error
     *
     * @see RSSChannel
     * @see FeedInfo
     */
    public void runPreFeedOutputPlugIn(FeedInfo      feedInfo,
                                       RSSChannel    channel,
                                       OutputHandler outputHandler)
        throws CurnException
    {
        if (mailIndividualItems &&
            (emailAddresses != null) &&
            (emailAddresses.size() > 0))
        {
            log.debug("mailIndividualItems=true, and there are email " +
                      "addresses. Mailing individual items for feed " +
                      feedInfo.getURL());

            // Use the output handler to generate the output. Break the channel
            // into multiple channels with one item each. Then, keep the output,
            // aggregated by RSSItem. That way, when it's time to generate
            // the output, we'll have the output from ALL output handlers
            // for each item, allowing us to generate a multipart/alternative
            // email for each item.

            RSSChannel newChannel = channel.makeCopy();
            for (RSSItem item : channel.getItems())
            {
                itemsSeen.add(item);
                newChannel.setItems(Collections.singletonList(item));
                outputHandler = outputHandler.makeCopy();
                outputHandler.displayChannel(newChannel, feedInfo);
                outputHandler.flush();
                if (outputHandler.hasGeneratedOutput())
                {
                    String mimeType = outputHandler.getContentType();
                    File outputFile = outputHandler.getGeneratedOutput();
                    itemOutputMap.put(item,
                                      new GeneratedOutput(outputFile, mimeType));
                }
            }
        }
    }

    /**
     * Called after <i>curn</i> has flushed <i>all</i> output handlers. A
     * post-output plug-in is a useful place to consolidate the output from
     * all output handlers. For instance, such a plug-in might pack all the
     * output into a zip file, or email it.
     *
     * @param outputHandlers a <tt>Collection</tt> of the
     *                       {@link OutputHandler} objects (useful for
     *                       obtaining the output files, for instance).
     *
     * @throws CurnException on error
     *
     * @see OutputHandler
     */
    public void runPostOutputPlugIn(Collection<OutputHandler> outputHandlers)
        throws CurnException
    {
        if ((emailAddresses != null) && (emailAddresses.size() > 0))
        {
            log.debug("There are email addresses.");
            if (mailIndividualItems)
                emailIndividualArticles();
            else
                emailConsolidatedOutput(outputHandlers);
        }
    }

    /*----------------------------------------------------------------------*\
                              Private Methods
    \*----------------------------------------------------------------------*/

    private void emailIndividualArticles()
        throws CurnException
    {
        assert(emailAddresses.size() > 0);

        // One message per item. The runPreFeedOutputPlugIn()
        // method should have saved the channels and the item outputs.

        assert(itemOutputMap != null);
        assert(itemsSeen != null);

        for (RSSItem item : itemsSeen)
        {
            Collection<GeneratedOutput> itemOutput =
                itemOutputMap.getValuesForKey(item);
            if (itemOutput != null)
            {
                log.debug("Emailing output for item \"" + item +
                          "\": total attachments=" + itemOutput.size());
                emailOutput(itemOutput);
            }
        }
    }

    private void emailConsolidatedOutput(Collection<OutputHandler> outputHandlers)
        throws CurnException
    {
        assert(emailAddresses.size() > 0);

        // One email message with all the output.

        Collection<GeneratedOutput> output =
            new ArrayList<GeneratedOutput>();
        for (OutputHandler handler : outputHandlers)
        {
            if (! handler.hasGeneratedOutput())
                break;

            output.add(new GeneratedOutput(handler.getGeneratedOutput(),
                                           handler.getContentType()));
        }

        if (output.size() == 0)
        {
            log.debug("None of the output handlers " +
                      "produced any emailable output.");
        }

        else
        {
            emailOutput(output);
        }
    }

    private void emailOutput(Collection<GeneratedOutput> generatedOutputs)
        throws CurnException
    {
        assert(generatedOutputs.size() > 0);
        assert(emailAddresses.size() > 0);

        try
        {
            // Create an SMTP transport and a new email message.

            EmailTransport transport = new SMTPEmailTransport(smtpHost,
                                                              smtpLocalhost);
            EmailMessage   message = new EmailMessage();

            log.debug("SMTP host = " + smtpHost);

            // Add the email addresses.

            for (EmailAddress emailAddress : emailAddresses)
            {
                try
                {
                    log.debug("Email recipient = " + emailAddress);
                    message.addTo(emailAddress);
                }

                catch (EmailException ex)
                {
                    throw new CurnException(ex);
                }
            }

            // Create an X-Mailer header that identifies this utility.

            message.addHeader("X-Mailer",
                              Version.getInstance().getFullVersion());

            // Set the subject

            message.setSubject(emailSubject);

            // Set the sender, if defined.

            if (emailSender != null)
                message.setSender(emailSender);

            if (log.isDebugEnabled())
                log.debug("Email sender = " + message.getSender());

            // Add the output. If there's only one attachment, and its
            // output is text, then there's no need for attachments.
            // Just set it as the text part, and set the appropriate
            // Content-type: header. Otherwise, make a
            // multipart-alternative message with separate attachments
            // for each output.

            DecimalFormat fmt  = new DecimalFormat("##000");
            StringBuffer  name = new StringBuffer();
            String        ext;
            String        contentType;
            File          file;

            if (generatedOutputs.size() == 1)
            {
                GeneratedOutput output = generatedOutputs.iterator().next();
                contentType = output.mimeType;
                ext = MIMETypeUtil.fileExtensionForMIMEType(contentType);
                file = output.outputFile;
                message.setMultipartSubtype(EmailMessage.MULTIPART_MIXED);

                name.append(fmt.format(1));
                name.append('.');
                name.append(ext);

                if (contentType.startsWith("text/"))
                    message.setText(file, name.toString(), contentType);
                else
                    message.addAttachment(file, name.toString(), contentType);
            }

            else
            {
                message.setMultipartSubtype(EmailMessage.MULTIPART_ALTERNATIVE);

                int i = 1;
                for (GeneratedOutput output : generatedOutputs)
                {
                    contentType = output.mimeType;
                    ext = MIMETypeUtil.fileExtensionForMIMEType(contentType);
                    file = output.outputFile;
                    if (file != null)
                    {
                        name.setLength(0);
                        name.append(fmt.format(i));
                        name.append('.');
                        name.append(ext);
                        i++;
                        message.addAttachment(file, name.toString(), contentType);
                    }
                }
            }

            log.debug("Sending message.");
            transport.send(message);
            message.clear();
        }

        catch (EmailException ex)
        {
            throw new CurnException (ex);
        }
    }
}
