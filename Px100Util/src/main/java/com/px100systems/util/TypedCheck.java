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
package com.px100systems.util;

/**
 * Type-safe security check
 *
 * @version 0.3 <br>Copyright (c) 2015 Px100 Systems. All Rights Reserved.<br>
 * @author Alex Rogachevsky
 */
public class TypedCheck<T> {
	private Class<T> operatingClass;
	private SimpleExtractor<Boolean, T> check;

	/**
	 * Constructor
	 * @param operatingClass the class to operate on
	 * @param check check expression
	 */
	public TypedCheck(Class<T> operatingClass, SimpleExtractor<Boolean, T> check) {
		this.operatingClass = operatingClass;
		this.check = check;
	}

	/**
	 * A helper constructor that always returns true
	 * @param operatingClass the class to operate on
	 * @param <T> class type
	 * @return true
	 */
	public static <T> TypedCheck<T> alwaysAllow(Class<T> operatingClass) {
		return new TypedCheck<T>(operatingClass, (item) -> true);
	}

	/**
	 * Perform the check
	 * @param item the bean to check
	 * @return check result
	 */
	public boolean check(Object item) {
		return check.extract(operatingClass.cast(item));
	}
}
