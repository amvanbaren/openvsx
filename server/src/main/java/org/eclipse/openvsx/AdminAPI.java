/********************************************************************************
 * Copyright (c) 2020 TypeFox and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/
package org.eclipse.openvsx;

import java.security.Principal;
import java.time.Period;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;
import java.net.URI;

import com.google.common.base.Strings;

import org.eclipse.openvsx.entities.ExtensionVersion;
import org.eclipse.openvsx.entities.PersistedLog;
import org.eclipse.openvsx.json.*;
import org.eclipse.openvsx.keycloak.KeycloakService;
import org.eclipse.openvsx.repositories.RepositoryService;
import org.eclipse.openvsx.search.SearchUtilService;
import org.eclipse.openvsx.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.util.Streamable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class AdminAPI {

    @Autowired
    RepositoryService repositories;

    @Autowired
    AdminService admins;

    @Autowired
    LocalRegistryService local;

    @Autowired
    SearchUtilService search;

    @Autowired
    KeycloakService keycloak;

    @Autowired
    JsonService json;

    @GetMapping(
            path = "/admin/report",
            produces = "text/csv"
    )
    public ResponseEntity<String> getReport(
            @RequestParam String token,
            @RequestParam int year,
            @RequestParam int month
    ) {
        try {
            var accessToken = repositories.findAccessToken(token);
            if(accessToken == null || !accessToken.isActive() || accessToken.getUserId() == null || !keycloak.getUserRoles(accessToken.getUserId()).contains("admin")) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }

            return ResponseEntity.ok(admins.getAdminStatisticsCsv(year, month));
        } catch (ErrorResultException exc) {
            return ResponseEntity.status(exc.getStatus()).body(exc.getMessage());
        }
    }

    @GetMapping(
        path = "/admin/stats",
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<StatsJson> getStats() {
        var statsJson = new StatsJson();
        statsJson.userCount = keycloak.countUsers();
        statsJson.extensionCount = repositories.countExtensions();
        statsJson.namespaceCount = repositories.countNamespaces();
        return ResponseEntity.ok(statsJson);
    }

    @GetMapping(
        path = "/admin/log",
        produces = MediaType.TEXT_PLAIN_VALUE
    )
    public String getLog(@RequestParam(name = "period", required = false) String periodString) {
        Streamable<PersistedLog> logs;
        if (Strings.isNullOrEmpty(periodString)) {
            logs = repositories.findAllPersistedLogs();
        } else {
            try {
                var period = Period.parse(periodString);
                var now = TimeUtil.getCurrentUTC();
                logs = repositories.findPersistedLogsAfter(now.minus(period));
            } catch (DateTimeParseException exc) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid period");
            }
        }
        return logs.stream()
                .map(this::toString)
                .collect(Collectors.joining("\n")) + "\n";
    }

    private String toString(PersistedLog log) {
        return String.join("\t", log.getTimestamp().withNano(0).toString(), keycloak.getUserName(log.getUserId()), log.getMessage());
    }

    @PostMapping(
        path = "/admin/update-search-index",
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<ResultJson> updateSearchIndex(Principal principal) {
        search.updateSearchIndex(true);

        var result = json.success("Updated search index");
        admins.logAdminAction(UserUtil.getUserId(principal), result);
        return ResponseEntity.ok(result);
    }

    @GetMapping(
        path = "/admin/extension/{namespaceName}/{extensionName}",
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<ExtensionJson> getExtension(
            @PathVariable String namespaceName,
            @PathVariable String extensionName
    ) {
        var extension = repositories.findExtension(extensionName, namespaceName);
        if (extension == null) {
            var error = json.error("Extension not found: " + namespaceName + "." + extensionName, ExtensionJson.class);
            return new ResponseEntity<>(error, HttpStatus.NOT_FOUND);
        }

        ExtensionJson extensionJson;
        // Don't rely on the 'latest' relationship here because the extension might be inactive
        var latest = VersionUtil.getLatest(repositories.findVersions(extension), Collections.emptyList());
        if (latest == null) {
            extensionJson = new ExtensionJson();
            extensionJson.namespace = extension.getNamespace().getName();
            extensionJson.name = extension.getName();
            extensionJson.allVersions = Collections.emptyMap();
            extensionJson.allTargetPlatformVersions = Collections.emptyMap();
        } else {
            extensionJson = local.toExtensionVersionJson(latest, null, false);
            keycloak.enrichUserJson(extensionJson);
            extensionJson.allTargetPlatformVersions = extension.getVersions().stream()
                    .collect(Collectors.groupingBy(ExtensionVersion::getVersion, Collectors.mapping(ExtensionVersion::getTargetPlatform, Collectors.toList())));
        }
        extensionJson.active = extension.isActive();
        return ResponseEntity.ok(extensionJson);
    }

    @PostMapping(
        path = "/admin/extension/{namespaceName}/{extensionName}/delete",
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<ResultJson> deleteExtension(
            Principal principal,
            @PathVariable String namespaceName,
            @PathVariable String extensionName,
            @RequestBody(required = false) List<TargetPlatformVersionJson> targetVersions
    ) {
        try {
            ResultJson result;
            var adminId = UserUtil.getUserId(principal);
            if (targetVersions == null) {
                result = admins.deleteExtension(namespaceName, extensionName, adminId);
            } else {
                var results = new ArrayList<ResultJson>();
                for (var targetVersion : targetVersions) {
                    results.add(admins.deleteExtension(namespaceName, extensionName, targetVersion.targetPlatform, targetVersion.version, adminId));
                }

                result = json.concatResults(results);
            }

            return ResponseEntity.ok(result);
        } catch(ErrorResultException exc) {
            return json.toResponseEntity(exc);
        }
    }

    @GetMapping(
        path = "/admin/namespace/{namespaceName}",
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<NamespaceJson> getNamespace(@PathVariable String namespaceName) {
        try {
            var namespace = local.getNamespace(namespaceName);
            var serverUrl = UrlUtil.getBaseUrl();
            namespace.membersUrl = UrlUtil.createApiUrl(serverUrl, "admin", "namespace", namespace.name, "members");
            namespace.roleUrl = UrlUtil.createApiUrl(serverUrl, "admin", "namespace", namespace.name, "change-member");
            return ResponseEntity.ok(namespace);
        } catch (NotFoundException exc) {
            var error = json.error("Namespace not found: " + namespaceName, NamespaceJson.class);
            return new ResponseEntity<>(error, HttpStatus.NOT_FOUND);
        }
    }

    @PostMapping(
        path = "/admin/create-namespace",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<ResultJson> createNamespace(@RequestBody NamespaceJson namespace) {
        try {
            var resultJson = admins.createNamespace(namespace);
            var serverUrl = UrlUtil.getBaseUrl();
            var url = UrlUtil.createApiUrl(serverUrl, "admin", "namespace", namespace.name);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .location(URI.create(url))
                    .body(resultJson);
        } catch (ErrorResultException exc) {
            return json.toResponseEntity(exc);
        }
    }

    @GetMapping(
        path = "/admin/namespace/{namespaceName}/members",
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<NamespaceMembershipListJson> getNamespaceMembers(@PathVariable String namespaceName) {
        var namespace = repositories.findNamespace(namespaceName);
        var memberships = repositories.findMemberships(namespace);
        var membershipList = new NamespaceMembershipListJson();
        membershipList.namespaceMemberships = memberships.map(membership -> json.toNamespaceMembershipJson(membership)).toList();
        keycloak.enrichUserJsons(membershipList);
        return ResponseEntity.ok(membershipList);
    }

    @PostMapping(
        path = "/admin/namespace/{namespaceName}/change-member",
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<ResultJson> editNamespaceMember(
            Principal principal,
            @PathVariable String namespaceName,
            @RequestParam String userName,
            @RequestParam String role
    ) {
        try {
            var adminId = UserUtil.getUserId(principal);
            var result = admins.editNamespaceMember(namespaceName, userName, role, adminId);
            return ResponseEntity.ok(result);
        } catch (ErrorResultException exc) {
            return json.toResponseEntity(exc);
        }
    }

    @GetMapping(
        path = "/admin/publisher/{userName}",
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<UserPublishInfoJson> getUserPublishInfo(@PathVariable String userName) {
        try {
            var userPublishInfo = admins.getUserPublishInfo(userName);
            return ResponseEntity.ok(userPublishInfo);
        } catch (ErrorResultException exc) {
            return json.toResponseEntity(exc, UserPublishInfoJson.class);
        }
    }

    @PostMapping(
        path = "/admin/publisher/{userName}/revoke",
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<ResultJson> revokePublisherContributions(
            Principal principal,
            @PathVariable String userName
    ) {
        try {
            var adminId = UserUtil.getUserId(principal);
            return ResponseEntity.ok(admins.revokePublisherContributions(userName, adminId));
        } catch (ErrorResultException exc) {
            return json.toResponseEntity(exc);
        }
    }
}