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
package org.apache.james.smtpserver.netty;

import java.nio.charset.Charset;

import javax.annotation.Resource;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.dnsservice.library.netmatcher.NetMatcher;
import org.apache.james.protocols.api.ProtocolSession;
import org.apache.james.protocols.api.ProtocolSessionFactory;
import org.apache.james.protocols.api.ProtocolTransport;
import org.apache.james.protocols.api.handler.HandlersPackage;
import org.apache.james.protocols.impl.ResponseEncoder;
import org.apache.james.protocols.lib.netty.AbstractProtocolAsyncServer;
import org.apache.james.protocols.smtp.SMTPConfiguration;
import org.apache.james.protocols.smtp.SMTPProtocol;
import org.apache.james.protocols.smtp.SMTPResponse;
import org.apache.james.smtpserver.CoreCmdHandlerLoader;
import org.apache.james.smtpserver.ExtendedSMTPSession;
import org.apache.james.smtpserver.jmx.JMXHandlersLoader;
import org.jboss.netty.channel.ChannelUpstreamHandler;
import org.jboss.netty.handler.codec.oneone.OneToOneEncoder;

/**
 * NIO SMTPServer which use Netty
 */
public class SMTPServer extends AbstractProtocolAsyncServer implements SMTPServerMBean {

    /**
     * Whether authentication is required to use this SMTP server.
     */
    private final static int AUTH_DISABLED = 0;
    private final static int AUTH_REQUIRED = 1;
    private final static int AUTH_ANNOUNCE = 2;
    private int authRequired = AUTH_DISABLED;
    

    /**
     * Whether the server needs helo to be send first
     */
    private boolean heloEhloEnforcement = false;

    /**
     * SMTPGreeting to use
     */
    private String smtpGreeting = null;

    /**
     * This is a Network Matcher that should be configured to contain authorized
     * networks that bypass SMTP AUTH requirements.
     */
    private NetMatcher authorizedNetworks = null;

    /**
     * The maximum message size allowed by this SMTP server. The default value,
     * 0, means no limit.
     */
    private long maxMessageSize = 0;

    /**
     * The number of bytes to read before resetting the connection timeout
     * timer. Defaults to 20 KB.
     */
    private int lengthReset = 20 * 1024;

    /**
     * The configuration data to be passed to the handler
     */
    private final SMTPConfiguration theConfigData = new SMTPHandlerConfigurationDataImpl();

    private boolean addressBracketsEnforcement = true;

    private boolean verifyIdentity;

    private DNSService dns;
    private String authorizedAddresses;

    private final static ResponseEncoder SMTP_RESPONSE_ENCODER = new ResponseEncoder(SMTPResponse.class, Charset.forName("US-ASCII"));
    
    private SMTPChannelUpstreamHandler coreHandler;

    @Resource(name = "dnsservice")
    public void setDNSService(DNSService dns) {
        this.dns = dns;
    }
    
    @Override
    protected void preInit() throws Exception {
        super.preInit();
        if (authorizedAddresses != null) {
            java.util.StringTokenizer st = new java.util.StringTokenizer(authorizedAddresses, ", ", false);
            java.util.Collection<String> networks = new java.util.ArrayList<String>();
            while (st.hasMoreTokens()) {
                String addr = st.nextToken();
                networks.add(addr);
            }
            authorizedNetworks = new NetMatcher(networks, dns);
        }
        SMTPProtocol transport = new SMTPProtocol(getProtocolHandlerChain(), theConfigData) {

            @Override
            public ProtocolSessionFactory getProtocolSessionFactory() {
                return new ProtocolSessionFactory() {
                    
                    @Override
                    public ProtocolSession newSession(ProtocolTransport transport) {
                        return new ExtendedSMTPSession(theConfigData, getLogger(), transport);
                    }
                };
            }
            
        };
        coreHandler = new SMTPChannelUpstreamHandler(getProtocolHandlerChain(), transport.getProtocolSessionFactory(), getLogger(), getSSLContext(), getEnabledCipherSuites());
        
    }

