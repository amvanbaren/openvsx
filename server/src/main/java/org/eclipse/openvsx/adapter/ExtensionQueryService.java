/** ******************************************************************************
 * Copyright (c) 2022 Precies. Software Ltd and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 * ****************************************************************************** */
package org.eclipse.openvsx.adapter;

import com.google.common.collect.Lists;
import org.eclipse.openvsx.cache.CacheService;
import org.eclipse.openvsx.dto.ExtensionDTO;
import org.eclipse.openvsx.dto.ExtensionVersionDTO;
import org.eclipse.openvsx.dto.FileResourceDTO;
import org.eclipse.openvsx.repositories.RepositoryService;
import org.eclipse.openvsx.util.TimeUtil;
import org.eclipse.openvsx.util.UrlUtil;
import org.eclipse.openvsx.util.VersionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

import static org.eclipse.openvsx.adapter.ExtensionQueryParam.*;
import static org.eclipse.openvsx.adapter.ExtensionQueryResult.Extension.FLAG_PREVIEW;
import static org.eclipse.openvsx.adapter.ExtensionQueryResult.ExtensionFile.*;
import static org.eclipse.openvsx.adapter.ExtensionQueryResult.ExtensionFile.FILE_CHANGELOG;
import static org.eclipse.openvsx.adapter.ExtensionQueryResult.Property.*;
import static org.eclipse.openvsx.adapter.ExtensionQueryResult.Statistic.*;
import static org.eclipse.openvsx.entities.FileResource.*;
import static org.eclipse.openvsx.entities.FileResource.CHANGELOG;

@Component
public class ExtensionQueryService {

    @Autowired
    RepositoryService repositories;

    @Autowired
    VersionService versions;

    public Map<Long, ExtensionQueryResult.Extension> search(List<Long> ids, String targetPlatform, int flags) {
        var extensionsList = repositories.findAllActiveExtensionDTOsById(ids);
        var extensionsMap = extensionsList.stream()
                .collect(Collectors.toMap(e -> e.getId(), e -> e));

        List<ExtensionVersionDTO> extensionVersions;
        List<ExtensionVersionDTO> allActiveExtensionVersions = repositories.findAllActiveExtensionVersionDTOs(extensionsMap.keySet(), targetPlatform);
        if (test(flags, FLAG_INCLUDE_LATEST_VERSION_ONLY)) {
            extensionVersions = allActiveExtensionVersions.stream()
                    .collect(Collectors.groupingBy(ev -> ev.getExtensionId() + "@" + ev.getTargetPlatform()))
                    .values()
                    .stream()
                    .map(versions::getLatest)
                    .collect(Collectors.toList());
        } else if (test(flags, FLAG_INCLUDE_VERSIONS) || test(flags, FLAG_INCLUDE_VERSION_PROPERTIES)) {
            extensionVersions = allActiveExtensionVersions;
        } else {
            extensionVersions = Collections.emptyList();
        }

        // similar to ExtensionVersion.SORT_COMPARATOR, difference is that it compares by extension id first
        var comparator = Comparator.<ExtensionVersionDTO, Long>comparing(ev -> ev.getExtension().getId())
                .thenComparing(ExtensionVersionDTO::getSemanticVersion)
                .thenComparing(ExtensionVersionDTO::getTimestamp)
                .reversed();

        var extensionVersionsMap = extensionVersions.stream()
                .map(ev -> {
                    ev.setExtension(extensionsMap.get(ev.getExtensionId()));
                    return ev;
                })
                .sorted(comparator)
                .collect(Collectors.groupingBy(ExtensionVersionDTO::getExtension));

        Map<Long, List<FileResourceDTO>> fileResources;
        if (test(flags, FLAG_INCLUDE_FILES) && !extensionVersionsMap.isEmpty()) {
            var types = List.of(MANIFEST, README, LICENSE, ICON, DOWNLOAD, CHANGELOG);
            var idsMap = extensionVersionsMap.values().stream()
                    .flatMap(Collection::stream)
                    .collect(Collectors.toMap(ev -> ev.getId(), ev -> ev));

            fileResources = repositories.findAllFileResourceDTOsByExtensionVersionIdAndType(idsMap.keySet(), types).stream()
                    .map(r -> {
                        r.setExtensionVersion(idsMap.get(r.getExtensionVersionId()));
                        return r;
                    })
                    .collect(Collectors.groupingBy(FileResourceDTO::getExtensionVersionId));
        } else {
            fileResources = Collections.emptyMap();
        }

        Map<Long, Integer> activeReviewCounts;
        if(test(flags, FLAG_INCLUDE_STATISTICS) && !extensionsList.isEmpty()) {
            var extensionIds = extensionsList.stream().map(ExtensionDTO::getId).collect(Collectors.toList());
            activeReviewCounts = repositories.findAllActiveReviewCountsByExtensionId(extensionIds);
        } else {
            activeReviewCounts = Collections.emptyMap();
        }

        var latestVersions = allActiveExtensionVersions.stream()
                .collect(Collectors.groupingBy(ExtensionVersionDTO::getExtensionId))
                .values()
                .stream()
                .map(versions::getLatest)
                .collect(Collectors.toMap(ExtensionVersionDTO::getExtensionId, ev -> ev));

        var extensionQueryResults = new HashMap<Long, ExtensionQueryResult.Extension>();
        for(var extension : extensionsList) {
            var latest = latestVersions.get(extension.getId());
            var queryExt = toQueryExtension(extension, latest, activeReviewCounts, flags);
            queryExt.versions = extensionVersionsMap.getOrDefault(extension, Collections.emptyList()).stream()
                    .map(extVer -> toQueryVersion(extVer, fileResources, flags))
                    .collect(Collectors.toList());

            extensionQueryResults.put(extension.getId(), queryExt);
        }

        return extensionQueryResults;
    }

