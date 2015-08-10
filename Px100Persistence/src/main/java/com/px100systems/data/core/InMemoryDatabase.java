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
package com.px100systems.data.core;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.google.gson.Gson;
import com.px100systems.data.plugin.storage.EntityCursor;
import com.px100systems.data.plugin.storage.InMemoryStorageLoader;
import com.px100systems.util.serialization.SerializationDefinition;
import org.apache.commons.io.FileUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Required;
import com.px100systems.data.plugin.persistence.DiskPersistence;
import com.px100systems.data.plugin.persistence.PersistenceLogEntry;
import com.px100systems.data.plugin.persistence.PersistenceProvider;
import com.px100systems.data.plugin.persistence.PersistenceProvider.Connection;
import com.px100systems.data.plugin.storage.InMemoryStorageProvider;
import com.px100systems.data.utility.BackupFile;
import com.px100systems.util.SpringELCtx;

/**
 * In-memory database with optional write-behind<br>
 * <br>
 * This API wrapper encapsulates distributed in-memory storage implemented by InMemoryStorageProvider with an optional write-behind persistence server.<br>
 * The general architecture is flat: multiple Web-servers collocated with in-memory storage collocated with optional persistence servers.<br>
 * <br>
 * In case of low risk tolerance (e.g. government standards) the following actions can be taken:<br>
 * 1. Add more redundancy on cheap hardware (if the client accepts/supplies it and can ensure the proper maintenance)<br>
 * 2. More frequent write-behinds: even one-minute ones that gets close to write-through frequencey is still faster than conventional disk persistence
 *    because the data is cached in memory and the system is read-mostly.<br>
 * 3. In the worst case (no risk allowed at all) write-through can be used - still faster than traditional SQL databases.<br>
 * <br>
 * Generally all members of the cluster are supposed to be running "forever": between cold cluster restarts due to periodic releases
 * or hot starts (joining the running cluster) after the member crash.<br>
 * <br>
 * InMemoryDatabase and JCache: we don't use JCache spec and don't leverage any (e.g. Ignite's) built-in persistence due to the following reasons:<br>
 * 1. Conceptually it is not a cache, so there are no read-through and other cache-specicifc operations.<br>
 * 2. When used as a data grid, all data entries need to be loaded for queries, etc.<br>
 * 3. JCache has no concept of redundant persister support: detecting stalled persistence, catching up, etc.<br>
 * 4. JCache has no concept of multi-process loading when different nodes load different entities<br>
 * <br>
 * Spring configuration:<br>
 * Some parameters like persistenceServer are local, while some should be shared among all cluster members (persistence, maxPersistenceDelayHours, tenants, etc.).
 * They should refer to beans defined in other config files residing in one shared directory (on a distributed file system)
 * which is made part of the JVM/server's classpath just like the local "externalConfig" directory.<br>
 * <br>
 * <b>Configuration:</b><br>
 * <ul>
 *   <li>persistence - persistence mode: Load (write-through) or Write-Behind. Write-Behind is not advised for "development mode",
 *   <li>maxPersistenceDelayHours - maximum period when the system detects that the write-behind persistence (if enabled) has stalled -
 *     no updates were made over that period since the last update time.
 *   <li>backupDirectory - directory to dump teh content of the cluster in case of automatic emergency shutdown (typically when the persistence stalled or manually invoked)
 *   <li>persistenceServer - persister, responsible at least for loading the database on startup. Can be null for non-persistent nodes (that don't load or write to the database).
 *     Not all nodes in teh claster need to have (collocated) persisters. However it is already collocated (specified in the RuntimeStorage config)
 *     in case of write-through, so it can (and should) be sued for loading as well.
 * </ul>
 *
 * @version 0.3 <br>Copyright (c) 2015 Px100 Systems. All Rights Reserved.<br>
 * @author Alex Rogachevsky
 */
public class InMemoryDatabase extends DatabaseStorage {
	private static final String CLUSTER_EVENTS = "DataStorageClusterEvents";
	private static InMemoryDatabase INSTANCE; // AlexR: Ignite requires event handlers to be serializable. This is the simplest way to pass it an instance. It should be a singleton anyway

	private static final long STATUS_UNINITIALIZED = 0L;
	private static final long STATUS_LOADING = 1L;
	private static final long STATUS_ACTIVE = 2L;

