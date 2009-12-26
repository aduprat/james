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

package org.apache.james.smtpserver;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.MimeMessage;

import junit.framework.TestCase;

import org.apache.avalon.cornerstone.services.sockets.SocketManager;
import org.apache.avalon.cornerstone.services.store.Store;
import org.apache.avalon.cornerstone.services.threads.ThreadManager;
import org.apache.avalon.framework.container.ContainerUtil;
import org.apache.commons.configuration.DefaultConfigurationBuilder;
import org.apache.commons.logging.impl.SimpleLog;
import org.apache.james.Constants;
import org.apache.james.api.dnsservice.DNSService;
import org.apache.james.api.kernel.mock.FakeLoader;
import org.apache.james.api.user.UsersRepository;
import org.apache.james.core.MailImpl;
import org.apache.james.services.MailServer;
import org.apache.james.socket.AvalonProtocolServer;
import org.apache.james.socket.JamesConnectionManager;
import org.apache.james.socket.SimpleConnectionManager;
import org.apache.james.test.mock.avalon.MockLogger;
import org.apache.james.test.mock.avalon.MockSocketManager;
import org.apache.james.test.mock.avalon.MockStore;
import org.apache.james.test.mock.avalon.MockThreadManager;
import org.apache.james.test.mock.james.InMemorySpoolRepository;
import org.apache.james.test.mock.james.MockFileSystem;
import org.apache.james.test.mock.james.MockMailServer;
import org.apache.james.test.util.Util;
import org.apache.james.transport.mailets.RemoteDelivery;
import org.apache.james.userrepository.MockUsersRepository;
import org.apache.james.util.ConfigurationAdapter;
import org.apache.mailet.HostAddress;
import org.apache.mailet.Mail;
import org.apache.mailet.MailAddress;
import org.apache.mailet.MailetContext;
import org.apache.mailet.base.test.FakeMailContext;
import org.apache.mailet.base.test.FakeMailetConfig;

/**
 * Tests the org.apache.james.smtpserver.SMTPServer unit 
 */
public class SMTPServerRemoteDeliveryIntegrationTest extends TestCase {
    
    private final class AlterableDNSServer implements DNSService {
        
        private InetAddress localhostByName = null;
        
        public Collection<String> findMXRecords(String hostname) {
            List<String> res = new ArrayList<String>();
            if (hostname == null) {
                return res;
            };
            if ("james.apache.org".equals(hostname)) {
                res.add("nagoya.apache.org");
            }
            return res;
        }

        public Iterator<HostAddress> getSMTPHostAddresses(String domainName) {
            throw new UnsupportedOperationException("Unimplemented mock service");
        }

        public InetAddress[] getAllByName(String host) throws UnknownHostException {
            return new InetAddress[] {getByName(host)};
        }

        public InetAddress getByName(String host) throws UnknownHostException {
            if (getLocalhostByName() != null) {
                if ("127.0.0.1".equals(host)) return getLocalhostByName();
            }
            
            if ("1.0.0.127.bl.spamcop.net.".equals(host)) {
                return InetAddress.getByName("localhost");
            }
            
            if ("james.apache.org".equals(host)) {
                return InetAddress.getByName("james.apache.org");
            }
            
            if ("abgsfe3rsf.de".equals(host)) {
                throw new UnknownHostException();
            }
            
            if ("128.0.0.1".equals(host) || "192.168.0.1".equals(host) || "127.0.0.1".equals(host) || "127.0.0.0".equals(host) || "255.0.0.0".equals(host) || "255.255.255.255".equals(host)) {
                return InetAddress.getByName(host);
            }
            
            throw new UnsupportedOperationException("getByName not implemented in mock for host: "+host);
            //return InetAddress.getByName(host);
        }

        public Collection<String> findTXTRecords(String hostname) {
            List<String> res = new ArrayList<String>();
            if (hostname == null) {
                return res;
            };
            if ("2.0.0.127.bl.spamcop.net.".equals(hostname)) {
                res.add("Blocked - see http://www.spamcop.net/bl.shtml?127.0.0.2");
            }
            return res;
        }

