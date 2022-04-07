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

import java.net.URI;
import java.security.Principal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;

import javax.persistence.EntityManager;
import javax.transaction.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;

import org.eclipse.openvsx.ExtensionService;
import org.eclipse.openvsx.entities.PublisherAgreement;
import org.eclipse.openvsx.json.JsonService;
import org.eclipse.openvsx.json.UserJson;
import org.eclipse.openvsx.keycloak.KeycloakService;
import org.eclipse.openvsx.repositories.RepositoryService;
import org.eclipse.openvsx.util.ErrorResultException;
import org.eclipse.openvsx.util.TimeUtil;
import org.eclipse.openvsx.util.UrlUtil;
import org.eclipse.openvsx.util.UserUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Component
public class EclipseService {

    public static final DateTimeFormatter CUSTOM_DATE_TIME = new DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .append(DateTimeFormatter.ISO_LOCAL_DATE)
            .appendLiteral(' ')
            .append(DateTimeFormatter.ISO_LOCAL_TIME)
            .toFormatter();
    
    private static final TypeReference<List<String>> TYPE_LIST_STRING = new TypeReference<>() {};
    private static final TypeReference<List<EclipseProfile>> TYPE_LIST_PROFILE = new TypeReference<>() {};
    private static final TypeReference<List<PublisherAgreementResponse>> TYPE_LIST_AGREEMENT = new TypeReference<>() {};

    protected final Logger logger = LoggerFactory.getLogger(EclipseService.class);

    @Autowired
    TransactionTemplate transactions;

    @Autowired
    ExtensionService extensions;

    @Autowired
    EntityManager entityManager;

    @Autowired
    RepositoryService repositories;

    @Autowired
    RestTemplate restTemplate;

    @Autowired
    KeycloakService keycloak;

    @Autowired
    JsonService json;

    @Value("${ovsx.eclipse.base-url:}")
    String eclipseApiUrl;

    @Value("${ovsx.eclipse.publisher-agreement.version:}")
    String publisherAgreementVersion;

