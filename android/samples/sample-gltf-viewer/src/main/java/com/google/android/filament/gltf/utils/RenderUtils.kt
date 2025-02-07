package com.google.android.filament.gltf.utils

import com.google.android.filament.Entity
import com.google.android.filament.RenderableManager

/**
 * Shows an entity.
 *
 * @see com.google.android.filament.View.setVisibleLayers
 */
fun RenderableManager.showEntity(@Entity entity: Int): Boolean {
    return if (hasComponent(entity)) {
        // (1: visible, 0: invisible)
        setLayerMask(getInstance(entity), 0xFF, 1)
        true
    } else {
        false
    }
}

/**
 * Hides an entity.
 *
 * @see com.google.android.filament.View.setVisibleLayers
 */
fun RenderableManager.hideEntity(@Entity entity: Int): Boolean {
    return if (hasComponent(entity)) {
        // (1: visible, 0: invisible)
        setLayerMask(getInstance(entity), 0xFF, 0)
        true
    } else {
        false
    }
}
