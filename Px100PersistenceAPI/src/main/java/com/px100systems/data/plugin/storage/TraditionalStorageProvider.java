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

import com.px100systems.data.core.Criteria;
import com.px100systems.data.core.DataStorageException;
import com.px100systems.data.core.Delete;
import com.px100systems.data.core.StoredBean;
import com.px100systems.util.serialization.SerializationDefinition;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Traditional database provider.
 * 
 * @version 0.3 <br>Copyright (c) 2015 Px100 Systems. All Rights Reserved.<br>
 * @author Alex Rogachevsky
*/
public interface TraditionalStorageProvider {
	/**
	 * Return a map of unit names to their indexes
	 *
	 * @param reset whether to reinitialize the database schema
	 * @return the map of existing unit indexes
	 */
	Map<String, List<String>> getSchema(boolean reset);

	/**
	 * Create entity
	 * @param unitName entity name
	 * @param indexedFields indexed fields (normal, w/o any suffixes)
	 */
	void createEntity(String unitName, Collection<String> indexedFields);

	/**
	 * Drop entity
	 * @param unitName entity name
	 */
	void dropEntity(String unitName);

	/**
	 * Alter the entity (SQL providers only)
	 *
	 * @param unitName unit (table) name
	 * @param def entity class definition
	 */
	void updateEntity(String unitName, SerializationDefinition def);

	/**
	 * Create new indexes
	 *
	 * @param unitName unit (table) name
	 * @param newIndexes new indexes to create
	 */
	void addIndexes(String unitName, List<String> newIndexes);

	/**
	 * Drop index
	 * @param unitName unit (table) name
	 * @param obsoleteIndex index to delete
	 */
	void dropIndex(String unitName, String obsoleteIndex);

	/**
	 * Returns max ID or null f there are no entity records
	 *
	 * @param unitName unit (table) name
	 * @return max ID (Long PK) in that table
	 */
	Long getMaxId(String unitName);

	/**
	 * Get a single entry: entity or service bean
	 *
	 * @param unitName unit (table) name
	 * @param cls entity class
	 * @param id PK
	 * @param <T> entity class
	 * @return the bean or null
	 */
	<T> T get(String unitName, Class<T> cls, Long id);

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
	 * Provider-level count. Providers w/o native support should throw UnsupportedOperationException
	 *
	 * @param unitName unit name
	 * @param cls bean class
	 * @param criteria search criteria
	 * @return the count
	 */
	long count(String unitName, Class<?> cls, Criteria criteria);
	
	/**
	 * Transactionally save the data.
	 * Non-transactional providers like Mongo should manually rollback and rethrow the exception
	 *
	 * @param inserts a list of beans to insert
	 * @param updates a list of changed beans to save
	 * @param deletes a list of deletes - eitehr bean IDs or some Conditions to find and delete entities
	 * @throws DataStorageException
	 */
	void save(List<StoredBean> inserts, List<StoredBean> updates, List<Delete> deletes) throws DataStorageException;
}
