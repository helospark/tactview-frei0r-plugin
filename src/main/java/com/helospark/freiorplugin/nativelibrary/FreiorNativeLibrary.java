package com.helospark.freiorplugin.nativelibrary;

import com.helospark.tactview.core.util.jpaplugin.NativeImplementation;
import com.sun.jna.Library;

@NativeImplementation("freiorplugin")
public interface FreiorNativeLibrary extends Library {
    public int loadPlugin(FreiorLoadPluginRequest loadRequest);

    public void render(RenderRequest request);
}
