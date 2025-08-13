package com.stcs.oon.fragments.extra

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.stcs.oon.R
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

class SavedDetailsFragment: Fragment(R.layout.fradment_saved_details) {

    private lateinit var map: MapView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        map = view.findViewById(R.id.mapView)

        val base = requireContext().cacheDir.resolve("osmdroid")
        Configuration.getInstance().apply {
            userAgentValue = requireContext().packageName
            osmdroidBasePath = base
            osmdroidTileCache = base.resolve("tiles")
        }

        // Map preview (small & light)
        map.setMultiTouchControls(false)
        map.setTileSource(TileSourceFactory.MAPNIK)

    }
}