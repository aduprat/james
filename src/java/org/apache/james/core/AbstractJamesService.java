/*
 * Copyright (C) The Apache Software Foundation. All rights reserved.
 *
 * This software is published under the terms of the Apache Software License
 * version 1.1, a copy of which has been included with this distribution in
 * the LICENSE file.
 */
package org.apache.james.core;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.UnknownHostException;

import org.apache.avalon.cornerstone.services.connection.AbstractHandlerFactory;
import org.apache.avalon.cornerstone.services.connection.ConnectionHandler;
import org.apache.avalon.cornerstone.services.connection.ConnectionHandlerFactory;
import org.apache.avalon.cornerstone.services.connection.ConnectionManager;
import org.apache.avalon.cornerstone.services.sockets.ServerSocketFactory;
import org.apache.avalon.cornerstone.services.sockets.SocketManager;
import org.apache.avalon.cornerstone.services.threads.ThreadManager;
import org.apache.avalon.excalibur.thread.ThreadPool;
import org.apache.avalon.framework.activity.Disposable;
import org.apache.avalon.framework.activity.Initializable;
import org.apache.avalon.framework.component.Component;
import org.apache.avalon.framework.component.ComponentException;
import org.apache.avalon.framework.component.ComponentManager;
import org.apache.avalon.framework.component.Composable;
import org.apache.avalon.framework.configuration.Configurable;
import org.apache.avalon.framework.configuration.Configuration;
import org.apache.avalon.framework.configuration.ConfigurationException;
import org.apache.avalon.framework.logger.LogEnabled;

import org.apache.james.util.connection.SimpleConnectionManager;
import org.apache.james.util.watchdog.ThreadPerWatchdogFactory;
import org.apache.james.util.watchdog.WatchdogFactory;

/**
 * Server which creates connection handlers. All new James service must inherit
 * from this abstract implementation.
 *
 * @author <a href="mailto:myfam@surfeu.fi">Andrei Ivanov</a>
 * @author <a href="farsight@alum.mit.edu">Peter M. Goldstein</a>
 */
