/***********************************************************************
 * Copyright (c) 2000-2005 The Apache Software Foundation.             *
 * All rights reserved.                                                *
 * ------------------------------------------------------------------- *
 * Licensed under the Apache License, Version 2.0 (the "License"); you *
 * may not use this file except in compliance with the License. You    *
 * may obtain a copy of the License at:                                *
 *                                                                     *
 *     http://www.apache.org/licenses/LICENSE-2.0                      *
 *                                                                     *
 * Unless required by applicable law or agreed to in writing, software *
 * distributed under the License is distributed on an "AS IS" BASIS,   *
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or     *
 * implied.  See the License for the specific language governing       *
 * permissions and limitations under the License.                      *
 ***********************************************************************/


package org.apache.james.test.mock.avalon;

import org.apache.avalon.cornerstone.services.store.Store;
import org.apache.avalon.framework.configuration.Configuration;
import org.apache.avalon.framework.configuration.ConfigurationException;
import org.apache.avalon.framework.service.ServiceException;

import java.util.HashMap;
import java.util.Map;

public class MockStore implements Store {

    Map m_storedObjectMap = new HashMap();

    public void add(Object key, Object obj) {
        m_storedObjectMap.put(key, obj);
    }
    
    public Object select(Object object) throws ServiceException {
        if (object instanceof Configuration) {
            Configuration repConf = (Configuration) object;
            try {
                object = repConf.getAttribute("destinationURL");
            } catch (ConfigurationException e) {
                throw new RuntimeException("test failed");
            }
        }
        Object result = m_storedObjectMap.get(object);
        return result;
    }

    public boolean isSelectable(Object object) {
        return m_storedObjectMap.get(object) != null; 
    }

    public void release(Object object) {
        //trivial implementation
    }
}
