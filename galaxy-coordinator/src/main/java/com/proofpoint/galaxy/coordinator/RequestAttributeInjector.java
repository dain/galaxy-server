package com.proofpoint.galaxy.coordinator;

import com.sun.jersey.api.core.HttpContext;
import com.sun.jersey.core.spi.component.ComponentContext;
import com.sun.jersey.core.spi.component.ComponentScope;
import com.sun.jersey.spi.container.WebApplication;
import com.sun.jersey.spi.inject.Injectable;
import com.sun.jersey.spi.inject.InjectableProvider;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.Context;
import javax.ws.rs.ext.Provider;
import java.lang.reflect.Type;

@Provider
public class RequestAttributeInjector implements InjectableProvider<RequestAttribute, Type>
{
    private HttpContext httpContext;
    private HttpServletRequest httpServletRequest;

    @Context
    public void setHttpContext(HttpContext httpContext)
    {
        this.httpContext = httpContext;
    }

    @Context
    public void setHttpServletRequest(HttpServletRequest httpServletRequest)
    {
        this.httpServletRequest = httpServletRequest;
    }

    public ComponentScope getScope()
    {
        return ComponentScope.PerRequest;
    }

    public Injectable<?> getInjectable(ComponentContext componentContext, RequestAttribute requestAttribute, Type type)
    {
        final String name = requestAttribute.value();

        return new Injectable<Object>()
        {
            public Object getValue()
            {
                Object value = httpContext.getProperties().get(name);
                if (value == null) {
                    value = httpServletRequest.getAttribute(name);
                }

                return value;
            }
        };
    }
}
