/*
 * Copyright (C) The Apache Software Foundation. All rights reserved.
 *
 * This software is published under the terms of the Apache Software License
 * version 1.1, a copy of which has been included with this distribution in
 * the LICENSE file.
 */
package org.apache.james.transport.matchers;

import org.apache.mailet.GenericRecipientMatcher;
import org.apache.mailet.MailAddress;

import java.util.StringTokenizer;
import java.util.Vector;

/**
 * @version 1.0.0, 24/04/1999
 */
public class UserIs extends GenericRecipientMatcher {
    Vector users = null;

    public void init() {
        StringTokenizer st = new StringTokenizer(getCondition(), ", ", false);
        users = new Vector();
        while (st.hasMoreTokens()) {
            users.add(st.nextToken());
        }
    }

    public boolean matchRecipient(MailAddress recipient) {
        return users.contains(recipient.getUser());
    }
}

