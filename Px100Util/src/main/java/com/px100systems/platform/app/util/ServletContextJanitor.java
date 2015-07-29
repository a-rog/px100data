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
package com.px100systems.platform.app.util;

import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Enumeration;
import java.util.Set;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

/**
 * JDBC Janitor - fixes Tomcat app redeploy memory leak. Specify in web.xml.
 * 
 * @version 0.3 <br>Copyright (c) 2015 Px100 Systems. All Rights Reserved.<br>
 * @author Alex Rogachevsky
 */
public class ServletContextJanitor implements ServletContextListener {
	@SuppressWarnings("deprecation")
	@Override
	public void contextDestroyed(ServletContextEvent arg0) {
		Enumeration<Driver> drivers = DriverManager.getDrivers();
		while (drivers.hasMoreElements())
			try {
				DriverManager.deregisterDriver(drivers.nextElement());
			} catch (SQLException ignored) {}
		
		Set<Thread> threadSet = Thread.getAllStackTraces().keySet();
		for (Thread t : threadSet.toArray(new Thread[threadSet.size()]))
			if (t.getName().contains("Abandoned connection cleanup thread")) {
				synchronized (this) {
					t.stop(); 
				}
				
				try {
				    Thread.sleep(10000);
				} catch (InterruptedException ignored) {}
			}
	}

	@Override
	public void contextInitialized(ServletContextEvent arg0) {
	}
}
