package com.stcs.oon.fragments.extra

import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import androidx.fragment.app.Fragment
import com.stcs.oon.R
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline
import androidx.navigation.fragment.findNavController
import com.stcs.oon.db.AppDatabase
import com.stcs.oon.db.LatLngDto
import com.stcs.oon.db.RideEntity
import com.stcs.oon.db.RouteSpec
import com.stcs.oon.fragments.helpers.LocationKit
import com.stcs.oon.fragments.helpers.LocationPermissionRequester
import kotlinx.coroutines.*
import com.stcs.oon.fragments.helpers.OrsClient
import com.stcs.oon.fragments.helpers.OrsDirectionsBody
import com.stcs.oon.fragments.helpers.OrsOptions
import com.stcs.oon.fragments.helpers.OrsRoundTrip
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import kotlin.String
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.roundToInt

class NavigatorFragment : Fragment(R.layout.fragment_navigator) {

    private val ors by lazy { OrsClient.create(logging = false) }

    private lateinit var mapView: MapView
    private lateinit var distanceTv: TextView
    private lateinit var timeTv: TextView
    private lateinit var difficultyTv: TextView
    private lateinit var startNavBtn: TextView
    private lateinit var saveRouteBtn: TextView

    private var routePolyline: Polyline? = null
    private var fetchJob: Job? = null

    // location / navigation state
    private lateinit var locationRequester: LocationPermissionRequester
    private var myLocationOverlay: MyLocationNewOverlay? = null
    private var navStarted = false
    private var navStartElapsedMs: Long = 0L

    // Args
    private var spec: RouteSpec? = null         // for auto-route flow
    private var rideId: Long? = null            // for manual-route flow (already saved)

