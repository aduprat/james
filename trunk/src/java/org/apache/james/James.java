/*
 * Copyright (C) The Apache Software Foundation. All rights reserved.
 *
 * This software is published under the terms of the Apache Software License
 * version 1.1, a copy of which has been included with this distribution in
 * the LICENSE file.
 */
package org.apache.james;

import org.apache.avalon.framework.activity.Initializable;
import org.apache.avalon.framework.component.*;
import org.apache.avalon.framework.configuration.Configurable;
import org.apache.avalon.framework.configuration.Configuration;
import org.apache.avalon.framework.configuration.ConfigurationException;
import org.apache.avalon.framework.configuration.DefaultConfiguration;
import org.apache.avalon.framework.context.Context;
import org.apache.avalon.framework.context.Contextualizable;
import org.apache.avalon.framework.context.DefaultContext;
import org.apache.avalon.framework.logger.AbstractLogEnabled;
import org.apache.avalon.framework.logger.Logger;
import org.apache.james.core.MailHeaders;
import org.apache.james.core.MailImpl;
import org.apache.james.services.*;
import org.apache.james.userrepository.DefaultJamesUser;
import org.apache.james.util.RFC2822Headers;
import org.apache.james.util.RFC822DateFormat;
import org.apache.mailet.Mail;
import org.apache.mailet.MailAddress;
import org.apache.mailet.MailetContext;

import javax.mail.Address;
import javax.mail.MessagingException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;

/**
 * Core class for JAMES. Provides three primary services:
 * <br> 1) Instantiates resources, such as user repository, and protocol
 * handlers
 * <br> 2) Handles interactions between components
 * <br> 3) Provides container services for Mailets
 *
 * @author  Federico Barbieri <scoobie@pop.systemy.it>
 * @author Serge
 * @author <a href="mailto:charles@benett1.demon.co.uk">Charles Benett</a>
 *
 * @version This is $Revision: 1.32 $

 */
