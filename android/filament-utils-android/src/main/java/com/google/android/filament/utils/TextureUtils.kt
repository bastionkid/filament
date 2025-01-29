package com.google.android.filament.utils

import android.content.res.Resources
import android.graphics.BitmapFactory
import com.google.android.filament.Engine
import com.google.android.filament.Texture
import java.nio.ByteBuffer

fun Engine.buildTextureFromImageResource(resId: Int, resources: Resources): Texture {
    val bitmap = BitmapFactory.decodeResource(resources, resId)

    val byteBuffer = ByteBuffer.allocate(bitmap.allocationByteCount)
    bitmap.copyPixelsToBuffer(byteBuffer)

    // Rewind the buffer to make it ready for read
    byteBuffer.rewind()

    return Texture.Builder()
        .width(bitmap.width)
        .height(bitmap.height)
        .levels(1)
        .format(Texture.InternalFormat.RGBA8) // RGBA for transparency
        .build(this)
        .apply {
            val buffer = Texture.PixelBufferDescriptor(byteBuffer, Texture.Format.RGBA, Texture.Type.UBYTE)
            setImage(this@buildTextureFromImageResource, 0, buffer)
        }
}
