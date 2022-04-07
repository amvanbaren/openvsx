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

import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import javax.persistence.EntityManager;

import com.c4_soft.springaddons.security.oauth2.test.annotations.OpenIdClaims;
import com.c4_soft.springaddons.security.oauth2.test.annotations.keycloak.WithMockKeycloakAuth;
import com.c4_soft.springaddons.security.oauth2.test.mockmvc.keycloak.ServletKeycloakAuthUnitTestingSupport;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import net.javacrumbs.shedlock.core.LockProvider;
import org.eclipse.openvsx.cache.CacheService;
import org.eclipse.openvsx.eclipse.EclipseService;
import org.eclipse.openvsx.entities.Namespace;
import org.eclipse.openvsx.entities.NamespaceMembership;
import org.eclipse.openvsx.entities.PersonalAccessToken;
import org.eclipse.openvsx.json.*;
import org.eclipse.openvsx.keycloak.KeycloakService;
import org.eclipse.openvsx.repositories.RepositoryService;
import org.eclipse.openvsx.security.SecurityConfig;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

@WebMvcTest(UserAPI.class)
@AutoConfigureWebClient
@MockBean({ EntityManager.class, LockProvider.class, CacheService.class, EclipseService.class })
@Import({ ServletKeycloakAuthUnitTestingSupport.UnitTestConfig.class, SecurityConfig.class })
public class UserAPITest {

    @SpyBean
    UserService users;

    @MockBean
    RepositoryService repositories;

    @MockBean
    KeycloakService keycloak;

    @Autowired
    MockMvc mockMvc;

    @Test
    @WithMockKeycloakAuth(authorities = { "ROLE_USER" }, claims = @OpenIdClaims(sub = "325a2930-bb11-11ec-8422-0242ac120002"))
    public void testAccessTokens() throws Exception {
        mockAccessTokens();
        mockMvc.perform(get("/user/tokens"))
                .andExpect(status().isOk())
                .andExpect(content().json(accessTokensJson(a -> {
                    var t1 = new AccessTokenJson();
                    t1.description = "This is token 1";
                    t1.createdTimestamp = "2000-01-01T10:00Z";
                    a.add(t1);
                    var t3 = new AccessTokenJson();
                    t3.description = "This is token 3";
                    t3.createdTimestamp = "2000-01-01T10:00Z";
                    a.add(t3);
                })));
    }

