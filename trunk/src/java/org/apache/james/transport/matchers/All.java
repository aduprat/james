/*
 * Copyright (C) The Apache Software Foundation. All rights reserved.
 *
 * This software is published under the terms of the Apache Software License
 * version 1.1, a copy of which has been included with this distribution in
 * the LICENSE file.
 */
package org.apache.james.transport.matchers;

import org.apache.mailet.GenericMatcher;
import org.apache.mailet.Mail;

import java.util.Collection;

/**
 * @version 1.0.0, 24/04/1999
 */
public class All extends GenericMatcher {
    public Collection match(Mail mail) {
        return mail.getRecipients();
    }
}
