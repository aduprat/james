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
import org.apache.avalon.activity.Initializable;
import org.apache.avalon.component.Component;
import org.apache.avalon.configuration.Configurable;
import org.apache.avalon.configuration.Configuration;
import org.apache.avalon.configuration.ConfigurationException;
import org.apache.avalon.logger.AbstractLoggable;
import org.apache.excalibur.io.AndFileFilter;
import org.apache.excalibur.io.DirectoryFileFilter;
import org.apache.james.nntpserver.DateSinceFileFilter;
import org.apache.james.nntpserver.NNTPException;
import org.apache.oro.io.GlobFilenameFilter;
import org.apache.phoenix.Block;

public class NNTPRepositoryImpl extends AbstractLoggable 
    implements NNTPRepository, Configurable, Initializable, Block
{
    private boolean readOnly;
    // the groups are located under this path.
    private File rootPath;
    // articles are temprorily written here and then sent to the spooler.
    private File tempPath;
    private NNTPSpooler spool;
    private ArticleIDRepository articleIDRepo;
    private String[] addGroups = null;
    public void configure( Configuration configuration ) throws ConfigurationException {
        //System.out.println(getClass().getName()+": configure");
        //NNTPUtil.show(configuration,System.out);
        readOnly = configuration.getChild("readOnly").getValueAsBoolean(false);
        rootPath = NNTPUtil.getDirectory(configuration,"rootPath");
        tempPath = NNTPUtil.getDirectory(configuration,"tempPath");
        File articleIDPath = NNTPUtil.getDirectory(configuration,"articleIDPath");
        String articleIDDomainSuffix = configuration.getChild("articleIDDomainSuffix")
            .getValue("foo.bar.sho.boo");
        articleIDRepo = new ArticleIDRepository(articleIDPath,articleIDDomainSuffix);
        spool = (NNTPSpooler)NNTPUtil.createInstance
            (configuration.getChild("spool"),getLogger(),
             "org.apache.james.nntpserver.repository.NNTPSpooler");
        spool.setRepository(this);
        spool.setArticleIDRepository(articleIDRepo);
        getLogger().debug("repository:readOnly="+readOnly);
        getLogger().debug("repository:rootPath="+rootPath.getAbsolutePath());
        getLogger().debug("repository:tempPath="+tempPath.getAbsolutePath());
        configuration = configuration.getChild("newsgroups");
        List addGroupsList = new ArrayList();
        if ( configuration != null ) {
            Configuration[] children = configuration.getChildren("newsgroup");
            if ( children != null )
                for ( int i = 0 ; i < children.length ; i++ )
                    addGroupsList.add(children[i].getValue());
        }
        addGroups = (String[])addGroupsList.toArray(new String[0]);
        getLogger().debug("repository configuration done");
    }
    public void initialize() throws Exception {
        //System.out.println(getClass().getName()+": init");
        if ( rootPath.exists() == false )
            rootPath.mkdirs();
        for ( int i = 0 ; i < addGroups.length ; i++ ) {
            File groupF = new File(rootPath,addGroups[i]);
            if ( groupF.exists() == false )
                groupF.mkdirs();
        }
        if ( tempPath.exists() == false )
            tempPath.mkdirs();
        File articleIDPath = articleIDRepo.getPath();
        if ( articleIDPath.exists() == false )
            articleIDPath.mkdirs();
        if ( spool instanceof Initializable )
            ((Initializable)spool).initialize();
        getLogger().debug("repository initialization done");
    }
    public boolean isReadOnly() {
        return readOnly;
    }
    public NNTPGroup getGroup(String groupName) {
        File f = new File(rootPath,groupName);
        return ( f.exists() && f.isDirectory() ) ? new NNTPGroupImpl(f) : null;
    }
    public NNTPArticle getArticleFromID(String id) {
        try {
            return articleIDRepo.getArticle(this,id);
        } catch(Exception ex) {
            ex.printStackTrace();
            return null;
        }
//         int idx = id.indexOf('@');
//         String name = id.substring(0,idx);
//         String groupname = id.substring(idx+1);
//         NNTPGroup group = getGroup(groupname);
//         return ( group == null ) ? null : group.getArticleFromID(name);
    }
    public void createArticle(NNTPLineReader reader) {
        File f = new File(tempPath,System.currentTimeMillis()+"."+Math.random());
        try {
            FileOutputStream fout = new FileOutputStream(f);
            PrintStream prt = new PrintStream(fout,true);
            String line;
            while ( ( line = reader.readLine() ) != null )
                prt.println(line);
            prt.close();
            f.renameTo(new File(spool.getSpoolPath(),f.getName()));
        } catch(IOException ex) {
            throw new NNTPException("create article failed",ex);
        }
    }
    public Iterator getMatchedGroups(String wildmat) {
        File[] f = rootPath.listFiles(new AndFileFilter
            (new DirectoryFileFilter(),new GlobFilenameFilter(wildmat)));
        return getGroups(f);
    }
    private Iterator getGroups(File[] f) {
        List list = new ArrayList();
        for ( int i = 0 ; i < f.length ; i++ )
            list.add(new NNTPGroupImpl(f[i]));
        return list.iterator();
    }
    public Iterator getGroupsSince(Date dt) {
        File[] f = rootPath.listFiles(new AndFileFilter
            (new DirectoryFileFilter(),new DateSinceFileFilter(dt.getTime())));
        return getGroups(f);
    }

    // gets the list of groups.
    // creates iterator that concatenates the article iterators in the list of groups.
    // there is at most one article iterator reference for all the groups
    public Iterator getArticlesSince(final Date dt) {
        final Iterator giter = getGroupsSince(dt);
        return new Iterator() {
                private Iterator iter = null;
                public boolean hasNext() {
                    if ( iter == null ) {
                        if ( giter.hasNext() ) {
                            NNTPGroup group = (NNTPGroup)giter.next();
                            iter = group.getArticlesSince(dt);
                        }
                        else
                            return false;
                    }
                    if ( iter.hasNext() )
                        return true;
                    else {
                        iter = null;
                        return hasNext();
                    }
                }
                public Object next() {
                    return iter.next();
                }
                public void remove() {
                    throw new UnsupportedOperationException("remove not supported");
                }
            };
    }
}
