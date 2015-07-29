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

import com.px100systems.util.serialization.SerializationAware;

import java.lang.reflect.InvocationTargetException;

/**
 * Base stored class. Every "non-transient" data structure passed to the data storage needs to extend it.
 * 
 * @version 0.3 <br>Copyright (c) 2015 Px100 Systems. All Rights Reserved.<br>
 * @author Alex Rogachevsky
 */
public abstract class StoredBean implements SerializationAware {
	transient boolean serializing = false;

	private Long id; // id in the map; not the PK when stored in a relational database

	private transient int operationOrderNo; // used by Transaction

	public StoredBean() {
	}

	public int getOperationOrderNo() {
		return operationOrderNo;
	}

	public void setOperationOrderNo(int operationOrderNo) {
		this.operationOrderNo = operationOrderNo;
	}

	/**
	 * Is the bean is currently being serialzed or deserialized.
	 * Useful in overriden setters to avoid side effects during teh serialization phase, only when invoked normally.
	 * @return if the entity is being serialized or deserialized.
	 */
	public boolean isSerializing() {
		return serializing;
	}

	@Override
	public void setSerializing(boolean serializing) {
		this.serializing = serializing;
	}

	public abstract String unitName();

	/**
	 * Entity's madatory PK: assigned automatically by Transaction.insert(0 using the ID generator
	 * @return the ID
	 */
	@Index
	public Long getId() {
		return id;
	}

	/**
	 * Entity's madatory PK: assigned automatically by Transaction.insert(0 using the ID generator
	 * @param id the ID
	 */
	public void setId(Long id) {
		this.id = id;
	}

	/**
	 * id OrderBy (comparator) - used by Hazelcast
	 * @return the Comparable implementation wrapper
	 */
	@SuppressWarnings({"rawtypes", "unused"})
	public static OrderBy orderById(int modifier) {
		return new OrderBy<StoredBean>(modifier) {
			private static final long serialVersionUID = 1L;
	
			@Override
			public Comparable<?> get(StoredBean object) {
				return object.getId();
			}
		};
	}
	
	public static class ReflectionOrderBy<T> extends OrderBy<T> {
		private static final long serialVersionUID = 1L;
		private String field;
		
		public ReflectionOrderBy(String field, int modifier) {
			super(modifier);
			this.field = field;
		}

		public String getField() {
			return field;
		}

		@Override
		public Comparable<?> get(T object) {
			try {
				return (Comparable<?>)object.getClass().getMethod("get" + field.substring(0, 1).toUpperCase() + field.substring(1), new Class<?>[0]).invoke(object);
			} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException | NoSuchMethodException | SecurityException e) {
				throw new RuntimeException(e);
			}
		}
	}	
	
	@Override
	public int hashCode() {
		return id == null ? 0 : new Long(id + 1).hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == null || !StoredBean.class.isAssignableFrom(obj.getClass()))
			return false;
		try {
			StoredBean o = (StoredBean)obj;
			return id == null ? o.id == null : id.equals(o.id);
		} catch (Exception e) {
			return false;
		}
	}
}
