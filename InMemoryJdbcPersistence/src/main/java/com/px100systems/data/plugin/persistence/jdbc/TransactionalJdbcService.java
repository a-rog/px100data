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
package com.px100systems.data.plugin.persistence.jdbc;

import org.springframework.beans.factory.BeanNameAware;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Transactional Spring JDBC template wrapper. Used internally by the {@link Storage}.<br>
 * <br>
 * Typical Spring config (in the client using this provider):<br>
 * <pre>
 * {@code
 *  <bean id="service1" class="com.px100systems.data.plugin.persistence.jdbc.TransactionalJdbcService">
 *		<property name="transactionManager" ref="txManager1"/>
 *  </bean>
 *  [<bean id="service2" class="com.px100systems.data.plugin.persistence.jdbc.TransactionalJdbcService">
 *		<property name="transactionManager" ref="anotherTxManager"/>
 *  </bean>]
 * 
 *	<bean id="dataSource1" class="org.springframework.jdbc.datasource.DriverManagerDataSource">
 *	    <property name="driverClassName" value="${jdbc.driverClassName}"/>
 *	    <property name="url" value="${jdbc.url}"/>
 *	    [<property name="username" value="${jdbc.username}"/>
 *	    <property name="password" value="${jdbc.password}"/>]
 *	</bean>	
 * 	<bean id="txManager1" class="org.springframework.jdbc.datasource.DataSourceTransactionManager">
 *		<property name="dataSource" ref="dataSource1"/>
 *	</bean>
 * }
 * </pre>
 *
 * @version 0.3 <br>Copyright (c) 2015 Px100 Systems. All Rights Reserved.<br>
 * @author Alex Rogachevsky
*/
public class TransactionalJdbcService implements BeanNameAware {
	private JdbcTemplate jdbc;
	private TransactionTemplate transaction;
	private String name = "unnamed";
	
	public TransactionalJdbcService() {
	}

	/**
	 * Spring transaction manager with configured DataSource, etc.
	 * @param mgr transaction manager
	 */
	@Required
	public void setTransactionManager(DataSourceTransactionManager mgr) {
		transaction = new TransactionTemplate(mgr);
		transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		transaction.setTimeout(300);
        jdbc = new JdbcTemplate(mgr.getDataSource());
		try {
			mgr.getDataSource().getConnection().close();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public JdbcTemplate getJdbc() {
		return jdbc;
	}

	public interface JdbcCallback<T> {
		T transaction(JdbcTemplate jdbc);
	}

	public <T> T write(final JdbcCallback<T> callback) {
		return transaction.execute(new TransactionCallback<T>() {
			@Override
			public T doInTransaction(TransactionStatus arg0) {
				return callback.transaction(jdbc);
			}
		});
	}

	@Override
	public void setBeanName(String name) {
		this.name = name;
	}

	public String getName() {
		return name;
	}
}