        public InetAddress getLocalhostByName() {
            return localhostByName;
        }

        public void setLocalhostByName(InetAddress localhostByName) {
            this.localhostByName = localhostByName;
        }

        public String getHostName(InetAddress addr) {
            return addr.getHostName();
        }

    public InetAddress getLocalHost() throws UnknownHostException {
        return InetAddress.getLocalHost();
    }
    }


    private int m_smtpListenerPort = Util.getNonPrivilegedPort();
    private MockMailServer m_mailServer;
    private SMTPTestConfiguration m_testConfiguration;
    private SMTPServerProtocolHandlerFactory m_smtpServer;
    private AvalonProtocolServer protoserver;
    
    private MockUsersRepository m_usersRepository = new MockUsersRepository();
    private SimpleConnectionManager connectionManager;
    private FakeMailContext mailetContext;
    private MockSocketManager socketManager;
    private MockThreadManager threadManager;
    private MockStore store;

    public SMTPServerRemoteDeliveryIntegrationTest() {
        super("SMTPServerTest");
    }

    public void verifyLastMail(String sender, String recipient, MimeMessage msg) throws IOException, MessagingException {
        Mail mailData = m_mailServer.getLastMail();
        assertNotNull("mail received by mail server", mailData);

        if (sender == null && recipient == null && msg == null) fail("no verification can be done with all arguments null");

        if (sender != null) assertEquals("sender verfication", sender, mailData.getSender().toString());
        if (recipient != null) assertTrue("recipient verfication", mailData.getRecipients().contains(new MailAddress(recipient)));
        if (msg != null) {
            ByteArrayOutputStream bo1 = new ByteArrayOutputStream();
            msg.writeTo(bo1);
            ByteArrayOutputStream bo2 = new ByteArrayOutputStream();
            mailData.getMessage().writeTo(bo2);
            assertEquals(bo1.toString(),bo2.toString());
            assertEquals("message verification", msg, mailData.getMessage());
        }
    }
    
    protected void setUp() throws Exception {
        m_smtpServer = new SMTPServerProtocolHandlerFactory();
        ContainerUtil.enableLogging(m_smtpServer,new MockLogger());
        m_serviceManager = setUpServiceManager();
        ContainerUtil.service(m_smtpServer, m_serviceManager);
        m_smtpServer.setLoader(m_serviceManager);
        m_smtpServer.setDNSService(m_dnsServer);
        m_smtpServer.setLog(new SimpleLog("Test"));
        m_smtpServer.setMailServer(m_mailServer);
        m_smtpServer.setMailetContext(mailetContext);
        
        protoserver = new AvalonProtocolServer();
        protoserver.setConnectionManager(connectionManager);
        protoserver.setFileSystem(new MockFileSystem());
        protoserver.setProtocolHandlerFactory(m_smtpServer);
        protoserver.setSocketManager(socketManager);
        protoserver.setThreadManager(threadManager);
        protoserver.setDNSService(m_dnsServer);
        protoserver.setLog(new SimpleLog("Test"));

        m_testConfiguration = new SMTPTestConfiguration(m_smtpListenerPort);
    }

    protected void tearDown() throws Exception {
        ContainerUtil.dispose(m_mailServer);
        super.tearDown();
    }

    private void finishSetUp(SMTPTestConfiguration testConfiguration) throws Exception {
        testConfiguration.init();
        ConfigurationAdapter conf = new ConfigurationAdapter(testConfiguration);
        m_smtpServer.setConfiguration(conf);
        m_smtpServer.init();
        
        protoserver.setConfiguration(conf);
        protoserver.init();
        m_mailServer.setMaxMessageSizeBytes(m_testConfiguration.getMaxMessageSize()*1024);
    }

