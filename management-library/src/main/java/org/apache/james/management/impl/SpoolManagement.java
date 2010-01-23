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




package org.apache.james.management.impl;

import org.apache.avalon.cornerstone.services.store.Store;
import org.apache.avalon.framework.container.ContainerUtil;
import org.apache.commons.configuration.DefaultConfigurationBuilder;
import org.apache.james.management.SpoolFilter;
import org.apache.james.management.SpoolManagementException;
import org.apache.james.management.SpoolManagementMBean;
import org.apache.james.management.SpoolManagementService;
import org.apache.james.services.SpoolRepository;
import org.apache.mailet.Mail;
import org.apache.mailet.MailAddress;
import org.apache.oro.text.regex.Pattern;
import org.apache.oro.text.regex.Perl5Matcher;

import javax.annotation.Resource;
import javax.mail.MessagingException;
import javax.mail.Address;
import javax.mail.internet.MimeMessage;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

/**
 * high-level management of spool contents like list, remove, resend
 */
public class SpoolManagement implements SpoolManagementService, SpoolManagementMBean {

    private Store mailStore;

    /**
     * Set the Store
     * 
     * @param mailStore the store
     */
    @Resource(name="mailstore")
    public void setStore(Store mailStore) {
        this.mailStore = mailStore;
    }

    /**
     * Move all mails from the given repository to another repository matching the given filter criteria
     *
     * @param srcSpoolRepositoryURL the spool whose item are listed
     * @param srcState if not NULL, only mails with matching state are returned
     * @param dstSpoolRepositoryURL the destination spool
     * @param dstState if not NULL, the state will be changed before storing the message to the new repository.
     * @param header if not NULL, only mails with at least one header with a value matching headerValueRegex are returned
     * @param headerValueRegex the regular expression the header must match
     * @return a counter of moved mails
     * @throws SpoolManagementException
     */
    public int moveSpoolItems(String srcSpoolRepositoryURL, String srcState, String dstSpoolRepositoryURL, String dstState, String header, String headerValueRegex) 
            throws SpoolManagementException {
        SpoolFilter filter = new SpoolFilter(srcState, header, headerValueRegex);
        try {
            return moveSpoolItems(srcSpoolRepositoryURL, dstSpoolRepositoryURL, dstState, filter);
        } catch (Exception e) {
            throw new SpoolManagementException(e);
        }
    }

    /**
     * Move all mails from the given repository to another repository matching the given filter criteria
     *
     * @param srcSpoolRepositoryURL the spool whose item are listed
     * @param dstSpoolRepositoryURL the destination spool
     * @param dstState if not NULL, the state will be changed before storing the message to the new repository.
     * @param filter the filter to select messages from the source repository
     * @return a counter of moved mails
     * @throws ServiceException 
     * @throws MessagingException 
     * @throws SpoolManagementException
     */
    public int moveSpoolItems(String srcSpoolRepositoryURL, String dstSpoolRepositoryURL, String dstState, SpoolFilter filter)
            throws MessagingException, SpoolManagementException {
        
        SpoolRepository srcSpoolRepository;
        SpoolRepository dstSpoolRepository;
        srcSpoolRepository = getSpoolRepository(srcSpoolRepositoryURL);
        dstSpoolRepository = getSpoolRepository(dstSpoolRepositoryURL);
        
        // get an iterator of all keys
        Iterator<String> spoolR = srcSpoolRepository.list();

        int count = 0;
        while (spoolR.hasNext()) {
            String key = spoolR.next();
            boolean locked = false;
            try {
                locked = srcSpoolRepository.lock(key);
            } catch (MessagingException e) {
                // unable to lock
            }
            if (locked) {
                Mail m = null;
                try {
                    m = srcSpoolRepository.retrieve(key);
                    if (filterMatches(m, filter)) {
                        if (dstState != null) {
                            m.setState(dstState);
                        }
                        dstSpoolRepository.store(m);
                        srcSpoolRepository.remove(m);
                        count++;
                    }
                } catch (MessagingException e) {
                    // unable to retrieve message
                } finally {
                    try {
                        srcSpoolRepository.unlock(key);
                    } catch (MessagingException e) {
                        // unable to unlock
                    }
                    ContainerUtil.dispose(m);
                }
            }
        }
        
        return count;

    }

