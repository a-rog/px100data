package com.px100systems.platform.benchmark.entity;

import com.hazelcast.nio.serialization.Portable;
import com.hazelcast.nio.serialization.PortableReader;
import com.hazelcast.nio.serialization.PortableWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Date;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

public class TestEntity implements Portable, Externalizable {
	private static Random random = new Random();
	private static final String[] firstNames = {
		"Amy", "Amber", "Anna", "Abigail", "Alyssa", "Becky", "Bianca", "Clarissa", "Cathy", "Charlotte", "Camilla", "Cora", "Cloe", "Cayla", "Diane",
		"Emma", "Evelyn", "Elizabeth", "Grace", "Jane", "Jennifer", "Julia", "Jordan", "Kayla", "Lily", "Leah", "Lucy", "Mary", "Lauren", "Madison", "Natalie", "Nora",
		"Melissa", "Olivia", "Rachel", "Scarlett", "Savannah", "Samantha", "Skyler", "Sarah", "Victoria", "Stella", "Cassandra", "Denise",
		"Monica", "Molly", "Nancy", "Heather", "Susan"
	};
	private static final String[] lastNames = {
		"Smith", "Gordon", "Jefferson", "Williams", "Stevens", "Hansen", "Brown", "White", "Miller", "Davis", "Wilson", "Tayler", "Thomas",
		"Moore", "Martin", "Thompson", "Campbell", "Jackson", "Svensson", "Richardson", "Richards", "Lee", "Harris", "Clark", "Lewis",
		"Walker", "Hall", "Allen", "Potter", "Green", "Adams", "Nelson", "Mitchell", "Roberts", "Carter", "Phillips", "Evans", "Turner",
		"Washington", "Pratt", "Grossman", "Rosewood", "Callaway"
	};

	private Long id;
	private Integer tenantId = 0;
	private Date createdAt;
	private Date modifiedAt;

	private String textField;
	private Integer intField;
	private Long longField;
	private Double doubleField;
	private Boolean boolField;
	private Date dateField;
	private Set<Long> filler;

	@Override
	public int getFactoryId() {
		return 1;
	}

	@Override
	public int getClassId() {
		return 1;
	}

	@Override
	public void writePortable(PortableWriter portableWriter) throws IOException {
		portableWriter.writeLong("id", id);
		portableWriter.writeInt("tenantId", tenantId);
		portableWriter.writeLong("createdAt", createdAt.getTime());
		portableWriter.writeLong("modifiedAt", modifiedAt.getTime());
		portableWriter.writeUTF("textField", textField);
		portableWriter.writeInt("intField", intField);
		portableWriter.writeLong("longField", longField);
		portableWriter.writeDouble("doubleField", doubleField);
		portableWriter.writeBoolean("boolField", boolField);
		portableWriter.writeLong("dateField", dateField.getTime());
		portableWriter.writeByteArray("filler", serializeFiller());
	}

	@Override
	public void readPortable(PortableReader portableReader) throws IOException {
		id = portableReader.readLong("id");
		tenantId = portableReader.readInt("tenantId");
		createdAt = new Date(portableReader.readLong("createdAt"));
		modifiedAt = new Date(portableReader.readLong("modifiedAt"));
		textField = portableReader.readUTF("textField");
		intField = portableReader.readInt("intField");
		longField = portableReader.readLong("longField");
		doubleField = portableReader.readDouble("doubleField");
		boolField = portableReader.readBoolean("boolField");
		dateField = new Date(portableReader.readLong("dateField"));
		deserializeFiller(portableReader.readByteArray("filler"));

	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeLong(id);
		out.writeInt(tenantId);
		out.writeLong(createdAt.getTime());
		out.writeLong(modifiedAt.getTime());
		out.writeUTF(textField);
		out.writeInt(intField);
		out.writeLong(longField);
		out.writeDouble(doubleField);
		out.writeBoolean(boolField);
		out.writeLong(dateField.getTime());
		byte[] fillerData = serializeFiller();
		out.writeInt(fillerData.length);
		out.write(serializeFiller());
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		id = in.readLong();
		tenantId = in.readInt();
		createdAt = new Date(in.readLong());
		modifiedAt = new Date(in.readLong());
		textField = in.readUTF();
		intField = in.readInt();
		longField = in.readLong();
		doubleField = in.readDouble();
		boolField = in.readBoolean();
		dateField = new Date(in.readLong());
		byte[] fillerData = new byte[in.readInt()];
		in.readFully(fillerData);
		deserializeFiller(fillerData);
	}

	private byte[] serializeFiller() {
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		DataOutputStream dos = new DataOutputStream(bos);
		try {
			for (Long l : filler)
				dos.writeLong(l);
		} catch (Exception e) {
			throw new RuntimeException(e);
		} finally {
			try {
				dos.close();
				bos.close();
			} catch (Exception ignored) {}
		}
		return bos.toByteArray();
	}

	private void deserializeFiller(byte[] data) {
		filler = new HashSet<>();

		ByteArrayInputStream bis = new ByteArrayInputStream(data);
		DataInputStream dis = new DataInputStream(bis);
		try {
			while(true)
				try {
					filler.add(dis.readLong());
				} catch (EOFException ignored) {
					break;
				}
		} catch (Exception e) {
			throw new RuntimeException(e);
		} finally {
			try {
				dis.close();
				bis.close();
			} catch (Exception ignored) {}
		}
	}

	public TestEntity() {
	}

	public TestEntity(Long id) {
		this.id = id;
		createdAt = new Date(new Date().getTime() - (id * 10000));
		modifiedAt = new Date(new Date().getTime() - (id * 1011));

		textField = firstNames[random.nextInt(firstNames.length)] + " " + lastNames[random.nextInt(lastNames.length)];
		intField = random.nextInt(1000) * (random.nextInt(20) % 2 == 0 ? 1 : -1);
		longField = (long)random.nextInt(1000) * (random.nextInt(20) % 2 == 0 ? 1 : -1) * 123456789L;
		doubleField = (double)random.nextInt(1000) * (random.nextInt(20) % 2 == 0 ? 1 : -1) + 1.23D;
		boolField = random.nextInt(20) % 2 == 0;
		dateField = new Date(new Date().getTime() + random.nextInt(600000) * (random.nextInt(20) % 2 == 0 ? 1 : -1));

		filler = new HashSet<>();
		for (int i = 0, n = 90 + random.nextInt(20); i < n; i++)
			filler.add(random.nextLong());
	}

	public Boolean getBoolField() {
		return boolField;
	}

	public void setBoolField(Boolean boolField) {
		this.boolField = boolField;
	}

	public Date getDateField() {
		return dateField;
	}

	public void setDateField(Date dateField) {
		this.dateField = dateField;
	}

	public Double getDoubleField() {
		return doubleField;
	}

	public void setDoubleField(Double doubleField) {
		this.doubleField = doubleField;
	}

	public Set<Long> getFiller() {
		return filler;
	}

	public void setFiller(Set<Long> filler) {
		this.filler = filler;
	}

	public static String[] getFirstNames() {
		return firstNames;
	}

	public Integer getIntField() {
		return intField;
	}

	public void setIntField(Integer intField) {
		this.intField = intField;
	}

	public static String[] getLastNames() {
		return lastNames;
	}

	public Long getLongField() {
		return longField;
	}

	public void setLongField(Long longField) {
		this.longField = longField;
	}

	public static Random getRandom() {
		return random;
	}

	public static void setRandom(Random random) {
		TestEntity.random = random;
	}

	public String getTextField() {
		return textField;
	}

	public void setTextField(String textField) {
		this.textField = textField;
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}
}