    private FakeLoader setUpServiceManager() throws Exception {
        m_serviceManager = new FakeLoader();

        mailetContext = new FakeMailContext();
        m_serviceManager.put(MailetContext.class.getName(), mailetContext);
        m_mailServer = new MockMailServer(new MockUsersRepository());
        m_serviceManager.put(MailServer.ROLE, m_mailServer);
        // Phoenix loader does not understand aliases
        m_serviceManager.put("James", m_mailServer);
        m_serviceManager.put("localusersrepository", m_usersRepository);
        m_serviceManager.put(UsersRepository.ROLE, m_usersRepository);
        socketManager = new MockSocketManager(m_smtpListenerPort);
        
        m_serviceManager.put(SocketManager.ROLE, socketManager);
        threadManager = new MockThreadManager();
        m_serviceManager.put(ThreadManager.ROLE,threadManager );
        
        connectionManager = new SimpleConnectionManager();
        connectionManager.setThreadManager(threadManager);
        connectionManager.setLog(new SimpleLog("CM"));
        connectionManager.setConfiguration(new DefaultConfigurationBuilder());
        connectionManager.init();
        m_serviceManager.put(SimpleConnectionManager.ROLE, connectionManager);
        
        m_dnsServer = new AlterableDNSServer();
        m_serviceManager.put(DNSService.ROLE, m_dnsServer);
        m_serviceManager.put("dnsserver", m_dnsServer);
        store =  new MockStore();
        m_serviceManager.put(Store.ROLE,store);
        return m_serviceManager;
    }

    // RemoteDelivery tests.
    
    InMemorySpoolRepository outgoingSpool;
    private FakeLoader m_serviceManager;
    private AlterableDNSServer m_dnsServer;
    
    private Properties getStandardParameters() {
        Properties parameters = new Properties();
        parameters.put("delayTime", "500 msec, 500 msec, 500 msec"); // msec, sec, minute, hour
        parameters.put("maxRetries", "3");
        parameters.put("deliveryThreads", "1");
        parameters.put("debug", "true");
        parameters.put("sendpartial", "false");
        parameters.put("bounceProcessor", "bounce");
        parameters.put("outgoing", "mocked://outgoing/");
        return parameters;
    }

