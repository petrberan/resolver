/*
 * JBoss, Home of Professional Open Source
 * Copyright 2012, Red Hat Middleware LLC, and individual contributors
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.shrinkwrap.resolver.impl.maven.filter;

import java.util.List;

import org.jboss.shrinkwrap.resolver.api.maven.dependency.DependencyDeclaration;

/**
 * A filter which accept all dependencies. This is the default behavior is no other filter is specified.
 *
 * @author <a href="mailto:kpiwko@redhat.com">Karel Piwko</a>
 * @author <a href="mailto:alr@jboss.org">Andrew Lee Rubinger</a>
 */
public enum AcceptAllFilter implements MavenResolutionFilterInternalView {
    INSTANCE;

    @Override
    public boolean accepts(final DependencyDeclaration coordinate) throws IllegalArgumentException {
        return true;
    }

    @Override
    public MavenResolutionFilterInternalView setDefinedDependencies(final List<DependencyDeclaration> dependencies) {
        return this;
    }

    @Override
    public MavenResolutionFilterInternalView setDefinedDependencyManagement(
        final List<DependencyDeclaration> dependencyManagement) {
        return this;
    }

}
