/********************************************************************************
 * Copyright (c) 2020 TypeFox and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/
package org.eclipse.openvsx.eclipse;

import java.time.LocalDateTime;
import java.util.function.Function;

import com.fasterxml.jackson.annotation.JsonProperty;

import org.eclipse.openvsx.entities.PublisherAgreement;

/**
 * https://eclipsefdn.github.io/openvsx-publisher-agreement-specs/#/paths/~1publisher_agreement/post
 */
public class PublisherAgreementResponse {

    public PublisherAgreement createEntityData(Function<String, LocalDateTime> parseDate) {
        var agreement = new PublisherAgreement();
        agreement.setActive(true);
        agreement.setDocumentId(documentID);
        agreement.setVersion(version);
        agreement.setPersonId(personID);
        agreement.setTimestamp(parseDate.apply(effectiveDate));
        return agreement;
    }

    /** Unique identifier for an addressable object in the API. */
    @JsonProperty("PersonID")
    String personID;

    /** Unique identifier for an addressable object in the API. */
    @JsonProperty("DocumentID")
    String documentID;

    /** The version number for the current document. */
    @JsonProperty("Version")
    String version;

    /** Date string in the RFC 3339 format. */
    @JsonProperty("EffectiveDate")
    String effectiveDate;

    /** Date string in the RFC 3339 format. */
    @JsonProperty("ReceivedDate")
    String receivedDate;

    /** Date string in the RFC 3339 format. */
    @JsonProperty("ExpirationDate")
    String expirationDate;

    /** The signed document as a blob entity. */
    @JsonProperty("ScannedDocumentBLOB")
    String scannedDocumentBLOB;

    @JsonProperty("ScannedDocumentBytes")
    String scannedDocumentBytes;

    /** The MIME type for the posted document blob. */
    @JsonProperty("ScannedDocumentMime")
    String scannedDocumentMime;

    /** The name of the document being posted. */
    @JsonProperty("ScannedDocumentFileName")
    String scannedDocumentFileName;

    /** Comment about the document being posted. */
    @JsonProperty("Comments")
    String comments;

}