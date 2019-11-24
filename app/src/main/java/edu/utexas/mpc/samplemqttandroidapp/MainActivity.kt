package edu.utexas.mpc.samplemqttandroidapp

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.widget.Button
import android.widget.TextView
import android.widget.ImageView
import android.provider.Settings
import android.content.Intent
import com.android.volley.RequestQueue
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.google.gson.annotations.SerializedName
import com.squareup.picasso.Picasso
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import org.eclipse.paho.android.service.MqttAndroidClient
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended
import org.eclipse.paho.client.mqttv3.MqttMessage

class MainActivity : AppCompatActivity() {

    // I'm using lateinit for these widgets because I read that repeated calls to findViewById
    // are energy intensive
    lateinit var retrieveButton: Button
    lateinit var temperature: TextView
    lateinit var imageView: ImageView
    lateinit var switchButton: Button
    lateinit var syncButton: Button
    lateinit var publishButton: Button
    lateinit var textView: TextView

    // I'm doing a late init here because I need this to be an instance variable but I don't
    // have all the info I need to initialize it yet
    lateinit var mqttAndroidClient: MqttAndroidClient

    lateinit var queue: RequestQueue
    lateinit var gson: Gson
    lateinit var mostRecentWeatherResult: WeatherResult
    lateinit var mostRecentWeatherForecast: WeatherForecastResult

    // you may need to change this depending on where your MQTT broker is running
    val serverUri = "tcp://192.168.4.1:1883"
    // you can use whatever name you want to here
    val clientId = "EmergingTechMQTTClient"

    //these should "match" the topics on the "other side" (i.e, on the Raspberry Pi)
    val subscribeTopic = "steps"
    val publishTopic = "weather"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        retrieveButton = this.findViewById(R.id.retrieveButton)
        temperature = this.findViewById(R.id.temperature)
        imageView = this.findViewById(R.id.imageView)
        switchButton = this.findViewById(R.id.switchButton)
        syncButton = this.findViewById(R.id.syncButton)
        publishButton = this.findViewById(R.id.publishButton)
        textView = this.findViewById(R.id.text)

        mqttAndroidClient = MqttAndroidClient(getApplicationContext(), serverUri, clientId)

        queue = Volley.newRequestQueue(this)
        gson = Gson()

        retrieveButton.setOnClickListener({ getAllWeather() })

        switchButton.setOnClickListener({ switchNetworks() })
        syncButton.setOnClickListener({ syncWithPi() })
        publishButton.setOnClickListener({ sendWeather() })

