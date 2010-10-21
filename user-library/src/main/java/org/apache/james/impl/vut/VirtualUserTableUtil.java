/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *   http://www.apache.org/licenses/LICENSE-2.0                 *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/
package org.apache.james.impl.vut;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.StringTokenizer;

import org.apache.james.util.XMLResources;
import org.apache.james.vut.api.VirtualUserTable;
import org.apache.mailet.MailAddress;
import org.apache.oro.text.regex.MalformedPatternException;
import org.apache.oro.text.regex.MatchResult;
import org.apache.oro.text.regex.Pattern;
import org.apache.oro.text.regex.Perl5Compiler;
import org.apache.oro.text.regex.Perl5Matcher;

/**
 * This helper class contains methods for the VirtualUserTable implementations
 */
public class VirtualUserTableUtil {

    private VirtualUserTableUtil() {}
    
    // @deprecated QUERY is deprecated - SQL queries are now located in sqlResources.xml
    public static String QUERY = "select VirtualUserTable.target_address from VirtualUserTable, VirtualUserTable as VUTDomains where (VirtualUserTable.user like ? or VirtualUserTable.user like '\\%') and (VirtualUserTable.domain like ? or (VirtualUserTable.domain like '%*%' and VUTDomains.domain like ?)) order by concat(VirtualUserTable.user,'@',VirtualUserTable.domain) desc limit 1";
    
    /**
     * Processes regex virtual user mapping
     *
     * If a mapped target string begins with the prefix regex:, it must be
     * formatted as regex:<regular-expression>:<parameterized-string>,
     * e.g., regex:(.*)@(.*):${1}@tld
     *
     * @param address the MailAddress to be mapped
     * @param targetString a String specifying the mapping
     * @throws MalformedPatternException 
     */
    public static String regexMap( MailAddress address, String targetString) throws MalformedPatternException {
        String result = null;
        int identifierLength = VirtualUserTable.REGEX_PREFIX.length();

        int msgPos = targetString.indexOf(':', identifierLength + 1);

        // Throw exception on invalid format
        if (msgPos < identifierLength + 1) throw new MalformedPatternException("Regex should be formatted as regex:<regular-expression>:<parameterized-string>");
        
        // log("regex: targetString = " + targetString);
        // log("regex: msgPos = " + msgPos);
        // log("regex: compile " + targetString.substring("regex:".length(), msgPos));
        // log("regex: address = " + address.toString());
        // log("regex: replace = " + targetString.substring(msgPos + 1));

        Pattern pattern = new Perl5Compiler().compile(targetString.substring(identifierLength, msgPos));
        Perl5Matcher matcher = new Perl5Matcher();

        if (matcher.matches(address.toString(), pattern)) {
            MatchResult match = matcher.getMatch();
            Map parameters = new HashMap(match.groups());
            for (int i = 1; i < match.groups(); i++) {
                parameters.put(Integer.toString(i), match.group(i));
            }
            result = XMLResources.replaceParameters(targetString.substring(msgPos + 1), parameters);
        }    
        return result;
     }
     
    /**
     * Returns the real recipient given a virtual username and domain.
     * 
     * @param user the virtual user
     * @param domain the virtual domain
     * @return the real recipient address, or <code>null</code> if no mapping exists
     */
    public static String getTargetString(String user, String domain, Map mappings) {
         StringBuffer buf;
         String target;
         
         //Look for exact (user@domain) match
         buf = new StringBuffer().append(user).append("@").append(domain);
         target = (String)mappings.get(buf.toString());
         if (target != null) {
             return target;
         }
         
         //Look for user@* match
         buf = new StringBuffer().append(user).append("@*");
         target = (String)mappings.get(buf.toString());
         if (target != null) {
             return target;
         }
         
         //Look for *@domain match
         buf = new StringBuffer().append("*@").append(domain);
         target = (String)mappings.get(buf.toString());
         if (target != null) {
             return target;
         }
         
         return null;
     }
     
     /**
      * Returns the character used to delineate multiple addresses.
      * 
      * @param targetString the string to parse
      * @return the character to tokenize on
      */
     public static String getSeparator(String targetString) {
        return (targetString.indexOf(',') > -1 ? "," : (targetString
        .indexOf(';') > -1 ? ";" : ((targetString.indexOf(VirtualUserTable.ERROR_PREFIX) > -1 
            || targetString.indexOf(VirtualUserTable.REGEX_PREFIX) > -1 || targetString.indexOf(VirtualUserTable.ALIASDOMAIN_PREFIX) > -1)? "" : ":")));
     }
     
     /**
      * Returns a Map which contains the mappings
      * 
      * @param mapping A String which contains a list of mappings
      * @return Map which contains the mappings
      */
     public static Map getXMLMappings(String mapping) {
         Map mappings = new HashMap();
         StringTokenizer tokenizer = new StringTokenizer(mapping, ",");
         while(tokenizer.hasMoreTokens()) {
           String mappingItem = tokenizer.nextToken();
           int index = mappingItem.indexOf('=');
           String virtual = mappingItem.substring(0, index).trim().toLowerCase();
           String real = mappingItem.substring(index + 1).trim().toLowerCase();
           mappings.put(virtual, real);
         }
         return mappings;
     }
     
     
    /**
     * Return a Collection which holds the extracted mappings of the given String
     * 
     * @param rawMapping
     * @deprecated Use mappingToCollection(String rawMapping)
     */
     public static Collection<String> getMappings(String rawMapping) {
        return mappingToCollection(rawMapping);
     }
     
     /**
      * Convert a raw mapping String to a Collection
      * 
      * @param rawMapping the mapping String
      * @return map a collection which holds all mappings
      */
     public static ArrayList<String> mappingToCollection(String rawMapping) {
         ArrayList<String> map = new ArrayList<String>();
         StringTokenizer tokenizer = new StringTokenizer(rawMapping,
         VirtualUserTableUtil.getSeparator(rawMapping));
         while (tokenizer.hasMoreTokens()) {
             final String raw = tokenizer.nextToken().trim();
             map.add(raw);
         }
         return map;
     }
     
     /**
      * Convert a Collection which holds mappings to a raw mapping String
      * 
      * @param map the Collection
      * @return mapping the mapping String
      */
     public static String CollectionToMapping(Collection map) {
         StringBuffer mapping = new StringBuffer();
         Iterator mappings = map.iterator();
         while (mappings.hasNext()) {
             mapping.append(mappings.next());
             if (mappings.hasNext()) {
                 mapping.append(";");
             }
         }  
         return mapping.toString();  
    }
     
}
