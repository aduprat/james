/*
 * Copyright (C) The Apache Software Foundation. All rights reserved.
 *
 * This software is published under the terms of the Apache Software License
 * version 1.1, a copy of which has been included with this distribution in
 * the LICENSE file.
 */
package org.apache.james.util;

import org.apache.oro.text.perl.MalformedPerl5PatternException;
import org.apache.oro.text.perl.Perl5Util;
import org.w3c.dom.*;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;


/**
 * Provides a set of SQL String resources (eg SQL Strings)
 * to use for a database connection.
 * This class allows SQL strings to be customised to particular
 * database products, by detecting product information from the
 * jdbc DatabaseMetaData object.
 * 
 * @author Darrell DeBoer <dd@bigdaz.com>
 */
public class SqlResources
{
    /**
     * Configures a DbResources object to provide SQL statements from a file.
     * 
     * SQL statements returned may be specific to the particular type
     * and version of the connected database, as well as the database driver.
     * 
     * Parameters encoded as $(parameter} in the input file are
     * replace by values from the parameters Map, if the named parameter exists.
     * Parameter values may also be specified in the resourceSection element.
     * 
     * @param sqlFile    the input file containing the string definitions
     * @param sqlDefsSection
     *                   the xml element containing the strings to be used
     * @param conn the Jdbc DatabaseMetaData, taken from a database connection
     * @param configParameters a map of parameters (name-value string pairs) which are
     *                   replaced where found in the input strings
     */
    public void init(File sqlFile, String sqlDefsSection,
                     Connection conn, Map configParameters)
        throws Exception
    {
        // Parse the sqlFile as an XML document.
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document sqlDoc = builder.parse(sqlFile);

        // First process the database matcher, to determine the
        //  sql statements to use.
        Element dbMatcherElement = 
            (Element)(sqlDoc.getElementsByTagName("dbMatchers").item(0));
        String dbProduct = null;
        if ( dbMatcherElement != null ) {
            dbProduct = matchDbConnection(conn, dbMatcherElement);
        }

        // Now get the section defining sql for the repository required.
        NodeList sections = sqlDoc.getElementsByTagName("sqlDefs");
        int sectionsCount = sections.getLength();
        Element sectionElement = null;
        for (int i = 0; i < sectionsCount; i++ ) {
            sectionElement = (Element)(sections.item(i));
            String sectionName = sectionElement.getAttribute("name");
            if ( sectionName != null && sectionName.equals(sqlDefsSection) ) {
                break;
            }

        }
        if ( sectionElement == null ) {
            StringBuffer exceptionBuffer =
                new StringBuffer(64)
                        .append("Error loading sql definition file. ")
                        .append("The element named \'")
                        .append(sqlDefsSection)
                        .append("\' does not exist.");
            throw new RuntimeException(exceptionBuffer.toString());
        }

        // Get parameters defined within the file as defaults,
        // and use supplied parameters as overrides.
        Map parameters = new HashMap();
        // First read from the <params> element, if it exists.
        Element parametersElement = 
            (Element)(sectionElement.getElementsByTagName("parameters").item(0));
        if ( parametersElement != null ) {
            NamedNodeMap params = parametersElement.getAttributes();
            int paramCount = params.getLength();
            for (int i = 0; i < paramCount; i++ ) {
                Attr param = (Attr)params.item(i);
                String paramName = param.getName();
                String paramValue = param.getValue();
                parameters.put(paramName, paramValue);
            }
        }
        // Then copy in the parameters supplied with the call.
        parameters.putAll(configParameters);

        // 2 maps - one for storing default statements,
        // the other for statements with a "db" attribute matching this 
        // connection.
        Map defaultSqlStatements = new HashMap();
        Map dbProductSqlStatements = new HashMap();

        // Process each sql statement, replacing string parameters,
        // and adding to the appropriate map..
        NodeList sqlDefs = sectionElement.getElementsByTagName("sql");
        int sqlCount = sqlDefs.getLength();
        for ( int i = 0; i < sqlCount; i++ ) {
            // See if this needs to be processed (is default or product specific)
            Element sqlElement = (Element)(sqlDefs.item(i));
            String sqlDb = sqlElement.getAttribute("db");
            Map sqlMap;
            if ( sqlDb.equals("")) {
                // default
                sqlMap = defaultSqlStatements;
            }
            else if (sqlDb.equals(dbProduct) ) {
                // Specific to this product
                sqlMap = dbProductSqlStatements;
            }
            else {
                // for a different product
                continue;
            }

            // Get the key and value for this SQL statement.
            String sqlKey = sqlElement.getAttribute("name");
            if ( sqlKey == null ) {
                // ignore statements without a "name" attribute.
                continue;
            }
            String sqlString = sqlElement.getFirstChild().getNodeValue();

            // Do parameter replacements for this sql string.
            Iterator paramNames = parameters.keySet().iterator();
            while ( paramNames.hasNext() ) {
                String paramName = (String)paramNames.next();
                String paramValue = (String)parameters.get(paramName);

                StringBuffer replaceBuffer =
                    new StringBuffer(64)
                            .append("${")
                            .append(paramName)
                            .append("}");
                sqlString = substituteSubString(sqlString, replaceBuffer.toString(), paramValue);
            }

            // Add to the sqlMap - either the "default" or the "product" map
            sqlMap.put(sqlKey, sqlString);
        }

        // Copy in default strings, then overwrite product-specific ones.
        m_sql.putAll(defaultSqlStatements);
        m_sql.putAll(dbProductSqlStatements);
    }

