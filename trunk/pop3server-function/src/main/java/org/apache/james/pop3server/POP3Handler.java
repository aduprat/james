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



package org.apache.james.pop3server;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.avalon.framework.container.ContainerUtil;
import org.apache.james.api.protocol.ProtocolHandlerChain;
import org.apache.james.services.MailRepository;
import org.apache.james.socket.api.CRLFTerminatedReader;
import org.apache.james.socket.api.ProtocolContext;
import org.apache.james.socket.api.Watchdog;
import org.apache.james.socket.shared.AbstractProtocolHandler;
import org.apache.mailet.Mail;

/**
 * The handler class for POP3 connections.
 *
 */
public class POP3Handler extends AbstractProtocolHandler implements POP3Session {

    private ProtocolContext context;
    
    private Map<String,Object> stateMap = new HashMap<String,Object>();
   

    /**
     * The per-service configuration data that applies to all handlers
     */
    private final POP3HandlerConfigurationData theConfigData;

    /**
     * The mail server's copy of the user's inbox
     */
    private MailRepository userInbox;

    /**
     * The current transaction state of the handler
     */
    private int handlerState;

    /**
     * A dynamic list representing the set of
     * emails in the user's inbox at any given time
     * during the POP3 transaction.
     */
    private List<Mail> userMailbox = new ArrayList<Mail>();

    /**
     * A snapshot list representing the set of
     * emails in the user's inbox at the beginning
     * of the transaction
     */
    private List<Mail> backupUserMailbox;  


    /**
     * The POP3HandlerChain object set by POP3Server
     */
    private final ProtocolHandlerChain handlerChain;

    /**
     * The session termination status
     */
    private boolean sessionEnded = false;

    
    /**
     * If not null every line is sent to this command handler instead
     * of the default "command parsing -> dipatching" procedure.
     */
    private LinkedList<LineHandler> lineHandlers;

    /**
     * Connect Handlers
     */
    private final LinkedList<ConnectHandler> connectHandlers;
    
    private int writtenBytes = 0;
    
    public POP3Handler(final POP3HandlerConfigurationData theConfigData, final ProtocolHandlerChain handlerChain) {
        this.theConfigData = theConfigData;
        this.handlerChain = handlerChain;
        connectHandlers = handlerChain.getHandlers(ConnectHandler.class);
        lineHandlers = handlerChain.getHandlers(LineHandler.class);
    }
    

    /**
     * @see org.apache.james.socket.shared.AbstractProtocolHandler#handleProtocolInternal(org.apache.james.socket.api.ProtocolContext)
     */
    public void handleProtocolInternal(ProtocolContext context) throws IOException {
        this.context = context;
        handlerState = AUTHENTICATION_READY;
        sessionEnded = false;


        //Session started - RUN all connect handlers
        if(connectHandlers != null) {
            int count = connectHandlers.size();
            for(int i = 0; i < count; i++) {
                connectHandlers.get(i).onConnect(this);
                if(sessionEnded) {
                    break;
                }
            }
        }

        context.getWatchdog().start();
        while(!sessionEnded) {
            String line = null;
            // parse the command
            try {
                line = context.getInputReader().readLine();
                if (line != null) {
                    line = line.trim();
                }
            } catch (CRLFTerminatedReader.TerminationException te) {
                context.writeLoggedFlushedResponse("-ERR Syntax error at character position " + te.position() + ". CR and LF must be CRLF paired.  See RFC 1939 #3.");
            }

          if (line == null) {
              break;
          }

          if (lineHandlers.size() > 0) {
              ((LineHandler) lineHandlers.getLast()).onLine(this, line);
          } else {
              sessionEnded = true;
          }
          context.getWatchdog().reset();
          
        }
        context.getWatchdog().stop();
        if (context.getLogger().isInfoEnabled()) {
            StringBuilder logBuffer =
                new StringBuilder(128)
                    .append("Connection for ")
                    .append(getUser())
                    .append(" from ")
                    .append(context.getRemoteHost())
                    .append(" (")
                    .append(context.getRemoteIP())
                    .append(") closed.");
            context.getLogger().info(logBuffer.toString());
        }
       
       
    }
    
    /**
     * @see org.apache.james.socket.AbstractJamesHandler#fatalFailure(java.lang.RuntimeException, ProtocolContext)
     */
    public void fatalFailure(RuntimeException e, ProtocolContext context) {
        try {
            context.getOutputWriter().println(POP3Response.ERR_RESPONSE + " Error closing connection.");
            context.getOutputWriter().flush();
        } catch (Throwable t) {
            
        }
    }

