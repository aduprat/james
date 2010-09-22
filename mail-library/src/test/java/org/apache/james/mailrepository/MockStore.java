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



package org.apache.james.mailrepository;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationUtils;
import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.james.mailrepository.MailStore;

import java.util.HashMap;
import java.util.Map;

public class MockStore implements MailStore {

    Map m_storedObjectMap = new HashMap();

    public void add(Object key, Object obj) {
        m_storedObjectMap.put(key, obj);
    }
    
    public Object select(HierarchicalConfiguration object) throws StoreException {
        Object result = get(object);
        return result;
    }

    private Object get(Object object) {
        Object key = extractKeyObject(object);
        System.out.println(key);
        return m_storedObjectMap.get(key);
    }

    private Object extractKeyObject(Object object) {
        if (object instanceof Configuration) {
            Configuration repConf = (Configuration) object;
            System.out.println(ConfigurationUtils.toString(repConf));

            String type = repConf.getString("[@type]");
            String prefix = "";
            if (!"MAIL".equals(type) && !"SPOOL".equals(type)) {
                prefix = type + ".";
            }
            String attribute = repConf.getString("[@destinationURL]");
            String[] strings = attribute.split("/");
            if (strings.length > 0) {
                return prefix + strings[strings.length - 1];
            }

        }
        return object;
    }

    public boolean isSelectable(Object object) {
        return get(object) != null;
    }
}
