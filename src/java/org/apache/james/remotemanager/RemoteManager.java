/*
 * Copyright (C) The Apache Software Foundation. All rights reserved.
 *
 * This software is published under the terms of the Apache Software License
 * version 1.1, a copy of which has been included with this distribution in
 * the LICENSE file.
 */
package org.apache.james.remotemanager;

import org.apache.avalon.cornerstone.services.connection.AbstractService;
import org.apache.avalon.cornerstone.services.connection.ConnectionHandlerFactory;
import org.apache.avalon.cornerstone.services.connection.DefaultHandlerFactory;
import org.apache.avalon.framework.configuration.Configuration;
import org.apache.avalon.framework.configuration.ConfigurationException;
import org.apache.avalon.framework.component.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Provides a really rude network interface to administer James.
 * Allow to add accounts.
 * TODO: -improve protocol
 *       -add remove user
 *       -much more...
 * @version 1.0.0, 24/04/1999
 * @author  Federico Barbieri <scoobie@pop.systemy.it>
 * @author <a href="mailto:donaldp@apache.org">Peter Donald</a>
 */
public class RemoteManager
    extends AbstractService implements Component {

    protected ConnectionHandlerFactory createFactory()
    {
        return new DefaultHandlerFactory( RemoteManagerHandler.class );
    }

    public void configure( final Configuration configuration )
        throws ConfigurationException {

        m_port = configuration.getChild( "port" ).getValueAsInteger( 4554 );

        try
        {
            final String bindAddress = configuration.getChild( "bind" ).getValue( null );
            if( null != bindAddress )
            {
                m_bindTo = InetAddress.getByName( bindAddress );
            }
        }
        catch( final UnknownHostException unhe )
        {
            throw new ConfigurationException( "Malformed bind parameter", unhe );
        }

        final boolean useTLS = configuration.getChild( "useTLS" ).getValueAsBoolean( false );
        if( useTLS ) {
            m_serverSocketType = "ssl";
        }

        super.configure( configuration.getChild( "handler" ) );
    }

    public void initialize() throws Exception {
        getLogger().info( "RemoteManager init..." );
        StringBuffer infoBuffer =
            new StringBuffer(64)
                    .append("RemoteManager using ")
                    .append(m_serverSocketType)
                    .append(" on port ")
                    .append(m_port);
        getLogger().info(infoBuffer.toString());
        super.initialize();
        getLogger().info("RemoteManager ...init end");
    }

    public void dispose()
    {
        getLogger().info( "RemoteManager dispose..." );
        getLogger().info( "RemoteManager dispose..." + m_connectionName);
        super.dispose();
       
        // This is needed to make sure that sockets are released promptly on Windows 2000
	    System.gc();
	
    	getLogger().info( "RemoteManager ...dispose end" );
    }
}
