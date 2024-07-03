package com.aoldacraft.minecraftkubernetesstack.domain.manager.entities;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.util.Assert;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Document(collection = "managers")
@Data
public class Manager implements UserDetails {

    @Id
    private String uuid;
    private String email;
    private String firstName;
    private String lastName;
    private String password;
    private String workspace = "default";
    private boolean enabled;
    private Set<String> roles;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        List<GrantedAuthority> authorities = new ArrayList<>(roles.size());
        roles.forEach(role -> {
            if(role.startsWith("ROLE_")) {
                authorities.add(new SimpleGrantedAuthority(role));
            }
            else {
                authorities.add(new SimpleGrantedAuthority("ROLE_" + role));

            }
        });
        return authorities;
    }


    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return isEnabled();
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }
}
