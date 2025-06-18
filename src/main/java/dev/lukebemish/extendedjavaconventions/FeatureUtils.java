package dev.lukebemish.extendedjavaconventions;

import org.gradle.api.Action;
import org.gradle.api.NamedDomainObjectProvider;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationPublications;
import org.gradle.api.component.AdhocComponentWithVariants;
import org.gradle.api.component.ConfigurationVariantDetails;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;

import javax.inject.Inject;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

class FeatureUtils {
    private FeatureUtils() {}

    public static abstract class Context {
        private final NamedDomainObjectProvider<Configuration> runtimeElements;
        private final NamedDomainObjectProvider<Configuration> apiElements;
        private final Configuration firstFound;
        private final SourceSet sourceSet;
        private final AdhocComponentWithVariants component;

        @Inject
        public abstract Project getProject();

        @Inject
        public Context(SourceSet sourceSet, Configuration firstFound) {
            this.sourceSet = sourceSet;
            this.firstFound = firstFound;
            this.runtimeElements = getProject().getConfigurations().named(sourceSet.getRuntimeElementsConfigurationName());
            this.apiElements = getProject().getConfigurations().named(sourceSet.getApiElementsConfigurationName());
            this.component = (AdhocComponentWithVariants) getProject().getComponents().getByName("java");
        }

        public void withCapabilities(Configuration variant) {
            firstFound.getOutgoing().getCapabilities().forEach(capability ->
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

        public NamedDomainObjectProvider<Configuration> getRuntimeElements() {
            return runtimeElements;
        }

        public NamedDomainObjectProvider<Configuration> getApiElements() {
            return apiElements;
        }

        public void modifyOutgoing(Action<ConfigurationPublications> action) {
            action.execute(apiElements.get().getOutgoing());
            action.execute(runtimeElements.get().getOutgoing());
            var sourcesName = sourceSet.getSourcesElementsConfigurationName();
            var javadocName = sourceSet.getJavadocElementsConfigurationName();
            getProject().getConfigurations().configureEach(configuration -> {
                if (configuration.getName().equals(sourcesName) || configuration.getName().equals(javadocName)) {
                    action.execute(configuration.getOutgoing());
                }
            });
        }
    }

    public static void forSourceSetFeature(Project project, String sourceSetName, Action<Context> action) {
        var sourceSet = project.getExtensions().getByType(SourceSetContainer.class).getByName(sourceSetName);
        var runtimeElementsLocated = new AtomicBoolean();
        var apiElementsLocated = new AtomicBoolean();
        var executed = new AtomicBoolean(false);
        var firstFound = new AtomicReference<Configuration>();
        Runnable onConfig = () -> {
            if (runtimeElementsLocated.get() && apiElementsLocated.get() && !executed.get()) {
                executed.set(true);
                action.execute(project.getObjects().newInstance(Context.class, sourceSet, firstFound.get()));
            }
        };
        var runtimeElements = project.getConfigurations().named(c -> c.equals(sourceSet.getRuntimeElementsConfigurationName()));
        var apiElements = project.getConfigurations().named(c -> c.equals(sourceSet.getApiElementsConfigurationName()));
        runtimeElements.whenObjectAdded(c -> {
            firstFound.updateAndGet(old -> old == null ? c : old);
            runtimeElementsLocated.set(true);
            onConfig.run();
        });
        runtimeElements.all(c -> {
            firstFound.updateAndGet(old -> old == null ? c : old);
            runtimeElementsLocated.set(true);
            onConfig.run();
        });
        apiElements.whenObjectAdded(c -> {
            firstFound.updateAndGet(old -> old == null ? c : old);
            apiElementsLocated.set(true);
            onConfig.run();
        });
        apiElements.all(c -> {
            firstFound.updateAndGet(old -> old == null ? c : old);
            apiElementsLocated.set(true);
            onConfig.run();
        });
    }
}
