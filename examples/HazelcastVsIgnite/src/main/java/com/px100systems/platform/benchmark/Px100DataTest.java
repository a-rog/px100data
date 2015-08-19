package com.px100systems.platform.benchmark;

import com.px100systems.data.core.DataStorageException;
import com.px100systems.data.core.DatabaseStorage;
import com.px100systems.data.core.Transaction;
import com.px100systems.platform.benchmark.entity.TestPx100Entity;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import java.util.Collections;
import java.util.Date;
import static com.px100systems.data.core.Criteria.*;

public class Px100DataTest {
	public static void main(String[] args) {
		if (args.length < 1) {
			System.err.println("Usage: java -cp \"grid-benchmark.jar:lib/*\" -Xms512m -Xmx3000m -Xss4m com.px100systems.platform.benchmark.Px100DataTest <number> hazelcast|ignite");
			return;
		}
		long numberOfEntities = Long.parseLong(args[0]);

		ApplicationContext context = new ClassPathXmlApplicationContext("classpath:" + args[1] + "-config.xml");
		DatabaseStorage ds = context.getBean("dataStorage", DatabaseStorage.class);

		try {
			Runtime runtime = Runtime.getRuntime();
			long memory = runtime.totalMemory() - runtime.freeMemory();
			System.out.println("Starting - used heap: " + memory + " bytes");

			long start = new Date().getTime();

			TestPx100Entity first = null;
			TestPx100Entity last = null;
			for (int i = 0, n = (int)numberOfEntities / 1000; i < n; i++) {
				Transaction tx = ds.transaction(0);

				for (int j = 0; j < 1000; j++) {
					TestPx100Entity entity = new TestPx100Entity(true);
					if (first == null)
						first = entity;
					last = entity;
					tx.insert(entity);
				}

				tx.commit();

				if (i == 0)
					System.out.print("Inserting " + numberOfEntities + " records: ");
				System.out.print(".");
			}

			assert first != null;

			Transaction tx = ds.transaction(0);

			long end = new Date().getTime();
			long memoryAfterInserts = runtime.totalMemory() - runtime.freeMemory();
			System.out.println("\nInserted all records - used heap: " + memoryAfterInserts + " bytes");
			System.out.println("Unit: " + tx.count(TestPx100Entity.class, null) + " entries, used heap: " + (memoryAfterInserts - memory) +
				" bytes, inserts took " + (end - start) + " ms");
			start = end;

			int count = 0;
			for (TestPx100Entity ignored : tx.find(TestPx100Entity.class, and(containsText("textField", "Jane"), gt("id", first.getId())),
				Collections.singletonList("id"), 100))
				count++;
			end = new Date().getTime();
			System.out.println("Query 1 count: " + count + ", time: "+ (end - start) + " ms, heap size: " + (runtime.totalMemory() - runtime.freeMemory()) + " bytes");
			start = end;

			count = 0;
			for (TestPx100Entity ignored : tx.find(TestPx100Entity.class, and(containsText("textField", "Jane"), le("id", last.getId())),
				Collections.singletonList("id DESC"), 100))
				count++;
			end = new Date().getTime();
			System.out.println("Query 2 count: " + count + ", time: "+ (end - start) + " ms, heap size: " + (runtime.totalMemory() - runtime.freeMemory()) + " bytes");
			start = end;

			count = 0;
			for (TestPx100Entity ignored : tx.find(TestPx100Entity.class, and(containsText("textField", "Richards"), gt("id", first.getId())),
				Collections.singletonList("id"), 100))
				count++;
			end = new Date().getTime();
			System.out.println("Query 3 count: " + count + ", time: "+ (end - start) + " ms, heap size: " + (runtime.totalMemory() - runtime.freeMemory()) + " bytes");
			start = end;

			count = 0;
			for (TestPx100Entity ignored : tx.find(TestPx100Entity.class, and(containsText("textField", "Richards"), le("id", last.getId())),
				Collections.singletonList("id DESC"), 100))
				count++;
			end = new Date().getTime();
			System.out.println("Query 4 count: " + count + ", time: "+ (end - start) + " ms, heap size: " + (runtime.totalMemory() - runtime.freeMemory()) + " bytes");
		} catch (DataStorageException e) {
			throw new RuntimeException(e);
		}

		((ClassPathXmlApplicationContext)context).close();
	}
}
