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
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.*
import com.google.android.filament.Box
import com.google.android.filament.Entity
import com.google.android.filament.EntityManager
import com.google.android.filament.IndexBuffer
import com.google.android.filament.Material
import com.google.android.filament.RenderableManager
import com.google.android.filament.RenderableManager.PrimitiveType
import com.google.android.filament.VertexBuffer
import com.google.android.filament.VertexBuffer.AttributeType
import com.google.android.filament.VertexBuffer.VertexAttribute
import com.google.android.filament.View
import com.google.android.filament.utils.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

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

    @Entity
    private var triangleEntity = 0

    private lateinit var triangleMaterial: Material
    private lateinit var triangleVertexBuffer: VertexBuffer
    private lateinit var triangleIndexBuffer: IndexBuffer

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.simple_layout)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        surfaceView = findViewById(R.id.main_sv)

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

        addTriangle()
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

    private fun addTriangle() {
        loadMaterial()
        createMesh()

        // To create a renderable we first create a generic entity
        triangleEntity = EntityManager.get().create()

        // We then create a renderable component on that entity
        // A renderable is made of several primitives; in this case we declare only 1
        RenderableManager.Builder(1)
            // Overall bounding box of the renderable. This Box does not dictate the position of
            // the entity in the scene. To set position you've to use TransformManager
            .boundingBox(Box(0.0f, 0.0f, 0.0f, 1.0f, 1.0f, 0.01f))
            // Sets the mesh data of the first primitive
            .geometry(0, PrimitiveType.TRIANGLES, triangleVertexBuffer, triangleIndexBuffer, 0, 3)
            // Sets the material of the first primitive
            .material(0, triangleMaterial.defaultInstance)
            .build(modelViewer.engine, triangleEntity)

        // Add the entity to the scene to render it
        modelViewer.scene.addEntity(triangleEntity)

        // Translate entity by 2 units along +ve y axis
        modelViewer.engine.transformManager.translateEntityUsingIdentityM(y = 2f, renderable = triangleEntity)
    }

    private fun loadMaterial() {
        readCompressedAsset("materials/baked_color.filamat").let {
            triangleMaterial = Material.Builder().payload(it, it.remaining()).build(modelViewer.engine)
            triangleMaterial.compile(
                Material.CompilerPriorityQueue.HIGH,
                Material.UserVariantFilterBit.ALL,
                Handler(Looper.getMainLooper()),
            ) {
                Log.i("TAG", "Material " + triangleMaterial.name + " compiled.")
            }
            modelViewer.engine.flush()
        }
    }

    private fun createMesh() {
        val intSize = 4
        val floatSize = 4
        val shortSize = 2
        // A vertex is a position + a color:
        // 3 floats for XYZ position, 1 integer for color
        val vertexSize = 3 * floatSize + intSize

        // Define a vertex and a function to put a vertex in a ByteBuffer
        data class Vertex(val x: Float, val y: Float, val z: Float, val color: Int)
        fun ByteBuffer.put(v: Vertex): ByteBuffer {
            putFloat(v.x)
            putFloat(v.y)
            putFloat(v.z)
            putInt(v.color)
            return this
        }

        // We are going to generate a single triangle
        val vertexCount = 3
        val a1 = PI * 2.0 / 3.0
        val a2 = PI * 4.0 / 3.0

        val vertexData = ByteBuffer.allocate(vertexCount * vertexSize)
            // It is important to respect the native byte order
            .order(ByteOrder.nativeOrder())
            .put(Vertex(1.0f,              0.0f,              0.0f, 0xffff0000.toInt()))
            .put(Vertex(cos(a1).toFloat(), sin(a1).toFloat(), 0.0f, 0xff00ff00.toInt()))
            .put(Vertex(cos(a2).toFloat(), sin(a2).toFloat(), 0.0f, 0xff0000ff.toInt()))
            // Make sure the cursor is pointing in the right place in the byte buffer
            .flip()

        // Declare the layout of our mesh
        triangleVertexBuffer = VertexBuffer.Builder()
            .bufferCount(1)
            .vertexCount(vertexCount)
            // Because we interleave position and color data we must specify offset and stride
            // We could use de-interleaved data by declaring two buffers and giving each
            // attribute a different buffer index
            .attribute(VertexAttribute.POSITION, 0, AttributeType.FLOAT3, 0,             vertexSize)
            .attribute(VertexAttribute.COLOR,    0, AttributeType.UBYTE4, 3 * floatSize, vertexSize)
            // We store colors as unsigned bytes but since we want values between 0 and 1
            // in the material (shaders), we must mark the attribute as normalized
            .normalized(VertexAttribute.COLOR)
            .build(modelViewer.engine)

        // Feed the vertex data to the mesh
        // We only set 1 buffer because the data is interleaved
        triangleVertexBuffer.setBufferAt(modelViewer.engine, 0, vertexData)

        // Create the indices
        val indexData = ByteBuffer.allocate(vertexCount * shortSize)
            .order(ByteOrder.nativeOrder())
            .putShort(0)
            .putShort(1)
            .putShort(2)
            .flip()

        triangleIndexBuffer = IndexBuffer.Builder()
            .indexCount(3)
            .bufferType(IndexBuffer.Builder.IndexType.USHORT)
            .build(modelViewer.engine)
        triangleIndexBuffer.setBuffer(modelViewer.engine, indexData)
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

        // Cleanup additional resources
        modelViewer.engine.destroyEntity(triangleEntity)
        modelViewer.engine.destroyVertexBuffer(triangleVertexBuffer)
        modelViewer.engine.destroyIndexBuffer(triangleIndexBuffer)
        modelViewer.engine.destroyMaterial(triangleMaterial)
    }

    inner class FrameCallback : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            choreographer.postFrameCallback(this)
            modelViewer.render(frameTimeNanos)
        }
    }
}
