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
package com.px100systems.data.plugin.storage;

import com.px100systems.data.core.StoredBean;
import java.util.List;

/**
 * Data stream based loader from persistent store. Used to initially load the data into the data grid. Avoids transaction overhead.
 *
 * @version 0.3 <br>Copyright (c) 2015 Px100 Systems. All Rights Reserved.<br>
 * @author Alex Rogachevsky
 */
public interface InMemoryStorageLoader {
	/**
	 * Load records (into the data input streams for different maps)
	 *
	 * @param inserts records to load
	 */
	void store(List<StoredBean> inserts);

	/**
	 * Close data input streams for all encountered maps
	 */
	void close();
}
