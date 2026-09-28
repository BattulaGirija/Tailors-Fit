package com.tailorsfit.app.projector

import android.app.Presentation
import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.view.Display
import android.view.WindowManager

/**
 * Shows the pattern on a second screen (a projector connected by HDMI / USB-C adapter or a
 * wireless display) while the phone stays usable as the remote control.
 */
class ProjectorPresentation(outerContext: Context, display: Display) : Presentation(outerContext, display) {
    val view: ProjectorView by lazy { ProjectorView(context) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(view)
    }

    companion object {
        /** The first display suitable for a presentation (a projector / TV), if any. */
        fun findExternalDisplay(context: Context): Display? {
            val dm = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
            return dm.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION).firstOrNull()
        }
    }
}
