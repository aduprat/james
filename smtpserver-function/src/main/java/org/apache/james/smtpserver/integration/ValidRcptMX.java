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

package org.apache.james.smtpserver.integration;

import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import javax.annotation.Resource;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.james.api.dnsservice.DNSService;
import org.apache.james.api.dnsservice.TemporaryResolutionException;
import org.apache.james.api.dnsservice.util.NetMatcher;
import org.apache.james.dsn.DSNStatus;
import org.apache.james.lifecycle.Configurable;
import org.apache.james.lifecycle.LogEnabled;
import org.apache.james.smtpserver.protocol.SMTPRetCode;
import org.apache.james.smtpserver.protocol.SMTPSession;
import org.apache.james.smtpserver.protocol.hook.HookResult;
import org.apache.james.smtpserver.protocol.hook.HookReturnCode;
import org.apache.james.smtpserver.protocol.hook.RcptHook;
import org.apache.mailet.MailAddress;

/**
 * This class can be used to reject email with bogus MX which is send from a authorized user or an authorized
 * network.
 */
public class ValidRcptMX implements LogEnabled, RcptHook, Configurable{

    /** This log is the fall back shared by all instances */
    private static final Log FALLBACK_LOG = LogFactory.getLog(ValidRcptMX.class);
    
    /** Non context specific log should only be used when no context specific log is available */
    private Log serviceLog = FALLBACK_LOG;
    
    private DNSService dnsService = null;

    private static final String LOCALHOST = "localhost";

    private NetMatcher bNetwork = null;


    /**
     * Sets the service log.
     * Where available, a context sensitive log should be used.
     * @param Log not null
     */
    public void setLog(Log log) {
        this.serviceLog = log;
    }
    
    /**
     * Gets the DNS service.
     * @return the dnsService
     */
    public final DNSService getDNSService() {
        return dnsService;
    }

    /**
     * Sets the DNS service.
     * @param dnsService the dnsService to set
     */
    @Resource(name="dnsserver")
    public final void setDNSService(DNSService dnsService) {
        this.dnsService = dnsService;
    }
    
    /**
     * @see org.apache.james.lifecycle.Configurable#configure(org.apache.commons.configuration.Configuration)
     */
    @SuppressWarnings("unchecked")
	public void configure(HierarchicalConfiguration config) throws ConfigurationException {

        List<String> networks = config.getList("invalidMXNetworks");

        if (networks.isEmpty() == false) {
        	
            Collection<String> bannedNetworks = new ArrayList<String>();

            for (int i = 0; i < networks.size(); i++) {
                String network = networks.get(i);
                bannedNetworks.add(network.trim());
            }

            setBannedNetworks(bannedNetworks, dnsService);

            serviceLog.info("Invalid MX Networks: " + bNetwork.toString());

        } else {
            throw new ConfigurationException(
                "Please configure at least on invalid MX network");
        }
        
    }

    /**
     * Set the banned networks
     * 
     * @param networks Collection of networks 
     * @param dnsServer The DNSServer
     */
    public void setBannedNetworks(Collection<String> networks, DNSService dnsServer) {
        bNetwork = new NetMatcher(networks, dnsServer) {
            protected void log(String s) {
                serviceLog.debug(s);
            }
        };
    }

    /**
     * @see org.apache.james.smtpserver.protocol.hook.RcptHook#doRcpt(org.apache.james.smtpserver.protocol.SMTPSession, org.apache.mailet.MailAddress, org.apache.mailet.MailAddress)
     */
    public HookResult doRcpt(SMTPSession session, MailAddress sender, MailAddress rcpt) {

        String domain = rcpt.getDomain();

        // Email should be deliver local
        if (!domain.equals(LOCALHOST)) {
 
            Iterator<String> mx = null;
            try {
                mx = dnsService.findMXRecords(domain).iterator();
            } catch (TemporaryResolutionException e1) {
                return new HookResult(HookReturnCode.DENYSOFT);
            }

            if (mx != null && mx.hasNext()) {
                while (mx.hasNext()) {
                    String mxRec = mx.next();

                     try {
                        String ip = dnsService.getByName(mxRec).getHostAddress();

                        // Check for invalid MX
                        if (bNetwork.matchInetNetwork(ip)) {
                            return new HookResult(HookReturnCode.DENY,SMTPRetCode.AUTH_REQUIRED, DSNStatus.getStatus(DSNStatus.PERMANENT, DSNStatus.SECURITY_AUTH) + " Invalid MX " + session.getRemoteIPAddress() 
                                    + " for domain " + domain + ". Reject email");
                        }
                    } catch (UnknownHostException e) {
                        // Ignore this
                    }
                }
            }
        }
        return new HookResult(HookReturnCode.DECLINED);
    }
}
