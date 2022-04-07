/** ******************************************************************************
 * Copyright (c) 2022 Precies. Software and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 * ****************************************************************************** */
package org.eclipse.openvsx.json;

import org.apache.jena.ext.com.google.common.collect.Maps;
import org.eclipse.openvsx.dto.ExtensionVersionDTO;
import org.eclipse.openvsx.entities.*;
import org.eclipse.openvsx.util.ErrorResultException;
import org.eclipse.openvsx.util.TimeUtil;
import org.eclipse.openvsx.util.UrlUtil;
import org.keycloak.adapters.springsecurity.token.KeycloakAuthenticationToken;
import org.springframework.data.util.Streamable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class JsonService {

    public ExtensionJson toExtensionJson(ExtensionVersion extVersion) {
        return toExtensionJson(toDTO(extVersion));
    }

    public ExtensionJson toExtensionJson(ExtensionVersionDTO extVersion) {
        var json = new ExtensionJson();
        var extension = extVersion.getExtension();
        json.targetPlatform = extVersion.getTargetPlatform();
        json.namespace = extension.getNamespace().getName();
        json.name = extension.getName();
        json.averageRating = extension.getAverageRating();
        json.downloadCount = extension.getDownloadCount();
        json.version = extVersion.getVersion();
        json.preRelease = extVersion.isPreRelease();
        if (extVersion.getTimestamp() != null) {
            json.timestamp = TimeUtil.toUTCString(extVersion.getTimestamp());
        }
        json.displayName = extVersion.getDisplayName();
        json.description = extVersion.getDescription();
        json.engines = getEnginesMap(extVersion);
        json.categories = extVersion.getCategories();
        json.extensionKind = extVersion.getExtensionKind();
        json.tags = extVersion.getTags();
        json.license = extVersion.getLicense();
        json.homepage = extVersion.getHomepage();
        json.repository = extVersion.getRepository();
        json.bugs = extVersion.getBugs();
        json.markdown = extVersion.getMarkdown();
        json.galleryColor = extVersion.getGalleryColor();
        json.galleryTheme = extVersion.getGalleryTheme();
        json.qna = extVersion.getQna();
        if (extVersion.getPublishedWithUserId() != null) {
            json.publishedBy = new UserJson();
            json.publishedBy.userId = extVersion.getPublishedWithUserId();
        }
        if (extVersion.getDependencies() != null) {
            json.dependencies = toExtensionReferenceJson(extVersion.getDependencies());
        }
        if (extVersion.getBundledExtensions() != null) {
            json.bundledExtensions = toExtensionReferenceJson(extVersion.getBundledExtensions());
        }
        return json;
    }

    private List<ExtensionReferenceJson> toExtensionReferenceJson(List<String> extensionReferences) {
        return extensionReferences.stream().map(fqn -> {
            var startIndex = fqn.indexOf('.');
            var lastIndex = fqn.lastIndexOf('.');
            if (startIndex <= 0 || lastIndex >= fqn.length() - 1 || startIndex != lastIndex) {
                return null;
            }
            var ref = new ExtensionReferenceJson();
            ref.namespace = fqn.substring(0, startIndex);
            ref.extension = fqn.substring(startIndex + 1);
            return ref;
        }).filter(Objects::nonNull).collect(Collectors.toList());
    }

    public SearchEntryJson toSearchEntryJson(ExtensionVersion extVersion) {
        var extension = extVersion.getExtension();
        var entry = new SearchEntryJson();
        entry.name = extension.getName();
        entry.namespace = extension.getNamespace().getName();
        entry.averageRating = extension.getAverageRating();
        entry.downloadCount = extension.getDownloadCount();
        entry.version = extVersion.getVersion();
        entry.timestamp = TimeUtil.toUTCString(extVersion.getTimestamp());
        entry.displayName = extVersion.getDisplayName();
        entry.description = extVersion.getDescription();
        return entry;
    }

    private Map<String, String> getEnginesMap(ExtensionVersionDTO extVersion) {
        var engines = extVersion.getEngines();
        if (engines == null)
            return null;
        var result = Maps.<String, String>newLinkedHashMapWithExpectedSize(engines.size());
        for (var engine : engines) {
            var split = engine.split("@");
            if (split.length == 2) {
                result.put(split[0], split[1]);
            }
        }
        return result;
    }

    public NamespaceMembershipJson toNamespaceMembershipJson(NamespaceMembership membership) {
        var json = new NamespaceMembershipJson();
        json.namespace = membership.getNamespace().getName();
        json.role = membership.getRole();
        json.user = new UserJson();
        json.user.userId = membership.getUserId();
        return json;
    }

    public AccessTokenJson toAccessTokenJson(PersonalAccessToken token) {
        var json = new AccessTokenJson();
        json.id = token.getId();
        // The value is not included: it is displayed only when the token is created
        if (token.getCreatedTimestamp() != null)
            json.createdTimestamp = TimeUtil.toUTCString(token.getCreatedTimestamp());
        if (token.getAccessedTimestamp() != null)
            json.accessedTimestamp = TimeUtil.toUTCString(token.getAccessedTimestamp());
        json.description = token.getDescription();
        return json;
    }

    public ReviewJson toReviewJson(ExtensionReview review) {
        var json = new ReviewJson();
        json.timestamp = TimeUtil.toUTCString(review.getTimestamp());
        json.title = review.getTitle();
        json.comment = review.getComment();
        json.rating = review.getRating();
        json.user = new UserJson();
        json.user.userId = review.getUserId();
        return json;
    }

    public UserJson toUserJson(Principal principal) {
        var token = ((KeycloakAuthenticationToken) principal).getAccount().getKeycloakSecurityContext().getToken();
        var json = new UserJson();
        json.userName = token.getPreferredUsername();
        json.role = token.getRealmAccess().isUserInRole("admin") ? "admin" : null;
        json.fullName = token.getName();
        json.avatarUrl = (String) token.getOtherClaims().get("avatar_url");
        json.homepage = (String) token.getOtherClaims().get("homepage");
        return json;
    }

    public UserPublishInfoJson toUserPublishInfoJson(String userId, int activeAccessTokenNum, List<ExtensionJson> versions) {
        var userPublishInfo = new UserPublishInfoJson();
        userPublishInfo.extensions = versions;
        userPublishInfo.activeAccessTokenNum = activeAccessTokenNum;
        userPublishInfo.user = new UserJson();
        userPublishInfo.user.userId = userId;

        return userPublishInfo;
    }

    public ResultJson success(String message) {
        var json = new ResultJson();
        json.success = message;
        return json;
    }

    public ResultJson error(String message) {
        var json = new ResultJson();
        json.error = message;
        return json;
    }

    public <T extends ResultJson> T error(String message, Class<T> resultType) {
        try {
            var json = resultType.getDeclaredConstructor().newInstance();
            json.error = message;
            return json;
        } catch (ReflectiveOperationException exc) {
            throw new RuntimeException(exc);
        }
    }

    public ResponseEntity<ResultJson> toResponseEntity(ErrorResultException exception) {
        return new ResponseEntity<>(error(exception.getMessage()), getResponseStatus(exception));
    }

    public <T extends ResultJson> ResponseEntity<T> toResponseEntity(ErrorResultException exception, Class<T> resultType) {
        return new ResponseEntity<>(error(exception.getMessage(), resultType), getResponseStatus(exception));
    }

    private HttpStatus getResponseStatus(ErrorResultException exception) {
        var status = exception.getStatus();
        if(status == null) {
            status = HttpStatus.BAD_REQUEST;
        }

        return status;
    }

    public ResultJson concatResults(List<ResultJson> results) {
        var result = new ResultJson();
        result.error = results.stream().map(r -> r.error).filter(Objects::nonNull).collect(Collectors.joining("\n"));
        result.success = results.stream().map(r -> r.success).filter(Objects::nonNull).collect(Collectors.joining("\n"));
        return result;
    }

    public NamespaceJson toNamespaceJson(Namespace namespace, Streamable<Extension> extensions) {
        var json = new NamespaceJson();
        json.name = namespace.getName();
        json.verified = true;

        var serverUrl = UrlUtil.getBaseUrl();
        var extensionUrl = UrlUtil.createApiUrl(serverUrl, "api", namespace.getName());
        json.extensions = extensions.stream().collect(Collectors.toMap(e -> e.getName(), e -> UrlUtil.createApiUrl(extensionUrl, e.getName())));
        json.membersUrl = UrlUtil.createApiUrl(serverUrl, "user", "namespace", namespace.getName(), "members");
        json.roleUrl = UrlUtil.createApiUrl(serverUrl, "user", "namespace", namespace.getName(), "role");
        return json;
    }

    public SearchEntryJson.VersionReference toVersionReference(ExtensionVersion extVersion) {
        var json = new SearchEntryJson.VersionReference();
        json.version = extVersion.getVersion();
        json.engines = getEnginesMap(toDTO(extVersion));
        return json;
    }

    private ExtensionVersionDTO toDTO(ExtensionVersion extVersion) {
        var publishedWithUserId = extVersion.getPublishedWith() != null ? extVersion.getPublishedWith().getUserId() : null;
        var extension = extVersion.getExtension();
        var namespace = extension.getNamespace();

        return new ExtensionVersionDTO(namespace.getId(), namespace.getPublicId(), namespace.getName(),
                extension.getId(), extension.getPublicId(), extension.getName(), extension.getAverageRating(),
                extension.getDownloadCount(), extension.getPublishedDate(), extension.getLastUpdatedDate(),
                publishedWithUserId, extVersion.getId(), extVersion.getVersion(), extVersion.getTargetPlatform(),
                extVersion.isPreview(), extVersion.isPreRelease(), extVersion.getTimestamp(), extVersion.getDisplayName(),
                extVersion.getDescription(), extVersion.getEngines(), extVersion.getCategories(), extVersion.getTags(),
                extVersion.getExtensionKind(), extVersion.getLicense(), extVersion.getHomepage(), extVersion.getRepository(),
                extVersion.getBugs(), extVersion.getMarkdown(), extVersion.getGalleryColor(), extVersion.getGalleryTheme(),
                extVersion.getQna(), extVersion.getDependencies(), extVersion.getBundledExtensions());
    }
}
