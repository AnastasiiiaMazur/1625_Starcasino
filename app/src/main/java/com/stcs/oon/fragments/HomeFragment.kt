package com.stcs.oon.fragments

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.stcs.oon.R

class HomeFragment: Fragment(R.layout.fragment_home) {

    private lateinit var newRouteBtn: LinearLayout
    private lateinit var savedRidesBtn: LinearLayout
    private lateinit var statsBtn: LinearLayout
    private lateinit var manualRouteBtn: LinearLayout

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

    }
}