    /**
     * Lists all mails from the given repository matching the given filter criteria 
     * 
     * @param spoolRepositoryURL the spool whose item are listed
     * @param state if not NULL, only mails with matching state are returned
     * @param header if not NULL, only mails with at least one header with a value matching headerValueRegex are returned
     * @param headerValueRegex the regular expression the header must match
     * @return String array, each line describing one matching mail from the spool 
     * @throws SpoolManagementException
     */
    public String[] listSpoolItems(String spoolRepositoryURL, String state, String header, String headerValueRegex) 
            throws SpoolManagementException {
        return listSpoolItems(spoolRepositoryURL, new SpoolFilter(state, header, headerValueRegex));
    }

    /**
     * Lists all mails from the given repository matching the given filter criteria 
     * 
     * @param spoolRepositoryURL the spool whose item are listed
     * @param filter the criteria against which all mails are matched
     * @return String array, each line describing one matching mail from the spool 
     * @throws SpoolManagementException
     */
    public String[] listSpoolItems(String spoolRepositoryURL, SpoolFilter filter) throws SpoolManagementException {
        List<String> spoolItems;
        try {
            spoolItems = getSpoolItems(spoolRepositoryURL, filter);
        } catch (Exception e) {
             throw new SpoolManagementException(e);
        }
        return spoolItems.toArray(new String[]{});
    }

    /**
     * Return true if the given Mail match the given SpoolFilter
     * 
     * @param mail the Mail which should be checked
     * @param filter the SpoolFilter which should be used
     * @return TRUE, if given mail matches all given filter criteria
     * @throws SpoolManagementException
     */
    protected boolean filterMatches(Mail mail, SpoolFilter filter) throws SpoolManagementException {
        if (filter == null || !filter.doFilter()) return true;

        if (filter.doFilterState() && !mail.getState().equalsIgnoreCase(filter.getState())) return false;
        
        if (filter.doFilterHeader()) {

            Perl5Matcher matcher = new Perl5Matcher();
            
            // check, if there is a match for every header/regex pair
            Iterator<String> headers = filter.getHeaders();
            while (headers.hasNext()) {
                String header = headers.next();
                
                String[] headerValues;
                try {
                    headerValues = mail.getMessage().getHeader(header);
                    if (headerValues == null) {
                        // some headers need special retrieval
                        if (header.equalsIgnoreCase("to")) {
                            headerValues = addressesToStrings(mail.getMessage().getRecipients(MimeMessage.RecipientType.TO));
                        }
                        else if (header.equalsIgnoreCase("cc")) { 
                            headerValues = addressesToStrings(mail.getMessage().getRecipients(MimeMessage.RecipientType.CC));
                        }
                        else if (header.equalsIgnoreCase("bcc")) { 
                            headerValues = addressesToStrings(mail.getMessage().getRecipients(MimeMessage.RecipientType.BCC));
                        }
                        else if (header.equalsIgnoreCase("from")) { 
                            headerValues = new String[]{mail.getMessage().getSender().toString()};
                        }
                    }
                } catch (MessagingException e) {
                    throw new SpoolManagementException("could not filter mail by headers", e);
                }
                if (headerValues == null) return false; // no header for this criteria

                Pattern pattern = filter.getHeaderValueRegexCompiled(header);

                // the regex must match at least one entry for the header
                boolean matched = false;
                for (int i = 0; i < headerValues.length; i++) {
                    String headerValue = headerValues[i];
                    if (matcher.matches(headerValue, pattern)) {
                        matched = true;
                        break;
                    }
                }
                if (!matched) return false;
            }
        }
            
        return true;
    }

    private String[] addressesToStrings(Address[] addresses) {
        if (addresses == null) return null;
        if (addresses.length == 0) return new String[]{};
        String[] addressStrings = new String[addresses.length];
        for (int i = 0; i < addresses.length; i++) {
            addressStrings[i] = addresses[i].toString();
        }
        return addressStrings;
    }

