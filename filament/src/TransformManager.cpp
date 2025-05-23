/*
 * Copyright (C) 2017 The Android Open Source Project
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

#include "components/TransformManager.h"

using namespace utils;

namespace filament {

using namespace math;

bool TransformManager::hasComponent(Entity const e) const noexcept {
    return downcast(this)->hasComponent(e);
}

size_t TransformManager::getComponentCount() const noexcept {
    return downcast(this)->getComponentCount();
}

bool TransformManager::empty() const noexcept {
    return downcast(this)->empty();
}

Entity TransformManager::getEntity(Instance const i) const noexcept {
    return downcast(this)->getEntity(i);
}

Entity const* TransformManager::getEntities() const noexcept {
    return downcast(this)->getEntities();
}

TransformManager::Instance TransformManager::getInstance(Entity const e) const noexcept {
    return downcast(this)->getInstance(e);
}

void TransformManager::create(Entity const entity, Instance const parent, const mat4f& worldTransform) {
    downcast(this)->create(entity, parent, worldTransform);
}

void TransformManager::create(Entity const entity, Instance const parent, const mat4& worldTransform) {
    downcast(this)->create(entity, parent, worldTransform);
}

void TransformManager::create(Entity const entity, Instance const parent) {
    downcast(this)->create(entity, parent, mat4f{});
}

void TransformManager::destroy(Entity const e) noexcept {
    downcast(this)->destroy(e);
}

void TransformManager::setTransform(Instance const ci, const mat4f& model) noexcept {
    downcast(this)->setTransform(ci, model);
}

void TransformManager::setTransform(Instance const ci, const mat4& model) noexcept {
    downcast(this)->setTransform(ci, model);
}

const mat4f& TransformManager::getTransform(Instance const ci) const noexcept {
    return downcast(this)->getTransform(ci);
}

mat4 TransformManager::getTransformAccurate(Instance const ci) const noexcept {
    return downcast(this)->getTransformAccurate(ci);
}

const mat4f& TransformManager::getWorldTransform(Instance const ci) const noexcept {
    return downcast(this)->getWorldTransform(ci);
}

mat4 TransformManager::getWorldTransformAccurate(Instance const ci) const noexcept {
    return downcast(this)->getWorldTransformAccurate(ci);
}

void TransformManager::setParent(Instance const i, Instance const newParent) noexcept {
    downcast(this)->setParent(i, newParent);
}

Entity TransformManager::getParent(Instance const i) const noexcept {
    return downcast(this)->getParent(i);
}

size_t TransformManager::getChildCount(Instance const i) const noexcept {
    return downcast(this)->getChildCount(i);
}

size_t TransformManager::getChildren(Instance const i, Entity* children,
        size_t const count) const noexcept {
    return downcast(this)->getChildren(i, children, count);
}

void TransformManager::openLocalTransformTransaction() noexcept {
    downcast(this)->openLocalTransformTransaction();
}

void TransformManager::commitLocalTransformTransaction() noexcept {
    downcast(this)->commitLocalTransformTransaction();
}

TransformManager::children_iterator TransformManager::getChildrenBegin(
        Instance const parent) const noexcept {
    return downcast(this)->getChildrenBegin(parent);
}

TransformManager::children_iterator TransformManager::getChildrenEnd(
        Instance const parent) const noexcept {
    return downcast(this)->getChildrenEnd(parent);
}

void TransformManager::setAccurateTranslationsEnabled(bool const enable) noexcept {
    downcast(this)->setAccurateTranslationsEnabled(enable);
}

bool TransformManager::isAccurateTranslationsEnabled() const noexcept {
    return downcast(this)->isAccurateTranslationsEnabled();
}

} // namespace filament
