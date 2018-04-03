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

package com.xfinity.blueprint_sample.mvp.view

import com.xfinity.blueprint.view.EventHandlingScreenView
import com.xfinity.blueprint.view.EventHandlingScreenViewDelegate
import com.xfinity.blueprint_sample.DynamicScreenActivity

class DynamicScreenView(val screenViewDelegate: EventHandlingScreenViewDelegate,
                        val activity: DynamicScreenActivity) :
        EventHandlingScreenView by screenViewDelegate {

    fun setEnabled(enabled: Boolean) {
        activity.setEnabled(enabled)
    }

    fun runOnUiThread(runnable: Runnable) {
        activity.runOnUiThread(runnable)
    }

    fun toast(msg: String) {
        activity.toast(msg)
    }
}