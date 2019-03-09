package info.benjaminhill.wbb

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.SignatureAlgorithm
import mu.KotlinLogging
import org.eclipse.paho.client.mqttv3.*
import org.eclipse.paho.client.mqttv3.MqttException.REASON_CODE_CONNECTION_LOST
import org.eclipse.paho.client.mqttv3.MqttException.REASON_CODE_SERVER_CONNECT_ERROR
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import org.joda.time.DateTime
import java.security.KeyFactory
import java.security.spec.PKCS8EncodedKeySpec


/**
 * https://console.cloud.google.com/iot/locations/us-central1/registries/wbb-registry/devices/ev3?project=whiteboardbot
 */
class MQTT : AutoCloseable {

    private val client: MqttClient

    init {
        LOG.info { "MQTT:init:start" }

        client = connectWithExponentialRetry(MqttClient("ssl://$BRIDGE_HOST_NAME:$BRIDGE_PORT",
                CLIENT_ID,
                /*Ok if lost on reboot*/ MemoryPersistence()))

        client.setCallback(object : MqttCallback {
            override fun messageArrived(topic: String?, message: MqttMessage?) {
                LOG.info { "messageArrived topic:$topic, message:${message?.payload?.toString()}" }
                // TODO: JSON parsing
            }

            override fun connectionLost(e: Throwable?) = LOG.error { "connectionLost: $e" }
            override fun deliveryComplete(token: IMqttDeliveryToken?) = LOG.debug { "deliveryComplete token:$token" }
        })

        if (client.isConnected) {
            client.subscribe(TOPIC)
            LOG.info { "Done subscribing." }
            // https://cloud.google.com/iot/docs/how-tos/commands#iot-core-send-command-nodejs
            // `gcloud iot devices describe $DEVICE_ID --project=$GCP_PROJECT_ID --region=$GCP_REGION  --registry=$REGISTRY_NAME`
            val message = MqttMessage("Hello World of MQTT - right back atcha!".toByteArray())
            message.qos = 1
            client.publish("/devices/$DEVICE_ID/events", message)
        } else {
            LOG.warn { "MQTT Client was unable to connect, skipped send and receive" }
        }

        LOG.debug { "MQTT:init:end" }
    }

    private fun connectWithExponentialRetry(client: MqttClient): MqttClient {
        // GCP Requirements
        val connectOptions = MqttConnectOptions()
        connectOptions.mqttVersion = MqttConnectOptions.MQTT_VERSION_3_1_1
        connectOptions.userName = "notused"
        connectOptions.password = createJwtRsa().toCharArray()

        exponentialRetryDelayMs().forEach { retryIntervalMs ->
            try {
                client.connect(connectOptions)
                return client
            } catch (e: MqttException) {
                when (e.reasonCode) {
                    REASON_CODE_CONNECTION_LOST.toInt(),
                    REASON_CODE_SERVER_CONNECT_ERROR.toInt() -> {
                        Thread.sleep(retryIntervalMs)
                        LOG.debug { "Exponential backoff $retryIntervalMs" }
                    }
                    else -> {
                        LOG.warn("Unknown MQTT connection issue (not retrying): $e")
                        return client
                    }
                }
            }
        }
        return client
    }

    override fun close() {
        if (client.isConnected) {
            client.disconnect()
        }
        client.close()
    }

    private fun createJwtRsa(): String {
        val now = DateTime()
        // openssl pkcs8 -topk8 -nocrypt -in rsa_private.pem -outform der -out rsa_private.der
        val privateKey = javaClass.classLoader.getResource("rsa_private.der")!!.readBytes()
        val jwtBuilder = Jwts.builder()
                .setIssuedAt(now.toDate())
                .setExpiration(now.plusDays(1).toDate())
                .setAudience("whiteboardbot")
        val spec = PKCS8EncodedKeySpec(privateKey)
        val kf = KeyFactory.getInstance("RSA")
        return jwtBuilder.signWith(SignatureAlgorithm.RS256, kf.generatePrivate(spec)).compact()
    }

    companion object {
        private val LOG = KotlinLogging.logger {}

        // General Google Cloud
        const val BRIDGE_HOST_NAME = "mqtt.googleapis.com"
        const val BRIDGE_PORT = 8883

        // Project and device specific
        private const val GCP_PROJECT_ID = "whiteboardbot"
        private const val GCP_REGION = "us-central1"
        private const val REGISTRY_NAME = "wbb-registry"
        private const val DEVICE_ID = "ev3"

        // Combined strings
        private const val TOPIC = "/devices/$DEVICE_ID/commands/#"
        private const val CLIENT_ID = "projects/$GCP_PROJECT_ID/locations/$GCP_REGION/registries/$REGISTRY_NAME/devices/$DEVICE_ID"
    }
}


