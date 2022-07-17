package dev.ayu.matcha;

import dev.ayu.latte.config.annotations.ConfiguratorDefault;

public class Config {

    @ConfiguratorDefault(defaultValue = "localhost:9094")
    public static String KAFKA_HOST;

}
