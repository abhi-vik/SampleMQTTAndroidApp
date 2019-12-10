package edu.utexas.mpc.samplemqttandroidapp

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.widget.Button
import org.eclipse.paho.android.service.MqttAndroidClient
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended
import org.eclipse.paho.client.mqttv3.MqttMessage

class MainActivity : AppCompatActivity() {

    lateinit var connectButton: Button
    lateinit var rockButton: Button
    lateinit var paperButton: Button
    lateinit var scissorsButton: Button

    lateinit var mqttAndroidClient: MqttAndroidClient

    val serverUri = "tcp://192.168.4.1:1883"
    val clientId = "EmergingTechMQTTClient"

//    val subscribeTopic = "steps"
    val publishTopic = "rockpaperscissors"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        connectButton = this.findViewById(R.id.connect)
        rockButton = this.findViewById(R.id.rock)
        paperButton = this.findViewById(R.id.paper)
        scissorsButton = this.findViewById(R.id.scissors)

        rockButton.visibility = Button.INVISIBLE
        paperButton.visibility = Button.INVISIBLE
        scissorsButton.visibility = Button.INVISIBLE

        connectButton.setOnClickListener { syncWithPi() }
        rockButton.setOnClickListener { sendHand(1) }
        paperButton.setOnClickListener { sendHand(2) }
        scissorsButton.setOnClickListener { sendHand(3) }

        mqttAndroidClient = MqttAndroidClient(applicationContext, serverUri, clientId)

        mqttAndroidClient.setCallback(object : MqttCallbackExtended {

            override fun connectComplete(reconnect: Boolean, serverURI: String?) {
                println("Connection Complete!!")
//                mqttAndroidClient.subscribe(subscribeTopic, 0)
                connectButton.visibility = Button.INVISIBLE
                rockButton.visibility = Button.VISIBLE
                paperButton.visibility = Button.VISIBLE
                scissorsButton.visibility = Button.VISIBLE
            }

            override fun messageArrived(topic: String?, message: MqttMessage?) {
                println("Message Arrived")
                println(message)
            }

            override fun connectionLost(cause: Throwable?) {
                println("Connection Lost")
            }

            override fun deliveryComplete(token: IMqttDeliveryToken?) {
                println("Delivery Complete")
            }
        })
    }

    private fun syncWithPi() {
        println("+++++++ Connecting...")
        mqttAndroidClient.connect()
        connectButton.isEnabled = false
    }

    private fun sendRock() {
        sendHand(1)
    }

    private fun sendPaper() {
        sendHand(2)
    }

    private fun sendScissors() {
        sendHand(3)
    }

    private fun sendHand(value: Int) {
        println("Sending $value")
        val message = MqttMessage()
        message.payload = value.toString().toByteArray()
        mqttAndroidClient.publish(publishTopic, message)
    }
}