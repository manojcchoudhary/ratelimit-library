package com.lycosoft.ratelimit.storage.redis;

import org.apache.commons.codec.digest.DigestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.exceptions.JedisNoScriptException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Manages versioned Lua scripts for Redis with automatic reload on version mismatch.
 * 
 * <p>This class addresses Pre-flight Check #2: Lua Script Versioning.
 * Each Lua script must have a version header: {@code -- Version: X.Y.Z}
 * 
 * <p><b>Features:</b>
 * <ul>
 *   <li>Version extraction from script headers</li>
 *   <li>SHA-256 verification</li>
 *   <li>Automatic reload on mismatch</li>
 *   <li>Script caching</li>
 *   <li>JedisNoScriptException recovery</li>
 * </ul>
 * 
 * @since 1.0.0
 */
public class VersionedLuaScriptManager {
    
    private static final Logger logger = LoggerFactory.getLogger(VersionedLuaScriptManager.class);
    
    /**
     * Pattern to extract version from Lua script header.
     * Example: -- Version: 1.0.0
     */
    private static final Pattern VERSION_PATTERN = Pattern.compile("--\\s*Version:\\s*([\\d.]+)");
    
    /**
     * Cache of script name → SHA-1 hash
     */
    private final Map<String, String> scriptShaCache = new ConcurrentHashMap<>();
    
    /**
     * Cache of script name → script content
     */
    private final Map<String, String> scriptContentCache = new ConcurrentHashMap<>();

    private static final int MAX_VERSION_SCAN_LINES = 10;

    /**
     * Loads a Lua script into Redis and returns its SHA-1 hash.
     * 
     * <p>This method:
     * <ol>
     *   <li>Reads the script from resources</li>
     *   <li>Extracts version from header</li>
     *   <li>Calculates expected SHA-1</li>
     *   <li>Checks if script exists in Redis</li>
     *   <li>Loads script if missing or version mismatch</li>
     * </ol>
     * 
     * @param jedis the Redis connection
     * @param scriptName the script name (e.g., "token_bucket_consume.lua")
     * @return the SHA-1 hash of the loaded script
     * @throws ScriptLoadException if script cannot be loaded
     */
    public String loadScript(Jedis jedis, String scriptName) {
        // Check cache first
        String cachedSha = scriptShaCache.get(scriptName);
        if (cachedSha != null) {
            logger.trace("Using cached script SHA for {}: {}", scriptName, cachedSha);
            return cachedSha;
        }

        synchronized (this) {

            cachedSha = scriptShaCache.get(scriptName);
            if (cachedSha != null && scriptExists(jedis, cachedSha)) {
                return cachedSha;
            }

            // Read script content
            String scriptContent = readScriptFromResources(scriptName);

            // Extract version
            String version = extractVersion(scriptContent);
            logger.debug("Loading Lua script: {} (version: {})", scriptName, version);

            // Calculate expected SHA-1
            String expectedSha = DigestUtils.sha1Hex(scriptContent);

            // Check if script exists in Redis with matching SHA
            if (!scriptExists(jedis, expectedSha)) {
                // Script not in Redis cache - load it
                String actualSha = jedis.scriptLoad(scriptContent);

                if (!expectedSha.equals(actualSha)) {
                    throw new ScriptLoadException(
                            "SHA mismatch for " + scriptName +
                                    ": expected=" + expectedSha + ", actual=" + actualSha
                    );
                }

                logger.info("Loaded Lua script: {} v{} (SHA: {})", scriptName, version, actualSha);
            } else {
                logger.debug("Script {} already exists in Redis (SHA: {})", scriptName, expectedSha);
            }

            // Cache the SHA and content
            scriptShaCache.put(scriptName, expectedSha);
            scriptContentCache.put(scriptName, scriptContent);

            return expectedSha;
        }
    }
    
    /**
     * Executes a Lua script with automatic reload on {@link JedisNoScriptException}.
     * 
     * @param jedis the Redis connection
     * @param scriptName the script name
     * @param keys the KEYS array for the script
     * @param args the ARGV array for the script
     * @return the script result
     */
    public Object evalsha(Jedis jedis, String scriptName, String[] keys, String[] args) {
        String sha = scriptShaCache.get(scriptName);
        
        if (sha == null) {
            // Script not loaded yet - load it now
            sha = loadScript(jedis, scriptName);
        }
        
        try {
            return jedis.evalsha(sha, keys.length, concat(keys, args));
        } catch (JedisNoScriptException e) {
            // Script evicted from Redis - reload and retry
            logger.warn("Script {} evicted from Redis, reloading...", scriptName);
            sha = reloadScript(jedis, scriptName);
            return jedis.evalsha(sha, keys.length, concat(keys, args));
        }
    }
    
