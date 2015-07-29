# Px100 Data Developer Guide
You are welcome to write new storage and persistence providers for Px100 Data. 

Below are provider SPIs for your convenience.

## In-Memory Storage Provider
```java
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
```

## Traditional (NoSQL) Storage Provider
```java
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
```

## In-Memory Persistence Provider
```java
public interface PersistenceProvider {
	/**
	 * Only relevant for NoSQL (MapDB, etc.) providers. The Connection accumulates the list of open DBs (files) to close at the end
	 * SQL providers rely on JDBC DataSource (pooled or always open)  
	 */
	interface Connection {
		void close();
	}
	
	/**
	 * Opens the DB connection - only relevant for NoSQL databases that should close all opened DB files they opened after the series of transactionalSaves()  
	 */
	Connection open();
	
	/**
	 * Normal persistence. Saves the time operation was performed in a special data structure (DB map or SQL table)<br>
	 * NoSQL: creates map if it does not exist (should be automatic).
	 * 
	 * @param conn - [NoSQL] connection
	 * @param insertsOrUpdates list of records to save
	 * @param deletes list of record IDs to delete
	 * @param timestamp current time
	 * @throws PersistenceProviderException
	 */
	void transactionalSave(Connection conn, Collection<RawRecord> insertsOrUpdates, Collection<EntityDescriptor> deletes, Long timestamp) throws PersistenceProviderException;
	
	/**
	 * Restoring database from the dump files.
	 * NoSQL: creates maps if it does not exist (should be automatic).
	 * 
	 * @param conn - [NoSQL] connection
	 * @param inserts - a list of reasonable size. It is the caller's responsibility to break long lists into smaller chunks.
	 */
	void transactionalSave(Connection conn, List<RawRecord> inserts) throws PersistenceProviderException;  
	
	interface LoadCallback {
		void process(RawRecord record);
	}
	
	/**
	 * Loads the entire named storage structure (SQL table or MapDB file).
	 * SQL providers should use JDBC Statement.setFetchSize() feature to read long tables.
	 * NoSQL providers (MapDB) apparently handle it automatically or with minimal tuning.
	 *
	 * @param storageName storage name
	 * @param callback how to load that storage record
	 * @throws PersistenceProviderException
	 */
	void loadByStorage(String storageName, LoadCallback callback) throws PersistenceProviderException;
	
	/**
	 * Returns max Ids per ID generator.
	 */
	Map<String, Long> loadMaxIds() throws PersistenceProviderException;
	
	/**
	 * The list of all storage structures (SQL table or MapDB file)
	 */
	List<String> storage();
	
	/**
	 * Returns unit's storage.
	 *
	 * @param unitName unit name
	 * @return storage name
	 */
	String unitStorage(String unitName);
	
	/**
	 * Used for cleaning the database before restoring it from dump files.<br>
	 * Reinitializes the database e.g. deleting/creating the schema in MySQL or all files in the specified directory in H2 or NoSQL (MapDB).<br>
	 * Recreates storage structures.<br>
	 * Recreates the table for last save time (SQL only).<br>
	 * <br>
	 * SQL: simple DDL w/o indices. The table should have its own (auto-incremented or sequenced) PK instead of using entities IDs.<br>
	 */
	void init();
	
    /**
     * Manually compacts a NoSQL database like MapDB. Quartz-scheduled by the server. Ignored by SQL providers.
     */
    void compact();
    
    /**
     * Time of last save.<br>
     * SQL providers keep it in a separate table.
     * NoSQL providers keep it in a one-entry map located in the most frequently used storage structure
     */
    Long lastSaved() throws PersistenceProviderException;
}
```
