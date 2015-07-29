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

import com.px100systems.data.plugin.storage.InMemoryStorageProvider;
import org.springframework.beans.factory.annotation.Required;
import java.util.ArrayList;
import java.util.List;

/**
 * Non-persistent in-memory storage aka "data grid". Used internally by the system and also can be used directly to manage transient objects.<br>
 * See setters for configuration.<br>
 *
 * @version 0.3 <br>Copyright (c) 2015 Px100 Systems. All Rights Reserved.<br>
 * @author Alex Rogachevsky
 */
public class RuntimeStorage {
	private InMemoryStorageProvider provider;
	private TenantLoader tenantLoader;
	private List<Class<?>> transientUnits = new ArrayList<>();

	/**
	 * Tenant Loader to create new units as tenants are being added.
	 * @param tenantLoader tenant loader/manager
	 */
	public void setTenantLoader(TenantLoader tenantLoader) {
		this.tenantLoader = tenantLoader;
	}

	/**
	 * Data grid (Hazelcast, Ignite, etc.) storage provider.
	 * @param provider storage provider.
	 */
	@Required
	public void setProvider(InMemoryStorageProvider provider) {
		this.provider = provider;
	}

	protected InMemoryStorageProvider getProvider() {
		return provider;
	}

	protected Long generateId(String idGeneratorName) throws DataStorageException {
		return provider.generateId(idGeneratorName);
	}

	public void onNewTenant(BaseTenantConfig tenant) throws DataStorageException {
		for (Class<?> cls : transientUnits)
			provider.createMap(cls, Entity.unitFromClass(cls, tenant.getId()), Entity.indexes(cls), true);
	}

	/**
	 * Create a transiebt unit - for all tenants.
	 * @param cls class - not necessarily Entity-based
	 */
	public void createTransientUnit(Class<?> cls) {
		transientUnits.add(cls);

		List<Integer> tenantIds = new ArrayList<>();
		if (tenantLoader != null)
			for (BaseTenantConfig t : tenantLoader.load())
				tenantIds.add(t.getId());
		else
			tenantIds.add(0);

		for (Integer tenantId : tenantIds)
			provider.createMap(cls, Entity.unitFromClass(cls, tenantId), Entity.indexes(cls), true);
	}

	/**
	 * Create a transiebt unit - for a specific tenant.
	 * @param cls class - not necessarily Entity-based
	 * @param tenantId tenant ID
	 */
	public void createTransientUnit(Class<?> cls, int tenantId) {
		provider.createMap(cls, Entity.unitFromClass(cls, tenantId), Entity.indexes(cls), true);
	}

	public void createIdGenerator(String name, Long initialValue) {
		provider.createIdGenerator(name, initialValue);
	}

	/**
	 * Get atomic long (per tenant). Newly created Longs are initialized with zero for consistence with Hazelcast.
	 * @param name name of atomic long
	 * @param tenantId tenant ID
	 * @return atomic long
	 */
	public Long getLong(String name, int tenantId) {
		return provider.getAtomicLong(name, tenantId);
	}

	/**
	 * Set atomic long.
	 * @param name name of atomic long
	 * @param tenantId tenant ID
	 * @param value atomic long value
	 */
	public void setLong(String name, int tenantId, Long value) {
		provider.setAtomicLong(name, tenantId, value);
	}

	/**
	 * Increment atomic long.
	 * @param name name of atomic long
	 * @param tenantId tenant ID
	 * @return atomic long's new value
	 */
	public Long incrementLong(String name, int tenantId) {
		return provider.incrementAtomicLong(name, tenantId);
	}

	/**
	 * Decrement atomic long.
	 * @param name name of atomic long
	 * @param tenantId tenant ID
	 * @return atomic long's new value
	 */
	@SuppressWarnings("unused")
	public Long decrementLong(String name, int tenantId) {
		return provider.decrementAtomicLong(name, tenantId);
	}

