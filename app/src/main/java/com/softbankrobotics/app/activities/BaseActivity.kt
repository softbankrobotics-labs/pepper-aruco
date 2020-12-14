package com.softbankrobotics.app.activities

import android.os.Bundle
import android.util.Log
import android.widget.ImageView
import com.aldebaran.qi.Future
import com.aldebaran.qi.sdk.QiContext
import com.aldebaran.qi.sdk.QiSDK
import com.aldebaran.qi.sdk.RobotLifecycleCallbacks
import com.aldebaran.qi.sdk.design.activity.RobotActivity
import com.aldebaran.qi.sdk.design.activity.conversationstatus.SpeechBarDisplayStrategy
import com.softbankrobotics.app.R
import com.softbankrobotics.app.ui.TutorialToolbar
import com.softbankrobotics.app.util.TUTORIAL_NAME_KEY
import com.softbankrobotics.dx.pepperextras.util.SingleThread
import com.softbankrobotics.dx.pepperextras.util.TAG
import com.softbankrobotics.dx.pepperextras.util.asyncFuture
import com.softbankrobotics.dx.pepperextras.util.awaitOrNull
import com.softbankrobotics.pepperaruco.util.OpenCVUtils
import kotlinx.coroutines.runBlocking


abstract class BaseActivity: RobotActivity(), RobotLifecycleCallbacks {

    protected abstract val layoutId: Int
    public val appScope = SingleThread.GlobalScope

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setSpeechBarDisplayStrategy(SpeechBarDisplayStrategy.IMMERSIVE)
        setContentView(layoutId)
        setupToolbar()
        OpenCVUtils.loadOpenCV(this)
        QiSDK.register(this, this)
    }

    override fun onDestroy() {
        super.onDestroy()
        QiSDK.unregister(this)
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

    abstract suspend fun onRobotFocus(qiContext: QiContext)

    var future: Future<Unit>? = null
    override fun onRobotFocusGained(qiContext: QiContext) {
        Log.d(TAG, "Focus gained")
        future = appScope.asyncFuture {
            try {
                onRobotFocus(qiContext)
            } catch (e: Throwable) {
                Log.e(TAG, "Uncaught error: $e")
            }
            Unit
        }
    }

    override fun onRobotFocusLost() {
        Log.d(TAG, "Focus lost")
        runBlocking {
            future?.apply { requestCancellation() }?.awaitOrNull()
            future = null
        }
    }
}