package com.google.android.filament.gltf.utils

import com.google.android.filament.Entity
import com.google.android.filament.TransformManager

/**
 * Utilize Identity [android.opengl.Matrix] to translate Entity
 */
fun TransformManager.translateEntity(x: Float = 0f, y: Float = 0f, z: Float = 0f, @Entity entity: Int) {
    // First get current transform matrix for the entity to ensure all the non translation related
    // properties are preserved after applying transformation
    val currentTransformMatrix = FloatArray(16)
    this.getTransform(this.getInstance(entity), currentTransformMatrix)

    // indices (12, 13, 14) represents (x, y, z) co-ordinates in the transformation matrix
    currentTransformMatrix[12] = x
    currentTransformMatrix[13] = y
    currentTransformMatrix[14] = z

    this.setTransform(this.getInstance(entity), currentTransformMatrix)
}