    @Override
    public void doConfigure(final HierarchicalConfiguration configuration) throws ConfigurationException {
        super.doConfigure(configuration);
        if (isEnabled()) {
            String authRequiredString = configuration.getString("authRequired", "false").trim().toLowerCase();
            if (authRequiredString.equals("true"))
                authRequired = AUTH_REQUIRED;
            else if (authRequiredString.equals("announce"))
                authRequired = AUTH_ANNOUNCE;
            else
                authRequired = AUTH_DISABLED;
            if (authRequired != AUTH_DISABLED) {
                getLogger().info("This SMTP server requires authentication.");
            } else {
                getLogger().info("This SMTP server does not require authentication.");
            }

            authorizedAddresses = configuration.getString("authorizedAddresses", null);
            if (authRequired == AUTH_DISABLED && authorizedAddresses == null) {
                /*
                 * if SMTP AUTH is not required then we will use
                 * authorizedAddresses to determine whether or not to relay
                 * e-mail. Therefore if SMTP AUTH is not required, we will not
                 * relay e-mail unless the sending IP address is authorized.
                 * 
                 * Since this is a change in behavior for James v2, create a
                 * default authorizedAddresses network of 0.0.0.0/0, which
                 * matches all possible addresses, thus preserving the current
                 * behavior.
                 * 
                 * James v3 should require the <authorizedAddresses> element.
                 */
                authorizedAddresses = "0.0.0.0/0.0.0.0";
            }

          
            if (authorizedNetworks != null) {
                getLogger().info("Authorized addresses: " + authorizedNetworks.toString());
            }

            // get the message size limit from the conf file and multiply
            // by 1024, to put it in bytes
            maxMessageSize = configuration.getLong("maxmessagesize", maxMessageSize) * 1024;
            if (maxMessageSize > 0) {
                getLogger().info("The maximum allowed message size is " + maxMessageSize + " bytes.");
            } else {
                getLogger().info("No maximum message size is enforced for this server.");
            }

            heloEhloEnforcement = configuration.getBoolean("heloEhloEnforcement", true);

            if (authRequiredString.equals("true"))
                authRequired = AUTH_REQUIRED;

            // get the smtpGreeting
            smtpGreeting = configuration.getString("smtpGreeting", null);

            addressBracketsEnforcement = configuration.getBoolean("addressBracketsEnforcement", true);

            verifyIdentity = configuration.getBoolean("verifyIdentity", true);

        }
    }

