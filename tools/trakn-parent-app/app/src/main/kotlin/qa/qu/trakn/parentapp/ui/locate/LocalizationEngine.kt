package qa.qu.trakn.parentapp.ui.locate

import qa.qu.trakn.parentapp.data.models.AccessPoint
import qa.qu.trakn.parentapp.data.models.LocationEstimate
import qa.qu.trakn.parentapp.data.models.ScannedAp
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sqrt

object LocalizationEngine {

    private const val MIN_RSSI_DBM  = -80   // drop anchors weaker than this
    private const val MAX_JUMP_M    = 6.0   // hard cap on position change per scan
    private const val RSSI_WINDOW   = 1

    // ── RSSI smoothing (trimmed-mean window) ──────────────────────────────────
    private val rssiHistory = mutableMapOf<String, ArrayDeque<Int>>()

    private fun smoothRssi(bssid: String, rssi: Int): Double {
        val q = rssiHistory.getOrPut(bssid) { ArrayDeque() }
        if (q.size >= RSSI_WINDOW) q.removeFirst()
        q.addLast(rssi)
        val sorted = q.sorted()
        return if (sorted.size >= 3) sorted.subList(1, sorted.size - 1).average()
               else sorted.average()
    }

    // ── Outlier rejection (> 2σ from mean distance) ───────────────────────────
    private fun filterAnchors(
        anchors: List<Pair<AccessPoint, Double>>,
    ): List<Pair<AccessPoint, Double>> {
        if (anchors.size <= 3) return anchors
        val mean = anchors.map { it.second }.average()
        val std  = sqrt(anchors.map { (it.second - mean).pow(2) }.average())
        val filtered = anchors.filter { abs(it.second - mean) <= 2.0 * std }
        return filtered.ifEmpty { anchors }
    }

    fun resetHistory() {
        rssiHistory.clear()
        lastEstimate = null
    }

    // ── Three-zone adaptive EMA with hard jump cap ────────────────────────────
    private var lastEstimate: Pair<Double, Double>? = null

    private fun smoothPosition(x: Double, y: Double): Pair<Double, Double> {
        val prev = lastEstimate ?: return Pair(x, y).also { lastEstimate = it }

        var nx = x; var ny = y
        val dx = nx - prev.first
        val dy = ny - prev.second
        var movement = sqrt(dx * dx + dy * dy)

        // Hard cap: prevent corridor snaps from crossing room boundaries
        if (movement > MAX_JUMP_M) {
            val scale = MAX_JUMP_M / movement
            nx = prev.first  + dx * scale
            ny = prev.second + dy * scale
            movement = MAX_JUMP_M
        }

        val alpha = when {
            movement < 0.5 -> 0.50   // stationary
            movement < 4.0 -> 0.85   // walking
            else           -> 0.95   // fast (only fires at exactly MAX_JUMP_M)
        }

        val result = Pair(
            alpha * nx + (1 - alpha) * prev.first,
            alpha * ny + (1 - alpha) * prev.second,
        )
        lastEstimate = result
        return result
    }

    /**
     * Log-distance path loss:  dist = 10^((rssi_ref − rssi) / (10 · n))
     * Clamped to [0.3 m, 80 m].
     */
    fun estimateDistance(ap: AccessPoint, rssi: Int): Double {
        val exponent = (ap.rssiRef - rssi) / (10.0 * ap.pathLossN)
        return 10.0.pow(exponent).coerceIn(0.3, 80.0)
    }

