package org.jboss.shrinkwrap.resolver.impl.maven.embedded.invoker.equipped;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.Properties;

import org.apache.maven.shared.invoker.DefaultInvocationRequest;
import org.apache.maven.shared.invoker.DefaultInvoker;
import org.apache.maven.shared.invoker.InvocationRequest;
import org.apache.maven.shared.invoker.Invoker;
import org.jboss.shrinkwrap.resolver.api.maven.embedded.BuiltProject;
import org.jboss.shrinkwrap.resolver.api.maven.embedded.EmbeddedMaven;
import org.jboss.shrinkwrap.resolver.impl.maven.embedded.TestWorkDirRule;
import org.jboss.shrinkwrap.resolver.impl.maven.embedded.pom.equipped.ResolverErrorOutputHandler;
import org.jboss.shrinkwrap.resolver.impl.maven.embedded.pom.equipped.ResolverOutputHandler;
import org.junit.Rule;
import org.junit.Test;

import static org.jboss.shrinkwrap.resolver.impl.maven.embedded.Utils.archiveNameModuleTwoParamKey;
import static org.jboss.shrinkwrap.resolver.impl.maven.embedded.Utils.archiveNameModuleTwoParamValue;
import static org.jboss.shrinkwrap.resolver.impl.maven.embedded.Utils.getPropertiesWithSkipTests;
import static org.jboss.shrinkwrap.resolver.impl.maven.embedded.Utils.multiModuleactivateModulesParamKey;
import static org.jboss.shrinkwrap.resolver.impl.maven.embedded.Utils.multiModuleactivateModulesParamValue;
import static org.jboss.shrinkwrap.resolver.impl.maven.embedded.Utils.pathToMultiModulePom;
import static org.jboss.shrinkwrap.resolver.impl.maven.embedded.Utils.verifyMavenVersion;
import static org.jboss.shrinkwrap.resolver.impl.maven.embedded.Utils.verifyMultiModuleSample;
import static org.jboss.shrinkwrap.resolver.impl.maven.embedded.Utils.verifyMultiModuleSampleWasCleaned;

/**
 * @author <a href="mailto:mjobanek@redhat.com">Matous Jobanek</a>
 */
public class InvokerEquippedEmbeddedMavenForMultiModuleSampleTestCase {

    @Rule
    public final TestWorkDirRule workDirRule = new TestWorkDirRule();

    /**
     * Original test testMultiModuleSampleBuildWithMaven305
     * Switched to 3.1.0 as 3.0.x Maven use should be avoided.
     */
    @Test
    public void testMultiModuleSampleBuildWithMaven310() {
        InvocationRequest request = new DefaultInvocationRequest();
        Invoker invoker = new DefaultInvoker();

        request.setPomFile(workDirRule.prepareProject(pathToMultiModulePom));
        request.setGoals(Collections.singletonList("install"));
        request.setUserSettingsFile(new File("src/it/settings.xml"));

        Properties props = getPropertiesWithSkipTests();
        props.put(multiModuleactivateModulesParamKey, multiModuleactivateModulesParamValue);
        props.put(archiveNameModuleTwoParamKey, archiveNameModuleTwoParamValue);
        request.setProperties(props);

        StringBuffer logBuffer = new StringBuffer("");
        invoker.setOutputHandler(new ResolverOutputHandler(logBuffer));
        invoker.setErrorHandler(new ResolverErrorOutputHandler(logBuffer));

        request.setShowVersion(true);

        BuiltProject builtProject = EmbeddedMaven
            .withMavenInvokerSet(request, invoker)
            .useMaven3Version("3.1.0")
            .build();
        builtProject.setMavenLog(logBuffer.toString());

        verifyMavenVersion(builtProject, "3.1.0");
        verifyMultiModuleSample(builtProject, true);
    }

    @Test
    public void testMultiModuleSampleCleanBuild() {
        InvocationRequest request = new DefaultInvocationRequest();
        Invoker invoker = new DefaultInvoker();

        request.setPomFile(workDirRule.prepareProject(pathToMultiModulePom));
        request.setGoals(Collections.singletonList("clean"));

        Properties props = getPropertiesWithSkipTests();
        props.put(multiModuleactivateModulesParamKey, multiModuleactivateModulesParamValue);
        props.put(archiveNameModuleTwoParamKey, archiveNameModuleTwoParamValue);
        request.setProperties(props);

        BuiltProject builtProject = EmbeddedMaven
            .withMavenInvokerSet(request, invoker)
            .build();

        verifyMultiModuleSampleWasCleaned(builtProject);
    }

    @Test
    public void testMultiModuleSampleBuildWithoutModulesActivated() {
        InvocationRequest request = new DefaultInvocationRequest();
        Invoker invoker = new DefaultInvoker();

        request.setPomFile(workDirRule.prepareProject(pathToMultiModulePom));
        request.setGoals(Arrays.asList("clean", "package"));

        Properties props = getPropertiesWithSkipTests();
        props.put(archiveNameModuleTwoParamKey, archiveNameModuleTwoParamValue);
        request.setProperties(props);

        BuiltProject builtProject = EmbeddedMaven
            .withMavenInvokerSet(request, invoker)
            .build();

        verifyMultiModuleSample(builtProject, false);
    }
}
