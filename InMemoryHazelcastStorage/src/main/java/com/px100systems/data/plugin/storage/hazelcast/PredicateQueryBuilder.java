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

import com.hazelcast.query.Predicate;
import com.hazelcast.query.Predicates;
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
import com.px100systems.data.core.StoredBean;
import com.px100systems.data.core.Criteria;
import com.px100systems.util.serialization.SerializationDefinition;
import java.util.Date;

/**
 * Criteria to Hazelcast Predicate translator. used internally. Takes care of nulls and other Hazelcast-specific query param conversions.
 * 
 * @version 0.3 <br>Copyright (c) 2015 Px100 Systems. All Rights Reserved.<br>
 * @author Alex Rogachevsky
*/
public class PredicateQueryBuilder implements Criteria.Converter<Predicate<HazelcastInMemoryStorage.Key, StoredBean>> {
	public static final long NULL_LONG = Long.MIN_VALUE + 1;
	public static final int NULL_INT = Integer.MIN_VALUE + 1;
	public static final double NULL_DOUBLE = Double.MIN_VALUE + 1;

	private SerializationDefinition def;

	public PredicateQueryBuilder(Class<?> cls) {
		def = SerializationDefinition.get(cls);
	}

	@Override
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public Predicate<HazelcastInMemoryStorage.Key, StoredBean> convert(and predicate) {
		if (predicate.getMembers().length == 0)
			throw new RuntimeException("Empty AND criteria: " + predicate);

		if (predicate.getMembers().length == 1)
			return predicate.getMembers()[0].convert(this);

		Predicate[] q = new Predicate[predicate.getMembers().length];
		for (int i = 0; i < q.length; i++)
			q[i] = predicate.getMembers()[i].convert(this);
		return new Predicates.AndPredicate(q);
	}

	@Override
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public Predicate<HazelcastInMemoryStorage.Key, StoredBean> convert(or predicate) {
		if (predicate.getMembers().length == 0)
			throw new RuntimeException("Empty OR criteria: " + predicate);

		if (predicate.getMembers().length == 1)
			return predicate.getMembers()[0].convert(this);

		Predicate[] q = new Predicate[predicate.getMembers().length];
		for (int i = 0; i < q.length; i++)
			q[i] = predicate.getMembers()[i].convert(this);
		return new Predicates.OrPredicate(q);
	}

	private static Comparable<?> adjustValue(Comparable<?> value) {
		if (value != null && (value instanceof Date))
			return new Long(((Date)value).getTime());
		return value;
	}

	private static Comparable<?> nullValue(Comparable<?> value) {
		if (value != null && ((value instanceof Date) || (value instanceof Long)))
			return new Long(NULL_LONG);
		if (value != null && (value instanceof Integer))
			return new Integer(NULL_INT);
		if (value != null && (value instanceof Double))
			return new Double(NULL_DOUBLE);
		return value;
	}

	@Override
	@SuppressWarnings({ "unchecked" })
	public Predicate<HazelcastInMemoryStorage.Key, StoredBean> convert(not predicate) {
		return new Predicates.NotPredicate(predicate.getMember().convert(this));
	}

	@Override
	@SuppressWarnings({ "unchecked" })
	public Predicate<HazelcastInMemoryStorage.Key, StoredBean> convert(eq predicate) {
		return new Predicates.EqualPredicate(predicate.getMember(), adjustValue(predicate.getValue()));
	}

	@Override
	@SuppressWarnings({ "unchecked" })
	public Predicate<HazelcastInMemoryStorage.Key, StoredBean> convert(ne predicate) {
		return new Predicates.NotEqualPredicate(predicate.getMember(), adjustValue(predicate.getValue()));
	}

	@Override
	@SuppressWarnings({ "unchecked" })
	public Predicate<HazelcastInMemoryStorage.Key, StoredBean> convert(gt predicate) {
		return new Predicates.GreaterLessPredicate(predicate.getMember(), adjustValue(predicate.getValue()), false, false);
	}

