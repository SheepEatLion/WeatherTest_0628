package com.example.weathertest

import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Transformations.map
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.tasks.OnCompleteListener
import com.google.android.gms.tasks.Task
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FindCurrentPlaceRequest
import com.google.android.libraries.places.api.net.FindCurrentPlaceResponse
import com.google.android.libraries.places.api.net.PlacesClient
import java.util.*


class MapsActivity : AppCompatActivity(), OnMapReadyCallback {
    val TAG = "MapTest"
    private lateinit var mMap: GoogleMap
    //Place 진입점.
    private lateinit var mPlacesClient: PlacesClient

    //Fused Location Provider의 진입점
    private lateinit var mFusedLocationProviderClient: FusedLocationProviderClient

    // 위치 허가를 받지 못했을때 사용하는 변수들. 호주 시드니로 되어 있다.
    private val mDefaultLocation = LatLng(35.17944, 129.07556)
    private val DEFAULT_ZOOM = 15
    private val PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION = 1
    private var mLocationPermissionGranted = false

    //장치가 현재 위치한 지리적 위치입니다. 마지막으로 알려진
    //Fused Location Provider에서 검색한 위치입니다.
    private var mLastKnownLocation: Location? = null
    private var mCameraPosition: CameraPosition? = null

    // 액티비티 상태를 저장하기 위한 '키'들입니다.
    private val KEY_CAMERA_POSITION = "camera_position"
    private val KEY_LOCATION = "location"

    //현재 장소를 선택하는데 사용되는 변수들
    private val M_MAX_ENTRIES = 15
    private lateinit var mLikelyPlaceNames: Array<String?>
    private lateinit var mLikelyPlaceAddresses: Array<String?>
    private lateinit var mLikelyPlaceAttributions: Array<List<Any?>?>
    private lateinit var mLikelyPlaceLatLngs: Array<LatLng?>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 저장된 인스턴스 상태에서 위치 및 카메라 위치를 검색해서 받기
        if (savedInstanceState != null) {
            mLastKnownLocation = savedInstanceState.getParcelable(KEY_LOCATION)
            mCameraPosition = savedInstanceState.getParcelable(KEY_CAMERA_POSITION)
        }

        // 지도를 그리는 컨텐츠 뷰 검색해서 받기
        setContentView(R.layout.activity_maps)

        // PlaceClient 구성
        Places.initialize(getApplication(), getString(R.string.google_maps_key))
        mPlacesClient = Places.createClient(this)

