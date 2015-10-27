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
package com.px100systems.util;

import com.google.gson.*;
import org.apache.http.client.fluent.Request;
import java.io.IOException;

/**
 * IP Locator
 * 
 * @version 0.3 <br>Copyright (c) 2015 Px100 Systems. All Rights Reserved.<br>
 * @author Alex Rogachevsky
 */
@SuppressWarnings("unused")
public class IpLocator {
	public static class Info {
		private String ip;
		private String countryCode;
		private String country;
		private String regionCode;
		private String region;
		private String city;
		private String zip;
		private Double latitude;
		private Double longitude;

		public Info(String ip, String countryCode, String country, String regionCode, String region, String city, String zip, Double latitude, Double longitude) {
			this.city = city;
			this.country = country;
			this.countryCode = countryCode;
			this.ip = ip;
			this.latitude = latitude;
			this.longitude = longitude;
			this.region = region;
			this.regionCode = regionCode;
			this.zip = zip;
		}

		public String getCity() {
			return city;
		}

		public String getCountry() {
			return country;
		}

		public String getCountryCode() {
			return countryCode;
		}

		public String getIp() {
			return ip;
		}

		public Double getLatitude() {
			return latitude;
		}

		public Double getLongitude() {
			return longitude;
		}

		public String getRegion() {
			return region;
		}

		public String getRegionCode() {
			return regionCode;
		}

		public String getZip() {
			return zip;
		}
	}

	public static Info locateIpOrHost(String ipOrHost) {
		try {
			JsonObject jso = new com.google.gson.JsonParser().parse(Request.Get("http://freegeoip.net/json/" + ipOrHost).execute().
				returnContent().asString()).getAsJsonObject();

			return new Info(
				jso.getAsJsonPrimitive("ip").getAsString(),
				jso.getAsJsonPrimitive("country_code").getAsString(),
				jso.getAsJsonPrimitive("country_name").getAsString(),
				jso.getAsJsonPrimitive("region_code").getAsString(),
				jso.getAsJsonPrimitive("region_name").getAsString(),
				jso.getAsJsonPrimitive("city").getAsString(),
				jso.getAsJsonPrimitive("zip_code").getAsString(),
				jso.getAsJsonPrimitive("latitude").getAsDouble(),
				jso.getAsJsonPrimitive("longitude").getAsDouble());
		} catch (IOException e) {
			return null;
		}
	}
}

