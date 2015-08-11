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
package com.px100systems.data.plugin.storage.ignite;

import com.px100systems.data.core.Criteria;
import com.px100systems.data.core.Criteria.and;
import com.px100systems.data.core.Criteria.between;
import com.px100systems.data.core.Criteria.containsText;
import com.px100systems.data.core.Criteria.endsWithText;
import com.px100systems.data.core.Criteria.eq;
import com.px100systems.data.core.Criteria.ge;
import com.px100systems.data.core.Criteria.gt;
import com.px100systems.data.core.Criteria.icontainsText;
import com.px100systems.data.core.Criteria.in;
import com.px100systems.data.core.Criteria.le;
import com.px100systems.data.core.Criteria.lt;
import com.px100systems.data.core.Criteria.ne;
import com.px100systems.data.core.Criteria.not;
import com.px100systems.data.core.Criteria.or;
import com.px100systems.data.core.Criteria.startsWithText;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Criteria to Ignite (ANSI 99) SQL translator. Uses standard SQL Date and Boolean literals.
 * 
 * @version 0.3 <br>Copyright (c) 2015 Px100 Systems. All Rights Reserved.<br>
 * @author Alex Rogachevsky
*/
public class PredicateQueryBuilder implements Criteria.Converter<String> {
	@Override
	public String convert(and predicate) {
		if (predicate.getMembers().length == 0)
			throw new RuntimeException("Empty AND criteria: " + predicate);

		if (predicate.getMembers().length == 1)
			return predicate.getMembers()[0].convert(this);

		StringBuilder result = new StringBuilder();
		for (Criteria q : predicate.getMembers()) {
			if (result.length() > 0)
				result.append(" AND ");
			result.append(q.convert(this));
		}
		return "(" + result + ")";
	}

	@Override
	public String convert(or predicate) {
		if (predicate.getMembers().length == 0)
			throw new RuntimeException("Empty OR criteria: " + predicate);

		if (predicate.getMembers().length == 1)
			return predicate.getMembers()[0].convert(this);

		StringBuilder result = new StringBuilder();
		for (Criteria q : predicate.getMembers()) {
			if (result.length() > 0)
				result.append(" OR ");
			result.append(q.convert(this));
		}
		return "(" + result + ")";
	}

	private String toSql99(Object value) {
		if (value == null)
			throw new RuntimeException("Null criteria value");
		if (value instanceof String)
			return "'" + value + "'";
		if (value instanceof Double)
			return new DecimalFormat("#.########").format(value);
		if (value instanceof Long || value instanceof Integer)
			return value.toString();
		if (value instanceof Boolean)
			return value.toString().toUpperCase();
		if (value instanceof Date)
			return "'" + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(value) + "'";
		throw new RuntimeException("Unsupported criteria value type: " + value.getClass().getSimpleName());
	}

	@Override
	public String convert(not predicate) {
		return "NOT (" + predicate.getMember().convert(this) + ")";
	}

	@Override
	public String convert(eq predicate) {
		if (predicate.getValue() == null)
			return predicate.getMember() + " IS NULL";
		return predicate.getMember() + " = " + toSql99(predicate.getValue());
	}

	@Override
	public String convert(ne predicate) {
		if (predicate.getValue() == null)
			return predicate.getMember() + " IS NOT NULL";
		return predicate.getMember() + " <> " + toSql99(predicate.getValue());
	}

	@Override
	public String convert(gt predicate) {
		return predicate.getMember() + " > " + toSql99(predicate.getValue());
	}

	@Override
	public String convert(ge predicate) {
		return predicate.getMember() + " >= " + toSql99(predicate.getValue());
	}

	@Override
	public String convert(lt predicate) {
		return predicate.getMember() + " < " + toSql99(predicate.getValue());
	}

	@Override
	public String convert(le predicate) {
		return predicate.getMember() + " <= " + toSql99(predicate.getValue());
	}

	@Override
	public String convert(between predicate) {
		return predicate.getMember() + " BETWEEN " + toSql99(predicate.getMin()) + " AND " + toSql99(predicate.getMax());
	}

	@Override
	public String convert(in predicate) {
		StringBuilder result = new StringBuilder();
		for (Object value : predicate.getList()) {
			if (result.length() > 0)
				result.append(", ");
			result.append(toSql99(value));
		}
		return predicate.getMember() + " IN (" + result + ")";
	}

	@Override
	public String convert(containsText predicate) {
		return predicate.getMember() + " LIKE '%" + predicate.getText() + "%'";
	}

	@Override
	@SuppressWarnings({ "unchecked" })
	public String convert(icontainsText predicate) {
		return "LOWER(" + predicate.getMember() + ") LIKE '%" + predicate.getText().toLowerCase() + "%'";
	}

	@Override
	@SuppressWarnings({ "unchecked" })
	public String convert(startsWithText predicate) {
		return predicate.getMember() + " LIKE '" + predicate.getText() + "%'";
	}

	@Override
	@SuppressWarnings({ "unchecked" })
	public String convert(endsWithText predicate) {
		return predicate.getMember() + " LIKE '%" + predicate.getText() + "'";
	}

	@Override
	public String convert(Criteria.isNull predicate) {
		return predicate.getMember() + " IS NULL";
	}
}
