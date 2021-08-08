package com.helospark.freiorplugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.helospark.freiorplugin.nativelibrary.FreiorNativeLibrary;
import com.helospark.freiorplugin.nativelibrary.FreiorParameter;
import com.helospark.freiorplugin.nativelibrary.RenderRequest;
import com.helospark.tactview.core.clone.CloneRequestMetadata;
import com.helospark.tactview.core.decoder.ImageMetadata;
import com.helospark.tactview.core.decoder.VisualMediaMetadata;
import com.helospark.tactview.core.save.LoadMetadata;
import com.helospark.tactview.core.save.SaveMetadata;
import com.helospark.tactview.core.timeline.GetFrameRequest;
import com.helospark.tactview.core.timeline.TimelineClip;
import com.helospark.tactview.core.timeline.TimelineInterval;
import com.helospark.tactview.core.timeline.TimelinePosition;
import com.helospark.tactview.core.timeline.effect.interpolation.KeyframeableEffect;
import com.helospark.tactview.core.timeline.effect.interpolation.ValueProviderDescriptor;
import com.helospark.tactview.core.timeline.image.ClipImage;
import com.helospark.tactview.core.timeline.image.ReadOnlyClipImage;
import com.helospark.tactview.core.timeline.proceduralclip.ProceduralVisualClip;
import com.helospark.tactview.core.util.MathUtil;

public class FreiorProceduralClip extends ProceduralVisualClip {
    private static final Object LOCK = new Object();

    private static final String PLUGIN_NAME_KEY = "pluginName";
    private static final String PARAMETERS_KEY = "parameters";

    private FreiorParameterMapper freiorParameterMapper;
    private FreiorNativeLibrary freiorNativeLibrary;

    private int pluginIndex;
    private String pluginName;
    private List<FreiorParameter> parameters;

    private List<KeyframeableEffect> keyframeableEffects;

    public FreiorProceduralClip(VisualMediaMetadata visualMediaMetadata, TimelineInterval interval, String pluginName, int pluginIndex, List<FreiorParameter> parameters,
            FreiorParameterMapper freiorParameterMapper, FreiorNativeLibrary freiorNativeLibrary) {
        super(visualMediaMetadata, interval);
        this.parameters = parameters;
        this.pluginIndex = pluginIndex;
        this.pluginName = pluginName;
        this.freiorParameterMapper = freiorParameterMapper;
        this.freiorNativeLibrary = freiorNativeLibrary;

        keyframeableEffects = new ArrayList<>();
        for (FreiorParameter parameter : parameters) {
            KeyframeableEffect result = freiorParameterMapper.mapParameterToEffect(parameter);
            keyframeableEffects.add(result);
        }
    }

    public FreiorProceduralClip(FreiorProceduralClip clip, CloneRequestMetadata cloneRequestMetadata) {
        super(clip, cloneRequestMetadata);

        this.freiorParameterMapper = clip.freiorParameterMapper;
        this.freiorNativeLibrary = clip.freiorNativeLibrary;

        this.pluginName = clip.pluginName;
        this.pluginIndex = clip.pluginIndex;

        List<KeyframeableEffect> clonedKeyframeableEffects = new ArrayList<>();
        for (var entry : clip.keyframeableEffects) {
            clonedKeyframeableEffects.add(entry.deepClone(cloneRequestMetadata));
        }
    }

    public FreiorProceduralClip(ImageMetadata metadata, JsonNode node, LoadMetadata loadMetadata, Map<String, PluginInformation> pluginNameToPluginInformation,
            FreiorParameterMapper freiorParameterMapper, FreiorNativeLibrary freiorNativeLibrary) {
        super(metadata, node, loadMetadata);
        this.freiorParameterMapper = freiorParameterMapper;
        this.freiorNativeLibrary = freiorNativeLibrary;

        this.pluginName = node.get(PLUGIN_NAME_KEY).asText();
        PluginInformation pluginInformation = pluginNameToPluginInformation.get(pluginName);
        pluginIndex = pluginInformation.id;

        JsonNode parametersNode = node.get(PARAMETERS_KEY);

        keyframeableEffects = freiorParameterMapper.restoreParameters(loadMetadata, freiorParameterMapper, pluginInformation, parametersNode);
    }

    @Override
    protected void generateSavedContentInternal(Map<String, Object> result, SaveMetadata saveMetadata) {
        super.generateSavedContentInternal(result, saveMetadata);
        result.put(PLUGIN_NAME_KEY, pluginName);
        result.put(PARAMETERS_KEY, keyframeableEffects);
    }

    @Override
    public ReadOnlyClipImage createProceduralFrame(GetFrameRequest request, TimelinePosition relativePosition) {
        int width = MathUtil.increaseToMakeDivideableBy(request.getExpectedWidth(), 8);
        int height = MathUtil.increaseToMakeDivideableBy(request.getExpectedHeight(), 8);

        ClipImage result = ClipImage.fromSize(width, height);

        RenderRequest renderRequest = new RenderRequest();
        renderRequest.width = width;
        renderRequest.height = height;
        renderRequest.output = result.getBuffer();
        renderRequest.pluginIndex = pluginIndex;
        renderRequest.time = relativePosition.getSeconds().doubleValue();
        renderRequest.parameters = freiorParameterMapper.getCurrentParameterValues(this.keyframeableEffects, relativePosition);

        synchronized (LOCK) {
            freiorNativeLibrary.render(renderRequest);
        }

        return result;
    }

    @Override
    public List<ValueProviderDescriptor> getDescriptorsInternal() {
        List<ValueProviderDescriptor> descriptors = super.getDescriptorsInternal();

        for (int i = 0; i < keyframeableEffects.size(); ++i) {
            descriptors.add(ValueProviderDescriptor.builder()
                    .withKeyframeableEffect(keyframeableEffects.get(i))
                    .withName(parameters.get(i).name)
                    .build());
        }

        return descriptors;
    }

    @Override
    public TimelineClip cloneClip(CloneRequestMetadata cloneRequestMetadata) {
        return new FreiorProceduralClip(this, cloneRequestMetadata);
    }

}
