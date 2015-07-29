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
package com.px100systems.data.plugin.persistence.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import com.px100systems.data.core.Entity;
import com.px100systems.data.core.RawRecord;
import com.px100systems.data.plugin.persistence.PersistenceProvider.LoadCallback;
import com.px100systems.data.plugin.persistence.jdbc.TransactionalJdbcService.JdbcCallback;

/**
 * Storage (relational table) configuration. Every storage can (theoretically) use a different JDBC connection/database.<br>
 * Storages should be organized by the most efficient data block size for the records/documents it stores.
 * Related documents (saved in one transaction) can reside on different storages, however should belong to one data source (connection).<br>
 * The storage is used internally by {@link JdbcPersistence} provider to load and save records.<br>
 * See setters for configuration parameters.<br>
 *
 * @version 0.3 <br>Copyright (c) 2015 Px100 Systems. All Rights Reserved.<br>
 * @author Alex Rogachevsky
*/
public class Storage {
	private String table;
	private String binaryType = "BINARY";
	private int blockSize;
	private TransactionalJdbcService connection;

	public Storage() {
	}

	/**
	 * Database table name
	 * @param table table name
	 */
	@Required
	public void setTable(String table) {
		this.table = table;
	}

	public String getTable() {
		return table;
	}

	/**
	 * Bblock size. If the record doesn't fit in one block, it'll take up several.
	 * @param blockSize record (BLOB) size in bytes
	 */
	@Required
	public void setBlockSize(int blockSize) {
		this.blockSize = blockSize;
	}

	/**
	 * Database connection
	 * @param connection database service
	 */
	@Required
	public void setConnection(TransactionalJdbcService connection) {
		this.connection = connection;
	}
	
	public TransactionalJdbcService getConnection() {
		return connection;
	}

	/**
	 * BLOB atabase type used when creating the table. Default is "BINARY" (works for MySQL).
	 * @param binaryType the main (BLOB) field type
	 */
	public void setBinaryType(String binaryType) {
		this.binaryType = binaryType;
	}

	protected void resetSchema(final String schemaName) {
		connection.write(new JdbcCallback<Void>() {
			@Override
			public Void transaction(JdbcTemplate jdbc) {
				jdbc.execute("DROP SCHEMA " + schemaName);
				jdbc.execute("CREATE SCHEMA " + schemaName);
				return null;
			}
		});
	}
	
	protected void create(final boolean lastSavedStorage) {
		connection.write(new JdbcCallback<Void>() {
			@Override
			public Void transaction(JdbcTemplate jdbc) {
				jdbc.execute("CREATE TABLE " + table + 
					" (pk_id BIGINT AUTO_INCREMENT, unit_name VARCHAR(50), generator_name VARCHAR(50), class_name VARCHAR(100), id BIGINT, block_number INT, data_size INT, data " + binaryType + "(" + blockSize + ")," +
					"  PRIMARY KEY (pk_id))");
				jdbc.execute("CREATE INDEX " + table + "_index0 ON " + table + " (unit_name, id)");

				if (lastSavedStorage) {
					jdbc.execute("CREATE TABLE last_saved (pk_id BIGINT NOT NULL, save_time BIGINT, PRIMARY KEY (pk_id))"); 
					jdbc.update("INSERT INTO last_saved (pk_id, save_time) VALUES (0, NULL)"); 
				}
				
				return null;
			}
		});
	}
	
	protected Long lastSaved() {
		return connection.getJdbc().queryForObject("SELECT save_time FROM last_saved WHERE pk_id = 0", Long.class);
	}
	
	protected void updateLastSaved(Long time) {
		connection.getJdbc().update("UPDATE last_saved SET save_time = ? WHERE pk_id = 0", time);
	}
	
	protected void save(JdbcTemplate jdbc, List<RawRecord> insertsOrUpdates, List<RawRecord> deletes) {
		for (RawRecord record : insertsOrUpdates) {
			deleteRecord(jdbc, record);
			insertRecord(jdbc, record);
		}

		for (RawRecord record : deletes)
			deleteRecord(jdbc, record);
	}

	protected void save(JdbcTemplate jdbc, List<RawRecord> inserts) {
		for (RawRecord record : inserts)
			insertRecord(jdbc, record);
	}

