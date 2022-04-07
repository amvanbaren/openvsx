/********************************************************************************
 * Copyright (c) 2019 TypeFox and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/

import {
    Extension, UserData, ExtensionCategory, ExtensionReviewList, PersonalAccessToken,
    SearchResult, NewReview, SuccessResult, ErrorResult, Namespace, MembershipRole, SortBy, SortOrder, UrlString, NamespaceMembershipList, PublisherInfo
} from './extension-registry-types';
import Keycloak from 'keycloak-js';
import { createAbsoluteURL, addQuery } from './utils';
import { sendRequest } from './server-request';

export class ExtensionRegistryService {

    readonly admin: AdminService;
    readonly keycloak: Keycloak.KeycloakInstance;

    constructor(readonly serverUrl: string = '', keycloak: Keycloak.KeycloakInstance, admin?: AdminService) {
        this.admin = admin ?? new AdminService(this);
        this.keycloak = keycloak;
    }

    initKeycloak(): Promise<boolean> {
        return this.keycloak.init({
            onLoad: 'check-sso',
            silentCheckSsoRedirectUri: window.location.origin + '/silent-check-sso.html'
        });
    }

    login(options?: Keycloak.KeycloakLoginOptions): Keycloak.KeycloakPromise<void, void> {
        if (!options) {
            options = { redirectUri: window.location.href };
        }

        return this.keycloak.login(options);
    }

    logout(): Keycloak.KeycloakPromise<void, void> {
        return this.keycloak.logout({ redirectUri: window.location.origin });
    }

    getExtensionApiUrl(ext: { namespace: string, name: string, target?: string, version?: string }): string {
        const arr = [this.serverUrl, 'api', ext.namespace, ext.name];
        if (ext.target) {
            arr.push(ext.target);
        }
        if (ext.version) {
            arr.push(ext.version);
        }

        return createAbsoluteURL(arr);
    }

    search(filter?: ExtensionFilter): Promise<Readonly<SearchResult | ErrorResult>> {
        const query: { key: string, value: string | number }[] = [];
        if (filter) {
            if (filter.query)
                query.push({ key: 'query', value: filter.query });
            if (filter.category)
                query.push({ key: 'category', value: filter.category });
            if (filter.offset)
                query.push({ key: 'offset', value: filter.offset });
            if (filter.size)
                query.push({ key: 'size', value: filter.size });
            if (filter.sortBy)
                query.push({ key: 'sortBy', value: filter.sortBy });
            if (filter.sortOrder)
                query.push({ key: 'sortOrder', value: filter.sortOrder });
        }
        const endpoint = createAbsoluteURL([this.serverUrl, 'api', '-', 'search'], query);
        return sendRequest({ endpoint });
    }

    getExtensionDetail(extensionUrl: UrlString): Promise<Readonly<Extension | ErrorResult>> {
        return sendRequest({ endpoint: extensionUrl });
    }

    getExtensionReadme(extension: Extension): Promise<string> {
        return sendRequest({
            endpoint: extension.files.readme!,
            headers: { 'Accept': 'text/plain' },
            followRedirect: true
        });
    }

    getExtensionChangelog(extension: Extension): Promise<string> {
        return sendRequest({
            endpoint: extension.files.changelog!,
            headers: { 'Accept': 'text/plain' },
            followRedirect: true
        });
    }

    getCategories(): ExtensionCategory[] {
        return [
            'Programming Languages',
            'Snippets',
            'Linters',
            'Themes',
            'Debuggers',
            'Formatters',
            'Keymaps',
            'SCM Providers',
            'Other',
            'Extension Packs',
            'Language Packs',
            'Data Science',
            'Machine Learning',
            'Visualization',
            'Notebooks'
        ];
    }

    getExtensionReviews(extension: Extension): Promise<Readonly<ExtensionReviewList>> {
        return sendRequest({ endpoint: extension.reviewsUrl });
    }

    async postReview(review: NewReview, postReviewUrl: UrlString): Promise<Readonly<SuccessResult | ErrorResult>> {
        const headers = await this.getAuthorizationHeader();
        headers['Content-Type'] = 'application/json;charset=UTF-8';
        return sendRequest({
            method: 'POST',
            payload: review,
            endpoint: postReviewUrl,
            headers
        });
    }

    async deleteReview(deleteReviewUrl: string): Promise<Readonly<SuccessResult | ErrorResult>> {
        const headers = await this.getAuthorizationHeader();
        return sendRequest({
            method: 'POST',
            endpoint: deleteReviewUrl,
            headers
        });
    }

    async getUser(): Promise<Readonly<UserData | undefined>> {
        if (!this.keycloak.authenticated) {
            return undefined;
        }

        const headers = await this.getAuthorizationHeader();
        return sendRequest({
            endpoint: createAbsoluteURL([this.serverUrl, 'user']),
            headers: headers
        });
    }

    async getUserActiveEclipseAccessToken(): Promise<Readonly<SuccessResult | ErrorResult>> {
        const headers = await this.getAuthorizationHeader();
        return sendRequest({
            endpoint: createAbsoluteURL([this.serverUrl, 'user', 'eclipse', 'active-access-token']),
            headers: headers
        });
    }

    async getUserByName(name: string): Promise<Readonly<UserData>[]> {
        const headers = await this.getAuthorizationHeader();
        return sendRequest({
            endpoint: createAbsoluteURL([this.serverUrl, 'user', 'search', name]),
            headers
        });
    }

    async getAccessTokens(): Promise<Readonly<PersonalAccessToken>[]> {
        const headers = await this.getAuthorizationHeader();
        return sendRequest({
            headers,
            endpoint: createAbsoluteURL([this.serverUrl, 'user', 'tokens']),
        });
    }

    async createAccessToken(user: UserData, description: string): Promise<Readonly<PersonalAccessToken>> {
        const headers = await this.getAuthorizationHeader();
        const endpoint = addQuery(createAbsoluteURL([this.serverUrl, 'user', 'token', 'create']), [{ key: 'description', value: description }]);
        return sendRequest({
            method: 'POST',
            endpoint,
            headers
        });
    }

    async deleteAccessToken(token: PersonalAccessToken): Promise<Readonly<SuccessResult | ErrorResult>> {
        const headers = await this.getAuthorizationHeader();
        return sendRequest({
            method: 'POST',
            endpoint: token.deleteTokenUrl,
            headers
        });
    }

    async deleteAllAccessTokens(tokens: PersonalAccessToken[]): Promise<Readonly<SuccessResult | ErrorResult>[]> {
        const headers = await this.getAuthorizationHeader();
        return await Promise.all(tokens.map(token => sendRequest<SuccessResult | ErrorResult>({
            method: 'POST',
            endpoint: token.deleteTokenUrl,
            headers
        })));
    }

    async getNamespaces(): Promise<Readonly<Namespace>[]> {
        const headers = await this.getAuthorizationHeader();
        return sendRequest({
            headers,
            endpoint: createAbsoluteURL([this.serverUrl, 'user', 'namespaces'])
        });
    }

    async getNamespaceMembers(namespace: Namespace): Promise<Readonly<NamespaceMembershipList>> {
        const headers = await this.getAuthorizationHeader();
        return sendRequest({
            headers,
            endpoint: namespace.membersUrl
        });
    }

    async setNamespaceMember(endpoint: UrlString, user: UserData, role: MembershipRole | 'remove'): Promise<Readonly<SuccessResult | ErrorResult>[]> {
        const headers = await this.getAuthorizationHeader();
        const query = [
            { key: 'userName', value: user.userName },
            { key: 'role', value: role }
        ];
        return sendRequest({
            headers,
            method: 'POST',
            endpoint: addQuery(endpoint, query)
        });
    }

    async signPublisherAgreement(): Promise<Readonly<UserData | ErrorResult>> {
        const headers = await this.getAuthorizationHeader();
        return sendRequest<UserData | ErrorResult>({
            method: 'POST',
            endpoint: createAbsoluteURL([this.serverUrl, 'user', 'publisher-agreement']),
            headers
        });
    }

    getStaticContent(url: string): Promise<string> {
        return sendRequest({
            endpoint: url,
            headers: { 'Accept': 'text/plain' },
            followRedirect: true
        });
    }

    async getAuthorizationHeader(): Promise<Record<string, string>> {
        return this.keycloak.updateToken(30).then(() => {
            return { 'Authorization': `Bearer ${this.keycloak.token}` };
        },
        async () => {
            return this.logout().then(() => {
                return {};
            });
        });
    }
}

export class AdminService {

    constructor(readonly registry: ExtensionRegistryService) { }

    async getExtension(namespace: string, extension: string): Promise<Readonly<Extension | ErrorResult>> {
        const headers = await this.registry.getAuthorizationHeader();
        return sendRequest({
            headers,
            endpoint: createAbsoluteURL([this.registry.serverUrl, 'admin', 'extension', namespace, extension])
        });
    }

    async deleteExtensions(req: { namespace: string, extension: string, targetPlatformVersions?: object[] }): Promise<Readonly<SuccessResult | ErrorResult>> {
        const headers = await this.registry.getAuthorizationHeader();
        headers['Content-Type'] = 'application/json;charset=UTF-8';
        return sendRequest({
            method: 'POST',
            endpoint: createAbsoluteURL([this.registry.serverUrl, 'admin', 'extension', req.namespace, req.extension, 'delete']),
            headers,
            payload: req.targetPlatformVersions
        });
    }

    async getNamespace(name: string): Promise<Readonly<Namespace>> {
        const headers = await this.registry.getAuthorizationHeader();
        return sendRequest({
            headers,
            endpoint: createAbsoluteURL([this.registry.serverUrl, 'admin', 'namespace', name])
        });
    }

    async createNamespace(namespace: { name: string }): Promise<Readonly<SuccessResult | ErrorResult>> {
        const headers = await this.registry.getAuthorizationHeader();
        headers['Content-Type'] = 'application/json;charset=UTF-8';
        return sendRequest({
            endpoint: createAbsoluteURL([this.registry.serverUrl, 'admin', 'create-namespace']),
            method: 'POST',
            payload: namespace,
            headers
        });
    }

    async getPublisherInfo(userName: string): Promise<Readonly<PublisherInfo>> {
        const headers = await this.registry.getAuthorizationHeader();
        return sendRequest({
            endpoint: createAbsoluteURL([this.registry.serverUrl, 'admin', 'publisher', userName]),
            headers
        });
    }

    async revokePublisherContributions(userName: string): Promise<Readonly<SuccessResult | ErrorResult>> {
        const headers = await this.registry.getAuthorizationHeader();
        return sendRequest({
            method: 'POST',
            endpoint: createAbsoluteURL([this.registry.serverUrl, 'admin', 'publisher', userName, 'revoke']),
            headers
        });
    }
}

export interface ExtensionFilter {
    query?: string;
    category?: ExtensionCategory | '';
    size?: number;
    offset?: number;
    sortBy?: SortBy;
    sortOrder?: SortOrder;
}
