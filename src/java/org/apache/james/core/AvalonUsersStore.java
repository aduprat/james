/*
 * Copyright (C) The Apache Software Foundation. All rights reserved.
 *
 * This software is published under the terms of the Apache Software License
 * version 1.1, a copy of which has been included  with this distribution in
 * the LICENSE file.
 */
package org.apache.james.core;

import org.apache.avalon.framework.activity.Initializable;
import org.apache.avalon.framework.component.Component;
import org.apache.avalon.framework.component.ComponentException;
import org.apache.avalon.framework.component.ComponentManager;
import org.apache.avalon.framework.component.Composable;
import org.apache.avalon.framework.configuration.Configurable;
import org.apache.avalon.framework.configuration.Configuration;
import org.apache.avalon.framework.configuration.ConfigurationException;
import org.apache.avalon.framework.context.Context;
import org.apache.avalon.framework.context.ContextException;
import org.apache.avalon.framework.context.Contextualizable;
import org.apache.avalon.framework.logger.AbstractLogEnabled;
import org.apache.james.services.UsersRepository;
import org.apache.james.services.UsersStore;

import java.util.HashMap;
import java.util.Iterator;

/**
 * Provides a registry of user repositories.
 *
 * @author <a href="mailto:fede@apache.org">Federico Barbieri</a>
 */
public class AvalonUsersStore
    extends AbstractLogEnabled
    implements Component, Contextualizable, Composable, Configurable, Initializable, UsersStore {

    // A mapping of respository identifiers to actual repositories
    // This mapping is obtained from the component configuration
    private HashMap repositories;

    /**
     * The Avalon context used by the instance
     */
    protected Context                context;

    /**
     * The Avalon configuration used by the instance
     */
    protected Configuration          configuration;

    /**
     * The Avalon component manager used by the instance
     */
    protected ComponentManager       componentManager;

    /**
     * Pass the Context to the component.
     * This method is called after the setLogger()
     * method and before any other method.
     *
     * @param context the context
     * @throws ContextException if context is invalid
     */
    public void contextualize(final Context context)
            throws ContextException {
        this.context = context;
    }

    /**
     * Pass the <code>ComponentManager</code> to the <code>composer</code>.
     * The instance uses the specified <code>ComponentManager</code> to 
     * acquire the components it needs for execution.
     *
     * @param componentManager The <code>ComponentManager</code> which this
     *                <code>Composable</code> uses.
     * @throws ComponentException if an error occurs
     */
    public void compose( final ComponentManager componentManager )
        throws ComponentException {
        this.componentManager = componentManager;
    }

    /**
     * Pass the <code>Configuration</code> to the instance.
     *
     * @param configuration the class configurations.
     * @throws ConfigurationException if an error occurs
     */
    public void configure( final Configuration configuration )
        throws ConfigurationException {
        this.configuration = configuration;
    }

    /**
     * Initialize the component. Initialization includes
     * allocating any resources required throughout the
     * components lifecycle.
     *
     * @throws Exception if an error occurs
     */
    public void initialize()
        throws Exception {

        getLogger().info("AvalonUsersStore init...");
        repositories = new HashMap();

        Configuration[] repConfs = configuration.getChildren("repository");
        for ( int i = 0; i < repConfs.length; i++ )
        {
            Configuration repConf = repConfs[i];
            String repName = repConf.getAttribute("name");
            String repClass = repConf.getAttribute("class");

            if (getLogger().isDebugEnabled()) {
                getLogger().debug("Starting " + repClass);
            }
            UsersRepository rep = (UsersRepository) Class.forName(repClass).newInstance();

            setupLogger((Component)rep);

            if (rep instanceof Contextualizable) {
                ((Contextualizable) rep).contextualize(context);
            }
            if (rep instanceof Composable) {
                ((Composable) rep).compose( componentManager );
            }
            if (rep instanceof Configurable) {
                ((Configurable) rep).configure(repConf);
            }
            if (rep instanceof Initializable) {
                ((Initializable) rep).initialize();
            }
            repositories.put(repName, rep);
            if (getLogger().isInfoEnabled()) {
                StringBuffer logBuffer = 
                    new StringBuffer(64)
                            .append("UsersRepository ")
                            .append(repName)
                            .append(" started.");
                getLogger().info(logBuffer.toString());
            }
        }
        getLogger().info("AvalonUsersStore ...init");
    }


    /** 
     * Get the repository, if any, whose name corresponds to
     * the argument parameter
     *
     * @param name the name of the desired repository
     *
     * @return the UsersRepository corresponding to the name parameter
     */
    public UsersRepository getRepository(String name) {
        UsersRepository response = (UsersRepository) repositories.get(name);
        if ((response == null) && (getLogger().isWarnEnabled())) {
            getLogger().warn("No users repository called: " + name);
        }
        return response;
    }

    /** 
     * Yield an <code>Iterator</code> over the set of repository
     * names managed internally by this store.
     *
     * @return an Iterator over the set of repository names
     *         for this store
     */
    public Iterator getRepositoryNames() {
        return this.repositories.keySet().iterator();
    }
}
