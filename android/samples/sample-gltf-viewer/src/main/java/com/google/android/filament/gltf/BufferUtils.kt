package com.google.android.filament.gltf

import com.google.android.filament.Engine
import com.google.android.filament.IndexBuffer
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
