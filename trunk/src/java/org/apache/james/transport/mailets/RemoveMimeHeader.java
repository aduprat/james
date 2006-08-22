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



package org.apache.james.transport.mailets;

import java.util.ArrayList;
import java.util.StringTokenizer;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;

import org.apache.mailet.GenericMailet;
import org.apache.mailet.Mail;
import org.apache.mailet.MailetException;

/**
 * Remove mime headers
 * 
 * Sample configuration:
 * 
 * &lt;mailet match="All" class="RemoveMimeHeader"&gt;
 * &lt;name&gt;header1&lt;/name&gt;
 * &lt;name&gt;header2&lt;/name&gt;
 * &lt;/mailet&gt;
 * 
 */
public class RemoveMimeHeader extends GenericMailet {
    
    /**
     * Arraylist which holds the headers which should be removed
     */
    ArrayList headers = new ArrayList();

    /**
     * @see org.apache.mailet.GenericMailet#init()
     */
    public void init() throws MailetException {
        String header = getInitParameter("name");
        if (header != null) {
            StringTokenizer st = new StringTokenizer(header, ",");
            while (st.hasMoreTokens()) {
                headers.add(st.nextToken());
            }
        } else {
            throw new MailetException(
                    "Invalid config. Please specify atleast one name");
        }
    }
    
    /**
     * @see org.apache.mailet.GenericMailet#service(Mail)
     */
    public void service(Mail mail) {
    try {
        MimeMessage  message = mail.getMessage();
        
        // loop through the headers
        for (int i = 0; i < headers.size(); i++) {
            message.removeHeader((String) headers.get(i));
        }
    } catch (MessagingException e) {
        // just log the exception
        log("Unable to remove headers: " + e.getMessage());
    }
    }
    
    /**
     * @see org.apache.mailet.GenericMailet#getMailetInfo()
     */
    public String getMailetInfo() {
        return "RemoveMimeHeader Mailet";
    }
}
