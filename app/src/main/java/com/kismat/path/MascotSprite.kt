package com.kismat.path

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

internal enum class MascotPose(val image: Int, val scale: Float = 1f) {
    WAVE_HELLO(R.drawable.mascot_wave),
    WALKING(R.drawable.mascot_walk),
    DRINKING(R.drawable.mascot_drink, 0.94f),
    REFRESHED(R.drawable.mascot_refreshed),
    CELEBRATE(R.drawable.mascot_celebrate),
    BOTTLE_CHEER(R.drawable.mascot_bottle_cheer),
    TIRED(R.drawable.mascot_tired),
    SLEEPING(R.drawable.mascot_sleep),
    CHEEKY(R.drawable.mascot_cheeky),
    STAR(R.drawable.mascot_star),
    JUMPING(R.drawable.mascot_jump),
    DEHYDRATED(R.drawable.mascot_dry),
    OVERFULL(R.drawable.mascot_overfull)
}

internal fun waterMascotPose(totalMl: Long, goalMl: Int): MascotPose {
    val progress = totalMl.toDouble() / goalMl.coerceAtLeast(1)
    return when {
        progress > 1.0 -> MascotPose.OVERFULL
        progress >= 1.0 -> MascotPose.STAR
        progress < 0.2 -> MascotPose.DEHYDRATED
        progress < 0.45 -> MascotPose.DRINKING
        progress < 0.8 -> MascotPose.REFRESHED
        else -> MascotPose.BOTTLE_CHEER
    }
}

/** Shared mascot renderer: transparent, individually cropped art using contain sizing. */
@Composable
internal fun MascotSprite(
    pose: MascotPose,
    description: String,
    modifier: Modifier = Modifier
) {
    Image(
        painter = painterResource(pose.image),
        contentDescription = description,
        modifier = modifier.graphicsLayer {
            scaleX = pose.scale
            scaleY = pose.scale
        }.semantics { contentDescription = description },
        contentScale = ContentScale.Fit,
        alignment = androidx.compose.ui.Alignment.Center
    )
}
