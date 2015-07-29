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

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Search criteria API. Does not correlate entity fields, just compares them (lvalues) to constants.<br>
 * Supports named ":xyz" parameters - see replaceValue().<br>
 * <br>
 * <b>Usage Example:</b><br>
 * {@code import static com.px100systems.data.core.Criteria.*;}<br>
 * {@code Criteria criteria = and(eq("textField", "something"), or(lt("dateField", new Date()), in("intField", 1L, 2L, 3L)));}<br>
 * <br>
 * Null handling caveats: depending on the implementation (e.g. cqengine) nulls match against gt/lt and possibly other queries.
 * Make sure to explicitly check for not null, especially with Dates.<br>
 * 
 * @version 0.3 <br>Copyright (c) 2015 Px100 Systems. All Rights Reserved.<br>
 * @author Alex Rogachevsky
*/
public abstract class Criteria {
	/**
	 * Null instance of Date whenever needed
	 */
	public static final Date NULL_DATE = new Date(0L);

	/**
	 * Converts criteria to the native provider form. Implemented internally by storage providers.
	 * @param <T> provider-specific criteria: Hazelcast predicates, Mongo filters, or Ignite SQL strings.
	 */
	public interface Converter<T> {
		T convert(and c);
		T convert(or c);
		T convert(not c);
		T convert(eq c);
		T convert(ne c);
		T convert(lt c);
		T convert(le c);
		T convert(gt c);
		T convert(ge c);
		T convert(between c);
		T convert(in c);
		T convert(containsText c);
		T convert(icontainsText c);
		T convert(startsWithText c);
		T convert(endsWithText c);
		T convert(isNull c);
	}
	
	public abstract <T> T convert(Converter<T> converter);

	public abstract List<String> fields();

	public abstract Criteria copy();

	/**
	 * Parameters substitution for named parameters
	 * @param replacements param name to replacement map
	 */
	public abstract void replaceValues(Map<String, Object> replacements);

	/**
	 * And criteria
	 * @param members and predicates
	 * @return the And criteria
	 */
	public static and and(Criteria... members) {
		return new and(members);
	}
	public static class and extends Criteria {
		private Criteria[] members;

		public and(Criteria[] members) {
			this.members = members;
		}

		public Criteria[] getMembers() {
			return members;
		}

		@Override
		public <T> T convert(Converter<T> converter) {
			return converter.convert(this);
		}

		@Override
		public List<String> fields() {
			List<String> result = new ArrayList<>();
			if (members != null)
				for (Criteria c : members)
					result.addAll(c.fields());
			return result;
		}

		@Override
		public Criteria copy() {
			List<Criteria> list = new ArrayList<>();
			if (members != null)
				for (Criteria c : members)
					list.add(c.copy());

			return new and(list.toArray(new Criteria[list.size()]));
		}

		@Override
		public void replaceValues(Map<String, Object> replacements) {
			if (members != null)
				for (Criteria c : members)
					c.replaceValues(replacements);
		}

		@Override
		public String toString() {
			StringBuilder result = new StringBuilder();
			for (Criteria c : members) {
				if (result.length() > 0)
					result.append(", ");
				result.append(c.toString());
			}
			return "and(" + result + ")";
		}
	}

	/**
	 * Or criteria
	 * @param members or predicates
	 * @return the Or criteria
	 */
	public static or or(Criteria... members) {
		return new or(members);
	}
	public static class or extends Criteria {
		private Criteria[] members;

		public or(Criteria[] members) {
			this.members = members;
		}

		public Criteria[] getMembers() {
			return members;
		}

		@Override
		public <T> T convert(Converter<T> converter) {
			return converter.convert(this);
		}

		@Override
		public List<String> fields() {
			List<String> result = new ArrayList<>();
			if (members != null)
				for (Criteria c : members)
					result.addAll(c.fields());
			return result;
		}

		@Override
		public Criteria copy() {
			List<Criteria> list = new ArrayList<>();
			if (members != null)
				for (Criteria c : members)
					list.add(c.copy());

			return new or(list.toArray(new Criteria[list.size()]));
		}

		@Override
		public void replaceValues(Map<String, Object> replacements) {
			if (members != null)
				for (Criteria c : members)
					c.replaceValues(replacements);
		}

