package io.github.stslex.workeeper.lint_rules

import io.github.detekt.test.utils.compileContentForTest
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class KmpFactoryOwnershipTest {
    @Test
    fun `graph construction belongs in the retained callback`() {
        assertEquals(emptyList<String>(), check("return rememberMetroStoreProcessor { factory.createHomeGraph().store }"))
        assertTrue(check("val graph = factory.createHomeGraph(); return rememberMetroStoreProcessor { graph.store }")
            .any { "inside the retained" in it })
    }

    @Test
    fun `pure helper extraction preserves construction timing`() {
        assertEquals(emptyList<String>(), check(
            "return rememberMetroStoreProcessor { createStore() }",
            helper = "private fun createStore() = factory.createHomeGraph().store",
        ))
        assertTrue(check(
            "val store = createStore(); return rememberMetroStoreProcessor { store }",
            helper = "private fun createStore() = factory.createHomeGraph().store",
        ).any { "inside the retained" in it })
    }

    @Test
    fun `nullable default mutable and missing factory parameters fail`() {
        for (parameter in listOf("private val factory: HomeGraph.Factory?", "private val factory: HomeGraph.Factory = fallback",
            "private var factory: HomeGraph.Factory", "")) {
            assertTrue(check("return rememberMetroStoreProcessor { factory.createHomeGraph().store }", parameter)
                .any { "constructor argument" in it })
        }
    }

    @Test
    fun `duplicate graph creation and fake retention helpers fail`() {
        assertTrue(check("return rememberMetroStoreProcessor { factory.createHomeGraph(); factory.createHomeGraph().store }")
            .any { "exactly one" in it })
        assertTrue(check(
            "return rememberMetroStoreProcessor { factory.createHomeGraph().store }",
            helper = "private fun rememberMetroStoreProcessor(create: () -> Any) = create()",
        ).isNotEmpty())
    }

    @Test
    fun `qualified retainer is permitted and eager argument work is rejected`() {
        assertEquals(emptyList<String>(), check(
            "return io.github.stslex.workeeper.core.ui.mvi.processor.rememberMetroStoreProcessor { factory.createHomeGraph().store }",
        ))
        assertTrue(check(
            "return rememberMetroStoreProcessor(key = factory.createHomeGraph().key) { factory.createHomeGraph().store }",
        ).any { "inside the retained" in it })
    }

    @Test
    fun `review initialization cannot construct a second graph`() {
        assertTrue(check(
            "return rememberMetroStoreProcessor { factory.createHomeGraph().store }",
            helper = "init { factory.createHomeGraph() }",
        ).any { "inside the retained" in it })
    }

    @Test
    fun `review graph creation must use the supplied factory`() {
        assertTrue(check("return rememberMetroStoreProcessor { otherRoot.factory.createHomeGraph().store }")
            .any { "supplied factory" in it })
    }

    @Test
    fun `review qualified retention ignores unrelated helper names`() {
        assertEquals(emptyList<String>(), check(
            "return io.github.stslex.workeeper.core.ui.mvi.processor.rememberMetroStoreProcessor { factory.createHomeGraph().store }",
            helper = "private fun rememberMetroStoreProcessor(create: () -> Any) = create()",
        ))
    }

    @Test
    fun `review constructor is a non null outer Factory even through aliases`() {
        assertTrue(check("return rememberMetroStoreProcessor { factory!!.createHomeGraph().store }",
            parameter = "private val factory: OptionalFactory",
            prefix = "typealias OptionalFactory = HomeGraph.Factory?",
        ).any { "constructor argument" in it })
        assertTrue(check("return rememberMetroStoreProcessor { factory.single().createHomeGraph().store }",
            parameter = "private val factory: List<HomeGraph.Factory>",
        ).any { "constructor argument" in it })
        assertEquals(emptyList<String>(), check("return rememberMetroStoreProcessor { factory.createHomeGraph().store }",
            parameter = "private val factory: FeatureFactory",
            prefix = "typealias FeatureFactory = HomeGraph.Factory",
        ))
    }

    @Test
    fun `local helpers capture factories`() {
        assertEquals(emptyList<String>(), check(
            "fun createStore() = factory.createHomeGraph().store; return rememberMetroStoreProcessor { createStore() }",
        ))
    }

    @Test
    fun `lambda parameters shadow constructor factories`() {
        assertTrue(check(
            "return rememberMetroStoreProcessor { listOf(otherRoot.factory).map { factory -> factory.createHomeGraph().store }.single() }",
        ).any { "supplied factory" in it })
    }

    private fun check(
        body: String,
        parameter: String = "private val factory: HomeGraph.Factory",
        helper: String = "",
        prefix: String = "",
    ): List<String> {
        val file = compileContentForTest(
            """
            package sample
            import io.github.stslex.workeeper.core.ui.mvi.Feature
            import io.github.stslex.workeeper.core.ui.mvi.processor.rememberMetroStoreProcessor
            $prefix
            class HomeFeature($parameter) : Feature<Processor, Home>() {
                fun processor(): Processor { $body }
                $helper
            }
            """.trimIndent(),
        )
        return KmpFactoryOwnership.verify(file.collectDescendantsOfType<KtClass>().single(), KotlinContractNames(file))
            .map { it.second }
    }
}
