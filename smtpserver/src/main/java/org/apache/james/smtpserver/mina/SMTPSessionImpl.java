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

package org.apache.james.smtpserver.mina;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import javax.net.ssl.SSLContext;

import org.apache.commons.logging.Log;
import org.apache.james.api.protocol.LineHandler;
import org.apache.james.smtpserver.mina.filter.SMTPResponseFilter;
import org.apache.james.smtpserver.mina.filter.TarpitFilter;
import org.apache.james.smtpserver.protocol.SMTPConfiguration;
import org.apache.james.smtpserver.protocol.SMTPSession;
import org.apache.james.socket.mina.AbstractMINASession;
import org.apache.james.socket.mina.filter.FilterLineHandlerAdapter;
import org.apache.mina.core.session.IoSession;

public class SMTPSessionImpl extends AbstractMINASession implements SMTPSession {

        public final static String SMTP_SESSION = "SMTP_SESSION";
        private static Random random = new Random();

        private boolean relayingAllowed;

        private String smtpID;

        private Map<String, Object> connectionState;

        private SMTPConfiguration theConfigData;

        private int lineHandlerCount = 0;

        public SMTPSessionImpl(SMTPConfiguration theConfigData,
                Log logger, IoSession session, SSLContext context) {
            super(logger, session, context);
        	this.theConfigData = theConfigData;
            connectionState = new HashMap<String, Object>();
            smtpID = random.nextInt(1024) + "";

            relayingAllowed = theConfigData.isRelayingAllowed(getRemoteIPAddress());
        }

        public SMTPSessionImpl(SMTPConfiguration theConfigData,
                Log logger, IoSession session) {
            this(theConfigData, logger, session, null);
        }
        /**
         * @see org.apache.james.smtpserver.protocol.SMTPSession#getConnectionState()
         */
        public Map<String, Object> getConnectionState() {
            return connectionState;
        }
        
        /**
         * @see org.apache.james.smtpserver.protocol.SMTPSession#getSessionID()
         */
        public String getSessionID() {
            return smtpID;
        }

        /**
         * @see org.apache.james.smtpserver.protocol.SMTPSession#getState()
         */
        @SuppressWarnings("unchecked")
        public Map<String, Object> getState() {
            Map<String, Object> res = (Map<String, Object>) getConnectionState()
                    .get(SMTPSession.SESSION_STATE_MAP);
            if (res == null) {
                res = new HashMap<String, Object>();
                getConnectionState().put(SMTPSession.SESSION_STATE_MAP, res);
            }
            return res;
        }

        /**
         * @see org.apache.james.smtpserver.protocol.SMTPSession#isRelayingAllowed()
         */
        public boolean isRelayingAllowed() {
            return relayingAllowed;
        }

        /**
         * @see org.apache.james.smtpserver.protocol.SMTPSession#resetState()
         */
        public void resetState() {
            // remember the ehlo mode between resets
            Object currentHeloMode = getState().get(CURRENT_HELO_MODE);

            getState().clear();

            // start again with the old helo mode
            if (currentHeloMode != null) {
                getState().put(CURRENT_HELO_MODE, currentHeloMode);
            }
        }

        /**
         * @see org.apache.james.smtpserver.protocol.SMTPSession#popLineHandler()
         */
        public void popLineHandler() {
            getIoSession().getFilterChain()
                    .remove("lineHandler" + lineHandlerCount);
            lineHandlerCount--;
        }

        /**
         * @see org.apache.james.smtpserver.protocol.SMTPSession#pushLineHandler(org.apache.james.smtpserver.protocol.LineHandler)
         */
        public void pushLineHandler(LineHandler<SMTPSession> overrideCommandHandler) {
            lineHandlerCount++;
            getIoSession().getFilterChain().addAfter(SMTPResponseFilter.NAME,
                    "lineHandler" + lineHandlerCount,
                    new FilterLineHandlerAdapter<SMTPSession>(overrideCommandHandler,SMTP_SESSION));
        }



        /**
         * @see org.apache.james.smtpserver.protocol.SMTPSession#getHelloName()
         */
        public String getHelloName() {
            return theConfigData.getHelloName();
        }


        /**
         * @see org.apache.james.smtpserver.protocol.SMTPSession#getMaxMessageSize()
         */
        public long getMaxMessageSize() {
            return theConfigData.getMaxMessageSize();
        }


        /**
         * @see org.apache.james.smtpserver.protocol.SMTPSession#getRcptCount()
         */
        @SuppressWarnings("unchecked")
        public int getRcptCount() {
            int count = 0;

            // check if the key exists
            if (getState().get(SMTPSession.RCPT_LIST) != null) {
                count = ((Collection) getState().get(SMTPSession.RCPT_LIST)).size();
            }

            return count;
        }

        /**
         * @see org.apache.james.smtpserver.protocol.SMTPSession#getSMTPGreeting()
         */
        public String getSMTPGreeting() {
            return theConfigData.getSMTPGreeting();
        }


        /**
         * @see org.apache.james.smtpserver.protocol.SMTPSession#isAuthSupported()
         */
        public boolean isAuthSupported() {
            return theConfigData.isAuthRequired(socketAddress.getAddress().getHostAddress());
        }


        /**
         * @see org.apache.james.smtpserver.protocol.SMTPSession#setRelayingAllowed(boolean)
         */
        public void setRelayingAllowed(boolean relayingAllowed) {
            this.relayingAllowed = relayingAllowed;
        }


        /**
         * @see org.apache.james.smtpserver.protocol.SMTPSession#sleep(long)
         */
        public void sleep(long ms) {
            session.getFilterChain().addAfter("connectionFilter", "tarpitFilter",new TarpitFilter(ms));
        }


        /**
         * @see org.apache.james.smtpserver.protocol.SMTPSession#useAddressBracketsEnforcement()
         */
        public boolean useAddressBracketsEnforcement() {
            return theConfigData.useAddressBracketsEnforcement();
        }

        /**
         * @see org.apache.james.smtpserver.protocol.SMTPSession#useHeloEhloEnforcement()
         */
        public boolean useHeloEhloEnforcement() {
            return theConfigData.useHeloEhloEnforcement();
        }


}
