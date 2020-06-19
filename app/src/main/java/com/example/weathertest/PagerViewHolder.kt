package com.example.weathertest

import android.view.View
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView

class PagerViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

    private var menu_img: ImageView = itemView.findViewById(R.id.item_pager_rv_img_view)

    fun bind(menu: MenuData) {
        menu_img.setImageResource(menu.menudata_menu_img)
    }
}