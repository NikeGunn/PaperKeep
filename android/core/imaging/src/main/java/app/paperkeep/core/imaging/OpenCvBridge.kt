package app.paperkeep.core.imaging

import android.graphics.Bitmap
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.core.TermCriteria
import org.opencv.imgproc.CLAHE
import org.opencv.imgproc.Imgproc
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Thin wrapper around OpenCV native operations used by Magic Scan.
 *
 * All entry points require OpenCV native libs to be initialised — only call
 * after [org.opencv.android.OpenCVLoader.initLocal] (or .initDebug) has
 * returned true and [OpenCvEdgeDetector.markLoaded] has been invoked.
 *
 * Each call wraps native work in a try/finally that releases Mats so we never
 * leak native memory.
 *
 * Detection pipeline (CamScanner-grade):
 *   1. Bitmap → gray, downsample to <=640px longest side.
 *   2. Bilateral filter (preserves edges, kills noise — beats Gaussian for white-on-white).
 *   3. CLAHE on the bilateral output for local contrast (clipLimit=3.0, tile=8).
 *   4. Otsu on the blurred image → auto-Canny thresholds (otsu*0.5 .. otsu*1.5).
 *   5. Adaptive threshold (mean) as a second edge channel.
 *   6. Combined edge map = Canny OR adaptive-threshold (catches edges either channel misses).
 *   7. findContours + approxPolyDP(0.02*peri) → keep only convex 4-vertex polys with
 *      area ≥ 15% of frame area.
 *   8. Score each candidate by weighted: area(0.35) + convexity(0.15) +
 *      rectangularity(0.25) + aspect(0.10) + center-position(0.10) + topmost(0.05).
 *   9. If no quad scores above threshold, dilate (3x3, 2 iters) and retry.
 *  10. If still nothing, retry on a 50%-downscaled version.
 *  11. Refine the winning quad's corners with cornerSubPix for sub-pixel accuracy.
 */
internal object OpenCvBridge {

    /** Result of a detection attempt — the quad plus a normalised confidence in [0,1]. */
    data class Detection(val quad: Quad, val confidence: Float)

    fun detectQuad(bitmap: Bitmap): Detection? = detectQuad(bitmap, tapX = null, tapY = null)

    /**
     * Detect a document quad. When [tapX]/[tapY] are non-null, the result is
     * the quad whose interior contains that point (in [bitmap] pixel coords).
     * Among candidates that contain the tap, the highest-quality one wins.
     * If no candidate contains the tap, the candidate whose centroid is
     * closest to the tap is returned (gives users a forgiving "near enough"
     * experience when their tap landed just outside the page).
     */
    fun detectQuad(bitmap: Bitmap, tapX: Float?, tapY: Float?): Detection? {
        val srcRgba = Mat()
        try {
            Utils.bitmapToMat(bitmap, srcRgba)
            val gray = Mat()
            Imgproc.cvtColor(srcRgba, gray, Imgproc.COLOR_RGBA2GRAY)

            // Downsample for speed. Geometry is restored via scale at the end.
            val maxEdge = max(gray.cols(), gray.rows())
            val analysisScale = if (maxEdge > ANALYSIS_MAX_EDGE) {
                ANALYSIS_MAX_EDGE.toDouble() / maxEdge.toDouble()
            } else 1.0
            if (analysisScale < 1.0) {
                val target = Size(gray.cols() * analysisScale, gray.rows() * analysisScale)
                Imgproc.resize(gray, gray, target, 0.0, 0.0, Imgproc.INTER_AREA)
            }

            // Map the user's tap from bitmap pixels into analysis-resolution pixels.
            val tap: Point? = if (tapX != null && tapY != null) {
                Point(tapX.toDouble() * analysisScale, tapY.toDouble() * analysisScale)
            } else null

            // Try at full analysis resolution; if nothing, try with extra dilation;
            // if still nothing, retry on a 50% downscaled copy.
            findBestQuad(gray, dilateExtra = false, tap = tap)?.let {
                return scaleDetection(it, gray, srcRgba, 1.0 / analysisScale)
            }
            findBestQuad(gray, dilateExtra = true, tap = tap)?.let {
                return scaleDetection(it, gray, srcRgba, 1.0 / analysisScale)
            }
            // Tap-specific local ROI fallback: when the global pipeline returns
            // no quad containing the tap, crop a generous region centred on
            // the tap and run the detector on just that ROI. This catches
            // documents whose global contour was masked by stronger edges
            // elsewhere in the frame (the "notebook in the middle, cable in
            // the corner wins" bug). Inside an ROI all contours are local, so
            // whatever the user pointed at is the dominant candidate.
            if (tap != null) {
                val roiQuad = detectInRoi(gray, tap)
                if (roiQuad != null) {
                    return scaleDetection(roiQuad, gray, srcRgba, 1.0 / analysisScale)
                }
            }
            // Last-resort: downscale further (catches very-close documents that bleed off-frame).
            // Only meaningful for the no-tap path — taps already had their ROI retry above.
            if (tap == null) {
                val small = Mat()
                try {
                    Imgproc.resize(gray, small, Size(gray.cols() * 0.5, gray.rows() * 0.5), 0.0, 0.0, Imgproc.INTER_AREA)
                    findBestQuad(small, dilateExtra = true, tap = null)?.let {
                        val rescaled = ScoredQuad(
                            pts = it.pts.map { p -> Point(p.x * 2.0, p.y * 2.0) }.toTypedArray(),
                            score = it.score,
                            rectangularity = it.rectangularity,
                        )
                        return scaleDetection(rescaled, gray, srcRgba, 1.0 / analysisScale)
                    }
                } finally {
                    small.release()
                }
            }

            return null
        } catch (_: Throwable) {
            return null
        } finally {
            srcRgba.release()
        }
    }

