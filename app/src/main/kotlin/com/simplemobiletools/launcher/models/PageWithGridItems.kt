package com.simplemobiletools.launcher.models

import androidx.room.Embedded
import androidx.room.Ignore
import androidx.room.Relation
import android.os.Parcel
import android.os.Parcelable

data class PageWithGridItems(
    @Embedded val page: HomeScreenPage?,
    @Relation(
        parentColumn = "id",
        entityColumn = "page_id"
    )
    val gridItems: List<HomeScreenGridItem>,
    @Ignore val isAddNewPageIndicator: Boolean = false
) : Parcelable {
    constructor(page: HomeScreenPage, gridItems: List<HomeScreenGridItem>) : this(page, gridItems, false)

    private constructor(parcel: Parcel) : this(
        parcel.readParcelable(HomeScreenPage::class.java.classLoader),
        mutableListOf<HomeScreenGridItem>().apply {
            parcel.readTypedList(this, HomeScreenGridItem.CREATOR)
        },
        parcel.readByte() != 0.toByte()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeParcelable(page, flags)
        parcel.writeTypedList(gridItems)
        parcel.writeByte(if (isAddNewPageIndicator) 1 else 0)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<PageWithGridItems> {
        override fun createFromParcel(parcel: Parcel): PageWithGridItems {
            return PageWithGridItems(parcel)
        }

        override fun newArray(size: Int): Array<PageWithGridItems?> {
            return arrayOfNulls(size)
        }
    }
}

