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
package com.px100systems.data.plugin.persistence;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import com.px100systems.data.core.InMemoryDatabase;
import com.px100systems.data.core.EntityDescriptor;
import com.px100systems.data.plugin.storage.EntityCursor;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Required;
import com.px100systems.data.core.RawRecord;
import com.px100systems.data.plugin.persistence.PersistenceProvider.Connection;

/**
 * Persistence server aka "persister".<br>
 * <br>
 * All servers are independent and access the cluster simultaneously.<br>
 * Not every in-memory cluster member would have a collocated persistence server.<br>
 * Persisters are tenant-agnostic: they save everything in the cluster to the persistent database, so those databases can be synchronized later.
 * If that presents a problem, shard tenants.<br>
 * <br>
 * All persistence servers are guaranteed synchronized before the cold start:<br>
 * <ul>
 *   <li>data copied from one to another database or loaded from the same backup files
 *   <li>database sync should use the newest DB and have fail-safety: backups before the operation, scripts to check, etc.
 * </ul>
 * <br>
 * Persisters merge later inserts and updates by skipping the record if it is updated later than the PersistenceLogEntry being processed.
 * That breaks transactional consistency until the persister caught up or finished all accumulated write-behinds.
 * However persisted data is not expected to be current in between write behind cycles (the risk we take), as there are no reads until the next (explicit) cluster cold load.
 * Correlated transactional updates should be rare (avoided) anyway - operations should be atomic in terms of data beans (documents, WIs, etc.)
 * If that presents a problem, the mode can be turned off.<br>
 * <br>
 *
 * <b>Configuration:</b><br>
 * <ul>
 *   <li>provider - persistence provider that reads and writes data to the datrabase
 *   <li>mergeUpdates - (enabled by default - rarely changed) try to merge accumulated (later) updates after inserts and deletes after updates for the same record to minimize the number of database operations
 *   <li>logEntriesPerTransaction - how many update database operations to batch in one database transaction
 *   <li>writeBehindSeconds - write-behind interval (default is 2 minutes)
 *   <li>cleanupHours - the period to keep update log entries in memory in case some (other) persisters were slow, down, and otherwise did not catch up
 *      see DataStorage.maxPersistenceDelayHours parameter - it controls the maximum persistence delay for any persister (if one caught up, that is good enough)
 *   <li>compactHours - only relevant for MapDB and similar persistence providers (has no effect for JDBC providers)
 * </ul>
 *
 * @version 0.3 <br>Copyright (c) 2015 Px100 Systems. All Rights Reserved.<br>
 * @author Alex Rogachevsky
 */
public class DiskPersistence {
	private static Log log = LogFactory.getLog(DiskPersistence.class);

	private PersistenceProvider provider;
	private InMemoryDatabase storage;
	
	private ScheduledThreadPoolExecutor scheduler = null; 
	private boolean mergeUpdates = true;
	private int logEntriesPerTransaction = 20;
	private int writeBehindSeconds = 120; 
	private int cleanupHours = 25;  
	private int compactHours = 3; 
	
	private Long lastSaveTime = null;
	
	public DiskPersistence() {
	}
	
	/**
	 * Used for one-node debugging - initializing the database instead of loading it after significant entity changes 
	 */
	public void init() { 
		provider.init();
	}
	
	/**
	 * Starts server's main loop: picks up where it left off (after the last persist time) as PersistenceLogEntries are kept for N (e.g. 24) hours.<br>
	 * start() should only be called once.<br>
	 * <br>
	 * Generally persistence server catch-ups should be fine (especially with merges), however large tenant deployments may generate frequent updates<br>
	 * Every restarted persistence server would put a (read) load on the cluster similar to DDoS attacks<br>
	 * Possible solution (for the future):
	 * <ul>
     *   <li>read persistence log entries from the cluster, but the data itself from another (paired) working database that is guaranteed ahead by lastSaveTime
     *   <li>if such server is running (the restarted is not the last/only one) - otherwise normal catchup
     *     automatic cluster-wide discovery of such running persistence server (persisters should register their credentials in the cluster)
     *   <li>should slow down write-behinds due to possible read/write collisions, but databases handle it well, and write-behinds are running in the background
	 * </ul>
	 */
	public void start() {
		try {
			Long ls = provider.lastSaved();
			lastSaveTime = ls == null ? 0L : ls;
		} catch (PersistenceProviderException e) {
			throw new RuntimeException(e);
		}
		resume();
	}

