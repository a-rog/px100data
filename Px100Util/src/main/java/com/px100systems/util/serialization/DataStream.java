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
package com.px100systems.util.serialization;

import org.apache.commons.io.IOUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Date;

/**
 * Byte stream. Used internally by {@link SerializationDefinition}.
 *
 * @version 0.3 <br>Copyright (c) 2015 Px100 Systems. All Rights Reserved.<br>
 * @author Alex Rogachevsky
 */
@SuppressWarnings("unused")
public class DataStream {
	private static final int NULL = -1;

	private ByteArrayOutputStream os = null;
	private DataOutputStream dos = null;
	private DataInputStream dis = null;

	public DataStream() {
		os = new ByteArrayOutputStream(1024);
		dos = new DataOutputStream(os);
	}

	public DataStream(byte[] data) {
		dis = new DataInputStream(new ByteArrayInputStream(data));
	}

	public byte[] getData() {
		return os.toByteArray();
	}

	public void close() {
		if (dos != null)
			IOUtils.closeQuietly(dos);
		if (dis != null)
			IOUtils.closeQuietly(dis);
	}

	public void writeBytes(DataStream data) {
		try {
			if (data == null)
				dos.writeInt(NULL);
			else {
				byte[] bytes = data.getData();
				dos.writeInt(bytes.length);
				dos.write(bytes);
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public void writeInteger(Integer data) {
		try {
			if (data == null)
				dos.writeInt(NULL);
			else {
				dos.writeInt(Integer.BYTES);
				dos.writeInt(data);
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public void writeLong(Long data) {
		try {
			if (data == null)
				dos.writeInt(NULL);
			else {
				dos.writeInt(Long.BYTES);
				dos.writeLong(data);
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public void writeDouble(Double data) {
		try {
			if (data == null)
				dos.writeInt(NULL);
			else {
				dos.writeInt(Double.BYTES);
				dos.writeDouble(data);
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public void writeBoolean(Boolean data) {
		try {
			if (data == null)
				dos.writeInt(NULL);
			else {
				dos.writeInt(1);
				dos.writeBoolean(data);
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public void writeDate(Date data) {
		writeLong(data == null ? null : data.getTime());
	}

	public void writeString(String data) {
		try {
			if (data == null)
				dos.writeInt(NULL);
			else {
				dos.writeInt(data.length());
				dos.write(data.getBytes("UTF-8"));
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public DataStream readBytes() {
		try {
			int size = dis.readInt();
			if (size == NULL)
				return null;

			byte[] buffer = new byte[size];
			dis.readFully(buffer);
			return new DataStream(buffer);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public Integer readInteger() {
		try {
			int size = dis.readInt();
			if (size == NULL)
				return null;

			return dis.readInt();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public Long readLong() {
		try {
			int size = dis.readInt();
			if (size == NULL)
				return null;

			return dis.readLong();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public Double readDouble() {
		try {
			int size = dis.readInt();
			if (size == NULL)
				return null;

			return dis.readDouble();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public Boolean readBoolean() {
		try {
			int size = dis.readInt();
			if (size == NULL)
				return null;

			return dis.readBoolean();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public Date readDate() {
		Long time = readLong();
		return time == null ? null : new Date(time);
	}

	public String readString() {
		try {
			int size = dis.readInt();
			if (size == NULL)
				return null;

			byte[] buffer = new byte[size];
			dis.readFully(buffer);
			return new String(buffer);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
}
