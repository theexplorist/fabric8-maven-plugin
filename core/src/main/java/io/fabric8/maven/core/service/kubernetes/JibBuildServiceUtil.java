/**
 * Copyright 2016 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version
 * 2.0 (the "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.  See the License for the specific language governing
 * permissions and limitations under the License.
 */
package io.fabric8.maven.core.service.kubernetes;

import com.google.cloud.tools.jib.api.AbsoluteUnixPath;
import com.google.cloud.tools.jib.api.CacheDirectoryCreationException;
import com.google.cloud.tools.jib.api.Containerizer;
import com.google.cloud.tools.jib.api.Credential;
import com.google.cloud.tools.jib.api.ImageReference;
import com.google.cloud.tools.jib.api.InvalidImageReferenceException;
import com.google.cloud.tools.jib.api.Jib;
import com.google.cloud.tools.jib.api.JibContainer;
import com.google.cloud.tools.jib.api.JibContainerBuilder;
import com.google.cloud.tools.jib.api.LayerConfiguration;
import com.google.cloud.tools.jib.api.Port;
import com.google.cloud.tools.jib.api.RegistryException;
import com.google.cloud.tools.jib.api.RegistryImage;
import com.google.cloud.tools.jib.api.TarImage;
import io.fabric8.maven.core.service.BuildService;
import io.fabric8.maven.core.util.FatJarDetector;
import io.fabric8.maven.docker.access.AuthConfig;
import io.fabric8.maven.docker.config.BuildImageConfiguration;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.docker.service.RegistryService;
import io.fabric8.maven.docker.util.Logger;
import io.fabric8.maven.docker.util.MojoParameters;
import org.apache.maven.plugin.MojoExecutionException;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.IllegalFormatException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;

public class JibBuildServiceUtil {

    private JibBuildServiceUtil() {}

    private static final String DEFAULT_JAR_NAME = "/app.jar";

    public static void buildImage(JibBuildConfiguration buildConfiguration, Logger log) throws InvalidImageReferenceException {

        String fromImage = buildConfiguration.getFrom();
        String targetImage = buildConfiguration.getTargetImage();
        Credential credential = buildConfiguration.getCredential();
        Map<String, String> envMap  = buildConfiguration.getEnvMap();
        List<String> portList = buildConfiguration.getPorts();
        Set<Port> portSet = getPortSet(portList);
        String  outputDir = buildConfiguration.getOutputDir();
        String targetDir = buildConfiguration.getTargetDir();
        Path fatJar = buildConfiguration.getFatJar();

        List<String> entrypointList = new ArrayList<>();
        if(buildConfiguration.getEntryPoint() != null) {
            entrypointList = buildConfiguration.getEntryPoint().asStrings();
        }

        buildImage(fromImage, targetImage, envMap, credential, portSet, fatJar, entrypointList, targetDir, outputDir, log);
    }

    private static Set<Port> getPortSet(List<String> ports) {

        Set<Port> portSet = new HashSet<Port>();
        for(String port : ports) {
            portSet.add(Port.tcp(Integer.parseInt(port)));
        }

        return portSet;
    }

