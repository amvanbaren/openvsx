/** ******************************************************************************
 * Copyright (c) 2025 Precies. Software OU and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 * ****************************************************************************** */
package org.eclipse.openvsx.adapter;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

import static org.eclipse.openvsx.adapter.ExtensionQueryParam.*;

public record ExtensionQueryExtensionData(
        String publicId,
        String extensionId,
        String extensionName,
        String displayName,
        String shortDescription,
        ExtensionQueryResult.Publisher publisher,
        List<ExtensionVersion> versions,
        List<Long> latestVersions,
        List<ExtensionQueryResult.Statistic> statistics,
        List<String> tags,
        String releaseDate,
        String publishedDate,
        String lastUpdated,
        List<String> categories,
        String flags
) implements Serializable {

    public record ExtensionVersion(long id, ExtensionQueryResult.ExtensionVersion data) implements Serializable {
        public ExtensionQueryResult.ExtensionVersion toJson(int flags) {
            var files = test(flags, FLAG_INCLUDE_FILES) ? data.files() : null;
            var assetUri = test(flags, FLAG_INCLUDE_ASSET_URI) ? data.assetUri() : null;
            var properties = test(flags, FLAG_INCLUDE_VERSION_PROPERTIES) ? data.properties() : null;

            return new ExtensionQueryResult.ExtensionVersion(
                    data.version(),
                    data.lastUpdated(),
                    assetUri,
                    assetUri,
                    files,
                    properties,
                    data.targetPlatform()
            );
        }
    }

    public ExtensionQueryResult.Extension toJson(int flags) {
        var extensionVersions = Collections.<ExtensionQueryResult.ExtensionVersion>emptyList();
        if (test(flags, FLAG_INCLUDE_LATEST_VERSION_ONLY)) {
            extensionVersions = versions.stream().filter(v -> latestVersions.contains(v.id())).map(v -> v.toJson(flags)).toList();
        } else if (test(flags, FLAG_INCLUDE_VERSIONS)) {
            extensionVersions = versions.stream().map(v -> v.toJson(flags)).toList();
        }

        var stats = test(flags, FLAG_INCLUDE_STATISTICS) ? statistics : Collections.<ExtensionQueryResult.Statistic>emptyList();
        return new ExtensionQueryResult.Extension(
                publicId,
                extensionName,
                displayName,
                shortDescription,
                publisher,
                extensionVersions,
                stats,
                tags,
                releaseDate,
                publishedDate,
                lastUpdated,
                categories,
                flags()
        );
    }

    private static boolean test(int flags, int flag) {
        return (flags & flag) != 0;
    }
}
