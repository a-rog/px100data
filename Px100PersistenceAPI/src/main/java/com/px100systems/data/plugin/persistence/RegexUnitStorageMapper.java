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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Required;

/**
 * Unit name to storage name mapping via regular expressions.
 * 
 * @version 0.3 <br>Copyright (c) 2015 Px100 Systems. All Rights Reserved.<br>
 * @author Alex Rogachevsky
 */
public class RegexUnitStorageMapper implements UnitStorageMapper {
	private Map<String, List<Pattern>> mapping;

	/**
	 * Maps the unit name (key - regex) to the storage name.<br>
	 * Example {@code <entry key="Test.*" value="big_table"/>}
	 * @param patterns the map
	 */
	@Required
	public void setMapping(Map<String, String> patterns) {
		mapping = new HashMap<String, List<Pattern>>();
		for (Map.Entry<String, String> e : patterns.entrySet()) {
			List<Pattern> list = mapping.get(e.getValue());
			if (list == null) {
				list = new ArrayList<Pattern>();
				mapping.put(e.getValue(), list);
			}
			list.add(Pattern.compile(e.getKey()));
		}
	}

	@Override
	public String map(String unitName) {
		for (Map.Entry<String, List<Pattern>> e : mapping.entrySet())
			for (Pattern pattern : e.getValue())
				if (pattern.matcher(unitName).matches())
					return e.getKey();
		throw new RuntimeException("RegexUnitStorageMapper could not match unit name " + unitName);
	}

	@Override
	public List<String> storages() {
		List<String> result = new ArrayList<String>(mapping.keySet());
		Collections.sort(result);
		return result;
	}
}
