/********************************************************************************
 * Copyright (c) 2019 TypeFox and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/
package org.eclipse.openvsx;

import java.security.Principal;
import java.util.List;

import javax.persistence.EntityManager;
import javax.transaction.Transactional;

import org.eclipse.openvsx.eclipse.EclipseService;
import org.eclipse.openvsx.entities.NamespaceMembership;
import org.eclipse.openvsx.entities.PersonalAccessToken;
import org.eclipse.openvsx.json.*;
import org.eclipse.openvsx.keycloak.KeycloakService;
import org.eclipse.openvsx.repositories.RepositoryService;
import org.eclipse.openvsx.util.ErrorResultException;
import org.eclipse.openvsx.util.TimeUtil;
import org.eclipse.openvsx.util.UrlUtil;
import org.eclipse.openvsx.util.UserUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class UserAPI {

    private final static int TOKEN_DESCRIPTION_SIZE = 255;

    @Autowired
    EntityManager entityManager;

    @Autowired
    RepositoryService repositories;

    @Autowired
    UserService users;

    @Autowired
    EclipseService eclipse;

    @Autowired
    KeycloakService keycloak;

    @Autowired
    JsonService json;

    @GetMapping(
            path = "/user",
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public UserJson getUserData(Principal principal) {
        var userJson = keycloak.getUserJson(principal);
        eclipse.enrichUserJson(userJson, UserUtil.getUserId(principal));
        return userJson;
    }

    @GetMapping(
        path = "/user/tokens",
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    public List<AccessTokenJson> getAccessTokens(Principal principal) {
        var serverUrl = UrlUtil.getBaseUrl();
        return repositories.findAccessTokens(UserUtil.getUserId(principal))
                .filter(token -> token.isActive())
                .map(token -> {
                    var accessTokenJson = json.toAccessTokenJson(token);
                    accessTokenJson.deleteTokenUrl = UrlUtil.createApiUrl(serverUrl, "user", "token", "delete", Long.toString(token.getId()));
                    return accessTokenJson;
                })
                .toList();
    }

    @PostMapping(
        path = "/user/token/create",
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    @Transactional
    public ResponseEntity<AccessTokenJson> createAccessToken(
            Principal principal,
            @RequestParam(required = false) String description
    ) {
        if (description != null && description.length() > TOKEN_DESCRIPTION_SIZE) {
            var error = json.error("The description must not be longer than " + TOKEN_DESCRIPTION_SIZE + " characters.", AccessTokenJson.class);
            return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
        }

        var token = new PersonalAccessToken();
        token.setUserId(UserUtil.getUserId(principal));
        token.setValue(users.generateTokenValue());
        token.setActive(true);
        token.setCreatedTimestamp(TimeUtil.getCurrentUTC());
        token.setDescription(description);
        entityManager.persist(token);

        var accessTokenJson = json.toAccessTokenJson(token);
        // Include the token value after creation so the user can copy it
        accessTokenJson.value = token.getValue();
        var serverUrl = UrlUtil.getBaseUrl();
        accessTokenJson.deleteTokenUrl = UrlUtil.createApiUrl(serverUrl, "user", "token", "delete", Long.toString(token.getId()));
        return new ResponseEntity<>(accessTokenJson, HttpStatus.CREATED);
    }

    @PostMapping(
        path = "/user/token/delete/{id}",
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    @Transactional
    public ResponseEntity<ResultJson> deleteAccessToken(
            Principal principal,
            @PathVariable long id
    ) {
        var token = repositories.findAccessToken(id);
        if (token == null || !token.isActive() || !token.getUserId().equals(UserUtil.getUserId(principal))) {
            var error = json.error("Token does not exist.");
            return new ResponseEntity<>(error, HttpStatus.NOT_FOUND);
        }
        token.setActive(false);
        var success = json.success("Deleted access token for user " + UserUtil.getUserName(principal) + ".");
        return ResponseEntity.ok(success);
    }

    @GetMapping(
        path = "/user/namespaces",
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    public List<NamespaceJson> getOwnNamespaces(Principal principal) {
        var memberships = repositories.findMemberships(UserUtil.getUserId(principal), NamespaceMembership.ROLE_OWNER);
        return memberships.map(membership -> {
            var namespace = membership.getNamespace();
            var extensions = repositories.findActiveExtensions(namespace);
            return json.toNamespaceJson(namespace, extensions);
        }).toList();
    }

    @GetMapping(
        path = "/user/namespace/{name}/members",
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<NamespaceMembershipListJson> getNamespaceMembers(
            Principal principal,
            @PathVariable String name
    ) {
        var namespace = repositories.findNamespace(name);
        var userMembership = repositories.findMembership(UserUtil.getUserId(principal), namespace);
        if (userMembership != null && userMembership.getRole().equals(NamespaceMembership.ROLE_OWNER)) {
            var memberships = repositories.findMemberships(namespace);
            var membershipList = new NamespaceMembershipListJson();
            membershipList.namespaceMemberships = memberships.map(membership -> json.toNamespaceMembershipJson(membership)).toList();
            keycloak.enrichUserJsons(membershipList);
            return new ResponseEntity<>(membershipList, HttpStatus.OK);
        } else {
            return new ResponseEntity<>(json.error("You don't have the permission to see this.", NamespaceMembershipListJson.class), HttpStatus.FORBIDDEN);
        }
    }

    @PostMapping(
        path = "/user/namespace/{namespace}/role",
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<ResultJson> setNamespaceMember(
            Principal principal,
            @PathVariable String namespace,
            @RequestParam String userName,
            @RequestParam String role
    ) {
        try {
            return ResponseEntity.ok(users.setNamespaceMember(principal, namespace, userName, role));
        } catch (ErrorResultException exc) {
            return json.toResponseEntity(exc);
        }
    }

    @GetMapping(
        path = "/user/search/{name}",
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    public List<UserJson> getUsersByUserNameContains(@PathVariable String name) {
        return keycloak.findUsersByUserNameContains(name, 5);
    }

    @PostMapping(
        path = "/user/publisher-agreement",
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<UserJson> signPublisherAgreement(Principal principal) {
        try {
            return ResponseEntity.ok(eclipse.signPublisherAgreement(principal));
        } catch (ErrorResultException exc) {
            return json.toResponseEntity(exc, UserJson.class);
        }
    }

    @GetMapping(
        path = "/user/eclipse/active-access-token",
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResultJson hasActiveEclipseAccessToken(Principal principal) {
        return keycloak.getEclipseToken(principal) != null
                ? json.success("Found active Eclipse access token")
                : json.error("No active Eclipse access token.\nPlease login with Eclipse.");
    }
}