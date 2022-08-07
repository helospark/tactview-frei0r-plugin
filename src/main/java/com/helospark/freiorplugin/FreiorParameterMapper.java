package com.helospark.freiorplugin;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.helospark.freiorplugin.nativelibrary.FreiorConstants;
import com.helospark.freiorplugin.nativelibrary.FreiorParameter;
import com.helospark.freiorplugin.nativelibrary.FreiorParameterRequest;
import com.helospark.lightdi.annotation.Component;
import com.helospark.tactview.core.save.LoadMetadata;
import com.helospark.tactview.core.timeline.TimelinePosition;
import com.helospark.tactview.core.timeline.effect.interpolation.KeyframeableEffect;
import com.helospark.tactview.core.timeline.effect.interpolation.interpolator.MultiKeyframeBasedDoubleInterpolator;
import com.helospark.tactview.core.timeline.effect.interpolation.interpolator.StepStringInterpolator;
import com.helospark.tactview.core.timeline.effect.interpolation.interpolator.bezier.BezierDoubleInterpolator;
import com.helospark.tactview.core.timeline.effect.interpolation.pojo.Color;
import com.helospark.tactview.core.timeline.effect.interpolation.pojo.Point;
import com.helospark.tactview.core.timeline.effect.interpolation.provider.BooleanProvider;
import com.helospark.tactview.core.timeline.effect.interpolation.provider.ColorProvider;
import com.helospark.tactview.core.timeline.effect.interpolation.provider.DoubleProvider;
import com.helospark.tactview.core.timeline.effect.interpolation.provider.PointProvider;
import com.helospark.tactview.core.timeline.effect.interpolation.provider.StringProvider;
import com.helospark.tactview.core.timeline.effect.interpolation.provider.evaluator.EvaluationContext;
import com.helospark.tactview.core.util.ReflectionUtil;

@Component
public class FreiorParameterMapper {
    private static final Logger LOGGER = LoggerFactory.getLogger(FreiorParameterMapper.class);

    public KeyframeableEffect mapParameterToEffect(FreiorParameter parameter) {
        KeyframeableEffect result = null;
        if (parameter.type == FreiorConstants.F0R_PARAM_BOOL) {
            result = new BooleanProvider(new MultiKeyframeBasedDoubleInterpolator(0.0));
        } else if (parameter.type == FreiorConstants.F0R_PARAM_COLOR) {
            result = ColorProvider.fromDefaultValue(0, 0, 0);
        } else if (parameter.type == FreiorConstants.F0R_PARAM_DOUBLE) {
            result = new DoubleProvider(new BezierDoubleInterpolator(0.0));
        } else if (parameter.type == FreiorConstants.F0R_PARAM_POSITION) {
            result = PointProvider.of(0, 0);
        } else if (parameter.type == FreiorConstants.F0R_PARAM_STRING) {
            result = new StringProvider(new StepStringInterpolator());
        } else {
            throw new IllegalStateException("Unknown type");
        }
        return result;
    }

    public FreiorParameterRequest getCurrentParameterValues(List<KeyframeableEffect> keyframeableEffects, TimelinePosition timelinePosition, EvaluationContext evaluationContext) {
        FreiorParameterRequest resultSingle = new FreiorParameterRequest();
        FreiorParameterRequest[] result = (FreiorParameterRequest[]) resultSingle.toArray(keyframeableEffects.size());
        for (int i = 0; i < keyframeableEffects.size(); ++i) {
            KeyframeableEffect provider = keyframeableEffects.get(i);
            if (provider instanceof DoubleProvider) {
                result[i].x = ((DoubleProvider) provider).getValueAt(timelinePosition, evaluationContext);
            } else if (provider instanceof ColorProvider) {
                Color color = ((ColorProvider) provider).getValueAt(timelinePosition, evaluationContext);
                result[i].r = (float) color.red;
                result[i].g = (float) color.green;
                result[i].b = (float) color.blue;
            } else if (provider instanceof BooleanProvider) {
                boolean v = ((BooleanProvider) provider).getValueAt(timelinePosition, evaluationContext);
                result[i].x = v ? 1.0 : 0.0;
            } else if (provider instanceof PointProvider) {
                Point v = ((PointProvider) provider).getValueAt(timelinePosition, evaluationContext);
                result[i].x = v.x;
                result[i].y = v.y;
            } else if (provider instanceof StringProvider) {
                String v = ((StringProvider) provider).getValueAt(timelinePosition, evaluationContext);
                result[i].string = v;
            } else {
                throw new IllegalStateException("Unexpected class " + provider.getClass().getSimpleName());
            }
        }
        return resultSingle;
    }

    public List<KeyframeableEffect> restoreParameters(LoadMetadata loadMetadata, FreiorParameterMapper freiorParameterMapper, PluginInformation pluginInformation, JsonNode parametersNode) {
        List<FreiorParameter> originalParameters = pluginInformation.parameters;

        List<KeyframeableEffect> keyframeableEffects = new ArrayList<>();
        for (int i = 0; i < originalParameters.size(); ++i) {
            KeyframeableEffect parameter = freiorParameterMapper.mapParameterToEffect(originalParameters.get(i));
            try {
                parameter = ReflectionUtil.deserialize(parametersNode.get(i), KeyframeableEffect.class, parameter, loadMetadata);
            } catch (Exception e) {
                LOGGER.warn("Unable to map parameter", e);
            }
            keyframeableEffects.add(parameter);
        }
        return keyframeableEffects;
    }
}
