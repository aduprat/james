/*****************************************************************************
 * Copyright (C) The Apache Software Foundation. All rights reserved.        *
 * ------------------------------------------------------------------------- *
 * This software is published under the terms of the Apache Software License *
 * version 1.1, a copy of which has been included  with this distribution in *
 * the LICENSE file.                                                         *
 *****************************************************************************/

package org.apache.james.transport.matchers;

import java.util.Collection;
import org.apache.mail.*;
import org.apache.james.transport.*;
/**
 * @version 1.0.0, 24/04/1999
 * @author  Federico Barbieri <scoobie@pop.systemy.it>
 */
public class All extends AbstractMatcher {

    public void init(String condition) {
    }

    public Collection match(Mail mail) {
        return mail.getRecipients();
    }
}

