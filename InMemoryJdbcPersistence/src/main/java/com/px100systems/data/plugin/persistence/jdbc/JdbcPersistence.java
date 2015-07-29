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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.px100systems.data.core.EntityDescriptor;
import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.jdbc.core.JdbcTemplate;
import com.px100systems.data.core.RawRecord;
import com.px100systems.data.plugin.persistence.PersistenceProvider;
import com.px100systems.data.plugin.persistence.PersistenceProviderException;
import com.px100systems.data.plugin.persistence.UnitStorageMapper;
import com.px100systems.data.plugin.persistence.jdbc.TransactionalJdbcService.JdbcCallback;

/**
 * JDBC persistence provider (tested on MySQL and H2). It stores data in blocks to populate the data grid on startup.
 * Two modes are supported: write-through and write-behind.<br>
 * <br>
 * Per-storage JDBC connection should be via (configured externally) DriverManagerDataSource:<br>
 * <ul>
 *   <li> H2 URL: jdbc:h2:/absolute_path/filename -
 *     multiple DataSources to spread the data among several files: break by completely isolated entities which would never be saved in one transaction
 *   <li>MySQL: jdbc:mysql://localhost:3306/xyz_schema (supply the user and password as well) -
 *     one DataSource
 * </ul>
 * <br>
 * A "storage" roughly corresponds to a relational database table.
 * Storages store blobs in blocks and are configured for entities of different size that need to use blocks of different (optimal) size.
 * Typically there are three storages of small, medium, and big block size.
 * It is better to use the latter (big block storage) for development since bog blocks would hold the entire JSON records w/o splitting
 * them into several blocks - useful to examine human-readable JSON records in the database.<br>
 * lastSaved time is always saved in the first storage.<br>
 * <br>
 * <b>Performance tip:</b> since no complex relational structure is required, simple fast databases like MySQL work best - much faster than any NoSQL ones.<br>
 * <br>
 * See configuration parameter setters for details.
 *
 * @version 0.3 <br>Copyright (c) 2015 Px100 Systems. All Rights Reserved.<br>
 * @author Alex Rogachevsky
*/
public class JdbcPersistence implements PersistenceProvider {
	private UnitStorageMapper unitStorageMapper; 
	private Map<String, Storage> storages;
	private Storage lastSavedStorage;
	private String schemaName = null;
	private String databaseDirectory = null;

	public JdbcPersistence() {
	}

	/**
	 * Maps unit (wildcarded entity name) to storage (database table). Typically all "x.y.z.*" package entities (classes) are assigned to some storage.
	 * @param unitStorageMapper unit to storage mappper
	 */
	@Required
	public void setUnitStorageMapper(UnitStorageMapper unitStorageMapper) {
		this.unitStorageMapper = unitStorageMapper;
	}

	/**
	 * The list of storages (database tables). Automatically created during the schema initialization.
	 * @param storages list of storage configs
	 */
	@Required
	public void setStorages(List<Storage> storages) {
		lastSavedStorage = storages.get(0);
		
		this.storages = new HashMap<String, Storage>();
		for (Storage storage : storages) {
			if (this.storages.containsKey(storage.getTable()))
				throw new RuntimeException("Duplicate storage table " + storage.getTable());	
			this.storages.put(storage.getTable(), storage);
		}
	}

	/**
	 * Optional schema DDL control (MySQL, PostgreSQL, Oracle, and similar heavy databases, not H2).
	 * @param schemaName schema name
	 */
	public void setSchemaName(String schemaName) {
		this.schemaName = schemaName;
	}

	/**
	 * Used for optional directory clean on database reset (typically mutually exclusive with schema name - relevant for H2 only)
	 * @param databaseDirectory database directory for on-disk databases like H2
	 */
	@SuppressWarnings("unused")
	public void setDatabaseDirectory(String databaseDirectory) {
		this.databaseDirectory = databaseDirectory;
	}

