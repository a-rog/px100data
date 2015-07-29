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

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Reflkection-based Java bean propert setter.
 * 
 * @version 0.3 <br>Copyright (c) 2015 Px100 Systems. All Rights Reserved.<br>
 * @author Alex Rogachevsky
 */
public class BeanPropertySetter {
	/**
	 * Sets the field via the setter optionally converting teh value from String or another compatible class if such constructor exists.
	 * @param target the bean
	 * @param mutatorMethod setter
	 * @param value field value - can be String for Dates and numbers
	 * @param throwException throw an exception or swallow it on errors
	 * @throws Exception
	 */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public static void assignField(Object target, Method mutatorMethod, Object value, Boolean throwException) throws Exception {
			Class type = mutatorMethod.getParameterTypes()[0];
			if (type.equals(String.class) && value != null && !(value instanceof String))
				value = value.toString();
			if (value == null || type.isInstance(value))
				mutatorMethod.invoke(target, value);
			else {
				Constructor c;
				try {
					c = type.getConstructor(value.getClass());
				} catch (NoSuchMethodException ex) {
					value = value.toString();
					try {
						c = type.getConstructor(String.class);
					} catch (NoSuchMethodException ee) {
						throw new RuntimeException("Cannot assign " + value.getClass().getName() + " to the field of type " + type.getName());
					}
				}
				
				try {
					mutatorMethod.invoke(target, c.newInstance(value));
				} catch (InvocationTargetException ee) {
					if (ee.getTargetException() != null && ee.getTargetException() instanceof NumberFormatException && 
							value instanceof String && value.toString().endsWith(".0") && !throwException) {
						value = value.toString().substring(0, value.toString().indexOf(".0"));
						mutatorMethod.invoke(target, c.newInstance(value));
					} else if (throwException) {
						throw new RuntimeException(ee);
					}
				}
			}
	}

	/**
	 * Sets the field via the setter optionally converting teh value from String or another compatible class if such constructor exists. Swallows conversion errors.
	 * @param target the bean
	 * @param mutatorMethod setter
	 * @param value field value - can be String for Dates and numbers
	 */
	public static void assignField(Object target, Method mutatorMethod, Object value) {
		try {
			assignField(target, mutatorMethod, value, false);
		} catch (Exception ee) {
			throw new RuntimeException(ee);
		}
	}
}
