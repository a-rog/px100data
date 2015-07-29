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

import java.io.Serializable;

/**
 * In-place update. used internally by the Transaction.
 *
 * @version 0.3 <br>Copyright (c) 2015 Px100 Systems. All Rights Reserved.<br>
 * @author Alex Rogachevsky
 */
public class InPlaceUpdate<T> implements Serializable {
	private int operationOrderNo;

	private Class<T> cls;
	private Long id;
	private EntityProcessor<T> processor;
	private String unitName;

	@SuppressWarnings("unused")
	public InPlaceUpdate() {
	}

	public InPlaceUpdate(int operationOrderNo, Class<T> cls, Long id, int tenantId, EntityProcessor<T> processor) {
		this.operationOrderNo = operationOrderNo;
		this.cls = cls;
		this.id = id;
		this.processor = processor;
		this.unitName = Entity.unitFromClass(cls, tenantId);
	}

	@SuppressWarnings("unchecked")
	public void eval(Object value) {
		processor.process((T)value);
	}

	public Class<T> getCls() {
		return cls;
	}

	public Long getId() {
		return id;
	}

	public int getOperationOrderNo() {
		return operationOrderNo;
	}

	public String getUnitName() {
		return unitName;
	}
}
