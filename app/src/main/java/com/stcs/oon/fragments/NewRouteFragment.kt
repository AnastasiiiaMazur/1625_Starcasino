package com.stcs.oon.fragments

import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.stcs.oon.R
import com.stcs.oon.db.LatLngDto
import kotlinx.coroutines.Job
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import com.stcs.oon.fragments.helpers.*
import com.stcs.oon.fragments.helpers.LocationKit
import com.stcs.oon.fragments.helpers.LocationPermissionRequester
import kotlinx.coroutines.*
import com.stcs.oon.db.RouteSessionViewModel
import com.stcs.oon.db.RouteSpec
import com.stcs.oon.fragments.extra.RouteCache
import kotlin.math.cos
import kotlin.math.hypot
import com.stcs.oon.fragments.helpers.OrsOptions


class NewRouteFragment : Fragment(R.layout.fragment_new_route) {

    // Views
    private lateinit var kmSeekBar: SeekBar
    private lateinit var startLocation: LinearLayout
    private lateinit var startManualLocation: LinearLayout
    private lateinit var twistyRoute: LinearLayout
    private lateinit var scenicRoute: LinearLayout
    private lateinit var flatRoute: LinearLayout
    private lateinit var clockwiseRoute: LinearLayout
    private lateinit var counterclockwiseRoute: LinearLayout
    private lateinit var randomRoute: LinearLayout
    private lateinit var startRouteBtn: TextView

    // Map + location
    private lateinit var mapView: MapView
    private lateinit var locationRequester: LocationPermissionRequester
    private var myLocationOverlay: MyLocationNewOverlay? = null
    private var startCenter: GeoPoint? = null

    // Drawn route
    private var routePolyline: Polyline? = null

    // ----- UI state -----
    private enum class StartOption { MY_LOCATION, MANUAL }
    private enum class RouteType { TWISTY, SCENIC, FLAT }
    private enum class Direction { CLOCKWISE, COUNTERCLOCKWISE, RANDOM }

    private var selectedStart = StartOption.MY_LOCATION
    private var selectedType = RouteType.TWISTY
    private var selectedDir = Direction.CLOCKWISE
    private var distanceKm = 0

    // SeekBar popup
    private lateinit var kmPopup: PopupWindow
    private lateinit var kmPopupText: TextView

    // Routing
    private val ors by lazy { OrsClient.create(logging = false) }
    private var routeJob: Job? = null
    private var randomSeed = 1

    private val routeSession: RouteSessionViewModel by activityViewModels()

    companion object {
        private const val ARG_ROUTE_SPEC = "arg_route_spec"
    }


    private var previewPoints: List<GeoPoint> = emptyList()
    private var lastProfile: String = "cycling-regular"

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        kmSeekBar = view.findViewById(R.id.kmSeekBar)
        startLocation = view.findViewById(R.id.startLocation)
        startManualLocation = view.findViewById(R.id.startManualLocation)
        mapView = view.findViewById(R.id.map)
        twistyRoute = view.findViewById(R.id.twistyRoute)
        scenicRoute = view.findViewById(R.id.scenicRoute)
        flatRoute = view.findViewById(R.id.flatRoute)
        clockwiseRoute = view.findViewById(R.id.clockwiseRoute)
        counterclockwiseRoute = view.findViewById(R.id.counterclockwiseRoute)
        randomRoute = view.findViewById(R.id.randomRoute)
        startRouteBtn = view.findViewById(R.id.startRouteBtn)

        val base = requireContext().cacheDir.resolve("osmdroid")
        val tiles = base.resolve("tiles")
        Configuration.getInstance().apply {
            userAgentValue = requireContext().packageName
            osmdroidBasePath = base
            osmdroidTileCache = tiles
        }

        mapView.setMultiTouchControls(true)
        mapView.setTileSource(TileSourceFactory.MAPNIK)

