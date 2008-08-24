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

package org.apache.james.imapserver.codec.encode.imap4rev1.server;

import java.io.IOException;

import org.apache.james.api.imap.ImapMessage;
import org.apache.james.imap.message.response.imap4rev1.server.STATUSResponse;
import org.apache.james.imapserver.codec.encode.ImapEncoder;
import org.apache.james.imapserver.codec.encode.ImapResponseComposer;
import org.apache.james.imapserver.codec.encode.base.AbstractChainedImapEncoder;

/**
 * Encodes <code>STATUS</code> responses.
 */
public class STATUSResponseEncoder extends AbstractChainedImapEncoder {

    public STATUSResponseEncoder(ImapEncoder next) {
        super(next);
    }

    protected void doEncode(ImapMessage acceptableMessage,
            ImapResponseComposer composer) throws IOException {
        STATUSResponse response = (STATUSResponse) acceptableMessage;
        composer.statusResponse(response.getMessages(), response.getRecent(), 
                response.getUidNext(), response.getUidValidity(), response.getUnseen(), 
                response.getMailbox());

    }

    protected boolean isAcceptable(ImapMessage message) {
        return message != null && message instanceof STATUSResponse;
    }

}
