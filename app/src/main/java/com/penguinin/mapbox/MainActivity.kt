package com.penguinin.mapbox

import android.Manifest
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.Color
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.provider.ContactsContract.Data
import android.view.View
import android.widget.Toast
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.mapbox.android.core.location.LocationEngineCallback
import com.mapbox.android.core.location.LocationEngineProvider
import com.mapbox.android.core.location.LocationEngineResult
import com.mapbox.android.gestures.MoveGestureDetector
import com.mapbox.api.directions.v5.DirectionsCriteria
import com.mapbox.api.directions.v5.models.Bearing
import com.mapbox.api.directions.v5.models.RouteOptions
import com.mapbox.bindgen.Expected
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.EdgeInsets
import com.mapbox.maps.MapView
import com.mapbox.maps.Style
import com.mapbox.maps.extension.style.layers.generated.lineLayer
import com.mapbox.maps.extension.style.layers.properties.generated.LineCap
import com.mapbox.maps.extension.style.layers.properties.generated.LineJoin
import com.mapbox.maps.extension.style.layers.properties.generated.TextAnchor
import com.mapbox.maps.extension.style.sources.generated.geoJsonSource
import com.mapbox.maps.extension.style.style
import com.mapbox.maps.plugin.animation.MapAnimationOptions
import com.mapbox.maps.plugin.animation.camera
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.createPointAnnotationManager
import com.mapbox.maps.plugin.gestures.OnMoveListener
import com.mapbox.maps.plugin.gestures.addOnMapClickListener
import com.mapbox.maps.plugin.gestures.gestures
import com.mapbox.maps.plugin.locationcomponent.LocationComponentConstants.LOCATION_INDICATOR_LAYER
import com.mapbox.maps.plugin.locationcomponent.location
import com.mapbox.navigation.base.extensions.applyDefaultNavigationOptions
import com.mapbox.navigation.base.options.NavigationOptions
import com.mapbox.navigation.base.route.NavigationRoute
import com.mapbox.navigation.base.route.NavigationRouterCallback
import com.mapbox.navigation.base.route.RouterFailure
import com.mapbox.navigation.base.route.RouterOrigin
import com.mapbox.navigation.core.MapboxNavigation
import com.mapbox.navigation.core.directions.session.RoutesObserver
import com.mapbox.navigation.core.lifecycle.MapboxNavigationApp.setup
import com.mapbox.navigation.core.trip.session.LocationMatcherResult
import com.mapbox.navigation.core.trip.session.LocationObserver
import com.mapbox.navigation.ui.base.util.MapboxNavigationConsumer
import com.mapbox.navigation.ui.maps.location.NavigationLocationProvider
import com.mapbox.navigation.ui.maps.route.line.api.MapboxRouteLineApi
import com.mapbox.navigation.ui.maps.route.line.api.MapboxRouteLineView
import com.mapbox.navigation.ui.maps.route.line.model.MapboxRouteLineOptions
import com.mapbox.navigation.ui.maps.route.line.model.RouteLineError
import com.mapbox.navigation.ui.maps.route.line.model.RouteLineResources
import com.mapbox.navigation.ui.maps.route.line.model.RouteSetValue
import com.penguinin.mapbox.databinding.ActivityMainBinding
import java.util.Arrays
import java.util.Objects


class MainActivity : AppCompatActivity() {

 lateinit var binding:ActivityMainBinding

     private val navigationLocationProvider = NavigationLocationProvider()
    private var routeLineView: MapboxRouteLineView? = null
    private var routeLineApi: MapboxRouteLineApi? = null
    lateinit var locationObserver: LocationObserver
    lateinit var routesObserver: RoutesObserver
    var focusLocation = true
    private var mapboxNavigation: MapboxNavigation? = null

    lateinit var onMoveListener: OnMoveListener
    private val activityResultLauncher = registerForActivityResult<String, Boolean>(
        ActivityResultContracts.RequestPermission(),
        object : ActivityResultCallback<Boolean> {
            override fun onActivityResult(result: Boolean){
                initMapBox()
            }
        })

    init {

        initLocationObserver()
        initMoverListener()
        initRouteObserver()
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding=DataBindingUtil.   setContentView(this,R.layout.activity_main)
         initMapBox()


    }

