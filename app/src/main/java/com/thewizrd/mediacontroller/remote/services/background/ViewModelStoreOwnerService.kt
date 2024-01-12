package com.thewizrd.mediacontroller.remote.services.background

import androidx.annotation.CallSuper
import androidx.annotation.MainThread
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelLazy
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.MutableCreationExtras

abstract class ViewModelStoreOwnerService : LifecycleService() {
    protected lateinit var viewModelStore: ViewModelStore

    @CallSuper
    override fun onCreate() {
        super.onCreate()
        viewModelStore = ViewModelStore()
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModelStore.clear()
    }

    @MainThread
    protected inline fun <reified VM : ViewModel> ViewModelStoreOwnerService.viewModels(
        noinline factoryProducer: (() -> ViewModelProvider.Factory)? = null
    ): Lazy<VM> {
        val factoryPromise = factoryProducer ?: {
            ViewModelProvider.AndroidViewModelFactory(application)
        }

        return ViewModelLazy(
            VM::class,
            { viewModelStore },
            factoryPromise,
            {
                MutableCreationExtras().apply {
                    if (application != null) {
                        set(ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY, application)
                    }
                }
            }
        )
    }
}