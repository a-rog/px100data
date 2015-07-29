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

import com.px100systems.util.serialization.SerializationDefinition;
import org.springframework.util.ObjectUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Criteria verifier - evaluates the criteria on a bean. See {@link Criteria} for details.
 *
 * @version 0.3 <br>Copyright (c) 2015 Px100 Systems. All Rights Reserved.<br>
 * @author Alex Rogachevsky
 */
public class CalculatingCriteria implements Criteria.Converter<CalculatingCriteria.Predicate> {
	private Object bean;
	private SerializationDefinition def;

	public CalculatingCriteria(Object bean) {
		this.bean = bean;
		def = SerializationDefinition.get(bean.getClass());
		if (def == null)
			throw new RuntimeException("SerializationDefinition not found for " + bean.getClass().getSimpleName());
	}

	public boolean eval(Criteria c) {
		return c.convert(this).eval();
	}

	interface Predicate {
		boolean eval();
	}

	@Override
	public CalculatingCriteria.Predicate convert(final Criteria.and c) {
		return new Predicate() {
			private List<CalculatingCriteria.Predicate> predicates = parse();

			private List<CalculatingCriteria.Predicate> parse() {
				List<CalculatingCriteria.Predicate> result = new ArrayList<>();
				for (Criteria criterion : c.getMembers())
					result.add(criterion.convert(CalculatingCriteria.this));
				return result;
			}

			@Override
			public boolean eval() {
				for (Predicate p : predicates)
					if (!p.eval())
						return false;
				return true;
			}
		};
	}

	@Override
	public CalculatingCriteria.Predicate convert(Criteria.or c) {
		return new Predicate() {
			private List<CalculatingCriteria.Predicate> predicates = parse();

			private List<CalculatingCriteria.Predicate> parse() {
				List<CalculatingCriteria.Predicate> result = new ArrayList<>();
				for (Criteria criterion : c.getMembers())
					result.add(criterion.convert(CalculatingCriteria.this));
				return result;
			}

			@Override
			public boolean eval() {
				for (Predicate p : predicates)
					if (p.eval())
						return true;
				return false;
			}
		};
	}

	@Override
	public CalculatingCriteria.Predicate convert(Criteria.not c) {
		return new Predicate() {
			private CalculatingCriteria.Predicate predicate = c.getMember().convert(CalculatingCriteria.this);

			@Override
			public boolean eval() {
				return !predicate.eval();
			}
		};
	}

	@Override
	public CalculatingCriteria.Predicate convert(Criteria.eq c) {
		return new Predicate() {
			@Override
			public boolean eval() {
				return ObjectUtils.nullSafeEquals(def.getField(bean, c.getMember()), c.getValue());
			}
		};
	}

	@Override
	public CalculatingCriteria.Predicate convert(Criteria.ne c) {
		return new Predicate() {
			@Override
			public boolean eval() {
				return !ObjectUtils.nullSafeEquals(def.getField(bean, c.getMember()), c.getValue());
			}
		};
	}

	@Override
	public CalculatingCriteria.Predicate convert(Criteria.lt c) {
		return new Predicate() {
			@SuppressWarnings("unchecked")
			@Override
			public boolean eval() {
				Comparable val = (Comparable)def.getField(bean, c.getMember());
				return val == null || val.compareTo(c.getValue()) < 0;
			}
		};
	}

	@Override
	public CalculatingCriteria.Predicate convert(Criteria.le c) {
		return new Predicate() {
			@SuppressWarnings("unchecked")
			@Override
			public boolean eval() {
				Comparable val = (Comparable)def.getField(bean, c.getMember());
				return val == null || val.compareTo(c.getValue()) <= 0;
			}
		};
	}

	@Override
	public CalculatingCriteria.Predicate convert(Criteria.gt c) {
		return new Predicate() {
			@SuppressWarnings("unchecked")
			@Override
			public boolean eval() {
				Comparable val = (Comparable)def.getField(bean, c.getMember());
				return val != null && val.compareTo(c.getValue()) > 0;
			}
		};
	}

	@Override
	public CalculatingCriteria.Predicate convert(Criteria.ge c) {
		return new Predicate() {
			@SuppressWarnings("unchecked")
			@Override
			public boolean eval() {
				Comparable val = (Comparable)def.getField(bean, c.getMember());
				return val != null && val.compareTo(c.getValue()) >= 0;
			}
		};
	}

	@Override
	public CalculatingCriteria.Predicate convert(Criteria.between c) {
		return new Predicate() {
			@SuppressWarnings("unchecked")
			@Override
			public boolean eval() {
				Comparable val = (Comparable)def.getField(bean, c.getMember());
				return val != null && val.compareTo(c.getMin()) >= 0 && val.compareTo(c.getMax()) <= 0;
			}
		};
	}

	@Override
	public CalculatingCriteria.Predicate convert(Criteria.in c) {
		return new Predicate() {
			@Override
			public boolean eval() {
				Object val = def.getField(bean, c.getMember());
				if (val == null)
					return false;
				for (Object o : c.getList())
					if (val.equals(o))
						return true;
				return false;
			}
		};
	}

	@Override
	public CalculatingCriteria.Predicate convert(Criteria.containsText c) {
		return new Predicate() {
			@Override
			public boolean eval() {
				Object val = def.getField(bean, c.getMember());
				if (val == null)
					return false;
				String s = val.toString();
				if (s.isEmpty())
					return false;
				return s.contains(c.getText());
			}
		};
	}

	@Override
	public CalculatingCriteria.Predicate convert(Criteria.icontainsText c) {
		return new Predicate() {
			@Override
			public boolean eval() {
				Object val = def.getField(bean, c.getMember());
				if (val == null)
					return false;
				String s = val.toString();
				if (s.isEmpty())
					return false;
				return s.toUpperCase().contains(c.getText().toUpperCase());
			}
		};
	}

	@Override
	public CalculatingCriteria.Predicate convert(Criteria.startsWithText c) {
		return new Predicate() {
			@Override
			public boolean eval() {
				Object val = def.getField(bean, c.getMember());
				if (val == null)
					return false;
				String s = val.toString();
				if (s.isEmpty())
					return false;
				return s.startsWith(c.getText());
			}
		};
	}

	@Override
	public CalculatingCriteria.Predicate convert(Criteria.endsWithText c) {
		return new Predicate() {
			@Override
			public boolean eval() {
				Object val = def.getField(bean, c.getMember());
				if (val == null)
					return false;
				String s = val.toString();
				if (s.isEmpty())
					return false;
				return s.endsWith(c.getText());
			}
		};
	}

	@Override
	public Predicate convert(Criteria.isNull c) {
		return new Predicate() {
			@Override
			public boolean eval() {
				return def.getField(bean, c.getMember()) == null;
			}
		};
	}
}
