package com.px100systems.platform.benchmark.entity;

import com.hazelcast.nio.serialization.Portable;
import com.hazelcast.nio.serialization.PortableReader;
import com.hazelcast.nio.serialization.PortableWriter;
import com.px100systems.data.core.Entity;
import com.px100systems.data.core.Index;
import com.px100systems.data.plugin.storage.hazelcast.PortableImpl;
import com.px100systems.util.serialization.SerializationDefinition;
import com.px100systems.util.serialization.SerializedCollection;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Date;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

public class TestPx100Entity extends Entity implements Portable, Externalizable {
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

	private String textField;
	private Integer intField;
	private Long longField;
	private Double doubleField;
	private Boolean boolField;
	private Date dateField;

	@SerializedCollection(type = Long.class)
	private Set<Long> filler;

	@Override
	public int getFactoryId() {
		return PortableImpl.factoryId();
	}

	@Override
	public int getClassId() {
		return PortableImpl.classId(this);
	}

	@Override
	public void writePortable(PortableWriter writer) throws IOException {
		PortableImpl.writePortable(this, writer);
	}

	@Override
	public void readPortable(PortableReader reader) throws IOException {
		PortableImpl.readPortable(this, reader);
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		SerializationDefinition.get(getClass()).write(out, this);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		SerializationDefinition.get(getClass()).read(in, this);
	}

	public TestPx100Entity() {
	}

	public TestPx100Entity (boolean initialize) {
		this();
		if (initialize) {
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
	}

	@Index
	public String getTextField() {
		return textField;
	}

	public void setTextField(String textField) {
		this.textField = textField;
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

	public Integer getIntField() {
		return intField;
	}

	public void setIntField(Integer intField) {
		this.intField = intField;
	}

	public Long getLongField() {
		return longField;
	}

	public void setLongField(Long longField) {
		this.longField = longField;
	}
}
