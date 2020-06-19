package com.example.weathertest

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView

class PagerRecyclerAdapter(private val menuDatas: Array<MenuData>) : RecyclerView.Adapter<PagerViewHolder>() {

    // 아이템 카운트
    override fun getItemCount(): Int = menuDatas.size

    // 뷰 생성
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PagerViewHolder =
        PagerViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_pager, parent, false))

    // 바인드
    override fun onBindViewHolder(holder: PagerViewHolder, position: Int) {
        holder.bind(menuDatas[position])

    }

}