    private data class ScoredQuad(
        val pts: Array<Point>,
        val score: Float,
        val rectangularity: Float,
    )

    /**
     * Run the full edge → contour → scoring pipeline on the supplied gray Mat.
     * @return the highest-scoring quad in [gray]'s coordinate space, or null.
     */
    private fun findBestQuad(gray: Mat, dilateExtra: Boolean, tap: Point? = null): ScoredQuad? {
        val bilateral = Mat()
        val claheOut = Mat()
        val canny = Mat()
        val adaptive = Mat()
        val edges = Mat()
        val dilated = Mat()
        val hierarchy = Mat()
        val contours = ArrayList<MatOfPoint>()
        var clahe: CLAHE? = null
        try {
            // Bilateral preserves document edges while smoothing texture — the key
            // improvement over Gaussian when paper and background have similar luma.
            Imgproc.bilateralFilter(gray, bilateral, 9, 75.0, 75.0)

            // CLAHE boosts local contrast so faint paper-on-paper edges become visible.
            clahe = Imgproc.createCLAHE(3.0, Size(8.0, 8.0))
            clahe.apply(bilateral, claheOut)

            // Auto-tune Canny via Otsu — self-calibrates per lighting condition.
            val otsuMat = Mat()
            val otsu = try {
                Imgproc.threshold(claheOut, otsuMat, 0.0, 255.0, Imgproc.THRESH_BINARY or Imgproc.THRESH_OTSU)
            } finally { otsuMat.release() }
            val low = (otsu * 0.5).coerceAtLeast(10.0)
            val high = (otsu * 1.5).coerceAtLeast(30.0)
            Imgproc.Canny(claheOut, canny, low, high)

            // Second edge channel: adaptive mean threshold. Catches soft document
            // edges that Canny misses (low-contrast paper on light desk).
            Imgproc.adaptiveThreshold(
                claheOut, adaptive, 255.0,
                Imgproc.ADAPTIVE_THRESH_MEAN_C,
                Imgproc.THRESH_BINARY_INV,
                15, 8.0,
            )

            // Merge both edge maps. OR = "edge if either channel saw it".
            Core.bitwise_or(canny, adaptive, edges)

            // Dilate so dashed edges connect into closed contours. dilateExtra=true is
            // used on a retry pass when the first attempt found nothing.
            val iters = if (dilateExtra) 2 else 1
            Imgproc.dilate(
                edges,
                dilated,
                Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0)),
                Point(-1.0, -1.0),
                iters,
            )

            // RETR_EXTERNAL first — only outermost contours. This is the key fix
            // for "detector found the printed sub-rectangle / inner copy" bugs:
            // when both an outer document and an inner crisp printed block are
            // visible, RETR_LIST happily ranks the inner one because its edges
            // are sharper. RETR_EXTERNAL excludes nested contours entirely.
            Imgproc.findContours(
                dilated, contours, hierarchy,
                Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE,
            )

            val frameW = gray.cols()
            val frameH = gray.rows()
            val frameArea = frameW.toDouble() * frameH.toDouble()
            val frameCx = frameW / 2.0
            val frameCy = frameH / 2.0
            val frameDiag = sqrt(frameW.toDouble() * frameW + frameH.toDouble() * frameH)

