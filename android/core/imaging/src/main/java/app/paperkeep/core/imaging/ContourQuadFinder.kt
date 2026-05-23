package app.paperkeep.core.imaging

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Real document-quad finder operating on a raw luminance grid.
 *
 * Pipeline:
 *   1. Sobel gradient magnitude over the luminance grid.
 *   2. Adaptive threshold (Otsu-style on the gradient histogram).
 *   3. Morphological close (dilate then erode) to bridge dashed edges.
 *   4. Connected-component scan; keep components whose bounding box covers
 *      a non-trivial fraction of the image.
 *   5. For each candidate component, extract its convex hull, simplify with
 *      Douglas-Peucker, and snap to 4 vertices.
 *   6. Score quads on area + convexity + aspect + edge-strength along sides
 *      and return the best valid one.
 *
 * Deliberately uses only primitive arrays so JVM unit tests can build a
 * synthetic luminance grid (no Android Bitmap dependency).
 */
internal object ContourQuadFinder {

    /**
     * @param luma     row-major luminance grid (0..255 ints; `length == width * height`)
     * @param width    grid width
     * @param height   grid height
     * @return best-scoring quad or null when no plausible document is found.
     */
    /** Backwards-compatible entry point — returns just the quad. */
    fun find(luma: IntArray, width: Int, height: Int): Quad? =
        findScored(luma, width, height)?.quad

    /**
     * Detect a document quad and return it alongside the detector's
     * confidence score. Confidence ∈ [0,1].
     */
    fun findScored(luma: IntArray, width: Int, height: Int): Scored? {
        if (width < MIN_DIMENSION || height < MIN_DIMENSION) return null
        if (luma.size != width * height) return null

        val gradient = sobelMagnitude(luma, width, height)
        val threshold = otsu(gradient)
        val binary = ByteArray(gradient.size) { i ->
            if (gradient[i] >= threshold) 1.toByte() else 0.toByte()
        }
        morphClose(binary, width, height)

        val components = findComponents(binary, width, height)
        if (components.isEmpty()) return null

        val imageArea = width.toFloat() * height.toFloat()
        val candidates = components
            .filter { it.boundingArea(width, height) >= imageArea * MIN_COMPONENT_AREA_FRACTION }
            .sortedByDescending { it.boundingArea(width, height) }
            .take(MAX_CANDIDATE_COMPONENTS)

        var best: Scored? = null
        for (component in candidates) {
            val hull = convexHull(component.points)
            if (hull.size < 4) continue
            val quadPts = simplifyToQuad(hull) ?: continue
            val quad = orderedQuad(quadPts)
            val score = scoreQuad(quad, width, height, gradient) ?: continue
            if (best == null || score > best.score) {
                best = Scored(quad, score)
            }
        }
        return best
    }

    // ── Sobel gradient magnitude ─────────────────────────────────────────────

    private fun sobelMagnitude(luma: IntArray, w: Int, h: Int): IntArray {
        val out = IntArray(w * h)
        var maxMag = 0
        for (y in 1 until h - 1) {
            val rowAbove = (y - 1) * w
            val rowMid = y * w
            val rowBelow = (y + 1) * w
            for (x in 1 until w - 1) {
                val tl = luma[rowAbove + x - 1]
                val tc = luma[rowAbove + x]
                val tr = luma[rowAbove + x + 1]
                val ml = luma[rowMid + x - 1]
                val mr = luma[rowMid + x + 1]
                val bl = luma[rowBelow + x - 1]
                val bc = luma[rowBelow + x]
                val br = luma[rowBelow + x + 1]

                val gx = (tr + 2 * mr + br) - (tl + 2 * ml + bl)
                val gy = (bl + 2 * bc + br) - (tl + 2 * tc + tr)
                val mag = abs(gx) + abs(gy)
                out[rowMid + x] = mag
                if (mag > maxMag) maxMag = mag
            }
        }
        if (maxMag <= 0) return out
        // Normalise to 0..255 so the Otsu histogram stays in 256 bins.
        val scale = 255.0 / maxMag
        for (i in out.indices) {
            out[i] = (out[i] * scale).toInt().coerceIn(0, 255)
        }
        return out
    }