	/**
	 * Get transient bean - anything: objects, Strings, Dates, etc.
	 * @param cls bean class
	 * @param tenantId tenant ID
	 * @param key any key e.g. String
	 * @param <T> bean class
	 * @return the bean or null
	 */
	public <T> T getTransient(Class<T> cls, int tenantId, String key) {
		return provider.get(Entity.unitFromClass(cls, tenantId), key);
	}

	/**
	 * Get unordered transient beans. use with caution, as there is no limit
	 * @param cls bean class
	 * @param tenantId tenant ID
	 * @param criteria - filter criteria or null to get everything. Criteria works for normal Entity subclasses and arbitrary beans.
	 * @param <T> bean class
	 * @return a list of beans.
	 */
	public <T> List<T> getAllTransient(Class<T> cls, int tenantId, Criteria criteria) {
		return provider.getAll(Entity.unitFromClass(cls, tenantId), cls, criteria);
	}

	/**
	 * Get ordered transient beans. use with caution, as there is no limit
	 * @param cls bean class
	 * @param tenantId tenant ID
	 * @param criteria - filter criteria or null to get everything. Criteria works for normal Entity subclasses and arbitrary beans.
	 * @param orderBy - an optional list of SQL-like ORDER BY strings e.g. {"field1 ASC", "field2 DESC", "field3"}
	 * @param limit mandatory list limit
	 * @param <T> bean class
	 * @return a list of beans.
	 */
	public <T> List<T> findTransient(Class<T> cls, int tenantId, Criteria criteria, List<String> orderBy, int limit) {
		return provider.search(Entity.unitFromClass(cls, tenantId), cls, criteria, orderBy, limit);
	}

	/**
	 * Count transient beans. use with caution, as there is no limit
	 * @param cls bean class
	 * @param tenantId tenant ID
	 * @param criteria - filter criteria or null to get everything. Criteria works for normal Entity subclasses and arbitrary beans.
	 * @param <T> bean class
	 * @return the count
	 */
	public <T> long countTransient(Class<T> cls, int tenantId, Criteria criteria) {
		return provider.count(Entity.unitFromClass(cls, tenantId), cls, criteria);
	}

	/**
	 * Find out if the beans match the criteria
	 * @param cls bean class
	 * @param tenantId tenant ID
	 * @param criteria - filter criteria.
	 * @param <T> bean class
	 * @return the count
	 */
	public <T> boolean existsTransient(Class<T> cls, int tenantId, Criteria criteria) {
		return !findTransient(cls, tenantId, criteria, null, 1).isEmpty();
	}

	/**
	 * Save: insert or update any arbitrary bean by arbitrary key
	 * @param cls bean class
	 * @param tenantId tenant ID
	 * @param key any key e.g. String
	 * @param bean the bean tp save
	 * @param <T> bean class
	 */
	public <T> void saveTransient(Class<T> cls, int tenantId, String key, T bean) {
		provider.save(Entity.unitFromClass(cls, tenantId), key, bean);
	}

	/**
	 * Delete any bean by key
	 * @param cls bean class
	 * @param tenantId tenant ID
	 * @param key any key e.g. String
	 */
	public void deleteTransient(Class<?> cls, int tenantId, String key) {
		provider.delete(Entity.unitFromClass(cls, tenantId), key);
	}

	/**
	 * Delete all beans that match the criteria
	 * @param cls bean class
	 * @param tenantId tenant ID
	 * @param criteria filyter criteria
	 */
	public <T> void deleteAllTransient(Class<T> cls, int tenantId, Criteria criteria) {
		provider.deleteAll(Entity.unitFromClass(cls, tenantId), cls, criteria);
	}

	public <T> void registerMessageCallback(String topicName, InMemoryStorageProvider.MessageCallback<T> c) {
		provider.registerMessageCallback(topicName, c);
	}

	public <T> void broadcastMessage(String topicName, T message) {
		provider.broadcastMessage(topicName, message);
	}

	public Lock lock(String lockName, Long timeout) {
		return provider.lock(lockName, timeout);
	}
}
