// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.settings.fixtures

import io.github.stslex.workeeper.core.core.platform.PlatformInfoProvider

internal actual fun settingsTestPlatformInfo(): PlatformInfoProvider = io.mockk.mockk(relaxed = true)
