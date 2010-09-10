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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.mail.internet.ParseException;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.commons.logging.Log;
import org.apache.james.api.vut.ErrorMappingException;
import org.apache.james.api.vut.VirtualUserTable;
import org.apache.james.api.vut.management.InvalidMappingException;
import org.apache.james.api.vut.management.VirtualUserTableManagement;
import org.apache.james.lifecycle.Configurable;
import org.apache.james.lifecycle.LogEnabled;
import org.apache.mailet.MailAddress;
import org.apache.oro.text.regex.MalformedPatternException;
import org.apache.oro.text.regex.Perl5Compiler;

/**
 * 
 */
public abstract class AbstractVirtualUserTable implements VirtualUserTable, VirtualUserTableManagement, LogEnabled, Configurable {       
    // The maximum mappings which will process before throwing exception
    private int mappingLimit = 10;
       
    private boolean recursive = true;
    
    private Log logger;

    /*
     * (non-Javadoc)
     * @see org.apache.james.lifecycle.Configurable#configure(org.apache.commons.configuration.HierarchicalConfiguration)
     */
    public void configure(HierarchicalConfiguration config) throws ConfigurationException {
    	setRecursiveMapping(config.getBoolean("recursiveMapping", true));
        try {
            setMappingLimit(config.getInt("mappingLimit", 10));
        } catch (IllegalArgumentException e) {
            throw new ConfigurationException(e.getMessage());
        }
        doConfigure(config);
    }

    public void setLog(Log logger) {
        this.logger = logger;
    }
    
    /**
     * Override to handle config
     * 
     * @param conf
     * @throws ConfigurationException
     */
    protected void doConfigure(HierarchicalConfiguration conf) throws ConfigurationException {
    	
    }
    
    public void setRecursiveMapping(boolean recursive) {
        this.recursive = recursive;
    }
    
    /**
     * Set the mappingLimit
     * 
     * @param mappingLimit the mappingLimit
     * @throws IllegalArgumentException get thrown if mappingLimit smaller then 1 is used
     */
    public void setMappingLimit(int mappingLimit) throws IllegalArgumentException {
        if (mappingLimit < 1) throw new IllegalArgumentException("The minimum mappingLimit is 1");
        this.mappingLimit = mappingLimit;
    }
    
    /**
     * @see org.apache.james.api.vut.VirtualUserTable#getMappings(String, String)
     */
    public Collection<String> getMappings(String user,String domain) throws ErrorMappingException {
        return getMappings(user,domain,mappingLimit);
    }
    
    public Collection<String> getMappings(String user, String domain, int mappingLimit) throws ErrorMappingException {

        // We have to much mappings throw ErrorMappingException to avoid infinity loop
        if (mappingLimit == 0) throw new ErrorMappingException("554 Too many mappings to process");

        String targetString = mapAddress(user, domain);
        
        // Only non-null mappings are translated
        if (targetString != null) {
            Collection<String> mappings = new ArrayList<String>();
            if (targetString.startsWith(VirtualUserTable.ERROR_PREFIX)) {
                throw new ErrorMappingException(targetString.substring(VirtualUserTable.ERROR_PREFIX.length()));

            } else {
                Iterator<String> map = VirtualUserTableUtil.mappingToCollection(targetString).iterator();

                while (map.hasNext()) {
                    String target = map.next();

                    if (target.startsWith(VirtualUserTable.REGEX_PREFIX)) {
                        try {
                            target = VirtualUserTableUtil.regexMap(new MailAddress(user,domain), target);
                        } catch (MalformedPatternException e) {
                            getLogger().error("Exception during regexMap processing: ", e);
                        } catch (ParseException e) {
                            // should never happen
                            getLogger().error("Exception during regexMap processing: ", e);
                        } 
                    } else if (target.startsWith(VirtualUserTable.ALIASDOMAIN_PREFIX)) {
                        target = user + "@" + target.substring(VirtualUserTable.ALIASDOMAIN_PREFIX.length());
                    }

                    if (target == null) continue;
                    
                    StringBuffer buf = new StringBuffer().append("Valid virtual user mapping ")
                                                         .append(user).append("@").append(domain)
                                                         .append(" to ").append(target);
                    getLogger().debug(buf.toString());
                   
                 
                    if (recursive) {
                    
                        String userName = null;
                        String domainName = null;
                        String args[] = target.split("@");
                                        
                        if (args != null && args.length > 1) {
                    
                            userName = args[0];
                            domainName = args[1];
                        } else {
                            // TODO Is that the right todo here?
                            userName = target;
                            domainName = domain;
                        }
                                        
                        // Check if the returned mapping is the same as the input. If so return null to avoid loops
                        if (userName.equalsIgnoreCase(user) && domainName.equalsIgnoreCase(domain)) {
                            return null;
                        }
                                        
                        Collection<String> childMappings = getMappings(userName, domainName, mappingLimit -1);
                    
                        if (childMappings == null) {
                             // add mapping
                            mappings.add(target);
                        } else {
                            mappings.addAll(childMappings);         
                        }
                                        
                    } else {
                        mappings.add(target);
                    }
                } 
            }
        
            return mappings;
        
        }
        
        return null;
    }
    