	private static Log log = LogFactory.getLog(InMemoryDatabase.class);
	private boolean active = false;
	private boolean stopped = false;

	public enum PersistenceMode {None, Load, WriteBehind}
	private PersistenceMode persistence = PersistenceMode.WriteBehind; // persistence can be turned off for internal (e.g. Hazelcast's) write-through: make sure no logs, no cleanup, etc.

	private int maxPersistenceDelayHours = 24;
	private String backupDirectory;

	private DiskPersistence persistenceServer;

	public interface DataStorageEvents {
		void emergencyShutdownStarted(String hostName);
		void emergencyShutdownFinished(String hostName);
		void clusterStarted();
		void clusterStopped();
	}
	private DataStorageEvents outgoingEvents;

	public InMemoryDatabase() {
	}

	/**
	 * Persistence mode
	 * @param persistence persistence mode
	 */
	public void setPersistence(PersistenceMode persistence) {
		this.persistence = persistence;
	}

	/**
	 * Maximum period when the system detects that the write-behind persistence (if enabled) has stalled - no updates were made over that period since the last update time.
	 * @param maxPersistenceDelayHours max delay in hours - typically 24 - one hour less than the persiter's log cleanup period
	 */
	public void setMaxPersistenceDelayHours(int maxPersistenceDelayHours) {
		this.maxPersistenceDelayHours = maxPersistenceDelayHours;
	}

	/**
	 * Directory to dump binary grid content in case of emregency server shutdown due to stalled persistence.
	 * @param backupDirectory local server directory
	 */
	@Required
	public void setBackupDirectory(String backupDirectory) {
		this.backupDirectory = backupDirectory;
	}

	/**
	 * Critical events to monitor: emergency shutdown, etc. (rarely used)
	 * @param outgoingEvents callback
	 */
	public void setOutgoingEvents(DataStorageEvents outgoingEvents) {
		this.outgoingEvents = outgoingEvents;
	}

	/**
	 * Is the database active i.e. not stopped and persistence not stalled
	 * @return the database state
	 */
	public boolean isActive() {
		if (!active && !stopped)
			active = getRuntimeStorage().getProvider().getAtomicLong("status", null) == STATUS_ACTIVE;
		return active && !persistenceStalled();
	}

	/**
	 * Persister for loading and write-behind
	 * @param persistenceServer collocated persistence server aka "persister"
	 */
	public void setPersistenceServer(DiskPersistence persistenceServer) {
		this.persistenceServer = persistenceServer;
		persistenceServer.setStorage(this);
	}

	public static class MessageHandler implements InMemoryStorageProvider.MessageCallback<String> {
		@Override
		public void process(String message) {
			INSTANCE.processMessage(message);
		}
	}

	private void processMessage(String message) {
		if (message.equals("clusterStart")) {
			active = true;
			if (outgoingEvents != null)
				outgoingEvents.clusterStarted();
		} else if (message.equals("clusterStop")) {
			active = false;
			stopped = true;
			if (persistenceServer != null) {
				persistenceServer.stop();
				persistenceServer = null;
			}
			if (outgoingEvents != null)
				outgoingEvents.clusterStopped();
		}
	}

	@Override
	public void afterPropertiesSet() throws Exception {
		INSTANCE = this;
		SerializationDefinition.register(getRuntimeStorage().getProvider().createPersistenceLogEntry().getClass());
		super.afterPropertiesSet();
	}

