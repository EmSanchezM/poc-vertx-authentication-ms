package com.auth.microservice.infrastructure.adapter.external;

import com.auth.microservice.domain.service.GeoLocationService;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.predicate.ResponsePredicate;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

/**
 * Implementation of GeoLocationService using the ip-api.com service.
 * Includes caching and error handling for external service calls.
 */
public class IpApiGeoLocationService implements GeoLocationService {
    
    private static final String IP_API_HOST = "ip-api.com";
    private static final int IP_API_PORT = 80;
    private static final String IP_API_PATH = "/json/";
    private static final int REQUEST_TIMEOUT_MS = 5000; // 5 seconds
    private static final int CACHE_TTL_MS = 3600000; // 1 hour
    
    // IP address validation pattern
    private static final Pattern IP_PATTERN = Pattern.compile(
        "^(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$"
    );
    
    private final WebClient webClient;
    private final ConcurrentHashMap<String, CachedLocationInfo> cache;
    private final AtomicLong totalRequests;
    private final AtomicLong cacheHits;
    private final AtomicLong cacheMisses;
    
    public IpApiGeoLocationService(WebClient webClient) {
        this.webClient = webClient;
        this.cache = new ConcurrentHashMap<>();
        this.totalRequests = new AtomicLong(0);
        this.cacheHits = new AtomicLong(0);
        this.cacheMisses = new AtomicLong(0);
    }
    
    @Override
    public Future<LocationInfo> getLocationByIp(String ipAddress) {
        if (ipAddress == null || ipAddress.trim().isEmpty()) {
            return Future.failedFuture(new IllegalArgumentException("IP address cannot be null or empty"));
        }
        
        String trimmedIp = ipAddress.trim();
        
        // Handle localhost and private IPs
        if (isLocalOrPrivateIp(trimmedIp)) {
            return Future.succeededFuture(LocationInfo.success(
                trimmedIp, "Local", "LO", "Local", "Local Region", "Local City",
                "00000", 0.0, 0.0, "UTC", "Local ISP", "Local Org", "Local AS"
            ));
        }
        
        // Validate IP format
        if (!isValidIpAddress(trimmedIp)) {
            return Future.failedFuture(new IllegalArgumentException("Invalid IP address format: " + trimmedIp));
        }
        
        totalRequests.incrementAndGet();
        
        // Check cache first
        CachedLocationInfo cached = cache.get(trimmedIp);
        if (cached != null && !cached.isExpired()) {
            cacheHits.incrementAndGet();
            return Future.succeededFuture(cached.locationInfo);
        }
        
        cacheMisses.incrementAndGet();
        
        // Make API call
        Promise<LocationInfo> promise = Promise.promise();
        
        webClient
            .get(IP_API_PORT, IP_API_HOST, IP_API_PATH + trimmedIp)
            .timeout(REQUEST_TIMEOUT_MS)
            .expect(ResponsePredicate.SC_OK)
            .send()
            .onSuccess(response -> {
                try {
                    JsonObject json = response.bodyAsJsonObject();
                    LocationInfo locationInfo = parseLocationResponse(json, trimmedIp);
                    
                    // Cache successful responses
                    if (locationInfo.isSuccess()) {
                        cache.put(trimmedIp, new CachedLocationInfo(locationInfo, System.currentTimeMillis() + CACHE_TTL_MS));
                    }
                    
                    promise.complete(locationInfo);
                } catch (Exception e) {
                    promise.complete(LocationInfo.failure(trimmedIp, "Failed to parse response: " + e.getMessage()));
                }
            })
            .onFailure(throwable -> {
                String errorMessage = "Failed to get location for IP " + trimmedIp + ": " + throwable.getMessage();
                promise.complete(LocationInfo.failure(trimmedIp, errorMessage));
            });
        
        return promise.future();
    }
    
    @Override
    public Future<String> getCountryByIp(String ipAddress) {
        return getLocationByIp(ipAddress)
            .map(locationInfo -> {
                if (locationInfo.isSuccess() && locationInfo.countryCode() != null) {
                    return locationInfo.countryCode();
                } else {
                    return "UNKNOWN";
                }
            });
    }
    
    @Override
    public Future<Boolean> isServiceHealthy() {
        // Test with a known public IP (Google DNS)
        return getLocationByIp("8.8.8.8")
            .map(LocationInfo::isSuccess)
            .recover(throwable -> Future.succeededFuture(false));
    }
    
    @Override
    public Future<Void> clearCache() {
        cache.clear();
        return Future.succeededFuture();
    }
    
    @Override
    public Future<CacheStats> getCacheStats() {
        long total = totalRequests.get();
        long hits = cacheHits.get();
        long misses = cacheMisses.get();
        double hitRate = total > 0 ? (double) hits / total : 0.0;
        
        // Clean expired entries while calculating size
        cleanExpiredEntries();
        long cacheSize = cache.size();
        
        CacheStats stats = new CacheStats(total, hits, misses, hitRate, cacheSize);
        return Future.succeededFuture(stats);
    }
    
    /**
     * Parses the JSON response from ip-api.com into a LocationInfo object.
     */
    private LocationInfo parseLocationResponse(JsonObject json, String ipAddress) {
        String status = json.getString("status", "fail");
        
        if ("success".equals(status)) {
            return LocationInfo.success(
                ipAddress,
                json.getString("country"),
                json.getString("countryCode"),
                json.getString("region"),
                json.getString("regionName"),
                json.getString("city"),
                json.getString("zip"),
                json.getDouble("lat"),
                json.getDouble("lon"),
                json.getString("timezone"),
                json.getString("isp"),
                json.getString("org"),
                json.getString("as")
            );
        } else {
            String message = json.getString("message", "Unknown error");
            return LocationInfo.failure(ipAddress, message);
        }
    }
    
    /**
     * Validates if the given string is a valid IPv4 address.
     */
    private boolean isValidIpAddress(String ip) {
        return IP_PATTERN.matcher(ip).matches();
    }
    
    /**
     * Checks if the IP address is localhost or a private IP.
     */
    private boolean isLocalOrPrivateIp(String ip) {
        if ("127.0.0.1".equals(ip) || "localhost".equals(ip) || "::1".equals(ip)) {
            return true;
        }
        
        if (!isValidIpAddress(ip)) {
            return false;
        }
        
        String[] parts = ip.split("\\.");
        if (parts.length != 4) {
            return false;
        }
        
        try {
            int first = Integer.parseInt(parts[0]);
            int second = Integer.parseInt(parts[1]);
            
            // Private IP ranges:
            // 10.0.0.0 - 10.255.255.255
            // 172.16.0.0 - 172.31.255.255
            // 192.168.0.0 - 192.168.255.255
            return (first == 10) ||
                   (first == 172 && second >= 16 && second <= 31) ||
                   (first == 192 && second == 168);
        } catch (NumberFormatException e) {
            return false;
        }
    }
    
    /**
     * Removes expired entries from the cache.
     */
    private void cleanExpiredEntries() {
        long currentTime = System.currentTimeMillis();
        cache.entrySet().removeIf(entry -> entry.getValue().isExpired(currentTime));
    }
    
    /**
     * Cached location information with expiration time.
     */
    private static class CachedLocationInfo {
        final LocationInfo locationInfo;
        final long expirationTime;
        
        CachedLocationInfo(LocationInfo locationInfo, long expirationTime) {
            this.locationInfo = locationInfo;
            this.expirationTime = expirationTime;
        }
        
        boolean isExpired() {
            return isExpired(System.currentTimeMillis());
        }
        
        boolean isExpired(long currentTime) {
            return currentTime > expirationTime;
        }
    }
}