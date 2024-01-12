package com.thewizrd.mediacontroller.remote.services.background

import android.content.Intent
import android.os.IBinder
import androidx.annotation.CallSuper
import androidx.annotation.MainThread
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ServiceLifecycleDispatcher
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelLazy
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.MutableCreationExtras
import androidx.media3.session.MediaSessionService

abstract class CustomMediaControllerService : MediaSessionService(), LifecycleOwner {
    private val dispatcher = ServiceLifecycleDispatcher(this)
    protected lateinit var viewModelStore: ViewModelStore

    @CallSuper
    override fun onCreate() {
        dispatcher.onServicePreSuperOnCreate()
        super.onCreate()
        viewModelStore = ViewModelStore()
    }

    override fun onBind(intent: Intent?): IBinder? {
        dispatcher.onServicePreSuperOnBind()
        return super.onBind(intent)
    }

    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    @CallSuper
    override fun onStart(intent: Intent?, startId: Int) {
        dispatcher.onServicePreSuperOnStart()
        super.onStart(intent, startId)
    }

    // this method is added only to annotate it with @CallSuper.
    // In usual Service, super.onStartCommand is no-op, but in LifecycleService
    // it results in dispatcher.onServicePreSuperOnStart() call, because
    // super.onStartCommand calls onStart().
    @CallSuper
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return super.onStartCommand(intent, flags, startId)
    }

    @CallSuper
    override fun onDestroy() {
        dispatcher.onServicePreSuperOnDestroy()
        super.onDestroy()
        viewModelStore.clear()
    }

    override val lifecycle: Lifecycle
        get() = dispatcher.lifecycle

    @MainThread
    protected inline fun <reified VM : ViewModel> CustomMediaControllerService.viewModels(
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