    private fun otsu(gradient: IntArray): Int {
        val hist = IntArray(256)
        for (v in gradient) hist[v]++
        val total = gradient.size
        if (total == 0) return 0

        var sum = 0L
        for (i in 0..255) sum += i.toLong() * hist[i]

        var sumB = 0L
        var wB = 0
        var maxVar = 0.0
        var threshold = 0

        for (t in 0..255) {
            wB += hist[t]
            if (wB == 0) continue
            val wF = total - wB
            if (wF == 0) break
            sumB += t.toLong() * hist[t]
            val mB = sumB.toDouble() / wB
            val mF = (sum - sumB).toDouble() / wF
            val between = wB.toDouble() * wF.toDouble() * (mB - mF) * (mB - mF)
            if (between > maxVar) {
                maxVar = between
                threshold = t
            }
        }
        // Edge maps are sparse — bias the threshold up so weak texture is dropped.
        return max(threshold, MIN_EDGE_THRESHOLD)
    }

    // ── Morphological close ──────────────────────────────────────────────────

    private fun morphClose(binary: ByteArray, w: Int, h: Int) {
        val dilated = dilate(binary, w, h)
        val eroded = erode(dilated, w, h)
        System.arraycopy(eroded, 0, binary, 0, binary.size)
    }

    private fun dilate(src: ByteArray, w: Int, h: Int): ByteArray {
        val out = ByteArray(src.size)
        for (y in 0 until h) {
            for (x in 0 until w) {
                var any: Byte = 0
                outer@ for (dy in -1..1) {
                    val ny = y + dy
                    if (ny < 0 || ny >= h) continue
                    for (dx in -1..1) {
                        val nx = x + dx
                        if (nx < 0 || nx >= w) continue
                        if (src[ny * w + nx] == 1.toByte()) { any = 1; break@outer }
                    }
                }
                out[y * w + x] = any
            }
        }
        return out
    }

    private fun erode(src: ByteArray, w: Int, h: Int): ByteArray {
        val out = ByteArray(src.size)
        for (y in 0 until h) {
            for (x in 0 until w) {
                var all: Byte = 1
                outer@ for (dy in -1..1) {
                    val ny = y + dy
                    if (ny < 0 || ny >= h) { all = 0; break@outer }
                    for (dx in -1..1) {
                        val nx = x + dx
                        if (nx < 0 || nx >= w) { all = 0; break@outer }
                        if (src[ny * w + nx] != 1.toByte()) { all = 0; break@outer }
                    }
                }
                out[y * w + x] = all
            }
        }
        return out
    }

    // ── Connected components (8-connectivity flood fill) ─────────────────────

    private data class Component(
        val points: ArrayList<IntPoint>,
        var minX: Int,
        var minY: Int,
        var maxX: Int,
        var maxY: Int,
    ) {
        fun boundingArea(w: Int, h: Int): Float {
            val bw = (maxX - minX + 1).coerceAtLeast(0)
            val bh = (maxY - minY + 1).coerceAtLeast(0)
            // unused w/h kept for symmetry with future refinement
            @Suppress("UNUSED_PARAMETER")
            return bw.toFloat() * bh.toFloat()
        }
    }

    internal data class IntPoint(val x: Int, val y: Int)

