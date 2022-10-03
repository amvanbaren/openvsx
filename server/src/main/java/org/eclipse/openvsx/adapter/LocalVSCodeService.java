/** ******************************************************************************
 * Copyright (c) 2022 Precies. Software and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 * ****************************************************************************** */
package org.eclipse.openvsx.adapter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import org.eclipse.openvsx.cache.CacheService;
import org.eclipse.openvsx.dto.ExtensionDTO;
import org.eclipse.openvsx.dto.ExtensionVersionDTO;
import org.eclipse.openvsx.dto.FileResourceDTO;
import org.eclipse.openvsx.entities.Extension;
import org.eclipse.openvsx.entities.ExtensionVersion;
import org.eclipse.openvsx.entities.FileResource;
import org.eclipse.openvsx.entities.Namespace;
import org.eclipse.openvsx.repositories.RepositoryService;
import org.eclipse.openvsx.search.SearchUtilService;
import org.eclipse.openvsx.storage.StorageUtilService;
import org.eclipse.openvsx.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.transaction.Transactional;

import static org.eclipse.openvsx.adapter.ExtensionQueryParam.Criterion.*;
import static org.eclipse.openvsx.adapter.ExtensionQueryParam.*;
import static org.eclipse.openvsx.adapter.ExtensionQueryResult.Extension.FLAG_PREVIEW;
import static org.eclipse.openvsx.adapter.ExtensionQueryResult.ExtensionFile.*;
import static org.eclipse.openvsx.adapter.ExtensionQueryResult.Property.*;
import static org.eclipse.openvsx.adapter.ExtensionQueryResult.Statistic.*;
import static org.eclipse.openvsx.entities.FileResource.*;

@Component
public class LocalVSCodeService implements IVSCodeService {

    private static final String BUILT_IN_EXTENSION_NAMESPACE = "vscode";

    @Autowired
    RepositoryService repositories;

    @Autowired
    SearchUtilService search;

    @Autowired
    StorageUtilService storageUtil;

    @Autowired
    ExtensionQueryService extensionQuery;

    @Autowired
    CacheService cache;

    @Value("${ovsx.webui.url:}")
    String webuiUrl;