public class James
    extends AbstractLogEnabled
    implements Contextualizable, Composable, Configurable, JamesMBean,
               Initializable, MailServer, MailetContext, Component {

    /**
     * The software name and version
     */
    private final static String SOFTWARE_NAME_VERSION = Constants.SOFTWARE_NAME + " " + Constants.SOFTWARE_VERSION;

    /**
     * Whether 'deep debugging' is turned on.
     */
    private final static boolean DEEP_DEBUG = false;

    /**
     * The component manager used both internally by James and by Mailets.
     */
    private DefaultComponentManager compMgr; //Components shared

    /**
     * TODO: Investigate what this is supposed to do.  Looks like it
     *       was supposed to be the Mailet context.
     */
    private DefaultContext context;

    /**
     * The top level configuration object for this server.
     */
    private Configuration conf;

    /**
     * The logger used by the Mailet API.
     */
    private Logger mailetLogger = null;

    /**
     * The mail store containing the inbox repository and the spool.
     */
    private MailStore mailstore;

    /**
     * The store containing the local user repository.
     */
    private UsersStore usersStore;

    /**
     * The spool used for processing mail handled by this server.
     */
    private SpoolRepository spool;

    /**
     * The repository that stores the user inboxes.
     */
    private MailRepository localInbox;

    /**
     * The root URL used to get mailboxes from the repository
     */
    private String inboxRootURL;

    /**
     * The user repository for this mail server.  Contains all the users with inboxes
     * on this server.
     */
    private UsersRepository localusers;

    /**
     * The collection of domain/server names for which this instance of James
     * will receive and process mail.
     */
    private Collection serverNames;

    /**
     * Whether to ignore case when looking up user names on this server
     */
    private boolean ignoreCase;

    /**
     * Whether to enable aliasing for users on this server
     */
    private boolean enableAliases;

    /**
     * Whether to enable forwarding for users on this server
     */
    private boolean enableForwarding;

    /**
     * The number of mails generated.  Access needs to be synchronized for
     * thread safety and to ensure that all threads see the latest value.
     */
    private static long count;

    /**
     * The address of the postmaster for this server
     */
    private MailAddress postmaster;

    /**
     * A map used to store mailboxes and reduce the cost of lookup of individual
     * mailboxes.
     */
    private Map mailboxes; //Not to be shared!

    /**
     * A hash table of server attributes
     * These are the MailetContext attributes
     */
    private Hashtable attributes = new Hashtable();

    /**
     * The Avalon context used by the instance
     */
    protected Context           myContext;

    /**
     * An RFC822 date formatter used to format dates in mail headers
     */
    private RFC822DateFormat rfc822DateFormat = new RFC822DateFormat();

    /**
     * Pass the Context to the component.
     * This method is called after the setLogger()
     * method and before any other method.
     *
     * @param context the context
     * @throws ContextException if context is invalid
     */
    public void contextualize(final Context context) {
        this.myContext = context;
    }

    /**
     * Pass the <code>ComponentManager</code> to the <code>composer</code>.
     * The instance uses the specified <code>ComponentManager</code> to 
     * acquire the components it needs for execution.
     *
     * @param componentManager The <code>ComponentManager</code> which this
     *                <code>Composable</code> uses.
     * @throws ComponentException if an error occurs
     */
    public void compose(ComponentManager comp) {
        compMgr = new DefaultComponentManager(comp);
        mailboxes = new HashMap(31);
    }

    /**
     * Pass the <code>Configuration</code> to the instance.
     *
     * @param configuration the class configurations.
     * @throws ConfigurationException if an error occurs
     */
    public void configure(Configuration conf) {
        this.conf = conf;
    }

    /**
     * Initialize the component. Initialization includes
     * allocating any resources required throughout the
     * components lifecycle.
     *
     * @throws Exception if an error occurs
     */
    public void initialize() throws Exception {

        getLogger().info("JAMES init...");

        // TODO: This should retrieve a more specific named thread pool from
        // Context that is set up in server.xml
        try {
            mailstore = (MailStore) compMgr.lookup( MailStore.ROLE );
        } catch (Exception e) {
            if (getLogger().isWarnEnabled()) {
                getLogger().warn("Can't get Store: " + e);
            }
        }
        if (getLogger().isDebugEnabled()) {
            getLogger().debug("Using MailStore: " + mailstore.toString());
        }
        try {
            usersStore = (UsersStore) compMgr.lookup( UsersStore.ROLE );
        } catch (Exception e) {
            if (getLogger().isWarnEnabled()) {
                getLogger().warn("Can't get Store: " + e);
            }
        }
        if (getLogger().isDebugEnabled()) {
            getLogger().debug("Using UsersStore: " + usersStore.toString());
        }

        String hostName = null;
        try {
            hostName = InetAddress.getLocalHost().getHostName();
        } catch  (UnknownHostException ue) {
            hostName = "localhost";
        }

        context = new DefaultContext();
        context.put("HostName", hostName);
        getLogger().info("Local host is: " + hostName);

        // Get the domains and hosts served by this instance
        serverNames = new HashSet();
        Configuration serverConf = conf.getChild("servernames");
        if (serverConf.getAttributeAsBoolean("autodetect") && (!hostName.equals("localhost"))) {
            serverNames.add(hostName.toLowerCase(Locale.US));
        }

        final Configuration[] serverNameConfs =
            conf.getChild( "servernames" ).getChildren( "servername" );
        for ( int i = 0; i < serverNameConfs.length; i++ ) {
            serverNames.add( serverNameConfs[i].getValue().toLowerCase(Locale.US));

            if (serverConf.getAttributeAsBoolean("autodetectIP", true)) {
                try {
                    /* This adds the IP address(es) for each host to support
                     * support <user@address-literal> - RFC 2821, sec 4.1.3.
                     * It might be proper to use the actual IP addresses
                     * available on this server, but we can't do that
                     * without NetworkInterface from JDK 1.4.  Because of
                     * Virtual Hosting considerations, we may need to modify
                     * this to keep hostname and IP associated, rather than
                     * just both in the set.
                     */
                    InetAddress[] addrs = InetAddress.getAllByName(serverNameConfs[i].getValue());
                    for (int j = 0; j < addrs.length ; j++) {
                        serverNames.add(addrs[j].getHostAddress());
                    }
                }
                catch(Exception genericException) {
                    getLogger().error("Cannot get IP address(es) for " + serverNameConfs[i].getValue());
                }
            }
        }
        if (serverNames.isEmpty()) {
            throw new ConfigurationException( "Fatal configuration error: no servernames specified!");
        }

        if (getLogger().isInfoEnabled()) {
            for (Iterator i = serverNames.iterator(); i.hasNext(); ) {
                getLogger().info("Handling mail for: " + i.next());
            }
        }
        context.put(Constants.SERVER_NAMES, this.serverNames);
        attributes.put(Constants.SERVER_NAMES, this.serverNames);


        // Get postmaster
        String postMasterAddress = conf.getChild("postmaster").getValue("postmaster");
        // if there is no @domain part, then add the first one from the
        // list of supported domains that isn't localhost.  If that
        // doesn't work, use the hostname, even if it is localhost.
        if (postMasterAddress.indexOf('@') < 0) {
            String domainName = null;    // the domain to use
            // loop through candidate domains until we find one or exhaust the list
            for ( int i = 0; domainName == null && i < serverNameConfs.length ; i++ ) {
                String serverName = serverNameConfs[i].getValue().toLowerCase(Locale.US);
                if (!("localhost".equals(serverName))) {
                    domainName = serverName;    // ok, not localhost, so use it
                }
            }
            // if we found a suitable domain, use it.  Otherwise fallback to the host name.
            postMasterAddress = postMasterAddress + "@" + (domainName != null ? domainName : hostName);
        }
        this.postmaster = new MailAddress( postMasterAddress );
        context.put( Constants.POSTMASTER, postmaster );

        if (!isLocalServer(postmaster.getHost())) {
            StringBuffer warnBuffer
                = new StringBuffer(320)
                        .append("The specified postmaster address ( ")
                        .append(postmaster)
                        .append(" ) is not a local address.  This is not necessarily a problem, but it does mean that emails addressed to the postmaster will be routed to another server.  For some configurations this may cause problems.");
            getLogger().warn(warnBuffer.toString());
        }

        Configuration userNamesConf = conf.getChild("usernames");
        ignoreCase = userNamesConf.getAttributeAsBoolean("ignoreCase", false);
        enableAliases = userNamesConf.getAttributeAsBoolean("enableAliases", false);
        enableForwarding = userNamesConf.getAttributeAsBoolean("enableForwarding", false);

        //Get localusers
        try {
            localusers = (UsersRepository) usersStore.getRepository("LocalUsers");
        } catch (Exception e) {
            getLogger().error("Cannot open private UserRepository");
            throw e;
        }
        //}
        compMgr.put( UsersRepository.ROLE, (Component)localusers);
        getLogger().info("Local users repository opened");

        Configuration inboxConf = conf.getChild("inboxRepository");
        Configuration inboxRepConf = inboxConf.getChild("repository");
        try {
            localInbox = (MailRepository) mailstore.select(inboxRepConf);
        } catch (Exception e) {
            getLogger().error("Cannot open private MailRepository");
            throw e;
        }
        inboxRootURL = inboxRepConf.getAttribute("destinationURL");

        getLogger().info("Private Repository LocalInbox opened");

        // Add this to comp
        compMgr.put( MailServer.ROLE, this);

        spool = mailstore.getInboundSpool();
        if ((DEEP_DEBUG) && (getLogger().isDebugEnabled())) {
            getLogger().debug("Got spool");
        }

        // For mailet engine provide MailetContext
        //compMgr.put("org.apache.mailet.MailetContext", this);
        // For AVALON aware mailets and matchers, we put the Component object as
        // an attribute
        attributes.put(Constants.AVALON_COMPONENT_MANAGER, compMgr);

        System.out.println(SOFTWARE_NAME_VERSION);
        getLogger().info("JAMES ...init end");
    }

    /**
     * Place a mail on the spool for processing
     *
     * @param message the message to send
     *
     * @throws MessagingException if an exception is caught while placing the mail
     *                            on the spool
     */
    public void sendMail(MimeMessage message) throws MessagingException {
        MailAddress sender = new MailAddress((InternetAddress)message.getFrom()[0]);
        Collection recipients = new HashSet();
        Address addresses[] = message.getAllRecipients();
        for (int i = 0; i < addresses.length; i++) {
            recipients.add(new MailAddress((InternetAddress)addresses[i]));
        }
        sendMail(sender, recipients, message);
    }

    /**
     * Place a mail on the spool for processing
     *
     * @param sender the sender of the mail
     * @param recipients the recipients of the mail
     * @param message the message to send
     *
     * @throws MessagingException if an exception is caught while placing the mail
     *                            on the spool
     */
    public void sendMail(MailAddress sender, Collection recipients, MimeMessage message)
            throws MessagingException {
        sendMail(sender, recipients, message, Mail.DEFAULT);
    }

    /**
     * Place a mail on the spool for processing
     *
     * @param sender the sender of the mail
     * @param recipients the recipients of the mail
     * @param message the message to send
     * @param state the state of the message
     *
     * @throws MessagingException if an exception is caught while placing the mail
     *                            on the spool
     */
    public void sendMail(MailAddress sender, Collection recipients, MimeMessage message, String state)
            throws MessagingException {
        MailImpl mail = new MailImpl(getId(), sender, recipients, message);
        mail.setState(state);
        sendMail(mail);
    }

    /**
     * Place a mail on the spool for processing
     *
     * @param sender the sender of the mail
     * @param recipients the recipients of the mail
     * @param msg an <code>InputStream</code> containing the message
     *
     * @throws MessagingException if an exception is caught while placing the mail
     *                            on the spool
     */
    public void sendMail(MailAddress sender, Collection recipients, InputStream msg)
            throws MessagingException {
        // parse headers
        MailHeaders headers = new MailHeaders(msg);

        // if headers do not contains minimum REQUIRED headers fields throw Exception
        if (!headers.isValid()) {
            throw new MessagingException("Some REQURED header field is missing. Invalid Message");
        }
        ByteArrayInputStream headersIn = new ByteArrayInputStream(headers.toByteArray());
        sendMail(new MailImpl(getId(), sender, recipients, new SequenceInputStream(headersIn, msg)));
    }

    /**
     * Place a mail on the spool for processing
     *
     * @param mail the mail to place on the spool
     *
     * @throws MessagingException if an exception is caught while placing the mail
     *                            on the spool
     */
    public void sendMail(Mail mail) throws MessagingException {
        MailImpl mailimpl = (MailImpl)mail;
        try {
            spool.store(mailimpl);
        } catch (Exception e) {
            try {
                spool.remove(mailimpl);
            } catch (Exception ignored) {
            }
            throw new MessagingException("Exception spooling message: " + e.getMessage(), e);
        }
        if (getLogger().isInfoEnabled()) {
            StringBuffer logBuffer =
                new StringBuffer(64)
                        .append("Mail ")
                        .append(mailimpl.getName())
                        .append(" pushed in spool");
            getLogger().info(logBuffer.toString());
        }
    }

    /**
     * <p>Retrieve the mail repository for a user</p>
     *
     * <p>For POP3 server only - at the moment.</p>
     *
     * @param userName the name of the user whose inbox is to be retrieved
     *
     * @return the POP3 inbox for the user
     */
    public synchronized MailRepository getUserInbox(String userName) {
        MailRepository userInbox = (MailRepository) null;

        userInbox = (MailRepository) mailboxes.get(userName);

        if (userInbox != null) {
            return userInbox;
        } else if (mailboxes.containsKey(userName)) {
            // we have a problem
            getLogger().error("Null mailbox for non-null key");
            throw new RuntimeException("Error in getUserInbox.");
        } else {
            // need mailbox object
            getLogger().info("Need inbox for " + userName );
            StringBuffer destinationBuffer =
                new StringBuffer(192)
                        .append(inboxRootURL)
                        .append(userName)
                        .append("/");
            String destination = destinationBuffer.toString();
            DefaultConfiguration mboxConf
                = new DefaultConfiguration("repository", "generated:AvalonFileRepository.compose()");
            mboxConf.setAttribute("destinationURL", destination);
            mboxConf.setAttribute("type", "MAIL");
            try {
                userInbox = (MailRepository) mailstore.select(mboxConf);
                mailboxes.put(userName, userInbox);
            } catch (Exception e) {
                e.printStackTrace();
                if (getLogger().isErrorEnabled())
                {
                    getLogger().error("Cannot open user Mailbox" + e);
                }
                throw new RuntimeException("Error in getUserInbox." + e);
            }
            return userInbox;
        }
    }

    /**
     * Return a new mail id.
     *
     * @return a new mail id
     */
    public String getId() {
        long localCount = -1;
        synchronized (James.class) {
            localCount = count++;
        }
        StringBuffer idBuffer =
            new StringBuffer(64)
                    .append("Mail")
                    .append(System.currentTimeMillis())
                    .append("-")
                    .append(count++);
        return idBuffer.toString();
    }

    /**
     * The main method.  Should never be invoked, as James must be called
     * from within an Avalon framework container.
     *
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        System.out.println("ERROR!");
        System.out.println("Cannot execute James as a stand alone application.");
        System.out.println("To run James, you need to have the Avalon framework installed.");
        System.out.println("Please refer to the Readme file to know how to run James.");
    }

    //Methods for MailetContext

    /**
     * <p>Get the prioritized list of mail servers for a given host.</p>
     *
     * <p>TODO: This needs to be made a more specific ordered subtype of Collection.</p>
     *
     * @param host 
     */
    public Collection getMailServers(String host) {
        DNSServer dnsServer = null;
        try {
            dnsServer = (DNSServer) compMgr.lookup( DNSServer.ROLE );
        } catch ( final ComponentException cme ) {
            getLogger().error("Fatal configuration error - DNS Servers lost!", cme );
            throw new RuntimeException("Fatal configuration error - DNS Servers lost!");
        }
        return dnsServer.findMXRecords(host);
    }

    public Object getAttribute(String key) {
        return attributes.get(key);
    }

    public void setAttribute(String key, Object object) {
        attributes.put(key, object);
    }

    public void removeAttribute(String key) {
        attributes.remove(key);
    }

    public Iterator getAttributeNames() {
        Vector names = new Vector();
        for (Enumeration e = attributes.keys(); e.hasMoreElements(); ) {
            names.add(e.nextElement());
        }
        return names.iterator();
    }

    /**
     * This generates a response to the Return-Path address, or the address of
     * the message's sender if the Return-Path is not available.  Note that
     * this is different than a mail-client's reply, which would use the
     * Reply-To or From header. This will send the bounce with the server's
     * postmaster as the sender.
     */
    public void bounce(Mail mail, String message) throws MessagingException {
        bounce(mail, message, getPostmaster());
    }

    /**
     * This generates a response to the Return-Path address, or the address of
     * the message's sender if the Return-Path is not available.  Note that
     * this is different than a mail-client's reply, which would use the
     * Reply-To or From header.
     */
    public void bounce(Mail mail, String message, MailAddress bouncer) throws MessagingException {
        MimeMessage orig = mail.getMessage();
        //Create the reply message
        MimeMessage reply = (MimeMessage) orig.reply(false);
        //If there is a Return-Path header,
        if (orig.getHeader(RFC2822Headers.RETURN_PATH) != null) {
            //Return the message to that address, not to the Reply-To address
            reply.setRecipient(MimeMessage.RecipientType.TO, new InternetAddress(orig.getHeader(RFC2822Headers.RETURN_PATH)[0]));
        }
        //Create the list of recipients in our MailAddress format
        Collection recipients = new HashSet();
        Address addresses[] = reply.getAllRecipients();
        for (int i = 0; i < addresses.length; i++) {
            recipients.add(new MailAddress((InternetAddress)addresses[i]));
        }
        //Change the sender...
        reply.setFrom(bouncer.toInternetAddress());
        try {
            //Create the message body
            MimeMultipart multipart = new MimeMultipart();
            //Add message as the first mime body part
            MimeBodyPart part = new MimeBodyPart();
            part.setContent(message, "text/plain");
            part.setHeader(RFC2822Headers.CONTENT_TYPE, "text/plain");
            multipart.addBodyPart(part);

            //Add the original message as the second mime body part
            part = new MimeBodyPart();
            part.setContent(orig.getContent(), orig.getContentType());
            part.setHeader(RFC2822Headers.CONTENT_TYPE, orig.getContentType());
            multipart.addBodyPart(part);
            reply.setHeader(RFC2822Headers.DATE, rfc822DateFormat.format(new Date()));
            reply.setContent(multipart);
            reply.setHeader(RFC2822Headers.CONTENT_TYPE, multipart.getContentType());
        } catch (IOException ioe) {
            throw new MessagingException("Unable to create multipart body", ioe);
        }
        //Send it off...
        sendMail(bouncer, recipients, reply);
    }

    /**
     * Returns whether that account has a local inbox on this server
     *
     * @param name the name to be checked
     *
     * @return whether the account has a local inbox
     */
    public boolean isLocalUser(String name) {
        if (ignoreCase) {
            return localusers.containsCaseInsensitive(name);
        } else {
            return localusers.contains(name);
        }
    }

    /**
     * Returns the address of the postmaster for this server.
     *
     * @return the <code>MailAddress</code> for the postmaster
     */
    public MailAddress getPostmaster() {
        return postmaster;
    }

    public void storeMail(MailAddress sender, MailAddress recipient, MimeMessage message)
        throws MessagingException {
        String username;
        if (ignoreCase) {
            username = localusers.getRealName(recipient.getUser());
        } else {
            username = recipient.getUser();
        }
        JamesUser user;
        if (enableAliases || enableForwarding) {
            user = (JamesUser) localusers.getUserByName(username);
            if (enableAliases && user.getAliasing()) {
                username = user.getAlias();
            }
            if (enableForwarding && user.getForwarding()) {
                MailAddress forwardTo = user.getForwardingDestination();
                Collection recipients = new HashSet();
                recipients.add(forwardTo);
                try {
                    sendMail(sender, recipients, message);
                    StringBuffer logBuffer =
                        new StringBuffer(128)
                                .append("Mail for ")
                                .append(username)
                                .append(" forwarded to ")
                                .append(forwardTo.toString());
                    getLogger().info(logBuffer.toString());
                    return;
                } catch (MessagingException me) {
                    if (getLogger().isErrorEnabled()) {
                        StringBuffer logBuffer =
                            new StringBuffer(128)
                                    .append("Error forwarding mail to ")
                                    .append(forwardTo.toString())
                                    .append("attempting local delivery");
                        getLogger().error(logBuffer.toString());
                    }
                }
            }
        }

        Collection recipients = new HashSet();
        recipients.add(recipient);
        MailImpl mailImpl = new MailImpl(getId(), sender, recipients, message);
        getUserInbox(username).store(mailImpl);
    }

    /**
     * Return the major version number for the server
     *
     * @return the major vesion number for the server
     */
    public int getMajorVersion() {
        return 2;
    }

    /**
     * Return the minor version number for the server
     *
     * @return the minor vesion number for the server
     */
    public int getMinorVersion() {
        return 1;
    }

    /**
     * Check whether the mail domain in question is to be 
     * handled by this server.
     *
     * @param serverName the name of the server to check
     * @return whether the server is local
     */
    public boolean isLocalServer( final String serverName ) {
        return serverNames.contains(serverName.toLowerCase(Locale.US));
    }

    /**
     * Return the type of the server
     *
     * @return the type of the server
     */
    public String getServerInfo() {
        return "Apache Jakarta JAMES";
    }

    /**
     * Return the logger for the Mailet API
     *
     * @return the logger for the Mailet API
     */
    private Logger getMailetLogger() {
        if (mailetLogger == null) {
            mailetLogger = getLogger().getChildLogger("Mailet");
        }
        return mailetLogger;
    }

    /**
     * Log a message to the Mailet logger
     *
     * @param message the message to pass to the Mailet logger
     */
    public void log(String message) {
        getMailetLogger().info(message);
    }

    /**
     * Log a message and a Throwable to the Mailet logger
     *
     * @param message the message to pass to the Mailet logger
     * @param t the <code>Throwable</code> to be logged
     */
    public void log(String message, Throwable t) {
        //System.err.println(message);
        //t.printStackTrace(); //DEBUG
        getMailetLogger().info(message,t);
    }

    /**
     * Adds a user to this mail server. Currently just adds user to a
     * UsersRepository.
     *
     * @param userName String representing user name, that is the portion of
     * an email address before the '@<domain>'.
     * @param password String plaintext password
     * @return boolean true if user added succesfully, else false.
     */
    public boolean addUser(String userName, String password) {
        boolean success;
        DefaultJamesUser user = new DefaultJamesUser(userName, "SHA");
        user.setPassword(password);
        user.initialize();
        success = localusers.addUser(user);
        return success;
    }
}
