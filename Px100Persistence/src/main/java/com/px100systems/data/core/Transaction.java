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

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import com.px100systems.data.plugin.storage.EntityCursor;
import com.px100systems.util.serialization.SerializationDefinition;

/**
 * Consumer interface with the data cluster: read or write transaction.<br>
 * Business logic code should only interact with Transaction instead of DatabaseStorage.<br>
 * Transaction is always limited to one tenant. use tenant ID zero for single-tenant systems.<br>
 * 
 * @version 0.3 <br>Copyright (c) 2015 Px100 Systems. All Rights Reserved.<br>
 * @author Alex Rogachevsky
*/
public class Transaction {
	private DatabaseStorage ds;
	private Integer tenantId;
	private boolean committed = false;

	private int orderNo = 0;
	private List<Entity> inserts = new ArrayList<Entity>(); 
	private List<Entity> optimisticUpdates = new ArrayList<Entity>();
	private List<Entity> updates = new ArrayList<Entity>();
	private List<Delete> deletes = new ArrayList<Delete>();
	private List<InPlaceUpdate<?>> inPlaceUpdates = new ArrayList<>();

	protected Transaction(DatabaseStorage ds, Integer tenantId) {
		this.ds = ds;
		this.tenantId = tenantId;
	}

	/**
	 * A rarely used method to create a temporary transaction for another tenant - or for the same one after committing the current one
	 * (useful for processing data in smaller batches).
	 * @param tenantId tenant ID
	 */
	public Transaction transaction(Integer tenantId) throws DataStorageException {
		return ds.transaction(tenantId);
	}

	/**
	 * Current tenant ID.
	 * @return tenant ID for thios transaction.
	 */
	public Integer getTenantId() {
		return tenantId;
	}

	/**
	 * Adds the entity to the list of inserts. Idempotent. Sets id.
	 * @param entity the bean
	 */
	public void insert(Entity entity) {
		if (entity.getId() != null) 
			return;	

		if (ds != null && ds.isActive()) {
			if (tenantId != null)
				entity.setTenantId(tenantId);
			try {
				entity.setId(ds.getRuntimeStorage().generateId(entity.idGeneratorName()));
			} catch (DataStorageException e) {
				throw new RuntimeException(e);
			}
			entity.setOperationOrderNo(orderNo++);
			inserts.add(entity);
		}
	}
	
	/**
	 * Adds the entity to the list of updates. Idempotent.
	 * @param entity the bean
	 * @param optimisticCheck check if the record has already been updated/deleted at the time of commit
	 */
	public void update(Entity entity, boolean optimisticCheck) {
		for (int i = 0, n = inserts.size(); i < n; i++) {
			Entity e =  inserts.get(i); 
			if (e.getClass().equals(entity.getClass()) && e.getId().equals(entity.getId()))
				return;
		}
		for (int i = 0, n = updates.size(); i < n; i++) {
			Entity e =  updates.get(i); 
			if (e.getClass().equals(entity.getClass()) && e.getId().equals(entity.getId())) {
				updates.remove(i);
				break;
			}
		}
		for (int i = 0, n = optimisticUpdates.size(); i < n; i++) {
			Entity e =  optimisticUpdates.get(i); 
			if (e.getClass().equals(entity.getClass()) && e.getId().equals(entity.getId())) {
				optimisticUpdates.remove(i);
				break;
			}
		}

		entity.setOperationOrderNo(orderNo++);
		if (ds != null && ds.isActive())
			if (optimisticCheck)
				optimisticUpdates.add(entity);
			else 
				updates.add(entity);
	}

	/**
	 * Process the entity in storage. Useful for in-memory storages to avoid serializing the whole bean and sending it across the network.<br>
	 * Only sends the required updates (field changes, etc.) to the primary and backup nodes.<br>
	 * Not supported by traditional databases.<br>
	 * Some storage providers like Hazelcast do not support them inside transactions, so those updates are executed first and then rolled back manually.<br>
	 *
	 * @param cls bean class
	 * @param id entity ID
	 * @param processor entry processor callback/lambda to do something with the bean
	 * @param <T> bean class
	 */
	public <T>void update(Class<T> cls, Long id, EntityProcessor<T> processor) {
		inPlaceUpdates.add(new InPlaceUpdate<>(orderNo++, cls, id, tenantId, processor));
	}

	/**
	 * Adds the entity to the list of deletes. Also executes teh cascadeDelete(0 method to delete any dependent entitites (children, etc.).
	 * Overwrite Entity.cascadeDelete() when needed.
	 * @param entity the bean
	 */
	public void deleteWithDependents(Entity entity) {
		if (ds != null && ds.isActive()) {
			entity.cascadeDelete();
			deletes.add(new Delete(orderNo++, entity));
		}
	}
	
	/**
	 * Adds an expression to the list of deletes.
	 *
	 * @param entityClass entity class
	 * @param criteria search criteria
	 */
	public void delete(Class<? extends StoredBean> entityClass, Criteria criteria) {
		validateCriteria(entityClass, criteria);
		if (ds != null && ds.isActive())
			deletes.add(new Delete(orderNo++, entityClass, tenantId, criteria));
	}

