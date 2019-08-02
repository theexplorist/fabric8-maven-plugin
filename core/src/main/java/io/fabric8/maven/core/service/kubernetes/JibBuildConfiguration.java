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

import com.google.cloud.tools.jib.api.Credential;

import io.fabric8.maven.docker.config.Arguments;
import io.fabric8.maven.docker.util.DeepCopy;
import io.fabric8.maven.docker.util.Logger;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class JibBuildConfiguration {

    private Map<String, String> envMap;

    private Credential credential;

    private List<String> ports;

    private String from;

    private String target;

    private Path fatJarPath;

    private Arguments entrypoint;

    private String targetDir;

    private String outputDir;

    private JibBuildConfiguration() {}

    public Arguments getEntryPoint() {return entrypoint;}

    public String getTargetDir() {return targetDir;}

    public String getOutputDir() {return outputDir;}

    public Map<String, String> getEnvMap() {
        return envMap;
    }

    public Credential getCredential() {return credential; }

    public List<String> getPorts() {return ports;}

    public String getFrom() {return from;}

    public String getTargetImage() {return target;}


    public Path getFatJar() {return fatJarPath;}

    public static class Builder {
        private final JibBuildConfiguration configutil;
        private final Logger logger;

        public Builder(Logger logger) {
            this(null, logger);
        }

        public Builder(JibBuildConfiguration that, Logger logger) {
            this.logger = logger;
            if (that == null) {
                this.configutil = new JibBuildConfiguration();
            } else {
                this.configutil = DeepCopy.copy(that);
            }
        }

        public Builder envMap(Map<String, String> envMap) {
            configutil.envMap = envMap;
            return this;
        }

        public Builder credential(Credential credential) {
            configutil.credential = credential;
            return this;
        }

        public Builder ports(List<String> ports) {
            configutil.ports = ports;
            return this;
        }

        public Builder from(String from) {
            configutil.from = from;
            return this;
        }

        public Builder targetImage(String imageName) {
            configutil.target = imageName;
            return this;
        }

        public Builder entrypoint(Arguments entrypoint) {
            configutil.entrypoint = entrypoint;
            return this;
        }

        public Builder buildDirectory(String buildDir) {
            configutil.fatJarPath = JibBuildServiceUtil.getFatJar(buildDir, logger);
            return this;
        }

        public Builder targetDir(String targetDir) {
            configutil.targetDir = targetDir;
            return this;
        }

        public Builder outputDir(String outputDir) {
            configutil.outputDir = outputDir;
            return this;
        }

        public JibBuildConfiguration build() {
            return configutil;
        }
    }
}