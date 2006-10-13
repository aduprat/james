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
import java.util.StringTokenizer;

import javax.mail.internet.ParseException;

import org.apache.avalon.framework.logger.AbstractLogEnabled;
import org.apache.james.services.VirtualUserTable;
import org.apache.james.services.VirtualUserTableManagement;
import org.apache.james.util.VirtualUserTableUtil;
import org.apache.mailet.MailAddress;
import org.apache.oro.text.regex.MalformedPatternException;
import org.apache.oro.text.regex.Perl5Compiler;

public abstract class AbstractVirtualUserTable extends AbstractLogEnabled
    implements VirtualUserTable, VirtualUserTableManagement {

    /**
     * @see org.apache.james.services.VirtualUserTable#getMapping(org.apache.mailet.MailAddress)
     */
    public Collection getMappings(String user,String domain) throws ErrorMappingException {
        Collection mappings = new ArrayList();

        String targetString = mapAddress(user, domain);


        // Only non-null mappings are translated
        if (targetString != null) {
            if (targetString.startsWith("error:")) {
                throw new ErrorMappingException(targetString.substring("error:".length()));

            } else {
                Iterator map= VirtualUserTableUtil.getMappings(targetString).iterator();

                while (map.hasNext()) {
                    String target;
                    String targetAddress = map.next().toString();

                    if (targetAddress.startsWith("regex:")) {
                        try {
                            targetAddress = VirtualUserTableUtil.regexMap(new MailAddress(user,domain), targetAddress);
                        } catch (MalformedPatternException e) {
                            getLogger().error("Exception during regexMap processing: ", e);
                        } catch (ParseException e) {
                           // should never happen
                        } 

                        if (targetAddress == null) continue;
                    }
            
                    if (targetAddress.indexOf('@') < 0) {
                         target = targetAddress + "@localhost";
                    } else {
                        target = targetAddress;
                    }
            
                    // add mapping
                    mappings.add(target);

                    StringBuffer buf = new StringBuffer().append("Valid virtual user mapping ")
                                                         .append(user).append("@").append(domain)
                                                         .append(" to ").append(targetAddress);
                    getLogger().debug(buf.toString());

                }
         }
    }
    return mappings;
    }
    
    /**
     * @see org.apache.james.services.VirtualUserTableManagement#addRegexMapping(java.lang.String, java.lang.String, java.lang.String)
     */
    public boolean addRegexMapping(String user, String domain, String regex) throws InvalidMappingException {
        // TODO: More logging
    
        if (validUserString(user) == false) {
            throw new InvalidMappingException("Invalid user: " + user);
        }
        if(validDomainString(domain) == false) {
            throw new InvalidMappingException("Invalid domain: " + domain);
        }
    
        try {
            new Perl5Compiler().compile(regex);
        } catch (MalformedPatternException e) {
            throw new InvalidMappingException("Invalid regex: " + regex);
        }
        return addMappingInternal(user,domain,"regex:" + regex);
    }

    
    /**
     * @see org.apache.james.services.VirtualUserTableManagement#removeRegexMapping(java.lang.String, java.lang.String, java.lang.String)
     */
    public boolean removeRegexMapping(String user, String domain, String regex) {
        // TODO: More logging
    
        return removeMappingInternal(user,domain,"regex:" + regex);
    }
    
    /**
     * @see org.apache.james.services.VirtualUserTableManagement#addAddressMapping(java.lang.String, java.lang.String, java.lang.String)
     */
    public boolean addAddressMapping(String user, String domain, String address) throws InvalidMappingException {
        // TODO: More logging
    
        if (validUserString(user) == false) {
            throw new InvalidMappingException("Invalid user: " + user);
        }
        if(validDomainString(domain) == false) {
            throw new InvalidMappingException("Invalid domain: " + domain);
        }
    
        if (address.indexOf('@') < 0) {
            address =  address + "@localhost";
        } 
        try {
            new MailAddress(address);
        } catch (ParseException e) {
            throw new InvalidMappingException("Invalid emailAddress: " + address);
        }
        return addMappingInternal(user,domain, address);
    }
    
    /**
     * @see org.apache.james.services.VirtualUserTableManagement#removeAddressMapping(java.lang.String, java.lang.String, java.lang.String)
     */
    public boolean removeAddressMapping(String user, String domain, String address) {
        // TODO: More logging
    
        if (address.indexOf('@') < 0) {
            address =  address + "@localhost";
        } 
        return removeMappingInternal(user,domain,address);
    }
    
    /**
     * @throws InvalidMappingException 
     * @see org.apache.james.services.VirtualUserTableManagement#addErrorMapping(java.lang.String, java.lang.String, java.lang.String)
     */
    public boolean addErrorMapping(String user, String domain, String error) throws InvalidMappingException {
        // TODO: More logging
    
        if (validUserString(user) == false) {
            throw new InvalidMappingException("Invalid user: " + user);
        }
        if(validDomainString(domain) == false) {
            throw new InvalidMappingException("Invalid domain: " + domain);
        }  
    
        return addMappingInternal(user,domain, "error:" + error);
    }
    
    /**
     * @see org.apache.james.services.VirtualUserTableManagement#removeErrorMapping(java.lang.String, java.lang.String, java.lang.String)
     */
    public boolean removeErrorMapping(String user, String domain, String error) {
        // TODO: More logging
    
        return removeMappingInternal(user,domain,"error:" + error);
    }


    /**
     * Convert a raw mapping String to a Collection
     * 
     * @param rawMapping the mapping Strin
     * @return map a collection which holds all mappings
     */
    protected ArrayList mappingToColletion(String rawMapping) {
        ArrayList map = new ArrayList();
        StringTokenizer tokenizer = new StringTokenizer(rawMapping,
        VirtualUserTableUtil.getSeparator(rawMapping));

        while (tokenizer.hasMoreTokens()) {
            map.add(tokenizer.nextToken().trim());
        }
        return map;
   }
    

    /**
     * Convert a Collection which holds mappings to a raw mapping String
     * 
     * @param map the Collection
     * @return mapping the mapping String
     */
    protected String CollectionToMapping(Collection map) {
        StringBuffer mapping = new StringBuffer();
    
        Iterator mappings = map.iterator();
    
        while (mappings.hasNext()) {
            mapping.append(mappings.next());
        
            if (mappings.hasNext()) {
                mapping.append(";");
            }
        }  
    
        return mapping.toString();
    
   }
    
    /**
     * Return true if the userString is valid
     * TODO: More checkin ?
     * 
     * @param user the userString
     * @return true of false
     */
    private boolean validUserString(String user) {
        if(user.endsWith("@%") || user.indexOf("@") < 0) {
            return true;
        }
        return false;
    }
    
    /**
     * Return true if the domainString is valid
     * TODO: More checkin ?
     * 
     * @param domain the domainString
     * @return true of false
     */
    private boolean validDomainString(String domain) {
        if (domain.startsWith("%@") || domain.indexOf("@") < 0) {
            return true;  
        }
        return false;
    }
    

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
     * @param recipient the mapping of virtual to real recipients, as 
     *    <code>MailAddress</code>es to <code>String</code>s.
     */
    protected abstract String mapAddress(String user, String domain);
    
    /**
     * Add new mapping
     * 
     * @param user the user
     * @param domain the domain
     * @param mapping the mapping
     * @return true if successfully
     */
    public abstract boolean  addMappingInternal(String user, String domain, String mapping);
    
    /**
     * Remove mapping 
     * 
     * @param user the user
     * @param domain the domain
     * @param mapping the mapping 
     * @return true if successfully
     */
    public abstract boolean  removeMappingInternal(String user, String domain, String mapping);

}
