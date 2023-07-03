/** ******************************************************************************
 * Copyright (c) 2022 Precies. Software Ltd and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 * ****************************************************************************** */
package org.eclipse.openvsx.publish;

import com.google.common.base.Joiner;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.eclipse.openvsx.ExtensionProcessor;
import org.eclipse.openvsx.ExtensionService;
import org.eclipse.openvsx.ExtensionValidator;
import org.eclipse.openvsx.UserService;
import org.eclipse.openvsx.adapter.VSCodeIdService;
import org.eclipse.openvsx.entities.*;
import org.eclipse.openvsx.repositories.RepositoryService;
import org.eclipse.openvsx.storage.StorageUtilService;
import org.eclipse.openvsx.util.ErrorResultException;
import org.eclipse.openvsx.util.NamingUtil;
import org.eclipse.openvsx.util.TempFile;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class PublishExtensionVersionService {

    @Autowired
    RepositoryService repositories;

    @Autowired
    EntityManager entityManager;

    @Autowired
    StorageUtilService storageUtil;

    @Autowired
    ExtensionVersionIntegrityService integrityService;

    @Autowired
    UserService users;

    @Autowired
    ExtensionValidator validator;

    @Autowired
    VSCodeIdService vsCodeIdService;

    @Transactional
    public ExtensionVersion createExtensionVersion(ExtensionProcessor processor, PersonalAccessToken token, LocalDateTime timestamp, boolean checkDependencies) {
        // Extract extension metadata from its manifest
        var extVersion = createExtensionVersion(processor, token.getUser(), token, timestamp);
        var dependencies = processor.getExtensionDependencies();
        var bundledExtensions = processor.getBundledExtensions();
        if (checkDependencies) {
            dependencies = dependencies.stream()
                    .map(this::checkDependency)
                    .collect(Collectors.toList());
            bundledExtensions = bundledExtensions.stream()
                    .map(this::checkBundledExtension)
                    .collect(Collectors.toList());
        }

        extVersion.setDependencies(dependencies);
        extVersion.setBundledExtensions(bundledExtensions);
        if(integrityService.isEnabled()) {
            extVersion.setSignatureKeyPair(repositories.findActiveKeyPair());
        }

        return extVersion;
    }

    private ExtensionVersion createExtensionVersion(ExtensionProcessor processor, UserData user, PersonalAccessToken token, LocalDateTime timestamp) {
        var namespaceName = processor.getNamespace();
        var namespace = repositories.findNamespace(namespaceName);
        if (namespace == null) {
            throw new ErrorResultException("Unknown publisher: " + namespaceName
                    + "\nUse the 'create-namespace' command to create a namespace corresponding to your publisher name.");
        }
        if (!users.hasPublishPermission(user, namespace)) {
            throw new ErrorResultException("Insufficient access rights for publisher: " + namespace.getName());
        }

        var extensionName = processor.getExtensionName();
        var nameIssue = validator.validateExtensionName(extensionName);
        if (nameIssue.isPresent()) {
            throw new ErrorResultException(nameIssue.get().toString());
        }

        var versionIssue = validator.validateExtensionVersion(processor.getVersion());
        if (versionIssue.isPresent()) {
            throw new ErrorResultException(versionIssue.get().toString());
        }

        var extVersion = processor.getMetadata();
        if (extVersion.getDisplayName() != null && extVersion.getDisplayName().trim().isEmpty()) {
            extVersion.setDisplayName(null);
        }
        extVersion.setTimestamp(timestamp);
        extVersion.setPublishedWith(token);
        extVersion.setActive(false);

        var extension = repositories.findExtension(extensionName, namespace);
        if (extension == null) {
            extension = new Extension();
            extension.setActive(false);
            extension.setName(extensionName);
            extension.setNamespace(namespace);
            extension.setPublishedDate(extVersion.getTimestamp());

            var updateExistingPublicIds = vsCodeIdService.setPublicIds(extension);
            if(updateExistingPublicIds) {
                updateExistingPublicIds(extension).forEach(vsCodeIdService::updateExtensionPublicId);
            }

            entityManager.persist(extension);
        } else {
            var existingVersion = repositories.findVersion(extVersion.getVersion(), extVersion.getTargetPlatform(), extension);
            if (existingVersion != null) {
                var extVersionId = NamingUtil.toLogFormat(namespaceName, extensionName, extVersion.getTargetPlatform(), extVersion.getVersion());
                var message = "Extension " + extVersionId + " is already published";
                message += existingVersion.isActive() ? "." : ", but currently is deactivated and therefore not visible.";
                throw new ErrorResultException(message);
            }
        }

        extension.setLastUpdatedDate(extVersion.getTimestamp());
        extension.getVersions().add(extVersion);
        extVersion.setExtension(extension);

        var metadataIssues = validator.validateMetadata(extVersion);
        if (!metadataIssues.isEmpty()) {
            if (metadataIssues.size() == 1) {
                throw new ErrorResultException(metadataIssues.get(0).toString());
            }
            throw new ErrorResultException("Multiple issues were found in the extension metadata:\n"
                    + Joiner.on("\n").join(metadataIssues));
        }

        entityManager.persist(extVersion);
        return extVersion;
    }

    private String checkDependency(String dependency) {
        var split = dependency.split("\\.");
        if (split.length != 2 || split[0].isEmpty() || split[1].isEmpty()) {
            throw new ErrorResultException("Invalid 'extensionDependencies' format. Expected: '${namespace}.${name}'");
        }
        var extensionCount = repositories.countExtensions(split[1], split[0]);
        if (extensionCount == 0) {
            throw new ErrorResultException("Cannot resolve dependency: " + dependency);
        }

        return dependency;
    }

    private String checkBundledExtension(String bundledExtension) {
        var split = bundledExtension.split("\\.");
        if (split.length != 2 || split[0].isEmpty() || split[1].isEmpty()) {
            throw new ErrorResultException("Invalid 'extensionPack' format. Expected: '${namespace}.${name}'");
        }

        return bundledExtension;
    }

    private List<Extension> updateExistingPublicIds(Extension extension) {
        var updated = true;
        var updatedExtensions = new ArrayList<Extension>();
        var newExtension = extension;
        while(updated) {
            updated = false;
            var oldExtension = repositories.findExtensionByPublicId(newExtension.getPublicId());
            if (oldExtension != null && !oldExtension.equals(newExtension)) {
                entityManager.detach(oldExtension);
                updated = vsCodeIdService.setPublicIds(oldExtension);
            }
            if(updated) {
                updatedExtensions.add(oldExtension);
                newExtension = oldExtension;
            }
        }

        Collections.reverse(updatedExtensions);
        return updatedExtensions;
    }

    @Transactional
    public void deleteFileResources(ExtensionVersion extVersion) {
        repositories.findFiles(extVersion)
                .filter(resource -> !resource.getType().equals(FileResource.DOWNLOAD))
                .forEach(entityManager::remove);
    }

    @Transactional
    public void storeDownload(FileResource download) {
        if (storageUtil.shouldStoreExternally(download)) {
            storageUtil.uploadFile(download);
            download.setContent(null);
        }
    }

    @Retryable
    public void storeResource(FileResource resource) {
        // Store file resource in the DB or external storage
        if (storageUtil.shouldStoreExternally(resource)) {
            storageUtil.uploadFile(resource);
            // Don't store the binary content in the DB - it's now stored externally
            resource.setContent(null);
        } else {
            resource.setStorageType(FileResource.STORAGE_DB);
        }
    }

    @Transactional
    public void mirror(FileResource download, TempFile extensionFile, String signatureName) {
        var extVersion = download.getExtension();
        mirrorResource(download);
        if(signatureName != null) {
            mirrorResource(getSignatureResource(signatureName, extVersion));
        }
        try(var processor = new ExtensionProcessor(extensionFile)) {
            processor.getFileResources(extVersion).forEach(resource -> mirrorResource(resource));
            mirrorResource(processor.generateSha256Checksum(extVersion));
            // don't store file resources, they can be generated on the fly to avoid traversing entire zip file
        }
    }

    private void mirrorResource(FileResource resource) {
        resource.setStorageType(storageUtil.getActiveStorageType());
        // Don't store the binary content in the DB - it's now stored externally
        resource.setContent(null);
        entityManager.persist(resource);
    }

    private FileResource getSignatureResource(String signatureName, ExtensionVersion extVersion) {
        var resource = new FileResource();
        resource.setExtension(extVersion);
        resource.setName(signatureName);
        resource.setType(FileResource.DOWNLOAD_SIG);
        return resource;
    }

    @Transactional
    public void persistResource(FileResource resource) {
        entityManager.persist(resource);
    }

    @Transactional
    public void persistDownload(FileResource download) {
        persistResource(download);
        entityManager.flush();
    }

    @Transactional
    public void activateExtension(ExtensionVersion extVersion, ExtensionService extensions) {
        extVersion.setActive(true);
        extVersion = entityManager.merge(extVersion);
        extensions.updateExtension(extVersion.getExtension());
    }

    @Transactional
    public FileResource getDownload(PublishExtensionVersionJobRequest jobRequest) {
        return entityManager.find(FileResource.class, jobRequest.getDownloadId());
    }
}
