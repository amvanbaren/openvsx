/********************************************************************************
 * Copyright (c) 2020 TypeFox and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/
package org.eclipse.openvsx;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Consumer;

import javax.persistence.EntityManager;

import com.c4_soft.springaddons.security.oauth2.test.annotations.OpenIdClaims;
import com.c4_soft.springaddons.security.oauth2.test.annotations.keycloak.WithMockKeycloakAuth;
import com.c4_soft.springaddons.security.oauth2.test.mockmvc.keycloak.ServletKeycloakAuthUnitTestingSupport;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import net.javacrumbs.shedlock.core.LockProvider;
import org.eclipse.openvsx.adapter.VSCodeIdService;
import org.eclipse.openvsx.cache.CacheService;
import org.eclipse.openvsx.eclipse.EclipseService;
import org.eclipse.openvsx.entities.*;
import org.eclipse.openvsx.json.*;
import org.eclipse.openvsx.keycloak.KeycloakService;
import org.eclipse.openvsx.repositories.RepositoryService;
import org.eclipse.openvsx.search.SearchUtilService;
import org.eclipse.openvsx.security.SecurityConfig;
import org.eclipse.openvsx.storage.AzureBlobStorageService;
import org.eclipse.openvsx.storage.AzureDownloadCountService;
import org.eclipse.openvsx.storage.GoogleCloudStorageService;
import org.eclipse.openvsx.storage.StorageUtilService;
import org.eclipse.openvsx.util.TargetPlatform;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.client.AutoConfigureWebClient;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.util.Streamable;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

@WebMvcTest(AdminAPI.class)
@AutoConfigureWebClient
@MockBean({
    UpstreamRegistryService.class, GoogleCloudStorageService.class, AzureBlobStorageService.class, VSCodeIdService.class,
    AzureDownloadCountService.class, LockProvider.class, CacheService.class, SearchUtilService.class, EntityManager.class,
    EclipseService.class
})
@Import({ ServletKeycloakAuthUnitTestingSupport.UnitTestConfig.class, SecurityConfig.class })
public class AdminAPITest {
    
    @SpyBean
    UserService users;

    @MockBean
    RepositoryService repositories;

    @MockBean
    KeycloakService keycloak;

    @Autowired
    MockMvc mockMvc;

    @Test
    public void testGetExtensionNotLoggedIn() throws Exception {
        mockExtension(2, 0, 0);
        mockMvc.perform(get("/admin/extension/{namespace}/{extension}", "foobar", "baz"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockKeycloakAuth(authorities = { "ROLE_USER" }, claims = @OpenIdClaims(sub = "6d773836-bb12-11ec-8422-0242ac120002"))
    public void testGetExtensionNotAdmin() throws Exception {
        mockExtension(2, 0, 0);
        mockMvc.perform(get("/admin/extension/{namespace}/{extension}", "foobar", "baz"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockKeycloakAuth(authorities = { "ROLE_ADMIN" }, claims = @OpenIdClaims(sub = "14b26f34-bbe1-11ec-8422-0242ac120002"))
    public void testGetExtension() throws Exception {
        mockExtension(2, 0, 0);
        mockMvc.perform(get("/admin/extension/{namespace}/{extension}", "foobar", "baz"))
                .andExpect(status().isOk())
                .andExpect(content().json(extensionJson(e -> {
                    e.namespace = "foobar";
                    e.name = "baz";
                    e.version = "2";
                    e.active = true;
                })));
    }

    @Test
    @WithMockKeycloakAuth(authorities = { "ROLE_ADMIN" }, claims = @OpenIdClaims(sub = "14b26f34-bbe1-11ec-8422-0242ac120002"))
    public void testGetInactiveExtension() throws Exception {
        mockExtension(2, 0, 0).forEach(ev -> {
            ev.setActive(false);
            ev.getExtension().setActive(false);
        });

        mockMvc.perform(get("/admin/extension/{namespace}/{extension}", "foobar", "baz"))
                .andExpect(status().isOk())
                .andExpect(content().json(extensionJson(e -> {
                    e.namespace = "foobar";
                    e.name = "baz";
                    e.version = "2";
                    e.active = false;
                })));
    }

    @Test
    public void testAddNamespaceMemberNotLoggedIn() throws Exception {
        mockMvc.perform(post("/admin/namespace/{namespace}/change-member?userName={userName}&role={role}", "foobar", "other_user", "owner"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockKeycloakAuth(authorities = { "ROLE_USER" }, claims = @OpenIdClaims(sub = "6d773836-bb12-11ec-8422-0242ac120002"))
    public void testAddNamespaceMemberNotAdmin() throws Exception {
        mockMvc.perform(post("/admin/namespace/{namespace}/change-member?userName={userName}&role={role}", "foobar", "other_user", "owner"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockKeycloakAuth(authorities = { "ROLE_ADMIN" }, claims = @OpenIdClaims(sub = "14b26f34-bbe1-11ec-8422-0242ac120002"))
    public void testAddNamespaceMember() throws Exception {
        var namespace = mockNamespace();

        var userName = "other_user";
        var userId = "7d94d644-b52a-11ec-b909-0242ac120002";
        Mockito.when(keycloak.findUserIdByUserName(userName)).thenReturn(userId);

        Mockito.when(repositories.findMembership(userId, namespace))
                .thenReturn(null);

        mockMvc.perform(post("/admin/namespace/{namespace}/change-member?userName={userName}&role={role}", "foobar", userName, "owner"))
                .andExpect(status().isOk())
                .andExpect(content().json(successJson("Added other_user as owner of foobar.")));
    }

    @Test
    @WithMockKeycloakAuth(authorities = { "ROLE_ADMIN" }, claims = @OpenIdClaims(sub = "14b26f34-bbe1-11ec-8422-0242ac120002"))
    public void testChangeNamespaceMember() throws Exception {
        var namespace = mockNamespace();

        var userName = "other_user";
        var userId = "7d94d644-b52a-11ec-b909-0242ac120002";
        Mockito.when(keycloak.findUserIdByUserName(userName)).thenReturn(userId);

        var membership2 = new NamespaceMembership();
        membership2.setUserId(userId);
        membership2.setNamespace(namespace);
        membership2.setRole(NamespaceMembership.ROLE_OWNER);
        Mockito.when(repositories.findMembership(userId, namespace))
                .thenReturn(membership2);

        mockMvc.perform(post("/admin/namespace/{namespace}/change-member?userName={userName}&role={role}", "foobar", "other_user", "contributor"))
                .andExpect(status().isOk())
                .andExpect(content().json(successJson("Changed role of other_user in foobar to contributor.")));
    }

    @Test
    @WithMockKeycloakAuth(authorities = { "ROLE_ADMIN" }, claims = @OpenIdClaims(sub = "14b26f34-bbe1-11ec-8422-0242ac120002"))
    public void testRemoveNamespaceMember() throws Exception {
        var namespace = mockNamespace();

        var userName2 = "other_user";
        var userId2 = "7d94d644-b52a-11ec-b909-0242ac120002";
        Mockito.when(keycloak.findUserIdByUserName(userName2)).thenReturn(userId2);

        var membership2 = new NamespaceMembership();
        membership2.setUserId(userId2);
        membership2.setNamespace(namespace);
        membership2.setRole(NamespaceMembership.ROLE_OWNER);
        Mockito.when(repositories.findMembership(userId2, namespace))
                .thenReturn(membership2);

        mockMvc.perform(post("/admin/namespace/{namespace}/change-member?userName={userName}&role={role}", "foobar", "other_user", "remove"))
                .andExpect(status().isOk())
                .andExpect(content().json(successJson("Removed other_user from namespace foobar.")));
    }

    @Test
    @WithMockKeycloakAuth(authorities = { "ROLE_ADMIN" }, claims = @OpenIdClaims(sub = "14b26f34-bbe1-11ec-8422-0242ac120002"))
    public void testChangeNamespaceMemberSameRole() throws Exception {
        var namespace = mockNamespace();

        var userName2 = "other_user";
        var userId2 = "7d94d644-b52a-11ec-b909-0242ac120002";
        Mockito.when(keycloak.findUserIdByUserName(userName2)).thenReturn(userId2);

        var membership2 = new NamespaceMembership();
        membership2.setUserId(userId2);
        membership2.setNamespace(namespace);
        membership2.setRole(NamespaceMembership.ROLE_CONTRIBUTOR);
        Mockito.when(repositories.findMembership(userId2, namespace))
                .thenReturn(membership2);

        mockMvc.perform(post("/admin/namespace/{namespace}/change-member?userName={userName}&role={role}", "foobar", "other_user", "contributor"))
                .andExpect(status().isBadRequest())
                .andExpect(content().json(errorJson("User other_user already has the role contributor.")));
    }

    @Test
    public void testDeleteExtensionNotLoggedIn() throws Exception {
        mockExtension(2, 0, 0);
        mockMvc.perform(post("/admin/extension/{namespace}/{extension}/delete", "foobar", "baz"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockKeycloakAuth(authorities = { "ROLE_USER" }, claims = @OpenIdClaims(sub = "6d773836-bb12-11ec-8422-0242ac120002"))
    public void testDeleteExtensionNotAdmin() throws Exception {
        mockExtension(2, 0, 0);
        mockMvc.perform(post("/admin/extension/{namespace}/{extension}/delete", "foobar", "baz"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockKeycloakAuth(authorities = { "ROLE_ADMIN" }, claims = @OpenIdClaims(sub = "14b26f34-bbe1-11ec-8422-0242ac120002"))
    public void testDeleteExtension() throws Exception {
        mockExtension(2, 0, 0);
        mockMvc.perform(post("/admin/extension/{namespace}/{extension}/delete", "foobar", "baz"))
                .andExpect(status().isOk())
                .andExpect(content().json(successJson("Deleted foobar.baz")));
    }

    @Test
    @WithMockKeycloakAuth(authorities = { "ROLE_ADMIN" }, claims = @OpenIdClaims(sub = "14b26f34-bbe1-11ec-8422-0242ac120002"))
    public void testDeleteExtensionVersion() throws Exception {
        mockExtension(2, 0, 0);
        mockMvc.perform(post("/admin/extension/{namespace}/{extension}/delete", "foobar", "baz")
                .content("[{\"targetPlatform\":\"universal\",\"version\":\"2\"}]")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().json(successJson("Deleted foobar.baz 2")));
    }

    @Test
    @WithMockKeycloakAuth(authorities = { "ROLE_ADMIN" }, claims = @OpenIdClaims(sub = "14b26f34-bbe1-11ec-8422-0242ac120002"))
    public void testDeleteLastExtensionVersion() throws Exception {
        mockExtension(1, 0, 0);
        mockMvc.perform(post("/admin/extension/{namespace}/{extension}/delete", "foobar", "baz")
                .content("[{\"targetPlatform\":\"universal\",\"version\":\"1\"}]")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().json(successJson("Deleted foobar.baz")));
    }

    @Test
    @WithMockKeycloakAuth(authorities = { "ROLE_ADMIN" }, claims = @OpenIdClaims(sub = "14b26f34-bbe1-11ec-8422-0242ac120002"))
    public void testDeleteBundledExtension() throws Exception {
        mockExtension(2, 1, 0);
        mockMvc.perform(post("/admin/extension/{namespace}/{extension}/delete", "foobar", "baz"))
                .andExpect(status().isBadRequest())
                .andExpect(content().json(errorJson("Extension foobar.baz is bundled by the following extension packs: foobar.bundle@1")));
    }

    @Test
    @WithMockKeycloakAuth(authorities = { "ROLE_ADMIN" }, claims = @OpenIdClaims(sub = "14b26f34-bbe1-11ec-8422-0242ac120002"))
    public void testDeleteDependingExtension() throws Exception {
        mockExtension(2, 0, 1);
        mockMvc.perform(post("/admin/extension/{namespace}/{extension}/delete", "foobar", "baz"))
                .andExpect(status().isBadRequest())
                .andExpect(content().json(errorJson("The following extensions have a dependency on foobar.baz: foobar.dependant@1")));
    }

    @Test
    public void testGetNamespaceNotLoggedIn() throws Exception {
        mockNamespace();
        mockMvc.perform(get("/admin/namespace/{namespace}", "foobar"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockKeycloakAuth(authorities = { "ROLE_USER" }, claims = @OpenIdClaims(sub = "6d773836-bb12-11ec-8422-0242ac120002"))
    public void testGetNamespaceNotAdmin() throws Exception {
        mockNamespace();
        mockMvc.perform(get("/admin/namespace/{namespace}", "foobar"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockKeycloakAuth(authorities = { "ROLE_ADMIN" }, claims = @OpenIdClaims(sub = "14b26f34-bbe1-11ec-8422-0242ac120002"))
    public void testGetNamespace() throws Exception {
        mockNamespace();
        mockMvc.perform(get("/admin/namespace/{namespace}", "foobar"))
                .andExpect(status().isOk())
                .andExpect(content().json(namespaceJson(n -> {
                    n.name = "foobar";
                })));
    }

    @Test
    public void testGetNamespaceMembersNotLoggedIn() throws Exception {
        mockNamespace();
        mockMvc.perform(get("/admin/namespace/{namespace}/members", "foobar"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockKeycloakAuth(authorities = { "ROLE_USER" }, claims = @OpenIdClaims(sub = "6d773836-bb12-11ec-8422-0242ac120002"))
    public void testGetNamespaceMembersNotAdmin() throws Exception {
        mockNamespace();
        mockMvc.perform(get("/admin/namespace/{namespace}/members", "foobar"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockKeycloakAuth(authorities = { "ROLE_ADMIN" }, claims = @OpenIdClaims(sub = "14b26f34-bbe1-11ec-8422-0242ac120002"))
    public void testGetNamespaceMembers() throws Exception {
        var namespace = mockNamespace();
        var membership1 = new NamespaceMembership();
        membership1.setNamespace(namespace);
        membership1.setUserId("other_user_id");
        membership1.setRole(NamespaceMembership.ROLE_OWNER);
        Mockito.when(repositories.findMemberships(namespace))
                .thenReturn(Streamable.of(membership1));
        Mockito.doAnswer(invocation -> {
            var list = invocation.getArgument(0, NamespaceMembershipListJson.class);
            if(list.namespaceMemberships.get(0).user.userId.equals("other_user_id")) {
                list.namespaceMemberships.get(0).user.userName = "other_user";
            }

            return list;
        }).when(keycloak).enrichUserJsons(any(NamespaceMembershipListJson.class));

        mockMvc.perform(get("/admin/namespace/{namespace}/members", "foobar"))
                .andExpect(status().isOk())
                .andExpect(content().json(namespaceMemberJson(nml -> {
                    var m = new NamespaceMembershipJson();
                    m.namespace = "foobar";
                    m.user = new UserJson();
                    m.user.userName = "other_user";
                    m.role = "owner";
                    nml.namespaceMemberships = Arrays.asList(m);
                })));
    }

    @Test
    public void testCreateNamespaceNotLoggedIn() throws Exception {
        mockMvc.perform(post("/admin/create-namespace")
                .contentType(MediaType.APPLICATION_JSON)
                .content(namespaceJson(n -> { n.name = "foobar"; })))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockKeycloakAuth(authorities = { "ROLE_USER" }, claims = @OpenIdClaims(sub = "6d773836-bb12-11ec-8422-0242ac120002"))
    public void testCreateNamespaceNotAdmin() throws Exception {
        mockMvc.perform(post("/admin/create-namespace")
                .contentType(MediaType.APPLICATION_JSON)
                .content(namespaceJson(n -> { n.name = "foobar"; })))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockKeycloakAuth(authorities = { "ROLE_ADMIN" }, claims = @OpenIdClaims(sub = "14b26f34-bbe1-11ec-8422-0242ac120002"))
    public void testCreateNamespace() throws Exception {
        mockMvc.perform(post("/admin/create-namespace")
                .contentType(MediaType.APPLICATION_JSON)
                .content(namespaceJson(n -> { n.name = "foobar"; })))
                .andExpect(status().isCreated())
                .andExpect(redirectedUrl("http://localhost/admin/namespace/foobar"))
                .andExpect(content().json(successJson("Created namespace foobar")));
    }

    @Test
    @WithMockKeycloakAuth(authorities = { "ROLE_ADMIN" }, claims = @OpenIdClaims(sub = "14b26f34-bbe1-11ec-8422-0242ac120002"))
    public void testCreateExistingNamespace() throws Exception {
        var namespace = new Namespace();
        namespace.setName("foobar");
        Mockito.when(repositories.findNamespace("foobar"))
                .thenReturn(namespace);
 
        mockMvc.perform(post("/admin/create-namespace")
                .contentType(MediaType.APPLICATION_JSON)
                .content(namespaceJson(n -> { n.name = "foobar"; })))
                .andExpect(status().isBadRequest())
                .andExpect(content().json(errorJson("Namespace already exists: foobar")));
    }

    @Test
    public void testGetUserPublishInfoNotLoggedIn() throws Exception {
        mockNamespace();
        mockMvc.perform(get("/admin/publisher/{loginName}", "test"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockKeycloakAuth(authorities = { "ROLE_USER" }, claims = @OpenIdClaims(sub = "6d773836-bb12-11ec-8422-0242ac120002"))
    public void testGetUserPublishInfoNotAdmin() throws Exception {
        mockMvc.perform(get("/admin/publisher/{loginName}", "test"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockKeycloakAuth(authorities = { "ROLE_ADMIN" }, claims = @OpenIdClaims(sub = "14b26f34-bbe1-11ec-8422-0242ac120002"))
    public void testGetUserPublishInfo() throws Exception {
        var userName = "test";
        var userId = "7d94d644-b52a-11ec-b909-0242ac120002";
        Mockito.when(keycloak.findUserIdByUserName(userName)).thenReturn(userId);

        var token = new PersonalAccessToken();
        token.setUserId(userId);
        token.setActive(true);
        Mockito.when(repositories.findAccessTokens(userId))
                .thenReturn(Streamable.of(token));

        var versions = mockExtension(1, 0, 0);
        versions.forEach(v -> v.setPublishedWith(token));
        Mockito.when(repositories.findVersions(userId))
                .thenReturn(Streamable.of(versions));

        var userJson = new UserJson();
        userJson.userName = userName;
        Mockito.when(keycloak.getUserJson(userId)).thenReturn(userJson);

        mockMvc.perform(get("/admin/publisher/{userName}", "test"))
                .andExpect(status().isOk())
                .andExpect(content().json(publishInfoJson(upi -> {
                    upi.user = new UserJson();
                    upi.user.userName = "test";
                    upi.activeAccessTokenNum = 1;
                    var ext1 = new ExtensionJson();
                    ext1.namespace = "foobar";
                    ext1.name = "baz";
                    ext1.version = "1";
                    upi.extensions = Arrays.asList(ext1);
                })));
    }

    @Test
    public void testRevokePublisherAgreementNotLoggedIn() throws Exception {
        mockNamespace();
        mockMvc.perform(post("/admin/publisher/{loginName}/revoke", "test"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockKeycloakAuth(authorities = { "ROLE_USER" }, claims = @OpenIdClaims(sub = "6d773836-bb12-11ec-8422-0242ac120002"))
    public void testRevokePublisherAgreementNotAdmin() throws Exception {
        mockMvc.perform(post("/admin/publisher/{loginName}/revoke", "test"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockKeycloakAuth(authorities = { "ROLE_ADMIN" }, claims = @OpenIdClaims(sub = "14b26f34-bbe1-11ec-8422-0242ac120002"))
    public void testRevokePublisherAgreement() throws Exception {
        var userName = "test";
        var userId = "7d94d644-b52a-11ec-b909-0242ac120002";
        Mockito.when(keycloak.findUserIdByUserName(userName)).thenReturn(userId);

        var token = new PersonalAccessToken();
        token.setUserId(userId);
        token.setActive(true);
        Mockito.when(repositories.findAccessTokens(userId))
                .thenReturn(Streamable.of(token));

        var versions = mockExtension(1, 0, 0);
        versions.forEach(v -> v.setPublishedWith(token));
        Mockito.when(repositories.findVersionsByAccessToken(token, true))
                .thenReturn(Streamable.of(versions));

        mockMvc.perform(post("/admin/publisher/{userName}/revoke", userName))
                .andExpect(status().isOk())
                .andExpect(content().json(successJson("Deactivated 1 tokens and deactivated 1 extensions of user test.")));

        assertThat(token.isActive()).isFalse();
        assertThat(versions.get(0).isActive()).isFalse();
    }

    @Test
    public void testReportNoAdminToken() throws Exception {
        var token = mockNonAdminToken();
        mockMvc.perform(get("/admin/report?token={token}&year=2021&month=3", token.getValue()))
                .andExpect(status().isForbidden());
    }

    @Test
    public void testReportNegativeYear() throws Exception {
        var token = mockAdminToken();
        mockMvc.perform(get("/admin/report?token={token}&year=-1&month=3", token.getValue()))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Year can't be negative"));
    }

    @Test
    public void testReportFutureYear() throws Exception {
        var token = mockAdminToken();
        var future = LocalDateTime.now().plusYears(1);
        mockMvc.perform(get("/admin/report?token={token}&year={year}&month=3", token.getValue(), future.getYear()))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Combination of year and month lies in the future"));
    }

    @Test
    public void testReportMonthLessThanOne() throws Exception {
        var token = mockAdminToken();
        var now = LocalDateTime.now();
        mockMvc.perform(get("/admin/report?token={token}&year={year}&month=0", token.getValue(), now.getYear()))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Month must be a value between 1 and 12"));
    }

    @Test
    public void testReportMonthGreaterThanTwelve() throws Exception {
        var token = mockAdminToken();
        var now = LocalDateTime.now();
        mockMvc.perform(get("/admin/report?token={token}&year={year}&month=13", token.getValue(), now.getYear()))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Month must be a value between 1 and 12"));
    }

    @Test
    public void testReportFutureMonth() throws Exception {
        var token = mockAdminToken();
        var future = LocalDateTime.now().plusMonths(1);
        mockMvc.perform(get("/admin/report?token={token}&year={year}&month={month}", token.getValue(), future.getYear(), future.getMonthValue()))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Combination of year and month lies in the future"));
    }

    @Test
    public void testArchivedReport() throws Exception {
        var token = mockAdminToken();
        var past = LocalDateTime.now().minusMonths(1);
        var year = past.getYear();
        var month = past.getMonthValue();

        var stats = new AdminStatistics();
        stats.setYear(year);
        stats.setMonth(month);
        stats.setExtensions(1234);
        stats.setDownloads(423);
        stats.setDownloadsTotal(67890);
        stats.setPublishers(891);
        stats.setAverageReviewsPerExtension(4.5);
        stats.setNamespaceOwners(56);
        stats.setExtensionsByRating(Map.of(1, 7, 2, 16, 3, 560, 4, 427, 5, 136));
        stats.setPublishersByExtensionsPublished(Map.of(1, 670, 2, 99, 3, 70, 4, 52));

        Mockito.when(repositories.findAdminStatisticsByYearAndMonth(year, month)).thenReturn(stats);
        mockMvc.perform(get("/admin/report?token={token}&year={year}&month={month}", token.getValue(), year, month))
                .andExpect(status().isOk())
                .andExpect(content().string(stats.toCsv()));
    }

    @Test
    public void testAdminOnTheFlyReport() throws Exception {
        var token = mockAdminToken();
        var year = 2021;
        var month = 7;
        var extensions = 9123L;
        var downloads = 2145L;
        var downloadsTotal = 57199L;
        var publishers = 846L;
        var averageReviewsPerExtension = 8.75;
        var namespaceOwners = 623L;
        var extensionsByRating = Map.of(3, 8000, 5, 1123);
        var publishersByExtensionsPublished = Map.of(1, 6590, 3, 815);

        var stats = new AdminStatistics();
        stats.setYear(year);
        stats.setMonth(month);
        stats.setExtensions(extensions);
        stats.setDownloads(downloads);
        stats.setDownloadsTotal(downloadsTotal);
        stats.setPublishers(publishers);
        stats.setAverageReviewsPerExtension(averageReviewsPerExtension);
        stats.setNamespaceOwners(namespaceOwners);
        stats.setExtensionsByRating(extensionsByRating);
        stats.setPublishersByExtensionsPublished(publishersByExtensionsPublished);

        Mockito.when(repositories.findAdminStatisticsByYearAndMonth(year, month)).thenReturn(null);

        var startInclusive = LocalDateTime.of(year, month, 1, 0, 0);
        var endExclusive = startInclusive.plusMonths(1);
        Mockito.when(repositories.countActiveExtensions(endExclusive)).thenReturn(extensions);
        Mockito.when(repositories.downloadsBetween(startInclusive, endExclusive)).thenReturn(downloads);
        Mockito.when(repositories.downloadsUntil(endExclusive)).thenReturn(downloadsTotal);
        Mockito.when(repositories.countActiveExtensionPublishers(endExclusive)).thenReturn(publishers);
        Mockito.when(repositories.averageNumberOfActiveReviewsPerActiveExtension(endExclusive)).thenReturn(averageReviewsPerExtension);
        Mockito.when(repositories.countPublishersThatClaimedNamespaceOwnership(endExclusive)).thenReturn(namespaceOwners);
        Mockito.when(repositories.countActiveExtensionsGroupedByExtensionReviewRating(endExclusive)).thenReturn(extensionsByRating);
        Mockito.when(repositories.countActiveExtensionPublishersGroupedByExtensionsPublished(endExclusive)).thenReturn(publishersByExtensionsPublished);

        mockMvc.perform(get("/admin/report?token={token}&year={year}&month={month}", token.getValue(), year, month))
                .andExpect(status().isOk())
                .andExpect(content().string(stats.toCsv()));
    }

    //---------- UTILITY ----------//

    private PersonalAccessToken mockAdminToken() {
        var tokenValue = "admin_token";
        var token = new PersonalAccessToken();
        token.setActive(true);
        token.setValue(tokenValue);
        token.setUserId("14b26f34-bbe1-11ec-8422-0242ac120002");

        Mockito.when(repositories.findAccessToken(tokenValue)).thenReturn(token);
        Mockito.when(keycloak.getUserRoles("14b26f34-bbe1-11ec-8422-0242ac120002")).thenReturn(List.of("admin"));
        return token;
    }

    private PersonalAccessToken mockNonAdminToken() {
        var tokenValue = "normal_token";
        var token = new PersonalAccessToken();
        token.setActive(true);
        token.setValue(tokenValue);
        token.setUserId("privileged_user");

        Mockito.when(repositories.findAccessToken(tokenValue)).thenReturn(token);
        return token;
    }

    private Namespace mockNamespace() {
        var namespace = new Namespace();
        namespace.setName("foobar");
        Mockito.when(repositories.findNamespace("foobar"))
                .thenReturn(namespace);
        Mockito.when(repositories.findActiveExtensions(namespace))
                .thenReturn(Streamable.empty());
        Mockito.when(repositories.countMemberships(namespace, NamespaceMembership.ROLE_OWNER))
                .thenReturn(0l);
        return namespace;
    }

    private String namespaceJson(Consumer<NamespaceJson> content) throws JsonProcessingException {
        var json = new NamespaceJson();
        content.accept(json);
        return new ObjectMapper().writeValueAsString(json);
    }

    private String namespaceMemberJson(Consumer<NamespaceMembershipListJson> content) throws JsonProcessingException {
        var json = new NamespaceMembershipListJson();
        content.accept(json);
        return new ObjectMapper().writeValueAsString(json);
    }

    private List<ExtensionVersion> mockExtension(int numberOfVersions, int numberOfBundles, int numberOfDependants) {
        var namespace = mockNamespace();
        var extension = new Extension();
        extension.setNamespace(namespace);
        extension.setName("baz");
        extension.setActive(true);
        Mockito.when(repositories.findExtension("baz", "foobar"))
                .thenReturn(extension);

        var versions = new ArrayList<ExtensionVersion>(numberOfVersions);
        for (var i = 0; i < numberOfVersions; i++) {
            var extVersion = new ExtensionVersion();
            extVersion.setExtension(extension);
            extVersion.setTargetPlatform(TargetPlatform.NAME_UNIVERSAL);
            extVersion.setVersion(Integer.toString(i + 1));
            extVersion.setActive(true);
            Mockito.when(repositories.findFiles(extVersion))
                    .thenReturn(Streamable.empty());
            Mockito.when(repositories.findFilesByType(eq(extVersion), any()))
                    .thenReturn(Streamable.empty());
            Mockito.when(repositories.findVersion(extVersion.getVersion(), TargetPlatform.NAME_UNIVERSAL, "baz", "foobar"))
                    .thenReturn(extVersion);
            Mockito.when(repositories.findTargetPlatformVersions(extVersion.getVersion(), "baz", "foobar"))
                    .thenReturn(Streamable.of(versions));
            versions.add(extVersion);
        }
        extension.getVersions().addAll(versions);
        Mockito.when(repositories.findVersions(extension))
                .thenReturn(Streamable.of(versions));
        Mockito.when(repositories.findActiveVersions(extension))
                .thenReturn(Streamable.of(versions));

        var bundleExt = new Extension();
        bundleExt.setName("bundle");
        bundleExt.setNamespace(namespace);

        var bundles = new ArrayList<ExtensionVersion>(numberOfBundles);
        for (var i = 0; i < numberOfBundles; i++) {
            var bundle = new ExtensionVersion();
            bundle.setExtension(bundleExt);
            bundle.setTargetPlatform(TargetPlatform.NAME_UNIVERSAL);
            bundle.setVersion(Integer.toString(i + 1));
            bundles.add(bundle);
        }
        Mockito.when(repositories.findBundledExtensionsReference(extension))
                .thenReturn(Streamable.of(bundles));

        var dependantExt = new Extension();
        dependantExt.setName("dependant");
        dependantExt.setNamespace(namespace);

        var dependants = new ArrayList<ExtensionVersion>(numberOfDependants);
        for (var i = 0; i < numberOfDependants; i++) {
            var dependant = new ExtensionVersion();
            dependant.setExtension(dependantExt);
            dependant.setTargetPlatform(TargetPlatform.NAME_UNIVERSAL);
            dependant.setVersion(Integer.toString(i + 1));
            dependants.add(dependant);
        }
        Mockito.when(repositories.findDependenciesReference(extension))
                .thenReturn(Streamable.of(dependants));

        Mockito.when(repositories.findAllReviews(extension))
                .thenReturn(Streamable.empty());
        return versions;
    }

    private String extensionJson(Consumer<ExtensionJson> content) throws JsonProcessingException {
        var json = new ExtensionJson();
        content.accept(json);
        return new ObjectMapper().writeValueAsString(json);
    }

    private String publishInfoJson(Consumer<UserPublishInfoJson> content) throws JsonProcessingException {
        var json = new UserPublishInfoJson();
        content.accept(json);
        return new ObjectMapper().writeValueAsString(json);
    }

    private String successJson(String message) throws JsonProcessingException {
        var result = new ResultJson();
        result.success = message;
        return resultJson(result);
    }

    private String errorJson(String message) throws JsonProcessingException {
        var result = new ResultJson();
        result.error = message;
        return resultJson(result);
    }

    private String resultJson(ResultJson result) throws JsonProcessingException {
        return new ObjectMapper().writeValueAsString(result);
    }
    
    @TestConfiguration
    static class TestConfig {
        @Bean
        TransactionTemplate transactionTemplate() {
            return new MockTransactionTemplate();
        }

        @Bean
        AdminService adminService() {
            return new AdminService();
        }

        @Bean
        LocalRegistryService localRegistryService() {
            return new LocalRegistryService();
        }

        @Bean
        ExtensionService extensionService() {
            return new ExtensionService();
        }

        @Bean
        JsonService jsonService() {
            return new JsonService();
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