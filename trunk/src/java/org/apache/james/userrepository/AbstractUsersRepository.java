/*
 * Copyright (C) The Apache Software Foundation. All rights reserved.
 *
 * This software is published under the terms of the Apache Software License
 * version 1.1, a copy of which has been included with this distribution in
 * the LICENSE file.
 */
package org.apache.james.userrepository;

import java.util.*;
import org.apache.james.services.*;
import org.apache.avalon.framework.logger.AbstractLoggable;

/**
 * A partial implementation of a Repository to store users on the File System.
 * <p>This implements common functionality found in different UsersRespository 
 * implementations, and makes it easier to create new User repositories.</p>
 *
 * @author Darrell DeBoer <dd@bigdaz.com>
 * @author Charles Benett <charles@benett1.demon.co.uk>
 */
public abstract class AbstractUsersRepository
    extends AbstractLoggable
    implements UsersRepository
{
    //
    // Core Abstract methods - override these for a functional UserRepository.
    //
    /**
     * Returns a list populated with all of the Users in the repository.
     * @return an <code>Iterator</code> of <code>JamesUser</code>s.
     */
    protected abstract Iterator listAllUsers();

    /**
     * Adds a user to the underlying Repository.
     * The user name must not clash with an existing user.
     */
    protected abstract void doAddUser(DefaultJamesUser user);

    /**
     * Removes a user from the underlying repository.
     * If the user doesn't exist, returns ok.
     */
    protected abstract void doRemoveUser(DefaultJamesUser user);

    //
    // Extended protected methods.
    // These provide very basic default implementations, which will work,
    // but may need to be overridden by subclasses for performance reasons.
    //
    /**
     * Updates a user record to match the supplied User.
     * This is a very basic, remove-then-insert implementation,
     * which should really be overridden, but will work.
     */
    protected void doUpdateUser(DefaultJamesUser user)
    {
        doRemoveUser(user);
        doAddUser(user);
    }

    /**
     * Produces the complete list of User names, with correct case.
     * @return a <code>List</code> of <code>String</code>s representing
     *         user names.
     */
    protected List listUserNames()
    {
        Iterator users = listAllUsers();
        List userNames = new LinkedList();
        while ( users.hasNext() ) {
            JamesUser user = (JamesUser)users.next();
            userNames.add(user.getUserName());
        }

        return userNames;
    }

    /**
     * Gets a user by name, ignoring case if specified.
     * This implementation gets the entire set of users,
     *  and scrolls through searching for one matching <code>name</code>.
     */
    protected User getUserByName(String name, boolean ignoreCase)
    {
        // Just iterate through all of the users until we find one matching.
        Iterator users = listAllUsers();
        while ( users.hasNext() ) 
        {
            JamesUser user = (JamesUser)users.next();
            String username = user.getUserName();
            if (( !ignoreCase && username.equals(name) ) ||
                ( ignoreCase && username.equalsIgnoreCase(name) )) {
                return user;
            }
        }
        // Not found - return null
        return null;
    }

    //
    // User conversion.
    //
    /**
     * This method is hackery to get JamesUser instances, regardless of what 
     * user implementation is passed.
     * Ideally, the UsersRepository interface would be updated to accept only 
     *  JamesUsers, or we could combine the 2.
     */
    protected DefaultJamesUser getJamesUser(User user)
    {
        // Return JamesUser instances directly.
        if ( user instanceof DefaultJamesUser ) {
            return (DefaultJamesUser)user;
        }
        // Build a JamesUser with a DefaultUser.
        else if ( user instanceof DefaultUser ) {
            DefaultUser defaultUser = (DefaultUser)user;
            return new DefaultJamesUser(defaultUser.getUserName(), 
                                        defaultUser.getHashedPassword(), 
                                        defaultUser.getHashAlgorithm());
        }
        // Shouldn't be any other implementations.
        else {
            throw new RuntimeException("An unknown implementation of User was found. This implementation cannot be persisted to a UsersJDBCRepsitory.");
        }

    }

    //
    // UsersRepository interface implementation.
    //
    /**
     * Adds a user to the repository with the specified User object.
     * Users names must be unique-case-insensitive in the repository.
     *
     * @returns true if succesful, false otherwise
     * @since James 1.2.2
     */
    public boolean addUser(User user)
    {
        String username = user.getUserName();

        if ( containsCaseInsensitive(username) ) {
            return false;
        }
        
        doAddUser(getJamesUser(user));
        return true;
    }

    /**
     * Adds a user to the repository with the specified attributes.  In current
     * implementations, the Object attributes is generally a String password.
     */
    public void addUser(String name, Object attributes) 
    {
        throw new RuntimeException("Improper use of deprecated method - read javadocs");
    }

    /**
     * Update the repository with the specified user object. A user object
     * with this username must already exist.
     *
     * @returns true if successful.
     */
    public boolean updateUser(User user)
    {
        // Return false if it's not found.
        if ( ! contains(user.getUserName()) ) {
            return false;
        }
        else {
            doUpdateUser(getJamesUser(user));
            return true;
        }
    }

    /**
     * Removes a user from the repository
     */
    public void removeUser(String name)
    {
        User user = getUserByName(name);
        if ( user != null ) {
            doRemoveUser(getJamesUser(user));
        }
    }

    /**
     * Gets the attribute for a user.  Not clear on behavior.
     *
     * @deprecated As of James 1.2.2 . Use the {@link #getUserByName(String) getUserByName} method.
     */
    public Object getAttributes(String name)
    {
        throw new RuntimeException("Improper use of deprecated method - read javadocs");
    }

    /**
     * Get the user object with the specified user name.  Return null if no
     * such user.
     *
     * @since James 1.2.2
     */
    public User getUserByName(String name)
    {
        return getUserByName(name, false);
    }

    /**
     * Get the user object with the specified user name. Match user naems on
     * a case insensitive basis.  Return null if no such user.
     *
     * @since James 1.2.2
     */
    public User getUserByNameCaseInsensitive(String name)
    {
        return getUserByName(name, true);
    }

    /**
     * Returns the user name of the user matching name on an equalsIgnoreCase
     * basis. Returns null if no match.
     */
    public String getRealName(String name)
    {
        // Get the user by name, ignoring case, and return the correct name.
        User user = getUserByName(name, true);
        if ( user == null ) {
            return null;
        }
        else {
            return user.getUserName();
        }
    }

    /**
     * Returns whether or not this user is in the repository
     */
    public boolean contains(String name)
    {
        User user = getUserByName(name, false);
        return ( user != null );
    }

    /**
     * Returns whether or not this user is in the repository. Names are
     * matched on a case insensitive basis.
     */
    public boolean containsCaseInsensitive(String name)
    {
        User user = getUserByName( name, true );
        return ( user != null );
    }

    /**
     * Tests a user with the appropriate attributes.  In current implementations,
     * this typically means "check the password" where a String password is passed
     * as the Object attributes.
     *
     * @deprecated As of James 1.2.2, use {@link #test(String, String) test(String name, String password)}
     */
    public boolean test(String name, Object attributes)
    {
        throw new RuntimeException("Improper use of deprecated method - read javadocs");
    }

    /**
     * Test if user with name 'name' has password 'password'.
     *
     * @since James 1.2.2
     */
    public boolean test(String name, String password)
    {
        User user = getUserByName(name, false);
        if ( user == null ) {
            return false;
        }
        else {
            return user.verifyPassword(password);
        }
    }

    /**
     * Returns a count of the users in the repository.
     */
    public int countUsers()
    {
        List usernames = listUserNames();
        return usernames.size();
    }

    /**
     * List users in repository.
     *
     * @returns Iterator over a collection of Strings, each being one user in the repository.
     */
    public Iterator list()
    {
        return listUserNames().iterator();
    }
}
