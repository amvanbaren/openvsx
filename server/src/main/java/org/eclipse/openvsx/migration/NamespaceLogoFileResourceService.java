package org.eclipse.openvsx.migration;

import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.eclipse.openvsx.entities.Namespace;
import org.springframework.stereotype.Component;

@Component
public class NamespaceLogoFileResourceService {

    private final EntityManager entityManager;

    public NamespaceLogoFileResourceService(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public Namespace getNamespace(long entityId) {
        return entityManager.find(Namespace.class, entityId);
    }

    @Transactional
    public void updateNamespace(Namespace namespace) {
        entityManager.merge(namespace);
    }
}
