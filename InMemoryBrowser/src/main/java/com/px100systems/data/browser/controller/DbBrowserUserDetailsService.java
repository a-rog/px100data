/*
 * This file is part of Px100 Data.
 *
 * Px100 Data is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see http://www.gnu.org/licenses/
 */
package com.px100systems.data.browser.controller;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import javax.annotation.Resource;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Spring Security user info service    
 * 
 * @version 0.3 <br>Copyright (c) 2015 Px100 Systems. All Rights Reserved.<br>
 * @author Alex Rogachevsky
 */
@Service("userSecurityService")
public class DbBrowserUserDetailsService implements UserDetailsService {
	@Resource(name="userFile")
	private String usersFile;

	/**
	 * Standard user role
	 */
	public static final String DEFAULT_AUTHORITY = "ROLE_USER";

	/**
	 * Find user by name
	 * @param userName user name
	 * @return user authentication details
	 * @throws UsernameNotFoundException
	 */
	@Override
	public UserDetails loadUserByUsername(String userName) throws UsernameNotFoundException {
		String password = getUsers().getProperty(userName);
		if (password == null || password.isEmpty())
			throw new UsernameNotFoundException("User not found");
		
		List<SimpleGrantedAuthority> authorities = new ArrayList<SimpleGrantedAuthority>();
		authorities.add(new SimpleGrantedAuthority(DEFAULT_AUTHORITY));
		
		return new User(userName, password, true, true, true, true, authorities);
	}

	/**
	 * Load user file content
	 * @return user/password pairs
	 * @throws UsernameNotFoundException
	 */
	public Properties getUsers() throws UsernameNotFoundException {
    	File file = new File(usersFile);
    	if (!file.exists())
			throw new UsernameNotFoundException("Invalid users file location " + usersFile);
    	Properties users = new Properties();
    	try {
			users.load(new FileReader(file));
		} catch (Exception e) {
			throw new UsernameNotFoundException("Invalid users file location " + usersFile);
		}
		return users;
	}

	/**
	 * Save user file content
	 * @param users user/password pairs
	 * @throws Exception
	 */
	public void saveUsers(Properties users) throws Exception {
		users.store(new FileWriter(usersFile), null);
	}
}

