/*
 * Copyright (C) The Apache Software Foundation. All rights reserved.
 *
 * This software is published under the terms of the Apache Software License
 * version 1.1, a copy of which has been included with this distribution in
 * the LICENSE file.
 */
package org.apache.james.imapserver;

import java.util.StringTokenizer;

/**
 * An single client request to an IMAP server, with necessary details for
 * command processing
 *
 * @author <a href="mailto:sascha@kulawik.de">Sascha Kulawik</a>
 * @author <a href="mailto:charles@benett1.demon.co.uk">Charles Benett</a>
 * @version 0.2 on 04 Aug 2002
 */
public class ImapRequestImpl implements ImapRequest
{

    private String _command;
    private StringTokenizer commandLine;
    private boolean useUIDs;
    private ACLMailbox currentMailbox;
    private String commandRaw;
    private String tag;
    private SingleThreadedConnectionHandler caller;
    private String currentFolder;

    public ImapRequestImpl(SingleThreadedConnectionHandler handler,
                           String command ) {
        caller = handler;
        _command = command;
    }
    
    public String getCommand()
    {
        return _command;
    }
    
    public void setCommand( String command )
    {
        _command = command;
    }

    public SingleThreadedConnectionHandler getCaller() {
        return caller;
    }

    public void setCommandLine(StringTokenizer st) {
        commandLine = st;
    }

    public StringTokenizer getCommandLine() {
        //return new java.util.StringTokenizer(this.getCommandRaw());
        return commandLine;
    }

    public int arguments()
    {
        return commandLine.countTokens();
    }

    public void setUseUIDs(boolean state) {
        useUIDs = state;
    }

    public boolean useUIDs() {
        return useUIDs;
    }

    public void setCurrentMailbox(ACLMailbox mbox) {
        currentMailbox = mbox;
    }

    public ACLMailbox getCurrentMailbox() {
        return currentMailbox;
    }

    public void setCommandRaw(String raw) {
        commandRaw = raw;
    }

    public String getCommandRaw() {
        return commandRaw;
    }

    public void setTag(String t) {
        tag = t;
    }

    public String getTag() {
        return tag;
    }

    public void setCurrentFolder(String f) {
        currentFolder = f;
    }

    public String getCurrentFolder() {
        return currentFolder;
    }
}
