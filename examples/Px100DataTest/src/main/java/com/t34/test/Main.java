package com.t34.test;

import com.px100systems.data.core.DataStorageException;
import com.px100systems.data.core.DatabaseStorage;
import com.px100systems.data.core.Transaction;
import com.t34.test.entity.MyEntity;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import java.util.Date;
import static com.px100systems.data.core.Criteria.*;

public class Main {
	public static void main(String[] args) {
		ApplicationContext context = new ClassPathXmlApplicationContext("classpath:config.xml");
		DatabaseStorage ds = context.getBean("dataStorage", DatabaseStorage.class);

		try {
			Transaction tx = ds.transaction(0);
			MyEntity bean = new MyEntity();
			bean.setDate(new Date());
			bean.setText("Hello");
			tx.insert(bean);
			tx.commit();

			tx = tx.transaction(0);
			System.out.println(tx.findOne(MyEntity.class, not(isNull("id"))));
		} catch (DataStorageException e) {
			throw new RuntimeException(e);
		}
		
		((ClassPathXmlApplicationContext)context).close();
	}
}