    /**
     * Localize the device from a Wi-Fi scan.
     *
     * 1. Drop anchors with RSSI < -80 dBm (too weak).
     * 2. Trimmed-mean RSSI smoothing (window 5).
     * 3. Sort (dist asc, rssi desc); outlier reject; top-5.
     * 4. Drop scan if avg error > 20 dBm.
     * 5. Snap only when AP distance < 0.5 m (nearly disabled — prevents false snaps).
     * 6. ≥3 → Gauss-Newton; 2 → centroid; 1 → null.
     * 7. Three-zone EMA + 4 m/scan jump cap.
     */
    fun localize(scan: List<ScannedAp>, knownAps: List<AccessPoint>): LocationEstimate? {
        val scanMap = scan.associateBy { it.bssid.lowercase() }

        val anchors: List<Pair<AccessPoint, Double>> = knownAps
            .mapNotNull { ap ->
                val scanned = scanMap[ap.bssid.lowercase()] ?: return@mapNotNull null
                if (scanned.rssi < MIN_RSSI_DBM) return@mapNotNull null   // too weak
                val filteredRssi = smoothRssi(ap.bssid, scanned.rssi)
                val dist = estimateDistance(ap, filteredRssi.toInt())
                Pair(ap, dist)
            }
            .sortedWith(compareBy(
                { it.second },
                { -(scanMap[it.first.bssid.lowercase()]?.rssi ?: Int.MIN_VALUE) }
            ))

        if (anchors.isEmpty()) return null

        val cleanedAnchors = filterAnchors(anchors)
        val strongAnchors  = cleanedAnchors.take(5)

        val avgError = strongAnchors.map { (ap, dist) ->
            val rssiModel = ap.rssiRef - 10.0 * ap.pathLossN * log10(dist)
            val rssiObs   = scanMap[ap.bssid.lowercase()]!!.rssi.toDouble()
            abs(rssiObs - rssiModel)
        }.average()

        if (avgError > 20.0) return null

        // Snap only when unmistakably next to an AP (< 0.5 m)
        val strongest = strongAnchors.maxByOrNull { scanMap[it.first.bssid.lowercase()]?.rssi ?: Int.MIN_VALUE }
        if (strongest != null && strongest.second < 0.5) {
            val smoothed = smoothPosition(strongest.first.x, strongest.first.y)
            return LocationEstimate(smoothed.first, smoothed.second, strongAnchors.size, avgError)
        }

        return when {
            strongAnchors.size == 1 -> null
            strongAnchors.size == 2 -> {
                val c = weightedCentroid(strongAnchors, avgError)
                val s = smoothPosition(c.xM, c.yM)
                LocationEstimate(s.first, s.second, strongAnchors.size, avgError)
            }
            else -> {
                val ls = multilaterateLS(strongAnchors)
                val pos = if (ls != null && isWithinBounds(ls, strongAnchors)) ls
                          else weightedCentroid(strongAnchors.take(3), avgError)
                              .let { Pair(it.xM, it.yM) }
                val smoothed = smoothPosition(pos.first, pos.second)
                LocationEstimate(smoothed.first, smoothed.second, strongAnchors.size, avgError)
            }
        }
    }

    // ── Weighted centroid (w = 1 / (dist + 0.5)) ─────────────────────────────
    private fun weightedCentroid(
        anchors: List<Pair<AccessPoint, Double>>,
        avgError: Double,
    ): LocationEstimate {
        val weights = anchors.map { (_, d) -> 1.0 / (d + 0.5) }
        val wSum    = weights.sum()
        val x = anchors.indices.sumOf { i -> weights[i] * anchors[i].first.x } / wSum
        val y = anchors.indices.sumOf { i -> weights[i] * anchors[i].first.y } / wSum
        return LocationEstimate(x, y, anchors.size, avgError)
    }

    // ── Bounds check ──────────────────────────────────────────────────────────
    private fun isWithinBounds(
        pos: Pair<Double, Double>,
        anchors: List<Pair<AccessPoint, Double>>,
    ): Boolean {
        val minX = anchors.minOf { it.first.x }
        val maxX = anchors.maxOf { it.first.x }
        val minY = anchors.minOf { it.first.y }
        val maxY = anchors.maxOf { it.first.y }
        val mx = (maxX - minX).coerceAtLeast(5.0) * 0.30 + 2.0
        val my = (maxY - minY).coerceAtLeast(5.0) * 0.30 + 2.0
        return pos.first  in (minX - mx)..(maxX + mx) &&
               pos.second in (minY - my)..(maxY + my)
    }

    // ── C-Taylor hybrid: weighted-centroid init + Gauss-Newton refinement, w=1/d² ──
    private fun multilaterateLS(anchors: List<Pair<AccessPoint, Double>>): Pair<Double, Double>? {
        val init = weightedCentroid(anchors, 0.0)
        var x = init.xM
        var y = init.yM

        repeat(10) {
            var jtj00 = 0.0; var jtj01 = 0.0; var jtj11 = 0.0
            var jtr0  = 0.0; var jtr1  = 0.0

            for ((ap, d) in anchors) {
                val dx   = x - ap.x
                val dy   = y - ap.y
                val dist = sqrt(dx * dx + dy * dy).coerceAtLeast(1e-6)
                val w    = 1.0 / (d + 0.5)
                val jx   = dx / dist
                val jy   = dy / dist
                val res  = dist - d

                jtj00 += w * jx * jx
                jtj01 += w * jx * jy
                jtj11 += w * jy * jy
                jtr0  += w * jx * res
                jtr1  += w * jy * res
            }

            val det = jtj00 * jtj11 - jtj01 * jtj01
            if (abs(det) < 1e-8) return null

            val dx = (jtj11 * jtr0 - jtj01 * jtr1) / det
            val dy = (jtj00 * jtr1 - jtj01 * jtr0) / det
            x -= dx
            y -= dy

            if (sqrt(dx * dx + dy * dy) < 1e-3) return Pair(x, y)
        }

        return Pair(x, y)
    }
}
