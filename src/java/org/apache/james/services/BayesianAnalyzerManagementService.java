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



package org.apache.james.services;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.sql.SQLException;

import org.apache.james.management.BayesianAnalyzerManagementException;

public interface BayesianAnalyzerManagementService {

    public static final String ROLE = "org.apache.james.services.BayesianAnalyzerManagementService";
    
    /**
     * Feed the BayesianAnalyter with spam
     * 
     * @param dir The directory in which the spam is located
     * @return count The count of added spam
     * @throws FileNotFoundException If the directory not exists
     * @throws IllegalArgumentException If the provided directory is not valid
     * @throws IOException 
     * @throws SQLException
     * @throws BayesianAnalyzerManagementException If the service is not configured
     */
    public int addSpam(String dir) throws FileNotFoundException, IllegalArgumentException, IOException, SQLException, BayesianAnalyzerManagementException;
    
    /**
     * Feed the BayesianAnalyzer with ham
     * 
     * @param dir The directory in which the ham is located
     * @return count The count of added ham
     * @throws FileNotFoundException If the directory not exists
     * @throws IllegalArgumentException If the provided directory is not valid
     * @throws IOException 
     * @throws SQLException
     * @throws BayesianAnalyzerManagementException If the service is not configured
     */
    public int addHam(String dir) throws FileNotFoundException, IllegalArgumentException, IOException, SQLException, BayesianAnalyzerManagementException;
}
