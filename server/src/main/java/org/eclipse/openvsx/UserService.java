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
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.persistence.EntityManager;
import javax.transaction.Transactional;

import org.eclipse.openvsx.entities.Namespace;
import org.eclipse.openvsx.entities.NamespaceMembership;
import org.eclipse.openvsx.entities.PersonalAccessToken;
import org.eclipse.openvsx.json.JsonService;
import org.eclipse.openvsx.json.ResultJson;
import org.eclipse.openvsx.json.UserJson;
import org.eclipse.openvsx.keycloak.KeycloakService;
import org.eclipse.openvsx.repositories.RepositoryService;
import org.eclipse.openvsx.util.ErrorResultException;
import org.eclipse.openvsx.util.TimeUtil;
import org.eclipse.openvsx.util.UserUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class UserService {

    @Autowired
    EntityManager entityManager;

    @Autowired
    RepositoryService repositories;

    @Autowired
    KeycloakService keycloak;

    @Autowired
    JsonService json;

    @Transactional
    public PersonalAccessToken useAccessToken(String tokenValue) {
        var token = repositories.findAccessToken(tokenValue);
        if (token == null || !token.isActive()) {
            return null;
        }
        token.setAccessedTimestamp(TimeUtil.getCurrentUTC());
        return token;
    }

    public String generateTokenValue() {
        String value;
        do {
            value = UUID.randomUUID().toString();
        } while (repositories.findAccessToken(value) != null);
        return value;
    }

    public boolean hasPublishPermission(String userId, Namespace namespace) {
        if(keycloak.getUserRoles(userId).contains("privileged")) {
            // Privileged users can publish to every namespace.
            return true;
        }

        var membership = repositories.findMembership(userId, namespace);
        if (membership == null) {
            // The requesting user is not a member of the namespace.
            return false;
        }
        var role = membership.getRole();
        return NamespaceMembership.ROLE_CONTRIBUTOR.equalsIgnoreCase(role)
                || NamespaceMembership.ROLE_OWNER.equalsIgnoreCase(role);
    }

    @Transactional(rollbackOn = ErrorResultException.class)
    public ResultJson setNamespaceMember(Principal principal, String namespaceName, String targetUserName, String role) {
        var namespace = repositories.findNamespace(namespaceName);
        var userMembership = repositories.findMembership(UserUtil.getUserId(principal), namespace);
        if (userMembership == null || !userMembership.getRole().equals(NamespaceMembership.ROLE_OWNER)) {
            throw new ErrorResultException("You must be an owner of this namespace.");
        }

        var targetUserId = keycloak.findUserIdByUserName(targetUserName);
        if (targetUserId == null) {
            throw new ErrorResultException("User not found: " + targetUserName);
        }

        return role.equals("remove")
                ? removeNamespaceMember(namespace, targetUserId, targetUserName)
                : addNamespaceMember(namespace, targetUserId, targetUserName, role);
    }

    @Transactional(rollbackOn = ErrorResultException.class)
    public ResultJson removeNamespaceMember(Namespace namespace, String userId, String userName) throws ErrorResultException {
        NamespaceMembership membership = repositories.findMembership(userId, namespace);
        if (membership == null) {
            throw new ErrorResultException("User " + userName + " is not a member of " + namespace.getName() + ".");
        }
        entityManager.remove(membership);
        return json.success("Removed " + userName + " from namespace " + namespace.getName() + ".");
    }

    @Transactional(rollbackOn = ErrorResultException.class)
    public ResultJson addNamespaceMember(Namespace namespace, String userId, String userName, String role) {
        if (!(role.equals(NamespaceMembership.ROLE_OWNER)
                || role.equals(NamespaceMembership.ROLE_CONTRIBUTOR))) {
            throw new ErrorResultException("Invalid role: " + role);
        }

        var membership = repositories.findMembership(userId, namespace);
        if (membership != null) {
            if (role.equals(membership.getRole())) {
                throw new ErrorResultException("User " + userName + " already has the role " + role + ".");
            }
            membership.setRole(role);
            return json.success("Changed role of " + userName + " in " + namespace.getName() + " to " + role + ".");
        }
        membership = new NamespaceMembership();
        membership.setNamespace(namespace);
        membership.setUserId(userId);
        membership.setRole(role);
        entityManager.persist(membership);
        return json.success("Added " + userName + " as " + role + " of " + namespace.getName() + ".");
    }
}