		@Override
		public String toString() {
			StringBuilder result = new StringBuilder();
			for (Criteria c : members) {
				if (result.length() > 0)
					result.append(", ");
				result.append(c.toString());
			}
			return "or(" + result + ")";
		}
	}

	/**
	 * Not criteria
	 * @param member the predicate to negate
	 * @return the Not criteria
	 */
	public static not not(Criteria member) {
		return new not(member);
	}
	public static class not extends Criteria {
		private Criteria member;

		public not(Criteria member) {
			this.member = member;
		}

		public Criteria getMember() {
			return member;
		}

		@Override
		public <T> T convert(Converter<T> converter) {
			return converter.convert(this);
		}

		@Override
		public List<String> fields() {
			return (member == null) ? new ArrayList<>() : member.fields();
		}

		@Override
		public Criteria copy() {
			return new not(member.copy());
		}

		@Override
		public void replaceValues(Map<String, Object> replacements) {
			if (member != null)
				member.replaceValues(replacements);
		}

		@Override
		public String toString() {
			return "not(" + member + ")";
		}
	}
	
	public static abstract class MemberCriteria extends Criteria {
		protected String member;
		
		public String getMember() {
			return member;
		}

		@Override
		public List<String> fields() {
			List<String> result =new ArrayList<>();
			if (member != null)
				result.add(member);
			return result;
		}
	}
	
	public static abstract class MemberValueCriteria extends MemberCriteria {
		protected Comparable<?> value;
		
		public Comparable<?> getValue() {
			return value;
		}

		public void setValue(Comparable<?> value) {
			this.value = value;
		}

		@Override
		public void replaceValues(Map<String, Object> replacements) {
			if (value != null && (value instanceof String)) {
				Comparable<?> replacement = (Comparable)replacements.get(value);
				if (replacement != null)
					value = replacement;
			}
		}
	}

	/**
	 * Equals criteria
	 * @param member field name
	 * @param value field value - can be null for Ignite. but generally prefer isNull() criteria. Careful with nulls in Hazelcast.
	 * @return teh Eq criteria
	 */
	public static eq eq(String member, Comparable<?> value) {
		return new eq(member, value);
	}
	public static class eq extends MemberValueCriteria {
		public eq(String member, Comparable<?> value) {
			this.member = member;
			this.value = value;
		}

		@Override
		public <T> T convert(Converter<T> converter) {
			return converter.convert(this);
		}

		@Override
		public Criteria copy() {
			return new eq(member, value);
		}

		@Override
		public String toString() {
			return "eq(" + member + ", " + value + ")";
		}
	}

	/**
	 * Not Equals criteria
	 * @param member field name
	 * @param value field value - can be null for Ignite. but generally prefer not(isNull()) criteria. Careful with nulls in Hazelcast.
	 * @return teh Ne criteria
	 */
	public static ne ne(String member, Comparable<?> value) {
		return new ne(member, value);
	}
	public static class ne extends MemberValueCriteria {
		public ne(String member, Comparable<?> value) {
			this.member = member;
			this.value = value;
		}

		@Override
		public <T> T convert(Converter<T> converter) {
			return converter.convert(this);
		}

		@Override
		public Criteria copy() {
			return new ne(member, value);
		}

		@Override
		public String toString() {
			return "ne(" + member + ", " + value + ")";
		}
	}

	/**
	 * Greater than criteria
	 * @param member field name
	 * @param value field value
	 * @return Gt criteria
	 */
	public static gt gt(String member, Comparable<?> value) {
		return new gt(member, value);
	}
	public static class gt extends MemberValueCriteria {
		public gt(String member, Comparable<?> value) {
			this.member = member;
			this.value = value;
		}

		@Override
		public <T> T convert(Converter<T> converter) {
			return converter.convert(this);
		}

		@Override
		public Criteria copy() {
			return new gt(member, value);
		}

		@Override
		public String toString() {
			return "gt(" + member + ", " + value + ")";
		}
	}

	/**
	 * Greater than or equals criteria
	 * @param member field name
	 * @param value field value
	 * @return Ge criteria
	 */
	public static ge ge(String member, Comparable<?> value) {
		return new ge(member, value);
	}
	public static class ge extends MemberValueCriteria {
		public ge(String member, Comparable<?> value) {
			this.member = member;
			this.value = value;
		}

