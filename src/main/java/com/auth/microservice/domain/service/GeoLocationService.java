package com.auth.microservice.domain.service;

import io.vertx.core.Future;

/**
 * Service interface for geolocation operations.
 * Provides functionality to determine geographical location from IP addresses.
 */
public interface GeoLocationService {
    
    /**
     * Gets the geographical location information for the given IP address.
     * 
     * @param ipAddress the IP address to lookup
     * @return Future containing the location information
     * @throws IllegalArgumentException if IP address is null or invalid
     */
    Future<LocationInfo> getLocationByIp(String ipAddress);
    
    /**
     * Gets the country code for the given IP address.
     * This is a convenience method that extracts only the country from location info.
     * 
     * @param ipAddress the IP address to lookup
     * @return Future containing the country code (e.g., "US", "GB", "DE")
     */
    Future<String> getCountryByIp(String ipAddress);
    
    /**
     * Checks if the service is available and responding.
     * 
     * @return Future containing true if service is healthy, false otherwise
     */
    Future<Boolean> isServiceHealthy();
    
    /**
     * Clears any cached location data.
     * 
     * @return Future that completes when cache is cleared
     */
    Future<Void> clearCache();
    
    /**
     * Gets cache statistics.
     * 
     * @return Future containing cache statistics
     */
    Future<CacheStats> getCacheStats();
    
    /**
     * Geographical location information.
     */
    record LocationInfo(
        String ip,
        String country,
        String countryCode,
        String region,
        String regionName,
        String city,
        String zip,
        Double lat,
        Double lon,
        String timezone,
        String isp,
        String org,
        String as,
        String query,
        String status,
        String message
    ) {
        /**
         * Creates a LocationInfo for a successful lookup.
         */
        public static LocationInfo success(String ip, String country, String countryCode, 
                                         String region, String regionName, String city, 
                                         String zip, Double lat, Double lon, String timezone, 
                                         String isp, String org, String as) {
            return new LocationInfo(ip, country, countryCode, region, regionName, city, 
                                  zip, lat, lon, timezone, isp, org, as, ip, "success", null);
        }
        
        /**
         * Creates a LocationInfo for a failed lookup.
         */
        public static LocationInfo failure(String ip, String message) {
            return new LocationInfo(ip, null, null, null, null, null, null, 
                                  null, null, null, null, null, null, ip, "fail", message);
        }
        
        /**
         * Checks if the location lookup was successful.
         */
        public boolean isSuccess() {
            return "success".equals(status);
        }
    }
    
    /**
     * Cache statistics information.
     */
    record CacheStats(
        long totalRequests,
        long cacheHits,
        long cacheMisses,
        double hitRate,
        long cacheSize
    ) {}
}