package com.px100systems.platform.benchmark;

import com.px100systems.platform.benchmark.entity.TestEntity;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.Ignition;
import org.apache.ignite.cache.CacheAtomicityMode;
import org.apache.ignite.cache.CacheMode;
import org.apache.ignite.cache.CachePeekMode;
import org.apache.ignite.cache.CacheTypeMetadata;
import org.apache.ignite.cache.CacheWriteSynchronizationMode;
import org.apache.ignite.cache.query.QueryCursor;
import org.apache.ignite.cache.query.SqlQuery;
import org.apache.ignite.configuration.AtomicConfiguration;
import org.apache.ignite.configuration.CacheConfiguration;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.configuration.TransactionConfiguration;
import org.apache.ignite.marshaller.optimized.OptimizedMarshaller;
import org.apache.ignite.transactions.Transaction;
import org.apache.ignite.transactions.TransactionConcurrency;
import org.apache.ignite.transactions.TransactionIsolation;
import javax.cache.Cache;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;

public class IgniteTest {
	private static final String ENTITY_NAME = "TestEntity";

	public static void main(String[] args) {
		if (args.length < 1) {
			System.err.println("Usage: java -cp \"grid-benchmark.jar:lib/*\" -Xms512m -Xmx3000m -Xss4m com.px100systems.platform.benchmark.IgniteTest <number>");
			return;
		}
		long numberOfEntities = Long.parseLong(args[0]);

		// AlexR: this programmatic config mimics the Spring XML I use in real projects
		IgniteConfiguration config = new IgniteConfiguration();
		config.setGridName("Benchmark");

		OptimizedMarshaller marshaller = new OptimizedMarshaller();
		marshaller.setRequireSerializable(false);
		config.setMarshaller(marshaller);

		config.setIncludeEventTypes(org.apache.ignite.events.EventType.EVT_NODE_JOINED);

		AtomicConfiguration atomicConfiguration = new AtomicConfiguration();
		atomicConfiguration.setBackups(1);
		atomicConfiguration.setCacheMode(CacheMode.PARTITIONED);
		config.setAtomicConfiguration(atomicConfiguration);

		//config.setPublicThreadPoolSize(15);
		//config.setManagementThreadPoolSize(10);
		//config.setSystemThreadPoolSize(10);
		//config.setUtilityCachePoolSize(10);
		//config.setMarshallerCachePoolSize(10);

		config.setMetricsLogFrequency(0);
		config.setMetricsUpdateFrequency(15000);

		config.setPeerClassLoadingEnabled(false);
		//config.setPeerClassLoadingThreadPoolSize(10);

		TransactionConfiguration transactionConfiguration = new TransactionConfiguration();
		transactionConfiguration.setDefaultTxConcurrency(TransactionConcurrency.PESSIMISTIC);
		transactionConfiguration.setDefaultTxIsolation(TransactionIsolation.READ_COMMITTED);
		transactionConfiguration.setDefaultTxTimeout(30000);
		config.setTransactionConfiguration(transactionConfiguration);

		Ignite ignite = Ignition.start(config);

		CacheConfiguration<Long, TestEntity> cacheConfig = new CacheConfiguration<Long, TestEntity>();
		cacheConfig.setName(ENTITY_NAME).
			setCacheMode(CacheMode.PARTITIONED).
			setBackups(config.getAtomicConfiguration().getBackups()).
			setAtomicityMode(CacheAtomicityMode.TRANSACTIONAL).
			setWriteBehindEnabled(false).
			setWriteSynchronizationMode(CacheWriteSynchronizationMode.FULL_SYNC).
			setReadThrough(false);

		CacheTypeMetadata type = new CacheTypeMetadata();
		type.setValueType(TestEntity.class.getName());

		type.getQueryFields().put("id", Long.class);
		type.getAscendingFields().put("id", Long.class);

		type.getQueryFields().put("idSort", String.class);
		type.getAscendingFields().put("idSort", String.class);

		type.getQueryFields().put("createdAt", Date.class);
		type.getAscendingFields().put("createdAt", Date.class);

		type.getQueryFields().put("createdAtSort", String.class);
		type.getAscendingFields().put("createdAtSort", String.class);

		type.getQueryFields().put("modifiedAt", Date.class);
		type.getAscendingFields().put("modifiedAt", Date.class);

		type.getQueryFields().put("modifiedAtSort", String.class);
		type.getAscendingFields().put("modifiedAtSort", String.class);

		type.getQueryFields().put("textField", String.class);
		type.getAscendingFields().put("textField", String.class);

		cacheConfig.setTypeMetadata(Collections.singletonList(type));
		ignite.createCache(cacheConfig);

		Runtime runtime = Runtime.getRuntime();
		long memory = runtime.totalMemory() - runtime.freeMemory();
		System.out.println("Starting - used heap: " + memory + " bytes");

		long start = new Date().getTime();

		long id = 1;
		TestEntity first = null;
		TestEntity last = null;
		for (int i = 0, n = (int)numberOfEntities / 1000; i < n; i++) {
			Transaction tx = ignite.transactions().txStart();
			IgniteCache<Long, TestEntity> cache = ignite.cache(ENTITY_NAME);
			assert cache != null;

			for (int j = 0; j < 1000; j++) {
				TestEntity entity = new TestEntity(id++);
				if (first == null)
					first = entity;
				last = entity;
				cache.put(entity.getId(), entity);
			}

			tx.commit();

			if (i == 0)
				System.out.print("Inserting " + numberOfEntities + " records: ");
			System.out.print(".");
		}

		assert first != null;

		IgniteCache<Long, TestEntity> cache = ignite.cache(ENTITY_NAME);
		assert cache != null;
		long end = new Date().getTime();
		long memoryAfterInserts = runtime.totalMemory() - runtime.freeMemory();
		System.out.println("\nInserted all records - used heap: " + memoryAfterInserts + " bytes");
		System.out.println("Cache: " + cache.size(CachePeekMode.PRIMARY) + " entries, heap size: " + (memoryAfterInserts - memory) +
			" bytes, inserts took " + (end - start) + " ms");
		start = end;

		cache = ignite.cache(ENTITY_NAME);
		assert cache != null;
		SqlQuery<Object, TestEntity> query = new SqlQuery<>(TestEntity.class,
			"FROM TestEntity WHERE textField LIKE '%Jane%' AND idSort > '" + first.getIdSort() + "' ORDER BY idSort LIMIT 100");
		query.setPageSize(100);
		QueryCursor<Cache.Entry<Object, TestEntity>> cursor = cache.query(query);
		int count = 0;
		try {
			for (Iterator<Cache.Entry<Object, TestEntity>> i = cursor.iterator(); i.hasNext(); ) {
				i.next();
				count++;
			}
		} finally {
			cursor.close();
		}
		end = new Date().getTime();
		System.out.println("Query 1 count: " + count + ", time: "+ (end - start) + " ms, heap size: " + (runtime.totalMemory() - runtime.freeMemory()) + " bytes");
		start = end;

		cache = ignite.cache(ENTITY_NAME);
		assert cache != null;
		query = new SqlQuery<>(TestEntity.class,
			"FROM TestEntity WHERE textField LIKE '%Jane%' AND idSort <= '" + last.getIdSort() + "' ORDER BY idSort DESC LIMIT 100");
		query.setPageSize(100);
		cursor = cache.query(query);
		count = 0;
		try {
			for (Iterator<Cache.Entry<Object, TestEntity>> i = cursor.iterator(); i.hasNext(); ) {
				i.next();
				count++;
			}
		} finally {
			cursor.close();
		}
		end = new Date().getTime();
		System.out.println("Query 2 count: " + count + ", time: "+ (end - start) + " ms, heap size: " + (runtime.totalMemory() - runtime.freeMemory()) + " bytes");
		start = end;

		cache = ignite.cache(ENTITY_NAME);
		assert cache != null;
		query = new SqlQuery<>(TestEntity.class,
			"FROM TestEntity WHERE textField LIKE '%Richards%' AND idSort > '" + first.getIdSort() + "' ORDER BY idSort ASC LIMIT 100");
		query.setPageSize(100);
		cursor = cache.query(query);
		count = 0;
		try {
			for (Iterator<Cache.Entry<Object, TestEntity>> i = cursor.iterator(); i.hasNext(); ) {
				i.next();
				count++;
			}
		} finally {
			cursor.close();
		}
		end = new Date().getTime();
		System.out.println("Query 3 count: " + count + ", time: "+ (end - start) + " ms, heap size: " + (runtime.totalMemory() - runtime.freeMemory()) + " bytes");
		start = end;

		cache = ignite.cache(ENTITY_NAME);
		assert cache != null;
		query = new SqlQuery<>(TestEntity.class,
			"FROM TestEntity WHERE textField LIKE '%Richards%' AND idSort <= '" + last.getIdSort() + "' ORDER BY idSort DESC LIMIT 100");
		query.setPageSize(100);
		cursor = cache.query(query);
		count = 0;
		try {
			for (Iterator<Cache.Entry<Object, TestEntity>> i = cursor.iterator(); i.hasNext(); ) {
				i.next();
				count++;
			}
		} finally {
			cursor.close();
		}
		end = new Date().getTime();
		System.out.println("Query 4 count: " + count + ", time: "+ (end - start) + " ms, heap size: " + (runtime.totalMemory() - runtime.freeMemory()) + " bytes");

		Ignition.stopAll(false);
	}
}
