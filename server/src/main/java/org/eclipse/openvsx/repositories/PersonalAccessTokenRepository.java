/********************************************************************************
 * Copyright (c) 2020 TypeFox and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/
package org.eclipse.openvsx.repositories;

import org.springframework.data.repository.Repository;
import org.springframework.data.util.Streamable;
import org.eclipse.openvsx.entities.PersonalAccessToken;

public interface PersonalAccessTokenRepository extends Repository<PersonalAccessToken, Long> {

    PersonalAccessToken findById(long id);

    PersonalAccessToken findByValue(String value);

    Streamable<PersonalAccessToken> findByUserId(String userId);

    Streamable<PersonalAccessToken> findAll();
}