/*
 * Copyright (C) The Apache Software Foundation. All rights reserved.
 *
 * This software is published under the terms of the Apache Software License
 * version 1.1, a copy of which has been included  with this distribution in
 * the LICENSE file.
 */
package org.apache.james.core;

import java.io.*;
import java.text.ParseException;
import java.util.*;
import javax.activation.*;
import javax.mail.*;
import javax.mail.internet.*;

/**
 * This object wraps a MimeMessage, only loading the underlying MimeMessage
 * object when needed.  Also tracks if changes were made to reduce
 * unnecessary saves.
 */
public class MimeMessageWrapper extends MimeMessage {

    /**
     * Can provide an input stream to the data
     */
    MimeMessageSource source = null;
    /**
     * The Internet headers in memory
     */
    MailHeaders headers = null;
    /**
     * The mime message in memory
     */
    MimeMessage message = null;
    /**
     * Record whether a change was made to this message
     */
    boolean modified = false;
    /**
     * How to format a mail date
     */
    MailDateFormat mailDateFormat = new MailDateFormat();

    public MimeMessageWrapper(MimeMessageSource source) {
        super(javax.mail.Session.getDefaultInstance(System.getProperties(), null));
        this.source = source;
    }

    public MimeMessageWrapper(Session session, MimeMessageSource source) {
        super(session);
        this.source = source;
    }

    /**
     * Instantiate the headers
     */
    private synchronized void loadHeaders() throws MessagingException {
        if (headers != null) {
            //Another thread has already loaded these headers
            return;
        }
        //System.err.println("parsing headers of " + this);
        try {
            InputStream in = source.getInputStream();
            headers = new MailHeaders(in);
            in.close();
        } catch (IOException ioe) {
            ioe.printStackTrace();
            throw new MessagingException("Unable to parse headers from stream: " + ioe.getMessage());
        }
    }

    /**
     * Internal implementations
     */
    private synchronized void loadMessage() throws MessagingException {
        if (message != null) {
            //Another thread has already loaded this message
            return;
        }
        //System.err.println("parsing headers and message of " + this);
        try {
            InputStream in = source.getInputStream();
            headers = new MailHeaders(in);

            ByteArrayInputStream headersIn
                    = new ByteArrayInputStream(headers.toByteArray());
            in = new SequenceInputStream(headersIn, in);

            message = new MimeMessage(session, in);
            in.close();
        } catch (IOException ioe) {
            ioe.printStackTrace();
            throw new MessagingException("Unable to parse stream: " + ioe.getMessage());
        }
    }

    /**
     * Internal implementation to get InternetAddress headers
     */
    private Address[] getAddressHeader(String name) throws MessagingException {
        String addr = getHeader(name, ",");
        if (addr == null) {
            return null;
        } else {
            return InternetAddress.parse(addr);
        }
    }


    /**
     * Internal implementation to find headers
     */
    private String getHeaderName(Message.RecipientType recipienttype) throws MessagingException {
        String s;
        if (recipienttype == javax.mail.Message.RecipientType.TO) {
            s = "To";
        } else if (recipienttype == javax.mail.Message.RecipientType.CC) {
            s = "Cc";
        } else if (recipienttype == javax.mail.Message.RecipientType.BCC) {
            s = "Bcc";
        } else if (recipienttype == RecipientType.NEWSGROUPS) {
            s = "Newsgroups";
        } else {
            throw new MessagingException("Invalid Recipient Type");
        }
        return s;
    }


    /**
     * Special methods you can call
     */
    public boolean isModified() {
        return modified;
    }

    /**
     * Rewritten for optimization purposes
     */
    public void writeTo(OutputStream os) throws IOException, MessagingException {
        if (message == null || !isModified()) {
            //We do not want to instantiate the message... just read from source
            //  and write to this outputstream
            byte[] block = new byte[1024];
            int read = 0;
            InputStream in = source.getInputStream();
            while ((read = in.read(block)) > 0) {
                os.write(block, 0, read);
            }
            in.close();
        } else {
            message.saveChanges();
            message.writeTo(os);
        }
    }

