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
package com.px100systems.data.plugin.storage;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

import com.px100systems.data.core.Criteria;
import com.px100systems.data.core.DataStorageException;
import com.px100systems.data.core.Delete;
import com.px100systems.data.core.EntityDescriptor;
import com.px100systems.data.core.InPlaceUpdate;
import com.px100systems.data.core.Lock;
import com.px100systems.data.core.StoredBean;
import com.px100systems.data.plugin.persistence.PersistenceLogEntry;

/**
 * In-memory storage provider.<br>
 * <br>
 * <b>Implementations:</b> Hazelcast and Ignite (faster queries).<br>
 *
 * @version 0.3 <br>Copyright (c) 2015 Px100 Systems. All Rights Reserved.<br>
 * @author Alex Rogachevsky
*/
public interface InMemoryStorageProvider {
	interface MessageCallback<T> extends Serializable {
		void process(T message);
	}

	/**
	 * Subscribe to cluster messages
	 *
	 * @param topicName topic
	 * @param c callback
	 * @param <T> typically a String
	 */
	<T> void registerMessageCallback(String topicName, MessageCallback<T> c);

	/**
	 * Send message to every cluster member (including the sender)
	 *
	 * @param topicName topic
	 * @param message message
	 * @param <T> typicallu a String
	 */
	<T> void broadcastMessage(String topicName, T message);

	/**
	 * Join the cluster
	 */
	void start();

	/**
	 * Create a storage "unit": hazelcast map or Ignite cache.
	 * @param unitName typically entity name + tenantId
	 * @param indexedFields field[*] where "*" suffix indicates if the index should be ordered (Hazelcast specific)
	 * @param cls class (non-transient units are homogeneous)
	 * @param transientData if the unit is transient (runtime-only) or persisted (Entity-based)
	 */
	void createMap(Class<?> cls, String unitName, Map<String, Class<?>> indexedFields, boolean transientData);

	/**
	 * Get a single persisted entry: entity or service bean
	 *
	 * @param unitName unit name
	 * @param id PK
	 * @param <T> the class
	 * @return the bean
	 */
	<T> T get(String unitName, Long id);

	/**
	 * Get any bean by its key: used for transient data.
	 *
	 * @param unitName unit name
	 * @param key anything: a String, etc.
	 * @param <T> the class
	 * @return anything: Date, String, etc.
	 */
	<T> T get(String unitName, Object key);

	/**
	 * Atomically inserts or updates any bean: used for transient data.
	 *
	 * @param unitName unit name
	 * @param key anything: a String, etc.
	 * @param bean anything: Date, String, etc.
	 */
	void save(String unitName, Object key, Object bean);

	/**
	 * Atomically deletes any bean: used for transient data.
	 *
	 * @param unitName unit name
	 * @param key anything: a String, etc.
	 */
	void delete(String unitName, Object key);

	/**
	 * Deletes all found keys. Use with caution: used for transient data.
	 *
	 * @param unitName unit name
	 * @param cls bean class
	 * @param criteria search criteria
	 * @param <T> the class
	 */
	<T> void deleteAll(String unitName, Class<T> cls, Criteria criteria);

	/**
	 * Limited search within one entity/tenant i.e. "unit". Any multi-entity searches need to be correlated manually (like JDBC w/o joins).
	 * Works for both tarnsient and persisted units.
	 *
	 * @param unitName unit name
	 * @param cls bean class
	 * @param criteria search criteria
	 * @param orderBy - list of SQL-like strings e.g. {"field1 ASC", "field2 DESC", "field3"}
	 * @param limit mandatory limit
	 * @param <T> the class
	 * @return the bean list
	 */
	<T> List<T> search(String unitName, Class<T> cls, Criteria criteria, List<String> orderBy, Integer limit);

