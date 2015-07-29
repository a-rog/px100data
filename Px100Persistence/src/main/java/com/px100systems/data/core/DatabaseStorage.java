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

import com.google.common.reflect.ClassPath;
import com.px100systems.data.plugin.storage.EntityCursor;
import com.px100systems.util.serialization.SerializationDefinition;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Required;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Persistent database: whether in-memory with write-behind/write-through or traditional one.<br>
 * Responsible for scanning the specified entity class packages and registering their SerializationDefinitions.<br>
 * <br>
 * <b>Configuration</b><br>
 * <ul>
 * 	<li>tenantLoader - responsible for loading tenant configuration (can be null for non multi-tenant apps).
 * 	<li>initializer - a "one-time" development mode parameter - a callback, that populates the database after it is wiped out (on server startup).
 * 	  if initializer is specified, the database schema is being wiped out on the startup - storage provider responsibility.
 * 	<li>baseEntityPackages - the packages to scan for Entity subclasses - to build persistence units (maps).
 *  <li>runtimeStorage - in-memory cache needed for ID generators, etc.
 * </ul>
 *
 * @version 0.3 <br>Copyright (c) 2015 Px100 Systems. All Rights Reserved.<br>
 * @author Alex Rogachevsky
 */
public abstract class DatabaseStorage implements InitializingBean, DisposableBean {
	private static Log log = LogFactory.getLog(DatabaseStorage.class);

	private TenantLoader tenantLoader;
	private RuntimeStorage runtimeStorage;

	private DbInitializer initializer = null;

	private List<String> baseEntityPackages;
	private Map<String, Class<? extends Entity>> configuredEntities;

	public static class EntityInfo {
		private String unitName;
		private int tenantId;
		private Map<String, Class<?>> indexes;
		private Class<?> entityClass;

		public EntityInfo(Class<?> entityClass, int tenantId, String unitName, Map<String, Class<?>> indexes) {
			this.indexes = indexes;
			this.unitName = unitName;
			this.entityClass = entityClass;
			this.tenantId = tenantId;
		}

		public String getUnitName() {
			return unitName;
		}

		public Class<?> getEntityClass() {
			return entityClass;
		}

		public Map<String, Class<?>> getIndexes() {
			Map<String, Class<?>> result = new HashMap<>();
			for (Map.Entry<String, Class<?>> e : indexes.entrySet()) {
				String s = e.getKey();
				result.put(s.endsWith("*") ? s.substring(0, s.length() - 1) : s, e.getValue());
			}
			return result;
		}

		public Map<String, Class<?>> getRawIndexes() {
			return indexes;
		}

		public int getTenantId() {
			return tenantId;
		}
	}

	/**
	 * Data grid storage - required for runtime (aka "transient") state like HTTP session management, global counters, etc.
	 * even when using traditional on-disk database e.g. Mongo - see {@link TraditionalDatabase}.
	 * Responsible for full data persistence when using the data grid as primary storage - see {@link InMemoryDatabase}.
	 * @param runtimeStorage Data grid base storage
	 */
	@Required
	public void setRuntimeStorage(RuntimeStorage runtimeStorage) {
		this.runtimeStorage = runtimeStorage;
	}

	/**
	 * Mandatory list of packages where database entities are expected. Entities need to subclass {@link Entity} and be serializable:
	 * Portable for Hazelcast, Externalizable for Ignite, and just normal POJOs for Mongo. Entities can only have non-transient fields
	 * (and serialized getters) of String, Long, Integer, Double, Date, and Boolean types. They can also have Lists and Sets of sub-objects
	 * which fields are limited to thsoe specified above. Sub-objects are handled by SerializationDefintion and don't need to implement
	 * Portable or Externalizable. Typically it'd be a one direct abstract subclass of Entity that implements those, and then real entities
	 * derived from it.
	 * @param baseEntityPackages list of Java class packages
	 */
	@Required
	public void setBaseEntityPackages(List<String> baseEntityPackages) {
		this.baseEntityPackages = baseEntityPackages;
	}

	/**
	 * Tenant loader for multi-tenant systems. Muli-tenant systems store different tenant data in different "units": Mongo documents,
	 * Hazelcast maps, and Ignite caches. See Entity.tenantId field for details. It is always zero for single-tenant systems.
	 * @param tenantLoader tenant loader.
	 */
	public void setTenantLoader(TenantLoader tenantLoader) {
		this.tenantLoader = tenantLoader;
	}

