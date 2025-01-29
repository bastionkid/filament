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

package com.google.android.filament.utils

import android.view.MotionEvent
import android.view.Surface
import android.view.SurfaceView
import android.view.TextureView
import com.google.android.filament.Camera
import com.google.android.filament.Colors
import com.google.android.filament.Engine
import com.google.android.filament.Entity
import com.google.android.filament.EntityManager
import com.google.android.filament.Fence
import com.google.android.filament.IndirectLight
import com.google.android.filament.LightManager
import com.google.android.filament.Renderer
import com.google.android.filament.Scene
import com.google.android.filament.Skybox
import com.google.android.filament.SwapChain
import com.google.android.filament.View
import com.google.android.filament.Viewport
import com.google.android.filament.android.DisplayHelper
import com.google.android.filament.android.UiHelper
import com.google.android.filament.gltfio.Animator
import com.google.android.filament.gltfio.AssetLoader
import com.google.android.filament.gltfio.FilamentAsset
import com.google.android.filament.gltfio.MaterialProvider
import com.google.android.filament.gltfio.ResourceLoader
import com.google.android.filament.gltfio.UbershaderProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.Buffer

private const val kNearPlane = 0.05f     // 5 cm
private const val kFarPlane = 1000.0f    // 1 km
private const val kAperture = 24f // higher number increases depth of field and image looks more darker
private const val kShutterSpeed = 1f / 125f
private const val kSensitivity = 100f
private const val kFocalLength = 28f
const val kOrbitSpeed = 0.001f
const val kZoomSpeed = 0.1f
const val kEyeYMovementPerPixel = kOrbitSpeed * 20 // 20 is derived from changing kOrbitSpeed and verifying the relative Eye y movement
const val kEyeYMinThreshold = 1.5

/**
 * Helps render glTF models into a [SurfaceView] or [TextureView] with an orbit controller.
 *
 * `ModelViewer` owns a Filament engine, renderer, swapchain, view, and scene. It allows clients
 * to access these objects via read-only properties. The viewer can display only one glTF scene
 * at a time, which can be scaled and translated into the viewing frustum by calling
 * [clearRootTransform]. All ECS entities can be accessed and modified via the [asset] property.
 *
 * For GLB files, clients can call [loadModelGlb] and pass in a [Buffer] with the contents of the
 * GLB file.
 *
 * `ModelViewer` reduces much of the boilerplate required for simple Filament applications, but
 * clients still have the responsibility of adding an [IndirectLight] and [Skybox] to the scene.
 * Additionally, clients should:
 *
 * 1. Pass the model viewer into [SurfaceView.setOnTouchListener] or call its [onTouchEvent]
 *    method from your touch handler.
 * 2. Call [render] and [Animator.applyAnimation] from a `Choreographer` frame callback.
 *
 * NOTE: if its associated SurfaceView or TextureView has become detached from its window, the
 * ModelViewer becomes invalid and must be recreated.
 *
 * See `sample-gltf-viewer` for a usage example.
 */
