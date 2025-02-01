package com.google.android.filament.gltf

/**
 * A quadratic Bézier curve is defined by 3 points: a start point (P0), a control point (P1),
 * and an end point (P2). The curve smoothly transitions from P0 to P2, influenced by P1.
 */
fun quadraticBezier(
    p0: Pair<Float, Float>,
    p1: Pair<Float, Float>,
    p2: Pair<Float, Float>,
    numOfPoints: Int,
): List<Pair<Float, Float>> {
    val points = mutableListOf<Pair<Float, Float>>()

    for (i in 0 until numOfPoints) {
        val t = i.toFloat() / (numOfPoints - 1) // t is a parameter between 0 and 1
        val x = (1 - t) * (1 - t) * p0.first + 2 * (1 - t) * t * p1.first + t * t * p2.first
        val y = (1 - t) * (1 - t) * p0.second + 2 * (1 - t) * t * p1.second + t * t * p2.second
        points.add(Pair(x, y))
    }

    return points
}

/**
 * A cubic Bézier curve is defined by 4 points: a start point (P0), two control points (P1 and P2),
 * and an end point (P3). This allows for more complex curves compared to the quadratic version.
 */
fun cubicBezier(
    p0: Pair<Float, Float>,
    p1: Pair<Float, Float>,
    p2: Pair<Float, Float>,
    p3: Pair<Float, Float>,
    numPoints: Int
): List<Pair<Float, Float>> {
    val points = mutableListOf<Pair<Float, Float>>()

    for (i in 0 until numPoints) {
        val t = i.toFloat() / (numPoints - 1)  // t is a parameter between 0 and 1
        val x = (1 - t) * (1 - t) * (1 - t) * p0.first +
            3 * (1 - t) * (1 - t) * t * p1.first +
            3 * (1 - t) * t * t * p2.first +
            t * t * t * p3.first
        val y = (1 - t) * (1 - t) * (1 - t) * p0.second +
            3 * (1 - t) * (1 - t) * t * p1.second +
            3 * (1 - t) * t * t * p2.second +
            t * t * t * p3.second
        points.add(Pair(x, y))
    }

    return points
}
