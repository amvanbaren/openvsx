/********************************************************************************
 * Copyright (c) 2020 TypeFox and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/
package org.eclipse.openvsx.security;

import com.google.common.base.Strings;

import org.keycloak.adapters.KeycloakConfigResolver;
import org.keycloak.adapters.springboot.KeycloakSpringBootConfigResolver;
import org.keycloak.adapters.springsecurity.KeycloakConfiguration;
import org.keycloak.adapters.springsecurity.config.KeycloakWebSecurityConfigurerAdapter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.builders.WebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.mapping.SimpleAuthorityMapper;
import org.springframework.security.web.authentication.Http403ForbiddenEntryPoint;
import org.springframework.security.web.authentication.session.NullAuthenticatedSessionStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;

@KeycloakConfiguration
@Import(KeycloakSpringBootConfigResolver.class)
public class SecurityConfig extends KeycloakWebSecurityConfigurerAdapter {

    @Value("${ovsx.webui.url:}")
    String webuiUrl;

    @Value("${ovsx.webui.frontendRoutes:/extension/**,/user-settings/**,/admin-dashboard/**}")
    String[] frontendRoutes;

    @Autowired
    public void configureGlobal(AuthenticationManagerBuilder auth) throws Exception {
        var mapper = new SimpleAuthorityMapper();
        mapper.setConvertToUpperCase(true);

        var keycloakAuthenticationProvider = keycloakAuthenticationProvider();
        keycloakAuthenticationProvider.setGrantedAuthoritiesMapper(mapper);
        auth.authenticationProvider(keycloakAuthenticationProvider);
    }

    @Bean
    @Override
    protected SessionAuthenticationStrategy sessionAuthenticationStrategy() {
        return new NullAuthenticatedSessionStrategy();
    }

    @Bean
    public KeycloakConfigResolver KeycloakConfigResolver() {
        return new KeycloakSpringBootConfigResolver();
    }

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        super.configure(http);
        var redirectUrl = Strings.isNullOrEmpty(webuiUrl) ? "/" : webuiUrl;

        http
            .sessionManagement()
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                .and()
            .csrf().disable()
            .authorizeRequests()
                .antMatchers("/*", "/actuator/health/**")
                    .permitAll()
                .antMatchers("/api/*/*/review", "/api/*/*/review/delete")
                    .hasAuthority("ROLE_USER")
                .antMatchers("/api/**", "/vscode/**", "/documents/**", "/admin/report")
                    .permitAll()
                .antMatchers("/admin/**")
                    .hasAuthority("ROLE_ADMIN")
                .antMatchers(frontendRoutes)
                    .permitAll()
                .anyRequest()
                    .hasAuthority("ROLE_USER")
                .and()
            .cors()
                .and()
            .exceptionHandling()
                // Respond with 403 status when the user is not logged in
                .authenticationEntryPoint(new Http403ForbiddenEntryPoint())
                .and()
            .logout()
                .logoutSuccessUrl(redirectUrl);
    }

    @Override
    public void configure(WebSecurity web) throws Exception {
        // Ignore resources required by Swagger API documentation
        web.ignoring().antMatchers("/v2/api-docs", "/swagger-resources/**", "/swagger-ui/**", "/webjars/**");
    }
}