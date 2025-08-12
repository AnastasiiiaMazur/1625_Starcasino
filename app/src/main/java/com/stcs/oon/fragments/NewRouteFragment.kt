package com.stcs.oon.fragments

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.SeekBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.stcs.oon.R

class NewRouteFragment: Fragment(R.layout.fragment_new_route) {

    private lateinit var kmSeekBar: SeekBar
    private lateinit var kmPopup: PopupWindow
    private lateinit var kmPopupText: TextView
    private lateinit var map:  org.osmdroid.views.MapView

    // Start location choices
    private lateinit var startLocation: LinearLayout
    private lateinit var startManualLocation: LinearLayout

    // Route type
    private lateinit var twistyRoute: LinearLayout
    private lateinit var scenicRoute: LinearLayout
    private lateinit var flatRoute: LinearLayout

    // Route direction
    private lateinit var clockwiseRoute: LinearLayout
    private lateinit var counterclockwiseRoute: LinearLayout
    private lateinit var randomRoute: LinearLayout

    private lateinit var generateRouteBtn: TextView

    private enum class StartOption { MY_LOCATION, MANUAL }
    private enum class RouteType { TWISTY, SCENIC, FLAT }
    private enum class Direction { CLOCKWISE, COUNTERCLOCKWISE, RANDOM }

    private var selectedStart = StartOption.MY_LOCATION
    private var selectedType = RouteType.TWISTY
    private var selectedDir = Direction.CLOCKWISE

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        kmSeekBar = view.findViewById(R.id.kmSeekBar)
        startLocation = view.findViewById(R.id.startLocation)
        startManualLocation = view.findViewById(R.id.startManualLocation)
        map = view.findViewById(R.id.map)
        twistyRoute = view.findViewById(R.id.twistyRoute)
        scenicRoute = view.findViewById(R.id.scenicRoute)
        flatRoute = view.findViewById(R.id.flatRoute)
        clockwiseRoute = view.findViewById(R.id.clockwiseRoute)
        counterclockwiseRoute = view.findViewById(R.id.counterclockwiseRoute)
        randomRoute = view.findViewById(R.id.randomRoute)
        generateRouteBtn = view.findViewById(R.id.generateRouteBtn)

        kmSeekBar.max = 200
        kmSeekBar.progress = 0

        kmPopupText = TextView(requireContext()).apply {
            textSize = 16f
            setPadding(dp(8), dp(4), dp(8), dp(4))
            setTextColor(Color.BLACK)
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = dp(12).toFloat()
                setStroke(dp(1), Color.LTGRAY)
            }
            elevation = dp(4).toFloat()
        }
        kmPopup = PopupWindow(
            kmPopupText,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            false
        )

        kmSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onStartTrackingTouch(seekBar: SeekBar) {
                showAndPositionKm(seekBar.progress)
            }

            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) showAndPositionKm(progress)
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                kmPopup.dismiss()
            }
        })

        setupUiGroups()
    }

    private fun showAndPositionKm(progress: Int) {
        kmPopupText.text = "$progress km"
        // measure popup to get its width
        kmPopupText.measure(
            View.MeasureSpec.UNSPECIFIED,
            View.MeasureSpec.UNSPECIFIED
        )
        val popW = kmPopupText.measuredWidth

        val sb = kmSeekBar
        val fraction = progress.toFloat() / sb.max
        val available = sb.width - sb.paddingLeft - sb.paddingRight
        val thumbCenterX = sb.paddingLeft + (available * fraction).toInt()

        // center popup under thumb; clamp inside seekbar width
        var xOff = thumbCenterX - popW / 2
        xOff = xOff.coerceIn(0, sb.width - popW)

        val yOff = dp(8) // distance below the SeekBar

        if (kmPopup.isShowing) {
            kmPopup.update(sb, xOff, yOff, -1, -1)
        } else {
            kmPopup.showAsDropDown(sb, xOff, yOff, Gravity.START)
        }
    }

    private fun dp(v: Int): Int =
        (v * resources.displayMetrics.density).toInt()

    private fun setupUiGroups() {
        setupSingleSelect(
            items = listOf(startLocation, startManualLocation),
            defaultIndex = 0
        ) { selectedId ->
            selectedStart = when (selectedId) {
                R.id.startLocation -> StartOption.MY_LOCATION
                else -> StartOption.MANUAL
            }
        }

        setupSingleSelect(
            items = listOf(twistyRoute, scenicRoute, flatRoute),
            defaultIndex = 0
        ) { selectedId ->
            selectedType = when (selectedId) {
                R.id.twistyRoute -> RouteType.TWISTY
                R.id.scenicRoute -> RouteType.SCENIC
                else -> RouteType.FLAT
            }
        }

        setupSingleSelect(
            items = listOf(clockwiseRoute, counterclockwiseRoute, randomRoute),
            defaultIndex = 0
        ) { selectedId ->
            selectedDir = when (selectedId) {
                R.id.clockwiseRoute -> Direction.CLOCKWISE
                R.id.counterclockwiseRoute -> Direction.COUNTERCLOCKWISE
                else -> Direction.RANDOM
            }
        }
    }

    private fun setupSingleSelect(
        items: List<LinearLayout>,
        defaultIndex: Int = 0,
        onSelected: (selectedLayoutId: Int) -> Unit = {}
    ) {
        var selectedIndex = defaultIndex.coerceIn(0, items.lastIndex)

        items.forEachIndexed { i, row ->
            setSelectedState(row, i == selectedIndex)
            row.setOnClickListener {
                if (i == selectedIndex) return@setOnClickListener
                setSelectedState(items[selectedIndex], false)
                setSelectedState(row, true)
                selectedIndex = i
                onSelected(row.id)
            }
        }

        onSelected(items[selectedIndex].id)
    }

    private fun setSelectedState(container: LinearLayout, selected: Boolean) {
        (container.getChildAt(0) as? ImageView)?.isEnabled = selected
    }
}