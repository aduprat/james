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


package org.apache.james.container.spring.bean.factory.mailetcontainer;
import javax.mail.MessagingException;

import org.apache.james.mailetcontainer.api.MailetLoader;
import org.apache.mailet.Mailet;
import org.apache.mailet.MailetConfig;
import org.apache.mailet.MailetException;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
/**
 * Loads Mailets for use inside James by using the {@link ConfigurableListableBeanFactory} of spring.
 * 
 * The Mailets are not registered in the factory after loading them!
 *
 */
public class MailetLoaderBeanFactory implements MailetLoader, BeanFactoryAware {
    
    private ConfigurableListableBeanFactory beanFactory;
    

    /*
     * (non-Javadoc)
     * @see org.apache.james.mailetcontainer.api.MailetLoader#getMailet(org.apache.mailet.MailetConfig)
     */
    @SuppressWarnings("unchecked")
    public Mailet getMailet(final MailetConfig config) throws MessagingException {
        String mailetName = config.getMailetName();

        try {
            String fullName;
            if (mailetName.indexOf(".") < 1) {
                fullName = "org.apache.james.transport.mailets." + mailetName;
            } else {
                fullName = mailetName;
            }
            
            // Use the classloader which is used for bean instance stuff
            Class clazz = beanFactory.getBeanClassLoader().loadClass(fullName);
            final Mailet mailet = (Mailet) beanFactory.createBean(clazz);

            // init the mailet
            mailet.init(config);

            
            return mailet;
            
        } catch (MessagingException me) {
            throw me;
        } catch (Exception e) {
            throw loadFailed(mailetName, e);
        }
    }

    /**
     * Constructs an appropriate exception with an appropriate message.
     * @param name not null
     * @param e not null
     * @return not null
     */
    protected MailetException loadFailed(String name, Exception e) {
        final StringBuilder builder =
            new StringBuilder(128).append("Could not load ").append("mailet")
                .append(" (").append(name).append(")");
        final MailetException mailetException = new MailetException(builder.toString(), e);
        return mailetException;
    }
    
    /*
     * (non-Javadoc)
     * @see org.springframework.beans.factory.BeanFactoryAware#setBeanFactory(org.springframework.beans.factory.BeanFactory)
     */
    public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
        this.beanFactory = (ConfigurableListableBeanFactory) beanFactory;
    }
}