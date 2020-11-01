package com.helospark.freiorplugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.helospark.freiorplugin.nativelibrary.FreiorNativeLibrary;
import com.helospark.freiorplugin.nativelibrary.FreiorParameter;
import com.helospark.freiorplugin.nativelibrary.RenderRequest;
import com.helospark.tactview.core.clone.CloneRequestMetadata;
import com.helospark.tactview.core.save.LoadMetadata;
import com.helospark.tactview.core.save.SaveMetadata;
import com.helospark.tactview.core.timeline.StatelessEffect;
import com.helospark.tactview.core.timeline.StatelessVideoEffect;
import com.helospark.tactview.core.timeline.TimelineInterval;
import com.helospark.tactview.core.timeline.effect.StatelessEffectRequest;
import com.helospark.tactview.core.timeline.effect.interpolation.KeyframeableEffect;
import com.helospark.tactview.core.timeline.effect.interpolation.ValueProviderDescriptor;
import com.helospark.tactview.core.timeline.image.ClipImage;
import com.helospark.tactview.core.timeline.image.ReadOnlyClipImage;

public class FreiorFilterEffect extends StatelessVideoEffect {
    private static final Object LOCK = new Object();

    private static final String PLUGIN_NAME_KEY = "pluginName";
    private static final String PARAMETERS_KEY = "parameters";

    private FreiorParameterMapper freiorParameterMapper;
    private FreiorNativeLibrary freiorNativeLibrary;

    private int pluginIndex;
    private String pluginName;
    private List<FreiorParameter> parameters;

    private List<KeyframeableEffect> keyframeableEffects;

    public FreiorFilterEffect(TimelineInterval interval, String pluginName, int pluginIndex, List<FreiorParameter> parameters, FreiorParameterMapper freiorParameterMapper,
            FreiorNativeLibrary freiorNativeLibrary) {
        super(interval);
        this.parameters = parameters;
        this.pluginIndex = pluginIndex;
        this.pluginName = pluginName;
        this.freiorParameterMapper = freiorParameterMapper;
        this.freiorNativeLibrary = freiorNativeLibrary;
    }

    public FreiorFilterEffect(FreiorFilterEffect effect, CloneRequestMetadata cloneRequestMetadata) {
        super(effect, cloneRequestMetadata);

        this.freiorParameterMapper = effect.freiorParameterMapper;
        this.freiorNativeLibrary = effect.freiorNativeLibrary;

        this.pluginName = effect.pluginName;
        this.pluginIndex = effect.pluginIndex;

        List<KeyframeableEffect> clonedKeyframeableEffects = new ArrayList<>();
        for (var entry : effect.keyframeableEffects) {
            clonedKeyframeableEffects.add(entry.deepClone());
        }
    }

    public FreiorFilterEffect(JsonNode node, LoadMetadata loadMetadata, Map<String, PluginInformation> pluginNameToPluginInformation, FreiorParameterMapper freiorParameterMapper,
            FreiorNativeLibrary freiorNativeLibrary) {
        super(node, loadMetadata);
        this.freiorParameterMapper = freiorParameterMapper;
        this.freiorNativeLibrary = freiorNativeLibrary;

        this.pluginName = node.get(PLUGIN_NAME_KEY).asText();
        PluginInformation pluginInformation = pluginNameToPluginInformation.get(pluginName);
        pluginIndex = pluginInformation.id;

        JsonNode parametersNode = node.get(PARAMETERS_KEY);

        keyframeableEffects = freiorParameterMapper.restoreParameters(loadMetadata, freiorParameterMapper, pluginInformation, parametersNode);
    }

    @Override
    protected void generateSavedContentInternal(Map<String, Object> result, SaveMetadata saveMetadatat) {
        super.generateSavedContentInternal(result, saveMetadatat);
        result.put(PLUGIN_NAME_KEY, pluginName);
        result.put(PARAMETERS_KEY, keyframeableEffects);
    }

    @Override
    public ReadOnlyClipImage createFrame(StatelessEffectRequest request) {
        ClipImage result = ClipImage.sameSizeAs(request.getCurrentFrame());

        RenderRequest renderRequest = new RenderRequest();
        renderRequest.width = request.getCurrentFrame().getWidth();
        renderRequest.height = request.getCurrentFrame().getHeight();
        renderRequest.input = request.getCurrentFrame().getBuffer();
        renderRequest.output = result.getBuffer();
        renderRequest.pluginIndex = pluginIndex;
        renderRequest.time = request.getEffectPosition().getSeconds().doubleValue();
        renderRequest.parameters = freiorParameterMapper.getCurrentParameterValues(this.keyframeableEffects, request.getEffectPosition());

        synchronized (LOCK) {
            freiorNativeLibrary.render(renderRequest);
        }

        return result;
    }

    @Override
    protected void initializeValueProviderInternal() {
        keyframeableEffects = new ArrayList<>();

        for (FreiorParameter parameter : parameters) {
            KeyframeableEffect result = freiorParameterMapper.mapParameterToEffect(parameter);
            keyframeableEffects.add(result);
        }

    }

    @Override
    protected List<ValueProviderDescriptor> getValueProvidersInternal() {
        List<ValueProviderDescriptor> result = new ArrayList<>();
        for (int i = 0; i < keyframeableEffects.size(); ++i) {
            result.add(ValueProviderDescriptor.builder()
                    .withKeyframeableEffect(keyframeableEffects.get(i))
                    .withName(parameters.get(i).name)
                    .build());
        }
        return result;
    }

    @Override
    public StatelessEffect cloneEffect(CloneRequestMetadata cloneRequestMetadata) {
        return new FreiorFilterEffect(this, cloneRequestMetadata);
    }

}
