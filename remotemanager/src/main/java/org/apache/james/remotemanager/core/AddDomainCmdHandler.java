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

import javax.annotation.Resource;

import org.apache.james.api.protocol.Request;
import org.apache.james.api.protocol.Response;
import org.apache.james.management.DomainListManagementException;
import org.apache.james.management.DomainListManagementService;
import org.apache.james.remotemanager.CommandHandler;
import org.apache.james.remotemanager.CommandHelp;
import org.apache.james.remotemanager.RemoteManagerResponse;
import org.apache.james.remotemanager.RemoteManagerSession;

/**
 * Handler which is called when a ADDEDOMAIN command is dispatched
 *
 */
public class AddDomainCmdHandler implements CommandHandler{
    
    private final static String COMMAND_NAME = "ADDDOMAIN";
    private CommandHelp help = new CommandHelp("adddomain [domainname]","add domain to local domains");

    private DomainListManagementService domService;

    @Resource(name="domainlistmanagement")
    public final void setDomainListManagement(DomainListManagementService domService) {
        this.domService = domService;
    }
    
    /**
     * @see org.apache.james.remotemanager.CommandHandler#getHelp()
     */
    public CommandHelp getHelp() {
        return help;
    }

    /*
     * (non-Javadoc)
     * @see org.apache.james.api.protocol.CommandHandler#onCommand(org.apache.james.api.protocol.LogEnabledSession, org.apache.james.api.protocol.Request)
     */
    public Response onCommand(RemoteManagerSession session, Request request) {
        RemoteManagerResponse response = null;
        String parameters = request.getArgument();
        // check if the command was called correct
        if (parameters == null) {
            response = new RemoteManagerResponse("Usage: " + help.getSyntax());
            return response;
        }
        
        try {
            if(domService.addDomain(parameters)) {
                response = new RemoteManagerResponse("Adding domain " + parameters + " successful");
            } else {
                response = new RemoteManagerResponse("Adding domain " + parameters + " fail");
            }
        } catch (DomainListManagementException e) {
            session.getLogger().error("Error on adding domain: " + e);
            response = new RemoteManagerResponse("Error on adding domain: " + e);
        }
        return response;
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
