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
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Choreographer
import android.view.SurfaceView
import android.view.WindowManager
import android.widget.Button
import androidx.fragment.app.FragmentActivity
import com.google.android.filament.Box
import com.google.android.filament.Colors
import com.google.android.filament.Entity
import com.google.android.filament.EntityManager
import com.google.android.filament.IndexBuffer
import com.google.android.filament.Material
import com.google.android.filament.MaterialInstance
import com.google.android.filament.RenderableManager
import com.google.android.filament.RenderableManager.PrimitiveType
import com.google.android.filament.TextureSampler
import com.google.android.filament.VertexBuffer
import com.google.android.filament.VertexBuffer.AttributeType
import com.google.android.filament.VertexBuffer.VertexAttribute
import com.google.android.filament.View
import com.google.android.filament.gltf.BufferUtils.FLOAT_SIZE
import com.google.android.filament.gltf.BufferUtils.UV_SIZE
import com.google.android.filament.gltf.BufferUtils.VERTEX_POSITION_SIZE
import com.google.android.filament.gltf.BufferUtils.VERTEX_POSITION_WITH_COLOR_SIZE
import com.google.android.filament.utils.KTX1Loader
import com.google.android.filament.utils.ModelViewer
import com.google.android.filament.utils.Utils
import org.json.JSONObject
import java.nio.Buffer
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

class MainActivity : FragmentActivity() {

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

    @Entity
    private var transparentEntity = 0

    private lateinit var transparentMaterial: Material
    private lateinit var transparentVertexBuffer: VertexBuffer
    private lateinit var transparentIndexBuffer: IndexBuffer

    private var pitchOverlayVisible = false

    // Ball diameter = 0.050f
    private val ballTrajectoryVertexOffset = 0.050f / 2

    private val ballTrajectoryUvData: Buffer by lazy {
        val uvPoints = floatArrayOf(
            0.0f, 1.0f,  // Top-left
            0.0f, 0.0f,  // Bottom-left
            1.0f, 1.0f,  // Top-right
            1.0f, 0.0f,  // Bottom-right
        )

        FloatBuffer.allocate(uvPoints.size)
            .put(uvPoints)
            .flip()
    }

    private val ballTrajectoryIndexBuffer: IndexBuffer by lazy {
        val indices = shortArrayOf(
            0, 1, 2,
            2, 1, 3,
        )

        modelViewer.engine.createIndexBuffer(indices)
    }

    private val ballTrajectoryVertexBuffers = mutableListOf<VertexBuffer>()
    private val ballTrajectoryEntities = mutableListOf<Int>()

    private val ballTrajectoryMaterial: Material by lazy {
        val byteBuffer = assets.readCompressedAsset("materials/ball_trajectory_circle.filamat")
        Material.Builder()
            .payload(byteBuffer, byteBuffer.remaining())
            .build(modelViewer.engine)
    }

    private val ballTrajectoryMaterialInstance: MaterialInstance by lazy {
        ballTrajectoryMaterial.createInstance().apply {
            setParameter("texture", modelViewer.engine.buildTextureFromImageResource(R.drawable.ball_trajectory_circle, resources), TextureSampler())
        }
    }

    private val quadVertexBuffers = mutableListOf<VertexBuffer>()
    private val quadEntities = mutableListOf<Int>()

    private val quadIndexBuffer: IndexBuffer by lazy {
        val indices = shortArrayOf(
            0, 1, 2,
            2, 1, 3
        )

        modelViewer.engine.createIndexBuffer(indices)
    }

    private val quadMaterial: Material by lazy {
        val materialBuffer = assets.readCompressedAsset("materials/cylinder.filamat")
        Material.Builder().payload(materialBuffer, materialBuffer.remaining()).build(modelViewer.engine).apply {
            modelViewer.engine.flush()
        }
    }

