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
package com.px100systems.data.core;

/**
 * Entity deletion request - by ID or some filter Condition. used internally by Transaction.
 * 
 * @version 0.3 <br>Copyright (c) 2015 Px100 Systems. All Rights Reserved.<br>
 * @author Alex Rogachevsky
*/
public class Delete {
	private int operationOrderNo;

	private String unitName;
	private Long id;
	private Criteria criteria;
	private Class<?> entityClass;

	public Delete(int operationOrderNo, Entity entity) {
		this.operationOrderNo = operationOrderNo;
		unitName = entity.unitName();
		id = entity.getId();
		criteria = Criteria.eq("id", id);
		entityClass = entity.getClass();
	}

	public Delete(int operationOrderNo, Class<?> entityClass, int tenantId, Criteria criteria) {
		this.operationOrderNo = operationOrderNo;
		this.entityClass = entityClass;
		this.unitName = Entity.unitFromClass(entityClass, tenantId);
		this.criteria = criteria;
		id = null;
	}

	public Delete(Class<?> entityClass, String unitName, Criteria criteria) {
		this.entityClass = entityClass;
		this.unitName = unitName;
		this.criteria = criteria;
		id = null;
	}

	public String getUnitName() {
		return unitName;
	}

	public Criteria getCriteria() {
		return criteria;
	}

	public Long getId() {
		return id;
	}

	@SuppressWarnings("unchecked")
	public <T> Class<T> getEntityClass() {
		return (Class<T>)entityClass;
	}

	public int getOperationOrderNo() {
		return operationOrderNo;
	}
}
