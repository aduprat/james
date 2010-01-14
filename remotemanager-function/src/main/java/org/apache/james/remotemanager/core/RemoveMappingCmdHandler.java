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

package org.apache.james.remotemanager.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.james.api.vut.management.VirtualUserTableManagementException;
import org.apache.james.remotemanager.CommandHelp;
import org.apache.james.remotemanager.RemoteManagerRequest;
import org.apache.james.remotemanager.RemoteManagerResponse;
import org.apache.james.remotemanager.RemoteManagerSession;

public class RemoveMappingCmdHandler extends AbstractMappingCmdHandler {
    private CommandHelp help = new CommandHelp("removemapping ([table=virtualusertablename]) [toUser@toDomain] [fromMapping]","remove mapping for the given emailaddress");

    /*
     * (non-Javadoc)
     * @see org.apache.james.remotemanager.CommandHandler#onCommand(org.apache.james.remotemanager.RemoteManagerSession, org.apache.james.remotemanager.RemoteManagerRequest)
     */
    public RemoteManagerResponse onCommand(RemoteManagerSession session, RemoteManagerRequest request) {
        RemoteManagerResponse response;
        String parameters = request.getArgument();
        String[] args = null;

        if (parameters != null)
            args = parameters.split(" ");

        // check if the command was called correct
        if (parameters == null || parameters.trim().equals("") || args.length < 2 || args.length > 3) {
            response = new RemoteManagerResponse("Usage: " + help.getSyntax());
        } else {
            try {
                response = new RemoteManagerResponse("Removing mapping successful: " + mappingAction(args, REMOVE_MAPPING_ACTION));
            } catch (VirtualUserTableManagementException e) {
                session.getLogger().error("Error on  removing mapping: " + e);
                response = new RemoteManagerResponse("Error on removing mapping: " + e);
            } catch (IllegalArgumentException e) {
                session.getLogger().error("Error on  removing mapping: " + e);
                response = new RemoteManagerResponse("Error on removing mapping: " + e);
            }
        }
        return response;
    }

    /**
     * @see org.apache.james.api.protocol.CommonCommandHandler#getImplCommands()
     */
    public Collection<String> getImplCommands() {
        List<String> commands = new ArrayList<String>();
        commands.add(REMOVE_MAPPING_ACTION);
        return commands;
    }

    /**
     * @see org.apache.james.remotemanager.CommandHandler#getHelp()
     */
    public CommandHelp getHelp() {
        return help;
    }

}
