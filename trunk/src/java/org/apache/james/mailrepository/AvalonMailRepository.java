/*
 * Copyright (C) The Apache Software Foundation. All rights reserved.
 *
 * This software is published under the terms of the Apache Software License
 * version 1.1, a copy of which has been included with this distribution in
 * the LICENSE file.
 */
package org.apache.james.mailrepository;

import org.apache.avalon.cornerstone.services.store.ObjectRepository;
import org.apache.avalon.cornerstone.services.store.Store;
import org.apache.avalon.cornerstone.services.store.StreamRepository;
import org.apache.avalon.framework.activity.Initializable;
import org.apache.avalon.framework.component.Component;
import org.apache.avalon.framework.component.ComponentException;
import org.apache.avalon.framework.component.ComponentManager;
import org.apache.avalon.framework.component.Composable;
import org.apache.avalon.framework.configuration.Configurable;
import org.apache.avalon.framework.configuration.Configuration;
import org.apache.avalon.framework.configuration.ConfigurationException;
import org.apache.avalon.framework.configuration.DefaultConfiguration;
import org.apache.avalon.framework.logger.AbstractLogEnabled;
import org.apache.james.core.MailImpl;
import org.apache.james.core.MimeMessageWrapper;
import org.apache.james.services.MailRepository;
import org.apache.james.services.MailStore;
import org.apache.james.util.Lock;

