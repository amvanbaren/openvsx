/********************************************************************************
 * Copyright (c) 2019 TypeFox and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/
package org.eclipse.openvsx.json;

import java.util.List;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
    name = "SearchResult",
    description = "List of extensions matching a search query"
)
@JsonInclude(Include.NON_NULL)
public class SearchResultJson extends ResultJson {

    public static SearchResultJson error(String message) {
        var result = new SearchResultJson();
        result.setError(message);
        return result;
    }

    @Schema(description = "Number of skipped entries according to the search query")
    @NotNull
    @Min(0)
    private int offset;

    @Schema(description = "Total number of entries that match the search query")
    @NotNull
    @Min(0)
    private int totalSize;

    @Schema(description = "List of matching entries, limited to the size specified in the search query")
    @NotNull
    private List<SearchEntryJson> extensions;

    public int getOffset() {
        return offset;
    }

    public void setOffset(int offset) {
        this.offset = offset;
    }

    public int getTotalSize() {
        return totalSize;
    }

    public void setTotalSize(int totalSize) {
        this.totalSize = totalSize;
    }

    public List<SearchEntryJson> getExtensions() {
        return extensions;
    }

    public void setExtensions(List<SearchEntryJson> extensions) {
        this.extensions = extensions;
    }
}