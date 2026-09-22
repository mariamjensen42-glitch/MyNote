package com.cycling.mynote.di

import javax.inject.Qualifier
import kotlin.annotation.AnnotationRetention.RUNTIME

/** Marks the dispatcher for file and provider I/O: reading, writing and scanning notes. */
@Qualifier
@Retention(RUNTIME)
annotation class IoDispatcher

/** Marks the dispatcher for CPU-bound work that is not on the main thread. */
@Qualifier
@Retention(RUNTIME)
annotation class DefaultDispatcher

/** Marks the application-lifetime scope, for work that must outlive a screen. */
@Qualifier
@Retention(RUNTIME)
annotation class ApplicationScope
