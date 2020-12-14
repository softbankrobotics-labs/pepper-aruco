package com.softbankrobotics.app.ui

import android.util.Log
import androidx.recyclerview.widget.RecyclerView
import android.view.View
import android.view.LayoutInflater
import android.view.ViewGroup
import com.aldebaran.qi.Future
import com.softbankrobotics.app.R
import com.softbankrobotics.dx.pepperextras.util.TAG
import com.softbankrobotics.pepperaruco.actuation.ArucoMarker
import kotlinx.android.synthetic.main.marker_layout.view.*


class MarkersViewAdapter(val lookAtClickCallback: (ArucoMarker) -> Future<Unit>,
                         val goToClickCallback: (ArucoMarker) -> Future<Unit>) :
    RecyclerView.Adapter<MarkersViewAdapter.MarkersViewHolder>() {

    val markers: MutableList<ArucoMarker> = mutableListOf()

    class MarkersViewHolder internal constructor(itemView: View) : RecyclerView.ViewHolder(itemView) {}

    fun resetMarkers() {
        markers.clear()
        notifyDataSetChanged()
    }

    fun addMarkers(marker: Set<ArucoMarker>) {
        markers.addAll(marker)
        notifyDataSetChanged()
    }

    fun addMarker(marker: ArucoMarker) {
        markers.add(marker)
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int {
        return markers.size
    }

    override fun onCreateViewHolder(viewGroup: ViewGroup, i: Int): MarkersViewHolder {
        val v = LayoutInflater.from(viewGroup.context).inflate(R.layout.marker_layout, viewGroup, false)
        return MarkersViewHolder(v)
    }

    override fun onBindViewHolder(markersViewHolder: MarkersViewHolder, i: Int) {
        markers[i].let { marker ->
            markersViewHolder.itemView.apply {
                markerId.text = "Id: ${markers[i].id}"
                markerPhoto.setImageBitmap(marker.detectionData?.image)
                lookAt.setOnClickListener { lookAtClickCallback(marker) }
                goTo.setOnClickListener { goToClickCallback(marker) }
            }
        }
    }
}