	@SuppressWarnings("unchecked")
	@Override
	public void init(List<EntityInfo> entities, boolean initializeData) {
		if (persistenceServer != null && persistenceServer.getCleanupHours() <= maxPersistenceDelayHours)
			throw new RuntimeException("Persistence server cleanup interval should be greater than maxPersistenceDelayHours");

		getRuntimeStorage().getProvider().registerMessageCallback(CLUSTER_EVENTS, new MessageHandler());

		if (getRuntimeStorage().getProvider().getAtomicLong("status", null) == STATUS_UNINITIALIZED) {
			Lock lock = getRuntimeStorage().getProvider().lock("coldClusterStart", 60L * 60L * 1000L);
			if (getRuntimeStorage().getProvider().getAtomicLong("status", null) == STATUS_UNINITIALIZED) {
				getRuntimeStorage().getProvider().setAtomicLong("status", null, persistence != PersistenceMode.None ? STATUS_LOADING : STATUS_ACTIVE);

				getRuntimeStorage().getProvider().createMap(PersistenceLogEntry.class, PersistenceLogEntry.UNIT_NAME,
					Entity.indexes(PersistenceLogEntry.class), false); // "Transient" logically, however needs to be transactional, not atomic (Ignite-specific)
				getRuntimeStorage().createIdGenerator(PersistenceLogEntry.UNIT_NAME, 0L);

				logSaveTime(new Date().getTime());

				for (EntityInfo ei : entities)
					getRuntimeStorage().getProvider().createMap(ei.getEntityClass(), ei.getUnitName(), ei.getRawIndexes(), false);
			}
			lock.unlock();
		}

		if (persistenceServer != null)
			if (getRuntimeStorage().getProvider().getAtomicLong("status", null) == STATUS_LOADING) {
				if (initializeData)
					persistenceServer.init();

				List<String> storages = persistenceServer.storages();
				List<Lock> locks = new ArrayList<Lock>();
				try {
					for(String storage : storages) {
						Lock lock = getRuntimeStorage().getProvider().lock("loadStorage_" + storage, 0L);
						if (lock != null) {
							locks.add(lock);
							persistenceServer.load(storage);

							Lock countLock = getRuntimeStorage().getProvider().lock("loadStorageCount", 60L * 60L * 1000L);
							Long count = getRuntimeStorage().getProvider().getAtomicLong("loadedStorages", null);
							count = count == 0 ? 1 : count + 1;
							getRuntimeStorage().getProvider().setAtomicLong("loadedStorages", null, count);
							countLock.unlock();
						}
					}
				} finally {
					for (Lock lock : locks)
						lock.unlock();
				}

				// the last loading member starts the cluster
				Long count = getRuntimeStorage().getProvider().getAtomicLong("loadedStorages", null);
				if (count == storages.size()) {

					for (Map.Entry<String, Long> maxId : persistenceServer.loadMaxIds().entrySet())
						getRuntimeStorage().createIdGenerator(maxId.getKey(), maxId.getValue());
					log.info("Loaded ID generators");

					getRuntimeStorage().getProvider().setAtomicLong("status", null, STATUS_ACTIVE);
					getRuntimeStorage().getProvider().broadcastMessage(CLUSTER_EVENTS, "clusterStart");
				}
			} else
				active = true; // join existing cluster w/o loading

		if (persistence == PersistenceMode.WriteBehind && persistenceServer != null)
			persistenceServer.start();
	}

	/**
	 * Graceful shutdown - useless with write-through. Makes sure the persisters persisted everything to the (relational) database.
	 * Always call it manually in Prod - hook on the Web App shutdown, not the Spring/JVM one
	 */
	@Override
	public void shutdown() {
		active = false;
		stopped = true;
		if (persistenceServer != null)
			persistenceServer.flush();
		getRuntimeStorage().getProvider().shutdown();
	}

	@Override
	protected <T> T get(String unitName, Class<T> cls, Long id) {
		return getRuntimeStorage().getProvider().get(unitName, id);
	}

	@Override
	protected long count(String unitName, Class<?> cls, Criteria criteria) {
		return getRuntimeStorage().getProvider().count(unitName, cls, criteria);
	}

	@Override
	protected <T> List<T> search(String unitName, Class<T> cls, Criteria criteria, List<String> orderBy, Integer limit) {
		return getRuntimeStorage().getProvider().search(unitName, cls, criteria, orderBy, limit);
	}

	@Override
	protected <T> EntityCursor<T> search(String unitName, Class<T> cls, Criteria criteria, List<String> orderBy) {
		return getRuntimeStorage().getProvider().search(unitName, cls, criteria, orderBy);
	}

	@Override
	protected List<EntityDescriptor> save(List<StoredBean> inserts, List<StoredBean> updates, List<Delete> deletes, List<InPlaceUpdate<?>> inPlaceUpdates) throws DataStorageException {
		return getRuntimeStorage().getProvider().save(inserts, updates, deletes, inPlaceUpdates, false);
	}

