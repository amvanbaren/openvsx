/** ******************************************************************************
 * Copyright (c) 2021 Precies. Software and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 * ****************************************************************************** */
package org.eclipse.openvsx.dto;

import org.eclipse.openvsx.entities.ListOfStringConverter;
import org.eclipse.openvsx.util.SemanticVersion;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

public class ExtensionVersionDTO {

    private static final ListOfStringConverter LIST_OF_STRING_CONVERTER = new ListOfStringConverter();

    private final long extensionId;
    private ExtensionDTO extension;

    private final long id;
    private final String version;
    private final String targetPlatform;
    private SemanticVersion semver;
    private final boolean preview;
    private final boolean preRelease;
    private final LocalDateTime timestamp;
    private String publishedWithUserId;
    private final String displayName;
    private final String description;
    private final List<String> engines;
    private final List<String> categories;
    private final List<String> tags;
    private final List<String> extensionKind;
    private String license;
    private String homepage;
    private final String repository;
    private String bugs;
    private String markdown;
    private final String galleryColor;
    private final String galleryTheme;
    private String qna;
    private final List<String> dependencies;
    private final List<String> bundledExtensions;

    public ExtensionVersionDTO(
            long namespaceId,
            String namespacePublicId,
            String namespaceName,
            long extensionId,
            String extensionPublicId,
            String extensionName,
            Double extensionAverageRating,
            int extensionDownloadCount,
            LocalDateTime extensionPublishedDate,
            LocalDateTime extensionLastUpdatedDate,
            String publishedWithUserId,
            long id,
            String version,
            String targetPlatform,
            boolean preview,
            boolean preRelease,
            LocalDateTime timestamp,
            String displayName,
            String description,
            String engines,
            String categories,
            String tags,
            String extensionKind,
            String license,
            String homepage,
            String repository,
            String bugs,
            String markdown,
            String galleryColor,
            String galleryTheme,
            String qna,
            String dependencies,
            String bundledExtensions
    ) {
        this(
            namespaceId,
            namespacePublicId,
            namespaceName,
            extensionId,
            extensionPublicId,
            extensionName,
            extensionAverageRating,
            extensionDownloadCount,
            extensionPublishedDate,
            extensionLastUpdatedDate,
            publishedWithUserId,
            id,
            version,
            targetPlatform,
            preview,
            preRelease,
            timestamp,
            displayName,
            description,
            LIST_OF_STRING_CONVERTER.convertToEntityAttribute(engines),
            LIST_OF_STRING_CONVERTER.convertToEntityAttribute(categories),
            LIST_OF_STRING_CONVERTER.convertToEntityAttribute(tags),
            LIST_OF_STRING_CONVERTER.convertToEntityAttribute(extensionKind),
            license,
            homepage,
            repository,
            bugs,
            markdown,
            galleryColor,
            galleryTheme,
            qna,
            LIST_OF_STRING_CONVERTER.convertToEntityAttribute(dependencies),
            LIST_OF_STRING_CONVERTER.convertToEntityAttribute(bundledExtensions)
        );
    }

    public ExtensionVersionDTO(
            long extensionId,
            long id,
            String version,
            String targetPlatform,
            boolean preview,
            boolean preRelease,
            LocalDateTime timestamp,
            String displayName,
            String description,
            String engines,
            String categories,
            String tags,
            String extensionKind,
            String repository,
            String galleryColor,
            String galleryTheme,
            String dependencies,
            String bundledExtensions
    ) {
        this(
            extensionId,
            id,
            version,
            targetPlatform,
            preview,
            preRelease,
            timestamp,
            displayName,
            description,
            LIST_OF_STRING_CONVERTER.convertToEntityAttribute(engines),
            LIST_OF_STRING_CONVERTER.convertToEntityAttribute(categories),
            LIST_OF_STRING_CONVERTER.convertToEntityAttribute(tags),
            LIST_OF_STRING_CONVERTER.convertToEntityAttribute(extensionKind),
            repository,
            galleryColor,
            galleryTheme,
            LIST_OF_STRING_CONVERTER.convertToEntityAttribute(dependencies),
            LIST_OF_STRING_CONVERTER.convertToEntityAttribute(bundledExtensions)
        );
    }

