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

#import "FILViewController.h"
#include <UIKit/UIKit.h>

#import "FILModelView.h"
#import "Vertex.h"
#import "Quad.h"
#import "QuadraticBezier.h"
#import "CylinderUtils.h"

#include <filament/Engine.h>
#include <filament/Scene.h>
#include <filament/Skybox.h>
#include <filament/IndexBuffer.h>
#include <filament/Material.h>
#include <filament/RenderableManager.h>
#include <filament/VertexBuffer.h>

#include <utils/EntityManager.h>

#include <gltfio/Animator.h>

#include <ktxreader/Ktx1Reader.h>

#include <viewer/AutomationEngine.h>
#include <viewer/RemoteServer.h>

using namespace filament;
using namespace utils;
using namespace ktxreader;

// This file is compiled via the matc tool. See the "Run Script" build phase.
static constexpr uint8_t CYLINDER_MATERIAL[] = {
#include "cylinder.inc"
};

static constexpr float3 TRIANGLE_VERTICES[3] = {
    {0, 2, 0},
    {-1, 0, 0},
    {1, 0, 0}
};

static constexpr uint16_t TRIANGLE_INDICES[3] = { 0, 1, 2 };

@interface FILViewController ()

- (void)startDisplayLink;
- (void)stopDisplayLink;

- (void)createDefaultRenderables;
- (void)createLights;

- (void)appWillResignActive:(NSNotification*)notification;
- (void)appDidBecomeActive:(NSNotification*)notification;

@end

@implementation FILViewController {
    CADisplayLink* _displayLink;
    CFTimeInterval _startTime;
    
    Texture* _skyboxTexture;
    Skybox* _skybox;
    Texture* _iblTexture;
    IndirectLight* _indirectLight;
    Entity _sun;
    Boolean _pitchOverlayVisible;
    Material* _material;
    MaterialInstance* _materialInstance;
    IndexBuffer* _indexBuffer;
}

#pragma mark UIViewController methods

- (void)viewDidLoad {
    [super viewDidLoad];

    // Observe lifecycle notifications to prevent us from rendering in the background.
    [NSNotificationCenter.defaultCenter addObserver:self
                                           selector:@selector(appWillResignActive:)
                                               name:UIApplicationWillResignActiveNotification
                                             object:nil];
    [NSNotificationCenter.defaultCenter addObserver:self
                                           selector:@selector(appDidBecomeActive:)
                                               name:UIApplicationDidBecomeActiveNotification
                                             object:nil];

    // on mobile, better use lower quality color buffer
    RenderQuality renderQuality = RenderQuality();
    renderQuality.hdrColorBuffer = QualityLevel::MEDIUM;
    self.modelView.view->setRenderQuality(renderQuality);
    
    // dynamic resolution often helps a lot
    DynamicResolutionOptions dynamicResolutionOptions = DynamicResolutionOptions();
    dynamicResolutionOptions.enabled = true;
    dynamicResolutionOptions.quality = QualityLevel::MEDIUM;
    self.modelView.view->setDynamicResolutionOptions(dynamicResolutionOptions);
    
    // MSAA is needed with dynamic resolution MEDIUM
    if (dynamicResolutionOptions.quality == QualityLevel::MEDIUM) {
        MultiSampleAntiAliasingOptions multiSampleAntiAliasingOptions = MultiSampleAntiAliasingOptions();
        multiSampleAntiAliasingOptions.enabled = true;
        self.modelView.view->setMultiSampleAntiAliasingOptions(multiSampleAntiAliasingOptions);
    }
    
    // FXAA is pretty cheap and helps a lot
    self.modelView.view->setAntiAliasing(AntiAliasing::FXAA);
    
    // ambient occlusion is the cheapest effect that adds a lot of quality
    //    AmbientOcclusionOptions ambientOcclusionOptions = AmbientOcclusionOptions();
    //    ambientOcclusionOptions.enabled = true;
    //    self.modelView.view->setAmbientOcclusionOptions(ambientOcclusionOptions);
    
    // Initialize quad rendering properties
    _material = Material::Builder()
        .package((void*) CYLINDER_MATERIAL, sizeof(CYLINDER_MATERIAL))
        .build(*_modelView.engine);

    _materialInstance = _material->createInstance();
    _materialInstance->setParameter("baseColor", RgbaType::sRGB, {1, 0, 0, 1.0f});
    
    int indexCount = 6;
    int shortSize = 2;
    int indexSize = indexCount * shortSize;
    
    uint16_t indices[6] = {
        0, 1, 2,
        2, 1, 3
    };
    
    _indexBuffer = IndexBuffer::Builder()
        .indexCount(indexCount)
        .bufferType(IndexBuffer::IndexType::USHORT)
        .build(*_modelView.engine);
    
    _indexBuffer->setBuffer(*_modelView.engine, IndexBuffer::BufferDescriptor(indices, indexSize, nullptr));
    
    [self createDefaultRenderables];
    
    // Creating light is mandatory
    [self createLights];
}

