package org.apache.james.core;

import java.io.*;
import java.util.*;
import javax.activation.*;
import javax.mail.*;
import javax.mail.internet.*;

public class MimeMessageWrapper extends MimeMessage {

    /**
     * Can provide an input stream to the data
     */
    MimeMessageSource source = null;
    /**
     * The mime message in memory
     */
    MimeMessage message = null;
    /**
     * Record whether a change was made to this message
     */
    boolean modified = false;

/*
    public MimeMessageWrapper(InputStream in) {
        this(new MimeMessageInputStreamSource(in));
    }
*/
    public MimeMessageWrapper(MimeMessageSource source) {
        super(javax.mail.Session.getDefaultInstance(System.getProperties(), null));
        this.source = source;
    }

    public MimeMessageWrapper(Session session, MimeMessageSource source) {
        super(session);
        this.source = source;
    }

    /**
     * Internal implementations
     */
    private synchronized void loadMessage() throws MessagingException {
        if (message != null) {
            //Another thread has already loaded this message
            return;
        }
        try {
            InputStream in = source.getInputStream();
            message = new MimeMessage(session, in);
            in.close();
        } catch (IOException ioe) {
            ioe.printStackTrace();
            throw new MessagingException("Unable to parse stream: " + ioe.getMessage());
        }
    }

    /**
     * Special methods you can call
     */
    public boolean isModified() {
        return modified;
    }

    /**
     * Methods that should be rewritten for optimization purposes
     */
    public void writeTo(OutputStream os) throws IOException, MessagingException {
        if (message == null) {
            loadMessage();
        }
        message.writeTo(os);
    }

    public void writeTo(OutputStream os, String[] ignoreList) throws IOException, MessagingException {
        if (message == null) {
            loadMessage();
        }
        message.writeTo(os, ignoreList);
    }


    /**
     * Various reader methods
     */

    public Address[] getFrom() throws MessagingException {
        if (message == null) {
            loadMessage();
        }
        return message.getFrom();
    }

    public Address[] getRecipients(Message.RecipientType type) throws MessagingException {
        if (message == null) {
            loadMessage();
        }
        return message.getRecipients(type);
    }

    public Address[] getAllRecipients() throws MessagingException {
        if (message == null) {
            loadMessage();
        }
        return message.getAllRecipients();
    }

    public Address[] getReplyTo() throws MessagingException {
        if (message == null) {
            loadMessage();
        }
        return message.getReplyTo();
    }

    public String getSubject() throws MessagingException {
        if (message == null) {
            loadMessage();
        }
        return message.getSubject();
    }

    public Date getSentDate() throws MessagingException {
        if (message == null) {
            loadMessage();
        }
        return message.getSentDate();
    }

    public Date getReceivedDate() throws MessagingException {
        if (message == null) {
            loadMessage();
        }
        return message.getReceivedDate();
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
        if (message == null) {
            loadMessage();
        }
        return message.getContentType();
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
        if (message == null) {
            loadMessage();
        }
        return message.getContentID();
    }

    public String getContentMD5() throws MessagingException {
        if (message == null) {
            loadMessage();
        }
        return message.getContentMD5();
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
        if (message == null) {
            loadMessage();
        }
        return message.getMessageID();
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
        if (message == null) {
            loadMessage();
        }
        return message.getHeader(name);
    }

    public String getHeader(String name, String delimiter) throws MessagingException {
        if (message == null) {
            loadMessage();
        }
        return message.getHeader(name, delimiter);
    }

    public Enumeration getAllHeaders() throws MessagingException {
        if (message == null) {
            loadMessage();
        }
        return message.getAllHeaders();
    }

    public Enumeration getMatchingHeaders(String[] names) throws MessagingException {
        if (message == null) {
            loadMessage();
        }
        return message.getMatchingHeaders(names);
    }

    public Enumeration getNonMatchingHeaders(String[] names) throws MessagingException {
        if (message == null) {
            loadMessage();
        }
        return message.getNonMatchingHeaders(names);
    }

    public Enumeration getAllHeaderLines() throws MessagingException {
        if (message == null) {
            loadMessage();
        }
        return message.getAllHeaderLines();
    }

    public Enumeration getMatchingHeaderLines(String[] names) throws MessagingException {
        if (message == null) {
            loadMessage();
        }
        return message.getMatchingHeaderLines(names);
    }

    public Enumeration getNonMatchingHeaderLines(String[] names) throws MessagingException {
        if (message == null) {
            loadMessage();
        }
        return message.getNonMatchingHeaderLines(names);
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
