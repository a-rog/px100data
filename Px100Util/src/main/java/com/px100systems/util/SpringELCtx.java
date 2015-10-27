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

import java.text.DecimalFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import java.util.Calendar;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Unified Spring EL context with all supplemental static functions
 * 
 * @version 0.3 <br>Copyright (c) 2015 Px100 Systems. All Rights Reserved.<br>
 * @author Alex Rogachevsky
 */
@SuppressWarnings("unused")
public class SpringELCtx extends StandardEvaluationContext {
	private final static long millisecondsInDay = 1000 * 60 * 60 * 24; 
	
	public SpringELCtx(Object rootObject) {
		super(rootObject);
		try {
			registerFunction("adjustedMidnight", SpringELCtx.class.getDeclaredMethod("adjustedMidnight", Date.class, Integer.class));
			registerFunction("printCollection", SpringELCtx.class.getDeclaredMethod("printCollection", Object.class, String.class));
			registerFunction("firstElement", SpringELCtx.class.getDeclaredMethod("firstElement", Object.class));
			registerFunction("element", SpringELCtx.class.getDeclaredMethod("element", Object.class, Integer.class));
			registerFunction("average", SpringELCtx.class.getDeclaredMethod("average", Object.class));
			registerFunction("sum", SpringELCtx.class.getDeclaredMethod("sum", Object.class));
			registerFunction("formatTime", SpringELCtx.class.getDeclaredMethod("formatTime", Date.class));
			registerFunction("formatDateTime", SpringELCtx.class.getDeclaredMethod("formatDateTime", Date.class));
			registerFunction("parseDateTime", SpringELCtx.class.getDeclaredMethod("parseDateTime", String.class));
			registerFunction("formatDate", SpringELCtx.class.getDeclaredMethod("formatDate", Date.class));
			registerFunction("parseDate", SpringELCtx.class.getDeclaredMethod("parseDate", String.class));
			registerFunction("formatNumber", SpringELCtx.class.getDeclaredMethod("formatNumber", Double.class, String.class));
			registerFunction("address", SpringELCtx.class.getDeclaredMethod("address", String.class, String.class, String.class, String.class));
			registerFunction("formatPhone", SpringELCtx.class.getDeclaredMethod("formatPhone", String.class));
			registerFunction("dateArithmetic", SpringELCtx.class.getDeclaredMethod("dateArithmetic", Date.class,String.class));
			registerFunction("periodStart", SpringELCtx.class.getDeclaredMethod("periodStart", Date.class,String.class));
			registerFunction("periodEnd", SpringELCtx.class.getDeclaredMethod("periodEnd", Date.class,String.class));
			registerFunction("dateWithin", SpringELCtx.class.getDeclaredMethod("dateWithin", Date.class,Date.class,Date.class));
			registerFunction("map", SpringELCtx.class.getDeclaredMethod("map", Object[].class));
			registerFunction("firstName", SpringELCtx.class.getDeclaredMethod("firstName", String.class));
			registerFunction("printableCamelCase", SpringELCtx.class.getDeclaredMethod("printableCamelCase", String.class));
			registerFunction("printObject", SpringELCtx.class.getDeclaredMethod("printObject", Object.class));
			registerFunction("find", SpringELCtx.class.getDeclaredMethod("find", String.class, String.class));
			registerFunction("localTime", SpringELCtx.class.getDeclaredMethod("localTime", Integer.class));
			registerFunction("toLocalTime", SpringELCtx.class.getDeclaredMethod("toLocalTime", Date.class,Integer.class));
			registerFunction("toServerTime", SpringELCtx.class.getDeclaredMethod("toServerTime", Date.class,Integer.class));
			registerFunction("serverTime", SpringELCtx.class.getDeclaredMethod("serverTime"));
			registerFunction("year", SpringELCtx.class.getDeclaredMethod("year", Date.class));
			registerFunction("isum", SpringELCtx.class.getDeclaredMethod("isum", String.class, String.class, Iterator.class));
			registerFunction("iaverage", SpringELCtx.class.getDeclaredMethod("iaverage", String.class, String.class, Iterator.class));
			registerFunction("imin", SpringELCtx.class.getDeclaredMethod("imin", String.class, String.class, Iterator.class));
			registerFunction("imax", SpringELCtx.class.getDeclaredMethod("imax", String.class, String.class, Iterator.class));
			registerFunction("icount", SpringELCtx.class.getDeclaredMethod("icount", String.class, Iterator.class));
			registerFunction("iset", SpringELCtx.class.getDeclaredMethod("iset", String.class, Iterator.class));
			registerFunction("ilist", SpringELCtx.class.getDeclaredMethod("ilist", String.class, Iterator.class));
			registerFunction("i2list", SpringELCtx.class.getDeclaredMethod("i2list", Iterator.class));
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Returns adjusted midnight time
	 * @param date time to adjust
	 * @param dayOffset add or subtract days
	 * @return the adjusted time
	 */
	public static long adjustedMidnight(Date date, Integer dayOffset) {
		Calendar cal = Calendar.getInstance();
		cal.setTime(date);
		cal.set(Calendar.HOUR_OF_DAY, 0);
		cal.set(Calendar.MINUTE, 0);
		cal.set(Calendar.SECOND, 0);
		cal.set(Calendar.MILLISECOND, 0);
		return cal.getTimeInMillis() + (millisecondsInDay * dayOffset);
	}

	/**
	 * Print any collection or array
	 * @param collection anything that's iterable
	 * @param separator element separator
	 * @return text
	 */
	@SuppressWarnings("unchecked")
	public static String printCollection(Object collection, String separator) {
		if (collection == null)
			return "";

		if (collection instanceof Object[])
			return printCollection(Arrays.asList((Object[]) collection), separator);

		if (!(collection instanceof Collection))
			return collection.toString();

		StringBuilder result = new StringBuilder();
		for (Object o : (Collection<Object>) collection) {
			if (o != null) {
				String string = o.toString().trim();
				if (result.length() != 0  && string.length() != 0) {
					result.append(separator);
					result.append(" ");
				}
				result.append(string);
			}
		}
		return result.toString();
	}

	/**
	 * First element of anything iterable including arrays
	 * @param list the list
	 * @return the first element or null
	 */
	@SuppressWarnings("unchecked")
	public static Object firstElement(Object list) {
		if (list == null)
			return null;
		
		if (list instanceof Iterator) {
			Iterator<Object> l = (Iterator<Object>)list;
			return l.hasNext() ? l.next() : null;
		}

		if (list instanceof Collection) {
			Collection<Object> l = (Collection<Object>)list;
			if (l.isEmpty())
				return null;
			
			return firstElement(l.iterator().next());
		}
		
		if (list instanceof Object[]) {
			Object[] l = (Object[])list;
			if (l.length == 0)
				return null;

			return firstElement(l[0]);
		}
			
		return list;
	}

	/**
	 * Return an average treating the list as logical numbers even though it can contain strings
	 * @param list the list
	 * @return the average
	 */
	@SuppressWarnings("unchecked")
	public static Double average(Object list) {
		Double sum = sum(list);
		if (sum == 0.0)
			return sum;
		
		if (list instanceof Object[]) 
			return sum / ((Object[])list).length;
		
		if (list instanceof Collection)
			return sum / ((Collection<Object>)list).size();

		return sum;
	}

	/**
	 * Return a sum treating the list as logical numbers even though it can contain strings
	 * @param list the list
	 * @return the average
	 */
	@SuppressWarnings("unchecked")
	public static Double sum(Object list) {
		if (list == null)
			return 0.0;
		
		if (list instanceof Object[]) 
			return sum(Arrays.asList((Object[])list));
		
		if (list instanceof Collection) {
			Double sum = 0.0;
			for (Object o : (Collection<Object>)list)
				sum += toDouble(firstElement(o));
			return sum;
		}
			
		return toDouble(list);
	}
	
	private static Double toDouble(Object o) {
		if (o == null)
			return 0.0;
		
		if (o instanceof Double)
			return (Double)o;
		
		if (o instanceof Integer)
			return ((Integer)o).doubleValue();

		if (o instanceof Long)
			return ((Long)o).doubleValue();
		
		return new Double(o.toString());
	}

	/**
	 * Nth element of anything iterable including arrays
	 * @param listOrArray the list
	 * @param index index
	 * @return the first element or null
	 */
	@SuppressWarnings({ "unchecked" })
	public static Object element(Object listOrArray, Integer index) {
		if (listOrArray == null)
			return null;
		
		if (listOrArray instanceof Collection) {
			Collection<Object> l = (Collection<Object>)listOrArray;
			if (l.size() <= index)
				return null;
			
			for (Iterator<Object> i = l.iterator(); i.hasNext(); index--) {
				Object o = i.next();
				if (index == 0)
					return o; 
			}
		}
		
		if (listOrArray instanceof Object[]) {
			Object[] l = (Object[])listOrArray;
			if (l.length <= index)
				return null;

			return l[index];
		}
			
		return null;
	}

	private final static String DATE_FORMAT = "MM/dd/yyyy"; // define in the Spring config later
	public static String formatDate(Date date) {
		return date != null ? new SimpleDateFormat(DATE_FORMAT).format(date) : "";	
	}
	public static Date parseDate(String date) {
		try {
			return date != null ? new SimpleDateFormat(DATE_FORMAT).parse(date) : null;
		} catch (ParseException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Format number
	 * @param number number
	 * @param format format
	 * @return formmated number
	 */
	public static String formatNumber(Double number, String format) {
		return number != null ? (number != 0d ? new DecimalFormat(format).format(number) : 0 + "") : "";
	}

	private final static String TIME_FORMAT = "hh:mm:ss a"; // define in the Spring config later
	public static String formatTime(Date date) {
		return date != null ? new SimpleDateFormat(TIME_FORMAT).format(date) : "";	
	}
	
	public static String formatDateTime(Date date) {
		return formatDate(date) + " " + formatTime(date);  	
	}
	public static Date parseDateTime(String date) {
		try {
			return date != null ? new SimpleDateFormat(DATE_FORMAT + " " + TIME_FORMAT).parse(date) : null;
		} catch (ParseException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Format American address
	 */
	public static String address(String street, String city, String state, String zip) {
		StringBuilder result = new StringBuilder();

		if (street != null)
			result.append(street);
		
		if (city != null) {
			if (result.length() > 0)
				result.append(" ");
			result.append(city);
		}

		if (state != null) {
			if (result.length() > 0)
				result.append(", ");
			result.append(state);
		}

		if (zip != null) {
			if (result.length() > 0)
				result.append(" ");
			result.append(zip);
		}

		return result.toString();
	}
	
	private static final int PHONE_DASH_POS = 3;
	private static final int LOCAL_PHONE_LENGTH = 7;

	/**
	 * Format American phone as (xxx) yyy-zzzz
	 */
	public static String formatPhone(String phone) {
		if (phone == null)
			return null;
		
		StringBuilder ph = new StringBuilder();
		for (char ch : phone.toCharArray())
			if (Character.isDigit(ch))
				ph.append(ch);
		phone = ph.toString();
		
		if (phone.length() < LOCAL_PHONE_LENGTH)
			return phone;
		
		if (phone.length() == LOCAL_PHONE_LENGTH)
			return phone.substring(0, PHONE_DASH_POS) + "-" + phone.substring(PHONE_DASH_POS);
		
		int localPos = phone.length() - LOCAL_PHONE_LENGTH; 
		return "(" + phone.substring(0, localPos) + ") " + formatPhone(phone.substring(localPos));
	}

	/**
	 * Date arithmetic.
	 * @param date time
	 * @param arg add/subtract value: "+/-" + "m/s/h/d/w/M/y" e.g. "+5m" or "-3d"
	 * @return the adjusted time
	 */
	public static Date dateArithmetic(Date date, String arg) {
		if (date == null)
			return null;

		Calendar cal = Calendar.getInstance();
		cal.setTime(date);
		
		int amount = Integer.parseInt(arg.substring(1, arg.length() -1));
		if (arg.startsWith("-"))
			amount = -amount;

		int field;
		if (arg.endsWith("h"))
			field = Calendar.HOUR_OF_DAY;
		else if (arg.endsWith("m"))
			field = Calendar.MINUTE;
		else if (arg.endsWith("s"))
			field = Calendar.SECOND;
		else if (arg.endsWith("d"))
			field = Calendar.DAY_OF_MONTH;
		else if (arg.endsWith("M"))
			field = Calendar.MONTH;
		else if (arg.endsWith("y"))
			field = Calendar.YEAR;
		else if (arg.endsWith("w"))
			field = Calendar.WEEK_OF_YEAR;
		else
			return date;
		
		cal.add(field, amount);
		return cal.getTime();
	}

	/**
	 * Check if the time is within period treating start and end as days
	 * @param date time
	 * @param start period start (start of the day)
	 * @param end period end (end of teh day)
	 */
	public static boolean dateWithin(Date date, Date start, Date end) {
		long d = adjustedMidnight(date, 0);
		return d >= adjustedMidnight(start, 0) && d <= adjustedMidnight(end, 0); 
	}

	/**
	 * Period start
	 * @param date time
	 * @param arg d/w/M/y
	 * @return adjusted time
	 */
	public static Date periodStart(Date date, String arg) {
		if (date == null)
			return null;

		Calendar cal = Calendar.getInstance();
		cal.setTime(date);
		cal.set(Calendar.HOUR_OF_DAY, 0);
		cal.set(Calendar.MINUTE, 0);
		cal.set(Calendar.SECOND, 0);
		cal.set(Calendar.MILLISECOND, 0);
		
		if (arg.endsWith("d")) 
			return cal.getTime();
		else if (arg.endsWith("M")) {
			cal.set(Calendar.DAY_OF_MONTH, 1);
			return cal.getTime();
		} else if (arg.endsWith("y")) {
			cal.set(Calendar.DAY_OF_YEAR, 1);
			return cal.getTime();
		} else if (arg.endsWith("w")) {
			cal.set(Calendar.DAY_OF_WEEK, cal.getFirstDayOfWeek());
			return cal.getTime();
		}
		
		return date;
	}

	/**
	 * Period end
	 * @param date time
	 * @param arg d/w/M/y
	 * @return adjusted time
	 */
	public static Date periodEnd(Date date, String arg) {
		if (date == null)
			return null;

		return periodStart(dateArithmetic(date, "+1" + arg), arg);
	}

	/**
	 * Create a String to T map
	 * @param values map entries as a list e.g. {"key1", new Date(), "key2", date2, ...} etc.
	 * @param <T> value class
	 * @return the map
	 */
	@SuppressWarnings("unchecked")
	public static <T> LinkedHashMap<String, T> map(Object... values) {
		LinkedHashMap<String, T> result = new LinkedHashMap<String, T>();
		for (int i = 0; i < values.length; i += 2) 
			result.put(values[i].toString(), (i + 1) < values.length ? (T)values[i + 1] : null);
		return result;
	}

	/**
	 * Person's first name helper
	 * @param name full name
	 * @return first name
	 */
	public static String firstName(String name) {
		if (name == null)
			return null;
		
		int commaIndex = name.indexOf(",");
		
		if (commaIndex != -1)
			return name.substring(commaIndex + 1).split(" ")[0];
		
		return name.split(" ")[0];
	}

	/**
	 * Convert to camel-case
	 * @param name text
	 * @return calmel-case text
	 */
	public static String printableCamelCase(String name) {
		if (name == null || name.trim().isEmpty())
			return "";
		
		StringBuilder result = new StringBuilder(name.substring(0, 1).toUpperCase());
		for (char a : name.substring(1).toCharArray()) {
			if (Character.isUpperCase(a))
				result.append(' ');
			result.append(a);
		}
		
		return result.toString();
	}

	/**
	 * Print any object: museful in toString()
	 * @param o object to print
	 * @return text
	 */
	public static String printObject(Object o) {
		if (o == null)
			return "";
		
		if ((o instanceof String) || (o instanceof Integer) || (o instanceof Long))
			return o.toString();
		
		if (o instanceof Double)
			return formatNumber((Double)o, "###,###.00"); 
		if (o instanceof Float)
			return formatNumber(new Double((Float)o), "###,###.00"); 
			
		if (o instanceof Boolean)
			return (Boolean)o ? "Y" : "N";
		
		if (o instanceof Date) {
			Date date = (Date)o;
			Calendar cal = Calendar.getInstance();
			cal.setTime(date);
			if (cal.get(Calendar.HOUR_OF_DAY) != 0 || cal.get(Calendar.MINUTE) != 0 || cal.get(Calendar.SECOND) != 0 || cal.get(Calendar.MILLISECOND) != 0)
				return formatDateTime(date);
			else
				return formatDate(date);
		}
		
		throw new RuntimeException("Unsupported type: " + o.getClass().getName());
	}

	/**
	 * Find a substring
	 * @param s string to search
	 * @param regex regex to find
	 * @return first found substring
	 */
	public static String find(String s, String regex) {
		Matcher matcher = Pattern.compile(regex).matcher(s);
		if (matcher.find())
			return matcher.group();
		return null;
	}

	/**
	 * Timezone adjustment using teh timezone offset
	 * @param offsetInMinutes timezone offset in minutes
	 * @return current local time
	 */
	public static Date localTime(Integer offsetInMinutes) {
		return toLocalTime(new Date(), offsetInMinutes);
	}

	/**
	 * Timezone adjustment using the timezone offset
	 * @param date time
	 * @param offsetInMinutes timezone offset in minutes
	 * @return adjusted time
	 */
    public static Date toLocalTime(Date date, Integer offsetInMinutes) {
    	if (date == null || offsetInMinutes == null || offsetInMinutes == 0)
    		return date;
    	return dateArithmetic(date, "-" + offsetInMinutes + "m");
    }

	/**
	 * Timezone adjustment using the timezone offset
	 * @param date time
	 * @param offsetInMinutes timezone offset in minutes
	 * @return adjusted time
	 */
    public static Date toServerTime(Date date, Integer offsetInMinutes) {
    	if (date == null || offsetInMinutes == null || offsetInMinutes == 0)
    		return date;
    	return dateArithmetic(date, "+" + offsetInMinutes + "m");
    }

	/**
	 * Unambigous method to get the current server time
	 * @return current server time
	 */
    public static Date serverTime() {
    	return new Date();
    }

	/**
	 * Get date year
	 * @param date time
	 * @return year
	 */
    public static int year(Date date) {
		Calendar cal = GregorianCalendar.getInstance();
		cal.setTime(date);
		return cal.get(Calendar.YEAR);
    }

	/**
	 * Iterator-based sum using Spring EL
	 * @param valueExpression how to get element values to sum
	 * @param groupExpression what to group by elements by (optional)
	 * @param iterator the iterator to iterate over
	 * @return the sum
	 */
    public static Map<String, Double> isum(String valueExpression, String groupExpression, Iterator<?> iterator) {
    	Expression val = new SpelExpressionParser().parseExpression(valueExpression);
    	Expression grp = groupExpression == null ? null : new SpelExpressionParser().parseExpression(groupExpression);
    	return isum(
    		item -> new Double(val.getValue(new SpringELCtx(item)).toString()), 
    		grp == null ? null : item -> grp.getValue(new SpringELCtx(item), String.class), 
    		iterator);
    }

	/**
	 * Iterator-based sum using lambdas
	 * @param val how to get element values to sum
	 * @param grp what to group by elements by (optional)
	 * @param iterator the iterator to iterate over
	 * @return the sum
	 */
    public static Map<String, Double> isum(SimpleExtractor<Double, Object> val, SimpleExtractor<String, Object> grp, Iterator<?> iterator) {
    	Map<String, Double> result = new HashMap<String, Double>();

    	while (iterator.hasNext()) {
    		Object item = iterator.next();
    		String key = grp == null ? "" : grp.extract(item);
    		
    		Double value = result.get(key);
    		if (value == null)
    			value = 0.0;
    		value += val.extract(item);
    		result.put(key, value);
    	}
 
    	if (result.isEmpty())
    		result.put("", new Double(0.0));
    	
    	return result;
    }

	/**
	 * Iterator-based average using Spring EL
	 * @param valueExpression how to get element values to sum
	 * @param groupExpression what to group by elements by (optional)
	 * @param iterator the iterator to iterate over
	 * @return the average
	 */
    public static Map<String, Double> iaverage(String valueExpression, String groupExpression, Iterator<?> iterator) {
    	Expression val = new SpelExpressionParser().parseExpression(valueExpression);
    	Expression grp = groupExpression == null ? null : new SpelExpressionParser().parseExpression(groupExpression);
    	return iaverage(
        		item -> new Double(val.getValue(new SpringELCtx(item)).toString()), 
        		grp == null ? null : item -> grp.getValue(new SpringELCtx(item), String.class), 
        		iterator);
    }

	/**
	 * Iterator-based average using lambdas
	 * @param val how to get element values to sum
	 * @param grp what to group by elements by (optional)
	 * @param iterator the iterator to iterate over
	 * @return the average
	 */
	public static Map<String, Double> iaverage(SimpleExtractor<Double, Object> val, SimpleExtractor<String, Object> grp, Iterator<?> iterator) {
    	Map<String, Double> sum = new HashMap<String, Double>();
    	Map<String, Long> cnt = new HashMap<String, Long>();

    	while (iterator.hasNext()) {
    		Object item = iterator.next();
    		String key = grp == null ? "" : grp.extract(item);
    		
    		Double value = sum.get(key);
    		if (value == null)
    			value = 0.0;
    		value += val.extract(item);
    		sum.put(key, value);
    		
    		Long count = cnt.get(key);
    		if (count == null)
    			count = 0L;
    		count++;
    		cnt.put(key, count);
    	}
 
    	if (sum.isEmpty()) {
    		sum.put("", new Double(0.0));
    		cnt.put("", new Long(1L));
    	}
    	
    	for (Map.Entry<String, Double> e : sum.entrySet())
    		e.setValue(e.getValue() / (double)cnt.get(e.getKey()));
    		
    	return sum;
    }

	/**
	 * Iterator-based minimum using Spring EL
	 * @param valueExpression how to get element values to sum
	 * @param groupExpression what to group by elements by (optional)
	 * @param iterator the iterator to iterate over
	 * @return the minimum
	 */
    public static Map<String, Double> imin(String valueExpression, String groupExpression, Iterator<?> iterator) {
    	Expression val = new SpelExpressionParser().parseExpression(valueExpression);
    	Expression grp = groupExpression == null ? null : new SpelExpressionParser().parseExpression(groupExpression);
    	return imin(
        		item -> new Double(val.getValue(new SpringELCtx(item)).toString()), 
        		grp == null ? null : item -> grp.getValue(new SpringELCtx(item), String.class), 
        		iterator);
    }

	/**
	 * Iterator-based minimum using lambdas
	 * @param val how to get element values to sum
	 * @param grp what to group by elements by (optional)
	 * @param iterator the iterator to iterate over
	 * @return the minimum
	 */
    public static Map<String, Double> imin(SimpleExtractor<Double, Object> val, SimpleExtractor<String, Object> grp, Iterator<?> iterator) {
    	Map<String, Double> result = new HashMap<String, Double>();

    	while (iterator.hasNext()) {
    		Object item = iterator.next();
    		String key = grp == null ? "" : grp.extract(item);
    		
    		Double value = result.get(key);
    		if (value == null)
    			value = Double.MAX_VALUE;
    		
    		Double v = val.extract(item);
    		if (v < value)
    			value = v;
    		result.put(key, value);
    	}
 
    	return result;
    }

	/**
	 * Iterator-based maximum using Spring EL
	 * @param valueExpression how to get element values to sum
	 * @param groupExpression what to group by elements by (optional)
	 * @param iterator the iterator to iterate over
	 * @return the maximum
	 */
	public static Map<String, Double> imax(String valueExpression, String groupExpression, Iterator<?> iterator) {
    	Expression val = new SpelExpressionParser().parseExpression(valueExpression);
    	Expression grp = groupExpression == null ? null : new SpelExpressionParser().parseExpression(groupExpression);
    	return imax(
        		item -> new Double(val.getValue(new SpringELCtx(item)).toString()), 
        		grp == null ? null : item -> grp.getValue(new SpringELCtx(item), String.class), 
        		iterator);
    }

	/**
	 * Iterator-based maximum using lambdas
	 * @param val how to get element values to sum
	 * @param grp what to group by elements by (optional)
	 * @param iterator the iterator to iterate over
	 * @return the maximum
	 */
    public static Map<String, Double> imax(SimpleExtractor<Double, Object> val, SimpleExtractor<String, Object> grp, Iterator<?> iterator) {
    	Map<String, Double> result = new HashMap<String, Double>();

    	while (iterator.hasNext()) {
    		Object item = iterator.next();
    		String key = grp == null ? "" : grp.extract(item);
    		
    		Double value = result.get(key);
    		if (value == null)
    			value = Double.MIN_VALUE;
    		
    		Double v = val.extract(item);
    		if (v > value)
    			value = v;
    		result.put(key, value);
    	}
 
    	return result;
    }

	/**
	 * Iterator-based count using Spring EL
	 * @param groupExpression what to group by elements by (optional)
	 * @param iterator the iterator to iterate over
	 * @return the count
	 */
    public static Map<String, Long> icount(String groupExpression, Iterator<?> iterator) {
    	Expression grp = groupExpression == null ? null : new SpelExpressionParser().parseExpression(groupExpression);
    	return icount(grp == null ? null : item -> grp.getValue(new SpringELCtx(item), String.class), iterator);
    }

	/**
	 * Iterator-based count using lambdas
	 * @param grp what to group by elements by (optional)
	 * @param iterator the iterator to iterate over
	 * @return the count
	 */
	public static Map<String, Long> icount(SimpleExtractor<String, Object> grp, Iterator<?> iterator) {
    	Map<String, Long> cnt = new HashMap<String, Long>();

    	while (iterator.hasNext()) {
    		Object item = iterator.next();
    		String key = grp == null ? "" : grp.extract(item);
    		
    		Long count = cnt.get(key);
    		if (count == null)
    			count = 0L;
    		count++;
    		cnt.put(key, count);
    	}
 
    	if (cnt.isEmpty())
    		cnt.put("", new Long(0L));
    	
    	return cnt;
    }

	/**
	 * EL-based iterator to Set conversion
	 * @param valueExpression value EL
	 * @param iterator iterator to iterate
	 * @return the Set of values
	 */
    public static Set<Object> iset(String valueExpression, Iterator<?> iterator) {
    	Expression val = new SpelExpressionParser().parseExpression(valueExpression);
    	return iset(item -> val.getValue(new SpringELCtx(item)), iterator);
    }

	/**
	 * Lambda-based iterator to Set conversion
	 * @param val value EL
	 * @param iterator iterator to iterate
	 * @return the Set of values
	 */
    public static Set<Object> iset(SimpleExtractor<Object, Object> val, Iterator<?> iterator) {
    	Set<Object> result = new LinkedHashSet<Object>();
    	while (iterator.hasNext())
    		result.add(val.extract(iterator.next()));
    	return result;
    }

	/**
	 * EL-based iterator to List conversion
	 * @param valueExpression value EL
	 * @param iterator iterator to iterate
	 * @return the List of values
	 */
    public static List<Object> ilist(String valueExpression, Iterator<?> iterator) {
    	Expression val = new SpelExpressionParser().parseExpression(valueExpression);
    	return ilist(item -> val.getValue(new SpringELCtx(item)), iterator);
    }

	/**
	 * Lambda-based iterator to List conversion
	 * @param val value EL
	 * @param iterator iterator to iterate
	 * @return the List of values
	 */
    public static List<Object> ilist(SimpleExtractor<Object, Object> val, Iterator<?> iterator) {
    	List<Object> result = new ArrayList<Object>();
    	while (iterator.hasNext())
    		result.add(val.extract(iterator.next()));
    	return result;
    }

	/**
	 * Simple as-is iterator to list conversion
	 * @param iterator iterator
	 * @param <T> member class
	 * @return the list
	 */
    @SuppressWarnings("unchecked")
	public static <T> List<T> i2list(Iterator<?> iterator) {
    	List<T> result = new ArrayList<T>();
    	while (iterator.hasNext())
    		result.add((T)iterator.next());
    	return result;
    }

	/**
	 * String to Long conversion
	 * @param src strings
	 * @return longs
	 */
	public static Long[] transformStringArrayToLong(String[] src) {
		if (src == null)
			return null;
		
		Long[] result = new Long[src.length];
		for (int i = 0; i < src.length; i++)
			result[i] = new Long(src[i]);
		return result;
	}

	/**
	 * String to Integer conversion
	 * @param src strings
	 * @return ints
	 */
	public static Integer[] transformStringArrayToInteger(String[] src) {
		if (src == null)
			return null;
		
		Integer[] result = new Integer[src.length];
		for (int i = 0; i < src.length; i++)
			result[i] = new Integer(src[i]);
		return result;
	}
}