- (void)appWillResignActive:(NSNotification*)notification {
    [self stopDisplayLink];
}

- (void)appDidBecomeActive:(NSNotification*)notification {
    [self startDisplayLink];
}

- (void)viewWillAppear:(BOOL)animated {
    [self startDisplayLink];
}

- (void)viewWillDisappear:(BOOL)animated {
    [self stopDisplayLink];
}

#pragma mark Private

- (void)startDisplayLink {
    [self stopDisplayLink];
    
    // Call our render method 60 times a second.
    _startTime = CACurrentMediaTime();
    _displayLink = [CADisplayLink displayLinkWithTarget:self selector:@selector(render)];
    _displayLink.preferredFramesPerSecond = 60;
    [_displayLink addToRunLoop:NSRunLoop.currentRunLoop forMode:NSDefaultRunLoopMode];
}

- (void)stopDisplayLink {
    [_displayLink invalidate];
    _displayLink = nil;
}

- (void)createDefaultRenderables {
    NSString* path = [[NSBundle mainBundle] pathForResource:@"stadium" ofType:@"gltf" inDirectory:@"BusterDrone"];
    NSData* buffer = [NSData dataWithContentsOfFile:path];
    [self.modelView loadModelGlb:buffer];
    [self.modelView transformToRoot];
    
    _pitchOverlayVisible = false;
    [self.modelView showEntity:@"pitch"];
    [self.modelView hideEntity:@"pitch_overlay"];
    [self.modelView hideEntity:@"bowling_accuracy_target"];
    
    [self.modelView hideEntity:@"ball_1"];
    [self.modelView hideEntity:@"ball_2"];
    [self.modelView hideEntity:@"ball_3"];
    [self.modelView hideEntity:@"ball_4"];
    [self.modelView hideEntity:@"ball_5"];
    [self.modelView hideEntity:@"ball_6"];
    
//    [self addTriangle];
    [self addCylinder];
}

- (void)createLights {
    // Load Skybox.
    NSString* skyboxPath = [[NSBundle mainBundle] pathForResource:@"default_env_skybox" ofType:@"ktx"];
    NSData* skyboxBuffer = [NSData dataWithContentsOfFile:skyboxPath];
    
    image::Ktx1Bundle* skyboxBundle = new image::Ktx1Bundle(static_cast<const uint8_t*>(skyboxBuffer.bytes), static_cast<uint32_t>(skyboxBuffer.length));
    _skyboxTexture = Ktx1Reader::createTexture(self.modelView.engine, skyboxBundle, false);
    _skybox = filament::Skybox::Builder().environment(_skyboxTexture).build(*self.modelView.engine);
    self.modelView.scene->setSkybox(_skybox);
    
    // Load IBL.
    NSString* iblPath = [[NSBundle mainBundle] pathForResource:@"default_env_ibl" ofType:@"ktx"];
    NSData* iblBuffer = [NSData dataWithContentsOfFile:iblPath];
    
    image::Ktx1Bundle* iblBundle = new image::Ktx1Bundle(static_cast<const uint8_t*>(iblBuffer.bytes), static_cast<uint32_t>(iblBuffer.length));
    math::float3 harmonics[9];
    iblBundle->getSphericalHarmonics(harmonics);
    _iblTexture = Ktx1Reader::createTexture(self.modelView.engine, iblBundle, false);
    _indirectLight = IndirectLight::Builder()
        .reflections(_iblTexture)
        .irradiance(3, harmonics)
        .intensity(30000.0f)
        .build(*self.modelView.engine);
    self.modelView.scene->setIndirectLight(_indirectLight);
    
    // Always add a direct light source since it is required for shadowing.
    _sun = EntityManager::get().create();
    LightManager::Builder(LightManager::Type::DIRECTIONAL)
        .color(Color::cct(6500.0f))
        .intensity(100000.0f)
        .direction(math::float3(0.0f, -1.0f, 0.0f))
        .castShadows(true)
        .build(*self.modelView.engine, _sun);
    self.modelView.scene->addEntity(_sun);
}

- (void)render {
    auto* animator = self.modelView.animator;
    if (animator) {
        if (animator->getAnimationCount() > 0) {
            CFTimeInterval elapsedTime = CACurrentMediaTime() - _startTime;
            animator->applyAnimation(0, static_cast<float>(elapsedTime));
        }
        animator->updateBoneMatrices();
    }
    
    [self.modelView render];
}

