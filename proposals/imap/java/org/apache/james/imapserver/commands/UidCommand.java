/*
 * Copyright (C) The Apache Software Foundation. All rights reserved.
 *
 * This software is published under the terms of the Apache Software License
 * version 1.1, a copy of which has been included with this distribution in
 * the LICENSE file.
 */
package org.apache.james.imapserver.commands;

import org.apache.james.imapserver.CommandFetch;
import org.apache.james.imapserver.CommandStore;
import org.apache.james.imapserver.ImapRequest;
import org.apache.james.imapserver.ImapSession;

import java.util.StringTokenizer;

class UidCommand extends SelectedStateCommand
{
    public boolean process( ImapRequest request, ImapSession session )
    {
        int arguments = request.arguments();
        StringTokenizer commandLine = request.getCommandLine();
        String command = request.getCommand();

        if ( arguments < 4 ) {
            session.badResponse( "Command should be <tag> <UID> <command> <command parameters>" );
            return true;
        }
        String uidCommand = commandLine.nextToken();
        ImapCommand cmd = session.getImapCommand( uidCommand );
        if ( cmd instanceof CommandFetch
                || cmd instanceof CommandStore ) {
            request.setCommand( uidCommand );
            cmd.process( request, session );
        }
        else {
            session.badResponse( "Invalid UID secondary command." );
        }
        return true;
    }
}