    /**
     * This has been created to test javamail 1.4 introduced bug.
     * http://issues.apache.org/jira/browse/JAMES-490
     */
    public void testDeliveryToSelfWithGatewayAndBind() throws Exception {
        finishSetUp(m_testConfiguration);
        outgoingSpool = new InMemorySpoolRepository();
        store.add("outgoing", outgoingSpool);
        RemoteDelivery rd = new RemoteDelivery();
        rd.setStore(store);
        rd.setDNSService(m_dnsServer);
        
        FakeMailContext mmc = new FakeMailContext();
        mmc.setAttribute(Constants.AVALON_COMPONENT_MANAGER,m_serviceManager);
        mmc.setAttribute(Constants.HELLO_NAME,"localhost");
        FakeMailetConfig mci = new FakeMailetConfig("Test",mmc,getStandardParameters());
        mci.setProperty("bind", "127.0.0.1");
        mci.setProperty("gateway","127.0.0.1");
        mci.setProperty("gatewayPort",""+m_smtpListenerPort);
        rd.init(mci);
        String sources = "Content-Type: text/plain;\r\nSubject: test\r\n\r\nBody";
        String sender = "test@localhost";
        String recipient = "test@localhost";
        MimeMessage mm = new MimeMessage(Session.getDefaultInstance(new Properties()),new ByteArrayInputStream(sources.getBytes()));
        MailImpl mail = new MailImpl("name",new MailAddress(sender),Arrays.asList(new MailAddress[] {new MailAddress(recipient)}),mm);
        
        rd.service(mail);
        
        while (outgoingSpool.size() > 0) {
            Thread.sleep(1000);
        }

        verifyLastMail(sender, recipient, null);
        
        assertEquals(((String) mm.getContent()).trim(),((String) m_mailServer.getLastMail().getMessage().getContent()).trim());
        
        mail.dispose();
    }

    
    /**
     * This is useful code to run tests on javamail bugs 
     * http://issues.apache.org/jira/browse/JAMES-52
     * 
     * This one passes with javamail 1.4.1EA
     * @throws Exception
     */
    public void test8bitmimeFromStream() throws Exception {
        finishSetUp(m_testConfiguration);
        outgoingSpool = new InMemorySpoolRepository();
        store.add("outgoing", outgoingSpool);
        RemoteDelivery rd = new RemoteDelivery();
        rd.setStore(store);
        rd.setDNSService(m_dnsServer);
        FakeMailContext mmc = new FakeMailContext();
        mmc.setAttribute(Constants.AVALON_COMPONENT_MANAGER,m_serviceManager);
        mmc.setAttribute(Constants.HELLO_NAME,"localhost");
        FakeMailetConfig mci = new FakeMailetConfig("Test",mmc,getStandardParameters());
        mci.setProperty("gateway","127.0.0.1");
        mci.setProperty("gatewayPort",""+m_smtpListenerPort);
        rd.init(mci);
        
        //String sources = "Content-Type: text/plain;\r\nContent-Transfer-Encoding: quoted-printable\r\nSubject: test\r\n\r\nBody=80\r\n";
        String sources = "Content-Type: text/plain; charset=iso-8859-15\r\nContent-Transfer-Encoding: quoted-printable\r\nSubject: test\r\n\r\nBody=80\r\n";
        //String sources = "Content-Type: text/plain; charset=iso-8859-15\r\nContent-Transfer-Encoding: 8bit\r\nSubject: test\r\n\r\nBody\u20AC\r\n";
        String sender = "test@localhost";
        String recipient = "test@localhost";
        MimeMessage mm = new MimeMessage(Session.getDefaultInstance(new Properties()),new ByteArrayInputStream(sources.getBytes()));
        MailImpl mail = new MailImpl("name",new MailAddress(sender),Arrays.asList(new MailAddress[] {new MailAddress(recipient)}),mm);
        
        rd.service(mail);
        
        while (outgoingSpool.size() > 0) {
            Thread.sleep(1000);
        }

        // verifyLastMail(sender, recipient, mm);
        verifyLastMail(sender, recipient, null);
        
        // THIS WOULD FAIL BECAUSE OF THE JAVAMAIL BUG
        assertEquals(mm.getContent(),m_mailServer.getLastMail().getMessage().getContent());
        
        mail.dispose();
    }

    /**
     * This is useful code to run tests on javamail bugs 
     * http://issues.apache.org/jira/browse/JAMES-52
     * 
     * This one passes with javamail 1.4.1EA
     * @throws Exception
     */
    public void test8bitmimeFromStreamWith8bitContent() throws Exception {
        finishSetUp(m_testConfiguration);
        outgoingSpool = new InMemorySpoolRepository();
        
        store.add("outgoing", outgoingSpool);
        RemoteDelivery rd = new RemoteDelivery();
        rd.setStore(store);
        rd.setDNSService(m_dnsServer);
        
        FakeMailContext mmc = new FakeMailContext();
        mmc.setAttribute(Constants.AVALON_COMPONENT_MANAGER,m_serviceManager);
        mmc.setAttribute(Constants.HELLO_NAME,"localhost");
        FakeMailetConfig mci = new FakeMailetConfig("Test",mmc,getStandardParameters());
        mci.setProperty("gateway","127.0.0.1");
        mci.setProperty("gatewayPort",""+m_smtpListenerPort);
        rd.init(mci);
        
        //String sources = "Content-Type: text/plain;\r\nContent-Transfer-Encoding: quoted-printable\r\nSubject: test\r\n\r\nBody=80\r\n";
        //String sources = "Content-Type: text/plain; charset=iso-8859-15\r\nContent-Transfer-Encoding: quoted-printable\r\nSubject: test\r\n\r\nBody=80\r\n";
        String sources = "Content-Type: text/plain; charset=iso-8859-15\r\nContent-Transfer-Encoding: 8bit\r\nSubject: test\r\n\r\nBody\u20AC\r\n";
        String sender = "test@localhost";
        String recipient = "test@localhost";
        MimeMessage mm = new MimeMessage(Session.getDefaultInstance(new Properties()),new ByteArrayInputStream(sources.getBytes()));
        MailImpl mail = new MailImpl("name",new MailAddress(sender),Arrays.asList(new MailAddress[] {new MailAddress(recipient)}),mm);
        
        rd.service(mail);
        
        while (outgoingSpool.size() > 0) {
            Thread.sleep(1000);
        }

        // verifyLastMail(sender, recipient, mm);
        verifyLastMail(sender, recipient, null);
        
        // THIS WOULD FAIL BECAUSE OF THE JAVAMAIL BUG
        assertEquals(mm.getContent(),m_mailServer.getLastMail().getMessage().getContent());
        
        mail.dispose();
    }
    