    private val quadMaterialInstance: MaterialInstance by lazy {
        quadMaterial.createInstance()
            .apply {
                setParameter("baseColor", Colors.RgbaType.SRGB, 1.0f, 0f, 0f, 1.0f) // Red color
            }
    }

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
                modelViewer.showEntity("pitch")
                modelViewer.hideEntity("pitch_overlay")
            } else {
                modelViewer.showEntity("pitch_overlay")
                modelViewer.hideEntity("pitch")
            }

            pitchOverlayVisible = !pitchOverlayVisible
        }

        btnToggleBallDots.setOnClickListener {
            modelViewer.showEntity("bowling_accuracy_target")

            val bowlingAccuracyData = JSONObject(
                """
                    {
                        "target": {
                          "x": 1.35,
                          "y": 2.3
                        },
                        "balls": [
                          {
                            "x": 1.15,
                            "y": 1.85
                          },
                          {
                            "x": 1.45,
                            "y": 2.25
                          },
                          {
                            "x": 0.78,
                            "y": 3.86
                          },
                          {
                            "x": 0.63,
                            "y": 4.67
                          },
                          {
                            "x": 1.32,
                            "y": 3.44
                          },
                          {
                            "x": 1.00,
                            "y": 5.45
                          }
                        ]
                    }
                """.trimIndent()
            )
            val target = bowlingAccuracyData.getJSONObject("target")
            val balls = bowlingAccuracyData.getJSONArray("balls")

            modelViewer.asset?.getFirstEntityByName("bowling_accuracy_target")?.let { entity ->
                if (entity == 0) return@let

                val (x, z) = translatedCoOrdinates(
                    x = target.getDouble("x").toFloat(),
                    y = target.getDouble("y").toFloat(),
                )

                modelViewer.engine.transformManager.translateEntity(
                    x = x,
                    y = 0.025f,
                    z = z,
                    entity = entity,
                )
            }

            // show and randomly place first 6 ball dots
            repeat(6) { index ->
                modelViewer.showEntity("ball_${index + 1}")

                val ball = balls.getJSONObject(index)

                val (x, z) = translatedCoOrdinates(
                    x = ball.getDouble("x").toFloat(),
                    y = ball.getDouble("y").toFloat(),
                )

                placeBallDot("ball_${index + 1}", x, 0.025f, z)
            }
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

    /**
     * translate the (x, y) co-ordinates outputted by the backend models to the gltf coordinate system.
     * Origin of the gltf has an offset of (1.525, 10.06) from the origin of the backend models.
     */
    private fun translatedCoOrdinates(x: Float, y: Float): Pair<Float, Float> {
        return Pair(x - 1.525f, y - 10.06f)
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
//        addTransparentTexture()
//        addBallTrajectory()
//        addQuad(
//            Quad(
//                topLeftVertex = Vertex(
//                    x = -0.5f,
//                    y = 2.00f,
//                    z = 8.00f,
//                ), bottomLeftVertex = Vertex(
//                    x = -0.5f,
//                    y = 1.00f,
//                    z = 8.00f,
//                ), topRightVertex = Vertex(
//                    x = 0.5f,
//                    y = 2.00f,
//                    z = 2.00f,
//                ), bottomRightVertex = Vertex(
//                    x = 0.5f,
//                    y = 1.00f,
//                    z = 2.00f,
//                )
//            )
//        )
//        addCylinder()

        modelViewer.showEntity("pitch")
        modelViewer.hideEntity("pitch_overlay")
        modelViewer.hideEntity("bowling_accuracy_target")

        // hide all the ball dots
        repeat(18) { index ->
            modelViewer.hideEntity("ball_${index + 1}")
        }
    }

    private fun updateRootTransform() {
        modelViewer.clearRootTransform()
    }

    private fun createIndirectLight() {
        val engine = modelViewer.engine
        val scene = modelViewer.scene
        val ibl = "white_furnace"

        // Create Light
        assets.readCompressedAsset("envs/$ibl/${ibl}_ibl.ktx").let {
            scene.indirectLight = KTX1Loader.createIndirectLight(engine, it)
            scene.indirectLight!!.intensity = 60_000.0f
        }

        // Create Skybox
        assets.readCompressedAsset("envs/$ibl/${ibl}_skybox.ktx").let {
            scene.skybox = KTX1Loader.createSkybox(engine, it)
        }
    }

    private fun placeBallDot(entityName: String, x: Float, y: Float, z: Float) {
        modelViewer.asset?.getFirstEntityByName(entityName)?.let { entity ->
            if (entity == 0) return

            modelViewer.engine.transformManager.translateEntity(
                x = x,
                y = y,
                z = z,
                entity = entity,
            )
        }
    }

    private fun addTriangle() {
        fun ByteBuffer.put(v: VertexWithColor): ByteBuffer {
            putFloat(v.x)
            putFloat(v.y)
            putFloat(v.z)
            putInt(v.color)
            return this
        }

        // We are going to generate a single triangle
        val vertices = listOf(
            VertexWithColor( 1.0f, 2.0f, 0.0f, 0xffff0000.toInt()),
            VertexWithColor(-1.0f, 3.0f, 0.0f, 0xff00ff00.toInt()),
            VertexWithColor(-1.0f, 1.0f, 0.0f, 0xff0000ff.toInt()),
        )

        val vertexData = ByteBuffer.allocate(vertices.size * VERTEX_POSITION_WITH_COLOR_SIZE)
            // It is important to respect the native byte order
            .order(ByteOrder.nativeOrder())
            .apply { vertices.forEach { put(it) } }
            .flip()

        // Declare the layout of our mesh
        triangleVertexBuffer = VertexBuffer.Builder()
            .bufferCount(1)
            .vertexCount(vertices.size)
            // Because we interleave position and color data (i.e. having both position and color
            // data in same source) we must specify offset and stride.
            // We could use de-interleaved data by declaring two buffers and giving each
            // attribute a different buffer index
            .attribute(VertexAttribute.POSITION, 0, AttributeType.FLOAT3, 0,              VERTEX_POSITION_WITH_COLOR_SIZE)
            .attribute(VertexAttribute.COLOR,    0, AttributeType.UBYTE4, 3 * FLOAT_SIZE, VERTEX_POSITION_WITH_COLOR_SIZE)
            // We store colors as unsigned bytes but since we want values between 0 and 1
            // in the material (shaders), we must mark the attribute as normalized
            .normalized(VertexAttribute.COLOR)
            .build(modelViewer.engine)

        // Feed the vertex data to the mesh
        // We only set 1 buffer because the data is interleaved
        triangleVertexBuffer.setBufferAt(modelViewer.engine, 0, vertexData)

        // Create the indices
        val indices = shortArrayOf(0, 1, 2)
        triangleIndexBuffer = modelViewer.engine.createIndexBuffer(indices)

        assets.readCompressedAsset("materials/baked_color.filamat").let {
            triangleMaterial = Material.Builder().payload(it, it.remaining()).build(modelViewer.engine)
            triangleMaterial.compile(
                Material.CompilerPriorityQueue.HIGH,
                Material.UserVariantFilterBit.ALL,
                Handler(Looper.getMainLooper()),
            ) {
                Log.i(TAG, "Material " + triangleMaterial.name + " compiled.")
            }
            modelViewer.engine.flush()
        }

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

    private fun addLine() {
        val vertices = floatArrayOf(
            -1f, 1f, 8f,     // Point 1
            -0.5f, 0.8f, 5f, // Point 2
            0.3f, 0.5f, 3f,  // Point 3
            0.5f, 0.5f, 2f,  // Point 4
            1f, 0.5f, 1f,    // Point 5
        )

        val vertexCount = vertices.size / 3

        // Step 1: Create a Vertex Buffer
        val vertexData = createBufferDataFromVertices(vertices)

        lineVertexBuffer = VertexBuffer.Builder()
            .bufferCount(1)
            .vertexCount(vertexCount)
            .attribute(VertexAttribute.POSITION, 0, AttributeType.FLOAT3, 0, VERTEX_POSITION_SIZE)
            .build(modelViewer.engine)

        lineVertexBuffer.setBufferAt(modelViewer.engine, 0, vertexData)

        // Step 2: Create an Index Buffer
        val indices = shortArrayOf(
            0, 1,  // Connect vertex 0 to vertex 1
            1, 2,  // Connect vertex 1 to vertex 2
            2, 3,  // Connect vertex 2 to vertex 3
            3, 4,  // Connect vertex 3 to vertex 4
        )
        lineIndexBuffer = modelViewer.engine.createIndexBuffer(indices)

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

    private fun addTransparentTexture() {
        // Define vertex points. Ensure that the width to height ratio matched that of the asset.
        // Better way to identify the aspect ratio is via first loading the asset into bitmap and
        // then get width & height
        val vertexPoints = floatArrayOf(
            -1.0f, 0.683f, 0.0f,  // Top-left
            -1.0f, 0.0f, 0.0f,    // Bottom-left
            1.0f, 0.683f, 0.0f,   // Top-right
            1.0f, 0.0f, 0.0f,     // Bottom-right
        )

        val vertexCount = vertexPoints.size / 3

        // Create vertex data and set it to buffer
        val vertexData = FloatBuffer.allocate(vertexPoints.size)
            .put(vertexPoints)
            .flip()

        // This will be used for texture mapping. This follows 2d mapping as we're going to
        // use 2d texture.
        // Note: Ensure that the uvPoints ordering matches vertexPoints ordering
        val uvPoints = floatArrayOf(
            0.0f, 1.0f,   // Top-left
            0.0f, 0.0f,   // Bottom-left
            1.0f, 1.0f,   // Top-right
            1.0f, 0.0f,   // Bottom-right
        )

        val uvData = FloatBuffer.allocate(uvPoints.size)
            .put(uvPoints)
            .flip()

        transparentVertexBuffer = VertexBuffer.Builder()
            .bufferCount(2)
            .vertexCount(vertexCount)
            .attribute(VertexAttribute.POSITION, 0, AttributeType.FLOAT3, 0, VERTEX_POSITION_SIZE)
            .attribute(VertexAttribute.UV0, 1, AttributeType.FLOAT2, 0, UV_SIZE)
            .build(modelViewer.engine)

        transparentVertexBuffer.setBufferAt(modelViewer.engine, 0, vertexData)
        transparentVertexBuffer.setBufferAt(modelViewer.engine, 1, uvData)

        // Define vertex indices which represents triangles
        val indices = shortArrayOf(
            0, 1, 2,
            2, 1, 3,
        )
        transparentIndexBuffer = modelViewer.engine.createIndexBuffer(indices)

        // Load material
        assets.readCompressedAsset("materials/transparent_image.filamat").also {
            transparentMaterial = Material.Builder()
                .payload(it, it.remaining())
                .build(modelViewer.engine)
        }

        val materialInstance = transparentMaterial.createInstance().apply {
            setParameter("texture", modelViewer.engine.buildTextureFromImageResource(R.drawable.text_yorker, resources), TextureSampler())
        }

        // Create entity
        transparentEntity = EntityManager.get().create()

        RenderableManager.Builder(1)
            .boundingBox(Box(0.0f, 0.0f, 0.0f, 1.0f, 1.0f, 0.01f))
            .geometry(0, PrimitiveType.TRIANGLES, transparentVertexBuffer, transparentIndexBuffer, 0, transparentIndexBuffer.indexCount)
            .material(0, materialInstance)
            .build(modelViewer.engine, transparentEntity)

        // Add the entity to the scene to render it
        modelViewer.scene.addEntity(transparentEntity)
    }

    private fun addBallTrajectory() {
        val xStepSize = 0.0003f
        val yStepSize = 0.001f
        val zStepSize = 0.01f

        // Ball start point
        var x = -0.5f
        var y = 1.5f
        var z = 10.0f

        var shouldDecrement = true

        while (z > -10.0f) {
            addTrajectory(x, y, z)

            x += xStepSize

            if (y >= 0.0f && shouldDecrement) {
                y -= yStepSize
            } else {
                shouldDecrement = false
                y += yStepSize
            }

            z -= zStepSize
        }
    }

    private fun addTrajectory(x: Float, y: Float, z: Float) {
        val vertexPoints = floatArrayOf(
            x - ballTrajectoryVertexOffset, y + ballTrajectoryVertexOffset, z,  // Top-left
            x - ballTrajectoryVertexOffset, y - ballTrajectoryVertexOffset, z,  // Bottom-left
            x + ballTrajectoryVertexOffset, y + ballTrajectoryVertexOffset, z,  // Top-right
            x + ballTrajectoryVertexOffset, y - ballTrajectoryVertexOffset, z,  // Bottom-right
        )

        val vertexCount = vertexPoints.size / 3

        val vertexData = FloatBuffer.allocate(vertexPoints.size)
            .put(vertexPoints)
            .flip()

        val vertexBuffer = VertexBuffer.Builder()
            .bufferCount(2)
            .vertexCount(vertexCount)
            .attribute(VertexAttribute.POSITION, 0, AttributeType.FLOAT3, 0, VERTEX_POSITION_SIZE)
            .attribute(VertexAttribute.UV0, 1, AttributeType.FLOAT2, 0, UV_SIZE)
            .build(modelViewer.engine)

        vertexBuffer.setBufferAt(modelViewer.engine, 0, vertexData)
        vertexBuffer.setBufferAt(modelViewer.engine, 1, ballTrajectoryUvData)
        ballTrajectoryVertexBuffers.add(vertexBuffer)

        val entity = EntityManager.get().create()
        ballTrajectoryEntities.add(entity)

        RenderableManager.Builder(1)
            .boundingBox(Box(0.0f, 0.0f, 0.0f, 1.0f, 1.0f, 0.01f))
            .geometry(0, PrimitiveType.TRIANGLES, vertexBuffer, ballTrajectoryIndexBuffer, 0, ballTrajectoryIndexBuffer.indexCount)
            .material(0, ballTrajectoryMaterialInstance)
            .build(modelViewer.engine, entity)

        modelViewer.scene.addEntity(entity)
    }

    private fun addQuad(quad: Quad) {
        val vertexCount = 4
        val vertexData = createBufferDataFromQuad(quad)

        val vertexBuffer = VertexBuffer.Builder()
            .bufferCount(1)
            .vertexCount(vertexCount)  // Quad has 4 vertices
            .attribute(VertexAttribute.POSITION, 0, AttributeType.FLOAT3, 0, VERTEX_POSITION_SIZE)
            .build(modelViewer.engine)

        vertexBuffer.setBufferAt(modelViewer.engine, 0, vertexData)
        quadVertexBuffers.add(vertexBuffer)

        val quadEntity = EntityManager.get().create()
        quadEntities.add(quadEntity)

        RenderableManager.Builder(1)
            .boundingBox(Box(0.0f, 0.0f, 0.0f, 1.0f, 1.0f, 0.01f))
            .geometry(0, PrimitiveType.TRIANGLES, vertexBuffer, quadIndexBuffer)
            .material(0, quadMaterialInstance)
            .build(modelViewer.engine, quadEntity)

        // Step 5: Add the Line to the Scene
        modelViewer.scene.addEntity(quadEntity)
    }

    private fun addCylinder() {
        val radius = 0.025f
        val interpolationPoints = 50
        val circumferencePoints = 50

        val vertices = listOf(
            Vertex(-0.55f, 1.75f, 10f), // Bowler Stump Point
            Vertex(-0.20f, 0.0f, -4f),  // Pitch Contact Point
            Vertex(0.0f, 0.5f, -10f),   // Batsman Stump Point
        )

        // Create window of two points and then smoothen the curve along those two points
        vertices.windowed(size = 2).forEach { (pointA, pointB) ->
            // Here we want to smoothen the curve between pointA and pointB along z-y axis and the
            // x axis points will be linearly interpolated. Here we get
            val xIncrement = (pointA.x - pointB.x) / (interpolationPoints - 1)

            // Calculate control point
            val controlPointZ = (pointA.z + pointB.z) / 2
            val controlPointY = if (pointA.y > pointB.y) {
                (pointA.y - pointB.y) * 0.75f
            } else {
                (pointB.y - pointA.y) * 0.75f
            }

            // Generate quadratic Bezier points in Z-Y plane
            val quadraticBezierPoints = quadraticBezier(
                start = Pair(pointA.z, pointA.y),
                control = Pair(controlPointZ, controlPointY),
                end = Pair(pointB.z, pointB.y),
                numOfPoints = interpolationPoints,
            ).mapIndexed { index, (z, y) ->
                Vertex(
                    x = pointA.x - (xIncrement * index),
                    y = y,
                    z = z,
                )
            }

            // Generate circumference points for each Bezier vertex
            val verticesPoints = quadraticBezierPoints.map { vertex ->
                CylinderUtils.getPointsAlongCircumference(
                    centerX = vertex.x,
                    centerY = vertex.y,
                    centerZ = vertex.z,
                    radius = radius,
                    numOfPoints = circumferencePoints,
                )
            }

            // Create quads between consecutive circumference point sets
            verticesPoints.windowed(2).map { (pointAVertices, pointBVertices) ->
                val quadVertices = CylinderUtils.getQuadVertices(pointAVertices, pointBVertices)

                quadVertices.forEach { quad ->
                    addQuad(quad)
                }
            }
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

        // Cleanup transparent texture resources
        if (::transparentVertexBuffer.isInitialized) {
            modelViewer.engine.destroyEntity(transparentEntity)
            modelViewer.engine.destroyVertexBuffer(transparentVertexBuffer)
            modelViewer.engine.destroyIndexBuffer(transparentIndexBuffer)
            modelViewer.engine.destroyMaterial(transparentMaterial)
        }

        // Cleanup ball trajectory resources
        if (ballTrajectoryEntities.isNotEmpty()) {
            ballTrajectoryEntities.forEach { entity ->
                modelViewer.engine.destroyEntity(entity)
            }
            ballTrajectoryEntities.clear()

            ballTrajectoryVertexBuffers.forEach { vertexBuffer ->
                modelViewer.engine.destroyVertexBuffer(vertexBuffer)
            }
            ballTrajectoryVertexBuffers.clear()

            modelViewer.engine.destroyIndexBuffer(ballTrajectoryIndexBuffer)
            modelViewer.engine.destroyMaterial(ballTrajectoryMaterial)
        }

        // Cleanup quad resources
        if (quadEntities.isNotEmpty()) {
            quadEntities.forEach { entity ->
                modelViewer.engine.destroyEntity(entity)
            }
            quadEntities.clear()

            quadVertexBuffers.forEach { vertexBuffer ->
                modelViewer.engine.destroyVertexBuffer(vertexBuffer)
            }
            quadVertexBuffers.clear()

            modelViewer.engine.destroyIndexBuffer(quadIndexBuffer)
            modelViewer.engine.destroyMaterial(quadMaterial)
        }
    }

    inner class FrameCallback : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            choreographer.postFrameCallback(this)
            modelViewer.render(frameTimeNanos)
        }
    }
}
