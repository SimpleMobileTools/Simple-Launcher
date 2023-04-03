package com.simplemobiletools.launcher.models

import androidx.room.Embedded
import androidx.room.Ignore
import androidx.room.Relation

data class PageWithGridItems(
    @Embedded val page: HomeScreenPage?,
    @Relation(
        parentColumn = "id",
        entityColumn = "page_id"
    )
    val gridItems: List<HomeScreenGridItem>,
    @Ignore val isAddNewPageIndicator: Boolean = false
){
    constructor(page: HomeScreenPage, gridItems: List<HomeScreenGridItem>) : this(page, gridItems, false)
}
