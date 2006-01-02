/***********************************************************************
 * Copyright (c) 1999-2005 The Apache Software Foundation.             *
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

import org.apache.avalon.framework.logger.Logger;

public class MockLogger implements Logger {
    private boolean m_debugEnabled = true;

    public void debug(java.lang.String string) {
        System.out.println(string);
    }

    public void debug(java.lang.String string, java.lang.Throwable throwable) {
        System.out.println(string + throwable.toString());
    }

    public boolean isDebugEnabled() {
        return m_debugEnabled;
    }

    public void disableDebug() {
        m_debugEnabled = false;
    }

    public void info(java.lang.String string) {
        System.out.println(string);
    }

    public void info(java.lang.String string, java.lang.Throwable throwable) {
        System.out.println(string + throwable.toString());
    }

    public boolean isInfoEnabled() {
        return true;
    }

    public void warn(java.lang.String string) {
        System.out.println(string);
    }

    public void warn(java.lang.String string, java.lang.Throwable throwable) {
        System.out.println(string + throwable.toString());
    }

    public boolean isWarnEnabled() {
        return true;
    }

    public void error(java.lang.String string) {
        System.out.println(string);
    }

    public void error(java.lang.String string, java.lang.Throwable throwable) {
        System.out.println(string + throwable.toString());
    }

    public boolean isErrorEnabled() {
        return true;
    }

    public void fatalError(java.lang.String string) {
        System.out.println(string);
    }

    public void fatalError(java.lang.String string, java.lang.Throwable throwable) {
        System.out.println(string + throwable.toString());
    }

    public boolean isFatalErrorEnabled() {
        return true;
    }

    public org.apache.avalon.framework.logger.Logger getChildLogger(java.lang.String string) {
        return this;
    }
    
}
