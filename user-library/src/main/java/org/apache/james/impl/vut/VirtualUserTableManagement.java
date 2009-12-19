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



package org.apache.james.impl.vut;

import java.util.Collection;
import java.util.Map;

import javax.annotation.Resource;

import org.apache.james.api.vut.VirtualUserTable;
import org.apache.james.api.vut.VirtualUserTableStore;
import org.apache.james.api.vut.management.InvalidMappingException;
import org.apache.james.api.vut.management.VirtualUserTableManagementException;
import org.apache.james.api.vut.management.VirtualUserTableManagementMBean;
import org.apache.james.api.vut.management.VirtualUserTableManagementService;


/**
 * Management for VirtualUserTables
 * 
 */
public class VirtualUserTableManagement implements VirtualUserTableManagementService, VirtualUserTableManagementMBean {

    private VirtualUserTableStore store;
    private org.apache.james.api.vut.management.VirtualUserTableManagement defaultVUT;    

    @Resource(name="virtualusertable-store")
    public void setVirtualUserTableStore(VirtualUserTableStore store) {
        this.store = store;
    }
    
    @Resource(name="virtualusertablemanagement")
    public void setVirtualUserTableManagement(org.apache.james.api.vut.management.VirtualUserTableManagement defaultVUT) {
        this.defaultVUT = defaultVUT;
    }
    
    /**
     * Return a VirtualUserTableManagement with the given tablename
     * 
     * @param tableName tableName if null is given the DefaultVirtualUserTable get returned
     * @return VirtualUserTableManagement object
     * @throws VirtualUserTableManagementException if no VirtualUserTable with the given name exists
     */
    private org.apache.james.api.vut.management.VirtualUserTableManagement getTable(String tableName) throws VirtualUserTableManagementException {     
        // if the tableName was null return the DefaultVirtualUserTable
        if (tableName == null) {
            return defaultVUT;
        } else {
            VirtualUserTable vut = store.getTable(tableName);
    
            // Check if a table with the given name exists, if not throw an Exception
            if (vut == null) {
                throw new VirtualUserTableManagementException("No VirtualUserTable with such name: " + tableName);
            } else if (!(vut instanceof org.apache.james.api.vut.management.VirtualUserTableManagement)){
                // Used VUT not support management, throw an Exception
                throw new VirtualUserTableManagementException("Used VirtualUserTable implementation not support management");
            } else {
                return (org.apache.james.api.vut.management.VirtualUserTableManagement) vut;
            }
        }
    }
    
    /**
     * @see org.apache.james.api.vut.management.VirtualUserTableManagementService#addAddressMapping(java.lang.String, java.lang.String, java.lang.String, java.lang.String)
     */
    public boolean addAddressMapping(String virtualUserTable, String user, String domain, String address) throws  VirtualUserTableManagementException {
        try {
            return getTable(virtualUserTable).addAddressMapping(user, domain, address);
        } catch (InvalidMappingException e) {
            throw new VirtualUserTableManagementException(e);
        }
    }

    /**
     * @see org.apache.james.api.vut.management.VirtualUserTableManagementService#addErrorMapping(java.lang.String, java.lang.String, java.lang.String, java.lang.String)
     */
    public boolean addErrorMapping(String virtualUserTable, String user, String domain, String error) throws VirtualUserTableManagementException {
        try {
            return getTable(virtualUserTable).addErrorMapping(user, domain, error);
        } catch (InvalidMappingException e) {
            throw new VirtualUserTableManagementException(e);
        }
    }

    /**
     * @see org.apache.james.api.vut.management.VirtualUserTableManagementService#addRegexMapping(java.lang.String, java.lang.String, java.lang.String, java.lang.String)
     */
    public boolean addRegexMapping(String virtualUserTable, String user, String domain, String regex) throws VirtualUserTableManagementException {
        try {
            return getTable(virtualUserTable).addRegexMapping(user, domain, regex);
        } catch (InvalidMappingException e) {
            throw new VirtualUserTableManagementException(e);
        }
    }