class ModelViewer(
    val engine: Engine,
    private val uiHelper: UiHelper
) {
    var asset: FilamentAsset? = null
        private set

    var animator: Animator? = null
        private set

    @Suppress("unused")
    val progress
        get() = resourceLoader.asyncGetLoadProgress()

    var normalizeSkinningWeights = true

    var cameraFocalLength = kFocalLength
        set(value) {
            field = value
            updateCameraProjection()
        }

    var cameraNear = kNearPlane
        set(value) {
            field = value
            updateCameraProjection()
        }

    var cameraFar = kFarPlane
        set(value) {
            field = value
            updateCameraProjection()
        }

    val scene: Scene by lazy { engine.createScene() }
    val view: View by lazy { engine.createView() }
    val camera: Camera by lazy { engine.createCamera(engine.entityManager.create()).apply { setExposure(kAperture, kShutterSpeed, kSensitivity) } }
    val renderer: Renderer by lazy { engine.createRenderer() }
    @get:Entity
    val light: Int by lazy { EntityManager.get().create() }

    private lateinit var displayHelper: DisplayHelper
    private lateinit var cameraManipulator: Manipulator
    private lateinit var gestureDetector: GestureDetector
    private var surfaceView: SurfaceView? = null
    private var textureView: TextureView? = null

    private var fetchResourcesJob: Job? = null

    private var swapChain: SwapChain? = null
    private var assetLoader: AssetLoader
    private var materialProvider: MaterialProvider
    private var resourceLoader: ResourceLoader

    private val eyePos = DoubleArray(3)
    private val target = DoubleArray(3)
    private val upward = DoubleArray(3)

    init {
        view.scene = scene
        view.camera = camera

        materialProvider = UbershaderProvider(engine)
        assetLoader = AssetLoader(engine, materialProvider, EntityManager.get())
        resourceLoader = ResourceLoader(engine, normalizeSkinningWeights)

        // Always add a direct light source since it is required for shadowing.
        // We highly recommend adding an indirect light as well.

        val (r, g, b) = Colors.cct(6_500.0f)
        LightManager.Builder(LightManager.Type.DIRECTIONAL)
            .color(r, g, b)
            .intensity(100_000.0f)
            .direction(0.0f, -1.0f, 0.0f)
            .castShadows(true)
            .build(engine, light)

        scene.addEntity(light)
    }

    constructor(
        surfaceView: SurfaceView,
        engine: Engine = Engine.create(),
        uiHelper: UiHelper = UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK),
        manipulator: Manipulator? = null
    ) : this(engine, uiHelper) {
        cameraManipulator = manipulator ?: getDefaultCameraManipulator(surfaceView.width, surfaceView.height)

        this.surfaceView = surfaceView
        gestureDetector = GestureDetector(surfaceView, cameraManipulator)
        displayHelper = DisplayHelper(surfaceView.context)
        uiHelper.renderCallback = SurfaceCallback()
        uiHelper.attachTo(surfaceView)
        addDetachListener(surfaceView)
    }

    @Suppress("unused")
    constructor(
        textureView: TextureView,
        engine: Engine = Engine.create(),
        uiHelper: UiHelper = UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK),
        manipulator: Manipulator? = null
    ) : this(engine, uiHelper) {
        cameraManipulator = manipulator ?: getDefaultCameraManipulator(textureView.width, textureView.height)

        this.textureView = textureView
        gestureDetector = GestureDetector(textureView, cameraManipulator)
        displayHelper = DisplayHelper(textureView.context)
        uiHelper.renderCallback = SurfaceCallback()
        uiHelper.attachTo(textureView)
        addDetachListener(textureView)
    }

    private fun getDefaultCameraManipulator(viewPortWidth: Int, viewPortHeight: Int): Manipulator {
        return Manipulator.Builder()
            .orbitHomePosition(kDefaultEyePosition.x, kDefaultEyePosition.y, kDefaultEyePosition.z)
            .targetPosition(kDefaultTargetPosition.x, kDefaultTargetPosition.y, kDefaultTargetPosition.z)
            .orbitSpeed(kOrbitSpeed, kOrbitSpeed)
            .zoomSpeed(kZoomSpeed)
            .viewport(viewPortWidth, viewPortHeight)
            .build(Manipulator.Mode.ORBIT)
    }

    /**
     * Loads a monolithic binary glTF and populates the Filament scene.
     */
    fun loadModelGlb(buffer: Buffer) {
        destroyModel()
        asset = assetLoader.createAsset(buffer)
        asset?.let { asset ->
            resourceLoader.asyncBeginLoad(asset)
            animator = asset.instance.animator
            asset.releaseSourceData()
        }
    }

    /**
     * Loads a JSON-style glTF file and populates the Filament scene.
     *
     * The given callback is triggered from a worker thread for each requested resource.
     */
    fun loadModelGltfAsync(buffer: Buffer, callback: (String) -> Buffer) {
        destroyModel()
        asset = assetLoader.createAsset(buffer)
        fetchResourcesJob = CoroutineScope(Dispatchers.IO).launch {
            fetchResources(asset!!, callback)
        }
    }

    fun clearRootTransform() {
        asset?.let {
            val tm = engine.transformManager
            // Use Identity matrix as transform
            val transform = Mat4()
            tm.setTransform(tm.getInstance(it.root), transform.toFloatArray())
        }
    }

    /**
     * Frees all entities associated with the most recently-loaded model.
     */
    fun destroyModel() {
        fetchResourcesJob?.cancel()
        resourceLoader.asyncCancelLoad()
        resourceLoader.evictResourceData()
        asset?.let { asset ->
            this.scene.removeEntities(asset.entities)
            assetLoader.destroyAsset(asset)
            this.asset = null
            this.animator = null
        }
    }

    /**
     * Renders the model and updates the Filament camera.
     *
     * @param frameTimeNanos time in nanoseconds when the frame started being rendered,
     *                       typically comes from {@link android.view.Choreographer.FrameCallback}
     */
    fun render(frameTimeNanos: Long) {
        if (!uiHelper.isReadyToRender) {
            return
        }

        // Allow the resource loader to finalize textures that have become ready.
        resourceLoader.asyncUpdateLoad()

        // Add renderable entities to the scene as they become ready.
        asset?.let { populateScene(it) }

        // Extract the camera basis from the helper and push it to the Filament camera.
        cameraManipulator.getLookAt(eyePos, target, upward)
        camera.lookAt(
            eyePos[0], eyePos[1], eyePos[2],
            target[0], target[1], target[2],
            upward[0], upward[1], upward[2],
        )

        // Render the scene, unless the renderer wants to skip the frame.
        if (renderer.beginFrame(swapChain!!, frameTimeNanos)) {
            renderer.render(view)
            renderer.endFrame()
        }
    }

    private fun populateScene(asset: FilamentAsset) {
        scene.addEntities(asset.renderableEntities)
        scene.addEntities(asset.lightEntities)
    }

    private fun addDetachListener(view: android.view.View) {
        view.addOnAttachStateChangeListener(object : android.view.View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: android.view.View) {}
            override fun onViewDetachedFromWindow(v: android.view.View) {
                uiHelper.detach()

                destroyModel()
                assetLoader.destroy()
                materialProvider.destroyMaterials()
                materialProvider.destroy()
                resourceLoader.destroy()

                engine.destroyEntity(light)
                engine.destroyRenderer(renderer)
                engine.destroyView(this@ModelViewer.view)
                engine.destroyScene(scene)
                engine.destroyCameraComponent(camera.entity)
                EntityManager.get().destroy(camera.entity)

                EntityManager.get().destroy(light)

                engine.destroy()
            }
        })
    }

    /**
     * Handles a [MotionEvent] to enable one-finger orbit, two-finger pan, and pinch-to-zoom.
     */
    fun onTouchEvent(event: MotionEvent) {
        gestureDetector.onTouchEvent(event)
    }

    private suspend fun fetchResources(asset: FilamentAsset, callback: (String) -> Buffer) {
        val items = HashMap<String, Buffer>()
        val resourceUris = asset.resourceUris
        for (resourceUri in resourceUris) {
            items[resourceUri] = callback(resourceUri)
        }

        withContext(Dispatchers.Main) {
            for ((uri, buffer) in items) {
                resourceLoader.addResourceData(uri, buffer)
            }
            resourceLoader.asyncBeginLoad(asset)
            animator = asset.instance.animator
            asset.releaseSourceData()
        }
    }

    private fun updateCameraProjection() {
        val width = view.viewport.width
        val height = view.viewport.height
        val aspect = width.toDouble() / height.toDouble()
        camera.setLensProjection(cameraFocalLength.toDouble(), aspect,
            cameraNear.toDouble(), cameraFar.toDouble())
    }

    inner class SurfaceCallback : UiHelper.RendererCallback {
        override fun onNativeWindowChanged(surface: Surface) {
            swapChain?.let { engine.destroySwapChain(it) }
            swapChain = engine.createSwapChain(surface)
            surfaceView?.let { displayHelper.attach(renderer, it.display) }
            textureView?.let { displayHelper.attach(renderer, it.display) }
        }

        override fun onDetachedFromSurface() {
            displayHelper.detach()
            swapChain?.let {
                engine.destroySwapChain(it)
                engine.flushAndWait()
                swapChain = null
            }
        }

        override fun onResized(width: Int, height: Int) {
            view.viewport = Viewport(0, 0, width, height)
            cameraManipulator.setViewport(width, height)
            updateCameraProjection()
            synchronizePendingFrames(engine)
        }
    }

    private fun synchronizePendingFrames(engine: Engine) {
        // Wait for all pending frames to be processed before returning. This is to
        // avoid a race between the surface being resized before pending frames are
        // rendered into it.
        val fence = engine.createFence()
        fence.wait(Fence.Mode.FLUSH, Fence.WAIT_FOR_EVER)
        engine.destroyFence(fence)
    }

    companion object {
        // Cricket Pitch width is 2.64m & batsman wicket to bowler wickets length is 20.12m
        private val kDefaultEyePosition = Float3(0.0f, 1.65f, 14.0f)
        private val kDefaultTargetPosition = Float3(0.0f, 0.0f, -4f)
    }
}
