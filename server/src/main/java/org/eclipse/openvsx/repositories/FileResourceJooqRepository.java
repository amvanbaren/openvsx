/** ******************************************************************************
 * Copyright (c) 2021 Precies. Software and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 * ****************************************************************************** */
package org.eclipse.openvsx.repositories;

import org.eclipse.openvsx.entities.Extension;
import org.eclipse.openvsx.entities.ExtensionVersion;
import org.eclipse.openvsx.entities.FileResource;
import org.eclipse.openvsx.entities.Namespace;
import org.eclipse.openvsx.util.TargetPlatform;
import org.eclipse.openvsx.util.VersionAlias;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.SelectQuery;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.eclipse.openvsx.jooq.Tables.*;

@Component
public class FileResourceJooqRepository {

    private final DSLContext dsl;

    public FileResourceJooqRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public List<FileResource> findByType(Collection<ExtensionVersion> extVersions, Collection<String> types) {
        if(extVersions.isEmpty() || types.isEmpty()) {
            return Collections.emptyList();
        }

        var extVersionsById = extVersions.stream().collect(Collectors.toMap(ExtensionVersion::getId, ev -> ev));
        return dsl.select(FILE_RESOURCE.ID, FILE_RESOURCE.EXTENSION_ID, FILE_RESOURCE.NAME, FILE_RESOURCE.TYPE)
                .from(FILE_RESOURCE)
                .where(FILE_RESOURCE.EXTENSION_ID.in(extVersionsById.keySet())).and(FILE_RESOURCE.TYPE.in(types))
                .fetch()
                .map(record -> toFileResource(record, extVersionsById));
    }

    public List<FileResource> findAll(Collection<Long> extensionIds, Collection<String> types) {
        return dsl.select(FILE_RESOURCE.ID, FILE_RESOURCE.EXTENSION_ID, FILE_RESOURCE.NAME, FILE_RESOURCE.TYPE)
                .from(FILE_RESOURCE)
                .where(FILE_RESOURCE.EXTENSION_ID.in(extensionIds).and(FILE_RESOURCE.TYPE.in(types)))
                .fetch()
                .map(this::toFileResource);
    }

    public List<FileResource> findAllResources(long extVersionId, String prefix) {
        return dsl.select(
                    FILE_RESOURCE.ID,
                    FILE_RESOURCE.EXTENSION_ID,
                    FILE_RESOURCE.NAME,
                    FILE_RESOURCE.TYPE,
                    FILE_RESOURCE.STORAGE_TYPE,
                    FILE_RESOURCE.CONTENT
                )
                .from(FILE_RESOURCE)
                .where(FILE_RESOURCE.TYPE.eq(FileResource.RESOURCE))
                .and(FILE_RESOURCE.EXTENSION_ID.eq(extVersionId))
                .and(FILE_RESOURCE.NAME.startsWith(prefix))
                .fetch()
                .map(record -> {
                    var fileResource = toFileResource(record);
                    fileResource.setStorageType(record.get(FILE_RESOURCE.STORAGE_TYPE));
                    fileResource.setContent(record.get(FILE_RESOURCE.CONTENT));

                    return fileResource;
                });
    }

    private FileResource toFileResource(Record record) {
        var extVersion = new ExtensionVersion();
        extVersion.setId(record.get(FILE_RESOURCE.EXTENSION_ID));

        return toFileResource(record, extVersion);
    }

    private FileResource toFileResource(Record record, Map<Long, ExtensionVersion> extVersionsById) {
        var extVersion = extVersionsById.get(record.get(FILE_RESOURCE.EXTENSION_ID));
        return toFileResource(record, extVersion);
    }

    private FileResource toFileResource(Record record, ExtensionVersion extVersion) {
        var fileResource = new FileResource();
        fileResource.setId(record.get(FILE_RESOURCE.ID));
        fileResource.setName(record.get(FILE_RESOURCE.NAME));
        fileResource.setType(record.get(FILE_RESOURCE.TYPE));
        fileResource.setExtension(extVersion);

        return fileResource;
    }

    public FileResource findByName(String namespace, String extension, String targetPlatform, String version, String name) {
        var onlyPreRelease = VersionAlias.PRE_RELEASE.equals(version);
        var query = findByQuery(namespace, extension, version, targetPlatform, onlyPreRelease);
        query.addConditions(FILE_RESOURCE.NAME.equalIgnoreCase(name));
        query.addOrderBy(FILE_RESOURCE.TYPE);

        return query.fetchOne(this::mapFindByQueryResult);
    }

    public FileResource findByType(String namespace, String extension, String targetPlatform, String version, String type) {
        var onlyPreRelease = VersionAlias.PRE_RELEASE.equals(version);
        var query = findByQuery(namespace, extension, version, targetPlatform, onlyPreRelease);
        query.addConditions(FILE_RESOURCE.TYPE.eq(type));
        return query.fetchOne(this::mapFindByQueryResult);
    }

