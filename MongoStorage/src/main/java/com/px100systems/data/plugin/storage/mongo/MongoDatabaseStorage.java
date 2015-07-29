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
package com.px100systems.data.plugin.storage.mongo;

import com.mongodb.MongoClient;
import com.mongodb.MongoClientURI;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.DeleteManyModel;
import com.mongodb.client.model.DeleteOneModel;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexModel;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.InsertOneModel;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.ReplaceOneModel;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.WriteModel;
import com.px100systems.data.core.Criteria;
import com.px100systems.data.core.DataStorageException;
import com.px100systems.data.core.Delete;
import com.px100systems.data.core.Entity;
import com.px100systems.data.core.StoredBean;
import com.px100systems.data.plugin.storage.EntityCursor;
import com.px100systems.data.plugin.storage.TraditionalStorageProvider;
import com.px100systems.util.serialization.SerializationDefinition;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Required;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * MongoDB database storage provider. See TraditionalStorageProvider for details.<br>
 * <br>
 * <b>Configuration</b><br>
 * <ul>
 *   <li>connectionUrl - expected to specify all Mongo connection parameters: host, port, database ("schema"), user/password,
 *    pool size, and other flags if needed
 * </ul>
 *
 * @version 0.3 <br>Copyright (c) 2015 Px100 Systems. All Rights Reserved.<br>
 * @author Alex Rogachevsky
 */
public class MongoDatabaseStorage implements TraditionalStorageProvider, InitializingBean, DisposableBean {
	private String connectionUrl;
	private MongoClient mongoClient;
	private String databaseName;

	@Required
	public void setConnectionUrl(String connectionUrl) {
		this.connectionUrl = connectionUrl;

		databaseName = connectionUrl.substring(connectionUrl.lastIndexOf("/") + 1);
		if (databaseName.contains("@"))
			throw new RuntimeException("Malformed Mongo URL: cannot determine the database");
		int pos = databaseName.indexOf("?");
		if (pos != -1)
			databaseName = databaseName.substring(0, pos);
	}

	@Override
	public void afterPropertiesSet() {
		mongoClient = new MongoClient(new MongoClientURI(connectionUrl));
	}

	@Override
	public void destroy() throws Exception {
		mongoClient.close();
	}

	private static String indexName(String fieldName) {
		return "px100_" + fieldName + "_idx";
	}

	public Map<String, List<String>> getSchema(boolean reset) {
		MongoDatabase db = mongoClient.getDatabase(databaseName);

		Map<String, List<String>> result = new HashMap<>();

		MongoCursor<String> cursor = db.listCollectionNames().iterator();
		try {
			while (cursor.hasNext()) {
				String name = cursor.next();
				if (!name.startsWith("system."))
					if (reset)
						db.getCollection(name).drop();
					else
						result.put(name, new ArrayList<String>());
			}
		} finally {
			cursor.close();
		}

		for (Map.Entry<String, List<String>> e : result.entrySet()) {
			MongoCursor<Document> idxCursor = db.getCollection(e.getKey()).listIndexes().iterator();
			try {
				while (idxCursor.hasNext()) {
					String name = idxCursor.next().get("key", Document.class).keySet().iterator().next();
					if (!name.startsWith("_"))
						e.getValue().add(name);
				}
			} finally {
				idxCursor.close();
			}
		}

		return result;
	}

	public void createEntity(String unitName, Collection<String> indexedFields) {
		MongoDatabase db = mongoClient.getDatabase(databaseName);
		db.createCollection(unitName);

		List<IndexModel> indexes = new ArrayList<>();
		for (String idx : indexedFields)
			indexes.add(new IndexModel(new Document(idx, 1), new IndexOptions().name(indexName(idx)).background(true)));

		db.getCollection(unitName).createIndexes(indexes);
	}

	public void dropEntity(String unitName) {
		if (unitName.startsWith("system.")) // just a precation, shouldn't happen
			return;

		MongoDatabase db = mongoClient.getDatabase(databaseName);
		db.getCollection(unitName).drop();
	}

	public void updateEntity(String unitName, SerializationDefinition def) {
	}

	public void addIndexes(String unitName, List<String> newIndexes) {
		MongoDatabase db = mongoClient.getDatabase(databaseName);

		List<IndexModel> indexes = new ArrayList<>();
		for (String idx : newIndexes)
			indexes.add(new IndexModel(new Document(idx, 1), new IndexOptions().name(indexName(idx)).background(true)));

		db.getCollection(unitName).createIndexes(indexes);
	}

