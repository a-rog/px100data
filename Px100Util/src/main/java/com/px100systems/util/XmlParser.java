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

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.StringBuilderWriter;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.Writer;

/**
 * Simple JAXB-based XML parser
 * 
 * @version 0.3 <br>Copyright (c) 2015 Px100 Systems. All Rights Reserved.<br>
 * @author Alex Rogachevsky
 */
public class XmlParser<T> {
	private JAXBContext jaxb;

	/**
	 * XML parser
	 * @param xmlClass class to parse into
	 */
	public XmlParser(Class<T> xmlClass) {
		try {
			jaxb = JAXBContext.newInstance(xmlClass);
		} catch (JAXBException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Parse
	 * @param in input
	 * @return the parsed class
	 */
	@SuppressWarnings("unchecked")
	public T parse(InputStream in) {
		try {
			return (T)jaxb.createUnmarshaller().unmarshal(in);
		} catch (JAXBException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Parse
	 * @param data input
	 * @return the parsed class
	 */
	public T parse(byte[] data) {
		InputStream in = null;
		try {
			in = new ByteArrayInputStream(data);
			return parse(in);
		} finally {
			IOUtils.closeQuietly(in);
		}
	}

	/**
	 * Serialize the bean to XML
	 * @param bean teh bean to serialize
	 * @return XML
	 */
	public String write(T bean) {
		StringBuilder result = new StringBuilder();
		Writer writer = new StringBuilderWriter(result);
		try {
			Marshaller m = jaxb.createMarshaller();
			m.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
			m.setProperty(Marshaller.JAXB_FRAGMENT, Boolean.TRUE);
			m.marshal(bean, writer);
		} catch (Exception e) {
			throw new RuntimeException(e);
		} finally {
			IOUtils.closeQuietly(writer);
		}
		return result.toString();
	}
}
