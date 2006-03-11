/***********************************************************************
 * Copyright (c) 1999-2006 The Apache Software Foundation.             *
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
package org.apache.james.core;

import javax.mail.Session;
import javax.mail.internet.MimeMessage;

import java.io.ByteArrayInputStream;
import java.util.Properties;

public class MimeMessageFromStreamTest extends MimeMessageTest {
    
    protected MimeMessage getMessageFromSources(String sources) throws Exception {
        return new MimeMessage(Session.getDefaultInstance(new Properties()),new ByteArrayInputStream(sources.getBytes()));
    }

    protected MimeMessage getMultipartMessage() throws Exception {
        return getMessageFromSources(getMultipartMessageSource());
    }

    protected MimeMessage getSimpleMessage() throws Exception {
        return getMessageFromSources(getSimpleMessageCleanedSource());
    }

    protected MimeMessage getMissingEncodingAddHeaderMessage() throws Exception {
        return getMessageFromSources(getMissingEncodingAddHeaderSource());
    }


}