		@Override
		public <T> T convert(Converter<T> converter) {
			return converter.convert(this);
		}

		@Override
		public Criteria copy() {
			return new ge(member, value);
		}

		@Override
		public String toString() {
			return "ge(" + member + ", " + value + ")";
		}
	}

	/**
	 * Less than criteria
	 * @param member field name
	 * @param value field value
	 * @return Lt criteria
	 */
	public static lt lt(String member, Comparable<?> value) {
		return new lt(member, value);
	}
	public static class lt extends MemberValueCriteria {
		public lt(String member, Comparable<?> value) {
			this.member = member;
			this.value = value;
		}

		@Override
		public <T> T convert(Converter<T> converter) {
			return converter.convert(this);
		}

		@Override
		public Criteria copy() {
			return new lt(member, value);
		}

		@Override
		public String toString() {
			return "lt(" + member + ", " + value + ")";
		}
	}

	/**
	 * Less than or equals criteria
	 * @param member field name
	 * @param value field value
	 * @return Le criteria
	 */
	public static le le(String member, Comparable<?> value) {
		return new le(member, value);
	}
	public static class le extends MemberValueCriteria {
		public le(String member, Comparable<?> value) {
			this.member = member;
			this.value = value;
		}

		@Override
		public <T> T convert(Converter<T> converter) {
			return converter.convert(this);
		}

		@Override
		public Criteria copy() {
			return new le(member, value);
		}

		@Override
		public String toString() {
			return "le(" + member + ", " + value + ")";
		}
	}

	/**
	 * Between criteria
	 * @param member field name
	 * @param min minimum field value
	 * @param max maximum field value
	 * @return Between criteria
	 */
	public static between between(String member, Comparable<?> min, Comparable<?> max) {
		return new between(member, min, max);
	}
	public static class between extends MemberCriteria {
		private Comparable<?> min;
		private Comparable<?> max;

		public between(String member, Comparable<?> min, Comparable<?> max) {
			this.member = member;
			this.min = min;
			this.max = max;
		}

		public Comparable<?> getMin() {
			return min;
		}

		public Comparable<?> getMax() {
			return max;
		}

		@Override
		public <T> T convert(Converter<T> converter) {
			return converter.convert(this);
		}

		@Override
		public Criteria copy() {
			return new between(member, min, max);
		}

		@Override
		public void replaceValues(Map<String, Object> replacements) {
			if (min != null && (min instanceof String)) {
				Comparable<?> replacement = (Comparable)replacements.get(min);
				if (replacement != null)
					min = replacement;
			}

			if (max != null && (max instanceof String)) {
				Comparable<?> replacement = (Comparable)replacements.get(max);
				if (replacement != null)
					max = replacement;
			}
		}

		@Override
		public String toString() {
			return "between(" + member + ", " + min + ", " + max + ")";
		}
	}

	/**
	 * In criteria
	 * @param member field name
	 * @param list list of values to comopare against
	 * @return the In criteria
	 */
	public static in in(String member, Comparable<?>... list) {
		return new in(member, list);
	}
	public static class in extends MemberCriteria {
		private Comparable<?>[] list;

		public in(String member, Comparable<?>... list) {
			this.member = member;
			this.list = list;
		}

		public Comparable<?>[] getList() {
			return list;
		}

		@Override
		public <T> T convert(Converter<T> converter) {
			return converter.convert(this);
		}

		@Override
		public Criteria copy() {
			return new in(member, list.clone());
		}

		@Override
		public void replaceValues(Map<String, Object> replacements) {
			for (int i = 0; i < list.length; i++)
				if (list[i] != null && (list[i] instanceof String)) {
					Comparable<?> replacement = (Comparable)replacements.get(list[i].toString());
					if (replacement != null)
						list[i] = replacement;
				}
		}

		@Override
		public String toString() {
			StringBuilder result = new StringBuilder();
			for (Object c : list) {
				if (result.length() > 0)
					result.append(", ");
				result.append(c.toString());
			}
			return "in(" + member + ", [" + result.toString() + "])";
		}
	}

	/**
	 * Like criteria to search for : '%pattern%'
	 * @param member field name
	 * @param text field text pattern to search for
	 * @return the criteria
	 */
	public static containsText containsText(String member, String text) {
		return new containsText(member, text);
	}
	public static class containsText extends MemberCriteria {
		private String text;

