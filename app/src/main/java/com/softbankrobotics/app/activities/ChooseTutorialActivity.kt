package com.softbankrobotics.app.activities

import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.softbankrobotics.app.R
import com.softbankrobotics.app.util.TUTORIAL_NAME_KEY
import com.softbankrobotics.app.util.activateImmersiveMode
import kotlinx.android.synthetic.main.activity_choose_tutorial.*
import kotlinx.android.synthetic.main.tutorial_button_layout.view.*
import kotlin.reflect.KClass

class ChooseTutorialActivity : AppCompatActivity() {

    data class TutorialData(val activity: KClass<out BaseActivity>, val nameResId: Int)

    class TutorialViewAdapter(val context: Context, val tutorials: LinkedHashMap<String, TutorialData>) :
        RecyclerView.Adapter<TutorialViewAdapter.TutorialViewHolder>() {

        class TutorialViewHolder internal constructor(itemView: View) : RecyclerView.ViewHolder(itemView) {}

        override fun getItemCount(): Int { return tutorials.size }

        override fun onCreateViewHolder(viewGroup: ViewGroup, i: Int): TutorialViewHolder {
            val v = LayoutInflater.from(viewGroup.context).inflate(R.layout.tutorial_button_layout, viewGroup, false)
            return TutorialViewHolder(v)
        }

        override fun onBindViewHolder(tutorialViewHolder: TutorialViewHolder, i: Int) {

            val tutorial = tutorials.keys.toTypedArray()[i]
            tutorialViewHolder.itemView.button.text = tutorial
            tutorialViewHolder.itemView.button.setOnClickListener {
                val (tutorialActivity, tutorialName) = tutorials.get(tutorial)!!
                val intent = Intent(context, tutorialActivity.java)
                intent.putExtra(TUTORIAL_NAME_KEY, tutorialName)
                context.startActivity(intent)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_choose_tutorial)

        val tutorials = linkedMapOf(
            "Detect an Aruco marker" to TutorialData(DetectArucoActivity::class, R.string.detect_aruco),
            "Look around for Aruco markers, look at and go to them"
                    to TutorialData(LookAroundForArucoActivity::class, R.string.look_for_aruco),
            "Engage and return to Aruco marker"
                    to TutorialData(EngageAndReturnToMarkerActivity::class, R.string.engage_and_return_to_marker)
        )
        recyclerview.layoutManager = LinearLayoutManager(this)
        recyclerview.adapter =
            TutorialViewAdapter(this, tutorials)
    }

    override fun onResume() {
        super.onResume()
        activateImmersiveMode()
    }
}
