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



package org.apache.james.smtpserver.protocol.core.fastfail;


import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.james.dsn.DSNStatus;
import org.apache.james.lifecycle.Configurable;
import org.apache.james.smtpserver.protocol.SMTPRetCode;
import org.apache.james.smtpserver.protocol.SMTPSession;
import org.apache.james.smtpserver.protocol.hook.HookResult;
import org.apache.james.smtpserver.protocol.hook.HookReturnCode;
import org.apache.james.smtpserver.protocol.hook.RcptHook;
import org.apache.mailet.MailAddress;

public class MaxRcptHandler implements RcptHook, Configurable {

    private int maxRcpt = 0;

    /*
     * (non-Javadoc)
     * @see org.apache.james.lifecycle.Configurable#configure(org.apache.commons.configuration.HierarchicalConfiguration)
     */
    public void configure(HierarchicalConfiguration handlerConfiguration)
            throws ConfigurationException {
        int maxRcpt = handlerConfiguration.getInt("maxRcpt", 0);
        setMaxRcpt(maxRcpt);
    }

    /**
     * Set the max rcpt for wich should be accepted
     * 
     * @param maxRcpt
     *            The max rcpt count
     */
    public void setMaxRcpt(int maxRcpt) {
        this.maxRcpt = maxRcpt;
    }
   
    /**
     * @see org.apache.james.smtpserver.protocol.hook.RcptHook#doRcpt(org.apache.james.smtpserver.protocol.SMTPSession, org.apache.mailet.MailAddress, org.apache.mailet.MailAddress)
     */
    public HookResult doRcpt(SMTPSession session, MailAddress sender, MailAddress rcpt) {
        if ((session.getRcptCount() + 1) > maxRcpt) {
            session.getLogger().info("Maximum recipients of " + maxRcpt + " reached");
            
            return new HookResult(HookReturnCode.DENY, SMTPRetCode.SYSTEM_STORAGE_ERROR, DSNStatus.getStatus(DSNStatus.NETWORK, DSNStatus.DELIVERY_TOO_MANY_REC)
                    + " Requested action not taken: max recipients reached");
        } else {
            return new HookResult(HookReturnCode.DECLINED);
        }
    }
}