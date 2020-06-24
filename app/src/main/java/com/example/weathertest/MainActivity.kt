package com.example.weathertest

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.AsyncTask
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.view.Menu
import android.view.View
import android.widget.ProgressBar
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.viewpager2.widget.ViewPager2
import com.google.android.gms.location.*
import kotlinx.android.synthetic.main.activity_main.*
import org.json.JSONObject
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    val API: String = "f4788fe2452a8792808ef8a838e16dea"
    val PERMISSION_ID = 42

    companion object {
        var lon = 37.34
        var lat = 126.94
        private const val MENU_ID_RECYCLER_ADAPTER = 100
        private const val MENU_ID_FRAGMENT_ADAPTER = 101
        private const val MENU_ID_ADD_ITEM = 103
        var count = 0;
    }

    lateinit var mFusedLocationClient: FusedLocationProviderClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        if(count == 0)
            startActivity(Intent(this, MainActivity::class.java))
        count++;
        refresh.setOnRefreshListener {
            //var intent = Intent(this, MainActivity::class.java)
            finish()
            startActivity(Intent(this, MainActivity::class.java))
            refresh.isRefreshing = false
        }

        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        getLastLocation()
        weatherTask().execute()

        // 여기에 when 사용해서 이미지 필터링 하는 코드 추가되어야함!!
        // 필터링 후에 아래의 어댑터 연결 부분에서 MenuData.values() 말고
        // 아예 필터링 된 이미지데이터셋을 넘겨 줄 예정.

        pager.adapter = PagerRecyclerAdapter(MenuData.values())
        pager.orientation = ViewPager2.ORIENTATION_HORIZONTAL
        pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                //Toast.makeText(baseContext, "Next", Toast.LENGTH_SHORT).show()

            }
        })
//*********************************버튼 클릭으로 map activity 에 인텐트 전달하는 부분 ******
        five_minute.setOnClickListener {
            Toast.makeText(this, "주변 5분거리 검색창으로 이동!", Toast.LENGTH_LONG).show()
            val intent:Intent = Intent(baseContext, MapsActivity::class.java)
            startActivity(intent)
        }
