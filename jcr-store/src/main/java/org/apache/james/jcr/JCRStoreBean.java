/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.james.jcr;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

import javax.jcr.Node;
import javax.jcr.PathNotFoundException;
import javax.jcr.RepositoryException;
import javax.mail.Address;
import javax.mail.BodyPart;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Part;
import javax.mail.Message.RecipientType;
import javax.mail.internet.ContentType;
import javax.mail.internet.MimeMessage;

import org.apache.jackrabbit.util.Text;

/**
 * JavaBean that stores messages to a JCR content repository.
 * <p>
 * After instantiating this bean you should use the
 * {@link #setParentNode(Node)} method to specify the root node under
 * which all messages should be stored. Then you can call
 * {@link #storeMessage(Message)} to store messages in the repository.
 * <p>
 * The created content structure below the given parent node consists
 * of a date based .../year/month/day tree structure, below which the actual
 * messages are stored. A stored message consists of an nt:file node whose
 * name is based on the subject of the message. The jcr:content child of the
 * nt:file node contains the MIME structure and all relevant headers of the
 * message. Note that the original message source is <em>not</em> stored,
 * which means that some of the message information will be lost.
 * <p>
 * The messages are stored using the session associated with the specified
 * parent node. No locking or synchronization is performed, and it is expected
 * that only one thread writing to the message subtree at any given moment.
 * You should use JCR locking or some other explicit synchronization mechanism
 * if you want to have concurrent writes to the message subtree.
 */
public class JCRStoreBean {

    /**
     * Parent node where the messages are stored.
     */
    private Node parent;

    public void setParentNode(Node parent) {
        this.parent = parent;
    }

    /**
     * Stores the given mail message to the content repository.
     *
     * @param message mail message
     * @throws MessagingException if the message could not be read
     * @throws RepositoryException if the message could not be saved
     */
    public void storeMessage(Message message)
            throws MessagingException, RepositoryException {
        try {
            Date date = message.getSentDate();
            Node year = getOrAddNode(parent, format("yyyy", date), "nt:folder");
            Node month = getOrAddNode(year, format("mm", date), "nt:folder");
            Node day = getOrAddNode(month, format("dd", date), "nt:folder");
            Node node = createNode(day, getMessageName(message), "nt:file");
            importEntity(message, node);
            parent.save();
        } catch (IOException e) {
            throw new MessagingException("Could not read message", e);
        }
    }

    /**
     * Import the given entity to the given JCR node.
     *
     * @param entity the source entity
     * @param parent the target node
     * @throws MessagingException if the message could not be read
     * @throws RepositoryException if the message could not be written
     * @throws IOException if the message could not be read
     */
    private void importEntity(Part entity, Node parent)
            throws MessagingException, RepositoryException, IOException {
        Node node = parent.addNode("jcr:content", "nt:unstructured");

        setProperty(node, "description", entity.getDescription());
        setProperty(node, "disposition", entity.getDisposition());
        setProperty(node, "filename", entity.getFileName());

        if (entity instanceof MimeMessage) {
            MimeMessage mime = (MimeMessage) entity;
            setProperty(node, "subject", mime.getSubject());
            setProperty(node, "message-id", mime.getMessageID());
            setProperty(node, "content-id", mime.getContentID());
            setProperty(node, "content-md5", mime.getContentMD5());
            setProperty(node, "language", mime.getContentLanguage());
            setProperty(node, "sent", mime.getSentDate());
            setProperty(node, "received", mime.getReceivedDate());
            setProperty(node, "from", mime.getFrom());
            setProperty(node, "to", mime.getRecipients(RecipientType.TO));
            setProperty(node, "cc", mime.getRecipients(RecipientType.CC));
            setProperty(node, "bcc", mime.getRecipients(RecipientType.BCC));
            setProperty(node, "reply-to", mime.getReplyTo());
            setProperty(node, "sender", mime.getSender());
        }

        Object content = entity.getContent();
        ContentType type = getContentType(entity);
        node.setProperty("jcr:mimeType", type.getBaseType());
        if (content instanceof Multipart) {
            Multipart multipart = (Multipart) content;
            for (int i = 0; i < multipart.getCount(); i++) {
                BodyPart part = multipart.getBodyPart(i);
                Node child;
                if (part.getFileName() != null) {
                    child = createNode(node, part.getFileName(), "nt:file");
                } else {
                    child = createNode(node, "part", "nt:unstructured");
                }
                importEntity(part, child);
            }
        } else if (content instanceof String) {
            byte[] bytes = ((String) content).getBytes("UTF-8");
            node.setProperty("jcr:encoding", "UTF-8");
            node.setProperty("jcr:data", new ByteArrayInputStream(bytes));
        } else if (content instanceof InputStream) {
            setProperty(
                    node, "jcr:encoding", type.getParameter("encoding"));
            node.setProperty("jcr:data", (InputStream) content);
        } else {
            node.setProperty("jcr:data", entity.getInputStream());
        }
    }

