package com.xactsolutions.email.filter;

import com.sun.net.httpserver.HttpExchange;

public abstract class AuthFilter {

    public abstract void applyFilter(HttpExchange exchange);

}
