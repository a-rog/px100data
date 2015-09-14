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
package com.px100systems.data.plugin.storage.hazelcast;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import com.google.gson.Gson;
import com.hazelcast.client.HazelcastClient;
import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.config.Config;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.map.AbstractEntryProcessor;
import com.hazelcast.nio.ObjectDataInput;
import com.hazelcast.nio.ObjectDataOutput;
import com.hazelcast.nio.serialization.DataSerializable;
import com.hazelcast.util.IterationType;
import com.px100systems.data.core.CompoundIndexDescriptor;
import com.px100systems.data.core.EntityDescriptor;
import com.px100systems.data.core.InPlaceUpdate;
import com.px100systems.data.plugin.persistence.PersistenceLogEntry;
import com.px100systems.data.plugin.storage.EntityCursor;
import com.px100systems.data.plugin.storage.InMemoryStorageLoader;
import com.px100systems.data.plugin.storage.InMemoryStorageProvider;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import com.hazelcast.core.ExecutionCallback;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.ILock;
import com.hazelcast.core.IMap;
import com.hazelcast.core.ITopic;
import com.hazelcast.core.Message;
import com.hazelcast.core.MessageListener;
import com.hazelcast.core.PartitionAware;
import com.hazelcast.core.TransactionalMap;
import com.hazelcast.query.PagingPredicate;
import com.hazelcast.query.Predicate;
import com.hazelcast.transaction.TransactionContext;
import com.hazelcast.transaction.TransactionOptions;
import com.hazelcast.transaction.TransactionOptions.TransactionType;
import com.px100systems.data.core.Criteria;
import com.px100systems.data.core.DataStorageException;
import com.px100systems.data.core.Delete;
import com.px100systems.data.core.Entity;
import com.px100systems.data.core.Lock;
import com.px100systems.data.core.OrderBy;
import com.px100systems.data.core.RawRecord;
import com.px100systems.data.core.StoredBean;
import com.px100systems.data.plugin.persistence.PersistenceProvider;
import com.px100systems.data.plugin.persistence.PersistenceProviderException;
import com.px100systems.data.plugin.persistence.PersistenceProvider.Connection;
import org.springframework.beans.factory.annotation.Required;

/**
 * Distributed Hazelcast in-memory storage provider. See {@link InMemoryStorageProvider}.<br>
 * Used internally by RuntimeStorage, which is in turn used (fully) by InMemoryDatabase or partially by any database.<br>
 * <br>
 * <b>Configuration:</b><br>
 * <ul>
 * 	<li>config - either Hazelcast's full-node Config or ClientConfig if this is a client node.
 *  <li>maxPartitionSize (default 100000 - rarely customized) - the amount of keys (IDs) in one Hazelcast (tenant-related) partition of one storage unit (map).
 *  <li>writeThrough - direct persistence provider (not persister server) for development mode (make sure normal write-behind is disabled -
 *    see {@link com.px100systems.data.core.DatabaseStorage}).
 * </ul>
 * <br>
 * <b>Hazelcast Performance Tips:</b><br>
 * <ul>
 * 	<li>Use Portable serialization. See {@link PortableImpl}.
 * 	<li>Annotate important search fields with @Index
 * 	<li>Since search only works at the top level, use artificail @SerializedGetter annotated getters for derived fields accessing sub-objects and collection.
 * 	  Index them too.
 * 	<li>All OrderBy fields should have matching OrderBy methods returning {@link OrderBy}: e.g. public static OrderBy orderByIdXyz(...) { ... } for field xyz.
 * 	  It'll work w/o those methods too using slow universal reflection-based comparators.
 * </ul>
 *
 * @version 0.3 <br>Copyright (c) 2015 Px100 Systems. All Rights Reserved.<br>
 * @author Alex Rogachevsky
*/
@SuppressWarnings("JavadocReference")
public class HazelcastInMemoryStorage implements InMemoryStorageProvider {
	private static Log log = LogFactory.getLog(HazelcastInMemoryStorage.class);

	private Object config;
	private long maxPartitionSize = 1000000L;
	private PersistenceProvider writeThrough = null;

	private HazelcastInstance hz; // either full instance or client (the provider doesn't care)

	@SuppressWarnings("unused")
	public void setWriteThrough(PersistenceProvider writeThrough) {
		this.writeThrough = writeThrough;
	}

	public static class Key implements DataSerializable, Comparable<Key>, PartitionAware<String> {
		private long id = 0;
	    private String partitionKey = "";

	    @SuppressWarnings("unused")
		public Key() {
	    }

