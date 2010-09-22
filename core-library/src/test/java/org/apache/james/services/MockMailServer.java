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

package org.apache.james.services;

import org.apache.james.api.user.UsersRepository;
import org.apache.james.core.MailImpl;
import org.apache.james.lifecycle.Disposable;
import org.apache.james.lifecycle.LifecycleUtil;
import org.apache.james.mailrepository.MailRepository;
import org.apache.james.services.MailServer;
import org.apache.james.test.mock.james.InMemorySpoolRepository;
import org.apache.mailet.base.test.MailUtil;
import org.apache.mailet.Mail;
import org.apache.mailet.MailAddress;

import javax.mail.Address;
import javax.mail.MessagingException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

import java.io.InputStream;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;

public class MockMailServer implements MailServer, Disposable {

    private final UsersRepository m_users;

    private int m_maxMessageSizeBytes = 0;

    // private final ArrayList mails = new ArrayList();
    
    private final InMemorySpoolRepository mails = new InMemorySpoolRepository();
    private String lastMailKey = null; 

    private HashMap<String,MailRepository> inboxes;
    
    private boolean virtualHosting;

    public MockMailServer(UsersRepository usersRepository) {
        this.m_users = usersRepository;
    }

    public void sendMail(MailAddress sender, Collection<MailAddress> recipients, MimeMessage msg) throws MessagingException {
        //        Object[] mailObjects = new Object[]{sender, recipients, new MimeMessageCopyOnWriteProxy(msg)};
//        mails.add(mailObjects);
//        
        String newId = MailUtil.newId();
        MailImpl m = new MailImpl(newId, sender, recipients, msg);
        sendMail(m);
        m.dispose();
    }

    public void sendMail(MailAddress sender, Collection<MailAddress> recipients, InputStream msg) throws MessagingException {
//        Object[] mailObjects = new Object[]{sender, recipients, msg};
//        mails.add(mailObjects);
        MailImpl m = new MailImpl(MailUtil.newId(), sender, recipients, msg);
        sendMail(m);
        m.dispose();
    }

    public void sendMail(Mail mail) throws MessagingException {
        int bodySize = mail.getMessage().getSize();
        if (m_maxMessageSizeBytes != 0 && m_maxMessageSizeBytes < bodySize) throw new MessagingException("message size exception");
        
        lastMailKey = mail.getName();
        mails.store(mail);
        // sendMail(mail.getSender(), mail.getRecipients(), mail.getMessage());
    }

    public void sendMail(MimeMessage message) throws MessagingException {
        // taken from class org.apache.james.James 
        MailAddress sender = new MailAddress((InternetAddress)message.getFrom()[0]);
        Collection<MailAddress> recipients = new HashSet<MailAddress>();
        Address addresses[] = message.getAllRecipients();
        if (addresses != null) {
            for (int i = 0; i < addresses.length; i++) {
                // Javamail treats the "newsgroups:" header field as a
                // recipient, so we want to filter those out.
                if ( addresses[i] instanceof InternetAddress ) {
                    recipients.add(new MailAddress((InternetAddress)addresses[i]));
                }
            }
        }
        sendMail(sender, recipients, message);
    }

    public MailRepository getUserInbox(String userName) {
        if (inboxes==null) {
            return null;
        } else {
            if ((userName.indexOf("@") < 0) == false && supportVirtualHosting() == false) userName = userName.split("@")[0]; 
            return (MailRepository) inboxes.get(userName);
        }
        
    }
    
    public void setUserInbox(String userName, MailRepository inbox) {
        if (inboxes == null) {
            inboxes = new HashMap<String,MailRepository>();
        }
        inboxes.put(userName,inbox);
    }

    public synchronized String getId() {
        return MailUtil.newId();
    }

    public boolean addUser(String userName, String password) {
        m_users.addUser(userName, password);
        return true;
    }

    public boolean isLocalServer(String serverName) {
        return "localhost".equals(serverName);
    }

    public Mail getLastMail()
    {
        if (mails.size() == 0) return null;
        try {
            return mails.retrieve(lastMailKey);
        } catch (MessagingException e) {
            e.printStackTrace();
        }
        return null;
    }

    public void setMaxMessageSizeBytes(int maxMessageSizeBytes) {
        m_maxMessageSizeBytes = maxMessageSizeBytes;
    }

    public void dispose() {
//        if (mails != null) {
//            Iterator i = mails.iterator();
//            while (i.hasNext()) {
//                Object[] obs = (Object[]) i.next();
//                // this is needed to let the MimeMessageWrapper to dispose.
//                ContainerUtil.dispose(obs[2]);
//            }
//        }
        mails.dispose();
        if (inboxes!=null) {
            Iterator<MailRepository> i = inboxes.values().iterator();
            while (i.hasNext()) {
                MailRepository m = i.next();
                LifecycleUtil.dispose(m);
            }
        }
    }
    
    public MailRepository getSentMailsRepository() {
        return mails;
    }
    
    public void setVirtualHosting(boolean virtualHosting) {
        this.virtualHosting = virtualHosting;
    }

    public boolean supportVirtualHosting() {
        return virtualHosting;
    }

    public String getDefaultDomain() {
        return "localhost";
    }

    public String getHelloName() {
        return "localhost";
    }
}


