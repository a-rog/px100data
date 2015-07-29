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

import com.px100systems.util.serialization.ExternalWriter;
import org.bson.Document;
import org.bson.types.Binary;
import java.util.Date;

/**
 * Mongo Document serializer.
 * Used internally by {@link MongoDatabaseStorage} to serialize beans through {@link com.px100systems.util.serialization.SerializationDefinition}<br>
 *
 * @version 0.3 <br>Copyright (c) 2015 Px100 Systems. All Rights Reserved.<br>
 * @author Alex Rogachevsky
 */
public class DocumentWriter implements ExternalWriter {
	private Document document;

	public DocumentWriter() {
		this.document = new Document();
	}

	public Document getDocument() {
		return document;
	}

	@Override
	public void writeString(String name, String value) {
		if (value != null)
			document.append(name, value);
	}

	@Override
	public void writeBoolean(String name, Boolean value) {
		if (value != null)
			document.append(name, value);
	}

	@Override
	public void writeDate(String name, Date value) {
		if (value != null)
			document.append(name, value);
	}

	@Override
	public void writeDouble(String name, Double value) {
		if (value != null)
			document.append(name, value);
	}

	@Override
	public void writeInteger(String name, Integer value) {
		if (value != null)
			document.append(name, value);
	}

	@Override
	public void writeLong(String name, Long value) {
		if (value != null)
			document.append(name, value);
	}

	@Override
	public void writeBytes(String name, byte[] value) {
		if (value != null && value.length > 0)
			document.append(name, new Binary(value));
	}
}
