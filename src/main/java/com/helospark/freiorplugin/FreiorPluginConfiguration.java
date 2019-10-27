package com.helospark.freiorplugin;

import com.helospark.lightdi.annotation.ComponentScan;
import com.helospark.lightdi.annotation.Configuration;
import com.helospark.lightdi.annotation.PropertySource;

@Configuration
@ComponentScan
@PropertySource("classpath:freior-plugin.properties")
public class FreiorPluginConfiguration {

}
