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

#include <filament/Engine.h>
#include <filament/Scene.h>
#include <filament/Skybox.h>

#include <utils/EntityManager.h>

#include <gltfio/Animator.h>

#include <ktxreader/Ktx1Reader.h>

#include <viewer/AutomationEngine.h>
#include <viewer/RemoteServer.h>

using namespace filament;
using namespace utils;
using namespace ktxreader;

const float kToastAnimationDuration = 0.25f;
const float kToastDelayDuration = 2.0f;

@interface FILViewController ()

- (void)startDisplayLink;
- (void)stopDisplayLink;

- (void)createRenderables;
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
    self.modelView.view->setAmbientOcclusion(View::AmbientOcclusion::SSAO);
    
    [self createDefaultRenderables];
    
    // Creating light is mandatory
    [self createLights];
    
    [self.modelView showEntity:@"pitch_overlay"];
    [self.modelView hideEntity:@"pitch"];
    [self.modelView showEntity:@"bowling_accuracy_target"];
    
    [self.modelView showEntity:@"ball_1"];
    [self.modelView showEntity:@"ball_2"];
    [self.modelView showEntity:@"ball_3"];
    [self.modelView showEntity:@"ball_4"];
    [self.modelView showEntity:@"ball_5"];
    [self.modelView showEntity:@"ball_6"];
    
    [self.modelView translateEntity:-1.0f :0.025f :-6.0f :@"ball_1"];
    [self.modelView translateEntity:-0.25f :0.025f :-4.5f :@"ball_2"];
    [self.modelView translateEntity:-0.50f :0.025f :-3.25f :@"ball_3"];
    [self.modelView translateEntity:-1.25f :0.025f :-1.25f :@"ball_4"];
    [self.modelView translateEntity:0.0f :0.025f :-4.75f :@"ball_5"];
    [self.modelView translateEntity:-0.60f :0.025f :-4.0f :@"ball_6"];
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
    
    [self.modelView showEntity:@"pitch"];
    [self.modelView hideEntity:@"pitch_overlay"];
    [self.modelView hideEntity:@"bowling_accuracy_target"];
    
    [self.modelView hideEntity:@"ball_1"];
    [self.modelView hideEntity:@"ball_2"];
    [self.modelView hideEntity:@"ball_3"];
    [self.modelView hideEntity:@"ball_4"];
    [self.modelView hideEntity:@"ball_5"];
    [self.modelView hideEntity:@"ball_6"];
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

@end
