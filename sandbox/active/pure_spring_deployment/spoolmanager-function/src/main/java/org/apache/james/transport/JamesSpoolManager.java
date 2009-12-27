/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *   http://www.apache.org/licenses/LICENSE-2.0                 *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/



package org.apache.james.transport;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.annotation.Resource;

import org.apache.avalon.framework.container.ContainerUtil;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.commons.logging.Log;
import org.apache.james.api.kernel.LoaderService;
import org.apache.james.lifecycle.Configurable;
import org.apache.james.lifecycle.LogEnabled;
import org.apache.james.services.SpoolManager;
import org.apache.james.services.SpoolRepository;
import org.apache.mailet.Mail;
import org.apache.mailet.MailetConfig;
import org.apache.mailet.MatcherConfig;

/**
 * Manages the mail spool.  This class is responsible for retrieving
 * messages from the spool, directing messages to the appropriate
 * processor, and removing them from the spool when processing is
 * complete.
 *
 * @version CVS $Revision$ $Date$
 */
public class JamesSpoolManager implements Runnable, SpoolManager, LogEnabled, Configurable {

    /**
     * The spool that this manager will process
     */
    private SpoolRepository spool;

    /**
     * The number of threads used to move mail through the spool.
     */
    private int numThreads;

    /**
     * The ThreadPool containing worker threads.
     *
     * This used to be used, but for threads that lived the entire
     * lifespan of the application.  Currently commented out.  In
     * the future, we could use a thread pool to run short-lived
     * workers, so that we have a smaller number of readers that
     * accept a message from the spool, and dispatch to a pool of
     * worker threads that process the message.
     */
    // private ThreadPool workerPool;

    /**
     * The ThreadManager from which the thread pool is obtained.
     */
    // private ThreadManager threadManager;

    /**
     * Number of active threads
     */
    private int numActive;

    /**
     * Spool threads are active
     */
    private boolean active;

    /**
     * Spool threads
     */
    private Collection<Thread> spoolThreads;

    /**
     * The mail processor 
     */
    private MailProcessor processorList;

    private Log logger;

    private LoaderService loaderService;

    private HierarchicalConfiguration config;

    /**
     * Set the SpoolRepository
     * 
     * @param spool the SpoolRepository
     */
    @Resource(name="spoolrepository")
    public void setSpoolRepository(SpoolRepository spool) {
        this.spool = spool;
    }


    @Resource(name="org.apache.james.LoaderService")
    public final void setLoaderService(LoaderService service) {
        this.loaderService = service;
    }
    
    public final void setLog(Log logger) {
        this.logger = logger;
    }
    
    
    
    public void configure(HierarchicalConfiguration config) throws ConfigurationException {
        numThreads = config.getInt("threads",1);

        String processorClass = config.getString("processorClass","org.apache.james.transport.StateAwareProcessorList");
        try {
            Class<?> cObj = Thread.currentThread().getContextClassLoader().loadClass(processorClass);
            processorList = (MailProcessor) loaderService.load(cObj);
        } catch (Exception e1) {
            logger.error("Unable to instantiate spoolmanager processor: "+processorClass, e1);
            throw new ConfigurationException("Instantiation exception: "+processorClass, e1);
        }

    }

    /**
     * Initialises the spool manager.
     */
    @PostConstruct
    public void init() throws Exception {
        logger.info("JamesSpoolManager init...");
        ContainerUtil.initialize(processorList);

        if (logger.isInfoEnabled()) {
            StringBuffer infoBuffer =
                new StringBuffer(64)
                    .append("Spooler Manager uses ")
                    .append(numThreads)
                    .append(" Thread(s)");
            logger.info(infoBuffer.toString());
        }

        active = true;
        numActive = 0;
        spoolThreads = new java.util.ArrayList<Thread>(numThreads);
        for ( int i = 0 ; i < numThreads ; i++ ) {
            Thread reader = new Thread(this, "Spool Thread #" + i);
            spoolThreads.add(reader);
            reader.start();
        }
    }

