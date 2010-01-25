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


package org.apache.james.vut;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

import org.apache.commons.configuration.DefaultConfigurationBuilder;
import org.apache.commons.logging.impl.SimpleLog;
import org.apache.james.api.vut.VirtualUserTable;
import org.apache.james.api.vut.management.InvalidMappingException;
import org.apache.james.impl.vut.AbstractVirtualUserTable;
import org.apache.james.impl.vut.VirtualUserTableUtil;


public class XMLVirtualUserTableTest extends AbstractVirtualUserTableTest {
    private DefaultConfigurationBuilder defaultConfiguration = new DefaultConfigurationBuilder();
    
    
    @Override
    protected void setUp() throws Exception {
        defaultConfiguration.setDelimiterParsingDisabled(true);
        
        super.setUp();
    }



    protected AbstractVirtualUserTable getVirtalUserTable() throws Exception {
        XMLVirtualUserTable mr = new XMLVirtualUserTable();
        mr.setDNSService(setUpDNSServer());
        mr.setLog(new SimpleLog("MockLog"));
       
        return mr;
    }
    
    

    /**
     * @see org.apache.james.vut.AbstractVirtualUserTableTest#addMapping(java.lang.String, java.lang.String, java.lang.String, int)
     */
    protected boolean addMapping(String user, String domain, String mapping, int type) throws InvalidMappingException {
        if (user == null) user = "*";
        if (domain == null) domain = "*";
        
        Collection<String> mappings = virtualUserTable.getUserDomainMappings(user, domain);

        if (mappings == null) {
            mappings = new ArrayList<String>();
        } else {
           removeMappings(user,domain,mappings);
        }
    
        if (type == ERROR_TYPE) {
            mappings.add(VirtualUserTable.ERROR_PREFIX + mapping);
        } else if (type == REGEX_TYPE) {
            mappings.add(VirtualUserTable.REGEX_PREFIX + mapping);
        } else if (type == ADDRESS_TYPE) {
            mappings.add(mapping);
        }  else if (type == ALIASDOMAIN_TYPE) {
            mappings.add(VirtualUserTable.ALIASDOMAIN_PREFIX + mapping);
        }
        
        if (mappings.size() > 0) { 
            defaultConfiguration.addProperty("mapping",user + "@" + domain +"=" + VirtualUserTableUtil.CollectionToMapping(mappings));
        }
    
        try {
            virtualUserTable.configure(defaultConfiguration);
            } catch (Exception e) {
            if (mappings.size() > 0) {
                return false;
            } else {
                return true;
            }
        }
        return true;
    }

    /**
     * @see org.apache.james.vut.AbstractVirtualUserTableTest#removeMapping(java.lang.String, java.lang.String, java.lang.String, int)
     */
    protected boolean removeMapping(String user, String domain, String mapping, int type) throws InvalidMappingException {       
        if (user == null) user = "*";
        if (domain == null) domain = "*";
        
        Collection<String> mappings = virtualUserTable.getUserDomainMappings(user, domain);
        
        if (mappings == null) {
            return false;
        }  
    
        removeMappings(user,domain, mappings);
    
        if (type == ERROR_TYPE) {
            mappings.remove(VirtualUserTable.ERROR_PREFIX + mapping);
        } else if (type == REGEX_TYPE) {
            mappings.remove(VirtualUserTable.REGEX_PREFIX + mapping);
        } else if (type == ADDRESS_TYPE) {
            mappings.remove(mapping);    
        }  else if (type == ALIASDOMAIN_TYPE) {
            mappings.remove(VirtualUserTable.ALIASDOMAIN_PREFIX + mapping);
        }

        if (mappings.size() > 0) {
            defaultConfiguration.addProperty("mapping",user + "@" + domain +"=" + VirtualUserTableUtil.CollectionToMapping(mappings));
        } 
    
        try {
            virtualUserTable.configure(defaultConfiguration);
            } catch (Exception e) {
           if (mappings.size() > 0) {
               return false;
           } else {
               return true;
           }
        }
        return true;
    }
    
    
    @SuppressWarnings("unchecked")
    private void removeMappings(String user, String domain, Collection<String> mappings) {
        Iterator<String> conf = defaultConfiguration.getKeys();
        
        while(conf.hasNext()) {
            String c = conf.next();
            try {
                String mapping = user + "@" + domain + "=" + VirtualUserTableUtil.CollectionToMapping(mappings);
            
                System.out.println("M=" + mapping);
            
                if (defaultConfiguration.getProperty(c).toString().equalsIgnoreCase(mapping)){
                    defaultConfiguration.clearProperty(c);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

}