//****************************************************************************************
    }

    @SuppressLint("MissingPermission")
    private fun getLastLocation() {
        if (checkPermissions()) {
            if (isLocationEnabled()) {
                mFusedLocationClient.lastLocation.addOnCompleteListener(this) { task ->
                    var location: Location? = task.result
                    if (location == null) {
                        requestNewLocationData()
                    } else {
                        lat = location.latitude.toDouble()
                        lon = location.longitude.toDouble()
                    }
                }
            } else {
                Toast.makeText(this, "Turn on location", Toast.LENGTH_LONG).show()
                val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                startActivity(intent)
            }
        } else {
            requestPermissions()
        }
    }

    override fun onPrepareOptionsMenu(menu: Menu?): Boolean {
        menu?.removeGroup(Menu.NONE)
        if (pager.adapter is PagerRecyclerAdapter) {
            menu?.add(Menu.NONE, MENU_ID_FRAGMENT_ADAPTER, Menu.NONE, "FragmentStateAdapter Vertical")
        } else {
            menu?.add(Menu.NONE, MENU_ID_RECYCLER_ADAPTER, Menu.NONE, "RecyclerViewAdapter Horizontal")
        }
        menu?.add(Menu.NONE, MENU_ID_ADD_ITEM, Menu.NONE, "Add New Item")
        return super.onPrepareOptionsMenu(menu)
    }

    @SuppressLint("MissingPermission")
    private fun requestNewLocationData() {
        var mLocationRequest = LocationRequest()
        mLocationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        mLocationRequest.interval = 0
        mLocationRequest.fastestInterval = 0
        mLocationRequest.numUpdates = 1

        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        mFusedLocationClient!!.requestLocationUpdates(
                mLocationRequest, mLocationCallback,
                Looper.myLooper()
        )
    }

    private val mLocationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            var mLastLocation: Location = locationResult.lastLocation
            lat = mLastLocation.latitude.toDouble()
            lon = mLastLocation.longitude.toDouble()
        }
    }

    private fun isLocationEnabled(): Boolean {
        var locationManager: LocationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) || locationManager.isProviderEnabled(
                LocationManager.NETWORK_PROVIDER
        )
    }

    private fun checkPermissions(): Boolean {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED){
            return true
        }
        return false
    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION),
                PERMISSION_ID
        )
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        if (requestCode == PERMISSION_ID) {
            if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                // Granted. Start getting the location information
            }
        }
    }

    inner class weatherTask() : AsyncTask<String, Void, String>() {
        override fun onPreExecute() {
            super.onPreExecute()
            /* Showing the ProgressBar, Making the main design GONE */
            findViewById<ProgressBar>(R.id.loader).visibility = View.VISIBLE
            findViewById<RelativeLayout>(R.id.mainContainer).visibility = View.GONE
            findViewById<TextView>(R.id.errorText).visibility = View.GONE
        }

        override fun doInBackground(vararg params: String?): String? {

            var response:String?
            try{
                var lat = lat
                var lon = lon
                response = URL("https://api.openweathermap.org/data/2.5/weather?lat=$lat&lon=$lon&units=metric&appid=$API&lang=kr").readText(
                    Charsets.UTF_8
                )
            }catch (e: Exception){
                response = null
            }
            return response
        }

        override fun onPostExecute(result: String?) {
            super.onPostExecute(result)
            try {
                /* Extracting JSON returns from the API */
                val jsonObj = JSONObject(result)
                val main = jsonObj.getJSONObject("main")
                val sys = jsonObj.getJSONObject("sys")
                val weather = jsonObj.getJSONArray("weather").getJSONObject(0)

                val updatedAt:Long = jsonObj.getLong("dt")
                val updatedAtText = "업데이트 시간: "+ SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.KOREA).format(Date(updatedAt*1000))
                val _temp = main.getString("temp")
                val temp = Math.round(_temp.toDouble()).toString() + "°C"
                val tempMin = "최저 온도: " + main.getString("temp_min")+"°C"
                val tempMax = "최고 온도: " + main.getString("temp_max")+"°C"
                val _feelsLike = main.getString("feels_like")
                val feelsLike = Math.round(_feelsLike.toDouble()).toString() + "°C"
                val humidity = main.getString("humidity")+"%"
                val weatherDescription = weather.getString("description")

                var city_name = ""
                if(jsonObj.getString("name") == "Seongnam-si"){
                    city_name = "성남시"
                }
                else if(jsonObj.getString("name") == "Gunpo"){
                    city_name = "군포시"
                }
                else if(jsonObj.getString("name") == "Seoul"){
                    city_name = "서울"
                }

                val address = city_name
                //val address = jsonObj.getString("name")+", "+sys.getString("country")

                /* Populating extracted data into our views */
                findViewById<TextView>(R.id.address).text = address
                findViewById<TextView>(R.id.updated_at).text =  updatedAtText
                findViewById<TextView>(R.id.status).text = weatherDescription.capitalize()
                findViewById<TextView>(R.id.temp).text = temp
                findViewById<TextView>(R.id.temp_min).text = tempMin
                findViewById<TextView>(R.id.temp_max).text = tempMax
                findViewById<TextView>(R.id.feels_like).text = feelsLike
                findViewById<TextView>(R.id.humidity).text = humidity

                /* Views populated, Hiding the loader, Showing the main design */
                findViewById<ProgressBar>(R.id.loader).visibility = View.GONE
                findViewById<RelativeLayout>(R.id.mainContainer).visibility = View.VISIBLE

            } catch (e: Exception) {
                findViewById<ProgressBar>(R.id.loader).visibility = View.GONE
                findViewById<TextView>(R.id.errorText).visibility = View.VISIBLE
            }
        }
    }
}
