/*
 * Copyright (C) The Apache Software Foundation. All rights reserved.
 *
 * This software is published under the terms of the Apache Software License
 * version 1.1, a copy of which has been included with this distribution in
 * the LICENSE file.
 */
package org.apache.james.services;

import java.util.Collection;

public interface DNSServer {
    String ROLE = "org.apache.james.services.DNSServer";

    Collection findMXRecords(String hostname);
}
