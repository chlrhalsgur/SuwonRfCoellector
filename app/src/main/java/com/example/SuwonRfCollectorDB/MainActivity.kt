package com.example.SuwonRfCollectorDB

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.wifi.WifiManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Vibrator
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.Toast
import androidx.core.app.ActivityCompat
import com.example.coexlibrary.ExIndoorLocalization
import com.example.heropdr.HeroPDR
import com.google.android.gms.location.FusedLocationProviderClient
import com.kircherelectronics.fsensor.observer.SensorSubject
import com.kircherelectronics.fsensor.sensor.FSensor
import com.kircherelectronics.fsensor.sensor.gyroscope.GyroscopeSensor
import kotlinx.coroutines.*
import kotlin.math.round


class MainActivity : AppCompatActivity(), SensorEventListener {
    private var pdrAngle = 0f
    private var curFloor = -2.0
    private val mSensorManager by lazy {
        getSystemService(Context.SENSOR_SERVICE) as SensorManager
    }
    private val vibrator by lazy {
        getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    private var arrow_update_time: Long = 300

    /* 자이로 센서 관련 변수 */
    private lateinit var fSensor: FSensor
    private var fusedOrientation = FloatArray(3)

    /* 자이로스코프 관련 함수 */
    private val sensorObserver = SensorSubject.SensorObserver { values -> updateValues(values!!) }
    private fun updateValues(values: FloatArray) {
        fusedOrientation = values
    }
    var webViewOn = false

    /* RF 엔진 관련 변수 */
    var wifiRange = arrayListOf(-1.0f, -1.0f, -1.0f, -1.0f)

    val wifiScanReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val success = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false)
            exIndoorLocalization.wifipermitted = success
        }
    }

    private lateinit var fusedLocationClient: FusedLocationProviderClient

    /* 측위 관련 변수 */
    private lateinit var exIndoorLocalization: ExIndoorLocalization
    private var resultPosition : ArrayList<Double> = arrayListOf(0.0, 0.0, 0.0)
    private var preResultPosition : ArrayList<Double> = arrayListOf(1.0, 1.0, 1.0)

    /* 웹뷰 관련 변수 */
    private val mHandler: Handler = Handler(Looper.myLooper()!!)
    private lateinit var webView : WebView
    private lateinit var updateBtn: Button
    private var seekBarProgress = 0
    private var inputAngle = 0
    private var inputPosX = 0.0
    private var inputPosY = 0.0
    private var inputPosZ = 0.0
    private var HTML_FILE = "http://163.152.52.60:8001/"
