package dev.novastep.core.modloader.model;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ExecutionPlan {

    public final String      mainClass;
    public final List<Path>  additionalClasspath;
    public final List<String> additionalJvmArgs;
    public final List<String> additionalGameArgs;
    public final boolean     useModulePath;

    public ExecutionPlan(
            String mainClass,
            List<Path> additionalClasspath,
            List<String> additionalJvmArgs,
            List<String> additionalGameArgs,
            boolean useModulePath) {
        this.mainClass           = mainClass;
        this.additionalClasspath = Collections.unmodifiableList(new ArrayList<>(additionalClasspath));
        this.additionalJvmArgs   = Collections.unmodifiableList(new ArrayList<>(additionalJvmArgs));
        this.additionalGameArgs  = Collections.unmodifiableList(new ArrayList<>(additionalGameArgs));
        this.useModulePath       = useModulePath;
    }

    public static ExecutionPlan fromVersionJson(
            String mainClass,
            List<Path> classpath,
            List<String> jvmArgs,
            List<String> gameArgs) {
        return new ExecutionPlan(mainClass, classpath, jvmArgs, gameArgs, false);
    }

    public static ExecutionPlan forBootstrapLauncher(
            String mainClass,
            List<Path> classpath,
            List<String> jvmArgs,
            List<String> gameArgs) {
        return new ExecutionPlan(mainClass, classpath, jvmArgs, gameArgs, true);
    }
}
