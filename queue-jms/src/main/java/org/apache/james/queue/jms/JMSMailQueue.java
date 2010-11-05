/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *   http://www.apache.org/licenses/LICENSE-2.0                 *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/
package org.apache.james.queue.jms;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.concurrent.TimeUnit;

import javax.jms.BytesMessage;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.ObjectMessage;
import javax.jms.Queue;
import javax.jms.Session;
import javax.mail.MessagingException;
import javax.mail.internet.AddressException;
import javax.mail.internet.MimeMessage;

import org.apache.commons.logging.Log;
import org.apache.james.core.MailImpl;
import org.apache.james.core.MimeMessageCopyOnWriteProxy;
import org.apache.james.queue.api.MailPrioritySupport;
import org.apache.james.queue.api.MailQueue;
import org.apache.mailet.Mail;
import org.apache.mailet.MailAddress;

/**
 * {@link MailQueue} implementation which use a JMS Queue for the
 * {@link MailQueue}. This implementation should work with every JMS 1.1.0
 * implementation
 * 
 * It use {@link ObjectMessage} with a byte array as payload to store the {@link Mail} objects.
 * 
 * 
 */
public class JMSMailQueue implements MailQueue, JMSSupport, MailPrioritySupport {

    protected final String queuename;
    protected final ConnectionFactory connectionFactory;
    protected final Log logger;


    public JMSMailQueue(final ConnectionFactory connectionFactory, final String queuename, final Log logger) {
        this.connectionFactory = connectionFactory;
        this.queuename = queuename;
        this.logger = logger;
    }

/**
     * Execute the given {@link DequeueOperation} when a mail is ready to process. As JMS does not support delay scheduling out-of-the box, we use 
     * a messageselector to check if a mail is ready. For this a {@link MessageConsumer#receive(long) is used with a timeout of 10 seconds. 
     * 
     * Many JMS implementations support better solutions for this, so this should get overridden by these implementations
     * 
     */
    public MailQueueItem deQueue() throws MailQueueException {
        Connection connection = null;
        Session session = null;
        Message message = null;
        MessageConsumer consumer = null;

        while (true) {
            try {
                connection = connectionFactory.createConnection();
                connection.start();

                session = connection.createSession(true, Session.SESSION_TRANSACTED);
                Queue queue = session.createQueue(queuename);
                consumer = session.createConsumer(queue, getMessageSelector());

                message = consumer.receive(10000);

                if (message != null) {
                    return createMailQueueItem(connection, session, consumer, message);
                } else {
                    session.commit();

                    if (consumer != null) {

                        try {
                            consumer.close();
                        } catch (JMSException e1) {
                            // ignore on rollback
                        }
                    }
                    try {
                        if (session != null)
                            session.close();
                    } catch (JMSException e1) {
                        // ignore here
                    }

                    try {
                        if (connection != null)
                            connection.close();
                    } catch (JMSException e1) {
                        // ignore here
                    }
                }

            } catch (Exception e) {
                try {
                    session.rollback();
                } catch (JMSException e1) {
                    // ignore on rollback
                }

                if (consumer != null) {

                    try {
                        consumer.close();
                    } catch (JMSException e1) {
                        // ignore on rollback
                    }
                }
                try {
                    if (session != null)
                        session.close();
                } catch (JMSException e1) {
                    // ignore here
                }

                try {
                    if (connection != null)
                        connection.close();
                } catch (JMSException e1) {
                    // ignore here
                }
                throw new MailQueueException("Unable to dequeue next message", e);
            }
        }

    }

    /**
     * Return message selector to use for consuming
     * 
     * @return selector
     */
    protected String getMessageSelector() {
        return JAMES_NEXT_DELIVERY + " <= " + System.currentTimeMillis();
    }
    
