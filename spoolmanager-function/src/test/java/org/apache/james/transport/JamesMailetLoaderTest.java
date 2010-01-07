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

import junit.framework.TestCase;
import org.apache.avalon.framework.configuration.DefaultConfiguration;
import org.apache.commons.configuration.DefaultConfigurationBuilder;
import org.apache.commons.logging.impl.SimpleLog;
import org.apache.james.api.kernel.mock.FakeLoader;
import org.apache.james.test.util.Util;
import org.apache.james.transport.mailets.MailetLoaderTestMailet;
import org.apache.james.util.ConfigurationAdapter;
import org.apache.mailet.Mailet;
import org.apache.mailet.MailetConfig;

import javax.mail.MessagingException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class JamesMailetLoaderTest extends TestCase {
    private JamesMailetLoader m_jamesMailetLoader  = new JamesMailetLoader();
    private JamesMailetLoaderConfiguration m_conf = new JamesMailetLoaderConfiguration();

    private class JamesMailetLoaderConfiguration extends DefaultConfiguration {
        private List<String> m_packageNames = new ArrayList<String>();
        
        public JamesMailetLoaderConfiguration() {
            super("mailetpackages");
        }

        public void init() {
            for (Iterator<String> iterator = m_packageNames.iterator(); iterator.hasNext();) {
                String packageName = (String) iterator.next();
                addChild(Util.getValuedConfiguration("mailetpackage", packageName));
            }
        }

        public void addStandardPackages() {
            add("org.apache.james.transport.mailets");
            add("org.apache.james.transport.mailets.smime");
        }

        public void add(String packageName) {
            m_packageNames.add(packageName);
        }

    }

    private void setUpLoader() throws Exception {
        m_conf.init();
        m_jamesMailetLoader.setLog(new SimpleLog("Test"));
        m_jamesMailetLoader.configure(new ConfigurationAdapter(m_conf));
        m_jamesMailetLoader.setLoaderService(new FakeLoader());
    }

    private void assetIsNullMailet(Mailet mailet) {
        assertNotNull("Null mailet loaded", mailet);
        assertTrue("Null mailet is expected class", mailet instanceof org.apache.james.transport.mailets.Null);
    }


    public void testUsingEmtpyConfig() throws Exception {
        setUpLoader();
    }

    public void testFullQualifiedUsingFakeConfig() throws Exception {
        m_conf.add("none.existing.package"); // has to be here so the Loader won't choke
        setUpLoader();

        Mailet mailet = m_jamesMailetLoader.getMailet("org.apache.james.transport.mailets.Null", null);
        assetIsNullMailet(mailet);
    }

    public void testStandardMailets() throws Exception {
        m_conf.addStandardPackages();
        setUpLoader();

        // use standard package
        Mailet mailetNull1 = m_jamesMailetLoader.getMailet("Null", null);
        assetIsNullMailet(mailetNull1);

        // use full qualified package in parallel
        Mailet mailetNull2 = m_jamesMailetLoader.getMailet("org.apache.james.transport.mailets.Null", null);
        assetIsNullMailet(mailetNull2);

    }

    public void testTestMailets() throws Exception {
        m_conf.addStandardPackages();
        setUpLoader();

        checkTestMailet("MailetLoaderTestMailet");
        
        checkTestMailet("MailetLoaderTestSMIMEMailet");

    }

    private void checkTestMailet(String mailetName) throws MessagingException {
        // use standard package
        DefaultConfigurationBuilder configuration = new DefaultConfigurationBuilder();
        configuration.addProperty("testMailetKey", "testMailetValue");

        Mailet mailet = m_jamesMailetLoader.getMailet(mailetName, configuration);
        assertTrue("MailetLoaderTestMailet mailet is expected class", mailet instanceof MailetLoaderTestMailet);
        MailetLoaderTestMailet mailetLoaderTestMailet = ((MailetLoaderTestMailet) mailet);
        assertTrue("init was called by loader", mailetLoaderTestMailet.assertInitCalled());
        MailetConfig mailetConfig = mailetLoaderTestMailet.getMailetConfig();
        assertEquals("init was called w/ right config", "testMailetValue", mailetConfig.getInitParameter("testMailetKey"));
    }

}