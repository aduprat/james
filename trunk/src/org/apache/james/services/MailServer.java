/*****************************************************************************
 * Copyright (C) The Apache Software Foundation. All rights reserved.        *
 * ------------------------------------------------------------------------- *
 * This software is published under the terms of the Apache Software License *
 * version 1.1, a copy of which has been included  with this distribution in *
 * the LICENSE file.                                                         *
 *****************************************************************************/

package org.apache.james.services;

import javax.mail.internet.*;
import javax.mail.MessagingException;
import java.util.Collection;

import org.apache.mailet.Mail;
import org.apache.mailet.MailAddress;

import java.io.InputStream;
import org.apache.phoenix.Service;

/**
 * The interface for Phoenix blocks to the James MailServer
 *
 * @author  Federico Barbieri <scoobie@pop.systemy.it>
 * @author <a href="mailto:charles@benett1.demon.co.uk">Charles Benett</a>
 */

public interface MailServer extends Service {

    /**
     * Reserved user name for teh mail delivery agent for multi-user mailboxes
     */
    public static final String MDA = "JamesMDA"; 

    /**
     * Reserved user name meaning all users for multi-user mailboxes
     */
    public static final String ALL = "AllMailUsers";

    public void sendMail(MailAddress sender, Collection recipients, MimeMessage msg)
    throws MessagingException;

    public void sendMail(MailAddress sender, Collection recipients, InputStream msg)
    throws MessagingException;

    public void sendMail(Mail mail)
    throws MessagingException;

    public MailRepository getUserInbox(String userName);

    public String getId();

    public boolean addUser(String userName, String password);
}
