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
import android.view.View
import androidx.annotation.FloatRange
import java.util.*
import kotlin.math.max
import kotlin.math.min

/**
 * Responds to Android touch events and manages a camera manipulator.
 * Supports one-touch orbit, two-touch pan, and pinch-to-zoom.
 */
class GestureDetector(private val view: View, private val manipulator: Manipulator) {
    private enum class Gesture { NONE, ORBIT, PAN, ZOOM }

    // Simplified memento of MotionEvent, minimal but sufficient for our purposes.
    private data class TouchPair(var pt0: Float2, var pt1: Float2, var count: Int) {

        constructor() : this(Float2(0f), Float2(0f), 0)

        constructor(me: MotionEvent, height: Int) : this() {
            if (me.pointerCount >= 1) {
                this.pt0 = Float2(me.getX(0), height - me.getY(0))
                this.pt1 = this.pt0
                this.count++
            }
            if (me.pointerCount >= 2) {
                this.pt1 = Float2(me.getX(1), height - me.getY(1))
                this.count++
            }
        }

        val separation get() = distance(pt0, pt1)
        val midpoint get() = mix(pt0, pt1, 0.5f)
        val x: Int get() = midpoint.x.toInt()
        val y: Int get() = midpoint.y.toInt()
    }

    private var currentGesture = Gesture.NONE
    private var previousTouch = TouchPair()
    private val tentativePanEvents = ArrayList<TouchPair>()
    private val tentativeOrbitEvents = ArrayList<TouchPair>()
    private val tentativeZoomEvents = ArrayList<TouchPair>()

    private val kGestureConfidenceCount = 2
    private val kPanConfidenceDistance = 4 // Distance in terms of screen pixel locations
    private val kZoomConfidenceDistance = 10 // Distance in terms of screen pixel locations
    private val kZoomRange = FloatRange(from = -5.0 / kZoomSpeed, to = 5.0 / kZoomSpeed) // 5.0 is derived from changing kZoomSpeed and verifying the relative zoom in/out movement
    private var currentZoomDelta = 0.0

    fun onTouchEvent(event: MotionEvent) {
        val touch = TouchPair(event, view.height)
        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> {

                // CANCEL GESTURE DUE TO UNEXPECTED POINTER COUNT

                if ((event.pointerCount != 1 && currentGesture == Gesture.ORBIT) ||
                        (event.pointerCount != 2 && currentGesture == Gesture.PAN) ||
                        (event.pointerCount != 2 && currentGesture == Gesture.ZOOM)) {
                    endGesture()
                    return
                }

                // UPDATE EXISTING GESTURE

                if (currentGesture == Gesture.ZOOM) {
                    val d0 = previousTouch.separation
                    val d1 = touch.separation
                    val scrollDelta = (d0 - d1) * kZoomSpeed

                    if (scrollDelta < 0) {
                        // ensure that the currentZoomDelta is clamped to kZoomRange.from in case of zoom in
                        if (currentZoomDelta > kZoomRange.from) {
                            currentZoomDelta = max(kZoomRange.from, currentZoomDelta + scrollDelta)
                        }
                    } else {
                        // ensure that the currentZoomDelta is clamped to kZoomRange.to in case of zoom out
                        if (currentZoomDelta < kZoomRange.to) {
                            currentZoomDelta = min(kZoomRange.to, currentZoomDelta + scrollDelta)
                        }
                    }

                    // Here we limit the zoom in & out range
                    if (currentZoomDelta > kZoomRange.from && currentZoomDelta < kZoomRange.to) {
                        manipulator.scroll(touch.x, touch.y, scrollDelta)
                        previousTouch = touch
                    } else {
                        endGesture()
                    }

                    return
                }

                if (currentGesture != Gesture.NONE) {
                    if (currentGesture == Gesture.ORBIT) {
                        // Get the eye position
                        val eyePosition = DoubleArray(3)
                        manipulator.getLookAt(eyePosition, DoubleArray(3), DoubleArray(3))

                        // Check if the orbit rotation is going up, because then only we want to
                        // enforce Eye position y co-ordinate minimum threshold
                        if (touch.y > previousTouch.y) {
                            // Calculate maximum y axis movement allowed before if reaches threshold
                            val maxAllowedYDelta = eyePosition[1] - kEyeYMinThreshold
                            val maxYPixelMovementAllowed = (maxAllowedYDelta / kEyeYMovementPerPixel) + previousTouch.y

                            // If the current touch will cause y movement beyond the threshold, then end gesture
                            if (touch.y < maxYPixelMovementAllowed) {
                                manipulator.grabUpdate(touch.x, touch.y)
                            } else {
                                manipulator.grabUpdate(touch.x, maxYPixelMovementAllowed.toInt())
                                endGesture()
                            }
                        } else {
                            manipulator.grabUpdate(touch.x, touch.y)
                        }

                        previousTouch = touch
                        return
                    }

                    manipulator.grabUpdate(touch.x, touch.y)
                    return
                }

                // DETECT NEW GESTURE

                if (event.pointerCount == 1) {
                    tentativeOrbitEvents.add(touch)
                }

                if (event.pointerCount == 2) {
                    tentativePanEvents.add(touch)
                    tentativeZoomEvents.add(touch)
                }

                if (isOrbitGesture()) {
                    manipulator.grabBegin(touch.x, touch.y, false)
                    currentGesture = Gesture.ORBIT
                    previousTouch = touch
                    return
                }

                if (isZoomGesture()) {
                    currentGesture = Gesture.ZOOM
                    previousTouch = touch
                    return
                }

                if (isPanGesture()) {
                    manipulator.grabBegin(touch.x, touch.y, true)
                    currentGesture = Gesture.PAN
                    return
                }
            }
            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_UP -> {
                endGesture()
            }
        }
    }

    private fun endGesture() {
        tentativePanEvents.clear()
        tentativeOrbitEvents.clear()
        tentativeZoomEvents.clear()
        currentGesture = Gesture.NONE
        manipulator.grabEnd()
    }

    private fun isOrbitGesture(): Boolean {
        return tentativeOrbitEvents.size > kGestureConfidenceCount
    }

    @Suppress("UNREACHABLE_CODE")
    private fun isPanGesture(): Boolean {
        // Disable pan gesture
        return false

        if (tentativePanEvents.size <= kGestureConfidenceCount) {
            return false
        }
        val oldest = tentativePanEvents.first().midpoint
        val newest = tentativePanEvents.last().midpoint
        return distance(oldest, newest) > kPanConfidenceDistance
    }

    private fun isZoomGesture(): Boolean {
        if (tentativeZoomEvents.size <= kGestureConfidenceCount) {
            return false
        }
        val oldest = tentativeZoomEvents.first().separation
        val newest = tentativeZoomEvents.last().separation
        return kotlin.math.abs(newest - oldest) > kZoomConfidenceDistance
    }
}