    companion object {
        const val ARG_ROUTE_SPEC = "arg_route_spec"
        const val ARG_RIDE_ID    = "arg_ride_id"
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        mapView = view.findViewById(R.id.map)
        distanceTv = view.findViewById(R.id.distance)
        timeTv = view.findViewById(R.id.time)
        difficultyTv = view.findViewById(R.id.difficulty)
        startNavBtn = view.findViewById(R.id.startNavBtn)
        saveRouteBtn = view.findViewById(R.id.saveRouteBtn)

        // osmdroid basics
        val base = requireContext().cacheDir.resolve("osmdroid")
        Configuration.getInstance().apply {
            userAgentValue = requireContext().packageName
            osmdroidBasePath = base
            osmdroidTileCache = base.resolve("tiles")
        }
        mapView.setMultiTouchControls(true)
        mapView.setTileSource(TileSourceFactory.MAPNIK)


        val argRideId = arguments?.getLong(ARG_RIDE_ID, -1L) ?: -1L
        rideId = if (argRideId > 0L) argRideId else null

        if (rideId != null) {
            loadRideAndDraw(rideId!!)
            saveRouteBtn.visibility = View.GONE
        } else {
            val argSpec: RouteSpec? = if (Build.VERSION.SDK_INT >= 33) {
                requireArguments().getParcelable(ARG_ROUTE_SPEC, RouteSpec::class.java)
            } else {
                @Suppress("DEPRECATION")
                requireArguments().getParcelable(ARG_ROUTE_SPEC)
            }
            if (argSpec == null) {
                Toast.makeText(requireContext(), "No route data.", Toast.LENGTH_SHORT).show()
                findNavController().popBackStack()
                return
            }
            spec = argSpec
            bindHeaderFromSpec(argSpec)

            fetchJob?.cancel()
            fetchJob = viewLifecycleOwner.lifecycleScope.launch {
                try {
                    val pts = fetchRouteForSpec(argSpec)
                    drawPolyline(pts.map { GeoPoint(it.lat, it.lon) })
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), "Routing failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }

            saveRouteBtn.setOnClickListener { saveAutoRouteNow() }
        }

        locationRequester = LocationPermissionRequester(
            fragment = this,
            onGranted = { startFollowing() },
            onDenied = { permanently ->
                if (permanently) LocationKit.openAppSettings(this)
                else Toast.makeText(requireContext(), "Location permission is required.", Toast.LENGTH_LONG).show()
            }
        )

        startNavBtn.setOnClickListener {
            if (!navStarted) locationRequester.request()
        }
    }

    override fun onDestroyView() {
        fetchJob?.cancel()
        myLocationOverlay?.disableMyLocation()
        myLocationOverlay = null
        routePolyline = null
        super.onDestroyView()
    }

    // ---------- Manual route: load from DB and draw ----------
    private fun loadRideAndDraw(id: Long) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val dao = AppDatabase.getInstance(requireContext()).rideDao()
            val ride = dao.getById(id)
            withContext(Dispatchers.Main) {
                if (ride == null) {
                    Toast.makeText(requireContext(), "Ride not found", Toast.LENGTH_SHORT).show()
                    findNavController().popBackStack()
                    return@withContext
                }
                bindHeaderFromRide(ride)
                val pts = decodePolyline(ride.polylineJson)
                drawPolyline(pts)
            }
        }
    }

    private fun decodePolyline(polylineJson: String?): List<GeoPoint> {
        if (polylineJson.isNullOrBlank()) return emptyList()
        val type = object : TypeToken<List<LatLngDto>>() {}.type
        val list: List<LatLngDto> = Gson().fromJson(polylineJson, type)
        return list.map { GeoPoint(it.lat, it.lon) }
    }

    private fun bindHeaderFromRide(ride: RideEntity) {
        distanceTv.text = "${(ride.distanceMeters / 1000.0).roundToInt()} km"
        timeTv.text = formatDuration(ride.durationSeconds)
        val diff = (ride.difficulty ?: difficultyForDistance(ride.distanceMeters)).coerceIn(1, 5)
        difficultyTv.text = "$diff/5"
    }

    private fun bindHeaderFromSpec(spec: RouteSpec) {
        distanceTv.text = "${(spec.lengthMeters / 1000.0).roundToInt()} km"
        timeTv.text = "~" + estimateTimeText(spec.lengthMeters)
        difficultyTv.text = "${difficultyForDistance(spec.lengthMeters)}/5"
    }

    private fun formatDuration(seconds: Long): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        return if (h > 0) "${h} h ${m} min" else "$m min"
    }


    // ---------- Start following ----------
    private fun startFollowing() {
        if (navStarted) return
        navStarted = true
        startNavBtn.isEnabled = false

        myLocationOverlay = LocationKit.attachMyLocationOverlay(
            mapView = mapView,
            context = requireContext(),
            follow = true
        ) { firstFix ->
            requireActivity().runOnUiThread {
                mapView.controller.animateTo(firstFix)
            }
        }
        mapView.overlays.add(myLocationOverlay)
        navStartElapsedMs = SystemClock.elapsedRealtime()
        Toast.makeText(requireContext(), "Navigation started", Toast.LENGTH_SHORT).show()
    }

    // ---------- SAVE for auto-route flow ----------
    private fun saveAutoRouteNow() {
        val currentLoc = myLocationOverlay?.myLocation
            ?: LocationKit.getBestLastKnownLocation(requireContext())

        val s = spec ?: return
        val startPt = GeoPoint(s.start.lat, s.start.lon)
        val currGeo = currentLoc?.let { LocationKit.toGeoPoint(LocationKit.getBestLastKnownLocation(requireContext())) } ?: startPt

        val distanceMeters = startPt.distanceToAsDouble(currGeo).roundToInt()
        val durationSeconds = if (navStarted)
            ((SystemClock.elapsedRealtime() - navStartElapsedMs) / 1000L) else 0L
        val difficulty = difficultyForDistance(distanceMeters)

        val entity = RideEntity(
            name = "",
            startLat = s.start.lat,
            startLon = s.start.lon,
            specLengthMeters = s.lengthMeters,
            specProfile = s.profile,
            specSeed = s.seed,
            specDir = s.dir,
            polylineJson = null, // auto route – we rebuild from spec if needed
            distanceMeters = distanceMeters,
            durationSeconds = durationSeconds,
            avgSpeedKmh = null,
            difficulty = difficulty,
            description = null,
            rating = null,
            im1Uri = null,
            im2Uri = null,
            im3Uri = null,
            createdAt = System.currentTimeMillis()
        )

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val dao = AppDatabase.getInstance(requireContext()).rideDao()
                val id = dao.insert(entity)
                dao.updateName(id, "Route $id")
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Ride saved as Route $id", Toast.LENGTH_LONG).show()
                    // Optionally jump to details:
                    // findNavController().navigate(R.id.savedRoutesDetailsFragment, bundleOf(SavedDetailsFragment.ARG_RIDE_ID to id))
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Save failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // ---------- ORS fetch (auto route) ----------
    private suspend fun fetchRouteForSpec(spec: RouteSpec): List<LatLngDto> = withContext(Dispatchers.IO) {
        val body = OrsDirectionsBody(
            coordinates = listOf(listOf(spec.start.lon, spec.start.lat)),
            options = OrsOptions(
                roundTrip = OrsRoundTrip(
                    length = spec.lengthMeters.coerceIn(1_000, 150_000),
                    points = 5,
                    seed = spec.seed
                )
            ),
            instructions = false,
            elevation = false,
            geometrySimplify = true
        )
        val resp = ors.routeGeoJson(getString(R.string.ors_api_key), spec.profile, body)

        var geo = resp.features.firstOrNull()?.geometry?.coordinates.orEmpty()
            .map { (lon, lat) -> GeoPoint(lat, lon) }

        if (spec.dir == "COUNTERCLOCKWISE") geo = geo.asReversed()

        val simplified = withContext(Dispatchers.Default) { simplifyForMap(geo) }
        simplified.map { LatLngDto(it.latitude, it.longitude) }
    }

    // ---------- Draw ----------
    private fun drawPolyline(points: List<GeoPoint>) {
        routePolyline?.let { mapView.overlays.remove(it) }
        routePolyline = null
        if (points.isEmpty()) { mapView.invalidate(); return }

        routePolyline = Polyline().apply {
            setPoints(points)
            outlinePaint.strokeWidth = 6f
            outlinePaint.color = 0xFFE53935.toInt()
        }
        mapView.overlays.add(routePolyline)
        val bbox: BoundingBox = BoundingBox.fromGeoPointsSafe(points)
        mapView.zoomToBoundingBox(bbox, true, 80)
        mapView.invalidate()
    }

    // ---------- Helpers ----------
    private fun estimateTimeText(distanceMeters: Int): String {
        val hours = distanceMeters / 1000.0 / 15.0
        val h = floor(hours).toInt()
        val m = ((hours - h) * 60).roundToInt()
        return if (h > 0) "${h} h ${m} min" else "$m min"
    }

    private fun difficultyForDistance(meters: Int): Int {
        val km = meters / 1000.0
        return when {
            km <= 0  -> 1
            km < 10  -> 1
            km < 30  -> 2
            km < 60  -> 3
            km < 100 -> 4
            else     -> 5
        }
    }

    private fun simplifyForMap(points: List<GeoPoint>, toleranceMeters: Double = 10.0, maxPoints: Int = 400): List<GeoPoint> {
        if (points.size <= 2) return points
        val dp = douglasPeucker(points, toleranceMeters)
        return capPoints(dp, maxPoints)
    }

    private fun capPoints(points: List<GeoPoint>, maxPoints: Int): List<GeoPoint> {
        if (points.size <= maxPoints) return points
        val step = ceil(points.size / maxPoints.toDouble()).toInt()
        val out = ArrayList<GeoPoint>((points.size / step) + 1)
        for (i in points.indices step step) out.add(points[i])
        if (out.last() != points.last()) out.add(points.last())
        return out
    }

    private fun douglasPeucker(points: List<GeoPoint>, toleranceMeters: Double): List<GeoPoint> {
        val n = points.size
        if (n < 3) return points
        val keep = BooleanArray(n).apply { this[0] = true; this[n - 1] = true }
        val refLat = points.first().latitude
        val mPerDegLat = 111_320.0
        val mPerDegLon = 111_320.0 * cos(Math.toRadians(refLat))
        fun toXY(p: GeoPoint) = Pair(p.longitude * mPerDegLon, p.latitude * mPerDegLat)
        val xy = points.map { toXY(it) }
        fun perpDist(i: Int, a: Int, b: Int): Double {
            val (x, y) = xy[i]; val (x1, y1) = xy[a]; val (x2, y2) = xy[b]
            if (x1 == x2 && y1 == y2) return hypot(x - x1, y - y1)
            val dx = x2 - x1; val dy = y2 - y1
            val t = ((x - x1) * dx + (y - y1) * dy) / (dx*dx + dy*dy)
            val tc = t.coerceIn(0.0, 1.0)
            val px = x1 + tc * dx; val py = y1 + tc * dy
            return hypot(x - px, y - py)
        }
        fun rec(a: Int, b: Int) {
            var maxD = 0.0; var idx = -1
            for (i in a + 1 until b) {
                val d = perpDist(i, a, b)
                if (d > maxD) { maxD = d; idx = i }
            }
            if (maxD > toleranceMeters && idx != -1) { keep[idx] = true; rec(a, idx); rec(idx, b) }
        }
        rec(0, n - 1)
        val out = ArrayList<GeoPoint>()
        for (i in 0 until n) if (keep[i]) out.add(points[i])
        return out
    }
}




