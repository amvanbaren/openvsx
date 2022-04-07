/** ******************************************************************************
 * Copyright (c) 2022 Precies. Software and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 * ****************************************************************************** */
package org.eclipse.openvsx.keycloak;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.openvsx.json.*;
import org.eclipse.openvsx.util.ErrorResultException;
import org.eclipse.openvsx.util.TimeUtil;
import org.eclipse.openvsx.util.UrlUtil;
import org.keycloak.OAuth2Constants;
import org.keycloak.adapters.springsecurity.token.KeycloakAuthenticationToken;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.representations.idm.FederatedIdentityRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.security.Principal;
import java.time.ZoneOffset;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class KeycloakService {

    protected final Logger logger = LoggerFactory.getLogger(KeycloakService.class);

    private final String realm;
    private final String authServerUrl;
    private final String clientId;
    private final String clientSecret;
    private final RestTemplate restTemplate;
    private final Keycloak keycloak;

    @Autowired
    public KeycloakService(
            RestTemplate restTemplate,
            @Value("${keycloak.auth-server-url}") String authServerUrl,
            @Value("${keycloak.realm}") String realm,
            @Value("${keycloak-service.client.id}") String clientId,
            @Value("${keycloak-service.client.secret}") String clientSecret
    ) {
        this.realm = realm;
        this.authServerUrl = authServerUrl;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.restTemplate = restTemplate;
        keycloak = KeycloakBuilder.builder()
                .grantType(OAuth2Constants.CLIENT_CREDENTIALS)
                .serverUrl(authServerUrl)
                .realm(realm)
                .clientId(clientId)
                .clientSecret(clientSecret)
                .build();
    }

    public List<String> getUserRoles(String userId) {
        return keycloak.realm(realm)
                .users().get(userId)
                .roles().realmLevel().listAll()
                .stream()
                .map(RoleRepresentation::getName)
                .collect(Collectors.toList());
    }

    public String getUserName(String userId) {
        return keycloak.realm(realm)
                .users().get(userId)
                .toRepresentation()
                .getUsername();
    }

    public String getEclipseUserName(String userId) {
        return keycloak.realm(realm)
                .users().get(userId)
                .getFederatedIdentity()
                .stream()
                .filter(i -> i.getIdentityProvider().equals("eclipse"))
                .findFirst()
                .map(FederatedIdentityRepresentation::getUserName)
                .orElse(null);
    }

    public String getEclipseToken(Principal principal) {
        var token = ((KeycloakAuthenticationToken) principal).getAccount().getKeycloakSecurityContext().getTokenString();
        return getBrokerToken(token, "eclipse");
    }

    public String getEclipseToken(String userId) {
        var token = userTokenExchange(userId);
        return getBrokerToken(token, "eclipse");
    }

    private String userTokenExchange(String userId) {
        var body = new LinkedMultiValueMap<String, String>();
        body.set("client_id", clientId);
        body.set("client_secret", clientSecret);
        body.set("grant_type", "urn:ietf:params:oauth:grant-type:token-exchange");
        body.set("subject_token", keycloak.tokenManager().getAccessTokenString());
        body.set("requested_token_type", "urn:ietf:params:oauth:token-type:access_token");
        body.set("requested_subject", userId);

        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        var requestUrl = UrlUtil.createApiUrl(authServerUrl, "realms", realm, "protocol", "openid-connect", "token");
        var request = new RequestEntity<>(body, headers, HttpMethod.POST, URI.create(requestUrl));
        try {
            var response = restTemplate.exchange(request, String.class);
            var json = new ObjectMapper().readTree(response.getBody());
            return json.get("access_token").asText();
        } catch (RestClientException e) {
            logger.error("Get request failed with URL: " + request.getUrl(), e);
            throw new ErrorResultException("Token exchange request failed: " + e.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        } catch (JsonProcessingException e) {
            logger.error("JSON processing failed", e);
            throw new ErrorResultException("Token exchange request failed: " + e.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private String getBrokerToken(String accessToken, String broker) {
        var headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        var requestUrl = UrlUtil.createApiUrl(authServerUrl, "realms", realm, "broker", broker, "token");
        var request = new RequestEntity<>(headers, HttpMethod.GET, URI.create(requestUrl));
        try {
            var response = restTemplate.exchange(request, String.class);
            var json = new ObjectMapper().readTree(response.getBody());
            var expiresAt = json.get("accessTokenExpiration").asLong();
            var now = TimeUtil.getCurrentUTC().toEpochSecond(ZoneOffset.UTC) + 30; // add 30 seconds as leeway
            return expiresAt > now
                    ? json.get("access_token").asText()
                    : null;
        } catch (RestClientException e) {
            logger.error("Get request failed with URL: " + request.getUrl(), e);
            throw new ErrorResultException("Request for retrieving Eclipse access token failed: " + e.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        } catch (JsonProcessingException e) {
            logger.error("JSON processing failed", e);
            throw new ErrorResultException("Request for retrieving Eclipse access token failed: " + e.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    public String findUserIdByUserName(String userName) {
        return keycloak.realm(realm)
                .users().search(userName, true)
                .stream()
                .findFirst()
                .map(UserRepresentation::getId)
                .orElse(null);
    }

    public int countUsers() {
        return keycloak.realm(realm).users().count();
    }

    public List<UserJson> findUsersByUserNameContains(String name, int limit) {
        return keycloak.realm(realm)
                .users().search(name, null, null, null, 0, limit, true, false)
                .stream()
                .map(userRep -> {
                    var roles = getUserRoles(userRep.getId());
                    if(roles.contains("user")) {
                        userRep.setRealmRoles(getUserRoles(userRep.getId()));
                        return userRep;
                    } else {
                        // exclude non users, e.g. Keycloak service accounts
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .map(this::toUserJson)
                .collect(Collectors.toList());
    }

    public void enrichUserJson(ExtensionJson extensionJson) {
        if(extensionJson.publishedBy == null || extensionJson.publishedBy.userId == null) {
            return;
        }

        extensionJson.publishedBy = getUserJson(extensionJson.publishedBy.userId);
    }

    public void enrichUserJsons(QueryResultJson queryResultJson) {
        this.enrichUserJsons(
                queryResultJson.extensions,
                e -> e.publishedBy,
                (e, u) -> e.publishedBy = u
        );
    }

    public void enrichUserJsons(ReviewListJson reviewListJson) {
        this.enrichUserJsons(
                reviewListJson.reviews,
                r -> r.user,
                (r, u) -> r.user = u
        );
    }

    public void enrichUserJsons(NamespaceMembershipListJson namespaceMembershipListJson) {
        this.enrichUserJsons(
                namespaceMembershipListJson.namespaceMemberships,
                m -> m.user,
                (m, u) -> m.user = u
        );
    }

    private <T> void enrichUserJsons(List<T> list, Function<T, UserJson> mapper, BiConsumer<T, UserJson> setter) {
        var userJsons = list.stream()
                .map(mapper)
                .map(u -> u.userId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet())
                .stream()
                .map(id -> new AbstractMap.SimpleEntry<>(id, toUserJson(findUser(id))))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        list.forEach(i -> setter.accept(i, userJsons.get(mapper.apply(i).userId)));
    }

    public UserJson getUserJson(Principal principal) {
        var token = ((KeycloakAuthenticationToken) principal).getAccount().getKeycloakSecurityContext().getToken();

        var userJson = new UserJson();
        userJson.userName = token.getPreferredUsername();
        userJson.role = token.getRealmAccess().isUserInRole("admin") ? "admin" : null;
        userJson.fullName = token.getName();
        var attributes = token.getOtherClaims();
        userJson.avatarUrl = (String) attributes.get("avatar_url");
        userJson.homepage = (String) attributes.get("homepage");
        return userJson;
    }

    public UserJson getUserJson(String userId) {
        return toUserJson(findUser(userId));
    }

    private UserJson toUserJson(UserRepresentation userRep) {
        var userJson = new UserJson();
        userJson.userName = userRep.getUsername();
        userJson.role = userRep.getRealmRoles().stream().filter(r -> r.equals("admin")).findFirst().orElse(null);
        userJson.fullName = userRep.getFirstName() + " " + userRep.getLastName();
        userJson.avatarUrl = userRep.firstAttribute("avatar_url");
        userJson.homepage = userRep.firstAttribute("homepage");
        return userJson;
    }

    private UserRepresentation findUser(String userId) {
        var userRep = keycloak.realm(realm).users().get(userId).toRepresentation();
        userRep.setRealmRoles(getUserRoles(userId));
        return userRep;
    }
}
