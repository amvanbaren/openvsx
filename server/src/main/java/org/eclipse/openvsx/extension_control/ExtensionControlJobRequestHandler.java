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

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.openvsx.admin.AdminService;
import org.eclipse.openvsx.migration.HandlerJobRequest;
import org.eclipse.openvsx.repositories.RepositoryService;
import org.jobrunr.jobs.lambdas.JobRequestHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ExtensionControlJobRequestHandler implements JobRequestHandler<HandlerJobRequest<?>>  {

    protected final Logger logger = LoggerFactory.getLogger(ExtensionControlJobRequestHandler.class);

    private final AdminService admin;
    private final ExtensionControlService service;
    private final RepositoryService repositories;

    public ExtensionControlJobRequestHandler(AdminService admin, ExtensionControlService service, RepositoryService repositories) {
        this.admin = admin;
        this.service = service;
        this.repositories = repositories;
    }

    @Override
    public void run(HandlerJobRequest<?> jobRequest) throws Exception {
        var json = service.getExtensionControlJson();
        processMaliciousExtensions(json);
        processDeprecatedExtensions(json);
    }

    private void processMaliciousExtensions(JsonNode json) {
        var node = json.get("malicious");
        if(!node.isArray()) {
            logger.error("field 'malicious' is not an array");
            return;
        }

        var adminUser = service.createExtensionControlUser();
        for(var item : node) {
            var extensionId = parseExtensionId(item.asText());
            if(extensionId != null && repositories.hasExtension(extensionId.namespace(), extensionId.extension())) {
                admin.deleteExtension(extensionId.namespace(), extensionId.extension(), adminUser);
            }
        }
    }

    private void processDeprecatedExtensions(JsonNode json) {
        var node = json.get("deprecated");
        if(!node.isObject()) {
            logger.error("field 'deprecated' is not an object");
            return;
        }

        node.fields().forEachRemaining(field -> {
            var extensionId = parseExtensionId(field.getKey());
            if(extensionId == null) {
                return;
            }

            var value = field.getValue();
            if(value.isBoolean()) {
                service.updateExtension(extensionId, value.asBoolean(), null, true);
            } else if(value.isObject()) {
                ExtensionId replacementId = null;
                var replacement = value.get("extension");
                if(replacement != null && replacement.isObject()) {
                    replacementId = parseExtensionId(replacement.get("id").asText());
                }

                var disallowInstall = value.has("disallowInstall") && value.get("disallowInstall").asBoolean(false);
                service.updateExtension(extensionId, true, replacementId, !disallowInstall);
            } else {
                logger.error("field '{}' is not an object or a boolean", extensionId);
            }
        });
    }

    private ExtensionId parseExtensionId(String text) {
        var pieces = text.split("\\.");
        if(pieces.length != 2 || StringUtils.isEmpty(pieces[0]) || StringUtils.isEmpty(pieces[1])) {
            logger.error("Invalid extension id: '{}'", text);
            return null;
        }

        return new ExtensionId(pieces[0], pieces[1]);
    }
}
