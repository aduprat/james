/*
 * Copyright (C) The Apache Software Foundation. All rights reserved.
 *
 * This software is published under the terms of the Apache Software License
 * version 1.1, a copy of which has been included with this distribution in
 * the LICENSE file.
 */
package org.apache.james.services;

import org.apache.mailet.Mail;
import org.apache.mailet.MailAddress;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import java.io.InputStream;
import java.util.Collection;

/**
 * The interface for Phoenix blocks to the James MailServer
 *
 * @author  Federico Barbieri <scoobie@pop.systemy.it>
 * @author <a href="mailto:charles@benett1.demon.co.uk">Charles Benett</a>
 *
 * This is $Revision: 1.8 $
 * Committed on $Date: 2002/07/30 10:38:35 $ by: $Author: danny $
 */
public interface MailServer
{
    String ROLE = "org.apache.james.services.MailServer";

    /**
     * Reserved user name for the mail delivery agent for multi-user mailboxes
     */
    String MDA = "JamesMDA";

    /**
     * Reserved user name meaning all users for multi-user mailboxes
     */
    String ALL = "AllMailUsers";

    /**
     * Pass a MimeMessage to this MailServer for processing
     *
     * @param sender - the sender of the message
     * @param recipients - a Collection of String objects of recipients
     * @param msg - the MimeMessage of the headers and body content of
     * the outgoing message
     * @throws MessagingException - if the message fails to parse
     */
    void sendMail(MailAddress sender, Collection recipients, MimeMessage msg)
        throws MessagingException;

    /**
     * Pass a MimeMessage to this MailServer for processing
     *
     * @param sender - the sender of the message
     * @param recipients - a Collection of String objects of recipients
     * @param msg - an InputStream containing the headers and body content of
     * the outgoing message
     * @throws MessagingException - if the message fails to parse
     */
    void sendMail(MailAddress sender, Collection recipients, InputStream msg)
        throws MessagingException;

    /**
     * Pass a Mail to this MailServer for processing
     *
     * @param sender - the sender of the message
     * @param recipients - a Collection of String objects of recipients
     * @param msg - an InputStream containing the headers and body content of
     * the outgoing message
     * @throws MessagingException - if the message fails to parse
     */
    void sendMail(Mail mail)
        throws MessagingException;

    /**
     * Retrieve the primary mailbox for userName. For POP3 style stores this
     * is their (sole) mailbox.
     *
     * @param sender - the name of the user
     * @return a reference to an initialised mailbox
     */
    MailRepository getUserInbox(String userName);

    String getId();

    /**
     * Adds a new user to the mail system with userName. For POP3 style stores
     * this may only involve adding the user to the UsersStore.
     *
     * @param sender - the name of the user
     * @return a reference to an initialised mailbox
     */
    boolean addUser(String userName, String password);

    /**
     * Checks if a server is serviced by mail context
     *
     * @param serverName - name of server.
     * @return true if server is local, i.e. serviced by this mail context
     */
    boolean isLocalServer(String serverName);
}
