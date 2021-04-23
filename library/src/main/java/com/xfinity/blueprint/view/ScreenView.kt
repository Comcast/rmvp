/*
 * Copyright 2017 Comcast Cable Communications Management, LLC
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.xfinity.blueprint.view

import androidx.recyclerview.widget.DiffUtil
import com.xfinity.blueprint.model.Component

/**
 * View (mVp) classs representing a screen
 */
interface ScreenView {
    fun reset()
    fun refresh()
    fun showLoading()
    fun hideLoading()
    fun addComponent(component: Component, notify : Boolean = false, position: Int = -1)
    fun addComponents(components: List<Component>, notify : Boolean = false, position: Int = -1)
    fun removeComponent(position: Int, notify: Boolean = false)
    fun removeComponents(startPosition: Int, endPosition: Int, notify: Boolean = false)
    fun removeComponentsByType(viewType: Int, notify: Boolean = false)
    fun updateComponent(position: Int, component: Component)
    fun onComponentChanged(position: Int)
    fun onComponentRangeChanged(startPosition: Int, endPosition: Int)
    fun onComponentMoved(fromPosition: Int, toPosition: Int)
    fun hasComponent(viewType: Int) : Boolean
    fun positionOfFirst(viewType: Int) : Int
    fun updateComponents(newComponents: List<Component>)
    fun updateComponents(newComponents: List<Component>, diffGenerator: (List<Component>) -> DiffUtil.Callback)
    fun updateCacheSize(size: Int)
}