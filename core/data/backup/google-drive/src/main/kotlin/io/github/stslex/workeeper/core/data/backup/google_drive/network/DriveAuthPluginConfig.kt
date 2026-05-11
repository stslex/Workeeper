package io.github.stslex.workeeper.core.data.backup.google_drive.network

/**
 * Configuration object for [DriveAuthPlugin]. Held separately so the Ktor plugin
 * factory can construct it without reflection and so the auth-token source stays
 * mutable until the plugin is installed.
 */
internal class DriveAuthPluginConfig {

    var authTokenProvider: AuthTokenProvider? = null
}