    /**
     * @see org.apache.james.api.vut.management.VirtualUserTableManagement#addRegexMapping(java.lang.String, java.lang.String, java.lang.String)
     */
    public boolean addRegexMapping(String user, String domain, String regex) throws InvalidMappingException {     
        try {
            new Perl5Compiler().compile(regex);
        } catch (MalformedPatternException e) {
            throw new InvalidMappingException("Invalid regex: " + regex);
        }
        
        if (checkMapping(user,domain,regex) == true) {
            getLogger().info("Add regex mapping => " + regex + " for user: " + user + " domain: " + domain);
            return addMappingInternal(user, domain, VirtualUserTable.REGEX_PREFIX + regex);
        } else {
            return false;
        }
    }

    
    /**
     * @see org.apache.james.api.vut.management.VirtualUserTableManagement#removeRegexMapping(java.lang.String, java.lang.String, java.lang.String)
     */
    public boolean removeRegexMapping(String user, String domain, String regex) throws InvalidMappingException {
        getLogger().info("Remove regex mapping => " + regex + " for user: " + user + " domain: " + domain);
        return removeMappingInternal(user,domain,VirtualUserTable.REGEX_PREFIX + regex);
    }
    
    /**
     * @see org.apache.james.api.vut.management.VirtualUserTableManagement#addAddressMapping(java.lang.String, java.lang.String, java.lang.String)
     */
    public boolean addAddressMapping(String user, String domain, String address) throws InvalidMappingException {
        if (address.indexOf('@') < 0) {
            address =  address + "@localhost";
        } 
        try {
            new MailAddress(address);
        } catch (ParseException e) {
            throw new InvalidMappingException("Invalid emailAddress: " + address);
        }
        if (checkMapping(user,domain,address) == true) {          
            getLogger().info("Add address mapping => " + address + " for user: " + user + " domain: " + domain);
            return addMappingInternal(user, domain, address);
        } else {
            return false;
        }   
    }
    
    /**
     * @see org.apache.james.api.vut.management.VirtualUserTableManagement#removeAddressMapping(java.lang.String, java.lang.String, java.lang.String)
     */
    public boolean removeAddressMapping(String user, String domain, String address) throws InvalidMappingException {
        if (address.indexOf('@') < 0) {
            address =  address + "@localhost";
        } 
        getLogger().info("Remove address mapping => " + address + " for user: " + user + " domain: " + domain);
        return removeMappingInternal(user,domain,address);
    }
    