    private ExtensionQueryResult.Extension toQueryExtension(ExtensionDTO extension, ExtensionVersionDTO latest, Map<Long, Integer> activeReviewCounts, int flags) {
        var namespace = extension.getNamespace();

        var queryExt = new ExtensionQueryResult.Extension();
        queryExt.extensionId = extension.getPublicId();
        queryExt.extensionName = extension.getName();
        queryExt.displayName = latest.getDisplayName();
        queryExt.shortDescription = latest.getDescription();
        queryExt.publisher = new ExtensionQueryResult.Publisher();
        queryExt.publisher.publisherId = namespace.getPublicId();
        queryExt.publisher.publisherName = namespace.getName();
        queryExt.publisher.displayName = namespace.getName();
        queryExt.tags = latest.getTags();
        queryExt.releaseDate = TimeUtil.toUTCString(extension.getPublishedDate());
        queryExt.publishedDate = TimeUtil.toUTCString(extension.getPublishedDate());
        queryExt.lastUpdated = TimeUtil.toUTCString(extension.getLastUpdatedDate());
        queryExt.categories = latest.getCategories();
        queryExt.flags = latest.isPreview() ? FLAG_PREVIEW : "";

        if (test(flags, FLAG_INCLUDE_STATISTICS)) {
            queryExt.statistics = Lists.newArrayList();
            var installStat = new ExtensionQueryResult.Statistic();
            installStat.statisticName = STAT_INSTALL;
            installStat.value = extension.getDownloadCount();
            queryExt.statistics.add(installStat);
            if (extension.getAverageRating() != null) {
                var avgRatingStat = new ExtensionQueryResult.Statistic();
                avgRatingStat.statisticName = STAT_AVERAGE_RATING;
                avgRatingStat.value = extension.getAverageRating();
                queryExt.statistics.add(avgRatingStat);
            }
            var ratingCountStat = new ExtensionQueryResult.Statistic();
            ratingCountStat.statisticName = STAT_RATING_COUNT;
            ratingCountStat.value = activeReviewCounts.getOrDefault(extension.getId(), 0);
            queryExt.statistics.add(ratingCountStat);
        }

        return queryExt;
    }

