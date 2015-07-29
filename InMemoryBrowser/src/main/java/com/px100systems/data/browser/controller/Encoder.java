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

import org.springframework.security.authentication.encoding.Md5PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Password encoder
 * 
 * @version 0.3 <br>Copyright (c) 2015 Px100 Systems. All Rights Reserved.<br>
 * @author Alex Rogachevsky
 */
@Component("userPasswordEncoder")
public class Encoder implements PasswordEncoder {
	private static final Md5PasswordEncoder passwordEncoder = new Md5PasswordEncoder();

	/**
	 * MD5 encoding
	 * @param rawPassword password
	 * @return encoded password
	 */
	@Override
	public String encode(CharSequence rawPassword) {
		return passwordEncoder.encodePassword(rawPassword.toString(), null);
	}

	/**
	 * Compare passwords
	 * @param rawPassword clear-text password
	 * @param encodedPassword encoded password
	 * @return whether passwords match
	 */
	@Override
	public boolean matches(CharSequence rawPassword, String encodedPassword) {
		return passwordEncoder.encodePassword(rawPassword.toString(), null).equals(encodedPassword);
	}
}
