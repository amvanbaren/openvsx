package org.eclipse.social;

import org.keycloak.broker.oidc.mappers.AbstractJsonUserAttributeMapper;

public class EclipseUserAttributeMapper extends AbstractJsonUserAttributeMapper {

    public static final String PROVIDER_ID = "eclipse-user-attribute-mapper";
    private static final String[] cp = new String[] { EclipseIdentityProviderFactory.PROVIDER_ID };

    @Override
    public String[] getCompatibleProviders() {
        return cp;
    }

    @Override
    public String getId() {
        return PROVIDER_ID;
    }
}
