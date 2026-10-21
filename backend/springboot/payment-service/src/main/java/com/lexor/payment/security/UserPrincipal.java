package com.lexor.payment.security;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPrincipal implements UserDetails {

    private Long id;
    private String uuid;
    private String email;
    @JsonIgnore
    private String password;
    private String role;
    private Collection<? extends GrantedAuthority> authorities;

    public static UserPrincipal fromClaims(String uuid, Long userId, String email, String role) {
        String normalizedRole = (role != null && role.startsWith("ROLE_")) ? role : "ROLE_" + (role != null ? role : "RIDER");
        List<GrantedAuthority> authorities = Collections.singletonList(new SimpleGrantedAuthority(normalizedRole));

        return UserPrincipal.builder()
                .id(userId)
                .uuid(uuid)
                .email(email)
                .role(normalizedRole)
                .authorities(authorities)
                .build();
    }

    @Override
    public String getUsername() {
        return email != null ? email : uuid;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