    /**
     * @see org.apache.james.socket.AbstractConfigurableAsyncServer.AbstractAsyncServer#getDefaultPort()
     */
    protected int getDefaultPort() {
        return 25;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.apache.james.socket.ServerMBean#getServiceType()
     */
    public String getServiceType() {
        return "SMTP Service";
    }

    /**
     * A class to provide SMTP handler configuration to the handlers
     */
    public class SMTPHandlerConfigurationDataImpl implements SMTPConfiguration {

        /**
         * @see org.apache.james.protocols.smtp.SMTPConfiguration#getHelloName()
         */
        public String getHelloName() {
            return SMTPServer.this.getHelloName();
        }

        /**
         * @see org.apache.james.protocols.smtp.SMTPConfiguration#getResetLength()
         */
        public int getResetLength() {
            return SMTPServer.this.lengthReset;
        }

        /**
         * @see org.apache.james.protocols.smtp.SMTPConfiguration#getMaxMessageSize()
         */
        public long getMaxMessageSize() {
            return SMTPServer.this.maxMessageSize;
        }

        /**
         * @see org.apache.james.protocols.smtp.SMTPConfiguration#isAuthSupported(String)
         */
        public boolean isRelayingAllowed(String remoteIP) {
            boolean relayingAllowed = false;
            if (authorizedNetworks != null) {
                relayingAllowed = SMTPServer.this.authorizedNetworks.matchInetNetwork(remoteIP);
            }
            return relayingAllowed;
        }

        /**
         * @see org.apache.james.protocols.smtp.SMTPConfiguration#useHeloEhloEnforcement()
         */
        public boolean useHeloEhloEnforcement() {
            return SMTPServer.this.heloEhloEnforcement;
        }

        /**
         * @see org.apache.james.protocols.smtp.SMTPConfiguration#getSMTPGreeting()
         */
        public String getSMTPGreeting() {
            return SMTPServer.this.smtpGreeting;
        }

        /**
         * @see org.apache.james.protocols.smtp.SMTPConfiguration#useAddressBracketsEnforcement()
         */
        public boolean useAddressBracketsEnforcement() {
            return SMTPServer.this.addressBracketsEnforcement;
        }

        /**
         * @see org.apache.james.protocols.smtp.SMTPConfiguration#isAuthRequired(java.lang.String)
         */
        public boolean isAuthRequired(String remoteIP) {
            if (SMTPServer.this.authRequired == AUTH_ANNOUNCE)
                return true;
            boolean authRequired = SMTPServer.this.authRequired != AUTH_DISABLED;
            if (authorizedNetworks != null) {
                authRequired = authRequired && !SMTPServer.this.authorizedNetworks.matchInetNetwork(remoteIP);
            }
            return authRequired;
        }

        /**
         * @see org.apache.james.protocols.smtp.SMTPConfiguration#isStartTLSSupported()
         */
        public boolean isStartTLSSupported() {
            return SMTPServer.this.isStartTLSSupported();
        }

        /**
         * Return true if the username and mail from must match for a authorized
         * user
         * 
         * @return verify
         */
        public boolean verifyIdentity() {
            return SMTPServer.this.verifyIdentity;
        }

    }

    /*
     * (non-Javadoc)
     * 
     * @see org.apache.james.smtpserver.SMTPServerMBean#getMaximalMessageSize()
     */
    public long getMaximalMessageSize() {
        return maxMessageSize;
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.apache.james.smtpserver.SMTPServerMBean#getAddressBracketsEnforcement
     * ()
     */
    public boolean getAddressBracketsEnforcement() {
        return addressBracketsEnforcement;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.apache.james.smtpserver.SMTPServerMBean#getHeloEhloEnforcement()
     */
    public boolean getHeloEhloEnforcement() {
        return heloEhloEnforcement;
    }
    
    /*
     * (non-Javadoc)
     * 
     * @see
     * org.apache.james.protocols.lib.netty.AbstractConfigurableAsyncServer#
     * getDefaultJMXName()
     */
    protected String getDefaultJMXName() {
        return "smtpserver";
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.apache.james.smtpserver.netty.SMTPServerMBean#setMaximalMessageSize
     * (long)
     */
    public void setMaximalMessageSize(long maxSize) {
        this.maxMessageSize = maxSize;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.apache.james.smtpserver.netty.SMTPServerMBean#
     * setAddressBracketsEnforcement(boolean)
     */
    public void setAddressBracketsEnforcement(boolean enforceAddressBrackets) {
        this.addressBracketsEnforcement = enforceAddressBrackets;
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.apache.james.smtpserver.netty.SMTPServerMBean#setHeloEhloEnforcement
     * (boolean)
     */
    public void setHeloEhloEnforcement(boolean enforceHeloEHlo) {
        this.heloEhloEnforcement = enforceHeloEHlo;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.apache.james.smtpserver.netty.SMTPServerMBean#getHeloName()
     */
    public String getHeloName() {
        return theConfigData.getHelloName();
    }

    @Override
    protected ChannelUpstreamHandler createCoreHandler() {
        return coreHandler;
    }

    @Override
    protected OneToOneEncoder createEncoder() {
        return SMTP_RESPONSE_ENCODER;
    }

    @Override
    protected Class<? extends HandlersPackage> getCoreHandlersPackage() {
        return CoreCmdHandlerLoader.class;
    }

    @Override
    protected Class<? extends HandlersPackage> getJMXHandlersPackage() {
        return JMXHandlersLoader.class;
    }

}
