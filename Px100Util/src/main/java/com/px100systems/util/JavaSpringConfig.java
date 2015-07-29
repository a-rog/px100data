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
package com.px100systems.util;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;

/**
 * Spring configuration to be used with @Configuration-annotated classes.
 * 
 * @version 0.3 <br>Copyright (c) 2015 Px100 Systems. All Rights Reserved.<br>
 * @author Alex Rogachevsky
 */
public abstract class JavaSpringConfig {
	@Autowired
	private AutowireCapableBeanFactory beanFactory;

	public JavaSpringConfig() {
	}

	/**
	 * Get the bean (declared somewhere else within the same context).
	 * @param name bean name
	 * @param requiredType bena type
	 * @param <T> bean class
	 * @return the bean
	 */
	protected <T> T springBean(String name, Class<T> requiredType) {
		return beanFactory.getBean(name, requiredType);
	}

	/**
	 * Autowire a non Spring-created bean that has @Autowired annotated fields
	 * @param bean teh bean to autowire
	 * @param <B> bean class
	 * @return teh autowired bean
	 */
	@SuppressWarnings("unused")
	protected <B> B autowire(B bean) {
		beanFactory.autowireBean(bean);
		return bean;
	}
}
