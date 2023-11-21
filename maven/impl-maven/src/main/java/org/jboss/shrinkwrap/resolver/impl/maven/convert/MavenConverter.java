/*
 * JBoss, Home of Professional Open Source
 * Copyright 2013, Red Hat Middleware LLC, and individual contributors
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
package org.jboss.shrinkwrap.resolver.impl.maven.convert;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.maven.model.Activation;
import org.apache.maven.model.ActivationFile;
import org.apache.maven.model.ActivationOS;
import org.apache.maven.model.ActivationProperty;
import org.apache.maven.model.Profile;
import org.apache.maven.model.Repository;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.ArtifactProperties;
import org.eclipse.aether.artifact.ArtifactType;
import org.eclipse.aether.artifact.ArtifactTypeRegistry;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.artifact.DefaultArtifactType;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.Exclusion;
import org.eclipse.aether.repository.Authentication;
import org.eclipse.aether.repository.Proxy;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.repository.RepositoryPolicy;
import org.eclipse.aether.util.repository.AuthenticationBuilder;
import org.jboss.shrinkwrap.resolver.api.CoordinateParseException;
import org.jboss.shrinkwrap.resolver.api.maven.PackagingType;
import org.jboss.shrinkwrap.resolver.api.maven.ScopeType;
import org.jboss.shrinkwrap.resolver.api.maven.coordinate.MavenCoordinate;
import org.jboss.shrinkwrap.resolver.api.maven.coordinate.MavenCoordinates;
import org.jboss.shrinkwrap.resolver.api.maven.coordinate.MavenDependencies;
import org.jboss.shrinkwrap.resolver.api.maven.coordinate.MavenDependency;
import org.jboss.shrinkwrap.resolver.api.maven.coordinate.MavenDependencyExclusion;
import org.jboss.shrinkwrap.resolver.impl.maven.coordinate.MavenDependencyImpl;
import org.jboss.shrinkwrap.resolver.spi.MavenDependencySPI;

/**
 * An utility class which provides conversion between SWR, Maven, and Aether objects. It allows creation of Aether
 * object from different objects than Maven objects as well.
 *
 * @author Benjamin Bentmann
 * @author <a href="mailto:kpiwko@redhat.com">Karel Piwko</a>
 * @author <a href="mailto:alr@jboss.org">Andrew Lee Rubinger</a>
 */
public class MavenConverter {

    private static final Logger log = Logger.getLogger(MavenConverter.class.getName());

    private static final String EMPTY = "";

    // disable instantiation
    private MavenConverter() {
        throw new UnsupportedOperationException("Utility class MavenConverter cannot be instantiated.");
    }

    private static final MavenDependencyExclusion[] TYPESAFE_EXCLUSIONS_ARRAY = new MavenDependencyExclusion[] {};

    public static MavenDependencyExclusion fromExclusion(final org.apache.maven.model.Exclusion exclusion) {
        final MavenDependencyExclusion translated = MavenDependencies.createExclusion(exclusion.getGroupId(),
                exclusion.getArtifactId());
        return translated;
    }

    public static MavenDependencyExclusion fromExclusion(final Exclusion exclusion) {
        final MavenDependencyExclusion translated = MavenDependencies.createExclusion(exclusion.getGroupId(),
                exclusion.getArtifactId());
        return translated;
    }

    public static Set<MavenDependencyExclusion> fromExclusions(final Collection<Exclusion> exclusions) {
        Set<MavenDependencyExclusion> set = new LinkedHashSet<>(exclusions.size());
        for (final Exclusion e : exclusions) {
            set.add(fromExclusion(e));
        }
        return set;
    }

    public static String toCanonicalForm(final Artifact artifact) {
        final StringBuilder sb = new StringBuilder();
        sb.append(artifact.getGroupId()).append(":");
        sb.append(artifact.getArtifactId()).append(":");

        final PackagingType packaging = PackagingType
                .of(artifact.getProperty(ArtifactProperties.TYPE, artifact.getExtension()));
        final String classifier = artifact.getClassifier().isEmpty() ? packaging.getClassifier() : artifact.getClassifier();

        sb.append(packaging.getId()).append(":");
        if (!classifier.isEmpty()) {
            sb.append(classifier).append(":");
        }
        sb.append(artifact.getVersion());

        return sb.toString();
    }