    public ExtensionVersionDTO(
            long namespaceId,
            String namespacePublicId,
            String namespaceName,
            long extensionId,
            String extensionPublicId,
            String extensionName,
            Double extensionAverageRating,
            int extensionDownloadCount,
            LocalDateTime extensionPublishedDate,
            LocalDateTime extensionLastUpdatedDate,
            String publishedWithUserId,
            long id,
            String version,
            String targetPlatform,
            boolean preview,
            boolean preRelease,
            LocalDateTime timestamp,
            String displayName,
            String description,
            List<String> engines,
            List<String> categories,
            List<String> tags,
            List<String> extensionKind,
            String license,
            String homepage,
            String repository,
            String bugs,
            String markdown,
            String galleryColor,
            String galleryTheme,
            String qna,
            List<String> dependencies,
            List<String> bundledExtensions
    ) {
        this(
                extensionId,
                id,
                version,
                targetPlatform,
                preview,
                preRelease,
                timestamp,
                displayName,
                description,
                engines,
                categories,
                tags,
                extensionKind,
                repository,
                galleryColor,
                galleryTheme,
                dependencies,
                bundledExtensions
        );

        this.extension = new ExtensionDTO(
                extensionId,
                extensionPublicId,
                extensionName,
                extensionAverageRating,
                extensionDownloadCount,
                extensionPublishedDate,
                extensionLastUpdatedDate,
                namespaceId,
                namespacePublicId,
                namespaceName
        );

        this.publishedWithUserId = publishedWithUserId;
        this.license = license;
        this.homepage = homepage;
        this.bugs = bugs;
        this.markdown = markdown;
        this.qna = qna;
    }

    private ExtensionVersionDTO(
            long extensionId,
            long id,
            String version,
            String targetPlatform,
            boolean preview,
            boolean preRelease,
            LocalDateTime timestamp,
            String displayName,
            String description,
            List<String> engines,
            List<String> categories,
            List<String> tags,
            List<String> extensionKind,
            String repository,
            String galleryColor,
            String galleryTheme,
            List<String> dependencies,
            List<String> bundledExtensions
    ) {
        this.extensionId = extensionId;
        this.id = id;
        this.version = version;
        this.targetPlatform = targetPlatform;
        this.preview = preview;
        this.preRelease = preRelease;
        this.timestamp = timestamp;
        this.displayName = displayName;
        this.description = description;
        this.engines = engines;
        this.categories = categories;
        this.tags = tags;
        this.extensionKind = extensionKind;
        this.repository = repository;
        this.galleryColor = galleryColor;
        this.galleryTheme = galleryTheme;
        this.dependencies = dependencies;
        this.bundledExtensions = bundledExtensions;
    }

    public long getExtensionId() {
        return extensionId;
    }

    public ExtensionDTO getExtension() {
        return extension;
    }

    public void setExtension(ExtensionDTO extension) {
        if(extension.getId() == extensionId) {
            this.extension = extension;
        } else {
            throw new IllegalArgumentException("extension must have the same id as extensionId");
        }
    }

    public long getId() {
        return id;
    }

    public String getVersion() {
        return version;
    }

    public SemanticVersion getSemanticVersion() {
        if (semver == null) {
            var version = getVersion();
            if (version != null)
                semver = new SemanticVersion(version);
        }
        return semver;
    }

    public String getTargetPlatform() { return targetPlatform; }

    public boolean isPreview() {
        return preview;
    }

    public boolean isPreRelease() {
        return preRelease;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public String getPublishedWithUserId() {
        return publishedWithUserId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public List<String> getEngines() {
        return engines;
    }

    public List<String> getCategories() {
        return categories;
    }

    public List<String> getTags() {
        return tags;
    }

    public List<String> getExtensionKind() {
        return extensionKind;
    }

    public String getLicense() {
        return license;
    }

    public String getHomepage() {
        return homepage;
    }

    public String getRepository() {
        return repository;
    }

    public String getBugs() {
        return bugs;
    }

    public String getMarkdown() {
        return markdown;
    }

    public String getGalleryColor() {
        return galleryColor;
    }

    public String getGalleryTheme() {
        return galleryTheme;
    }

    public String getQna() {
        return qna;
    }

    public List<String> getDependencies() {
        return dependencies;
    }

    public List<String> getBundledExtensions() {
        return bundledExtensions;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ExtensionVersionDTO that = (ExtensionVersionDTO) o;
        return extensionId == that.extensionId && id == that.id && preview == that.preview && preRelease == that.preRelease && Objects.equals(extension, that.extension) && Objects.equals(version, that.version) && Objects.equals(targetPlatform, that.targetPlatform) && Objects.equals(semver, that.semver) && Objects.equals(timestamp, that.timestamp) && Objects.equals(publishedWithUserId, that.publishedWithUserId) && Objects.equals(displayName, that.displayName) && Objects.equals(description, that.description) && Objects.equals(engines, that.engines) && Objects.equals(categories, that.categories) && Objects.equals(tags, that.tags) && Objects.equals(extensionKind, that.extensionKind) && Objects.equals(license, that.license) && Objects.equals(homepage, that.homepage) && Objects.equals(repository, that.repository) && Objects.equals(bugs, that.bugs) && Objects.equals(markdown, that.markdown) && Objects.equals(galleryColor, that.galleryColor) && Objects.equals(galleryTheme, that.galleryTheme) && Objects.equals(qna, that.qna) && Objects.equals(dependencies, that.dependencies) && Objects.equals(bundledExtensions, that.bundledExtensions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(extensionId, extension, id, version, targetPlatform, semver, preview, preRelease, timestamp, publishedWithUserId, displayName, description, engines, categories, tags, extensionKind, license, homepage, repository, bugs, markdown, galleryColor, galleryTheme, qna, dependencies, bundledExtensions);
    }
}
