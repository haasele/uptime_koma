package dev.haasele.koma.shared.core

import dev.haasele.koma.shared.notify.AndroidAppContext
import dev.haasele.koma.shared.notify.AndroidNotificationChannels

actual fun openAppNotificationSettings() {
    val context = AndroidAppContext.application ?: return
    AndroidNotificationChannels.openSystemSettings(context)
}