    public static MavenDependency fromDependency(final Dependency dependency) {
        final Artifact artifact = dependency.getArtifact();

        final PackagingType packaging = PackagingType
                .of(artifact.getProperty(ArtifactProperties.TYPE, artifact.getExtension()));
        final String classifier = artifact.getClassifier().isEmpty() ? packaging.getClassifier() : artifact.getClassifier();

        final MavenCoordinate coordinate = MavenCoordinates.createCoordinate(artifact.getGroupId(),
                artifact.getArtifactId(), artifact.getVersion(), packaging, classifier);

        // SHRINKRES-143 lets ignore invalid scope
        ScopeType scope = ScopeType.RUNTIME;
        try {
            scope = ScopeType.fromScopeType(dependency.getScope());
        } catch (IllegalArgumentException e) {
            // let scope be RUNTIME
            log.log(Level.WARNING, "Invalid scope {0} of dependency {1} will be replaced by <scope>runtime</scope>",
                    new Object[] { dependency.getScope(), coordinate.toCanonicalForm() });
        }

        final MavenDependency result = MavenDependencies.createDependency(coordinate, scope, dependency.isOptional(),
                fromExclusions(dependency.getExclusions()).toArray(TYPESAFE_EXCLUSIONS_ARRAY));
        return result;
    }

    public static Set<MavenDependency> fromDependencies(Collection<Dependency> dependencies) {

        Set<MavenDependency> set = new LinkedHashSet<>();
        for (Dependency d : dependencies) {
            set.add(fromDependency(d));
        }

        return set;
    }

    /**
     * Converts Maven {@link org.apache.maven.model.Dependency} to Aether {@link org.eclipse.aether.graph.Dependency}
     *
     * @param dependency
     * the Maven dependency to be converted
     * @param registry
     * the Artifact type catalog to determine common artifact properties
     * @return Equivalent Aether dependency
     */
    public static MavenDependency fromDependency(org.apache.maven.model.Dependency dependency,
            ArtifactTypeRegistry registry) {
        ArtifactType stereotype = registry.get(dependency.getType());
        if (stereotype == null) {
            stereotype = new DefaultArtifactType(dependency.getType());
        }

        boolean system = dependency.getSystemPath() != null && !dependency.getSystemPath().isEmpty();

        Map<String, String> props = null;
        if (system) {
            props = Collections.singletonMap(ArtifactProperties.LOCAL_PATH, dependency.getSystemPath());
        }

        Artifact artifact = new DefaultArtifact(dependency.getGroupId(), dependency.getArtifactId(),
                dependency.getClassifier(), null, dependency.getVersion(), props, stereotype);

        Set<MavenDependencyExclusion> exclusions = new LinkedHashSet<>();
        for (org.apache.maven.model.Exclusion e : dependency.getExclusions()) {
            exclusions.add(fromExclusion(e));
        }

        final PackagingType packaging = PackagingType
                .of(artifact.getProperty(ArtifactProperties.TYPE, artifact.getExtension()));
        final String classifier = artifact.getClassifier().isEmpty() ? packaging.getClassifier() : artifact.getClassifier();

        final MavenCoordinate coordinate = MavenCoordinates.createCoordinate(artifact.getGroupId(),
                artifact.getArtifactId(), artifact.getVersion(), packaging, classifier);

        // SHRINKRES-123 Allow for depMgt explicitly not setting scope
        final String resolvedScope = dependency.getScope();
        final boolean undeclaredScope = resolvedScope == null;

        // SHRINKRES-143 lets ignore invalid scope
        ScopeType scope = ScopeType.RUNTIME;
        try {
            scope = ScopeType.fromScopeType(resolvedScope);
        } catch (IllegalArgumentException e) {
            // let scope be RUNTIME
            log.log(Level.WARNING, "Invalid scope {0} of dependency {1} will be replaced by <scope>runtime</scope>",
                    new Object[] { dependency.getScope(), coordinate.toCanonicalForm() });
        }

        final MavenDependencySPI result = new MavenDependencyImpl(coordinate, scope,
                dependency.isOptional(), undeclaredScope, exclusions.toArray(TYPESAFE_EXCLUSIONS_ARRAY));
        return result;
    }

    public static Set<MavenDependency> fromDependencies(Collection<org.apache.maven.model.Dependency> dependencies,
            ArtifactTypeRegistry registry) {

        Set<MavenDependency> set = new LinkedHashSet<>();
        for (org.apache.maven.model.Dependency d : dependencies) {
            set.add(fromDependency(d, registry));
        }

        return set;
    }