	/**
	 * Get entity by ID.
	 *
	 * @param entityClass bean class
	 * @param id entity ID
	 * @param <T> bean class
	 * @return the bean or null
	 */
	@SuppressWarnings("unchecked")
	public <T extends Entity> T get(Class<T> entityClass, Long id) {
		if (ds == null || !ds.isActive())
			return null;

		entityClass = (Class<T>)ds.entityClass(entityClass.getSimpleName());
		return ds.get(Entity.unitFromClass(entityClass, tenantId), entityClass, id);
	}

	/**
	 * Native count - faster than iterating through the iterator - default behavior if the storage provider doesn't support it.
	 *
	 * @param entityClass bean class
	 * @param criteria search criteria
	 * @return the count
	 */
	public long count(Class<? extends StoredBean> entityClass, Criteria criteria) {
		validateCriteria(entityClass, criteria);
		if (ds == null || !ds.isActive())
			return 0;

		return ds.count(Entity.unitFromClass(ds.entityClass(entityClass.getSimpleName()), tenantId), entityClass, criteria);
	}

	/**
	 * Convenience method to get exactly one bean.
	 * @param entityClass bean class
	 * @param criteria search criteria
	 * @param <T> bean class
	 * @return the bean or null
	 */
	public <T extends Entity> T findOne(Class<T> entityClass, Criteria criteria) {
		List<T> i = find(entityClass, criteria, null, 1);
		return i.isEmpty() ? null : i.get(0);
	}

	/**
	 * Convenience method to check if the bean exists.
	 * @param entityClass bean class
	 * @param criteria search criteria
	 * @return if the bean exists
	 */
	public boolean exists(Class<? extends StoredBean> entityClass, Criteria criteria) {
		return !find(entityClass, criteria, null, 1).isEmpty();
	}

	/**
	 * Criteria-based search with limit.<br>
	 * There are no "joins" of different entities - all such queries should be done inside the loop manually. Joins are generally rare with good NoSQL design.<br>
	 *
	 * @param entityClass bean class
	 * @param criteria search criteria
	 * @param orderBy - an optional list of SQL-like ORDER BY strings e.g. {"field1 ASC", "field2 DESC", "field3"}
	 * @param limit mandatory list limit
	 * @param <T> bean class
	 * @return the list of beans
	 */
	@SuppressWarnings("unchecked")
	public <T extends StoredBean> List<T> find(Class<T> entityClass, Criteria criteria, List<String> orderBy, Integer limit) {
		validateCriteria(entityClass, criteria);
		if (ds == null || !ds.isActive())
			return null;
		
		entityClass = (Class<T>)ds.entityClass(entityClass.getSimpleName());

		return ds.search(Entity.unitFromClass(entityClass, tenantId), entityClass, criteria, orderBy, limit);
	}

	/**
	 * Criteria-based search without limit.<br>
	 * There are no "joins" of different entities - all such queries should be done inside the loop manually. Joins are generally rare with good NoSQL design.<br>
	 *
	 * @param entityClass bean class
	 * @param criteria search criteria
	 * @param orderBy - an optional list of SQL-like ORDER BY strings e.g. {"field1 ASC", "field2 DESC", "field3"}
	 * @param <T> bean class
	 * @return a closeable cursor (always use try/finally to close)
	 */
	@SuppressWarnings("unchecked")
	public <T extends StoredBean> EntityCursor<T> find(Class<T> entityClass, Criteria criteria, List<String> orderBy) {
		validateCriteria(entityClass, criteria);
		if (ds == null || !ds.isActive())
			return null;

		entityClass = (Class<T>)ds.entityClass(entityClass.getSimpleName());

		return ds.search(Entity.unitFromClass(entityClass, tenantId), entityClass, criteria, orderBy);
	}

	/**
	 * Transaction commit.
	 * Does nothing if there are no data changes.
	 *
	 * @return if the transaction was committed or not due to the database being inactive.
	 * @throws DataStorageException
	 */
	public boolean commit() throws DataStorageException {
		if (committed)
			throw new DataStorageException("committed");
	
		if (inserts.isEmpty() && optimisticUpdates.isEmpty() && updates.isEmpty() && deletes.isEmpty() && inPlaceUpdates.isEmpty())
			return true;
		
		if (!ds.isActive())
			return false;
		
		for (Entity entity : optimisticUpdates) {
			Entity savedEntity = get(entity.getClass(), entity.getId());
			if (savedEntity == null || savedEntity.getModifiedAt().after(entity.getModifiedAt()))
				throw new DataStorageException("stale");
		}
		
		Date now = new Date();
		
		List<StoredBean> allInserts = new ArrayList<StoredBean>();
		for (Entity e : inserts) {
			e.setCreatedAt(now);
			e.setModifiedAt(now);
			allInserts.add(e);
		}

		List<StoredBean> allUpdates = new ArrayList<StoredBean>();
		for (Entity e : optimisticUpdates) {
			e.setModifiedAt(now);
			allUpdates.add(e);
		}
		for (Entity e : updates) {
			e.setModifiedAt(now);
			allUpdates.add(e);
		}

		ds.afterSave(now, allInserts, allUpdates, ds.save(allInserts, allUpdates, deletes, inPlaceUpdates), inPlaceUpdates);
		committed = true; // once committed, cannot do it again
		return true;
	}

	private void validateCriteria(Class<?> entityClass, Criteria criteria) {
		if (criteria != null) {
			SerializationDefinition def = SerializationDefinition.get(entityClass);
			if (def != null)
				def.checkFields(criteria.fields());
		}
	}
}