package dev.lukebemish.extendedjavaconventions;

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationPublications;
import org.gradle.api.artifacts.ConfigurationVariant;
import org.gradle.api.component.AdhocComponentWithVariants;
import org.gradle.api.component.ConfigurationVariantDetails;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;

import javax.inject.Inject;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

class FeatureUtils {
    private FeatureUtils() {}

    public static abstract class Context {
        private final Configuration runtimeElements;
        private final Configuration apiElements;
        private final SourceSet sourceSet;
        private final AdhocComponentWithVariants component;
        private final Configuration foundFirst;

        @Inject
        public abstract Project getProject();

        @Inject
        public Context(SourceSet sourceSet, Configuration foundFirst) {
            this.sourceSet = sourceSet;
            this.runtimeElements = getProject().getConfigurations().getByName(sourceSet.getRuntimeElementsConfigurationName());
            this.apiElements = getProject().getConfigurations().getByName(sourceSet.getApiElementsConfigurationName());
            this.foundFirst = foundFirst;
            this.component = (AdhocComponentWithVariants) getProject().getComponents().getByName("java");
        }

        public void withCapabilities(Configuration variant) {
            foundFirst.getOutgoing().getCapabilities().forEach(capability ->
                variant.getOutgoing().capability(capability)
            );
        }

        public void publishWithVariants(Configuration variant) {
            withCapabilities(variant);
            component.addVariantsFromConfiguration(variant, ConfigurationVariantDetails::mapToOptional);
        }

        public AdhocComponentWithVariants getComponent() {
            return component;
        }

        public SourceSet getSourceSet() {
            return sourceSet;
        }

        public Configuration getRuntimeElements() {
            return runtimeElements;
        }

        public Configuration getApiElements() {
            return apiElements;
        }

        public void modifyOutgoing(Action<ConfigurationPublications> action) {
            action.execute(apiElements.getOutgoing());
            action.execute(runtimeElements.getOutgoing());
            var sourcesName = sourceSet.getSourcesElementsConfigurationName();
            var javadocName = sourceSet.getJavadocElementsConfigurationName();
            getProject().getConfigurations().configureEach(configuration -> {
                if (configuration.getName().equals(sourcesName) || configuration.getName().equals(javadocName)) {
                    action.execute(configuration.getOutgoing());
                }
            });
        }
    }

    public static void forSourceSetFeatures(Project project, List<String> sourceSetNames, boolean needsClassesResources, Action<List<Context>> action) {
        AtomicInteger counter = new AtomicInteger(sourceSetNames.size());
        Context[] contexts = new Context[sourceSetNames.size()];
        for (int i = 0; i < sourceSetNames.size(); i++) {
            String sourceSetName = sourceSetNames.get(i);
            int finalI = i;
            forSourceSetFeature(project, sourceSetName, needsClassesResources, context -> {
                contexts[finalI] = context;
                if (counter.decrementAndGet() == 0) {
                    action.execute(List.of(contexts));
                }
            });
        }
    }

    public static void forSourceSetFeature(Project project, String sourceSetName, boolean needsClassesResources, Action<Context> action) {
        var sourceSet = project.getExtensions().getByType(SourceSetContainer.class).getByName(sourceSetName);
        AtomicBoolean foundRuntimeElements = new AtomicBoolean(false);
        AtomicBoolean foundApiElements = new AtomicBoolean(false);
        AtomicBoolean executed = new AtomicBoolean(false);
        AtomicBoolean foundApiClasses = new AtomicBoolean(false);
        AtomicBoolean foundRuntimeClasses = new AtomicBoolean(false);
        AtomicBoolean foundRuntimeResources = new AtomicBoolean(false);
        AtomicReference<Configuration> foundFirst = new AtomicReference<>();
        Runnable checkedAction = () -> {
            if (foundRuntimeElements.get() && (!needsClassesResources || (foundRuntimeClasses.get() && foundRuntimeResources.get())) &&
                foundApiElements.get() && foundApiClasses.get() &&
                !executed.get()) {
                executed.set(true);
                action.execute(project.getObjects().newInstance(Context.class, sourceSet, foundFirst.get()));
            }
        };
        Action<Configuration> configAction = configuration -> {
            if (configuration.getName().equals(sourceSet.getRuntimeElementsConfigurationName())) {
                foundRuntimeElements.set(true);
                if (foundFirst.get() == null) {
                    foundFirst.set(configuration);
                }
                if (needsClassesResources) {
                    Action<ConfigurationVariant> variantsAction = variant -> {
                        var name = variant.getName();
                        if (name.equals("classes")) {
                            foundRuntimeClasses.set(true);
                            checkedAction.run();
                        } else if (name.equals("resources")) {
                            foundRuntimeResources.set(true);
                            checkedAction.run();
                        }
                    };
                    configuration.getOutgoing().getVariants().all(variantsAction);
                    configuration.getOutgoing().getVariants().whenObjectAdded(variantsAction);
                }
                checkedAction.run();
            } else if (configuration.getName().equals(sourceSet.getApiElementsConfigurationName())) {
                foundApiElements.set(true);
                if (foundFirst.get() == null) {
                    foundFirst.set(configuration);
                }
                Action<ConfigurationVariant> variantsAction = variant -> {
                    var name = variant.getName();
                    if (name.equals("classes")) {
                        foundApiClasses.set(true);
                        checkedAction.run();
                    }
                };
                configuration.getOutgoing().getVariants().all(variantsAction);
                configuration.getOutgoing().getVariants().whenObjectAdded(variantsAction);
                checkedAction.run();
            }
        };
        // Ideally would be lazy -- we'll change it when we can
        project.getConfigurations().all(configAction);
        project.getConfigurations().whenObjectAdded(configAction);
    }
}
