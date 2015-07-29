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
 * Serializable OrderBy element. Wraps Cpmparable. Used by Hazelcast providers, ignored by other ones. See {@link Entity} for details.
 *  
 * @version 0.3 <br>Copyright (c) 2015 Px100 Systems. All Rights Reserved.<br>
 * @author Alex Rogachevsky
*/
public abstract class OrderBy<T> implements Serializable {
	public static final int ASC = 1;
	public static final int DESC = -1;
	private int modifier;

	public OrderBy() {
		modifier = ASC;
	}

	public OrderBy(int modifier) {
		this.modifier = modifier;
	}

	public int getModifier() {
		return modifier;
	}

	public abstract Comparable<?> get(T object);
}
