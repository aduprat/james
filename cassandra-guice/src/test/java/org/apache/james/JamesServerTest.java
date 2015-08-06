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
package org.apache.james;

import java.util.Properties;

import javax.mail.AuthenticationFailedException;
import javax.mail.NoSuchProviderException;
import javax.mail.Session;
import javax.mail.Store;

import org.apache.james.mailbox.cassandra.CassandraClusterSingleton;
import org.apache.james.modules.CassandraSessionModule;
import org.apache.james.modules.IMAPServerModule;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.google.inject.name.Names;

public class JamesServerTest {

    private TestJamesServer server;
    private static final CassandraClusterSingleton CASSANDRA = CassandraClusterSingleton.build();
    private static final int IMAP_PORT = 1143; // You need to be root (superuser) to bind to ports under 1024.

    private class TestCassandraSessionModule extends CassandraSessionModule {

        @Override
        protected void bindConstants() {
            bind(String.class).annotatedWith(Names.named("cassandra.ip")).toInstance(CassandraClusterSingleton.CLUSTER_IP);
            bind(Integer.class).annotatedWith(Names.named("cassandra.port")).toInstance(CassandraClusterSingleton.CLUSTER_PORT_TEST);
            bind(String.class).annotatedWith(Names.named("cassandra.keyspace")).toInstance(CassandraClusterSingleton.KEYSPACE_NAME);
            bind(Integer.class).annotatedWith(Names.named("cassandra.replication.factor")).toInstance(CassandraClusterSingleton.REPLICATION_FACTOR);
        }
    }
    
    private class TestIMAPServerModule extends IMAPServerModule {

        @Override
        protected int imapPort() {
            return IMAP_PORT;
        }
    }
    
    private class TestJamesServer extends JamesServer {

        @Override
        protected CassandraSessionModule cassandraSessionModule() {
            return new TestCassandraSessionModule();
        }

        @Override
        protected IMAPServerModule imapServerModule() {
            return new TestIMAPServerModule();
        }
    }

    @Before
    public void setup() throws Exception {
        CASSANDRA.ensureAllTables();

        server = new TestJamesServer();
        server.start();
    }

    @After
    public void tearDown() throws Exception {
        server.stop();
        CASSANDRA.clearAllTables();
    }

    @Test (expected = AuthenticationFailedException.class)
    public void connectShouldThrowWhenNoCrendentials() throws Exception {
        store().connect();
    }
    
    private Store store() throws NoSuchProviderException {
        Properties properties = new Properties();
        properties.put("mail.imap.host", "localhost");
        properties.put("mail.imap.port", String.valueOf(IMAP_PORT));
        Session session = Session.getDefaultInstance(properties);
        session.setDebug(true);
        return session.getStore("imap");
    }
}
