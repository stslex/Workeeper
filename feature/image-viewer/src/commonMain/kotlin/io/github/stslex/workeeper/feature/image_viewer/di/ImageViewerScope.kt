// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.image_viewer.di

/**
 * Metro feature-scope marker for feature/image-viewer — the Metro analogue of Hilt's
 * `@ViewModelScoped`. Every Metro-constructed node is `@SingleIn(ImageViewerScope::class)`.
 */
internal abstract class ImageViewerScope private constructor()
