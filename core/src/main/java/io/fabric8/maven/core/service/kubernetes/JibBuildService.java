package io.fabric8.maven.core.service.kubernetes;

import com.google.cloud.tools.jib.api.Credential;
import io.fabric8.maven.core.service.BuildService;
import io.fabric8.maven.docker.access.AuthConfig;
import io.fabric8.maven.docker.config.BuildImageConfiguration;
import io.fabric8.maven.docker.config.ImageConfiguration;
import io.fabric8.maven.docker.service.RegistryService;
import io.fabric8.maven.docker.service.ServiceHub;
import io.fabric8.maven.docker.util.EnvUtil;
import io.fabric8.maven.docker.util.ImageName;
import io.fabric8.maven.docker.util.Logger;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;

import java.awt.*;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class JibBuildService implements BuildService {

    // TODO ADD LOGGING
    private BuildServiceConfig config;

    private Logger log;
    private JibBuildService() { }

    public JibBuildService (BuildServiceConfig config, Logger log) {
        Objects.requireNonNull(config, "config");
        this.config = config;
        this.log = log;
    }

    @Override
    public void build(ImageConfiguration imageConfiguration) {
       try {
           List<String> tags = imageConfiguration.getBuildConfiguration().getTags();

           JibBuildConfiguration jibBuildConfiguration;
           String fullName = "";
           if (tags.size() > 0) {
               for (String tag : tags) {
                   if (tag != null) {
                        fullName = new ImageName(imageConfiguration.getName(), tag).getFullName();
                   }
               }
           } else {
               fullName = new ImageName(imageConfiguration.getName(), null).getFullName();
           }

           jibBuildConfiguration = JibBuildServiceUtil.getJibBuildConfiguration(config, imageConfiguration, fullName);
           JibBuildServiceUtil.buildImage(jibBuildConfiguration, log);
       } catch (Exception ex) {
           throw new UnsupportedOperationException();
       }
    }



    @Override
    public void postProcess(BuildServiceConfig config) {

    }
}