    /**
     * @see org.apache.james.management.SpoolManagementService#getSpoolItems(String, SpoolFilter)
     */
    public List<String> getSpoolItems(String spoolRepositoryURL, SpoolFilter filter)
            throws MessagingException, SpoolManagementException {
        SpoolRepository spoolRepository = getSpoolRepository(spoolRepositoryURL);

        List<String> items = new ArrayList<String>();

        // get an iterator of all keys
        Iterator<String> spoolR = spoolRepository.list();
        while (spoolR.hasNext()) {
            String key = spoolR.next().toString();
            Mail m = spoolRepository.retrieve(key);

            if (filterMatches(m, filter)) {
                StringBuffer itemInfo = new StringBuffer();
                itemInfo.append("key: ").append(key).append(" sender: ").append(m.getSender()).append(" recipient:");
                Collection<MailAddress> recipients = m.getRecipients();
                for (Iterator<MailAddress> iterator = recipients.iterator(); iterator.hasNext();) {
                    MailAddress mailAddress = (MailAddress) iterator.next();
                    itemInfo.append(" ").append(mailAddress);
                }
                items.add(itemInfo.toString());
            }
        }

        return items;
    }

    /**
     * @see org.apache.james.management.SpoolManagementMBean#removeSpoolItems(String, String, String, String, String)
     */
    public int removeSpoolItems(String spoolRepositoryURL, String key, String state, String header, String headerValueRegex) 
            throws SpoolManagementException {
        return removeSpoolItems(spoolRepositoryURL, key, new SpoolFilter(state, header, headerValueRegex));
    }

    /**
     * Removes all mails from the given repository matching the filter
     *  
     * @param spoolRepositoryURL the spool whose item are listed
     * @param key ID of the mail to be removed. if not NULL, all other filters are ignored
     * @param filter the criteria against which all mails are matched. only applied if key is NULL.
     * @return number of removed mails
     * @throws SpoolManagementException
     */
    public int removeSpoolItems(String spoolRepositoryURL, String key, SpoolFilter filter) throws SpoolManagementException {
        try {
            return removeSpoolItems(spoolRepositoryURL, key, null, filter);
        } catch (Exception e) {
            throw new SpoolManagementException(e);
        }
    }


    /**
     * @see org.apache.james.management.SpoolManagementService#removeSpoolItems(String, String, List, SpoolFilter)
     */
    public int removeSpoolItems(String spoolRepositoryURL, String key, List<String> lockingFailures, SpoolFilter filter) throws SpoolManagementException, MessagingException, SpoolManagementException {
        int count = 0;
        SpoolRepository spoolRepository = getSpoolRepository(spoolRepositoryURL);

        if (key != null) {
            count = removeMail(spoolRepository, key, count, lockingFailures, null);
        } else {
            Iterator<String> spoolR = spoolRepository.list();

            while (spoolR.hasNext()) {
                key = spoolR.next();
                count = removeMail(spoolRepository, key, count, lockingFailures, filter);
            }
        }
        return count;
    }

    private int removeMail(SpoolRepository spoolRepository, String key, int count, List<String> lockingFailures, SpoolFilter filter) throws MessagingException {
        try {
            if (removeMail(spoolRepository, key, filter)) count++;
        } catch (IllegalStateException e) {
            lockingFailures.add(key);
        } catch (SpoolManagementException e) {
            return count;
        }
        return count;
    }

    /**
     * Tries to resend all mails from the given repository matching the given filter criteria 
     * 
     * @param spoolRepositoryURL the spool whose item are about to be resend
     * @param key ID of the mail to be resend. if not NULL, all other filters are ignored
     * @param filter the SpoolFilter to use
     * @return int the number of resent mails
     * @throws SpoolManagementException
     */
    public int resendSpoolItems(String spoolRepositoryURL, String key, SpoolFilter filter) throws SpoolManagementException {
        try {
            return resendSpoolItems(spoolRepositoryURL, key, null, filter);
        } catch (Exception e) {
            throw new SpoolManagementException(e);
        }
    }

