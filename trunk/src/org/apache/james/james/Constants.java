/*****************************************************************************
 * Copyright (C) The Apache Software Foundation. All rights reserved.        *
 * ------------------------------------------------------------------------- *
 * This software is published under the terms of the Apache Software License *
 * version 1.1, a copy of which has been included  with this distribution in *
 * the LICENSE file.                                                         *
 *****************************************************************************/

package org.apache.james.james;

import org.apache.james.JamesConstants;

/**
 * @version 1.0.0, 24/04/1999
 * @author  Federico Barbieri <scoobie@pop.systemy.it>
 */
public class Constants extends JamesConstants {
    
    public static final String SOFTWARE_NAME = "JAMES Mail Server";
    
    public static final String SERVER_NAMES = "SERVER_NAMES";

    public static final String USERS_REPOSITORY = "USER_REPOSITORY";

    public static final String SPOOL_REPOSITORY = "SPOOL_REPOSITORY";

    public static final String INBOX_ROOT = "INBOX_ROOT";
    
    public static final String POSTMASTER = "POSTMASTER";
    
    public static final int HEADERLIMIT = 2048;
}