package com.google.android.filament.gltf.models

/**
 * Holds vertices representing each corner of a quad
 */
data class Quad(
    val topLeftVertex: Vertex,
    val bottomLeftVertex: Vertex,
    val topRightVertex: Vertex,
    val bottomRightVertex: Vertex,
)
