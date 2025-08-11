package com.stcs.oon.fragments

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.stcs.oon.R
import java.io.File

class HomeFragment: Fragment(R.layout.fragment_home) {

    private lateinit var newRouteBtn: LinearLayout
    private lateinit var savedRidesBtn: LinearLayout
    private lateinit var statsBtn: LinearLayout
    private lateinit var manualRouteBtn: LinearLayout

    private lateinit var mapView: org.osmdroid.views.MapView
    private var myLocation: org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay? = null

    private val requestLocationPerms =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
            if (granted.values.any { it }) enableMyLocation()
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        newRouteBtn = view.findViewById(R.id.newRouteBtn)
        savedRidesBtn = view.findViewById(R.id.savedRidesBtn)
        statsBtn = view.findViewById(R.id.statsBtn)
        manualRouteBtn = view.findViewById(R.id.manualRouteBtn)

        newRouteBtn.setOnClickListener { findNavController().navigate(R.id.newRouteFragment) }
        savedRidesBtn.setOnClickListener { findNavController().navigate(R.id.savedRoutesFragment) }
        statsBtn.setOnClickListener { findNavController().navigate(R.id.statsFragment) }
        manualRouteBtn.setOnClickListener { findNavController().navigate(R.id.manualRouteFragment) }

        // Configure osmdroid cache paths + user agent
        val base = File(requireContext().cacheDir, "osmdroid")
        val tiles = File(base, "tiles")
        org.osmdroid.config.Configuration.getInstance().apply {
            userAgentValue = requireContext().packageName
            osmdroidBasePath = base
            osmdroidTileCache = tiles
        }

        mapView = view.findViewById(R.id.mapView)
        mapView.setMultiTouchControls(true)
        mapView.controller.setZoom(15.0)
        // Optional: start centered roughly over user's country to avoid (0,0) flash
        mapView.controller.setCenter(org.osmdroid.util.GeoPoint(51.5074, -0.1278)) // London

        // Default online OSM tiles (no key)
        mapView.setTileSource(org.osmdroid.tileprovider.tilesource.TileSourceFactory.MAPNIK)

        checkPermsThenEnableLocation()

    }

    private fun checkPermsThenEnableLocation() {
        val ctx = requireContext()
        val hasFine = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (hasFine || hasCoarse) enableMyLocation()
        else requestLocationPerms.launch(arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ))
    }

    @SuppressLint("MissingPermission")
    private fun enableMyLocation() {
        if (myLocation != null) return
        val provider = org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider(requireContext())
        myLocation = org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay(provider, mapView).apply {
            enableMyLocation()
            enableFollowLocation()
            // optional: set a custom accuracy circle or icon here
        }
        mapView.overlays.add(myLocation)
        // Center once we get a fix
        myLocation?.runOnFirstFix {
            val p = myLocation?.myLocation ?: return@runOnFirstFix
            requireActivity().runOnUiThread {
                mapView.controller.animateTo(p)
            }
        }
    }


    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        mapView.onPause()
        super.onPause()
    }

    override fun onDestroyView() {
        myLocation?.disableMyLocation()
        myLocation = null
        super.onDestroyView()
    }
}