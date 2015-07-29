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

import java.util.Date;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.springframework.util.ObjectUtils;

/**
 * Serialized data record - used internally by persisters to load and serialize the entity.
 * 
 * @version 0.3 <br>Copyright (c) 2015 Px100 Systems. All Rights Reserved.<br>
 * @author Alex Rogachevsky
*/
public class RawRecord {
	private static final String JSON_DATE_FORMAT = "yyyy-MM-dd HH:mm:ss.SSS";

	private String unitName;
	private String idGeneratorName;
	private Long id;
	private Date lastUpdate; 

	private Class<? extends Entity> entityClass;
	private String serializedEntity;
	
	public static Gson createGson() {
		return new GsonBuilder().setDateFormat(JSON_DATE_FORMAT).setPrettyPrinting().create();
	}

	public RawRecord() {
	}

	public RawRecord(String unitName, Long id) {
		super();
		this.unitName = unitName;
		this.id = id;
	}

	public RawRecord(Entity entity, Gson gson) {
		id = entity.getId();
		unitName = entity.unitName();
		idGeneratorName = entity.idGeneratorName();
		lastUpdate = entity.getModifiedAt();
		entityClass = entity.getClass();
		serializedEntity = gson.toJson(entity);
	}

	@SuppressWarnings("unchecked")
	public <T extends Entity> T toEntity(Gson gson) {
		return serializedEntity == null || serializedEntity.isEmpty() ? null : (T)gson.fromJson(serializedEntity, entityClass);
	}
	
	@Override
	public int hashCode() {
		return (unitName + id).hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == null || !(obj instanceof RawRecord))
			return false;
		
		RawRecord o = (RawRecord)obj;
		
		String data1 = serializedEntity == null ? "" : serializedEntity;
		String data2 = o.serializedEntity == null ? "" : o.serializedEntity;
		if (!data1.equals(data2))
			return false;
		
		return ObjectUtils.nullSafeEquals(unitName, o.unitName) && ObjectUtils.nullSafeEquals(id, o.id) && ObjectUtils.nullSafeEquals(lastUpdate, o.lastUpdate);
	}

	public String getUnitName() {
		return unitName;
	}

	public void setUnitName(String unitName) {
		this.unitName = unitName;
	}

	public String getIdGeneratorName() {
		return idGeneratorName;
	}

	public void setIdGeneratorName(String idGeneratorName) {
		this.idGeneratorName = idGeneratorName;
	}

	public String getSerializedEntity() {
		return serializedEntity;
	}

	public void setSerializedEntity(String serializedEntity) {
		this.serializedEntity = serializedEntity;
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public Date getLastUpdate() {
		return lastUpdate;
	}

	@SuppressWarnings("unused")
	public void setLastUpdate(Date lastUpdate) {
		this.lastUpdate = lastUpdate;
	}

	public Class<? extends Entity> getEntityClass() {
		return entityClass;
	}

	@SuppressWarnings("unused")
	public void setEntityClass(Class<? extends Entity> entityClass) {
		this.entityClass = entityClass;
	}
}
