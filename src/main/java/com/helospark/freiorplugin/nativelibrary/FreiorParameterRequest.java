package com.helospark.freiorplugin.nativelibrary;

import java.util.List;

import com.sun.jna.Structure;

public class FreiorParameterRequest extends Structure implements Structure.ByReference {
    public double x;
    public double y;

    public float r;
    public float g;
    public float b;

    public String string;

    @Override
    protected List<String> getFieldOrder() {
        return List.of("x", "y", "r", "g", "b", "string");
    }
}