    /**
     * @see org.apache.james.api.vut.management.VirtualUserTableManagementService#getUserDomainMappings(java.lang.String, java.lang.String, java.lang.String)
     */
    public Collection<String> getUserDomainMappings(String virtualUserTable, String user, String domain) throws VirtualUserTableManagementException {
        try {
            return getTable(virtualUserTable).getUserDomainMappings(user, domain);
        } catch (InvalidMappingException e) {
            throw new VirtualUserTableManagementException(e);
        }
    }

    /**
     * @see org.apache.james.api.vut.management.VirtualUserTableManagementService#removeAddressMapping(java.lang.String, java.lang.String, java.lang.String, java.lang.String)
     */
    public boolean removeAddressMapping(String virtualUserTable, String user, String domain, String address) throws VirtualUserTableManagementException {
        try {
            return getTable(virtualUserTable).removeAddressMapping(user, domain, address);
        } catch (InvalidMappingException e) {
            throw new VirtualUserTableManagementException(e);
        }
    }

    /**
     * @see org.apache.james.api.vut.management.VirtualUserTableManagementService#removeErrorMapping(java.lang.String, java.lang.String, java.lang.String, java.lang.String)
     */
    public boolean removeErrorMapping(String virtualUserTable, String user, String domain, String error) throws VirtualUserTableManagementException {
        try {
            return getTable(virtualUserTable).removeErrorMapping(user, domain, error);
        } catch (InvalidMappingException e) {
            throw new VirtualUserTableManagementException(e);
        }
    }

    /**
     * @see org.apache.james.api.vut.management.VirtualUserTableManagementService#removeRegexMapping(java.lang.String, java.lang.String, java.lang.String, java.lang.String)
     */
    public boolean removeRegexMapping(String virtualUserTable, String user, String domain, String regex) throws VirtualUserTableManagementException {
        try {
            return getTable(virtualUserTable).removeRegexMapping(user, domain, regex);
        } catch (InvalidMappingException e) {
            throw new VirtualUserTableManagementException(e);
        }
    }

    /**
     * @see org.apache.james.api.vut.management.VirtualUserTableManagementService#addMapping(java.lang.String, java.lang.String, java.lang.String, java.lang.String)
     */
    public boolean addMapping(String virtualUserTable, String user, String domain, String mapping) throws VirtualUserTableManagementException {
        try {
            return getTable(virtualUserTable).addMapping(user, domain, mapping);
        } catch (InvalidMappingException e) {
            throw new VirtualUserTableManagementException(e);
        }
    }

    /**
     * @see org.apache.james.api.vut.management.VirtualUserTableManagementService#removeMapping(java.lang.String, java.lang.String, java.lang.String, java.lang.String)
     */
    public boolean removeMapping(String virtualUserTable, String user, String domain, String mapping) throws VirtualUserTableManagementException {
        try {
            return getTable(virtualUserTable).removeMapping(user, domain, mapping);
        } catch (InvalidMappingException e) {
            throw new VirtualUserTableManagementException(e);
        }
    }

    /**
     * @see org.apache.james.api.vut.management.VirtualUserTableManagementService#getAllMappings(java.lang.String)
     */
    public Map<String,Collection<String>> getAllMappings(String virtualUserTable) throws VirtualUserTableManagementException{
        return getTable(virtualUserTable).getAllMappings();
    }

    /**
     * @see org.apache.james.api.vut.management.VirtualUserTableManagementService#addAliasDomainMapping(java.lang.String, java.lang.String, java.lang.String)
     */
    public boolean addAliasDomainMapping(String virtualUserTable, String aliasDomain, String realDomain) throws VirtualUserTableManagementException {
        try {
            return getTable(virtualUserTable).addAliasDomainMapping(aliasDomain, realDomain);
        } catch (InvalidMappingException e) {
            throw new VirtualUserTableManagementException(e);
        }
    }

    /**
     * @see org.apache.james.api.vut.management.VirtualUserTableManagementService#removeAliasDomainMapping(java.lang.String, java.lang.String, java.lang.String)
     */
    public boolean removeAliasDomainMapping(String virtualUserTable, String aliasDomain, String realDomain) throws VirtualUserTableManagementException {
        try {
            return getTable(virtualUserTable).removeAliasDomainMapping(aliasDomain, realDomain);
        } catch (InvalidMappingException e) {
            throw new VirtualUserTableManagementException(e);
        }
    }
}
