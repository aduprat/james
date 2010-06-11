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

package org.apache.james.userrepository;


import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.DefaultConfigurationBuilder;
import org.apache.commons.logging.impl.SimpleLog;
import org.apache.james.api.user.JamesUser;
import org.apache.james.api.user.UsersRepository;
import org.apache.james.api.vut.VirtualUserTable;
import org.apache.james.lifecycle.LifecycleUtil;
import org.apache.james.test.mock.james.MockFileSystem;
import org.apache.james.test.util.Util;
import org.apache.mailet.MailAddress;

import java.util.Collection;
import java.util.Iterator;

/**
 * Test basic behaviours of UsersFileRepository
 */
public class JamesUsersJdbcRepositoryTest extends MockUsersRepositoryTest {

    /**
     * Create the repository to be tested.
     * 
     * @return the user repository
     * @throws Exception 
     */
    protected UsersRepository getUsersRepository() throws Exception {
        JamesUsersJdbcRepository res = new JamesUsersJdbcRepository();
        String tableString = "jamesusers";
        configureAbstractJdbcUsersRepository(res, tableString);
        return res;
    }

    /**
     * @param res
     * @param tableString
     * @throws Exception
     * @throws ConfigurationException
     */
    protected void configureAbstractJdbcUsersRepository(AbstractJdbcUsersRepository res, String tableString) throws Exception, ConfigurationException {
        res.setFileSystem(new MockFileSystem());
        res.setDatasources(Util.getDataSourceSelector());
        
        DefaultConfigurationBuilder configuration = new DefaultConfigurationBuilder();
        configuration.addProperty("[@destinationURL]", "db://maildb/"+tableString);
        configuration.addProperty("sqlFile","file://conf/sqlResources.xml");
        res.setLog(new SimpleLog("MockLog"));
        res.configure(configuration);
        res.init();
    }

    /**
     * @return
     */
    protected boolean getCheckCase() {
        return true;
    }

    protected void disposeUsersRepository() {
        Iterator<String> i = this.usersRepository.list();
        while (i.hasNext()) {
            this.usersRepository.removeUser((String) i.next());
        }
        LifecycleUtil.dispose(this.usersRepository);
    }
    
    
    public void testVirtualUserTableImpl() throws Exception {
        String username = "test";
        String password = "pass";
        String alias = "alias";
        String domain = "localhost";
        String forward = "forward@somewhere";
        
        JamesUsersJdbcRepository repos = (JamesUsersJdbcRepository) getUsersRepository();
        repos.setEnableAliases(true);
        repos.setEnableForwarding(true);
        repos.addUser(username,password);
        
        JamesUser user = (JamesUser)repos.getUserByName(username);
        user.setAlias(alias);
        repos.updateUser(user);
        
        Collection<String> map = ((VirtualUserTable) repos).getMappings(username, domain);
        assertNull("No mapping", map);
        
        user.setAliasing(true);
        repos.updateUser(user);
        map = ((VirtualUserTable) repos).getMappings(username, domain);
        assertEquals("One mapping", 1, map.size());
        assertEquals("Alias found", map.iterator().next().toString(), alias + "@" + domain);
        
        
        user.setForwardingDestination(new MailAddress(forward));
        repos.updateUser(user);
        map = ((VirtualUserTable) repos).getMappings(username, domain);
        assertTrue("One mapping", map.size() == 1);
        assertEquals("Alias found", map.iterator().next().toString(), alias + "@" + domain);
        
        
        user.setForwarding(true);
        repos.updateUser(user);
        map = ((VirtualUserTable) repos).getMappings(username, domain);
        Iterator<String> mappings = map.iterator();
        assertTrue("Two mapping",map.size() == 2);
        assertEquals("Alias found", mappings.next().toString(), alias + "@" + domain);
        assertEquals("Forward found", mappings.next().toString(), forward);
    }


}
