package com.stcs.oon.fragments.extra

import com.stcs.oon.db.LatLngDto
import com.stcs.oon.db.RouteSpec

object RouteCache {
    private const val MAX = 16
    private val map = object : LinkedHashMap<String, List<LatLngDto>>(MAX, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<LatLngDto>>?) = size > MAX
    }

    fun key(spec: RouteSpec) =
        "${spec.profile}|${spec.lengthMeters}|${spec.seed}|${spec.dir}|" +
                "${"%.5f".format(spec.start.lat)},${"%.5f".format(spec.start.lon)}"

    fun get(spec: RouteSpec): List<LatLngDto>? = map[key(spec)]
    fun put(spec: RouteSpec, pts: List<LatLngDto>) { map[key(spec)] = pts }
}
