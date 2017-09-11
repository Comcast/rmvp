package com.xfinity.rmvp_sample.mvp.presenter

import com.xfinity.rmvp.event.ComponentEvent
import com.xfinity.rmvp.model.Component
import com.xfinity.rmvp.model.ComponentModel
import com.xfinity.rmvp.presenter.DefaultComponentPresenter
import com.xfinity.rmvp.presenter.EventHandlingScreenPresenter
import com.xfinity.rmvp_sample.mvp.model.DataItemModel
import com.xfinity.rmvp_sample.mvp.model.DynamicScreenModel
import com.xfinity.rmvp_sample.mvp.view.DynamicScreenView
import com.xfinity.rmvp_sample.rmvp.AppComponentRegistry
import java.util.*

class DynamicScreenPresenter : EventHandlingScreenPresenter<DynamicScreenView> {
    var model: DynamicScreenModel = DynamicScreenModel()
    lateinit var view: DynamicScreenView
    lateinit var dataItemPresenter: DataItemPresenter
    var headerPosition = 0

    override fun attachView(screenView: DynamicScreenView) {
        view = screenView
        dataItemPresenter = DataItemPresenter(view.componentEventManager)
    }

    /**
     * Present the overall screen, by adding Components
     */
    override fun present() {
        val screenComponents = mutableListOf<Component>()
        if (!model.headerModel.header.isEmpty()) {
            screenComponents.add(Component(model.headerModel, AppComponentRegistry.HeaderView_VIEW_TYPE))

            if (!model.headerModel.enabled) {
                screenComponents.add(Component(object : ComponentModel{},
                        AppComponentRegistry.LoadingDotsView_VIEW_TYPE, DefaultComponentPresenter()))
                val timer = Timer()
                timer.schedule(object : TimerTask() {
                    override fun run() {
                        model.headerModel.enabled = true
                        for (dataItemModel in model.dataItemModels) {
                            dataItemModel.enabled = true
                        }

                        view.runOnUiThread (Runnable {
                            view.setEnabled(true)
                            present()
                            view.onComponentChanged(headerPosition)
                        })
                    }
                }, 3000)
            }
        }

        if (model.dataItemModels[0].enabled) {
            for (dataItemModel in model.dataItemModels) {
                if (dataItemModel.enabled) {
                    screenComponents.add(Component(dataItemModel, AppComponentRegistry.DataItemView_VIEW_TYPE,
                            dataItemPresenter))
                }
            }
        }


        if (model.headerModel.enabled && !model.footerModel.enabled) {
            screenComponents.add(Component(object : ComponentModel{},
                    AppComponentRegistry.LoadingDotsView_VIEW_TYPE, DefaultComponentPresenter()))
            val timer = Timer()
            timer.schedule(object : TimerTask() {
                override fun run() {
                    model.footerModel.enabled = true
                    view.runOnUiThread (Runnable {
                        present()
                    })
                }
            }, 3000)
        } else if (model.footerModel.enabled) {
            screenComponents.add(Component(model.footerModel, AppComponentRegistry.FooterView_VIEW_TYPE))
        }

        view.updateComponents(screenComponents)
    }

    override fun onComponentEvent(componentEvent: ComponentEvent): Boolean {
        if (componentEvent is DataItemPresenter.DataItemClickedEvent) {
            view.toast(componentEvent.toast)
            return true  //consume
        }

        return false
    }

    fun removeItemRequested() {
        if (model.dataItemModels.size > 0) {
            model.dataItemModels.removeAt(model.dataItemModels.size - 1)
        }
        present()
    }

    fun refreshDataItems() {
        model.dataItemModels = mutableListOf(DataItemModel(), DataItemModel(), DataItemModel(),
                DataItemModel(), DataItemModel(), DataItemModel())
        for (dataItemModel in model.dataItemModels) {
            dataItemModel.enabled = true
        }
        present()
    }

    fun onResume() {
        view.resume()
    }

    fun onPause() {
        view.pause()
    }
}