    /**
     * Converts MavenDepedency to Dependency representation used in Aether
     *
     * @param dependency
     * the Maven dependency
     * @param registry
     * A registry of known artifact types.
     * @return the corresponding Aether dependency
     */
    public static Dependency asDependency(MavenDependencySPI dependency, ArtifactTypeRegistry registry) {

        /*
         * Allow for undeclared scopes
         */
        String scope = dependency.getScope().toString();
        if (dependency.isUndeclaredScope()) {
            scope = EMPTY;
        }
        return new Dependency(asArtifact(dependency, registry), scope, dependency.isOptional(),
                asExclusions(dependency.getExclusions()));
    }

    public static List<Dependency> asDependencies(List<MavenDependency> dependencies, ArtifactTypeRegistry registry) {
        final List<Dependency> list = new ArrayList<>(dependencies.size());
        for (final MavenDependency d : dependencies) {
            list.add(asDependency((MavenDependencySPI) d, registry));
        }

        return list;
    }

    public static Artifact asArtifact(MavenCoordinate coordinate, ArtifactTypeRegistry registry) throws CoordinateParseException {
        try {
            return new DefaultArtifact(coordinate.getGroupId(), coordinate.getArtifactId(),
                    coordinate.getClassifier(), coordinate.getPackaging().getExtension(), coordinate.getVersion(), registry.get(coordinate.getPackaging().getId()));
        } catch (IllegalArgumentException e) {
            throw new CoordinateParseException("Unable to create artifact from invalid coordinates "
                    + coordinate.toCanonicalForm());
        }
    }

    public static Exclusion asExclusion(MavenDependencyExclusion coordinates) {

        String group = coordinates.getGroupId();
        String artifact = coordinates.getArtifactId();

        group = (group == null || group.isEmpty()) ? "*" : group;
        artifact = (artifact == null || artifact.isEmpty()) ? "*" : artifact;

        return new Exclusion(group, artifact, "*", "*");
    }

    public static List<Exclusion> asExclusions(Collection<MavenDependencyExclusion> exclusions) {
        List<Exclusion> list = new ArrayList<>(exclusions.size());
        for (MavenDependencyExclusion coords : exclusions) {
            list.add(asExclusion(coords));
        }
        return list;
    }

    /**
     * Converts Maven {@link Repository} to Aether {@link RemoteRepository}
     *
     * @param repository
     * the Maven repository to be converted
     * @return Equivalent remote repository
     */
    public static RemoteRepository asRemoteRepository(org.apache.maven.model.Repository repository) {
        return new RemoteRepository.Builder(repository.getId(), repository.getLayout(), repository.getUrl())
                .setSnapshotPolicy(asRepositoryPolicy(repository.getSnapshots()))
                .setReleasePolicy(asRepositoryPolicy(repository.getReleases())).build();
    }

    /**
     * Converts Maven {@link Repository} to Aether {@link RemoteRepository}
     *
     * @param repository
     * the Maven repository to be converted
     * @return Equivalent remote repository
     */
    public static RemoteRepository asRemoteRepository(org.apache.maven.settings.Repository repository) {
        return new RemoteRepository.Builder(repository.getId(), repository.getLayout(), repository.getUrl())
                .setSnapshotPolicy(asRepositoryPolicy(repository.getSnapshots()))
                .setReleasePolicy(asRepositoryPolicy(repository.getReleases())).build();
    }

    /**
     * Converts Maven Proxy to Aether Proxy
     *
     * @param proxy
     * the Maven proxy to be converted
     * @return Aether proxy equivalent
     */
    public static Proxy asProxy(org.apache.maven.settings.Proxy proxy) {
        final Authentication authentication;
        if (proxy.getUsername() != null || proxy.getPassword() != null) {
            authentication = new AuthenticationBuilder().addUsername(proxy.getUsername())
                    .addPassword(proxy.getPassword()).build();
        } else {
            authentication = null;
        }
        return new Proxy(proxy.getProtocol(), proxy.getHost(), proxy.getPort(), authentication);
    }

    public static Profile asProfile(org.apache.maven.settings.Profile profile) {
        Profile mavenProfile = new Profile();

        if (profile != null) {
            mavenProfile.setId(profile.getId());
            mavenProfile.setActivation(asActivation(profile.getActivation()));
            mavenProfile.setProperties(profile.getProperties());
            mavenProfile.setRepositories(asRepositories(profile.getRepositories()));
            mavenProfile.setPluginRepositories(asRepositories(profile.getPluginRepositories()));
        }

        return mavenProfile;
    }

    public static List<Profile> asProfiles(List<org.apache.maven.settings.Profile> profiles) {
        List<Profile> mavenProfiles = new ArrayList<>();
        for (org.apache.maven.settings.Profile p : profiles) {
            mavenProfiles.add(asProfile(p));
        }

        return mavenProfiles;
    }