    /*
     * (non-Javadoc)
     * 
     * @see org.apache.james.queue.MailQueue#enQueue(org.apache.mailet.Mail,
     * long, java.util.concurrent.TimeUnit)
     */
    public void enQueue(Mail mail, long delay, TimeUnit unit) throws MailQueueException {
        Connection connection = null;
        Session session = null;

        long mydelay = 0;

        if (delay > 0) {
            mydelay = TimeUnit.MILLISECONDS.convert(delay, unit);
        }

        try {

            connection = connectionFactory.createConnection();
            connection.start();
            session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            
            int msgPrio = NORMAL_PRIORITY;
            Object prio = mail.getAttribute(MAIL_PRIORITY);
            if (prio instanceof Integer) {
                msgPrio = (Integer) prio;
            }
            
            Map<String, Object> props = getJMSProperties(mail, mydelay);
            
            produceMail(session, props, msgPrio, mail);
            
        } catch (Exception e) {
            if (session != null) {
                try {
                    session.rollback();
                } catch (JMSException e1) {
                    // ignore on rollback
                }
            }
            throw new MailQueueException("Unable to enqueue mail " + mail, e);

        } finally {
            try {
                if (session != null)
                    session.close();
            } catch (JMSException e) {
                // ignore here
            }

            try {
                if (connection != null)
                    connection.close();
            } catch (JMSException e) {
                // ignore here
            }
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.apache.james.queue.MailQueue#enQueue(org.apache.mailet.Mail)
     */
    public void enQueue(Mail mail) throws MailQueueException {
        enQueue(mail, NO_DELAY, TimeUnit.MILLISECONDS);
    }

    /**
     * Produce the mail to the JMS Queue
     */
    protected void produceMail(Session session, Map<String,Object> props, int msgPrio, Mail mail) throws JMSException, MessagingException, IOException {
        MessageProducer producer = null;

        try {
            Queue queue = session.createQueue(queuename);

            producer = session.createProducer(queue);
            ObjectMessage message = session.createObjectMessage();

            Iterator<String> keys = props.keySet().iterator();
            while(keys.hasNext()) {
                String key = keys.next();
                message.setObjectProperty(key, props.get(key));
            }
            
            long size = mail.getMessageSize();
            ByteArrayOutputStream out;
            if (size > -1) {
                out = new ByteArrayOutputStream((int)size);
            } else {
                out = new ByteArrayOutputStream();
            }
            mail.getMessage().writeTo(out);
            
            // store the byte array in a ObjectMessage so we can use a SharedByteArrayInputStream later
            // without the need of copy the day 
            message.setObject(out.toByteArray());

            producer.send(message, Message.DEFAULT_DELIVERY_MODE, msgPrio, Message.DEFAULT_TIME_TO_LIVE);
            
        } finally {

            try {
                if (producer != null)
                    producer.close();
            } catch (JMSException e) {
                // ignore here
            }
        }
        
        
        
    }

    /**
     * Get JMS Message properties with values
     * 
     * @param message
     * @param mail
     * @param delayInMillis
     * @throws JMSException
     * @throws MessagingException
     */
    @SuppressWarnings("unchecked")
    protected Map<String,Object> getJMSProperties(Mail mail, long delayInMillis) throws JMSException, MessagingException {
        Map<String, Object> props = new HashMap<String, Object>();
        long nextDelivery = -1;
        if (delayInMillis > 0) {
            nextDelivery = System.currentTimeMillis() + delayInMillis;

        }
        props.put(JAMES_NEXT_DELIVERY, nextDelivery);
        props.put(JAMES_MAIL_ERROR_MESSAGE, mail.getErrorMessage());
        props.put(JAMES_MAIL_LAST_UPDATED, mail.getLastUpdated().getTime());
        props.put(JAMES_MAIL_MESSAGE_SIZE, mail.getMessageSize());
        props.put(JAMES_MAIL_NAME, mail.getName());

        StringBuilder recipientsBuilder = new StringBuilder();

        Iterator<MailAddress> recipients = mail.getRecipients().iterator();
        while (recipients.hasNext()) {
            String recipient = recipients.next().toString();
            recipientsBuilder.append(recipient.trim());
            if (recipients.hasNext()) {
                recipientsBuilder.append(JAMES_MAIL_SEPARATOR);
            }
        }
        props.put(JAMES_MAIL_RECIPIENTS, recipientsBuilder.toString());
        props.put(JAMES_MAIL_REMOTEADDR, mail.getRemoteAddr());
        props.put(JAMES_MAIL_REMOTEHOST, mail.getRemoteHost());

        String sender;
        MailAddress s = mail.getSender();
        if (s == null) {
            sender = "";
        } else {
            sender = mail.getSender().toString();
        }

        StringBuilder attrsBuilder = new StringBuilder();
        Iterator<String> attrs = mail.getAttributeNames();
        while (attrs.hasNext()) {
            String attrName = attrs.next();
            attrsBuilder.append(attrName);

            Object value = convertAttributeValue(mail.getAttribute(attrName));
            props.put(attrName, value);

            if (attrs.hasNext()) {
                attrsBuilder.append(JAMES_MAIL_SEPARATOR);
            }
        }
        props.put(JAMES_MAIL_ATTRIBUTE_NAMES, attrsBuilder.toString());
        props.put(JAMES_MAIL_SENDER, sender);
        props.put(JAMES_MAIL_STATE, mail.getState());
        return props;
    }

    /**
     * Create the complete Mail from the JMS Message. So the created
     * {@link Mail} is completly populated
     * 
     * @param message
     * @return
     * @throws MessagingException
     * @throws JMSException
     */
    protected final Mail createMail(Message message) throws MessagingException, JMSException {
        MailImpl mail = new MailImpl();
        populateMail(message, mail);
        populateMailMimeMessage(message, mail);

        return mail;
    }

    /**
     * Populat the given {@link Mail} instance with a {@link MimeMessage}. The
     * {@link MimeMessage} is read from the JMS Message. This implementation use
     * a {@link BytesMessage}
     * 
     * @param message
     * @param mail
     * @throws MessagingException
     */
    protected void populateMailMimeMessage(Message message, Mail mail) throws MessagingException, JMSException {
        if (message instanceof ObjectMessage) {
            mail.setMessage(new MimeMessageCopyOnWriteProxy(new MimeMessageObjectMessageSource((ObjectMessage)message)));
        } else {
            throw new MailQueueException("Not supported JMS Message received " + message);
        }

    }

    /**
     * Populate Mail with values from Message. This exclude the
     * {@link MimeMessage}
     * 
     * @param message
     * @param mail
     * @throws JMSException
     */
    protected void populateMail(Message message, MailImpl mail) throws JMSException {
        mail.setErrorMessage(message.getStringProperty(JAMES_MAIL_ERROR_MESSAGE));
        mail.setLastUpdated(new Date(message.getLongProperty(JAMES_MAIL_LAST_UPDATED)));
        mail.setName(message.getStringProperty(JAMES_MAIL_NAME));

        List<MailAddress> rcpts = new ArrayList<MailAddress>();
        String recipients = message.getStringProperty(JAMES_MAIL_RECIPIENTS);
        StringTokenizer recipientTokenizer = new StringTokenizer(recipients, JAMES_MAIL_SEPARATOR);
        while (recipientTokenizer.hasMoreTokens()) {
            try {
                MailAddress rcpt = new MailAddress(recipientTokenizer.nextToken());
                rcpts.add(rcpt);
            } catch (AddressException e) {
                // Should never happen as long as the user does not modify the
                // the header by himself
                // Maybe we should log it anyway
            }
        }
        mail.setRecipients(rcpts);
        mail.setRemoteAddr(message.getStringProperty(JAMES_MAIL_REMOTEADDR));
        mail.setRemoteHost(message.getStringProperty(JAMES_MAIL_REMOTEHOST));

        String attributeNames = message.getStringProperty(JAMES_MAIL_ATTRIBUTE_NAMES);
        StringTokenizer namesTokenizer = new StringTokenizer(attributeNames, JAMES_MAIL_SEPARATOR);
        while (namesTokenizer.hasMoreTokens()) {
            String name = namesTokenizer.nextToken();
            Serializable attrValue = message.getStringProperty(name);

            mail.setAttribute(name, attrValue);
        }

        String sender = message.getStringProperty(JAMES_MAIL_SENDER);
        if (sender == null || sender.trim().length() <= 0) {
            mail.setSender(null);
        } else {
            try {
                mail.setSender(new MailAddress(sender));
            } catch (AddressException e) {
                // Should never happen as long as the user does not modify the
                // the header by himself
                // Maybe we should log it anyway
            }
        }

        mail.setState(message.getStringProperty(JAMES_MAIL_STATE));

    }

    /**
     * Convert the attribute value if necessary.
     * 
     * @param value
     * @return convertedValue
     */
    protected Object convertAttributeValue(Object value) {
        if (value == null || value instanceof String || value instanceof Byte || value instanceof Long || value instanceof Double || value instanceof Boolean || value instanceof Integer || value instanceof Short || value instanceof Float) {
            return value;
        }
        return value.toString();
    }

    @Override
    public String toString() {
        return "MailQueue:" + queuename;
    }

    /**
     * Create a {@link MailQueueItem} for the given parameters
     * 
     * @param connection
     * @param session
     * @param consumer
     * @param message
     * @return item
     * @throws JMSException
     * @throws MessagingException
     */
    protected MailQueueItem createMailQueueItem(Connection connection, Session session, MessageConsumer consumer, Message message) throws JMSException, MessagingException {
        final Mail mail = createMail(message);
        return new JMSMailQueueItem(mail, connection, session, consumer);
    }

}