package io.github.stslex.workeeper

import App
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.remember
import io.github.stslex.workeeper.core.ui.kit.utils.ActivityHolderProducer
import io.github.stslex.workeeper.host.NavHostControllerHolder.Companion.rememberNavHostControllerHolder
import io.github.stslex.workeeper.navigation.NavigatorImpl
import org.koin.android.ext.android.getKoin

class MainActivity : ComponentActivity() {

    private val activityProducer: ActivityHolderProducer by lazy { getKoin().get() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        activityProducer.produce(this)

        setContent {
            val navigatorHolder = rememberNavHostControllerHolder()
            App(navigatorHolder)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        activityProducer.produce(null)
    }
}