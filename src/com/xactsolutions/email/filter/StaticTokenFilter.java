package com.xactsolutions.email.filter;

import com.sun.net.httpserver.HttpExchange;
import com.xactsolutions.email.exception.AuthenticationException;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
public class StaticTokenFilter extends AuthFilter {

    private final List<String> tokens;

    public StaticTokenFilter(List<String> tokens) {
        this.tokens = tokens;
    }

    @Override
    public void applyFilter(HttpExchange exchange) {
        List<String> authorization = exchange.getRequestHeaders().get("Authorization");
        log.trace("Authorization: {}", authorization);
        if (authorization == null || authorization.isEmpty() || !tokens.contains(authorization.getFirst()))
            throw new AuthenticationException();
        log.trace("Authenticated");
    }

}