            // When the user has tapped, drop the area floor (the tap is explicit
            // intent — even a small phone-receipt in-frame is fair game) AND
            // start with RETR_LIST so we catch inner contours too; the smallest
            // quad containing the tap will be picked.
            val areaFloor = if (tap != null) MIN_AREA_FRACTION_TAPPED else MIN_AREA_FRACTION
            if (tap != null) {
                contours.forEach { it.release() }
                contours.clear()
                Imgproc.findContours(
                    dilated, contours, hierarchy,
                    Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE,
                )
            }
            var candidates = collectCandidates(contours, dilated, frameArea, frameCx, frameCy, frameDiag, areaFloor)

            // If the external pass found nothing usable, retry with RETR_LIST so we
            // still catch documents whose outer edges didn't form a closed contour
            // (e.g. one side ran off-frame). Inner sub-rectangle ambiguity is
            // resolved later by the outer-containment preference.
            if (candidates.isEmpty() && tap == null) {
                contours.forEach { it.release() }
                contours.clear()
                Imgproc.findContours(
                    dilated, contours, hierarchy,
                    Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE,
                )
                candidates = collectCandidates(contours, dilated, frameArea, frameCx, frameCy, frameDiag, areaFloor)
            }

            if (candidates.isEmpty()) return null

            // If the user tapped, only consider quads that contain (or come
            // closest to) the tap point. This is the brain behind tap-to-detect:
            // "I'm pointing at THIS document, not whatever has the biggest
            // edges in the frame." Falls back to plain ranking when tap is null.
            val best = if (tap != null) {
                pickBestForTap(candidates, tap)
            } else {
                pickBestPreferringOuter(candidates)
            } ?: return null
            if (best.score < MIN_ACCEPT_SCORE) return null

