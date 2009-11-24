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

import org.apache.avalon.cornerstone.services.sockets.SocketManager;
import org.apache.avalon.cornerstone.services.threads.ThreadManager;
import org.apache.avalon.framework.activity.Initializable;
import org.apache.avalon.framework.configuration.Configurable;
import org.apache.avalon.framework.configuration.Configuration;
import org.apache.avalon.framework.configuration.ConfigurationException;
import org.apache.avalon.framework.logger.LogEnabled;
import org.apache.avalon.framework.logger.Logger;
import org.apache.avalon.framework.service.ServiceException;
import org.apache.avalon.framework.service.ServiceManager;
import org.apache.avalon.framework.service.Serviceable;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.impl.AvalonLogger;
import org.apache.james.api.dnsservice.DNSService;
import org.apache.james.api.kernel.LoaderService;
import org.apache.james.api.user.UsersRepository;
import org.apache.james.bridge.GuiceInjected;
import org.apache.james.services.FileSystem;
import org.apache.james.services.MailServer;
import org.apache.james.socket.JamesConnectionManager;
import org.apache.james.socket.api.ProtocolHandlerFactory;
import org.apache.james.util.ConfigurationAdapter;
import org.guiceyfruit.jsr250.Jsr250Module;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Provider;
import com.google.inject.name.Names;

public class AvalonPOP3Server implements GuiceInjected, Initializable, Serviceable, Configurable, LogEnabled, POP3ServerMBean {
    
    private FileSystem filesystem;
    private MailServer mailserver;
    private DNSService dns;
    private Log logger;
    private org.apache.commons.configuration.HierarchicalConfiguration config;
    private UsersRepository userRepos;
    private JamesConnectionManager connectionManager;
    private SocketManager socketManager;
    private POP3Server pop3server = new POP3Server();
    private ThreadManager threadManager;
    
    public String getNetworkInterface() {
        return pop3server.getNetworkInterface();
    }

    public int getPort() {
        return pop3server.getPort();
    }

    public String getSocketType() {
        return pop3server.getSocketType();
    }

    public boolean isEnabled() {
        return pop3server.isEnabled();
    }

    /**
     * @see org.apache.avalon.framework.configuration.Configurable#configure(org.apache.avalon.framework.configuration.Configuration)
     */
    public void configure(Configuration config) throws ConfigurationException {
        try {
            this.config = new ConfigurationAdapter(config);
        } catch (org.apache.commons.configuration.ConfigurationException e) {
            throw new ConfigurationException("Unable to convert configuration", e);
        }
    }

    /**
     * @see org.apache.avalon.framework.service.Serviceable#service(org.apache.avalon.framework.service.ServiceManager)
     */
    public void service(ServiceManager manager) throws ServiceException {
        dns = (DNSService) manager.lookup(DNSService.ROLE);
        mailserver = (MailServer) manager.lookup(MailServer.ROLE);
        filesystem = (FileSystem) manager.lookup(FileSystem.ROLE);
        userRepos = (UsersRepository) manager.lookup(UsersRepository.ROLE);
        socketManager = (SocketManager) manager.lookup(SocketManager.ROLE);
        connectionManager = (JamesConnectionManager) manager.lookup(JamesConnectionManager.ROLE);     
        threadManager = (ThreadManager) manager.lookup(ThreadManager.ROLE);
    }

    /**
     * @see org.apache.avalon.framework.activity.Initializable#initialize()
     */
    public void initialize() throws Exception {
        pop3server = Guice.createInjector(new POP3ServerModule(), new Jsr250Module()).getInstance(POP3Server.class);
    }
                 
    /**
     * @see org.apache.avalon.framework.logger.LogEnabled#enableLogging(org.apache.avalon.framework.logger.Logger)
     */
    public void enableLogging(Logger logger) {
        this.logger = new AvalonLogger(logger);
    }

    private final class POP3ServerModule extends AbstractModule {

        @Override
        protected void configure() {
            bind(DNSService.class).annotatedWith(Names.named("org.apache.james.api.dnsservice.DNSService")).toInstance(dns);
            bind(MailServer.class).annotatedWith(Names.named("org.apache.james.services.MailServer")).toInstance(mailserver);
            bind(org.apache.commons.configuration.HierarchicalConfiguration.class).annotatedWith(Names.named("org.apache.commons.configuration.Configuration")).toInstance(config);
            bind(Log.class).annotatedWith(Names.named("org.apache.commons.logging.Log")).toInstance(logger);
            bind(FileSystem.class).annotatedWith(Names.named("org.apache.james.services.FileSystem")).toInstance(filesystem);
            bind(UsersRepository.class).annotatedWith(Names.named("org.apache.james.api.user.UsersRepository")).toInstance(userRepos);
            bind(ProtocolHandlerFactory.class).annotatedWith(Names.named("org.apache.james.socket.api.ProtocolHandlerFactory")).toProvider(new Provider<ProtocolHandlerFactory>() {

                public ProtocolHandlerFactory get() {
                    return pop3server;
                }
                
            });
            bind(SocketManager.class).annotatedWith(Names.named("org.apache.avalon.cornerstone.services.sockets.SocketManager")).toInstance(socketManager);
            bind(JamesConnectionManager.class).annotatedWith(Names.named("org.apache.james.socket.JamesConnectionManager")).toInstance(connectionManager);
            bind(ThreadManager.class).annotatedWith(Names.named("org.apache.avalon.cornerstone.services.threads.ThreadManager")).toInstance(threadManager);
            // we bind the LoaderService to an Provider to get sure everything is there when the SMTPLoaderService get created.
            bind(LoaderService.class).annotatedWith(Names.named("org.apache.james.LoaderService")).toProvider(new Provider<LoaderService>() {

                public LoaderService get() {
                    return new SMTPLoaderService();
                }
                
                // Mimic the loaderservice
                class SMTPLoaderService implements LoaderService {
                    Injector injector = Guice.createInjector(new POP3ServerModule(), new Jsr250Module());

                    public <T> T load(Class<T> type) {
                        return injector.getInstance(type);
                    }
                    
                }
                
            });

        }
    }
}