    /**
     * This routinely checks the message spool for messages, and processes
     * them as necessary
     */
    public void run() {

        if (logger.isInfoEnabled()) {
            logger.info("Run JamesSpoolManager: "
                             + Thread.currentThread().getName());
            logger.info("Spool=" + spool.getClass().getName());
        }

        numActive++;
        while(active) {
            String key = null;
            try {
                Mail mail = (Mail)spool.accept();
                key = mail.getName();
                if (logger.isDebugEnabled()) {
                    StringBuffer debugBuffer =
                        new StringBuffer(64)
                                .append("==== Begin processing mail ")
                                .append(mail.getName())
                                .append("====");
                    logger.debug(debugBuffer.toString());
                }

                processorList.service(mail);

                // Only remove an email from the spool is processing is
                // complete, or if it has no recipients
                if ((Mail.GHOST.equals(mail.getState())) ||
                    (mail.getRecipients() == null) ||
                    (mail.getRecipients().size() == 0)) {
                    ContainerUtil.dispose(mail);
                    spool.remove(key);
                    if (logger.isDebugEnabled()) {
                        StringBuffer debugBuffer =
                            new StringBuffer(64)
                                    .append("==== Removed from spool mail ")
                                    .append(key)
                                    .append("====");
                        logger.debug(debugBuffer.toString());
                    }
                }
                else {
                    // spool.remove() has a side-effect!  It unlocks the
                    // message so that other threads can work on it!  If
                    // we don't remove it, we must unlock it!
                    spool.store(mail);
                    ContainerUtil.dispose(mail);
                    spool.unlock(key);
                    // Do not notify: we simply updated the current mail
                    // and we are able to reprocess it now.
                }
                mail = null;
            } catch (InterruptedException ie) {
                logger.info("Interrupted JamesSpoolManager: " + Thread.currentThread().getName());
            } catch (Throwable e) {
                if (logger.isErrorEnabled()) {
                    logger.error("Exception processing " + key + " in JamesSpoolManager.run "
                                      + e.getMessage(), e);
                }
                /* Move the mail to ERROR state?  If we do, it could be
                 * deleted if an error occurs in the ERROR processor.
                 * Perhaps the answer is to resolve that issue by
                 * having a special state for messages that are not to
                 * be processed, but aren't to be deleted?  The message
                 * would already be in the spool, but would not be
                 * touched again.
                if (mail != null) {
                    try {
                        mail.setState(Mail.ERROR);
                        spool.store(mail);
                    }
                }
                */
            }
        }
        if (logger.isInfoEnabled()){
            logger.info("Stop JamesSpoolManager: " + Thread.currentThread().getName());
        }
        numActive--;
    }

    /**
     * The dispose operation is called at the end of a components lifecycle.
     * Instances of this class use this method to release and destroy any
     * resources that they own.
     *
     * This implementation shuts down the LinearProcessors managed by this
     * JamesSpoolManager
     * 
     * @see org.apache.avalon.framework.activity.Disposable#dispose()
     */
    @PreDestroy
    public void dispose() {
        logger.info("JamesSpoolManager dispose...");
        active = false; // shutdown the threads
        for (Thread thread: spoolThreads) {
            thread.interrupt(); // interrupt any waiting accept() calls.
        }

        long stop = System.currentTimeMillis() + 60000;
        // give the spooler threads one minute to terminate gracefully
        while (numActive != 0 && stop > System.currentTimeMillis()) {
            try {
                Thread.sleep(1000);
            } catch (Exception ignored) {}
        }
        logger.info("JamesSpoolManager thread shutdown completed.");

        ContainerUtil.dispose(processorList);
    }

    /**
     * @see org.apache.james.services.SpoolManager#getProcessorNames()
     */
    public String[] getProcessorNames() {
        if (!(processorList instanceof ProcessorList)) {
            return new String[0];  
        }
        String[] processorNames = ((ProcessorList) processorList).getProcessorNames();
        return processorNames;
    }

    /**
     * @see org.apache.james.services.SpoolManager#getMailetConfigs(java.lang.String)
     */
    public List<MailetConfig> getMailetConfigs(String processorName) {
        MailetContainer mailetContainer = getMailetContainerByName(processorName);
        if (mailetContainer == null) return new ArrayList<MailetConfig>();
        return mailetContainer.getMailetConfigs();
    }

    /**
     * @see org.apache.james.services.SpoolManager#getMatcherConfigs(java.lang.String)
     */
    public List<MatcherConfig> getMatcherConfigs(String processorName) {
        MailetContainer mailetContainer = getMailetContainerByName(processorName);
        if (mailetContainer == null) return new ArrayList<MatcherConfig>();
        return mailetContainer.getMatcherConfigs();
    }

    private MailetContainer getMailetContainerByName(String processorName) {
        if (!(processorList instanceof ProcessorList)) return null;
        
        MailProcessor processor = ((ProcessorList) processorList).getProcessor(processorName);
        if (!(processor instanceof MailetContainer)) return null;
        // TODO: decide, if we have to visit all sub-processors for being ProcessorLists 
        // on their very own and collecting the processor names deeply.
        return (MailetContainer)processor;
    }
}
