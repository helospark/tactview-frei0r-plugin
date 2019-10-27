package com.helospark.freiorplugin.nativelibrary;

import java.nio.ByteBuffer;
import java.util.List;

import com.sun.jna.Structure;

public class RenderRequest extends Structure implements Structure.ByReference {
    public int pluginIndex;

    public int width;
    public int height;
    public ByteBuffer input;
    public ByteBuffer output;

    public FreiorParameterRequest parameters;

    public double time;

    public ByteBuffer transitionToInput;

    @Override
    protected List<String> getFieldOrder() {
        return List.of("pluginIndex", "width", "height", "input", "output", "parameters", "time", "transitionToInput");
    }

}
