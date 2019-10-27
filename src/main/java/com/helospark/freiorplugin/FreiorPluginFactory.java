package com.helospark.freiorplugin;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.helospark.freiorplugin.nativelibrary.FreiorConstants;
import com.helospark.freiorplugin.nativelibrary.FreiorLoadPluginRequest;
import com.helospark.freiorplugin.nativelibrary.FreiorNativeLibrary;
import com.helospark.freiorplugin.nativelibrary.FreiorParameter;
import com.helospark.lightdi.annotation.Bean;
import com.helospark.lightdi.annotation.Configuration;
import com.helospark.tactview.core.decoder.ImageMetadata;
import com.helospark.tactview.core.timeline.TimelineClipType;
import com.helospark.tactview.core.timeline.TimelineInterval;
import com.helospark.tactview.core.timeline.TimelineLength;
import com.helospark.tactview.core.timeline.effect.StandardEffectFactory;
import com.helospark.tactview.core.timeline.effect.TimelineEffectType;
import com.helospark.tactview.core.timeline.proceduralclip.ProceduralClipFactoryChainItem;
import com.helospark.tactview.core.timeline.proceduralclip.StandardProceduralClipFactoryChainItem;

@Configuration
public class FreiorPluginFactory {
    private static final Logger LOGGER = LoggerFactory.getLogger(FreiorPluginFactory.class);
    private static final Set<String> BLACKLISTED_PLUGINS = Set.of("Partik0l"); // crashes for unknown reasons (on size 320:184). TODO: debug why
    private FreiorPluginProvider freiorPluginProvider;
    private FreiorParameterMapper freiorParameterMapper;
    private FreiorNativeLibrary freiorNativeLibrary;

    private List<StandardEffectFactory> effectFactories = new ArrayList<>();
    private List<ProceduralClipFactoryChainItem> proceduralClips = new ArrayList<>();

    private Map<String, PluginInformation> pluginNameToPluginInformation = new HashMap<>();

    public FreiorPluginFactory(FreiorPluginProvider freiorPluginProvider, FreiorParameterMapper freiorParameterMapper, FreiorNativeLibrary freiorNativeLibrary) {
        this.freiorPluginProvider = freiorPluginProvider;
        this.freiorParameterMapper = freiorParameterMapper;
        this.freiorNativeLibrary = freiorNativeLibrary;
    }

    @PostConstruct
    public void initialize() {
        Set<File> plugins = freiorPluginProvider.getFreiorPlugins();

        for (File plugin : plugins) {
            FreiorLoadPluginRequest loadRequest = new FreiorLoadPluginRequest();
            loadRequest.file = plugin.getAbsolutePath();

            LOGGER.debug("Loading frei0r plugin " + plugin.getAbsolutePath());

            int pluginIndex = freiorNativeLibrary.loadPlugin(loadRequest);

            if (pluginIndex > 0) {
                List<FreiorParameter> parameters;

                if (loadRequest.numberOfParameters > 0) {
                    parameters = Arrays.asList((FreiorParameter[]) loadRequest.parameters.toArray(loadRequest.numberOfParameters));
                } else {
                    parameters = Collections.emptyList();
                }

                if (!pluginNameToPluginInformation.containsKey(loadRequest.name) && !BLACKLISTED_PLUGINS.contains(loadRequest.name)) {
                    pluginNameToPluginInformation.put(loadRequest.name, new PluginInformation(pluginIndex, parameters));

                    if (loadRequest.pluginType == FreiorConstants.F0R_PLUGIN_TYPE_FILTER) {
                        initializeEffect(loadRequest, pluginIndex, parameters);
                    } else if (loadRequest.pluginType == FreiorConstants.F0R_PLUGIN_TYPE_SOURCE) {
                        initializeGenerator(loadRequest, pluginIndex, parameters);
                    }
                }
            }
        }
    }

    private void initializeGenerator(FreiorLoadPluginRequest loadRequest, int pluginIndex, List<FreiorParameter> parameters) {
        String id = "freior-clip-" + loadRequest.name;

        TimelineLength defaultLength = TimelineLength.ofMillis(30000);
        ImageMetadata metadata = ImageMetadata.builder()
                .withWidth(1920)
                .withHeight(1080)
                .withLength(defaultLength)
                .build();

        ProceduralClipFactoryChainItem proceduralClipFactory = new StandardProceduralClipFactoryChainItem(id, loadRequest.name + "-openfx",
                request -> {
                    return new FreiorProceduralClip(metadata, new TimelineInterval(request.getPosition(), defaultLength), loadRequest.name, pluginIndex, parameters, freiorParameterMapper,
                            freiorNativeLibrary);
                },
                (node, loadMetadata) -> {
                    return new FreiorProceduralClip(metadata, node, loadMetadata, pluginNameToPluginInformation, freiorParameterMapper, freiorNativeLibrary);
                });

        proceduralClips.add(proceduralClipFactory);
    }

    private void initializeEffect(FreiorLoadPluginRequest loadRequest, int pluginIndex, List<FreiorParameter> parameters) {
        String id = "freior-filter-" + loadRequest.name;

        effectFactories.add(StandardEffectFactory.builder()
                .withFactory(request -> new FreiorFilterEffect(new TimelineInterval(request.getPosition(), TimelineLength.ofMillis(5000)), loadRequest.name, pluginIndex, parameters,
                        freiorParameterMapper, freiorNativeLibrary))
                .withRestoreFactory((node, loadMetadata) -> new FreiorFilterEffect(node, loadMetadata, pluginNameToPluginInformation, freiorParameterMapper, freiorNativeLibrary))
                .withName(loadRequest.name + "-frei0r")
                .withSupportedEffectId(id)
                .withSupportedClipTypes(List.of(TimelineClipType.VIDEO, TimelineClipType.IMAGE))
                .withEffectType(TimelineEffectType.VIDEO_EFFECT)
                .build());
    }

    @Bean
    public List<StandardEffectFactory> freiorEffects() {
        return effectFactories;
    }

    @Bean
    public List<ProceduralClipFactoryChainItem> freiorProceduralClips() {
        return proceduralClips;
    }

}
