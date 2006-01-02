/***********************************************************************
 * Copyright (c) 1999-2005 The Apache Software Foundation.             *
 * All rights reserved.                                                *
 * ------------------------------------------------------------------- *
 * Licensed under the Apache License, Version 2.0 (the "License"); you *
 * may not use this file except in compliance with the License. You    *
 * may obtain a copy of the License at:                                *
 *                                                                     *
 *     http://www.apache.org/licenses/LICENSE-2.0                      *
 *                                                                     *
 * Unless required by applicable law or agreed to in writing, software *
 * distributed under the License is distributed on an "AS IS" BASIS,   *
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or     *
 * implied.  See the License for the specific language governing       *
 * permissions and limitations under the License.                      *
 ***********************************************************************/
package org.apache.james.smtpserver;

import junit.framework.TestCase;
import org.apache.avalon.cornerstone.services.sockets.SocketManager;
import org.apache.avalon.cornerstone.services.threads.ThreadManager;
import org.apache.james.services.JamesConnectionManager;
import org.apache.james.test.mock.avalon.MockLogger;
import org.apache.james.test.mock.avalon.MockServiceManager;
import org.apache.james.test.mock.avalon.MockSocketManager;
import org.apache.james.test.mock.avalon.MockThreadManager;
import org.apache.james.test.mock.james.MockMailServer;
import org.apache.james.test.mock.james.MockUsersRepository;
import org.apache.james.test.mock.mailet.MockMailContext;
import org.apache.james.test.util.Util;
import org.apache.james.util.Base64;
import org.apache.james.util.connection.SimpleConnectionManager;
import org.columba.ristretto.composer.MimeTreeRenderer;
import org.columba.ristretto.io.CharSequenceSource;
import org.columba.ristretto.message.*;
import org.columba.ristretto.smtp.SMTPException;
import org.columba.ristretto.smtp.SMTPProtocol;
import org.columba.ristretto.smtp.SMTPResponse;

import java.io.IOException;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.List;

/**
 * Tests the org.apache.james.smtpserver.SMTPServer unit 
 */
public class SMTPServerTest extends TestCase {
    private int m_smtpListenerPort = Util.getRandomNonPrivilegedPort();
    private MockMailServer m_mailServer;
    private SMTPTestConfiguration m_testConfiguration;
    private SMTPServer m_smtpServer;
    private MockUsersRepository m_usersRepository = new MockUsersRepository();

    public SMTPServerTest() {
        super("SMTPServerTest");
    }

    protected void setUp() throws Exception {
        m_smtpServer = new SMTPServer();
        m_smtpServer.enableLogging(new MockLogger());

        m_smtpServer.service(setUpServiceManager());
        m_testConfiguration = new SMTPTestConfiguration(m_smtpListenerPort);
    }

    private void finishSetUp(SMTPTestConfiguration testConfiguration) throws Exception {
        testConfiguration.init();
        m_smtpServer.configure(testConfiguration);
        m_smtpServer.initialize();
        m_mailServer.setMaxMessageSizeBytes(m_testConfiguration.getMaxMessageSize());
    }

    private MockServiceManager setUpServiceManager() {
        MockServiceManager serviceManager = new MockServiceManager();
        SimpleConnectionManager connectionManager = new SimpleConnectionManager();
        connectionManager.enableLogging(new MockLogger());
        serviceManager.put(JamesConnectionManager.ROLE, connectionManager);
        serviceManager.put("org.apache.mailet.MailetContext", new MockMailContext());
        m_mailServer = new MockMailServer();
        serviceManager.put("org.apache.james.services.MailServer", m_mailServer);
        serviceManager.put("org.apache.james.services.UsersRepository", m_usersRepository);
        serviceManager.put(SocketManager.ROLE, new MockSocketManager(m_smtpListenerPort));
        serviceManager.put(ThreadManager.ROLE, new MockThreadManager());
        return serviceManager;
    }

    public void testSimpleMailSendWithEHLO() throws Exception, SMTPException {
        finishSetUp(m_testConfiguration);

        SMTPProtocol smtpProtocol = new SMTPProtocol("127.0.0.1", m_smtpListenerPort);
        smtpProtocol.openPort();

        // no message there, yet
        assertNull("no mail received by mail server", m_mailServer.getLastMail());

        String[] capabilityStrings = smtpProtocol.ehlo(InetAddress.getLocalHost());
        assertEquals("capabilities", 3, capabilityStrings.length);
        List capabilitieslist = Arrays.asList(capabilityStrings);
        assertTrue("capabilities present PIPELINING", capabilitieslist.contains("PIPELINING"));
        assertTrue("capabilities present ENHANCEDSTATUSCODES", capabilitieslist.contains("ENHANCEDSTATUSCODES"));
        assertTrue("capabilities present 8BITMIME", capabilitieslist.contains("8BITMIME"));

        smtpProtocol.mail(new Address("mail@localhost"));
        smtpProtocol.rcpt(new Address("mail@localhost"));

        smtpProtocol.data(MimeTreeRenderer.getInstance().renderMimePart(createMail()));

        smtpProtocol.quit();

        // mail was propagated by SMTPServer
        assertNotNull("mail received by mail server", m_mailServer.getLastMail());
    }