    /**
     * This is useful code to run tests on javamail bugs 
     * http://issues.apache.org/jira/browse/JAMES-52
     * 
     * This one passes with javamail 1.4.1EA
     * @throws Exception
     */
    public void test8bitmimeFromStreamWithoutContentTransferEncoding() throws Exception {
        finishSetUp(m_testConfiguration);
        outgoingSpool = new InMemorySpoolRepository();        

        store.add("outgoing", outgoingSpool);
        RemoteDelivery rd = new RemoteDelivery();
        rd.setStore(store);
        rd.setDNSService(m_dnsServer);
        
        FakeMailContext mmc = new FakeMailContext();
        mmc.setAttribute(Constants.AVALON_COMPONENT_MANAGER,m_serviceManager);
        mmc.setAttribute(Constants.HELLO_NAME,"localhost");
        FakeMailetConfig mci = new FakeMailetConfig("Test",mmc,getStandardParameters());
        mci.setProperty("gateway","127.0.0.1");
        mci.setProperty("gatewayPort",""+m_smtpListenerPort);
        rd.init(mci);
        
        String sources = "Content-Type: text/plain;\r\nSubject: test\r\n\r\nBody\u03B2\r\n";
        //String sources = "Content-Type: text/plain; charset=iso-8859-15\r\nContent-Transfer-Encoding: quoted-printable\r\nSubject: test\r\n\r\nBody=80\r\n";
        //String sources = "Content-Type: text/plain; charset=iso-8859-15\r\nContent-Transfer-Encoding: 8bit\r\nSubject: test\r\n\r\nBody\u20AC\r\n";
        String sender = "test@localhost";
        String recipient = "test@localhost";
        MimeMessage mm = new MimeMessage(Session.getDefaultInstance(new Properties()),new ByteArrayInputStream(sources.getBytes()));
        MailImpl mail = new MailImpl("name",new MailAddress(sender),Arrays.asList(new MailAddress[] {new MailAddress(recipient)}),mm);
        
        rd.service(mail);
        
        while (outgoingSpool.size() > 0) {
            Thread.sleep(1000);
        }

        // verifyLastMail(sender, recipient, mm);
        verifyLastMail(sender, recipient, null);
        
        // THIS WOULD FAIL BECAUSE OF THE JAVAMAIL BUG
        assertEquals(mm.getContent(),m_mailServer.getLastMail().getMessage().getContent());
        
        mail.dispose();
    }

