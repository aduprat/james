/*
 * Copyright (C) The Apache Software Foundation. All rights reserved.
 *
 * This software is published under the terms of the Apache Software License
 * version 1.1, a copy of which has been included with this distribution in
 * the LICENSE file.
 */
package org.apache.james.smtpserver;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.InterruptedIOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.SequenceInputStream;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.StringTokenizer;

import javax.mail.MessagingException;

import org.apache.avalon.cornerstone.services.connection.ConnectionHandler;
import org.apache.avalon.excalibur.pool.Poolable;
import org.apache.avalon.framework.activity.Disposable;
import org.apache.avalon.framework.logger.AbstractLogEnabled;
import org.apache.james.Constants;
import org.apache.james.core.MailHeaders;
import org.apache.james.core.MailImpl;
import org.apache.james.util.Base64;
import org.apache.james.util.CharTerminatedInputStream;
import org.apache.james.util.InternetPrintWriter;
import org.apache.james.util.watchdog.BytesReadResetInputStream;
import org.apache.james.util.watchdog.Watchdog;
import org.apache.james.util.watchdog.WatchdogTarget;
import org.apache.mailet.MailAddress;
import org.apache.mailet.RFC2822Headers;
import org.apache.mailet.dates.RFC822DateFormat;
/**
 * Provides SMTP functionality by carrying out the server side of the SMTP
 * interaction.
 *
 * @author Serge Knystautas <sergek@lokitech.com>
 * @author Federico Barbieri <scoobie@systemy.it>
 * @author Jason Borden <jborden@javasense.com>
 * @author Matthew Pangaro <mattp@lokitech.com>
 * @author Danny Angus <danny@thought.co.uk>
 * @author Peter M. Goldstein <farsight@alum.mit.edu>
 *
 * @version This is $Revision: 1.41 $
 */
