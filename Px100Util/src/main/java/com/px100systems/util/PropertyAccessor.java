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

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import org.springframework.util.ReflectionUtils;

/**
 * Bean property accessor. Supports dot-separated nested sub-object properties "x.y.z"
 * 
 * @version 0.3 <br>Copyright (c) 2015 Px100 Systems. All Rights Reserved.<br>
 * @author Alex Rogachevsky
 */
public class PropertyAccessor {
	private List<Method> accessorMethods;
	private List<Method> mutatorMethods;
	private Class<?> lastClass;
	private String lastField;
	private Class<?> lastType;

	@SuppressWarnings("unused")
	public PropertyAccessor(PropertyAccessor copy) {
		accessorMethods = copy.accessorMethods;
		mutatorMethods = copy.mutatorMethods;
		lastClass = copy.lastClass;
		lastField = copy.lastField;
	}

	/**
	 * Constructor
	 * @param srcClass class
	 * @param srcProperty property
	 */
	public PropertyAccessor(Class<?> srcClass, String srcProperty) {
		accessorMethods = new ArrayList<Method>();
		mutatorMethods = new ArrayList<Method>();

		String[] properties = srcProperty.split("\\.");
		lastClass = srcClass;
		for (int i = 0; i < properties.length; i++) {
			lastField = properties[i];

			Method getter = ReflectionUtils.findMethod(lastClass, methodName("get", lastField));
			if (getter == null)
				throw new RuntimeException("Couldn't find getter for " + lastField + " in " + lastClass.getName());
			accessorMethods.add(getter);

			Class<?> fieldType = getter.getReturnType();

			if (mutatorMethods != null) {
				Method setter = ReflectionUtils.findMethod(lastClass, methodName("set", lastField), fieldType);
				if (setter != null)
					mutatorMethods.add(setter);
				else
					mutatorMethods = null;
			}

			if (i < properties.length - 1)
				lastClass = fieldType;
			else
				lastType = fieldType;
		}
	}

	@SuppressWarnings("unused")
	public Class<?> getLastClass() {
		return lastClass;
	}

	@SuppressWarnings("unused")
	public String getLastField() {
		return lastField;
	}

	@SuppressWarnings("unused")
	public Class<?> getLastType() {
		return lastType;
	}

	@SuppressWarnings("unused")
	public Method getLastAccessor() {
		return accessorMethods.isEmpty() ? null : accessorMethods.get(accessorMethods.size() - 1);
	}

	/**
	 * Get the property
	 * @param item the bean
	 * @return property value
	 */
	public Object get(Object item) {
		Object result = null; 
		Object o = item; 
		for (Method accessor : accessorMethods)
			try {
				o = accessor.invoke(o);
				result = o;
				if (result == null)
					break;
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		return result;
	}

	/**
	 * Get property's enclosing (parent) class
	 * @param item the bean
	 * @return the bean the property is declared in
	 */
	public Object getParent(Object item) {
		Object target = item;
		for (int i = 0, n = accessorMethods.size(); i < n - 1; i++)
			try {
				Object subObject = accessorMethods.get(i).invoke(target);
				if (subObject == null) {
					subObject = accessorMethods.get(i).getReturnType().newInstance();
					BeanPropertySetter.assignField(target, mutatorMethods.get(i), subObject);
				}
				target = subObject;
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		return target;
	}

	/**
	 * Set the property
	 * @param item the bean
	 * @param value property value
	 */
	public void set(Object item, Object value) {
		BeanPropertySetter.assignField(getParent(item), mutatorMethods.get(accessorMethods.size() - 1), value);
	}

	@SuppressWarnings("unused")
	public static String findAnnotatedField(Class<?> topClass, Class<? extends Annotation> annotation) {
		for (Class<?> cls = topClass; cls != null; cls = cls.getSuperclass()) 
			for (Field field : cls.getDeclaredFields())
				if (field.getAnnotation(annotation) != null)
					return field.getName();
		return null;
	}

	/**
	 * Helper method to convert a field "xyz" name into "[prefix]Xyz"
	 * @param prefix typically "get" or "set"
	 * @param propertyName field name
	 * @return the getter/setter name
	 */
	public static String methodName(String prefix, String propertyName) {
		return prefix + propertyName.substring(0, 1).toUpperCase() + propertyName.substring(1);
	}
}

