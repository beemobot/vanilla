package dev.ayu.matcha

import dev.ayu.latte.kafka.KafkaClient
import dev.ayu.latte.kafka.KafkaConnection
import dev.ayu.latte.logging.log
import dev.ayu.latte.ratelimit.SharedRatelimitData
import dev.ayu.latte.util.SuspendingRatelimit
import kotlin.time.Duration.Companion.seconds

class RatelimitClient(conn: KafkaConnection) : KafkaClient<SharedRatelimitData.RatelimitClientData>(
    conn,
    SharedRatelimitData.RatelimitClientData::class.java,
    SharedRatelimitData.RATELIMIT_TOPIC,
) {

    private val globalRatelimit = SuspendingRatelimit(50, 1.seconds)
    private val identifyRatelimit = SuspendingRatelimit(1, 5.seconds)

    private val nothing = SharedRatelimitData.RatelimitClientData()

    init {
        on(SharedRatelimitData.KEY_REQUEST_GLOBAL_QUOTA) { msg ->
            log.debug("Incoming global quota request from cluster ${msg.headers.sourceCluster}")
            globalRatelimit.requestQuota()
            log.debug("Granted global quota request for ${msg.headers.sourceCluster}")
            msg.respond(nothing)
        }

        on(SharedRatelimitData.KEY_REQUEST_IDENTIFY_QUOTA) { msg ->
            log.debug("Incoming identify quota request from cluster ${msg.headers.sourceCluster}")
            identifyRatelimit.requestQuota()
            log.debug("Granted identify quota request for ${msg.headers.sourceCluster}")
            msg.respond(nothing)
        }
    }

}