	    public Key(Long id, String unitName, long maxPartitionSize) {
			this.id = id;
			this.partitionKey = unitName + (id > maxPartitionSize ? id % maxPartitionSize : "");
		}

		@Override
		public int hashCode() {
			return new Long(id).hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			return (obj != null) && (obj instanceof Key) && (id == ((Key)obj).id);
		}

		@Override
	    public String toString() {
	        return id + "." + partitionKey;
	    }

		@Override
		public String getPartitionKey() {
			return partitionKey;
		}

		@SuppressWarnings("NullableProblems")
		@Override
		public int compareTo(Key o) {
			if (o == null)
				return 1;
			return new Long(id).compareTo(o.id);
		}

		@Override
		public void writeData(ObjectDataOutput out) throws IOException {
			out.writeLong(id);
			out.writeUTF(partitionKey);
		}

		@Override
		public void readData(ObjectDataInput in) throws IOException {
			id = in.readLong();
			partitionKey = in.readUTF();
		}
	}
	
	public static class MemberPropertyTask implements Callable<String>, DataSerializable {
		private String property = null;

	    @SuppressWarnings("unused")
		public MemberPropertyTask() {
	    }

	    public MemberPropertyTask(String property) {
	        this.property = property;
	    }

	    @Override
	    public String call() {
	        return System.getProperty(property);
	    }

		@Override
		public void writeData(ObjectDataOutput out) throws IOException {
			out.writeUTF(property);
		}

		@Override
		public void readData(ObjectDataInput in) throws IOException {
			property = in.readUTF();
		}
	}
	
	@SuppressWarnings("rawtypes")
	public static class OrderByComparator implements Comparator<Map.Entry>, Serializable {
		private List<OrderBy> orderBy;

		@SuppressWarnings("unused")
		public OrderByComparator() {
		}

		public OrderByComparator(List<OrderBy> orderBy) {
			this.orderBy = orderBy;
		}

		@Override
		@SuppressWarnings({ "unchecked" })
		public int compare(Map.Entry o1, Map.Entry o2) {
			try {
				for (OrderBy ob : orderBy) {
					Comparable value1 = ob.get(o1.getValue());   
					Comparable value2 = ob.get(o2.getValue());   
					if (value1 == null && value2 == null)
						continue;
					if (value1 == null)
						return -ob.getModifier(); 
					if (value2 == null)
						return ob.getModifier();
					int result = value1.compareTo(value2);
					if (result == 0)
						continue;
					return result * ob.getModifier();
				}
				return 0;
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}
	}
	
	public HazelcastInMemoryStorage() {
	}

	@Override
	public void shutdown() {
		log.info("Forcing Hazelcast shutdown in a Web app");
		com.hazelcast.core.Hazelcast.shutdownAll();

		log.info("Forcing Hazelcast client shutdown in a Web app");
		com.hazelcast.client.HazelcastClient.shutdownAll();
	}

	@Required
	public void setConfig(Object config) {
		if (!(config instanceof ClientConfig) && !(config instanceof Config))
			throw new RuntimeException("Invalid Hazelcast config: ClientConfig or Config is required");
		this.config = config;
	}

	@SuppressWarnings("unused")
	public void setMaxPartitionSize(long maxPartitionSize) {
		this.maxPartitionSize = maxPartitionSize;
	}

	@SuppressWarnings("unused")
	public void getMemberProperty(String unitName, Long id, String property, ExecutionCallback<String> callback) {
		hz.getExecutorService("default").submitToKeyOwner(new MemberPropertyTask(property), new Key(id, unitName, maxPartitionSize), callback);
	}

	@Override
	public <T> void registerMessageCallback(String topicName, MessageCallback<T> callback) {
		ITopic<T> events = hz.getTopic(topicName);
		events.addMessageListener(new MessageListener<T>() {
			@Override
			public void onMessage(Message<T> message) {
				callback.process(message.getMessageObject());
			}
		});
	}

	@Override
	public <T> void broadcastMessage(String topicName, T message) {
		ITopic<T> events = hz.getTopic(topicName);
		events.publish(message);
	}

	@Override
	public void start() {
		hz = config instanceof ClientConfig ? HazelcastClient.newHazelcastClient((ClientConfig)config) :
			Hazelcast.newHazelcastInstance((Config)config);
	}

	@Override
	public void createMap(Class<?> cls, String unitName, Map<String, Class<?>> indexFields, List<CompoundIndexDescriptor> compoundIndexes, boolean transientData) {
		IMap<Key, StoredBean> map = hz.getMap(unitName);

		for (String indexField : indexFields.keySet()) {
			boolean ordered = false;
			if (indexField.endsWith("*")) {
				ordered = true;
				indexField = indexField.substring(0, indexField.length() - 1);
			}
			map.addIndex(indexField, ordered);
		}

		// AlexR: Hazelcast currently (v.3.5.1) does not support compound indexes
	}

