/*
 * Copyright (C) The Apache Software Foundation. All rights reserved.
 *
 * This software is published under the terms of the Apache Software License
 * version 1.1, a copy of which has been included with this distribution in
 * the LICENSE file.
 */
package org.apache.james.transport.mailets.debug;

import org.apache.mailet.GenericMailet;
import org.apache.mailet.Mail;

/**
 * Opposite of Null Mailet. It let any incoming mail untouched. Used only for
 * debugging.
 */
public class Identity extends GenericMailet {

    /**
     * Do nothing.
     *
     * @param mail the mail to be processed
     */
    public void service(Mail mail) {
        //Do nothing
    }

    /**
     * Return a string describing this mailet.
     *
     * @return a string describing this mailet
     */
    public String getMailetInfo() {
        return "Identity Mailet";
    }
}