    public void testSimpleMailSendWithHELO() throws Exception, SMTPException {
        finishSetUp(m_testConfiguration);

        SMTPProtocol smtpProtocol = new SMTPProtocol("127.0.0.1", m_smtpListenerPort);
        smtpProtocol.openPort();

        // no message there, yet
        assertNull("no mail received by mail server", m_mailServer.getLastMail());

        smtpProtocol.helo(InetAddress.getLocalHost());

        smtpProtocol.mail(new Address("mail@localhost"));
        smtpProtocol.rcpt(new Address("mail@localhost"));

        smtpProtocol.data(MimeTreeRenderer.getInstance().renderMimePart(createMail()));

        smtpProtocol.quit();

        // mail was propagated by SMTPServer
        assertNotNull("mail received by mail server", m_mailServer.getLastMail());
    }

    private LocalMimePart createMail() {
        MimeHeader mimeHeader = new MimeHeader(new Header());
        mimeHeader.set("Mime-Version", "1.0");
        LocalMimePart mail = new LocalMimePart(mimeHeader);
        MimeHeader header = mail.getHeader();
        header.setMimeType(new MimeType("text", "plain"));

        mail.setBody(new CharSequenceSource("James Unit Test Body"));
        return mail;
    }

    public void testAuth() throws Exception, SMTPException {
        m_testConfiguration.setAuthorizedAddresses("128.0.0.1/8");
        m_testConfiguration.setAuthorizingAnnounce();
        finishSetUp(m_testConfiguration);

        MySMTPProtocol smtpProtocol = new MySMTPProtocol("127.0.0.1", m_smtpListenerPort);
        smtpProtocol.openPort();

        String[] capabilityStrings = smtpProtocol.ehlo(InetAddress.getLocalHost());
        List capabilitieslist = Arrays.asList(capabilityStrings);
        assertTrue("anouncing auth required", capabilitieslist.contains("AUTH LOGIN PLAIN"));
        // is this required or just for compatibility? assertTrue("anouncing auth required", capabilitieslist.contains("AUTH=LOGIN PLAIN"));

        String userName = "test_user_smtp";
        String noexistUserName = "noexist_test_user_smtp";
        
        smtpProtocol.sendCommand("AUTH FOO", null);
        SMTPResponse response = smtpProtocol.getResponse();
        assertEquals("expected error: unrecognized authentication type", 504, response.getCode());

        smtpProtocol.mail(new Address(userName));

        try {
            smtpProtocol.rcpt(new Address("mail@sample.com"));
            fail("no auth required");
        } catch (SMTPException e) {
            assertEquals("expected 530 error", 530, e.getCode());
        }

        assertFalse("user not existing", m_usersRepository.contains(noexistUserName));
        try {
            smtpProtocol.auth("PLAIN", noexistUserName, "pwd".toCharArray());
            fail("auth succeeded for non-existing user");
        } catch (SMTPException e) {
            assertEquals("expected error", 535, e.getCode());
        }

        m_usersRepository.addUser(userName, "pwd");
        try {
            smtpProtocol.auth("PLAIN", userName, "wrongpwd".toCharArray());
            fail("auth succeeded with wrong password");
        } catch (SMTPException e) {
            assertEquals("expected error", 535, e.getCode());
        }

        try {
            smtpProtocol.auth("PLAIN", userName, "pwd".toCharArray());
        } catch (SMTPException e) {
            e.printStackTrace(); 
            fail("authentication failed");
        }

        smtpProtocol.sendCommand("AUTH PLAIN ", new String[]{Base64.encodeAsString("\0" + userName + "\0pwd")});
        response = smtpProtocol.getResponse();
        assertEquals("expected error: User has previously authenticated.", 503, response.getCode());

        smtpProtocol.rcpt(new Address("mail@sample.com"));
        smtpProtocol.data(MimeTreeRenderer.getInstance().renderMimePart(createMail()));

        smtpProtocol.quit();

        // mail was propagated by SMTPServer
        assertNotNull("mail received by mail server", m_mailServer.getLastMail());
    }

