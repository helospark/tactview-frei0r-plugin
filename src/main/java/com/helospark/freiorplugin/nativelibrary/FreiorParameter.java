package com.helospark.freiorplugin.nativelibrary;

import java.util.List;

import com.sun.jna.Structure;

public class FreiorParameter extends Structure implements Structure.ByReference {
    public String name;
    public int type;
    public String description;

    @Override
    protected List<String> getFieldOrder() {
        return List.of("name", "type", "description");
    }
}
