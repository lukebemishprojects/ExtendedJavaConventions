package dev.lukebemish.extendedjavaconventions;

import org.gradle.api.Project;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.attributes.Bundling;
import org.gradle.api.attributes.DocsType;
import org.gradle.api.attributes.Usage;
import org.gradle.api.attributes.java.TargetJvmEnvironment;
import org.gradle.api.attributes.java.TargetJvmVersion;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.compile.JavaCompile;

import javax.inject.Inject;
import java.io.File;
import java.util.ArrayList;

public abstract class ExtendedJavaConventions {
    @Inject
    public ExtendedJavaConventions() {}

    @Inject
    protected abstract Project getProject();

    @Inject
    protected abstract ObjectFactory getObjects();

    private SourceSetContainer getSourceSets() {
        return getProject().getExtensions().findByType(SourceSetContainer.class);
    }

    private ConfigurationContainer getConfigurations() {
        return getProject().getConfigurations();
    }

    private TaskContainer getTasks() {
        return getProject().getTasks();
    }

    public void sourcepath() {
        getSourceSets().configureEach(this::sourcepath);
    }

    public void sourcepath(SourceSet sourceSet) {
        var sourceImplementation = getConfigurations().dependencyScope(sourceSet.getTaskName(null, "sourceImplementation"));
        var sourceCompileOnly = getConfigurations().dependencyScope(sourceSet.getTaskName(null, "sourceCompileOnly"));
        var sourceCompileOnlyApi = getConfigurations().dependencyScope(sourceSet.getTaskName(null, "sourceCompileOnlyApi"));
        var sourceApi = getConfigurations().dependencyScope(sourceSet.getTaskName(null, "sourceApi"));

        var compileClasspath = getConfigurations().named(sourceSet.getCompileClasspathConfigurationName(), config -> {
            config.extendsFrom(sourceImplementation.get());
            config.extendsFrom(sourceCompileOnly.get());
            config.extendsFrom(sourceCompileOnlyApi.get());
            config.extendsFrom(sourceApi.get());
        });

        FeatureUtils.forSourceSetFeature(getProject(), sourceSet.getName(), false, context -> {
            var sourcepathElements = getConfigurations().consumable(sourceSet.getTaskName(null, "sourcepathElements"), config -> {
                config.attributes(attributes -> {
                    attributes.attribute(DocsType.DOCS_TYPE_ATTRIBUTE, getObjects().named(DocsType.class, DocsType.SOURCES));
                    attributes.attribute(Bundling.BUNDLING_ATTRIBUTE, getObjects().named(Bundling.class, Bundling.EXTERNAL));
                    attributes.attribute(TargetJvmEnvironment.TARGET_JVM_ENVIRONMENT_ATTRIBUTE, getObjects().named(TargetJvmEnvironment.class, TargetJvmEnvironment.STANDARD_JVM));
                    attributes.attribute(Usage.USAGE_ATTRIBUTE, getObjects().named(Usage.class, "java-api-sources"));
                    attributes.attributeProvider(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, getProject().provider(() -> compileClasspath.get().getAttributes().getAttribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE)));
                });
                config.extendsFrom(sourceApi.get());
                config.extendsFrom(sourceCompileOnlyApi.get());
            });

            getProject().getPluginManager().withPlugin("java-library", p -> {
                var api = getConfigurations().named(sourceSet.getApiConfigurationName());
                var compileOnlyApi = getConfigurations().named(sourceSet.getCompileOnlyApiConfigurationName());
                sourcepathElements.get().extendsFrom(api.get());
                sourcepathElements.get().extendsFrom(compileOnlyApi.get());
            });

            context.getApiElements().extendsFrom(sourceApi.get());
            context.getApiElements().extendsFrom(sourceCompileOnlyApi.get());

            context.getRuntimeElements().extendsFrom(sourceImplementation.get());
            context.getRuntimeElements().extendsFrom(sourceApi.get());

            sourcepathElements.get().getArtifacts().addAllLater(getProject().provider(() -> {
                var artifacts = new ArrayList<PublishArtifact>();
                for (var file : sourceSet.getAllJava().getSourceDirectories()) {
                    artifacts.add(getObjects().newInstance(LazyDirectoryArtifact.class, "java-sources-directory", getProject().provider(() -> file), sourceSet.getAllJava().getSourceDirectories().getBuildDependencies()));
                }
                return artifacts;
            }));

            context.withCapabilities(sourcepathElements.get());
        });

        var artifactsView = compileClasspath.get().getIncoming().artifactView(config -> {
            config.getAttributes().attribute(Usage.USAGE_ATTRIBUTE, getObjects().named(Usage.class, "java-api-sources"));
            config.withVariantReselection();
        });
        var artifactsProvider = artifactsView.getArtifacts().getResolvedArtifacts();

        sourceSet.setCompileClasspath(getProject().files(artifactsProvider.map(set -> {
            var list = new ArrayList<File>();
            for (var artifact : set) {
                var usage = artifact.getVariant().getAttributes().getAttribute(Usage.USAGE_ATTRIBUTE);
                if (usage == null || !usage.getName().equals("java-api-sources")) {
                    list.add(artifact.getFile());
                }
            }
            return list;
        })).builtBy(artifactsView.getArtifacts().getArtifactFiles()));

        getTasks().named(sourceSet.getCompileJavaTaskName(), JavaCompile.class, task -> {
            var existing = task.getOptions().getSourcepath();
            var files = getProject().files();
            if (existing != null) {
                files.from(existing);
            }
            files.from(artifactsProvider.map(set -> {
                var list = new ArrayList<File>();
                for (var artifact : set) {
                    var usage = artifact.getVariant().getAttributes().getAttribute(Usage.USAGE_ATTRIBUTE);
                    if (usage != null && usage.getName().equals("java-api-sources")) {
                        list.add(artifact.getFile());
                    }
                }
                return list;
            })).builtBy(artifactsView.getArtifacts().getArtifactFiles());
            task.getOptions().setSourcepath(files);
        });
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
}
