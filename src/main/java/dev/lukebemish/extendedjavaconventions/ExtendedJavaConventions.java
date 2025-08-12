package dev.lukebemish.extendedjavaconventions;

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.jvm.toolchain.JavaToolchainService;
import org.gradle.jvm.toolchain.JvmImplementation;
import org.gradle.jvm.toolchain.JvmVendorSpec;

import javax.inject.Inject;

public abstract class ExtendedJavaConventions {
    @Inject
    public ExtendedJavaConventions() {
        getSourceSets().configureEach(s -> {
            s.getExtensions().create("extendedJavaConventions", SourceSetExtension.class, this, s);
        });
    }

    @Inject
    protected abstract Project getProject();

    @Inject
    protected abstract JavaToolchainService getJavaToolchainService();

    @Inject
    protected abstract ObjectFactory getObjectFactory();

    private SourceSetContainer getSourceSets() {
        return getProject().getExtensions().findByType(SourceSetContainer.class);
    }

    private ConfigurationContainer getConfigurations() {
        return getProject().getConfigurations();
    }

    private TaskContainer getTasks() {
        return getProject().getTasks();
    }

    public void local() {
        getSourceSets().configureEach(this::local);
    }

    public void local(SourceSet sourceSet) {
        var localRuntime = getConfigurations().dependencyScope(sourceSet.getTaskName(null, "localRuntime"));
        var localImplementation = getConfigurations().dependencyScope(sourceSet.getTaskName(null, "localImplementation"));
        getConfigurations().named(sourceSet.getRuntimeClasspathConfigurationName(), config -> {
            config.extendsFrom(localImplementation.get());
            config.extendsFrom(localRuntime.get());
        });
        getConfigurations().named(sourceSet.getCompileClasspathConfigurationName(), config -> {
            config.extendsFrom(localImplementation.get());
        });
    }

    private void copyAttributes(Configuration source, Configuration target) {
        for (Attribute<?> attribute : source.getAttributes().keySet()) {
            copyAttribute(attribute, source.getAttributes(), target.getAttributes());
        }
    }

    private <T> void copyAttribute(Attribute<T> attribute, AttributeContainer source, AttributeContainer target) {
        var value = source.getAttribute(attribute);
        if (value != null) {
            target.attribute(attribute, value);
        }
    }

    public void generateModuleInfo(SourceSet sourceSet, Action<ModuleInfoSpec> action) {
        var spec = getObjectFactory().newInstance(ModuleInfoSpec.class);
        action.execute(spec);

        var runtimeClasspath = getConfigurations().named(sourceSet.getRuntimeClasspathConfigurationName());
        var compileClasspath = getConfigurations().named(sourceSet.getCompileClasspathConfigurationName());

        var api = getConfigurations().named(sourceSet.getApiConfigurationName());
        var implementation = getConfigurations().named(sourceSet.getImplementationConfigurationName());
        var compileOnly = getConfigurations().named(sourceSet.getCompileOnlyConfigurationName());
        var compileOnlyApi = getConfigurations().named(sourceSet.getCompileOnlyApiConfigurationName());

        var requireStaticModules = getConfigurations().resolvable(sourceSet.getTaskName(null, "requireStaticModules"), c -> {
            c.setTransitive(spec.getIncludeTransitive().get());
            copyAttributes(compileClasspath.get(), c);
            c.extendsFrom(api.get(), implementation.get(), compileOnlyApi.get(), compileOnly.get());
        });
        var requireRuntimeModules = getConfigurations().resolvable(sourceSet.getTaskName(null, "requireRuntimeModules"), c -> {
            c.setTransitive(spec.getIncludeTransitive().get());
            copyAttributes(runtimeClasspath.get(), c);
            c.extendsFrom(api.get(), implementation.get());
        });
        var transitiveModules = getConfigurations().resolvable(sourceSet.getTaskName(null, "transitiveModules"), c -> {
            c.setTransitive(spec.getIncludeTransitive().get());
            copyAttributes(compileClasspath.get(), c);
            c.extendsFrom(compileOnlyApi.get(), api.get());
        });

        var generateTask = getTasks().register(sourceSet.getTaskName("generate", "moduleInfo"), GenerateModuleInfoTask.class, task -> {
            task.getRequireRuntime().from(requireRuntimeModules);
            task.getRequireStatic().from(requireStaticModules);
            task.getRequireTransitive().from(transitiveModules);
            task.getOutputDirectory().set(getProject().getLayout().getBuildDirectory().dir("generated/generatedModuleInfo/" + sourceSet.getName()));
            var compiler = getTasks().named(sourceSet.getCompileJavaTaskName(), JavaCompile.class).flatMap(JavaCompile::getJavaCompiler);
            task.getJavaLauncher().set(getJavaToolchainService().launcherFor(toolchain -> {
                toolchain.getLanguageVersion().set(compiler.map(c -> c.getMetadata().getLanguageVersion()));
                toolchain.getVendor().set(compiler.map(c -> JvmVendorSpec.matching(c.getMetadata().getVendor())));
            }));
            task.getModuleInfoSpec().set(spec);
        });
        sourceSet.getJava().srcDir(generateTask.flatMap(GenerateModuleInfoTask::getOutputDirectory));
    }
}
