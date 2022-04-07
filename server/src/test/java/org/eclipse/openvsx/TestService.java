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

import java.io.IOException;
import java.time.LocalDateTime;

import javax.persistence.EntityManager;
import javax.transaction.Transactional;

import org.eclipse.openvsx.entities.PersonalAccessToken;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.MountableFile;

@Component
@Profile("test")
public class TestService {
    
    @Autowired
    EntityManager entityManager;

    public GenericContainer initKeycloak() throws IOException, InterruptedException {
        var container = new GenericContainer("jboss/keycloak:17.0.1.Final")
                .withExposedPorts(9090)
                .withEnv("KEYCLOAK_USER", "admin")
                .withEnv("KEYCLOAK_PASSWORD", "admin")
                .withEnv("KEYCLOAK_IMPORT", "/tmp/realm.json")
                .withClasspathResourceMapping("realm-export.json", "/tmp/realm.json", BindMode.READ_ONLY)
                .withCopyFileToContainer(MountableFile.forClasspathResource("create-keycloak-user.sh", 700),
                        "/opt/jboss/create-keycloak-user.sh")
                .waitingFor(Wait.forHttp("/auth"));

        Container.ExecResult commandResult = container.execInContainer("sh", "/opt/jboss/create-keycloak-user.sh");
        assert commandResult.getExitCode() == 0;
        return container;
    }

    @Transactional
    public void createToken() {
        var token = new PersonalAccessToken();
        token.setCreatedTimestamp(LocalDateTime.now());
        token.setActive(true);
        token.setUserId("test_user");
        token.setValue("test_token");
        entityManager.persist(token);
    }
    
}