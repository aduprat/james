/*****************************************************************************
 * Copyright (C) The Apache Software Foundation. All rights reserved.        *
 * ------------------------------------------------------------------------- *
 * This software is published under the terms of the Apache Software License *
 * version 1.1, a copy of which has been included  with this distribution in *
 * the LICENSE file.                                                         *
 *****************************************************************************/

package org.apache.james.usermanager;

import org.apache.avalon.blocks.*;
import org.apache.avalon.*;
import org.apache.avalon.utils.*;
import java.util.*;
import java.io.*;

/**
 * Interface for a Repository to store users.
 * @version 1.0.0, 24/04/1999
 * @author  Federico Barbieri <scoobie@pop.systemy.it>
 */
public interface UsersRepository extends Store.Repository {

    public final static String USER = "USER";

 
        

    public void addUser(String name, Object attributes) ;

    public Object getAttributes(String name) ;
    
    public void removeUser(String name) ;
    
    public boolean contains(String name) ;
    
    public boolean test(String name, Object attributes) ;
    
    public int countUsers() ;

    public String getDomains();
    
}

    
