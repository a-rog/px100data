package com.t34.test.entity;

import com.px100systems.data.core.Entity;
import com.px100systems.data.core.Index;
import com.px100systems.util.serialization.SerializationDefinition;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Date;

public class MyEntity extends Entity implements Externalizable {
	private String text;
	private Date date;

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		SerializationDefinition.get(getClass()).write(out, this);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		SerializationDefinition.get(getClass()).read(in, this);
	}

	@Override
	public String toString() {
		return "MyEntity{" +
			"date=" + date +
			", text='" + text + '\'' +
			'}';
	}

	@Index
	public Date getDate() {
		return date;
	}

	public void setDate(Date date) {
		this.date = date;
	}

	public String getText() {
		return text;
	}

	public void setText(String text) {
		this.text = text;
	}
}
