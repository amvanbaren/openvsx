/********************************************************************************
 * Copyright (c) 2023 Precies. Software Ltd and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/

package org.eclipse.openvsx.repositories;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.persistence.EntityManager;
import javax.transaction.Transactional;

@Component
public class EntityService {

    @Autowired
    EntityManager entityManager;

    @Transactional
    public <T> void insert(T entity) {
        entityManager.persist(entity);
    }

    @Transactional
    public <T> T update(T entity) {
        return entityManager.merge(entity);
    }

    @Transactional
    public void updateAll(Object... entities) {
        for(var entity : entities) {
            entityManager.merge(entity);
        }
    }

    @Transactional
    public <T> void delete(T entity) {
        entity = entityManager.merge(entity);
        entityManager.remove(entity);
    }
}
