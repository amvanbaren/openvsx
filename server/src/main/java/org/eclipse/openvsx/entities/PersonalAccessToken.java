/********************************************************************************
 * Copyright (c) 2020 TypeFox and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/
package org.eclipse.openvsx.entities;

import java.time.LocalDateTime;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;

@Entity
@Table(uniqueConstraints = { @UniqueConstraint(columnNames = "value") })
public class PersonalAccessToken {

    @Id
    @GeneratedValue
    long id;

    String userId;

    @Column(length = 64)
    String value;

    boolean active;

    LocalDateTime createdTimestamp;

    LocalDateTime accessedTimestamp;

    @Column(length = 2048)
    String description;

    public long getId() {
        return id;
    }

    public void setId(long id) {
		this.id = id;
	}

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public LocalDateTime getCreatedTimestamp() {
        return createdTimestamp;
    }

    public void setCreatedTimestamp(LocalDateTime timestamp) {
        this.createdTimestamp = timestamp;
    }

    public LocalDateTime getAccessedTimestamp() {
        return accessedTimestamp;
    }

    public void setAccessedTimestamp(LocalDateTime timestamp) {
        this.accessedTimestamp = timestamp;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

}