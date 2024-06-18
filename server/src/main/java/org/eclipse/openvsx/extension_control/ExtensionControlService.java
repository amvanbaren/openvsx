/** ******************************************************************************
 * Copyright (c) 2024 Precies. Software OU and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 * ****************************************************************************** */
package org.eclipse.openvsx.extension_control;

import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.eclipse.openvsx.entities.UserData;
import org.eclipse.openvsx.migration.HandlerJobRequest;
import org.eclipse.openvsx.repositories.RepositoryService;
import org.jobrunr.scheduling.JobRequestScheduler;
import org.jobrunr.scheduling.cron.Cron;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.ZoneId;

@Component
public class ExtensionControlService {

    private final JobRequestScheduler scheduler;
    private final RepositoryService repositories;
    private final EntityManager entityManager;

    public ExtensionControlService(JobRequestScheduler scheduler, RepositoryService repositories, EntityManager entityManager) {
        this.scheduler = scheduler;
        this.repositories = repositories;
        this.entityManager = entityManager;
    }

    @EventListener
    public void applicationStarted(ApplicationStartedEvent event) {
        var jobRequest = new HandlerJobRequest<>(ExtensionControlJobRequestHandler.class);
        scheduler.scheduleRecurrently("UpdateExtensionControl", Cron.daily(1, 8), ZoneId.of("UTC"), jobRequest);
    }

    @Transactional
    public UserData createExtensionControlUser() {
        var userName = "ExtensionControlUser";
        var user = repositories.findUserByLoginName(null, userName);
        if(user == null) {
            user = new UserData();
            user.setLoginName(userName);
            entityManager.persist(user);
        }
        return user;
    }

    @Transactional
    public void updateExtension(ExtensionId extensionId, boolean deprecated, ExtensionId replacementId, boolean downloadable) {
        var extension = repositories.findExtension(extensionId.extension(), extensionId.namespace());
        if(extension == null) {
            return;
        }

        extension.setDeprecated(deprecated);
        extension.setDownloadable(downloadable);
        if(replacementId != null) {
            var replacement = repositories.findExtension(replacementId.extension(), replacementId.namespace());
            extension.setReplacement(replacement);
        }
    }
}