        setupUiGroups()
        setupKmSeekbar()

//        startRouteBtn.setOnClickListener {
//            val center = startCenter ?: return@setOnClickListener
//            val profile = when (selectedType) {
//                RouteType.SCENIC -> "cycling-regular"
//                RouteType.FLAT -> "cycling-road"
//                RouteType.TWISTY -> "cycling-mountain"
//            }
//            val seed = if (selectedDir == Direction.RANDOM) randomSeed else 1
//            val spec = RouteSpec(
//                start = LatLngDto(center.latitude, center.longitude),
//                lengthMeters = distanceKm * 1000,
//                profile = profile,
//                seed = seed,
//                dir = selectedDir.name
//            )
//            if (previewPoints.isNotEmpty()) {
//                RouteCache.put(spec, previewPoints.map { LatLngDto(it.latitude, it.longitude) })
//            }
//            routeSession.spec = spec
//            findNavController().navigate(R.id.navigatorFragment)
//        }

        startRouteBtn.setOnClickListener {
            Log.d("NAV", "Start tapped")
            val center = startCenter ?: return@setOnClickListener

            val profile = when (selectedType) {
                RouteType.SCENIC -> "cycling-regular"
                RouteType.FLAT   -> "cycling-road"
                RouteType.TWISTY -> "cycling-mountain"
            }
            val seed = if (selectedDir == Direction.RANDOM) randomSeed else 1

            val spec = RouteSpec(
                start = LatLngDto(center.latitude, center.longitude),
                lengthMeters = distanceKm * 1000,
                profile = profile,
                seed = seed,
                dir = selectedDir.name
            )

            // Cancel any ongoing routing work and map following before navigating
            routeJob?.cancel()
            myLocationOverlay?.disableFollowLocation()

            val args = android.os.Bundle().apply { putParcelable(ARG_ROUTE_SPEC, spec) }
            findNavController().navigate(R.id.navigatorFragment, args)
        }