	/**
	 * Stops the server
	 */
	public void stop() {
		if (scheduler != null) {
			scheduler.shutdown();
			scheduler = null;
			log.info("Stopped in-memory data storage persistence");
		}
	}

	/**
	 * Resumes the stopped server
	 */
	public void resume() {
		if (scheduler != null)
			scheduler.shutdown();
		scheduler = new ScheduledThreadPoolExecutor(1); // One thread should be fine. Write-behind nature is single-threaded

		persist();

		scheduler.scheduleAtFixedRate(new Runnable() {
			@Override
			public void run() {
				persist();
			}
		}, writeBehindSeconds, writeBehindSeconds, TimeUnit.SECONDS);
		
		scheduler.scheduleAtFixedRate(new Runnable() {
			@Override
			public void run() {
				cleanup();
			}
		}, cleanupHours, cleanupHours, TimeUnit.HOURS);
		
		scheduler.scheduleAtFixedRate(new Runnable() {
			@Override
			public void run() {
				compact();
			}
		}, compactHours, compactHours, TimeUnit.HOURS);
		
		log.info("Started in-memory data storage persistence");
	}

	/**
	 * Flush the remaining data at the time of this call) to disk. Called by the Spring container shutdown via DataStorage.destroy().
	 */
	public void flush() {
		if (scheduler != null) {
			log.info("Flushing in-memory data storage to disk");
			scheduler.shutdown();
			persist();
			stop();
		}
	}

	/**
	 * Storage load on startup. Storage loosely represents a database "table" and hosts many different entities.<br>
	 * Storages can and are loaded independently in parallel if more than one persister exists in teh cluster.
	 * @param storageName storage name
	 */
	public void load(String storageName) {
		log.info("Started loading " + storageName);
		
		final List<RawRecord> buffer = new ArrayList<RawRecord>();
		final InMemoryDatabase.Loader loader = storage.loader();
		try {
			provider.loadByStorage(storageName, new PersistenceProvider.LoadCallback() {
				@Override
				public void process(RawRecord record) {
					buffer.add(record);
					if (buffer.size() > 100) {
						loader.store(buffer);
						buffer.clear();
					}
				}
			});
			
			if (!buffer.isEmpty()) {
				loader.store(buffer);
				buffer.clear();
			}
		} catch (PersistenceProviderException e) {
			throw new RuntimeException(e);
		} finally {
			loader.close();
		}

		log.info("Finished loading " + storageName);
	}

	/**
	 * ID generator load.
	 * @return a map of max IDs in the database - per ID generator name. One ID generator is typically shared by many entities.
	 */
	public Map<String, Long> loadMaxIds() {
		try {
			return provider.loadMaxIds();
		} catch (PersistenceProviderException e) {
			throw new RuntimeException(e);
		}
	}

	public List<String> storages() {
		return provider.storage();
	}
	
	@SuppressWarnings("unused")
	public String unitStorage(String unitName) {
		return provider.unitStorage(unitName);
	}
	
