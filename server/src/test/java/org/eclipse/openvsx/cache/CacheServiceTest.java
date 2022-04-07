/** ******************************************************************************
 * Copyright (c) 2022 Precies. Software and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 * ****************************************************************************** */
package org.eclipse.openvsx.cache;

import org.eclipse.openvsx.AdminService;
import org.eclipse.openvsx.ExtensionService;
import org.eclipse.openvsx.LocalRegistryService;
import org.eclipse.openvsx.TestService;
import org.eclipse.openvsx.entities.*;
import org.eclipse.openvsx.json.ExtensionJson;
import org.eclipse.openvsx.json.ReviewJson;
import org.eclipse.openvsx.util.TimeUtil;
import org.junit.After;
import org.junit.Before;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.testcontainers.containers.GenericContainer;

import javax.persistence.EntityManager;
import javax.transaction.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Collections;

import static org.eclipse.openvsx.cache.CacheService.CACHE_EXTENSION_JSON;
import static org.eclipse.openvsx.entities.FileResource.DOWNLOAD;
import static org.eclipse.openvsx.entities.FileResource.STORAGE_DB;
import static org.junit.Assert.*;
import static org.junit.jupiter.api.Assertions.assertNull;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public class CacheServiceTest {

    @Autowired
    CacheManager cache;

    @Autowired
    AdminService admins;

    @Autowired
    ExtensionService extensions;

    @Autowired
    LocalRegistryService registry;

    @Autowired
    EntityManager entityManager;

    @Autowired
    TestService testService;

    private GenericContainer keycloak;

    @Before
    public void beforeClass() throws IOException, InterruptedException {
        // TODO fix this
        keycloak = testService.initKeycloak();
    }

    @After
    public void afterClass() {
        keycloak.stop();
    }

    @Test
    @Transactional
    public void testGetExtension() {
        setRequest();
        var extVersion = insertExtensionVersion();
        var extension = extVersion.getExtension();
        var namespace = extension.getNamespace();
        var cacheKey = new ExtensionJsonCacheKeyGenerator().generate(namespace.getName(), extension.getName(),
                extVersion.getTargetPlatform(), extVersion.getVersion());

        var json = registry.getExtension(namespace.getName(), extension.getName(), extVersion.getTargetPlatform(), extVersion.getVersion());
        var cachedJson = cache.getCache(CACHE_EXTENSION_JSON).get(cacheKey, ExtensionJson.class);
        assertEquals(json, cachedJson);
    }

    @Test
    @Transactional
    public void testPostReview() {
        setRequest();
        var extVersion = insertExtensionVersion();
        var extension = extVersion.getExtension();
        var namespace = extension.getNamespace();
        var cacheKey = new ExtensionJsonCacheKeyGenerator().generate(namespace.getName(), extension.getName(),
                extVersion.getTargetPlatform(), extVersion.getVersion());

        var json = registry.getExtension(namespace.getName(), extension.getName(), extVersion.getTargetPlatform(), extVersion.getVersion());
        assertEquals(Long.valueOf(0), json.reviewCount);
        assertNull(json.averageRating);

        var review = new ReviewJson();
        review.rating = 3;
        review.comment = "Somewhat ok";
        review.timestamp = "2000-01-01T10:00Z";

        // TODO fix this
//        registry.postReview(review, namespace.getName(), extension.getName());
        assertNull(cache.getCache(CACHE_EXTENSION_JSON).get(cacheKey, ExtensionJson.class));

        json = registry.getExtension(namespace.getName(), extension.getName(), extVersion.getTargetPlatform(), extVersion.getVersion());
        assertEquals(Long.valueOf(1), json.reviewCount);
        assertEquals(Double.valueOf(3), json.averageRating);

        var cachedJson = cache.getCache(CACHE_EXTENSION_JSON).get(cacheKey, ExtensionJson.class);
        assertEquals(json, cachedJson);
    }

    @Test
    @Transactional
    public void testDeleteReview() {
        setRequest();
        var extVersion = insertExtensionVersion();
        var extension = extVersion.getExtension();
        var namespace = extension.getNamespace();
        var cacheKey = new ExtensionJsonCacheKeyGenerator().generate(namespace.getName(), extension.getName(),
                extVersion.getTargetPlatform(), extVersion.getVersion());

        var review = new ReviewJson();
        review.rating = 3;
        review.comment = "Somewhat ok";
        review.timestamp = "2000-01-01T10:00Z";

        // TODO fix this
//        registry.postReview(review, namespace.getName(), extension.getName());
        var json = registry.getExtension(namespace.getName(), extension.getName(), extVersion.getTargetPlatform(), extVersion.getVersion());
        assertEquals(Long.valueOf(1), json.reviewCount);
        assertEquals(Double.valueOf(3), json.averageRating);

        // TODO fix this
//        registry.deleteReview(namespace.getName(), extension.getName());
        assertNull(cache.getCache(CACHE_EXTENSION_JSON).get(cacheKey, ExtensionJson.class));

        json = registry.getExtension(namespace.getName(), extension.getName(), extVersion.getTargetPlatform(), extVersion.getVersion());
        assertEquals(Long.valueOf(0), json.reviewCount);
        assertNull(json.averageRating);

        var cachedJson = cache.getCache(CACHE_EXTENSION_JSON).get(cacheKey, ExtensionJson.class);
        assertEquals(json, cachedJson);
    }

    @Test
    @Transactional
    public void testDeleteExtension() {
        setRequest();
        var extVersion = insertExtensionVersion();
        var extension = extVersion.getExtension();
        var namespace = extension.getNamespace();
        var cacheKey = new ExtensionJsonCacheKeyGenerator().generate(namespace.getName(), extension.getName(),
                extVersion.getTargetPlatform(), extVersion.getVersion());

        registry.getExtension(namespace.getName(), extension.getName(), extVersion.getTargetPlatform(), extVersion.getVersion());

        admins.deleteExtension(namespace.getName(), extension.getName(), "admin_user");
        assertNull(cache.getCache(CACHE_EXTENSION_JSON).get(cacheKey, ExtensionJson.class));
    }

    @Test
    @Transactional
    public void testDeleteExtensionVersion() {
        setRequest();
        var extVersion = insertExtensionVersion();
        var extension = extVersion.getExtension();
        var namespace = extension.getNamespace();
        var cacheKey = new ExtensionJsonCacheKeyGenerator().generate(namespace.getName(), extension.getName(),
                extVersion.getTargetPlatform(), extVersion.getVersion());

        var newVersion = "0.2.0";
        var oldVersion = extVersion.getVersion();
        insertNewVersion(extension, extVersion.getPublishedWith(), newVersion);

        var json = registry.getExtension(namespace.getName(), extension.getName(), extVersion.getTargetPlatform(), newVersion);
        assertTrue(json.allVersions.containsKey(newVersion));
        assertTrue(json.allVersions.containsKey(oldVersion));

        admins.deleteExtension(namespace.getName(), extension.getName(), extVersion.getTargetPlatform(), newVersion, "admin_user");
        assertNull(cache.getCache(CACHE_EXTENSION_JSON).get(cacheKey, ExtensionJson.class));

        json = registry.getExtension(namespace.getName(), extension.getName(), extVersion.getTargetPlatform(), extVersion.getVersion());
        assertFalse(json.allVersions.containsKey(newVersion));
        assertTrue(json.allVersions.containsKey(oldVersion));

        var cachedJson = cache.getCache(CACHE_EXTENSION_JSON).get(cacheKey, ExtensionJson.class);
        assertEquals(json, cachedJson);
    }

    @Test
    @Transactional
    public void testUpdateExtension() {
        setRequest();
        var extVersion = insertExtensionVersion();
        var extension = extVersion.getExtension();
        var namespace = extension.getNamespace();
        var cacheKey = new ExtensionJsonCacheKeyGenerator().generate(namespace.getName(), extension.getName(),
                extVersion.getTargetPlatform(), extVersion.getVersion());

        registry.getExtension(namespace.getName(), extension.getName(), extVersion.getTargetPlatform(), extVersion.getVersion());

        var newVersion = "0.2.0";
        var oldVersion = extVersion.getVersion();
        var newExtVersion = insertNewVersion(extension, extVersion.getPublishedWith(), newVersion);
        newExtVersion.setPreRelease(true);
        extensions.updateExtension(extension);
        assertNull(cache.getCache(CACHE_EXTENSION_JSON).get(cacheKey, ExtensionJson.class));

        var json = registry.getExtension(namespace.getName(), extension.getName(), extVersion.getTargetPlatform(), oldVersion);
        assertTrue(json.allVersions.containsKey(oldVersion));
        assertTrue(json.allVersions.containsKey(newVersion));
        assertTrue(json.allVersions.containsKey("latest"));
        assertTrue(json.allVersions.containsKey("pre-release"));

        var cachedJson = cache.getCache(CACHE_EXTENSION_JSON).get(cacheKey, ExtensionJson.class);
        assertEquals(json, cachedJson);
    }

    private void setRequest() {
        // UrlUtil.getBaseUrl needs request
        var request = new MockHttpServletRequest();
        request.setScheme("https");
        request.setServerName("open-vsx.org");
        request.setServerPort(8080);
        request.setContextPath("/openvsx-server");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    private ExtensionVersion insertNewVersion(Extension extension, PersonalAccessToken token, String newVersion) {
        var extVersion = new ExtensionVersion();
        extVersion.setPreview(false);
        extVersion.setActive(true);
        extVersion.setVersion(newVersion);
        extVersion.setTargetPlatform("universal");
        extVersion.setDisplayName("baz");
        extVersion.setDescription("foo.bar baz");
        extVersion.setTimestamp(TimeUtil.getCurrentUTC());
        extVersion.setCategories(Collections.emptyList());
        extVersion.setTags(Collections.emptyList());
        extVersion.setExtension(extension);
        extVersion.setPublishedWith(token);
        entityManager.persist(extVersion);

        // populate extension versions list
        entityManager.flush();
        entityManager.refresh(extension);

        var download = new FileResource();
        download.setExtension(extVersion);
        download.setName("foo.bar-" + newVersion + ".vsix");
        download.setType(DOWNLOAD);
        download.setStorageType(STORAGE_DB);
        download.setContent("VSIX Package".getBytes(StandardCharsets.UTF_8));
        entityManager.persist(download);

        return extVersion;
    }

    private ExtensionVersion insertExtensionVersion() {
        return insertExtensionVersion("0.1.0");
    }

    private ExtensionVersion insertExtensionVersion(String version) {
        var namespace = new Namespace();
        namespace.setName("foo");
        namespace.setPublicId("12823789-189273189-1721983");
        entityManager.persist(namespace);

        var extension = new Extension();
        extension.setActive(true);
        extension.setName("bar");
        extension.setDownloadCount(0);
        extension.setNamespace(namespace);
        entityManager.persist(extension);

        var token = new PersonalAccessToken();
        token.setUserId("test_user");
        token.setValue("lkasdjfdklas-daskjfdaksl-kasdljfaksl");
        token.setActive(true);
        token.setDescription("test token");
        token.setCreatedTimestamp(LocalDateTime.now());
        token.setAccessedTimestamp(LocalDateTime.now());
        entityManager.persist(token);

        var extVersion = new ExtensionVersion();
        extVersion.setPreview(false);
        extVersion.setActive(true);
        extVersion.setVersion(version);
        extVersion.setTargetPlatform("universal");
        extVersion.setDisplayName("baz");
        extVersion.setDescription("foo.bar baz");
        extVersion.setTimestamp(TimeUtil.getCurrentUTC());
        extVersion.setCategories(Collections.emptyList());
        extVersion.setTags(Collections.emptyList());
        extVersion.setExtension(extension);
        extVersion.setPublishedWith(token);
        entityManager.persist(extVersion);

        // populate extension versions list
        entityManager.flush();
        entityManager.refresh(extension);

        var download = new FileResource();
        download.setExtension(extVersion);
        download.setName("foo.bar-" + version + ".vsix");
        download.setType(DOWNLOAD);
        download.setStorageType(STORAGE_DB);
        download.setContent("VSIX Package".getBytes(StandardCharsets.UTF_8));
        entityManager.persist(download);

        return extVersion;
    }
}
