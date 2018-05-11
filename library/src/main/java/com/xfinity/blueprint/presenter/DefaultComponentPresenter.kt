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

package com.xfinity.blueprint.presenter

import com.xfinity.blueprint.model.ComponentModel
import com.xfinity.blueprint.view.ComponentView

open class DefaultComponentPresenter : ComponentPresenter {
    override fun present(componentView: ComponentView<*>, componentModel: ComponentModel) {}
    override fun onComponentClicked(componentView: ComponentView<*>, position: Int) {}
}

@Suppress("UNCHECKED_CAST")
abstract class DefaultComponentReflectionPresenter<in CV : ComponentView<*>, in CM : ComponentModel> : ComponentPresenter {

    override fun present(componentView: ComponentView<*>, componentModel: ComponentModel) {
        presentView(componentView as CV, componentModel as CM)
    }

    override fun onComponentClicked(componentView: ComponentView<*>, position: Int) {
        onComponentViewClicked(componentView as CV, position)
    }

    abstract fun presentView(view: CV, model: CM)
    open fun onComponentViewClicked(view: CV, position: Int) {}
}