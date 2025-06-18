package dev.lukebemish.extendedjavaconventions;

import org.gradle.api.Project;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.attributes.AttributeCompatibilityRule;
import org.gradle.api.attributes.AttributeDisambiguationRule;
import org.gradle.api.attributes.Bundling;
import org.gradle.api.attributes.Category;
import org.gradle.api.attributes.CompatibilityCheckDetails;
import org.gradle.api.attributes.MultipleCandidatesDetails;
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
        getProject().getDependencies().getAttributesSchema().attribute(Usage.USAGE_ATTRIBUTE, strategy -> {
            strategy.getCompatibilityRules().add(JavaApiSourcesCompatabilityRule.class);
            strategy.getDisambiguationRules().add(JavaApiSourcesDisambiguationRule.class);
        });
        getSourceSets().configureEach(this::sourcepath);
    }

    private static final String JAVA_API_SOURCES = "java-api-sources";

    public static final class JavaApiSourcesDisambiguationRule implements AttributeDisambiguationRule<Usage> {

        @Override
        public void execute(MultipleCandidatesDetails<Usage> details) {
            var consumer = details.getConsumerValue() == null ? null : details.getConsumerValue().getName();
            if (JAVA_API_SOURCES.equals(consumer)) {
                for (var value : details.getCandidateValues()) {
                    if (value.getName().equals(JAVA_API_SOURCES)) {
                        details.closestMatch(value);
                        return;
                    } else if (value.getName().equals(Usage.JAVA_API)) {
                        details.closestMatch(value);
                        return;
                    } else if (value.getName().equals(Usage.JAVA_RUNTIME)) {
                        details.closestMatch(value);
                        return;
                    }
                }
            }
        }
    }

    public static final class JavaApiSourcesCompatabilityRule implements AttributeCompatibilityRule<Usage> {

        @Override
        public void execute(CompatibilityCheckDetails<Usage> details) {
            var consumer = details.getConsumerValue() == null ? null : details.getConsumerValue().getName();
            var producer = details.getProducerValue() == null ? null : details.getProducerValue().getName();
            if (JAVA_API_SOURCES.equals(consumer)) {
                if (producer == null || producer.equals(Usage.JAVA_API) || producer.equals(Usage.JAVA_RUNTIME) || producer.equals(JAVA_API_SOURCES)) {
                    details.compatible();
                }
            } else if (JAVA_API_SOURCES.equals(producer)) {
                if (consumer == null || consumer.equals(Usage.JAVA_API) || consumer.equals(Usage.JAVA_RUNTIME) || consumer.equals(JAVA_API_SOURCES)) {
                    details.compatible();
                }
            }
        }
    }

    public void sourcepath(SourceSet sourceSet) {
        var compileClasspath = getConfigurations().named(sourceSet.getCompileClasspathConfigurationName(), config -> {
            config.getAttributes().attribute(Usage.USAGE_ATTRIBUTE, getObjects().named(Usage.class, JAVA_API_SOURCES));
        });

        FeatureUtils.forSourceSetFeature(getProject(), sourceSet.getName(), context -> {
            var sourcepathApi = getConfigurations().dependencyScope(sourceSet.getTaskName(null, "sourcepathApi"), config -> {
                config.withDependencies(set -> {
                    set.addAll(context.getApiElements().get().getAllDependencies());
                });
            });

            getConfigurations().consumable(sourceSet.getTaskName(null, "sourcepathElements"), config -> {
                config.attributes(attributes -> {
                    attributes.attribute(Category.CATEGORY_ATTRIBUTE, getObjects().named(Category.class, Category.LIBRARY));
                    attributes.attribute(Bundling.BUNDLING_ATTRIBUTE, getObjects().named(Bundling.class, Bundling.EXTERNAL));
                    attributes.attribute(TargetJvmEnvironment.TARGET_JVM_ENVIRONMENT_ATTRIBUTE, getObjects().named(TargetJvmEnvironment.class, TargetJvmEnvironment.STANDARD_JVM));
                    attributes.attribute(Usage.USAGE_ATTRIBUTE, getObjects().named(Usage.class, JAVA_API_SOURCES));
                    attributes.attributeProvider(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, getProject().provider(() -> compileClasspath.get().getAttributes().getAttribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE)));
                });
                config.extendsFrom(sourcepathApi.get());
                context.withCapabilities(config);

                config.getArtifacts().addAllLater(getProject().provider(() -> {
                    var artifacts = new ArrayList<PublishArtifact>();
                    for (var file : sourceSet.getAllJava().getSourceDirectories()) {
                        artifacts.add(getObjects().newInstance(LazyDirectoryArtifact.class, "java-sources-directory", getProject().provider(() -> file), sourceSet.getAllJava().getSourceDirectories().getBuildDependencies()));
                    }
                    return artifacts;
                }));
            });
        });

        getTasks().named(sourceSet.getCompileJavaTaskName(), JavaCompile.class, task -> {
            var sourcesView = getConfigurations().named(sourceSet.getCompileClasspathConfigurationName()).get().getIncoming();
            var sources = sourcesView.getArtifacts().getResolvedArtifacts().map(set -> {
                var files = new ArrayList<File>();
                for (var artifact : set) {
                    var usage = artifact.getVariant().getAttributes().getAttribute(Usage.USAGE_ATTRIBUTE);
                    if (usage != null && usage.getName().equals(JAVA_API_SOURCES)) {
                        files.add(artifact.getFile());
                    }
                }
                return files;
            });
            var binaries = sourcesView.getArtifacts().getResolvedArtifacts().map(set -> {
                var files = new ArrayList<File>();
                for (var artifact : set) {
                    var usage = artifact.getVariant().getAttributes().getAttribute(Usage.USAGE_ATTRIBUTE);
                    if (usage == null || !usage.getName().equals(JAVA_API_SOURCES)) {
                        files.add(artifact.getFile());
                    }
                }
                return files;
            });

            var existing = task.getOptions().getSourcepath();
            var files = getProject().files();
            if (existing != null) {
                files.from(existing);
            }
            files.from(sources);
            files.builtBy(sourcesView.getArtifacts().getArtifactFiles());
            task.getOptions().setSourcepath(files);

            if (!Boolean.getBoolean("idea.sync.active")) {
                // IntelliJ does not understand the source path -- so, during sync, we trick it a bit.
                var classpath = getProject().files();
                classpath.from(binaries);
                classpath.builtBy(sourcesView.getArtifacts().getArtifactFiles());
                task.setClasspath(classpath);
            }
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