    /**
     * Rewritten for optimization purposes
     */
    public void writeTo(OutputStream os, String[] ignoreList) throws IOException, MessagingException {
        writeTo(os, os, ignoreList);
    }

    /**
     * Write
     */
    public void writeTo(OutputStream headerOs, OutputStream bodyOs) throws IOException, MessagingException {
        writeTo(headerOs, bodyOs, new String[0]);
    }

    public void writeTo(OutputStream headerOs, OutputStream bodyOs, String[] ignoreList) throws IOException, MessagingException {
        if (message == null || !isModified()) {
            //We do not want to instantiate the message... just read from source
            //  and write to this outputstream

            //First handle the headers
            InputStream in = source.getInputStream();
            InternetHeaders headers = new InternetHeaders(in);
            PrintStream pos = new PrintStream(headerOs);
            for (Enumeration e = headers.getNonMatchingHeaderLines(ignoreList); e.hasMoreElements(); ) {
                String header = (String)e.nextElement();
                pos.print(header);
                pos.print("\r\n");
            }
            pos.print("\r\n");
            pos.flush();
            byte[] block = new byte[1024];
            int read = 0;
            while ((read = in.read(block)) > 0) {
                bodyOs.write(block, 0, read);
            }
            in.close();
        } else {
            writeTo(message, headerOs, bodyOs, ignoreList);
        }
    }

    /**
     * Convenience method to take any MimeMessage and write the headers and body to two
     * different output streams
     */
    public static void writeTo(MimeMessage message, OutputStream headerOs, OutputStream bodyOs) throws IOException, MessagingException {
        writeTo(message, headerOs, bodyOs, null);
    }

    /**
     * Convenience method to take any MimeMessage and write the headers and body to two
     * different output streams, with an ignore list
     */
    public static void writeTo(MimeMessage message, OutputStream headerOs, OutputStream bodyOs, String[] ignoreList) throws IOException, MessagingException {
        if (message instanceof MimeMessageWrapper) {
            MimeMessageWrapper wrapper = (MimeMessageWrapper)message;
            wrapper.writeTo(headerOs, bodyOs, ignoreList);
        } else {
            message.saveChanges();

            //Write the headers (minus ignored ones)
            Enumeration headers = message.getNonMatchingHeaderLines(ignoreList);
            PrintStream pos = new PrintStream(headerOs);
            while (headers.hasMoreElements()) {
                pos.print((String)headers.nextElement());
                pos.print("\r\n");
            }
            pos.flush();

            //Write the message without any headers

            //Get a list of all headers
            Vector headerNames = new Vector();
            Enumeration allHeaders = message.getAllHeaders();
            while (allHeaders.hasMoreElements()) {
                Header header = (Header)allHeaders.nextElement();
                headerNames.add(header.getName());
            }
            String headerNamesArray[] = (String[])headerNames.toArray(new String[0]);

            //Write message to bodyOs, ignoring all headers (without writing any headers)
            message.writeTo(bodyOs, headerNamesArray);
        }
    }


    /**
     * Various reader methods
     */

    public Address[] getFrom() throws MessagingException {
        if (headers == null) {
            loadHeaders();
        }
        Address from[] = getAddressHeader("From");
        if(from == null) {
            from = getAddressHeader("Sender");
        }
        return from;
    }

    public Address[] getRecipients(Message.RecipientType type) throws MessagingException {
        if (headers == null) {
            loadHeaders();
        }
        if (type == RecipientType.NEWSGROUPS) {
            String s = headers.getHeader("Newsgroups", ",");
            if(s == null) {
                return null;
            } else {
                return NewsAddress.parse(s);
            }
        } else {
            return getAddressHeader(getHeaderName(type));
        }
    }

