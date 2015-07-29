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
package com.px100systems.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;

/**
 * Generic if/then rule.
 * 
 * @version 0.3 <br>Copyright (c) 2015 Px100 Systems. All Rights Reserved.<br>
 * @author Alex Rogachevsky
 */
public class RuleEngine {
	private List<Expression> rules; // returns first not-null result
	
	public static class Context {
		private Map<String, Object> args;

		public Context(Map<String, Object> args) {
			this.args = args;
		}

		public Map<String, Object> getArgs() {
			return args;
		}
	}

	/**
	 * Set the list of rules. teh first one returning non-null, wins.
	 * @param rules a list of Spring EL expressions typically "condition ? result : laternateResult" ones
	 */
	@Required
    public void setRules(List<String> rules) {
		this.rules = new ArrayList<Expression>(); 
		
		SpelExpressionParser parser = new SpelExpressionParser();
		for (String s : rules)
			this.rules.add(parser.parseExpression(s));
    }

	/**
	 * Evaluate the rules on the given args
	 * @param args arguments, accessed normally as "xxx" in expressions
	 * @param defaultValue default value if all expressions returned null
	 * @return the first not null expression result or defaultValue
	 */
	public Object eval(Map<String, Object> args, Object defaultValue) {
		SpringELCtx ctx = new SpringELCtx(new Context(args)); 
		
		for (Expression rule : rules) {
			Object result = rule.getValue(ctx);
			if (result != null)
				return result;
		}
			
		return defaultValue;
	}
}

