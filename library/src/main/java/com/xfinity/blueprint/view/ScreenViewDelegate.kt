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

import android.view.View
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.xfinity.blueprint.ComponentAdapter
import com.xfinity.blueprint.ComponentRegistry
import com.xfinity.blueprint.model.Component

open class ScreenViewDelegate(componentRegistry: ComponentRegistry,
                              private var loadingView: View? = null,
                              private val recyclerView: RecyclerView) : ScreenView {
    val componentAdapter = ComponentAdapter(componentRegistry)

    override fun refresh() {
        componentAdapter.notifyDataSetChanged()
    }

    override fun reset() {
        componentAdapter.clear(true)
    }

    override fun addComponent(component: Component, notify: Boolean, position: Int) {
        componentAdapter.addComponent(component, notify, position)
    }

    override fun addComponents(components: List<Component>, notify: Boolean, position: Int) {
        componentAdapter.addComponents(components, notify, position)
    }

    override fun removeComponent(position: Int, notify: Boolean) {
        componentAdapter.removeComponent(position, notify)
    }

    override fun removeComponents(startPosition: Int, endPosition: Int, notify: Boolean) {
        componentAdapter.removeComponents(startPosition, endPosition, notify)
    }

    override fun removeComponentsByType(viewType: Int, notify: Boolean) {
        componentAdapter.removeComponentsByType(viewType, notify)
    }

    override fun updateComponent(position: Int, component: Component) {
        removeComponent(position, false)
        addComponent(component, false, position)
        onComponentChanged(position)
    }

    override fun onComponentChanged(position: Int) {
        componentAdapter.notifyItemChanged(position)
    }

    override fun onComponentRangeChanged(startPosition: Int, endPosition: Int) {
        componentAdapter.notifyItemRangeChanged(startPosition, endPosition)
    }

    override fun onComponentMoved(fromPosition: Int, toPosition: Int) {
        componentAdapter.notifyItemMoved(fromPosition, toPosition)
    }

    override fun hasComponent(viewType: Int): Boolean {
        return componentAdapter.hasComponent(viewType)
    }

    override fun positionOfFirst(viewType: Int) : Int {
        return componentAdapter.positionOfFirst(viewType)
    }

    override fun showLoading() {
        loadingView?.visibility = View.VISIBLE
    }

    override fun hideLoading() {
        loadingView?.visibility = View.GONE
    }

    override fun updateCacheSize(size: Int) {
        recyclerView.setItemViewCacheSize(size)
    }

    /**
     * updateComponents uses DiffUtil and a DefaultComponentDiffCallback in order to allow a screen to be
     * updated with new, removed, or changed components without needing a presenter to maintain the state
     * of the ComponentAdapter itself. Instead, the state of the screen can be handed off for the delegate
     * and adapter to manage the updates.
     *
     * @param newComponents A List of the components the screen should be updated to display
     */
    override fun updateComponents(newComponents: List<Component>) {
        updateComponents(newComponents) { oldComponents -> DefaultComponentDiffCallback(oldComponents, newComponents) }
    }

    /**
     * updateComponents uses DiffUtil in order to allow a screen to be
     * updated with new, removed, or changed components without needing a presenter to maintain the state
     * of the ComponentAdapter itself. Instead, the state of the screen can be handed off for the delegate
     * and adapter to manage the updates.
     *
     * @param newComponents A List of the components the screen should be updated to display
     * @param diffGenerator A function to create a DiffUtil.Callback once handed off the current list of components
     *                      being used by the ComponentAdapter
     */
    override fun updateComponents(newComponents: List<Component>, diffGenerator: (List<Component>) -> DiffUtil.Callback) {
        val oldComponents = componentAdapter.components

        val diff = DiffUtil.calculateDiff(diffGenerator.invoke(oldComponents))

        componentAdapter.clear(false)
        componentAdapter.addComponents(newComponents, false)

        val updateBehavior = {
            diff.dispatchUpdatesTo(componentAdapter)
        }

        if (!recyclerView.isComputingLayout) {
            updateBehavior.invoke()
        } else {
            //If we dipatch updates while the recyclerview is computing, the app will crash
            recyclerView.addOnScrollListener(OnIdleScrollListener(updateBehavior))
        }
    }

    private inner class OnIdleScrollListener(private val behavior: () -> Unit) : RecyclerView.OnScrollListener() {

        override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
            if (newState == RecyclerView.SCROLL_STATE_IDLE && !recyclerView.isComputingLayout) {
                behavior.invoke()
                recyclerView.removeOnScrollListener(this)
            }
        }
    }
}

open class DefaultComponentDiffCallback(protected val oldComponents: List<Component>,
                                        protected val newComponents: List<Component>) : DiffUtil.Callback() {
    override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        return (oldComponents[oldItemPosition].viewType == newComponents[newItemPosition].viewType)
                && areContentsTheSame(oldItemPosition, newItemPosition)
    }

    override fun getOldListSize(): Int {
        return oldComponents.size
    }

    override fun getNewListSize(): Int {
        return newComponents.size
    }

    override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        return oldComponents[oldItemPosition].model.equals(newComponents[newItemPosition].model)
    }

}