		public containsText(String member, String text) {
			this.member = member;
			this.text = text;
		}

		public String getText() {
			return text;
		}

		@Override
		public <T> T convert(Converter<T> converter) {
			return converter.convert(this);
		}

		@Override
		public Criteria copy() {
			return new containsText(member, text);
		}

		@Override
		public void replaceValues(Map<String, Object> replacements) {
			if (text != null) {
				Object replacement = replacements.get(text);
				if (replacement != null)
					text = replacement.toString();
			}
		}

		@Override
		public String toString() {
			return "containsText(" + member + ", \"" + text + "\")";
		}
	}

	/**
	 * Case-insensitive Like criteria to search for : '%pattern%'
	 * @param member field name
	 * @param text field text pattern to search for
	 * @return the criteria
	 */
	public static icontainsText icontainsText(String member, String text) {
		return new icontainsText(member, text);
	}
	public static class icontainsText extends MemberCriteria {
		private String text;

		public icontainsText(String member, String text) {
			this.member = member;
			this.text = text;
		}

		public String getText() {
			return text;
		}

		@Override
		public <T> T convert(Converter<T> converter) {
			return converter.convert(this);
		}

		@Override
		public Criteria copy() {
			return new icontainsText(member, text);
		}

		@Override
		public void replaceValues(Map<String, Object> replacements) {
			if (text != null) {
				Object replacement = replacements.get(text);
				if (replacement != null)
					text = replacement.toString();
			}
		}

		@Override
		public String toString() {
			return "icontainsText(" + member + ", \"" + text + "\")";
		}
	}

	/**
	 * Like criteria to search for : 'pattern%'
	 * @param member field name
	 * @param text field text pattern to search for
	 * @return the criteria
	 */
	@SuppressWarnings("unused")
	public static startsWithText startsWithText(String member, String text) {
		return new startsWithText(member, text);
	}
	public static class startsWithText extends MemberCriteria {
		private String text;

		public startsWithText(String member, String text) {
			this.member = member;
			this.text = text;
		}

		public String getText() {
			return text;
		}

		@Override
		public <T> T convert(Converter<T> converter) {
			return converter.convert(this);
		}

		@Override
		public Criteria copy() {
			return new startsWithText(member, text);
		}

		@Override
		public void replaceValues(Map<String, Object> replacements) {
			if (text != null) {
				Object replacement = replacements.get(text);
				if (replacement != null)
					text = replacement.toString();
			}
		}

		@Override
		public String toString() {
			return "startsWithText(" + member + ", \"" + text + "\")";
		}
	}

	/**
	 * Like criteria to search for : '%pattern'
	 * @param member field name
	 * @param text field text pattern to search for
	 * @return the criteria
	 */
	@SuppressWarnings("unused")
	public static endsWithText endsWithText(String member, String text) {
		return new endsWithText(member, text);
	}
	public static class endsWithText extends MemberCriteria {
		private String text;

		public endsWithText(String member, String text) {
			this.member = member;
			this.text = text;
		}

		public String getText() {
			return text;
		}

		@Override
		public <T> T convert(Converter<T> converter) {
			return converter.convert(this);
		}

		@Override
		public Criteria copy() {
			return new endsWithText(member, text);
		}

		@Override
		public void replaceValues(Map<String, Object> replacements) {
			if (text != null) {
				Object replacement = replacements.get(text);
				if (replacement != null)
					text = replacement.toString();
			}
		}

		@Override
		public String toString() {
			return "endsWithText(" + member + ", \"" + text + "\")";
		}
	}

	/**
	 * Helper IS NULL criteria
	 * @param member field name
	 * @return IsNull criteria
	 */
	@SuppressWarnings("unused")
	public static isNull isNull(String member) {
		return new isNull(member);
	}
	public static class isNull extends MemberCriteria {
		public isNull(String member) {
			this.member = member;
		}

		@Override
		public <T> T convert(Converter<T> converter) {
			return converter.convert(this);
		}

		@Override
		public Criteria copy() {
			return new isNull(member);
		}

		@Override
		public void replaceValues(Map<String, Object> replacements) {
		}

		@Override
		public String toString() {
			return "isNull(" + member + ")";
		}
	}
}