	public void dropIndex(String unitName, String obsoleteIndex) {
		if (obsoleteIndex.startsWith("_")) // just a precation, shouldn't happen
			return;

		MongoDatabase db = mongoClient.getDatabase(databaseName);
		db.getCollection(unitName).dropIndex(indexName(obsoleteIndex));
	}

	public Long getMaxId(String unitName) {
		MongoDatabase db = mongoClient.getDatabase(databaseName);
		Document result = db.getCollection(unitName).find().sort(Sorts.descending("id")).projection(Projections.include("id")).limit(1).first();
		if (result == null)
			return null;

		return result.getLong("id");
	}

	@SuppressWarnings("unchecked")
	public <T> T get(String unitName, Class<T> cls, Long id) {
		MongoDatabase db = mongoClient.getDatabase(databaseName);
		Document doc = db.getCollection(unitName).find(Filters.eq("id", id)).limit(1).first();
		if (doc == null)
			return null;

		SerializationDefinition def = SerializationDefinition.get(cls);
		if (def == null)
			throw new RuntimeException("Cannot find SerializedDefinition for " + cls.getSimpleName());

		T result = (T)def.newInstance();
		def.read(new DocumentReader(doc), result);
		return result;
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T> List<T> search(String unitName, Class<T> cls, Criteria criteria, List<String> orderBy, Integer limit) {
		SerializationDefinition def = SerializationDefinition.get(cls);
		if (def == null)
			throw new RuntimeException("Cannot find SerializationDefinition for " + cls.getSimpleName());

		MongoDatabase db = mongoClient.getDatabase(databaseName);

		FindIterable<Document> query = criteria == null ? db.getCollection(unitName).find() :
			db.getCollection(unitName).find(criteria.convert(new FilterQueryBuilder()));

		if (orderBy != null && !orderBy.isEmpty())
			if (orderBy.size() == 1)
				query = query.sort(orderBy(orderBy.get(0)));
			else {
				List<Bson> ob = new ArrayList<>();
				for (String s : orderBy)
					ob.add(orderBy(s));
				query = query.sort(Sorts.orderBy(ob));
			}

		List<T> result = new ArrayList<>();
		MongoCursor<Document> cursor = query.limit(limit).iterator();
		try {
			while (cursor.hasNext()) {
				T item = (T)def.newInstance();
				def.read(new DocumentReader(cursor.next()), item);
				result.add(item);
			}
		} finally {
			cursor.close();
		}

		return result;
	}

	@Override
	public <T> EntityCursor<T> search(String unitName, Class<T> cls, Criteria criteria, List<String> orderBy) {
		SerializationDefinition def = SerializationDefinition.get(cls);
		if (def == null)
			throw new RuntimeException("Cannot find SerializationDefinition for " + cls.getSimpleName());

		MongoDatabase db = mongoClient.getDatabase(databaseName);

		FindIterable<Document> query = criteria == null ? db.getCollection(unitName).find() :
			db.getCollection(unitName).find(criteria.convert(new FilterQueryBuilder()));

		if (orderBy != null && !orderBy.isEmpty())
			if (orderBy.size() == 1)
				query = query.sort(orderBy(orderBy.get(0)));
			else {
				List<Bson> ob = new ArrayList<>();
				for (String s : orderBy)
					ob.add(orderBy(s));
				query = query.sort(Sorts.orderBy(ob));
			}

		return new ResultIterator<T>(query.iterator(), def);
	}

	private Bson orderBy(String s) {
		boolean descending = false;
		if (s.toUpperCase().endsWith(" ASC")) {
			s = s.substring(0, s.length() - " ASC".length()).trim();
			descending = false;
		} else if (s.toUpperCase().endsWith(" DESC")) {
			s = s.substring(0, s.length() - " DESC".length()).trim();
			descending = true;
		} else
			s = s.trim();

		return descending ? Sorts.descending(s) : Sorts.ascending(s);
	}

	public static class ResultIterator<T> implements EntityCursor<T> {
		private MongoCursor<Document> cursor;
		private SerializationDefinition def;

		public ResultIterator(MongoCursor<Document> cursor, SerializationDefinition def) {
			this.cursor = cursor;
			this.def = def;
		}

		@Override
		public Iterator<T> iterator() {
			return new Iterator<T>() {
				@Override
				public boolean hasNext() {
					return cursor.hasNext();
				}

				@Override
				@SuppressWarnings("unchecked")
				public T next() {
					T item = (T)def.newInstance();
					def.read(new DocumentReader(cursor.next()), item);
					return item;
				}
			};
		}

		@Override
		public void close() {
			cursor.close();
		}
	}

	public long count(String unitName, Class<?> cls, Criteria criteria) {
		MongoDatabase db = mongoClient.getDatabase(databaseName);
		return criteria == null ? db.getCollection(unitName).count() :
			db.getCollection(unitName).count(criteria.convert(new FilterQueryBuilder()));
	}

	public void save(List<StoredBean> inserts, List<StoredBean> updates, List<Delete> deletes) throws DataStorageException {
		List<Delete> preInserted = new ArrayList<>();
		List<StoredBean> preUpdated = new ArrayList<>();
		List<StoredBean> preDeleted = new ArrayList<>();

		boolean needsRollback = false;
		for (Delete d : deletes)
			if (d.getId() == null) {
				needsRollback = true;
				break;
			}
		if (!needsRollback)
			needsRollback = (inserts.size() + updates.size() + deletes.size()) > 1;

		if (needsRollback) {
			for (StoredBean e : updates)
				preUpdated.add(get(Entity.unitFromClass(e.getClass(), ((Entity) e).getTenantId()), e.getClass(), e.getId()));

			for (Delete d : deletes)
				if (d.getId() == null) {
					EntityCursor<Entity> cursor = search(d.getUnitName(), d.getEntityClass(), d.getCriteria(), null);
					try {
						for (Iterator<Entity> i = cursor.iterator(); i.hasNext(); )
							preDeleted.add(i.next());
					} finally {
						cursor.close();
					}
				} else
					preDeleted.add(get(d.getUnitName(), d.getEntityClass(), d.getId()));

			for (StoredBean entity : inserts)
				preInserted.add(new Delete(0, (Entity)entity));
		}

		MongoDatabase db = mongoClient.getDatabase(databaseName);
		try {
			batchSave(db, inserts, updates, deletes);
		} catch (Exception e) {
			if (needsRollback) {
				try {
					batchSave(db, preDeleted, preUpdated, preInserted);
				} catch (Exception ignored) {}
			}

			throw new DataStorageException(e);
		}
	}

	private void batchSave(MongoDatabase db, List<StoredBean> inserts, List<StoredBean> updates, List<Delete> deletes) {
		Map<String, List<WriteModel<Document>>> batches = new HashMap<>();

		for (StoredBean bean : inserts) {
			SerializationDefinition def = SerializationDefinition.get(bean.getClass());
			if (def == null)
				throw new RuntimeException("Cannot find SerializationDefinition for " + bean.getClass().getSimpleName());

			DocumentWriter doc = new DocumentWriter();
			def.write(doc, bean);

			String unitName = bean.unitName();
			List<WriteModel<Document>> batch = batches.get(unitName);
			if (batch == null) {
				batch = new ArrayList<>();
				batches.put(unitName, batch);
			}

			batch.add(new InsertOneModel<Document>(doc.getDocument()));
		}

		for (StoredBean bean : updates) {
			SerializationDefinition def = SerializationDefinition.get(bean.getClass());
			if (def == null)
				throw new RuntimeException("Cannot find SerializationDefinition for " + bean.getClass().getSimpleName());

			DocumentWriter doc = new DocumentWriter();
			def.write(doc, bean);

			String unitName = bean.unitName();
			List<WriteModel<Document>> batch = batches.get(unitName);
			if (batch == null) {
				batch = new ArrayList<>();
				batches.put(unitName, batch);
			}

			batch.add(new ReplaceOneModel<Document>(Filters.eq("id", bean.getId()), doc.getDocument()));
		}

		for (Delete delete : deletes) {
			String unitName = delete.getUnitName();
			List<WriteModel<Document>> batch = batches.get(unitName);
			if (batch == null) {
				batch = new ArrayList<>();
				batches.put(unitName, batch);
			}

			batch.add(delete.getId() == null ? new DeleteManyModel<Document>(delete.getCriteria().convert(new FilterQueryBuilder())) :
				new DeleteOneModel<Document>(Filters.eq("id", delete.getId())));
		}

		for (Map.Entry<String, List<WriteModel<Document>>> e : batches.entrySet())
			db.getCollection(e.getKey()).bulkWrite(e.getValue());
	}
}
