/*
 * Copyright (C) The Apache Software Foundation. All rights reserved.
 *
 * This software is published under the terms of the Apache Software License
 * version 1.1, a copy of which has been included with this distribution in
 * the LICENSE file.
 */
package org.apache.james;

/**
 * Assorted Constants for use in all James blocks
 * The Software Version, Software Name and Build Date are set by ant during
 * the build process.
 *
 * @author <a href="mailto:fede@apache.org">Federico Barbieri</a>
 *
 * @version This is $Revision: 1.3 $
 * Committed on $Date: 2001/06/24 13:18:36 $ by: $Author: charlesb $ 
 */
public class Constants {

    public static final String SOFTWARE_VERSION = "@@VERSION@@";

    public static final String SOFTWARE_NAME = "@@NAME@@";

    public static final String SERVER_NAMES = "SERVER_NAMES";

    public static final String SPOOL_REPOSITORY = "SPOOL_REPOSITORY";

    public static final String LOCAL_USERS = "LOCAL_USERS";

    public static final String POSTMASTER = "POSTMASTER";

    public static final int HEADERLIMIT = 2048;

    public static final String AVALON_COMPONENT_MANAGER = "AVALON_COMP_MGR";

    public static final String BUILD_DATE = "@@DATE@@";

}