    public Address[] getAllRecipients() throws MessagingException {
        if (headers == null) {
            loadHeaders();
        }
        Address toAddresses[] = getRecipients(RecipientType.TO);
        Address ccAddresses[] = getRecipients(RecipientType.CC);
        Address bccAddresses[] = getRecipients(RecipientType.BCC);
        Address newsAddresses[] = getRecipients(RecipientType.NEWSGROUPS);
        if(ccAddresses == null && bccAddresses == null && newsAddresses == null) {
            return toAddresses;
        }
        int i = (toAddresses == null ? 0 : toAddresses.length)
                + (ccAddresses == null ? 0 : ccAddresses.length)
                + (bccAddresses == null ? 0 : bccAddresses.length)
                + (newsAddresses == null ? 0 : newsAddresses.length);
        Address allAddresses[] = new Address[i];
        int j = 0;
        if (toAddresses != null) {
            System.arraycopy(toAddresses, 0, allAddresses, j, toAddresses.length);
            j += toAddresses.length;
        }
        if(ccAddresses != null) {
            System.arraycopy(ccAddresses, 0, allAddresses, j, ccAddresses.length);
            j += ccAddresses.length;
        }
        if(bccAddresses != null) {
            System.arraycopy(bccAddresses, 0, allAddresses, j, bccAddresses.length);
            j += bccAddresses.length;
        }
        if(newsAddresses != null) {
            System.arraycopy(newsAddresses, 0, allAddresses, j, newsAddresses.length);
            j += newsAddresses.length;
        }
        return allAddresses;
    }

    public Address[] getReplyTo() throws MessagingException {
        if (headers == null) {
            loadHeaders();
        }
        Address replyTo[] = getAddressHeader("Reply-To");
        if(replyTo == null) {
            replyTo = getFrom();
        }
        return replyTo;
    }

    public String getSubject() throws MessagingException {
        if (headers == null) {
            loadHeaders();
        }
        String subject = getHeader("Subject", null);
        if (subject == null) {
            return null;
        }
        try {
            return MimeUtility.decodeText(subject);
        } catch(UnsupportedEncodingException _ex) {
            return subject;
        }
    }

    public Date getSentDate() throws MessagingException {
        if (headers == null) {
            loadHeaders();
        }
        String header = getHeader("Date", null);
        if(header != null) {
            try {
                synchronized(mailDateFormat) {
                    Date date = mailDateFormat.parse(header);
                    return date;
                }
            } catch(ParseException _ex) {
                return null;
            }
        } else {
            return null;
        }
    }

    public Date getReceivedDate() throws MessagingException {
        //if (headers == null) {
        //    loadHeaders();
        //}
        return null;
    }

    public int getSize() throws MessagingException {
        try {
            if (message == null) {
                return (int)source.getSize();
            } else {
                //Would be better to use in memory mimemessage
                //Need to figure out size of message plus size of headers...
                return message.getSize();
                //return source.getSize();
            }
        } catch (IOException ioe) {
            ioe.printStackTrace();
            throw new MessagingException("Trouble in getSize", ioe);
        }
    }

    /**
     * Corrects JavaMail 1.1 version which always returns -1.
     * Only corrected for content less than 5000 bytes,
     * to avoid memory hogging.
     */
    public int getLineCount() throws MessagingException {
        if (message == null) {
            loadMessage();
        }
        if (content == null) {
            return -1;
        }
        int size = content.length; // size of byte array
        int lineCount = 0;
        if (size < 5000) {
            for (int i=0; i < size -1; i++) {
                if (content[i] == '\r' && content[i+1] == '\n') {
                    lineCount++;
                }
            }
        } else {
            lineCount = -1;
        }
        return lineCount;
    }

    /**
     * Returns size of message, ie headers and content. Current implementation
     * actually returns number of characters in headers plus number of bytes
     * in the internal content byte array.
     */
    public int getMessageSize() throws MessagingException {
        int contentSize = content.length;
        int headerSize = 0;
        Enumeration e = getAllHeaderLines();
        while (e.hasMoreElements()) {
            headerSize += ((String)e.nextElement()).length();
        }
        return headerSize + contentSize;
    }

    public String getContentType() throws MessagingException {
        if (headers == null) {
            loadHeaders();
        }
        String value = getHeader("Content-Type", null);
        if (value == null) {
            return "text/plain";
        } else {
            return value;
        }
    }

    public boolean isMimeType(String mimeType) throws MessagingException {
        if (message == null) {
            loadMessage();
        }
        return message.isMimeType(mimeType);
    }

    public String getDisposition() throws MessagingException {
        if (message == null) {
            loadMessage();
        }
        return message.getDisposition();
    }

