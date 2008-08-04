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

package org.apache.james.mailboxmanager.torque;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.james.mailboxmanager.MessageResult.Content;
import org.apache.james.mime4j.MimeException;
import org.apache.james.mime4j.parser.MimeTokenStream;

public class PartContentBuilder {
    
    private static final byte[] EMPTY = {};
    
    private MimeTokenStream parser;
    private boolean empty = false;
    private boolean topLevel = true;
    
    public PartContentBuilder() {
        parser = new MimeTokenStream();
    }
    
    public void markEmpty() {
        empty = true;
    }
    
    public void parse(final InputStream in) {
        parser.setRecursionMode(MimeTokenStream.M_RECURSE);
        parser.parse(in);
        topLevel = true;
    }
    
    private void skipToStartOfInner(int position) throws IOException {
        final int state = parser.next();
        switch (state) {
            case MimeTokenStream.T_START_MULTIPART: break;
            case MimeTokenStream.T_START_MESSAGE: break;
            case MimeTokenStream.T_END_OF_STREAM: throw new PartNotFoundException(position);
            case MimeTokenStream.T_END_BODYPART: throw new PartNotFoundException(position);
            default: skipToStartOfInner(position);
        }
    }
    
    public void to(int position) throws IOException {
        try {
            if (topLevel) {
                topLevel = false;
            } else {
                skipToStartOfInner(position);
            }
            for (int count=0;count<position;) {
                final int state = parser.next();
                switch (state) {
                    case MimeTokenStream.T_BODY:
                        if (position == 1) {
                            count++;
                        }
                        break;
                    case MimeTokenStream.T_START_BODYPART:
                        count++;
                        break;
                    case MimeTokenStream.T_START_MULTIPART:
                        if (count>0 && count<position) {
                            ignoreInnerMessage();
                        }
                        break;
                    case MimeTokenStream.T_END_OF_STREAM:
                        throw new PartNotFoundException(position);
                }
            }
        }
        catch (IllegalStateException e) {
            throw new PartNotFoundException(position, e);
        }
    }
    
    private void ignoreInnerMessage() throws IOException {
        for (int state = parser.next(); state != MimeTokenStream.T_END_MULTIPART; state = parser.next()) {
            switch (state) {
                case MimeTokenStream.T_END_OF_STREAM:
                    throw new UnexpectedEOFException();
                    
                case MimeTokenStream.T_START_MULTIPART:
                    ignoreInnerMessage();
                    break;
            }
        }
    }
    
    public Content getFullContent() throws IOException {
        final List headers = getMimeHeaders();
        final byte[] content = mimeBodyContent();
        return new FullContent(content, headers);
    }
    
    public Content getMessageBodyContent() throws IOException {
        final byte[] content = messageBodyContent();
        return new ByteContent(content);
    }

    private byte[] messageBodyContent() throws IOException {
        final byte[] content;
        if (empty) {
            content = EMPTY;
        } else {
            boolean valid;
            try 
            {
                advancedToMessage();
                valid = true;
            } catch (UnexpectedEOFException e) {
                // No TEXT part
                valid = false;
            }
            if (valid) {
                parser.setRecursionMode(MimeTokenStream.M_FLAT);
                for ( int state = parser.getState(); 
                        state != MimeTokenStream.T_BODY && state != MimeTokenStream.T_START_MULTIPART; 
                        state = parser.next()) {
                    if (state == MimeTokenStream.T_END_OF_STREAM) {
                        valid = false;
                        break;
                    }
                }
                if (valid) {
                    content = MessageUtils.toByteArray(parser.getInputStream());
                } else {
                    content = EMPTY;
                }
            } else {
                content = EMPTY;
            }
        }
        return content;
    }
    
    public Content getMimeBodyContent() throws IOException {
        final byte[] content = mimeBodyContent();
        return new ByteContent(content);
    }

    private byte[] mimeBodyContent() throws IOException {
        final byte[] content;
        if (empty) {
            content = EMPTY;
        } else {
            parser.setRecursionMode(MimeTokenStream.M_FLAT);
            boolean valid = true;
            for ( int state = parser.getState(); 
                    state != MimeTokenStream.T_BODY && state != MimeTokenStream.T_START_MULTIPART; 
                    state = parser.next()) {
                if (state == MimeTokenStream.T_END_OF_STREAM) {
                    valid = false;
                    break;
                }
            }
            if (valid) {
                content = MessageUtils.toByteArray(parser.getInputStream());
            } else {
                content = EMPTY;
            }
        }
        return content;
    }
    
    public List getMimeHeaders() throws IOException {
        final List results;
        if (empty) {
            results = Collections.EMPTY_LIST;
        } else {
            results = new ArrayList();
            for (int state = parser.getState(); state != MimeTokenStream.T_END_HEADER; state = parser.next()) {
                switch (state) {
                    case MimeTokenStream.T_END_OF_STREAM:
                        throw new UnexpectedEOFException();
                        
                    case MimeTokenStream.T_FIELD:
                        final String fieldValue = parser.getFieldValue().trim();
                        final String fieldName = parser.getFieldName();
                        Header header = new Header(fieldName, fieldValue);
                        results.add(header);
                        break;
                }
            }
        }
        return results;
    }
    
    public List getMessageHeaders() throws IOException {
        final List results;
        if (empty) {
            results = Collections.EMPTY_LIST;
        } else {
            results = new ArrayList();
            try
            {
                advancedToMessage();
                
                for (int state = parser.getState(); state != MimeTokenStream.T_END_HEADER; state = parser.next()) {
                    switch (state) {
                        case MimeTokenStream.T_END_OF_STREAM:
                            throw new IOException("Unexpected EOF");
                            
                        case MimeTokenStream.T_FIELD:
                            final String fieldValue = parser.getFieldValue().trim();
                            final String fieldName = parser.getFieldName();
                            Header header = new Header(fieldName, fieldValue);
                            results.add(header);
                            break;
                    }
                }
            } catch (UnexpectedEOFException e) {
                // No headers found
            }
        }
        return results;
    }

    private void advancedToMessage() throws IOException {
        for (int state = parser.getState(); state != MimeTokenStream.T_START_MESSAGE; state = parser.next()) {
            if (state == MimeTokenStream.T_END_OF_STREAM) {
                throw new UnexpectedEOFException();
            }
        }
    }
    
    public static final class UnexpectedEOFException extends MimeException {

        private static final long serialVersionUID = -3755637466593055796L;

        public UnexpectedEOFException() {
            super("Unexpected EOF");
        }        
    }
    
    public static final class PartNotFoundException extends MimeException {

        private static final long serialVersionUID = 7519976990944851574L;
        
        private final int position;
        
        public PartNotFoundException(int position) {
            this(position, null);
        }

        public PartNotFoundException(int position, Exception e) {
            super("Part " + position + " not found.", e);
            this.position = position;
        }
        
        public final int getPosition() {
            return position;
        }
        
    }
}