public class SMTPHandler
    extends AbstractLogEnabled
    implements ConnectionHandler, Poolable {

    /**
     * SMTP Server identification string used in SMTP headers
     */
    private final static String SOFTWARE_TYPE = "JAMES SMTP Server "
                                                 + Constants.SOFTWARE_VERSION;

    // Keys used to store/lookup data in the internal state hash map

    private final static String CURRENT_HELO_MODE = "CURRENT_HELO_MODE"; // HELO or EHLO
    private final static String SENDER = "SENDER_ADDRESS";     // Sender's email address
    private final static String MESG_FAILED = "MESG_FAILED";   // Message failed flag
    private final static String MESG_SIZE = "MESG_SIZE";       // The size of the message
    private final static String RCPT_LIST = "RCPT_LIST";   // The message recipients

    /**
     * The character array that indicates termination of an SMTP connection
     */
    private final static char[] SMTPTerminator = { '\r', '\n', '.', '\r', '\n' };

    /**
     * Static Random instance used to generate SMTP ids
     */
    private final static Random random = new Random();

    /**
     * Static RFC822DateFormat used to generate date headers
     */
    private final static RFC822DateFormat rfc822DateFormat = new RFC822DateFormat();

    /**
     * The text string for the SMTP HELO command.
     */
    private final static String COMMAND_HELO = "HELO";

    /**
     * The text string for the SMTP EHLO command.
     */
    private final static String COMMAND_EHLO = "EHLO";

    /**
     * The text string for the SMTP AUTH command.
     */
    private final static String COMMAND_AUTH = "AUTH";

    /**
     * The text string for the SMTP MAIL command.
     */
    private final static String COMMAND_MAIL = "MAIL";

    /**
     * The text string for the SMTP RCPT command.
     */
    private final static String COMMAND_RCPT = "RCPT";

    /**
     * The text string for the SMTP NOOP command.
     */
    private final static String COMMAND_NOOP = "NOOP";

    /**
     * The text string for the SMTP RSET command.
     */
    private final static String COMMAND_RSET = "RSET";

    /**
     * The text string for the SMTP DATA command.
     */
    private final static String COMMAND_DATA = "DATA";

    /**
     * The text string for the SMTP QUIT command.
     */
    private final static String COMMAND_QUIT = "QUIT";

    /**
     * The text string for the SMTP HELP command.
     */
    private final static String COMMAND_HELP = "HELP";

    /**
     * The text string for the SMTP VRFY command.
     */
    private final static String COMMAND_VRFY = "VRFY";

    /**
     * The text string for the SMTP EXPN command.
     */
    private final static String COMMAND_EXPN = "EXPN";

    /**
     * The text string for the SMTP AUTH type PLAIN.
     */
    private final static String AUTH_TYPE_PLAIN = "PLAIN";

    /**
     * The text string for the SMTP AUTH type LOGIN.
     */
    private final static String AUTH_TYPE_LOGIN = "LOGIN";

    /**
     * The text string for the SMTP MAIL command SIZE option.
     */
    private final static String MAIL_OPTION_SIZE = "SIZE";

    /**
     * The thread executing this handler
     */
    private Thread handlerThread;

    /**
     * The TCP/IP socket over which the SMTP
     * dialogue is occurring.
     */
    private Socket socket;

    /**
     * The incoming stream of bytes coming from the socket.
     */
    private InputStream in;

    /**
     * The writer to which outgoing messages are written.
     */
    private PrintWriter out;

    /**
     * The remote host name obtained by lookup on the socket.
     */
    private String remoteHost;

    /**
     * The remote IP address of the socket.
     */
    private String remoteIP;

    /**
     * The user name of the authenticated user associated with this SMTP transaction.
     */
    private String authenticatedUser;

    /**
     * The id associated with this particular SMTP interaction.
     */
    private String smtpID;

    /**
     * The per-service configuration data that applies to all handlers
     */
    private SMTPHandlerConfigurationData theConfigData;

    /**
     * The hash map that holds variables for the SMTP message transfer in progress.
     *
     * This hash map should only be used to store variable set in a particular
     * set of sequential MAIL-RCPT-DATA commands, as described in RFC 2821.  Per
     * connection values should be stored as member variables in this class.
     */
    private HashMap state = new HashMap();

    /**
     * The watchdog being used by this handler to deal with idle timeouts.
     */
    Watchdog theWatchdog;

    /**
     * The watchdog target that idles out this handler.
     */
    WatchdogTarget theWatchdogTarget = new SMTPWatchdogTarget();

    /**
     * The per-handler response buffer used to marshal responses.
     */
    StringBuffer responseBuffer = new StringBuffer(256);

    /**
     * Set the configuration data for the handler
     *
     * @param theData the per-service configuration data for this handler
     */
    void setConfigurationData(SMTPHandlerConfigurationData theData) {
        theConfigData = theData;
    }

    /**
     * Set the Watchdog for use by this handler.
     *
     * @param theWatchdog the watchdog
     */
    void setWatchdog(Watchdog theWatchdog) {
        this.theWatchdog = theWatchdog;
    }

    /**
     * Gets the Watchdog Target that should be used by Watchdogs managing
     * this connection.
     *
     * @return the WatchdogTarget
     */
    WatchdogTarget getWatchdogTarget() {
        return theWatchdogTarget;
    }

    /**
     * Idle out this connection
     */
    void idleClose() {
        if (getLogger() != null) {
            getLogger().error("SMTP Connection has idled out.");
        }
        try {
            if (socket != null) {
                socket.close();
            }
        } catch (Exception e) {
            // ignored
        }

        synchronized (this) {
            // Interrupt the thread to recover from internal hangs
            if (handlerThread != null) {
                handlerThread.interrupt();
            }
        }
    }

    /**
     * @see org.apache.avalon.cornerstone.services.connection.ConnectionHandler#handleConnection(Socket)
     */
    public void handleConnection(Socket connection) throws IOException {

        try {
            this.socket = connection;
            synchronized (this) {
                handlerThread = Thread.currentThread();
            }
            in = new BufferedInputStream(socket.getInputStream(), 1024);
            remoteIP = socket.getInetAddress().getHostAddress();
            remoteHost = socket.getInetAddress().getHostName();
            smtpID = random.nextInt(1024) + "";
            resetState();
        } catch (Exception e) {
            StringBuffer exceptionBuffer =
                new StringBuffer(256)
                    .append("Cannot open connection from ")
                    .append(remoteHost)
                    .append(" (")
                    .append(remoteIP)
                    .append("): ")
                    .append(e.getMessage());
            String exceptionString = exceptionBuffer.toString();
            getLogger().error(exceptionString, e );
            throw new RuntimeException(exceptionString);
        }

        if (getLogger().isInfoEnabled()) {
            StringBuffer infoBuffer =
                new StringBuffer(128)
                        .append("Connection from ")
                        .append(remoteHost)
                        .append(" (")
                        .append(remoteIP)
                        .append(")");
            getLogger().info(infoBuffer.toString());
        }

        try {

            out = new InternetPrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()), 1024), false);

            // Initially greet the connector
            // Format is:  Sat, 24 Jan 1998 13:16:09 -0500

            responseBuffer.append("220 ")
                          .append(theConfigData.getHelloName())
                          .append(" SMTP Server (")
                          .append(SOFTWARE_TYPE)
                          .append(") ready ")
                          .append(rfc822DateFormat.format(new Date()));
            String responseString = clearResponseBuffer();
            writeLoggedFlushedResponse(responseString);

            theWatchdog.start();
            while (parseCommand(readCommandLine())) {
                theWatchdog.reset();
            }
            theWatchdog.stop();
            getLogger().debug("Closing socket.");
        } catch (SocketException se) {
            if (getLogger().isDebugEnabled()) {
                StringBuffer errorBuffer =
                    new StringBuffer(64)
                        .append("Socket to ")
                        .append(remoteHost)
                        .append(" (")
                        .append(remoteIP)
                        .append(") closed remotely.");
                getLogger().debug(errorBuffer.toString(), se );
            }
        } catch ( InterruptedIOException iioe ) {
            if (getLogger().isDebugEnabled()) {
                StringBuffer errorBuffer =
                    new StringBuffer(64)
                        .append("Socket to ")
                        .append(remoteHost)
                        .append(" (")
                        .append(remoteIP)
                        .append(") timeout.");
                getLogger().debug( errorBuffer.toString(), iioe );
            }
        } catch ( IOException ioe ) {
            if (getLogger().isDebugEnabled()) {
                StringBuffer errorBuffer =
                    new StringBuffer(256)
                            .append("Exception handling socket to ")
                            .append(remoteHost)
                            .append(" (")
                            .append(remoteIP)
                            .append(") : ")
                            .append(ioe.getMessage());
                getLogger().debug( errorBuffer.toString(), ioe );
            }
        } catch (Exception e) {
            if (getLogger().isDebugEnabled()) {
                getLogger().debug( "Exception opening socket: "
                                   + e.getMessage(), e );
            }
        } finally {
            resetHandler();
        }
    }

    /**
     * Resets the handler data to a basic state.
     */
    private void resetHandler() {
        resetState();

        clearResponseBuffer();
        in = null;
        out = null;
        remoteHost = null;
        remoteIP = null;
        authenticatedUser = null;
        smtpID = null;

        if (theWatchdog != null) {
            if (theWatchdog instanceof Disposable) {
                ((Disposable)theWatchdog).dispose();
            }
            theWatchdog = null;
        }

        try {
            if (socket != null) {
                socket.close();
            }
        } catch (IOException e) {
            if (getLogger().isErrorEnabled()) {
                getLogger().error("Exception closing socket: "
                                  + e.getMessage());
            }
        } finally {
            socket = null;
        }

        synchronized (this) {
            handlerThread = null;
        }

    }

    /**
     * Clears the response buffer, returning the String of characters in the buffer.
     *
     * @return the data in the response buffer
     */
    private String clearResponseBuffer() {
        String responseString = responseBuffer.toString();
        responseBuffer.delete(0,responseBuffer.length());
        return responseString;
    }

    /**
     * This method logs at a "DEBUG" level the response string that
     * was sent to the SMTP client.  The method is provided largely
     * as syntactic sugar to neaten up the code base.  It is declared
     * private and final to encourage compiler inlining.
     *
     * @param responseString the response string sent to the client
     */
    private final void logResponseString(String responseString) {
        if (getLogger().isDebugEnabled()) {
            getLogger().debug("Sent: " + responseString);
        }
    }

    /**
     * Write and flush a response string.  The response is also logged.
     * Should be used for the last line of a multi-line response or
     * for a single line response.
     *
     * @param responseString the response string sent to the client
     */
    final void writeLoggedFlushedResponse(String responseString) {
        out.println(responseString);
        out.flush();
        logResponseString(responseString);
    }

    /**
     * Write a response string.  The response is also logged.
     * Used for multi-line responses.
     *
     * @param responseString the response string sent to the client
     */
    final void writeLoggedResponse(String responseString) {
        out.println(responseString);
        logResponseString(responseString);
    }

    /**
     * Reads a line of characters off the command line.
     *
     * @return the trimmed input line
     * @throws IOException if an exception is generated reading in the input characters
     */
    final String readCommandLine() throws IOException {
        //Read through for \r or \n
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        byte b = -1;
        while (true) {
            b = (byte) in.read();
            if (b == 13) {
                //We're done, but we want to see if \n is next
                in.mark(1);
                b = (byte) in.read();
                if (b != 10) {
                    in.reset();
                }
                break;
            } else if (b == 10) {
                //We're done
                break;
            } else if (b == -1) {
                break;
            }
            bout.write(b);
        }
        // An ASCII encoding can be used because all transmissions other
        // that those in the DATA command are guaranteed
        // to be ASCII
        return bout.toString("ASCII").trim();
    }

    /**
     * Sets the user name associated with this SMTP interaction.
     *
     * @param userID the user name
     */
    private void setUser(String userID) {
        authenticatedUser = userID;
    }

    /**
     * Returns the user name associated with this SMTP interaction.
     *
     * @return the user name
     */
    private String getUser() {
        return authenticatedUser;
    }

    /**
     * Resets message-specific, but not authenticated user, state.
     *
     */
    private void resetState() {
        ArrayList recipients = (ArrayList)state.get(RCPT_LIST);
        if (recipients != null) {
            recipients.clear();
        }
        state.clear();
    }

    /**
     * This method parses SMTP commands read off the wire in handleConnection.
     * Actual processing of the command (possibly including additional back and
     * forth communication with the client) is delegated to one of a number of
     * command specific handler methods.  The primary purpose of this method is
     * to parse the raw command string to determine exactly which handler should
     * be called.  It returns true if expecting additional commands, false otherwise.
     *
     * @param rawCommand the raw command string passed in over the socket
     *
     * @return whether additional commands are expected.
     */
    private boolean parseCommand(String rawCommand) throws Exception {
        String argument = null;
        boolean returnValue = true;
        String command = rawCommand;

        if (rawCommand == null) {
            return false;
        }
        if ((state.get(MESG_FAILED) == null) && (getLogger().isDebugEnabled())) {
            getLogger().debug("Command received: " + command);
        }
        int spaceIndex = command.indexOf(" ");
        if (spaceIndex > 0) {
            argument = command.substring(spaceIndex + 1);
            command = command.substring(0, spaceIndex);
        }
        command = command.toUpperCase(Locale.US);
        if (command.equals(COMMAND_HELO)) {
            doHELO(argument);
        } else if (command.equals(COMMAND_EHLO)) {
            doEHLO(argument);
        } else if (command.equals(COMMAND_AUTH)) {
            doAUTH(argument);
        } else if (command.equals(COMMAND_MAIL)) {
            doMAIL(argument);
        } else if (command.equals(COMMAND_RCPT)) {
            doRCPT(argument);
        } else if (command.equals(COMMAND_NOOP)) {
            doNOOP(argument);
        } else if (command.equals(COMMAND_RSET)) {
            doRSET(argument);
        } else if (command.equals(COMMAND_DATA)) {
            doDATA(argument);
        } else if (command.equals(COMMAND_QUIT)) {
            doQUIT(argument);
            returnValue = false;
        } else if (command.equals(COMMAND_VRFY)) {
            doVRFY(argument);
        } else if (command.equals(COMMAND_EXPN)) {
            doEXPN(argument);
        } else if (command.equals(COMMAND_HELP)) {
            doHELP(argument);
        } else {
            if (state.get(MESG_FAILED) == null)  {
                doUnknownCmd(command, argument);
            }
        }
        return returnValue;
    }

    /**
     * Handler method called upon receipt of a HELO command.
     * Responds with a greeting and informs the client whether
     * client authentication is required.
     *
     * @param argument the argument passed in with the command by the SMTP client
     */
    private void doHELO(String argument) {
        String responseString = null;
        if (argument == null) {
            responseString = "501 Domain address required: " + COMMAND_HELO;
            writeLoggedFlushedResponse(responseString);
        } else {
            resetState();
            state.put(CURRENT_HELO_MODE, COMMAND_HELO);
            if (theConfigData.isAuthRequired()) {
                //This is necessary because we're going to do a multiline response
                responseBuffer.append("250-");
            } else {
                responseBuffer.append("250 ");
            }
            responseBuffer.append(theConfigData.getHelloName())
                          .append(" Hello ")
                          .append(argument)
                          .append(" (")
                          .append(remoteHost)
                          .append(" [")
                          .append(remoteIP)
                          .append("])");
            responseString = clearResponseBuffer();
            if (theConfigData.isAuthRequired()) {
                writeLoggedResponse(responseString);
                responseString = "250 AUTH LOGIN PLAIN";
            }
        }
            writeLoggedFlushedResponse(responseString);
    }

    /**
     * Handler method called upon receipt of a EHLO command.
     * Responds with a greeting and informs the client whether
     * client authentication is required.
     *
     * @param argument the argument passed in with the command by the SMTP client
     */
    private void doEHLO(String argument) {
        String responseString = null;
        if (argument == null) {
            responseString = "501 Domain address required: " + COMMAND_EHLO;
            writeLoggedFlushedResponse(responseString);
        } else {
            resetState();
            state.put(CURRENT_HELO_MODE, COMMAND_EHLO);
            // Extension defined in RFC 1870
            long maxMessageSize = theConfigData.getMaxMessageSize();
            if (maxMessageSize > 0) {
                responseString = "250-SIZE " + maxMessageSize;
                writeLoggedResponse(responseString);
            }
            if (theConfigData.isAuthRequired()) {
                //This is necessary because we're going to do a multiline response
                responseBuffer.append("250-");
            } else {
                responseBuffer.append("250 ");
            }
            responseBuffer.append(theConfigData.getHelloName())
                           .append(" Hello ")
                           .append(argument)
                           .append(" (")
                           .append(remoteHost)
                           .append(" [")
                           .append(remoteIP)
                           .append("])");
            responseString = clearResponseBuffer();
            if (theConfigData.isAuthRequired()) {
                writeLoggedResponse(responseString);
                responseString = "250 AUTH LOGIN PLAIN";
            }
            writeLoggedFlushedResponse(responseString);
        }
    }

    /**
     * Handler method called upon receipt of a AUTH command.
     * Handles client authentication to the SMTP server.
     *
     * @param argument the argument passed in with the command by the SMTP client
     */
    private void doAUTH(String argument)
            throws Exception {
        String responseString = null;
        if (getUser() != null) {
            responseString = "503 User has previously authenticated. "
                        + " Further authentication is not required!";
            writeLoggedFlushedResponse(responseString);
        } else if (argument == null) {
            responseString = "501 Usage: AUTH (authentication type) <challenge>";
            writeLoggedFlushedResponse(responseString);
        } else {
            String initialResponse = null;
            if ((argument != null) && (argument.indexOf(" ") > 0)) {
                initialResponse = argument.substring(argument.indexOf(" ") + 1);
                argument = argument.substring(0,argument.indexOf(" "));
            }
            String authType = argument.toUpperCase(Locale.US);
            if (authType.equals(AUTH_TYPE_PLAIN)) {
                doPlainAuth(initialResponse);
                return;
            } else if (authType.equals(AUTH_TYPE_LOGIN)) {
                doLoginAuth(initialResponse);
                return;
            } else {
                doUnknownAuth(authType, initialResponse);
                return;
            }
        }
    }

    /**
     * Carries out the Plain AUTH SASL exchange.
     *
     * @param initialResponse the initial response line passed in with the AUTH command
     */
    private void doPlainAuth(String initialResponse)
            throws IOException {
        String userpass = null, user = null, pass = null, responseString = null;
        if (initialResponse == null) {
            responseString = "334 OK. Continue authentication";
            writeLoggedFlushedResponse(responseString);
            userpass = readCommandLine();
        } else {
            userpass = initialResponse.trim();
        }
        try {
            if (userpass != null) {
                userpass = Base64.decodeAsString(userpass);
            }
            if (userpass != null) {
                StringTokenizer authTokenizer = new StringTokenizer(userpass, "\0");
                user = authTokenizer.nextToken();
                pass = authTokenizer.nextToken();
                authTokenizer = null;
            }
        }
        catch (Exception e) {
            // Ignored - this exception in parsing will be dealt
            // with in the if clause below
        }
        // Authenticate user
        if ((user == null) || (pass == null)) {
            responseString = "501 Could not decode parameters for AUTH PLAIN";
            writeLoggedFlushedResponse(responseString);
        } else if (theConfigData.getUsersRepository().test(user, pass)) {
            setUser(user);
            responseString = "235 Authentication Successful";
            writeLoggedFlushedResponse(responseString);
            getLogger().info("AUTH method PLAIN succeeded");
        } else {
            responseString = "535 Authentication Failed";
            writeLoggedFlushedResponse(responseString);
            getLogger().error("AUTH method PLAIN failed");
        }
        return;
    }

    /**
     * Carries out the Login AUTH SASL exchange.
     *
     * @param initialResponse the initial response line passed in with the AUTH command
     */
    private void doLoginAuth(String initialResponse)
            throws IOException {
        String user = null, pass = null, responseString = null;
        if (initialResponse == null) {
            responseString = "334 VXNlcm5hbWU6"; // base64 encoded "Username:"
            writeLoggedFlushedResponse(responseString);
            user = readCommandLine();
        } else {
            user = initialResponse.trim();
        }
        if (user != null) {
            try {
                user = Base64.decodeAsString(user);
            } catch (Exception e) {
                // Ignored - this parse error will be
                // addressed in the if clause below
                user = null;
            }
        }
        responseString = "334 UGFzc3dvcmQ6"; // base64 encoded "Password:"
        writeLoggedFlushedResponse(responseString);
        pass = readCommandLine();
        if (pass != null) {
            try {
                pass = Base64.decodeAsString(pass);
            } catch (Exception e) {
                // Ignored - this parse error will be
                // addressed in the if clause below
                pass = null;
            }
        }
        // Authenticate user
        if ((user == null) || (pass == null)) {
            responseString = "501 Could not decode parameters for AUTH LOGIN";
        } else if (theConfigData.getUsersRepository().test(user, pass)) {
            setUser(user);
            responseString = "235 Authentication Successful";
            if (getLogger().isDebugEnabled()) {
                // TODO: Make this string a more useful debug message
                getLogger().debug("AUTH method LOGIN succeeded");
            }
        } else {
            responseString = "535 Authentication Failed";
            // TODO: Make this string a more useful error message
            getLogger().error("AUTH method LOGIN failed");
        }
        writeLoggedFlushedResponse(responseString);
        return;
    }

    /**
     * Handles the case of an unrecognized auth type.
     *
     * @param authType the unknown auth type
     * @param initialResponse the initial response line passed in with the AUTH command
     */
    private void doUnknownAuth(String authType, String initialResponse) {
        String responseString = "504 Unrecognized Authentication Type";
        writeLoggedFlushedResponse(responseString);
        if (getLogger().isErrorEnabled()) {
            StringBuffer errorBuffer =
                new StringBuffer(128)
                    .append("AUTH method ")
                        .append(authType)
                        .append(" is an unrecognized authentication type");
            getLogger().error(errorBuffer.toString());
        }
        return;
    }

    /**
     * Handler method called upon receipt of a MAIL command.
     * Sets up handler to deliver mail as the stated sender.
     *
     * @param argument the argument passed in with the command by the SMTP client
     */
    private void doMAIL(String argument) {
        String responseString = null;

        String sender = null;
        if ((argument != null) && (argument.indexOf(":") > 0)) {
            int colonIndex = argument.indexOf(":");
            sender = argument.substring(colonIndex + 1);
            argument = argument.substring(0, colonIndex);
        }
        if (state.containsKey(SENDER)) {
            responseString = "503 Sender already specified";
            writeLoggedFlushedResponse(responseString);
        } else if (argument == null || !argument.toUpperCase(Locale.US).equals("FROM")
                   || sender == null) {
            responseString = "501 Usage: MAIL FROM:<sender>";
            writeLoggedFlushedResponse(responseString);
        } else {
            sender = sender.trim();
            int lastChar = sender.lastIndexOf('>');
            // Check to see if any options are present and, if so, whether they are correctly formatted
            // (separated from the closing angle bracket by a ' ').
            if ((lastChar > 0) && (sender.length() > lastChar + 2) && (sender.charAt(lastChar + 1) == ' ')) {
                String mailOptionString = sender.substring(lastChar + 2);

                // Remove the options from the sender
                sender = sender.substring(0, lastChar + 1);

                StringTokenizer optionTokenizer = new StringTokenizer(mailOptionString, " ");
                while (optionTokenizer.hasMoreElements()) {
                    String mailOption = optionTokenizer.nextToken();
                    int equalIndex = mailOptionString.indexOf('=');
                    String mailOptionName = mailOption;
                    String mailOptionValue = "";
                    if (equalIndex > 0) {
                        mailOptionName = mailOption.substring(0, equalIndex).toUpperCase(Locale.US);
                        mailOptionValue = mailOption.substring(equalIndex + 1);
                    }

                    // Handle the SIZE extension keyword

                    if (mailOptionName.startsWith(MAIL_OPTION_SIZE)) {
                        if (!(doMailSize(mailOptionValue))) {
                            return;
                        }
                    } else {
                        // Unexpected option attached to the Mail command
                        if (getLogger().isDebugEnabled()) {
                            StringBuffer debugBuffer =
                                new StringBuffer(128)
                                    .append("MAIL command had unrecognized/unexpected option ")
                                    .append(mailOptionName)
                                    .append(" with value ")
                                    .append(mailOptionValue);
                            getLogger().debug(debugBuffer.toString());
                        }
                    }
                }
            }
            if (!sender.startsWith("<") || !sender.endsWith(">")) {
                responseString = "501 Syntax error in MAIL command";
                writeLoggedFlushedResponse(responseString);
                if (getLogger().isErrorEnabled()) {
                    StringBuffer errorBuffer =
                        new StringBuffer(128)
                            .append("Error parsing sender address: ")
                            .append(sender)
                            .append(": did not start and end with < >");
                    getLogger().error(errorBuffer.toString());
                }
                return;
            }
            MailAddress senderAddress = null;
            //Remove < and >
            sender = sender.substring(1, sender.length() - 1);
            if (sender.length() == 0) {
                //This is the <> case.  Let senderAddress == null
            } else {
                if (sender.indexOf("@") < 0) {
                    sender = sender + "@localhost";
                }
                try {
                    senderAddress = new MailAddress(sender);
                } catch (Exception pe) {
                    responseString = "501 Syntax error in sender address";
                    writeLoggedFlushedResponse(responseString);
                    if (getLogger().isErrorEnabled()) {
                        StringBuffer errorBuffer =
                            new StringBuffer(256)
                                    .append("Error parsing sender address: ")
                                    .append(sender)
                                    .append(": ")
                                    .append(pe.getMessage());
                        getLogger().error(errorBuffer.toString());
                    }
                    return;
                }
            }
            state.put(SENDER, senderAddress);
            responseBuffer.append("250 Sender <")
                          .append(sender)
                          .append("> OK");
            responseString = clearResponseBuffer();
            writeLoggedFlushedResponse(responseString);
        }
    }

    /**
     * Handles the SIZE MAIL option.
     *
     * @param mailOptionValue the option string passed in with the SIZE option
     * @returns true if further options should be processed, false otherwise
     */
    private boolean doMailSize(String mailOptionValue) {
        int size = 0;
        try {
            size = Integer.parseInt(mailOptionValue);
        } catch (NumberFormatException pe) {
            // This is a malformed option value.  We return an error
            String responseString = "501 Syntactically incorrect value for SIZE parameter";
            writeLoggedFlushedResponse(responseString);
            getLogger().error("Rejected syntactically incorrect value for SIZE parameter.");
            return false;
        }
        if (getLogger().isDebugEnabled()) {
            StringBuffer debugBuffer =
                new StringBuffer(128)
                    .append("MAIL command option SIZE received with value ")
                    .append(size)
                    .append(".");
                    getLogger().debug(debugBuffer.toString());
        }
        long maxMessageSize = theConfigData.getMaxMessageSize();
        if ((maxMessageSize > 0) && (size > maxMessageSize)) {
            // Let the client know that the size limit has been hit.
            String responseString = "552 Message size exceeds fixed maximum message size";
            writeLoggedFlushedResponse(responseString);
            StringBuffer errorBuffer =
                new StringBuffer(256)
                    .append("Rejected message from ")
                    .append(state.get(SENDER).toString())
                    .append(" from host ")
                    .append(remoteHost)
                    .append(" (")
                    .append(remoteIP)
                    .append(") of size ")
                    .append(size)
                    .append(" exceeding system maximum message size of ")
                    .append(maxMessageSize)
                    .append("based on SIZE option.");
            getLogger().error(errorBuffer.toString());
            return false;
        } else {
            // put the message size in the message state so it can be used
            // later to restrict messages for user quotas, etc.
            state.put(MESG_SIZE, new Integer(size));
        }
        return true;
    }

    /**
     * Handler method called upon receipt of a RCPT command.
     * Reads recipient.  Does some connection validation.
     *
     * @param argument the argument passed in with the command by the SMTP client
     */
    private void doRCPT(String argument) {
        String responseString = null;

        String recipient = null;
        if ((argument != null) && (argument.indexOf(":") > 0)) {
            int colonIndex = argument.indexOf(":");
            recipient = argument.substring(colonIndex + 1);
            argument = argument.substring(0, colonIndex);
        }
        if (!state.containsKey(SENDER)) {
            responseString = "503 Need MAIL before RCPT";
            writeLoggedFlushedResponse(responseString);
        } else if (argument == null || !argument.toUpperCase(Locale.US).equals("TO")
                   || recipient == null) {
            responseString = "501 Usage: RCPT TO:<recipient>";
            writeLoggedFlushedResponse(responseString);
        } else {
            Collection rcptColl = (Collection) state.get(RCPT_LIST);
            if (rcptColl == null) {
                rcptColl = new ArrayList();
            }
            recipient = recipient.trim();
            int lastChar = recipient.lastIndexOf('>');
            // Check to see if any options are present and, if so, whether they are correctly formatted
            // (separated from the closing angle bracket by a ' ').
            if ((lastChar > 0) && (recipient.length() > lastChar + 2) && (recipient.charAt(lastChar + 1) == ' ')) {
                String rcptOptionString = recipient.substring(lastChar + 2);

                // Remove the options from the recipient
                recipient = recipient.substring(0, lastChar + 1);

                StringTokenizer optionTokenizer = new StringTokenizer(rcptOptionString, " ");
                while (optionTokenizer.hasMoreElements()) {
                    String rcptOption = optionTokenizer.nextToken();
                    int equalIndex = rcptOptionString.indexOf('=');
                    String rcptOptionName = rcptOption;
                    String rcptOptionValue = "";
                    if (equalIndex > 0) {
                        rcptOptionName = rcptOption.substring(0, equalIndex).toUpperCase(Locale.US);
                        rcptOptionValue = rcptOption.substring(equalIndex + 1);
                    }
                    // Unexpected option attached to the RCPT command
                    if (getLogger().isDebugEnabled()) {
                        StringBuffer debugBuffer =
                            new StringBuffer(128)
                                .append("RCPT command had unrecognized/unexpected option ")
                                .append(rcptOptionName)
                                .append(" with value ")
                                .append(rcptOptionValue);
                        getLogger().debug(debugBuffer.toString());
                    }
                }
                optionTokenizer = null;
            }
            if (!recipient.startsWith("<") || !recipient.endsWith(">")) {
                responseString = "501 Syntax error in parameters or arguments";
                writeLoggedFlushedResponse(responseString);
                if (getLogger().isErrorEnabled()) {
                    StringBuffer errorBuffer =
                        new StringBuffer(192)
                                .append("Error parsing recipient address: ")
                                .append(recipient)
                                .append(": did not start and end with < >");
                    getLogger().error(errorBuffer.toString());
                }
                return;
            }
            MailAddress recipientAddress = null;
            //Remove < and >
            recipient = recipient.substring(1, recipient.length() - 1);
            if (recipient.indexOf("@") < 0) {
                recipient = recipient + "@localhost";
            }
            try {
                recipientAddress = new MailAddress(recipient);
            } catch (Exception pe) {
                responseString = "501 Syntax error in recipient address";
                writeLoggedFlushedResponse(responseString);

                if (getLogger().isErrorEnabled()) {
                    StringBuffer errorBuffer =
                        new StringBuffer(192)
                                .append("Error parsing recipient address: ")
                                .append(recipient)
                                .append(": ")
                                .append(pe.getMessage());
                    getLogger().error(errorBuffer.toString());
                }
                return;
            }
            if (theConfigData.isAuthRequired()) {
                // Make sure the mail is being sent locally if not
                // authenticated else reject.
                if (getUser() == null) {
                    String toDomain = recipientAddress.getHost();
                    if (!theConfigData.getMailServer().isLocalServer(toDomain)) {
                        responseString = "530 Authentication Required";
                        writeLoggedFlushedResponse(responseString);
                        getLogger().error("Rejected message - authentication is required for mail request");
                        return;
                    }
                } else {
                    // Identity verification checking
                    if (theConfigData.isVerifyIdentity()) {
                        String authUser = (getUser()).toLowerCase(Locale.US);
                        MailAddress senderAddress = (MailAddress) state.get(SENDER);
                        boolean domainExists = false;

                        if ((!authUser.equals(senderAddress.getUser())) ||
                            (!theConfigData.getMailServer().isLocalServer(senderAddress.getHost()))) {
                            responseString = "503 Incorrect Authentication for Specified Email Address";
                            writeLoggedFlushedResponse(responseString);
                            if (getLogger().isErrorEnabled()) {
                                StringBuffer errorBuffer =
                                    new StringBuffer(128)
                                        .append("User ")
                                        .append(authUser)
                                        .append(" authenticated, however tried sending email as ")
                                        .append(senderAddress);
                                getLogger().error(errorBuffer.toString());
                            }
                            return;
                        }
                    }
                }
            }
            rcptColl.add(recipientAddress);
            state.put(RCPT_LIST, rcptColl);
            responseBuffer.append("250 Recipient <")
                          .append(recipient)
                          .append("> OK");
            responseString = clearResponseBuffer();
            writeLoggedFlushedResponse(responseString);
        }
    }

    /**
     * Handler method called upon receipt of a NOOP command.
     * Just sends back an OK and logs the command.
     *
     * @param argument the argument passed in with the command by the SMTP client
     */
    private void doNOOP(String argument) {
        String responseString = "250 OK";
        writeLoggedFlushedResponse(responseString);
    }

    /**
     * Handler method called upon receipt of a RSET command.
     * Resets message-specific, but not authenticated user, state.
     *
     * @param argument the argument passed in with the command by the SMTP client
     */
    private void doRSET(String argument) {
        String responseString = "";
        if ((argument == null) || (argument.length() == 0)) {
            responseString = "250 OK";
            resetState();
        } else {
            responseString = "500 Unexpected argument provided with RSET command";
        }
        writeLoggedFlushedResponse(responseString);
    }

    /**
     * Handler method called upon receipt of a DATA command.
     * Reads in message data, creates header, and delivers to
     * mail server service for delivery.
     *
     * @param argument the argument passed in with the command by the SMTP client
     */
    private void doDATA(String argument) {
        String responseString = null;
        if ((argument != null) && (argument.length() > 0)) {
            responseString = "500 Unexpected argument provided with DATA command";
            writeLoggedFlushedResponse(responseString);
        }
        if (!state.containsKey(SENDER)) {
            responseString = "503 No sender specified";
            writeLoggedFlushedResponse(responseString);
        } else if (!state.containsKey(RCPT_LIST)) {
            responseString = "503 No recipients specified";
            writeLoggedFlushedResponse(responseString);
        } else {
            responseString = "354 Ok Send data ending with <CRLF>.<CRLF>";
            writeLoggedFlushedResponse(responseString);
            InputStream msgIn = new CharTerminatedInputStream(in, SMTPTerminator);
            try {
                msgIn = new BytesReadResetInputStream(msgIn,
                                                      theWatchdog,
                                                      theConfigData.getResetLength());

                // if the message size limit has been set, we'll
                // wrap msgIn with a SizeLimitedInputStream
                long maxMessageSize = theConfigData.getMaxMessageSize();
                if (maxMessageSize > 0) {
                    if (getLogger().isDebugEnabled()) {
                        StringBuffer logBuffer =
                            new StringBuffer(128)
                                    .append("Using SizeLimitedInputStream ")
                                    .append(" with max message size: ")
                                    .append(maxMessageSize);
                        getLogger().debug(logBuffer.toString());
                    }
                    msgIn = new SizeLimitedInputStream(msgIn, maxMessageSize);
                }
                // Removes the dot stuffing
                msgIn = new SMTPInputStream(msgIn);
                // Parse out the message headers
                MailHeaders headers = new MailHeaders(msgIn);
                headers = processMailHeaders(headers);
                processMail(headers, msgIn);
                headers = null;
            } catch (MessagingException me) {
                // Grab any exception attached to this one.
                Exception e = me.getNextException();
                // If there was an attached exception, and it's a
                // MessageSizeException
                if (e != null && e instanceof MessageSizeException) {
                    // Add an item to the state to suppress
                    // logging of extra lines of data
                    // that are sent after the size limit has
                    // been hit.
                    state.put(MESG_FAILED, Boolean.TRUE);
                    // then let the client know that the size
                    // limit has been hit.
                    responseString = "552 Error processing message: "
                                + e.getMessage();
                    StringBuffer errorBuffer =
                        new StringBuffer(256)
                            .append("Rejected message from ")
                            .append(state.get(SENDER).toString())
                            .append(" from host ")
                            .append(remoteHost)
                            .append(" (")
                            .append(remoteIP)
                            .append(") exceeding system maximum message size of ")
                            .append(theConfigData.getMaxMessageSize());
                    getLogger().error(errorBuffer.toString());
                } else {
                    responseString = "451 Error processing message: "
                                + me.getMessage();
                    getLogger().error("Unknown error occurred while processing DATA.", me);
                }
                writeLoggedFlushedResponse(responseString);
                return;
            } finally {
                if (msgIn != null) {
                    try {
                        msgIn.close();
                    } catch (Exception e) {
                        // Ignore close exception
                    }
                    msgIn = null;
                }
            }
            resetState();
            responseString = "250 Message received";
            writeLoggedFlushedResponse(responseString);
        }
    }

    private MailHeaders processMailHeaders(MailHeaders headers)
        throws MessagingException {
        // If headers do not contains minimum REQUIRED headers fields,
        // add them
        if (!headers.isSet(RFC2822Headers.DATE)) {
            headers.setHeader(RFC2822Headers.DATE, rfc822DateFormat.format(new Date()));
        }
        if (!headers.isSet(RFC2822Headers.FROM) && state.get(SENDER) != null) {
            headers.setHeader(RFC2822Headers.FROM, state.get(SENDER).toString());
        }
        // Determine the Return-Path
        String returnPath = headers.getHeader(RFC2822Headers.RETURN_PATH, "\r\n");
        headers.removeHeader(RFC2822Headers.RETURN_PATH);
        StringBuffer headerLineBuffer = new StringBuffer(512);
        if (returnPath == null) {
            if (state.get(SENDER) == null) {
                returnPath = "<>";
            } else {
                headerLineBuffer.append("<")
                                .append(state.get(SENDER))
                                .append(">");
                returnPath = headerLineBuffer.toString();
                headerLineBuffer.delete(0, headerLineBuffer.length());
            }
        }
        // We will rebuild the header object to put Return-Path and our
        // Received header at the top
        Enumeration headerLines = headers.getAllHeaderLines();
        MailHeaders newHeaders = new MailHeaders();
        // Put the Return-Path first
        newHeaders.addHeaderLine(RFC2822Headers.RETURN_PATH + ": " + returnPath);
        // Put our Received header next
        headerLineBuffer.append(RFC2822Headers.RECEIVED + ": from ")
                        .append(remoteHost)
                        .append(" ([")
                        .append(remoteIP)
                        .append("])");

        newHeaders.addHeaderLine(headerLineBuffer.toString());
        headerLineBuffer.delete(0, headerLineBuffer.length());

        headerLineBuffer.append("          by ")
                        .append(theConfigData.getHelloName())
                        .append(" (")
                        .append(SOFTWARE_TYPE)
                        .append(") with SMTP ID ")
                        .append(smtpID);

        if (((Collection) state.get(RCPT_LIST)).size() == 1) {
            // Only indicate a recipient if they're the only recipient
            // (prevents email address harvesting and large headers in
            //  bulk email)
            newHeaders.addHeaderLine(headerLineBuffer.toString());
            headerLineBuffer.delete(0, headerLineBuffer.length());
            headerLineBuffer.append("          for <")
                            .append(((List) state.get(RCPT_LIST)).get(0).toString())
                            .append(">;");
            newHeaders.addHeaderLine(headerLineBuffer.toString());
            headerLineBuffer.delete(0, headerLineBuffer.length());
        } else {
            // Put the ; on the end of the 'by' line
            headerLineBuffer.append(";");
            newHeaders.addHeaderLine(headerLineBuffer.toString());
            headerLineBuffer.delete(0, headerLineBuffer.length());
        }
        headerLineBuffer = null;
        newHeaders.addHeaderLine("          " + rfc822DateFormat.format(new Date()));

        // Add all the original message headers back in next
        while (headerLines.hasMoreElements()) {
            newHeaders.addHeaderLine((String) headerLines.nextElement());
        }
        return newHeaders;
    }

    /**
     * Processes the mail message coming in off the wire.  Reads the
     * content and delivers to the spool.
     *
     * @param headers the headers of the mail being read
     * @param msgIn the stream containing the message content
     */
    private void processMail(MailHeaders headers, InputStream msgIn)
        throws MessagingException {
        ByteArrayInputStream headersIn = null;
        MailImpl mail = null;
        List recipientCollection = null;
        try {
            headersIn = new ByteArrayInputStream(headers.toByteArray());
            recipientCollection = (List) state.get(RCPT_LIST);
            mail =
                new MailImpl(theConfigData.getMailServer().getId(),
                             (MailAddress) state.get(SENDER),
                             recipientCollection,
                             new SequenceInputStream(headersIn, msgIn));
            // Call mail.getSize() to force the message to be
            // loaded. Need to do this to enforce the size limit
            if (theConfigData.getMaxMessageSize() > 0) {
                mail.getMessageSize();
            }
            mail.setRemoteHost(remoteHost);
            mail.setRemoteAddr(remoteIP);
            theConfigData.getMailServer().sendMail(mail);
            Collection theRecipients = mail.getRecipients();
            String recipientString = "";
            if (theRecipients != null) {
                recipientString = theRecipients.toString();
            }
            if (getLogger().isDebugEnabled()) {
                StringBuffer debugBuffer =
                    new StringBuffer(256)
                        .append("Successfully spooled mail )")
                        .append(mail.getName())
                        .append(" from ")
                        .append(mail.getSender())
                        .append(" for ")
                        .append(recipientString);
                getLogger().debug(debugBuffer.toString());
            } else if (getLogger().isInfoEnabled()) {
                StringBuffer infoBuffer =
                    new StringBuffer(256)
                        .append("Successfully spooled mail from ")
                        .append(mail.getSender())
                        .append(" for ")
                        .append(recipientString);
                getLogger().info(infoBuffer.toString());
            }
        } finally {
            if (recipientCollection != null) {
                recipientCollection.clear();
            }
            recipientCollection = null;
            if (mail != null) {
                mail.dispose();
            }
            mail = null;
            if (headersIn != null) {
                try {
                    headersIn.close();
                } catch (IOException ioe) {
                    // Ignore exception on close.
                }
            }
            headersIn = null;
        }
    }

    /**
     * Handler method called upon receipt of a QUIT command.
     * This method informs the client that the connection is
     * closing.
     *
     * @param argument the argument passed in with the command by the SMTP client
     */
    private void doQUIT(String argument) {

        String responseString = "";
        if ((argument == null) || (argument.length() == 0)) {
            responseBuffer.append("221 ")
                          .append(theConfigData.getHelloName())
                          .append(" Service closing transmission channel");
            responseString = clearResponseBuffer();
        } else {
            responseString = "500 Unexpected argument provided with QUIT command";
        }
        writeLoggedFlushedResponse(responseString);
    }

    /**
     * Handler method called upon receipt of a VRFY command.
     * This method informs the client that the command is
     * not implemented.
     *
     * @param argument the argument passed in with the command by the SMTP client
     */
    private void doVRFY(String argument) {
        String responseString = "502 VRFY is not supported";
        writeLoggedFlushedResponse(responseString);
    }

    /**
     * Handler method called upon receipt of a EXPN command.
     * This method informs the client that the command is
     * not implemented.
     *
     * @param argument the argument passed in with the command by the SMTP client
     */
    private void doEXPN(String argument) {

        String responseString = "502 EXPN is not supported";
        writeLoggedFlushedResponse(responseString);
    }

    /**
     * Handler method called upon receipt of a HELP command.
     * This method informs the client that the command is
     * not implemented.
     *
     * @param argument the argument passed in with the command by the SMTP client
     */
    private void doHELP(String argument) {

        String responseString = "502 HELP is not supported";
        writeLoggedFlushedResponse(responseString);
    }

    /**
     * Handler method called upon receipt of an unrecognized command.
     * Returns an error response and logs the command.
     *
     * @param command the command parsed by the SMTP client
     * @param argument the argument passed in with the command by the SMTP client
     */
    private void doUnknownCmd(String command, String argument) {
        responseBuffer.append("500 ")
                      .append(theConfigData.getHelloName())
                      .append(" Syntax error, command unrecognized: ")
                      .append(command);
        String responseString = clearResponseBuffer();
        writeLoggedFlushedResponse(responseString);
    }

    /**
     * A private inner class which serves as an adaptor
     * between the WatchdogTarget interface and this
     * handler class.
     */
    private class SMTPWatchdogTarget
        implements WatchdogTarget {

        /**
         * @see org.apache.james.util.watchdog.WatchdogTarget#execute()
         */
        public void execute() {
            SMTPHandler.this.idleClose();
        }

    }

}