	@Override
	public void createIdGenerator(String unitName, long value) {
		log.info("Initialized ID generator '" + unitName + "' with " + value);
		hz.getAtomicLong("IDgen_" + unitName).set(value);
	}

	@Override
	public long generateId(String unitName) {
		return hz.getAtomicLong("IDgen_" + unitName).incrementAndGet();
	}

	public static class HzLock implements Lock {
		private java.util.concurrent.locks.Lock lock;
		
		public HzLock(java.util.concurrent.locks.Lock lock) {
			this.lock = lock;
		}

		@Override
		public void unlock() {
			lock.unlock();
		}
	}
	
	@Override
	public Lock lock(String lockName, Long timeout) {
		ILock lock = hz.getLock(lockName);
		try {
			if (lock.tryLock(timeout, TimeUnit.MILLISECONDS))
				return new HzLock(lock);
		} catch (InterruptedException ignored) {}
		return null;
	}

	@Override
	public long getAtomicLong(String name, Integer tenantId) {
		return hz.getAtomicLong(name + "@long" + tenantId).get();
	}

	@Override
	public void setAtomicLong(String name, Integer tenantId, Long value) {
		hz.getAtomicLong(name + "@long" + tenantId).set(value == null ? 0 : value);
	}

	@Override
	public long incrementAtomicLong(String name, Integer tenantId) {
		return hz.getAtomicLong(name + "@long" + tenantId).incrementAndGet();
	}

