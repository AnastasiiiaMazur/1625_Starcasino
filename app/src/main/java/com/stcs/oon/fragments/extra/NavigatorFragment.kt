package com.stcs.oon.fragments.extra

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.stcs.oon.R
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import com.stcs.oon.fragments.helpers.LocationKit
import com.stcs.oon.db.RouteSessionViewModel
import com.stcs.oon.db.LatLngDto
import com.stcs.oon.db.AppDatabase
import com.stcs.oon.db.RideEntity
import com.google.gson.Gson
import kotlin.math.*

class NavigatorFragment : Fragment(R.layout.fragment_navigator) {

    private val session: RouteSessionViewModel by activityViewModels()

    private lateinit var mapView: MapView
    private var myLocationOverlay: MyLocationNewOverlay? = null
    private var routePolyline: Polyline? = null

    private lateinit var distanceTv: TextView
    private lateinit var timeTv: TextView
    private lateinit var difficultyTv: TextView
    private lateinit var startNavBtn: TextView
    private lateinit var saveRouteBtn: TextView

    private var navStartedAtMs: Long? = null

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

        val draft = session.draft
        if (draft == null) {
            Toast.makeText(requireContext(), "No route draft.", Toast.LENGTH_SHORT).show()
            return
        }

        // draw the same route
        val points = draft.points.map { GeoPoint(it.lat, it.lon) }
        drawPolyline(points)

        // fill quick header info
        distanceTv.text = "${draft.lengthMeters / 1000} km"
        timeTv.text = "~${estimateTimeText(draft.lengthMeters)}"
        difficultyTv.text = "—/5"

        // follow-location overlay (dot). We'll enable on "Start navigation"
        myLocationOverlay = LocationKit.attachMyLocationOverlay(
            mapView = mapView,
            context = requireContext(),
            follow = false
        )
        mapView.overlays.add(myLocationOverlay)

        // Start navigation: start timer + follow user
        startNavBtn.setOnClickListener {
            if (navStartedAtMs == null) {
                navStartedAtMs = System.currentTimeMillis()
                myLocationOverlay?.enableFollowLocation()
                startNavBtn.text = "STOP NAVIGATION"
            } else {
                myLocationOverlay?.disableFollowLocation()
                navStartedAtMs = null
                startNavBtn.text = "START NAVIGATION"
            }
        }

        // Save ride
        saveRouteBtn.setOnClickListener {
            saveRide(draft)
        }
    }

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

    private fun estimateTimeText(distanceMeters: Int): String {
        // very rough: 15 km/h cycling
        val hours = distanceMeters / 1000.0 / 15.0
        val h = floor(hours).toInt()
        val m = ((hours - h) * 60).roundToInt()
        return if (h > 0) "${h} h ${m} min" else "$m min"
    }

    private fun saveRide(draft: com.stcs.oon.db.RouteDraft) {
        val startMs = navStartedAtMs ?: System.currentTimeMillis() // if they never pressed start, assume now
        val durationSec = max(1L, (System.currentTimeMillis() - startMs) / 1000)

        // Distance from draft.start to current user position (straight line as requested)
        val current = myLocationOverlay?.myLocation
        val distanceMeters = if (current != null) {
            haversineMeters(draft.start.lat, draft.start.lon, current.latitude, current.longitude).roundToInt()
        } else {
            0
        }

        val speedMps = if (durationSec > 0) distanceMeters.toDouble() / durationSec else 0.0

        val polylineJson = Gson().toJson(draft.points) // store the exact route

        viewLifecycleOwner.lifecycleScope.launch {
            val dao = AppDatabase.get(requireContext()).rideDao()
            // insert first
            val id = dao.insert(
                RideEntity(
                    name = "", // fill after we get id
                    polylineJson = polylineJson,
                    distanceMeters = distanceMeters,
                    durationSeconds = durationSec,
                    avgSpeedMps = speedMps,
                    difficulty = null, // later
                    description = ""
                )
            )
            // update name = "Route {id}"
            dao.updateName(id, "Route $id")
            Toast.makeText(requireContext(), "Ride saved as Route $id", Toast.LENGTH_LONG).show()
        }
    }

    private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat/2).pow(2.0) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon/2).pow(2.0)
        val c = 2 * atan2(sqrt(a), sqrt(1-a))
        return R * c
    }

    override fun onDestroyView() {
        myLocationOverlay?.disableMyLocation()
        myLocationOverlay = null
        routePolyline = null
        super.onDestroyView()
    }
}