	/**
	 * Limitless search within one entity/tenant i.e. "unit". Any multi-entity searches need to be correlated manually (like JDBC w/o joins)
	 * Works for both tarnsient and persisted units.
	 *
	 * @param unitName a unit name is typically the entity name + tenantId
	 * @param cls entity class - make sure the exact concrete class is specified and not a superclass
	 * @param criteria filter - can be null to get all entities
	 * @param orderBy - list of SQL-like strings e.g. {"field1 ASC", "field2 DESC", "field3"}
	 * @param <T> entity class
	 * @return a closeable cursor (always use try/finally to close)
	 */
	<T> EntityCursor<T> search(String unitName, Class<T> cls, Criteria criteria, List<String> orderBy);

	/**
	 * Just what it name implies. Use with caution - assuming the result list is naturally small. Works for both tarnsient and persisted units.
	 *
	 * @param unitName unit name
	 * @param cls bean class
	 * @param criteria search criteria
	 * @param <T> the class
	 * @return the bean list
	 */
	<T> List<T> getAll(String unitName, Class<T> cls, Criteria criteria);
	
	/**
	 * Provider-level count. Providers w/o native support should throw UnsupportedOperationException
	 *
	 * @param unitName unit name
	 * @param cls bean class
	 * @param criteria search criteria
	 * @return the count
	 */
	long count(String unitName, Class<?> cls, Criteria criteria);
	
	/**
	 * Transactionally save the data. Optional operation - cache-only providers should throw UnsupportedOperationException.<br>
	 * Providers like Hazelcast ensure data collocation/affinity by unit name (which includes tenant ID).
	 *
	 * @param inserts a list of beans to insert
	 * @param updates a list of changed beans to save
	 * @param deletes a list of deletes - eitehr bean IDs or some Conditions to find and delete entities
	 * @param inPlaceUpdates a list of callbacks to execute in-place updates via entry processors. Relevant to in-memory grids only.
	 * @param serviceData whether it is saving the service data like persistence log entries or normal domain entities
	 * @return list of deleted records (their IDs) if any
	 * @throws DataStorageException
	 */
	List<EntityDescriptor> save(List<StoredBean> inserts, List<StoredBean> updates, List<Delete> deletes, List<InPlaceUpdate<?>> inPlaceUpdates, boolean serviceData) throws DataStorageException;
	
	/**
	 * Creates an id generator (one per entity and tenant) and initializes it.
	 *
	 * @param unitName unit name
	 * @param value what to initialize it with
	 */
	void createIdGenerator(String unitName, long value);
	
	/**
	 * Obtains the next incremental ID (one per entity and tenant)
	 * If the generator wasn't created, creates it and initializes to 1 (which it returns).
	 *
	 * @param unitName unit name
	 * @return the next ID
	 */
	long generateId(String unitName);
	
	/**
	 * Request a cluster-wide lock.
	 * @param lockName lock name
	 * @param timeout - max wait time in millisecond (0 means just try the lock)
	 * @return lock
	 */
	Lock lock(String lockName, Long timeout);

	/**
	 * Get atomic long
	 *
	 * @param name name
	 * @param tenantId tenant ID
	 * @return the atomic long - newly created longs should be initialized with zero to be consistent with Hazelcast.
	 */
	long getAtomicLong(String name, Integer tenantId);

	/**
	 * Atomically update long
	 * @param name name
	 * @param tenantId tenant ID
	 * @param value new value
	 */
	void setAtomicLong(String name, Integer tenantId, Long value);

	/**
	 * Atomically increment long
	 * @param name name
	 * @param tenantId tenant ID
	 * @return the new value
	 */
	long incrementAtomicLong(String name, Integer tenantId);

	/**
	 * Atomically decrement long
	 * @param name name
	 * @param tenantId tenant ID
	 * @return the new value
	 */
	long decrementAtomicLong(String name, Integer tenantId);

	/**
	 * Creates the appropriately serializable PersistenceLogEntry
	 * @return the PersistenceLogEntry instance
	 */
	PersistenceLogEntry createPersistenceLogEntry();

	/**
	 * Bulk loader.
	 * @return the loader
	 */
	InMemoryStorageLoader loader();
}
