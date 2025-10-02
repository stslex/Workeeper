package io.github.stslex.workeeper

import App
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import io.github.stslex.workeeper.core.ui.kit.utils.activityHolder.ActivityHolderProducer
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var activityProducer: ActivityHolderProducer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        activityProducer.produce(this)

        setContent { App() }
    }

    override fun onDestroy() {
        super.onDestroy()
        activityProducer.produce(null)
    }
}
