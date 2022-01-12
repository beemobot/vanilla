package dev.ayu.matcha;

import dev.ayu.matcha.ratelimiter.KafkaRatelimitProvider;

import java.util.Timer;
import java.util.TimerTask;

public class Matcha {

    public static final String KAFKA_SERVER = "kafka:9092";
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

        /*
         * Launch the Global Discord Ratelimit Provider Streams
         */
        new KafkaRatelimitProvider();
        LOGGER.info("GlobalDiscordRatelimitProvider streams active!");
    }

}