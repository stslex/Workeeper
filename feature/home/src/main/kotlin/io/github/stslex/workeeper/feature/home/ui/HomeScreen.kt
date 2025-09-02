package io.github.stslex.workeeper.feature.home.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogProperties
import androidx.paging.compose.collectAsLazyPagingItems
import com.arkivanov.decompose.ComponentContext
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.toDp
import io.github.stslex.workeeper.core.ui.mvi.NavComponentScreen
import io.github.stslex.workeeper.core.ui.navigation.DialogComponent
import io.github.stslex.workeeper.core.ui.navigation.DialogRouter
import io.github.stslex.workeeper.feature.home.di.HomeFeature
import io.github.stslex.workeeper.feature.home.ui.mvi.handler.HomeComponent

@Composable
fun HomeScreen(
    component: HomeComponent,
    modifier: Modifier = Modifier
) {
    NavComponentScreen(HomeFeature, component) { processor ->

        val items = remember { processor.state.value.items.invoke() }.collectAsLazyPagingItems()

        processor.Handle { event -> }

        val lazyListState = rememberLazyListState()

        HomeWidget(
            lazyPagingItems = items,
            lazyState = lazyListState,
            consume = processor::consume
        )
    }
}

interface CreateDialogComponent : DialogComponent {

    companion object {

        fun create(router: DialogRouter): CreateDialogComponent = ExerciseCreateDialogComponentImpl(router)
    }
}

internal class ExerciseCreateDialogComponentImpl(
    private val router: DialogRouter
) : CreateDialogComponent, ComponentContext by router {

    override fun dismiss() {
        router.popBack()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateNewExerciseDialog(
    modifier: Modifier,
    component: CreateDialogComponent
) {
    val dialogHeight = LocalView.current.height.toDp * 0.5f
    BasicAlertDialog(
        onDismissRequest = { component.dismiss() },
        modifier = modifier
            .fillMaxWidth()
            .height(dialogHeight)
            .background(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(AppDimension.Radius.large)
            )
            .padding(AppDimension.Padding.large),
        properties = DialogProperties()
    ) {
        Box {
            Text("create new exercise")
        }
    }
}