- (void)dealloc {
    [NSNotificationCenter.defaultCenter removeObserver:self];
    self.modelView.engine->destroy(_indirectLight);
    self.modelView.engine->destroy(_iblTexture);
    self.modelView.engine->destroy(_skybox);
    self.modelView.engine->destroy(_skyboxTexture);
    self.modelView.scene->remove(_sun);
    self.modelView.engine->destroy(_sun);
}

- (IBAction)onToggleOverlayClick:(id)sender {
    if (_pitchOverlayVisible) {
        [self.modelView showEntity:@"pitch"];
        [self.modelView hideEntity:@"pitch_overlay"];
    } else {
        [self.modelView showEntity:@"pitch_overlay"];
        [self.modelView hideEntity:@"pitch"];
    }
    
    _pitchOverlayVisible = !_pitchOverlayVisible;
}

- (IBAction)onToggleBallDotsClick:(id)sender {
    [self.modelView showEntity:@"bowling_accuracy_target"];
    [self.modelView showEntity:@"ball_1"];
    [self.modelView showEntity:@"ball_2"];
    [self.modelView showEntity:@"ball_3"];
    [self.modelView showEntity:@"ball_4"];
    [self.modelView showEntity:@"ball_5"];
    [self.modelView showEntity:@"ball_6"];
    
    [self.modelView translateEntity:[self getBallX] :0.025f :[self getBallZ] :@"ball_1"];
    [self.modelView translateEntity:[self getBallX] :0.025f :[self getBallZ] :@"ball_2"];
    [self.modelView translateEntity:[self getBallX] :0.025f :[self getBallZ] :@"ball_3"];
    [self.modelView translateEntity:[self getBallX] :0.025f :[self getBallZ] :@"ball_4"];
    [self.modelView translateEntity:[self getBallX] :0.025f :[self getBallZ] :@"ball_5"];
    [self.modelView translateEntity:[self getBallX] :0.025f :[self getBallZ] :@"ball_6"];
}

- (CGFloat)getBallX {
    float low_bound = -1.0f;
    float high_bound = 0.5f;
    return (((float)arc4random()/0x100000000)*(high_bound-low_bound)+low_bound);
}

- (CGFloat)getBallZ {
    float low_bound = -10.0f;
    float high_bound = 0.0f;
    return (((float)arc4random()/0x100000000)*(high_bound-low_bound)+low_bound);
}

- (void)addCylinder {
    const float radius = 0.025f;
    const int interpolationPoints = 50;
    const int circumferencePoints = 50;
    
    NSArray<Vertex *> *vertices = @[
        [[Vertex alloc] initWithX:-0.55f y:1.75f z:10.0f], // Bowler Stump Point
        [[Vertex alloc] initWithX:-0.20f y:0.0f z:-4.0f],  // Pitch Contact Point
        [[Vertex alloc] initWithX:0.0f y:0.5f z:-10.0f]    // Batsman Stump Point
    ];
    
    // Create window of two points and then smoothen the curve along those two points
    for (NSUInteger i = 0; i < vertices.count - 1; i++) {
        Vertex *pointA = vertices[i];
        Vertex *pointB = vertices[i + 1];
        
        // Here we want to smoothen the curve between pointA and pointB along z-y axis and the
        // x axis points will be linearly interpolated. Here we get
        const float xIncrement = (pointA.x - pointB.x) / (interpolationPoints - 1);
        
        // Calculate control point
        const float controlPointZ = (pointA.z + pointB.z) / 2;
        float controlPointY;
        if (pointA.y > pointB.y) {
            controlPointY = (pointA.y - pointB.y) * 0.75f;
        } else {
            controlPointY = (pointB.y - pointA.y) * 0.75f;
        }
        
        // Generate quadratic Bezier points in Z-Y plane
        NSArray<NSValue *> *bezierPoints = [QuadraticBezier quadraticBezierWithStart:CGPointMake(pointA.z, pointA.y)
                                                                             control:CGPointMake(controlPointZ, controlPointY)
                                                                                 end:CGPointMake(pointB.z, pointB.y)
                                                                         numOfPoints:interpolationPoints];
        
        // Convert Bezier points to 3D vertices with interpolated X coordinates
        NSMutableArray<Vertex *> *quadraticBezierPoints = [NSMutableArray array];
        for (NSUInteger index = 0; index < bezierPoints.count; index++) {
            CGPoint point = [bezierPoints[index] CGPointValue];
            float x = pointA.x - (xIncrement * index);
            [quadraticBezierPoints addObject:[[Vertex alloc] initWithX:x
                                                                     y:point.y
                                                                     z:point.x]]; // point.x contains Z value from Bezier
        }
        
        // Generate circumference points for each Bezier vertex
        NSMutableArray<NSArray<Vertex *> *> *verticesPoints = [NSMutableArray array];
        for (Vertex *vertex in quadraticBezierPoints) {
            NSArray<Vertex *> *circleVertices = [CylinderUtils getPointsAlongCircumferenceWithCenterX:vertex.x
                                                                                              centerY:vertex.y
                                                                                              centerZ:vertex.z
                                                                                               radius:radius
                                                                                          numOfPoints:circumferencePoints];
            [verticesPoints addObject:circleVertices];
        }
        
        // Create quads between consecutive circumference point sets
        for (NSUInteger i = 0; i < verticesPoints.count - 1; i++) {
            NSArray<Vertex *> *pointAVertices = verticesPoints[i];
            NSArray<Vertex *> *pointBVertices = verticesPoints[i + 1];
            
            NSArray<Quad *> *quadVertices = [CylinderUtils getQuadVerticesWithPoint1Vertices:pointAVertices
                                                                              point2Vertices:pointBVertices];
            // Add all quads to the model
            for (Quad *quad in quadVertices) {
                [self addQuad:quad];
            }
        }
    }
}

