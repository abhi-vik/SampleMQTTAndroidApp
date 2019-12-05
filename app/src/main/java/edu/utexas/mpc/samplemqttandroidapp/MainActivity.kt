package edu.utexas.mpc.samplemqttandroidapp

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.widget.Button
import android.widget.TextView
import android.widget.ImageView
import android.provider.Settings
import android.content.Intent
import android.app.AlertDialog
import kotlin.concurrent.schedule
import com.android.volley.RequestQueue
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.google.gson.annotations.SerializedName
import com.squareup.picasso.Picasso
import java.util.Timer
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import org.eclipse.paho.android.service.MqttAndroidClient
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended
import org.eclipse.paho.client.mqttv3.MqttMessage
import java.lang.StringBuilder

class MainActivity : AppCompatActivity() {

    lateinit var retrieveButton: Button
    lateinit var temperature: TextView
    lateinit var imageView: ImageView
    //    lateinit var switchButton: Button
    lateinit var publishButton: Button
    lateinit var textView: TextView

    lateinit var mqttAndroidClient: MqttAndroidClient

    lateinit var queue: RequestQueue
    lateinit var gson: Gson
    lateinit var mostRecentWeatherResult: WeatherResult
    lateinit var mostRecentWeatherForecast: WeatherForecastResult

    val serverUri = "tcp://192.168.4.1:1883"
    val clientId = "EmergingTechMQTTClient"

    val subscribeTopic = "steps"
    val publishTopic = "weather"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        retrieveButton = this.findViewById(R.id.retrieveButton)
        temperature = this.findViewById(R.id.temperature)
        imageView = this.findViewById(R.id.imageView)
//        switchButton = this.findViewById(R.id.switchButton)
        publishButton = this.findViewById(R.id.publishButton)
        textView = this.findViewById(R.id.text)

        mqttAndroidClient = MqttAndroidClient(applicationContext, serverUri, clientId)

        queue = Volley.newRequestQueue(this)
        gson = Gson()


        retrieveButton.setOnClickListener({ getAllWeather() })
//        switchButton.setOnClickListener({ switchNetworks() })
        publishButton.visibility = Button.INVISIBLE
        publishButton.setOnClickListener({ sendMessage() })

        mqttAndroidClient.setCallback(object : MqttCallbackExtended {

            override fun connectComplete(reconnect: Boolean, serverURI: String?) {
                println("Connection Complete!!")
                mqttAndroidClient.subscribe(subscribeTopic, 0)
                sendWeather()
            }

            override fun messageArrived(topic: String?, message: MqttMessage?) {
                println("Message Arrived")
                println(message)
                textView.text = message.toString()
            }

            override fun connectionLost(cause: Throwable?) {
                println("Connection Lost")
            }

            override fun deliveryComplete(token: IMqttDeliveryToken?) {
                println("Delivery Complete")
            }
        })

    }

    private fun makeWeatherURL(type: String, count: Boolean = false): String {
        var url = "https://api.openweathermap.org/data/2.5/$type?q=Austin&units=imperial&appid=03519fb228fd7abd9e4f94d06d81eb27"
        if (count) {
            url += "&cnt=16"
        }
        return url
    }

    private fun getForecast() {
        val url = makeWeatherURL("forecast", true)

        val stringRequest = object : StringRequest(com.android.volley.Request.Method.GET, url,
                com.android.volley.Response.Listener<String> { response ->
                    mostRecentWeatherForecast = gson.fromJson(response, WeatherForecastResult::class.java)
                },
                com.android.volley.Response.ErrorListener { println("******That didn't work!") }) {}
        queue.add(stringRequest)
    }

    private fun getWeather() {
        val url = makeWeatherURL("weather")

        val stringRequest = object : StringRequest(com.android.volley.Request.Method.GET, url,
                com.android.volley.Response.Listener<String> { response ->
                    mostRecentWeatherResult = gson.fromJson(response, WeatherResult::class.java)
                    val icon = mostRecentWeatherResult.weather.get(0).icon
                    temperature.text = (mostRecentWeatherResult.main.temp.toString() + " °F")
                    Picasso.get().load("https://openweathermap.org/img/wn/$icon@2x.png").into(imageView)
                },
                com.android.volley.Response.ErrorListener { println("******That didn't work!") }) {}
        queue.add(stringRequest)
    }

    private fun getAllWeather() {
        val that = this

        getWeather()
        getForecast()
        temperature.text = StringBuilder("Hold tight!").toString()
        retrieveButton.visibility = Button.INVISIBLE

        Timer("SettingUp", false).schedule(3000) {
            that.runOnUiThread(Runnable() {
                AlertDialog.Builder(that)
                        .setTitle("Weather Received!")
                        .setMessage("You will be redirected to the WiFi Settings page. Connect to \"IOT-MIS-21\". " +
                                "Press the Back button to return here and then hit \"Check Goal Progress\".")
                        .setPositiveButton("Understood", { _, _ ->
                            startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
                        })
                        .create()
                        .show()

                publishButton.visibility = Button.VISIBLE
            })
        }
    }

    private fun sendMessage() {
        if (mqttAndroidClient.isConnected) {
            sendWeather()
        } else {
            syncWithPi()
        }
    }

    private fun syncWithPi() {
        println("+++++++ Connecting...")
        mqttAndroidClient.connect()
    }

    private fun extractPrecipitation(rain: Rain?): Double {
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

                totalPrecipitation += extractPrecipitation(weatherResult.rain)
            }
        }

        val map = """{
            "now": {
                "temp_min": "${mostRecentWeatherResult.main.temp_min}",
                "temp_max": "${mostRecentWeatherResult.main.temp_max}",
                "rain": ${extractPrecipitation(mostRecentWeatherResult.rain)}
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
        textView.text = StringBuilder("Hold tight!").toString()
    }
}

class WeatherResult(val id: Int, val name: String, val cod: Int, val coord: Coordinates, val main: WeatherMain, val weather: Array<Weather>, val rain: Rain?, val dt_txt: String)
class Rain(@SerializedName("3h") val threehour: Double?, @SerializedName("1h") val onehour: Double?)
class Coordinates(val lon: Double, val lat: Double)
class Weather(val id: Int, val main: String, val description: String, val icon: String)
class WeatherMain(val temp: Double, val pressure: Int, val humidity: Int, val temp_min: Double, val temp_max: Double)
class WeatherForecastResult(val list: List<WeatherResult>)