    /**
     * @see org.apache.james.api.vut.management.VirtualUserTableManagement#addErrorMapping(java.lang.String, java.lang.String, java.lang.String)
     */
    public boolean addErrorMapping(String user, String domain, String error) throws InvalidMappingException {   
        if (checkMapping(user,domain,error) == true) {          
            getLogger().info("Add error mapping => " + error + " for user: " + user + " domain: " + domain);
            return addMappingInternal(user,domain, VirtualUserTable.ERROR_PREFIX + error);
        } else {
            return false;
        } 
    }
    
    /**
     * @see org.apache.james.api.vut.management.VirtualUserTableManagement#removeErrorMapping(java.lang.String, java.lang.String, java.lang.String)
     */
    public boolean removeErrorMapping(String user, String domain, String error) throws InvalidMappingException {
        getLogger().info("Remove error mapping => " + error + " for user: " + user + " domain: " + domain);     
        return removeMappingInternal(user,domain,VirtualUserTable.ERROR_PREFIX + error);
    }


    /**
     * @see org.apache.james.api.vut.management.VirtualUserTableManagement#addMapping(java.lang.String, java.lang.String, java.lang.String)
     */
    public boolean addMapping(String user, String domain, String mapping) throws InvalidMappingException {

        String map = mapping.toLowerCase();
        
        if (map.startsWith(VirtualUserTable.ERROR_PREFIX)) {
            return addErrorMapping(user,domain,map.substring(VirtualUserTable.ERROR_PREFIX.length()));
        } else if (map.startsWith(VirtualUserTable.REGEX_PREFIX)) {
            return addRegexMapping(user,domain,map.substring(VirtualUserTable.REGEX_PREFIX.length()));
        } else if (map.startsWith(VirtualUserTable.ALIASDOMAIN_PREFIX)) {
            if (user != null) throw new InvalidMappingException("User must be null for aliasDomain mappings");
            return addAliasDomainMapping(domain,map.substring(VirtualUserTable.ALIASDOMAIN_PREFIX.length()));
        } else {
            return addAddressMapping(user,domain,map);
        }
        
    }
    
    /**
     * @see org.apache.james.api.vut.management.VirtualUserTableManagement#removeMapping(java.lang.String, java.lang.String, java.lang.String)
     */
    public boolean removeMapping(String user, String domain, String mapping) throws InvalidMappingException {

        String map = mapping.toLowerCase();
    
        if (map.startsWith(VirtualUserTable.ERROR_PREFIX)) {
            return removeErrorMapping(user,domain,map.substring(VirtualUserTable.ERROR_PREFIX.length()));
        } else if (map.startsWith(VirtualUserTable.REGEX_PREFIX)) {
            return removeRegexMapping(user,domain,map.substring(VirtualUserTable.REGEX_PREFIX.length()));
        } else if (map.startsWith(VirtualUserTable.ALIASDOMAIN_PREFIX)) {
            if (user != null) throw new InvalidMappingException("User must be null for aliasDomain mappings");
            return removeAliasDomainMapping(domain,map.substring(VirtualUserTable.ALIASDOMAIN_PREFIX.length()));
        } else {
            return removeAddressMapping(user,domain,map);
        }
        
    }
    
    /**
     * @see org.apache.james.api.vut.management.VirtualUserTableManagement#getAllMappings()
     */
    public Map<String,Collection<String>> getAllMappings() {
        int count = 0;
        Map<String,Collection<String>> mappings = getAllMappingsInternal();
    
        if (mappings != null) {
            count = mappings.size();
        }
        getLogger().debug("Retrieve all mappings. Mapping count: " + count);
        return mappings;
    }
    
    /**
     * @see org.apache.james.api.vut.management.VirtualUserTableManagement#getUserDomainMappings(java.lang.String, java.lang.String)
     */
    public Collection<String> getUserDomainMappings(String user, String domain) {
        return getUserDomainMappingsInternal(user, domain);
    }

