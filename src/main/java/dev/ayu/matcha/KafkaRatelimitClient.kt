package dev.ayu.matcha

import dev.ayu.latte.kafka.KafkaClient
import dev.ayu.latte.kafka.KafkaConnection
import dev.ayu.latte.kafka.KafkaMessage
import dev.ayu.latte.logging.log
import dev.ayu.latte.ratelimit.SharedRatelimitData
import dev.ayu.latte.ratelimit.SharedRatelimitData.RatelimitClientData
import dev.ayu.latte.util.SuspendingRatelimit
import kotlin.time.Duration.Companion.seconds

// Give request expiry a bit of leeway in case of clock drift
private val EXPIRY_GRACE_PERIOD = 5.seconds.inWholeMilliseconds

class RatelimitClient(conn: KafkaConnection) : KafkaClient<RatelimitClientData>(
    conn,
    RatelimitClientData::class.java,
    SharedRatelimitData.RATELIMIT_TOPIC,
) {

    private val globalRatelimit = SuspendingRatelimit(50, 1.seconds)
    private val identifyRatelimit = SuspendingRatelimit(1, 5.seconds)

    init {
        on(SharedRatelimitData.KEY_REQUEST_GLOBAL_QUOTA) { msg ->
            handleRatelimitRequest(msg, globalRatelimit, "global")
        }

        on(SharedRatelimitData.KEY_REQUEST_IDENTIFY_QUOTA) { msg ->
            handleRatelimitRequest(msg, identifyRatelimit, "identify")
        }
    }

    private suspend fun handleRatelimitRequest(
        msg: KafkaMessage<RatelimitClientData>,
        limiter: SuspendingRatelimit,
        type: String,
    ) {
        val sourceCluster = msg.headers.sourceCluster
        val expiresAt = msg.value?.requestExpiresAt
        if (expiresAt != null && (expiresAt + EXPIRY_GRACE_PERIOD) < System.currentTimeMillis()) {
            log.info("Incoming expired '$type' quota request from cluster $sourceCluster, ignoring")
            // If the request has already expired, ignore it to not eat quotas unnecessarily
            return
        }
        log.debug("Incoming '$type' quota request from cluster $sourceCluster")
        limiter.requestQuota()
        log.debug("Granted '$type' quota request for $sourceCluster")
        msg.respond(null)
    }

}
