package dev.lukebemish.extendedjavaconventions;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.jvm.toolchain.JavaLauncher;
import org.gradle.process.ExecOperations;
import org.jetbrains.annotations.Nullable;

import javax.inject.Inject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@CacheableTask
public abstract class GenerateModuleInfoTask extends DefaultTask {
    @InputFiles
    @PathSensitive(PathSensitivity.NAME_ONLY)
    public abstract ConfigurableFileCollection getRequireStatic();

    @InputFiles
    @PathSensitive(PathSensitivity.NAME_ONLY)
    public abstract ConfigurableFileCollection getRequireTransitive();

    @InputFiles
    @PathSensitive(PathSensitivity.NAME_ONLY)
    public abstract ConfigurableFileCollection getRequireRuntime();

    @OutputDirectory
    public abstract DirectoryProperty getOutputDirectory();

    @Nested
    public abstract Property<JavaLauncher> getJavaLauncher();

    @Inject
    public GenerateModuleInfoTask() {}

    @Inject
    protected abstract ExecOperations getExecOperations();

    @Nested
    public abstract Property<ModuleInfoSpec> getModuleInfoSpec();

    private record ListedModule(
        String name,
        @Nullable String version,
        @Nullable URL location
    ) {
        private static final Pattern PATTERN = Pattern.compile(
                "([^\\s@]+)(?:@(\\S+))?(?:\\s+(\\S+))?.*"
        );

        static ListedModule of(String line) {
            var matcher = PATTERN.matcher(line);
            if (!matcher.matches()) {
                throw new IllegalArgumentException("Invalid module line: " + line);
            }
            String name = matcher.group(1);
            String version = matcher.group(2);
            URL location;
            try {
                if (matcher.group(3) == null) {
                    location = null;
                } else {
                    location = new URI(matcher.group(3)).toURL();
                }
            } catch (MalformedURLException | URISyntaxException e) {
                throw new IllegalArgumentException("Invalid module line: " + line, e);
            }
            return new ListedModule(name, version, location);
        }
    }

    private List<ListedModule> getListedModules(ConfigurableFileCollection collection) {
        var out = new ByteArrayOutputStream();
        getExecOperations().exec(spec -> {
            spec.setExecutable(getJavaLauncher().get().getExecutablePath());
            spec.setArgs(List.of(
                "-p",
                collection.getAsPath(),
                "--list-modules"
            ));
            spec.setStandardOutput(out);
        }).rethrowFailure().assertNormalExitValue();
        return out.toString(StandardCharsets.UTF_8).lines()
            .filter(s -> !s.isBlank())
            .map(ListedModule::of)
            .filter(m -> m.location() != null)
            .toList();
    }

    @TaskAction
    public void run() throws IOException {
        var moduleInfoFile = getOutputDirectory().get().getAsFile().toPath().resolve("module-info.java");
        Files.createDirectories(moduleInfoFile.getParent());
        var requireRuntime = getListedModules(getRequireRuntime());
        var requireStatic = getListedModules(getRequireStatic());
        var requireTransitive = getListedModules(getRequireTransitive());

        var runtimeNames = requireRuntime.stream()
            .map(ListedModule::name)
            .collect(Collectors.toSet());
        var staticNames = requireStatic.stream()
            .map(ListedModule::name)
            .collect(Collectors.toSet());
        var transitiveNames = requireTransitive.stream()
            .map(ListedModule::name)
            .collect(Collectors.toSet());

        var spec = getModuleInfoSpec().get();

        var builder = new StringBuilder();
        if (spec.getOpen().get()) {
            builder.append("open ");
        }
        builder.append("module ").append(spec.getName().get()).append(" {\n");

        // require static transitive -- static and transitive, but not runtime
        var requireStaticTransitiveNames = new HashSet<>(staticNames);
        requireStaticTransitiveNames.removeAll(runtimeNames);
        requireStaticTransitiveNames.removeIf(n -> !transitiveNames.contains(n));

        // require transitive -- transitive and runtime
        var requireTransitiveNames = new HashSet<>(runtimeNames);
        requireTransitiveNames.removeIf(n -> !transitiveNames.contains(n));

        // require static -- static and not runtime or transitive
        var requireStaticNames = new HashSet<>(staticNames);
        requireStaticNames.removeAll(runtimeNames);
        requireStaticNames.removeAll(transitiveNames);

        // require -- runtime and not transitive
        var requireNames = new HashSet<>(runtimeNames);
        requireNames.removeAll(transitiveNames);

        for (var requires : spec.getRequires().get()) {
            var name = requires.getModule().get();
            if (requires.getStatic().get()) {
                if (requires.getTransitive().get()) {
                    requireStaticTransitiveNames.add(name);
                } else {
                    requireStaticNames.add(name);
                }
            } else if (requires.getTransitive().get()) {
                requireTransitiveNames.add(name);
            } else {
                requireNames.add(name);
            }
        }

        for (var name : requireStaticTransitiveNames) {
            writeLine(builder, "requires static transitive", name);
        }

        for (var name : requireTransitiveNames) {
            writeLine(builder, "requires transitive", name);
        }

        for (var name : requireStaticNames) {
            writeLine(builder, "requires static", name);
        }

        for (var name : requireNames) {
            writeLine(builder, "requires", name);
        }

        if (!spec.getUses().get().isEmpty()) {
            builder.append("\n");
            for (var uses : spec.getUses().get()) {
                builder.append("    uses ").append(uses).append(";\n");
            }
        }
        if (!spec.getProvides().get().isEmpty()) {
            builder.append("\n");
            for (var provides : spec.getProvides().get()) {
                builder.append("    provides ").append(provides.getService().get()).append(" with ");
                var impls = provides.getImplementations().get();
                builder.append(impls.stream().map(Object::toString).collect(Collectors.joining(", ")));
                builder.append(";\n");
            }
        }
        if (!spec.getExports().get().isEmpty()) {
            builder.append("\n");
            for (var exports : spec.getExports().get()) {
                builder.append("    exports ").append(exports.getPackage().get());
                var modules = exports.getModules().get();
                if (!modules.isEmpty()) {
                    builder.append(" to ");
                    builder.append(modules.stream().sorted(Comparator.naturalOrder()).collect(Collectors.joining(", ")));
                }
                builder.append(";\n");
            }
        }
        if (!spec.getOpens().get().isEmpty()) {
            builder.append("\n");
            for (var opens : spec.getOpens().get()) {
                builder.append("    opens ").append(opens.getPackage().get());
                var modules = opens.getModules().get();
                if (!modules.isEmpty()) {
                    builder.append(" to ");
                    builder.append(modules.stream().sorted(Comparator.naturalOrder()).collect(Collectors.joining(", ")));
                }
                builder.append(";\n");
            }
        }

        builder.append("}\n");
        Files.writeString(moduleInfoFile, builder.toString(), StandardCharsets.UTF_8);
    }

    private void writeLine(StringBuilder builder, String prefix, String module) {
        builder.append("    ").append(prefix).append(" ");
        builder.append(module);
        // versions not used at present because javac handles this -- unfortunately left out for runtime-only dependencies!
        builder.append(";\n");
    }
}
