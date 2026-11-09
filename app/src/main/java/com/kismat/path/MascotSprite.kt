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
