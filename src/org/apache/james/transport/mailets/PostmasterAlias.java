/*****************************************************************************
 * Copyright (C) The Apache Software Foundation. All rights reserved.        *
 * ------------------------------------------------------------------------- *
 * This software is published under the terms of the Apache Software License *
 * version 1.1, a copy of which has been included  with this distribution in *
 * the LICENSE file.                                                         *
 *****************************************************************************/

package org.apache.james.transport.mailets;

import java.util.*;
import javax.mail.*;
import javax.mail.internet.*;
import org.apache.mailet.*;
import com.workingdogs.town.*;

/**
 * Rewrites recipient addresses to make sure email for the postmaster is
 * always handles.  This mailet is silently inserted at the top of the root
 * spool processor.  All recipients mapped to postmaster@<servernames> are
 * changed to the postmaster account as specified in the server conf.
 *
 * @author  Serge Knystautas <sergek@lokitech.com>
 */
public class PostmasterAlias extends GenericMailet {

    public void service(Mail mail) throws MessagingException {
        Collection recipients = mail.getRecipients();
        Collection recipientsToRemove = null;
        MailetContext mailetContext = getMailetContext();
        boolean postmasterAddressed = false;

        for (Iterator i = recipients.iterator(); i.hasNext(); ) {
            MailAddress addr = (MailAddress)i.next();
            if (addr.getUser().equalsIgnoreCase("postmaster") &&
                    mailetContext.isLocalServer(addr.getHost())) {
                //Should remove this address... we want to replace it with
                //  the server's postmaster address
                if (recipientsToRemove == null) {
                    recipientsToRemove = new Vector();
                }
                recipientsToRemove.add(addr);
                //Flag this as having found the postmaster
                postmasterAddressed = true;
            }
        }
        if (postmasterAddressed) {
            recipients.removeAll(recipientsToRemove);
            recipients.add(getMailetContext().getPostmaster());
        }
    }

    public String getMailetInfo() {
        return "Postmaster aliasing mailet";
    }
}
