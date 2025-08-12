package dev.lukebemish.extendedjavaconventions;

import org.gradle.api.Action;
import org.gradle.api.tasks.SourceSet;

import javax.inject.Inject;

public abstract class SourceSetExtension {
    private final ExtendedJavaConventions extendedJavaConventions;
    private final SourceSet owner;

    @Inject
    public SourceSetExtension(ExtendedJavaConventions extendedJavaConventions, SourceSet owner) {
        this.extendedJavaConventions = extendedJavaConventions;
        this.owner = owner;
    }

    public void local() {
        extendedJavaConventions.local(owner);
    }

    public void generateModuleInfo(Action<ModuleInfoSpec> action) {
        extendedJavaConventions.generateModuleInfo(owner, action);
    }
}
