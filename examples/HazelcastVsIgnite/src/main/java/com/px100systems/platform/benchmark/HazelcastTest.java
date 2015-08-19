package com.px100systems.platform.benchmark;

import com.hazelcast.config.Config;
import com.hazelcast.config.GroupConfig;
import com.hazelcast.config.MapConfig;
import com.hazelcast.config.SerializationConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IMap;
import com.hazelcast.core.TransactionalMap;
import com.hazelcast.query.PagingPredicate;
import com.hazelcast.query.Predicates;
import com.hazelcast.transaction.TransactionContext;
import com.hazelcast.transaction.TransactionOptions;
import com.hazelcast.util.IterationType;
import com.px100systems.platform.benchmark.entity.TestEntity;
import java.util.Date;
import java.util.HashMap;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

public class HazelcastTest {
	private static final String ENTITY_NAME = "TestEntity";

	public static void main(String[] args) {
		if (args.length < 1) {
			System.err.println("Usage: java -cp \"grid-benchmark.jar:lib/*\" -Xms512m -Xmx3000m -Xss4m com.px100systems.platform.benchmark.HazelcastTest <number>");
			return;
		}
		long numberOfEntities = Long.parseLong(args[0]);

		// AlexR: this programmatic config mimics the Spring XML I use in real projects
		Config config = new Config();
		config.setInstanceName("Benchmark");

		GroupConfig groupConfig = new GroupConfig();
		groupConfig.setName("test");
		groupConfig.setPassword("hazelcast");

		SerializationConfig serializationConfig = new SerializationConfig();
		serializationConfig.setPortableFactories(new HashMap<>());
		serializationConfig.getPortableFactories().put(1, id -> id == 1 ? new TestEntity() : null);
		config.setSerializationConfig(serializationConfig);

		Properties props = new Properties();
		props.put("hazelcast.health.monitoring.level", "OFF");
		//props.put("hazelcast.io.thread.count", "3");
		//props.put("hazelcast.backpressure.enabled", "true");
		//props.put("hazelcast.backpressure.syncwindow", "100");
		//props.put("hazelcast.icmp.enabled", "true");
		config.setProperties(props);

		//ExecutorConfig executorConfig = new ExecutorConfig();
		//executorConfig.setPoolSize(10);
		//config.setExecutorConfigs(new HashMap<>());
		//config.getExecutorConfigs().put("hz:query", executorConfig);

		MapConfig mapConfig = new MapConfig();
		//mapConfig.setInMemoryFormat(InMemoryFormat.OBJECT);
		mapConfig.setBackupCount(1);
		mapConfig.setAsyncBackupCount(0);
		config.setMapConfigs(new HashMap<>());
		config.getMapConfigs().put("*", mapConfig);

		HazelcastInstance hz = Hazelcast.newHazelcastInstance(config);

		IMap<Long, TestEntity> map = hz.getMap(ENTITY_NAME);
		map.addIndex("id", true);
		map.addIndex("createdAt", true);
		map.addIndex("modifiedAt", true);
		map.addIndex("textField", true);

		Runtime runtime = Runtime.getRuntime();
		long memory = runtime.totalMemory() - runtime.freeMemory();
		System.out.println("Starting - used heap: " + memory + " bytes");

		long start = new Date().getTime();

		long id = 1;
		TestEntity first = null;
		TestEntity last = null;
		for (int i = 0, n = (int)numberOfEntities / 1000; i < n; i++) {
			TransactionContext context = hz.newTransactionContext(new TransactionOptions().
				setTransactionType(TransactionOptions.TransactionType.TWO_PHASE).
				setTimeout(30, TimeUnit.SECONDS));
			context.beginTransaction();
			TransactionalMap<Long, TestEntity> tmap = context.getMap(ENTITY_NAME);

			for (int j = 0; j < 1000; j++) {
				TestEntity entity = new TestEntity(id++);
				if (first == null)
					first = entity;
				last = entity;
				tmap.set(entity.getId(), entity);
			}

			context.commitTransaction();

			if (i == 0)
				System.out.print("Inserting " + numberOfEntities + " records: ");
			System.out.print(".");
		}

		assert first != null;

		map = hz.getMap(ENTITY_NAME);
		long end = new Date().getTime();
		long memoryAfterInserts = runtime.totalMemory() - runtime.freeMemory();
		System.out.println("\nInserted all records - used heap: " + memoryAfterInserts + " bytes");
		System.out.println("Map: " + map.size() + " entries, used heap: " + (memoryAfterInserts - memory) +
			" bytes, inserts took " + (end - start) + " ms");
		start = end;

		map = hz.getMap(ENTITY_NAME);
		PagingPredicate predicate = new PagingPredicate(
			new Predicates.AndPredicate(new Predicates.LikePredicate("textField", "%Jane%"),
				new Predicates.GreaterLessPredicate("id", first.getId(), false, false)),
			(o1, o2) -> ((TestEntity)o1.getValue()).getId().compareTo(((TestEntity)o2.getValue()).getId()),
			100);
		predicate.setIterationType(IterationType.VALUE);
		int count = 0;
		for (TestEntity ignored : map.values(predicate))
			count++;
		end = new Date().getTime();
		System.out.println("Query 1 count: " + count + ", time: "+ (end - start) + " ms, heap size: " + (runtime.totalMemory() - runtime.freeMemory()) + " bytes");
		start = end;

		map = hz.getMap(ENTITY_NAME);
		predicate = new PagingPredicate(
			new Predicates.AndPredicate(new Predicates.LikePredicate("textField", "%Jane%"),
				new Predicates.GreaterLessPredicate("id", last.getId(), true, true)),
			(o1, o2) -> -1 * ((TestEntity)o1.getValue()).getId().compareTo(((TestEntity)o2.getValue()).getId()),
			100);
		predicate.setIterationType(IterationType.VALUE);
		count = 0;
		for (TestEntity ignored : map.values(predicate))
			count++;
		end = new Date().getTime();
		System.out.println("Query 2 count: " + count + ", time: "+ (end - start) + " ms, heap size: " + (runtime.totalMemory() - runtime.freeMemory()) + " bytes");
		start = end;

		map = hz.getMap(ENTITY_NAME);
		predicate = new PagingPredicate(
			new Predicates.AndPredicate(new Predicates.LikePredicate("textField", "%Richards%"),
				new Predicates.GreaterLessPredicate("id", first.getId(), false, false)),
			(o1, o2) -> ((TestEntity)o1.getValue()).getId().compareTo(((TestEntity)o2.getValue()).getId()),
			100);
		predicate.setIterationType(IterationType.VALUE);
		count = 0;
		for (TestEntity ignored : map.values(predicate))
			count++;
		end = new Date().getTime();
		System.out.println("Query 3 count: " + count + ", time: "+ (end - start) + " ms, heap size: " + (runtime.totalMemory() - runtime.freeMemory()) + " bytes");
		start = end;

		map = hz.getMap(ENTITY_NAME);
		predicate = new PagingPredicate(
			new Predicates.AndPredicate(new Predicates.LikePredicate("textField", "%Richards%"),
				new Predicates.GreaterLessPredicate("id", last.getId(), true, true)),
			(o1, o2) -> -1 * ((TestEntity)o1.getValue()).getId().compareTo(((TestEntity)o2.getValue()).getId()),
			100);
		predicate.setIterationType(IterationType.VALUE);
		count = 0;
		for (TestEntity ignored : map.values(predicate))
			count++;
		end = new Date().getTime();
		System.out.println("Query 4 count: " + count + ", time: "+ (end - start) + " ms, heap size: " + (runtime.totalMemory() - runtime.freeMemory()) + " bytes");

		Hazelcast.shutdownAll();
	}
}
