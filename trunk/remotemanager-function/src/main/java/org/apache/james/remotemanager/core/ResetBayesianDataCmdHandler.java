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

import org.apache.james.management.BayesianAnalyzerManagementException;
import org.apache.james.management.BayesianAnalyzerManagementService;
import org.apache.james.remotemanager.CommandHandler;
import org.apache.james.remotemanager.CommandHelp;
import org.apache.james.remotemanager.RemoteManagerRequest;
import org.apache.james.remotemanager.RemoteManagerResponse;
import org.apache.james.remotemanager.RemoteManagerSession;

public class ResetBayesianDataCmdHandler implements CommandHandler{
    private final static String COMMAND_NAME = "RESETBAYESIANDATA";
    private CommandHelp help = new CommandHelp("resetbayesiandata","reset trained BayesianAnalysis data");
    
    private BayesianAnalyzerManagementService bayesianAnalyzerManagement;

    /**
     * Set the BayesianAnalyzerManagementService
     * 
     * @param bayesianAnalyzerManagement the BayesianAnalyzerManagementService
     */
    @Resource(name="bayesiananalyzermanagement")
    public final void setBayesianAnalyzerManagement(BayesianAnalyzerManagementService bayesianAnalyzerManagement) {
        this.bayesianAnalyzerManagement = bayesianAnalyzerManagement;
    }
    
    /**
     * @see org.apache.james.remotemanager.CommandHandler#getHelp()
     */
    public CommandHelp getHelp() {
        return help;
    }

    /*
     * (non-Javadoc)
     * @see org.apache.james.remotemanager.CommandHandler#onCommand(org.apache.james.remotemanager.RemoteManagerSession, org.apache.james.remotemanager.RemoteManagerRequest)
     */
    public RemoteManagerResponse onCommand(RemoteManagerSession session, RemoteManagerRequest request) {
        RemoteManagerResponse response;
        try {           
            // stop watchdog cause feeding can take some time
            session.getWatchdog().stop();
            
            bayesianAnalyzerManagement.resetData();
            response = new RemoteManagerResponse("Reseted the BayesianAnalysis data");

        } catch (BayesianAnalyzerManagementException e) {
            session.getLogger().error("Error on reseting BayesianAnalysis data: " + e);
            response = new RemoteManagerResponse("Error on reseting BayesianAnalysis data: " + e);
        } finally {
            session.getWatchdog().start();
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
