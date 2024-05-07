/** ******************************************************************************
 * Copyright (c) 2024 Precies. Software OU and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 * ****************************************************************************** */
package org.eclipse.openvsx.repositories;

import org.eclipse.openvsx.entities.Extension;
import org.eclipse.openvsx.entities.ExtensionReview;
import org.eclipse.openvsx.entities.Namespace;
import org.eclipse.openvsx.entities.UserData;
import org.jooq.DSLContext;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

import static org.eclipse.openvsx.jooq.tables.Extension.EXTENSION;
import static org.eclipse.openvsx.jooq.tables.ExtensionReview.EXTENSION_REVIEW;
import static org.eclipse.openvsx.jooq.tables.Namespace.NAMESPACE;
import static org.eclipse.openvsx.jooq.tables.UserData.USER_DATA;

@Component
public class ExtensionReviewJooqRepository {

    private final DSLContext dsl;

    public ExtensionReviewJooqRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public List<ExtensionReview> findActiveReviews(String extensionName, String namespaceName) {
        return dsl.select(
                    NAMESPACE.ID,
                    NAMESPACE.NAME,
                    EXTENSION.ID,
                    EXTENSION.NAME,
                    EXTENSION_REVIEW.ID,
                    EXTENSION_REVIEW.TIMESTAMP,
                    EXTENSION_REVIEW.TITLE,
                    EXTENSION_REVIEW.COMMENT,
                    EXTENSION_REVIEW.RATING,
                    USER_DATA.ID,
                    USER_DATA.LOGIN_NAME,
                    USER_DATA.FULL_NAME,
                    USER_DATA.AVATAR_URL,
                    USER_DATA.PROVIDER_URL,
                    USER_DATA.PROVIDER
                )
                .from(NAMESPACE)
                .join(EXTENSION).on(EXTENSION.NAMESPACE_ID.eq(NAMESPACE.ID))
                .leftJoin(EXTENSION_REVIEW).on(EXTENSION_REVIEW.EXTENSION_ID.eq(EXTENSION.ID))
                .join(USER_DATA).on(USER_DATA.ID.eq(EXTENSION_REVIEW.USER_ID))
                .where(NAMESPACE.NAME.equalIgnoreCase(namespaceName))
                .and(EXTENSION.NAME.equalIgnoreCase(extensionName))
                .and(EXTENSION.ACTIVE.eq(true))
                .and(EXTENSION_REVIEW.ACTIVE.eq(true))
                .fetch(record -> {
                    var namespace = new Namespace();
                    namespace.setId(record.get(NAMESPACE.ID));
                    namespace.setName(record.get(NAMESPACE.NAME));

                    var extension = new Extension();
                    extension.setId(record.get(EXTENSION.ID));
                    extension.setName(record.get(EXTENSION.NAME));
                    extension.setNamespace(namespace);

                    long reviewId = Optional.ofNullable(record.get(EXTENSION_REVIEW.ID)).orElse(-1L);
                    var review = new ExtensionReview();
                    review.setId(reviewId);
                    review.setExtension(extension);
                    if(reviewId != -1) {
                        var user = new UserData();
                        user.setId(record.get(USER_DATA.ID));
                        user.setLoginName(record.get(USER_DATA.LOGIN_NAME));
                        user.setFullName(record.get(USER_DATA.FULL_NAME));
                        user.setAvatarUrl(record.get(USER_DATA.AVATAR_URL));
                        user.setProviderUrl(record.get(USER_DATA.PROVIDER_URL));
                        user.setProvider(record.get(USER_DATA.PROVIDER));

                        review.setTimestamp(record.get(EXTENSION_REVIEW.TIMESTAMP));
                        review.setTitle(record.get(EXTENSION_REVIEW.TITLE));
                        review.setComment(record.get(EXTENSION_REVIEW.COMMENT));
                        review.setRating(record.get(EXTENSION_REVIEW.RATING));
                        review.setUser(user);
                    }

                    return review;
                });
    }

    public boolean hasActiveReview(Extension extension, UserData user) {
        return dsl.fetchExists(
                dsl.selectOne()
                        .from(EXTENSION_REVIEW)
                        .where(EXTENSION_REVIEW.EXTENSION_ID.eq(extension.getId()))
                        .and(EXTENSION_REVIEW.USER_ID.eq(user.getId()))
                        .and(EXTENSION_REVIEW.ACTIVE.eq(true))
        );
    }
}
