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

import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import org.apache.commons.io.IOUtils;

import com.px100systems.data.core.RawRecord;
import com.px100systems.data.plugin.persistence.PersistenceProvider;

/**
 * Backup file used by emergency shutdowns and normal backups (data export). It uses the .obak extension.<br>
 * There is one file per "unit" i.e. entity + tenant.<br>
 * 
 * @version 0.3 <br>Copyright (c) 2015 Px100 Systems. All Rights Reserved.<br>
 * @author Alex Rogachevsky
 */
public class BackupFile {
	private static final String EXTENSION = ".obak";
	
	private File file;
	private ObjectOutputStream os = null;
	private ObjectInputStream is = null;
	
	public BackupFile(String directory, String unitName) {
		file = new File(directory + File.separatorChar + unitName + EXTENSION);
	}

	public BackupFile(File file) {
		this.file = file;
	}

	public static boolean isBackup(File file) {
		return file.isFile() && file.getName().endsWith(EXTENSION);
	}
	
	public static boolean isBackup(File file, String unitName) {
		return file.isFile() && file.getName().endsWith(unitName + EXTENSION);
	}
	
	public void close() {
		if (os != null) { 
			try {
				os.flush();
			} catch (IOException ignored) {}
				IOUtils.closeQuietly(os);
		}
		if (is != null)
			IOUtils.closeQuietly(is);
	}
	
	public void write(RawRecord record) throws IOException {
		if (os == null)
			os = new ObjectOutputStream(new FileOutputStream(file));
		os.writeObject(record);
	}
	
	public void read(PersistenceProvider.LoadCallback callback) throws IOException {
		is = new ObjectInputStream(new FileInputStream(file));
		try {
			for (RawRecord record = (RawRecord)is.readObject(); record != null; record = (RawRecord)is.readObject())
				callback.process(record);
		} catch (EOFException ignored) {
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	} 
}
