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
package com.px100systems.data.plugin.storage.ignite;

import com.google.gson.Gson;
import com.px100systems.data.core.CompoundIndexDescriptor;
import com.px100systems.data.core.Criteria;
import com.px100systems.data.core.DataStorageException;
import com.px100systems.data.core.Delete;
import com.px100systems.data.core.Entity;
import com.px100systems.data.core.EntityDescriptor;
import com.px100systems.data.core.InPlaceUpdate;
import com.px100systems.data.core.Lock;
import com.px100systems.data.core.RawRecord;
import com.px100systems.data.core.StoredBean;
import com.px100systems.data.plugin.persistence.PersistenceLogEntry;
import com.px100systems.data.plugin.persistence.PersistenceProvider;
import com.px100systems.data.plugin.persistence.PersistenceProviderException;
import com.px100systems.data.plugin.storage.EntityCursor;
import com.px100systems.data.plugin.storage.InMemoryStorageLoader;
import com.px100systems.data.plugin.storage.InMemoryStorageProvider;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.IgniteCountDownLatch;
import org.apache.ignite.IgniteDataStreamer;
import org.apache.ignite.Ignition;
import org.apache.ignite.cache.CacheAtomicityMode;
import org.apache.ignite.cache.CacheEntryProcessor;
import org.apache.ignite.cache.CacheMemoryMode;
import org.apache.ignite.cache.CacheMode;
import org.apache.ignite.cache.CachePeekMode;
import org.apache.ignite.cache.CacheTypeMetadata;
import org.apache.ignite.cache.CacheWriteSynchronizationMode;
import org.apache.ignite.cache.affinity.AffinityKey;
import org.apache.ignite.cache.query.QueryCursor;
import org.apache.ignite.cache.query.SqlFieldsQuery;
import org.apache.ignite.cache.query.SqlQuery;
import org.apache.ignite.configuration.CacheConfiguration;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.lang.IgniteBiTuple;
import org.apache.ignite.transactions.Transaction;
import org.springframework.beans.factory.annotation.Required;
import javax.cache.Cache;
import javax.cache.processor.EntryProcessorException;
import javax.cache.processor.MutableEntry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Distributed Ignite in-memory storage provider. See {@link InMemoryStorageProvider}.<br>
 * Used internally by RuntimeStorage, which is in turn used (fully) by InMemoryDatabase or partially by any database.<br>
 * <br>
 * <b>Configuration:</b><br>
 * <ul>
 *   <li>config - Ignite config bean
 *   <li>maxPartitionSize (default 100000 - rarely customized) - the amount of keys (IDs) in one Ignite (tenant-related) partition of one storage unit (map)
 *   <li>offHeap - off-heap memory (JVM memory limits still apply). Use with caution due to some cache entry corruption (Boolean instead of the entity, etc.)
 *   <li>writeThrough - direct persistence provider (not persister server) for development mode (make sure normal write-behind is disabled -
 *      see {@link com.px100systems.data.core.DatabaseStorage}).
 * </ul>
 * <br>
 * <b>Ignite Tips:</b><br>
 * <ul>
 *   <li>Use Externilizable serialization. Implement Externalizable for one base entity all other entities will derive from.
 *     The base entity should be a direct or indirect subclass of {@link com.px100systems.data.core.StoredBean}: most commonly
 *     {@link com.px100systems.data.core.Entity}. {@link com.px100systems.util.serialization.SerializationDefinition} provides a universal Externalizable implementation.
 *   <li>All Ignite query fields need to be annotated with either @Index, @SerializedGetter, or @QueryField. That is if it is not indexed or
 *     an artifical serialized getter, the getter nneds to be annotated with @QueryField. OrderBy fields are advised to be indexed.
 * 	 <li>Since search only works at the top level, use artificail @SerializedGetter annotated getters for derived fields accessing sub-objects and collection.
 * 	   Index them too.
 * </ul>
 *
 * @version 0.3 <br>Copyright (c) 2015 Px100 Systems. All Rights Reserved.<br>
 * @author Alex Rogachevsky
 */
@SuppressWarnings("JavadocReference")
public class IgniteInMemoryStorage implements InMemoryStorageProvider {
	private static Log log = LogFactory.getLog(IgniteInMemoryStorage.class);

	private IgniteConfiguration config;
	private Ignite ignite;

	private long maxPartitionSize = 1000000L;
	private boolean offHeap = false;

	private PersistenceProvider writeThrough = null;

	@SuppressWarnings("unused")
	public void setWriteThrough(PersistenceProvider writeThrough) {
		this.writeThrough = writeThrough;
	}

