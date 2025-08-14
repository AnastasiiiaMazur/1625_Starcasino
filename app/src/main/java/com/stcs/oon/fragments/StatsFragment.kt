package com.stcs.oon.fragments

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.components.AxisBase
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.stcs.oon.R
import com.stcs.oon.db.AppDatabase
import com.stcs.oon.db.RideEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import com.github.mikephil.charting.formatter.ValueFormatter
import kotlin.math.floor
import kotlin.math.roundToInt

class StatsFragment : Fragment(R.layout.fragment_stats) {

    // Filters
    private lateinit var dayView: TextView
    private lateinit var weekView: TextView
    private lateinit var monthView: TextView
    private lateinit var allView: TextView

    // KPIs
    private lateinit var totalDistance: TextView
    private lateinit var totalTime: TextView
    private lateinit var averageSpeed: TextView
    private lateinit var mostActiveDays: TextView
    private lateinit var longestRide: TextView
    private lateinit var totalRides: TextView

    // Charts
    private lateinit var distChart: BarChart
    private lateinit var timeChart: BarChart

    private enum class Range { DAY, WEEK, MONTH, ALL }
    private var currentRange = Range.DAY

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        dayView = view.findViewById(R.id.dayView)
        weekView = view.findViewById(R.id.weekView)
        monthView = view.findViewById(R.id.monthView)
        allView = view.findViewById(R.id.allView)

        totalDistance = view.findViewById(R.id.totalDistance)
        totalTime     = view.findViewById(R.id.totalTime)
        averageSpeed  = view.findViewById(R.id.averageSpeed)
        mostActiveDays= view.findViewById(R.id.mostActiveDays)
        longestRide   = view.findViewById(R.id.longestRide)
        totalRides    = view.findViewById(R.id.totalRides)

        distChart = view.findViewById(R.id.idBarChart)
        timeChart = view.findViewById(R.id.idBarChart2)

        setupFilterButtons()
        setupChart(distChart, yLabel = "km")
        setupChart(timeChart, yLabel = "min")