            // Sub-pixel refine the corners against the CLAHE-enhanced gray for tight crops.
            val refined = cornerSubPix(claheOut, best.pts)
            return ScoredQuad(refined, best.score, best.rectangularity)
        } catch (_: Throwable) {
            return null
        } finally {
            bilateral.release()
            claheOut.release()
            canny.release()
            adaptive.release()
            edges.release()
            dilated.release()
            hierarchy.release()
            contours.forEach { it.release() }
        }
    }

    /**
     * Walk the contour list, approxPolyDP each to 4 corners, score, return list.
     * Edge-strength is measured against [edges] (the merged Canny+adaptive map)
     * so a quad's score reflects how much of its perimeter actually sits on a
     * real image edge — kills phantom rectangles drawn through empty space.
     */
    private fun collectCandidates(
        contours: List<MatOfPoint>,
        edges: Mat,
        frameArea: Double,
        frameCx: Double,
        frameCy: Double,
        frameDiag: Double,
        areaFloor: Double = MIN_AREA_FRACTION,
    ): ArrayList<ScoredQuad> {
        val sortedContours = contours.sortedByDescending { Imgproc.contourArea(it) }.take(12)
        val out = ArrayList<ScoredQuad>()
        for (contour in sortedContours) {
            val contour2f = MatOfPoint2f(*contour.toArray())
            try {
                val perimeter = Imgproc.arcLength(contour2f, true)
                if (perimeter <= 0) continue
                val approx = MatOfPoint2f()
                try {
                    // Try a couple of epsilons — a single 2% can miss documents
                    // with rounded or noisy corners that need a looser fit.
                    for (epsFrac in doubleArrayOf(0.02, 0.03, 0.04)) {
                        Imgproc.approxPolyDP(contour2f, approx, epsFrac * perimeter, true)
                        val pts = approx.toArray()
                        if (pts.size != 4) continue
                        val matPts = MatOfPoint(*pts)
                        try {
                            if (!Imgproc.isContourConvex(matPts)) continue
                            val area = Imgproc.contourArea(matPts)
                            val frac = area / frameArea
                            if (frac < areaFloor || frac > 0.99) continue
                            val scored = scoreQuad(
                                pts, frac, frameCx, frameCy, frameDiag, edges,
                            ) ?: continue
                            out.add(scored)
                            break
                        } finally {
                            matPts.release()
                        }
                    }
                } finally {
                    approx.release()
                }
            } finally {
                contour2f.release()
            }
        }
        return out
    }

    /**
     * Pick the winning quad. When a high-scoring small quad is geometrically
     * contained inside a lower-scoring larger quad (the classic "I printed
     * inside a page" case), the larger one wins — that is almost always the
     * document the user actually wants.
     */
    private fun pickBestPreferringOuter(candidates: List<ScoredQuad>): ScoredQuad? {
        if (candidates.isEmpty()) return null
        // Sort by area descending — larger first.
        val byArea = candidates.sortedByDescending { polygonArea(it.pts) }
        // The default winner is the highest scorer.
        var winner = candidates.maxBy { it.score }
        val winnerArea = polygonArea(winner.pts)
        // Look for a larger quad that contains the current winner and is still a
        // plausible document (passes its own scoring gates, which it did by being
        // in `candidates`). Prefer it.
        for (cand in byArea) {
            val candArea = polygonArea(cand.pts)
            if (candArea <= winnerArea * 1.1) break // nothing meaningfully larger left
            if (quadContains(cand.pts, winner.pts)) {
                winner = cand
                break
            }
        }
        return winner
    }

    /**
     * Run the detection pipeline on a region of interest centred on [tap].
     * The ROI is sized so even documents that fill the frame fit inside it
     * (with margin). Returns the winning quad re-mapped back into [gray]'s
     * coordinate space, or null when the ROI itself yields no candidate.
     *
     * This is the "user is right" failsafe: when global contour ranking
     * doesn't find a quad over the tap, we restrict the search to a
     * neighbourhood of the tap so external strong edges can't outvote it.
     */
    private fun detectInRoi(gray: Mat, tap: Point): ScoredQuad? {
        val cols = gray.cols()
        val rows = gray.rows()
        // ROI = a square centred on tap, side = 70% of the shorter edge of
        // the analysis frame. Comfortably covers a hand-held page; small
        // enough to exclude most of the rest of the scene.
        val side = (minOf(cols, rows) * 0.70).toInt().coerceAtLeast(64)
        val half = side / 2
        var x0 = (tap.x.toInt() - half).coerceAtLeast(0)
        var y0 = (tap.y.toInt() - half).coerceAtLeast(0)
        var x1 = (x0 + side).coerceAtMost(cols)
        var y1 = (y0 + side).coerceAtMost(rows)
        // Re-clamp if hitting the right/bottom edge shrank the box.
        x0 = (x1 - side).coerceAtLeast(0)
        y0 = (y1 - side).coerceAtLeast(0)
        val roiW = x1 - x0
        val roiH = y1 - y0
        if (roiW <= 16 || roiH <= 16) return null

        val roi = Mat(gray, org.opencv.core.Rect(x0, y0, roiW, roiH))
        try {
            // Tap coordinates relative to the ROI's origin.
            val tapLocal = Point(tap.x - x0, tap.y - y0)
            val local = findBestQuad(roi, dilateExtra = true, tap = tapLocal) ?: return null
            // Map ROI-local pixels back into the full analysis frame.
            val remapped = local.pts.map { p -> Point(p.x + x0, p.y + y0) }.toTypedArray()
            return ScoredQuad(remapped, local.score, local.rectangularity)
        } finally {
            roi.release()
        }
    }

    /**
     * Tap-anchored winner selection. STRICT semantics — when the user has
     * pointed at something, the detector either returns a quad whose interior
     * contains the tap, or returns null. We never "guess" a nearby quad: that
     * was the source of the "tapped the notebook, got a cable-edge in the
     * corner" bug — the detector confidently returned a wrong region.
     *
     * Tolerance band: a tap within [TAP_TOLERANCE_PX_ANALYSIS] of a quad's
     * edge still counts as "contained." Lets the user tap near the document
     * border without strict pixel-perfect aim.
     *
     * Among quads that contain (or nearly contain) the tap, the **smallest**
     * wins — the most specific match. When both a small document and a large
     * surface contain the tap, the user almost always means the document.
     */
    private fun pickBestForTap(candidates: List<ScoredQuad>, tap: Point): ScoredQuad? {
        if (candidates.isEmpty()) return null
        val containing = candidates.filter { containsPointWithTolerance(it.pts, tap, TAP_TOLERANCE_PX_ANALYSIS) }
        if (containing.isEmpty()) return null
        return containing.minBy { polygonArea(it.pts) }
    }

    /**
     * True if [pt] lies inside [poly], or within [toleranceAnalysisPx] of its
     * boundary. Tolerance is measured in analysis-resolution pixels (the
     * downsampled space where contours live), so it's consistent across input
     * resolutions.
     */
    private fun containsPointWithTolerance(poly: Array<Point>, pt: Point, toleranceAnalysisPx: Double): Boolean {
        val mat = MatOfPoint2f(*poly)
        try {
            // measureDist=true returns the signed distance: positive inside,
            // negative outside. -|d| < tolerance means "within tolerance of edge".
            val signedDist = Imgproc.pointPolygonTest(mat, pt, true)
            return signedDist >= -toleranceAnalysisPx
        } finally {
            mat.release()
        }
    }

    /** True if [pt] lies inside the convex polygon [poly] (inclusive on edges). */
    private fun containsPoint(poly: Array<Point>, pt: Point): Boolean {
        val mat = MatOfPoint2f(*poly)
        try {
            return Imgproc.pointPolygonTest(mat, pt, false) >= 0
        } finally {
            mat.release()
        }
    }

    /** True if quad [outer] geometrically contains all 4 corners of [inner]. */
    private fun quadContains(outer: Array<Point>, inner: Array<Point>): Boolean {
        val poly = MatOfPoint2f(*outer)
        try {
            for (p in inner) {
                // pointPolygonTest: >0 inside, =0 on edge, <0 outside.
                if (Imgproc.pointPolygonTest(poly, p, false) < 0) return false
            }
            return true
        } finally {
            poly.release()
        }
    }

    /**
     * Sample the edge map along each side of the quad. Returns the fraction of
     * sampled points whose edge-map value is non-zero. Close to 1.0 means the
     * quad's perimeter sits on real image edges; close to 0.0 means we drew a
     * rectangle through empty space.
     */
    private fun edgeSupport(pts: Array<Point>, edges: Mat): Double {
        val w = edges.cols()
        val h = edges.rows()
        if (w <= 0 || h <= 0) return 0.0
        var hits = 0
        var total = 0
        // Tolerance band: count a sample as "on edge" if any pixel in a 3x3
        // neighbourhood is non-zero. Compensates for sub-pixel quad placement.
        for (i in 0..3) {
            val a = pts[i]
            val b = pts[(i + 1) % 4]
            val steps = 40
            for (s in 0..steps) {
                val t = s.toDouble() / steps
                val x = (a.x + (b.x - a.x) * t).toInt()
                val y = (a.y + (b.y - a.y) * t).toInt()
                total++
                var hit = false
                outer@ for (dy in -1..1) {
                    val yy = y + dy
                    if (yy < 0 || yy >= h) continue
                    for (dx in -1..1) {
                        val xx = x + dx
                        if (xx < 0 || xx >= w) continue
                        if (edges.get(yy, xx)[0] != 0.0) { hit = true; break@outer }
                    }
                }
                if (hit) hits++
            }
        }
        return if (total == 0) 0.0 else hits.toDouble() / total
    }

    /**
     * Weighted multi-criterion scoring (rebalanced for "pick the biggest
     * plausible rectangle" semantics).
     *
     *   area(0.45)            — biggest plausible document wins. Was 0.35; the
     *                           lower weight let high-contrast printed sub-blocks
     *                           outscore the actual page.
     *   edgeSupport(0.20)     — fraction of perimeter sitting on real edges.
     *                           This is the new "is this actually an edge" gate
     *                           — replaces the broken polyArea/polyArea convexity.
     *   rectangularity(0.20)  — 1 - mean(|angle - 90°|) / 90°.
     *   aspect(0.075)         — penalises slivers (aspect outside 0.3..3.5 rejected).
     *   position(0.075)       — centroid near frame centre.
     *
     * topmost weight removed — it biased detection toward the upper of two
     * stacked papers, which is exactly the wrong default when the upper "paper"
     * is actually a printed sub-region.
     *
     * @return null if the quad fails a hard gate (aspect, rectangularity, edge).
     */
    private fun scoreQuad(
        pts: Array<Point>,
        areaFraction: Double,
        frameCx: Double,
        frameCy: Double,
        frameDiag: Double,
        edges: Mat,
    ): ScoredQuad? {
        // Order TL/TR/BR/BL so side lengths and angle measurements are well-defined.
        val ordered = orderPoints(pts)

        val s01 = distance(ordered[0], ordered[1])
        val s12 = distance(ordered[1], ordered[2])
        val s23 = distance(ordered[2], ordered[3])
        val s30 = distance(ordered[3], ordered[0])
        val shortest = min(min(s01, s12), min(s23, s30))
        if (shortest <= 1.0) return null

        // Aspect: width over height. A valid document is 0.3 .. 3.5.
        val width = (s01 + s23) / 2.0
        val height = (s12 + s30) / 2.0
        val aspect = width / height
        if (aspect < MIN_ASPECT || aspect > MAX_ASPECT) return null
        val aspectScore = 1.0 - (abs(aspect - 1.0) / max(MAX_ASPECT - 1.0, 1.0 - MIN_ASPECT)).coerceIn(0.0, 1.0)

        // Rectangularity: mean deviation of the 4 corner angles from 90°, normalised.
        var devSum = 0.0
        for (i in 0..3) {
            val a = ordered[(i + 3) % 4]
            val b = ordered[i]
            val c = ordered[(i + 1) % 4]
            devSum += abs(cornerAngleDeg(a, b, c) - 90.0)
        }
        val meanDev = devSum / 4.0
        val rectangularity = (1.0 - meanDev / 90.0).coerceIn(0.0, 1.0)
        if (rectangularity < MIN_RECTANGULARITY) return null

        // Edge support: how much of the quad's perimeter actually sits on a real
        // image edge. Hard floor MIN_EDGE_SUPPORT rejects phantom rectangles.
        val edgeSupport = edgeSupport(ordered, edges)
        if (edgeSupport < MIN_EDGE_SUPPORT) return null

        // Position: centroid proximity to frame centre.
        val cx = ordered.sumOf { it.x } / 4.0
        val cy = ordered.sumOf { it.y } / 4.0
        val dCenter = sqrt((cx - frameCx) * (cx - frameCx) + (cy - frameCy) * (cy - frameCy))
        val positionScore = (1.0 - (dCenter / (frameDiag / 2.0))).coerceIn(0.0, 1.0)

        val score = (
            areaFraction * 0.45 +
                edgeSupport * 0.20 +
                rectangularity * 0.20 +
                aspectScore * 0.075 +
                positionScore * 0.075
            ).toFloat()

        return ScoredQuad(ordered, score, rectangularity.toFloat())
    }

    private fun cornerSubPix(gray: Mat, pts: Array<Point>): Array<Point> {
        return try {
            val refineMat = MatOfPoint2f(*pts)
            try {
                Imgproc.cornerSubPix(
                    gray, refineMat, Size(5.0, 5.0), Size(-1.0, -1.0),
                    TermCriteria(TermCriteria.EPS + TermCriteria.COUNT, 30, 0.1),
                )
                refineMat.toArray()
            } finally {
                refineMat.release()
            }
        } catch (_: Throwable) {
            pts
        }
    }

    private fun orderPoints(pts: Array<Point>): Array<Point> {
        val cx = pts.sumOf { it.x } / 4.0
        val cy = pts.sumOf { it.y } / 4.0
        var tl = pts[0]; var tr = pts[0]; var br = pts[0]; var bl = pts[0]
        var hasTl = false; var hasTr = false; var hasBr = false; var hasBl = false
        for (p in pts) {
            val left = p.x < cx
            val top = p.y < cy
            when {
                left && top -> { tl = p; hasTl = true }
                !left && top -> { tr = p; hasTr = true }
                !left && !top -> { br = p; hasBr = true }
                else -> { bl = p; hasBl = true }
            }
        }
        // Degenerate split (rare) — fall back to angle-sort around centroid.
        if (!(hasTl && hasTr && hasBr && hasBl)) {
            val sorted = pts.sortedBy { kotlin.math.atan2(it.y - cy, it.x - cx) }
            return sorted.toTypedArray()
        }
        return arrayOf(tl, tr, br, bl)
    }

    private fun cornerAngleDeg(a: Point, b: Point, c: Point): Double {
        val abx = a.x - b.x
        val aby = a.y - b.y
        val cbx = c.x - b.x
        val cby = c.y - b.y
        val dot = abx * cbx + aby * cby
        val magAB = sqrt(abx * abx + aby * aby)
        val magCB = sqrt(cbx * cbx + cby * cby)
        if (magAB == 0.0 || magCB == 0.0) return 0.0
        val cos = (dot / (magAB * magCB)).coerceIn(-1.0, 1.0)
        return Math.toDegrees(acos(cos))
    }

    private fun distance(a: Point, b: Point): Double {
        val dx = a.x - b.x
        val dy = a.y - b.y
        return sqrt(dx * dx + dy * dy)
    }

    private fun polygonArea(pts: Array<Point>): Double {
        var area = 0.0
        val n = pts.size
        for (i in 0 until n) {
            val j = (i + 1) % n
            area += pts[i].x * pts[j].y - pts[j].x * pts[i].y
        }
        return abs(area) / 2.0
    }

    /** Map ordered analysis-space points back to bitmap pixels and emit a Detection. */
    private fun scaleDetection(
        sq: ScoredQuad,
        @Suppress("UNUSED_PARAMETER") gray: Mat,
        @Suppress("UNUSED_PARAMETER") srcRgba: Mat,
        scaleBack: Double,
    ): Detection {
        val pts = sq.pts
        fun map(p: Point) = Point2f((p.x * scaleBack).toFloat(), (p.y * scaleBack).toFloat())
        val quad = Quad(
            topLeft = map(pts[0]),
            topRight = map(pts[1]),
            bottomRight = map(pts[2]),
            bottomLeft = map(pts[3]),
        )
        return Detection(quad, sq.score.coerceIn(0f, 1f))
    }

    // ── Perspective warp ────────────────────────────────────────────────────

    fun warp(bitmap: Bitmap, quad: Quad, outWidth: Int, outHeight: Int): Bitmap? {
        val src = Mat()
        val dst = Mat()
        var srcPts: MatOfPoint2f? = null
        var dstPts: MatOfPoint2f? = null
        var transform: Mat? = null
        try {
            Utils.bitmapToMat(bitmap, src)
            srcPts = MatOfPoint2f(
                Point(quad.topLeft.x.toDouble(), quad.topLeft.y.toDouble()),
                Point(quad.topRight.x.toDouble(), quad.topRight.y.toDouble()),
                Point(quad.bottomRight.x.toDouble(), quad.bottomRight.y.toDouble()),
                Point(quad.bottomLeft.x.toDouble(), quad.bottomLeft.y.toDouble()),
            )
            dstPts = MatOfPoint2f(
                Point(0.0, 0.0),
                Point(outWidth.toDouble() - 1, 0.0),
                Point(outWidth.toDouble() - 1, outHeight.toDouble() - 1),
                Point(0.0, outHeight.toDouble() - 1),
            )
            transform = Imgproc.getPerspectiveTransform(srcPts, dstPts)
            Imgproc.warpPerspective(
                src, dst, transform,
                Size(outWidth.toDouble(), outHeight.toDouble()),
                Imgproc.INTER_LINEAR, Core.BORDER_REPLICATE, Scalar(0.0),
            )
            val out = Bitmap.createBitmap(outWidth, outHeight, Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(dst, out)
            return out
        } catch (_: Throwable) {
            return null
        } finally {
            src.release()
            dst.release()
            srcPts?.release()
            dstPts?.release()
            transform?.release()
        }
    }

    // ── Shadow removal / illumination normalisation ─────────────────────────

    /**
     * Remove uneven illumination (shadows, vignetting, hand-shadow over the
     * paper) so the document reads with a uniform bright background.
     *
     * Algorithm — the same one CamScanner and Adobe Scan use:
     *   1. Convert RGB → LAB; we only modify the L (lightness) channel.
     *   2. Estimate the illumination map: large morphological closing on L
     *      (kernel ≈ short-edge / 15) followed by a heavy Gaussian blur. The
     *      result approximates "what would L look like if there were no ink,
     *      just paper" — i.e. it captures the shadow and vignette pattern.
     *   3. Divide L by the illumination map and rescale to a clean
     *      paper-white target. Pixels darker than their local illumination
     *      (ink, lines) stay dark; pixels at illumination level become bright
     *      white regardless of where they sat in the original shadow.
     *   4. Merge channels back and convert to RGB.
     *
     * Returns null if OpenCV throws or the bitmap is degenerate.
     */
    fun removeShadow(bitmap: Bitmap): Bitmap? {
        if (bitmap.width < 8 || bitmap.height < 8) return null
        val src = Mat()
        val lab = Mat()
        val lChan = Mat()
        val aChan = Mat()
        val bChan = Mat()
        val lFloat = Mat()
        val illumination = Mat()
        val normalized = Mat()
        val outMat = Mat()
        val merged = Mat()
        try {
            Utils.bitmapToMat(bitmap, src)
            Imgproc.cvtColor(src, src, Imgproc.COLOR_RGBA2RGB)
            Imgproc.cvtColor(src, lab, Imgproc.COLOR_RGB2Lab)

            val channels = ArrayList<Mat>()
            Core.split(lab, channels)
            channels[0].copyTo(lChan)
            channels[1].copyTo(aChan)
            channels[2].copyTo(bChan)
            channels.forEach { it.release() }

            // Build illumination estimate from a closed-then-blurred L channel.
            val shortEdge = min(lChan.cols(), lChan.rows())
            val kernelSize = (shortEdge / 15).coerceAtLeast(15).let { if (it % 2 == 0) it + 1 else it }
            val structuring = Imgproc.getStructuringElement(
                Imgproc.MORPH_ELLIPSE,
                Size(kernelSize.toDouble(), kernelSize.toDouble()),
            )
            try {
                // MORPH_CLOSE = dilate-then-erode. Removes dark "holes" (ink/text),
                // leaving the smooth background illumination.
                Imgproc.morphologyEx(lChan, illumination, Imgproc.MORPH_CLOSE, structuring)
                Imgproc.GaussianBlur(
                    illumination, illumination,
                    Size(kernelSize.toDouble(), kernelSize.toDouble()),
                    0.0,
                )
            } finally {
                structuring.release()
            }

            // Normalize: L_out = clip(L * (target / illumination)). target = 235
            // gives clean paper-white without blowing out highlights.
            val target = 235.0
            lChan.convertTo(lFloat, CvType.CV_32F)
            val illumF = Mat()
            try {
                illumination.convertTo(illumF, CvType.CV_32F)
                // Avoid div-by-zero; floor illumination at 1.0.
                Core.max(illumF, Scalar(1.0), illumF)
                Core.divide(target, illumF, illumF)
                // illumF now holds the per-pixel gain (≈ target/illum).
                Core.multiply(lFloat, illumF, lFloat)
            } finally {
                illumF.release()
            }
            lFloat.convertTo(normalized, CvType.CV_8U)

            val newChannels = listOf(normalized, aChan, bChan)
            Core.merge(newChannels, merged)
            Imgproc.cvtColor(merged, outMat, Imgproc.COLOR_Lab2RGB)
            Imgproc.cvtColor(outMat, outMat, Imgproc.COLOR_RGB2RGBA)

            val out = Bitmap.createBitmap(outMat.cols(), outMat.rows(), Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(outMat, out)
            return out
        } catch (_: Throwable) {
            return null
        } finally {
            src.release()
            lab.release()
            lChan.release()
            aChan.release()
            bChan.release()
            lFloat.release()
            illumination.release()
            normalized.release()
            outMat.release()
            merged.release()
        }
    }

    // ── Magic-Color enhancement ─────────────────────────────────────────────

    fun magicColor(bitmap: Bitmap): Bitmap? {
        val src = Mat()
        val lab = Mat()
        val merged = Mat()
        val outMat = Mat()
        val lChan = Mat()
        val aChan = Mat()
        val bChan = Mat()
        var clahe: CLAHE? = null
        try {
            Utils.bitmapToMat(bitmap, src)
            Imgproc.cvtColor(src, src, Imgproc.COLOR_RGBA2RGB)
            Imgproc.cvtColor(src, lab, Imgproc.COLOR_RGB2Lab)
            val channels = ArrayList<Mat>()
            Core.split(lab, channels)
            channels[0].copyTo(lChan)
            channels[1].copyTo(aChan)
            channels[2].copyTo(bChan)
            channels.forEach { it.release() }

            clahe = Imgproc.createCLAHE(2.5, Size(8.0, 8.0))
            clahe.apply(lChan, lChan)

            val newChannels = listOf(lChan, aChan, bChan)
            Core.merge(newChannels, merged)
            Imgproc.cvtColor(merged, outMat, Imgproc.COLOR_Lab2RGB)

            outMat.convertTo(outMat, CvType.CV_8UC3, 1.05, 6.0)
            Imgproc.cvtColor(outMat, outMat, Imgproc.COLOR_RGB2RGBA)

            val out = Bitmap.createBitmap(outMat.cols(), outMat.rows(), Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(outMat, out)
            return out
        } catch (_: Throwable) {
            return null
        } finally {
            src.release()
            lab.release()
            merged.release()
            outMat.release()
            lChan.release()
            aChan.release()
            bChan.release()
        }
    }

    private const val ANALYSIS_MAX_EDGE = 640
    // 0.08 (was 0.15) — accepts smaller-in-frame documents (held farther away or
    // angled). The outer-contains-inner preference + edge-support gate prevent
    // the small-rectangle false positives the old floor was protecting against.
    private const val MIN_AREA_FRACTION = 0.08
    // When the user has tapped, we trust their intent and accept much smaller
    // quads — a phone-screen-sized note in a wide frame is still scannable.
    private const val MIN_AREA_FRACTION_TAPPED = 0.02
    // ~4% of analysis edge (~25px on a 640px edge). Generous enough to forgive
    // imprecise taps near the document border, tight enough to reject taps
    // that landed in totally different image regions.
    private const val TAP_TOLERANCE_PX_ANALYSIS = 25.0
    private const val MIN_RECTANGULARITY = 0.55
    // Reject quads with <40% of their perimeter on real image edges. This is
    // what kills "detector drew a phantom rectangle in the middle of nothing".
    private const val MIN_EDGE_SUPPORT = 0.40
    private const val MIN_ASPECT = 0.3
    private const val MAX_ASPECT = 3.5
    private const val MIN_ACCEPT_SCORE = 0.30f
}