	private void insertRecord(JdbcTemplate jdbc, RawRecord record) {
		try {
			byte[] rawData = record.getSerializedEntity().getBytes("UTF-8");
				
			int count = 0;
			for (int offset = 0, length = rawData.length; offset < length; offset += blockSize) {
				byte[] data = new byte[(length - offset) > blockSize ? blockSize : (length - offset)];
				//noinspection ManualArrayCopy
				for (int i = 0; i < data.length; i++)
					data[i] = rawData[offset + i];
				
				jdbc.update("INSERT INTO " + table + " (unit_name, generator_name, class_name, id, block_number, data_size, data) " +
					"VALUES ('" + record.getUnitName() + "', '" + record.getIdGeneratorName() + "', '" + record.getEntityClass().getName()
						+ "', " + record.getId() + ", " + count + ", " + data.length + ", ?)",
					new Object[]{data}); //, new int[]{java.sql.Types..BINARY});
				count++;
			}
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private void deleteRecord(JdbcTemplate jdbc, RawRecord record) {
		jdbc.update("DELETE FROM " + table + " WHERE unit_name = '" + record.getUnitName() + "' AND id = " + record.getId());
	}
	
	private class LoadData {
		private String unitName = null;
		private String generatorName = null;
		private String className = null;
		private Long id = null;
		private List<byte[]> data = new ArrayList<byte[]>();
		private int lastBlockSize = 0;
		
		public void grow(String unitName, String generatorName, String className, Long id, byte[] data, int lastBlockSize) {
			this.unitName = unitName;
			this.generatorName = generatorName;
			this.className = className;
			this.id = id;
			this.data.add(data);
			this.lastBlockSize = lastBlockSize;
		}

		private void insert(LoadCallback callback) {
			if (data.isEmpty())
				return;
			
			RawRecord record = new RawRecord();

			try {
				record.setId(id);
				record.setUnitName(unitName);
				record.setIdGeneratorName(generatorName);

				//noinspection unchecked
				record.setEntityClass((Class<? extends Entity>)Class.forName(className));

				// MySQL debugging fix - ignore lastBlockSize if there is only one block and it was changed server-side (by a developer)
				if (data.size() == 1) {
					int dataLength = data.get(0).length;
					if (dataLength != lastBlockSize)
						lastBlockSize = dataLength;
				}

				StringBuilder sb = new StringBuilder();
				for (int i = 0, n = data.size(); i < n; i++)
					sb.append(i < n - 1 ? new String(data.get(i), "UTF-8") : new String(data.get(i), 0, lastBlockSize, "UTF-8"));
				record.setSerializedEntity(sb.toString());
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
			
			callback.process(record);
			data.clear();
		}
	}
	
	protected void load(List<String> unitNames, final LoadCallback callback) {
		final LoadData currentRecord = new LoadData();
		
		StringBuilder where = new StringBuilder();
		if (unitNames != null)
			for (String like : unitNames) {
				if (where.length() > 0)
					where.append(" OR ");
				where.append("(unit_name LIKE '");
				where.append(like);
				where.append("%')");
			}
		
		connection.getJdbc().setFetchSize(50);
		connection.getJdbc().query("SELECT unit_name, generator_name, class_name, id, data_size, data FROM " + table + 
			(where.length() > 0 ? (" WHERE " + where) : "") +
			" ORDER BY unit_name ASC, id ASC, block_number ASC", new RowCallbackHandler() {
			@Override
			public void processRow(ResultSet rs) throws SQLException {
				String unitName = rs.getString("unit_name");
				String generatorName = rs.getString("generator_name");
				String className = rs.getString("class_name");
				Long id = rs.getLong("id");
				int dataSize = rs.getInt("data_size");
				byte[] data = rs.getBytes("data");
				
				if (currentRecord.id != null && (!currentRecord.id.equals(id) || !currentRecord.unitName.equals(unitName)))
					currentRecord.insert(callback);
				
				currentRecord.grow(unitName, generatorName, className, id, data, dataSize);
			}
		});

		currentRecord.insert(callback);
	}

	protected Map<String, Long> loadMaxIds() {
		final Map<String, Long> result = new HashMap<String, Long>();
		connection.getJdbc().query("SELECT MAX(id), generator_name FROM " + table + " GROUP BY generator_name", new RowCallbackHandler() {
				@Override
				public void processRow(ResultSet rs) throws SQLException {
					result.put(rs.getString("generator_name"), rs.getLong(1));
				}
			});
		return result;
	}

	protected String sql(String unitName, List<Long> ids) {
		StringBuilder list = new StringBuilder();
		for (Long id : ids) {
			if (list.length() > 0)
				list.append(",");
			list.append(id);
		}
		
		return "SELECT id, block_number, data_size, data FROM " + table + " WHERE unit_name = '" + unitName + "' AND id IN (" + list + ")";
	}
}