	@Override
	public long decrementAtomicLong(String name, Integer tenantId) {
		return hz.getAtomicLong(name + "@long" + tenantId).decrementAndGet();
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T> T get(String unitName, Long id) {
		return (T)hz.getMap(unitName).get(new Key(id, unitName, maxPartitionSize));
	}

	@SuppressWarnings("unchecked")
	@Override
	public <T> T get(String unitName, Object key) {
		return (T)hz.getMap(unitName).get(key);
	}

	@Override
	public void save(String unitName, Object key, Object bean) {
		hz.getMap(unitName).put(key, bean);
	}

	@Override
	public void delete(String unitName, Object key) {
		hz.getMap(unitName).delete(key);
	}

	@Override
	public <T> void deleteAll(String unitName, Class<T> cls, Criteria criteria) {
		IMap<Object, T> map = hz.getMap(unitName);
		for (Object key : map.keySet(criteria.convert(new PredicateQueryBuilder(cls))))
			map.delete(key);
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	public static class SearchComparator implements Comparator<Map.Entry>, Serializable {
		@Override
		public int compare(Map.Entry o1, Map.Entry o2) {
			return ((Map.Entry<Key, StoredBean>)o1).getKey().compareTo(((Map.Entry<Key, StoredBean>)o2).getKey());
		}
	}

	@Override
	public <T> List<T> search(String unitName, Class<T> cls, Criteria criteria, List<String> orderBy, Integer limit) {
		Comparator<Map.Entry> orderByComparator = orderBy == null || orderBy.isEmpty() ? new SearchComparator() : new OrderByComparator(ob(cls, orderBy));
		Predicate<Key, StoredBean> filter = criteria == null ? null : criteria.convert(new PredicateQueryBuilder(cls));

		IMap<Key, T> map = hz.getMap(unitName);
		PagingPredicate predicate = criteria == null ? new PagingPredicate(orderByComparator, limit) : new PagingPredicate(filter, orderByComparator, limit);
		predicate.setIterationType(IterationType.VALUE);
		return new ArrayList<T>(map.values(predicate));
	}

	@Override
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public <T> EntityCursor<T> search(String unitName, Class<T> cls, Criteria criteria, List<String> orderBy) {
		Comparator<Map.Entry> orderByComparator = orderBy == null || orderBy.isEmpty() ? new SearchComparator() : new OrderByComparator(ob(cls, orderBy));
		Predicate<Key, StoredBean> filter = criteria == null ? null : criteria.convert(new PredicateQueryBuilder(cls));

		IMap<Key, T> map = hz.getMap(unitName);
		PagingPredicate predicate = criteria == null ? new PagingPredicate(orderByComparator, 50) : new PagingPredicate(filter, orderByComparator, 50);
		predicate.setIterationType(IterationType.VALUE);
		return new ResultIterator<T>(predicate, map);
	}

	private <T> List<OrderBy> ob(Class<T> cls, List<String> orderBy) {
		List<OrderBy> ob = new ArrayList<OrderBy>();
		for (String s : orderBy) {
			String[] ss = s.split(" ");
			int modifier = ss.length > 1 && ss[1].equalsIgnoreCase("desc") ? OrderBy.DESC : OrderBy.ASC;
			try {
				ob.add((OrderBy)cls.getMethod("orderBy" + ss[0].substring(0, 1).toUpperCase() + ss[0].substring(1), new Class<?>[]{int.class}).invoke(null, modifier));
			} catch (NoSuchMethodException e) {
				ob.add(new StoredBean.ReflectionOrderBy<>(ss[0], modifier));
			} catch (Exception e) {
				throw new RuntimeException(cls.getSimpleName() + " OrderBy '" + s + "' parsing error: " + e);
			}
		}

		return ob;
	}

	public static class ResultIterator<T> implements EntityCursor<T> {
		private PagingPredicate predicate;
		private IMap<Key, T> map;
		private List<T> values;

		public ResultIterator(PagingPredicate predicate, IMap<Key, T> map) {
			this.predicate = predicate;
			this.map = map;
			values = new ArrayList<T>(map.values(predicate));
		}

		public List<T> next() {
			List<T> result = values;
			predicate.nextPage();
			values = new ArrayList<T>(map.values(predicate));
			return result;
		}

		@Override
		public Iterator<T> iterator() {
			return new Iterator<T>() {
				int idx = 0;

				@Override
				public boolean hasNext() {
					if (values.isEmpty())
						return false;

					if (idx >= values.size()) {
						predicate.nextPage();
						values = new ArrayList<T>(map.values(predicate));

						if (values.isEmpty())
							return false;

						idx = 0;
					}

					return true;
				}

				@Override
				public T next() {
					return values.get(idx++);
				}
			};
		}

		@Override
		public void close() {
			map = null;
			predicate = null;
			values = null;
		}
	}

	@Override
	public long count(String unitName, Class<?> cls, Criteria criteria) {
		if (criteria == null)
			return hz.getMap(unitName).size();
		
		IMap<Key, StoredBean> map = hz.getMap(unitName);
		PagingPredicate predicate = new PagingPredicate(criteria.convert(new PredicateQueryBuilder(cls)), 50);
		predicate.setIterationType(IterationType.KEY);
		
		long result = 0;
		for (Set<Key> keys = map.keySet(predicate); !keys.isEmpty(); keys = map.keySet(predicate)) {
			result += keys.size();
			predicate.nextPage();
		}
		return result;
	}

	@Override
	public <T> List<T>  getAll(String unitName, Class<T> cls, Criteria criteria) {
		IMap<?, T> map = hz.getMap(unitName);
		return new ArrayList<T>(criteria == null ? map.values() : map.values(criteria.convert(new PredicateQueryBuilder(cls))));
	}

	public static class HzEntryProcessor extends AbstractEntryProcessor<Key, Object> {
		private InPlaceUpdate<?> u;

		@SuppressWarnings("unused")
		public HzEntryProcessor() {
		}

		public HzEntryProcessor(InPlaceUpdate<?> u) {
			this.u = u;
		}

		@Override
		public Object process(Map.Entry<Key, Object> entry) {
			Object value = entry.getValue();
			u.eval(value);
			entry.setValue(value);
			return value;
		}
	}

	@Override
	public List<EntityDescriptor> save(List<StoredBean> inserts, List<StoredBean> updates, List<Delete> deletes, List<InPlaceUpdate<?>> inPlaceUpdates,
									   boolean serviceData) throws DataStorageException {
		List<StoredBean> inPlaceEntities = new ArrayList<>();
		if (!inPlaceUpdates.isEmpty()) {
			int lastUpdate = inPlaceUpdates.get(inPlaceUpdates.size() - 1).getOperationOrderNo();
			if ((!inserts.isEmpty() && inserts.get(0).getOperationOrderNo() < lastUpdate) ||
				(!updates.isEmpty() && updates.get(0).getOperationOrderNo() < lastUpdate) ||
				(!deletes.isEmpty() && deletes.get(0).getOperationOrderNo() < lastUpdate))
				throw new RuntimeException("Hazelcast implies that all non-transactional in-place updates happen before conventional operations");

			try {
				for (final InPlaceUpdate<?> u : inPlaceUpdates) {
					IMap<Key, Object> map = hz.getMap(u.getUnitName());
					Key key = new Key(u.getId(), u.getUnitName(), maxPartitionSize);
					inPlaceEntities.add((StoredBean)map.get(key));
					map.executeOnKey(key, new HzEntryProcessor(u));
				}
			} catch (Throwable e) {
				for (int i = 0, n = inPlaceEntities.size(); i < n; i++) {
					InPlaceUpdate<?> u = inPlaceUpdates.get(i);
					save(u.getUnitName(), u.getId(), inPlaceEntities.get(i));
				}
				throw new RuntimeException(e);
			}
		}

		TransactionContext context = hz.newTransactionContext(
			new TransactionOptions().setTransactionType(TransactionType.TWO_PHASE).setTimeout(30, TimeUnit.SECONDS));
		
		context.beginTransaction();		
		try {
			Map<String, TransactionalMap<Key, StoredBean>> maps = new HashMap<String, TransactionalMap<Key, StoredBean>>();
			for (StoredBean bean : inserts) {
				String unitName = bean.unitName();
				if (!maps.containsKey(unitName)) {
					TransactionalMap<Key, StoredBean> map = context.getMap(unitName); 
					maps.put(unitName, map);
				}
			}
			for (StoredBean bean : updates) {
				String unitName = bean.unitName();
				if (!maps.containsKey(unitName)) {
					TransactionalMap<Key, StoredBean> map = context.getMap(unitName); 
					maps.put(unitName, map);
				}
			}
			for (Delete delete : deletes) {
				String unitName = delete.getUnitName();
				if (!maps.containsKey(unitName)) {
					TransactionalMap<Key, StoredBean> map = context.getMap(unitName); 
					maps.put(unitName, map);
				}
			}
			
			for (StoredBean bean : inserts) {
				TransactionalMap<Key, StoredBean> map = maps.get(bean.unitName());
				Key key = new Key(bean.getId(), bean.unitName(), maxPartitionSize);
				if (map.containsKey(key))
					throw new RuntimeException("Bad ID generator");
				map.set(key, bean);
			}

			for (StoredBean bean : updates) {
				TransactionalMap<Key, StoredBean> map = maps.get(bean.unitName());
				Key key = new Key(bean.getId(), bean.unitName(), maxPartitionSize);
				if (map.containsKey(key))
					map.set(key, bean);
				else
					throw new DataStorageException("stale");
			}

			List<EntityDescriptor> result = new ArrayList<>();
			for (Delete delete : deletes) {
				if (delete.getCriteria() == null)
					throw new RuntimeException("Empty Delete filter");
				
				TransactionalMap<Key, StoredBean> map = maps.get(delete.getUnitName());
				if (delete.getId() != null) {
					result.add(new EntityDescriptor(delete.getEntityClass(), delete.getId(), delete.getUnitName()));
					map.delete(new Key(delete.getId(), delete.getUnitName(), maxPartitionSize));
				} else
					for (Key key : map.keySet(delete.getCriteria().convert(new PredicateQueryBuilder(delete.getEntityClass())))) {
						result.add(new EntityDescriptor(delete.getEntityClass(), key.id, delete.getUnitName()));
						map.delete(key);
					}
			}

			if (writeThrough != null && !serviceData) {
				Gson gson = RawRecord.createGson();
				Connection conn = writeThrough.open();
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
			
			context.commitTransaction();
			return result;
		} catch (DataStorageException e) {
		    context.rollbackTransaction();
			for (int i = 0, n = inPlaceEntities.size(); i < n; i++) {
				InPlaceUpdate<?> u = inPlaceUpdates.get(i);
				save(u.getUnitName(), u.getId(), inPlaceEntities.get(i));
			}
			throw e;
		} catch (Throwable e) {
		    context.rollbackTransaction();
			for (int i = 0, n = inPlaceEntities.size(); i < n; i++) {
				InPlaceUpdate<?> u = inPlaceUpdates.get(i);
				save(u.getUnitName(), u.getId(), inPlaceEntities.get(i));
			}
		    throw new RuntimeException(e);
		}
	}

	@Override
	public PersistenceLogEntry createPersistenceLogEntry() {
		return new PortablePersistenceLogEntry();
	}

	@Override
	public InMemoryStorageLoader loader() {
		return new InMemoryStorageLoader() {
			private Map<String, IMap<Key, StoredBean>> maps = new HashMap<>();

			@Override
			public void store(List<StoredBean> inserts) {
				for (StoredBean entity : inserts) {
					String unitName = entity.unitName();
					IMap<Key, StoredBean> map = maps.get(unitName);
					if (map == null) {
						map = hz.getMap(unitName);
						maps.put(unitName, map);
					}
					map.set(new Key(entity.getId(), unitName, maxPartitionSize), entity);
				}
			}

			@Override
			public void close() {
				maps.clear();
			}
		};
	}
}
