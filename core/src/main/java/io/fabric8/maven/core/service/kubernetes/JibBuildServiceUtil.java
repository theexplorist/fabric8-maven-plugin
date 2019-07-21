package io.fabric8.maven.core.service.kubernetes;

import com.google.cloud.tools.jib.api.*;
import com.google.cloud.tools.jib.registry.credentials.DockerConfigCredentialRetriever;
import io.fabric8.maven.core.model.Dependency;
import io.fabric8.maven.core.model.GroupArtifactVersion;
import io.fabric8.maven.core.service.BuildService;
import io.fabric8.maven.core.service.Fabric8ServiceException;
import io.fabric8.maven.core.util.FatJarDetector;
import io.fabric8.maven.docker.access.AuthConfig;
import io.fabric8.maven.docker.config.AssemblyConfiguration;
import io.fabric8.maven.docker.config.BuildImageConfiguration;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.docker.config.RegistryAuthConfiguration;
import io.fabric8.maven.docker.service.RegistryService;
import io.fabric8.maven.docker.util.ImageName;
import org.apache.maven.Maven;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;


import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class JibBuildServiceUtil {

    private JibBuildServiceUtil() {}

    public static void buildImage(JibBuildConfiguration buildConfiguration) throws InvalidImageReferenceException {

        String fromImage = buildConfiguration.getFrom();
        String targetImage = buildConfiguration.getTargetImage();
        Credential credential = buildConfiguration.getCredential();
        Map<String, String> envMap  = buildConfiguration.getEnvMap();
        List<String> portList = buildConfiguration.getPorts();
        Set<Port> portSet = getPortSet(portList);

        List<String> entrypointList = new ArrayList<>();
        if(buildConfiguration.getEntryPoint() != null) {
            entrypointList = buildConfiguration.getEntryPoint().asStrings();
        }
        buildImage(fromImage, targetImage, envMap, credential, portSet, buildConfiguration.getFatJar(), entrypointList, buildConfiguration.getTargetDir(), buildConfiguration.getOutputDir());
    }

    private static Set<Port> getPortSet(List<String> ports) {

        Set<Port> portSet = new HashSet<Port>();
        for(String port : ports) {
            portSet.add(Port.tcp(Integer.parseInt(port)));
        }

        return portSet;
    }

    protected static JibContainer buildImage(String baseImage, String targetImage, Map<String, String> envMap, Credential credential, Set<Port> portSet, Path fatJar, List<String> entrypointList, String targetDir, String outputDir) throws InvalidImageReferenceException {

        JibContainerBuilder contBuild = Jib.from(baseImage);

        if (!envMap.isEmpty()) {
            contBuild = contBuild.setEnvironment(envMap);
        }

        if (!portSet.isEmpty()) {
            contBuild = contBuild.setExposedPorts(portSet);
        }

        if (fatJar != null) {

            String jarPath = targetDir + "/app.jar";
            contBuild = contBuild
                    .addLayer(LayerConfiguration.builder().addEntry(fatJar, AbsoluteUnixPath.get(jarPath)).build());
        }

        if(!entrypointList.isEmpty()) {
            contBuild = contBuild.setEntrypoint(entrypointList);
        }

        if (credential != null) {
            String username = credential.getUsername();
            String password = credential.getPassword();

        try {
                return contBuild.containerize(
                        Containerizer.to(RegistryImage.named(targetImage)
                                .addCredential(username, password)));
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        } else {

            try {
                return contBuild.containerize(Containerizer.to(TarImage.named(targetImage).saveTo(Paths.get(outputDir + "/" + targetImage + ".tar"))));
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
    }

    public static JibBuildConfiguration getJibBuildConfiguration(BuildService.BuildServiceConfig config, ImageConfiguration imageConfiguration, String fullImageName) throws MojoExecutionException {
        BuildImageConfiguration buildImageConfiguration = imageConfiguration.getBuildConfiguration();

        RegistryService.RegistryConfig registryConfig = config.getDockerBuildContext().getRegistryConfig();

        String targetDir = buildImageConfiguration.getAssemblyConfiguration().getTargetDir();

        String outputDir = config.getDockerMojoParameters().getOutputDirectory();

        MavenProject project = config.getDockerMojoParameters().getProject();

        if(targetDir == null) {
            targetDir = "/app";
        }

        AuthConfig authConfig = registryConfig.getAuthConfigFactory()
                .createAuthConfig(true, true, registryConfig.getAuthConfig(),
                        registryConfig.getSettings(), null, registryConfig.getRegistry());


        JibBuildConfiguration.Builder jibBuildConfigurationBuilder = new JibBuildConfiguration.Builder().from(buildImageConfiguration.getFrom())
                                                                                                        .envMap(buildImageConfiguration.getEnv())
                                                                                                        .ports(buildImageConfiguration.getPorts())
                                                                                                        .entrypoint(buildImageConfiguration.getEntryPoint())
                                                                                                        .targetImage(fullImageName)
                                                                                                        .pushRegistry(registryConfig.getRegistry())
                                                                                                        .targetDir(targetDir)
                                                                                                        .outputDir(outputDir)
                                                                                                        .buildDirectory(config.getBuildDirectory());

        if(authConfig != null) {
            jibBuildConfigurationBuilder.credential(Credential.from(authConfig.getUsername(), authConfig.getPassword()));
        }

        return jibBuildConfigurationBuilder.build();
    }

    public static Path getFatJar(String buildDir) {
        FatJarDetector fatJarDetector = new FatJarDetector(buildDir);
        try {
            FatJarDetector.Result result = fatJarDetector.scan();
            if(result != null) {
                return result.getArchiveFile().toPath();
            }

        } catch (MojoExecutionException e) {
            // TODO log.err("MOJO EXEC EXCEPTION!")
            throw new UnsupportedOperationException();
        }
        return null;
    }

    private static String extractBaseImage(BuildImageConfiguration buildImgConfig) {

        String fromImage = buildImgConfig.getFrom();
        if (fromImage == null) {
            AssemblyConfiguration assemblyConfig = buildImgConfig.getAssemblyConfiguration();
            if (assemblyConfig == null) {
                fromImage = "busybox";
            }
        }
        return fromImage;
    }

    public static List<Path> getDependencies(boolean transitive, MavenProject project) {
        final Set<Artifact> artifacts = transitive ?
                project.getArtifacts() : project.getDependencyArtifacts();

        final List<Dependency> dependencies = new ArrayList<>();

        for (Artifact artifact : artifacts) {
            dependencies.add(
                    new Dependency(new GroupArtifactVersion(artifact.getGroupId(),
                            artifact.getArtifactId(),
                            artifact.getVersion()),
                            artifact.getType(),
                            artifact.getScope(),
                            artifact.getFile()));
        }

        final List<Path> dependenciesPath = new ArrayList<>();
        for(Dependency dep : dependencies) {
            File depLocation = dep.getLocation();
            Path depPath = depLocation.toPath();
            dependenciesPath.add(depPath);
        }
        return dependenciesPath;
    }
}