    /**
     * Tries to resend all mails from the given repository matching the given filter criteria 
     * @param spoolRepositoryURL the spool whose item are about to be resend
     * @param key ID of the mail to be resend. if not NULL, all other filters are ignored
     * @param state if not NULL, only mails with matching state are resend
     * @param header if not NULL, only mails with at least one header with a value matching headerValueRegex are resend
     * @param headerValueRegex the regular expression the header must match
     * @return int number of resent mails 
     * @throws SpoolManagementException
     */
    public int resendSpoolItems(String spoolRepositoryURL, String key, String state, String header, String headerValueRegex) throws SpoolManagementException {
        return resendSpoolItems(spoolRepositoryURL, key, new SpoolFilter(state, header, headerValueRegex));
    }


    /**
     * @see org.apache.james.management.SpoolManagementService#resendSpoolItems(String, String, List, SpoolFilter)
     */
    public int resendSpoolItems(String spoolRepositoryURL, String key, List<String> lockingFailures, SpoolFilter filter)
            throws MessagingException, SpoolManagementException {
        int count = 0;
        SpoolRepository spoolRepository = getSpoolRepository(spoolRepositoryURL);

        // check if an key was given as argument
        if (key != null) {
            try {
                if (resendMail(spoolRepository, key, filter)) count++;
            } catch (IllegalStateException e) {
                if (lockingFailures != null) lockingFailures.add(key);
            }
        } else {
            // get an iterator of all keys
            Iterator<String> spoolR = spoolRepository.list();

            while (spoolR.hasNext()) {
                key = spoolR.next();
                try {
                    if (resendMail(spoolRepository, key, filter)) count++;
                } catch (IllegalStateException e) {
                    if (lockingFailures != null) lockingFailures.add(key);
                }
            }
        }
        return count;
    }

    /**
     * Resent the mail that belongs to the given key and spoolRepository 
     * 
     * @param spoolRepository The spoolRepository
     * @param key The message key
     * @param filter
     * @return true or false
     * @throws MessagingException Get thrown if there happen an error on modify the mail
     */
    private boolean resendMail(SpoolRepository spoolRepository, String key, SpoolFilter filter)
            throws MessagingException, IllegalStateException, SpoolManagementException {
        if (!spoolRepository.lock(key)) throw new IllegalStateException("locking failure");

        // get the mail and set the error_message to "0" that will force the spoolmanager to try to deliver it now!
        Mail m = spoolRepository.retrieve(key);

        if (filterMatches(m, filter)) {

            // this will force Remotedelivery to try deliver the mail now!
            m.setLastUpdated(new Date(0));

            // store changes
            spoolRepository.store(m);
            spoolRepository.unlock(key);

            synchronized (spoolRepository) {
                spoolRepository.notify();
            }
            return true;
        } else {
            spoolRepository.unlock(key);
            return false;
        }
    }

    /**
     * Remove the mail that belongs to the given key and spoolRepository 
     * 
     * @param spoolRepository The spoolRepository
     * @param key The message key
     * @param filter
     * @return true or false
     * @throws MessagingException Get thrown if there happen an error on modify the mail
     */
    private boolean removeMail(SpoolRepository spoolRepository, String key, SpoolFilter filter) 
            throws MessagingException, SpoolManagementException {
        if (!spoolRepository.lock(key)) throw new IllegalStateException("locking failure");

        Mail m = spoolRepository.retrieve(key);
        if (m == null) throw new SpoolManagementException("mail not available having key " + key);
        if (!filterMatches(m, filter)) return false;
        ContainerUtil.dispose(m);
        spoolRepository.remove(key);
        return true;
    }

    /**
     * Retrieve a spoolRepository by the given url
     * 
     * @param url The spoolRepository url
     * @return The spoolRepository
     * @throws ServiceException Get thrown if the spoolRepository can not retrieved
     */
    private SpoolRepository getSpoolRepository(String url)
            throws SpoolManagementException {
        // Setup all needed data
        DefaultConfigurationBuilder spoolConf = new DefaultConfigurationBuilder();
        spoolConf.addProperty("[@destinationURL]", url);
        spoolConf.addProperty("[@type]", "SPOOL");

        try {
            return (SpoolRepository) mailStore.select(spoolConf);
        } catch (Exception e) {
            throw new SpoolManagementException("Exception while looking up for the repository", e);
        }
    }


}