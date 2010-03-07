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

import org.apache.commons.logging.Log;

import org.apache.james.protocols.api.ProtocolHandlerChain;
import org.apache.james.protocols.api.ProtocolSession;
import org.apache.james.protocols.smtp.SMTPConfiguration;
import org.apache.james.protocols.smtp.SMTPResponse;
import org.apache.james.protocols.smtp.SMTPRetCode;
import org.apache.james.protocols.smtp.SMTPSession;
import org.apache.james.socket.mina.AbstractIoHandler;
import org.apache.james.socket.mina.codec.LineLengthExceededException;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.codec.ProtocolDecoderException;
import org.apache.mina.filter.ssl.SslContextFactory;

/**
 * This IoHandler handling the calling of ConnectHandler and LineHandlers
 * 
 *
 */
public class SMTPIoHandler extends AbstractIoHandler{    
    private Log logger;
    private SMTPConfiguration conf;
    private SslContextFactory contextFactory;

    public SMTPIoHandler(ProtocolHandlerChain chain,
            SMTPConfiguration conf, Log logger) {
        this(chain,conf,logger,null);
    }
    
    public SMTPIoHandler(ProtocolHandlerChain chain,
            SMTPConfiguration conf, Log logger, SslContextFactory contextFactory) {
        super(chain);
        this.conf = conf;
        this.logger = logger;
        this.contextFactory = contextFactory;
    }

    /**
     * @see org.apache.mina.core.service.IoHandler#exceptionCaught(org.apache.mina.core.session.IoSession,
     *      java.lang.Throwable)
     */
    public void exceptionCaught(IoSession session, Throwable exception)
            throws Exception {
        logger.debug("Caught exception: " + session.getCurrentWriteMessage(),
                exception);
        if (exception instanceof ProtocolDecoderException) {
            ProtocolDecoderException e = (ProtocolDecoderException) exception;
            if (e.getCause() instanceof LineLengthExceededException) {
                session.write(new SMTPResponse(SMTPRetCode.SYNTAX_ERROR_COMMAND_UNRECOGNIZED, "Line length exceeded. See RFC 2821 #4.5.3.1."));
                return;
            }
        }

            
        if (session.isConnected()) {
            session.write(new SMTPResponse(SMTPRetCode.LOCAL_ERROR, "Unable to process smtp request"));
        }
        
        
    }

    @Override
    protected ProtocolSession createSession(IoSession session) throws Exception{
        SMTPSession smtpSession;
        if (contextFactory == null) {
            smtpSession= new SMTPSessionImpl(conf, logger, session);
        } else {
            smtpSession= new SMTPSessionImpl(conf, logger, session, contextFactory.newInstance());
        }        
        return smtpSession;
    }

    @Override
    protected String getSessionKey() {
        return SMTPSessionImpl.SMTP_SESSION;
    }
}