    private static Repository asRepository(org.apache.maven.settings.Repository repository) {
        Repository mavenRepository = new Repository();
        if (repository != null) {
            mavenRepository.setId(repository.getId());
            mavenRepository.setLayout(repository.getLayout());
            mavenRepository.setName(repository.getName());
            mavenRepository.setUrl(repository.getUrl());
            mavenRepository.setReleases(asMavenRepositoryPolicy(repository.getReleases()));
            mavenRepository.setSnapshots(asMavenRepositoryPolicy(repository.getSnapshots()));
        }

        return mavenRepository;
    }

    private static List<Repository> asRepositories(List<org.apache.maven.settings.Repository> repositories) {
        List<Repository> mavenRepositories = new ArrayList<>();
        for (org.apache.maven.settings.Repository repository : repositories) {
            mavenRepositories.add(asRepository(repository));
        }

        return mavenRepositories;
    }

    private static Activation asActivation(org.apache.maven.settings.Activation activation) {
        Activation mavenActivation = new Activation();

        if (activation != null) {
            mavenActivation.setActiveByDefault(activation.isActiveByDefault());
            mavenActivation.setJdk(activation.getJdk());
            if (activation.getFile() != null) {
                mavenActivation.setFile(asActivationFile(activation.getFile()));
            }
            if (activation.getOs() != null) {
                mavenActivation.setOs(asActivationOS(activation.getOs()));
            }
            if (activation.getProperty() != null) {
                mavenActivation.setProperty(asActivationProperty(activation.getProperty()));
            }
        }

        return mavenActivation;
    }

    private static ActivationFile asActivationFile(org.apache.maven.settings.ActivationFile file) {
        ActivationFile mavenActivationFile = new ActivationFile();

        if (file != null) {
            mavenActivationFile.setExists(file.getExists());
            mavenActivationFile.setMissing(file.getMissing());
        }

        return mavenActivationFile;
    }

    private static ActivationOS asActivationOS(org.apache.maven.settings.ActivationOS os) {
        ActivationOS mavenOS = new ActivationOS();

        if (os != null) {
            mavenOS.setArch(os.getArch());
            mavenOS.setFamily(os.getFamily());
            mavenOS.setName(os.getName());
            mavenOS.setVersion(os.getVersion());
        }

        return mavenOS;
    }

    private static ActivationProperty asActivationProperty(org.apache.maven.settings.ActivationProperty property) {
        ActivationProperty mavenProperty = new ActivationProperty();

        if (property != null) {
            mavenProperty.setName(property.getName());
            mavenProperty.setValue(property.getValue());
        }

        return mavenProperty;
    }

    // converts repository policy
    private static RepositoryPolicy asRepositoryPolicy(org.apache.maven.model.RepositoryPolicy policy) {
        boolean enabled = true;
        String checksums = RepositoryPolicy.CHECKSUM_POLICY_WARN;
        String updates = RepositoryPolicy.UPDATE_POLICY_DAILY;

        if (policy != null) {
            enabled = policy.isEnabled();
            if (policy.getUpdatePolicy() != null) {
                updates = policy.getUpdatePolicy();
            }
            if (policy.getChecksumPolicy() != null) {
                checksums = policy.getChecksumPolicy();
            }
        }

        return new RepositoryPolicy(enabled, updates, checksums);
    }

    // converts repository policy
    private static RepositoryPolicy asRepositoryPolicy(org.apache.maven.settings.RepositoryPolicy policy) {
        boolean enabled = true;
        String checksums = RepositoryPolicy.CHECKSUM_POLICY_WARN;
        String updates = RepositoryPolicy.UPDATE_POLICY_DAILY;

        if (policy != null) {
            enabled = policy.isEnabled();
            if (policy.getUpdatePolicy() != null) {
                updates = policy.getUpdatePolicy();
            }
            if (policy.getChecksumPolicy() != null) {
                checksums = policy.getChecksumPolicy();
            }
        }

        return new RepositoryPolicy(enabled, updates, checksums);
    }

    // converts repository policy
    private static org.apache.maven.model.RepositoryPolicy asMavenRepositoryPolicy(
            org.apache.maven.settings.RepositoryPolicy policy) {

        org.apache.maven.model.RepositoryPolicy mavenPolicy = new org.apache.maven.model.RepositoryPolicy();
        if (policy != null) {
            mavenPolicy.setChecksumPolicy(policy.getChecksumPolicy());
            mavenPolicy.setUpdatePolicy(policy.getUpdatePolicy());
            mavenPolicy.setEnabled(policy.isEnabled());
        }

        return mavenPolicy;
    }

}
