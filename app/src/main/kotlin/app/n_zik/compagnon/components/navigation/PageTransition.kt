package app.n_zik.compagnon.components.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandIn
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkOut
import androidx.compose.animation.togetherWith
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.IntOffset
import app.n_zik.compagnon.bridge.state.TransitionEffect

/**
 * The phone's page transitions (`AppNavigation.kt` 183-230): its `transitionEffect` setting (read since
 * contract 1.10.0, `ui.settings`; `Fade` by default), 350 ms. Forward ([isBack] `false`): its enter / exit
 * transitions. Back ([isBack] `true`, a page closed): its pop transitions — `None` enter and an exit
 * sliding Down / Right for the two slides, the same scale / fade / expand otherwise.
 */
fun <S> AnimatedContentTransitionScope<S>.pageTransition(effect: TransitionEffect, isBack: Boolean = false): ContentTransform {
    val enter: EnterTransition = when (effect) {
        TransitionEffect.None -> EnterTransition.None
        TransitionEffect.Expand -> scaleIn(animationSpec = tween(350), initialScale = 2.0f)
        TransitionEffect.Fade -> fadeIn(animationSpec = tween(350))
        TransitionEffect.Scale -> scaleIn(animationSpec = tween(350))
        TransitionEffect.SlideVertical ->
            if (isBack) EnterTransition.None else slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Up)
        TransitionEffect.SlideHorizontal ->
            if (isBack) EnterTransition.None else slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left)
    }
    val exit: ExitTransition = when (effect) {
        TransitionEffect.None -> ExitTransition.None
        TransitionEffect.Expand -> scaleOut(animationSpec = tween(350), targetScale = 2.0f)
        TransitionEffect.Fade -> fadeOut(animationSpec = tween(350))
        TransitionEffect.Scale -> scaleOut(animationSpec = tween(350))
        TransitionEffect.SlideVertical -> slideOutOfContainer(
            if (isBack) AnimatedContentTransitionScope.SlideDirection.Down else AnimatedContentTransitionScope.SlideDirection.Up,
        )
        TransitionEffect.SlideHorizontal -> slideOutOfContainer(
            if (isBack) AnimatedContentTransitionScope.SlideDirection.Right else AnimatedContentTransitionScope.SlideDirection.Left,
        )
    }
    // The phone's NavHost draws the popped page on top while it leaves
    return (enter togetherWith exit).apply { targetContentZIndex = if (isBack) -1f else 0f }
}

/**
 * The phone's tab-switch transition (`EffectHandler.kt` `transition()`): scale / fade (350 ms), expand
 * (expandIn / shrinkOut from the top start, 350 ms), none, or a spring slide whose direction follows the
 * tab index (Left / Up forward, Right / Down backward).
 */
fun AnimatedContentTransitionScope<Int>.tabTransition(effect: TransitionEffect): ContentTransform = when (effect) {
    TransitionEffect.Scale -> scaleIn(tween(350)) togetherWith scaleOut(tween(350))
    TransitionEffect.Fade -> fadeIn(tween(350)) togetherWith fadeOut(tween(350))
    TransitionEffect.Expand ->
        expandIn(tween(350, 0, LinearOutSlowInEasing), Alignment.TopStart) togetherWith
            shrinkOut(tween(350, 0, LinearOutSlowInEasing), Alignment.TopStart)
    TransitionEffect.None -> EnterTransition.None togetherWith ExitTransition.None
    TransitionEffect.SlideVertical, TransitionEffect.SlideHorizontal -> {
        val spec = spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessLow, visibilityThreshold = IntOffset.VisibilityThreshold)
        val forward = targetState > initialState
        val direction = when {
            effect == TransitionEffect.SlideHorizontal && forward -> AnimatedContentTransitionScope.SlideDirection.Left
            effect == TransitionEffect.SlideHorizontal -> AnimatedContentTransitionScope.SlideDirection.Right
            forward -> AnimatedContentTransitionScope.SlideDirection.Up
            else -> AnimatedContentTransitionScope.SlideDirection.Down
        }
        slideIntoContainer(direction, spec) togetherWith slideOutOfContainer(direction, spec)
    }
}
