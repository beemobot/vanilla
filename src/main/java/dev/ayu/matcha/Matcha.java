package dev.ayu.matcha;

import dev.ayu.latte.config.Configurator;
import dev.ayu.latte.kafka.KafkaConnection;
import dev.ayu.latte.logging.LoggerKt;
import dev.ayu.latte.ratelimit.SharedRatelimitData;
import org.apache.logging.log4j.Logger;

public class Matcha {

    private static final Logger LOGGER = LoggerKt.getLogger(Matcha.class);

    public static void main(String[] args) {
        LOGGER.debug("Loading configuration");
        Configurator.create().mirror(Config.class);

        try {
            LOGGER.debug("Initializing Kafka connection");
            KafkaConnection kafkaConnection = new KafkaConnection(
                    Config.KAFKA_HOST,
                    "matcha",
                    "matcha",
                    SharedRatelimitData.MATCHA_CLUSTER_ID
            );

            LOGGER.debug("Initializing Kafka Ratelimit client");
            new RatelimitClient(kafkaConnection);

            LOGGER.debug("Starting Kafka connection");
            kafkaConnection.start();
        } catch (Throwable t) {
            LOGGER.error("Error initializing Kafka", t);
            System.exit(1);
        }
        LOGGER.info("Initialization done!");
    }

}
