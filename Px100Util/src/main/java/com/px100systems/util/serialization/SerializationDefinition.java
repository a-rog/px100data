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
package com.px100systems.util.serialization;

import com.px100systems.util.PropertyAccessor;
import org.springframework.util.ReflectionUtils;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Definition to serialize class.<br>
 * Used in universal reflection-based Externalizable, Portable, and other implementations.<br>
 * Application (JVM) wide singleton. Not thread-safe during creation for performance reasons - expected to be created by one "initialization" thread.
 * Register every class using {@link SerializationDefinition#register register()} and then call {@link SerializationDefinition#lock lock()}.
 *
 * @version 0.3 <br>Copyright (c) 2015 Px100 Systems. All Rights Reserved.<br>
 * @author Alex Rogachevsky
 */
public class SerializationDefinition {
	private static Map<Class<?>, SerializationDefinition> definitions = new HashMap<>();

	private static int classId = 1;
	private static Map<Integer, SerializationDefinition> classIds = new HashMap<>();

	private static boolean locked = false;

	private static class FieldDefinition {
		private String name;
		private Class<?> type;
		private Class<?> collectionType = null;
		private Method accessor;
		private Method mutator = null;

		public FieldDefinition() {
		}

		public FieldDefinition(String name) {
			this.name = name;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (!(o instanceof FieldDefinition)) return false;

			FieldDefinition that = (FieldDefinition) o;

			return name.equals(that.name);

		}

		@Override
		public int hashCode() {
			return name.hashCode();
		}
	}
	private List<FieldDefinition> fields = new ArrayList<>();
	private List<FieldDefinition> gettersOnly = new ArrayList<>();
	private Constructor<?> constructor;
	private Method serializingSetter = null;

	private Integer id;

	/**
	 * Get the definition by class
	 * @param cls class
	 * @return definition or null
	 */
	public static SerializationDefinition get(Class<?> cls) {
		return definitions.get(cls);
	}

	/**
	 * Get the definition by assigned ID (Hazelcast portable only)
	 * @param id auto-assigned ID
	 * @return definition or null
	 */
	public static SerializationDefinition getById(Integer id) {
		return classIds.get(id);
	}

	/**
	 * Auto-assigned ID
	 * @return registered class ID
	 */
	public Integer getId() {
		return id;
	}

	/**
	 * Helper method leveraging already parsed reflection data. Throws an exception oif the getter is not found.
	 * @param name field name
	 */
	public void checkGetter(String name) {
		Set<FieldDefinition> list = new HashSet<>(gettersOnly);
		if (!list.contains(new FieldDefinition(name)))
			throw new RuntimeException("Getter for " + name + " not found in " + constructor.getDeclaringClass().getSimpleName());
	}

	/**
	 * Helper method leveraging already parsed reflection data. Throws an exception if the field is not found.
	 * @param fieldNames field names
	 */
	public void checkFields(List<String> fieldNames) {
		Set<FieldDefinition> list = new HashSet<>(fields);
		list.addAll(gettersOnly);
		for (String name : fieldNames) {
			FieldDefinition f = new FieldDefinition(name);
			if (!list.contains(f))
				throw new RuntimeException("Field " + name + " not found in " + constructor.getDeclaringClass().getSimpleName());
		}
	}

	/**
	 * Get bean's field leveraging already parsed reflection data. Throws an exception if the field is not found.
	 * @param bean the bean
	 * @param name field name
	 * @return teh field value
	 */
	public Object getField(Object bean, String name) {
		List<FieldDefinition> list = new ArrayList<>(fields);
		list.addAll(gettersOnly);
		for (FieldDefinition f : list)
			if (f.name.equals(name))
				return invokeMethod(f.accessor, bean);
		throw new RuntimeException("Field " + name + " not found in " + constructor.getDeclaringClass().getSimpleName());
	}

	/**
	 * Get field type leveraging already parsed reflection data. Throws an exception if the field is not found.
	 * @param name field name
	 * @return the field type
	 */
	public Class<?> getFieldType(String name) {
		List<FieldDefinition> list = new ArrayList<>(fields);
		list.addAll(gettersOnly);
		for (FieldDefinition f : list)
			if (f.name.equals(name))
				return f.type;
		throw new RuntimeException("Field " + name + " not found in " + constructor.getDeclaringClass().getSimpleName());
	}

	/**
	 * Should be called after all definitions have been registered on startup allowing them to be used concurrently w/o synchronization since they are immutable.
	 */
	public static void lock() {
		definitions = Collections.unmodifiableMap(definitions);
		classIds = Collections.unmodifiableMap(classIds);
		locked = true;
	}

	/**
	 * Creates and registers the definition
	 * @param cls class to register
	 * @return assigned ID
	 */
	public static int register(Class<?> cls) {
		SerializationDefinition def = definitions.get(cls);
		if (def == null)
			def = new SerializationDefinition(cls);

		def.id = classId;
		classIds.put(classId++, def);
		return def.id;
	}

	private SerializationDefinition(Class<?> cls) {
		definitions.put(cls, this);

		if (cls.getName().startsWith("java"))
			throw new RuntimeException("System classes are not supported: " + cls.getSimpleName());

		try {
			constructor = cls.getConstructor();
		} catch (NoSuchMethodException e) {
			throw new RuntimeException("Missing no-arg constructor: " + cls.getSimpleName());
		}

		serializingSetter = ReflectionUtils.findMethod(cls, "setSerializing", boolean.class);

		for (Class<?> c = cls; c != null && !c.equals(Object.class); c = c.getSuperclass()) {
			for (Field field : c.getDeclaredFields()) {
				if (Modifier.isTransient(field.getModifiers()) || Modifier.isStatic(field.getModifiers()))
					continue;

				FieldDefinition fd = new FieldDefinition();
				fd.name = field.getName();

				fd.type = field.getType();
				if (fd.type.isPrimitive())
					throw new RuntimeException("Primitives are not supported: " + fd.type.getSimpleName());

				if (!fd.type.equals(Integer.class) &&
					!fd.type.equals(Long.class) &&
					!fd.type.equals(Double.class) &&
					!fd.type.equals(Boolean.class) &&
					!fd.type.equals(Date.class) &&
					!fd.type.equals(String.class))
					if (fd.type.equals(List.class) || fd.type.equals(Set.class)) {
						SerializedCollection sc = field.getAnnotation(SerializedCollection.class);
						if (sc == null)
							throw new RuntimeException(cls.getSimpleName() + "." + fd.name + " is missing @SerializedCollection");

						fd.collectionType = sc.type();
						if (!fd.collectionType.equals(Integer.class) &&
							!fd.collectionType.equals(Long.class) &&
							!fd.collectionType.equals(Double.class) &&
							!fd.collectionType.equals(Boolean.class) &&
							!fd.collectionType.equals(Date.class) &&
							!fd.collectionType.equals(String.class)) {
							if (cls.getName().startsWith("java"))
								throw new RuntimeException(cls.getSimpleName() + "." + fd.name +
									": system collection types are not supported: " + fd.collectionType.getSimpleName());

							if (!definitions.containsKey(fd.collectionType))
								new SerializationDefinition(fd.collectionType);
						}
					} else {
						if (cls.getName().startsWith("java"))
							throw new RuntimeException("System classes are not supported: " + fd.type.getSimpleName());

						if (!definitions.containsKey(fd.type))
							new SerializationDefinition(fd.type);
					}

				try {
					fd.accessor = c.getMethod(PropertyAccessor.methodName("get", fd.name));
				} catch (NoSuchMethodException e) {
					throw new RuntimeException(cls.getSimpleName() + "." + fd.name + " is missing getter");
				}

				try {
					fd.mutator = c.getMethod(PropertyAccessor.methodName("set", fd.name), fd.type);
				} catch (NoSuchMethodException e) {
					throw new RuntimeException(cls.getSimpleName() + "." + fd.name + " is missing setter");
				}

				fields.add(fd);
			}

			for (Method method : c.getDeclaredMethods())
				if (Modifier.isPublic(method.getModifiers()) && !Modifier.isStatic(method.getModifiers()) &&
					method.getName().startsWith("get") && method.isAnnotationPresent(SerializedGetter.class)) {
					FieldDefinition fd = new FieldDefinition();
					fd.name = method.getName().substring(3);
					fd.name = fd.name.substring(0, 1).toLowerCase() + fd.name.substring(1);

					fd.type = method.getReturnType();
					if (fd.type == null || fd.type.isPrimitive() ||
						(!fd.type.equals(Integer.class) &&
						!fd.type.equals(Long.class) &&
						!fd.type.equals(Double.class) &&
						!fd.type.equals(Boolean.class) &&
						!fd.type.equals(Date.class) &&
						!fd.type.equals(String.class)))
						throw new RuntimeException("Not compact-serializable getter type: " + (fd.type == null ? "void" : fd.type.getSimpleName()));

					fd.accessor = method;
					gettersOnly.add(fd);
				}
		}
	}

	/**
	 * Universal bean constructor
	 * @return the created bean
	 */
	public Object newInstance() {
		try {
			return constructor.newInstance();
		} catch (Exception e) {
			throw new RuntimeException("Constructor " + constructor.getDeclaringClass().getSimpleName() + "." + constructor.getName() + " invocation error: " + e.getMessage(), e);
		}
	}

	/**
	 * Externalizable helper - should be used in Externalizable implementations.
	 * @param out output
	 * @param bean bean
	 */
	public void write(ObjectOutput out, Object bean) {
		DataStream ds = new DataStream();
		try {
			write(ds, bean);
			byte[] data = ds.getData();
			out.writeInt(data.length);
			out.write(data);
		} catch (IOException e) {
			throw new RuntimeException(e);
		} finally {
			ds.close();
		}
	}

	/**
	 * Externalizable helper - should be used in Externalizable implementations.
	 * @param in input
	 * @param bean bean
	 */
	public void read(ObjectInput in, Object bean) {
		DataStream ds = null;
		try {
			int length = in.readInt();
			byte[] data = new byte[length];
			in.readFully(data);
			ds = new DataStream(data);
			read(ds, bean);
		} catch (IOException e) {
			throw new RuntimeException(e);
		} finally {
			if (ds != null)
				ds.close();
		}
	}

	/**
	 * Normal stream serialization
	 * @param stream the stream
	 * @param bean the bean
	 */
	public void write(DataStream stream, Object bean) {
		if (!locked)
			throw new RuntimeException("Lock the definitions after creating all of them at startup");

		for (FieldDefinition fd : fields) {
			Object value = invokeMethod(fd.accessor, bean);
			if (fd.type.equals(List.class) || fd.type.equals(Set.class)) {
				if (value == null)
					stream.writeInteger(null);
				else {
					Collection<?> collection = (Collection<?>)value;
					DataStream newStream = new DataStream();
					newStream.writeInteger(collection.size());
					for (Object o : collection)
						write(newStream, o, fd.collectionType);
					stream.writeBytes(newStream);
					newStream.close();
				}
			} else
				write(stream, value, fd.type);
		}
	}

	private void write(DataStream stream, Object value, Class<?> type) {
		if (type.equals(Integer.class))
			stream.writeInteger((Integer)value);
		else if (type.equals(Long.class))
			stream.writeLong((Long)value);
		else if (type.equals(Double.class))
			stream.writeDouble((Double)value);
		else if (type.equals(Boolean.class))
			stream.writeBoolean((Boolean)value);
		else if (type.equals(Date.class))
			stream.writeDate((Date)value);
		else if (type.equals(String.class))
			stream.writeString((String) value);
		else {
			if (value == null)
				stream.writeInteger(null);
			else {
				DataStream newStream = new DataStream();
				definitions.get(type).write(newStream, value);
				stream.writeBytes(newStream);
				newStream.close();
			}
		}
	}

	/**
	 * External field-level writer serialization: used by Hazelcast's Portable implementation and Mongo serialization.
	 * Serializes all top level fields plus serialized getters. Collections and sub-objects are serialized as binary data (byte arrays).
	 * @param writer the field writer
	 * @param bean the bean
	 */
	public void write(ExternalWriter writer, Object bean) {
		if (!locked)
			throw new RuntimeException("Lock the definitions after creating all of them at startup");

		for (FieldDefinition fd : fields) {
			Object value = invokeMethod(fd.accessor, bean);

			if (fd.type.equals(Integer.class))
				writer.writeInteger(fd.name, (Integer)value);
			else if (fd.type.equals(Long.class))
				writer.writeLong(fd.name, (Long)value);
			else if (fd.type.equals(Double.class))
				writer.writeDouble(fd.name, (Double)value);
			else if (fd.type.equals(Boolean.class))
				writer.writeBoolean(fd.name, (Boolean)value);
			else if (fd.type.equals(Date.class))
				writer.writeDate(fd.name, (Date)value);
			else if (fd.type.equals(String.class))
				writer.writeString(fd.name, (String) value);
			else if (fd.type.equals(List.class) || fd.type.equals(Set.class)) {
				if (value == null)
					writer.writeBytes(fd.name, null);
				else {
					Collection<?> collection = (Collection<?>) value;
					DataStream newStream = new DataStream();
					newStream.writeInteger(collection.size());
					for (Object member : collection)
						write(newStream, member, fd.collectionType);
					writer.writeBytes(fd.name, newStream.getData());
					newStream.close();
				}
			} else
				if (value == null)
					writer.writeBytes(fd.name, null);
				else {
					DataStream newStream = new DataStream();
					definitions.get(fd.type).write(newStream, value);
					writer.writeBytes(fd.name, newStream.getData());
					newStream.close();
				}
		}

		for (FieldDefinition fd : gettersOnly) {
			Object value = invokeMethod(fd.accessor, bean);

			if (fd.type.equals(Integer.class))
				writer.writeInteger(fd.name, (Integer)value);
			else if (fd.type.equals(Long.class))
				writer.writeLong(fd.name, (Long)value);
			else if (fd.type.equals(Double.class))
				writer.writeDouble(fd.name, (Double)value);
			else if (fd.type.equals(Boolean.class))
				writer.writeBoolean(fd.name, (Boolean)value);
			else if (fd.type.equals(Date.class))
				writer.writeDate(fd.name, (Date)value);
			else if (fd.type.equals(String.class))
				writer.writeString(fd.name, (String) value);
		}
	}

	/**
	 * Normal stream deserialization
	 * @param stream the stream
	 * @param bean the bean
	 */
	public void read(DataStream stream, Object bean) {
		if (!locked)
			throw new RuntimeException("Lock the definitions after creating all of them at startup");

		if (serializingSetter != null)
			invokeMethod(serializingSetter, bean, true);

		for (FieldDefinition fd : fields)
			if (fd.type.equals(List.class) || fd.type.equals(Set.class)) {
				DataStream newStream =  stream.readBytes();
				Collection<Object> collection = null;
				if (newStream != null) {
					collection = fd.type.equals(List.class) ? new ArrayList<Object>() : new HashSet<Object>();
					int size = newStream.readInteger();
					for (int i = 0; i < size; i++) {
						Object member = read(newStream, fd.collectionType);
						collection.add(member);
					}
					newStream.close();
				}
				invokeMethod(fd.mutator, bean, collection);
			} else
				invokeMethod(fd.mutator, bean, read(stream, fd.type));

		if (serializingSetter != null)
			invokeMethod(serializingSetter, bean, false);
	}

	private Object read(DataStream stream, Class<?> type) {
		if (type.equals(Integer.class))
			return stream.readInteger();
		if (type.equals(Long.class))
			return stream.readLong();
		if (type.equals(Double.class))
			return stream.readDouble();
		if (type.equals(Boolean.class))
			return stream.readBoolean();
		if (type.equals(Date.class))
			return stream.readDate();
		if (type.equals(String.class))
			return stream.readString();
		else {
			DataStream newStream = stream.readBytes();
			if (newStream == null)
				return null;
			SerializationDefinition def = definitions.get(type);
			Object bean = def.newInstance();
			def.read(newStream, bean);
			newStream.close();
			return bean;
		}
	}

	/**
	 * External field-level reader deserialization: used by Hazelcast's Portable implementation and Mongo serialization.
	 * Serializes all top level fields plus serialized getters. Collections and sub-objects are serialized as binary data (byte arrays).
	 * @param reader the field reader
	 * @param bean the bean
	 */
	public void read(ExternalReader reader, Object bean) {
		if (!locked)
			throw new RuntimeException("Lock the definitions after creating all of them at startup");

		if (serializingSetter != null)
			invokeMethod(serializingSetter, bean, true);

		for (FieldDefinition fd : fields)
			if (fd.type.equals(Integer.class))
				invokeMethod(fd.mutator, bean, reader.readInteger(fd.name));
			else if (fd.type.equals(Long.class))
				invokeMethod(fd.mutator, bean, reader.readLong(fd.name));
			else if (fd.type.equals(Double.class))
				invokeMethod(fd.mutator, bean, reader.readDouble(fd.name));
			else if (fd.type.equals(Boolean.class))
				invokeMethod(fd.mutator, bean, reader.readBoolean(fd.name));
			else if (fd.type.equals(Date.class))
				invokeMethod(fd.mutator, bean, reader.readDate(fd.name));
			else if (fd.type.equals(String.class))
				invokeMethod(fd.mutator, bean, reader.readString(fd.name));
			else if (fd.type.equals(List.class) || fd.type.equals(Set.class)) {
				byte[] data = reader.readBytes(fd.name);
				Collection<Object> collection = null;
				if (data != null) {
					DataStream newStream = new DataStream(data);
					collection = fd.type.equals(List.class) ? new ArrayList<Object>() : new HashSet<Object>();
					for (int i = 0, size = newStream.readInteger(); i < size; i++) {
						Object member = read(newStream, fd.collectionType);
						collection.add(member);
					}
					newStream.close();
				}
				invokeMethod(fd.mutator, bean, collection);
			} else {
				byte[] data = reader.readBytes(fd.name);
				Object value = null;
				if (data != null) {
					DataStream newStream = new DataStream(data);
					SerializationDefinition def = definitions.get(fd.type);
					value = def.newInstance();
					def.read(newStream, value);
					newStream.close();
				}
				invokeMethod(fd.mutator, bean, value);
			}

		if (serializingSetter != null)
			invokeMethod(serializingSetter, bean, false);
	}

	private Object invokeMethod(Method method, Object bean, Object... args) {
		try {
			return method.invoke(bean, args);
		} catch (Exception e) {
			throw new RuntimeException("Method " + method.getDeclaringClass().getSimpleName() + "." + method.getName() + " invocation error: " + e.getMessage(), e);
		}
	}

	/**
	 * DataStream-based cloning.
	 * @param bean bean to clone
	 * @return cloned bean
	 */
	@SuppressWarnings("unchecked")
	public <T> T clone(T bean) {
		DataStream ds = new DataStream();
		try {
			write(ds, bean);
		} finally {
			ds.close();
		}

		T result = (T)newInstance();

		ds = new DataStream(ds.getData());
		try {
			read(ds, result);
		} finally {
			ds.close();
		}

		return result;
	}
}