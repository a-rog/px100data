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

import java.util.Date;

/**
 * External (Hazelcast Portable, etc.) writer. Guaranteed to never have nulls.
 * Used internally by {@link SerializationDefinition}.
 *
 * @version 0.3 <br>Copyright (c) 2015 Px100 Systems. All Rights Reserved.<br>
 * @author Alex Rogachevsky
 */
public interface ExternalWriter {
	void writeString(String name, String value);
	void writeBoolean(String name, Boolean value);
	void writeDate(String name, Date value);
	void writeDouble(String name, Double value);
	void writeInteger(String name, Integer value);
	void writeLong(String name, Long value);
	void writeBytes(String name, byte[] value);
}