    private ExtensionQueryResult.ExtensionVersion toQueryVersion(
            ExtensionVersionDTO extVer,
            Map<Long, List<FileResourceDTO>> fileResources,
            int flags
    ) {
        var queryVer = new ExtensionQueryResult.ExtensionVersion();
        queryVer.version = extVer.getVersion();
        queryVer.lastUpdated = TimeUtil.toUTCString(extVer.getTimestamp());
        queryVer.targetPlatform = extVer.getTargetPlatform();
        var serverUrl = UrlUtil.getBaseUrl();
        var namespaceName = extVer.getExtension().getNamespace().getName();
        var extensionName = extVer.getExtension().getName();

        if (test(flags, FLAG_INCLUDE_ASSET_URI)) {
            queryVer.assetUri = UrlUtil.createApiUrl(serverUrl, "vscode", "asset", namespaceName, extensionName, extVer.getVersion());
            queryVer.fallbackAssetUri = queryVer.assetUri;
        }
        if (test(flags, FLAG_INCLUDE_VERSION_PROPERTIES)) {
            queryVer.properties = Lists.newArrayList();
            queryVer.addProperty(PROP_BRANDING_COLOR, extVer.getGalleryColor());
            queryVer.addProperty(PROP_BRANDING_THEME, extVer.getGalleryTheme());
            queryVer.addProperty(PROP_REPOSITORY, extVer.getRepository());
            queryVer.addProperty(PROP_ENGINE, getVscodeEngine(extVer));
            var dependencies = extVer.getDependencies().stream()
                    .collect(Collectors.joining(","));
            queryVer.addProperty(PROP_DEPENDENCY, dependencies);
            var bundledExtensions = extVer.getBundledExtensions().stream()
                    .collect(Collectors.joining(","));
            queryVer.addProperty(PROP_EXTENSION_PACK, bundledExtensions);
            queryVer.addProperty(PROP_LOCALIZED_LANGUAGES, "");
            if (extVer.isPreRelease()) {
                queryVer.addProperty(PROP_PRE_RELEASE, "true");
            }
            if (isWebExtension(extVer)) {
                queryVer.addProperty(PROP_WEB_EXTENSION, "true");
            }
        }

        if(fileResources.containsKey(extVer.getId())) {
            var resourcesByType = fileResources.get(extVer.getId()).stream()
                    .collect(Collectors.groupingBy(FileResourceDTO::getType));

            var fileBaseUrl = UrlUtil.createApiFileBaseUrl(serverUrl, namespaceName, extensionName, extVer.getTargetPlatform(), extVer.getVersion());

            queryVer.files = Lists.newArrayList();
            queryVer.addFile(FILE_MANIFEST, createFileUrl(resourcesByType.get(MANIFEST), fileBaseUrl));
            queryVer.addFile(FILE_DETAILS, createFileUrl(resourcesByType.get(README), fileBaseUrl));
            queryVer.addFile(FILE_LICENSE, createFileUrl(resourcesByType.get(LICENSE), fileBaseUrl));
            queryVer.addFile(FILE_ICON, createFileUrl(resourcesByType.get(ICON), fileBaseUrl));
            queryVer.addFile(FILE_VSIX, createFileUrl(resourcesByType.get(DOWNLOAD), fileBaseUrl));
            queryVer.addFile(FILE_CHANGELOG, createFileUrl(resourcesByType.get(CHANGELOG), fileBaseUrl));
        }

        return queryVer;
    }

    private String getVscodeEngine(ExtensionVersionDTO extVer) {
        if (extVer.getEngines() == null)
            return null;
        return extVer.getEngines().stream()
                .filter(engine -> engine.startsWith("vscode@"))
                .findFirst()
                .map(engine -> engine.substring("vscode@".length()))
                .orElse(null);
    }

    private boolean isWebExtension(ExtensionVersionDTO extVer) {
        return extVer.getExtensionKind() != null && extVer.getExtensionKind().contains("web");
    }

    private boolean test(int flags, int flag) {
        return (flags & flag) != 0;
    }

    private String createFileUrl(List<FileResourceDTO> singleResource, String fileBaseUrl) {
        if(singleResource == null || singleResource.isEmpty()) {
            return null;
        }

        return createFileUrl(singleResource.get(0), fileBaseUrl);
    }

    private String createFileUrl(FileResourceDTO resource, String fileBaseUrl) {
        return resource != null ? UrlUtil.createApiFileUrl(fileBaseUrl, resource.getName()) : null;
    }
}
