package org.eclipse.openvsx.util;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashSet;
import java.util.Set;

@Configuration
public class CreateAdminConfig {

    @Bean
    @ConfigurationProperties(prefix = "ovsx.admins.login-names")
    public Set<String> loginNames(){
        return new HashSet<>();
    }
}
