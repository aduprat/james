/*
 * Copyright (C) The Apache Software Foundation. All rights reserved.
 *
 * This software is published under the terms of the Apache Software License
 * version 1.1, a copy of which has been included with this distribution in
 * the LICENSE file.
 */
package org.apache.james;

import java.io.*;
import java.net.*;
import java.util.*;
import javax.mail.Address;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.*;
import org.apache.avalon.Initializable;
import org.apache.avalon.component.Component;
import org.apache.avalon.component.ComponentException;
import org.apache.avalon.component.ComponentManager;
import org.apache.avalon.component.Composable;
import org.apache.avalon.component.DefaultComponentManager;
import org.apache.avalon.configuration.Configurable;
import org.apache.avalon.configuration.Configuration;
import org.apache.avalon.configuration.ConfigurationException;
import org.apache.avalon.configuration.DefaultConfiguration;
import org.apache.avalon.context.Context;
import org.apache.avalon.context.Contextualizable;
import org.apache.avalon.context.Contextualizable;
import org.apache.avalon.context.DefaultContext;
import org.apache.avalon.logger.AbstractLoggable;
import org.apache.excalibur.thread.ThreadPool;
import org.apache.james.core.*;
import org.apache.james.imapserver.*;
import org.apache.james.services.*;
import org.apache.james.transport.*;
import org.apache.log.LogKit;
import org.apache.log.Logger;
import org.apache.mailet.*;
import org.apache.phoenix.Block;
import org.apache.phoenix.BlockContext;

/**
 * Core class for JAMES. Provides three primary services:
 * <br> 1) Instantiates resources, such as user repository, and protocol
 * handlers
 * <br> 2) Handles interactions between components
 * <br> 3) Provides container services for Mailets
 *
 * @version
 * @author  Federico Barbieri <scoobie@pop.systemy.it>
 * @author Serge
 * @author <a href="mailto:charles@benett1.demon.co.uk">Charles Benett</a>
 */
