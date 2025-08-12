package com.stcs.oon.fragments

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.stcs.oon.R
import java.io.File
import android.net.Uri
import android.provider.Settings
import android.widget.Toast

class HomeFragment: Fragment(R.layout.fragment_home) {

    private lateinit var newRouteBtn: LinearLayout
    private lateinit var savedRidesBtn: LinearLayout
    private lateinit var statsBtn: LinearLayout
    private lateinit var manualRouteBtn: LinearLayout

    private lateinit var mapView: org.osmdroid.views.MapView
    private var myLocation: org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay? = null

    private val requestLocationPerms =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            val granted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                    result[Manifest.permission.ACCESS_COARSE_LOCATION] == true

            if (granted) {
                centerFromLastKnown()
                enableMyLocation()
            } else {
                if (isPermanentlyDenied()) {
                    showLocationPermissionSettingsDialog()
                } else {
                    Toast.makeText(requireContext(), "Location permission is required to show your position.", Toast.LENGTH_LONG).show()
                }
            }
        }

    private val openSettingsLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            checkPermsThenEnableLocation()
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

        val base = File(requireContext().cacheDir, "osmdroid")
        val tiles = File(base, "tiles")
        org.osmdroid.config.Configuration.getInstance().apply {
            userAgentValue = requireContext().packageName
            osmdroidBasePath = base
            osmdroidTileCache = tiles
        }

        mapView = view.findViewById(R.id.mapView)
        mapView.setMultiTouchControls(true)
        mapView.setTileSource(org.osmdroid.tileprovider.tilesource.TileSourceFactory.MAPNIK)

        checkPermsThenEnableLocation()

    }

    private fun checkPermsThenEnableLocation() {
        val ctx = requireContext()
        val fine = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (fine || coarse) {
            centerFromLastKnown()
            enableMyLocation()
        } else {
            requestLocationPerms.launch(arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ))
        }
    }

    @SuppressLint("MissingPermission")
    private fun centerFromLastKnown() {
        val lm = requireContext().getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER
        )
        var best: Location? = null
        for (p in providers) {
            val l = runCatching { lm.getLastKnownLocation(p) }.getOrNull()
            if (l != null && (best == null || l.time > best!!.time)) best = l
        }
        best?.let {
            val here = org.osmdroid.util.GeoPoint(it.latitude, it.longitude)
            mapView.controller.setZoom(15.0)
            mapView.controller.setCenter(here)
        }
    }

    @SuppressLint("MissingPermission")
    private fun enableMyLocation() {
        if (myLocation != null) return
        val gpsProvider = org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider(requireContext()).apply {
            locationUpdateMinTime = 1000L
            locationUpdateMinDistance = 2f
        }
        myLocation = org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay(gpsProvider, mapView).apply {
            enableMyLocation()
            enableFollowLocation()
        }
        mapView.overlays.add(myLocation)
        myLocation?.runOnFirstFix {
            myLocation?.myLocation?.let { loc ->
                requireActivity().runOnUiThread {
                    mapView.controller.animateTo(org.osmdroid.util.GeoPoint(loc.latitude, loc.longitude))
                }
            }
        }
    }

    private fun isPermanentlyDenied(): Boolean {
        val ctx = requireContext()
        val fineDenied = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
        val coarseDenied = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
        val fineRationale = shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)
        val coarseRationale = shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_COARSE_LOCATION)
        return fineDenied && coarseDenied && !fineRationale && !coarseRationale
    }

    private fun showLocationPermissionSettingsDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Location permission needed")
            .setMessage("To show your current location, allow location access in Settings.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Open settings") { _, _ ->
                val intent = Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", requireContext().packageName, null)
                )
                openSettingsLauncher.launch(intent)
            }
            .show()
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