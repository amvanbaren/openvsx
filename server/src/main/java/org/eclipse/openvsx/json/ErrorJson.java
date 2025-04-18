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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

/**
 * Used to describe an error situation as JSON response.
 * @param message Error message
 * @param code Error code, see constants in {@link org.eclipse.openvsx.security.CodedAuthException}.
 */
@JsonInclude(Include.NON_NULL)
public record ErrorJson(String message, String code) {}