    /**
     * Formats the given date using the given {@link SimpleDateFormat}
     * format string.
     *
     * @param format format string
     * @param date date to be formatted
     * @return formatted date
     */
    private String format(String format, Date date) {
        return new SimpleDateFormat(format).format(date);
    }

    /**
     * Suggests a name for the node where the given message will be stored.
     *
     * @param message mail message
     * @return suggested name
     * @throws MessagingException if an error occurs
     */
    private String getMessageName(Message message)
            throws MessagingException {
        String name = message.getSubject();
        if (name == null) {
            name = "unnamed";
        } else {
            name = name.replaceAll("[^A-Za-z0-9 ]", "").trim();
            if (name.length() == 0) {
                name = "unnamed";
            }
        }
        return name;
    }

    /**
     * Returns the named child node of the given parent. If the child node
     * does not exist, it is automatically created with the given node type.
     * The created node is not saved by this method.
     *
     * @param parent parent node
     * @param name name of the child node
     * @param type type of the child node
     * @return child node
     * @throws RepositoryException if the child node could not be accessed
     */
    private Node getOrAddNode(Node parent, String name, String type)
            throws RepositoryException {
        try {
            return parent.getNode(name);
        } catch (PathNotFoundException e) {
            return parent.addNode(name, type);
        }
    }

    /**
     * Creates a new node with a name that resembles the given suggestion.
     * The created node is not saved by this method.
     *
     * @param parent parent node
     * @param name suggested name
     * @param type node type
     * @return created node
     * @throws RepositoryException if an error occurs
     */
    private Node createNode(Node parent, String name, String type)
            throws RepositoryException {
        String original = name;
        name = Text.escapeIllegalJcrChars(name);
        for (int i = 2; parent.hasNode(name); i++) {
            name = Text.escapeIllegalJcrChars(original + i);
        }
        return parent.addNode(name, type);
    }

    /**
     * Returns the content type of the given message entity. Returns
     * the default "text/plain" content type if a content type is not
     * available. Returns "application/octet-stream" if an error occurs.
     *
     * @param entity the message entity
     * @return content type, or <code>text/plain</code> if not available
     */
    private static ContentType getContentType(Part entity) {
        try {
            String type = entity.getContentType();
            if (type != null) {
                return new ContentType(type);
            } else {
                return new ContentType("text/plain");
            }
        } catch (MessagingException e) {
            ContentType type = new ContentType();
            type.setPrimaryType("application");
            type.setSubType("octet-stream");
            return type;
        }
    }

    /**
     * Sets the named property if the given value is not null.
     *
     * @param node target node
     * @param name property name
     * @param value property value
     * @throws RepositoryException if an error occurs
     */
    private void setProperty(Node node, String name, String value)
            throws RepositoryException {
        if (value != null) {
            node.setProperty(name, value);
        }
    }

    /**
     * Sets the named property if the given array of values is
     * not null or empty.
     *
     * @param node target node
     * @param name property name
     * @param values property values
     * @throws RepositoryException if an error occurs
     */
    private void setProperty(Node node, String name, String[] values)
            throws RepositoryException {
        if (values != null && values.length > 0) {
            node.setProperty(name, values);
        }
    }

    /**
     * Sets the named property if the given value is not null.
     *
     * @param node target node
     * @param name property name
     * @param value property value
     * @throws RepositoryException if an error occurs
     */
    private void setProperty(Node node, String name, Date value)
            throws RepositoryException {
        if (value != null) {
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(value);
            node.setProperty(name, calendar);
        }
    }

    /**
     * Sets the named property if the given value is not null.
     *
     * @param node target node
     * @param name property name
     * @param value property value
     * @throws RepositoryException if an error occurs
     */
    private void setProperty(Node node, String name, Address value)
            throws RepositoryException {
        if (value != null) {
            node.setProperty(name, value.toString());
        }
    }

    /**
     * Sets the named property if the given array of values is
     * not null or empty.
     *
     * @param node target node
     * @param name property name
     * @param values property values
     * @throws RepositoryException if an error occurs
     */
    private void setProperty(Node node, String name, Address[] values)
            throws RepositoryException {
        if (values != null && values.length > 0) {
            String[] strings = new String[values.length];
            for (int i = 0; i < values.length; i++) {
                strings[i] = values[i].toString();
            }
            node.setProperty(name, strings);
        }
    }

}