    /**
     * @see org.apache.james.api.vut.management.VirtualUserTableManagement#addAliasDomainMapping(java.lang.String, java.lang.String)
     */
    public synchronized boolean addAliasDomainMapping(String aliasDomain, String realDomain) throws InvalidMappingException {
        getLogger().info("Add domain mapping: " + aliasDomain  + " => " + realDomain);
        return addMappingInternal(null, aliasDomain, VirtualUserTable.ALIASDOMAIN_PREFIX + realDomain);
    }
    
    /**
     * @see org.apache.james.api.vut.management.VirtualUserTableManagement#removeAliasDomainMapping(java.lang.String, java.lang.String)
     */
    public synchronized boolean removeAliasDomainMapping(String aliasDomain, String realDomain) throws InvalidMappingException {
        getLogger().info("Remove domain mapping: " + aliasDomain  + " => " + realDomain);
        return removeMappingInternal(null, aliasDomain, VirtualUserTable.ALIASDOMAIN_PREFIX + realDomain);
    }
    
    protected Log getLogger() {
        return logger;
    }
    
    /**
     * Add new mapping
     * 
     * @param user the user
     * @param domain the domain
     * @param mapping the mapping
     * @return true if successfully
     * @throws InvalidMappingException 
     */
    protected abstract boolean  addMappingInternal(String user, String domain, String mapping) throws InvalidMappingException;
    
    /**
     * Remove mapping 
     * 
     * @param user the user
     * @param domain the domain
     * @param mapping the mapping 
     * @return true if successfully
     * @throws InvalidMappingException 
     */
    protected abstract boolean  removeMappingInternal(String user, String domain, String mapping) throws InvalidMappingException;

    /**
     * Return Collection of all mappings for the given username and domain
     * 
     * @param user the user
     * @param domain the domain
     * @return Collection which hold the mappings
     */
    protected abstract Collection<String> getUserDomainMappingsInternal(String user, String domain);

    /**
     * Return a Map which holds all Mappings
     * 
     * @return Map
     */
    protected abstract Map<String,Collection<String>> getAllMappingsInternal();
    
    /**
     * Override to map virtual recipients to real recipients, both local and non-local.
     * Each key in the provided map corresponds to a potential virtual recipient, stored as
     * a <code>MailAddress</code> object.
     * 
     * Translate virtual recipients to real recipients by mapping a string containing the
     * address of the real recipient as a value to a key. Leave the value <code>null<code>
     * if no mapping should be performed. Multiple recipients may be specified by delineating
     * the mapped string with commas, semi-colons or colons.
     * 
     * @param user the mapping of virtual to real recipients, as 
     *    <code>MailAddress</code>es to <code>String</code>s.
     */
    protected abstract String mapAddressInternal(String user, String domain);
    
    /**
     * Get all mappings for the given user and domain. If a aliasdomain mapping was found get sure it is in the map as first mapping. 
     * 
     * @param user the username
     * @param domain the domain
     * @return the mappings
     */
    private String mapAddress(String user, String domain) {

        String mappings = mapAddressInternal(user, domain);

        // check if we need to sort
        // TODO: Maybe we should just return the aliasdomain mapping
        if (mappings != null && mappings.indexOf(VirtualUserTable.ALIASDOMAIN_PREFIX) > -1) {
            Collection<String> mapCol = VirtualUserTableUtil.mappingToCollection(mappings);
            Iterator<String> mapIt = mapCol.iterator();
        
            List<String> col = new ArrayList<String>(mapCol.size());
        
            while (mapIt.hasNext()) {
                int i = 0;
                String mapping = mapIt.next().toString();
        
                if (mapping.startsWith(VirtualUserTable.ALIASDOMAIN_PREFIX)) {
                    col.add(i,mapping);
                    i++;
                } else {
                    col.add(mapping);
                }
            }
            return VirtualUserTableUtil.CollectionToMapping(col);
        } else {  
            return mappings;
        }
    }
      
    private boolean checkMapping(String user,String domain, String mapping) {
        Collection<String> mappings = getUserDomainMappings(user,domain);
        if (mappings != null && mappings.contains(mapping)) {
            return false;
        } else {
            return true;
        }
     }

}