    /**
     * Compares the DatabaseProductName value for a jdbc Connection
     * against a set of regular expressions defined in XML.
     * The first successful match defines the name of the database product
     * connected to. This value is then used to choose the specific SQL 
     * expressions to use.
     *
     */
    private String matchDbConnection(Connection conn, 
                                     Element dbMatchersElement)
        throws MalformedPerl5PatternException, SQLException
    {
        String dbProductName = conn.getMetaData().getDatabaseProductName();
    
        NodeList dbMatchers = 
            dbMatchersElement.getElementsByTagName("dbMatcher");
        for ( int i = 0; i < dbMatchers.getLength(); i++ ) {
            // Get the values for this matcher element.
            Element dbMatcher = (Element)dbMatchers.item(i);
            String dbMatchName = dbMatcher.getAttribute("db");
            StringBuffer dbProductPatternBuffer =
                new StringBuffer(64)
                        .append("/")
                        .append(dbMatcher.getAttribute("databaseProductName"))
                        .append("/i");

            // If the connection databaseProcuctName matches the pattern,
            // use the match name from this matcher.
            if ( m_perl5Util.match(dbProductPatternBuffer.toString(), dbProductName) ) {
                return dbMatchName;
            }
        }
        return null;
    }

    /**
     * Replace substrings of one string with another string and return altered string.
     * @param input input string
     * @param find the string to replace
     * @param replace the string to replace with
     * @return the substituted string
     */
    private String substituteSubString( String input, 
                                        String find,
                                        String replace )
    {
        int find_length = find.length();
        int replace_length = replace.length();

        StringBuffer output = new StringBuffer(input);
        int index = input.indexOf(find);
        int outputOffset = 0;

        while ( index > -1 ) {
            output.replace(index + outputOffset, index + outputOffset + find_length, replace);
            outputOffset = outputOffset - find_length + replace_length;

            index = input.indexOf(find, index + replace_length);
        }

        String result = output.toString();
        return result;
    }

    /**
     * Returns a named SQL string for the specified connection,
     * replacing parameters with the values set.
     * 
     * @param name   the name of the SQL resource required.
     * @return the requested resource
     */
    public String getSqlString(String name)
    {
        return (String)m_sql.get(name);
    }

    /**
     * Returns a named SQL string for the specified connection,
     * replacing parameters with the values set.
     * 
     * @param name     the name of the SQL resource required.
     * @param required true if the resource is required
     * @return the requested resource
     * @exception ConfigurationException
     *                   if a required resource cannot be found.
     */
    public String getSqlString(String name, boolean required)
    {
        String sql = getSqlString(name);

        if ( sql == null ) {
            StringBuffer exceptionBuffer =
                new StringBuffer(64)
                        .append("Required SQL resource: '")
                        .append(name)
                        .append("' was not found.");
            throw new RuntimeException(exceptionBuffer.toString());
        }
        return sql;
    }

    private Map m_sql = new HashMap();
    private Perl5Util m_perl5Util = new Perl5Util();
}
