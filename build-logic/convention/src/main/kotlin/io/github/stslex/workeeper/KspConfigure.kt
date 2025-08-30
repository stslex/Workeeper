package io.github.stslex.workeeper

import AppExt.findPluginId
import AppExt.libs
import com.google.devtools.ksp.gradle.KspExtension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

fun Project.configureKsp(
    extraBlock: KspExtension.() -> Unit = {}
) {
    pluginManager.apply(libs.findPluginId("ksp"))
    extensions.configure<KspExtension> {
        arg("KOIN_CONFIG_CHECK", "true")
        extraBlock()
    }
}