	private void persist() {
		int processedLogEntries = 0;
		Long fromTime = lastSaveTime;
		
		Map<String, RawRecord> insertsOrUpdates = new HashMap<String, RawRecord>();
		Map<String, EntityDescriptor> deletes = new HashMap<String, EntityDescriptor>();
		int count = 0;
		
		Connection conn = provider.open();
		try {
			Long lastTransactionTime = null;

			EntityCursor<PersistenceLogEntry> cursor = storage.getPersistenceLog(fromTime);
			try {
				for (Iterator<PersistenceLogEntry> i = cursor.iterator(); i.hasNext();) {
					while (i.hasNext()) {
						if (scheduler == null)
							return;

						PersistenceLogEntry transaction = i.next();
						lastTransactionTime = transaction.getTime();
						Date transactionTime = new Date(lastTransactionTime);

						List<PersistenceLogEntry.PersistenceLogRecord> insertsUpdates = new ArrayList<PersistenceLogEntry.PersistenceLogRecord>(transaction.getNewEntities());
						insertsUpdates.addAll(transaction.getUpdatedEntities());
						for (PersistenceLogEntry.PersistenceLogRecord r : insertsUpdates) {
							RawRecord record = storage.getPersistenceRecord(r.getUnitName(), r.getId());
							if (record != null) {
								if (mergeUpdates && record.getLastUpdate().after(transactionTime))
									continue;

								String key = r.getUnitName() + "_" + r.getId();
								if (insertsOrUpdates.containsKey(key) || deletes.containsKey(key))
									continue;

								insertsOrUpdates.put(key, record);
							}
						}

						for (PersistenceLogEntry.PersistenceLogRecord r : transaction.getDeletedEntities()) {
							String key = r.getUnitName() + "_" + r.getId();
							if (insertsOrUpdates.containsKey(key))
								insertsOrUpdates.remove(key);

							if (!deletes.containsKey(key))
								deletes.put(key, new EntityDescriptor(null, r.getId(), r.getUnitName()));
						}

						count++;
						if (count >= logEntriesPerTransaction) {
							if (scheduler == null)
								return;
							provider.transactionalSave(conn, insertsOrUpdates.values(), deletes.values(), lastTransactionTime);
							lastSaveTime = lastTransactionTime;
							storage.logSaveTime(lastSaveTime);

							insertsOrUpdates.clear();
							deletes.clear();
							processedLogEntries += count;
							count = 0;
						}
					}

					cursor.close();
					cursor = storage.getPersistenceLog(lastTransactionTime);
					i = cursor.iterator();
				}
			} finally {
				cursor.close();
			}
			
			if (count > 0) {
				if (scheduler == null)
					return;
				provider.transactionalSave(conn, insertsOrUpdates.values(), deletes.values(), lastTransactionTime);
				lastSaveTime = lastTransactionTime;
				storage.logSaveTime(lastSaveTime);
				processedLogEntries += count;
			}
		} catch (PersistenceProviderException e) {
			// Ignoring errors (e.g. database down) for now: the server will retry during its next write-behind
		} finally {
			conn.close();
		}
		
		log.info("Persisted " + processedLogEntries + " transactions since " + fromTime);
	}
	
	/**
	 * Restarting crashed persistence servers is expected within cleanupHours (typically 24).
	 * Otherwise the system is shut down via DataStorage.emergencyShutdown().
	 */
	private void cleanup() {
		storage.purgePersistenceLog(cleanupHours);
	}

	/**
	 * Compact the storage for providers that support that.
	 */
	public void compact() {
		provider.compact();
	}

	@Required
	public void setProvider(PersistenceProvider provider) {
		this.provider = provider;
	}

	public void setStorage(InMemoryDatabase storage) {
		this.storage = storage;
	}

	@SuppressWarnings("unused")
	public void setMergeUpdates(boolean mergeUpdates) {
		this.mergeUpdates = mergeUpdates;
	}

	@SuppressWarnings("unused")
	public void setLogEntriesPerTransaction(int logEntriesPerTransaction) {
		this.logEntriesPerTransaction = logEntriesPerTransaction;
	}

	public void setWriteBehindSeconds(int writeBehindInterval) {
		this.writeBehindSeconds = writeBehindInterval;
	}

	public void setCleanupHours(int cleanupHours) {
		this.cleanupHours = cleanupHours;
	}

	public int getCleanupHours() {
		return cleanupHours;
	}

	@SuppressWarnings("unused")
	public void setCompactHours(int compactHours) {
		this.compactHours = compactHours;
	}
}