    protected static JibContainer buildImage(String baseImage, String targetImage, Map<String, String> envMap, Credential credential, Set<Port> portSet, Path fatJar, List<String> entrypointList, String targetDir, String outputDir, Logger log) throws InvalidImageReferenceException {

        String username = "";
        String password = "";
        String imageTarName = ImageReference.parse(targetImage).getRepository().concat(".tar");

        JibContainerBuilder contBuild = Jib.from(baseImage);

        if (envMap != null) {
            contBuild = contBuild.setEnvironment(envMap);
        }

        if (portSet != null) {
            contBuild = contBuild.setExposedPorts(portSet);
        }


        if (fatJar != null) {
            String fatJarName = fatJar.getFileName().toString();
            String jarPath = targetDir + "/" + (fatJarName.isEmpty() ? DEFAULT_JAR_NAME: fatJarName);
            contBuild = contBuild
                    .addLayer(LayerConfiguration.builder().addEntry(fatJar, AbsoluteUnixPath.get(jarPath)).build());
        }

        if(!entrypointList.isEmpty()) {
            contBuild = contBuild.setEntrypoint(entrypointList);
        }

        if (credential != null) {
            username = credential.getUsername();
            password = credential.getPassword();
        }

        RegistryImage registryImage = RegistryImage.named(targetImage);
        TarImage tarImage = TarImage.named(targetImage).saveTo(Paths.get(outputDir + "/" + imageTarName));

        try {
            Containerizer containerizer = Containerizer.to(registryImage.addCredential(username, password));
            JibContainer jibRegistryImageContainer = contBuild.containerize(containerizer);
            log.info("Image %s successfully built and pushed.", targetImage);
            return jibRegistryImageContainer;
        } catch (Exception re) {

            if(re instanceof RegistryException) {
                log.warn("Registry Exception occured : %s", re.getMessage());
                log.warn("Credentials are probably either not configured or are incorrect.");
                log.info("Building Image Tarball at %s." + imageTarName);
                try {
                    JibContainer jibTarImageContainer = contBuild.containerize(Containerizer.to(tarImage));
                    log.info(" %s successfully built.", tarImage);
                    return jibTarImageContainer;
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            } else if(re instanceof java.util.concurrent.ExecutionException) {
                log.warn("Execution Exception occured : %s", re.getMessage());
                log.warn("The system is probably offline");
                log.info("Building Image Tarball at %s in offline mode." + imageTarName);
                Containerizer containerizer = Containerizer.to(tarImage);
                containerizer.setOfflineMode(true);
                try {
                    JibContainer jibTarImageContainerOffline = contBuild.containerize(containerizer);
                    log.info(" %s successfully built offline.", tarImage);
                    return jibTarImageContainerOffline;
                } catch (Exception e) {
                    log.info("Exception occured : %s", e.getMessage());
                    throw new IllegalStateException(e);
                }
            }
        }
        return null;
    }

    public static JibBuildConfiguration getJibBuildConfiguration(BuildService.BuildServiceConfig config, BuildImageConfiguration buildImageConfiguration, String fullImageName, Logger log) throws MojoExecutionException {

        io.fabric8.maven.docker.service.BuildService.BuildContext dockerBuildContext = config.getDockerBuildContext();
        RegistryService.RegistryConfig registryConfig = dockerBuildContext.getRegistryConfig();

        String targetDir = buildImageConfiguration.getAssemblyConfiguration().getTargetDir();

        MojoParameters mojoParameters = config.getDockerMojoParameters();
        String outputDir = mojoParameters.getOutputDirectory();

        if(targetDir == null) {
            targetDir = "/deployments";
        }

        AuthConfig authConfig = registryConfig.getAuthConfigFactory()
                .createAuthConfig(true, true, registryConfig.getAuthConfig(),
                        registryConfig.getSettings(), null, registryConfig.getRegistry());

        JibBuildConfiguration.Builder jibBuildConfigurationBuilder = new JibBuildConfiguration.Builder(log).from(buildImageConfiguration.getFrom())
                                                                                                        .envMap(buildImageConfiguration.getEnv())
                                                                                                        .ports(buildImageConfiguration.getPorts())
                                                                                                        .entrypoint(buildImageConfiguration.getEntryPoint())
                                                                                                        .targetImage(fullImageName)
                                                                                                        .targetDir(targetDir)
                                                                                                        .outputDir(outputDir)
                                                                                                        .buildDirectory(config.getBuildDirectory());
        if(authConfig != null) {
            jibBuildConfigurationBuilder.credential(Credential.from(authConfig.getUsername(), authConfig.getPassword()));
        }

        return jibBuildConfigurationBuilder.build();
    }

    public static Path getFatJar(String buildDir, Logger log) {
        FatJarDetector fatJarDetector = new FatJarDetector(buildDir);
        try {
            FatJarDetector.Result result = fatJarDetector.scan();
            if(result != null) {
                return result.getArchiveFile().toPath();
            }

        } catch (MojoExecutionException e) {
            log.error("MOJO Execution exception occured: %s", e);
            throw new UnsupportedOperationException();
        }
        return null;
    }

}