public abstract class AbstractJamesService extends AbstractHandlerFactory
    implements Component,Composable,Configurable,Disposable,Initializable,
        ConnectionHandlerFactory {
    /**
     * The default value for the connection timeout.
     */
    protected final static int DEFAULT_TIMEOUT = 5*60*1000;

    /**
     * The name of the parameter defining the connection timeout.
     */
    protected final static String TIMEOUT_NAME = "connectiontimeout";

    /**
     * The name of the parameter defining the service hello name.
     */
    public final static String HELLO_NAME = "helloName";

    /**
     * Network interface to which the service will bind.  If not set, the
     * server binds to all available interfaces.
     */
    protected InetAddress bindTo = null;

    /**
     * The maximum number of connections allowed for this service.
     */
    protected Integer connectionLimit;
    /*
     * The server socket associated with this service
     */
    protected ServerSocket serverSocket;

    /**
     * The name of the connection used by this service.  We need to track this
     * so we can tell the ConnectionManager which service to disconnect upon
     * shutdown.
     */
    protected String connectionName;

    /**
     * The hello name for the service.
     */
    protected String helloName;

    /**
     * The server socket type used to generate connections for this server.
     */
    protected String serverSocketType = "plain";

    /**
     * The name of the thread group to be used by this service for  generating
     * connections
     */
    protected String threadGroup;

    /**
     * The thread pool used by this service that holds the threads that service
     * the client connections.
     */
    protected ThreadPool threadPool = null;

    /**
     * The port on which this service will be made available.
     */
    protected int port = -1;

    /**
     * The connection idle timeout.  Used primarily to prevent server problems
     * from hanging a connection.
     */
    protected int timeout;

    /**
     * The component manager used by this service.
     */
    private ComponentManager compMgr;

    /**
     * The ConnectionManager that spawns and manages service connections.
     */
    private ConnectionManager connectionManager;

    /**
     * Whether this service is enabled.
     */
    private volatile boolean enabled;

    /**
     * This method returns the type of service provided by this server. This
     * should be invariant over the life of the class. Subclasses may override
     * this implementation.  This implementation parses the complete class
     * name and returns the undecorated class name.
     *
     * @return description of this server
     */
    public String getServiceType() {
        String name = getClass().getName();
        int p       = name.lastIndexOf(".");
        if((p > 0)&&(p < (name.length()-2))) {
            name = name.substring(p+1);
        }
        return name;
    }

    /**
     * @see org.apache.avalon.framework.component.Composable#compose(ComponentManager)
     */
    public void compose(ComponentManager comp) throws ComponentException {
        super.compose(comp);
        compMgr               = comp;
        connectionManager =
            (ConnectionManager)compMgr.lookup(ConnectionManager.ROLE);
    }

    /**
     * @see org.apache.avalon.framework.configuration.Configurable#configure(Configuration)
     */
    public void configure(Configuration conf) throws ConfigurationException {
        enabled               = conf.getAttributeAsBoolean("enabled",true);
        if(!enabled) {
            getLogger().info(getServiceType()+" disabled by configuration");
            return;
        }

        Configuration handlerConfiguration = conf.getChild("handler");

        // Send the handler subconfiguration to the super class.  This 
        // ensures that the handler config is passed to the handlers.
        //
        // TODO: This should be rationalized.  The handler element of the
        //       server configuration doesn't really make a whole lot of 
        //       sense.  We should modify the config to get rid of it.
        //       Keeping it for now to maintain backwards compatibility.
        super.configure(handlerConfiguration);

        port = conf.getChild("port").getValueAsInteger(getDefaultPort());

        Configuration serverSocketTypeConf =
            conf.getChild("serverSocketType",false);
        String confSocketType = null;
        if(serverSocketTypeConf != null) {
            confSocketType = serverSocketTypeConf.getValue();
        }

        if(confSocketType == null) {
            // Only load the useTLS parameter if a specific socket type has not
            // been specified.  This maintains backwards compatibility while
            // allowing us to have more complex (i.e. multiple SSL configuration)
            // deployments
            final boolean useTLS =
                conf.getChild("useTLS").getValueAsBoolean(
                    isDefaultTLSEnabled());
            if(useTLS) {
                serverSocketType = "ssl";
            }
        } else {
            serverSocketType = confSocketType;
        }

        StringBuffer infoBuffer;
        threadGroup = conf.getChild("threadGroup").getValue(null);
        if(threadGroup != null) {
            infoBuffer =
                new StringBuffer(64).append(getServiceType())
                                    .append(" uses thread group: ").append(
                    threadGroup);
            getLogger().info(infoBuffer.toString());
        } else {
            getLogger().info(getServiceType()+" uses default thread group.");
        }

        try {
            final String bindAddress = conf.getChild("bind").getValue(null);
            if(null != bindAddress) {
                bindTo         = InetAddress.getByName(bindAddress);
                infoBuffer =
                    new StringBuffer(64).append(getServiceType())
                                        .append(" bound to: ").append(bindTo);
                getLogger().info(infoBuffer.toString());
            }
        } catch(final UnknownHostException unhe) {
            throw new ConfigurationException(
                "Malformed bind parameter in configuration of service "
                +getServiceType(),unhe);
        }

        String hostName        = null;
        try {
            hostName = InetAddress.getLocalHost().getHostName();
        } catch(UnknownHostException ue) {
            hostName = "localhost";
        }

        infoBuffer =
            new StringBuffer(64).append(getServiceType())
                                .append(" is running on: ").append(hostName);
        getLogger().info(infoBuffer.toString());

        Configuration helloConf = handlerConfiguration.getChild(HELLO_NAME);
        boolean autodetect      =
            helloConf.getAttributeAsBoolean("autodetect",true);
        if(autodetect) {
            helloName = hostName;
        } else {
            helloName = helloConf.getValue("localhost");
        }
        infoBuffer =
            new StringBuffer(64).append(getServiceType())
                                .append(" handler hello name is: ").append(
                helloName);
        getLogger().info(infoBuffer.toString());

        timeout =
            handlerConfiguration.getChild(TIMEOUT_NAME).getValueAsInteger(
                DEFAULT_TIMEOUT);

        infoBuffer =
            new StringBuffer(64).append(getServiceType())
                                .append(" handler connection timeout is: ")
                                .append(timeout);
        getLogger().info(infoBuffer.toString());

        final String location = "generated:"+getServiceType();

        if(connectionManager instanceof SimpleConnectionManager) {
            String connectionLimitString =
                conf.getChild("connectionLimit").getValue(null);
            if(connectionLimitString != null) {
                try {
                    connectionLimit = new Integer(connectionLimitString);
                } catch(NumberFormatException nfe) {
                    getLogger().error(
                        "Connection limit value is not properly formatted.",nfe);
                }
                if(connectionLimit.intValue() < 0) {
                    getLogger().error(
                        "Connection limit value cannot be less than zero.");
                    throw new ConfigurationException(
                        "Connection limit value cannot be less than zero.");
                }
            } else {
                connectionLimit =
                    new Integer(
                        ((SimpleConnectionManager)connectionManager)
                        .getMaximumNumberOfOpenConnections());
            }
            infoBuffer =
                new StringBuffer(128).append(getServiceType())
                                     .append(" will allow a maximum of ")
                                     .append(connectionLimit.intValue()).append(
                    " connections.");
            getLogger().info(infoBuffer.toString());
        }
    }

    /**
     * @see org.apache.avalon.framework.activity.Disposable#dispose()
     */
    public void dispose() {
        if(!isEnabled()) {
            return;
        }
        StringBuffer infoBuffer =
            new StringBuffer(64).append(getServiceType()).append(
                " dispose... ").append(connectionName);
        getLogger().debug(infoBuffer.toString());

        try {
            connectionManager.disconnect(connectionName,true);
        } catch(final Exception e) {
            StringBuffer warnBuffer =
                new StringBuffer(64).append("Error disconnecting ")
                                    .append(getServiceType()).append(": ");
            getLogger().warn(warnBuffer.toString(),e);
        }

        compMgr     = null;

        connectionManager     = null;
        threadPool            = null;

        // This is needed to make sure sockets are promptly closed on Windows 2000
        // TODO: Check this - shouldn't need to explicitly gc to force socket closure
        System.gc();

        getLogger().debug(getServiceType()+" ...dispose end");
    }

    /**
     * @see org.apache.avalon.framework.activity.Initializable#initialize()
     */
    public void initialize() throws Exception {
        if(!isEnabled()) {
            getLogger().info(getServiceType()+" Disabled");
            System.out.println(getServiceType()+" Disabled");
            return;
        }
        getLogger().debug(getServiceType()+" init...");

        SocketManager socketManager =
            (SocketManager)compMgr.lookup(SocketManager.ROLE);

        ThreadManager threadManager =
            (ThreadManager)compMgr.lookup(ThreadManager.ROLE);

        if(threadGroup != null) {
            threadPool = threadManager.getThreadPool(threadGroup);
        } else {
            threadPool = threadManager.getDefaultThreadPool();
        }

        ServerSocketFactory factory =
            socketManager.getServerSocketFactory(serverSocketType);
        ServerSocket serverSocket = factory.createServerSocket(port,5,bindTo);

        if(null == connectionName) {
            final StringBuffer sb = new StringBuffer();
            sb.append(serverSocketType);
            sb.append(':');
            sb.append(port);

            if(null != bindTo) {
                sb.append('/');
                sb.append(bindTo);
            }
            connectionName = sb.toString();
        }

        if(
            (connectionLimit != null)
                &&(connectionManager instanceof SimpleConnectionManager)) {
            if(null != threadPool) {
                ((SimpleConnectionManager)connectionManager).connect(
                    connectionName,serverSocket,this,threadPool,
                    connectionLimit.intValue());
            } else {
                ((SimpleConnectionManager)connectionManager).connect(
                    connectionName,serverSocket,this,connectionLimit.intValue()); // default pool
            }
        } else {
            if(null != threadPool) {
                connectionManager.connect(
                    connectionName,serverSocket,this,threadPool);
            } else {
                connectionManager.connect(connectionName,serverSocket,this); // default pool
            }
        }

        getLogger().debug(getServiceType()+" ...init end");

        StringBuffer logBuffer =
            new StringBuffer(64).append(getServiceType()).append(" started ")
                                .append(connectionName);
        String logString = logBuffer.toString();
        System.out.println(logString);
        getLogger().info(logString);
    }

    /**
     * Describes whether this service is enabled by configuration.
     *
     * @return is the service enabled.
     */
    protected final boolean isEnabled() {
        return enabled;
    }

    /**
     * This constructs the WatchdogFactory that will be used to guard against
     * runaway or stuck behavior.  Should only be called once by a subclass in
     * its initialize() method.
     *
     * @return the WatchdogFactory to be employed by subclasses.
     */
    protected WatchdogFactory getWatchdogFactory() {
        WatchdogFactory theWatchdogFactory = null;
        theWatchdogFactory = new ThreadPerWatchdogFactory(threadPool,timeout);
        if(theWatchdogFactory instanceof LogEnabled) {
            ((LogEnabled)theWatchdogFactory).enableLogging(getLogger());
        }
        return theWatchdogFactory;
    }

    /**
     * Overide this method to create actual instance of connection handler.
     *
     * @return the new ConnectionHandler
     *
     * @exception Exception if an error occurs
     */
    protected abstract ConnectionHandler newHandler() throws Exception;

    /**
     * Get the default port for this server type. It is strongly recommended
     * that subclasses of this class override this method to specify the
     * default port for their specific server type.
     *
     * @return the default port
     */
    protected int getDefaultPort() {
        return 0;
    }

    /**
     * Get whether TLS is enabled for this server's socket by default.
     *
     * @return the default port
     */
    protected boolean isDefaultTLSEnabled() {
        return false;
    }
}
