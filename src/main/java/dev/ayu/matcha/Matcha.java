package dev.ayu.matcha;

import dev.ayu.latte.config.Configurator;
import dev.ayu.matcha.ratelimiter.KafkaRatelimitProvider;

import java.util.Timer;
import java.util.TimerTask;

public class Matcha {

    private static final ConsoleLogger LOGGER = new ConsoleLogger();

    public static ConsoleLogger getLogger() {
        return LOGGER;
    }

    public static void main(String[] args) {
        /*
         * MEMORY LOGGING
         */
        Timer memoryTimer = new Timer();
        TimerTask memoryLogsTask = new TimerTask() {
            @Override
            public void run() {
                getLogger().info(
                    String.format("MEM INFO: TOTAL=%.2f MB, FREE=%.2f MB, MAX=%.2f MB",
                        Runtime.getRuntime().totalMemory()/1024.0/1024.0,
                        Runtime.getRuntime().freeMemory()/1024.0/1024.0,
                        Runtime.getRuntime().maxMemory()/1024.0/1024.0)
                );
            }
        };
        // No delay; 15 second interval.
        memoryTimer.scheduleAtFixedRate(memoryLogsTask, 0L, 15_000L);

        Configurator.create().mirror(Config.class);

        // Launch the ratelimit provider (creates all necessary ratelimit topics)
        new KafkaRatelimitProvider();
    }

}