	@Override
	public void init() {
		if (schemaName != null)
			lastSavedStorage.resetSchema(schemaName);
		
		if (databaseDirectory != null)
			try {
				File baseDir = new File(databaseDirectory);
				if (baseDir.exists())
					FileUtils.deleteDirectory(baseDir);
				//noinspection ResultOfMethodCallIgnored
				baseDir.mkdirs();
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		
		for (Storage s : storages.values())
			s.create(s.getTable().equals(lastSavedStorage.getTable()));
	}

	private static class StorageTransaction {
		private List<RawRecord> insertsOrUpdates = new ArrayList<RawRecord>();
		private List<RawRecord> deletes = new ArrayList<RawRecord>();
		private Storage storage;
		
		public StorageTransaction(Storage storage) {
			super();
			this.storage = storage;
		}
		
		public void save(JdbcTemplate jdbc) {
			storage.save(jdbc, insertsOrUpdates, deletes);
		}

		public void insert(JdbcTemplate jdbc) {
			storage.save(jdbc, insertsOrUpdates);
		}
	}
	
	private void organizeRecord(RawRecord r, boolean delete, Map<String, List<StorageTransaction>> transactionsByDataSource, Map<String, StorageTransaction> transactions) {
		String storageName = unitStorageMapper.map(r.getUnitName());
		StorageTransaction t = transactions.get(storageName);
		if (t == null) {
			t = new StorageTransaction(storages.get(storageName));
			transactions.put(storageName, t);
			
			String connectionName = t.storage.getConnection().getName();
			List<StorageTransaction> tt = transactionsByDataSource.get(connectionName);
			if (tt == null) {
				tt = new ArrayList<StorageTransaction>();
				transactionsByDataSource.put(connectionName, tt);
			}
			tt.add(t);
		}
		
		if (delete)
			t.deletes.add(r);
		else
			t.insertsOrUpdates.add(r);
	}
	
	@Override
	public void transactionalSave(Connection conn, Collection<RawRecord> insertsOrUpdates, Collection<EntityDescriptor> deletes, Long lastUpdateTime) throws PersistenceProviderException {
		if (insertsOrUpdates.isEmpty() && deletes.isEmpty())
			return;
		
		Map<String, List<StorageTransaction>> transactionsByDataSource = new HashMap<String, List<StorageTransaction>>();
		Map<String, StorageTransaction> transactions = new HashMap<String, StorageTransaction>();
		
		for (RawRecord r : insertsOrUpdates) 
			organizeRecord(r, false, transactionsByDataSource, transactions);		
			
		for (EntityDescriptor r : deletes)
			organizeRecord(new RawRecord(r.getUnit(), r.getId()), true, transactionsByDataSource, transactions);
		
		try {
			for (final List<StorageTransaction> t : transactionsByDataSource.values())
				t.get(0).storage.getConnection().write(new JdbcCallback<Void>() {
					@Override
					public Void transaction(JdbcTemplate jdbc) {
						for (StorageTransaction transaction : t)
							transaction.save(jdbc);
						return null;
					}
				});
			
			lastSavedStorage.updateLastSaved(lastUpdateTime);
		} catch (Exception e) {
			throw new PersistenceProviderException(e);
		}
	}

	@Override
	public void transactionalSave(Connection conn, List<RawRecord> inserts) throws PersistenceProviderException {
		if (inserts.isEmpty())
			return;
		
		Map<String, List<StorageTransaction>> transactionsByDataSource = new HashMap<String, List<StorageTransaction>>();
		Map<String, StorageTransaction> transactions = new HashMap<String, StorageTransaction>();
		
		for (RawRecord r : inserts) 
			organizeRecord(r, false, transactionsByDataSource, transactions);		

		try {
			for (final List<StorageTransaction> t : transactionsByDataSource.values())
				t.get(0).storage.getConnection().write(new JdbcCallback<Void>() {
					@Override
					public Void transaction(JdbcTemplate jdbc) {
						for (StorageTransaction transaction : t)
							transaction.insert(jdbc);
						return null;
					}
				});
		} catch (Exception e) {
			throw new PersistenceProviderException(e);
		}
	}

	@Override
	public void loadByStorage(String storageName, LoadCallback callback) throws PersistenceProviderException {
		try {
			storages.get(storageName).load(null, callback);
		} catch (Exception e) {
			throw new PersistenceProviderException(e);
		}
	}

	@SuppressWarnings("unused")
	public void loadEntities(List<String> entityNames, LoadCallback callback) throws PersistenceProviderException {
		for (Storage storage : storages.values())
			storage.load(entityNames, callback);
	}

	@Override
	public Map<String, Long> loadMaxIds() throws PersistenceProviderException {
		try {
			Map<String, Long> result = new HashMap<String, Long>();
			for (Storage storage : storages.values()) 
				for (Map.Entry<String, Long> e : storage.loadMaxIds().entrySet()) {
					Long maxId = result.get(e.getKey());
					if (maxId == null || e.getValue() > maxId)
						maxId = e.getValue();
					result.put(e.getKey(), maxId);
				}
			return result;
		} catch (Exception e) {
			throw new PersistenceProviderException(e);
		}
	}
	
	@SuppressWarnings("unused")
	public String sql(String unitName, List<Long> ids) {
		return storages.get(unitStorage(unitName)).sql(unitName, ids);
	}

	@Override
	public Long lastSaved() throws PersistenceProviderException {
		try {
			return lastSavedStorage.lastSaved();
		} catch (Exception e) {
			throw new PersistenceProviderException(e);
		}
	}

	@Override
	public List<String> storage() {
		return new ArrayList<String>(storages.keySet());
	}

	@Override
	public String unitStorage(String unitName) {
		return unitStorageMapper.map(unitName);
	}

	@Override
	public void compact() {
	}

	@Override
	public Connection open() {
		return new Connection() {
			@Override
			public void close() {
			}
		};
	}
}
