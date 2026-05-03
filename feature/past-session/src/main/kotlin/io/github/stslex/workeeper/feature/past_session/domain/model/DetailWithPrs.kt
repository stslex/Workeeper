// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.past_session.domain.model

internal data class DetailWithPrs(
    val detail: SessionDetailDomain,
    val prSetUuids: Set<String>,
)
