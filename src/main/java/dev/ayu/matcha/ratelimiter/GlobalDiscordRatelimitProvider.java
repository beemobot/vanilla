package dev.ayu.matcha.ratelimiter;

import dev.ayu.matcha.Matcha;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.*;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Produced;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CountDownLatch;

public class GlobalDiscordRatelimitProvider {

    // GLOBAL
    private static final String KAFKA_GLOBAL_RATELIMIT_BLOCKING_STREAM = "discord-global-ratelimit-blocking-stream";
    private static final String KAFKA_GLOBAL_RATELIMIT_STREAM = "discord-global-ratelimit-stream";
    private static final String KAFKA_GLOBAL_RATELIMIT_INPUT_TOPIC = "discord-global-ratelimit-requests";
    private static final String KAFKA_GLOBAL_RATELIMIT_OUTPUT_TOPIC = "discord-global-ratelimit-grants";
    // IDENTIFY
    private static final String KAFKA_IDENTIFY_RATELIMIT_BLOCKING_STREAM = "discord-identify-ratelimit-blocking-stream";
    private static final String KAFKA_IDENTIFY_RATELIMIT_STREAM = "discord-identify-ratelimit-stream";
    private static final String KAFKA_IDENTIFY_RATELIMIT_INPUT_TOPIC = "discord-identify-ratelimit-requests";
    private static final String KAFKA_IDENTIFY_RATELIMIT_OUTPUT_TOPIC = "discord-identify-ratelimit-grants";

    private final Ratelimiter globalRatelimiter;
    private final Ratelimiter identifyRatelimiter;

    /**
     * Creates a new global Discord ratelimit provider that will connect with Kafka to
     * provide ratelimiting for all tea clusters.
     */
    public GlobalDiscordRatelimitProvider() {
        // 1 action every 22 ms (~50 per 1sec)
        this.globalRatelimiter = new Ratelimiter(1, Duration.ofMillis(22));
        // 1 identify every 87 seconds (~1000 per 24hrs)
        this.identifyRatelimiter = new Ratelimiter(1, Duration.ofSeconds(87));
        try {
            initializeStreams();
        } catch (Throwable e) {
            Matcha.getLogger().error("Unexpected error when initializing Ratelimiter Kafka streams:", e);
            System.exit(1);
        }
    }

