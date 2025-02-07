package com.google.android.filament.gltf.utils

import com.google.android.filament.gltf.models.Quad
import com.google.android.filament.gltf.models.Vertex
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

object CylinderUtils {

    /**
     * Returns vertices having size = [numOfPoints] along the circumference of a circle having
     * radius = [radius] and center at (x = [centerX], y = [centerY], z = [centerZ])
     */
    fun getPointsAlongCircumference(
        centerX: Float,
        centerY: Float,
        centerZ: Float,
        radius: Float,
        numOfPoints: Int,
    ): List<Vertex> {
        val angleIncrement = (2 * PI) / numOfPoints

        return buildList {
            for (i in 0 until numOfPoints) {
                val theta = i * angleIncrement

                add(
                    Vertex(
                        x = centerX + (radius * cos(theta)).toFloat(),
                        y = centerY + (radius * sin(theta)).toFloat(),
                        z = centerZ,
                    )
                )
            }
        }
    }

    /**
     * Create a list of quad vertices from zipping [point1Vertices] & [point2Vertices].
     * Each quad
     */
    fun getQuadVertices(point1Vertices: List<Vertex>, point2Vertices: List<Vertex>): List<Quad> {
        // assert that size of point1Vertices & point2Vertices is same as well as even
        require(point1Vertices.size == point2Vertices.size && point1Vertices.size % 2 == 0)

        // Zip point1 & point2 vertices list and then create a windows of 2 elements in each iteration
        // We also add first element of each list to the end of each list to make sure the cylinder ends are connected
        val combinedVertices = (point1Vertices + point1Vertices.first())
            .zip(point2Vertices + point2Vertices.first())
            .windowed(size = 2)

        return combinedVertices.map { combinedVerticesWindow ->
            val (topLeftVertex, bottomLeftVertex) = combinedVerticesWindow[0]
            val (topRightVertex, bottomRightVertex) = combinedVerticesWindow[1]

            Quad(
                topLeftVertex = topLeftVertex,
                bottomLeftVertex = bottomLeftVertex,
                topRightVertex = topRightVertex,
                bottomRightVertex = bottomRightVertex,
            )
        }
    }
}
