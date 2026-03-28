package com.example.demostracion.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.password.NoOpPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

import com.example.demostracion.service.UsuarioDetailsService;

@Configuration
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    private final UsuarioDetailsService userDetailsService;

    public SecurityConfig(UsuarioDetailsService userDetailsService) {
        this.userDetailsService = userDetailsService;
    }
    

    @Bean
    public DaoAuthenticationProvider authProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder()); // ⚠️ NoOp solo pruebas
        return authProvider;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return NoOpPasswordEncoder.getInstance(); // ⚠️ Usa BCrypt en producción
    }

    // 👇 Aquí decides a dónde va cada rol
    @Bean
    public AuthenticationSuccessHandler customSuccessHandler() {
        return (request, response, authentication) -> {
            boolean isAdmin = authentication.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
            boolean isGerente = authentication.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_GERENTE"));
                boolean isVendedor = authentication.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_VENDEDOR"));
            boolean isConductor = authentication.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_CONDUCTOR"));
                    

            if (isAdmin) {
                response.sendRedirect("/dashboard");
            } else if (isGerente) {
                response.sendRedirect("/gerente");
            } else if (isVendedor) {
                response.sendRedirect("/vendedor/panel");
            } else if (isConductor) {
                response.sendRedirect("/vendedor/panel");
            } else {
                response.sendRedirect("/"); // por defecto
            }
        };
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.authenticationProvider(authProvider());

        http
            .authorizeHttpRequests(auth -> auth
                // Recursos públicos
                .requestMatchers("/", "/index", "/css/**", "/images/**", "/js/**", "/login", "/error/**", "/imagenes/**").permitAll()
                .requestMatchers("/setup/**", "/debug/**", "/usuarios-test/**", "/info/**", "/db-debug/**", "/auth-debug/**", "/email-test/**", "/inbox-debug/**").permitAll()
                
                // Rutas específicas de ADMIN
                .requestMatchers("/dashboard/**", "/admin/**").hasAnyRole("ADMIN", "GERENTE")
                .requestMatchers("/usuarios/**", "/roles/**").hasAnyRole("ADMIN", "GERENTE")
                
                // Rutas específicas de GERENTE
                .requestMatchers("/gerente/**").hasRole("GERENTE")
                .requestMatchers("/clima", "/consultar").hasRole("GERENTE")
                
                // Rutas específicas de CONDUCTOR
                .requestMatchers("/conductor/**").hasRole("CONDUCTOR")

                // Rutas específicas de VENDEDOR
                .requestMatchers("/vendedor/**").hasAnyRole("VENDEDOR", "CONDUCTOR")
                
                // Rutas compartidas entre ADMIN y GERENTE
                .requestMatchers("/novedades/**").hasAnyRole("ADMIN", "GERENTE")
                
                // Inventario y vehículos - solo para roles autorizados
                .requestMatchers("/inventario/**").hasAnyRole("ADMIN", "GERENTE")
                .requestMatchers("/vehiculos/**").hasAnyRole("ADMIN", "GERENTE")
                
                // Email - solo ADMIN
                .requestMatchers("/email/**").hasRole("ADMIN")

                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .loginProcessingUrl("/login")
                .successHandler(customSuccessHandler()) // 👈 aquí usamos el handler
                .failureUrl("/login?error")
                .permitAll()
            )
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/login?logout")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID")
                .permitAll()
            );

        return http.build();
    }
}