    fun initMapBox() {

        val options: MapboxRouteLineOptions =
            MapboxRouteLineOptions.Builder(this)
                .withRouteLineResources(RouteLineResources.Builder().build())
                .withRouteLineBelowLayerId(LOCATION_INDICATOR_LAYER).build()
        routeLineView = MapboxRouteLineView(options)
        routeLineApi = MapboxRouteLineApi(options)


        val navigationOptions: NavigationOptions =
            NavigationOptions.Builder(this).accessToken(getString(R.string.mapbox_access_token))
                .build()

        setup(navigationOptions)
        mapboxNavigation = MapboxNavigation(navigationOptions)

        mapboxNavigation?.registerRoutesObserver(routesObserver)
        mapboxNavigation?.registerLocationObserver(locationObserver)



        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(
                    this@MainActivity,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                activityResultLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (ActivityCompat.checkSelfPermission(
                this@MainActivity,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
            && ActivityCompat.checkSelfPermission(
                this@MainActivity,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            activityResultLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            activityResultLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        } else {
            mapboxNavigation?.startTripSession()
        }

            binding.focusLocation?.hide()
        val locationComponentPlugin = binding.mapView?.location
        binding.  mapView?.gestures?.addOnMoveListener(onMoveListener)

        binding. setRoute?.setOnClickListener(View.OnClickListener {
            Toast.makeText(
                this@MainActivity,
                "Please select a location in map",
                Toast.LENGTH_SHORT
            ).show()
        })
        binding.  mapView?.getMapboxMap()?.loadStyle(
            (
                    style(styleUri = Style.MAPBOX_STREETS) {
                        +geoJsonSource("line") {
                            url("asset://map.geojson")
                        }
                        +lineLayer("linelayer", "line") {
                            lineCap(LineCap.ROUND)
                            lineJoin(LineJoin.ROUND)
                            lineOpacity(0.9)
                            lineWidth(1.0)
                            lineColor(Color.GRAY)

                        }
                        binding.      mapView?.location?.updateSettings {
                            enabled = true
                            pulsingEnabled = true
                        }
                        binding.     mapView?.getMapboxMap()
                            ?.setCamera(CameraOptions.Builder().zoom(20.0).build())

                        locationComponentPlugin?.enabled = true
                        locationComponentPlugin?.setLocationProvider(navigationLocationProvider)
                        binding.    mapView?.gestures?.addOnMoveListener(onMoveListener)
                        locationComponentPlugin?.updateSettings {
                            this.enabled = true
                            this.pulsingEnabled = true

                        }
                        val bitmap =
                            BitmapFactory.decodeResource(resources, R.drawable.location_pin)
                        val annotationPlugin = binding.mapView?.annotations
                        val pointAnnotationManager =
                            annotationPlugin?.createPointAnnotationManager(binding.mapView!!)
                        binding.   mapView?.getMapboxMap()?.addOnMapClickListener { point ->
                            pointAnnotationManager?.deleteAll()
                            val pointAnnotationOptions =
                                PointAnnotationOptions().withTextAnchor(TextAnchor.CENTER)
                                    .withIconImage(bitmap)
                                    .withPoint(point)
                            pointAnnotationManager?.create(pointAnnotationOptions)
                            binding.  setRoute?.setOnClickListener(View.OnClickListener { fetchRoute(point) })
                            true
                        }
                             binding.focusLocation?.setOnClickListener(View.OnClickListener {
                            focusLocation = true
                            binding. mapView?.gestures?.addOnMoveListener(onMoveListener)
                                 binding.focusLocation?.hide()

                        })


                    }
                    )
        )

    }

    @SuppressLint("MissingPermission")
    private fun fetchRoute(point: Point) {
        val locationEngine = LocationEngineProvider.getBestLocationEngine(this@MainActivity)
        locationEngine.getLastLocation(object : LocationEngineCallback<LocationEngineResult> {
            override fun onSuccess(result: LocationEngineResult) {
                val location = result.lastLocation
                binding.setRoute!!.isEnabled = false
                binding.     setRoute!!.text = "Fetching route..."
                val builder = RouteOptions.builder()
                val origin = Point.fromLngLat(
                    Objects.requireNonNull(location)!!.longitude,
                    location!!.latitude
                )
                builder.coordinatesList(Arrays.asList(origin, point))
                builder.alternatives(false)
                builder.profile(DirectionsCriteria.PROFILE_DRIVING)
                builder.bearingsList(
                    Arrays.asList(
                        Bearing.builder().angle(location.bearing.toDouble()).degrees(45.0).build(),
                        null
                    )
                )
                builder.applyDefaultNavigationOptions()
                mapboxNavigation!!.requestRoutes(
                    builder.build(),
                    object : NavigationRouterCallback {
                        override fun onRoutesReady(
                            list: List<NavigationRoute>,
                            routerOrigin: RouterOrigin
                        ) {
                            mapboxNavigation?.setNavigationRoutes(list)
                                 binding.focusLocation!!.performClick()
                            binding.   setRoute!!.isEnabled = true
                            binding.    setRoute!!.text = "Route"
                        }

                        override fun onFailure(
                            list: List<RouterFailure>,
                            routeOptions: RouteOptions
                        ) {
                            binding.    setRoute!!.isEnabled = true
                            binding.   setRoute!!.text = "Route"
                            Toast.makeText(
                                this@MainActivity,
                                "Route request failed",
                                Toast.LENGTH_SHORT
                            ).show()
                        }

                        override fun onCanceled(
                            routeOptions: RouteOptions,
                            routerOrigin: RouterOrigin
                        ) {
                        }
                    })
            }

            override fun onFailure(exception: Exception) {}
        })
    }

    fun initMoverListener() {
        onMoveListener = object : OnMoveListener {
            override fun onMoveBegin(moveGestureDetector: MoveGestureDetector) {
                focusLocation = false
                binding.mapView!!.gestures.removeOnMoveListener(this)
                      binding.focusLocation!!.show()
            }

            override fun onMove(moveGestureDetector: MoveGestureDetector): Boolean {
                return false
            }

            override fun onMoveEnd(moveGestureDetector: MoveGestureDetector) {}
        }
    }

    fun initLocationObserver() {
        locationObserver = object : LocationObserver {
            override fun onNewRawLocation(location: Location) {}
            override fun onNewLocationMatcherResult(locationMatcherResult: LocationMatcherResult) {
                val location = locationMatcherResult.enhancedLocation

                navigationLocationProvider.changePosition(
                    location,
                    locationMatcherResult.keyPoints,
                    latLngTransitionOptions = null,
                    bearingTransitionOptions = null
                )
                if (focusLocation) {
                    updateCamera(
                        Point.fromLngLat(location.longitude, location.latitude),
                        location.bearing.toDouble()
                    )
                }


            }
        }
    }

    fun initRouteObserver() {
        routesObserver =
            RoutesObserver { routesUpdatedResult ->
                routeLineApi!!.setNavigationRoutes(
                    routesUpdatedResult.navigationRoutes,
                    object : MapboxNavigationConsumer<Expected<RouteLineError, RouteSetValue>> {
                        override fun accept(value: Expected<RouteLineError, RouteSetValue>) {
                            val style = binding.mapView!!.getMapboxMap().getStyle()
                            if (style != null) {
                                routeLineView!!.renderRouteDrawData(
                                    style,
                                    value
                                )
                            }
                        }

                    })
            }
    }

    private fun updateCamera(point: Point, bearing: Double) {
        val animationOptions: MapAnimationOptions =
            MapAnimationOptions.Builder().duration(1500L).build()
        val cameraOptions =
            CameraOptions.Builder().center(point).zoom(18.0).bearing(bearing).pitch(45.0)
                .padding(EdgeInsets(1000.0, 0.0, 0.0, 0.0)).build()
        binding.  mapView!!.camera.easeTo(cameraOptions, animationOptions)
    }

    override fun onDestroy() {
        super.onDestroy()
        mapboxNavigation?.onDestroy()
        mapboxNavigation?.unregisterRoutesObserver(routesObserver)
        mapboxNavigation?.unregisterLocationObserver(locationObserver)
    }


    override fun onStart() {
        super.onStart()
        binding.   mapView?.onStart()
    }

    override fun onStop() {
        super.onStop()
        binding.  mapView?.onStop()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        binding. mapView?.onLowMemory()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Permission Granted", Toast.LENGTH_SHORT).show()


            } else {
                Toast.makeText(this, "Permission Denied", Toast.LENGTH_SHORT).show()
            }
        }
    }


}