package org.eclipse.openvsx.db.migration;

import org.eclipse.openvsx.entities.PublisherAgreement;
import org.elasticsearch.common.Strings;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.keycloak.OAuth2Constants;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.representations.idm.FederatedIdentityRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class V1_25__Keycloak_Authentication extends BaseJavaMigration {

    private static final Logger LOGGER = LoggerFactory.getLogger(V1_25__Keycloak_Authentication.class);

    private final String realm;
    private final Keycloak keycloak;

    @Autowired
    public V1_25__Keycloak_Authentication(
            @Value("${keycloak.auth-server-url}") String authServerUrl,
            @Value("${keycloak.realm}") String realm,
            @Value("${keycloak.resource}") String clientId,
            @Value("${keycloak.credentials.secret}") String clientSecret
    ) {
        this.realm = realm;
        keycloak = KeycloakBuilder.builder()
                .grantType(OAuth2Constants.CLIENT_CREDENTIALS)
                .serverUrl(authServerUrl)
                .realm(realm)
                .clientId(clientId)
                .clientSecret(clientSecret)
                .build();
    }

    @Override
    public void migrate(Context context) throws Exception {
        var connection = context.getConnection();
        var mappings = Map.of(
                "user_data", "id",
                "extension_review", "user_id",
                "namespace_membership", "user_data",
                "persisted_log", "user_data",
                "personal_access_token", "user_data"
        );
        for(var table : mappings.keySet()) {
            var query = "ALTER TABLE " + table + " ADD COLUMN ext_user_id CHARACTER VARYING(255)";
            LOGGER.info(query);
            execute(query, connection);
        }

        migrateUserDataToKeycloak(connection);
        updateUserIds(mappings, connection);

        migratePublisherAgreements(connection);
        execute("DROP TABLE user_data", connection);
    }

    private void migrateUserDataToKeycloak(Connection connection) throws SQLException {
        // TODO is it OK to only migrate github logins?
        // OR should you check which users have published an extension?
        // TODO make sure that user has to login first with github to activate the account
        var userIdUpdates = new HashMap<Long, String>();
        var query = "SELECT id, avatar_url, email, login_name, role, auth_id FROM user_data WHERE provider = 'github'";
        LOGGER.info(query);
        try(var statement = connection.prepareStatement(query)) {
            try(var result = statement.executeQuery()) {
                while(result.next()) {
                    var userRep = new UserRepresentation();
                    userRep.setEnabled(true);
                    userRep.setUsername(result.getString("login_name"));
                    userRep.setEmail(result.getString("email"));
                    userRep.setAttributes(Map.of("avatar_url", List.of(result.getString("avatar_url"))));
                    var role = result.getString("role");
                    if(!Strings.isNullOrEmpty(role)) {
                        userRep.setRealmRoles(List.of(role));
                    }

                    var githubIdentity = new FederatedIdentityRepresentation();
                    githubIdentity.setIdentityProvider("github");
                    githubIdentity.setUserId(result.getString("auth_id"));
                    githubIdentity.setUserName(userRep.getUsername());
                    userRep.setFederatedIdentities(List.of(githubIdentity));

                    var status = keycloak.realm(realm).users().create(userRep).getStatus();
                    if(status == HttpStatus.CREATED.value()) {
                        var createdUserRep = keycloak.realm(realm)
                                .users().search(userRep.getUsername(), true)
                                .stream()
                                .findFirst()
                                .orElse(null);

                        userIdUpdates.put(result.getLong("id"), createdUserRep.getId());
                    } else {
                        throw new RuntimeException("Failed to create user in Keycloak");
                    }
                }
            }
        }

        var update = "UPDATE user_data SET ext_user_id = ? WHERE id = ?";
        LOGGER.info(update);
        try(var statement = connection.prepareStatement(update)) {
            for(var entry : userIdUpdates.entrySet()) {
                statement.setString(1, entry.getValue());
                statement.setLong(2, entry.getKey());
                statement.executeUpdate();
            }
        }
    }

    private void updateUserIds(Map<String, String> mappings, Connection connection) throws SQLException {
        for(var mapping : mappings.entrySet()) {
            var table = mapping.getKey();
            var column = mapping.getValue();
            if(table.equals("user_data")) {
                continue;
            }

            var update = "UPDATE " + table +
                    " SET ext_user_id = user_data.ext_user_id" +
                    " FROM user_data" +
                    " WHERE " + table + "." + column + " = user_data.id";
            LOGGER.info(update);
            execute(update, connection);
            LOGGER.info("ALTER TABLE " + table + " DROP COLUMN " + column);
            execute("ALTER TABLE " + table + " DROP COLUMN " + column, connection);
            LOGGER.info("ALTER TABLE " + table + " RENAME COLUMN ext_user_id TO user_id");
            execute("ALTER TABLE " + table + " RENAME COLUMN ext_user_id TO user_id", connection);
        }
    }

    private void migratePublisherAgreements(Connection connection) throws SQLException {
        var query = "CREATE TABLE publisher_agreement(" +
                "id BIGINT PRIMARY KEY," +
                "user_id CHARACTER VARYING(255)," +
                "active BOOL NOT NULL," +
                "document_id CHARACTER VARYING(255)," +
                "version CHARACTER VARYING(255)," +
                "person_id CHARACTER VARYING(255)," +
                "timestamp TIMESTAMP WITHOUT TIME ZONE)";
        LOGGER.info(query);
        execute(query, connection);

        var converter = new EclipseDataConverter();
        var agreements = new ArrayList<PublisherAgreement>();
        LOGGER.info("SELECT user_id, eclipse_data FROM user_data");
        try(var statement = connection.prepareStatement("SELECT ext_user_id, eclipse_data FROM user_data WHERE eclipse_data IS NOT NULL")) {
            try(var result = statement.executeQuery()) {
                while(result.next()) {
                    var eclipseData = converter.convertToEntityAttribute(result.getString("eclipse_data"));
                    if(eclipseData.personId == null || eclipseData.publisherAgreement == null) {
                        continue;
                    }

                    var agreement = new PublisherAgreement();
                    agreement.setUserId(result.getString("ext_user_id"));
                    agreement.setActive(eclipseData.publisherAgreement.isActive);
                    agreement.setDocumentId(eclipseData.publisherAgreement.documentId);
                    agreement.setVersion(eclipseData.publisherAgreement.version);
                    agreement.setPersonId(eclipseData.personId);
                    agreement.setTimestamp(eclipseData.publisherAgreement.timestamp);
                    agreements.add(agreement);
                }
            }
        }

        var insert = "INSERT INTO publisher_agreement(id, user_id, active, document_id, version, person_id, timestamp)" +
                " VALUES(next_val(), ?, ?, ?, ?, ?, ?)";
        LOGGER.info(insert);
        try(var statement = connection.prepareStatement(insert)) {
            for(var agreement : agreements) {
                statement.setString(1, agreement.getUserId());
                statement.setBoolean(2, agreement.isActive());
                statement.setString(3, agreement.getDocumentId());
                statement.setString(4, agreement.getVersion());
                statement.setString(5, agreement.getPersonId());
                statement.setTimestamp(6, Timestamp.valueOf(agreement.getTimestamp()));
                statement.executeUpdate();
            }
        }
    }

    private void execute(String query, Connection connection) throws SQLException {
        try(var statement = connection.prepareStatement(query)) {
            statement.execute();
        }
    }
}
