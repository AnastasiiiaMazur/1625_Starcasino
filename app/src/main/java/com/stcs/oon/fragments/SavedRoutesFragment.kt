package com.stcs.oon.fragments

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import com.stcs.oon.R
import org.osmdroid.views.MapView
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.stcs.oon.db.AppDatabase
import com.stcs.oon.db.RideEntity
import com.stcs.oon.db.LatLngDto
import com.stcs.oon.db.RouteSpec
import com.stcs.oon.fragments.extra.SavedDetailsFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Marker
import java.util.Locale

class SavedRoutesFragment: Fragment(R.layout.fragment_saved_routes) {

    private lateinit var ridesContainer: LinearLayout
    private val mapViews = mutableListOf<MapView>() // to forward lifecycle

    companion object {
        private const val ARG_ROUTE_SPEC = "arg_route_spec"
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        ridesContainer = view.findViewById(R.id.ridesContainer)

        // osmdroid base config once
        val base = requireContext().cacheDir.resolve("osmdroid")
        Configuration.getInstance().apply {
            userAgentValue = requireContext().packageName
            osmdroidBasePath = base
            osmdroidTileCache = base.resolve("tiles")
        }
        loadRides()
    }

    private fun loadRides() {
        viewLifecycleOwner.lifecycleScope.launch {
            val rides = withContext(Dispatchers.IO) {
                AppDatabase.getInstance(requireContext()).rideDao().getAll()
            }
            populate(rides)
        }
    }

    private fun populate(rides: List<RideEntity>) {
        ridesContainer.removeAllViews()
        mapViews.clear()

        for (ride in rides) {
            val item = layoutInflater.inflate(R.layout.saved_ride_item, ridesContainer, false)

            val titleTv = item.findViewById<TextView>(R.id.rideTitle)
            val dateTv = item.findViewById<TextView>(R.id.rideDate)
            val distTv = item.findViewById<TextView>(R.id.rideDistance)
            val viewBtn = item.findViewById<TextView>(R.id.view)
            val delBtn = item.findViewById<TextView>(R.id.delete)
            val map = item.findViewById<MapView>(R.id.mapView)

            map.setMultiTouchControls(false)
            map.setTileSource(TileSourceFactory.MAPNIK)
            val start = GeoPoint(ride.startLat, ride.startLon)
            map.controller.setZoom(15.0)
            map.controller.setCenter(start)
            Marker(map).apply {
                position = start
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                title = "Start"
                map.overlays.add(this)
            }
            mapViews.add(map)

            // Texts
            titleTv.text = if (ride.name.isNotBlank()) ride.name else "Route ${ride.id}"
            dateTv.text = "—"
            val km = ride.distanceMeters / 1000.0
            distTv.text = "${formatKm(km)} km, ${formatDuration(ride.durationSeconds)}"
            dateTv.text = formatRideDate(ride.createdAt, longMonth = true)

            viewBtn.setOnClickListener {
                val args = Bundle().apply { putLong("arg_ride_id", ride.id) }
                findNavController().navigate(R.id.savedRoutesDetailsFragment, args)
            }

            delBtn.setOnClickListener {
                showConfirmDeleteDialog(
                    onYes = {
                        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                            AppDatabase.getInstance(requireContext()).rideDao().delete(ride.id)
                            withContext(Dispatchers.Main) {
                                Toast.makeText(requireContext(), "Deleted", Toast.LENGTH_SHORT).show()
                                ridesContainer.removeView(item)
                            }
                        }
                    },
                    onNo = { /* optional: do nothing */ }
                )
            }


            ridesContainer.addView(item)
        }
    }

    // ---------- Lifecycle -> forward to all MapViews ----------
    override fun onResume() {
        super.onResume()
        mapViews.forEach { it.onResume() }
    }
    override fun onPause() {
        mapViews.forEach { it.onPause() }
        super.onPause()
    }
    override fun onDestroyView() {
        mapViews.forEach { mv ->
            mv.overlays.clear()
        }
        mapViews.clear()
        super.onDestroyView()
    }

    // ---------- Helpers ----------
    private fun formatKm(km: Double): String =
        String.format(Locale.US, "%.1f", km)

    private fun formatDuration(seconds: Long): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        return if (h > 0) "${h}h ${m}m" else "${m}m"
    }

    private fun formatRideDate(epochMillis: Long, longMonth: Boolean = false): String {
        val pattern = if (longMonth) "d MMMM yyyy" else "d MMM yyyy"
        val sdf = java.text.SimpleDateFormat(pattern, java.util.Locale.getDefault())
        return sdf.format(java.util.Date(epochMillis))
    }


    private fun showConfirmDeleteDialog(
        onYes: () -> Unit,
        onNo: (() -> Unit)? = null
    ) {
        val dialogView = layoutInflater.inflate(R.layout.custom_alert_dialog, null)

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setCancelable(true)
            .create()

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.show()

        dialogView.findViewById<TextView>(R.id.message).setText(R.string.delete_string)

        dialogView.findViewById<TextView>(R.id.yes).setOnClickListener {
            dialog.dismiss()
            onYes()
        }
        dialogView.findViewById<TextView>(R.id.no).setOnClickListener {
            dialog.dismiss()
            onNo?.invoke()
        }

    }


    private fun insertSampleRides(count: Int = 10) {
        val cities = listOf(
            51.5074 to -0.1278,   // London
            53.4808 to -2.2426,   // Manchester
            55.9533 to -3.1883,   // Edinburgh
            48.8566 to  2.3522,   // Paris
            52.5200 to 13.4050    // Berlin
        )
        val profiles = listOf("cycling-regular", "cycling-road", "cycling-mountain")
        val dirs = listOf("CLOCKWISE", "COUNTERCLOCKWISE")

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val dao = AppDatabase.getInstance(requireContext()).rideDao()
            val now = System.currentTimeMillis()
            val dayMs = 24L * 60 * 60 * 1000

            repeat(count) {
                val (lat, lon) = cities.random()
                val lenMeters = listOf(5_000, 12_000, 25_000, 40_000, 65_000).random()
                val profile = profiles.random()
                val dir = dirs.random()
                val seed = (1..100_000).random()

                // Random date within last 30 days
                val createdAt = now - (0..30).random() * dayMs

                val km = lenMeters / 1000.0
                val durationSeconds = ((km / 15.0) * 3600.0).toLong().coerceAtLeast(300L)

                val ride = RideEntity(
                    id = 0,
                    name = "", // will rename after insert
                    startLat = lat,
                    startLon = lon,
                    specLengthMeters = lenMeters,
                    specProfile = profile,
                    specSeed = seed,
                    specDir = dir,
                    polylineJson = null,
                    distanceMeters = lenMeters,
                    durationSeconds = durationSeconds,
                    avgSpeedKmh = null,
                    difficulty = difficultyForDistance(lenMeters),
                    description = null,
                    rating = null,
                    createdAt = createdAt
                )

                val id = dao.insert(ride)
                dao.updateName(id, "Route $id")
            }

            withContext(Dispatchers.Main) {
                Toast.makeText(requireContext(), "Inserted $count dummy rides", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun difficultyForDistance(meters: Int): Int {
        val km = meters / 1000.0
        return when {
            km < 10   -> 1
            km < 30   -> 2
            km < 60   -> 3
            km < 100  -> 4
            else      -> 5
        }
    }
}
