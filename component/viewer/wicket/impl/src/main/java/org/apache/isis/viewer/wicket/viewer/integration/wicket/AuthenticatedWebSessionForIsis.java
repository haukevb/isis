/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */

package org.apache.isis.viewer.wicket.viewer.integration.wicket;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import java.util.Arrays;
import java.util.List;

import org.apache.wicket.Session;
import org.apache.wicket.authroles.authentication.AuthenticatedWebSession;
import org.apache.wicket.authroles.authorization.strategies.role.Roles;
import org.apache.wicket.protocol.http.request.WebClientInfo;
import org.apache.wicket.request.Request;

import org.apache.isis.core.commons.authentication.AuthenticationSession;
import org.apache.isis.core.commons.authentication.AuthenticationSessionProvider;
import org.apache.isis.core.commons.authentication.AuthenticationSessionProviderAware;
import org.apache.isis.core.commons.ensure.Ensure;
import org.apache.isis.core.runtime.authentication.AuthenticationManager;
import org.apache.isis.core.runtime.authentication.AuthenticationRequest;
import org.apache.isis.core.runtime.authentication.AuthenticationRequestPassword;
import org.apache.isis.core.runtime.system.context.IsisContext;
import org.apache.isis.viewer.wicket.model.models.BookmarkedPagesModel;
import org.apache.isis.viewer.wicket.ui.components.widgets.breadcrumbs.BreadcrumbModel;
import org.apache.isis.viewer.wicket.ui.components.widgets.breadcrumbs.BreadcrumbModelProvider;
import org.apache.isis.viewer.wicket.ui.pages.BookmarkedPagesModelProvider;

/**
 * Viewer-specific implementation of {@link AuthenticatedWebSession}, which
 * delegates to the Isis' configured {@link AuthenticationManager}, and which
 * also tracks threadusage (so that multiple concurrent requests are all
 * associated with the same session).
 */
public class AuthenticatedWebSessionForIsis extends AuthenticatedWebSession implements AuthenticationSessionProvider, BreadcrumbModelProvider, BookmarkedPagesModelProvider {

    private static final long serialVersionUID = 1L;

    public static final String USER_ROLE = "org.apache.isis.viewer.wicket.roles.USER";

    public static AuthenticatedWebSessionForIsis get() {
        return (AuthenticatedWebSessionForIsis) Session.get();
    }

    private final BookmarkedPagesModel bookmarkedPagesModel = new BookmarkedPagesModel();
    private final BreadcrumbModel breadcrumbModel = new BreadcrumbModel();
    
    private AuthenticationSession authenticationSession;

    public AuthenticatedWebSessionForIsis(final Request request) {
        super(Ensure.ensureThatArg(request, is(not(nullValue(Request.class)))));
    }

    @Override
    public boolean authenticate(final String username, final String password) {
        AuthenticationRequest authenticationRequest;
        authenticationRequest = new AuthenticationRequestPassword(username, password);
        authenticationRequest.setRoles(Arrays.asList(USER_ROLE));
        authenticationSession = getAuthenticationManager().authenticate(authenticationRequest);
        return authenticationSession != null;
    }

    @Override
    public AuthenticationSession getAuthenticationSession() {
        return authenticationSession;
    }

    @Override
    public Roles getRoles() {
        if (!isSignedIn()) {
            return null;
        }
        final List<String> roles = authenticationSession.getRoles();
        return new Roles(roles.toArray(new String[] {}));
    }

    /**
     * Simply downcasts, for convenience of callers.
     */
    @Override
    public WebClientInfo getClientInfo() {
        return (WebClientInfo) super.getClientInfo();
    }

    
    // /////////////////////////////////////////////////
    // Breadcrumbs and Bookmarks support
    // /////////////////////////////////////////////////

    @Override
    public BreadcrumbModel getBreadcrumbModel() {
        return breadcrumbModel;
    }

    @Override
    public BookmarkedPagesModel getBookmarkedPagesModel() {
        return bookmarkedPagesModel;
    }


    // /////////////////////////////////////////////////
    // Dependencies
    // /////////////////////////////////////////////////

    protected AuthenticationManager getAuthenticationManager() {
        return IsisContext.getAuthenticationManager();
    }

    // /////////////////////////////////////////////////
    // *Provider impl.
    // /////////////////////////////////////////////////
    
    @Override
    public void injectInto(final Object candidate) {
        if (AuthenticationSessionProviderAware.class.isAssignableFrom(candidate.getClass())) {
            final AuthenticationSessionProviderAware cast = AuthenticationSessionProviderAware.class.cast(candidate);
            cast.setAuthenticationSessionProvider(this);
        }
    }

    
}
