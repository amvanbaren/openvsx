package org.eclipse.social;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jboss.logging.Logger;
import org.keycloak.broker.oidc.AbstractOAuth2IdentityProvider;
import org.keycloak.broker.oidc.OAuth2IdentityProviderConfig;
import org.keycloak.broker.oidc.mappers.AbstractJsonUserAttributeMapper;
import org.keycloak.broker.provider.BrokeredIdentityContext;
import org.keycloak.broker.provider.IdentityBrokerException;
import org.keycloak.broker.provider.util.SimpleHttp;
import org.keycloak.broker.social.SocialIdentityProvider;
import org.keycloak.events.EventBuilder;
import org.keycloak.models.KeycloakSession;

public class EclipseIdentityProvider extends AbstractOAuth2IdentityProvider implements SocialIdentityProvider {

    private static final Logger LOG = Logger.getLogger(EclipseIdentityProvider.class);

    public static final String AUTH_URL = "https://accounts.eclipse.org/oauth2/authorize";
    public static final String TOKEN_URL = "https://accounts.eclipse.org/oauth2/token";
    public static final String PROFILE_URL = "https://accounts.eclipse.org/oauth2/UserInfo";
    public static final String DEFAULT_SCOPE = "openvsx_publisher_agreement profile";

    public EclipseIdentityProvider(KeycloakSession session, OAuth2IdentityProviderConfig config) {
        super(session, config);
        config.setAuthorizationUrl(AUTH_URL);
        config.setTokenUrl(TOKEN_URL);
        config.setUserInfoUrl(PROFILE_URL);
    }

    @Override
    protected boolean supportsExternalExchange() {
        return true;
    }

    @Override
    protected String getProfileEndpointForValidation(EventBuilder event) {
        return PROFILE_URL;
    }

    @Override
    protected BrokeredIdentityContext extractIdentityFromProfile(EventBuilder event, JsonNode profile) {
        LOG.info("profile: " + profile.toPrettyString());
        String userId = getJsonProperty(profile, "user_id");
        BrokeredIdentityContext user = new BrokeredIdentityContext(userId);

        user.setUsername(userId);
        user.setEmail(getJsonProperty(profile, "email"));
        user.setName(getJsonProperty(profile, "name"));
        user.setIdpConfig(getConfig());
        user.setIdp(this);

        AbstractJsonUserAttributeMapper.storeUserProfileForMapper(user, profile, getConfig().getAlias());

        return user;
    }


    @Override
    protected BrokeredIdentityContext doGetFederatedIdentity(String accessToken) {
        LOG.info("accessToken: " + accessToken);
        LOG.info("PROFILE_URL: " + PROFILE_URL);
        try {
//            JsonNode profile = SimpleHttp.doGet(PROFILE_URL, session)
//                    .header("Authorization", "Bearer " + accessToken)
//                    .asJson();

            String profileString = SimpleHttp.doGet(PROFILE_URL, session)
                    .header("Authorization", "Bearer " + accessToken)
                    .asString();

            LOG.info("profile: " + profileString);
            JsonNode profile = new ObjectMapper().readTree(profileString);

            return extractIdentityFromProfile(null, profile);
        } catch (Exception e) {
            throw new IdentityBrokerException("Could not obtain user profile from eclipse.", e);
        }
    }

    @Override
    protected String getDefaultScopes() {
        return DEFAULT_SCOPE;
    }
}