	@Override
	@SuppressWarnings({ "unchecked" })
	public Predicate<HazelcastInMemoryStorage.Key, StoredBean> convert(ge predicate) {
		return new Predicates.GreaterLessPredicate(predicate.getMember(), adjustValue(predicate.getValue()), true, false);
	}

	@Override
	@SuppressWarnings({ "unchecked" })
	public Predicate<HazelcastInMemoryStorage.Key, StoredBean> convert(lt predicate) {
		if (predicate.getValue() != null && (predicate.getValue() instanceof String))
			return new Predicates.GreaterLessPredicate(predicate.getMember(), predicate.getValue(), false, true);
		else
			//noinspection ConstantConditions
			return new Predicates.AndPredicate(
				new Predicates.GreaterLessPredicate(predicate.getMember(), adjustValue(predicate.getValue()), false, true),
				new Predicates.GreaterLessPredicate(predicate.getMember(), nullValue(predicate.getValue()), false, false));
	}

	@Override
	@SuppressWarnings({ "unchecked" })
	public Predicate<HazelcastInMemoryStorage.Key, StoredBean> convert(le predicate) {
		if (predicate.getValue() != null && (predicate.getValue() instanceof String))
			return new Predicates.GreaterLessPredicate(predicate.getMember(), predicate.getValue(), true, true);
		else
			//noinspection ConstantConditions
			return new Predicates.AndPredicate(
				new Predicates.GreaterLessPredicate(predicate.getMember(), adjustValue(predicate.getValue()), true, true),
				new Predicates.GreaterLessPredicate(predicate.getMember(), nullValue(predicate.getValue()), false, false));
	}

	@Override
	@SuppressWarnings({ "unchecked" })
	public Predicate<HazelcastInMemoryStorage.Key, StoredBean> convert(between predicate) {
		return new Predicates.BetweenPredicate(predicate.getMember(), adjustValue(predicate.getMin()), adjustValue(predicate.getMax()));
	}

	@Override
	@SuppressWarnings({ "unchecked" })
	public Predicate<HazelcastInMemoryStorage.Key, StoredBean> convert(in predicate) {
		return new Predicates.InPredicate(predicate.getMember(), predicate.getList());
	}

	@Override
	@SuppressWarnings({ "unchecked" })
	public Predicate<HazelcastInMemoryStorage.Key, StoredBean> convert(containsText predicate) {
		return new Predicates.LikePredicate(predicate.getMember(), "%" + predicate.getText() + "%");
	}

	@Override
	@SuppressWarnings({ "unchecked" })
	public Predicate<HazelcastInMemoryStorage.Key, StoredBean> convert(icontainsText predicate) {
		return new Predicates.ILikePredicate(predicate.getMember(), "%" + predicate.getText() + "%");
	}

	@Override
	@SuppressWarnings({ "unchecked" })
	public Predicate<HazelcastInMemoryStorage.Key, StoredBean> convert(startsWithText predicate) {
		return new Predicates.LikePredicate(predicate.getMember(), predicate.getText() + "%");
	}

	@Override
	@SuppressWarnings({ "unchecked" })
	public Predicate<HazelcastInMemoryStorage.Key, StoredBean> convert(endsWithText predicate) {
		return new Predicates.LikePredicate(predicate.getMember(), "%" + predicate.getText());
	}

	@Override
	@SuppressWarnings({ "unchecked" })
	public Predicate<HazelcastInMemoryStorage.Key, StoredBean> convert(Criteria.isNull predicate) {
		if (def == null)
			throw new RuntimeException("SerializedDefinition not found for " + predicate);
		Class<?> type = def.getFieldType(predicate.getMember());

		Comparable<?> value;
		if (type.equals(Integer.class))
			value = new Integer(NULL_INT);
		else if (type.equals(Long.class) || type.equals(Date.class))
			value = new Long(NULL_LONG);
		else if (type.equals(Double.class))
			value = new Double(NULL_DOUBLE);
		else if (type.equals(Boolean.class))
			value = false;
		else if (type.equals(String.class))
			value = "";
		else
			throw new RuntimeException("Unsupported type " + type.getSimpleName() + " for " + predicate);

		return new Predicates.EqualPredicate(predicate.getMember(), value);
	}
}