        // FusedLocationProviderClient 구성
        mFusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)

        // SupportMapFragment를 확보하고 맵을 사용할 준비가되면 알림을받습니다.
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

    }

    /**
     * Activity가 멈추게 되었을 때 상태를 저장
     */
    @Suppress("SENSELESS_COMPARISON")
    override fun onSaveInstanceState(outState: Bundle) {
        if (mMap != null) {
            outState.putParcelable(KEY_CAMERA_POSITION, mMap.getCameraPosition())
            outState.putParcelable(KEY_LOCATION, mLastKnownLocation)
            super.onSaveInstanceState(outState)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.current_place_menu, menu)
        return true
    }
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.option_get_place) {
            showCurrentPlace()
        } else{
            var uri = Uri.parse("geo:38.98765432,127.98765432")
            intent =  Intent(Intent.ACTION_VIEW, uri)
            startActivity(intent)
        }
        return super.onOptionsItemSelected(item)
    }

    /**
     */
    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        // 정보창 내용에서 여러 줄의 텍스트를 처리하기 위해 사용자 정의 정보창 어댑터를 사용
        mMap.setInfoWindowAdapter(object : GoogleMap.InfoWindowAdapter {
            // 여기서 getInfoContents()가 호출되도록 null을 반환합니다
            override fun getInfoWindow(arg0: Marker): View? {
                return null
            }

            override fun getInfoContents(p0: Marker): View {
                // 정보창, 제목 및 스니펫의 레이아웃을 inflate
                val infoWindow = layoutInflater.inflate(
                    R.layout.custom_info_contents,
                    findViewById(R.id.map) as FrameLayout, false
                )

                val title = infoWindow.findViewById<TextView>(R.id.title)
                title.text = p0.title

                val snippet = infoWindow.findViewById<TextView>(R.id.snippet)
                snippet.text = p0.snippet

                return infoWindow
            }
        })

        // 유저권한 요청
        getLocationPermission()

        // 내 위치 레이어 및 관련 컨트롤을 지도에서 구현
        updateLocationUI();

        // 장치의 현재 위치로 지도 위치 설정
        getDeviceLocation();
    }
    //위치 권한 요청 결과를 처리
    private fun getDeviceLocation() {
        //가장 최근의 기계 위치 얻기, 드물게 null일 수 있다.
        //위치가 사용 불가능한 경우
        try {
            if (mLocationPermissionGranted) {
                val locationResult = mFusedLocationProviderClient.lastLocation
                locationResult.addOnCompleteListener(this) { task ->
                    //작업이 성공적으로 끝났을 경우
                    if (task.isSuccessful) {
                        //맵의 카메라 위치를 기계의 현재 위치로 세팅
                        mLastKnownLocation = task.getResult()
                        if (mLastKnownLocation != null) {
                            mMap.moveCamera(
                                CameraUpdateFactory.newLatLngZoom(
                                    LatLng(
                                        mLastKnownLocation!!.latitude,
                                        mLastKnownLocation!!.longitude
                                    ), DEFAULT_ZOOM.toFloat()
                                )
                            )
                        } else {
                            //현재 위치 값을 못받으면 Log 띄움, 디폴트 위치로 이동하고 로케이션 버튼 불가
                            Log.d(TAG, "Current location is null. Using defaults.")
                            Log.e(TAG, "Exception: %s", task.exception)
                            mMap.moveCamera(
                                CameraUpdateFactory.newLatLngZoom(
                                    mDefaultLocation, DEFAULT_ZOOM.toFloat()
                                )
                            )
                            mMap.getUiSettings().isMyLocationButtonEnabled = false
                        }
                    }
                }
            }
        }
        //보안 예외 발생
        catch (e: SecurityException) {
            Log.e("Exception: %s", e.message!!)
        }
    }

    //장치 위치 사용 권한을 사용자에게 요청
    private fun getLocationPermission() {
        //위치 권한을 요청한다.
        //권한 요청의 결과는 콜백으로 조정된다.
        //권한을 얻은 경우
        if (ContextCompat.checkSelfPermission(
                this.applicationContext,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            mLocationPermissionGranted = true
        }
        //권한 얻기에 실패한 경우
        else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION),
                PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION
            )
        }
    }
    //위치 권한 요청 결과를 처리
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        mLocationPermissionGranted = false
        when (requestCode) {
            PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION -> {
                //만약 요청이 취소되면, 결과 배열에는 값이 없다
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    mLocationPermissionGranted = true
                }
            }
        }
        updateLocationUI()
    }

    //
    //사용자에게 가능한 장소 목록에서 현재 장소를 선택하라는 메시지를 표시하고
    // 사용자가 위치 권한을 부여한 경우 지도에 현재 장소를 표시합니다.
    @Suppress("SENSELESS_COMPARISON")
    private fun showCurrentPlace() {
        if (mMap == null) return

        //위치 권한을 받는데 성공한 경우
        if (mLocationPermissionGranted) {
            //반환할 데이터 형식을 정의하려면 필드를 사용하십시오.
            val placeFields: List<Place.Field> = Arrays.asList(
                Place.Field.NAME, Place.Field.ADDRESS,
                Place.Field.LAT_LNG
            )

            //빌더를 사용하여 FindCurrentPlaceRequest를 작성
            val request = FindCurrentPlaceRequest.newInstance(placeFields)

            // 장치의 현재 위치에 가장 적합한 비즈니스 및 기타 관심 지점을 검색
            @SuppressWarnings("MissingPermission")
            val placeResult = mPlacesClient.findCurrentPlace(request)
            placeResult.addOnCompleteListener(object :
                OnCompleteListener<FindCurrentPlaceResponse> {
                override fun onComplete(task: Task<FindCurrentPlaceResponse>) {
                    if (task.isSuccessful && task.result != null) {
                        val likelyPlaces = task.getResult()

                        // 5 개 미만의 항목이 반환되는 경우와 아닌 경우 처리
                        var count: Int
                        if (likelyPlaces!!.placeLikelihoods.size < M_MAX_ENTRIES) {
                            count = likelyPlaces.placeLikelihoods.size
                        } else {
                            count = M_MAX_ENTRIES
                        }

                        var i = 0
                        mLikelyPlaceNames = arrayOfNulls(count)
                        mLikelyPlaceAddresses = arrayOfNulls(count)
                        mLikelyPlaceAttributions = arrayOfNulls(count)
                        mLikelyPlaceLatLngs = arrayOfNulls(count)

                        for (placeLikelihood in likelyPlaces.placeLikelihoods) {
                            //가능성이 높은 장소들의 리스트 만들기, 유저에게 보여주기 위한
                            mLikelyPlaceNames[i] = placeLikelihood.place.name
                            mLikelyPlaceAddresses[i] = placeLikelihood.place.address
                            mLikelyPlaceAttributions[i] = placeLikelihood.place.attributions
                            mLikelyPlaceLatLngs[i] = placeLikelihood.place.latLng

                            i++
                            if (i > (count - 1)) {
                                break
                            }
                        }

                        // 사용자에게 보여줄 수 있는 장소 목록 작성
                        // 선택된 장소에 마커.
                        this@MapsActivity.openPlacesDialog()
                    } else {
                        Log.e(TAG, "Exception: %s", task.exception)
                    }
                }
            })
        } else {
            //권한을 주지 않은 유저
            Log.i(TAG, "위치 권한을 얻지 못한 유저.")

            //유저가 선택할 수 있는 장소가 없기에 기본 마커 추가
            mMap.addMarker(
                MarkerOptions()
                    .title(getString(R.string.default_info_title))
                    .position(mDefaultLocation)
                    .snippet(getString(R.string.default_info_snippet))
            )

            //유저 권한 요청
            getLocationPermission()
        }
    }
    // 사용자가 가능한 장소 목록에서 장소를 선택할 수 있는 양식을 표시
    private fun openPlacesDialog() {
        //요청하기, 유저에게 현재 위치를 선택하도록
        val listener = DialogInterface.OnClickListener { dialog, which ->
            //"which"인수는 선택된 항목의 위치를 포함합니다.
            var markerLatLng = mLikelyPlaceLatLngs[which]!!
            var markerSnippet = mLikelyPlaceAddresses[which]!!
            if (mLikelyPlaceAttributions[which] != null) {
                markerSnippet = """
                    $markerSnippet
                    ${mLikelyPlaceAttributions[which]}
                """.trimMargin()
            }

            //선택된 장소에 정보창과 함께 마커 추가하기
            //해당 장소에 대한 정보 표시
            mMap.addMarker(
                MarkerOptions()
                    .title(mLikelyPlaceNames[which])
                    .position(markerLatLng)
                    .snippet(markerSnippet)
            )

            //맵의 카메라 마커 장소로 위치시키기
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(markerLatLng,DEFAULT_ZOOM.toFloat()))
        }
        //대화상자 화면에 나타내기
        val dialog= AlertDialog.Builder(this)
            .setTitle(R.string.pick_place)
            .setItems(mLikelyPlaceNames,listener)
            .show()
    }

    //
    //사용자가 위치 권한을 부여했는지 여부에 따라 지도의 UI 설정을 업데이트
    @Suppress("SENSELESS_COMPARISON", "NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
    private fun updateLocationUI(){
        if(mMap==null){
            return
        }
        try {
            //권한이 있는 경우
            if(mLocationPermissionGranted){
                mMap.isMyLocationEnabled=true
                mMap.uiSettings.isMyLocationButtonEnabled=true
            }
            //권한이 없는 경우
            else{
                mMap.isMyLocationEnabled=false
                mMap.uiSettings.isMyLocationButtonEnabled=false
                mLastKnownLocation=null
                //권한 신청
                getLocationPermission()
            }
        }catch (e:SecurityException){
            Log.e("Exception: %s", e.message)
        }
    }


}
