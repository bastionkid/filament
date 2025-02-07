package com.google.android.filament.gltf.utils

import com.google.android.filament.Engine
import com.google.android.filament.IndexBuffer
import com.google.android.filament.gltf.models.Quad
import com.google.android.filament.gltf.models.Vertex
import java.nio.Buffer
import java.nio.FloatBuffer
import java.nio.ShortBuffer

object BufferUtils {
    private const val INT_SIZE = 4

    const val FLOAT_SIZE = 4
    const val VERTEX_POSITION_SIZE = 3 * FLOAT_SIZE
    const val VERTEX_POSITION_WITH_COLOR_SIZE = 3 * FLOAT_SIZE + INT_SIZE
    const val UV_SIZE = 2 * FLOAT_SIZE
}

fun createBufferDataFromVertices(vertices: FloatArray): Buffer {
    return FloatBuffer.allocate(vertices.size)
        .put(vertices)
        .flip()
}

fun createBufferDataFromVertices(vertices: List<Vertex>): Buffer {
    return FloatBuffer.allocate(vertices.size)
        .apply {
            vertices.forEach {
                put(it.x)
                put(it.y)
                put(it.z)
            }
        }
        .flip()
}

fun createBufferDataFromQuad(quad: Quad): Buffer {
    val vertices = 4 * 3 // Quad has 4 vertex with 3 co-ordinates each

    return FloatBuffer.allocate(vertices)
        .apply {
            // Top Left Vertex
            put(quad.topLeftVertex.x)
            put(quad.topLeftVertex.y)
            put(quad.topLeftVertex.z)

            // Bottom Left Vertex
            put(quad.bottomLeftVertex.x)
            put(quad.bottomLeftVertex.y)
            put(quad.bottomLeftVertex.z)

            // Top Right Vertex
            put(quad.topRightVertex.x)
            put(quad.topRightVertex.y)
            put(quad.topRightVertex.z)

            // Bottom Right Vertex
            put(quad.bottomRightVertex.x)
            put(quad.bottomRightVertex.y)
            put(quad.bottomRightVertex.z)
        }
        .flip()
}

fun createBufferDataFromIndices(indices: ShortArray): Buffer {
    return ShortBuffer.allocate(indices.size)
        .put(indices)
        .flip()
}

fun Engine.createIndexBuffer(indices: ShortArray): IndexBuffer {
    val data = ShortBuffer.allocate(indices.size)
        .put(indices)
        .flip()

    return IndexBuffer.Builder()
        .indexCount(indices.size)
        .bufferType(IndexBuffer.Builder.IndexType.USHORT)
        .build(this)
        .apply {
            setBuffer(this@createIndexBuffer, data)
        }
}