- (void)addQuad:(Quad*) quad {
    int vertexCount = 4; // Quad has 4 vertices each having 3 co-ordinates
    int floatSize = 4;
    int vertexSize = 3 * floatSize;
    
    int indexCount = 6; // Quad is made up of 2 triangles, having 3 indices for each
    
    float3 quadVertices[4] = {
        {quad.topLeftVertex.x, quad.topLeftVertex.y, quad.topLeftVertex.z},
        {quad.bottomLeftVertex.x, quad.bottomLeftVertex.y, quad.bottomLeftVertex.z},
        {quad.topRightVertex.x, quad.topRightVertex.y, quad.topRightVertex.z},
        {quad.bottomRightVertex.x, quad.bottomRightVertex.y, quad.bottomRightVertex.z}
    };
    
    VertexBuffer* vertexBuffer = VertexBuffer::Builder()
        .vertexCount(vertexCount)
        .bufferCount(1)
        .attribute(VertexAttribute::POSITION, 0, VertexBuffer::AttributeType::FLOAT3, 0, vertexSize)
        .build(*_modelView.engine);
    
    vertexBuffer->setBufferAt(*_modelView.engine, 0, VertexBuffer::BufferDescriptor(quadVertices, vertexSize * 4, nullptr));
    
    Entity renderable = EntityManager::get().create();
    
    RenderableManager::Builder(1)
        .boundingBox({{ 0, 0, 0 }, { 1, 1, 0.01 }})
        .material(0, _materialInstance)
        .geometry(0, RenderableManager::PrimitiveType::TRIANGLES, vertexBuffer, _indexBuffer, 0, indexCount)
        .build(*_modelView.engine, renderable);
    
    _modelView.scene->addEntity(renderable);
}

- (void)addTriangle {
    int vertexCount = 3; // Triangle has 3 vertices each having 3 co-ordinates
    int floatSize = 4;
    int vertexSize = 3 * floatSize;
    
    int shortSize = 2;
    int indexCount = 3; // Triangle is made up of 3 vertices
    int indexSize = indexCount * shortSize;
    
    VertexBuffer* vertexBuffer = VertexBuffer::Builder()
        .vertexCount(vertexCount)
        .bufferCount(1)
        .attribute(VertexAttribute::POSITION, 0, VertexBuffer::AttributeType::FLOAT3, 0, vertexSize)
        .build(*_modelView.engine);
    
    vertexBuffer->setBufferAt(*_modelView.engine, 0, VertexBuffer::BufferDescriptor(TRIANGLE_VERTICES, vertexCount * vertexSize, nullptr));
    
    IndexBuffer* indexBuffer = IndexBuffer::Builder()
        .indexCount(indexCount)
        .bufferType(IndexBuffer::IndexType::USHORT)
        .build(*_modelView.engine);
    
    indexBuffer->setBuffer(*_modelView.engine, IndexBuffer::BufferDescriptor(TRIANGLE_INDICES, indexSize, nullptr));
    
    Entity renderable = EntityManager::get().create();
    
    RenderableManager::Builder(1)
        .boundingBox({{ 0, 0, 0 }, { 1, 1, 0.01 }})
        .material(0, _materialInstance)
        .geometry(0, RenderableManager::PrimitiveType::TRIANGLES, vertexBuffer, indexBuffer, 0, indexCount)
        .build(*_modelView.engine, renderable);
    
    _modelView.scene->addEntity(renderable);
}

@end
