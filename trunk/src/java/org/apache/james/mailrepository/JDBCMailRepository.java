/*
 * Copyright (C) The Apache Software Foundation. All rights reserved.
 *
 * This software is published under the terms of the Apache Software License
 * version 1.1, a copy of which has been included with this distribution in
 * the LICENSE file.
 */
package org.apache.james.mailrepository;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;

import javax.mail.internet.MimeMessage;

import org.apache.avalon.cornerstone.services.datasource.DataSourceSelector;
import org.apache.avalon.cornerstone.services.store.Store;
import org.apache.avalon.cornerstone.services.store.StreamRepository;
import org.apache.avalon.excalibur.datasource.DataSourceComponent;
import org.apache.avalon.framework.activity.Initializable;
import org.apache.avalon.framework.component.Component;
import org.apache.avalon.framework.component.ComponentException;
import org.apache.avalon.framework.component.ComponentManager;
import org.apache.avalon.framework.component.Composable;
import org.apache.avalon.framework.configuration.Configurable;
import org.apache.avalon.framework.configuration.Configuration;
import org.apache.avalon.framework.configuration.ConfigurationException;
import org.apache.avalon.framework.configuration.DefaultConfiguration;
import org.apache.avalon.framework.context.Context;
import org.apache.avalon.framework.context.ContextException;
import org.apache.avalon.framework.context.Contextualizable;
import org.apache.avalon.framework.logger.AbstractLogEnabled;
import org.apache.james.context.AvalonContextUtilities;
import org.apache.james.core.MailImpl;
import org.apache.james.core.MimeMessageWrapper;
import org.apache.james.util.JDBCUtil;
import org.apache.james.util.Lock;
import org.apache.james.util.SqlResources;
import org.apache.mailet.Mail;
import org.apache.mailet.MailAddress;
import org.apache.mailet.MailRepository;

/**
 * Implementation of a MailRepository on a database.
 *
 * <p>Requires a configuration element in the .conf.xml file of the form:
 *  <br>&lt;repository destinationURL="db://&lt;datasource&gt;/&lt;table_name&gt;/&lt;repository_name&gt;"
 *  <br>            type="MAIL"
 *  <br>            model="SYNCHRONOUS"/&gt;
 *  <br>&lt;/repository&gt;
 * <p>destinationURL specifies..(Serge??)
 * <br>Type can be SPOOL or MAIL
 * <br>Model is currently not used and may be dropped
 *
 * <p>Requires a logger called MailRepository.
 *
 * @version 1.0.0, 24/04/1999
 */