    public String getEncoding() throws MessagingException {
        if (message == null) {
            loadMessage();
        }
        return message.getEncoding();
    }

    public String getContentID() throws MessagingException {
        if (headers == null) {
            loadHeaders();
        }
        return getHeader("Content-Id", null);
    }

    public String getContentMD5() throws MessagingException {
        if (headers == null) {
            loadHeaders();
        }
        return getHeader("Content-MD5", null);
    }

    public String getDescription() throws MessagingException {
        if (message == null) {
            loadMessage();
        }
        return message.getDescription();
    }

    public String[] getContentLanguage() throws MessagingException {
        if (message == null) {
            loadMessage();
        }
        return message.getContentLanguage();
    }

    public String getMessageID() throws MessagingException {
        if (headers == null) {
            loadHeaders();
        }
        return getHeader("Message-ID", null);
    }

    public String getFileName() throws MessagingException {
        if (message == null) {
            loadMessage();
        }
        return message.getFileName();
    }

    public InputStream getInputStream() throws IOException, MessagingException {
        if (message == null) {
            return source.getInputStream();
        } else {
            return message.getInputStream();
        }
    }

    public DataHandler getDataHandler() throws MessagingException {
        if (message == null) {
            loadMessage();
        }
        return message.getDataHandler();
    }

    public Object getContent() throws IOException, MessagingException {
        if (message == null) {
            loadMessage();
        }
        return message.getContent();
    }

    public String[] getHeader(String name) throws MessagingException {
        if (headers == null) {
            loadHeaders();
        }
        return headers.getHeader(name);
    }

    public String getHeader(String name, String delimiter) throws MessagingException {
        if (headers == null) {
            loadHeaders();
        }
        return headers.getHeader(name, delimiter);
    }

    public Enumeration getAllHeaders() throws MessagingException {
        if (headers == null) {
            loadHeaders();
        }
        return headers.getAllHeaders();
    }

    public Enumeration getMatchingHeaders(String[] names) throws MessagingException {
        if (headers == null) {
            loadHeaders();
        }
        return headers.getMatchingHeaders(names);
    }

    public Enumeration getNonMatchingHeaders(String[] names) throws MessagingException {
        if (headers == null) {
            loadHeaders();
        }
        return headers.getNonMatchingHeaders(names);
    }

    public Enumeration getAllHeaderLines() throws MessagingException {
        if (headers == null) {
            loadHeaders();
        }
        return headers.getAllHeaderLines();
    }

    public Enumeration getMatchingHeaderLines(String[] names) throws MessagingException {
        if (headers == null) {
            loadHeaders();
        }
        return headers.getMatchingHeaderLines(names);
    }

    public Enumeration getNonMatchingHeaderLines(String[] names) throws MessagingException {
        if (headers == null) {
            loadHeaders();
        }
        return headers.getNonMatchingHeaderLines(names);
    }

    public Flags getFlags() throws MessagingException {
        if (message == null) {
            loadMessage();
        }
        return message.getFlags();
    }

    public boolean isSet(Flags.Flag flag) throws MessagingException {
        if (message == null) {
            loadMessage();
        }
        return message.isSet(flag);
    }


    /**
     * Writes content only, ie not headers, to the specified outputstream.
     */
    public void writeContentTo(OutputStream outs)
            throws java.io.IOException, MessagingException {
        int size = content.length; // size of byte array
        int chunk = 1000; //arbitrary choice - ideas welcome
        int pointer = 0;
        while (pointer < size) {
            if ((size - pointer) > chunk) {
                outs.write(content, pointer, chunk);
            } else {
                outs.write(content, pointer, size-pointer);
            }
            pointer += chunk;
        }
    }












    /**
     * Various writer methods
     */
    public void setFrom(Address address) throws MessagingException {
        if (message == null) {
            loadMessage();
        }
        modified = true;
        message.setFrom(address);
    }

    public void setFrom() throws MessagingException {
        if (message == null) {
            loadMessage();
        }
        modified = true;
        message.setFrom();
    }

    public void addFrom(Address[] addresses) throws MessagingException {
        if (message == null) {
            loadMessage();
        }
        modified = true;
        message.addFrom(addresses);
    }

