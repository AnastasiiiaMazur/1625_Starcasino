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

            // Map preview (small & light)
            map.setMultiTouchControls(false)
            map.setTileSource(TileSourceFactory.MAPNIK)
            val start = GeoPoint(ride.startLat, ride.startLon)
            map.controller.setZoom(13.0)
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
            dateTv.text = "—" // add createdAt in schema later if you want a real date
            val km = ride.distanceMeters / 1000.0
            distTv.text = "${formatKm(km)} km, ${formatDuration(ride.durationSeconds)}"
            dateTv.text = formatRideDate(ride.createdAt, longMonth = true)

            // VIEW -> Navigator with RouteSpec built from snapshot in DB
            viewBtn.setOnClickListener {
                val spec = RouteSpec(
                    start = LatLngDto(ride.startLat, ride.startLon),
                    lengthMeters = ride.specLengthMeters,
                    profile = ride.specProfile,
                    seed = ride.specSeed,
                    dir = ride.specDir
                )
                val args = bundleOf(ARG_ROUTE_SPEC to spec)
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

    private fun insertDummyRide() {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val dao = AppDatabase.getInstance(requireContext()).rideDao()

            val dummy = RideEntity(
                name = "",
                startLat = 51.5074,
                startLon = -0.1278,
                specLengthMeters = 5_000,
                specProfile = "cycling-mountain",
                specSeed = 1,
                specDir = "CLOCKWISE",
                polylineJson = null,
                distanceMeters = 5_000,
                durationSeconds = 1_800,
                avgSpeedKmh = null,
                difficulty = 2,
                description = "Testing how it works",
                rating = null,
                createdAt = System.currentTimeMillis()
            )

            val id = dao.insert(dummy)
            dao.updateName(id, "Route $id")

            withContext(Dispatchers.Main) {
                Toast.makeText(requireContext(), "Inserted Route $id", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
