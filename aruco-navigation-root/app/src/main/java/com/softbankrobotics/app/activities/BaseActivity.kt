package com.softbankrobotics.app.activities

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.widget.ImageView
import com.aldebaran.qi.Future
import com.aldebaran.qi.sdk.QiContext
import com.aldebaran.qi.sdk.RobotLifecycleCallbacks
import com.aldebaran.qi.sdk.`object`.holder.AutonomousAbilitiesType
import com.aldebaran.qi.sdk.`object`.holder.Holder
import com.aldebaran.qi.sdk.builder.HolderBuilder
import com.softbankrobotics.app.R
import com.softbankrobotics.app.ui.TutorialToolbar
import com.softbankrobotics.app.util.TUTORIAL_NAME_KEY
import com.softbankrobotics.app.util.activateImmersiveMode


abstract class BaseActivity: AppCompatActivity(), RobotLifecycleCallbacks {

    protected abstract val layoutId: Int
    private lateinit var holder: Holder

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(layoutId)
        setupToolbar()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus)
            activateImmersiveMode()
    }

    override fun onResume() {
        super.onResume()
        activateImmersiveMode()
    }

    fun setupToolbar() {
        val toolbar: TutorialToolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        toolbar.setOnClickListener { onBackPressed() }
        val nameNotFound = -1
        val nameResId = intent.getIntExtra(TUTORIAL_NAME_KEY, nameNotFound)
        if (nameResId != nameNotFound) {
            toolbar.setName(nameResId)
        }
        findViewById<ImageView>(R.id.close_button).setOnClickListener { finishAffinity() }
    }

    protected fun holdAutonomousAbilities(qiContext: QiContext, vararg abilitiesType: AutonomousAbilitiesType): Future<Void> {
        holder = HolderBuilder.with(qiContext)
            .withAutonomousAbilities(*abilitiesType)
            .build()
        return holder.async().hold()
    }
}