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

package org.apache.james.queue.activemq;

import java.io.IOException;

import javax.jms.Connection;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.Session;

import org.apache.activemq.command.ActiveMQBlobMessage;
import org.apache.commons.logging.Log;
import org.apache.james.queue.MailQueue.MailQueueException;
import org.apache.james.queue.MailQueue.MailQueueItem;
import org.apache.james.queue.jms.JMSMailQueueItem;
import org.apache.mailet.Mail;

/**
 * ActiveMQ {@link MailQueueItem} implementation which handles Blob-Messages as
 * well
 * 
 */
public class ActiveMQMailQueueItem extends JMSMailQueueItem {

    private final Message message;
    private final Log logger;

    public ActiveMQMailQueueItem(Mail mail, Connection connection, Session session, MessageConsumer consumer, Message message, Log logger) {
        super(mail, connection, session, consumer);
        this.message = message;
        this.logger = logger;
    }

    @Override
    public void done(boolean success) throws MailQueueException {
        super.done(success);
        if (success) {
            if (message instanceof ActiveMQBlobMessage) {
                /*
                 * TODO: Enable this once activemq 5.4.2 was released
                // delete the file
                // This should get removed once this jira issue was fixed
                // https://issues.apache.org/activemq/browse/AMQ-1529
                try {
                    ((ActiveMQBlobMessage) message).deleteFile();
                } catch (IOException e) {
                    logger.info("Unable to delete blob message file for mail " + getMail().getName());
                } catch (JMSException e) {
                    logger.info("Unable to delete blob message file for mail " + getMail().getName());
                }
                */
            }
        }
    }

}