	@Required
	public void setConfig(IgniteConfiguration config) {
		this.config = config;
	}

	@SuppressWarnings("unused")
	public void setMaxPartitionSize(long maxPartitionSize) {
		this.maxPartitionSize = maxPartitionSize;
	}

	@SuppressWarnings("unused")
	public void setOffHeap(boolean offHeap) {
		this.offHeap = offHeap;
	}

	private AffinityKey<Long> key(Long id, String unitName) {
		return new AffinityKey<>(id, unitName + (id > maxPartitionSize ? id % maxPartitionSize : ""));
	}

	@Override
	public void shutdown() {
		log.info("Forcing Ignite cluster stop");
		Ignition.stopAll(false);
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T> void registerMessageCallback(String topicName, final MessageCallback<T> c) {
		ignite.message().remoteListen(topicName, (nodeId, message) -> { c.process((T)message); return true; });
	}

	@Override
	public <T> void broadcastMessage(String topicName, T message) {
		ignite.message().sendOrdered(topicName, message, 0);
	}

	@Override
	public void start() {
		ignite = Ignition.start(config);
	}

	@Override
	public void createMap(Class<?> cls, String unitName, Map<String, Class<?>> indexedFields, List<CompoundIndexDescriptor> compoundIndexes, boolean transientData) {
		IgniteCache<AffinityKey<Long>, ?> cache = ignite.cache(unitName);
		if (cache != null)
			return;

		CacheConfiguration<AffinityKey<Long>, ?> cacheConfig = new CacheConfiguration<>();
		cacheConfig.setName(unitName);
		cacheConfig.setCacheMode(CacheMode.PARTITIONED);
		cacheConfig.setBackups(config.getAtomicConfiguration().getBackups());
		cacheConfig.setAtomicityMode(transientData ? CacheAtomicityMode.ATOMIC : CacheAtomicityMode.TRANSACTIONAL);
		cacheConfig.setReadThrough(false);
		cacheConfig.setWriteBehindEnabled(false);
		cacheConfig.setWriteSynchronizationMode(CacheWriteSynchronizationMode.FULL_SYNC);

		if (!transientData && offHeap) {
			cacheConfig.setMemoryMode(CacheMemoryMode.OFFHEAP_TIERED);
			cacheConfig.setOffHeapMaxMemory(0);
		}

		Map<String, Class<?>> querydFields = Entity.queryFields(cls);

		if (!indexedFields.isEmpty() || !querydFields.isEmpty() || !compoundIndexes.isEmpty()) {
			CacheTypeMetadata type = new CacheTypeMetadata();
			type.setValueType(cls.getName());

			for (Map.Entry<String, Class<?>> e : indexedFields.entrySet()) {
				String indexField = e.getKey();
				Class<?> fieldClass = e.getValue();

				if (indexField.endsWith("*"))
					indexField = indexField.substring(0, indexField.length() - 1);

				type.getQueryFields().put(indexField, fieldClass);
				type.getAscendingFields().put(indexField, fieldClass);
			}

			for (Map.Entry<String, Class<?>> e : querydFields.entrySet())
				type.getQueryFields().put(e.getKey(), e.getValue());

			for (CompoundIndexDescriptor ci : compoundIndexes) {
				LinkedHashMap<String, IgniteBiTuple<Class<?>,Boolean>> fields = new LinkedHashMap<>();
				for (CompoundIndexDescriptor.Field field : ci.getFields()) {
					fields.put(field.getName(), new IgniteBiTuple<>(field.getType(), !field.isDescending()));
					type.getQueryFields().put(field.getName(), field.getType());
				}
				type.getGroups().put(ci.getName(), fields);
			}

			cacheConfig.setTypeMetadata(Collections.singletonList(type));
		}

		ignite.createCache(cacheConfig);
	}

	// This is a safety method primarily for primitive types, as it doesn't index anything
	private <T> IgniteCache<Object, T> getOrCreateTransientMap(String unitName, boolean atomic) {
		CacheConfiguration<Object, T> cacheConfig = new CacheConfiguration<>();
		cacheConfig.setName(unitName);
		cacheConfig.setCacheMode(CacheMode.PARTITIONED);
		cacheConfig.setBackups(config.getAtomicConfiguration().getBackups());
		cacheConfig.setAtomicityMode(atomic ? CacheAtomicityMode.ATOMIC : CacheAtomicityMode.TRANSACTIONAL);
		cacheConfig.setReadThrough(false);
		cacheConfig.setWriteBehindEnabled(false);
		cacheConfig.setWriteSynchronizationMode(CacheWriteSynchronizationMode.FULL_SYNC);
		return ignite.getOrCreateCache(cacheConfig);
	}

	@Override
	public <T> T get(String unitName, Long id) {
		IgniteCache<AffinityKey<Long>, T> cache = ignite.cache(unitName);
		if (cache == null)
			throw new RuntimeException("Map not found: " + unitName);
		return cache.get(key(id, unitName));
	}

	@Override
	public <T> T get(String unitName, Object key) {
		IgniteCache<Object, T> cache = getOrCreateTransientMap(unitName, true);
		return cache.get(key);
	}

	@Override
	public void save(String unitName, Object key, Object bean) {
		IgniteCache<Object, Object> cache = getOrCreateTransientMap(unitName, true);
		cache.put(key, bean);
	}

	@Override
	public void delete(String unitName, Object key) {
		IgniteCache<Object, Object> cache = getOrCreateTransientMap(unitName, true);
		cache.remove(key);
	}

	@Override
	public <T> void deleteAll(String unitName, Class<T> cls, Criteria criteria) {
		IgniteCache<Object, T> cache = ignite.cache(unitName);
		if (cache == null)
			throw new RuntimeException("Map not found: " + unitName);

		Set<Object> keys = new HashSet<>();
		QueryCursor<Cache.Entry<Object, T>> cursor = cache.query(new SqlQuery<Object, T>(cls, "FROM " + cls.getSimpleName() + " WHERE " +
			criteria.convert(new PredicateQueryBuilder())));
		try {
			for (Iterator<Cache.Entry<Object, T>> i = cursor.iterator(); i.hasNext(); )
				keys.add(i.next().getKey());
		} finally {
			cursor.close();
		}

		cache.removeAll(keys);
	}

	public static class IgniteQueryIterator<T> implements Iterator<T> {
		private Iterator<Cache.Entry<Object, T>> iterator;

		public IgniteQueryIterator(Iterator<Cache.Entry<Object, T>> iterator) {
			this.iterator = iterator;
		}

		@Override
		public boolean hasNext() {
			return iterator.hasNext();
		}

		@Override
		public T next() {
			return iterator.next().getValue();
		}
	}

	@Override
	public <T> List<T> search(String unitName, Class<T> cls, Criteria criteria, List<String> orderBy, Integer limit) {
		String sql = "FROM " + cls.getSimpleName();
		if (criteria != null)
			sql += " WHERE " + criteria.convert(new PredicateQueryBuilder());
		if (orderBy != null && !orderBy.isEmpty()) {
			String ob = "";
			for (String s : orderBy) {
				if (!ob.isEmpty())
					ob += ", ";
				ob += s;
			}
			sql += " ORDER BY " + ob;
		}
		if (limit != null)
			sql += " LIMIT " + limit;

		List<T> result = new ArrayList<>();

		SqlQuery<Object, T> query = new SqlQuery<Object, T>(cls, sql);
		if (limit != null)
			query.setPageSize(limit);

		@SuppressWarnings("ConstantConditions")
		QueryCursor<Cache.Entry<Object, T>> cursor = ignite.cache(unitName).query(query);
		try {
			for (Iterator<Cache.Entry<Object, T>> i = cursor.iterator(); i.hasNext(); )
				result.add(i.next().getValue());
		} finally {
			cursor.close();
		}

		return result;
	}

	@Override
	public <T> EntityCursor<T> search(String unitName, Class<T> cls, Criteria criteria, List<String> orderBy) {
		String sql = "FROM " + cls.getSimpleName();
		if (criteria != null)
			sql += " WHERE " + criteria.convert(new PredicateQueryBuilder());
		if (orderBy != null && !orderBy.isEmpty()) {
			String ob = "";
			for (String s : orderBy) {
				if (!ob.isEmpty())
					ob += ", ";
				ob += s;
			}
			sql += " ORDER BY " + ob;
		}

		final SqlQuery<Object, T> query = new SqlQuery<Object, T>(cls, sql);
		return new EntityCursor<T>() {
			@SuppressWarnings("ConstantConditions")
			private QueryCursor<Cache.Entry<Object, T>> cursor = ignite.cache(unitName).query(query);

			@Override
			public Iterator<T> iterator() {
				return new IgniteQueryIterator<T>(cursor.iterator());
			}

			@Override
			public void close() {
				cursor.close();
			}
		};
	}

	@Override
	public <T> List<T> getAll(String unitName, Class<T> cls, Criteria criteria) {
		if (criteria == null) {
			IgniteCache<Object, T> cache = ignite.cache(unitName);
			if (cache == null)
				throw new RuntimeException("Map not found: " + unitName);

			List<T> result = new ArrayList<>();
			for (Iterator<Cache.Entry<Object, T>> i = cache.iterator(); i.hasNext(); )
				result.add(i.next().getValue());

			return result;
		}

		return search(unitName, cls, criteria, null, null);
	}

	@Override
	public long count(String unitName, Class<?> cls, Criteria criteria) {
		IgniteCache<?, ?> cache = ignite.cache(unitName);
		if (cache == null)
			throw new RuntimeException("Map not found: " + unitName);

		if (criteria == null)
			return cache.size(CachePeekMode.PRIMARY);

		QueryCursor<List<?>> cursor = cache.query(new SqlFieldsQuery("SELECT COUNT(*) FROM " + cls.getSimpleName() +
			" WHERE " + criteria.convert(new PredicateQueryBuilder())));
		Long count = new Long(cursor.iterator().next().get(0).toString());
		cursor.close();

		return count;
	}

	@Override
	public void createIdGenerator(String unitName, long value) {
		log.info("Initialized ID generator '" + unitName + "' with " + value);
		ignite.atomicLong("IDgen_" + unitName, value, true);
	}

	@Override
	public long generateId(String unitName) {
		return ignite.atomicLong("IDgen_" + unitName, 0, true).incrementAndGet();
	}

	public static class IgniteLock implements Lock {
		private IgniteCountDownLatch latch;

		public IgniteLock(IgniteCountDownLatch latch) {
			this.latch = latch;
		}

		@Override
		public void unlock() {
			latch.countDown();
		}
	}

	@Override
	public Lock lock(String lockName, Long timeout) {
		IgniteCountDownLatch latch = ignite.countDownLatch("LATCH__" + lockName, 2, true, true);
		if (latch.count() > 1) {
			latch.countDown();
			return new IgniteLock(latch);
		}

		latch.await(timeout, TimeUnit.MILLISECONDS);
		return lock(lockName, timeout);
	}

	@Override
	public long getAtomicLong(String name, Integer tenantId) {
		return ignite.atomicLong(name + "@long" + tenantId, 0, true).get(); // Consistent with Hazelcast's auto-created Long
	}

	@Override
	public void setAtomicLong(String name, Integer tenantId, Long value) {
		ignite.atomicLong(name + "@long" + tenantId, 0, true).getAndSet(value);
	}

	@Override
	public long incrementAtomicLong(String name, Integer tenantId) {
		return ignite.atomicLong(name + "@long" + tenantId, 0, true).incrementAndGet();
	}

	@Override
	public long decrementAtomicLong(String name, Integer tenantId) {
		return ignite.atomicLong(name + "@long" + tenantId, 0, true).decrementAndGet();
	}

	@Override
	public PersistenceLogEntry createPersistenceLogEntry() {
		return new PersistenceLogEntry();
	}

	@Override
	public InMemoryStorageLoader loader() {
		return new InMemoryStorageLoader() {
			Map<String, IgniteDataStreamer<AffinityKey<Long>, Object>> streamers = new HashMap<>();

			@Override
			public void store(List<StoredBean> inserts) {
				for (StoredBean entity : inserts) {
					String unitName = entity.unitName();
					IgniteDataStreamer<AffinityKey<Long>, Object> streamer = streamers.get(unitName);
					if (streamer == null) {
						streamer = ignite.dataStreamer(unitName);
						streamers.put(unitName, streamer);
					}
					streamer.addData(key(entity.getId(), unitName), entity);
				}
			}

			@Override
			public void close() {
				for (IgniteDataStreamer<AffinityKey<Long>, ?> streamer : streamers.values())
					streamer.close();
				streamers.clear();
			}
		};
	}

	public static class EntryUpdater implements CacheEntryProcessor<AffinityKey<Long>, StoredBean, Object> {
		private InPlaceUpdate<?> update;

		@SuppressWarnings("unused")
		public EntryUpdater() {
		}

		public EntryUpdater(InPlaceUpdate<?> update) {
			this.update = update;
		}

		@Override
		public Object process(MutableEntry<AffinityKey<Long>, StoredBean> mutableEntry, Object... objects) throws EntryProcessorException {
			if (mutableEntry.getKey().key() != null) {
				StoredBean value = mutableEntry.getValue();
				update.eval(value);
				mutableEntry.setValue(value);
			}
			return null;
		}
	}

	@Override
	public List<EntityDescriptor> save(List<StoredBean> inserts, List<StoredBean> updates, List<Delete> deletes, List<InPlaceUpdate<?>> inPlaceUpdates, boolean serviceData) throws DataStorageException {
		Transaction tx = ignite.transactions().txStart();
		try {
			Map<String, IgniteCache<AffinityKey<Long>, StoredBean>> maps = new HashMap<>();
			for (StoredBean bean : inserts) {
				String unitName = bean.unitName();
				if (!maps.containsKey(unitName)) {
					IgniteCache<AffinityKey<Long>, StoredBean> map = ignite.cache(unitName);
					maps.put(unitName, map);
				}
			}
			for (StoredBean bean : updates) {
				String unitName = bean.unitName();
				if (!maps.containsKey(unitName)) {
					IgniteCache<AffinityKey<Long>, StoredBean> map = ignite.cache(unitName);
					maps.put(unitName, map);
				}
			}
			for (Delete delete : deletes) {
				String unitName = delete.getUnitName();
				if (!maps.containsKey(unitName)) {
					IgniteCache<AffinityKey<Long>, StoredBean> map = ignite.cache(unitName);
					maps.put(unitName, map);
				}
			}
			for (InPlaceUpdate<?> update : inPlaceUpdates) {
				String unitName = update.getUnitName();
				if (!maps.containsKey(unitName)) {
					IgniteCache<AffinityKey<Long>, StoredBean> map = ignite.cache(unitName);
					maps.put(unitName, map);
				}
			}

			for (StoredBean bean : inserts) {
				IgniteCache<AffinityKey<Long>, StoredBean> map = maps.get(bean.unitName());
				AffinityKey<Long> key = key(bean.getId(), bean.unitName());
				if (map.containsKey(key))
					throw new RuntimeException("Bad ID generator");
				map.put(key, bean);
			}

			for (StoredBean bean : updates) {
				IgniteCache<AffinityKey<Long>, StoredBean> map = maps.get(bean.unitName());
				AffinityKey<Long> key = key(bean.getId(), bean.unitName());
				if (map.containsKey(key))
					map.put(key, bean);
				else
					throw new DataStorageException("stale");
			}

			List<EntityDescriptor> result = new ArrayList<>();
			for (Delete delete : deletes) {
				if (delete.getCriteria() == null)
					throw new RuntimeException("Empty Delete filter");

				IgniteCache<AffinityKey<Long>, StoredBean> map = maps.get(delete.getUnitName());
				if (delete.getId() != null) {
					result.add(new EntityDescriptor(delete.getEntityClass(), delete.getId(), delete.getUnitName()));
					map.remove(key(delete.getId(), delete.getUnitName()));
				} else {
					Set<AffinityKey<Long>> keys = new HashSet<>();
					QueryCursor<Cache.Entry<AffinityKey<Long>, Object>> cursor = map.query(new SqlQuery<AffinityKey<Long>, Object>(delete.getEntityClass(),
						"FROM " + delete.getEntityClass().getSimpleName() + " WHERE " + delete.getCriteria().convert(new PredicateQueryBuilder())));
					try {
						for (Iterator<Cache.Entry<AffinityKey<Long>, Object>> i = cursor.iterator(); i.hasNext(); )
							keys.add(i.next().getKey());
					} finally {
						cursor.close();
					}

					for (AffinityKey<Long> key : keys) {
						result.add(new EntityDescriptor(delete.getEntityClass(), key.key(), delete.getUnitName()));
						map.remove(key);
					}
				}
			}

			for (final InPlaceUpdate<?> update : inPlaceUpdates) {
				IgniteCache<AffinityKey<Long>, StoredBean> map = maps.get(update.getUnitName());
				map.invoke(key(update.getId(), update.getUnitName()), new EntryUpdater(update));
			}

			if (writeThrough != null && !serviceData) {
				Gson gson = RawRecord.createGson();
				PersistenceProvider.Connection conn = writeThrough.open();
				try {
					List<RawRecord> insertsOrUpdates = new ArrayList<RawRecord>();
					for (StoredBean bean : inserts)
						if (Entity.class.isAssignableFrom(bean.getClass()))
							insertsOrUpdates.add(new RawRecord((Entity)bean, gson));
					for (StoredBean bean : updates)
						if (Entity.class.isAssignableFrom(bean.getClass()))
							insertsOrUpdates.add(new RawRecord((Entity)bean, gson));
					writeThrough.transactionalSave(conn, insertsOrUpdates, result, new Date().getTime());
				} catch (PersistenceProviderException e) {
					throw new DataStorageException(e);
				} finally {
					conn.close();
				}
			}

			tx.commit();
			return result;
		} catch (DataStorageException e) {
			tx.rollback();
			throw e;
		} catch (Throwable e) {
			tx.rollback();
			throw new RuntimeException(e);
		}
	}
}
