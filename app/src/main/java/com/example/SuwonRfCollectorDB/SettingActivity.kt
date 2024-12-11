package com.example.SuwonRfCollectorDB

import android.annotation.SuppressLint
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.MotionEvent
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.kircherelectronics.fsensor.observer.SensorSubject
import com.kircherelectronics.fsensor.sensor.FSensor
import com.kircherelectronics.fsensor.sensor.gyroscope.GyroscopeSensor
import kotlin.math.round


class SettingActivity  : AppCompatActivity(), SensorEventListener {


    /* 자이로 센서 관련 변수 */
    private lateinit var fSensor: FSensor
    private var fusedOrientation = FloatArray(3)

    /* 자이로스코프 관련 함수 */
    private val sensorObserver = SensorSubject.SensorObserver { values -> updateValues(values!!) }
    private fun updateValues(values: FloatArray) {
        fusedOrientation = values
    }
    /* 웹뷰 관련 변수 */
    private val mHandler: Handler = Handler(Looper.myLooper()!!)
    private lateinit var webView : WebView
    private lateinit var seekBar: SeekBar
    private lateinit var goBtn: Button
    private lateinit var inputPosX: TextView
    private lateinit var inputPosY: TextView
    private lateinit var inputPosZ: TextView
    private lateinit var curAngle: TextView
    private var seekBarProgress = 0

    /* 자기장 센서 관련 변수 */
    private var magneticSensorStabilize : Boolean = false
    private var sensorStabilize : Int = 0
    private var is_popup_on : Boolean = false
    private lateinit var alertDialog : AlertDialog

    private var isFirstInit : Boolean = true
    private var lastStep_pdr = 0
    private var caliVector: Array<Float> = arrayOf()
    private var magMatrix: FloatArray = floatArrayOf()
    private var accMatrix: FloatArray = floatArrayOf()
    /* 자이로 센서 관련 변수 */
    private var gyro_get_cnt = 0
    private var gyro_stabilized = false
    private var gyro_cali_value = 0.0f
    private var yaw_angle = 0.0f

    private var HTML_FILE = "file:///android_asset/코엑스몰-setting.html"
//    private val HTML_FILE = "http:163.152.52.60:8001/coex/"

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setting)

        webView = findViewById(R.id.webView)
        seekBar = findViewById(R.id.seekBar)
        goBtn = findViewById(R.id.goBtn)
        inputPosX = findViewById(R.id.input_pos_x)
        inputPosY = findViewById(R.id.input_pos_y)
        inputPosZ = findViewById(R.id.input_pos_z)
        curAngle = findViewById(R.id.cur_angle)
        goBtn = findViewById(R.id.goBtn)

        HTML_FILE = "file:///android_asset/코엑스몰-setting.html"
        webViewSetting(HTML_FILE)


        goBtn.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            setContentView(R.layout.activity_main)
            val ix = inputPosX.text.toString()
            val iy = inputPosY.text.toString()
            val iA = curAngle.text.toString()
            intent.putExtra("inputPosX", ix)
            intent.putExtra("inputPosY", iy)
            intent.putExtra("curAngle", iA)

            Log.d("asdjasijd233332444444",  iA.toString())
            Log.d("asdjasijd233332444444",  ix.toString())
            Log.d("asdjasijd233332444444",  iy.toString())
            startActivity(intent)
        }


        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                curAngle.text = progress.toString()
                webView.loadUrl("javascript:rotateArrow($progress)")
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // 자이로스코프 센서 초기화
        fSensor = GyroscopeSensor(this)
        (fSensor as GyroscopeSensor).register(sensorObserver)
        (fSensor as GyroscopeSensor).start()
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {

        if(event.getAction()== MotionEvent.ACTION_UP){
            webView.evaluateJavascript("getClickedPosition()") {
                    value ->
                val pos_arr = value.replace("\"", "").split("\\t")
                inputPosX.setText((round(pos_arr[0].toFloat()*100) /100).toString())
                inputPosY.setText((round(pos_arr[1].toFloat()*100) /100).toString())
            }
        }
        return super.dispatchTouchEvent(event);
    }

    fun printArrowInWebView(gyro_from_map: Float) {
        mHandler.postDelayed(kotlinx.coroutines.Runnable {
            webView.loadUrl("javascript:rotateArrow($gyro_from_map)")
            webView.loadUrl("javascript:arrow_rotation($gyro_from_map)")
        }, 100)
    }
    override fun onSensorChanged(event: SensorEvent?) {
        if (event != null) {
            when (event.sensor.type){
                Sensor.TYPE_ACCELEROMETER -> {
                }
                Sensor.TYPE_ROTATION_VECTOR -> {
                }
                Sensor.TYPE_MAGNETIC_FIELD -> {
                    /* 자기장 센서 안정화 */
                    if (!magneticSensorStabilize) {
                        if (sensorStabilize <= 200){
                            sensorStabilize ++
                            return // 센서 안정화되기 전까지는 onSensorChanged() 그냥 종료
                        }
                        else
                            magneticSensorStabilize = true
                    }


                    /* 자기장 맵 자동 업데이트용 - 매 순간의 자기장값 측정, 측정하자마자 자기장 맵 방향으로 vector calibration */
                    magMatrix = event.values.clone()
                }

                /* 자이로 센서 안정화 */
                Sensor.TYPE_GYROSCOPE -> {
                    gyroStabilize()
                }

                Sensor.TYPE_LINEAR_ACCELERATION -> {
                }
            }

        }
    }

    override fun onAccuracyChanged(p0: Sensor?, p1: Int) {
        1+1
    }

    private fun gyroStabilize() : Boolean {
        gyro_get_cnt++
        if (gyro_get_cnt > 200) {
            gyro_stabilized = true
            gyro_cali_value = ((Math.toDegrees(fusedOrientation[0].toDouble()) + 360) % 360).toFloat()
            return true
        }
        else
            return false
    }

    private fun webViewSetting(html_file_name: String) {
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                return false
            }
        }
        webView.clearCache(true)
        webView.clearHistory()
        webView.clearFormData()

        webView.goBack()
        webView.loadUrl(html_file_name)
        webView.scrollTo(1690, 480)
        webView.isScrollbarFadingEnabled = true
        webView.setInitialScale(160)

        val webSettings = webView.settings
        webSettings.cacheMode = WebSettings.LOAD_NO_CACHE
        webSettings.useWideViewPort = true
        webSettings.builtInZoomControls = true
        webSettings.javaScriptEnabled = true
        webSettings.javaScriptCanOpenWindowsAutomatically = false
        webSettings.setSupportMultipleWindows(false)
        webSettings.setSupportZoom(true)
        webSettings.domStorageEnabled = true
        webSettings.userAgentString = "Mozilla/5.0 (Linux; Android 5.1; XT1021 Build/LPCS23.13-34.8-3; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/55.0.2883.91 Mobile Safari/537.36"
    }

    override fun onStart() {
        super.onStart()
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                webView.loadUrl("javascript:setTestbed('suwonstation', floor='2', mode='setting')")
            }
        }
    }
    override fun onResume() {
        super.onResume()
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                webView.loadUrl("javascript:setTestbed('suwonstation', floor='2', mode='setting')")
            }
        }
    }

    override fun onPause() {
        super.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}
