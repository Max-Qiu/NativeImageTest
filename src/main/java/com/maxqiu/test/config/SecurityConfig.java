package com.maxqiu.test.config;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.maxqiu.test.entity.SysUser;
import com.maxqiu.test.mapper.SysUserMapper;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public UserDetailsService userDetailsService(SysUserMapper sysUserMapper) {
        return username -> {
            SysUser user = sysUserMapper.selectOne(
                Wrappers.<SysUser>lambdaQuery().eq(SysUser::getUsername, username));
            if (user == null) {
                throw new UsernameNotFoundException("user not found: " + username);
            }
            String role = user.getRole() == null ? "USER" : user.getRole();
            return new org.springframework.security.core.userdetails.User(
                user.getUsername(), user.getPassword(),
                List.of(new SimpleGrantedAuthority("ROLE_" + role)));
        };
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable);
        http.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED));
        http.authorizeHttpRequests(auth -> auth
            .requestMatchers("/auth/login", "/auth/register", "/error").permitAll()
            .requestMatchers("/cache/**").hasRole("ADMIN")
            .anyRequest().authenticated());
        http.exceptionHandling(ex -> ex
            .authenticationEntryPoint((_, res, _) -> res.setStatus(401)));
        http.formLogin(form -> form
            .loginProcessingUrl("/auth/login")
            .successHandler((_, res, _) -> res.setStatus(200))
            .failureHandler((_, res, _) -> res.setStatus(401)));
        http.logout(logout -> logout
            .logoutUrl("/auth/logout")
            .logoutSuccessHandler((_, res, _) -> res.setStatus(200)));
        return http.build();
    }
}
