/********************************************************************************
 * Copyright (c) 2020 TypeFox and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/
package org.eclipse.openvsx.eclipse;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.stream.Collectors;

import javax.persistence.EntityManager;

import org.eclipse.openvsx.ExtensionService;
import org.eclipse.openvsx.entities.Extension;
import org.eclipse.openvsx.entities.PersonalAccessToken;
import org.eclipse.openvsx.keycloak.KeycloakService;
import org.eclipse.openvsx.repositories.RepositoryService;
import org.eclipse.openvsx.util.TargetPlatform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class PublisherComplianceChecker {

    protected final Logger logger = LoggerFactory.getLogger(PublisherComplianceChecker.class);

    @Autowired
    TransactionTemplate transactions;

    @Autowired
    EntityManager entityManager;

    @Autowired
    RepositoryService repositories;

    @Autowired
    ExtensionService extensions;

    @Autowired
    EclipseService eclipse;

    @Autowired
    KeycloakService keycloak;

    @Value("${ovsx.eclipse.check-compliance-on-start:false}")
    boolean checkCompliance;

    @EventListener
    public void checkPublishers(ApplicationStartedEvent event) {
        if (!checkCompliance || !eclipse.isActive())
            return;

        var accessTokensByUserId = repositories.findAllAccessTokens().stream()
                .collect(Collectors.groupingBy(PersonalAccessToken::getUserId));

        for(var entry : accessTokensByUserId.entrySet()) {
            var userId = entry.getKey();
            var accessTokens = entry.getValue();
            if(!entry.getValue().isEmpty() && !isCompliant(userId)) {
                // Found a non-compliant publisher: deactivate all extension versions
                transactions.<Void>execute(status -> {
                    deactivateExtensions(accessTokens, userId);
                    return null;
                });
            }
        }
    }

    private boolean isCompliant(String userId) {
        // Users without authentication provider have been created directly in the DB,
        // so we skip the agreement check in this case.
        if(keycloak.getUserRoles(userId).contains("skip-publisher-agreement")) {
            return true;
        }

        var userName = keycloak.getEclipseUserName(userId);
        if (userName == null) {
            // The user has never logged in with Eclipse
            return false;
        }

        var agreement = repositories.findPublisherAgreement(userId);
        if (agreement == null || !agreement.isActive()) {
            // We don't have any active PA in our DB, let's check their Eclipse profile
            var profile = eclipse.getPublicProfile(userName);
            if (profile.publisherAgreements == null || profile.publisherAgreements.openVsx == null
                    || profile.publisherAgreements.openVsx.version == null) {
                return false;
            }
        }
        return true;
    }

    private void deactivateExtensions(List<PersonalAccessToken> accessTokens, String userId) {
        var affectedExtensions = new LinkedHashSet<Extension>();
        for (var accessToken : accessTokens) {
            var versions = repositories.findVersionsByAccessToken(accessToken, true);
            for (var version : versions) {
                version.setActive(false);
                entityManager.merge(version);
                var extension = version.getExtension();
                affectedExtensions.add(extension);
                var userName = keycloak.getEclipseUserName(userId);
                logger.info("Deactivated: " + userName + " - "
                        + extension.getNamespace().getName() + "." + extension.getName() + " " + version.getVersion()
                        + (TargetPlatform.isUniversal(version) ? "" : " (" + version.getTargetPlatform() + ")"));
            }
        }
        
        // Update affected extensions
        for (var extension : affectedExtensions) {
            extensions.updateExtension(extension);
            entityManager.merge(extension);
        }
    }
    
}