    @Override
    public ExtensionQueryResult extensionQuery(ExtensionQueryParam param, int defaultPageSize) {
        String targetPlatform;
        String queryString = null;
        String category = null;
        int pageNumber;
        int pageSize;
        String sortOrder;
        String sortBy;
        Set<String> publicIds;
        Set<String> extensionNames;
        if (param.filters == null || param.filters.isEmpty()) {
            pageNumber = 0;
            pageSize = defaultPageSize;
            sortBy = "relevance";
            sortOrder = "desc";
            targetPlatform = null;
            publicIds = Collections.emptySet();
            extensionNames = Collections.emptySet();
        } else {
            var filter = param.filters.get(0);
            publicIds = new HashSet<>(filter.findCriteria(FILTER_EXTENSION_ID));
            extensionNames = new HashSet<>(filter.findCriteria(FILTER_EXTENSION_NAME));

            queryString = filter.findCriterion(FILTER_SEARCH_TEXT);
            if (queryString == null)
                queryString = filter.findCriterion(FILTER_TAG);

            category = filter.findCriterion(FILTER_CATEGORY);
            var targetCriterion = filter.findCriterion(FILTER_TARGET);
            targetPlatform = TargetPlatform.isValid(targetCriterion) ? targetCriterion : null;

            pageNumber = Math.max(0, filter.pageNumber - 1);
            pageSize = filter.pageSize > 0 ? filter.pageSize : defaultPageSize;
            sortOrder = getSortOrder(filter.sortOrder);
            sortBy = getSortBy(filter.sortBy);
        }

        Long totalCount = null;
        List<Long> extensionIds;
        if (!publicIds.isEmpty()) {
            extensionIds = repositories.findAllActiveExtensionIdsByPublicId(publicIds, BUILT_IN_EXTENSION_NAMESPACE);
        } else if (!extensionNames.isEmpty()) {
            extensionIds = repositories.findAllActiveExtensionIdsByExtensionName(extensionNames, BUILT_IN_EXTENSION_NAMESPACE);
        } else if (!search.isEnabled()) {
            extensionIds = Collections.emptyList();
        } else {
            try {
                var pageOffset = pageNumber * pageSize;
                var searchOptions = new SearchUtilService.Options(queryString, category, targetPlatform, pageSize,
                        pageOffset, sortOrder, sortBy, false, BUILT_IN_EXTENSION_NAMESPACE);

                var searchResult = search.search(searchOptions);
                totalCount = searchResult.getTotalHits();
                extensionIds = searchResult.getSearchHits().stream()
                        .map(hit -> hit.getContent().id)
                        .collect(Collectors.toList());
            } catch (ErrorResultException exc) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exc.getMessage(), exc);
            }
        }

        var results = new HashMap<Long, ExtensionQueryResult.Extension>();
        results.putAll(cache.getExtensionQueryResults(extensionIds, targetPlatform, param.flags));

        var notCachedExtensionIds = new ArrayList<>(extensionIds);
        notCachedExtensionIds.removeAll(results.keySet());

        var notCachedResults = extensionQuery.search(notCachedExtensionIds, targetPlatform, param.flags);
        cache.putExtensionQueryResults(notCachedResults, targetPlatform, param.flags);
        results.putAll(notCachedResults);

        var extensionsList = extensionIds.stream()
                .map(results::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        
        if(totalCount == null) {
            totalCount = (long) extensionsList.size();
        }

        return toQueryResult(extensionsList, totalCount);
    }

    private ExtensionQueryResult toQueryResult(List<ExtensionQueryResult.Extension> extensions, long totalCount) {
        var resultItem = new ExtensionQueryResult.ResultItem();
        resultItem.extensions = extensions;

        var countMetadataItem = new ExtensionQueryResult.ResultMetadataItem();
        countMetadataItem.name = "TotalCount";
        countMetadataItem.count = totalCount;
        var countMetadata = new ExtensionQueryResult.ResultMetadata();
        countMetadata.metadataType = "ResultCount";
        countMetadata.metadataItems = List.of(countMetadataItem);
        resultItem.resultMetadata = List.of(countMetadata);

        var result = new ExtensionQueryResult();
        result.results = List.of(resultItem);
        return result;
    }

    private String getSortBy(int sortBy) {
        switch (sortBy) {
            case 4: // InstallCount
                return "downloadCount";
            case 5: // PublishedDate
                return "timestamp";
            case 6: // AverageRating
                return "averageRating";
            default:
                return "relevance";
        }
    }

    private String getSortOrder(int sortOrder) {
        switch (sortOrder) {
            case 1: // Ascending
                return "asc";
            default:
                return "desc";
        }
    }

    @Override
    @Transactional
    public ResponseEntity<byte[]> getAsset(
            String namespace, String extensionName, String version, String assetType, String targetPlatform,
            String restOfTheUrl
    ) {
        if(isBuiltInExtensionNamespace(namespace)) {
            return new ResponseEntity<>(("Built-in extension namespace '" + namespace + "' not allowed").getBytes(StandardCharsets.UTF_8), null, HttpStatus.BAD_REQUEST);
        }

        var extVersion = repositories.findVersion(version, targetPlatform, extensionName, namespace);
        if (extVersion == null || !extVersion.isActive()) {
            throw new NotFoundException();
        }

        var asset = (restOfTheUrl != null && restOfTheUrl.length() > 0) ? (assetType + "/" + restOfTheUrl) : assetType;
        var resource = getFileFromDB(extVersion, asset);
        if (resource == null) {
            throw new NotFoundException();
        }
        if (resource.getType().equals(FileResource.DOWNLOAD)) {
            storageUtil.increaseDownloadCount(extVersion, resource);
        }
        if (resource.getStorageType().equals(FileResource.STORAGE_DB)) {
            var headers = storageUtil.getFileResponseHeaders(resource.getName());
            return new ResponseEntity<>(resource.getContent(), headers, HttpStatus.OK);
        } else {
            return ResponseEntity.status(HttpStatus.FOUND)
                    .location(storageUtil.getLocation(resource))
                    .cacheControl(CacheControl.maxAge(7, TimeUnit.DAYS).cachePublic())
                    .build();
        }
    }

    private FileResource getFileFromDB(ExtensionVersion extVersion, String assetType) {
        switch (assetType) {
            case FILE_VSIX:
                return repositories.findFileByType(extVersion, FileResource.DOWNLOAD);
            case FILE_MANIFEST:
                return repositories.findFileByType(extVersion, FileResource.MANIFEST);
            case FILE_DETAILS:
                return repositories.findFileByType(extVersion, FileResource.README);
            case FILE_CHANGELOG:
                return repositories.findFileByType(extVersion, FileResource.CHANGELOG);
            case FILE_LICENSE:
                return repositories.findFileByType(extVersion, FileResource.LICENSE);
            case FILE_ICON:
                return repositories.findFileByType(extVersion, FileResource.ICON);
            default: {
                var name = assetType.startsWith(FILE_WEB_RESOURCES)
                        ? assetType.substring((FILE_WEB_RESOURCES.length()))
                        : null;

                return name != null && name.startsWith("extension/") // is web resource
                        ? repositories.findFileByTypeAndName(extVersion, FileResource.RESOURCE, name)
                        : null;
            }
        }
    }

    @Override
    public String getItemUrl(String namespaceName, String extensionName) {
        if(isBuiltInExtensionNamespace(namespaceName)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Built-in extension namespace '" + namespaceName + "' not allowed");
        }

        var extension = repositories.findExtension(extensionName, namespaceName);
        if (extension == null || !extension.isActive()) {
            throw new NotFoundException();
        }

        return UrlUtil.createApiUrl(webuiUrl, "extension", namespaceName, extensionName);
    }

    @Override
    public String download(String namespace, String extension, String version, String targetPlatform) {
        if(isBuiltInExtensionNamespace(namespace)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Built-in extension namespace '" + namespace + "' not allowed");
        }

        var extVersion = repositories.findVersion(version, targetPlatform, extension, namespace);
        if (extVersion == null || !extVersion.isActive()) {
            throw new NotFoundException();
        }

        var resource = repositories.findFileByType(extVersion, FileResource.DOWNLOAD);
        if (resource == null) {
            throw new NotFoundException();
        }

        if(resource.getStorageType().equals(STORAGE_DB)) {
            var apiUrl = UrlUtil.createApiUrl(UrlUtil.getBaseUrl(), "vscode", "asset", namespace, extension, version, FILE_VSIX);
            if(!TargetPlatform.isUniversal(targetPlatform)) {
                apiUrl = UrlUtil.addQuery(apiUrl, "targetPlatform", targetPlatform);
            }

            return apiUrl;
        } else {
            storageUtil.increaseDownloadCount(extVersion, resource);
            return storageUtil.getLocation(resource).toString();
        }
    }

    @Override
    public ResponseEntity<byte[]> browse(String namespaceName, String extensionName, String version, String path) {
        if(isBuiltInExtensionNamespace(namespaceName)) {
            return new ResponseEntity<>(("Built-in extension namespace '" + namespaceName + "' not allowed").getBytes(StandardCharsets.UTF_8), null, HttpStatus.BAD_REQUEST);
        }

        var extVersions = repositories.findActiveExtensionVersionDTOsByVersion(version, extensionName, namespaceName);
        var extVersion = extVersions.stream().max(Comparator.<ExtensionVersionDTO, Boolean>comparing(TargetPlatform::isUniversal)
                .thenComparing(ExtensionVersionDTO::getTargetPlatform))
                .orElse(null);

        if (extVersion == null) {
            return ResponseEntity.notFound().build();
        }

        var resources = repositories.findAllResourceFileResourceDTOs(extVersion.getId(), path);
        if(resources.isEmpty()) {
            throw new NotFoundException();
        } else if(resources.size() == 1 && resources.get(0).getName().equals(path)) {
            return browseFile(resources.get(0), namespaceName, extensionName, extVersion.getTargetPlatform(), version);
        } else {
            return browseDirectory(resources, namespaceName, extensionName, version, path);
        }
    }

    private ResponseEntity<byte[]> browseFile(
            FileResourceDTO resource,
            String namespaceName,
            String extensionName,
            String targetPlatform,
            String version
    ) {
        if (resource.getStorageType().equals(FileResource.STORAGE_DB)) {
            var headers = storageUtil.getFileResponseHeaders(resource.getName());
            return new ResponseEntity<>(resource.getContent(), headers, HttpStatus.OK);
        } else {
            var namespace = new Namespace();
            namespace.setName(namespaceName);

            var extension = new Extension();
            extension.setName(extensionName);
            extension.setNamespace(namespace);

            var extVersion = new ExtensionVersion();
            extVersion.setVersion(version);
            extVersion.setTargetPlatform(targetPlatform);
            extVersion.setExtension(extension);

            var fileResource = new FileResource();
            fileResource.setName(resource.getName());
            fileResource.setExtension(extVersion);
            fileResource.setStorageType(resource.getStorageType());

            return ResponseEntity.status(HttpStatus.FOUND)
                    .location(storageUtil.getLocation(fileResource))
                    .cacheControl(CacheControl.maxAge(7, TimeUnit.DAYS).cachePublic())
                    .build();
        }
    }

    private ResponseEntity<byte[]> browseDirectory(
            List<FileResourceDTO> resources,
            String namespaceName,
            String extensionName,
            String version,
            String path
    ) {
        if(!path.isEmpty() && !path.endsWith("/")) {
            path += "/";
        }

        var urls = new HashSet<String>();
        var baseUrl = UrlUtil.createApiUrl(UrlUtil.getBaseUrl(), "vscode", "unpkg", namespaceName, extensionName, version);
        for(var resource : resources) {
            var name = resource.getName();
            if(name.startsWith(path)) {
                var index = name.indexOf('/', path.length());
                var isDirectory = index != -1;
                if(isDirectory) {
                    name = name.substring(0, index);
                }

                var url = UrlUtil.createApiUrl(baseUrl, name.split("/"));
                if(isDirectory) {
                    url += '/';
                }

                urls.add(url);
            }
        }

        String json;
        try {
            json = new ObjectMapper().writeValueAsString(urls);
        } catch (JsonProcessingException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to generate JSON: " + e.getMessage());
        }

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .cacheControl(CacheControl.maxAge(7, TimeUnit.DAYS).cachePublic())
                .body(json.getBytes(StandardCharsets.UTF_8));
    }

    private boolean isBuiltInExtensionNamespace(String namespaceName) {
        return namespaceName.equals(BUILT_IN_EXTENSION_NAMESPACE);
    }
}