        locationRequester = LocationPermissionRequester(
            fragment = this,
            onGranted = {
                LocationKit.toGeoPoint(LocationKit.getBestLastKnownLocation(requireContext()))?.let {
                    startCenter = it
                    mapView.controller.setZoom(15.0)
                    mapView.controller.setCenter(it)
                    requestRoute()
                }
                myLocationOverlay = LocationKit.attachMyLocationOverlay(
                    mapView = mapView,
                    context = requireContext(),
                    follow = true
                ) { firstFix ->
                    requireActivity().runOnUiThread {
                        startCenter = firstFix
                        mapView.controller.animateTo(firstFix)
                        requestRoute()
                    }
                }
                mapView.overlays.add(myLocationOverlay)
            },
            onDenied = { permanentlyDenied ->
                if (permanentlyDenied) LocationKit.openAppSettings(this)
                else Toast.makeText(requireContext(), "Location permission is needed for My Location.", Toast.LENGTH_LONG).show()
            }
        )
        locationRequester.request()
    }

    override fun onResume() { super.onResume(); mapView.onResume() }
    override fun onPause()  { mapView.onPause(); super.onPause() }
    override fun onDestroyView() {
        routeJob?.cancel()
        myLocationOverlay?.disableMyLocation()
        myLocationOverlay = null
        super.onDestroyView()
    }

    // ----- UI groups -----
    private fun setupUiGroups() {
        setupSingleSelect(listOf(startLocation, startManualLocation), 0) { id ->
            selectedStart =
                if (id == R.id.startLocation) StartOption.MY_LOCATION else StartOption.MANUAL
            requestRoute()
        }
        setupSingleSelect(listOf(twistyRoute, scenicRoute, flatRoute), 0) { id ->
            selectedType = when (id) {
                R.id.twistyRoute -> RouteType.TWISTY
                R.id.scenicRoute -> RouteType.SCENIC
                else -> RouteType.FLAT
            }
            requestRoute()
        }
        setupSingleSelect(listOf(clockwiseRoute, counterclockwiseRoute, randomRoute), 0) { id ->
            selectedDir = when (id) {
                R.id.clockwiseRoute -> Direction.CLOCKWISE
                R.id.counterclockwiseRoute -> Direction.COUNTERCLOCKWISE
                else -> Direction.RANDOM
            }
            if (selectedDir == Direction.RANDOM) randomSeed = (1..100000).random()
            requestRoute()
        }
    }

    private fun setupSingleSelect(
        items: List<LinearLayout>,
        defaultIndex: Int,
        onSelected: (selectedLayoutId: Int) -> Unit
    ) {
        var selectedIndex = defaultIndex.coerceIn(0, items.lastIndex)
        items.forEachIndexed { i, row ->
            setRowSelected(row, i == selectedIndex)
            row.setOnClickListener {
                if (i == selectedIndex) return@setOnClickListener
                setRowSelected(items[selectedIndex], false)
                setRowSelected(row, true)
                selectedIndex = i
                onSelected(row.id)
            }
        }
        onSelected(items[selectedIndex].id)
    }

    private fun setRowSelected(container: LinearLayout, selected: Boolean) {
        (container.getChildAt(0) as? ImageView)?.isEnabled = selected
    }

    // ----- SeekBar + popup -----
    private fun setupKmSeekbar() {
        kmSeekBar.max = 100
        kmSeekBar.progress = 0
        distanceKm = 0

        kmPopupText = TextView(requireContext()).apply {
            textSize = 16f
            setPadding(dp(8), dp(4), dp(8), dp(4))
            setTextColor(0xFF000000.toInt())
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(0xFFFFFFFF.toInt())
                cornerRadius = dp(12).toFloat()
                setStroke(dp(1), 0xFFCCCCCC.toInt())
            }
            elevation = dp(4).toFloat()
        }
        kmPopup = PopupWindow(kmPopupText, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, false)

        kmSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onStartTrackingTouch(sb: SeekBar) { showAndPositionKm(sb.progress) }
            override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {
                if (fromUser) showAndPositionKm(p)
                distanceKm = p
                requestRoute(debounce = true) // live update with debounce
            }
            override fun onStopTrackingTouch(sb: SeekBar) { kmPopup.dismiss() }
        })
    }

    private fun showAndPositionKm(progress: Int) {
        kmPopupText.text = "$progress km"
        kmPopupText.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val popW = kmPopupText.measuredWidth

        val sb = kmSeekBar
        val fraction = progress.toFloat() / sb.max
        val available = sb.width - sb.paddingLeft - sb.paddingRight
        val thumbCenterX = sb.paddingLeft + (available * fraction).toInt()

        var xOff = (thumbCenterX - popW / 2).coerceIn(0, sb.width - popW)
        val yOff = dp(8)

        if (kmPopup.isShowing) kmPopup.update(sb, xOff, yOff, -1, -1)
        else kmPopup.showAsDropDown(sb, xOff, yOff, Gravity.START)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun requestRoute(debounce: Boolean = false) {
        val center = startCenter ?: return
        if (distanceKm <= 0) { drawPolyline(emptyList()); return }

        routeJob?.cancel()
        routeJob = viewLifecycleOwner.lifecycleScope.launch {
            if (debounce) delay(250)

            val profile = when (selectedType) {
                RouteType.SCENIC -> "cycling-regular"
                RouteType.FLAT   -> "cycling-road"
                RouteType.TWISTY -> "cycling-mountain"
            }
            val seed = if (selectedDir == Direction.RANDOM) randomSeed else 1

            try {
                val body = OrsDirectionsBody(
                    coordinates = listOf(listOf(center.longitude, center.latitude)), // start only (round-trip)
                    options = OrsOptions(roundTrip = OrsRoundTrip(length = distanceKm * 1000, seed = seed))
                )
                val apiKey = getString(R.string.ors_api_key)
                val resp = withContext(Dispatchers.IO) { ors.routeGeoJson(apiKey, profile, body) }

                var points = resp.features.firstOrNull()?.geometry?.coordinates
                    ?.map { ll -> GeoPoint(ll[1], ll[0]) }
                    .orEmpty()

                points = when (selectedDir) {
                    Direction.CLOCKWISE        -> points
                    Direction.COUNTERCLOCKWISE -> points.asReversed()
                    Direction.RANDOM           -> points
                }

                val simplified = withContext(Dispatchers.Default) { simplifyForMap(points).let { capPoints(it, 400) } }

                previewPoints = simplified     // keep this for NavigatorFragment
                lastProfile = profile
                startRouteBtn.isEnabled = simplified.isNotEmpty()

                // also put preview in cache for Navigator (instant draw)
                val spec = RouteSpec(
                    start = LatLngDto(center.latitude, center.longitude),
                    lengthMeters = distanceKm * 1000,
                    profile = profile,
                    seed = seed,
                    dir = selectedDir.name
                )
                RouteCache.put(spec, simplified.map { LatLngDto(it.latitude, it.longitude) })

                drawPolyline(simplified)

                Log.d("Route", "raw=${points.size}, simplified=${simplified.size}, lenKm=$distanceKm")

                drawPolyline(points)
            } catch (e: CancellationException) {
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Routing failed: ${e.message}", Toast.LENGTH_SHORT).show()
                drawPolyline(emptyList())
            }
        }
    }

    private fun drawPolyline(points: List<GeoPoint>) {
        routePolyline?.let { mapView.overlays.remove(it) }
        routePolyline = null

        if (points.isEmpty()) { mapView.invalidate(); return }

        routePolyline = Polyline().apply {
            setPoints(points)
            outlinePaint.strokeWidth = 6f
            outlinePaint.color = 0xFFE53935.toInt() // red-ish
        }
        mapView.overlays.add(routePolyline)

        val bbox: BoundingBox = BoundingBox.fromGeoPointsSafe(points)
        mapView.zoomToBoundingBox(bbox, true, 80)
        mapView.invalidate()
    }

    // --- Polyline simplification & capping ---

    private fun simplifyForMap(
        points: List<GeoPoint>,
        toleranceMeters: Double = 10.0,
        maxPoints: Int = 400
    ): List<GeoPoint> {
        if (points.size <= 2) return points
        val dp = douglasPeucker(points, toleranceMeters)
        return capPoints(dp, maxPoints)
    }

    private fun capPoints(points: List<GeoPoint>, maxPoints: Int): List<GeoPoint> {
        if (points.size <= maxPoints) return points
        val step = kotlin.math.ceil(points.size / maxPoints.toDouble()).toInt()
        val out = ArrayList<GeoPoint>((points.size / step) + 1)
        for (i in points.indices step step) out.add(points[i])
        if (out.last() != points.last()) out.add(points.last())
        return out
    }

    // Douglas–Peucker in meters (approx), using simple meters-per-degree projection
    private fun douglasPeucker(points: List<GeoPoint>, toleranceMeters: Double): List<GeoPoint> {
        val n = points.size
        if (n < 3) return points
        val keep = BooleanArray(n)
        keep[0] = true
        keep[n - 1] = true

        val refLat = points.first().latitude
        fun toXY(p: GeoPoint): Pair<Double, Double> {
            val mPerDegLat = 111_320.0
            val mPerDegLon = 111_320.0 * kotlin.math.cos(Math.toRadians(refLat))
            return Pair(p.longitude * mPerDegLon, p.latitude * mPerDegLat)
        }
        val xy = points.map { toXY(it) }

        fun perpDist(idx: Int, a: Int, b: Int): Double {
            val (x, y) = xy[idx]
            val (x1, y1) = xy[a]
            val (x2, y2) = xy[b]
            if (x1 == x2 && y1 == y2) {
                val dx = x - x1; val dy = y - y1
                return kotlin.math.hypot(dx, dy)
            }
            val dx = x2 - x1; val dy = y2 - y1
            val t = ((x - x1) * dx + (y - y1) * dy) / (dx*dx + dy*dy)
            val clamped = t.coerceIn(0.0, 1.0)
            val projX = x1 + clamped * dx
            val projY = y1 + clamped * dy
            return kotlin.math.hypot(x - projX, y - projY)
        }

        fun recurse(a: Int, b: Int) {
            var maxDist = 0.0
            var idx = -1
            for (i in a + 1 until b) {
                val d = perpDist(i, a, b)
                if (d > maxDist) { maxDist = d; idx = i }
            }
            if (maxDist > toleranceMeters && idx != -1) {
                keep[idx] = true
                recurse(a, idx)
                recurse(idx, b)
            }
        }
        recurse(0, n - 1)

        val out = ArrayList<GeoPoint>()
        for (i in 0 until n) if (keep[i]) out.add(points[i])
        return out
    }

}


