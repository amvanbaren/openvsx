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
    ExtensionCategory, SortBy, SortOrder
} from './extension-registry-types';
import { serverUrl } from './store/api';
import { createAbsoluteURL } from './utils';

export class ExtensionRegistryService {

    getLogoutUrl(): string {
        return createAbsoluteURL([serverUrl, 'logout']);
    }

    getExtensionApiUrl(ext: { namespace: string, name: string, target?: string, version?: string }): string {
        const arr = [serverUrl, 'api', ext.namespace, ext.name];
        if (ext.target) {
            arr.push(ext.target);
        }
        if (ext.version) {
            arr.push(ext.version);
        }

        return createAbsoluteURL(arr);
    }
}

export interface ExtensionFilter {
    query: string;
    category: ExtensionCategory | '';
    size: number;
    offset: number;
    sortBy: SortBy;
    sortOrder: SortOrder;
}