        // default selection: DAY (others remain default style)
        setRange(Range.ALL)
    }

    // ---------------- UI: filters ----------------

    private fun setupFilterButtons() {
        dayView.setOnClickListener   { setRange(Range.DAY) }
        weekView.setOnClickListener  { setRange(Range.WEEK) }
        monthView.setOnClickListener { setRange(Range.MONTH) }
        allView.setOnClickListener   { setRange(Range.ALL) }
    }

    private fun setRange(range: Range) {
        currentRange = range
        val defaultBg = R.drawable.button_outline_black
        val selectedBg = R.drawable.basic_button_small_corners
        val red = requireContext().getColor(R.color.text_red)
        val white = requireContext().getColor(R.color.white)

        fun style(tv: TextView, selected: Boolean) {
            tv.setBackgroundResource(if (selected) selectedBg else defaultBg)
            tv.setTextColor(if (selected) white else red)
        }

        style(dayView,   range == Range.DAY)
        style(weekView,  range == Range.WEEK)
        style(monthView, range == Range.MONTH)
        style(allView,   range == Range.ALL)

        loadStats(range)
    }

    // ---------------- Data loading ----------------

    private fun loadStats(range: Range) {
        val (startMs, endMs) = rangeWindow(range)

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val dao = AppDatabase.getInstance(requireContext()).rideDao()

            val rides = if (startMs != null && endMs != null) {
                try { dao.getBetween(startMs, endMs) } catch (_: Throwable) {
                    dao.getAllasc().filter { it.createdAt in startMs..endMs }
                }
            } else {
                try { dao.getAll() } catch (_: Throwable) { emptyList() }
            }

            val dayKeyFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val labelFmt  = SimpleDateFormat("d MMM", Locale.getDefault())

            data class Bucket(
                val key: String,
                val dayStart: Long,
                var distMeters: Long = 0L,
                var durSeconds: Long = 0L,
                var count: Int = 0
            )

            val cal = Calendar.getInstance()
            fun dayStart(millis: Long): Long {
                cal.timeInMillis = millis
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                return cal.timeInMillis
            }

            val buckets = LinkedHashMap<String, Bucket>()
            var sumDist = 0L
            var sumDur  = 0L
            var longest: RideEntity? = null

            val weekdayCounts = IntArray(7)
            val weekdayNames = arrayOf("Sunday","Monday","Tuesday","Wednesday","Thursday","Friday","Saturday")

            rides.sortedBy { it.createdAt }.forEach { r ->
                sumDist += r.distanceMeters
                sumDur  += r.durationSeconds
                if (longest == null || r.distanceMeters > longest!!.distanceMeters) longest = r

                cal.timeInMillis = r.createdAt
                val w = cal.get(Calendar.DAY_OF_WEEK)
                weekdayCounts[w - 1]++

                val key = dayKeyFmt.format(Date(r.createdAt))
                val start = dayStart(r.createdAt)
                val b = buckets.getOrPut(key) { Bucket(key, start) }
                b.distMeters += r.distanceMeters
                b.durSeconds += r.durationSeconds
                b.count++
            }

            val bucketList = buckets.values.sortedBy { it.dayStart }

            val distEntries = ArrayList<BarEntry>(bucketList.size)
            val timeEntries = ArrayList<BarEntry>(bucketList.size)
            val xLabels     = ArrayList<String>(bucketList.size)

            bucketList.forEachIndexed { idx, b ->
                distEntries += BarEntry(idx.toFloat(), (b.distMeters / 1000f))
                timeEntries += BarEntry(idx.toFloat(), (b.durSeconds / 60f))
                xLabels     += labelFmt.format(Date(b.dayStart))
            }

            val totalKm = sumDist / 1000.0
            val totalHours = sumDur / 3600.0
            val avgSpeedKmH = if (sumDur > 0) totalKm / totalHours else 0.0

            val ranked = weekdayCounts
                .mapIndexed { i, c -> i to c }
                .sortedByDescending { it.second }
                .filter { it.second > 0 }
                .take(2)
                .joinToString(", ") { weekdayNames[it.first] }
                .ifEmpty { "—" }

            withContext(Dispatchers.Main) {
                totalDistance.text = "Total Distance: ${formatKm(totalKm)} km"
                totalTime.text     = "Total Ride Time: ${formatHours(totalHours)}"
                averageSpeed.text  = "Average Speed: ${formatKm(avgSpeedKmH)} km/h"
                mostActiveDays.text= "Most Active Days: $ranked"
                totalRides.text    = "Total Rides: ${rides.size} rides"
                longestRide.text   =
                    longest?.let { r ->
                        val title = if (r.name.isNotBlank()) r.name else "Route ${r.id}"
                        "Longest Ride: $title, ${formatKm(r.distanceMeters / 1000.0)} km"
                    } ?: "Longest Ride: —"

                setBarData(
                    chart = distChart,
                    entries = distEntries,
                    label = "Distance (km)",
                    xLabels = xLabels
                )
                setBarData(
                    chart = timeChart,
                    entries = timeEntries,
                    label = "Time (min)",
                    xLabels = xLabels
                )
            }
        }
    }

    // ---------------- Charts ----------------

    private fun setupChart(chart: BarChart, yLabel: String) {
        chart.description.isEnabled = false
        chart.axisRight.isEnabled = false
        chart.legend.isEnabled = false
        chart.setScaleEnabled(false)
        chart.setPinchZoom(false)
        chart.setDrawValueAboveBar(true)
        chart.setNoDataText("No data")
        chart.axisLeft.granularity = 1f
        chart.xAxis.granularity = 1f
        chart.xAxis.position = XAxis.XAxisPosition.BOTTOM
        chart.xAxis.setDrawGridLines(false)
        chart.axisLeft.setDrawGridLines(true)
        chart.axisLeft.axisMinimum = 0f
    }

    private fun setBarData(chart: BarChart, entries: List<BarEntry>, label: String, xLabels: List<String>) {
        val top    = ContextCompat.getColor(requireContext(), R.color.light_red)
        val bottom = ContextCompat.getColor(requireContext(), R.color.dark_red)

        val dataSet = BarDataSet(entries, label).apply {
            setGradientColor(top, bottom)
        }

        chart.data = BarData(dataSet).apply { barWidth = 0.6f }

        chart.xAxis.valueFormatter = object : ValueFormatter() {
            override fun getAxisLabel(value: Float, axis: AxisBase?): String {
                val i = value.toInt()
                return if (i in xLabels.indices) xLabels[i] else ""
            }
        }

        chart.setFitBars(true)
        chart.animateY(600)
        chart.invalidate()
    }

    // ---------------- Ranges ----------------

    private fun rangeWindow(range: Range): Pair<Long?, Long?> {
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance().apply { timeInMillis = now }

        fun startOfDay(c: Calendar): Long {
            c.set(Calendar.HOUR_OF_DAY, 0)
            c.set(Calendar.MINUTE, 0)
            c.set(Calendar.SECOND, 0)
            c.set(Calendar.MILLISECOND, 0)
            return c.timeInMillis
        }

        return when (range) {
            Range.DAY -> {
                val start = startOfDay(cal)
                start to now
            }
            Range.WEEK -> {
                cal.firstDayOfWeek = Calendar.MONDAY
                cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
                val start = startOfDay(cal)
                start to now
            }
            Range.MONTH -> {
                cal.set(Calendar.DAY_OF_MONTH, 1)
                val start = startOfDay(cal)
                start to now
            }
            Range.ALL -> null to null
        }
    }

    // ---------------- Formatting ----------------

    private fun formatKm(v: Double) = String.format(Locale.US, "%.1f", v)
    private fun formatHours(h: Double): String {
        val whole = floor(h).toInt()
        val mins = ((h - whole) * 60.0).roundToInt()
        return if (whole > 0) "${whole} h ${mins} min" else "$mins min"
    }
}
