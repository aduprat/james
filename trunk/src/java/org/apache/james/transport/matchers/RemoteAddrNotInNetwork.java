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

import javax.mail.MessagingException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collection;
import java.util.Iterator;
import java.util.StringTokenizer;
import java.util.Vector;

/**
 * Checks the IP address of the sending server against a comma-
 * delimited list of IP addresses or domain names.
 * <P>Networks should be indicated with a wildcard *, e.g. 192.168.*
 * <br>Note: The wildcard can go at any level, the matcher will match if the
 * sending host's IP address (as a String based on the octet representation)
 * starts with the String indicated in the configuration file, excluding the
 * wildcard.
 * <p>Multiple addresses can be indicated, e.g: '127.0.0.1,192.168.*,domain.tld'
 *
 */
public class RemoteAddrNotInNetwork extends GenericMatcher {
    private Collection networks = null;

    public void init() throws MessagingException {
        StringTokenizer st = new StringTokenizer(getCondition(), ", ", false);
        networks = new Vector();
        while (st.hasMoreTokens()) {
            String addr = st.nextToken();
            if (addr.equals("127.0.0.1")) {
                //Add address of local machine as a match
                try {
                    InetAddress localaddr = InetAddress.getLocalHost();
                    networks.add(localaddr.getHostAddress());
                } catch (UnknownHostException uhe) {
                }
            }

            try {
                if (addr.endsWith("*")) {
                    addr = addr.substring(0, addr.length() - 1);
                }
                else {
                    addr = InetAddress.getByName(addr).getHostAddress();
                }
                networks.add(addr);
            } catch (UnknownHostException uhe) {
                log("Cannot match against invalid domain: " + uhe.getMessage());
            }
        }
    }

    public Collection match(Mail mail) {
        String host = mail.getRemoteAddr();
        //Check to see whether it's in any of the networks... needs to be smarter to
        // support subnets better
        for (Iterator i = networks.iterator(); i.hasNext(); ) {
            String invalidNetwork = i.next().toString();
            if (host.startsWith(invalidNetwork)) {
                //This is in this network... that's all we need for a failed match
                return null;
            }
        }
        //Could not match this to any network
        return mail.getRecipients();
    }
}