import java.io.OutputStream;
import java.util.*;

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
public class AvalonMailRepository
    extends AbstractLogEnabled
    implements MailRepository, Component, Configurable, Composable, Initializable {

    private Lock lock;
    protected final static boolean DEEP_DEBUG = false;
    private static final String TYPE = "MAIL";
    private Store store;
    private StreamRepository sr;
    private ObjectRepository or;
    private MailStore mailstore;
    private String destination;
    private Set keys;

    public void configure(Configuration conf) throws ConfigurationException {
        destination = conf.getAttribute("destinationURL");
        if (getLogger().isDebugEnabled()) {
            getLogger().debug("AvalonMailRepository.destinationURL: " + destination);
        }
        String checkType = conf.getAttribute("type");
        if (! (checkType.equals("MAIL") || checkType.equals("SPOOL")) ) {
            String exceptionString = "Attempt to configure AvalonMailRepository as " +
                                     checkType;
            if (getLogger().isWarnEnabled()) {
                getLogger().warn(exceptionString);
            }
            throw new ConfigurationException(exceptionString);
        }
        // ignore model
    }

    public void compose( final ComponentManager componentManager )
            throws ComponentException {
        store = (Store)componentManager.
            lookup( "org.apache.avalon.cornerstone.services.store.Store" );
    }

    public void initialize()
            throws Exception {
        try {
            //prepare Configurations for object and stream repositories
            DefaultConfiguration objectConfiguration
                = new DefaultConfiguration( "repository",
                                            "generated:AvalonFileRepository.compose()" );

            objectConfiguration.setAttribute("destinationURL", destination);
            objectConfiguration.setAttribute("type", "OBJECT");
            objectConfiguration.setAttribute("model", "SYNCHRONOUS");

            DefaultConfiguration streamConfiguration
                = new DefaultConfiguration( "repository",
                                            "generated:AvalonFileRepository.compose()" );

            streamConfiguration.setAttribute( "destinationURL", destination );
            streamConfiguration.setAttribute( "type", "STREAM" );
            streamConfiguration.setAttribute( "model", "SYNCHRONOUS" );

            sr = (StreamRepository) store.select(streamConfiguration);
            or = (ObjectRepository) store.select(objectConfiguration);
            lock = new Lock();
            keys = Collections.synchronizedSet(new HashSet());


            //Finds non-matching pairs and deletes the extra files
            HashSet streamKeys = new HashSet();
            for (Iterator i = sr.list(); i.hasNext(); ) {
                streamKeys.add(i.next());
            }
            HashSet objectKeys = new HashSet();
            for (Iterator i = or.list(); i.hasNext(); ) {
                objectKeys.add(i.next());
            }

            Collection strandedStreams = (Collection)streamKeys.clone();
            strandedStreams.removeAll(objectKeys);
            for (Iterator i = strandedStreams.iterator(); i.hasNext(); ) {
                String key = (String)i.next();
                remove(key);
            }

            Collection strandedObjects = (Collection)objectKeys.clone();
            strandedObjects.removeAll(streamKeys);
            for (Iterator i = strandedObjects.iterator(); i.hasNext(); ) {
                String key = (String)i.next();
                remove(key);
            }

            //Next get a list from the object repository
            //  and use that for the list of keys
            keys.clear();
            for (Iterator i = or.list(); i.hasNext(); ) {
                keys.add(i.next());
            }
            if (getLogger().isDebugEnabled()) {
                StringBuffer logBuffer =
                    new StringBuffer(128)
                            .append(this.getClass().getName())
                            .append(" created in ")
                            .append(destination);
                getLogger().debug(logBuffer.toString());
            }
        } catch (Exception e) {
            final String message = "Failed to retrieve Store component:" + e.getMessage();
            getLogger().error( message, e );
            throw e;
        }
    }

    public boolean unlock(String key) {
        if (lock.unlock(key)) {
            synchronized (this) {
                notifyAll();
            }
            return true;
        } else {
            return false;
        }
    }

    public boolean lock(String key) {
        if (lock.lock(key)) {
            synchronized (this) {
                notifyAll();
            }
            return true;
        } else {
            return false;
        }
    }

    public void store(MailImpl mc) {
        try {
            String key = mc.getName();
            //Remember whether this key was locked
            boolean wasLocked = lock.isLocked(key);

            if (!wasLocked) {
                //If it wasn't locked, we want a lock during the store
                lock.lock(key);
            }
            try {
                if (!keys.contains(key)) {
                    keys.add(key);
                }
                boolean saveStream = true;

                if (mc.getMessage() instanceof MimeMessageWrapper) {
                    MimeMessageWrapper wrapper = (MimeMessageWrapper) mc.getMessage();
                    if (DEEP_DEBUG) {
                        System.out.println("Retrieving from: " + wrapper.getSourceId());
                        StringBuffer debugBuffer =
                            new StringBuffer(64)
                                    .append("Saving to:       ")
                                    .append(destination)
                                    .append("/")
                                    .append(mc.getName());
                        System.out.println(debugBuffer.toString());
                        System.out.println("Modified: " + wrapper.isModified());
                    }
                    StringBuffer destinationBuffer =
                        new StringBuffer(128)
                            .append(destination)
                            .append("/")
                            .append(mc.getName());
                    if (wrapper.getSourceId().equals(destinationBuffer.toString()) && !wrapper.isModified()) {
                        //We're trying to save to the same place, and it's not modified... we shouldn't save.
                        //More importantly, if we try to save, we will create a 0-byte file since we're
                        //retrying to retrieve from a file we'll be overwriting.
                        saveStream = false;
                    }
                }
                if (saveStream) {
                    OutputStream out = null;
                    try {
                        out = sr.put(key);
                        mc.writeMessageTo(out);
                    } finally {
                        out.close();
                    }
                }
                //Always save the header information
                or.put(key, mc);
            } finally {
                if (!wasLocked) {
                    //If it wasn't locked, we need to now unlock
                    lock.unlock(key);
                }
            }

            if ((DEEP_DEBUG) && (getLogger().isDebugEnabled())) {
                StringBuffer logBuffer =
                    new StringBuffer(64)
                            .append("Mail ")
                            .append(key)
                            .append(" stored.");
                getLogger().debug(logBuffer.toString());
            }

            synchronized (this) {
                notifyAll();
            }
        } catch (Exception e) {
            getLogger().error("Exception storing mail: " + e);
            e.printStackTrace();
            throw new RuntimeException("Exception caught while storing Message Container: " + e);
        }
    }

    public MailImpl retrieve(String key) {
        if ((DEEP_DEBUG) && (getLogger().isDebugEnabled())) {
            getLogger().debug("Retrieving mail: " + key);
        }
        try {
            MailImpl mc = null;
            try {
                mc = (MailImpl) or.get(key);
            } catch (RuntimeException re) {
                StringBuffer exceptionBuffer =
                    new StringBuffer(128)
                            .append("Exception retrieving mail: ")
                            .append(re.toString())
                            .append(", so we're deleting it... good riddance!");
                getLogger().error(exceptionBuffer.toString());
                remove(key);
                return null;
            }
            MimeMessageAvalonSource source = new MimeMessageAvalonSource(sr, destination, key);
            mc.setMessage(new MimeMessageWrapper(source));

            return mc;
        } catch (Exception me) {
            getLogger().error("Exception retrieving mail: " + me);
            throw new RuntimeException("Exception while retrieving mail: " + me.getMessage());
        }
    }

    public void remove(MailImpl mail) {
        remove(mail.getName());
    }

    public void remove(String key) {
        if (lock(key)) {
            try {
                keys.remove(key);
                sr.remove(key);
                or.remove(key);
            } finally {
                unlock(key);
            }
        } else {
            StringBuffer exceptionBuffer =
                new StringBuffer(64)
                        .append("Cannot lock ")
                        .append(key)
                        .append(" to remove it");
            throw new RuntimeException(exceptionBuffer.toString());
        }
    }

    public Iterator list() {
//  Fix ConcurrentModificationException by cloning the keyset before getting an iterator
//        return keys.iterator();
        final HashSet clone = new HashSet();
        clone.addAll( keys );
        return clone.iterator();
    }
}
