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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.eclipse.openvsx.entities.UserData;
import org.eclipse.openvsx.migration.HandlerJobRequest;
import org.eclipse.openvsx.repositories.RepositoryService;
import org.eclipse.openvsx.search.SearchUtilService;
import org.jobrunr.scheduling.JobRequestScheduler;
import org.jobrunr.scheduling.cron.Cron;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.eclipse.openvsx.cache.CacheService.CACHE_MALICIOUS_EXTENSIONS;

@Component
public class ExtensionControlService {

    protected final Logger logger = LoggerFactory.getLogger(ExtensionControlService.class);

    private final JobRequestScheduler scheduler;
    private final RepositoryService repositories;
    private final EntityManager entityManager;
    private final SearchUtilService search;
    private final RestTemplate restTemplate;

    public ExtensionControlService(
            JobRequestScheduler scheduler,
            RepositoryService repositories,
            EntityManager entityManager,
            SearchUtilService search,
            RestTemplate restTemplate
    ) {
        this.scheduler = scheduler;
        this.repositories = repositories;
        this.entityManager = entityManager;
        this.search = search;
        this.restTemplate = restTemplate;
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

        var wasDeprecated = extension.isDeprecated();
        extension.setDeprecated(deprecated);
        extension.setDownloadable(downloadable);
        if(replacementId != null) {
            var replacement = repositories.findExtension(replacementId.extension(), replacementId.namespace());
            extension.setReplacement(replacement);
        }
        if(deprecated != wasDeprecated) {
            search.updateSearchEntry(extension);
        }
    }

    public JsonNode getExtensionControlJson() {
        var url = "https://github.com/open-vsx/publish-extensions/raw/master/extension-control/extensions.json";
        return restTemplate.getForObject(url, JsonNode.class);
    }

    @Cacheable(CACHE_MALICIOUS_EXTENSIONS)
    public List<String> getMaliciousExtensionIds() {
        var json = getExtensionControlJson();
        var malicious = json.get("malicious");
        if(!malicious.isArray()) {
            logger.error("field 'malicious' is not an array");
            return Collections.emptyList();
        }

        var list = new ArrayList<String>();
        malicious.forEach(node -> list.add(node.asText()));
        return list;
    }
}
