package com.softbankrobotics.app.ui

import androidx.recyclerview.widget.RecyclerView
import android.view.View
import android.view.LayoutInflater
import android.view.ViewGroup
import com.aldebaran.qi.Future
import com.softbankrobotics.app.R
import com.softbankrobotics.pepperaruco.ArucoMarker
import com.softbankrobotics.pepperaruco.ArucoMarkerDetectionData
import kotlinx.android.synthetic.main.marker_layout.view.*


class MarkersViewAdapter(val lookAtClickCallback: (ArucoMarker) -> Future<Void>,
                         val goToClickCallback: (ArucoMarker) -> Future<Void>) :
    RecyclerView.Adapter<MarkersViewAdapter.MarkersViewHolder>() {

    val markers: MutableList<Pair<ArucoMarker, ArucoMarkerDetectionData>> = mutableListOf()

    class MarkersViewHolder internal constructor(itemView: View) : RecyclerView.ViewHolder(itemView) {}

    fun resetMarkers() {
        markers.clear()
        notifyDataSetChanged()
    }

    fun addMarker(marker: ArucoMarker, detectionData: ArucoMarkerDetectionData) {
        markers.add(Pair(marker, detectionData))
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
        val (marker, detectionData) = markers[i]
        markersViewHolder.itemView.markerId.text = "Id: ${marker.id}"
        markersViewHolder.itemView.markerPhoto.setImageBitmap(detectionData.image)
        markersViewHolder.itemView.lookAt.setOnClickListener { lookAtClickCallback(marker) }
        markersViewHolder.itemView.goTo.setOnClickListener { goToClickCallback(marker) }
    }
}






