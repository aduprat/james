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

package org.apache.james.mailboxmanager;

import java.util.ArrayList;
import java.util.Date;

import javax.mail.Flags;

import org.apache.james.mailboxmanager.MessageResult.FetchGroup;
import org.apache.james.mailboxmanager.impl.MessageResultImpl;
import org.apache.james.mailboxmanager.util.MessageResultUtils;
import org.jmock.MockObjectTestCase;

public class MessageResultImplIncludedResultsTest extends MockObjectTestCase {

    MessageResultImpl result;
    MessageResult.Content content;
    
    protected void setUp() throws Exception {
        super.setUp();
        result = new MessageResultImpl();
        content = (MessageResult.Content) mock(MessageResult.Content.class).proxy();
    }

    protected void tearDown() throws Exception {
        super.tearDown();
    }

    public void testShouldIncludedResultsWhenFlagsSet() throws Exception {
        result.setFlags(null);
        assertEquals(FetchGroup.MINIMAL, result.getIncludedResults().content());
        Flags flags = new Flags();
        result.setFlags(flags);
        assertEquals(FetchGroup.FLAGS, result.getIncludedResults().content());
        MessageResultImpl result = new MessageResultImpl(77, flags);
        assertEquals(FetchGroup.FLAGS, result.getIncludedResults().content());
        result = new MessageResultImpl(this.result);
        assertEquals(FetchGroup.FLAGS, result.getIncludedResults().content());
    }

    public void testShouldIncludedResultsWhenSizeSet() throws Exception {
        result.setSize(100);
        assertEquals(FetchGroup.SIZE, result.getIncludedResults().content());
        MessageResultImpl result = new MessageResultImpl(this.result);
        assertEquals(FetchGroup.SIZE, result.getIncludedResults().content());
    }

    public void testShouldIncludedResultsWhenInternalDateSet() throws Exception {
        result.setInternalDate(null);
        assertEquals(FetchGroup.MINIMAL, result.getIncludedResults().content());
        Date date = new Date();
        result.setInternalDate(date);
        assertEquals(FetchGroup.INTERNAL_DATE, result.getIncludedResults().content());
        result = new MessageResultImpl(this.result);
        assertEquals(FetchGroup.INTERNAL_DATE, result.getIncludedResults().content());
    }

    public void testShouldIncludedResultsWhenHeadersSet() throws Exception {
        result.setHeaders(null);
        assertEquals(FetchGroup.MINIMAL, result.getIncludedResults().content());
        result.setHeaders(new ArrayList());
        assertEquals(FetchGroup.HEADERS, result.getIncludedResults().content());
        result = new MessageResultImpl(this.result);
        assertEquals(FetchGroup.HEADERS, result.getIncludedResults().content());
    }
    
    public void testShouldIncludedResultsWhenFullMessageSet() throws Exception  {
        result.setFullContent(null);
        assertEquals(FetchGroup.MINIMAL, result.getIncludedResults().content());
        result.setFullContent(content);
        assertEquals(FetchGroup.FULL_CONTENT, result.getIncludedResults().content());
        result = new MessageResultImpl(this.result);
        assertEquals(FetchGroup.FULL_CONTENT, result.getIncludedResults().content());
    }

    public void testShouldIncludedResultsWhenMessageBodySet() throws Exception {
        result.setBody(null);
        assertEquals(FetchGroup.MINIMAL, result.getIncludedResults().content());
        result.setBody(content);
        assertEquals(FetchGroup.BODY_CONTENT, result.getIncludedResults().content());
        result = new MessageResultImpl(this.result);
        assertEquals(FetchGroup.BODY_CONTENT, result.getIncludedResults().content());
    }
    
    public void testShouldIncludedResultsWhenAllSet() {
        Flags flags = new Flags();
        result.setFlags(flags);
        assertEquals(FetchGroup.FLAGS, result.getIncludedResults().content());
        assertTrue(MessageResultUtils.isFlagsIncluded(result));
        result.setUid(99);
        assertEquals(FetchGroup.FLAGS, result.getIncludedResults().content());
        assertTrue(MessageResultUtils.isFlagsIncluded(result));
        result.setBody(content);
        assertEquals(FetchGroup.FLAGS | FetchGroup.BODY_CONTENT, result.getIncludedResults().content());
        assertTrue(MessageResultUtils.isFlagsIncluded(result));
        assertTrue(MessageResultUtils.isBodyContentIncluded(result));
        result.setFullContent(content);
        assertEquals(FetchGroup.FLAGS | 
                FetchGroup.BODY_CONTENT | FetchGroup.FULL_CONTENT, result.getIncludedResults().content());
        assertTrue(MessageResultUtils.isFlagsIncluded(result));
        assertTrue(MessageResultUtils.isBodyContentIncluded(result));
        assertTrue(MessageResultUtils.isFullContentIncluded(result));
        result.setHeaders(new ArrayList());
        assertEquals(FetchGroup.FLAGS | 
                FetchGroup.BODY_CONTENT | FetchGroup.FULL_CONTENT 
                | FetchGroup.HEADERS, result.getIncludedResults().content());
        assertTrue(MessageResultUtils.isFlagsIncluded(result));
        assertTrue(MessageResultUtils.isBodyContentIncluded(result));
        assertTrue(MessageResultUtils.isFullContentIncluded(result));
        assertTrue(MessageResultUtils.isHeadersIncluded(result));
        result.setInternalDate(new Date());
        assertEquals(FetchGroup.FLAGS | 
                FetchGroup.BODY_CONTENT | FetchGroup.FULL_CONTENT 
                | FetchGroup.HEADERS | FetchGroup.INTERNAL_DATE, result.getIncludedResults().content());
        assertTrue(MessageResultUtils.isFlagsIncluded(result));
        assertTrue(MessageResultUtils.isBodyContentIncluded(result));
        assertTrue(MessageResultUtils.isFullContentIncluded(result));
        assertTrue(MessageResultUtils.isHeadersIncluded(result));
        assertTrue(MessageResultUtils.isInternalDateIncluded(result));
        result.setSize(100);
        assertEquals(FetchGroup.FLAGS | 
                FetchGroup.BODY_CONTENT | FetchGroup.FULL_CONTENT 
                | FetchGroup.HEADERS | FetchGroup.INTERNAL_DATE
                | FetchGroup.SIZE, result.getIncludedResults().content());
        assertTrue(MessageResultUtils.isFlagsIncluded(result));
        assertTrue(MessageResultUtils.isBodyContentIncluded(result));
        assertTrue(MessageResultUtils.isFullContentIncluded(result));
        assertTrue(MessageResultUtils.isHeadersIncluded(result));
        assertTrue(MessageResultUtils.isInternalDateIncluded(result));
        assertTrue(MessageResultUtils.isSizeIncluded(result));
    }
}