    @Test
    public void testAccessTokensNotLoggedIn() throws Exception {
        mockAccessTokens();
        mockMvc.perform(get("/user/tokens"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockKeycloakAuth(authorities = { "ROLE_USER" }, claims = @OpenIdClaims(sub = "325a2930-bb11-11ec-8422-0242ac120002"))
    public void testCreateAccessToken() throws Exception {
        Mockito.doReturn("foobar").when(users).generateTokenValue();
        mockMvc.perform(post("/user/token/create?description={description}", "This is my token"))
                .andExpect(status().isCreated())
                .andExpect(content().json(accessTokenJson(t -> {
                    t.value = "foobar";
                    t.description = "This is my token";
                })));
    }

    @Test
    public void testCreateAccessTokenNotLoggedIn() throws Exception {
        mockMvc.perform(post("/user/token/create?description={description}", "This is my token"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockKeycloakAuth(
            authorities = { "ROLE_USER" },
            claims = @OpenIdClaims(sub = "325a2930-bb11-11ec-8422-0242ac120002", preferredUsername = "test_user")
    )
    public void testDeleteAccessToken() throws Exception {
        var token = new PersonalAccessToken();
        token.setId(100);
        token.setUserId("325a2930-bb11-11ec-8422-0242ac120002");
        token.setActive(true);
        Mockito.when(repositories.findAccessToken(100))
                .thenReturn(token);

        mockMvc.perform(post("/user/token/delete/{id}", 100))
                .andExpect(status().isOk())
                .andExpect(content().json(successJson("Deleted access token for user test_user.")));
    }

    @Test
    public void testDeleteAccessTokenNotLoggedIn() throws Exception {
        mockMvc.perform(post("/user/token/delete/{id}", 100))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockKeycloakAuth(authorities = { "ROLE_USER" }, claims = @OpenIdClaims(sub = "325a2930-bb11-11ec-8422-0242ac120002"))
    public void testDeleteAccessTokenInactive() throws Exception {
        var token = new PersonalAccessToken();
        token.setId(100);
        token.setUserId("325a2930-bb11-11ec-8422-0242ac120002");
        token.setActive(false);
        Mockito.when(repositories.findAccessToken(100))
                .thenReturn(token);

        mockMvc.perform(post("/user/token/delete/{id}", 100))
                .andExpect(status().isNotFound())
                .andExpect(content().json(errorJson("Token does not exist.")));
    }

    @Test
    @WithMockKeycloakAuth(authorities = { "ROLE_USER" }, claims = @OpenIdClaims(sub = "325a2930-bb11-11ec-8422-0242ac120002"))
    public void testDeleteAccessTokenWrongUser() throws Exception {
        var token = new PersonalAccessToken();
        token.setId(100);
        token.setUserId("92d2910c-bbe9-11ec-8422-0242ac120002");
        token.setActive(true);
        Mockito.when(repositories.findAccessToken(100))
                .thenReturn(token);

        mockMvc.perform(post("/user/token/delete/{id}", 100))
                .andExpect(status().isNotFound())
                .andExpect(content().json(errorJson("Token does not exist.")));
    }

    @Test
    @WithMockKeycloakAuth(authorities = { "ROLE_USER" }, claims = @OpenIdClaims(sub = "325a2930-bb11-11ec-8422-0242ac120002"))
    public void testOwnNamespaces() throws Exception {
        mockOwnMemberships();
        mockMvc.perform(get("/user/namespaces"))
                .andExpect(status().isOk())
                .andExpect(content().json(namespacesJson(a -> {
                    var ns1 = new NamespaceJson();
                    ns1.name = "foo";
                    a.add(ns1);
                    var ns2 = new NamespaceJson();
                    ns2.name = "bar";
                    a.add(ns2);
                })));
    }

    @Test
    public void testOwnNamespacesNotLoggedIn() throws Exception {
        mockOwnMemberships();
        mockMvc.perform(get("/user/namespaces"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockKeycloakAuth(authorities = { "ROLE_USER" }, claims = @OpenIdClaims(sub = "325a2930-bb11-11ec-8422-0242ac120002"))
    public void testNamespaceMembers() throws Exception {
        mockNamespaceMemberships(NamespaceMembership.ROLE_OWNER);
        mockMvc.perform(get("/user/namespace/{name}/members", "foobar"))
                .andExpect(status().isOk())
                .andExpect(content().json(membershipsJson(a -> {
                    var u1 = new UserJson();
                    u1.userName = "test_user";
                    var m1 = new NamespaceMembershipJson();
                    m1.user = u1;
                    m1.namespace = "foobar";
                    m1.role = NamespaceMembership.ROLE_OWNER;
                    a.namespaceMemberships.add(m1);
                    var u2 = new UserJson();
                    u2.userName = "other_user";
                    var m2 = new NamespaceMembershipJson();
                    m2.user = u2;
                    m2.namespace = "foobar";
                    m2.role = NamespaceMembership.ROLE_CONTRIBUTOR;
                    a.namespaceMemberships.add(m2);
                })));
    }

    @Test
    public void testNamespaceMembersNotLoggedIn() throws Exception {
        mockNamespaceMemberships(NamespaceMembership.ROLE_OWNER);
        mockMvc.perform(get("/user/namespace/{name}/members", "foobar"))
                .andExpect(status().isForbidden());
    }

    @Test
    public void testNamespaceMembersNotOwner() throws Exception {
        mockNamespaceMemberships(NamespaceMembership.ROLE_CONTRIBUTOR);
        mockMvc.perform(get("/user/namespace/{name}/members", "foobar"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockKeycloakAuth(authorities = { "ROLE_USER" }, claims = @OpenIdClaims(sub = "325a2930-bb11-11ec-8422-0242ac120002"))
    public void testAddNamespaceMember() throws Exception {
        var namespace = new Namespace();
        namespace.setName("foobar");
        Mockito.when(repositories.findNamespace("foobar"))
                .thenReturn(namespace);

        var userId1 = "325a2930-bb11-11ec-8422-0242ac120002";
        var membership = new NamespaceMembership();
        membership.setUserId(userId1);
        membership.setNamespace(namespace);
        membership.setRole(NamespaceMembership.ROLE_OWNER);
        Mockito.when(repositories.findMembership(userId1, namespace))
                .thenReturn(membership);

        var userId2 = "206b5986-bb0e-11ec-8422-0242ac120002";
        Mockito.when(keycloak.findUserIdByUserName("other_user")).thenReturn(userId2);
        Mockito.when(repositories.findMembership(userId2, namespace))
                .thenReturn(null);

        mockMvc.perform(post("/user/namespace/{namespace}/role?userName={userName}&role={role}", "foobar",
                    "other_user", "contributor"))
                .andExpect(status().isOk())
                .andExpect(content().json(successJson("Added other_user as contributor of foobar.")));
    }

    @Test
    public void testAddNamespaceMemberNotLoggedIn() throws Exception {
        mockMvc.perform(post("/user/namespace/{namespace}/role?userName={userName}&role={role}", "foobar",
                    "other_user", "contributor"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockKeycloakAuth(authorities = { "ROLE_USER" }, claims = @OpenIdClaims(sub = "325a2930-bb11-11ec-8422-0242ac120002"))
    public void testChangeNamespaceMember() throws Exception {
        var namespace = new Namespace();
        namespace.setName("foobar");
        Mockito.when(repositories.findNamespace("foobar"))
                .thenReturn(namespace);

        var userId1 = "325a2930-bb11-11ec-8422-0242ac120002";
        var membership1 = new NamespaceMembership();
        membership1.setUserId(userId1);
        membership1.setNamespace(namespace);
        membership1.setRole(NamespaceMembership.ROLE_OWNER);
        Mockito.when(repositories.findMembership(userId1, namespace))
                .thenReturn(membership1);

        var userId2 = "206b5986-bb0e-11ec-8422-0242ac120002";
        var membership2 = new NamespaceMembership();
        membership2.setUserId(userId2);
        membership2.setNamespace(namespace);
        membership2.setRole(NamespaceMembership.ROLE_OWNER);
        Mockito.when(keycloak.findUserIdByUserName("other_user")).thenReturn(userId2);
        Mockito.when(repositories.findMembership(userId2, namespace))
                .thenReturn(membership2);

        mockMvc.perform(post("/user/namespace/{namespace}/role?userName={userName}&role={role}", "foobar",
                    "other_user", "contributor"))
                .andExpect(status().isOk())
                .andExpect(content().json(successJson("Changed role of other_user in foobar to contributor.")));
    }

    @Test
    @WithMockKeycloakAuth(authorities = { "ROLE_USER" }, claims = @OpenIdClaims(sub = "325a2930-bb11-11ec-8422-0242ac120002"))
    public void testRemoveNamespaceMember() throws Exception {
        var namespace = new Namespace();
        namespace.setName("foobar");
        Mockito.when(repositories.findNamespace("foobar"))
                .thenReturn(namespace);

        var userId1 = "325a2930-bb11-11ec-8422-0242ac120002";
        var membership1 = new NamespaceMembership();
        membership1.setUserId(userId1);
        membership1.setNamespace(namespace);
        membership1.setRole(NamespaceMembership.ROLE_OWNER);
        Mockito.when(repositories.findMembership(userId1, namespace))
                .thenReturn(membership1);

        var userId2 = "206b5986-bb0e-11ec-8422-0242ac120002";
        var membership2 = new NamespaceMembership();
        membership2.setUserId(userId2);
        membership2.setNamespace(namespace);
        membership2.setRole(NamespaceMembership.ROLE_OWNER);
        Mockito.when(keycloak.findUserIdByUserName("other_user")).thenReturn(userId2);
        Mockito.when(repositories.findMembership(userId2, namespace))
                .thenReturn(membership2);

        mockMvc.perform(post("/user/namespace/{namespace}/role?userName={userName}&role={role}", "foobar",
                    "other_user", "remove"))
                .andExpect(status().isOk())
                .andExpect(content().json(successJson("Removed other_user from namespace foobar.")));
    }

    @Test
    @WithMockKeycloakAuth(authorities = { "ROLE_USER" }, claims = @OpenIdClaims(sub = "325a2930-bb11-11ec-8422-0242ac120002"))
    public void testAddNamespaceMemberNotOwner() throws Exception {
        var userId = "325a2930-bb11-11ec-8422-0242ac120002";
        var namespace = new Namespace();
        namespace.setName("foobar");
        Mockito.when(repositories.findNamespace("foobar"))
                .thenReturn(namespace);
        var membership = new NamespaceMembership();
        membership.setUserId(userId);
        membership.setNamespace(namespace);
        membership.setRole(NamespaceMembership.ROLE_CONTRIBUTOR);
        Mockito.when(repositories.findMembership(userId, namespace))
                .thenReturn(membership);

        mockMvc.perform(post("/user/namespace/{namespace}/role?userName={userName}&role={role}", "foobar",
                    "other_user", "contributor"))
                .andExpect(status().isBadRequest())
                .andExpect(content().json(errorJson("You must be an owner of this namespace.")));
    }

    @Test
    @WithMockKeycloakAuth(authorities = { "ROLE_USER" }, claims = @OpenIdClaims(sub = "325a2930-bb11-11ec-8422-0242ac120002"))
    public void testChangeNamespaceMemberSameRole() throws Exception {
        var userId1 = "325a2930-bb11-11ec-8422-0242ac120002";
        var namespace = new Namespace();
        namespace.setName("foobar");
        Mockito.when(repositories.findNamespace("foobar"))
                .thenReturn(namespace);
        var membership1 = new NamespaceMembership();
        membership1.setUserId(userId1);
        membership1.setNamespace(namespace);
        membership1.setRole(NamespaceMembership.ROLE_OWNER);
        Mockito.when(repositories.findMembership(userId1, namespace))
                .thenReturn(membership1);

        var userId2 = "206b5986-bb0e-11ec-8422-0242ac120002";
        var membership2 = new NamespaceMembership();
        membership2.setUserId(userId2);
        membership2.setNamespace(namespace);
        membership2.setRole(NamespaceMembership.ROLE_CONTRIBUTOR);
        Mockito.when(keycloak.findUserIdByUserName("other_user")).thenReturn(userId2);
        Mockito.when(repositories.findMembership(userId2, namespace))
                .thenReturn(membership2);

        mockMvc.perform(post("/user/namespace/{namespace}/role?userName={userName}&role={role}", "foobar",
                    "other_user", "contributor"))
                .andExpect(status().isBadRequest())
                .andExpect(content().json(errorJson("User other_user already has the role contributor.")));
    }


    //---------- UTILITY ----------//
    private void mockAccessTokens() {
        var userId = "325a2930-bb11-11ec-8422-0242ac120002";
        var token1 = new PersonalAccessToken();
        token1.setUserId(userId);
        token1.setValue("token1");
        token1.setDescription("This is token 1");
        token1.setCreatedTimestamp(LocalDateTime.parse("2000-01-01T10:00"));
        token1.setActive(true);
        var token2 = new PersonalAccessToken();
        token2.setUserId(userId);
        token2.setValue("token2");
        token2.setDescription("This is token 2");
        token2.setCreatedTimestamp(LocalDateTime.parse("2000-01-01T10:00"));
        token2.setActive(false);
        var token3 = new PersonalAccessToken();
        token3.setUserId(userId);
        token3.setValue("token3");
        token3.setDescription("This is token 3");
        token3.setCreatedTimestamp(LocalDateTime.parse("2000-01-01T10:00"));
        token3.setActive(true);
        Mockito.when(repositories.findAccessTokens(userId))
                .thenReturn(Streamable.of(token1, token2, token3));
    }

    private String accessTokenJson(Consumer<AccessTokenJson> content) throws JsonProcessingException {
        var json = new AccessTokenJson();
        content.accept(json);
        return new ObjectMapper().writeValueAsString(json);
    }

    private String accessTokensJson(Consumer<List<AccessTokenJson>> content) throws JsonProcessingException {
        var json = new ArrayList<AccessTokenJson>();
        content.accept(json);
        return new ObjectMapper().writeValueAsString(json);
    }

    private void mockOwnMemberships() {
        var userId = "325a2930-bb11-11ec-8422-0242ac120002";
        var namespace1 = new Namespace();
        namespace1.setName("foo");
        Mockito.when(repositories.findActiveExtensions(namespace1)).thenReturn(Streamable.empty());
        var membership1 = new NamespaceMembership();
        membership1.setUserId(userId);
        membership1.setNamespace(namespace1);
        membership1.setRole(NamespaceMembership.ROLE_OWNER);
        var namespace2 = new Namespace();
        namespace2.setName("bar");
        Mockito.when(repositories.findActiveExtensions(namespace2)).thenReturn(Streamable.empty());
        var membership2 = new NamespaceMembership();
        membership2.setUserId(userId);
        membership2.setNamespace(namespace2);
        membership2.setRole(NamespaceMembership.ROLE_OWNER);
        Mockito.when(repositories.findMemberships(userId, NamespaceMembership.ROLE_OWNER))
                .thenReturn(Streamable.of(membership1, membership2));
    }

    private String namespacesJson(Consumer<List<NamespaceJson>> content) throws JsonProcessingException {
        var json = new ArrayList<NamespaceJson>();
        content.accept(json);
        return new ObjectMapper().writeValueAsString(json);
    }

    private void mockNamespaceMemberships(String userRole) {
        var userId = "325a2930-bb11-11ec-8422-0242ac120002";
        var userName = "test_user";
        var namespace = new Namespace();
        namespace.setName("foobar");
        Mockito.when(repositories.findNamespace("foobar"))
                .thenReturn(namespace);
        var membership1 = new NamespaceMembership();
        membership1.setUserId(userId);
        membership1.setNamespace(namespace);
        membership1.setRole(userRole);
        Mockito.when(repositories.findMembership(userId, namespace))
                .thenReturn(membership1);

        var userId2 = "206b5986-bb0e-11ec-8422-0242ac120002";
        var userName2 = "other_user";
        var membership2 = new NamespaceMembership();
        membership2.setUserId(userId2);
        membership2.setNamespace(namespace);
        membership2.setRole(NamespaceMembership.ROLE_CONTRIBUTOR);
        Mockito.when(repositories.findMemberships(namespace))
                .thenReturn(Streamable.of(membership1, membership2));

        Mockito.doAnswer(invocation -> {
            var users = Map.of(
                    userId, userName,
                    userId2, userName2
            );

            var list = invocation.getArgument(0, NamespaceMembershipListJson.class);
            for(var membership : list.namespaceMemberships) {
                membership.user.userName = users.get(membership.user.userId);
            }
            return list;
        }).when(keycloak).enrichUserJsons(any(NamespaceMembershipListJson.class));
    }

    private String membershipsJson(Consumer<NamespaceMembershipListJson> content) throws JsonProcessingException {
        var json = new NamespaceMembershipListJson();
        json.namespaceMemberships = new ArrayList<>();
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
        JsonService jsonService() {
            return new JsonService();
        }
    }
}