    /**
     * This is useful code to run tests on javamail bugs 
     * http://issues.apache.org/jira/browse/JAMES-52
     * 
     * This one passes with javamail 1.4.1EA
     * @throws Exception
     */
    public void test8bitmimeFromStreamWithoutContentTransferEncodingSentAs8bit() throws Exception {
        finishSetUp(m_testConfiguration);
        outgoingSpool = new InMemorySpoolRepository();
        
        store.add("outgoing", outgoingSpool);
        RemoteDelivery rd = new RemoteDelivery();
        rd.setStore(store);
        rd.setDNSService(m_dnsServer);
        
        FakeMailContext mmc = new FakeMailContext();
        mmc.setAttribute(Constants.AVALON_COMPONENT_MANAGER,m_serviceManager);
        mmc.setAttribute(Constants.HELLO_NAME,"localhost");
        FakeMailetConfig mci = new FakeMailetConfig("Test",mmc,getStandardParameters());
        mci.setProperty("gateway","127.0.0.1");
        mci.setProperty("gatewayPort",""+m_smtpListenerPort);
        rd.init(mci);
        
        String sources = "Content-Type: text/plain;\r\nSubject: test\r\n\r\nBody=32=48\r\n";
        //String sources = "Content-Type: text/plain; charset=iso-8859-15\r\nContent-Transfer-Encoding: quoted-printable\r\nSubject: test\r\n\r\nBody=80\r\n";
        //String sources = "Content-Type: text/plain; charset=iso-8859-15\r\nContent-Transfer-Encoding: 8bit\r\nSubject: test\r\n\r\nBody\u20AC\r\n";
        String sender = "test@localhost";
        String recipient = "test@localhost";
        MimeMessage mm = new MimeMessage(Session.getDefaultInstance(new Properties()),new ByteArrayInputStream(sources.getBytes()));
        MailImpl mail = new MailImpl("name",new MailAddress(sender),Arrays.asList(new MailAddress[] {new MailAddress(recipient)}),mm);
        
        rd.service(mail);
        
        while (outgoingSpool.size() > 0) {
            Thread.sleep(1000);
        }

        // verifyLastMail(sender, recipient, mm);
        verifyLastMail(sender, recipient, null);
        
        // THIS WOULD FAIL BECAUSE OF THE JAVAMAIL BUG
        assertEquals(mm.getContent(),m_mailServer.getLastMail().getMessage().getContent());
        
        mail.dispose();
    }
    
    /**
     * This is useful code to run tests on javamail bugs 
     * http://issues.apache.org/jira/browse/JAMES-52
     * 
     * This one passes with javamail 1.4.1EA
     * @throws Exception
     */
    public void test8bitmimeWith8bitmimeDisabledInServer() throws Exception {
        finishSetUp(m_testConfiguration);
        outgoingSpool = new InMemorySpoolRepository();
        
        store.add("outgoing", outgoingSpool);
        RemoteDelivery rd = new RemoteDelivery();
        rd.setStore(store);
        rd.setDNSService(m_dnsServer);
        
        FakeMailContext mmc = new FakeMailContext();
        mmc.setAttribute(Constants.AVALON_COMPONENT_MANAGER,m_serviceManager);
        mmc.setAttribute(Constants.HELLO_NAME,"localhost");
        FakeMailetConfig mci = new FakeMailetConfig("Test",mmc,getStandardParameters());
        mci.setProperty("gateway","127.0.0.1");
        mci.setProperty("gatewayPort",""+m_smtpListenerPort);
        mci.setProperty("mail.smtp.allow8bitmime", "false");
        rd.init(mci);
        
        //String sources = "Content-Type: text/plain;\r\nSubject: test\r\n\r\nBody=32=48\r\n";
        //String sources = "Content-Type: text/plain; charset=iso-8859-15\r\nContent-Transfer-Encoding: quoted-printable\r\nSubject: test\r\n\r\nBody=80\r\n";
        String sources = "Content-Type: text/plain; charset=iso-8859-15\r\nContent-Transfer-Encoding: 8bit\r\nSubject: test\r\n\r\nBody\u20AC\r\n";
        String sender = "test@localhost";
        String recipient = "test@localhost";
        MimeMessage mm = new MimeMessage(Session.getDefaultInstance(new Properties()),new ByteArrayInputStream(sources.getBytes()));
        MailImpl mail = new MailImpl("name",new MailAddress(sender),Arrays.asList(new MailAddress[] {new MailAddress(recipient)}),mm);
        
        rd.service(mail);
        
        while (outgoingSpool.size() > 0) {
            Thread.sleep(1000);
        }

        // verifyLastMail(sender, recipient, mm);
        verifyLastMail(sender, recipient, null);
        
        // THIS WOULD FAIL BECAUSE OF THE JAVAMAIL BUG
        assertEquals(mm.getContent(),m_mailServer.getLastMail().getMessage().getContent());
        
        mail.dispose();
    }
}
