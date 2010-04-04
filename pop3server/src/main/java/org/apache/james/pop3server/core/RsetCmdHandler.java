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



package org.apache.james.pop3server.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import javax.mail.MessagingException;

import org.apache.james.imap.mailbox.MailboxSession;
import org.apache.james.imap.mailbox.MessageRange;
import org.apache.james.imap.mailbox.MessageResult;
import org.apache.james.imap.mailbox.MessageResult.FetchGroup;
import org.apache.james.imap.mailbox.util.FetchGroupImpl;
import org.apache.james.pop3server.POP3Response;
import org.apache.james.pop3server.POP3Session;
import org.apache.james.protocols.api.CommandHandler;
import org.apache.james.protocols.api.Request;
import org.apache.james.protocols.api.Response;


/**
  * Handles RSET command
  */
public class RsetCmdHandler implements CommandHandler<POP3Session> {
	private final static String COMMAND_NAME = "RSET";
	
	
	/**
     * Handler method called upon receipt of a RSET command.
     * Calls stat() to reset the mailbox.
     *
	 */
    public Response onCommand(POP3Session session, Request request) {
        POP3Response response = null;
        if (session.getHandlerState() == POP3Session.TRANSACTION) {
            stat(session);
            response = new POP3Response(POP3Response.OK_RESPONSE);
        } else {
            response = new POP3Response(POP3Response.ERR_RESPONSE);
        }
        return response;    
    }

   

    /**
     * Implements a "stat".  If the handler is currently in
     * a transaction state, this amounts to a rollback of the
     * mailbox contents to the beginning of the transaction.
     * This method is also called when first entering the
     * transaction state to initialize the handler copies of the
     * user inbox.
     *
     */
    protected void stat(POP3Session session) {
        try {
            MailboxSession mailboxSession = (MailboxSession) session.getState().get(POP3Session.MAILBOX_SESSION);

            List<Long> uids = new ArrayList<Long>();
        	Iterator<MessageResult> it = session.getUserMailbox().getMessages(MessageRange.all(), new FetchGroupImpl(FetchGroup.MINIMAL), mailboxSession);
            while (it.hasNext()) {
            	uids.add(it.next().getUid());
            }
            session.getState().put(POP3Session.UID_LIST, uids);
            session.getState().put(POP3Session.DELETED_UID_LIST, new ArrayList<Long>());
        } catch(MessagingException e) {
            // In the event of an exception being thrown there may or may not be anything in userMailbox
            session.getLogger().error("Unable to STAT mail box ", e);
        }
        
    }

    /**
     * @see org.apache.james.api.protocol.CommonCommandHandler#getImplCommands()
     */
    public Collection<String> getImplCommands() {
        List<String> commands = new ArrayList<String>();
        commands.add(COMMAND_NAME);
        return commands;
    }
    
}