/*
 * Copyright (C) The Apache Software Foundation. All rights reserved.
 *
 * This software is published under the terms of the Apache Software License
 * version 1.1, a copy of which has been included with this distribution in
 * the LICENSE file.
 */
package org.apache.james.core;

import org.apache.mailet.Mail;
import org.apache.mailet.MailAddress;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.ParseException;
import java.io.*;
import java.util.Collection;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashSet;

/**
 * Wrap a MimeMessage adding routing informations (from SMTP) and same simple API.
 * @author Federico Barbieri <scoobie@systemy.it>
 * @author Serge Knystautas <sergek@lokitech.com>
 * @version 0.9
 */
public class MailImpl implements Mail {
    //We hardcode the serialVersionUID so that from James 1.2 on,
    //  MailImpl will be deserializable (so your mail doesn't get lost)
    public static final long serialVersionUID = -4289663364703986260L;

    private String errorMessage;
    private String state;
    private MimeMessage message;
    private MailAddress sender;
    private Collection recipients;
    private String name;
    private String remoteHost = "localhost";
    private String remoteAddr = "127.0.0.1";
    private Date lastUpdated = new Date();

    public MailImpl() {
        setState(Mail.DEFAULT);
    }

    public MailImpl(String name, MailAddress sender, Collection recipients) {
        this();
        this.name = name;
        this.sender = sender;
        this.recipients = recipients;
    }

    public MailImpl(String name, MailAddress sender, Collection recipients, InputStream messageIn)
            throws MessagingException {
        this(name, sender, recipients);
        MimeMessageSource source = new MimeMessageInputStreamSource(name, messageIn);
        MimeMessageWrapper wrapper = new MimeMessageWrapper(source);
        this.setMessage(wrapper);
    }

    public MailImpl(String name, MailAddress sender, Collection recipients, MimeMessage message) {
        this(name, sender, recipients);
        this.setMessage(message);
    }

    public void clean() {
        message = null;
    }

    public Mail duplicate() {
        try {
            MailImpl newMail = new MailImpl(name, sender, recipients, getMessage());
            newMail.setRemoteHost(remoteHost);
            newMail.setRemoteAddr(remoteAddr);
            newMail.setLastUpdated(lastUpdated);
            return newMail;
        } catch (MessagingException me) {
        }
        return (Mail) null;
    }

    public Mail duplicate(String newName) {
        try {
            MailImpl newMail = new MailImpl(newName, sender, recipients, getMessage());
            newMail.setRemoteHost(remoteHost);
            newMail.setRemoteAddr(remoteAddr);
            newMail.setLastUpdated(lastUpdated);
            return newMail;
        } catch (MessagingException me) {
        }
        return (Mail) null;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public MimeMessage getMessage() throws MessagingException {
        return message;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public Collection getRecipients() {
        return recipients;
    }

    public MailAddress getSender() {
        return sender;
    }

    public String getState() {
        return state;
    }

    public String getRemoteHost() {
        return remoteHost;
    }

    public String getRemoteAddr() {
        return remoteAddr;
    }

    public Date getLastUpdated() {
        return lastUpdated;
    }

    /**
     * <p>Return the size of the message including its headers.
     * MimeMessage.getSize() method only returns the size of the
     * message body.</p>
     *
     * <p>Note: this size is not guaranteed to be accurate - see Sun's
     * documentation of MimeMessage.getSize().</p>
     *
     * @return approximate size of full message including headers.
     *
     * @author Stuart Roebuck <stuart.roebuck@adolos.co.uk>
     */
    public long getMessageSize() throws MessagingException {
        //If we have a MimeMessageWrapper, then we can ask it for just the
        //  message size and skip calculating it
        if (message instanceof MimeMessageWrapper) {
            MimeMessageWrapper wrapper = (MimeMessageWrapper)message;
            return wrapper.getMessageSize();
        }

        //SK: Should probably eventually store this as a locally
        //  maintained value (so we don't have to load and reparse
        //  messages each time).
        long size = message.getSize();
        Enumeration e = message.getAllHeaderLines();
        while (e.hasMoreElements()) {
            size += ((String)e.nextElement()).length();
        }
        return size;
    }

    private void readObject(java.io.ObjectInputStream in) throws IOException, ClassNotFoundException {
        try {
            Object obj = in.readObject();
            if (obj == null) {
                sender = null;
            } else if (obj instanceof String) {
                sender = new MailAddress((String)obj);
            } else if (obj instanceof MailAddress) {
                sender = (MailAddress)obj;
            }
        } catch (ParseException pe) {
            throw new IOException("Error parsing sender address: " + pe.getMessage());
        }
        recipients = (Collection) in.readObject();
        state = (String) in.readObject();
        errorMessage = (String) in.readObject();
        name = (String) in.readObject();
        remoteHost = (String) in.readObject();
        remoteAddr = (String) in.readObject();
        lastUpdated = (Date) in.readObject();
    }

    public void setErrorMessage(String msg) {
        this.errorMessage = msg;
    }

    public void setMessage(MimeMessage message) {
        this.message = message;
    }

    public void setRecipients(Collection recipients) {
        this.recipients = recipients;
    }

    public void setSender(MailAddress sender) {
        this.sender = sender;
    }

    public void setState(String state) {
        this.state = state;
    }

    public void setRemoteHost(String remoteHost) {
        this.remoteHost = remoteHost;
    }

    public void setRemoteAddr(String remoteAddr) {
        this.remoteAddr = remoteAddr;
    }

    public void setLastUpdated(Date lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    public void writeMessageTo(OutputStream out) throws IOException, MessagingException {
        if (message != null) {
            message.writeTo(out);
        } else {
            throw new MessagingException("No message set for this MailImpl.");
        }
    }

    private void writeObject(java.io.ObjectOutputStream out) throws IOException {
        lastUpdated = new Date();
        out.writeObject(sender);
        out.writeObject(recipients);
        out.writeObject(state);
        out.writeObject(errorMessage);
        out.writeObject(name);
        out.writeObject(remoteHost);
        out.writeObject(remoteAddr);
        out.writeObject(lastUpdated);
    }

    public Mail bounce(String message) throws MessagingException {

        //This sends a message to the james component that is a bounce of the sent message
        MimeMessage original = getMessage();
        MimeMessage reply = (MimeMessage) original.reply(false);
        reply.setSubject("Re: " + original.getSubject());
        Collection recipients = new HashSet();
        recipients.add(getSender());
        InternetAddress addr[] = {new InternetAddress(getSender().toString())};
        reply.setRecipients(Message.RecipientType.TO, addr);
        reply.setFrom(new InternetAddress(getRecipients().iterator().next().toString()));
        reply.setText(message);
        reply.setHeader("Message-Id", "replyTo-" + getName());

        return new MailImpl("replyTo-" + getName(), new MailAddress(getRecipients().iterator().next().toString()), recipients, reply);
    }

    public void writeContentTo(OutputStream out, int lines)
           throws IOException, MessagingException {
        String line;
        BufferedReader br;
        if(message != null) {
            br = new BufferedReader(new InputStreamReader(message.getInputStream()));
            while(lines-- > 0) {
                if((line = br.readLine()) == null)  break;
                line += "\r\n";
                out.write(line.getBytes());
            }
        } else {
            throw new MessagingException("No message set for this MailImpl.");
        }
    }
}
