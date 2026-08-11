plugins {
    alias(libs.plugins.convention.androidLibrary)
    // Metro plugin so the platform impls (Android{PlatformInfoProvider,TempFileProvider,AppReinitializer})
    // contribute to the app-scope AppGraph via @ContributesBinding, and so the two @BindingContainer
    // @ContributesTo(AppScope) objects (DispatchersBindingContainer / ResourceWrapperBindingContainer)
    // aggregate. AppScope itself is declared in :core:core commonMain; this module only consumes it.
    alias(libs.plugins.metro)
}

metro {
    interop {
        includeJavax()
    }
}

// Android side of the split core module (Phase C KMP cascade, L1). :core:core is a pure-Kotlin KMP
// module that cannot reference android.*; this Android-library module hosts everything that must —
// the framework implementations (AndroidResourceWrapper, ImageStorageImpl + buildImageStorage, the
// three platform providers), the TempFileProvider interface, the Android-only formatRelativeTime
// helper, and the two Metro binding containers. Packages are kept under
// io.github.stslex.workeeper.core.core.* so no downstream import changes.
//
// :app:app depends on this so the @ContributesTo(AppScope) containers aggregate into AppGraph.
// Only two other modules need the edge, and only because they name an Android-only type:
// feature:home (formatRelativeTime) and feature:settings (TempFileProvider). Everything else
// resolves AppScope, the dispatcher qualifiers and the platform interfaces from :core:core.
dependencies {
    api(project(":core:core"))

    implementation(libs.kermit)
    implementation(libs.kotlinx.datetime)
}
