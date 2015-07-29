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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVStrategy;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.StringBuilderWriter;

/**
 * CSV parser.
 * 
 * @version 0.3 <br>Copyright (c) 2015 Px100 Systems. All Rights Reserved.<br>
 * @author Alex Rogachevsky
 */
public class CsvParser<T> {
	private CSVStrategy csvStrategy;

	/**
	 * Default constructor (should be enough)
	 */
	public CsvParser() {
		this (',', '"', '#');
	}

	/**
	 * Custom constructor for non-standard CSV files
	 * @param csvSeparator separator (,)
	 * @param csvQuote quote (")
	 * @param csvComment comment (#)
	 */
	public CsvParser(char csvSeparator, char csvQuote, char csvComment) {
		csvStrategy = new CSVStrategy(csvSeparator, csvQuote, csvComment);
	}

	/**
	 * Parsing into a list of beans (requires CSV header).
	 *
	 * @param content text
	 * @param cls class
	 * @return the list of beans of teh specified class.
	 */
	public List<T> parse(String content, Class<T> cls) {
		List<T> result = new ArrayList<T>();
		
		CSVParser parser = new CSVParser(new BufferedReader(new StringReader(content)), csvStrategy);
		try {
			List<PropertyAccessor> properties = new ArrayList<PropertyAccessor>(); 
			for (String h : parser.getLine())
				properties.add(new PropertyAccessor(cls, h));
			
			if (!properties.isEmpty())
				for (String[] line = parser.getLine(); line != null; line = parser.getLine()) {
					T object = cls.newInstance();
					int fieldCount = 0;
				
					for (int i = 0, n = properties.size(); i < n && i < line.length; i++)
						if (line[i] != null && !line[i].trim().isEmpty()) {
							properties.get(i).set(object, line[i]);
							fieldCount++;
						}
						
					if (fieldCount > 0)
						result.add(object);
				}
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		
		return result.isEmpty() ? null : result;
	}

	private String[] currentLine = null;
	private String[] header;

	/**
	 * Parsing into a list of maps of fields (requires header)
	 *
	 * @param is imput stream
	 * @return List of maps of {field, value} pairs
	 */
	public Iterator<Map<String,String>> parseAsMap(InputStream is) {
		final CSVParser parser = new CSVParser(new BufferedReader(new InputStreamReader(is)), csvStrategy);
		try {
			header = parser.getLine();
			return new Iterator<Map<String, String>>() {
				@Override
				public boolean hasNext() {
					try {
						currentLine = parser.getLine();
						return currentLine != null;
					} catch (Exception e) {
						throw new RuntimeException(e);
					}
				}

				@Override
				public Map<String, String> next() {
					Map<String, String> row = new HashMap<String, String>();
					for (int i = 0; i < header.length; i++)
						row.put(header[i], currentLine[i]);
					return row;
				}
			};
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Parsing into a list of lists (headerless)
	 *
	 * @param is input stream
	 * @return a List of Lists of (String) fields
	 */
	@SuppressWarnings("unused")
	public Iterator<List<String>> parseHeaderlessAsMap(InputStream is) {
		final CSVParser parser = new CSVParser(new BufferedReader(new InputStreamReader(is)), csvStrategy);
		return new Iterator<List<String>>() {
			@Override
			public boolean hasNext() {
				try {
					currentLine = parser.getLine();
					return currentLine != null;
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
			}

			@Override
			public List<String> next() {
				return Arrays.asList(currentLine);
			}
		};
	}

	/**
	 * Export - the opposite of parseHeaderlessAsMap().
	 *
	 * @param lines of Strings
	 * @return the CSV text
	 */
	public String write(List<List<String>> lines) {
		StringBuilder result = new StringBuilder();

		Writer writer = new StringBuilderWriter(result);
		CSVPrinter printer = new CSVPrinter(writer, csvStrategy);
		try {
			for (List<String> line : lines)
				printer.println(line.toArray(new String[line.size()]));
		} catch (IOException e) {
			throw new RuntimeException(e);
		} finally {
			IOUtils.closeQuietly(writer);
		}

		return result.toString();
	}
}
