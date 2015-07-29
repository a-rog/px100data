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
package com.px100systems.data.plugin.storage.mongo;

import com.px100systems.util.serialization.ExternalReader;
import org.bson.Document;
import org.bson.types.Binary;

import java.util.Date;

/**
 * Mongo Document deserializer.
 * Used internally by {@link MongoDatabaseStorage} to deserialize beans through {@link com.px100systems.util.serialization.SerializationDefinition}<br>
 *
 * @version 0.3 <br>Copyright (c) 2015 Px100 Systems. All Rights Reserved.<br>
 * @author Alex Rogachevsky
 */
public class DocumentReader implements ExternalReader {
	private Document document;

	public DocumentReader(Document document) {
		this.document = document;
	}

	@Override
	public String readString(String name) {
		return document.getString(name);
	}

	@Override
	public Boolean readBoolean(String name) {
		return document.getBoolean(name);
	}

	@Override
	public Date readDate(String name) {
		return document.getDate(name);
	}

	@Override
	public Double readDouble(String name) {
		return document.getDouble(name);
	}

	@Override
	public Integer readInteger(String name) {
		return document.getInteger(name);
	}

	@Override
	public Long readLong(String name) {
		return document.getLong(name);
	}

	@Override
	public byte[] readBytes(String name) {
		Binary result = (Binary)document.get(name);
		return result == null || result.length() == 0 ? null : result.getData();
	}
}
