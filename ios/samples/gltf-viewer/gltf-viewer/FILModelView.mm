/*
 * Copyright (C) 2021 The Android Open Source Project
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

// These defines are set in the "Preprocessor Macros" build setting for each scheme.
#if !FILAMENT_APP_USE_METAL && !FILAMENT_APP_USE_OPENGL
#error A valid FILAMENT_APP_USE_ backend define must be set.
#endif

#include "FILModelView.h"

#import <Foundation/Foundation.h>

#include <filament/Camera.h>
#include <filament/Color.h>
#include <filament/Engine.h>
#include <filament/IndirectLight.h>
#include <filament/LightManager.h>
#include <filament/RenderableManager.h>
#include <filament/Renderer.h>
#include <filament/Scene.h>
#include <filament/Skybox.h>
#include <filament/TransformManager.h>
#include <filament/View.h>
#include <filament/Viewport.h>

#include <gltfio/AssetLoader.h>
#include <gltfio/ResourceLoader.h>
#include <gltfio/TextureProvider.h>
#include <gltfio/materials/uberarchive.h>

#include <utils/EntityManager.h>
#include <utils/NameComponentManager.h>

#include <camutils/Manipulator.h>

using namespace filament;
using namespace utils;
using namespace filament::gltfio;
using namespace camutils;

const double kNearPlane = 0.05;   // 5 cm
const double kFarPlane = 1000.0;  // 1 km
const float kScaleMultiplier = 100.0f;
const float kAperture = 24.0f; // higher number increases depth of field and image looks more darker
const float kShutterSpeed = 1.0f / 125.0f;
const float kSensitivity = 100.0f;
const float kFocalLength = 28.0f;
const float kOrbitSpeed = 0.001f;
const float kZoomSpeed = 0.1f;
const float kEyeYMovementPerPixel = kOrbitSpeed * 18.0f; // 18 is derived from changing kOrbitSpeed and verifying the relative Eye y movement manually
const float kEyeYMinThreshold = 1.5f;
const float kZoomScaleMin = 0.25f;
const float kZoomScaleMax = 1.5f;

// Cricket Pitch width is 2.64m & batsman wicket to bowler wickets length is 20.12m
const float kDefaultEyePositionX = 0.0f;
const float kDefaultEyePositionY = 1.65f;
const float kDefaultEyePositionZ = 14.0f;
const float kDefaultTargetPositionX = 0.0f;
const float kDefaultTargetPositionY = 0.0f;
const float kDefaultTargetPositionZ = -4.0f;

@interface FILModelView ()

- (void)initCommon;
- (void)updateViewportAndCameraProjection;
- (void)didPan:(UIGestureRecognizer*)sender;
- (void)didPinch:(UIGestureRecognizer*)sender;

@end

@implementation FILModelView {
    Camera* _camera;
    SwapChain* _swapChain;

    struct {
        Entity camera;
    } _entities;

    MaterialProvider* _materialProvider;
    AssetLoader* _assetLoader;
    ResourceLoader* _resourceLoader;

    Manipulator<float>* _manipulator;
    TextureProvider* _stbDecoder;
    TextureProvider* _ktxDecoder;

    FilamentAsset* _asset;

    UIPanGestureRecognizer* _panRecognizer;
    UIPinchGestureRecognizer* _pinchRecognizer;
    CGFloat _previousScale;
    CGFloat _currentZoomScale;
    CGFloat _lastYPosition;
}

- (instancetype)initWithFrame:(CGRect)frame {
    if (self = [super initWithFrame:frame]) {
        [self initCommon];
    }
    return self;
}

- (instancetype)initWithCoder:(NSCoder*)coder {
    if (self = [super initWithCoder:coder]) {
        [self initCommon];
    }
    return self;
}

- (void)initCommon {
    self.contentScaleFactor = UIScreen.mainScreen.nativeScale;
    [self initializeMetalLayer];
    _engine = Engine::create(Engine::Backend::METAL);

    _renderer = _engine->createRenderer();
    _scene = _engine->createScene();
    _entities.camera = EntityManager::get().create();
    _camera = _engine->createCamera(_entities.camera);
    _view = _engine->createView();
    _view->setScene(_scene);
    _view->setCamera(_camera);

    _cameraFocalLength = kFocalLength;
    _camera->setExposure(kAperture, kShutterSpeed, kSensitivity);

    _swapChain = _engine->createSwapChain((__bridge void*)self.layer);

    _materialProvider =
            createUbershaderProvider(_engine, UBERARCHIVE_DEFAULT_DATA, UBERARCHIVE_DEFAULT_SIZE);
    EntityManager& em = EntityManager::get();
    NameComponentManager* ncm = new NameComponentManager(em);
    _assetLoader = AssetLoader::create({_engine, _materialProvider, ncm, &em});
    _resourceLoader = new ResourceLoader({.engine = _engine, .normalizeSkinningWeights = true});
    _stbDecoder = createStbProvider(_engine);
    _ktxDecoder = createKtx2Provider(_engine);
    _resourceLoader->addTextureProvider("image/png", _stbDecoder);
    _resourceLoader->addTextureProvider("image/jpeg", _stbDecoder);
    _resourceLoader->addTextureProvider("image/ktx2", _ktxDecoder);

    _manipulator = Manipulator<float>::Builder()
        .orbitHomePosition(kDefaultEyePositionX, kDefaultEyePositionY, kDefaultEyePositionZ)
        .targetPosition(kDefaultTargetPositionX, kDefaultTargetPositionY, kDefaultTargetPositionZ)
        .orbitSpeed(kOrbitSpeed, kOrbitSpeed)
        .zoomSpeed(kZoomSpeed)
        .build(Mode::ORBIT);

    // Set up pan and pinch gesture recognizers, used to orbit, zoom, and translate the camera.
    _panRecognizer = [[UIPanGestureRecognizer alloc] initWithTarget:self action:@selector(didPan:)];
    _panRecognizer.minimumNumberOfTouches = 1;
    // Disable PAN gesture by setting the maximumNumberOfTouches to 1 instead of 2
    _panRecognizer.maximumNumberOfTouches = 1;
    _pinchRecognizer = [[UIPinchGestureRecognizer alloc] initWithTarget:self action:@selector(didPinch:)];

    [self addGestureRecognizer:_panRecognizer];
    [self addGestureRecognizer:_pinchRecognizer];
    _previousScale = 1.0f;
    _currentZoomScale = 1.0f;

    _asset = nullptr;
}

#pragma mark UIView methods

- (void)layoutSubviews {
    [super layoutSubviews];
    [self updateViewportAndCameraProjection];
}

- (void)setContentScaleFactor:(CGFloat)contentScaleFactor {
    [super setContentScaleFactor:contentScaleFactor];
    [self updateViewportAndCameraProjection];
}

#pragma mark FILModelView methods

- (void)destroyModel {
    if (!_asset) {
        return;
    }
    _resourceLoader->evictResourceData();
    _scene->removeEntities(_asset->getEntities(), _asset->getEntityCount());
    _assetLoader->destroyAsset(_asset);
    _asset = nullptr;
    _animator = nullptr;
}

- (void)issuePickQuery:(CGPoint)point callback:(PickCallback)callback {
    CGPoint pointOriginBottomLeft = CGPointMake(point.x, self.bounds.size.height - point.y);
    CGPoint pointScaled = CGPointMake(pointOriginBottomLeft.x * self.contentScaleFactor,
            pointOriginBottomLeft.y * self.contentScaleFactor);
    _view->pick(pointScaled.x, pointScaled.y,
            [callback](View::PickingQueryResult const& result) { callback(result.renderable); });
}

- (NSString* _Nullable)getEntityName:(utils::Entity)entity {
    if (!_asset) {
        return nil;
    }
    NameComponentManager* ncm = _assetLoader->getNames();
    NameComponentManager::Instance instance = ncm->getInstance(entity);
    if (instance) {
        return [NSString stringWithUTF8String:ncm->getName(ncm->getInstance(entity))];
    }
    return nil;
}

- (void)transformToUnitCube {
    if (!_asset) {
        return;
    }
    auto& tm = _engine->getTransformManager();
    auto aabb = _asset->getBoundingBox();
    auto center = aabb.center();
    auto halfExtent = aabb.extent();
    auto maxExtent = max(halfExtent) * 2;
    auto scaleFactor = 2.0f / maxExtent;
    auto transform = math::mat4f::scaling(scaleFactor) * math::mat4f::translation(-center);
    tm.setTransform(tm.getInstance(_asset->getRoot()), transform);
}

- (void)transformToRoot {
    if (!_asset) {
        return;
    }
    auto& tm = _engine->getTransformManager();
    // Use Identity matrix as transform
    auto transform = math::mat4f();
    tm.setTransform(tm.getInstance(_asset->getRoot()), transform);
}

- (void)loadModelGlb:(NSData*)buffer {
    [self destroyModel];
    _asset = _assetLoader->createAsset(
            static_cast<const uint8_t*>(buffer.bytes), static_cast<uint32_t>(buffer.length));

    if (!_asset) {
        return;
    }

    _scene->addEntities(_asset->getEntities(), _asset->getEntityCount());
    _resourceLoader->loadResources(_asset);
    _animator = _asset->getInstance()->getAnimator();
    _asset->releaseSourceData();
}

- (void)loadModelGltf:(NSData*)buffer callback:(ResourceCallback)callback {
    [self destroyModel];
    _asset = _assetLoader->createAsset(
            static_cast<const uint8_t*>(buffer.bytes), static_cast<uint32_t>(buffer.length));

    if (!_asset) {
        return;
    }

    auto destroy = [](void*, size_t, void* userData) { CFBridgingRelease(userData); };

    const char* const* const resourceUris = _asset->getResourceUris();
    const size_t resourceUriCount = _asset->getResourceUriCount();
    for (size_t i = 0; i < resourceUriCount; i++) {
        const char* const uri = resourceUris[i];
        NSString* uriString = [NSString stringWithCString:uri encoding:NSUTF8StringEncoding];
        NSData* data = callback(uriString);
        ResourceLoader::BufferDescriptor b(
                data.bytes, data.length, destroy, (void*)CFBridgingRetain(data));
        _resourceLoader->addResourceData(uri, std::move(b));
    }

    _resourceLoader->loadResources(_asset);
    _animator = _asset->getInstance()->getAnimator();
    _asset->releaseSourceData();

    _scene->addEntities(_asset->getEntities(), _asset->getEntityCount());
}

- (void)render {
    // Extract the camera basis from the helper and push it to the Filament camera.
    math::float3 eye, target, upward;
    _manipulator->getLookAt(&eye, &target, &upward);
    _camera->lookAt(eye, target, upward);

    // Render the scene, unless the renderer wants to skip the frame.
    if (_renderer->beginFrame(_swapChain)) {
        _renderer->render(_view);
        _renderer->endFrame();
    }
}

- (void)showEntity:(NSString*)entityName {
    Entity entity = _asset->getFirstEntityByName([entityName UTF8String]);
    auto& rm = _engine->getRenderableManager();
    
    if (rm.hasComponent(entity)) {
        rm.setLayerMask(rm.getInstance(entity), 0xFF, 1);
    }
}

- (void)hideEntity:(NSString*)entityName {
    Entity entity = _asset->getFirstEntityByName([entityName UTF8String]);
    auto& rm = _engine->getRenderableManager();
    
    if (rm.hasComponent(entity)) {
        rm.setLayerMask(rm.getInstance(entity), 0xFF, 0);
    }
}

- (void)translateEntity:(float)x :(float)y :(float)z :(NSString*)entityName {
    Entity entity = _asset->getFirstEntityByName([entityName UTF8String]);
    auto& tm = _engine->getTransformManager();
    
    // First get current transform matrix for the entity to ensure all the non translation related
    // properties are preserved after applying transformation
    auto transform = tm.getTransform(tm.getInstance(entity));
    
    // indices (12, 13, 14) represents (x, y, z) co-ordinates in the transformation matrix
    transform[3][0] = x;
    transform[3][1] = y;
    transform[3][2] = z;
    
    tm.setTransform(tm.getInstance(entity), transform);
}

- (void)dealloc {
    [self destroyModel];

    _materialProvider->destroyMaterials();
    delete _materialProvider;
    auto* ncm = _assetLoader->getNames();
    delete ncm;
    AssetLoader::destroy(&_assetLoader);
    delete _resourceLoader;

    delete _manipulator;
    delete _stbDecoder;
    delete _ktxDecoder;

    _engine->destroy(_swapChain);
    _engine->destroy(_view);
    EntityManager::get().destroy(_entities.camera);
    _engine->destroyCameraComponent(_entities.camera);
    _engine->destroy(_scene);
    _engine->destroy(_renderer);
    _engine->destroy(&_engine);
}

#pragma mark ModelViewer properties

- (void)setCameraFocalLength:(float)cameraFocalLength {
    _cameraFocalLength = cameraFocalLength;
    [self updateViewportAndCameraProjection];
}

#pragma mark Private

- (void)initializeMetalLayer {
    CAMetalLayer* metalLayer = (CAMetalLayer*)self.layer;
    metalLayer.pixelFormat = MTLPixelFormatBGRA8Unorm;
    metalLayer.opaque = YES;
}

- (void)updateViewportAndCameraProjection {
    if (!_view || !_camera || !_manipulator) {
        return;
    }

    _manipulator->setViewport(self.bounds.size.width, self.bounds.size.height);

    const uint32_t width = self.bounds.size.width * self.contentScaleFactor;
    const uint32_t height = self.bounds.size.height * self.contentScaleFactor;
    _view->setViewport({0, 0, width, height});

    CAMetalLayer* metalLayer = (CAMetalLayer*)self.layer;
    metalLayer.drawableSize = CGSizeMake(width, height);

    const double aspect = (double)width / height;
    _camera->setLensProjection(self.cameraFocalLength, aspect, kNearPlane, kFarPlane);
}

- (void)didPan:(UIPanGestureRecognizer*)sender {
    CGPoint location = [sender locationInView:self];
    location.y = self.bounds.size.height - location.y;
    if (sender.state == UIGestureRecognizerStateBegan) {
        const bool strafe = _panRecognizer.numberOfTouches == 2;
        _manipulator->grabBegin(location.x, location.y, strafe);
        _lastYPosition = location.y;
    } else if (sender.state == UIGestureRecognizerStateChanged) {
        // Check if the orbit rotation is going up, because then only we want to
        // enforce Eye position y co-ordinate minimum threshold
        if (location.y > _lastYPosition) {
            // Get the eye position
            math::float3 eye, target, upward;
            _manipulator->getLookAt(&eye, &target, &upward);
            
            // Calculate maximum y axis movement allowed before it reaches threshold
            CGFloat maxAllowedYDelta = eye.y - kEyeYMinThreshold;
            CGFloat maxYPixelMovementAllowed = (maxAllowedYDelta / kEyeYMovementPerPixel) + _lastYPosition;
            
            // If the current touch will cause y movement beyond the threshold, then end gesture
            if (location.y < maxYPixelMovementAllowed) {
                _manipulator->grabUpdate(location.x, location.y);
                _lastYPosition = location.y;
            } else {
                _manipulator->grabEnd();
                _lastYPosition = location.y;
            }
        } else {
            _manipulator->grabUpdate(location.x, location.y);
            _lastYPosition = location.y;
        }
    } else if (sender.state == UIGestureRecognizerStateEnded || sender.state == UIGestureRecognizerStateFailed) {
        _manipulator->grabEnd();
    }
}

- (void)didPinch:(UIGestureRecognizer*)sender {
    CGPoint location = [sender locationInView:self];
    location.y = self.bounds.size.height - location.y;
    if (sender.state == UIGestureRecognizerStateBegan) {
        _previousScale = 1.0f;
        CGFloat deltaScale = _pinchRecognizer.scale - _previousScale;
        _currentZoomScale = _currentZoomScale - deltaScale;
        _previousScale = _pinchRecognizer.scale;
    } else if (sender.state == UIGestureRecognizerStateChanged) {
        CGFloat deltaScale = _pinchRecognizer.scale - _previousScale;
        _currentZoomScale = _currentZoomScale - deltaScale;
        
        // Ensure that the _currentZoomScale is clamped to kZoomScaleMin in case of zoom in and
        // to kZoomScaleMax in case of zoom out
        if (_currentZoomScale < kZoomScaleMin) {
            _currentZoomScale = kZoomScaleMin;
        } else if (_currentZoomScale > kZoomScaleMax) {
            _currentZoomScale = kZoomScaleMax;
        }
        
        // Here we limit the zoom in & out range via comparing _currentZoomScale with kZoomScaleMin and kZoomScaleMax
        if (_currentZoomScale == kZoomScaleMin || _currentZoomScale == kZoomScaleMax) {
            _manipulator->grabEnd();
        } else {
            _manipulator->scroll(location.x, location.y, -deltaScale * kScaleMultiplier);
            _previousScale = _pinchRecognizer.scale;
        }
    }
}

+ (Class)layerClass {
#if FILAMENT_APP_USE_OPENGL
    return [CAEAGLLayer class];
#elif FILAMENT_APP_USE_METAL
    return [CAMetalLayer class];
#endif
}

@end