public class James
    extends AbstractLoggable
    implements Block, Contextualizable, Composable, Configurable, Initializable, MailServer, MailetContext {

    public final static String VERSION = "James 1.2.2 Alpha";

    private DefaultComponentManager compMgr; //Components shared
    private DefaultContext context;
    private Configuration conf;

    private Logger mailetLogger = LogKit.getLoggerFor("james.Mailets");
    //private ThreadPool workerPool;
    private MailStore mailstore;
    private UsersStore usersStore;
    private SpoolRepository spool;
    private MailRepository localInbox;
    private String inboxRootURL;
    private UsersRepository localusers;
    private Collection serverNames;
    // this used to be long, but increment operations on long are not
    // thread safe. Changed to int. 'int' should be ok, because id generation
    // is based on System time and count
    private static int count;
    private String helloName;
    private String hostName;
    private Map mailboxes; //Not to be shared!
    private Hashtable attributes = new Hashtable();

    // IMAP related fields
    private boolean useIMAPstorage = false;
    private IMAPSystem imapSystem;
    private Host imapHost;
    protected BlockContext           blockContext;

    public void contextualize( final Context context )
    {
        this.blockContext = (BlockContext)context;
    }

    public void configure(Configuration conf) {
        this.conf = conf;
    }

    /**
     * Override compose method of AbstractBlock to create new ComponentManager object
     */
    public void compose(ComponentManager comp) {
        //throws ConfigurationException {
        compMgr = new DefaultComponentManager(comp);
        mailboxes = new HashMap(31);
    }

    public void init() throws Exception {

        getLogger().info("JAMES init...");

        //TODO: This should retrieve a more specific named thread pool from BlockContext
        //that is set up in server.xml
        //workerPool = blockContext.getThreadPool( "default" );
        try {
            mailstore = (MailStore) compMgr.lookup("org.apache.james.services.MailStore");
        } catch (Exception e) {
            getLogger().warn("Can't get Store: " + e);
        }
        getLogger().debug("Using MailStore: " + mailstore.toString());
        try {
            usersStore = (UsersStore) compMgr.lookup("org.apache.james.services.UsersStore");
        } catch (Exception e) {
            getLogger().warn("Can't get Store: " + e);
        }
        getLogger().debug("Using UsersStore: " + usersStore.toString());
        context = new DefaultContext();

        try {
            hostName = InetAddress.getLocalHost().getHostName();
        } catch  (UnknownHostException ue) {
            hostName = "localhost";
        }
        getLogger().info("Local host is: " + hostName);


        helloName = null;
        Configuration helloConf = conf.getChild("helloName");
        if (helloConf.getAttribute("autodetect").equals("TRUE")) {
            helloName = hostName;
        } else {
            helloName = helloConf.getValue();
            if (helloName == null || helloName.trim().equals("") )
                helloName = "localhost";
        }
        getLogger().info("Hello Name is: " + helloName);
        context.put(Constants.HELO_NAME, helloName);

        // Get the domains and hosts served by this instance
        serverNames = new Vector();
        Configuration serverConf = conf.getChild("servernames");
        if (serverConf.getAttribute("autodetect").equals("TRUE") && (!hostName.equals("localhost"))) {
            serverNames.add(hostName);
        }

        final Configuration[] serverNameConfs =
            conf.getChild( "servernames" ).getChildren( "servername" );
        for ( int i = 0; i < serverNameConfs.length; i++ )
        {
            serverNames.add( serverNameConfs[i].getValue() );
        }
        if (serverNames.isEmpty()) {
            throw new ConfigurationException( "Fatal configuration error: no servernames specified!");
        }

        for (Iterator i = serverNames.iterator(); i.hasNext(); ) {
            getLogger().info("Handling mail for: " + i.next());
        }
        context.put(Constants.SERVER_NAMES, serverNames);


        // Get postmaster
        String postmaster = conf.getChild("postmaster").getValue("root@localhost");
        context.put(Constants.POSTMASTER, new MailAddress(postmaster));

        //Get localusers
        try {
            localusers = (UsersRepository) usersStore.getRepository("LocalUsers");
        } catch (Exception e) {
            getLogger().error("Cannot open private UserRepository");
            throw e;
        }
        //}
        compMgr.put("org.apache.james.services.UsersRepository", (Component)localusers);
        getLogger().info("Local users repository opened");

        // Get storage system
        if (conf.getChild("storage").getValue().equals("IMAP")) {
            useIMAPstorage = true;
        }
        
        //IMAPServer instance is controlled via assembly.xml. 
        //Assumption is that assembly.xml will set the correct IMAP Store 
        //if IMAP is enabled.
        //if (provideIMAP && (! useIMAPstorage)) {
        //    throw new ConfigurationException ("Fatal configuration error: IMAP service requires IMAP storage ");
        //}

        // Get the LocalInbox repository
        if (useIMAPstorage) {
            Configuration imapSetup = conf.getChild("imapSetup");
            String imapSystemClass = imapSetup.getAttribute("systemClass");
            String imapHostClass = imapSetup.getAttribute("hostClass");

            try {
                // We will need to use a no-args constructor for flexibility
                //imapSystem = new Class.forName(imapSystemClass).newInstance();
                imapSystem = new SimpleSystem();
                imapSystem.configure(conf.getChild("imapHost"));
                imapSystem.contextualize(context);
                imapSystem.compose(compMgr);
                if (imapSystem instanceof Initializable) {
                    ((Initializable)imapSystem).init();
                }
                compMgr.put("org.apache.james.imapserver.IMAPSystem", (Component)imapSystem);
                getLogger().info("Using SimpleSystem.");
                imapHost = (Host) Class.forName(imapHostClass).newInstance();
                //imapHost = new JamesHost();
                imapHost.configure(conf.getChild("imapHost"));
                imapHost.contextualize(context);
                imapHost.compose(compMgr);
                if (imapHost instanceof Initializable) {
                    ((Initializable)imapHost).init();
                }
                compMgr.put("org.apache.james.imapserver.Host", (Component)imapHost);
                getLogger().info("Using: " + imapHostClass);
            } catch (Exception e) {
                getLogger().error("Exception in IMAP Storage init: " + e.getMessage());
                throw e;
            }
        } else {
            Configuration inboxConf = conf.getChild("inboxRepository");
            Configuration inboxRepConf = inboxConf.getChild("repository");
            try {
                localInbox = (MailRepository) mailstore.select(inboxRepConf);
            } catch (Exception e) {
                getLogger().error("Cannot open private MailRepository");
                throw e;
            }
            inboxRootURL = inboxRepConf.getAttribute("destinationURL");
        }
        getLogger().info("Private Repository LocalInbox opened");

        // Add this to comp
        compMgr.put("org.apache.james.services.MailServer", this);

        Configuration spoolConf = conf.getChild("spoolRepository");
        Configuration spoolRepConf = spoolConf.getChild("repository");
        try {
            this.spool = (SpoolRepository) mailstore.select(spoolRepConf);
        } catch (Exception e) {
            getLogger().error("Cannot open private SpoolRepository");
            throw e;
        }
        getLogger().info("Private SpoolRepository Spool opened");
        //compMgr.put("org.apache.james.services.SpoolRepository", (Component)spool);
        // For mailet engine provide MailetContext
        //compMgr.put("org.apache.mailet.MailetContext", this);
        // For AVALON aware mailets and matchers, we put the Component object as
        // an attribute
        attributes.put(Constants.AVALON_COMPONENT_MANAGER, compMgr);

        // int threads = conf.getConfiguration("spoolmanagerthreads").getValueAsInt(1);
        //while (threads-- > 0) {
/*
        try {
            JamesSpoolManager spoolMgr = new JamesSpoolManager();
            setupLogger( spoolMgr, "SpoolManager" );
            spoolMgr.configure(conf.getChild("spoolmanager"));
            spoolMgr.contextualize(context);
            spoolMgr.compose(compMgr);
            spoolMgr.init();
            workerPool.execute(spoolMgr);
            getLogger().info("SpoolManager started");
        } catch (Exception e) {
            getLogger().error("Exception in SpoolManager init: " + e.getMessage());
            throw e;
        }
*/
        System.out.println("James "+VERSION);
        getLogger().info("JAMES ...init end");
    }


    public void sendMail(MimeMessage message) throws MessagingException {
        MailAddress sender = new MailAddress((InternetAddress)message.getFrom()[0]);
        Collection recipients = new HashSet();
        Address addresses[] = message.getAllRecipients();
        for (int i = 0; i < addresses.length; i++) {
            recipients.add(new MailAddress((InternetAddress)addresses[i]));
        }
        sendMail(sender, recipients, message);
    }


    public void sendMail(MailAddress sender, Collection recipients, MimeMessage message)
        throws MessagingException {
        //FIX ME!!! we should validate here MimeMessage.  - why? (SK)
        sendMail(sender, recipients, message, Mail.DEFAULT);
    }


    public void sendMail(MailAddress sender, Collection recipients, MimeMessage message, String state)
        throws MessagingException {
        //FIX ME!!! we should validate here MimeMessage.
        MailImpl mail = new MailImpl(getId(), sender, recipients, message);
        mail.setState(state);
        sendMail(mail);
    }


    public synchronized void sendMail(MailAddress sender, Collection recipients, InputStream msg)
        throws MessagingException {

        // parse headers
        MailHeaders headers = new MailHeaders(msg);
        // if headers do not contains minimum REQUIRED headers fields throw Exception
        if (!headers.isValid()) {
            throw new MessagingException("Some REQURED header field is missing. Invalid Message");
        }
        //        headers.setReceivedStamp("Unknown", (String) serverNames.elementAt(0));
        ByteArrayInputStream headersIn = new ByteArrayInputStream(headers.toByteArray());
        sendMail(new MailImpl(getId(), sender, recipients, new SequenceInputStream(headersIn, msg)));
    }


    public synchronized void sendMail(Mail mail) throws MessagingException {
        MailImpl mailimpl = (MailImpl)mail;
        try {
            spool.store(mailimpl);
        } catch (Exception e) {
            try {
                spool.remove(mailimpl);
            } catch (Exception ignored) {
            }
            throw new MessagingException("Exception spooling message: " + e.getMessage());
        }
        getLogger().info("Mail " + mailimpl.getName() + " pushed in spool");
    }

    /**
     * For POP3 server only - at the momment.
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
            String destination = inboxRootURL + userName + File.separator;;
            DefaultConfiguration mboxConf
                = new DefaultConfiguration("repository", "generated:AvalonFileRepository.compose()");
            mboxConf.addAttribute("destinationURL", destination);
            mboxConf.addAttribute("type", "MAIL");
            mboxConf.addAttribute("model", "SYNCHRONOUS");
            try {
                userInbox = (MailRepository) mailstore.select(mboxConf);
                mailboxes.put(userName, userInbox);
            } catch (Exception e) {
                getLogger().error("Cannot open user Mailbox" + e);
                throw new RuntimeException("Error in getUserInbox." + e);
            }
            return userInbox;
        }
    }

    public String getId() {
        return "Mail" + System.currentTimeMillis() + "-" + count++;
    }

    public static void main(String[] args) {
        System.out.println("ERROR!");
        System.out.println("Cannot execute James as a stand alone application.");
        System.out.println("To run James, you need to have the Avalon framework installed.");
        System.out.println("Please refer to the Readme file to know how to run James.");
    }

    //Methods for MailetContext

    public Collection getMailServers(String host) {
        DNSServer dnsServer = null;
        try {
            dnsServer = (DNSServer) compMgr.lookup("org.apache.james.services.DNSServer");
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

    public void bounce(Mail mail, String message) throws MessagingException {
        bounce(mail, message, getPostmaster());
    }

    public void bounce(Mail mail, String message, MailAddress bouncer) throws MessagingException {
        MimeMessage orig = mail.getMessage();
        //Create the reply message
        MimeMessage reply = (MimeMessage) orig.reply(false);
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
            part.setHeader("Content-Type", "text/plain");
            multipart.addBodyPart(part);

            //Add the original message as the second mime body part
            part = new MimeBodyPart();
            part.setContent(orig.getContent(), orig.getContentType());
            part.setHeader("Content-Type", orig.getContentType());
            multipart.addBodyPart(part);

            reply.setContent(multipart);
            reply.setHeader("Content-Type", multipart.getContentType());
        } catch (IOException ioe) {
            throw new MessagingException("Unable to create multipart body");
        }
        //Send it off...
        sendMail(bouncer, recipients, reply);
    }

    public boolean isLocalUser(String userAccout) {
        return localusers.contains(userAccout);
    }

    public MailAddress getPostmaster() {
        return (MailAddress)context.get(Constants.POSTMASTER);
    }

    public void storeMail(MailAddress sender, MailAddress recipient, MimeMessage message) {

        if (useIMAPstorage) {
            ACLMailbox mbox = null;
            try {
                String folderName = "#users." + recipient.getUser() + ".INBOX";
                getLogger().debug("Want to store to: " + folderName);
                mbox = imapHost.getMailbox(MailServer.MDA, folderName);
                if(mbox.store(message,MailServer.MDA)) {
                    getLogger().info("Message " + message.getMessageID() +" stored in " + folderName);
                } else {
                    throw new RuntimeException("Failed to store mail: ");
                }
                imapHost.releaseMailbox(MailServer.MDA, mbox);
                mbox = null;
            } catch (Exception e) {
                getLogger().error("Exception storing mail: " + e);
                e.printStackTrace();
                if (mbox != null) {
                    imapHost.releaseMailbox(MailServer.MDA, mbox);
                    mbox = null;
                }
                throw new RuntimeException("Exception storing mail: " + e);
            }
        } else {
            Collection recipients = new HashSet();
            recipients.add(recipient);
            MailImpl mailImpl = new MailImpl(getId(), sender, recipients, message);
            getUserInbox(recipient.getUser()).store(mailImpl);
        }
    }

    public int getMajorVersion() {
        return 1;
    }

    public int getMinorVersion() {
        return 2;
    }

    public boolean isLocalServer(String serverName) {
        List names = (List)context.get(Constants.SERVER_NAMES);
        return names.contains(serverName);
    }

    public String getServerInfo() {
        return "JAMES/1.2";
    }

    public void log(String message) {
        mailetLogger.info(message);
    }

    public void log(String message, Throwable t) {
        System.err.println(message);
        t.printStackTrace(); //DEBUG
        mailetLogger.info(message + ": " + t.getMessage());
    }

    /**
     * Adds a user to this mail server. Currently just adds user to a
     * UsersRepository.
     * <p> As we move to IMAP support this will also create mailboxes and
     * access control lists.
     *
     * @param userName String representing user name, that is the portion of
     * an email address before the '@<domain>'.
     * @param password String plaintext password
     * @returns boolean true if user added succesfully, else false.
     */
    public boolean addUser(String userName, String password) {
        localusers.addUser(userName, password);
        if (useIMAPstorage) {
            JamesHost jh = (JamesHost) imapHost;
            if (jh.createPrivateMailAccount(userName)) {
                getLogger().info("New MailAccount created for" + userName);
            }
        }
        return true;
    }

}
