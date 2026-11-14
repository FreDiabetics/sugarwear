package app.sugarlicious.mobile

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource

/** Shared, illustrative preview from the Orbit module; never presented as live CGM. */
@Composable
internal fun OrbitFacePreview(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.sugarlicious_orbit_preview),
        contentDescription = "Orbit – Vorschau",
        modifier = modifier,
        contentScale = ContentScale.Fit,
    )
}
