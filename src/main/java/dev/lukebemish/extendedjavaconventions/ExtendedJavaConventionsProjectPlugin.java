package dev.lukebemish.extendedjavaconventions;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.attributes.AttributeCompatibilityRule;
import org.gradle.api.attributes.CompatibilityCheckDetails;
import org.gradle.api.attributes.Usage;
import org.gradle.api.provider.ProviderFactory;

import javax.inject.Inject;

public abstract class ExtendedJavaConventionsProjectPlugin implements Plugin<Project> {
    @Inject
    public ExtendedJavaConventionsProjectPlugin() {}

    @Override
    public void apply(Project target) {
        target.getPluginManager().withPlugin("java-base", p -> {
            var extension = target.getExtensions().create("extendedJavaConventions", ExtendedJavaConventions.class);
            if (getProperty(ExtendedJavaConventionsProperties.SOURCEPATH)) {
                extension.sourcepath();
            }
            if (getProperty(ExtendedJavaConventionsProperties.LOCAL)) {
                extension.local();
            }
            target.getDependencies().getAttributesSchema().attribute(Usage.USAGE_ATTRIBUTE, strategy -> {
                strategy.getCompatibilityRules().add(UsageCompatibilityRule.class);
            });
        });
    }

    @Inject
    protected abstract ProviderFactory getProviders();

    private boolean getProperty(String property) {
        return getProviders().gradleProperty(property).map(Boolean::valueOf).orElse(false).get();
    }

    public abstract static class UsageCompatibilityRule implements AttributeCompatibilityRule<Usage> {
        @Override
        public void execute(CompatibilityCheckDetails<Usage> details) {
            if (details.getConsumerValue() != null && details.getConsumerValue().getName().equals("java-api-sources")) {
                if (details.getProducerValue() == null) {
                    details.compatible();
                    return;
                }
                var producerValue = details.getProducerValue().getName();
                if (producerValue.equals(Usage.JAVA_API) || producerValue.equals(Usage.JAVA_RUNTIME)) {
                    details.compatible();
                }
            }
        }
    }
}