//    private val HTML_FILE = "http:163.152.52.60:8001/coex/"

    private val accXMovingAverage: com.example.coexlibrary.sensors.MovingAverage =
        com.example.coexlibrary.sensors.MovingAverage(10)
    private val accYMovingAverage: com.example.coexlibrary.sensors.MovingAverage =
        com.example.coexlibrary.sensors.MovingAverage(10)
    private val accZMovingAverage: com.example.coexlibrary.sensors.MovingAverage =
        com.example.coexlibrary.sensors.MovingAverage(10)

    /* 자이로 센서 관련 변수 */
    private var gyro_get_cnt = 0
    private var gyro_stabilized = false
    private var gyro_cali_value = 0.0f

    /* 자기장 센서 관련 변수 */
    private var magneticSensorStabilize : Boolean = false
    private var sensorStabilize : Int = 0

    private var isFirstInit : Boolean = true
    private var lastStep_pdr = 0
    private var magMatrix: FloatArray = floatArrayOf()
    private var accMatrix: FloatArray = floatArrayOf()
    val heroPDR: HeroPDR = HeroPDR()
    var history_of_PDR_position : ArrayList<Array<Int>> = arrayListOf()
    var isSensorStabled: Boolean = false
    private var gyroStableCount : Int = 100
    private var gyroCaliValue = 0.0f
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        checkPermission()
        webView = findViewById(R.id.webView)
        updateBtn = findViewById(R.id.updateBtn)


        updateBtn.setOnClickListener {
            val intent = Intent(this, SettingActivity::class.java)
            startActivity(intent)
            setContentView(R.layout.activity_main)
            removeDot()
            exIndoorLocalization.finishRfThread()
        }


        webViewSetting(HTML_FILE)


        // 자이로스코프 센서 초기화
        fSensor = GyroscopeSensor(this)
        (fSensor as GyroscopeSensor).register(sensorObserver)
        (fSensor as GyroscopeSensor).start()

        exIndoorLocalization = ExIndoorLocalization(this)


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
    private fun isReadyLocalization(event: SensorEvent, fusedOrientation : FloatArray) : Boolean {
        if (isSensorStabled) {
            return true
        } else {
            when (event.sensor.type) {
                Sensor.TYPE_GYROSCOPE -> {  // 230911 메인 엑티비티에 있던 자기장, 자이로 안정화 코드 내부로 이동
                    gyroStableCount--
                }
            }
            if (gyroStableCount <= 0 ) {
                gyroCaliValue = ((Math.toDegrees(fusedOrientation[0].toDouble()) + 360) % 360).toFloat()
            }

            isSensorStabled = gyroStableCount <= 0
            return isSensorStabled
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event?.let { isReadyLocalization(it, fusedOrientation) }
        if (event != null) {
            when (event.sensor.type){
                Sensor.TYPE_ACCELEROMETER -> {
                    accMatrix = event.values.clone()
                }
                Sensor.TYPE_ROTATION_VECTOR -> {
                    heroPDR.setQuaternion(event.values.clone())
                }
                Sensor.TYPE_MAGNETIC_FIELD -> {
                    /* 자기장 센서 안정화 */
                    if (!magneticSensorStabilize) {
                        if (sensorStabilize <= 200){
                            sensorStabilize ++
                            return
                        }
                        else
                            magneticSensorStabilize = true
                    }

                    magMatrix = event.values.clone()
                }

                /* 자이로 센서 안정화 */
                Sensor.TYPE_GYROSCOPE -> {
                    gyroStabilize()
                    val gyro = (((Math.toDegrees(fusedOrientation[0].toDouble()).toFloat() + 360) % 360 - gyroCaliValue ) + 360) % 360
                    pdrAngle = (gyro + inputAngle)%360

                    printArrowInWebView(pdrAngle)
                }

                Sensor.TYPE_LINEAR_ACCELERATION -> {
                    accXMovingAverage.newData(event.values[0].toDouble())
                    accYMovingAverage.newData(event.values[1].toDouble())
                    accZMovingAverage.newData(event.values[2].toDouble())
                }
            }

            // 센서 안정화가 끝나면, 사용자한테 메세지와 진동을 통해 보행 시작을 유도합니다.
            if (isFirstInit && magneticSensorStabilize) {
                Toast.makeText(this, "지금부터 걸어주세요.", Toast.LENGTH_SHORT).show()
                vibrator.vibrate(160)
                isFirstInit = false
                /* 일단 현재 첫 위치 찍기 */
                printDotInWebView(inputPosX, inputPosY, true)  // 마지막 인자에 true가 들어가야합니다.
//                send_data_to_server(cur_PDR_position[0].toInt(), cur_PDR_position[1].toInt(), caliVector[0].toInt(), caliVector[1].toInt(), caliVector[2].toInt())
                history_of_PDR_position.add(arrayOf(round(inputPosX.toDouble()).toInt(), round(
                    inputPosY.toDouble()
                ).toInt()))
            }
            resultPosition = exIndoorLocalization.sensorChanged(event, fusedOrientation, pdrAngle)

            if (resultPosition != preResultPosition) {

                preResultPosition = resultPosition
            }

            if (lastStep_pdr != exIndoorLocalization.stepCount) { // 걸음 인식 되면,
                vibrator.vibrate(30)
                lastStep_pdr = exIndoorLocalization.stepCount
                printDotInWebView(exIndoorLocalization.coorPdr[0], exIndoorLocalization.coorPdr[1], true)
                printRedDot(resultPosition[0], resultPosition[1])


                if (exIndoorLocalization.rfCng){
                    printBlueDot(exIndoorLocalization.coorPdr[0], exIndoorLocalization.coorPdr[1])
                    exIndoorLocalization.rfMkCng()
                }
            }
        }
    }

    override fun onAccuracyChanged(p0: Sensor?, p1: Int) {
        val a = 1
    }

    override fun onStart() {
        super.onStart()
        // 웹뷰 클라이언트 설정
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                Thread.sleep(2000)

                webView.loadUrl("javascript:setTestbed('coex', floor='1', mode='test')")

                val inputPosXL = intent.getStringExtra("inputPosX")?.toFloat()
                val inputPosYL = intent.getStringExtra("inputPosY")?.toFloat()
                val inputAngleL = intent.getStringExtra("curAngle")?.toInt()
                if (inputPosXL != null) {
                    inputPosX = inputPosXL.toDouble()
                }
                if (inputPosYL != null) {
                    inputPosY = inputPosYL.toDouble()
                }
                if (inputAngleL != null){
                    inputAngle = inputAngleL
                }
                Log.d("asdjasijd", inputAngle.toString())
                exIndoorLocalization.coorPdr = arrayListOf(inputPosX,inputPosY,0.0)
                webViewOn = true
                exIndoorLocalization.webViewOn()
            }

        }
    }

    fun printDotInWebView(x: Double, y: Double, remove_range: Boolean = true) {
        var states = if(exIndoorLocalization.stepCount > 50){ 1 }else{ 2 }

        mHandler.postDelayed(Runnable {
            webView.loadUrl("javascript:show_my_position($y, $x,2)")
        }, 100)
    }

    fun printRangeInWebView(wifiRange: ArrayList<Float>, floor: Int) {
        mHandler.postDelayed(kotlinx.coroutines.Runnable {
            webView.loadUrl("javascript:showArea(${wifiRange[2]},${wifiRange[3]},${wifiRange[0]},${wifiRange[1]},$floor)")
            if ((wifiRange[3]-wifiRange[2]) > 10)
                webView.loadUrl("javascript:show_my_position(${(wifiRange[2]+wifiRange[3])/2}, ${(wifiRange[0]+wifiRange[1])/2}, ${floor})")
        }, 100)
    }


    fun removeRangeInWebView() {
        mHandler.postDelayed(kotlinx.coroutines.Runnable {
            webView.loadUrl("javascript:showArea(0,0,0,0)")
        }, 100)
    }

    fun printArrowInWebView(gyro_from_map: Float) {
        mHandler.postDelayed(kotlinx.coroutines.Runnable {
            webView.loadUrl("javascript:rotateArrow($gyro_from_map)")
            webView.loadUrl("javascript:arrow_rotation($gyro_from_map)")
        }, 100)
    }
    fun removeDot(){
        mHandler.postDelayed(kotlinx.coroutines.Runnable {
            webView.loadUrl("javascript:make_matrix(0,0,2,0,0,'blue')")
        }, 100)
    }
    fun printBlueDot(x:Double, y:Double){
        mHandler.postDelayed(kotlinx.coroutines.Runnable {
            webView.loadUrl("javascript:make_matrix($y, $x, 2, 1, 1, 'blue')")
        }, 100)
    }
    fun printRedDot(x:Double, y:Double){
        mHandler.postDelayed(kotlinx.coroutines.Runnable {
            webView.loadUrl("javascript:make_matrix($y, $x, 2, 1, 1, 'red')")
        }, 100)
    }

    private fun checkPermission() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
            && ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {

            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION)
                && ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                && ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                ActivityCompat.requestPermissions(this,
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    ), 101)
            } else {
                ActivityCompat.requestPermissions(this,
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    ), 101)
            }
        }
    }

    private fun webViewSetting(html_file_name: String) {
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                return false
            }
        }

        webView.addJavascriptInterface(WebviewInterface(this), "NaviEvent")
        webView.loadUrl(html_file_name)
        webView.scrollTo(1690, 480)
        webView.isScrollbarFadingEnabled = true
        webView.setInitialScale(160)

        val webSettings = webView.settings
        webSettings.useWideViewPort = true
        webSettings.builtInZoomControls = true
        webSettings.javaScriptEnabled = true
        webSettings.javaScriptCanOpenWindowsAutomatically = false
        webSettings.setSupportMultipleWindows(false)
        webSettings.setSupportZoom(true)
        webSettings.domStorageEnabled = true
    }

    override fun onResume() {
        super.onResume()
        mSensorManager.registerListener(this, mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_GAME)
        mSensorManager.registerListener(this, mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD), SensorManager.SENSOR_DELAY_GAME)
        mSensorManager.registerListener(this, mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE), SensorManager.SENSOR_DELAY_GAME)
        mSensorManager.registerListener(this, mSensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION), SensorManager.SENSOR_DELAY_GAME)
        mSensorManager.registerListener(this, mSensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE), SensorManager.SENSOR_DELAY_GAME)
        mSensorManager.registerListener(this, mSensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR), SensorManager.SENSOR_DELAY_GAME)
        mSensorManager.registerListener(this, mSensorManager.getDefaultSensor(Sensor.TYPE_LIGHT), SensorManager.SENSOR_DELAY_GAME)
        mSensorManager.registerListener(this, mSensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR), SensorManager.SENSOR_DELAY_GAME)
        /* RF 엔진 관련 */
        registerReceiver(wifiScanReceiver, IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION))
    }

    override fun onPause() {
        super.onPause()
        mSensorManager.unregisterListener(this)
        unregisterReceiver(wifiScanReceiver)
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}


class WebviewInterface(private val mContext: Context) {
    @JavascriptInterface
    fun showAndroidToast(toast: String) {
        Toast.makeText(mContext, toast, Toast.LENGTH_SHORT).show()
    }
}