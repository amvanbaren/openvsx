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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

import java.io.IOException;
import java.io.InputStreamReader;
import java.security.Principal;
import java.util.Set;

import javax.persistence.EntityManager;

import com.google.common.io.CharStreams;

import net.javacrumbs.shedlock.core.LockProvider;
import org.eclipse.openvsx.ExtensionService;
import org.eclipse.openvsx.ExtensionValidator;
import org.eclipse.openvsx.MockTransactionTemplate;
import org.eclipse.openvsx.UserService;
import org.eclipse.openvsx.adapter.VSCodeIdService;
import org.eclipse.openvsx.cache.CacheService;
import org.eclipse.openvsx.entities.*;
import org.eclipse.openvsx.json.JsonService;
import org.eclipse.openvsx.keycloak.KeycloakService;
import org.eclipse.openvsx.repositories.RepositoryService;
import org.eclipse.openvsx.search.SearchUtilService;
import org.eclipse.openvsx.storage.AzureBlobStorageService;
import org.eclipse.openvsx.storage.AzureDownloadCountService;
import org.eclipse.openvsx.storage.GoogleCloudStorageService;
import org.eclipse.openvsx.storage.StorageUtilService;
import org.eclipse.openvsx.util.ErrorResultException;
import org.eclipse.openvsx.util.TargetPlatform;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.KeycloakSecurityContext;
import org.keycloak.adapters.OidcKeycloakAccount;
import org.keycloak.adapters.springsecurity.token.KeycloakAuthenticationToken;
import org.keycloak.representations.AccessToken;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.data.util.Streamable;
import org.springframework.http.HttpStatus;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

@ExtendWith(SpringExtension.class)
@MockBean({
    EntityManager.class, SearchUtilService.class, GoogleCloudStorageService.class, AzureBlobStorageService.class,
    VSCodeIdService.class, AzureDownloadCountService.class, LockProvider.class, CacheService.class
})
public class EclipseServiceTest {

    @MockBean
    RepositoryService repositories;

    @MockBean
    UserService users;

    @MockBean
    RestTemplate restTemplate;

    @MockBean
    KeycloakService keycloak;

    @Autowired
    EclipseService eclipse;

    @BeforeEach
    public void setup() {
        eclipse.publisherAgreementVersion = "1";
        eclipse.eclipseApiUrl = "https://test.openvsx.eclipse.org/";
    }

    @Test
    public void testGetPublicProfile() throws Exception {
        Mockito.when(restTemplate.exchange(any(RequestEntity.class), eq(String.class)))
            .thenReturn(mockProfileResponse());

        var profile = eclipse.getPublicProfile("test");

        assertThat(profile).isNotNull();
        assertThat(profile.name).isEqualTo("test");
        assertThat(profile.githubHandle).isEqualTo("test");
        assertThat(profile.publisherAgreements).isNotNull();
        assertThat(profile.publisherAgreements.openVsx).isNotNull();
        assertThat(profile.publisherAgreements.openVsx.version).isEqualTo("1");
    }

    @Test
    public void testGetUserProfile() throws Exception {
        Mockito.when(restTemplate.exchange(any(RequestEntity.class), eq(String.class)))
            .thenReturn(mockProfileResponse());

        var profile = eclipse.getUserProfile("12345");

        assertThat(profile).isNotNull();
        assertThat(profile.name).isEqualTo("test");
        assertThat(profile.githubHandle).isEqualTo("test");
        assertThat(profile.publisherAgreements).isNotNull();
        assertThat(profile.publisherAgreements.openVsx).isNotNull();
        assertThat(profile.publisherAgreements.openVsx.version).isEqualTo("1");
    }

    @Test
    public void testSignPublisherAgreement() throws Exception {
        var principal = mockPrincipal();
        Mockito.when(restTemplate.postForEntity(any(String.class), any(), eq(String.class)))
            .thenReturn(mockAgreementResponse());
        Mockito.when(repositories.findAccessTokens("6d773836-bb12-11ec-8422-0242ac120002"))
            .thenReturn(Streamable.empty());
        Mockito.when(restTemplate.exchange(any(RequestEntity.class), eq(String.class)))
                .thenReturn(mockProfileResponse());

        var json = eclipse.signPublisherAgreement(principal);
        assertThat(json).isNotNull();
        assertThat(json.userName).isEqualTo("test");
        assertThat(json.publisherAgreement.status).isEqualTo("signed");
        assertThat(json.publisherAgreement.timestamp).isNotNull();
        assertThat(json.publisherAgreement.timestamp).isEqualTo("2020-10-09T05:10:32Z");
    }

