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

import com.px100systems.data.plugin.storage.EntityCursor;
import com.px100systems.data.plugin.storage.TraditionalStorageProvider;
import com.px100systems.util.serialization.SerializationDefinition;
import org.springframework.beans.factory.annotation.Required;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Traditional (remote) database like Mongo. Synchronizes its state of "tables" and "indexes" with the currently configured entities on startup.<br>
 *
 * @version 0.3 <br>Copyright (c) 2015 Px100 Systems. All Rights Reserved.<br>
 * @author Alex Rogachevsky
 */
@SuppressWarnings("unused")
public class TraditionalDatabase extends DatabaseStorage {
	private TraditionalStorageProvider provider;

	@Required
	public void setProvider(TraditionalStorageProvider provider) {
		this.provider = provider;
	}

	@Override
	protected void init(List<EntityInfo> entities, boolean initializeData) {
		Map<String, List<String>> existingEntities = provider.getSchema(initializeData);

		Map<String, Long> idGenerators = new HashMap<>();

		for (EntityInfo entity : entities) {
			List<String> indexes = existingEntities.get(entity.getUnitName());
			if (indexes == null)
				provider.createEntity(entity.getUnitName(), entity.getIndexes().keySet());
			else {
				for (String index : indexes)
					if (!entity.getIndexes().containsKey(index))
						provider.dropIndex(entity.getUnitName(), index);

				provider.updateEntity(entity.getUnitName(), SerializationDefinition.get(entity.getEntityClass()));

				List<String> newIndexes = new ArrayList<>();
				for (String index : entity.getIndexes().keySet())
					if (!indexes.contains(index))
						newIndexes.add(index);
				if (!newIndexes.isEmpty())
					provider.addIndexes(entity.getUnitName(), newIndexes);

				existingEntities.remove(entity.getUnitName());

				Long maxId = provider.getMaxId(entity.getUnitName());
				if (maxId == null)
					maxId = 0L;

				try {
					Entity e = (Entity)entity.getEntityClass().newInstance();
					e.setTenantId(entity.getTenantId());
					String idGenerator = e.idGeneratorName();
					Long id = idGenerators.get(idGenerator);
					if (id == null)
						id = -1L;
					if (maxId > id)
						idGenerators.put(idGenerator, maxId);
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
			}
		}

		for (String unitName : existingEntities.keySet())
			provider.dropEntity(unitName);

		for (Map.Entry<String, Long> e : idGenerators.entrySet())
			getRuntimeStorage().createIdGenerator(e.getKey(), e.getValue());
	}

	@Override
	protected <T> T get(String unitName, Class<T> cls, Long id) {
		return provider.get(unitName, cls, id);
	}

	@Override
	protected long count(String unitName, Class<?> cls, Criteria criteria) {
		return provider.count(unitName, cls, criteria);
	}

	@Override
	protected <T> List<T> search(String unitName, Class<T> cls, Criteria criteria, List<String> orderBy, Integer limit) {
		return provider.search(unitName, cls, criteria, orderBy, limit);
	}

	@Override
	protected <T> EntityCursor<T> search(String unitName, Class<T> cls, Criteria criteria, List<String> orderBy) {
		return provider.search(unitName, cls, criteria, orderBy);
	}

	@Override
	protected List<EntityDescriptor> save(List<StoredBean> inserts, List<StoredBean> updates, List<Delete> deletes, List<InPlaceUpdate<?>> inPlaceUpdates) throws DataStorageException {
		if (!inPlaceUpdates.isEmpty())
			throw new UnsupportedOperationException("In-place update are not supported by traditional on-disk databases");
		provider.save(inserts, updates, deletes);
		return new ArrayList<>();
	}

	@Override
	protected void shutdown() {
	}

	@Override
	public boolean isActive() {
		return true;
	}

	@Override
	protected void afterSave(Date now, List<StoredBean> allInserts, List<StoredBean> allUpdates, List<EntityDescriptor> deletes, List<InPlaceUpdate<?>> inPlaceUpdates) throws DataStorageException {
	}
}
