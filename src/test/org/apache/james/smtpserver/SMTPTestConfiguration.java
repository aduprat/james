/***********************************************************************
 * Copyright (c) 2000-2006 The Apache Software Foundation.             *
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

import org.apache.avalon.framework.configuration.Configuration;
import org.apache.avalon.framework.configuration.ConfigurationException;
import org.apache.avalon.framework.configuration.DefaultConfiguration;
import org.apache.james.test.util.Util;

public class SMTPTestConfiguration extends DefaultConfiguration {

    private int m_smtpListenerPort;
    private int m_maxMessageSize = 0;
    private String m_authorizedAddresses = "127.0.0.0/8";
    private String m_authorizingMode = "false";
    private boolean m_verifyIdentity = false;
    private Integer m_connectionLimit = null;
    private boolean m_heloResolv = false;
    private boolean m_ehloResolv = false;
    private boolean m_senderDomainResolv = false;
    private boolean m_checkAuthClients = false;
    private int m_maxRcpt = 0;

    
    public SMTPTestConfiguration(int smtpListenerPort) {
        super("smptserver");

        m_smtpListenerPort = smtpListenerPort;
    }

    public void setMaxMessageSize(int kilobytes)
    {
        m_maxMessageSize = kilobytes;
    }
    
    public int getMaxMessageSize() {
        return m_maxMessageSize;
    }

    public String getAuthorizedAddresses() {
        return m_authorizedAddresses;
    }

    public void setAuthorizedAddresses(String authorizedAddresses) {
        m_authorizedAddresses = authorizedAddresses;
    }

    public void setAuthorizingNotRequired() {
        m_authorizingMode = "false";
        m_verifyIdentity = false; 
    }

    public void setAuthorizingRequired() {
        m_authorizingMode = "true";
        m_verifyIdentity = true; 
    }

    public void setAuthorizingAnnounce() {
        m_authorizingMode = "announce";
        m_verifyIdentity = true; 
    }

    public void setConnectionLimit(int iConnectionLimit) {
        m_connectionLimit = new Integer(iConnectionLimit);
    }
    
    public void setHeloResolv() {
        m_heloResolv = true; 
    }
    
    public void setEhloResolv() {
        m_ehloResolv = true; 
    }
    
    public void setSenderDomainResolv() {
        m_senderDomainResolv = true; 
    }
    
    public void setCheckAuthClients(boolean ignore) {
        m_checkAuthClients = ignore; 
    }
    
    public void setMaxRcpt(int maxRcpt) {
        m_maxRcpt = maxRcpt; 
    }

    public void init() throws ConfigurationException {

        setAttribute("enabled", true);

        addChild(Util.getValuedConfiguration("port", "" + m_smtpListenerPort));
        if (m_connectionLimit != null) addChild(Util.getValuedConfiguration("connectionLimit", "" + m_connectionLimit.intValue()));
        
        DefaultConfiguration handlerConfig = new DefaultConfiguration("handler");
        handlerConfig.addChild(Util.getValuedConfiguration("helloName", "myMailServer"));
        handlerConfig.addChild(Util.getValuedConfiguration("connectiontimeout", "360000"));
        handlerConfig.addChild(Util.getValuedConfiguration("authorizedAddresses", m_authorizedAddresses));
        handlerConfig.addChild(Util.getValuedConfiguration("maxmessagesize", "" + m_maxMessageSize));
        handlerConfig.addChild(Util.getValuedConfiguration("authRequired", m_authorizingMode));
        if (m_verifyIdentity) handlerConfig.addChild(Util.getValuedConfiguration("verifyIdentity", "" + m_verifyIdentity));
        
        handlerConfig.addChild(Util.createSMTPHandlerChainConfiguration());
        
        // Add Configuration for Helo checks and Ehlo checks
        Configuration[] heloConfig = handlerConfig.getChild("handlerchain").getChildren("handler");
        for (int i = 0; i < heloConfig.length; i++) {
            if (heloConfig[i] instanceof DefaultConfiguration) {
                String cmd = ((DefaultConfiguration) heloConfig[i]).getAttribute("command",null);
                if (cmd != null) {
                    if ("HELO".equals(cmd)) {
                        ((DefaultConfiguration) heloConfig[i]).addChild(Util.getValuedConfiguration("checkValidHelo",m_heloResolv+""));     
                    } else if ("EHLO".equals(cmd)) {
                        ((DefaultConfiguration) heloConfig[i]).addChild(Util.getValuedConfiguration("checkValidEhlo",m_ehloResolv+""));
                    } else if ("MAIL".equals(cmd)) {
                        ((DefaultConfiguration) heloConfig[i]).addChild(Util.getValuedConfiguration("checkValidSenderDomain",m_senderDomainResolv+""));
                        ((DefaultConfiguration) heloConfig[i]).addChild(Util.getValuedConfiguration("checkAuthClients",m_checkAuthClients+""));
                    } else if ("RCPT".equals(cmd)) {
                        ((DefaultConfiguration) heloConfig[i]).addChild(Util.getValuedConfiguration("maxRcpt",m_maxRcpt+""));
                    }
                }
            }
        }
        
        addChild(handlerConfig);
    }

}
