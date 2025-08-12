package dev.lukebemish.extendedjavaconventions;

import org.gradle.api.Action;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Nested;

import javax.inject.Inject;

public abstract class ModuleInfoSpec {
    @Inject
    public ModuleInfoSpec() {
        getOpen().convention(false);
        getIncludeTransitive().convention(false);
    }

    @Inject
    protected abstract ObjectFactory getObjectFactory();

    @Input
    public abstract Property<String> getName();

    @Input
    public abstract Property<Boolean> getOpen();

    @Input
    public abstract Property<Boolean> getIncludeTransitive();

    @Nested
    public abstract ListProperty<PackageSpec> getExports();

    @Nested
    public abstract ListProperty<PackageSpec> getOpens();

    @Input
    public abstract ListProperty<String> getUses();

    @Nested
    public abstract ListProperty<ProvidesSpec> getProvides();

    @Nested
    public abstract ListProperty<RequiresSpec> getRequires();

    public ProvidesSpec provides(String service) {
        ProvidesSpec spec = getObjectFactory().newInstance(ProvidesSpec.class);
        spec.getService().set(service);
        getProvides().add(spec);
        return spec;
    }

    public PackageSpec exports(String pkg) {
        PackageSpec spec = getObjectFactory().newInstance(PackageSpec.class);
        spec.getPackage().set(pkg);
        getExports().add(spec);
        return spec;
    }

    public PackageSpec opens(String pkg) {
        PackageSpec spec = getObjectFactory().newInstance(PackageSpec.class);
        spec.getPackage().set(pkg);
        getOpens().add(spec);
        return spec;
    }

    public void uses(String service) {
        getUses().add(service);
    }

    public RequiresSpec requires(String module) {
        return requires(module, spec -> {});
    }

    public RequiresSpec requires(String module, Action<RequiresSpec> action) {
        RequiresSpec spec = getObjectFactory().newInstance(RequiresSpec.class);
        spec.getModule().set(module);
        action.execute(spec);
        getRequires().add(spec);
        return spec;
    }

    public static abstract class RequiresSpec {
        @Inject
        public RequiresSpec() {
            getStatic().convention(false);
            getTransitive().convention(false);
        }

        @Input
        public abstract Property<String> getModule();

        @Input
        public abstract Property<Boolean> getStatic();

        @Input
        public abstract Property<Boolean> getTransitive();
    }

    public static abstract class PackageSpec {
        @Input
        public abstract Property<String> getPackage();

        @Input
        public abstract ListProperty<String> getModules();

        public PackageSpec to(String module, String... modules) {
            getModules().add(module);
            for (String mod : modules) {
                getModules().add(mod);
            }
            return this;
        }
    }

    public static abstract class ProvidesSpec {
        @Input
        public abstract Property<String> getService();

        @Input
        public abstract ListProperty<String> getImplementations();

        public ProvidesSpec with(String implementation, String... implementations) {
            getImplementations().add(implementation);
            for (String impl : implementations) {
                getImplementations().add(impl);
            }
            return this;
        }
    }
}
