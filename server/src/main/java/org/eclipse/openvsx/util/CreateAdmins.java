package org.eclipse.openvsx.util;

import org.eclipse.openvsx.repositories.RepositoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import javax.transaction.Transactional;
import java.util.Objects;
import java.util.Set;

@Component
public class CreateAdmins {

    private static final Logger LOGGER = LoggerFactory.getLogger(CreateAdmins.class);

    @Autowired
    RepositoryService repositories;

    @Autowired
    Set<String> loginNames;

    public CreateAdmins() {
        LOGGER.info("CreateAdmins");
    }

    @EventListener
    @Transactional
    public void createAdmins(ApplicationStartedEvent event) {
        loginNames.stream()
                .map(loginName -> repositories.findUserByLoginName("github", loginName))
                .filter(Objects::nonNull)
                .forEach(user -> {
                    user.setRole("admin");
                    LOGGER.info("{} is admin now", user.getLoginName());
                });
    }
}
