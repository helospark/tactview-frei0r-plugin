package com.helospark.freiorplugin.nativelibrary;

import java.util.List;

import com.sun.jna.Structure;

public class FreiorLoadPluginRequest extends Structure implements Structure.ByReference {
    public String file;

    // output
    public String name;
    public String description;
    public int colorModel;
    public int pluginType;

    public int numberOfParameters;
    public FreiorParameter parameters;

    @Override
    protected List<String> getFieldOrder() {
        return List.of("file", "name", "description", "colorModel", "pluginType", "numberOfParameters", "parameters");
    }
}