public class JDBCMailRepository
    extends AbstractLogEnabled
    implements MailRepository, Component, Contextualizable, Composable, Configurable, Initializable {

    /**
     * Whether 'deep debugging' is turned on.
     */
    private static final boolean DEEP_DEBUG = false;

    /**
     * The Avalon componentManager used by the instance
     */
    private ComponentManager componentManager;

    /**
     * The Avalon context used by the instance
     */
    protected Context context;

    /**
     * A lock used to control access to repository elements, locking access
     * based on the key
     */
    private Lock lock;

    // Configuration elements

    /**
     * Destination URL for the repository.  See class description for more info
     */
    protected String destination;

    /**
     * The table name parsed from the destination URL
     */
    protected String tableName;

    /**
     * The repository name parsed from the destination URL
     */
    protected String repositoryName;

    /**
     * The name of the filestore to be used to store mail when configured to use dbfile mode.
     */
    protected String filestore;

    /**
     * The name of the SQL configuration file to be used to configure this repository.
     */
    protected String sqlFileName;

    /**
     * The stream repository used in dbfile mode
     */
    private StreamRepository sr = null;

    //The data-source for this repository

    /**
     * The selector used to obtain the JDBC datasource
     */
    protected DataSourceSelector datasources;

    /**
     * The JDBC datasource that provides the JDBC connection
     */
    protected DataSourceComponent datasource;

    /**
     * The name of the datasource used by this repository
     */
    protected String datasourceName;

    /**
     * Contains all of the sql strings for this component.
     */
    protected SqlResources sqlQueries;

    /**
     * The JDBCUtil helper class
     */
    protected JDBCUtil theJDBCUtil;

    /**
     * @see org.apache.avalon.framework.context.Contextualizable#contextualize(Context)
     */
    public void contextualize(final Context context)
            throws ContextException {
        this.context = context;
    }

    /**
     * @see org.apache.avalon.framework.component.Composable#compose(ComponentManager)
     */
    public void compose( final ComponentManager componentManager )
        throws ComponentException {
        StringBuffer logBuffer = null;
        if (getLogger().isDebugEnabled()) {
            logBuffer =
                new StringBuffer(64)
                        .append(this.getClass().getName())
                        .append(".compose()");
            getLogger().debug(logBuffer.toString());
        }
        // Get the DataSourceSelector service
        datasources = (DataSourceSelector)componentManager.lookup( DataSourceSelector.ROLE );
        this.componentManager = componentManager;

    }

    /**
     * @see org.apache.avalon.framework.configuration.Configurable#configure(Configuration)
     */
    public void configure(Configuration conf) throws ConfigurationException {
        if (getLogger().isDebugEnabled()) {
            getLogger().debug(this.getClass().getName() + ".configure()");
        }

        destination = conf.getAttribute("destinationURL");
        // normalize the destination, to simplify processing.
        if ( ! destination.endsWith("/") ) {
            destination += "/";
        }
        // Parse the DestinationURL for the name of the datasource,
        // the table to use, and the (optional) repository Key.
        // Split on "/", starting after "db://"
        List urlParams = new ArrayList();
        int start = 5;
        if (destination.startsWith("dbfile")) {
            //this is dbfile:// instead of db://
            start += 4;
        }
        int end = destination.indexOf('/', start);
        while ( end > -1 ) {
            urlParams.add(destination.substring(start, end));
            start = end + 1;
            end = destination.indexOf('/', start);
        }

        // Build SqlParameters and get datasource name from URL parameters
        if (urlParams.size() == 0) {
            StringBuffer exceptionBuffer =
                new StringBuffer(256)
                        .append("Malformed destinationURL - Must be of the format '")
                        .append("db://<data-source>[/<table>[/<repositoryName>]]'.  Was passed ")
                        .append(conf.getAttribute("destinationURL"));
            throw new ConfigurationException(exceptionBuffer.toString());
        }
        if (urlParams.size() >= 1) {
            datasourceName = (String)urlParams.get(0);
        }
        if (urlParams.size() >= 2) {
            tableName = (String)urlParams.get(1);
        }
        if (urlParams.size() >= 3) {
            repositoryName = "";
            for (int i = 2; i < urlParams.size(); i++) {
                if (i >= 3) {
                    repositoryName += '/';
                }
                repositoryName += (String)urlParams.get(i);
            }
        }

        if (getLogger().isDebugEnabled()) {
            StringBuffer logBuffer =
                new StringBuffer(128)
                        .append("Parsed URL: table = '")
                        .append(tableName)
                        .append("', repositoryName = '")
                        .append(repositoryName)
                        .append("'");
            getLogger().debug(logBuffer.toString());
        }

        filestore = conf.getChild("filestore").getValue(null);
        sqlFileName = conf.getChild("sqlFile").getValue();
        if (!sqlFileName.startsWith("file://")) {
            throw new ConfigurationException
                ("Malformed sqlFile - Must be of the format 'file://<filename>'.");
        }
        try {
            if (filestore != null) {
                Store store = (Store)componentManager.
                        lookup("org.apache.avalon.cornerstone.services.store.Store");
                //prepare Configurations for stream repositories
                DefaultConfiguration streamConfiguration
                    = new DefaultConfiguration( "repository",
                                                "generated:JDBCMailRepository.configure()" );

                streamConfiguration.setAttribute( "destinationURL", filestore );
                streamConfiguration.setAttribute( "type", "STREAM" );
                streamConfiguration.setAttribute( "model", "SYNCHRONOUS" );
                sr = (StreamRepository) store.select(streamConfiguration);

                if (getLogger().isDebugEnabled()) {
                    getLogger().debug("Got filestore for JdbcMailRepository: " + filestore);
                }
            }

            lock = new Lock();
            if (getLogger().isDebugEnabled()) {
                StringBuffer logBuffer =
                    new StringBuffer(128)
                            .append(this.getClass().getName())
                            .append(" created according to ")
                            .append(destination);
                getLogger().debug(logBuffer.toString());
            }
        } catch (Exception e) {
            final String message = "Failed to retrieve Store component:" + e.getMessage();
            getLogger().error(message, e);
            e.printStackTrace();
            throw new ConfigurationException(message, e);
        }
    }

    /**
     * Initialises the JDBC repository.
     * 1) Tests the connection to the database.
     * 2) Loads SQL strings from the SQL definition file,
     *     choosing the appropriate SQL for this connection,
     *     and performing paramter substitution,
     * 3) Initialises the database with the required tables, if necessary.
     *
     * @throws Exception if an error occurs
     */
    public void initialize() throws Exception {
        StringBuffer logBuffer = null;
        if (getLogger().isDebugEnabled()) {
            getLogger().debug(this.getClass().getName() + ".initialize()");
        }

        theJDBCUtil =
            new JDBCUtil() {
                protected void delegatedLog(String logString) {
                    JDBCMailRepository.this.getLogger().warn("JDBCMailRepository: " + logString);
                }
            };
        // Get the data-source required.
        datasource = (DataSourceComponent)datasources.select(datasourceName);

        // Test the connection to the database, by getting the DatabaseMetaData.
        Connection conn = datasource.getConnection();
        PreparedStatement createStatement = null;

        try {
            // Initialise the sql strings.

            File sqlFile = null;
            try {
                sqlFile = AvalonContextUtilities.getFile(context, sqlFileName);
            } catch (Exception e) {
                getLogger().fatalError(e.getMessage(), e);
                throw e;
            }

            String resourceName = "org.apache.james.mailrepository.JDBCMailRepository";

            if (getLogger().isDebugEnabled()) {
                logBuffer =
                    new StringBuffer(128)
                            .append("Reading SQL resources from file: ")
                            .append(sqlFile.getAbsolutePath())
                            .append(", section ")
                            .append(this.getClass().getName())
                            .append(".");
                getLogger().debug(logBuffer.toString());
            }

            // Build the statement parameters
            Map sqlParameters = new HashMap();
            if (tableName != null) {
                sqlParameters.put("table", tableName);
            }
            if (repositoryName != null) {
                sqlParameters.put("repository", repositoryName);
            }

            sqlQueries = new SqlResources();
            sqlQueries.init(sqlFile, this.getClass().getName(),
                            conn, sqlParameters);

            // Check if the required table exists. If not, create it.
            DatabaseMetaData dbMetaData = conn.getMetaData();
            // Need to ask in the case that identifiers are stored, ask the DatabaseMetaInfo.
            // Try UPPER, lower, and MixedCase, to see if the table is there.
            if (!(theJDBCUtil.tableExists(dbMetaData, tableName))) {
                // Users table doesn't exist - create it.
                createStatement =
                    conn.prepareStatement(sqlQueries.getSqlString("createTable", true));
                createStatement.execute();

                if (getLogger().isInfoEnabled()) {
                    logBuffer =
                        new StringBuffer(64)
                                .append("JdbcMailRepository: Created table '")
                                .append(tableName)
                                .append("'.");
                    getLogger().info(logBuffer.toString());
                }
            }

        } finally {
            theJDBCUtil.closeJDBCStatement(createStatement);
            theJDBCUtil.closeJDBCConnection(conn);
        }
    }

    /**
     * Releases a lock on a message identified by a key
     *
     * @param key the key of the message to be unlocked
     *
     * @return true if successfully released the lock, false otherwise
     */
    public synchronized boolean unlock(String key) {
        if (lock.unlock(key)) {
            if ((DEEP_DEBUG) && (getLogger().isDebugEnabled())) {
                StringBuffer debugBuffer =
                    new StringBuffer(256)
                            .append("Unlocked ")
                            .append(key)
                            .append(" for ")
                            .append(Thread.currentThread().getName())
                            .append(" @ ")
                            .append(new java.util.Date(System.currentTimeMillis()));
                getLogger().debug(debugBuffer.toString());
            }
            notifyAll();
            return true;
        } else {
            return false;
        }
    }

    /**
     * Obtains a lock on a message identified by a key
     *
     * @param key the key of the message to be locked
     *
     * @return true if successfully obtained the lock, false otherwise
     */
    public synchronized boolean lock(String key) {
        if (lock.lock(key)) {
            if ((DEEP_DEBUG) && (getLogger().isDebugEnabled())) {
                StringBuffer debugBuffer =
                    new StringBuffer(256)
                            .append("Locked ")
                            .append(key)
                            .append(" for ")
                            .append(Thread.currentThread().getName())
                            .append(" @ ")
                            .append(new java.util.Date(System.currentTimeMillis()));
                getLogger().debug(debugBuffer.toString());
            }
            return true;
        } else {
            return false;
        }
    }

    /**
     * Store this message to the database.  Optionally stores the message
     * body to the filesystem and only writes the headers to the database.
     */
    public void store(Mail mc) {
        Connection conn = null;
        try {
            conn = datasource.getConnection();

            //Need to determine whether need to insert this record, or update it.

            //Begin a transaction
            conn.setAutoCommit(false);

            PreparedStatement checkMessageExists = null;
            ResultSet rsExists = null;
            boolean exists = false;
            try {
                checkMessageExists =
                    conn.prepareStatement(sqlQueries.getSqlString("checkMessageExistsSQL", true));
                checkMessageExists.setString(1, mc.getName());
                checkMessageExists.setString(2, repositoryName);
                rsExists = checkMessageExists.executeQuery();
                exists = rsExists.next() && rsExists.getInt(1) > 0;
            } finally {
                theJDBCUtil.closeJDBCResultSet(rsExists);
                theJDBCUtil.closeJDBCStatement(checkMessageExists);
            }

            if (exists) {
                //Update the existing record
                PreparedStatement updateMessage = null;

                try {
                    updateMessage =
                        conn.prepareStatement(sqlQueries.getSqlString("updateMessageSQL", true));
                    updateMessage.setString(1, mc.getState());
                    updateMessage.setString(2, mc.getErrorMessage());
                    if (mc.getSender() == null) {
                        updateMessage.setNull(3, java.sql.Types.VARCHAR);
                    } else {
                        updateMessage.setString(3, mc.getSender().toString());
                    }
                    StringBuffer recipients = new StringBuffer();
                    for (Iterator i = mc.getRecipients().iterator(); i.hasNext(); ) {
                        recipients.append(i.next().toString());
                        if (i.hasNext()) {
                            recipients.append("\r\n");
                        }
                    }
                    updateMessage.setString(4, recipients.toString());
                    updateMessage.setString(5, mc.getRemoteHost());
                    updateMessage.setString(6, mc.getRemoteAddr());
                    updateMessage.setTimestamp(7, new java.sql.Timestamp(mc.getLastUpdated().getTime()));
                    updateMessage.setString(8, mc.getName());
                    updateMessage.setString(9, repositoryName);
                    updateMessage.execute();
                } finally {
                    Statement localUpdateMessage = updateMessage;
                    // Clear reference to statement
                    updateMessage = null;
                    theJDBCUtil.closeJDBCStatement(localUpdateMessage);
                }

                //Determine whether the message body has changed, and possibly avoid
                //  updating the database.
                MimeMessage messageBody = mc.getMessage();
                boolean saveBody = false;
                if (messageBody instanceof MimeMessageWrapper) {
                    MimeMessageWrapper message = (MimeMessageWrapper)messageBody;
                    saveBody = message.isModified();
                } else {
                    saveBody = true;
                }

                if (saveBody) {
                    try {
                        updateMessage =
                            conn.prepareStatement(sqlQueries.getSqlString("updateMessageBodySQL", true));
                        ByteArrayOutputStream headerOut = new ByteArrayOutputStream();
                        OutputStream bodyOut = null;
                        try {
                            if (sr == null) {
                                //If there is no filestore, use the byte array to store headers
                                //  and the body
                                bodyOut = headerOut;
                            } else {
                                //Store the body in the stream repository
                                bodyOut = sr.put(mc.getName());
                            }

                            //Write the message to the headerOut and bodyOut.  bodyOut goes straight to the file
                            MimeMessageWrapper.writeTo(messageBody, headerOut, bodyOut);

                            //Store the headers in the database
                            ByteArrayInputStream headerInputStream =
                                new ByteArrayInputStream(headerOut.toByteArray());
                            updateMessage.setBinaryStream(1, headerInputStream, headerOut.size());
                        } finally {
                            closeOutputStreams(headerOut, bodyOut);
                        }
                        updateMessage.setString(2, mc.getName());
                        updateMessage.setString(3, repositoryName);
                        updateMessage.execute();
                    } finally {
                        theJDBCUtil.closeJDBCStatement(updateMessage);
                    }
                }
            } else {
                //Insert the record into the database
                PreparedStatement insertMessage = null;
                try {
                    insertMessage =
                        conn.prepareStatement(sqlQueries.getSqlString("insertMessageSQL", true));
                    insertMessage.setString(1, mc.getName());
                    insertMessage.setString(2, repositoryName);
                    insertMessage.setString(3, mc.getState());
                    insertMessage.setString(4, mc.getErrorMessage());
                    if (mc.getSender() == null) {
                        insertMessage.setNull(5, java.sql.Types.VARCHAR);
                    } else {
                        insertMessage.setString(5, mc.getSender().toString());
                    }
                    StringBuffer recipients = new StringBuffer();
                    for (Iterator i = mc.getRecipients().iterator(); i.hasNext(); ) {
                        recipients.append(i.next().toString());
                        if (i.hasNext()) {
                            recipients.append("\r\n");
                        }
                    }
                    insertMessage.setString(6, recipients.toString());
                    insertMessage.setString(7, mc.getRemoteHost());
                    insertMessage.setString(8, mc.getRemoteAddr());
                    insertMessage.setTimestamp(9, new java.sql.Timestamp(mc.getLastUpdated().getTime()));
                    MimeMessage messageBody = mc.getMessage();

                    ByteArrayOutputStream headerOut = new ByteArrayOutputStream();
                    OutputStream bodyOut = null;
                    try {
                        if (sr == null) {
                            //If there is no sr, then use the same byte array to hold the headers
                            //  and the body
                            bodyOut = headerOut;
                        } else {
                            //Store the body in the file system.
                            bodyOut = sr.put(mc.getName());
                        }

                        //Write the message to the headerOut and bodyOut.  bodyOut goes straight to the file
                        MimeMessageWrapper.writeTo(messageBody, headerOut, bodyOut);

                        ByteArrayInputStream headerInputStream =
                            new ByteArrayInputStream(headerOut.toByteArray());
                        insertMessage.setBinaryStream(10, headerInputStream, headerOut.size());
                    } finally {
                        closeOutputStreams(headerOut, bodyOut);
                    }
                    //Store the headers in the database
                    insertMessage.execute();
                } finally {
                    theJDBCUtil.closeJDBCStatement(insertMessage);
                }
            }

            conn.commit();
            conn.setAutoCommit(true);

            synchronized (this) {
                notifyAll();
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Exception caught while storing mail Container: " + e);
        } finally {
            theJDBCUtil.closeJDBCConnection(conn);
        }
    }

    /**
     * Retrieves a message given a key. At the moment, keys can be obtained
     * from list()
     *
     * @param key the key of the message to retrieve
     * @return the mail corresponding to this key, null if none exists
     */
    public Mail retrieve(String key) {
        if (DEEP_DEBUG) {
            System.err.println("retrieving " + key);
        }
        Connection conn = null;
        PreparedStatement retrieveMessage = null;
        ResultSet rsMessage = null;
        try {
            conn = datasource.getConnection();
            if (DEEP_DEBUG) {
                System.err.println("got a conn " + key);
            }

            retrieveMessage =
                conn.prepareStatement(sqlQueries.getSqlString("retrieveMessageSQL", true));
            retrieveMessage.setString(1, key);
            retrieveMessage.setString(2, repositoryName);
            rsMessage = retrieveMessage.executeQuery();
            if (DEEP_DEBUG) {
                System.err.println("ran the query " + key);
            }
            if (!rsMessage.next()) {
                if (getLogger().isDebugEnabled()) {
                    StringBuffer debugBuffer =
                        new StringBuffer(64)
                                .append("Did not find a record ")
                                .append(key)
                                .append(" in ")
                                .append(repositoryName);
                    getLogger().debug(debugBuffer.toString());
                }
                return null;
            }
            MailImpl mc = new MailImpl();
            mc.setName(key);
            mc.setState(rsMessage.getString(1));
            mc.setErrorMessage(rsMessage.getString(2));
            String sender = rsMessage.getString(3);
            if (sender == null) {
                mc.setSender(null);
            } else {
                mc.setSender(new MailAddress(sender));
            }
            StringTokenizer st = new StringTokenizer(rsMessage.getString(4), "\r\n", false);
            Set recipients = new HashSet();
            while (st.hasMoreTokens()) {
                recipients.add(new MailAddress(st.nextToken()));
            }
            mc.setRecipients(recipients);
            mc.setRemoteHost(rsMessage.getString(5));
            mc.setRemoteAddr(rsMessage.getString(6));
            mc.setLastUpdated(rsMessage.getTimestamp(7));

            MimeMessageJDBCSource source = new MimeMessageJDBCSource(this, key, sr);
            MimeMessageWrapper message = new MimeMessageWrapper(source);
            mc.setMessage(message);
            return mc;
        } catch (SQLException sqle) {
            synchronized (System.err) {
                System.err.println("Error retrieving message");
                System.err.println(sqle.getMessage());
                System.err.println(sqle.getErrorCode());
                System.err.println(sqle.getSQLState());
                System.err.println(sqle.getNextException());
                sqle.printStackTrace();
            }
            throw new RuntimeException("Exception while retrieving mail: " + sqle.getMessage());
        } catch (Exception me) {
            me.printStackTrace();
            throw new RuntimeException("Exception while retrieving mail: " + me.getMessage());
        } finally {
            theJDBCUtil.closeJDBCResultSet(rsMessage);
            theJDBCUtil.closeJDBCStatement(retrieveMessage);
            theJDBCUtil.closeJDBCConnection(conn);
        }
    }

    /**
     * Removes a specified message
     *
     * @param mail the message to be removed from the repository
     */
    public void remove(Mail mail) {
        remove(mail.getName());
    }

    /**
     * Removes a message identified by a key.
     *
     * @param key the key of the message to be removed from the repository
     */
    public void remove(String key) {
        //System.err.println("removing " + key);
        if (lock(key)) {
            Connection conn = null;
            PreparedStatement removeMessage = null;
            try {
                conn = datasource.getConnection();
                removeMessage =
                    conn.prepareStatement(sqlQueries.getSqlString("removeMessageSQL", true));
                removeMessage.setString(1, key);
                removeMessage.setString(2, repositoryName);
                removeMessage.execute();

                if (sr != null) {
                    sr.remove(key);
                }
            } catch (Exception me) {
                throw new RuntimeException("Exception while removing mail: " + me.getMessage());
            } finally {
                theJDBCUtil.closeJDBCStatement(removeMessage);
                theJDBCUtil.closeJDBCConnection(conn);
                unlock(key);
            }
        }
    }

    /**
     * Gets a list of message keys stored in this repository.
     *
     * @return an Iterator of the message keys
     */
    public Iterator list() {
        //System.err.println("listing messages");
        Connection conn = null;
        PreparedStatement listMessages = null;
        ResultSet rsListMessages = null;
        try {
            conn = datasource.getConnection();
            listMessages =
                conn.prepareStatement(sqlQueries.getSqlString("listMessagesSQL", true));
            listMessages.setString(1, repositoryName);
            rsListMessages = listMessages.executeQuery();

            List messageList = new ArrayList();
            while (rsListMessages.next() && !Thread.currentThread().isInterrupted()) {
                messageList.add(rsListMessages.getString(1));
            }
            return messageList.iterator();
        } catch (Exception me) {
            me.printStackTrace();
            throw new RuntimeException("Exception while listing mail: " + me.getMessage());
        } finally {
            theJDBCUtil.closeJDBCResultSet(rsListMessages);
            theJDBCUtil.closeJDBCStatement(listMessages);
            theJDBCUtil.closeJDBCConnection(conn);
        }
    }

    /**
     * Gets the SQL connection to be used by this JDBCMailRepository
     *
     * @return the connection
     * @throws SQLException if there is an issue with getting the connection
     */
    protected Connection getConnection() throws SQLException {
        return datasource.getConnection();
    }

    /**
     * @see java.lang.Object#equals(Object)
     */
    public boolean equals(Object obj) {
        if (!(obj instanceof JDBCMailRepository)) {
            return false;
        }
        // TODO: Figure out whether other instance variables should be part of
        // the equals equation
        JDBCMailRepository repository = (JDBCMailRepository)obj;
        return  ((repository.tableName == tableName) || ((repository.tableName != null) && repository.tableName.equals(tableName))) &&
                ((repository.repositoryName == repositoryName) || ((repository.repositoryName != null) && repository.repositoryName.equals(repositoryName)));
    }

    /**
     * Provide a hash code that is consistent with equals for this class
     *
     * @return the hash code
     */
     public int hashCode() {
        int result = 17;
        if (tableName != null) {
            result = 37 * tableName.hashCode();
        }
        if (repositoryName != null) {
            result = 37 * repositoryName.hashCode();
        }
        return result;
     }

    /**
     * Closes output streams used to update message
     *
     * @headerStream the stream containing header information - potentially the same
     *               as the body stream
     * @bodyStream the stream containing body information
     */
    private void closeOutputStreams(OutputStream headerStream, OutputStream bodyStream) {
        try {
            // If the header stream is not the same as the body stream,
            // close the header stream here.
            if ((headerStream != null) && (headerStream != bodyStream)) {
                headerStream.close();
            }
        } catch (IOException ioe) {
            getLogger().debug("JDBCMailRepository: Unexpected exception while closing output stream.");
        }
        try {
            if (bodyStream != null) {
                bodyStream.close();
            }
        } catch (IOException ioe) {
            getLogger().debug("JDBCMailRepository: Unexpected exception while closing output stream.");
        }
    }
}
