/*
 * Copyright (C) The Apache Software Foundation. All rights reserved.
 *
 * This software is published under the terms of the Apache Software License
 * version 1.1, a copy of which has been included with this distribution in
 * the LICENSE file.
 */
package org.apache.james.imapserver;

import java.io.*;
import java.util.*;
import org.apache.avalon.framework.logger.AbstractLoggable;
import org.apache.james.util.Assert;


/**
 * Implementation of a RecordRepository on a FileSystem.
 *
 * @author <a href="mailto:charles@benett1.demon.co.uk">Charles Benett</a>
 * @version 0.1 on 14 Dec 2000
 * @see RecordRepository
 */
public class DefaultRecordRepository
    extends AbstractLoggable
    implements RecordRepository   {
 
    private String path;
    private File repository;

    /**
     * Returns the a unique UID validity value for this Host.
     * UID validity values are used to differentiate messages in 2 mailboxes with the same names
     * (when one is deleted).
     */
    public int nextUIDValidity()
    {
        // TODO - make this a better unique value
        // ( although this will probably never break in practice,
        //  should be incrementing a persisted value.
        return Math.abs( Calendar.getInstance().hashCode() );
    }

    /**
     * Deletes the FolderRecord from the repository.
     */
    public synchronized void deleteRecord( FolderRecord fr )
    {
        try {
            String key = path + File.separator + fr.getAbsoluteName();
            File record = new File( key );
            Assert.isTrue( record.exists() );
            record.delete();
            getLogger().info("Record deleted for: " + fr.getAbsoluteName());
            notifyAll();
        }
        catch (Exception e) {
            e.printStackTrace();
            throw new
                RuntimeException("Exception caught while storing Folder Record: " + e);
        }
    }

    public void setPath(final String rootPath) {
        if (path != null) {
            throw new RuntimeException("Error: Attempt to reset AvalonRecordRepository");
        }
        path = rootPath;
        
        repository = new File(rootPath);

        if (!repository.isDirectory()) {
            if (! repository.mkdirs()){
                throw new RuntimeException("Error: Cannot create directory for AvalonRecordRepository at: " + rootPath);
            }
        } else if (!repository.canWrite()) {
            throw new RuntimeException("Error: Cannot write to directory for AvalonRecordRepository at: " + rootPath);
        }

                
    }

    public synchronized void store( final FolderRecord fr) {
        ObjectOutputStream out = null;
        try {
            String key = path + File.separator + fr.getAbsoluteName();
            out = new ObjectOutputStream( new FileOutputStream(key) );
            out.writeObject(fr);
            out.close();
            getLogger().info("Record stored for: " + fr.getAbsoluteName());
            notifyAll();
        } catch (Exception e) {
            if (out != null) {
                try {
                    out.close();
                } catch (Exception ignored) {
                }
            }
            e.printStackTrace();
            throw new
                RuntimeException("Exception caught while storing Folder Record: " + e);
        }
    }

    public synchronized Iterator getAbsoluteNames() {
        String[] names = repository.list();
        return Collections.unmodifiableList(Arrays.asList(names)).iterator();
    }

    public synchronized FolderRecord retrieve(final String folderAbsoluteName) {
        FolderRecord fr = null;
        ObjectInputStream in = null;
        try {
            String key = path + File.separator + folderAbsoluteName;
            in        = new ObjectInputStream( new FileInputStream(key) );
            fr = (FolderRecord) in.readObject();
            in.close();
  
        } catch (Exception e) {
            if (in != null) {
                try {
                    in.close();
                } catch (Exception ignored) {
                }
            }
            e.printStackTrace();
            throw new
                RuntimeException("Exception caught while reading Folder Record: " + e);
        } finally {
            notifyAll();
        }
        return fr;
    }
       
    public boolean containsRecord(String folderAbsoluteName) {
        File testFile = new File(repository, folderAbsoluteName);
        return testFile.exists();
    }
}

    
