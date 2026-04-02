package com.sentinelx.auth.security;

import com.sentinelx.user.entity.User;
import com.sentinelx.user.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String usernameOrEmail) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(usernameOrEmail)
            .or(() -> userRepository.findAll().stream()
                .filter(candidate -> usernameOrEmail.equalsIgnoreCase(candidate.getEmail()))
                .findFirst())
            .orElseThrow(() -> new UsernameNotFoundException("User not found: " + usernameOrEmail));

            // Force role initialization before leaving repository context.
            user.getRole().getName();

        return new CustomUserDetails(user);
    }
}
