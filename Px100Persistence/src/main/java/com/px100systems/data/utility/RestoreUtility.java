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
package com.px100systems.data.utility;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.context.support.FileSystemXmlApplicationContext;
import com.px100systems.data.core.InMemoryDatabase;
import com.px100systems.data.core.RawRecord;
import com.px100systems.data.plugin.persistence.PersistenceProvider;

/**
 * Restore utility. Also used to compare backup files against the persistent store e.g. database.<br>
 * <b>Usage:</b> {@code java -cp ... com.px100systems.data.utility.RestoreUtility <springXmlConfigFile> <persisterBeanName> <backupDirectory> [compare]}<br>
 * 
 * @version 0.3 <br>Copyright (c) 2015 Px100 Systems. All Rights Reserved.<br>
 * @author Alex Rogachevsky
 */
public class RestoreUtility {

	public RestoreUtility() {
	}

	public static void main(String[] args) {
		if (args.length < 3) {
			System.err.println("Usage: java -cp ... com.px100systems.data.utility.RestoreUtility " +
				"<springXmlConfigFile> <persisterBeanName> <backupDirectory> [compare]");
			return;
		}

		FileSystemXmlApplicationContext ctx = new FileSystemXmlApplicationContext("file:" + args[0]);
		try {
			PersistenceProvider persister = ctx.getBean(args[1], PersistenceProvider.class);
			
			File directory = new File(args[2]);
			if (!directory.isDirectory()) {
				System.err.println(directory.getName() + " is not a directory");
				return;
			}

			List<File> files = new ArrayList<File>();
			//noinspection ConstantConditions
			for (File file : directory.listFiles())
				if (BackupFile.isBackup(file)) 
					files.add(file);
			
			if (files.isEmpty()) {
				System.err.println(directory.getName() + " directory has no backup files");
				return;
			}
	
			if (args.length == 4 && args[3].equalsIgnoreCase("compare")) {
				final Map<String, Map<Long, RawRecord>> units = new HashMap<String, Map<Long, RawRecord>>();
				
				for (String storage : persister.storage()) {
					System.out.println("Storage " + storage);
					persister.loadByStorage(storage, new PersistenceProvider.LoadCallback() {
						@Override
						public void process(RawRecord record) {
							Map<Long, RawRecord> unitList = units.get(record.getUnitName());
							if (unitList == null) {
								unitList = new HashMap<Long, RawRecord>();
								units.put(record.getUnitName(), unitList);
							}
							unitList.put(record.getId(), record);
						}
					});

					for (final Map.Entry<String, Map<Long, RawRecord>> unit : units.entrySet()) {
						BackupFile file = null;
						for (int i = 0, n = files.size(); i < n; i++) 
							if (BackupFile.isBackup(files.get(i), unit.getKey())) {
								file = new BackupFile(files.get(i));
								files.remove(i);
								break;
							}
						
						if (file == null)
							throw new RuntimeException("Could not find backup file for unit " + unit.getKey());
						
						final Long[] count = new Long[]{0L};
						file.read(new PersistenceProvider.LoadCallback() {
							@Override
							public void process(RawRecord record) {
								RawRecord r = unit.getValue().get(record.getId());
								if (r == null)
									throw new RuntimeException("Could not find persisted record " + record.getId() + " for unit " + unit.getKey());
								if (!r.equals(record))
									throw new RuntimeException("Record " + record.getId() + " mismatch for unit " + unit.getKey());
								count[0] = count[0] + 1; 
							}
						});
						
						if (count[0] != unit.getValue().size())
							throw new RuntimeException("Extra persisted records for unit " + unit.getKey());
						System.out.println("   Unit " + unit.getKey() + ": OK");
					}
					
					units.clear();
				}
				
				if (!files.isEmpty()) {
					System.err.println("Extra backups: ");
					for (File file : files)
						System.err.println("   " + file.getName());
				}
			} else {
				persister.init();
				for (File file : files) {
					InMemoryDatabase.readBackupFile(file, persister);
					System.out.println("Loaded " + file.getName());
				}
			}
		} catch (Exception e) {
			throw new RuntimeException(e);
		} finally {
			ctx.close();
		}
	}
}
