package dev.lukebemish.extendedjavaconventions;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.initialization.Settings;

public class ExtendedJavaConventionsPlugin implements Plugin<Object> {
    @Override
    public void apply(Object target) {
        if (target instanceof Project project) {
            project.getPluginManager().apply(ExtendedJavaConventionsProjectPlugin.class);
        } else if (target instanceof Settings settings) {
            settings.getGradle().getLifecycle().beforeProject(project -> {
                project.getPluginManager().apply(ExtendedJavaConventionsProjectPlugin.class);
            });
        } else {
            throw new IllegalArgumentException("Unsupported target type: " + target.getClass().getName());
        }
    }
}
