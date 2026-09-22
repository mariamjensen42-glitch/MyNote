package com.cycling.mynote.ui.settings

import android.content.Context
import com.cycling.mynote.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The app's version name, read from the package manager rather than from `BuildConfig`.
 *
 * `BuildConfig` is off by default under AGP 9's build-feature defaults, and enabling it project-wide
 * just to print one string would be the tail wagging the dog; the package manager is the
 * authoritative source for a value the user can also see in system settings.
 */
@Singleton
class PackageInfoProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun versionName(): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull().orEmpty().ifEmpty { context.getString(R.string.settings_version_fallback) }
}
