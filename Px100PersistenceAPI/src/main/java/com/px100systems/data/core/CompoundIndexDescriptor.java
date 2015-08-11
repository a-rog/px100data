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
package com.px100systems.data.core;

import com.px100systems.util.PropertyAccessor;
import org.springframework.util.ReflectionUtils;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Compound index desciptor.<br>
 *
 * @version 0.3 <br>Copyright (c) 2015 Px100 Systems. All Rights Reserved.<br>
 * @author Alex Rogachevsky
 */
public class CompoundIndexDescriptor {
	private String name;

	public static class Field {
		private String name;
		private Class<?> type;
		private boolean descending;

		public Field(String name, Class<?> type, boolean descending) {
			this.descending = descending;
			this.name = name;
			this.type = type;
		}

		public boolean isDescending() {
			return descending;
		}

		public String getName() {
			return name;
		}

		public Class<?> getType() {
			return type;
		}
	}
	private List<Field> fields = new ArrayList<>();

	private static final String NAME_PREFIX = "px100_compound_";

	public static boolean isCompondIndexName(String s) {
		return s.startsWith(NAME_PREFIX);
	}

	public CompoundIndexDescriptor(Class<?> entityClass, String name, String[] fields) {
		if (name.trim().isEmpty())
			throw new RuntimeException("Empty compound index name in " + entityClass.getSimpleName());

		this.name = NAME_PREFIX + entityClass.getSimpleName() + "_" + name + "_idx";

		for (String field : fields) {
			String[] s = field.split(" ");
			if (s.length != 2)
				throw new RuntimeException("Invalid compound index " + name + " in " + entityClass.getSimpleName() + ": couldn't parse field '" + field + "'");

			Method getter = ReflectionUtils.findMethod(entityClass, PropertyAccessor.methodName("get", s[0]));
			if (getter == null)
				throw new RuntimeException("Invalid compound index " + name + " in " + entityClass.getSimpleName() + ": couldn't find getter for '" + field + "'");
			Class<?> returnType = getter.getReturnType();
			if (!returnType.equals(Integer.class) && !returnType.equals(Long.class) && !returnType.equals(Double.class) &&
				!returnType.equals(Date.class) && !returnType.equals(String.class) && !returnType.equals(Boolean.class))
				throw new RuntimeException("Invalid compound index " + name + " in " + entityClass.getSimpleName() + ": field '" + field +
					"' - only Integer, Long, Double, Date, String, and Boolean are supported");

			this.fields.add(new Field(s[0], returnType, s[1].equalsIgnoreCase("DESC")));
		}
	}

	public List<Field> getFields() {
		return fields;
	}

	public String getName() {
		return name;
	}
}