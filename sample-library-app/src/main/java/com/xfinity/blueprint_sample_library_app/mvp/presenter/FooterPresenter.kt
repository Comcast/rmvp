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

package com.xfinity.blueprint_sample_library_app.mvp.presenter

import com.xfinity.blueprint.presenter.ComponentPresenter
import com.xfinity.blueprint_annotations.DefaultPresenter
import com.xfinity.blueprint_sample_library_app.mvp.model.FooterModel
import com.xfinity.blueprint_sample_library_app.mvp.view.FooterView

@DefaultPresenter(viewClass = FooterView::class)
class FooterPresenter : ComponentPresenter<FooterView, FooterModel> {
    override fun present(view: FooterView, model: FooterModel) {
        view.setFooterText(model.footer)
    }
}