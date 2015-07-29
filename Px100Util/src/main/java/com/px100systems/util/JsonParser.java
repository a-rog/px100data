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

import com.google.gson.GsonBuilder;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.StringBuilderWriter;
import com.google.gson.Gson;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Writer;
import java.util.List;

/**
 * Simple Gson-based Json parser
 * 
 * @version 0.3 <br>Copyright (c) 2015 Px100 Systems. All Rights Reserved.<br>
 * @author Alex Rogachevsky
 */
public class JsonParser<T> {
	private static final String JSON_DATE_FORMAT = "yyyy-MM-dd HH:mm:ss.SSS";
	private Class<T> jsonClass;

	/**
	 * Constructor
	 * @param jsonClass class to parse into
	 */
	public JsonParser(Class<T> jsonClass) {
		this.jsonClass = jsonClass;
	}

	public static class JsonParsingException extends Exception {
		public JsonParsingException(String message) {
			super(message);
		}
	}

	/**
	 * Parse
	 * @param in input
	 * @return the parsed bean
	 * @throws JsonParsingException
	 */
	public T parse(InputStream in) throws JsonParsingException {
		Reader reader = null;
		try {
			reader = new InputStreamReader(in);
			Gson gson = new GsonBuilder().setDateFormat(JSON_DATE_FORMAT).create();
			return gson.fromJson(reader, jsonClass);
		} catch (JsonSyntaxException|JsonIOException e) {
			throw new JsonParsingException(e.getMessage());
		} finally {
			if (reader != null)
				IOUtils.closeQuietly(reader);
		}
	}

	/**
	 * Parse
	 * @param data input
	 * @return the parsed bean
	 * @throws JsonParsingException
	 */
	public T parse(byte[] data) throws JsonParsingException {
		InputStream in = null;
		try {
			in = new ByteArrayInputStream(data);
			return parse(in);
		} finally {
			IOUtils.closeQuietly(in);
		}
	}

	/**
	 * Parse a list of beans
	 * @param in input
	 * @return the parsed bean
	 * @throws JsonParsingException
	 */
	public List<T> parseList(InputStream in) throws JsonParsingException {
		Reader reader = null;
		try {
			reader = new InputStreamReader(in);
			Gson gson = new GsonBuilder().setDateFormat(JSON_DATE_FORMAT).create();
			return gson.fromJson(reader, new TypeToken<List<T>>(){}.getType());
		} catch (JsonSyntaxException|JsonIOException e) {
			throw new JsonParsingException(e.getMessage());
		} finally {
			if (reader != null)
				IOUtils.closeQuietly(reader);
		}
	}

	/**
	 * Parse a list of beans
	 * @param data input
	 * @return the parsed bean
	 * @throws JsonParsingException
	 */
	@SuppressWarnings("unused")
	public List<T> parseList(byte[] data) throws JsonParsingException {
		InputStream in = null;
		try {
			in = new ByteArrayInputStream(data);
			return parseList(in);
		} finally {
			IOUtils.closeQuietly(in);
		}
	}

	/**
	 * Write a bean (anything: standalone beans, collections, etc.)
	 * @param bean bean to serialize
	 * @return JSON
	 */
	public String write(Object bean) {
		StringBuilder result = new StringBuilder();
		Writer writer = new StringBuilderWriter(result);
		try {
			Gson gson = new GsonBuilder().setDateFormat(JSON_DATE_FORMAT).setPrettyPrinting().create();
			writer.write(gson.toJson(bean));
		} catch (Exception e) {
			throw new RuntimeException(e);
		} finally {
			IOUtils.closeQuietly(writer);
		}
		return result.toString();
	}
}
