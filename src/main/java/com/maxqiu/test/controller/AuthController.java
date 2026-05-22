package com.maxqiu.test.controller;

import com.maxqiu.test.entity.SysUser;
import com.maxqiu.test.service.SysUserService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final SysUserService sysUserService;
    private final PasswordEncoder passwordEncoder;

    public AuthController(SysUserService sysUserService, PasswordEncoder passwordEncoder) {
        this.sysUserService = sysUserService;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping("/me")
    public Map<String, Object> me(Authentication authentication) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (authentication == null) {
            result.put("authenticated", false);
            return result;
        }
        result.put("authenticated", authentication.isAuthenticated());
        result.put("username", authentication.getName());
        result.put("authorities", authentication.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority).toList());
        return result;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody SysUser sysUser) {
        sysUser.setPassword(passwordEncoder.encode(sysUser.getPassword()));
        if (sysUser.getRole() == null) {
            sysUser.setRole("USER");
        }
        try {
            sysUserService.save(sysUser);
            return ResponseEntity.ok(sysUser);
        } catch (DuplicateKeyException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", "username already exists", "username", sysUser.getUsername()));
        }
    }

    @GetMapping("/roles")
    public List<String> currentRoles(Authentication authentication) {
        if (authentication == null) {
            return List.of();
        }
        return authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
    }
}
