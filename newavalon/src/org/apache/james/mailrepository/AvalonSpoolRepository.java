/*****************************************************************************
 * Copyright (C) The Apache Software Foundation. All rights reserved.        *
 * ------------------------------------------------------------------------- *
 * This software is published under the terms of the Apache Software License *
 * version 1.1, a copy of which has been included  with this distribution in *
 * the LICENSE file.                                                         *
 *****************************************************************************/

package org.apache.james.mailrepository;

import java.io.*;
import java.util.*;
import javax.mail.internet.*;
import javax.mail.MessagingException;

import org.apache.avalon.*;
//import org.apache.avalon.blocks.*;
import org.apache.avalon.services.*;
import org.apache.avalon.util.*;
import org.apache.log.LogKit;
import org.apache.log.Logger;

import org.apache.mailet.*;
import org.apache.james.core.*;
import org.apache.james.services.SpoolRepository;
import org.apache.james.services.MailStore;


/**
 * Implementation of a MailRepository on a FileSystem.
 *
 * Requires a configuration element in the .conf.xml file of the form:
 *  <repository destinationURL="file://path-to-root-dir-for-repository"
 *              type="MAIL"
 *              model="SYNCHRONOUS"/>
 * Requires a logger called MailRepository.
 * 
 * @version 1.0.0, 24/04/1999
 * @author  Federico Barbieri <scoobie@pop.systemy.it>
 * @author Charles Benett <charles@benett1.demon.co.uk>
 */
public class AvalonSpoolRepository extends AvalonMailRepository implements SpoolRepository {

    private static final String TYPE = "MAIL";
    private final static boolean        LOG        = true;
    private final static boolean        DEBUG      = LOG && false;
    private Logger logger =  LogKit.getLoggerFor("MailRepository");
    private Store store;
    private Store.StreamRepository sr;
    private Store.ObjectRepository or;
    private MailStore mailstore;
    //  private String path;
    //   private String name;
    private String destination;
    //  private String model;
    private Lock lock;

    public AvalonSpoolRepository() {
	super();
    }

 

    public synchronized String accept() {

        while (true) {
            for(Iterator it = or.list(); it.hasNext(); ) {
                Object o = it.next();
                if (lock.lock(o)) {
                    return o.toString();
                }
            }
            try {
                wait();
            } catch (InterruptedException ignored) {
            }
        }
    }

    public synchronized String accept(long delay) {
        while (true) {
            long youngest = 0;
            for (Iterator it = list(); it.hasNext(); ) {
                String s = it.next().toString();
                if (lock.lock(s)) {
                    //We have a lock on this object... let's grab the message
                    //  and see if it's a valid time.
                    MailImpl mail = retrieve(s);
                    if (mail.getState().equals(Mail.ERROR)) {
                        //Test the time...
                        long timeToProcess = delay + mail.getLastUpdated().getTime();
                        if (System.currentTimeMillis() > timeToProcess) {
                            //We're ready to process this again
                            return s;
                        } else {
                            //We're not ready to process this.
                            if (youngest == 0 || youngest > timeToProcess) {
                                //Mark this as the next most likely possible mail to process
                                youngest = timeToProcess;
                            }
                        }
                    } else {
                        //This mail is good to go... return the key
                        return s;
                    }
                }
            }
            //We did not find any... let's wait for a certain amount of time
            try {
                if (youngest == 0) {
                    wait();
                } else {
                    wait(youngest - System.currentTimeMillis());
                }
            } catch (InterruptedException ignored) {
            }
        }
    }

}
