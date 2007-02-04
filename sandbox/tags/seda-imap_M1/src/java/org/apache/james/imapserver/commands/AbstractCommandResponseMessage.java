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

package org.apache.james.imapserver.commands;

import org.apache.avalon.framework.logger.AbstractLogEnabled;
import org.apache.avalon.framework.logger.Logger;
import org.apache.james.imapserver.ImapResponse;
import org.apache.james.imapserver.ImapSession;
import org.apache.james.imapserver.store.MailboxException;

public abstract class AbstractCommandResponseMessage extends AbstractLogEnabled implements ImapResponseMessage {

    private final ImapCommand command;

    public AbstractCommandResponseMessage(final ImapCommand command) {
        super();
        this.command = command;
    }
    
    public ImapCommand getCommand() {
        return command;
    }

    public void encode(ImapResponse response, ImapSession session) {
        try {
            doEncode(response, session, command);
        } catch (MailboxException e) {
            // TODO: it seems wrong for session to throw a mailbox exception
            // TODO: really, errors in unsolicited response should not
            // TODO: impact the execution of this command
            final Logger logger = getLogger();
            if (logger != null) {
                logger.debug("error processing command ", e);
            }
            response.commandFailed( command, e.getResponseCode(), e.getMessage() );            
        }
    }
    abstract void doEncode(ImapResponse response, ImapSession session, ImapCommand command) throws MailboxException;

}
