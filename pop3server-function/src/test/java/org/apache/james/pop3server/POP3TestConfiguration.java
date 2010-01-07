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

import org.apache.avalon.framework.configuration.DefaultConfiguration;
import org.apache.james.pop3server.core.CoreCmdHandlerLoader;
import org.apache.james.test.util.Util;

public class POP3TestConfiguration extends DefaultConfiguration {

    private int m_pop3ListenerPort;

    public POP3TestConfiguration(int pop3ListenerPort) {
        super("pop3server");
        m_pop3ListenerPort = pop3ListenerPort;
    }

    public void init() {
        setAttribute("enabled", true);
        addChild(Util.getValuedConfiguration("port", "" + m_pop3ListenerPort));
        DefaultConfiguration handlerConfig = new DefaultConfiguration("handler");
        handlerConfig.addChild(Util.getValuedConfiguration("helloName", "myMailServer"));
        handlerConfig.addChild(Util.getValuedConfiguration("connectiontimeout", "360000"));
       
        DefaultConfiguration config = new DefaultConfiguration("handlerchain");

        config.addChild(createHandler(CoreCmdHandlerLoader.class.getName()));
        handlerConfig.addChild(config);
        addChild(handlerConfig);
    }

    private DefaultConfiguration createHandler(String className) {
        DefaultConfiguration d = new DefaultConfiguration("handler");
       
        d.setAttribute("class", className);
        return d;
    }
}