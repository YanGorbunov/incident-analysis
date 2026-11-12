package io.github.yangorbunov.incidents.security;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class AppUserDetailsService implements UserDetailsService {

    private final AppUserRepository users;
    private final Clock clock;

    public AppUserDetailsService(AppUserRepository users, Clock clock) {
        this.users = users;
        this.clock = clock;
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        var user = users.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException(username));
        boolean locked = user.lockedUntil() != null && user.lockedUntil().isAfter(OffsetDateTime.now(clock));
        return User.withUsername(user.username())
                .password(user.passwordHash())
                .authorities(List.of(new SimpleGrantedAuthority(user.role().authority())))
                .disabled(!user.enabled())
                .accountLocked(locked)
                .build();
    }
}
