package org.eclipse.openvsx.util;

import org.keycloak.adapters.springsecurity.token.KeycloakAuthenticationToken;

import java.security.Principal;

public class UserUtil {

    private UserUtil(){}

    public static String getUserId(Principal principal) {
        return ((KeycloakAuthenticationToken) principal).getAccount()
                .getKeycloakSecurityContext().getToken().getSubject();
    }

    public static String getUserName(Principal principal) {
        return ((KeycloakAuthenticationToken) principal).getAccount()
                .getKeycloakSecurityContext().getToken().getPreferredUsername();
    }
}