	/**
	 * Database initializer - if specified, the underlying database provider will wipe out and recreate its schema on startup before invoking it.
	 * @param initializer initializer callback/lambda
	 */
	public void setInitializer(DbInitializer initializer) {
		this.initializer = initializer;
	}

	public RuntimeStorage getRuntimeStorage() {
		return runtimeStorage;
	}

	@Override
	public void destroy() {
		shutdown();
	}

	@Override
	@SuppressWarnings("unchecked")
	public void afterPropertiesSet() throws Exception {
		if (tenantLoader != null)
			tenantLoader.setDatabase(this);

		configuredEntities = new HashMap<String, Class<? extends Entity>>();

		try {
			ClassPath cp = ClassPath.from(getClass().getClassLoader());
			for (String p : baseEntityPackages)
				for (ClassPath.ClassInfo cli : cp.getTopLevelClassesRecursive(p)) {
					Class<?> eClass = Class.forName(cli.getName());
					if (!Modifier.isAbstract(eClass.getModifiers()) && Entity.class.isAssignableFrom(eClass)) {
						Class<? extends Entity> entityClass = (Class<? extends Entity>)eClass;
						String name = entityClass.getSimpleName();
						Class<? extends Entity> cls = configuredEntities.get(name);
						if (cls != null) {
							if (cls.getName().equals(entityClass.getName()))
								continue;
							throw new RuntimeException("Duplicate entities in different packages: " + name);
						}
						configuredEntities.put(name, entityClass);
						SerializationDefinition.register(entityClass);
					}
				}
		} catch (Exception e1) {
			throw new RuntimeException(e1);
		}

		SerializationDefinition.lock();

		runtimeStorage.getProvider().start();

		List<BaseTenantConfig> tenants = new ArrayList<BaseTenantConfig>();
		if (tenantLoader != null)
			tenants = tenantLoader.load();

		List<EntityInfo> entities = new ArrayList<>();
		for (Class<? extends Entity> entityClass : getConfiguredEntities().values()) {
			Map<String, Class<?>> indexes = Entity.indexes(entityClass);
			if (tenants.isEmpty())
				entities.add(new EntityInfo(entityClass, 0, Entity.unitFromClass(entityClass, 0), indexes));
			else
				for (BaseTenantConfig tenant : tenants)
					entities.add(new EntityInfo(entityClass, tenant.getId(), Entity.unitFromClass(entityClass, tenant.getId()), indexes));
		}

		init(entities, initializer != null);

		if (initializer != null) {
			initializer.initialize(new Transaction(this, 0));
			log.info("Initialized the database");
		}
	}

	/**
	 * Get a map of current tenants. Used internally by the framework.
	 * @return tenant map
	 */
	public LinkedHashMap<Integer, String> getTenants() {
		LinkedHashMap<Integer, String> tenants = new LinkedHashMap<>();
		if (tenantLoader != null)
			for (BaseTenantConfig t : tenantLoader.load())
				tenants.put(t.getId(), t.getUrlIdentifier());
		else
			tenants.put(0, "Default");
		return tenants;
	}

	public void onNewTenant(BaseTenantConfig tenant) throws DataStorageException {
		for (Class<? extends Entity> entityClass : configuredEntities.values())
			runtimeStorage.getProvider().createMap(entityClass, Entity.unitFromClass(entityClass, tenant.getId()), Entity.indexes(entityClass), false);
		runtimeStorage.onNewTenant(tenant);
	}

	public Class<?> entityClass(String entityName) {
		return configuredEntities.get(entityName);
	}

	public Set<String> entityNames() {
		return configuredEntities.keySet();
	}

	protected Map<String, Class<? extends Entity>> getConfiguredEntities() {
		return configuredEntities;
	}

	/**
	 * Database "transaction" for a specified tenant (pass 0 in single-tenant projects).<br>
	 * A transaction does not lock anything until it is actiually committed (see {@link Transaction#commit}).
	 * Only at that time all accumulated inerts, updates, and deletes are executed at once. This way it is a simulated pessimistic,
	 * read-committed transaction, especially for non-transactional providers like Mongo. Mongo provider does attempt to recover/rollback
	 * by storing the before-transaction state of all entity beans enqueued for saving. Once committed, the transaction cannot be reused,
	 * however a new one can be started from the old one - see {@link Transaction#transaction}
	 * @param tenantId tenant ID
	 * @return transaction
	 * @throws DataStorageException
	 */
	public Transaction transaction(Integer tenantId) throws DataStorageException {
		if (!isActive())
			throw new DataStorageException("inactive");
		return new Transaction(this, tenantId);
	}

