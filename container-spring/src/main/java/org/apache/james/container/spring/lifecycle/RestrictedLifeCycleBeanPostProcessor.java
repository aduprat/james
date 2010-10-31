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
package org.apache.james.container.spring.lifecycle;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

/**
 * {@link AbstractLifeCycleBeanPostProcessor} sub-class which will only try to apply LifeCycle callbacks on beans which are registered on the {@link ApplicationContext}.
 * 
 *
 * @param <T>
 */
public abstract class RestrictedLifeCycleBeanPostProcessor<T> extends AbstractLifeCycleBeanPostProcessor<T> implements ApplicationContextAware{

    private ApplicationContext context;

    @Override
    protected final void executeLifecycleMethodAfterInit(T bean, String beanname) throws Exception {
        // check if the bean is registered in the context. If not it was create by the InstanceFactory and so there is no need to execute the callback
        if (context.containsBeanDefinition(beanname)) {
            executeLifecycleMethodAfterInitChecked(bean, beanname);
        }
    }

    @Override
    protected final void executeLifecycleMethodBeforeInit(T bean, String beanname) throws Exception {
        // check if the bean is registered in the context. If not it was create by the InstanceFactory and so there is no need to execute the callback
        if (context.containsBeanDefinition(beanname)) {
            executeLifecycleMethodBeforeInitChecked(bean, beanname);
        }
    }

    /*
     * (non-Javadoc)
     * @see org.springframework.context.ApplicationContextAware#setApplicationContext(org.springframework.context.ApplicationContext)
     */
    public void setApplicationContext(ApplicationContext context) throws BeansException {
        this.context = context;
    }

    /**
     * Execute method on bean which is registered in the {@link ApplicationContext}. Subclasses should override this if needed
     * 
     * @param bean
     * @param beanname
     * @throws Exception
     */
    protected void executeLifecycleMethodAfterInitChecked(T bean, String beanname) throws Exception {
        
    }
    
    /**
     * Execute method on bean which is registered in the {@link ApplicationContext}. Subclasses should override this if needed
     * 
     * @param bean
     * @param beanname
     * @throws Exception
     */
    protected void executeLifecycleMethodBeforeInitChecked(T bean, String beanname) throws Exception {
        
    }

}