package com.mdportnov.monk.shared

import androidx.compose.ui.window.ComposeUIViewController
import com.mdportnov.monk.shared.platform.IosPlatform
import com.mdportnov.monk.shared.platform.UserDefaultsStore
import com.mdportnov.monk.shared.ui.MonkApp
import platform.UIKit.UIViewController

/** Entry point for the Swift shell. The UI renders, the blocker itself is a stub on iOS. */
fun MainViewController(): UIViewController {
    MonkRuntime.init(UserDefaultsStore(), IosPlatform)
    return ComposeUIViewController { MonkApp() }
}
