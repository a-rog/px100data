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
package com.px100systems.data.plugin.persistence;

/**
 * On-disk persistence provider exception.<br>
 * Generally recoverable - the caller should pause operations for some period and then resume assuming the database has been restarted.<br>
 * 
 * @version 0.3 <br>Copyright (c) 2015 Px100 Systems. All Rights Reserved.<br>
 * @author Alex Rogachevsky
*/
@SuppressWarnings("unused")
public class PersistenceProviderException extends Exception {
	private static final long serialVersionUID = 1L;

	public PersistenceProviderException(String message, Throwable cause) {
		super(message, cause);
	}

	public PersistenceProviderException(String message) {
		super(message);
	}

	public PersistenceProviderException(Throwable cause) {
		super(cause);
	}
}
