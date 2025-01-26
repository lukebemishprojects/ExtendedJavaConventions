package dev.lukebemish.extendedjavaconventions;

import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.TaskDependency;
import org.jetbrains.annotations.Nullable;

import javax.inject.Inject;
import java.io.File;
import java.util.Date;

public class LazyDirectoryArtifact implements PublishArtifact {
    private final String type;
    private final Provider<File> fileProvider;
    private final TaskDependency taskDependency;

    @Inject
    public LazyDirectoryArtifact(String type, Provider<File> fileProvider, TaskDependency taskDependency) {
        this.type = type;
        this.fileProvider = fileProvider;
        this.taskDependency = taskDependency;
    }

    @Override
    public String getName() {
        return getFile().getName();
    }

    @Override
    public String getExtension() {
        return "";
    }

    @Override
    public String getType() {
        return this.type;
    }

    @Override
    public @Nullable String getClassifier() {
        return null;
    }

    @Override
    public File getFile() {
        return fileProvider.get();
    }

    @Override
    public @Nullable Date getDate() {
        return null;
    }

    @Override
    public TaskDependency getBuildDependencies() {
        return taskDependency;
    }
}
