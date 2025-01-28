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
import android.widget.Button
import com.google.android.filament.Box
import com.google.android.filament.Colors
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
import java.nio.FloatBuffer
import java.nio.ShortBuffer

class MainActivity : Activity() {

    companion object {
        // Load the library for the utility layer, which in turn loads gltfio and the Filament core.
        init { Utils.init() }
        private const val TAG = "gltf-viewer"
    }

    private lateinit var surfaceView: SurfaceView
    private lateinit var btnToggleOverlay: Button
    private lateinit var btnToggleBallDots: Button
    private val choreographer: Choreographer by lazy { Choreographer.getInstance() }
    private val frameScheduler = FrameCallback()
    private val modelViewer: ModelViewer by lazy { ModelViewer(surfaceView) }

    @Entity
    private var triangleEntity = 0

    private lateinit var triangleMaterial: Material
    private lateinit var triangleVertexBuffer: VertexBuffer
    private lateinit var triangleIndexBuffer: IndexBuffer

    @Entity
    private var lineEntity = 0

    private lateinit var lineMaterial: Material
    private lateinit var lineVertexBuffer: VertexBuffer
    private lateinit var lineIndexBuffer: IndexBuffer

    private var pitchOverlayVisible = false

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.simple_layout)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        surfaceView = findViewById(R.id.main_sv)
        btnToggleOverlay = findViewById(R.id.btn_toggle_overlay)
        btnToggleBallDots = findViewById(R.id.btn_toggle_ball_dots)

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
        }

        createDefaultRenderables()

        // Creating light is mandatory
        createIndirectLight()

        btnToggleOverlay.setOnClickListener {
            if (pitchOverlayVisible) {
                showEntity("pitch")
                hideEntity("pitch_overlay")
            } else {
                showEntity("pitch_overlay")
                hideEntity("pitch")
            }

            pitchOverlayVisible = !pitchOverlayVisible
        }

        btnToggleBallDots.setOnClickListener {
            showEntity("ball_1")
            showEntity("ball_2")
            showEntity("ball_3")
            showEntity("ball_4")
            showEntity("ball_5")
            showEntity("ball_6")

            placeBallDot("ball_1", getBallX(), 0.025f, getBallZ())
            placeBallDot("ball_2", getBallX(), 0.025f, getBallZ())
            placeBallDot("ball_3", getBallX(), 0.025f, getBallZ())
            placeBallDot("ball_4", getBallX(), 0.025f, getBallZ())
            placeBallDot("ball_5", getBallX(), 0.025f, getBallZ())
            placeBallDot("ball_6", getBallX(), 0.025f, getBallZ())
        }
    }

    /**
     * x values are constrained to be in [-1, 0.5] meters
     */
    private fun getBallX(): Float {
        return -1f + (1.5f * Math.random().toFloat())
    }

    /**
     * z values are constrained to be in [-10, 0] meters
     */
    private fun getBallZ(): Float {
        return -(10 * Math.random().toFloat())
    }

    private fun createDefaultRenderables() {
        val buffer = assets.open("models/stadium.gltf").use { input ->
            val bytes = ByteArray(input.available())
            input.read(bytes)
            ByteBuffer.wrap(bytes)
        }

        modelViewer.loadModelGlb(buffer)
        updateRootTransform()

//        addTriangle()
//        addLine()
//        addQuadLine()

        showEntity("pitch")
        hideEntity("pitch_overlay")

        // hide all the ball dots
        hideEntity("ball_1")
        hideEntity("ball_2")
        hideEntity("ball_3")
        hideEntity("ball_4")
        hideEntity("ball_5")
        hideEntity("ball_6")
    }

    private fun showEntity(entityName: String) {
        modelViewer.asset?.getFirstEntityByName(entityName)?.let { entity ->
            modelViewer.engine.renderableManager.showEntity(entity)
        }
    }

    private fun hideEntity(entityName: String) {
        modelViewer.asset?.getFirstEntityByName(entityName)?.let { entity ->
            modelViewer.engine.renderableManager.hideEntity(entity)
        }
    }

    private fun updateRootTransform() {
        modelViewer.clearRootTransform()
    }

    private fun createIndirectLight() {
        val engine = modelViewer.engine
        val scene = modelViewer.scene
        val ibl = "default_env"

        // Create Light
        assets.readCompressedAsset("envs/$ibl/${ibl}_ibl.ktx").let {
            scene.indirectLight = KTX1Loader.createIndirectLight(engine, it)
            scene.indirectLight!!.intensity = 30_000.0f
        }

        // Create Skybox
        assets.readCompressedAsset("envs/$ibl/${ibl}_skybox.ktx").let {
            scene.skybox = KTX1Loader.createSkybox(engine, it)
        }
    }

    private fun placeBallDot(entityName: String, x: Float, y: Float, z: Float) {
        modelViewer.asset?.getFirstEntityByName(entityName)?.let { entity ->
            modelViewer.engine.transformManager.translateEntity(
                x = x,
                y = y,
                z = z,
                entity = entity,
            )
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
    }

    private fun loadMaterial() {
        assets.readCompressedAsset("materials/baked_color.filamat").let {
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

        // A vertex is a position + a color: 3 floats for XYZ position, 1 integer for color
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

        val vertexData = ByteBuffer.allocate(vertexCount * vertexSize)
            // It is important to respect the native byte order
            .order(ByteOrder.nativeOrder())
            .put(Vertex( 1.0f, 2.0f, 0.0f, 0xffff0000.toInt()))
            .put(Vertex(-1.0f, 3.0f, 0.0f, 0xff00ff00.toInt()))
            .put(Vertex(-1.0f, 1.0f, 0.0f, 0xff0000ff.toInt()))
            .flip()

        // Declare the layout of our mesh
        triangleVertexBuffer = VertexBuffer.Builder()
            .bufferCount(1)
            .vertexCount(vertexCount)
            // Because we interleave position and color data (i.e. having both position and color
            // data in same source) we must specify offset and stride.
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
            .indexCount(vertexCount)
            .bufferType(IndexBuffer.Builder.IndexType.USHORT)
            .build(modelViewer.engine)

        triangleIndexBuffer.setBuffer(modelViewer.engine, indexData)
    }

    private fun addLine() {
        val floatSize = 4
        val vertexSize = 3 * floatSize

        val points = floatArrayOf(
            -1f, 1f, 8f,     // Point 1
            -0.5f, 0.8f, 5f, // Point 2
            0.3f, 0.5f, 3f,  // Point 3
            0.5f, 0.5f, 2f,  // Point 4
            1f, 0.5f, 1f,    // Point 5
        )

        val vertexCount = points.size / 3

        val lineIndices = shortArrayOf(
            0, 1,  // Connect vertex 0 to vertex 1
            1, 2,  // Connect vertex 1 to vertex 2
            2, 3,  // Connect vertex 2 to vertex 3
            3, 4,  // Connect vertex 3 to vertex 4
        )

        // Step 1: Create a Vertex Buffer
        val vertexData = FloatBuffer.allocate(points.size)
            .put(points)
            .flip()

        lineVertexBuffer = VertexBuffer.Builder()
            .bufferCount(1)
            .vertexCount(vertexCount)
            .attribute(VertexAttribute.POSITION, 0, AttributeType.FLOAT3, 0, vertexSize)
            .build(modelViewer.engine)

        lineVertexBuffer.setBufferAt(modelViewer.engine, 0, vertexData)

        // Step 2: Create an Index Buffer
        val indexData = ShortBuffer.allocate(lineIndices.size)
            .put(lineIndices)
            .flip()

        lineIndexBuffer = IndexBuffer.Builder()
            .indexCount(lineIndices.size)
            .bufferType(IndexBuffer.Builder.IndexType.USHORT)
            .build(modelViewer.engine)

        lineIndexBuffer.setBuffer(modelViewer.engine, indexData)

        // Step 3: Load the Material
        assets.readCompressedAsset("materials/line.filamat").let {
            lineMaterial = Material.Builder().payload(it, it.remaining()).build(modelViewer.engine)
            modelViewer.engine.flush()
        }

        val materialInstance = lineMaterial.createInstance()
        materialInstance.setParameter("baseColor", Colors.RgbaType.SRGB, 0f, 0f, 0f, 1.0f) // Black color

        // Step 4: Create a Renderable
        lineEntity = EntityManager.get().create()

        RenderableManager.Builder(1)
            .boundingBox(Box(0.0f, 0.0f, 0.0f, 1.0f, 1.0f, 0.01f))
            .geometry(0, PrimitiveType.LINES, lineVertexBuffer, lineIndexBuffer)
            .material(0, materialInstance)
            .build(modelViewer.engine, lineEntity)

        // Step 5: Add the Line to the Scene
        modelViewer.scene.addEntity(lineEntity)
    }

    /**
     * Create a quad line i.e. line between 2 points is made using a quad (i.e. 2 triangles).
     * Here we generate 4 triangle vertices (required to draw 2 triangle where 2 vertices ate common between them)
     * from 2 line vertices
     */
    private fun addQuadLine() {
        val floatSize = 4
        val vertexSize = 3 * floatSize

        val thickLinePoints = createThickLineGeometry(
            points = floatArrayOf(
                -1f, 1f, 8f,     // Point 1
                -0.5f, 0.8f, 5f, // Point 2
                0.3f, 0.5f, 3f,  // Point 3
                0.5f, 0.5f, 2f,  // Point 4
                1f, 0.5f, 1f,    // Point 5
            ),
            thickness = 0.1f,
        )

        val vertexCount = thickLinePoints.size / 3

        val lineIndices = shortArrayOf(
            0, 1, 2,
            2, 1, 3,
            4, 5, 6,
            6, 5, 7,
            8, 9, 10,
            10, 9, 11,
            12, 13, 14,
            14, 13, 15,
        )

        // Step 1: Create a Vertex Buffer
        val vertexData = FloatBuffer.allocate(thickLinePoints.size)
            .put(thickLinePoints)
            .flip()

        lineVertexBuffer = VertexBuffer.Builder()
            .bufferCount(1)
            .vertexCount(vertexCount)
            .attribute(VertexAttribute.POSITION, 0, AttributeType.FLOAT3, 0, vertexSize)
            .build(modelViewer.engine)

        lineVertexBuffer.setBufferAt(modelViewer.engine, 0, vertexData)

        // Step 2: Create an Index Buffer
        val indexData = ShortBuffer.allocate(lineIndices.size)
            .put(lineIndices)
            .flip()

        lineIndexBuffer = IndexBuffer.Builder()
            .indexCount(lineIndices.size)
            .bufferType(IndexBuffer.Builder.IndexType.USHORT)
            .build(modelViewer.engine)

        lineIndexBuffer.setBuffer(modelViewer.engine, indexData)

        // Step 3: Load the Material
        assets.readCompressedAsset("materials/line.filamat").let {
            lineMaterial = Material.Builder().payload(it, it.remaining()).build(modelViewer.engine)
            modelViewer.engine.flush()
        }

        val materialInstance = lineMaterial.createInstance()
        materialInstance.setParameter("baseColor", Colors.RgbaType.SRGB, 0f, 0f, 0f, 1.0f) // Black color

        // Step 4: Create a Renderable
        lineEntity = EntityManager.get().create()

        RenderableManager.Builder(1)
            .boundingBox(Box(0.0f, 0.0f, 0.0f, 1.0f, 1.0f, 0.01f))
            .geometry(0, PrimitiveType.TRIANGLES, lineVertexBuffer, lineIndexBuffer)
            .material(0, materialInstance)
            .build(modelViewer.engine, lineEntity)

        // Step 5: Add the Line to the Scene
        modelViewer.scene.addEntity(lineEntity)
    }

    private fun createThickLineGeometry(points: FloatArray, thickness: Float): FloatArray {
        val vertices = mutableListOf<Float>()

        for (i in 0 .. points.size - 6 step 3) {
            val x1 = points[i]
            val y1 = points[i + 1]
            val z1 = points[i + 2]
            val x2 = points[i + 3]
            val y2 = points[i + 4]
            val z2 = points[i + 5]

            // Compute direction vector
            val dx = x2 - x1
            val dy = y2 - y1
            val length = kotlin.math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()

            // Normalize and compute perpendicular vector
            val nx = -dy / length * thickness / 2
            val ny = dx / length * thickness / 2

            // Add quad vertices
            vertices.addAll(
                listOf(
                    x1 + nx, y1 + ny, z1, // Top-left
                    x1 - nx, y1 - ny, z1, // Bottom-left
                    x2 + nx, y2 + ny, z2, // Top-right
                    x2 - nx, y2 - ny, z2  // Bottom-right
                )
            )
        }

        return vertices.toFloatArray()
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

        // Cleanup triangle resources
        if (::triangleVertexBuffer.isInitialized) {
            modelViewer.engine.destroyEntity(triangleEntity)
            modelViewer.engine.destroyVertexBuffer(triangleVertexBuffer)
            modelViewer.engine.destroyIndexBuffer(triangleIndexBuffer)
            modelViewer.engine.destroyMaterial(triangleMaterial)
        }

        // Cleanup line resources
        if (::lineVertexBuffer.isInitialized) {
            modelViewer.engine.destroyEntity(lineEntity)
            modelViewer.engine.destroyVertexBuffer(lineVertexBuffer)
            modelViewer.engine.destroyIndexBuffer(lineIndexBuffer)
            modelViewer.engine.destroyMaterial(lineMaterial)
        }
    }

    inner class FrameCallback : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            choreographer.postFrameCallback(this)
            modelViewer.render(frameTimeNanos)
        }
    }
}
