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

import org.apache.james.protocols.api.Request;
import org.apache.james.protocols.api.Response;
import org.apache.james.remotemanager.CommandHandler;
import org.apache.james.remotemanager.CommandHelp;
import org.apache.james.remotemanager.RemoteManagerResponse;
import org.apache.james.remotemanager.RemoteManagerSession;
import org.apache.james.user.api.JamesUser;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.api.UsersStore;

/**
 * Handler called upon receipt of an UNSETALIAS command.
 */
public class UnsetAliasCmdHandler implements CommandHandler{
    public final static String COMMAND_NAME = "UNSETALIAS";
    private CommandHelp help = new CommandHelp("unsetalias [user]","unsets an alias for 'user'");

    private UsersStore uStore;

    /**
     * Sets the users store.
     * 
     * @param users
     *            the users to set
     */
    @Resource(name = "usersstore")
    public final void setUsers(UsersStore uStore) {
        this.uStore = uStore;
    }
    
    /**
     * @see org.apache.james.remotemanager.CommandHandler#getHelp()
     */
    public CommandHelp getHelp() {
        return help;
    }
    
    /*
     * (non-Javadoc)
     * @see org.apache.james.protocols.api.CommandHandler#onCommand(org.apache.james.protocols.api.ProtocolSession, org.apache.james.protocols.api.Request)
     */
    public Response onCommand(RemoteManagerSession session, Request request) {
        RemoteManagerResponse response;
        String parameters = request.getArgument();
        
        if ((parameters == null) || (parameters.equals(""))) {
            response = new RemoteManagerResponse("Usage: " + help.getSyntax());
            return response;
        }
        UsersRepository users = uStore.getRepository((String) session.getState().get(RemoteManagerSession.CURRENT_USERREPOSITORY));

        String username = parameters;
        JamesUser user = (JamesUser) users.getUserByName(username);
        if (user == null) {
            response = new RemoteManagerResponse("No such user " + username);
        } else if (user.getAliasing()){
            user.setAliasing(false);
            users.updateUser(user);
            StringBuilder responseBuffer =
                new StringBuilder(64)
                        .append("Alias for ")
                        .append(username)
                        .append(" unset");
            String responseString = responseBuffer.toString();
            response = new RemoteManagerResponse(responseString);
            session.getLogger().info(responseString);
        } else {
            response = new RemoteManagerResponse("Aliasing not active for" + username);
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
