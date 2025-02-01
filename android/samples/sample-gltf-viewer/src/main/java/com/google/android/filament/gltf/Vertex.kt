package com.google.android.filament.gltf

/**
 * Holds position of a point in 3D space
 */
data class Vertex(val x: Float, val y: Float, val z: Float)

/**
 * Holds position of a point in 3D space along with color
 */
data class VertexWithColor(val x: Float, val y: Float, val z: Float, val color: Int)

/**
 * Holds vertices representing each corner of a quad
 */
data class Quad(
    val topLeftVertex: Vertex,
    val bottomLeftVertex: Vertex,
    val topRightVertex: Vertex,
    val bottomRightVertex: Vertex,
)