    /**
     * Reloads a script, forcing a fresh load from resources.
     * 
     * @param jedis the Redis connection
     * @param scriptName the script name
     * @return the new SHA-1 hash
     */
    private String reloadScript(Jedis jedis, String scriptName) {
        scriptShaCache.remove(scriptName);
        scriptContentCache.remove(scriptName);
        return loadScript(jedis, scriptName);
    }
    
    /**
     * Checks if a script exists in Redis.
     * 
     * @param jedis the Redis connection
     * @param sha the script SHA-1 hash
     * @return true if the script exists
     */
    private boolean scriptExists(Jedis jedis, String sha) {
        try {
            Boolean exists = jedis.scriptExists(sha);
            return exists != null && exists;
        } catch (Exception e) {
            logger.warn("Error checking script existence: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Reads a Lua script from classpath resources.
     * 
     * @param scriptName the script filename
     * @return the script content
     * @throws ScriptLoadException if the script cannot be read
     */
    private String readScriptFromResources(String scriptName) {

        // Validate against whitelist
        if (!LuaScripts.WHITELISTED_SCRIPTS.contains(scriptName)) {
            throw new ScriptLoadException("Script not in whitelist: " + scriptName);
        }

        // Additional validation
        if (scriptName.contains("..") ||
                scriptName.contains("/") ||
                scriptName.contains("\\") ||
                !scriptName.endsWith(".lua")) {
            throw new ScriptLoadException("Invalid script name format: " + scriptName);
        }

        // Check content cache first
        String cached = scriptContentCache.get(scriptName);
        if (cached != null) {
            return cached;
        }
        
        String resourcePath = "/lua/" + scriptName;
        
        try (InputStream is = getClass().getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new ScriptLoadException("Script not found: " + resourcePath);
            }
            
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(is, StandardCharsets.UTF_8))) {
                return reader.lines().collect(Collectors.joining("\n"));
            }
        } catch (IOException e) {
            throw new ScriptLoadException("Failed to read script: " + resourcePath, e);
        }
    }
    
    /**
     * Extracts version from Lua script header.
     * 
     * @param scriptContent the script content
     * @return the version string, or "unknown" if not found
     */
    private String extractVersion(String scriptContent) {
        // Version header must be in first 10 lines
        int lineCount = 0;
        int pos = 0;

        while (lineCount < MAX_VERSION_SCAN_LINES && pos < scriptContent.length()) {
            int nextLine = scriptContent.indexOf('\n', pos);
            if (nextLine == -1) {
                nextLine = scriptContent.length();
            }

            String line = scriptContent.substring(pos, nextLine);
            Matcher matcher = VERSION_PATTERN.matcher(line);

            if (matcher.find()) {
                return matcher.group(1);
            }

            lineCount++;
            pos = nextLine + 1;
        }

        logger.warn("No version header found in first {} lines of script", MAX_VERSION_SCAN_LINES);
        return "unknown";
    }
    
    /**
     * Concatenates keys and args into a single array.
     * 
     * @param keys the KEYS array
     * @param args the ARGV array
     * @return concatenated array
     */
    private String[] concat(String[] keys, String[] args) {
        String[] result = new String[keys.length + args.length];
        System.arraycopy(keys, 0, result, 0, keys.length);
        System.arraycopy(args, 0, result, keys.length, args.length);
        return result;
    }
    
    /**
     * Gets the cached SHA for a script.
     * 
     * @param scriptName the script name
     * @return the SHA, or null if not cached
     */
    public String getCachedSha(String scriptName) {
        return scriptShaCache.get(scriptName);
    }
    
    /**
     * Clears all cached scripts.
     */
    public void clearCache() {
        scriptShaCache.clear();
        scriptContentCache.clear();
        logger.info("Cleared Lua script cache");
    }
    
    /**
     * Exception thrown when a script cannot be loaded.
     */
    public static class ScriptLoadException extends RuntimeException {
        public ScriptLoadException(String message) {
            super(message);
        }
        
        public ScriptLoadException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Gets the number of scripts currently cached.
     *
     * @return the cache size
     */
    public int getCachedScriptCount() {
        return scriptShaCache.size();
    }

}
