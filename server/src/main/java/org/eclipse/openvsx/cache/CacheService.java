/** ******************************************************************************
 * Copyright (c) 2022 Precies. Software and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 * ****************************************************************************** */
package org.eclipse.openvsx.cache;

import org.apache.commons.collections4.map.HashedMap;
import org.eclipse.openvsx.adapter.ExtensionQueryResult;
import org.eclipse.openvsx.dto.ExtensionVersionDTO;
import org.eclipse.openvsx.entities.Extension;
import org.eclipse.openvsx.entities.ExtensionVersion;
import org.eclipse.openvsx.entities.UserData;
import org.eclipse.openvsx.repositories.RepositoryService;
import org.eclipse.openvsx.util.TargetPlatform;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class CacheService {

    public static final String CACHE_DATABASE_SEARCH = "database.search";
    public static final String CACHE_EXTENSION_JSON = "extension.json";
    public static final String CACHE_LATEST_EXTENSION_VERSION = "latest.extension.version";
    public static final String CACHE_LATEST_EXTENSION_VERSION_DTO = "latest.extension.version.dto";
    public static final String CACHE_EXTENSION_QUERY_RESULT = "extenionquery.json";
    public static final String GENERATOR_EXTENSION_JSON = "extensionJsonCacheKeyGenerator";
    public static final String GENERATOR_LATEST_EXTENSION_VERSION = "latestExtensionVersionCacheKeyGenerator";
    public static final String GENERATOR_LATEST_EXTENSION_VERSION_DTO = "latestExtensionVersionDTOCacheKeyGenerator";

    @Autowired
    CacheManager cacheManager;

    @Autowired
    RepositoryService repositoryService;

    @Autowired
    ExtensionJsonCacheKeyGenerator extensionJsonCacheKey;

    @Autowired
    LatestExtensionVersionCacheKeyGenerator latestExtensionVersionCacheKey;

    @Autowired
    LatestExtensionVersionDTOCacheKeyGenerator latestExtensionVersionDTOCacheKeyGenerator;

    @Autowired
    ExtensionQueryExtensionCacheKeyGenerator extensionQueryExtensionCacheKeyGenerator;

    public void evictExtensionJsons(UserData user) {
        repositoryService.findVersions(user)
                .map(ExtensionVersion::getExtension)
                .toSet()
                .forEach(this::evictExtensionJsons);
    }

    public void evictExtensionJsons(Extension extension) {
        var cache = cacheManager.getCache(CACHE_EXTENSION_JSON);
        if(cache == null) {
            return; // cache is not created
        }
        if(extension.getVersions() == null) {
            return;
        }

        var versions = new ArrayList<>(List.of("latest", "pre-release"));
        extension.getVersions().stream()
                .map(ExtensionVersion::getVersion)
                .forEach(versions::add);

        var namespaceName = extension.getNamespace().getName();
        var extensionName = extension.getName();
        for(var version : versions) {
            for(var targetPlatform : TargetPlatform.TARGET_PLATFORM_NAMES) {
                cache.evictIfPresent(extensionJsonCacheKey.generate(namespaceName, extensionName, targetPlatform, version));
            }
        }
    }

    public void evictLatestExtensionVersion(Extension extension) {
        var cache = cacheManager.getCache(CACHE_LATEST_EXTENSION_VERSION);
        if(cache != null) {
            var targetPlatforms = new ArrayList<>(TargetPlatform.TARGET_PLATFORM_NAMES);
            targetPlatforms.add(null);
            for (var targetPlatform : targetPlatforms) {
                for (var preRelease : List.of(true, false)) {
                    for (var onlyActive : List.of(true, false)) {
                        var key = latestExtensionVersionCacheKey.generate(null, null, extension, targetPlatform, preRelease, onlyActive);
                        cache.evictIfPresent(key);
                    }
                }
            }
        }

        cache = cacheManager.getCache(CACHE_LATEST_EXTENSION_VERSION_DTO);
        if(cache != null) {
            for(var type : ExtensionVersionDTO.Type.values()) {
                var key = latestExtensionVersionDTOCacheKeyGenerator.generate(null, null, extension.getId(), type);
                cache.evictIfPresent(key);
            }
        }
    }

    public Map<Long, ExtensionQueryResult.Extension> getExtensionQueryResults(List<Long> extensionIds, String targetPlatform, int flags) {
        var cache = cacheManager.getCache(CACHE_EXTENSION_QUERY_RESULT);
        if(cache == null) {
            return Collections.emptyMap();
        }

        var results = new HashedMap<Long, ExtensionQueryResult.Extension>();
        for(var id : extensionIds) {
            var key =  extensionQueryExtensionCacheKeyGenerator.generate(id, targetPlatform, flags);
            var result = cache.get(key, ExtensionQueryResult.Extension.class);
            results.put(id, result);
        }

        return results;
    }

    public void putExtensionQueryResults(Map<Long, ExtensionQueryResult.Extension> extensionQueryResults, String targetPlatform, int flags) {
        var cache = cacheManager.getCache(CACHE_EXTENSION_QUERY_RESULT);
        if(cache == null) {
            return;
        }

        for(var entry : extensionQueryResults.entrySet()) {
            var key =  extensionQueryExtensionCacheKeyGenerator.generate(entry.getKey(), targetPlatform, flags);
            cache.put(key, entry.getValue());
        }
    }
}