    private static Properties getDefaultStreamProps() {
        Properties props = new Properties();
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, Matcha.KAFKA_SERVER);
        props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass());
        props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String().getClass());
        props.put(StreamsConfig.PROCESSING_GUARANTEE_CONFIG, StreamsConfig.EXACTLY_ONCE_V2);
        return props;
    }

    private void initializeStreams() {
        // Build the props
        Properties globalBlockingProps = getDefaultStreamProps();
        globalBlockingProps.put(
                StreamsConfig.APPLICATION_ID_CONFIG,
                KAFKA_GLOBAL_RATELIMIT_BLOCKING_STREAM
        );
        Properties globalProps = getDefaultStreamProps();
        globalProps.put(
                StreamsConfig.APPLICATION_ID_CONFIG,
                KAFKA_GLOBAL_RATELIMIT_STREAM
        );
        Properties identifyBlockingProps = getDefaultStreamProps();
        identifyBlockingProps.put(
                StreamsConfig.APPLICATION_ID_CONFIG,
                KAFKA_IDENTIFY_RATELIMIT_BLOCKING_STREAM
        );
        Properties identifyProps = getDefaultStreamProps();
        identifyProps.put(
                StreamsConfig.APPLICATION_ID_CONFIG,
                KAFKA_IDENTIFY_RATELIMIT_STREAM
        );
        // Initialize the streams individually
        initializeStreamFromProps(
                KAFKA_GLOBAL_RATELIMIT_INPUT_TOPIC,
                KAFKA_GLOBAL_RATELIMIT_OUTPUT_TOPIC,
                globalBlockingProps
        );
        initializeStreamFromProps(
                KAFKA_GLOBAL_RATELIMIT_INPUT_TOPIC,
                KAFKA_GLOBAL_RATELIMIT_OUTPUT_TOPIC,
                globalProps
        );
        initializeStreamFromProps(
                KAFKA_IDENTIFY_RATELIMIT_INPUT_TOPIC,
                KAFKA_IDENTIFY_RATELIMIT_OUTPUT_TOPIC,
                identifyBlockingProps
        );
        initializeStreamFromProps(
                KAFKA_IDENTIFY_RATELIMIT_INPUT_TOPIC,
                KAFKA_IDENTIFY_RATELIMIT_OUTPUT_TOPIC,
                identifyProps
        );
    }

    private void initializeStreamFromProps(String inputTopic, String outputTopic, Properties props) {
        // https://kafka.apache.org/30/documentation/streams/tutorial
        // https://kafka.apache.org/30/documentation/streams/quickstart
        final StreamsBuilder builder = new StreamsBuilder();
        // Serializer/deserializer (serde)
        final Serde<String> stringSerde = Serdes.String();
        // Construct a `KStream` from the input topic
        KStream<String, String> stream = builder.stream(
                inputTopic,
                Consumed.with(stringSerde, stringSerde)
        );
        String streamName = props.get(StreamsConfig.APPLICATION_ID_CONFIG)
                .toString();
        if (streamName.toLowerCase(Locale.ROOT).contains("blocking")) {
            // Blocking streams:
            stream = stream.filter((requestingCluster, request) ->
                    request.equals(RatelimitSignal.REQUEST_PERMIT.toString())
            ).map((requestingCluster, request) -> {
                    if (RatelimitSignal.valueOf(request) == RatelimitSignal.REQUEST_PERMIT) {
                        if (streamName.equals(KAFKA_GLOBAL_RATELIMIT_BLOCKING_STREAM)) {
                            globalRatelimiter.requestQuota();
                        } else {
                            Matcha.getLogger().info("Received " + requestingCluster + " requesting identify quota."
                                    + " Estimated wait time: "
                                    + Duration.ofNanos(identifyRatelimiter.getRemainingPauseNanos()).toSeconds()
                                    + " seconds");
                            long startTime = System.nanoTime();
                            identifyRatelimiter.requestQuota();
                            Matcha.getLogger().info("Granted " + requestingCluster + " identify quota after "
                                    + Duration.ofNanos(System.nanoTime() - startTime).toSeconds() + " seconds.");
                        }
                        return new KeyValue<>(
                                requestingCluster,
                                RatelimitSignal.GRANT_PERMIT.toString()
                        );
                    }
                    return null;
            });
        } else {
            // Non-blocking streams:
            stream = stream.filter((requestingCluster, request) ->
                    !request.equals(RatelimitSignal.REQUEST_PERMIT.toString())
            ).map((requestingCluster, request) -> {
                    long amount;
                    String responseSignal;
                    switch (RatelimitSignal.valueOf(request)) {
                        case REQUEST_REMAINING_PAUSE_NANOS -> {
                            responseSignal = RatelimitSignal.GRANT_REMAINING_PAUSE_NANOS.toString();
                            if (streamName.equals(KAFKA_GLOBAL_RATELIMIT_BLOCKING_STREAM)) {
                                amount = globalRatelimiter.getRemainingPauseNanos();
                            } else {
                                amount = identifyRatelimiter.getRemainingPauseNanos();
                            }
                        }
                        case REQUEST_NUM_REMAINING_PERMITS -> {
                            responseSignal = RatelimitSignal.GRANT_NUM_REMAINING_PERMITS.toString();
                            if (streamName.equals(KAFKA_GLOBAL_RATELIMIT_BLOCKING_STREAM)) {
                                amount = globalRatelimiter.getRemainingPermits();
                            } else {
                                amount = identifyRatelimiter.getRemainingPermits();
                            }
                        }
                        default -> {
                            return null;
                        }
                    }
                    return new KeyValue<>(
                            requestingCluster,
                            responseSignal + RatelimitSignal.getSeparator() + amount
                    );
            });
        }
        stream.to(outputTopic, Produced.with(stringSerde, stringSerde));

        final Topology topology = builder.build();

        final KafkaStreams streams = new KafkaStreams(topology, props);
        final CountDownLatch latch = new CountDownLatch(1);

        // Attach shutdown handler to catch control-c
        Runtime.getRuntime().addShutdownHook(new Thread(streamName + "-shutdown-hook") {
            @Override
            public void run() {
                streams.close();
                latch.countDown();
            }
        });

        Thread streamThread = new Thread(() -> {
            try {
                streams.start();
                latch.await();
            } catch (Throwable e) {
                Matcha.getLogger().uncaughtError(streamName + " error:", e);
            }
        }, streamName + "-thread");
        streamThread.setDaemon(false);
        streamThread.start();
    }

}
