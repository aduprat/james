/***********************************************************************
 * Copyright (c) 1999-2005 The Apache Software Foundation.             *
 * All rights reserved.                                                *
 * ------------------------------------------------------------------- *
 * Licensed under the Apache License, Version 2.0 (the "License"); you *
 * may not use this file except in compliance with the License. You    *
 * may obtain a copy of the License at:                                *
 *                                                                     *
 *     http://www.apache.org/licenses/LICENSE-2.0                      *
 *                                                                     *
 * Unless required by applicable law or agreed to in writing, software *
 * distributed under the License is distributed on an "AS IS" BASIS,   *
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or     *
 * implied.  See the License for the specific language governing       *
 * permissions and limitations under the License.                      *
 ***********************************************************************/

package org.apache.james.smtpserver;

import org.apache.avalon.framework.logger.AbstractLogEnabled;
import org.apache.avalon.framework.configuration.Configuration;
import org.apache.avalon.framework.configuration.Configurable;
import org.apache.avalon.framework.configuration.ConfigurationException;
import javax.mail.internet.MimeMessage;


/**
  * Adds the header to the message
  */
public class AddHeaderHandler
    extends AbstractLogEnabled
    implements MessageHandler, Configurable {

    /**
     * The header name and value that needs to be added
     */
    private String headerName;
    private String headerValue;

    /**
     * @see org.apache.avalon.framework.configuration.Configurable#configure(Configuration)
     */
    public void configure(Configuration handlerConfiguration) throws ConfigurationException {

        Configuration configuration = handlerConfiguration.getChild("headername");
        if(configuration != null) {
            headerName = configuration.getValue();
        }

        configuration = handlerConfiguration.getChild("headervalue");
        if(configuration != null) {
            headerValue = configuration.getValue();
        }
    }

    /**
     * Adds header to the message
     * @see org.apache.james.smtpserver#onMessage(SMTPSession)
     */
    public void onMessage(SMTPSession session) {
        try {
            MimeMessage message = session.getMail().getMessage ();

            //Set the header name and value (supplied at init time).
            if(headerName != null) {
                message.setHeader(headerName, headerValue);
                message.saveChanges();
            }

        } catch (javax.mail.MessagingException me) {
            getLogger().error(me.getMessage());
        }
    }



}