    public FileResource findByTypeAndName(String namespace, String extension, String targetPlatform, String version, String type, String name) {
        var onlyPreRelease = VersionAlias.PRE_RELEASE.equals(version);
        var query = findByQuery(namespace, extension, version, targetPlatform, onlyPreRelease);
        query.addConditions(
                FILE_RESOURCE.TYPE.eq(type),
                FILE_RESOURCE.NAME.equalIgnoreCase(name)
        );
        return query.fetchOne(this::mapFindByQueryResult);
    }

    private SelectQuery<Record> findByQuery(
            String namespace,
            String extension,
            String version,
            String targetPlatform,
            boolean onlyPreRelease
    ) {
        var query = dsl.selectQuery();
        query.addSelect(
                NAMESPACE.ID,
                NAMESPACE.NAME,
                EXTENSION.ID,
                EXTENSION.NAME,
                EXTENSION_VERSION.ID,
                EXTENSION_VERSION.TARGET_PLATFORM,
                EXTENSION_VERSION.VERSION,
                FILE_RESOURCE.ID,
                FILE_RESOURCE.NAME,
                FILE_RESOURCE.TYPE,
                FILE_RESOURCE.STORAGE_TYPE
        );
        query.addFrom(FILE_RESOURCE);
        query.addJoin(EXTENSION_VERSION, EXTENSION_VERSION.ID.eq(FILE_RESOURCE.EXTENSION_ID));
        query.addJoin(EXTENSION, EXTENSION.ID.eq(EXTENSION_VERSION.EXTENSION_ID));
        query.addJoin(NAMESPACE, NAMESPACE.ID.eq(EXTENSION.NAMESPACE_ID));
        query.addConditions(
                NAMESPACE.NAME.equalIgnoreCase(namespace),
                EXTENSION.NAME.equalIgnoreCase(extension),
                EXTENSION.ACTIVE.eq(true),
                EXTENSION_VERSION.ACTIVE.eq(true)
        );
        if(!VersionAlias.LATEST.equals(version) && !VersionAlias.PRE_RELEASE.equals(version)) {
            query.addConditions(EXTENSION_VERSION.VERSION.eq(version));
        }
        if(TargetPlatform.isValid(targetPlatform)) {
            query.addConditions(EXTENSION_VERSION.TARGET_PLATFORM.eq(targetPlatform));
        }
        if(onlyPreRelease) {
            query.addConditions(EXTENSION_VERSION.PRE_RELEASE.eq(true));
        }

        query.addOrderBy(
                EXTENSION_VERSION.SEMVER_MAJOR.desc(),
                EXTENSION_VERSION.SEMVER_MINOR.desc(),
                EXTENSION_VERSION.SEMVER_PATCH.desc(),
                EXTENSION_VERSION.SEMVER_IS_PRE_RELEASE.asc(),
                EXTENSION_VERSION.UNIVERSAL_TARGET_PLATFORM.desc(),
                EXTENSION_VERSION.TARGET_PLATFORM.asc(),
                EXTENSION_VERSION.TIMESTAMP.desc()
        );
        query.addLimit(1);
        return query;
    }

    private FileResource mapFindByQueryResult(Record record) {
        var namespace = new Namespace();
        namespace.setId(record.get(NAMESPACE.ID));
        namespace.setName(record.get(NAMESPACE.NAME));

        var extension = new Extension();
        extension.setId(record.get(EXTENSION.ID));
        extension.setName(record.get(EXTENSION.NAME));
        extension.setNamespace(namespace);

        var extVersion = new ExtensionVersion();
        extVersion.setId(record.get(EXTENSION_VERSION.ID));
        extVersion.setTargetPlatform(record.get(EXTENSION_VERSION.TARGET_PLATFORM));
        extVersion.setVersion(record.get(EXTENSION_VERSION.VERSION));
        extVersion.setExtension(extension);

        var resource = new FileResource();
        resource.setId(record.get(FILE_RESOURCE.ID));
        resource.setName(record.get(FILE_RESOURCE.NAME));
        resource.setType(record.get(FILE_RESOURCE.TYPE));
        resource.setStorageType(record.get(FILE_RESOURCE.STORAGE_TYPE));
        resource.setExtension(extVersion);
        return resource;
    }

    public Integer countFileResourceNodeModules(String[] types) {
        var count = DSL.count().as("count");
        var query = dsl.selectQuery();
        query.addSelect(count);
        query.addFrom(FILE_RESOURCE);
        query.addConditions(FILE_RESOURCE.NAME.like("%/node_modules/%"));
        if(types.length > 1) {
            query.addConditions(FILE_RESOURCE.TYPE.in(types));
        } else if(types.length == 1) {
            query.addConditions(FILE_RESOURCE.TYPE.eq(types[0]));
        }

        return query.fetchOne(count);
    }

    public Integer countFileResources(String[] types) {
        var count = DSL.count().as("count");
        var query = dsl.selectQuery();
        query.addSelect(count);
        query.addFrom(FILE_RESOURCE);
        if(types.length > 1) {
            query.addConditions(FILE_RESOURCE.TYPE.in(types));
        } else if(types.length == 1) {
            query.addConditions(FILE_RESOURCE.TYPE.eq(types[0]));
        }

        return query.fetchOne(count);
    }
}
