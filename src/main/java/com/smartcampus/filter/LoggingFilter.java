package com.smartcampus.filter;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.ext.Provider;
import java.io.IOException;
import java.util.logging.Logger;

/**
 * Part 5.5 - API Request & Response Logging Filter.
 *
 * Implements both ContainerRequestFilter and ContainerResponseFilter to log
 * every incoming request and outgoing response in one place.
 *
 * Using a filter for cross-cutting concerns like logging is superior to manually
 * calling Logger.info() in every resource method because:
 *  - It follows the DRY principle (no duplicated logging code)
 *  - Resource methods stay focused on business logic only
 *  - Logging can be added/removed/modified without touching any resource class
 *  - It guarantees consistent logging even for endpoints added in the future
 */
@Provider
public class LoggingFilter implements ContainerRequestFilter, ContainerResponseFilter {

    private static final Logger LOGGER = Logger.getLogger(LoggingFilter.class.getName());

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        LOGGER.info(String.format("[REQUEST]  %s %s",
                requestContext.getMethod(),
                requestContext.getUriInfo().getRequestUri()));
    }

    @Override
    public void filter(ContainerRequestContext requestContext,
                       ContainerResponseContext responseContext) throws IOException {
        LOGGER.info(String.format("[RESPONSE] %d %s for %s %s",
                responseContext.getStatus(),
                responseContext.getStatusInfo().getReasonPhrase(),
                requestContext.getMethod(),
                requestContext.getUriInfo().getRequestUri()));
    }
}
