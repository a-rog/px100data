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
package com.px100systems.data.plugin.storage.hazelcast;

import com.hazelcast.nio.serialization.PortableReader;
import com.hazelcast.nio.serialization.PortableWriter;
import com.px100systems.util.serialization.ExternalReader;
import com.px100systems.util.serialization.ExternalWriter;
import com.px100systems.util.serialization.SerializationDefinition;
import java.io.IOException;
import java.util.Date;

/**
 * Universal reflection-based Portable implementation based on {@link SerializationDefinition}.
 * Use it to implement Portable for one base entity all other entities will derive from.
 * The base entity should be a direct or indirect subclass of {@link com.px100systems.data.core.StoredBean}: most commonly {@link com.px100systems.data.core.Entity}.
 *
 * @version 0.3 <br>Copyright (c) 2015 Px100 Systems. All Rights Reserved.<br>
 * @author Alex Rogachevsky
 */
public class PortableImpl {
	private static final int FACTORY_ID = 100;

	public static class Writer implements ExternalWriter {
		private PortableWriter writer;

		public Writer(PortableWriter writer) {
			this.writer = writer;
		}

		@Override
		public void writeString(String name, String value) {
			try {
				writer.writeUTF(name, value == null ? "" : value);
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}

		@Override
		public void writeBoolean(String name, Boolean value) {
			try {
				writer.writeBoolean(name, value == null ? false : value);
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}

		@Override
		public void writeDate(String name, Date value) {
			try {
				writer.writeLong(name, value == null ? PredicateQueryBuilder.NULL_LONG : value.getTime());
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}

		@Override
		public void writeDouble(String name, Double value) {
			try {
				writer.writeDouble(name, value == null ? PredicateQueryBuilder.NULL_DOUBLE : value);
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}

		@Override
		public void writeInteger(String name, Integer value) {
			try {
				writer.writeInt(name, value == null ? PredicateQueryBuilder.NULL_INT : value);
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}

		@Override
		public void writeLong(String name, Long value) {
			try {
				writer.writeLong(name, value == null ? PredicateQueryBuilder.NULL_LONG : value);
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}

		@Override
		public void writeBytes(String name, byte[] value) {
			try {
				writer.writeByteArray(name, value == null ? new byte[0] : value);
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}
	}

	public static class Reader implements ExternalReader {
		private PortableReader reader;

		public Reader(PortableReader reader) {
			this.reader = reader;
		}

		@Override
		public String readString(String name) {
			try {
				String result = reader.readUTF(name);
				return result == null || result.isEmpty() ? null : result;
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}

		@Override
		public Boolean readBoolean(String name) {
			try {
				return reader.readBoolean(name);
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}

		@Override
		public Date readDate(String name) {
			try {
				long time = reader.readLong(name);
				return time == PredicateQueryBuilder.NULL_LONG ? null : new Date(time);
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}

		@Override
		public Double readDouble(String name) {
			try {
				double result = reader.readDouble(name);
				return result == PredicateQueryBuilder.NULL_DOUBLE ? null : result;
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}

		@Override
		public Integer readInteger(String name) {
			try {
				int result = reader.readInt(name);
				return result == PredicateQueryBuilder.NULL_INT ? null : result;
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}

		@Override
		public Long readLong(String name) {
			try {
				long result = reader.readLong(name);
				return result == PredicateQueryBuilder.NULL_LONG ? null : result;
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}

		@Override
		public byte[] readBytes(String name) {
			try {
				byte[] result = reader.readByteArray(name);
				return result == null || result.length == 0 ? null : result;
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}
	}

	/**
	 * Used internally.
	 * @return internal ID
	 */
	public static int factoryId() {
		return FACTORY_ID;
	}

	/**
	 * used internally
	 * @param bean entity to serialize
	 * @return internally assigned class ID
	 */
	public static int classId(Object bean) {
		return SerializationDefinition.get(bean.getClass()).getId();
	}

	/**
	 * Serializes entity
	 * @param bean entity
	 * @param writer Hazelcast portable writer
	 * @throws IOException
	 */
	public static void writePortable(Object bean, PortableWriter writer) throws IOException {
		SerializationDefinition.get(bean.getClass()).write(new Writer(writer), bean);
	}

	/**
	 * Deerializes entity
	 * @param bean entity
	 * @param reader Hazelcast portable reader
	 * @throws IOException
	 */
	public static void readPortable(Object bean, PortableReader reader) throws IOException {
		SerializationDefinition.get(bean.getClass()).read(new Reader(reader), bean);
	}
}
