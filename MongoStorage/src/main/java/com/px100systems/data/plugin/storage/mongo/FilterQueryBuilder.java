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

import com.mongodb.client.model.Filters;
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
import org.bson.conversions.Bson;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Criteria to Mongo Filter translator. Used internally by {@link MongoDatabaseStorage}.
 * No fancy data conversions needed, as Mongo handles all major Java data types and nulls with ease.<br>
 * 
 * @version 0.3 <br>Copyright (c) 2015 Px100 Systems. All Rights Reserved.<br>
 * @author Alex Rogachevsky
*/
public class FilterQueryBuilder implements Criteria.Converter<Bson> {
	@Override
	public Bson convert(and predicate) {
		List<Bson> q = new ArrayList<>();
		for (Criteria c: predicate.getMembers())
			q.add(c.convert(this));
		return Filters.and(q);
	}

	@Override
	public Bson convert(or predicate) {
		List<Bson> q = new ArrayList<>();
		for (Criteria c: predicate.getMembers())
			q.add(c.convert(this));
		return Filters.or(q);
	}

	@Override
	public Bson convert(not predicate) {
		return Filters.not(predicate.getMember().convert(this));
	}

	@Override
	public Bson convert(eq predicate) {
		return Filters.eq(predicate.getMember(), predicate.getValue());
	}

	@Override
	public Bson convert(ne predicate) {
		return Filters.ne(predicate.getMember(), predicate.getValue());
	}

	@Override
	public Bson convert(gt predicate) {
		return Filters.gt(predicate.getMember(), predicate.getValue());
	}

	@Override
	public Bson convert(ge predicate) {
		return Filters.gte(predicate.getMember(), predicate.getValue());
	}

	@Override
	public Bson convert(lt predicate) {
		return Filters.lt(predicate.getMember(), predicate.getValue());
	}

	@Override
	public Bson convert(le predicate) {
		return Filters.lte(predicate.getMember(), predicate.getValue());
	}

	@Override
	public Bson convert(between predicate) {
		return Filters.and(Filters.gte(predicate.getMember(), predicate.getMin()), Filters.lte(predicate.getMember(), predicate.getMax()));
	}

	@Override
	public Bson convert(in predicate) {
		return Filters.in(predicate.getMember(), predicate.getList());
	}

	@Override
	public Bson convert(containsText predicate) {
		return Filters.regex(predicate.getMember(), ".*" + Pattern.quote(predicate.getText()) + ".*");
	}

	@Override
	public Bson convert(icontainsText predicate) {
		return Filters.regex(predicate.getMember(), ".*" + Pattern.quote(predicate.getText()) + ".*", "i");
	}

	@Override
	public Bson convert(startsWithText predicate) {
		return Filters.regex(predicate.getMember(), Pattern.quote(predicate.getText()) + ".*");
	}

	@Override
	public Bson convert(endsWithText predicate) {
		return Filters.regex(predicate.getMember(), ".*" + Pattern.quote(predicate.getText()));
	}

	@Override
	public Bson convert(Criteria.isNull predicate) {
		return Filters.exists(predicate.getMember(), false);
	}
}
