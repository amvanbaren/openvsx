package org.eclipse.social;

import org.keycloak.broker.oidc.OAuth2IdentityProviderConfig;
import org.keycloak.broker.provider.AbstractIdentityProviderFactory;
import org.keycloak.broker.social.SocialIdentityProviderFactory;
import org.keycloak.models.IdentityProviderModel;
import org.keycloak.models.KeycloakSession;

public class EclipseIdentityProviderFactory extends AbstractIdentityProviderFactory<EclipseIdentityProvider> implements SocialIdentityProviderFactory<EclipseIdentityProvider> {

    public static final String PROVIDER_ID = "eclipse";

    public EclipseIdentityProviderFactory(){}

    @Override
    public String getName() {
        return "Eclipse";
    }

    @Override
    public EclipseIdentityProvider create(KeycloakSession session, IdentityProviderModel model) {
        return new EclipseIdentityProvider(session, new OAuth2IdentityProviderConfig(model));
    }

    @Override
    public OAuth2IdentityProviderConfig createConfig() {
        return new OAuth2IdentityProviderConfig();
    }

    @Override
    public String getId() {
        return PROVIDER_ID;
    }
}
