/*
 * Copyright (C) The Apache Software Foundation. All rights reserved.
 *
 * This software is published under the terms of the Apache Software License
 * version 1.1, a copy of which has been included with this distribution in
 * the LICENSE file.
 */
package org.apache.mailet;
import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import java.io.Serializable;
import java.util.Collection;
import java.util.Date;
/**
 * Wrap a MimeMessage with routing information (from SMTP) such
 * as SMTP specified recipients, sender, and ip address and hostname
 * of sending server.  It also contains its state which represents
 * which processor in the mailet container it is currently running.
 * Special processor names are "root" and "error".
 *
 * @author Federico Barbieri <scoobie@systemy.it>
 * @author Serge Knystautas <sergek@lokitech.com>
 * @version 0.9
 */
public interface Mail extends Serializable, Cloneable {
    String GHOST = "ghost";
    String DEFAULT = "root";
    String ERROR = "error";
    String TRANSPORT = "transport";
    /**
     * Returns the MimeMessage stored in this message
     *
     * @return the MimeMessage that this Mail object wraps
     * @throws MessagingException - an error occured while loading this object
     */
    MimeMessage getMessage() throws MessagingException;
    /**
     * Returns a Collection of MailAddress objects that are recipients of this message
     *
     * @return a Collection of MailAddress objects that are recipients of this message
     */
    Collection getRecipients();
    /**
     * Method setRecipients.
     * @param recipients a Collection of MailAddress Objects representing the recipients of this message
     */
    void setRecipients(Collection recipients);
    /**
     * The sender of the message, as specified by the MAIL FROM header, or internally defined
     *
     * @return a MailAddress of the sender of this message
     */
    MailAddress getSender();
    /**
     * The current state of the message, such as GHOST, ERROR, or DEFAULT
     *
     * @return the state of this message
     */
    String getState();
    /**
     * The remote hostname of the server that connected to send this message
     *
     * @return a String of the hostname of the server that connected to send this message
     */
    String getRemoteHost();
    /**
     * The remote ip address of the server that connected to send this message
     *
     * @return a String of the ip address of the server that connected to send this message
     */
    String getRemoteAddr();
    /**
     * The error message, if any, associated with this message.  Not sure why this is needed.
     *
     * @return a String of a descriptive error message
     */
    String getErrorMessage();
    /**
     * Sets the error message associated with this message.  Not sure why this is needed.
     *
     * @param msg - a descriptive error message
     */
    void setErrorMessage(String msg);
    /**
     * Sets the MimeMessage associated with this message via the object.
     *
     * @param message - the new MimeMessage that this Mail object will wrap
     */
    void setMessage(MimeMessage message);
    /**
     * Sets the state of this message.
     *
     * @param state - the new state of this message
     */
    void setState(String state);
    /**
     * Method getUID.
     * Mainly used to log the progress of mails through processing the string returned need only be unique to this installation of the system.
     * @return a String Unique Identifier in this system.
     */
    String getName();
    /**
     * Method setName. Set the internal unique identifier of this Mail which need only be unique to this installation of the system.
     * @param name the UID
     */
    void setName(String name);
    /**
     * Method setLastUpdated. Used to set the internal timestamp of the Mail during processing.
     * @param date
     */
    void setLastUpdated(Date date);
    /**
     * Method getLastUpdated.
     * @return Date. timestamp of the last action which changed this Mail
     */
    Date getLastUpdated();
}
