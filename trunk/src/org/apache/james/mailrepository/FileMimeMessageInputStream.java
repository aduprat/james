package org.apache.james.mailrepository;

import java.io.*;

import org.apache.james.core.*;

import org.apache.avalon.*;
import org.apache.cornerstone.services.Store;
//import org.apache.avalon.blocks.*;


public class FileMimeMessageInputStream extends JamesMimeMessageInputStream {
    //Define how to get to the data
    Store.StreamRepository sr = null;
    String key = null;

    public FileMimeMessageInputStream(Store.StreamRepository sr, String key) throws IOException {
        this.sr = sr;
        this.key = key;
    }

    public Store.StreamRepository getStreamStore() {
        return sr;
    }

    public String getKey() {
        return key;
    }

    protected synchronized InputStream openStream() throws IOException {
        return sr.get(key);
    }

    public boolean equals(Object obj) {
        if (obj instanceof FileMimeMessageInputStream) {
            FileMimeMessageInputStream in = (FileMimeMessageInputStream)obj;
            return in.getStreamStore().equals(sr) && in.getKey().equals(key);
        }
        return false;
    }
}