    @Value("${ovsx.eclipse.publisher-agreement.timezone:}")
    String publisherAgreementTimeZone;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public EclipseService() {
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    private final Function<String, LocalDateTime> parseDate = dateString -> {
        try {
            var local = LocalDateTime.parse(dateString, CUSTOM_DATE_TIME);
            if (Strings.isNullOrEmpty(publisherAgreementTimeZone)) {
                return local;
            }
            return TimeUtil.convertToUTC(local, publisherAgreementTimeZone);
        } catch (DateTimeParseException exc) {
            logger.error("Failed to parse timestamp.", exc);
            return null;
        }
    };

    public boolean isActive() {
        return !Strings.isNullOrEmpty(publisherAgreementVersion);
    }

    /**
     * Check whether the given user has an active publisher agreement.
     * @throws ErrorResultException if the user has no active agreement
     */
    public void checkPublisherAgreement(String userId) {
        if (!isActive()) {
            return;
        }

        var roles = keycloak.getUserRoles(userId);
        if(roles.contains("skip-publisher-agreement")) {
            return;
        }

        var userName = keycloak.getEclipseUserName(userId);
        if (userName == null) {
            throw new ErrorResultException("You must log in with an Eclipse Foundation account and sign a Publisher Agreement before publishing any extension.");
        }

        var profile = getPublicProfile(userName);
        if (profile.publisherAgreements == null || profile.publisherAgreements.openVsx == null
                || profile.publisherAgreements.openVsx.version == null) {
            throw new ErrorResultException("You must sign a Publisher Agreement with the Eclipse Foundation before publishing any extension.");
        }
        if (!publisherAgreementVersion.equals(profile.publisherAgreements.openVsx.version)) {
            throw new ErrorResultException("Your Publisher Agreement with the Eclipse Foundation is outdated (version "
                    + profile.publisherAgreements.openVsx.version + "). The current version is "
                    + publisherAgreementVersion + ".");
        }
    }

    /**
     * Update the given user data with a profile obtained from Eclipse API.
     */
    @Transactional
    public PublisherAgreement updatePublisherAgreement(PublisherAgreement agreement, EclipseProfile profile) {
        if (agreement != null && Strings.isNullOrEmpty(agreement.getPersonId())) {
            agreement.setPersonId(profile.name);
        }
        if (profile.publisherAgreements != null) {
            if (profile.publisherAgreements.openVsx == null) {
                if (agreement != null) {
                    agreement.setActive(false);
                }
            } else if (!Strings.isNullOrEmpty(profile.publisherAgreements.openVsx.version)) {
                if (agreement == null) {
                    agreement = new PublisherAgreement();
                }

                agreement.setActive(true);
                agreement.setVersion(profile.publisherAgreements.openVsx.version);
            }
        }

        return agreement;
    }

    /**
     * Enrich the given JSON user data with Eclipse-specific information.
     */
    public void enrichUserJson(UserJson userJson, String userId) {
        if (!isActive()) {
            return;
        }

        var agreement = repositories.findPublisherAgreement(userId);
        enrichUserJson(userJson, agreement);
    }

    /**
     * Enrich the given JSON user data with Eclipse-specific information.
     */
    private void enrichUserJson(UserJson userJson, PublisherAgreement agreement) {
        userJson.publisherAgreement = new UserJson.PublisherAgreement();
        if (agreement == null) {
            userJson.publisherAgreement.status = "none";
            return;
        }

        // Update the internal data from the Eclipse profile
        if (agreement.getPersonId() != null) {
            try {
                var profile = getPublicProfile(agreement.getPersonId());
                transactions.execute(status -> updatePublisherAgreement(agreement, profile));
            } catch (ErrorResultException | TransactionException exc) {
                // Continue with the information that is currently in the DB
            }
        }

        // Add information on the publisher agreement status
        if (!agreement.isActive() || agreement.getVersion() == null)
            userJson.publisherAgreement.status = "none";
        else if (publisherAgreementVersion.equals(agreement.getVersion()))
            userJson.publisherAgreement.status = "signed";
        else
            userJson.publisherAgreement.status = "outdated";
        if (agreement.getTimestamp() != null)
            userJson.publisherAgreement.timestamp = TimeUtil.toUTCString(agreement.getTimestamp());
    }

    /**
     * Get the publicly available user profile.
     */
    public EclipseProfile getPublicProfile(String personId) {
        checkApiUrl();
        var headers = new HttpHeaders();
        headers.setAccept(Arrays.asList(MediaType.APPLICATION_JSON));
        var requestUrl = UrlUtil.createApiUrl(eclipseApiUrl, "account", "profile", personId);
        var request = new RequestEntity<>(headers, HttpMethod.GET, URI.create(requestUrl));

        try {
            var response = restTemplate.exchange(request, String.class);
            return parseEclipseProfile(response);
        } catch (RestClientException exc) {
            if (exc instanceof HttpStatusCodeException) {
                var status = ((HttpStatusCodeException) exc).getStatusCode();
                if (status == HttpStatus.NOT_FOUND)
                    throw new ErrorResultException("No Eclipse profile data available for user: " + personId);
            }
            logger.error("Get request failed with URL: " + requestUrl, exc);
            throw new ErrorResultException("Request for retrieving user profile failed: " + exc.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Get the user profile available through an access token.
     */
    public EclipseProfile getUserProfile(String accessToken) {
        checkApiUrl();
        var headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setAccept(Arrays.asList(MediaType.APPLICATION_JSON));
        var requestUrl = UrlUtil.createApiUrl(eclipseApiUrl, "openvsx", "profile");
        var request = new RequestEntity<>(headers, HttpMethod.GET, URI.create(requestUrl));

        try {
            var response = restTemplate.exchange(request, String.class);
            return parseEclipseProfile(response);
        } catch (RestClientException exc) {
            logger.error("Get request failed with URL: " + requestUrl, exc);
            throw new ErrorResultException("Request for retrieving user profile failed: " + exc.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private EclipseProfile parseEclipseProfile(ResponseEntity<String> response) {
        var responseJson = response.getBody();
        try {
            if (responseJson.startsWith("[\"")) {
                var error = objectMapper.readValue(responseJson, TYPE_LIST_STRING);
                logger.error("Profile request failed:\n" + responseJson);
                throw new ErrorResultException("Request to the Eclipse Foundation server failed: " + error,
                        HttpStatus.INTERNAL_SERVER_ERROR);
            } else if (responseJson.startsWith("[")) {
                var profileList = objectMapper.readValue(responseJson, TYPE_LIST_PROFILE);
                if (profileList.isEmpty()) {
                    throw new ErrorResultException("No Eclipse user profile available.", HttpStatus.INTERNAL_SERVER_ERROR);
                }
                return profileList.get(0);
            } else {
                return objectMapper.readValue(responseJson, EclipseProfile.class);
            }
        } catch (JsonProcessingException exc) {
            logger.error("Failed to parse JSON response (" + response.getStatusCode() + "):\n" + responseJson, exc);
            throw new ErrorResultException("Parsing Eclipse user profile failed: " + exc.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private static final Pattern STATUS_400_MESSAGE = Pattern.compile("400 Bad Request: \\[\\[\"(?<message>[^\"]+)\"\\]\\]");

    /**
     * Sign the publisher agreement on behalf of the given user.
     */
    public UserJson signPublisherAgreement(Principal principal) {
        checkApiUrl();

        var userId = UserUtil.getUserId(principal);
        var accessToken = keycloak.getEclipseToken(principal);
        if(accessToken == null) {
            throw new ErrorResultException("No active Eclipse access token", HttpStatus.FORBIDDEN);
        }

        var userName = keycloak.getEclipseUserName(userId);
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(accessToken);
        headers.setAccept(Arrays.asList(MediaType.APPLICATION_JSON));
        var data = new SignAgreementParam(publisherAgreementVersion, userName);
        var request = new HttpEntity<>(data, headers);
        var requestUrl = UrlUtil.createApiUrl(eclipseApiUrl, "openvsx", "publisher_agreement");

        try {
            var entity = restTemplate.postForEntity(requestUrl, request, String.class);

            // The request was successful: reactivate all previously published extensions
            extensions.reactivateExtensions(userId);

            // Parse the response and store the publisher agreement metadata
            var response = parseAgreementResponse(entity);
            var agreement = response.createEntityData(parseDate);
            entityManager.persist(agreement);

            var userJson = json.toUserJson(principal);
            enrichUserJson(userJson, agreement);
            return userJson;
        } catch (RestClientException exc) {
            String message = exc.getMessage();
            var statusCode = HttpStatus.INTERNAL_SERVER_ERROR;
            if (exc instanceof HttpStatusCodeException) {
                var excStatus = ((HttpStatusCodeException) exc).getStatusCode();
                // The endpoint yields 409 if the specified user has already signed a publisher agreement
                if (excStatus == HttpStatus.CONFLICT) {
                    message = "A publisher agreement is already present for user " + userName + ".";
                    statusCode = HttpStatus.BAD_REQUEST;
                } else if (excStatus == HttpStatus.BAD_REQUEST) {
                    var matcher = STATUS_400_MESSAGE.matcher(exc.getMessage());
                    if (matcher.matches()) {
                        message = matcher.group("message");
                    }
                }
            }
            if (statusCode == HttpStatus.INTERNAL_SERVER_ERROR) {
                message = "Request for signing publisher agreement failed: " + message;
            }

            String payload;
            try {
                payload = objectMapper.writeValueAsString(data);
            } catch (JsonProcessingException exc2) {
                payload = "<" + exc2.getMessage() + ">";
            }
            logger.error("Post request failed with URL: " + requestUrl + " Payload: " + payload, exc);
            throw new ErrorResultException(message, statusCode);
        }
    }

    private PublisherAgreementResponse parseAgreementResponse(ResponseEntity<String> response) {
        var responseJson = response.getBody();
        try {
            if (responseJson.startsWith("[\"")) {
                var error = objectMapper.readValue(responseJson, TYPE_LIST_STRING);
                logger.error("Publisher agreement request failed:\n" + responseJson);
                throw new ErrorResultException("Request to the Eclipse Foundation server failed: " + error,
                        HttpStatus.INTERNAL_SERVER_ERROR);
            } else if (responseJson.startsWith("[")) {
                var profileList = objectMapper.readValue(responseJson, TYPE_LIST_AGREEMENT);
                if (profileList.isEmpty()) {
                    throw new ErrorResultException("No publisher agreement available.", HttpStatus.INTERNAL_SERVER_ERROR);
                }
                return profileList.get(0);
            } else {
                return objectMapper.readValue(responseJson, PublisherAgreementResponse.class);
            }
        } catch (JsonProcessingException exc) {
            logger.error("Failed to parse JSON response (" + response.getStatusCode() + "):\n" + responseJson, exc);
            throw new ErrorResultException("Parsing publisher agreement response failed: " + exc.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Revoke the given user's publisher agreement. If an admin user is given,
     * the admin's access token is used for the Eclipse API request, otherwise
     * the access token of the target user is used.
     */
    public void revokePublisherAgreement(String userId, String adminId) {
        checkApiUrl();
        var userName = keycloak.getEclipseUserName(userId);
        if (Strings.isNullOrEmpty(userName)) {
            throw new ErrorResultException("Eclipse person ID is unavailable for user: " + userName);
        }

        var accessToken = keycloak.getEclipseToken(Optional.ofNullable(adminId).orElse(userId));
        if(accessToken == null) {
            throw new ErrorResultException("No active Eclipse access token", HttpStatus.FORBIDDEN);
        }

        var headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        var request = new HttpEntity<Void>(headers);
        var requestUrl = UrlUtil.createApiUrl(eclipseApiUrl, "openvsx", "publisher_agreement", userName);

        try {
            var requestCallback = restTemplate.httpEntityCallback(request);
            restTemplate.execute(requestUrl, HttpMethod.DELETE, requestCallback, null);

            var agreement = repositories.findPublisherAgreement(userId);
            if (agreement != null) {
                agreement.setActive(false);
            }
        } catch (RestClientException exc) {
            logger.error("Delete request failed with URL: " + requestUrl, exc);
            throw new ErrorResultException("Request for revoking publisher agreement failed: " + exc.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private void checkApiUrl() {
        if (Strings.isNullOrEmpty(eclipseApiUrl)) {
            throw new ErrorResultException("Missing URL for Eclipse API.");
        }
    }
}