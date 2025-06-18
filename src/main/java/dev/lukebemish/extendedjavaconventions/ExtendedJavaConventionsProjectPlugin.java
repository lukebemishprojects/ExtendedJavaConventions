package dev.lukebemish.extendedjavaconventions;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
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
        });
    }

    @Inject
    protected abstract ProviderFactory getProviders();

    private boolean getProperty(String property) {
        return getProviders().gradleProperty(property).map(Boolean::valueOf).orElse(false).get();
    }
}
