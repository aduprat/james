/*
 * Copyright (C) The Apache Software Foundation. All rights reserved.
 *
 * This software is published under the terms of the Apache Software License
 * version 1.1, a copy of which has been included with this distribution in
 * the LICENSE file.
 */
package org.apache.james.nntpserver.repository;

import java.io.*;
import java.util.*;
import org.apache.avalon.Initializable;
import org.apache.avalon.configuration.Configurable;
import org.apache.avalon.configuration.Configuration;
import org.apache.avalon.configuration.ConfigurationException;
import org.apache.avalon.logger.AbstractLoggable;
import org.apache.excalibur.io.AndFileFilter;
import org.apache.excalibur.io.DirectoryFileFilter;
import org.apache.oro.io.GlobFilenameFilter;

public interface NNTPRepository {
    NNTPGroup getGroup(String groupName);
    NNTPArticle getArticleFromID(String id);
    void createArticle(NNTPLineReader reader);
    Iterator getMatchedGroups(String wildmat);
    Iterator getGroupsSince(Date dt);
    Iterator getArticlesSince(Date dt);
    boolean isReadOnly();
}