    @Test
    public void testSignPublisherAgreementReactivateExtension() throws Exception {
        var principal = mockPrincipal();
        Mockito.when(restTemplate.postForEntity(any(String.class), any(), eq(String.class)))
            .thenReturn(mockAgreementResponse());
        Mockito.when(restTemplate.exchange(any(RequestEntity.class), eq(String.class)))
                .thenReturn(mockProfileResponse());

        var accessToken = new PersonalAccessToken();
        accessToken.setUserId("6d773836-bb12-11ec-8422-0242ac120002");
        accessToken.setActive(true);
        Mockito.when(repositories.findAccessTokens("6d773836-bb12-11ec-8422-0242ac120002"))
            .thenReturn(Streamable.of(accessToken));
        var namespace = new Namespace();
        namespace.setName("foo");
        var extension = new Extension();
        extension.setName("bar");
        extension.setNamespace(namespace);
        var extVersion = new ExtensionVersion();
        extVersion.setVersion("1.0.0");
        extVersion.setTargetPlatform(TargetPlatform.NAME_UNIVERSAL);
        extVersion.setExtension(extension);
        extension.getVersions().add(extVersion);
        Mockito.when(repositories.findVersionsByAccessToken(accessToken, false))
            .thenReturn(Streamable.of(extVersion));

        var json = eclipse.signPublisherAgreement(principal);
        assertThat(json.publisherAgreement.status).isEqualTo("signed");
        assertThat(extVersion.isActive()).isTrue();
        assertThat(extension.isActive()).isTrue();
    }

    @Test
    public void testPublisherAgreementAlreadySigned() {
        var principal = mockPrincipal();
        Mockito.when(restTemplate.postForEntity(any(String.class), any(), eq(String.class)))
            .thenThrow(new HttpClientErrorException(HttpStatus.CONFLICT));

        try {
            eclipse.signPublisherAgreement(principal);
            fail("Expected an ErrorResultException");
        } catch (ErrorResultException exc) {
            assertThat(exc.getMessage()).isEqualTo("A publisher agreement is already present for user test.");
        }
    }

    @Test
    public void testRevokePublisherAgreement() throws Exception {
        var userId = "6d773836-bb12-11ec-8422-0242ac120002";
        var agreement = new PublisherAgreement();
        agreement.setUserId(userId);
        agreement.setPersonId("test");
        agreement.setActive(true);
        Mockito.when(repositories.findPublisherAgreement(userId)).thenReturn(agreement);
        Mockito.when(keycloak.getEclipseUserName(userId)).thenReturn("test");
        Mockito.when(keycloak.getEclipseToken(userId)).thenReturn("12345");

        eclipse.revokePublisherAgreement(userId, null);
        assertThat(agreement.isActive()).isFalse();
    }

    @Test
    public void testRevokePublisherAgreementByAdmin() throws Exception {
        var agreement = new PublisherAgreement();
        agreement.setPersonId("test");
        agreement.setActive(true);

        var userId = "6d773836-bb12-11ec-8422-0242ac120002";
        Mockito.when(keycloak.getEclipseUserName(userId)).thenReturn(agreement.getPersonId());
        Mockito.when(repositories.findPublisherAgreement(userId)).thenReturn(agreement);

        var adminId = "14b26f34-bbe1-11ec-8422-0242ac120002";
        Mockito.when(keycloak.getEclipseToken(adminId)).thenReturn("67890");
        eclipse.revokePublisherAgreement(userId, adminId);
        assertThat(agreement.isActive()).isFalse();
    }

    private Principal mockPrincipal() {
        var userId = "6d773836-bb12-11ec-8422-0242ac120002";
        var account = new OidcKeycloakAccount() {

            @Override
            public KeycloakSecurityContext getKeycloakSecurityContext() {
                var token = new AccessToken();
                token.setSubject(userId);
                token.setPreferredUsername("test");
                token.setRealmAccess(new AccessToken.Access().addRole("user"));
                token.setName("Test User");
                token.setOtherClaims("avatar_url", "avatar.png");
                token.setOtherClaims("homepage", "homepage.com");
                return new KeycloakSecurityContext(null, token, null, null);
            }

            @Override
            public Principal getPrincipal() {
                return () -> "test";
            }

            @Override
            public Set<String> getRoles() {
                return Set.of("user");
            }
        };

        var token = new KeycloakAuthenticationToken(account, false);
        Mockito.when(keycloak.getEclipseToken(token)).thenReturn("12345");
        Mockito.when(keycloak.getEclipseUserName(userId)).thenReturn("test");
        return token;
    }

    private ResponseEntity<String> mockProfileResponse() throws IOException {
        try (
            var stream = getClass().getResourceAsStream("profile-response.json");
        ) {
            var json = CharStreams.toString(new InputStreamReader(stream));
            return new ResponseEntity<>(json, HttpStatus.OK);
        }
    }

    private ResponseEntity<String> mockAgreementResponse() throws IOException {
        try (
            var stream = getClass().getResourceAsStream("publisher-agreement-response.json");
        ) {
            var json = CharStreams.toString(new InputStreamReader(stream));
            return new ResponseEntity<>(json, HttpStatus.OK);
        }
    }
    
    @TestConfiguration
    static class TestConfig {
        @Bean
        TransactionTemplate transactionTemplate() {
            return new MockTransactionTemplate();
        }

        @Bean
        EclipseService eclipseService() {
            return new EclipseService();
        }

        @Bean
        JsonService jsonService() {
            return new JsonService();
        }

        @Bean
        ExtensionService extensionService() {
            return new ExtensionService();
        }

        @Bean
        ExtensionValidator extensionValidator() {
            return new ExtensionValidator();
        }

        @Bean
        StorageUtilService storageUtilService() {
            return new StorageUtilService();
        }
    }
    
}