	/**
	 * Internal method responsible for creating/syncing all entities and indexes on startup.
	 * initializeData is relevant for in-memory providers - tells it to reset the disk storage: recreate schemas, etc.
	 * @param entities a list of recognized (scanned) entities with indexes and otehr metadata.
	 * @param initializeData whether to wipe out the database.
	 */
	protected abstract void init(List<EntityInfo> entities, boolean initializeData);

	/**
	 * Graceful shutdown. Used internally.
	 */
	protected abstract void shutdown();

	/**
	 * Get entity by its Long ID. Internal method used by Transaction.
	 * @param unitName a unit name is typically the entity name + tenantId
	 * @param cls entity class
	 * @param id entity ID
	 * @param <T> entity class
	 * @return entity bean or null
	 */
	protected abstract <T> T get(String unitName, Class<T> cls, Long id);

	/**
	 * Count entites matching some criteria or all if no criteria was specified. Used internally by Transaction.
	 * @param unitName a unit name is typically the entity name + tenantId
	 * @param cls entity class
	 * @param criteria filter criteria. Can be null
	 * @return the number of foudn entities
	 */
	protected abstract long count(String unitName, Class<?> cls, Criteria criteria);

	/**
	 * Query with a limit. Used internally by Transaction.
	 * @param unitName a unit name is typically the entity name + tenantId
	 * @param cls entity class - make sure the exact concrete class is specified and not a superclass
	 * @param criteria filter - can be null to get all entities
	 * @param orderBy - list of SQL-like strings e.g. {"field1 ASC", "field2 DESC", "field3"}
	 * @param limit mandatory limit
	 * @param <T> entity class
	 * @return a list of entity beans
	 */
	protected abstract <T> List<T> search(String unitName, Class<T> cls, Criteria criteria, List<String> orderBy, Integer limit);

	/**
	 * Query without a limit. Used internally by Transaction.
	 * @param unitName a unit name is typically the entity name + tenantId
	 * @param cls entity class - make sure the exact concrete class is specified and not a superclass
	 * @param criteria filter - can be null to get all entities
	 * @param orderBy - list of SQL-like strings e.g. {"field1 ASC", "field2 DESC", "field3"}
	 * @param <T> entity class
	 * @return a closeable cursor (always use try/finally to close)
	 */
	protected abstract <T> EntityCursor<T> search(String unitName, Class<T> cls, Criteria criteria, List<String> orderBy);

	/**
	 * Saving the changes transactionally. Used internally by Transaction.commit().
	 * @param inserts a list of beans to insert
	 * @param updates a list of changed beans to save
	 * @param deletes a list of deletes - eitehr bean IDs or some Conditions to find and delete entities
	 * @param inPlaceUpdates a list of callbacks to execute in-place updates via entry processors. Relevant to in-memory grids only.
	 * @return a list of deleted entity descriptors: unit name, class, and ID. Since we already know inserts and updates (including in-pace ines),
	 *   only deletes are not known until the actual execution since they can be Condition-based. All three kinds of operations need to be passed
	 *   to persistance servers responsiblke for write-behind - immediately after the transaction.
	 * @throws DataStorageException
	 */
	protected abstract List<EntityDescriptor> save(List<StoredBean> inserts, List<StoredBean> updates, List<Delete> deletes,
												   List<InPlaceUpdate<?>> inPlaceUpdates) throws DataStorageException;

	/**
	 * Active and healthy e.g. no stalled persistence, etc. Used internally.
	 * @return if the dartabase is active
	 */
	public abstract boolean isActive();

	/**
	 * Internal wite-behind implementation - relevant for data grids only, not traditional databases (Mongo).
	 * @param now current time
	 * @param allInserts inserts
	 * @param allUpdates updates
	 * @param deletes deleted bean IDs
	 * @param inPlaceUpdates in-place updates
	 * @throws DataStorageException
	 */
	protected abstract void afterSave(Date now, List<StoredBean> allInserts, List<StoredBean> allUpdates, List<EntityDescriptor> deletes,
									  List<InPlaceUpdate<?>> inPlaceUpdates) throws DataStorageException;
}
