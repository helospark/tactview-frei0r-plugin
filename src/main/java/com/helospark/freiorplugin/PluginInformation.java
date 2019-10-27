package com.helospark.freiorplugin;

import java.util.List;

import com.helospark.freiorplugin.nativelibrary.FreiorParameter;

public class PluginInformation {
    Integer id;
    List<FreiorParameter> parameters;

    public PluginInformation(Integer id, List<FreiorParameter> parameters) {
        this.id = id;
        this.parameters = parameters;
    }

}
