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

package org.apache.james.transport.camel;

import java.util.Map;

import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.ScheduledPollEndpoint;

public class ActiveMQPollingEndpoint extends ScheduledPollEndpoint{

    public ActiveMQPollingEndpoint(String uri, Component component) {
        super(uri,component);
    }
    
    public Producer createProducer() throws Exception {
        return null;
    }

    /*
     * (non-Javadoc)
     * @see org.apache.camel.IsSingleton#isSingleton()
     */
    public boolean isSingleton() {
        return true;
    }

    /*
     * (non-Javadoc)
     * @see org.apache.camel.Endpoint#createConsumer(org.apache.camel.Processor)
     */
    public Consumer createConsumer(Processor processor) throws Exception {
        ActiveMQPollingConsumer consumer =  new ActiveMQPollingConsumer(this,processor,getCamelContext().createConsumerTemplate());
        configureConsumer(consumer);
        return consumer;
    }

	@Override
	public boolean isLenientProperties() {
		return true;
	}
    
    

}
