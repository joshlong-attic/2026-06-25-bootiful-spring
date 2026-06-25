package com.example.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.security.authorization.AuthorizationManagerFactories;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authorization.EnableMultiFactorAuthentication;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.security.provisioning.JdbcUserDetailsManager;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.sql.DataSource;
import java.security.Principal;
import java.util.Map;

@EnableMultiFactorAuthentication(
        authorities = {}
//        authorities = {
//        FactorGrantedAuthority.PASSWORD_AUTHORITY,
//        FactorGrantedAuthority.OTT_AUTHORITY
// }
)
@SpringBootApplication
public class AuthApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthApplication.class, args);
    }

    @Bean
    JdbcUserDetailsManager userDetailsService(DataSource dataSource) {
        var u = new JdbcUserDetailsManager(dataSource);
        u.setEnableUpdatePassword(true);
        return u;
    }

    @Bean
    Customizer<HttpSecurity> httpSecurityCustomizer() {
//        var amf = AuthorizationManagerFactories
//                .multiFactor()
//                .requireFactors(FactorGrantedAuthority.PASSWORD_AUTHORITY, FactorGrantedAuthority.OTT_AUTHORITY)
//                .build();
        return http -> http
//                .authorizeHttpRequests(a -> a.requestMatchers("/admin").access(amf.authenticated()))
                .oauth2AuthorizationServer(a -> a.oidc(Customizer.withDefaults()))
                .webAuthn(a -> a
                        .allowedOrigins("http://localhost:9090/")
                        .rpId("localhost")
                        .rpName("bootiful")
                )
                .oneTimeTokenLogin(ott -> ott
                        .tokenGenerationSuccessHandler((_, response, oneTimeToken) -> {
                            response.getWriter().println("you've got console mail!");
                            response.setContentType(MediaType.TEXT_PLAIN_VALUE);
                            IO.println("please go to http://localhost:9090/login/ott?token=" + oneTimeToken.getTokenValue());
                        }));
    }

   /* @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity security) {
        return security
                .authorizeHttpRequests(a -> a.anyRequest().authenticated())
                .httpBasic(Customizer.withDefaults())
                .csrf(Customizer.withDefaults())
                .formLogin(Customizer.withDefaults())
                .build();
    }*/
}
/*
@Controller
@ResponseBody
class MeController {

    @GetMapping("/admin")
    Map<String, String> admin(Principal principal) {
        return Map.of("adminName", principal.getName());
    }

    @GetMapping("/")
    Map<String, String> me(Principal principal) {
        return Map.of("name", principal.getName());
    }
}

 */
// spring security 7
// > authentication
// > authroization