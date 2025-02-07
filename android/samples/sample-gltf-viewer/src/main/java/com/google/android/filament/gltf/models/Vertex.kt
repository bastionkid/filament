package com.google.android.filament.gltf.models

/**
 * Holds position of a point in 3D space
 */
data class Vertex(val x: Float, val y: Float, val z: Float)

/**
 * Holds position of a point in 3D space along with color
 */
data class VertexWithColor(val x: Float, val y: Float, val z: Float, val color: Int)