        // when things happen in the mqtt client, these callbacks will be called
        mqttAndroidClient.setCallback(object : MqttCallbackExtended {

            // when the client is successfully connected to the broker, this method gets called
            override fun connectComplete(reconnect: Boolean, serverURI: String?) {
                println("Connection Complete!!")
                // this subscribes the client to the subscribe topic
                mqttAndroidClient.subscribe(subscribeTopic, 0)
            }

            // this method is called when a message is received that fulfills a subscription
            override fun messageArrived(topic: String?, message: MqttMessage?) {
                println("received")
                textView.text = (message.toString() + " steps")
            }

            override fun connectionLost(cause: Throwable?) {
                println("Connection Lost")
            }

            // this method is called when the client succcessfully publishes to the broker
            override fun deliveryComplete(token: IMqttDeliveryToken?) {
                println("Delivery Complete")
            }
        })

    }

    private fun getForecast() {
        val url = "https://api.openweathermap.org/data/2.5/forecast?q=London&units=imperial&cnt=16&appid=03519fb228fd7abd9e4f94d06d81eb27"

        val stringRequest = object : StringRequest(com.android.volley.Request.Method.GET, url,
                com.android.volley.Response.Listener<String> { response ->
                    mostRecentWeatherForecast = gson.fromJson(response, WeatherForecastResult::class.java)
                },
                com.android.volley.Response.ErrorListener { println("******That didn't work!") }) {}
        queue.add(stringRequest)
    }

    private fun getWeather() {
        val url = "https://api.openweathermap.org/data/2.5/weather?q=London&units=imperial&appid=03519fb228fd7abd9e4f94d06d81eb27"

        val stringRequest = object : StringRequest(com.android.volley.Request.Method.GET, url,
                com.android.volley.Response.Listener<String> { response ->
                    mostRecentWeatherResult = gson.fromJson(response, WeatherResult::class.java)
                    val icon = mostRecentWeatherResult.weather.get(0).icon
                    temperature.text = (mostRecentWeatherResult.main.temp.toString() + " F")
                    Picasso.get().load("https://openweathermap.org/img/wn/$icon@2x.png").into(imageView)
                },
                com.android.volley.Response.ErrorListener { println("******That didn't work!") }) {}
        queue.add(stringRequest)
    }

    private fun getAllWeather() {
        getWeather()
        getForecast()
    }

    // this method just connects the paho mqtt client to the broker
    private fun syncWithPi() {
        println("+++++++ Connecting...")
        mqttAndroidClient.connect()
    }

    // this method just connects the paho mqtt client to the broker
    private fun switchNetworks() {
        println("Attempt intent")
        startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
    }

    private fun extractPreciptation(rain: Rain?): Double {
        var precipitation = 0.0
        if (rain?.onehour != null) {
            precipitation += rain.onehour.toDouble()
        }
        if (rain?.threehour != null) {
            precipitation += rain.threehour.toDouble()
        }
        return precipitation
    }

    private fun sendWeather() {
        val gson = GsonBuilder().setPrettyPrinting().create()

        var lowestTemp = Double.MAX_VALUE
        var highestTemp = Double.MIN_VALUE
        var totalPrecipitation = 0.0

        val tomorrow = LocalDateTime.now().plusDays(1)
        val tomorrowDateString = tomorrow.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))

        for (weatherResult in mostRecentWeatherForecast.list) {
            if (weatherResult.dt_txt.startsWith(tomorrowDateString)) {
                if (weatherResult.main.temp_min < lowestTemp) {
                    lowestTemp = weatherResult.main.temp_min
                }
                if (weatherResult.main.temp_max > highestTemp) {
                    highestTemp = weatherResult.main.temp_max
                }

                totalPrecipitation += extractPreciptation(weatherResult.rain)
            }
        }

        println(lowestTemp)
        println(highestTemp)
        println(totalPrecipitation)

        val map = """{
            "now": {
                "temp_min": "${mostRecentWeatherResult.main.temp_min}",
                "temp_max": "${mostRecentWeatherResult.main.temp_max}",
                "rain": ${extractPreciptation(mostRecentWeatherResult.rain)}
            },
            "later": {
                "temp_min": "$lowestTemp",
                "temp_max": "$highestTemp",
                "rain": $totalPrecipitation
            }
        }"""

        val jsonMap: Map<String, Any> = gson.fromJson(map, object : TypeToken<Map<String, Any>>() {}.type)
        val weatherMainString = gson.toJson(jsonMap)

        val message = MqttMessage()

        message.payload = weatherMainString.toByteArray()
        println(message)
        mqttAndroidClient.publish(publishTopic, message)
    }
}

class WeatherResult(val id: Int, val name: String, val cod: Int, val coord: Coordinates, val main: WeatherMain, val weather: Array<Weather>, val rain: Rain?, val dt_txt: String)
class Rain(@SerializedName("3h") val threehour: Double?, @SerializedName("1h") val onehour: Double?)
class Coordinates(val lon: Double, val lat: Double)
class Weather(val id: Int, val main: String, val description: String, val icon: String)
class WeatherMain(val temp: Double, val pressure: Int, val humidity: Int, val temp_min: Double, val temp_max: Double)

class WeatherForecastResult(val list: List<WeatherResult>)