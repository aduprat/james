/*
 * Copyright (C) The Apache Software Foundation. All rights reserved.
 *
 * This software is published under the terms of the Apache Software License
 * version 1.1, a copy of which has been included with this distribution in
 * the LICENSE file.
 */
package org.apache.james.transport.mailets;

import org.apache.mailet.GenericMailet;
import org.apache.mailet.Mail;
import org.apache.mailet.MailetException;

import javax.mail.MessagingException;

/**
 * No idea what this class is for..... seems to send processor of a message to
 * another mailet (which I didn't think we were supporting)
 *
 * Sample configuration:
 * <mailet match="All" class="ToProcessor">
 *   <processor>spam</processor>
 *   <notice>Notice attached to the message (optional)</notice>
 * </mailet>
 *
 * @author  Federico Barbieri <scoobie@pop.systemy.it>
 * @author  Serge Knystautas <sergek@lokitech.com>
 */
public class ToProcessor extends GenericMailet {
    String processor;
    String noticeText = null;

    public void init() throws MailetException {
        processor = getInitParameter("processor");
        if (processor == null) {
            throw new MailetException("processor parameter is required");
        }
        noticeText = getInitParameter("notice");
    }

    /**
     * Throw an exception if any mail is processed.
     *
     * @param mail the mail to process
     *
     * @throws MessagingException in all cases
     */
    public void service(Mail mail) throws MessagingException {
        StringBuffer logBuffer =
            new StringBuffer(128)
                    .append("Sending mail ")
                    .append(mail)
                    .append(" to ")
                    .append(processor);
        log(logBuffer.toString());
        mail.setState(processor);
        if (noticeText != null) {
            if (mail.getErrorMessage() == null) {
                mail.setErrorMessage(noticeText);
            } else {
                StringBuffer errorMessageBuffer =
                    new StringBuffer(256)
                            .append(mail.getErrorMessage())
                            .append("\r\n")
                            .append(noticeText);
                mail.setErrorMessage(errorMessageBuffer.toString());
            }
        }
    }

    /**
     * Return a string describing this mailet.
     *
     * @return a string describing this mailet
     */
    public String getMailetInfo() {
        return "ToProcessor Mailet";
    }
}
