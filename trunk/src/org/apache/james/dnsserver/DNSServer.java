/*****************************************************************************
 * Copyright (C) The Apache Software Foundation. All rights reserved.        *
 * ------------------------------------------------------------------------- *
 * This software is published under the terms of the Apache Software License *
 * version 1.1, a copy of which has been included  with this distribution in *
 * the LICENSE file.                                                         *
 *****************************************************************************/

package org.apache.james.dnsserver;

import java.io.*;
import java.net.*;
import java.util.*;
import org.apache.avalon.*;
import org.apache.avalon.AbstractLoggable;
import org.apache.james.transport.Resources;
import org.apache.mailet.Mail;
import org.xbill.DNS.*;

/**
 * @version 1.0.0, 18/06/2000
 * @author  Serge Knystautas <sergek@lokitech.com>
 */
public class DNSServer 
    extends AbstractLoggable
    implements Component, Configurable, Contextualizable, Initializable {

    private DefaultComponentManager compMgr;
    private Configuration conf;
    private Resolver resolver;
    private Cache cache;
    private byte dnsCredibility;

    public void configure(Configuration conf) throws ConfigurationException{
        this.conf = conf;
    }

    public void compose(ComponentManager comp) {
        compMgr = new DefaultComponentManager(comp);
    }

    public void contextualize(Context context) {
    }

    public void init() throws Exception {

        getLogger().info("DNSServer init...");

        // Get this servers that this block will use for lookups
        Collection servers = new Vector();
        Configuration serversConf = conf.getChild("servers");
        for (Iterator it = serversConf.getChildren("server"); it.hasNext(); ) {
            servers.add(((Configuration) it.next()).getValue());
        }
        if (servers.isEmpty()) {
            try {
                servers.add(InetAddress.getLocalHost().getHostName());
            } catch (UnknownHostException ue) {
                servers.add("127.0.0.1");
            }
        }
        for (Iterator i = servers.iterator(); i.hasNext(); ) {
            getLogger().info("DNS Servers is: " + i.next());
        }
        boolean authoritative = conf.getChild("authoritative").getValueAsBoolean(false);

        //Create the extended resolver...
        String serversArray[] = (String[])servers.toArray(new String[0]);
        resolver = new ExtendedResolver (serversArray);

        dnsCredibility = authoritative ? Credibility.AUTH_ANSWER : Credibility.NONAUTH_ANSWER;

        cache = new Cache ();

        // Add this to comp
        compMgr.put("DNS_SERVER", this);

        getLogger().info("DNSServer ...init end");
    }

    public Collection findMXRecords(String hostname) {
        Record answers[] = rawDNSLookup(hostname, false, Type.MX);

        Collection servers = new Vector ();
        try {
            if (answers == null) {
                return servers;
            }

            MXRecord mxAnswers[] = new MXRecord[answers.length];
            for (int i = 0; i < answers.length; i++) {
                mxAnswers[i] = (MXRecord)answers[i];
            }

            Comparator prioritySort = new Comparator () {
                public int compare (Object a, Object b) {
                    MXRecord ma = (MXRecord)a;
                    MXRecord mb = (MXRecord)b;
                    return ma.getPriority () - mb.getPriority ();
                }
            };
            Arrays.sort(mxAnswers, prioritySort);

            for (int i = 0; i < mxAnswers.length; i++) {
                servers.add(mxAnswers[i].getTarget ().toString ());
            }
            return servers;
        } finally {
            //If we found no results, we'll add the original domain name if
            //it's a valid DNS entry
            if (servers.size () == 0) {
                try {
                    InetAddress.getByName(hostname);
                    servers.add(hostname);
                } catch (UnknownHostException uhe) {
                }
            }
        }
    }

    private Record[] rawDNSLookup(String namestr, boolean querysent, short type) {
        Name name = new Name(namestr);
        short dclass = DClass.IN;

        Record [] answers;
        int answerCount = 0, n = 0;
        Enumeration e;

        SetResponse cached = cache.lookupRecords(name, type, dclass, dnsCredibility);
        if (cached.isSuccessful()) {
            RRset [] rrsets = cached.answers();
            answerCount = 0;
            for (int i = 0; i < rrsets.length; i++) {
                answerCount += rrsets[i].size();
            }

            answers = new Record[answerCount];

            for (int i = 0; i < rrsets.length; i++) {
                e = rrsets[i].rrs();
                while (e.hasMoreElements()) {
                    Record r = (Record)e.nextElement();
                    answers[n++] = r;
                }
            }
        }
        else if (cached.isNegative()) {
            return null;
        }
        else if (querysent) {
            return null;
        }
        else {
            Record question = Record.newRecord(name, type, dclass);
            org.xbill.DNS.Message query = org.xbill.DNS.Message.newQuery(question);
            org.xbill.DNS.Message response;

            try {
                response = resolver.send(query);
            }
            catch (Exception ex) {
                return null;
            }

            short rcode = response.getHeader().getRcode();
            if (rcode == Rcode.NOERROR || rcode == Rcode.NXDOMAIN) {
                cache.addMessage(response);
            }

            if (rcode != Rcode.NOERROR) {
                return null;
            }

            return rawDNSLookup(namestr, true, Type.MX);
        }

        return answers;
    }
}