	public void emergencyShutdown() {
		getRuntimeStorage().getProvider().broadcastMessage(CLUSTER_EVENTS, "clusterStop");
		Gson gson = RawRecord.createGson();

		try {
			if (outgoingEvents != null)
				outgoingEvents.emergencyShutdownStarted(InetAddress.getLocalHost().getHostName());
		} catch (UnknownHostException e) {
			throw new RuntimeException(e);
		}

		try {
			File baseDir = new File(backupDirectory);
			if (baseDir.exists())
				FileUtils.deleteDirectory(baseDir);
			//noinspection ResultOfMethodCallIgnored
			baseDir.mkdirs();

			for (Class entityClass : getConfiguredEntities().values())
				for (Integer tenantId : getTenants().keySet()) {
					String unitName = Entity.unitFromClass(entityClass, tenantId);
					EntityCursor<?> cursor = getRuntimeStorage().getProvider().search(unitName, entityClass, null, null);
					try {
						Iterator<?> i = cursor.iterator();
						if (i.hasNext()) {
							BackupFile file = new BackupFile(backupDirectory, unitName);
							try {
								while (i.hasNext())
									file.write(new RawRecord((Entity)i.next(), gson));
							} finally {
								file.close();
							}
						}
					} finally {
						cursor.close();
					}
				}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		try {
			if (outgoingEvents != null)
				outgoingEvents.emergencyShutdownFinished(InetAddress.getLocalHost().getHostName());
		} catch (UnknownHostException e) {
			throw new RuntimeException(e);
		}

		log.info("Backed up data to " + backupDirectory);
	}

	/**
	 * Used by Restore utility. Transfers emergency shutdown data to the database backing the data grid.
	 * @param f one of backup files
	 * @param persister persistence server
	 */
	public static void readBackupFile(File f, final PersistenceProvider persister) {
		BackupFile file = new BackupFile(f);
		final Connection conn = persister.open();
		try {
			final List<RawRecord> buffer = new ArrayList<RawRecord>();
			file.read(new PersistenceProvider.LoadCallback() {
				@Override
				public void process(RawRecord record) {
					buffer.add(record);
					if (buffer.size() > 100)
						try {
							persister.transactionalSave(conn, buffer);
							buffer.clear();
						} catch (Exception e) {
							throw new RuntimeException(e);
						}
				}
			});

			if (!buffer.isEmpty())
				try {
					persister.transactionalSave(conn, buffer);
					buffer.clear();
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
		} catch (IOException e) {
			throw new RuntimeException(e);
		} finally {
			file.close();
			conn.close();
		}
	}

	public static class Loader {
		private InMemoryStorageLoader loader;

		public Loader(InMemoryStorageProvider storage) {
			loader = storage.loader();
		}

		public void store(List<RawRecord> ins) {
			Gson gson = RawRecord.createGson();
			List<StoredBean> inserts = new ArrayList<StoredBean>();
			for (RawRecord r : ins)
				try {
					inserts.add(r.toEntity(gson));
				} catch (Exception e) {
					// ignoring errors caused by deserializing classes that no longer exist
				}
			if (!inserts.isEmpty())
				loader.store(inserts);
		}

		public void close() {
			loader.close();
		}
	}

	public Loader loader() {
		return new Loader(getRuntimeStorage().getProvider());
	}

	/**
	 * Used internally by pesisters.
	 * @param fromTime start time
	 * @return persistence log - records to persist.
	 */
	public EntityCursor<PersistenceLogEntry> getPersistenceLog(Long fromTime) {
		return getRuntimeStorage().getProvider().search(PersistenceLogEntry.UNIT_NAME, PersistenceLogEntry.class, Criteria.gt("time", fromTime),
			Collections.singletonList("time ASC"));
	}

	public RawRecord getPersistenceRecord(String unitName, Long id) {
		Entity entity = getRuntimeStorage().getProvider().get(unitName, id);
		return entity == null ? null : new RawRecord(entity, RawRecord.createGson());
	}

	public void logSaveTime(Long time) {
		getRuntimeStorage().getProvider().setAtomicLong("lastPersist", null, time); // Should be fast enough. If a simple atomic update presents a problem, use no-wait locks
	}

	/**
	 * Used internally by pesisters to cleanup (purge) the persistence log. Typically the cluster-wide log will keep N hours of data for
	 * slow persisters to catch up.
	 * @param hoursBeforeLastSave start purge time - hours before the last/fresh log entry
	 */
	public void purgePersistenceLog(int hoursBeforeLastSave) {
		Lock lock = getRuntimeStorage().getProvider().lock("purgePersistenceLog", 0L);
		if (lock != null) {
			Date cleanupTime = SpringELCtx.dateArithmetic(new Date(getRuntimeStorage().getProvider().getAtomicLong("lastPersist", null)), "-" + hoursBeforeLastSave + "h");
			try {
				getRuntimeStorage().getProvider().save(new ArrayList<StoredBean>(), new ArrayList<StoredBean>(),
					Collections.singletonList(new Delete(PersistenceLogEntry.class, PersistenceLogEntry.UNIT_NAME, Criteria.lt("time", cleanupTime.getTime()))),
					new ArrayList<InPlaceUpdate<?>>(), true);
			} catch (DataStorageException e) {
				throw new RuntimeException(e); // Unexpected - something bad
			}
			lock.unlock();
			log.info("Purged persistence log records older than " + cleanupTime);
		}
	}

	protected boolean persistenceStalled() {
		if (persistence == PersistenceMode.WriteBehind) {
			Date threshold = SpringELCtx.dateArithmetic(new Date(), "-" + maxPersistenceDelayHours + "h");
			Date lastSaveTime = new Date(getRuntimeStorage().getProvider().getAtomicLong("lastPersist", null));
			if (lastSaveTime.before(threshold) && !getRuntimeStorage().getProvider().search(PersistenceLogEntry.UNIT_NAME, PersistenceLogEntry.class,
				Criteria.gt("time", lastSaveTime.getTime()), null, 10).isEmpty()) {
				log.info("Persistence has stalled");
				emergencyShutdown();
				return true;
			}
		}
		return false;
	}

	@Override
	protected void afterSave(Date now, List<StoredBean> allInserts, List<StoredBean> allUpdates, List<EntityDescriptor> deletes,
							 List<InPlaceUpdate<?>> inPlaceUpdates) throws DataStorageException {
		if (persistence == PersistenceMode.WriteBehind) {
			Set<String> keys = new HashSet<>();

			List<PersistenceLogEntry.PersistenceLogRecord> pInserts = new ArrayList<PersistenceLogEntry.PersistenceLogRecord>();
			for (StoredBean e : allInserts)
				pInserts.add(new PersistenceLogEntry.PersistenceLogRecord(e.unitName(), e.getId()));

			List<PersistenceLogEntry.PersistenceLogRecord> pUpdates = new ArrayList<PersistenceLogEntry.PersistenceLogRecord>();
			for (StoredBean e : allUpdates) {
				String key = e.unitName() + "/" + e.getId();
				if (!keys.contains(key)) {
					keys.add(key);
					pUpdates.add(new PersistenceLogEntry.PersistenceLogRecord(e.unitName(), e.getId()));
				}
			}

			List<PersistenceLogEntry.PersistenceLogRecord> pDeletes = new ArrayList<PersistenceLogEntry.PersistenceLogRecord>();
			for (EntityDescriptor d : deletes) {
				String key = d.getUnit() + "/" + d.getId();
				if (!keys.contains(key)) {
					keys.add(key);
					pDeletes.add(new PersistenceLogEntry.PersistenceLogRecord(d.getUnit(), d.getId()));
				}
			}

			for (InPlaceUpdate<?> u : inPlaceUpdates) {
				String key = u.getUnitName() + "/" + u.getId();
				if (!keys.contains(key)) {
					keys.add(key);
					pUpdates.add(new PersistenceLogEntry.PersistenceLogRecord(u.getUnitName(), u.getId()));
				}
			}

			PersistenceLogEntry log = createPersistenceLogEntry();
			log.setId(getRuntimeStorage().getProvider().generateId(log.unitName()));
			log.setTime(now.getTime());
			log.setNewEntities(pInserts);
			log.setUpdatedEntities(pUpdates);
			log.setDeletedEntities(pDeletes);

			allInserts.clear();
			allUpdates.clear();
			allInserts.add(log);
			getRuntimeStorage().getProvider().save(allInserts, allUpdates, new ArrayList<Delete>(), new ArrayList<InPlaceUpdate<?>>(), true);
		}
	}

	protected PersistenceLogEntry createPersistenceLogEntry() {
		return getRuntimeStorage().getProvider().createPersistenceLogEntry();
	}
}
