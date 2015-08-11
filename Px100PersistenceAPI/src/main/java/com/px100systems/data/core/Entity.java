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

import com.px100systems.util.serialization.SerializedGetter;
import java.lang.reflect.Method;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Base stored/persistible entity - a subclass of Storedbean that defines the Long id (mandatory PK).<br>
 * <br>
 * Transient fields should be marked with standard Java "transient" modifier (excluded from serialization).<br>
 * Use @Index on top-level getters only. Create custom artificial getters (annotated with @SerializedGetter) for complex indices.<br>
 * Annotate serialized collections (Lists and Sets) with @SerializedCollection to tell teh framework the member type.<br>
 * Some providers like Ignite need to know all query field, so whateve wasn't annotated with @Index or @SerializedGetter,
 * should be annotated with @Query field - the getter, as always.<br>
 * <br>
 * <b>Special getters:</b><br>
 * Special artificaial indexed sort fields: xxxSort - used by teh higher-level Px100 framework to sort result sets.<br>
 * Special "native" orderBy methods orderByXxx() - used by teh Hazelcast provider. Sort fields should always have matching orderBy methods.<br>
 * <br>
 * An entity cannot include other scanned entities as sub-objects or children though it's never been tested what'd happen.
 * You can still use an Entity-derived class, just put it in a different (not scanned) package.<br>
 * Generally entities should be self-contained and complete, however if they go over some size threshold e.g. 5K,
 * it makes sense splitting them into several scalar records of different (logical) type grouped under the same ID; 
 * per screen (CRUD use case) or group of screens. Main entity plus those satellite ones (saved together in one transaction).<br>
 * Long collections of records (beyond 100) should also be moved out and belong to the same ID (saved together with the parent in one transaction).
 * Overwrite Entity.cascadeDelete(0 for cascade-deletes of those. Estimated reasonable size - probably a couple thousand rows max, not millions.<br>
 * <br>
 * <b>Data Migration:</b> field-wise Java (or Mongo) serialization takes care of type compatibility (sub-objects and collections too).
 * However if you need something complex (very rare), the procedure is below. It should be a custom console Java app:
 * <ol>
 * <li> load old data from the DB
 * <li> optional step: run some custom converters to set new fields - iterate through the needed units to transform them
 * <li> "emergency save" to files and recreate the DB from those files
 * <li> optional step: remove old fields (that were left - used to populate the new ones), repeat #1 and #3
 * </ol>
 * 
 * @version 0.3 <br>Copyright (c) 2015 Px100 Systems. All Rights Reserved.<br>
 * @author Alex Rogachevsky
 */
public abstract class Entity extends StoredBean {
	public static final String TENANT_DELIMITER = "___";

	private Integer tenantId = null; // whether used or not doesn't matter - needed for sharding/partition in the distributed storage (cluster)
	private Date createdAt; // automatically set by DataStorage - do not mess with it
	private Date modifiedAt; // automatically set by DataStorage - do not mess with it
	
	public Entity() {
	}

	/**
	 * Entity unit od storage (Hazelcast map, Mongo document, or Ignite cache) name
	 * @return the tenant-specific unit name
	 */
	@Override
	public String unitName() {
		return unitFromClass(getClass(), tenantId);
	}

	/**
	 * Entity name - its class name
	 * @return the entity name
	 */
	@SuppressWarnings("unused")
	public String entityName() {
		return getClass().getSimpleName();
	}

	/**
	 * Entity unit od storage (Hazelcast map, Mongo document, or Ignite cache) name
	 * @return the tenant-specific unit name
	 */
	public static String unitFromClass(Class<?> entityClass, Integer tenantId) {
		return entityClass.getSimpleName() + TENANT_DELIMITER + tenantId;
	}

	/**
	 * ID generator name. Typically it'd be just one ID generator for all domain-specifc entities (defined at the only subclass
	 * directly derived from Entity, all domain entities are based on.
	 * @return the ID generator name
	 */
	public String idGeneratorName() {
		return unitName();
	}
	
	public static Map<String, Class<?>> indexes(Class<?> entityClass) {
		Map<String, Class<?>> result = new HashMap<>();

		for (Method m : entityClass.getDeclaredMethods()) {
			Index indexAnnotation = m.getAnnotation(Index.class);
			if (indexAnnotation != null) {
				if (!m.getName().startsWith("get") || m.getParameterCount() != 0)
					throw new RuntimeException("Invalid @Index method: should be a getter: " + entityClass.getSimpleName() + "." + m.getName() + "()");
				Class<?> returnType = m.getReturnType();
				boolean ordered = returnType.equals(Integer.class) || returnType.equals(Long.class) || returnType.equals(Double.class) ||
					returnType.equals(Date.class) || returnType.equals(String.class);
				if (!ordered && !returnType.equals(Boolean.class))
					throw new RuntimeException("Invalid @Index method: only Integer, Long, Double, Date, String, and Boolean are supported: " + entityClass.getSimpleName() + "." + m.getName() + "()");
				result.put(m.getName().substring(3, 4).toLowerCase() + (m.getName().length() > 4 ? m.getName().substring(4) : "") + (ordered ? "*" : ""),
					m.getReturnType());
			}
		}
		
		Class<?> superclass = entityClass.getSuperclass();
		if (superclass != null && !superclass.equals(Object.class))
			result.putAll(indexes(superclass));
		
		return result;	
	}

	public static Map<String, Class<?>> queryFields(Class<?> entityClass) {
		Map<String, Class<?>> result = new HashMap<>();

		for (Method m : entityClass.getDeclaredMethods()) {
			Index indexAnnotation = m.getAnnotation(Index.class);
			QueryField queryAnnotation = m.getAnnotation(QueryField.class);
			SerializedGetter serializedGetterAnnotation = m.getAnnotation(SerializedGetter.class);
			if ((queryAnnotation != null || serializedGetterAnnotation != null) && indexAnnotation == null) {
				if (!m.getName().startsWith("get") || m.getParameterCount() != 0)
					throw new RuntimeException("Invalid @QueryField method: should be a getter: " + entityClass.getSimpleName() + "." + m.getName() + "()");
				Class<?> returnType = m.getReturnType();
				if (!returnType.equals(Integer.class) && !returnType.equals(Long.class) && !returnType.equals(Double.class) &&
					!returnType.equals(Date.class) && !returnType.equals(String.class) && !returnType.equals(Boolean.class))
					throw new RuntimeException("Invalid @QueryField method: only Integer, Long, Double, Date, String, and Boolean are supported: " + entityClass.getSimpleName() + "." + m.getName() + "()");
				result.put(m.getName().substring(3, 4).toLowerCase() + (m.getName().length() > 4 ? m.getName().substring(4) : ""), m.getReturnType());
			}
		}

		Class<?> superclass = entityClass.getSuperclass();
		if (superclass != null && !superclass.equals(Object.class))
			result.putAll(queryFields(superclass));

		return result;
	}

	public static List<CompoundIndexDescriptor> compoundIndexes(Class<?> entityClass) {
		List<CompoundIndexDescriptor> result = new ArrayList<>();

		CompoundIndexes indexes = entityClass.getAnnotation(CompoundIndexes.class);
		if (indexes != null)
			for (CompoundIndex index : indexes.value())
				result.add(new CompoundIndexDescriptor(entityClass, index.name(), index.fields()));

		return result;
	}

	/**
	 * Helper method for special sort getters
	 * @param date date
	 * @return sort value
	 */
	protected String sortValue(Date date) {
		return sortValue(date == null ? null : date.getTime());
	}

	/**
	 * Helper method for special sort getters
	 * @param number number
	 * @return sort value
	 */
	protected String sortValue(Long number) {
		String s;
		if (number == null)
			s = "########################################";
		else if (number >= 0)
			s = "0" + number;
		else
			s = String.format("-%1$024d", Long.MAX_VALUE + number);

		return String.format("%1$24.24s:%2$024d", s, getId()).replace(" ", "0");
	}

	private static final double MAX_NEGATIVE_DOUBLE = 999999999999999999999999.999D;

	/**
	 * Helper method for special sort getters
	 * @param number number
	 * @return sort value
	 */
	protected String sortValue(Double number) {
		String s;
		if (number == null)
			s = "########################################";
		else if (number >= 0.0)
			s = "0" + new DecimalFormat("#.000").format(number);
		else
			s = String.format("-%1$31.31s", number < -MAX_NEGATIVE_DOUBLE ? "0.000" : new DecimalFormat("#.000").format(MAX_NEGATIVE_DOUBLE + number));

		return String.format("%1$32.32s:%2$024d", s, getId()).replace(" ", "0");
	}

	/**
	 * Helper method for special sort getters
	 * @param value value
	 * @return sort value
	 */
	protected String sortValue(Boolean value) {
		return String.format("%1$s:%2$024d", value == null ? "#" : (value ? "1" : "0"), getId());
	}

	/**
	 * Helper method for special sort getters
	 * @param s value
	 * @return sort value
	 */
	protected String sortValue(String s) {
		return String.format("%1$-32.32s:%2$024d", s == null ? "" : s, getId()).replace(" ", "0");
	}

	/**
	 * Tenant ID - 0 for single-tenant projects
	 * @return tenant ID
	 */
	public Integer getTenantId() {
		return tenantId;
	}

	/**
	 * Tenant ID - 0 for single-tenant projects
	 * @param tenantId tenant ID
	 */
	public void setTenantId(Integer tenantId) {
		this.tenantId = tenantId;
	}

	/**
	 * Special ID field sorter - required by teh higher-level Px100 framework displaying lists
	 * @return the sort value
	 */
	@Index
	@SerializedGetter
	public String getIdSort() {
		return sortValue(getId());
	}

	/**
	 * idSort OrderBy (comparator) - used by Hazelcast
	 * @return the Comparable implementation wrapper
	 */
	@SuppressWarnings("unused")
	public static OrderBy orderByIdSort(int modifier) {
		return new OrderBy<Entity>(modifier) {
			@Override
			public Comparable<?> get(Entity object) {
				return object.getIdSort();
			}
		};
	}

	/**
	 * Creation time. Set by Transaction when the bean is created.
	 * @return craetion time
	 */
	@Index
	public Date getCreatedAt() {
		return createdAt;
	}

	/**
	 * Special createdAt field sorter - required by teh higher-level Px100 framework displaying lists
	 * @return the sort value
	 */
	@Index
	@SerializedGetter
	public String getCreatedAtSort() {
		return sortValue(createdAt);
	}

	/**
	 * createdAtSort OrderBy (comparator) - used by Hazelcast
	 * @return the Comparable implementation wrapper
	 */
	@SuppressWarnings("unused")
	public static OrderBy orderByCreatedAtSort(int modifier) {
		return new OrderBy<Entity>(modifier) {
			@Override
			public Comparable<?> get(Entity object) {
				return object.getCreatedAtSort();
			}
		};
	}

	/**
	 * Creation time. Set by Transaction when the bean is created.
	 * @param createdAt craetion time
	 */
	public void setCreatedAt(Date createdAt) {
		this.createdAt = createdAt;
	}

	/**
	 * Last modification time. Set by Transaction when the bean is updated.
	 * @return modification time
	 */
	@Index
	public Date getModifiedAt() {
		return modifiedAt;
	}

	/**
	 * Special modifiedAt field sorter - required by teh higher-level Px100 framework displaying lists
	 * @return the sort value
	 */
	@Index
	@SerializedGetter
	public String getModifiedAtSort() {
		return sortValue(modifiedAt);
	}

	/**
	 * modifiedAtSort OrderBy (comparator) - used by Hazelcast
	 * @return the Comparable implementation wrapper
	 */
	@SuppressWarnings("unused")
	public static OrderBy orderByModifiedAtSort(int modifier) {
		return new OrderBy<Entity>(modifier) {
			@Override
			public Comparable<?> get(Entity object) {
				return object.getModifiedAtSort();
			}
		};
	}

	/**
	 * Last modification time. Set by Transaction when the bean is updated.
	 * @param modifiedAt modification time
	 */
	public void setModifiedAt(Date modifiedAt) {
		this.modifiedAt = modifiedAt;
	}

	/**
	 * Cascade-delete method: invoked by Transaction when the entity is being deleted
	 */
	public void cascadeDelete() {
	}
}