    /**
     * Resets the handler data to a basic state.
     */
    public void resetHandlerInternal() {

        stateMap.clear();
        userInbox = null;
        if (userMailbox != null) {
            Iterator<Mail> i = userMailbox.iterator();
            while (i.hasNext()) {
                ContainerUtil.dispose(i.next());
            }
            userMailbox.clear();
            userMailbox = null;
        }

        if (backupUserMailbox != null) {
            Iterator<Mail> i = backupUserMailbox.iterator();
            while (i.hasNext()) {
                ContainerUtil.dispose(i.next());
            }
            backupUserMailbox.clear();
            backupUserMailbox = null;
        }
        
        // empty any previous line handler and add self (command dispatcher)
        // as the default.
        lineHandlers = handlerChain.getHandlers(LineHandler.class);
    }

    /**
     * @see org.apache.james.pop3server.POP3Session#getWatchdog()
     */
    public Watchdog getWatchdog() {
        return context.getWatchdog();
    }

    /**
     * @see org.apache.james.pop3server.POP3Session#getConfigurationData()
     */
    public POP3HandlerConfigurationData getConfigurationData() {
        return theConfigData;
    }

    /**
     * @see org.apache.james.pop3server.POP3Session#getHandlerState()
     */
    public int getHandlerState() {
        return handlerState;
    }

    /**
     * @see org.apache.james.pop3server.POP3Session#setHandlerState(int)
     */
    public void setHandlerState(int handlerState) {
        this.handlerState = handlerState;
    }

    /**
     * @see org.apache.james.pop3server.POP3Session#getUserInbox()
     */
    public MailRepository getUserInbox() {
        return userInbox;
    }

    /**
     * @see org.apache.james.pop3server.POP3Session#setUserInbox(org.apache.james.services.MailRepository)
     */
    public void setUserInbox(MailRepository userInbox) {
        this.userInbox = userInbox;
    }

    /**
     * @see org.apache.james.pop3server.POP3Session#getUserMailbox()
     */
    public List<Mail> getUserMailbox() {
        return userMailbox;
    }

    /**
     * @see org.apache.james.pop3server.POP3Session#setUserMailbox(List)
     */
    public void setUserMailbox(List<Mail> userMailbox) {
        this.userMailbox = userMailbox;
    }

    /**
     * @see org.apache.james.pop3server.POP3Session#getBackupUserMailbox()
     */
    public List<Mail> getBackupUserMailbox() {
        return backupUserMailbox;
    }


    /**
     * @see org.apache.james.pop3server.POP3Session#setUserMailbox(List)
     */
    public void setBackupUserMailbox(List<Mail> backupUserMailbox) {
        this.backupUserMailbox = backupUserMailbox;
    }

    /**
     * @see org.apache.james.pop3server.POP3Session#getOutputStream()
     */
    public OutputStream getOutputStream() {
        return context.getOutputStream();
    }


	/**
	 * @see org.apache.james.api.protocol.TLSSupportedSession#isStartTLSSupported()
	 */
	public boolean isStartTLSSupported() {
		return getConfigurationData().isStartTLSSupported();
	}

	/**
	 * @see org.apache.james.pop3server.POP3Session#writePOP3Response(org.apache.james.pop3server.POP3Response)
	 */
    public void writePOP3Response(POP3Response response) {
        // Write a single-line or multiline response
        if (response != null) {
           
            List<CharSequence> responseList = response.getLines();
            if (responseList != null) {

                for (int k = 0; k < responseList.size(); k++) {
                	StringBuffer respBuff = new StringBuffer(256);
                    if (k == 0) {
                    	respBuff.append(response.getRetCode());
                        respBuff.append(" ");
                        respBuff.append(response.getLines().get(k));
                      
                    } else {
                        respBuff.append(response.getLines().get(k));
                    }
                    context.writeLoggedFlushedResponse(respBuff.toString());
               
                    checkWatchDog(respBuff.toString());

                }
            }
            
            if (response.isEndSession()) {
                sessionEnded = true;
            } 
            
        }
    }

    private void checkWatchDog(String lines) {
    	byte[] lineArray = lines.getBytes();
    	
    	for(int i = 0; i < lineArray.length; i++) {
    		writtenBytes += lineArray[i];
    		
    		if (writtenBytes > getConfigurationData().getResetLength()) {
    			getWatchdog().reset();
    			writtenBytes = 0;
    		}
    	}
    }
    /**
     * @see org.apache.james.pop3server.POP3Session#getState()
     */
    public Map<String, Object> getState() {
        return stateMap;
    }


	public void resetState() {
		stateMap.clear();
	}
	
}
