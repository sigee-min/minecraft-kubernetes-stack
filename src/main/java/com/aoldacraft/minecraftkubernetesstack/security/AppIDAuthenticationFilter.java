package com.aoldacraft.minecraftkubernetesstack.security;

import com.aoldacraft.minecraftkubernetesstack.domain.manager.AppIDRepository;
import com.aoldacraft.minecraftkubernetesstack.domain.manager.entities.AppID;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class AppIDAuthenticationFilter extends OncePerRequestFilter {

    private final AppIDRepository appIDRepository;
    private final UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws IOException, ServletException {
        String appId = request.getHeader("MKS_ACCESS_TOKEN");

        if (appId != null && !appId.isEmpty()) {
            Optional<AppID> appIDOptional = appIDRepository.findById(appId);
            if (appIDOptional.isPresent()) {
                AppID appID = appIDOptional.get();
                String username = appID.getEmail();

                var userDetails = userDetailsService.loadUserByUsername(username);
                if (userDetails != null) {
                    var authentication = new UsernamePasswordAuthenticationToken(
                            userDetails, null, userDetails.getAuthorities());
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            }
        }

        filterChain.doFilter(request, response);
    }
}
