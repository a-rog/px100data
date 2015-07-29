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

/**
 * Base tenant config. Used by multi-tenant projects. Tenant configs (derived from this class) are typically stored in one JSON file.
 * 
 * @version 0.3 <br>Copyright (c) 2015 Px100 Systems. All Rights Reserved.<br>
 * @author Alex Rogachevsky
 */
public abstract class BaseTenantConfig {
	private Integer id;
	private Boolean active;
	private String urlIdentifier; 

	public BaseTenantConfig() {
		super();
		active = true;
	}

	public Integer getId() {
		return id;
	}

	public void setId(Integer id) {
		this.id = id;
	}

	@Index
	public Boolean getActive() {
		return active;
	}

	public void setActive(Boolean active) {
		this.active = active;
	}

	@Index
	public String getUrlIdentifier() {
		return urlIdentifier;
	}

	public void setUrlIdentifier(String urlIdentifier) {
		this.urlIdentifier = urlIdentifier;
	}
}