    public void testNoRecepientSpecified() throws Exception, SMTPException {
        finishSetUp(m_testConfiguration);

        MySMTPProtocol smtpProtocol = new MySMTPProtocol("127.0.0.1", m_smtpListenerPort);
        smtpProtocol.openPort();

        smtpProtocol.ehlo(InetAddress.getLocalHost());

        smtpProtocol.mail(new Address("mail@sample.com"));

        // left out for test smtpProtocol.rcpt(new Address("mail@localhost"));

        try {
            smtpProtocol.data(MimeTreeRenderer.getInstance().renderMimePart(createMail()));
            fail("sending succeeded without recepient");
        } catch (Exception e) {
            // test succeeded
        }

        smtpProtocol.quit();

        // mail was propagated by SMTPServer
        assertNull("no mail received by mail server", m_mailServer.getLastMail());
    }

    public void testRelayingDenied() throws Exception, SMTPException {
        m_testConfiguration.setAuthorizedAddresses("128.0.0.1/8");
        finishSetUp(m_testConfiguration);

        SMTPProtocol smtpProtocol = new SMTPProtocol("127.0.0.1", m_smtpListenerPort);
        smtpProtocol.openPort();

        smtpProtocol.ehlo(InetAddress.getLocalHost());

        smtpProtocol.mail(new Address("mail@sample.com"));
        try {
            smtpProtocol.rcpt(new Address("maila@sample.com"));
            fail("relaying allowed");
        } catch (SMTPException e) {
            assertEquals("expected 550 error", 550, e.getCode());
        }
    }

    public void testHandleAnnouncedMessageSizeLimitExceeded() throws Exception, SMTPException {
        m_testConfiguration.setMaxMessageSize(1); // set message limit to 1kb
        finishSetUp(m_testConfiguration);

        MySMTPProtocol smtpProtocol = new MySMTPProtocol("127.0.0.1", m_smtpListenerPort);
        smtpProtocol.openPort();

        smtpProtocol.ehlo(InetAddress.getLocalHost());

        smtpProtocol.sendCommand("MAIL FROM:<mail@localhost> SIZE=1025", null);
        SMTPResponse response = smtpProtocol.getResponse();
        assertEquals("expected error: max msg size exceeded", 552, response.getCode());

        smtpProtocol.rcpt(new Address("mail@localhost"));
    }

    public void testHandleMessageSizeLimitExceeded() throws Exception, SMTPException {
        m_testConfiguration.setMaxMessageSize(1); // set message limit to 1kb 
        finishSetUp(m_testConfiguration);

        MySMTPProtocol smtpProtocol = new MySMTPProtocol("127.0.0.1", m_smtpListenerPort);
        smtpProtocol.openPort();

        smtpProtocol.ehlo(InetAddress.getLocalHost());

        smtpProtocol.mail(new Address("mail@localhost"));
        smtpProtocol.rcpt(new Address("mail@localhost"));

        MimeHeader mimeHeader = new MimeHeader(new Header());
        mimeHeader.set("Mime-Version", "1.0");
        LocalMimePart mail = new LocalMimePart(mimeHeader);
        MimeHeader header = mail.getHeader();
        header.setMimeType(new MimeType("text", "plain"));

        // create Body with more than 1kb
        StringBuffer body = new StringBuffer();
        body.append("1234567810123456782012345678301234567840123456785012345678601234567870123456788012345678901234567100");
        body.append("1234567810123456782012345678301234567840123456785012345678601234567870123456788012345678901234567100");
        body.append("1234567810123456782012345678301234567840123456785012345678601234567870123456788012345678901234567100");
        body.append("1234567810123456782012345678301234567840123456785012345678601234567870123456788012345678901234567100");
        body.append("1234567810123456782012345678301234567840123456785012345678601234567870123456788012345678901234567100");
        body.append("1234567810123456782012345678301234567840123456785012345678601234567870123456788012345678901234567100");
        body.append("1234567810123456782012345678301234567840123456785012345678601234567870123456788012345678901234567100");
        body.append("1234567810123456782012345678301234567840123456785012345678601234567870123456788012345678901234567100");
        body.append("1234567810123456782012345678301234567840123456785012345678601234567870123456788012345678901234567100");
        body.append("1234567810123456782012345678301234567840123456785012345678601234567870123456788012345678901234567100");
        body.append("1234567810123456782012345678301234567840123456785012345678601234567870123456788012345678901234567100");
        body.append("1234567810123456782012345"); // 1025 chars

        mail.setBody(new CharSequenceSource(body.toString()));

        try {
            smtpProtocol.data(MimeTreeRenderer.getInstance().renderMimePart(mail));
            fail("message size exceeded not recognized");
        } catch (SMTPException e) {
            assertEquals("expected 552 error", 552, e.getCode());
        }

    }
}

class MySMTPProtocol extends SMTPProtocol
{

    public MySMTPProtocol(String s, int i) {
        super(s, i);
    }

    public MySMTPProtocol(String s) {
        super(s);
    }

    public void sendCommand(String string, String[] strings) throws IOException {
        super.sendCommand(string, strings);     
    }

    public SMTPResponse getResponse() throws IOException, SMTPException {
        return super.readSingleLineResponse();
    }
}
