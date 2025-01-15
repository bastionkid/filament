/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.filament.gltf

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.view.*
import android.view.GestureDetector
import com.google.android.filament.View
import com.google.android.filament.utils.*
import com.google.android.filament.utils.AutomationEngine.ViewerOptions
import java.nio.ByteBuffer

class MainActivity : Activity() {

    companion object {
        // Load the library for the utility layer, which in turn loads gltfio and the Filament core.
        init { Utils.init() }
        private const val TAG = "gltf-viewer"
    }

    private lateinit var surfaceView: SurfaceView
    private val choreographer: Choreographer by lazy { Choreographer.getInstance() }
    private val frameScheduler = FrameCallback()
    private val modelViewer: ModelViewer by lazy { ModelViewer(surfaceView) }
    private val viewerContent = AutomationEngine.ViewerContent()

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.simple_layout)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        surfaceView = findViewById(R.id.main_sv)

        with(viewerContent) {
            view = modelViewer.view
            sunlight = modelViewer.light
            lightManager = modelViewer.engine.lightManager
            scene = modelViewer.scene
            renderer = modelViewer.renderer
        }

        surfaceView.setOnTouchListener { _, event ->
            modelViewer.onTouchEvent(event)
            true
        }

        with(modelViewer.view) {
            // on mobile, better use lower quality color buffer
            renderQuality.hdrColorBuffer = View.QualityLevel.MEDIUM

            // dynamic resolution often helps a lot
            dynamicResolutionOptions.apply {
                enabled = true
                quality = View.QualityLevel.MEDIUM
            }

            // MSAA is needed with dynamic resolution MEDIUM
            if (dynamicResolutionOptions.quality == View.QualityLevel.MEDIUM) {
                multiSampleAntiAliasingOptions.enabled = true
            }

            // FXAA is pretty cheap and helps a lot
            antiAliasing = View.AntiAliasing.FXAA

            // ambient occlusion is the cheapest effect that adds a lot of quality
            ambientOcclusionOptions.enabled = true

            // bloom is pretty expensive but adds a fair amount of realism
            bloomOptions.enabled = true
        }

        createDefaultRenderables()

        // Creating light is mandatory
        createIndirectLight()
    }

    private fun createDefaultRenderables() {
        val buffer = assets.open("models/stadium.gltf").use { input ->
            val bytes = ByteArray(input.available())
            input.read(bytes)
            ByteBuffer.wrap(bytes)
        }

        modelViewer.loadModelGlb(buffer)
        updateRootTransform()
    }

    private fun updateRootTransform() {
        modelViewer.clearRootTransform()
    }

    private fun createIndirectLight() {
        val engine = modelViewer.engine
        val scene = modelViewer.scene
        val ibl = "default_env"

        // Create Light
        readCompressedAsset("envs/$ibl/${ibl}_ibl.ktx").let {
            scene.indirectLight = KTX1Loader.createIndirectLight(engine, it)
            scene.indirectLight!!.intensity = 30_000.0f
            viewerContent.indirectLight = modelViewer.scene.indirectLight
        }

        // Create Skybox
        readCompressedAsset("envs/$ibl/${ibl}_skybox.ktx").let {
            scene.skybox = KTX1Loader.createSkybox(engine, it)
        }
    }

    private fun readCompressedAsset(assetName: String): ByteBuffer {
        return assets.open(assetName).use { input ->
            val bytes = ByteArray(input.available())
            input.read(bytes)
            ByteBuffer.wrap(bytes)
        }
    }

    override fun onResume() {
        super.onResume()
        choreographer.postFrameCallback(frameScheduler)
    }

    override fun onPause() {
        super.onPause()
        choreographer.removeFrameCallback(frameScheduler)
    }

    override fun onDestroy() {
        super.onDestroy()
        choreographer.removeFrameCallback(frameScheduler)
    }

    inner class FrameCallback : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            choreographer.postFrameCallback(this)
            modelViewer.render(frameTimeNanos)
        }
    }
}