    public void setRecipients(Message.RecipientType type, Address[] addresses) throws MessagingException {
        if (message == null) {
            loadMessage();
        }
        modified = true;
        message.setRecipients(type, addresses);
    }

    public void addRecipients(Message.RecipientType type, Address[] addresses) throws MessagingException {
        if (message == null) {
            loadMessage();
        }
        modified = true;
        message.addRecipients(type, addresses);
    }

    public void setReplyTo(Address[] addresses) throws MessagingException {
        if (message == null) {
            loadMessage();
        }
        modified = true;
        message.setReplyTo(addresses);
    }

    public void setSubject(String subject) throws MessagingException {
        if (message == null) {
            loadMessage();
        }
        modified = true;
        message.setSubject(subject);
    }

    public void setSubject(String subject, String charset) throws MessagingException {
        if (message == null) {
            loadMessage();
        }
        modified = true;
        message.setSubject(subject, charset);
    }

    public void setSentDate(Date d) throws MessagingException {
        if (message == null) {
            loadMessage();
        }
        modified = true;
        message.setSentDate(d);
    }

    public void setDisposition(String disposition) throws MessagingException {
        if (message == null) {
            loadMessage();
        }
        modified = true;
        message.setDisposition(disposition);
    }

    public void setContentID(String cid) throws MessagingException {
        if (message == null) {
            loadMessage();
        }
        modified = true;
        message.setContentID(cid);
    }

    public void setContentMD5(String md5) throws MessagingException {
        if (message == null) {
            loadMessage();
        }
        modified = true;
        message.setContentMD5(md5);
    }

    public void setDescription(String description) throws MessagingException {
        if (message == null) {
            loadMessage();
        }
        modified = true;
        message.setDescription(description);
    }

    public void setDescription(String description, String charset) throws MessagingException {
        if (message == null) {
            loadMessage();
        }
        modified = true;
        message.setDescription(description, charset);
    }

    public void setContentLanguage(String[] languages) throws MessagingException {
        if (message == null) {
            loadMessage();
        }
        modified = true;
        message.setContentLanguage(languages);
    }

    public void setFileName(String filename) throws MessagingException {
        if (message == null) {
            loadMessage();
        }
        modified = true;
        message.setFileName(filename);
    }

    public void setDataHandler(DataHandler dh) throws MessagingException {
        if (message == null) {
            loadMessage();
        }
        modified = true;
        message.setDataHandler(dh);
    }

    public void setContent(Object o, String type) throws MessagingException {
        if (message == null) {
            loadMessage();
        }
        modified = true;
        message.setContent(o, type);
    }

    public void setText(String text) throws MessagingException {
        if (message == null) {
            loadMessage();
        }
        modified = true;
        message.setText(text);
    }

    public void setText(String text, String charset) throws MessagingException {
        if (message == null) {
            loadMessage();
        }
        modified = true;
        message.setText(text, charset);
    }

    public void setContent(Multipart mp) throws MessagingException {
        if (message == null) {
            loadMessage();
        }
        modified = true;
        message.setContent(mp);
    }

    public Message reply(boolean replyToAll) throws MessagingException {
        if (message == null) {
            loadMessage();
        }
        modified = true;
        return message.reply(replyToAll);
    }

    public void setHeader(String name, String value) throws MessagingException {
        if (message == null) {
            loadMessage();
        }
        modified = true;
        message.setHeader(name, value);
    }

    public void addHeader(String name, String value) throws MessagingException {
        if (message == null) {
            loadMessage();
        }
        modified = true;
        message.addHeader(name, value);
    }

    public void removeHeader(String name) throws MessagingException {
        if (message == null) {
            loadMessage();
        }
        modified = true;
        message.removeHeader(name);
    }

    public void addHeaderLine(String line) throws MessagingException {
        if (message == null) {
            loadMessage();
        }
        message.addHeaderLine(line);
    }

    public void setFlags(Flags flag, boolean set) throws MessagingException {
        if (message == null) {
            loadMessage();
        }
        modified = true;
        message.setFlags(flag, set);
    }

    public void saveChanges() throws MessagingException {
        if (message == null) {
            loadMessage();
        }
        modified = true;
        message.saveChanges();
    }
}
