package com.google.android.filament.gltf

/**
 * A quadratic Bézier curve is defined by 3 points: a start point, a control point,
 * and an end point. The curve smoothly transitions from start to end, influenced by control point.
 */
fun quadraticBezier(
    start: Pair<Float, Float>,
    control: Pair<Float, Float>,
    end: Pair<Float, Float>,
    numOfPoints: Int,
): List<Pair<Float, Float>> {
    val points = mutableListOf<Pair<Float, Float>>()

    for (i in 0 until numOfPoints) {
        val t = i.toFloat() / (numOfPoints - 1) // t is a parameter between 0 and 1
        val x = (1 - t) * (1 - t) * start.first + 2 * (1 - t) * t * control.first + t * t * end.first
        val y = (1 - t) * (1 - t) * start.second + 2 * (1 - t) * t * control.second + t * t * end.second
        points.add(Pair(x, y))
    }

    return points
}

/**
 * A cubic Bézier curve is defined by 4 points: a start point, two control (control1 and control2)
 * points, and an end point. This allows for more complex curves compared to the quadratic version.
 */
fun cubicBezier(
    start: Pair<Float, Float>,
    control1: Pair<Float, Float>,
    control2: Pair<Float, Float>,
    end: Pair<Float, Float>,
    numPoints: Int
): List<Pair<Float, Float>> {
    val points = mutableListOf<Pair<Float, Float>>()

    for (i in 0 until numPoints) {
        val t = i.toFloat() / (numPoints - 1)  // t is a parameter between 0 and 1
        val x = (1 - t) * (1 - t) * (1 - t) * start.first +
            3 * (1 - t) * (1 - t) * t * control1.first +
            3 * (1 - t) * t * t * control2.first +
            t * t * t * end.first
        val y = (1 - t) * (1 - t) * (1 - t) * start.second +
            3 * (1 - t) * (1 - t) * t * control1.second +
            3 * (1 - t) * t * t * control2.second +
            t * t * t * end.second
        points.add(Pair(x, y))
    }

    return points
}
