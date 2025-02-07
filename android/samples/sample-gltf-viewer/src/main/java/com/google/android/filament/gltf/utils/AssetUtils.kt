package com.google.android.filament.gltf.utils

import android.content.res.AssetManager
import java.nio.ByteBuffer

fun AssetManager.readCompressedAsset(assetName: String): ByteBuffer {
    return open(assetName).use { input ->
        val bytes = ByteArray(input.available())
        input.read(bytes)
        ByteBuffer.wrap(bytes)
    }
}
