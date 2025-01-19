package com.google.android.filament.gltf

import android.opengl.Matrix
import com.google.android.filament.TransformManager
import com.google.android.filament.utils.Float4
import com.google.android.filament.utils.Mat4

/**
 * Utilize Identity [android.opengl.Matrix] to translate Entity
 */
fun TransformManager.translateEntityUsingIdentityM(x: Float = 0f, y: Float = 0f, z: Float = 0f, renderable: Int) {
    val transformMatrix = FloatArray(16).apply {
        Matrix.setIdentityM(this, 0)
        Matrix.translateM(this, 0, x, y, z)
    }

    this.setTransform(this.getInstance(renderable), transformMatrix)
}

/**
 * Utilize [com.google.android.filament.utils.Mat4] to translate Entity
 */
fun TransformManager.translateEntityUsingMat4(x: Float = 0f, y: Float = 0f, z: Float = 0f, renderable: Int) {
    val transformMatrix = Mat4(
        x = Float4(x = 1f, w = x),
        y = Float4(y = 1f, w = y),
        z = Float4(z = 1f, w = z),
    ).toFloatArray()

    this.setTransform(this.getInstance(renderable), transformMatrix)
}
