package com.px100systems.data.plugin.persistence;

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
import java.util.Collection;
import java.util.List;
import java.util.Map;
import com.px100systems.data.core.EntityDescriptor;
import com.px100systems.data.core.RawRecord;

/**
 * On-disk persistence provider for in-memory data grids responsible for data loading, write-thropugh and write-behind.<br>
 * <br>
 * Databases should be either in-process (H2 or MapDB) or at the very least local (MySQL, Oracle, etc.) to minimize network traffic.<br>
 * <br>
 * Any SQL or NoSQL provider should translate the passed unit name (which contains tenant ID) into a physical table of DB file name aka "storage"<br>
 * according to its configuration: typically a set of wildcarded/regex mappings of unit names to table/file names. See {@link RegexUnitStorageMapper}.
 * Technically all data could reside in one file/table, however fragmentation (block size) matters.<br>
 * <br>
 * SQL provider candidates: MySQL (fast), H2<br>
 * NoSQL solution: MapDB (slow)<br>
 * Custom NoSQL solution (if anything above is inadequate): binary files mimicking filesystems (block-based), etc.<br>
 *  
 * @version 0.3 <br>Copyright (c) 2015 Px100 Systems. All Rights Reserved.<br>
 * @author Alex Rogachevsky
*/
public interface PersistenceProvider {
	/**
	 * Only relevant for NoSQL (MapDB, etc.) providers. The Connection accumulates the list of open DBs (files) to close at the end
	 * SQL providers rely on JDBC DataSource (pooled or always open)  
	 */
	interface Connection {
		void close();
	}
	
	/**
	 * Opens the DB connection - only relevant for NoSQL databases that should close all opened DB files they opened after the series of transactionalSaves()  
	 */
	Connection open();
	
	/**
	 * Normal persistence. Saves the time operation was performed in a special data structure (DB map or SQL table)<br>
	 * NoSQL: creates map if it does not exist (should be automatic).
	 * 
	 * @param conn - [NoSQL] connection
	 * @param insertsOrUpdates list of records to save
	 * @param deletes list of record IDs to delete
	 * @param timestamp current time
	 * @throws PersistenceProviderException
	 */
	void transactionalSave(Connection conn, Collection<RawRecord> insertsOrUpdates, Collection<EntityDescriptor> deletes, Long timestamp) throws PersistenceProviderException;
	
	/**
	 * Restoring database from the dump files.
	 * NoSQL: creates maps if it does not exist (should be automatic).
	 * 
	 * @param conn - [NoSQL] connection
	 * @param inserts - a list of reasonable size. It is the caller's responsibility to break long lists into smaller chunks.
	 */
	void transactionalSave(Connection conn, List<RawRecord> inserts) throws PersistenceProviderException;  
	
	interface LoadCallback {
		void process(RawRecord record);
	}
	
	/**
	 * Loads the entire named storage structure (SQL table or MapDB file).
	 * SQL providers should use JDBC Statement.setFetchSize() feature to read long tables.
	 * NoSQL providers (MapDB) apparently handle it automatically or with minimal tuning.
	 *
	 * @param storageName storage name
	 * @param callback how to load that storage record
	 * @throws PersistenceProviderException
	 */
	void loadByStorage(String storageName, LoadCallback callback) throws PersistenceProviderException;
	
	/**
	 * Returns max Ids per ID generator.
	 */
	Map<String, Long> loadMaxIds() throws PersistenceProviderException;
	
	/**
	 * The list of all storage structures (SQL table or MapDB file)
	 */
	List<String> storage();
	
	/**
	 * Returns unit's storage.
	 *
	 * @param unitName unit name
	 * @return storage name
	 */
	String unitStorage(String unitName);
	
	/**
	 * Used for cleaning the database before restoring it from dump files.<br>
	 * Reinitializes the database e.g. deleting/creating the schema in MySQL or all files in the specified directory in H2 or NoSQL (MapDB).<br>
	 * Recreates storage structures.<br>
	 * Recreates the table for last save time (SQL only).<br>
	 * <br>
	 * SQL: simple DDL w/o indices. The table should have its own (auto-incremented or sequenced) PK instead of using entities IDs.<br>
	 */
	void init();
	
    /**
     * Manually compacts a NoSQL database like MapDB. Quartz-scheduled by the server. Ignored by SQL providers.
     */
    void compact();
    
    /**
     * Time of last save.<br>
     * SQL providers keep it in a separate table.
     * NoSQL providers keep it in a one-entry map located in the most frequently used storage structure
     */
    Long lastSaved() throws PersistenceProviderException;
}