    private fun findComponents(binary: ByteArray, w: Int, h: Int): List<Component> {
        val visited = BooleanArray(binary.size)
        val out = ArrayList<Component>()
        val stackX = IntArray(binary.size)
        val stackY = IntArray(binary.size)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val idx = y * w + x
                if (visited[idx] || binary[idx] != 1.toByte()) continue

                val points = ArrayList<IntPoint>()
                var minX = x; var minY = y; var maxX = x; var maxY = y
                var top = 0
                stackX[top] = x; stackY[top] = y
                top++
                visited[idx] = true
                while (top > 0) {
                    top--
                    val cx = stackX[top]
                    val cy = stackY[top]
                    points.add(IntPoint(cx, cy))
                    if (cx < minX) minX = cx
                    if (cx > maxX) maxX = cx
                    if (cy < minY) minY = cy
                    if (cy > maxY) maxY = cy
                    for (dy in -1..1) {
                        val ny = cy + dy
                        if (ny < 0 || ny >= h) continue
                        for (dx in -1..1) {
                            if (dx == 0 && dy == 0) continue
                            val nx = cx + dx
                            if (nx < 0 || nx >= w) continue
                            val nIdx = ny * w + nx
                            if (visited[nIdx] || binary[nIdx] != 1.toByte()) continue
                            visited[nIdx] = true
                            stackX[top] = nx; stackY[top] = ny
                            top++
                        }
                    }
                }
                if (points.size >= MIN_COMPONENT_POINTS) {
                    out.add(Component(points, minX, minY, maxX, maxY))
                }
            }
        }
        return out
    }

    // ── Convex hull (Andrew's monotone chain) ───────────────────────────────

    private fun convexHull(pointsIn: List<IntPoint>): List<IntPoint> {
        // Subsample huge clouds for speed; the hull is preserved by extremes only.
        val pts = if (pointsIn.size > MAX_HULL_INPUT) {
            val step = pointsIn.size / MAX_HULL_INPUT
            pointsIn.filterIndexed { i, _ -> i % step == 0 }
        } else pointsIn

        val sorted = pts.sortedWith(compareBy({ it.x }, { it.y }))
        if (sorted.size < 3) return sorted

        val lower = ArrayList<IntPoint>()
        for (p in sorted) {
            while (lower.size >= 2 && cross(lower[lower.size - 2], lower.last(), p) <= 0) {
                lower.removeAt(lower.size - 1)
            }
            lower.add(p)
        }
        val upper = ArrayList<IntPoint>()
        for (i in sorted.indices.reversed()) {
            val p = sorted[i]
            while (upper.size >= 2 && cross(upper[upper.size - 2], upper.last(), p) <= 0) {
                upper.removeAt(upper.size - 1)
            }
            upper.add(p)
        }
        lower.removeAt(lower.size - 1)
        upper.removeAt(upper.size - 1)
        return lower + upper
    }

    private fun cross(o: IntPoint, a: IntPoint, b: IntPoint): Long {
        return (a.x - o.x).toLong() * (b.y - o.y) - (a.y - o.y).toLong() * (b.x - o.x)
    }

    // ── Reduce hull to 4 corners ────────────────────────────────────────────

    /**
     * Pick the 4 vertices of the hull that maximise the enclosed area.
     * This is the classic "largest inscribed quadrilateral" — for convex hulls
     * an exact O(n^2) DP exists, but a greedy farthest-point strategy is
     * both fast and produces excellent results for document hulls.
     */
    private fun simplifyToQuad(hull: List<IntPoint>): List<IntPoint>? {
        if (hull.size < 4) return null
        if (hull.size == 4) return hull

        // Start with extreme points (min-x, max-x, min-y, max-y indices into hull)
        var iMinX = 0; var iMaxX = 0; var iMinY = 0; var iMaxY = 0
        for (i in hull.indices) {
            val p = hull[i]
            if (p.x < hull[iMinX].x) iMinX = i
            if (p.x > hull[iMaxX].x) iMaxX = i
            if (p.y < hull[iMinY].y) iMinY = i
            if (p.y > hull[iMaxY].y) iMaxY = i
        }
        val seed = sortedSetOf(iMinX, iMaxX, iMinY, iMaxY).toMutableList()
        if (seed.size < 4) {
            // Degenerate (e.g. axis-aligned bar) — pad from the hull head.
            var i = 0
            while (seed.size < 4 && i < hull.size) {
                if (i !in seed) seed.add(i)
                i++
            }
        }
        if (seed.size < 4) return null

        // Local refinement: for each of the 4 indices try replacing with each
        // hull vertex and accept the change that grows the area. Iterate to a
        // fixed point or for a small bounded number of passes.
        val cur = seed.subList(0, 4).toMutableList()
        var bestArea = polygonArea(cur.map { hull[it] })
        var improved = true
        var passes = 0
        while (improved && passes < 8) {
            improved = false
            passes++
            for (slot in 0..3) {
                val original = cur[slot]
                for (candidateIdx in hull.indices) {
                    if (candidateIdx in cur) continue
                    cur[slot] = candidateIdx
                    val area = polygonArea(cur.sortedBy { it }.map { hull[it] })
                    if (area > bestArea + 0.5f) {
                        bestArea = area
                        improved = true
                    } else {
                        cur[slot] = original
                    }
                }
            }
        }
        return cur.sortedBy { it }.map { hull[it] }
    }

    private fun polygonArea(points: List<IntPoint>): Float {
        if (points.size < 3) return 0f
        var area = 0L
        for (i in points.indices) {
            val j = (i + 1) % points.size
            area += points[i].x.toLong() * points[j].y - points[j].x.toLong() * points[i].y
        }
        return abs(area).toFloat() / 2f
    }

    // ── Order corners TL/TR/BR/BL ────────────────────────────────────────────

    private fun orderedQuad(pts: List<IntPoint>): Quad {
        require(pts.size == 4)
        val cx = pts.sumOf { it.x.toDouble() } / 4.0
        val cy = pts.sumOf { it.y.toDouble() } / 4.0
        var tl = pts[0]; var tr = pts[0]; var br = pts[0]; var bl = pts[0]
        for (p in pts) {
            val isLeft = p.x < cx
            val isTop = p.y < cy
            when {
                isLeft && isTop -> tl = p
                !isLeft && isTop -> tr = p
                !isLeft && !isTop -> br = p
                else -> bl = p
            }
        }
        return Quad(
            topLeft = Point2f(tl.x.toFloat(), tl.y.toFloat()),
            topRight = Point2f(tr.x.toFloat(), tr.y.toFloat()),
            bottomRight = Point2f(br.x.toFloat(), br.y.toFloat()),
            bottomLeft = Point2f(bl.x.toFloat(), bl.y.toFloat()),
        )
    }

    // ── Scoring ──────────────────────────────────────────────────────────────

    /** Public so callers can read the confidence the detector chose. */
    data class Scored(val quad: Quad, val score: Float)

    /**
     * Weighted multi-criterion score — mirrors [OpenCvBridge.scoreQuad] so the
     * fallback path produces the same ranking as the native path.
     *
     *   area(0.35) + convexity(0.15) + rectangularity(0.25) + aspect(0.10)
     *   + position(0.10) + topmost(0.05)
     *
     * A small edge-strength bonus is folded into convexity since gradient
     * alignment along a quad's sides is a strong "is this actually an edge"
     * signal that OpenCV's native path gets implicitly from Canny+contours.
     */
    private fun scoreQuad(quad: Quad, w: Int, h: Int, gradient: IntArray): Float? {
        val imageArea = w.toFloat() * h.toFloat()
        val area = quad.area()
        val areaFraction = area / imageArea
        if (areaFraction < MIN_QUAD_AREA_FRACTION || areaFraction > MAX_QUAD_AREA_FRACTION) return null
        if (!isConvex(quad)) return null

        val sides = listOf(
            quad.topLeft to quad.topRight,
            quad.topRight to quad.bottomRight,
            quad.bottomRight to quad.bottomLeft,
            quad.bottomLeft to quad.topLeft,
        )
        val s1 = distance(sides[0])
        val s2 = distance(sides[1])
        val s3 = distance(sides[2])
        val s4 = distance(sides[3])
        val shortest = minOf(s1, s2, s3, s4)
        val longest = maxOf(s1, s2, s3, s4)
        if (shortest <= 0f) return null
        if (longest / shortest > MAX_ASPECT_RATIO) return null

        // Aspect: average top/bottom width over average left/right height.
        val width = (s1 + s3) / 2f
        val height = (s2 + s4) / 2f
        val aspect = width / height
        if (aspect < MIN_ASPECT || aspect > MAX_ASPECT) return null
        val aspectScore = 1f - (abs(aspect - 1f) / max(MAX_ASPECT - 1f, 1f - MIN_ASPECT)).coerceIn(0f, 1f)

        // Rectangularity: how close are the 4 corner angles to 90°?
        val pts = listOf(quad.topLeft, quad.topRight, quad.bottomRight, quad.bottomLeft)
        var devSum = 0f
        for (i in 0..3) {
            val a = pts[(i + 3) % 4]
            val b = pts[i]
            val c = pts[(i + 1) % 4]
            devSum += abs(cornerAngleDeg(a, b, c) - 90f)
        }
        val rectangularity = (1f - (devSum / 4f) / 90f).coerceIn(0f, 1f)

        // Edge-strength bonus folded into convexity slot. Strong gradient along
        // the quad's sides == this really is an edge in the image.
        val edgeStrength = sides.sumOf {
            sampleEdgeStrength(it.first, it.second, gradient, w, h).toDouble()
        } / 4.0
        val convexity = (edgeStrength.toFloat() / 255f).coerceIn(0f, 1f)

        // Position: centroid distance from frame centre.
        val cx = pts.sumOf { it.x.toDouble() } / 4.0
        val cy = pts.sumOf { it.y.toDouble() } / 4.0
        val frameDiag = sqrt(w.toDouble() * w + h.toDouble() * h).toFloat()
        val dCenter = sqrt(((cx - w / 2.0) * (cx - w / 2.0) + (cy - h / 2.0) * (cy - h / 2.0))).toFloat()
        val positionScore = (1f - dCenter / (frameDiag / 2f)).coerceIn(0f, 1f)

        // Topmost: prefer the document with the smallest centroid y (highest in frame).
        val topmostScore = (1f - cy.toFloat() / h).coerceIn(0f, 1f)

        return (
            areaFraction * 0.35f +
                convexity * 0.15f +
                rectangularity * 0.25f +
                aspectScore * 0.10f +
                positionScore * 0.10f +
                topmostScore * 0.05f
            )
    }

    private fun cornerAngleDeg(a: Point2f, b: Point2f, c: Point2f): Float {
        val abx = (a.x - b.x).toDouble()
        val aby = (a.y - b.y).toDouble()
        val cbx = (c.x - b.x).toDouble()
        val cby = (c.y - b.y).toDouble()
        val dot = abx * cbx + aby * cby
        val magAB = sqrt(abx * abx + aby * aby)
        val magCB = sqrt(cbx * cbx + cby * cby)
        if (magAB == 0.0 || magCB == 0.0) return 0f
        val cos = (dot / (magAB * magCB)).coerceIn(-1.0, 1.0)
        return Math.toDegrees(kotlin.math.acos(cos)).toFloat()
    }

    private fun isConvex(q: Quad): Boolean {
        val pts = listOf(q.topLeft, q.topRight, q.bottomRight, q.bottomLeft)
        var sign = 0
        for (i in 0..3) {
            val a = pts[i]
            val b = pts[(i + 1) % 4]
            val c = pts[(i + 2) % 4]
            val crossZ = (b.x - a.x) * (c.y - b.y) - (b.y - a.y) * (c.x - b.x)
            val s = if (crossZ > 0f) 1 else if (crossZ < 0f) -1 else 0
            if (s == 0) continue
            if (sign == 0) sign = s else if (sign != s) return false
        }
        return true
    }

    private fun distance(pair: Pair<Point2f, Point2f>): Float {
        val dx = pair.second.x - pair.first.x
        val dy = pair.second.y - pair.first.y
        return sqrt(dx * dx + dy * dy)
    }

    private fun sampleEdgeStrength(a: Point2f, b: Point2f, gradient: IntArray, w: Int, h: Int): Float {
        val steps = 24
        var sum = 0
        var n = 0
        for (i in 0..steps) {
            val t = i.toFloat() / steps
            val x = (a.x + (b.x - a.x) * t).toInt().coerceIn(0, w - 1)
            val y = (a.y + (b.y - a.y) * t).toInt().coerceIn(0, h - 1)
            sum += gradient[y * w + x]
            n++
        }
        return if (n == 0) 0f else sum.toFloat() / n
    }

    // ── Constants ────────────────────────────────────────────────────────────

    private const val MIN_DIMENSION = 16
    private const val MIN_EDGE_THRESHOLD = 32
    private const val MIN_COMPONENT_POINTS = 32
    private const val MIN_COMPONENT_AREA_FRACTION = 0.03f
    private const val MAX_CANDIDATE_COMPONENTS = 6
    private const val MAX_HULL_INPUT = 2048
    private const val MIN_QUAD_AREA_FRACTION = 0.05f
    private const val MAX_QUAD_AREA_FRACTION = 0.98f
    private const val MAX_ASPECT_RATIO = 12f
    private const val MIN_ASPECT = 0.3f
    private const val MAX_ASPECT = 3.5f
}

