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
package com.px100systems.data.plugin.persistence;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.List;
import com.px100systems.data.core.Index;
import com.px100systems.data.core.OrderBy;
import com.px100systems.data.core.StoredBean;
import com.px100systems.util.serialization.SerializationDefinition;
import com.px100systems.util.serialization.SerializedCollection;

/**
 * Transaction record - tells write-behind persistence servers what to do. Used internally.
 * 
 * @version 0.3 <br>Copyright (c) 2015 Px100 Systems. All Rights Reserved.<br>
 * @author Alex Rogachevsky
*/
public class PersistenceLogEntry extends StoredBean implements Externalizable {
	public static final String UNIT_NAME = "PersistenceLog__";

	private Long time;

	@SerializedCollection(type = PersistenceLogRecord.class)
	private List<PersistenceLogRecord> newEntities;

	@SerializedCollection(type = PersistenceLogRecord.class)
	private List<PersistenceLogRecord> updatedEntities;

	@SerializedCollection(type = PersistenceLogRecord.class)
	private List<PersistenceLogRecord> deletedEntities;

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		SerializationDefinition.get(getClass()).write(out, this);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		SerializationDefinition.get(getClass()).read(in, this);
	}

	public static class PersistenceLogRecord {
		private String unitName;
		private Long id;

		@SuppressWarnings("unused")
		public PersistenceLogRecord() {
		}

		public PersistenceLogRecord(String unitName, Long id) {
			this.unitName = unitName;
			this.id = id;
		}

		public String getUnitName() {
			return unitName;
		}

		@SuppressWarnings("unused")
		public void setUnitName(String unitName) {
			this.unitName = unitName;
		}

		public Long getId() {
			return id;
		}

		public void setId(Long id) {
			this.id = id;
		}
	}

	@Override
	public String unitName() {
		return UNIT_NAME;
	}

	@Index
	public Long getTime() {
		return time;
	}

	public void setTime(Long time) {
		this.time = time;
	}

	@SuppressWarnings({"rawtypes", "unused"})
	public static OrderBy orderByTime(int modifier) {
		return new OrderBy<PersistenceLogEntry>(modifier) {
			private static final long serialVersionUID = 1L;
	
			@Override
			public Comparable<?> get(PersistenceLogEntry object) {
				return object.getTime();
			}
		};
	}

	public List<PersistenceLogRecord> getNewEntities() {
		return newEntities;
	}

	public void setNewEntities(List<PersistenceLogRecord> newEntities) {
		this.newEntities = newEntities;
	}

	public List<PersistenceLogRecord> getUpdatedEntities() {
		return updatedEntities;
	}

	public void setUpdatedEntities(List<PersistenceLogRecord> updatedEntities) {
		this.updatedEntities = updatedEntities;
	}

	public List<PersistenceLogRecord> getDeletedEntities() {
		return deletedEntities;
	}

	public void setDeletedEntities(List<PersistenceLogRecord> deletedEntities) {
		this.deletedEntities = deletedEntities;
	}
}
