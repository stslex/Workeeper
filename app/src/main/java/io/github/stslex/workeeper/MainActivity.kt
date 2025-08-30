package io.github.stslex.workeeper

import App
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.arkivanov.decompose.defaultComponentContext
import io.github.stslex.workeeper.host.DefaultRootComponent
import io.github.stslex.workeeper.core.ui.kit.utils.ActivityHolderProducer

import org.koin.android.ext.android.getKoin

class MainActivity : ComponentActivity() {

    private val activityProducer: ActivityHolderProducer by lazy { getKoin().get() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val rootComponent = DefaultRootComponent(defaultComponentContext())
        activityProducer.produce(this)
        setContent {
            App(
                rootComponent = rootComponent,
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        activityProducer.produce(null)
    }
}