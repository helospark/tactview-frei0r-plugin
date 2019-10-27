package com.helospark.freiorplugin;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.apache.commons.lang3.SystemUtils;

import com.helospark.lightdi.annotation.Component;
import com.helospark.lightdi.annotation.Value;

@Component
public class FreiorPluginProvider {
    private String freiorPluginLocations;

    public FreiorPluginProvider(@Value("${freior.plugin.locations}") String freiorPluginLocations) {
        this.freiorPluginLocations = freiorPluginLocations;
    }

    public Set<File> getFreiorPlugins() {
        String freiorPath = System.getenv("FREI0R_PATH");

        Set<File> plugins = new LinkedHashSet<>();
        if (SystemUtils.IS_OS_LINUX) {
            Stream.of("/usr/lib/frei0r-1/", "/usr/local/lib/frei0r-1/", System.getProperty("user.home") + "/.frei0r-1/lib/")
                    .flatMap(a -> searchPath(a).stream())
                    // distinct
                    .forEach(a -> plugins.add(a));

            if (freiorPath != null) {
                Arrays.stream(freiorPath.split(":"))
                        .forEach(a -> plugins.addAll(searchPath(a)));
            }
        } else if (SystemUtils.IS_OS_WINDOWS) {
            if (freiorPath != null) {
                Arrays.stream(freiorPath.split(";"))
                        .forEach(a -> plugins.addAll(searchPath(a)));
            }
        } else if (SystemUtils.IS_OS_MAC) {
            if (freiorPath != null) {
                Arrays.stream(freiorPath.split(":"))
                        .forEach(a -> plugins.addAll(searchPath(a)));
            }
        }

        Arrays.stream(freiorPluginLocations.split(":"))
                .forEach(a -> plugins.addAll(searchPath(a)));

        return plugins;
    }

    private List<File> searchPath(String pathString) {
        List<File> result = new ArrayList<>();
        File path = new File(pathString);
        if (!path.exists()) {
            return Collections.emptyList();
        }
        if (path.isFile() && (path.getAbsolutePath().endsWith(".so") || path.getAbsolutePath().endsWith(".dll"))) {
            return List.of(path);
        }
        if (path.isDirectory()) {
            for (File childFile : path.listFiles()) {
                result.addAll(searchPath(childFile.getAbsolutePath()));
            }
        }
        return result;
    }

}
