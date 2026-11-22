package com.lambdarc.litememo.data.local.dao

import com.lambdarc.litememo.data.local.model.MemoWithRefs

internal fun MemoWithRefs?.isSameSnapshotAs(other: MemoWithRefs?): Boolean =
    this?.withNormalizedPositions() == other?.withNormalizedPositions()

// Deleting a tag can leave gaps in positions; only the relative order is meaningful.
private fun MemoWithRefs.withNormalizedPositions(): MemoWithRefs = copy(
    tagRefs = tagRefs.sortedBy { it.position }.mapIndexed { index, ref ->
        ref.copy(position = index)
    },
    imageRefs = imageRefs.sortedBy { it.position }.mapIndexed { index, ref ->
        ref.copy(position = index)
    }
)
