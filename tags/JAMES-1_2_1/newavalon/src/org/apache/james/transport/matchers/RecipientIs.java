/*****************************************************************************
 * Copyright (C) The Apache Software Foundation. All rights reserved.        *
 * ------------------------------------------------------------------------- *
 * This software is published under the terms of the Apache Software License *
 * version 1.1, a copy of which has been included  with this distribution in *
 * the LICENSE file.                                                         *
 *****************************************************************************/

package org.apache.james.transport.matchers;

import org.apache.mailet.*;
import java.util.*;
import javax.mail.*;

/**
 * @version 1.0.0, 24/04/1999
 * @author Federico Barbieri <scoobie@pop.systemy.it>
 * @author Serge Knystautas <sergek@lokitech.com>
 */
public class RecipientIs extends GenericRecipientMatcher {

    private Collection recipients;

    public void init() throws javax.mail.MessagingException {
        StringTokenizer st = new StringTokenizer(getCondition(), ", \t", false);
        recipients = new Vector();
        while (st.hasMoreTokens()) {
            recipients.add(new MailAddress(st.nextToken()));
        }
    }

    public boolean matchRecipient(MailAddress recipient) {
        return recipients.contains(recipient);
    }
}
